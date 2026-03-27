import java.io.FileInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.util.Properties
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

data class Utf8Replacement(
    val from: String,
    val to: String,
)

data class RelocatedArchive(
    val rawConfiguration: org.gradle.api.artifacts.Configuration,
    val outputFile: org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile>,
    val task: org.gradle.api.tasks.TaskProvider<org.gradle.api.Task>,
)

fun rewriteUtf8String(
    utf8: String,
    replacements: List<Utf8Replacement>,
): String = replacements.fold(utf8) { current, replacement -> current.replace(replacement.from, replacement.to) }

fun patchClassUtf8Constants(
    classBytes: ByteArray,
    replacements: List<Utf8Replacement>,
): Pair<ByteArray, Int> {
    val input = DataInputStream(ByteArrayInputStream(classBytes))
    val outputBytes = ByteArrayOutputStream(classBytes.size + 128)
    val output = DataOutputStream(outputBytes)
    var patchedCount = 0

    output.writeInt(input.readInt())
    output.writeShort(input.readUnsignedShort())
    output.writeShort(input.readUnsignedShort())

    val constantPoolCount = input.readUnsignedShort()
    output.writeShort(constantPoolCount)

    var index = 1
    while (index < constantPoolCount) {
        val tag = input.readUnsignedByte()
        output.writeByte(tag)
        when (tag) {
            1 -> {
                val utf8 = input.readUTF()
                val replacement = rewriteUtf8String(utf8, replacements)
                if (replacement != utf8) {
                    output.writeUTF(replacement)
                    patchedCount += 1
                } else {
                    output.writeUTF(utf8)
                }
            }

            3, 4 -> output.writeInt(input.readInt())
            5, 6 -> {
                output.writeLong(input.readLong())
                index += 1
            }

            7, 8, 16, 19, 20 -> output.writeShort(input.readUnsignedShort())
            9, 10, 11, 12, 17, 18 -> {
                output.writeShort(input.readUnsignedShort())
                output.writeShort(input.readUnsignedShort())
            }

            15 -> {
                output.writeByte(input.readUnsignedByte())
                output.writeShort(input.readUnsignedShort())
            }

            else -> error("Unsupported constant pool tag: $tag")
        }
        index += 1
    }

    output.write(input.readBytes())
    output.flush()
    return outputBytes.toByteArray() to patchedCount
}

fun relocateEntryName(
    entryName: String,
    replacements: List<Utf8Replacement>,
): String = rewriteUtf8String(entryName, replacements)

fun patchJarClasses(
    inputJar: java.nio.file.Path,
    outputJar: java.nio.file.Path,
    replacements: List<Utf8Replacement>,
): Int {
    Files.createDirectories(outputJar.parent)
    var patchedClasses = 0

    JarInputStream(Files.newInputStream(inputJar)).use { jarInput ->
        JarOutputStream(Files.newOutputStream(outputJar)).use { jarOutput ->
            var entry = jarInput.nextJarEntry
            while (entry != null) {
                val entryBytes = jarInput.readBytes()
                val relocatedName = relocateEntryName(entry.name, replacements)
                val outputBytes =
                    if (!entry.isDirectory && entry.name.endsWith(".class")) {
                        val (patched, count) = patchClassUtf8Constants(entryBytes, replacements)
                        if (count > 0) {
                            patchedClasses += 1
                        }
                        patched
                    } else {
                        entryBytes
                    }

                if (!entry.isDirectory) {
                    jarOutput.putNextEntry(copyZipEntry(entry, relocatedName))
                    jarOutput.write(outputBytes)
                    jarOutput.closeEntry()
                }
                entry = jarInput.nextJarEntry
            }
        }
    }

    return patchedClasses
}

fun patchAarClassesJar(
    inputAar: java.nio.file.Path,
    outputAar: java.nio.file.Path,
    replacements: List<Utf8Replacement>,
): Int {
    Files.createDirectories(outputAar.parent)
    var patchedClasses = 0

    ZipInputStream(Files.newInputStream(inputAar)).use { aarInput ->
        ZipOutputStream(Files.newOutputStream(outputAar)).use { aarOutput ->
            var entry = aarInput.nextEntry
            while (entry != null) {
                val entryBytes = aarInput.readBytes()
                val outputBytes =
                    if (!entry.isDirectory && entry.name == "classes.jar") {
                        val tempInput = Files.createTempFile("media3-guava-input", ".jar")
                        val tempOutput = Files.createTempFile("media3-guava-output", ".jar")
                        try {
                            Files.write(tempInput, entryBytes)
                            patchedClasses = patchJarClasses(tempInput, tempOutput, replacements)
                            Files.readAllBytes(tempOutput)
                        } finally {
                            Files.deleteIfExists(tempInput)
                            Files.deleteIfExists(tempOutput)
                        }
                    } else {
                        entryBytes
                    }

                aarOutput.putNextEntry(copyZipEntry(entry))
                aarOutput.write(outputBytes)
                aarOutput.closeEntry()
                entry = aarInput.nextEntry
            }
        }
    }

    return patchedClasses
}

fun copyZipEntry(
    entry: ZipEntry,
    name: String = entry.name,
): ZipEntry {
    val copy = ZipEntry(name)
    copy.comment = entry.comment
    copy.extra = entry.extra
    copy.method = ZipEntry.DEFLATED
    return copy
}

fun aarContainsUtf8(inputAar: java.nio.file.Path, target: String): Boolean {
    ZipInputStream(Files.newInputStream(inputAar)).use { aarInput ->
        var entry = aarInput.nextEntry
        while (entry != null) {
            if (!entry.isDirectory && entry.name == "classes.jar") {
                val jarBytes = aarInput.readBytes()
                JarInputStream(ByteArrayInputStream(jarBytes)).use { jarInput ->
                    var jarEntry = jarInput.nextJarEntry
                    while (jarEntry != null) {
                        if (!jarEntry.isDirectory && jarEntry.name.endsWith(".class")) {
                            val classBytes = jarInput.readBytes()
                            if (classBytes.decodeToString().contains(target)) {
                                return true
                            }
                        }
                        jarEntry = jarInput.nextJarEntry
                    }
                }
            } else {
                aarInput.readBytes()
            }
            entry = aarInput.nextEntry
        }
    }
    return false
}

fun jarContainsUtf8(
    inputJar: java.nio.file.Path,
    target: String,
): Boolean {
    JarInputStream(Files.newInputStream(inputJar)).use { jarInput ->
        var entry = jarInput.nextJarEntry
        while (entry != null) {
            if (!entry.isDirectory && entry.name.endsWith(".class")) {
                val classBytes = jarInput.readBytes()
                if (classBytes.decodeToString().contains(target)) {
                    return true
                }
            } else {
                jarInput.readBytes()
            }
            entry = jarInput.nextJarEntry
        }
    }
    return false
}

fun archiveContainsEntryPrefix(
    inputArchive: java.nio.file.Path,
    prefix: String,
): Boolean =
    ZipFile(inputArchive.toFile()).use { zipFile ->
        zipFile.entries().asSequence().any { it.name.startsWith(prefix) }
    }

fun createRawResolvableConfiguration(
    name: String,
    preferAndroidRuntimeVariant: Boolean = false,
): org.gradle.api.artifacts.Configuration =
    configurations.create(name) {
        isCanBeConsumed = false
        isCanBeResolved = true
        isTransitive = false

        if (preferAndroidRuntimeVariant) {
            attributes {
                attribute(
                    Category.CATEGORY_ATTRIBUTE,
                    objects.named(Category::class.java, Category.LIBRARY),
                )
                attribute(
                    Bundling.BUNDLING_ATTRIBUTE,
                    objects.named(Bundling::class.java, Bundling.EXTERNAL),
                )
                attribute(
                    LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                    objects.named(LibraryElements::class.java, LibraryElements.JAR),
                )
                attribute(
                    Usage.USAGE_ATTRIBUTE,
                    objects.named(Usage::class.java, Usage.JAVA_RUNTIME),
                )
                attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8)
                attribute(
                    Attribute.of("org.gradle.jvm.environment", String::class.java),
                    "android",
                )
            }
        }
    }

fun String.toTaskSuffix(): String =
    split('-').joinToString(separator = "") { part ->
        part.replaceFirstChar { char ->
            if (char.isLowerCase()) {
                char.titlecase()
            } else {
                char.toString()
            }
        }
    }

val media3Version = libs.versions.media3.get()
val guavaVersion = libs.versions.guava.get()
val failureaccessVersion = "1.0.1"
val guavaRelocationReplacements =
    listOf(
        Utf8Replacement(
            "com/google/common/",
            "top/yogiczy/mytv/shaded/guava/"
        ),
    )

val rawGuavaJar = createRawResolvableConfiguration(
    name = "rawGuavaJar",
    preferAndroidRuntimeVariant = true,
)
val rawFailureaccessJar = createRawResolvableConfiguration("rawFailureaccessJar")

val relocatedGuavaJar = layout.buildDirectory.file("relocated-guava/guava-$guavaVersion-relocated.jar")
val relocatedFailureaccessJar =
    layout.buildDirectory.file("relocated-guava/failureaccess-$failureaccessVersion-relocated.jar")

val relocateGuavaJar by tasks.registering {
    inputs.files(rawGuavaJar)
    outputs.file(relocatedGuavaJar)

    doLast {
        patchJarClasses(
            inputJar = rawGuavaJar.singleFile.toPath(),
            outputJar = relocatedGuavaJar.get().asFile.toPath(),
            replacements = guavaRelocationReplacements,
        )
    }
}

val relocateFailureaccessJar by tasks.registering {
    inputs.files(rawFailureaccessJar)
    outputs.file(relocatedFailureaccessJar)

    doLast {
        patchJarClasses(
            inputJar = rawFailureaccessJar.singleFile.toPath(),
            outputJar = relocatedFailureaccessJar.get().asFile.toPath(),
            replacements = guavaRelocationReplacements,
        )
    }
}

val media3Modules =
    listOf(
        "common",
        "container",
        "database",
        "datasource",
        "decoder",
        "extractor",
        "exoplayer",
        "exoplayer-hls",
        "exoplayer-rtsp",
    )

val relocatedMedia3Aars =
    media3Modules.associateWith { module ->
        val taskSuffix = module.toTaskSuffix()
        val rawConfiguration = createRawResolvableConfiguration("rawMedia3${taskSuffix}Aar")
        val outputFile =
            layout.buildDirectory.file(
                "relocated-media3/media3-$module-$media3Version-relocated.aar"
            )
        val relocateTask =
            tasks.register("relocateMedia3${taskSuffix}Aar") {
                inputs.files(rawConfiguration)
                outputs.file(outputFile)
                dependsOn(relocateGuavaJar, relocateFailureaccessJar)

                doLast {
                    patchAarClassesJar(
                        inputAar = rawConfiguration.singleFile.toPath(),
                        outputAar = outputFile.get().asFile.toPath(),
                        replacements = guavaRelocationReplacements,
                    )
                }
            }

        RelocatedArchive(
            rawConfiguration = rawConfiguration,
            outputFile = outputFile,
            task = relocateTask,
        )
    }

tasks.register("verifyMedia3GuavaRelocation") {
    dependsOn(relocateGuavaJar, relocateFailureaccessJar)
    dependsOn(relocatedMedia3Aars.values.map { it.task })

    doLast {
        val shadedPrefix = "top/yogiczy/mytv/shaded/guava/"
        val resolvedGuavaJar = rawGuavaJar.singleFile
        val commonAar = relocatedMedia3Aars.getValue("common").outputFile.get().asFile.toPath()
        val hlsAar = relocatedMedia3Aars.getValue("exoplayer-hls").outputFile.get().asFile.toPath()

        check(resolvedGuavaJar.name == "guava-$guavaVersion.jar") {
            "rawGuavaJar resolved wrong artifact: ${resolvedGuavaJar.name}"
        }
        check(!archiveContainsEntryPrefix(relocatedGuavaJar.get().asFile.toPath(), "com/google/common/")) {
            "Relocated guava JAR still contains com/google/common/ classes"
        }
        check(!archiveContainsEntryPrefix(relocatedFailureaccessJar.get().asFile.toPath(), "com/google/common/")) {
            "Relocated failureaccess JAR still contains com/google/common/ classes"
        }
        check(archiveContainsEntryPrefix(relocatedGuavaJar.get().asFile.toPath(), shadedPrefix)) {
            "Relocated guava JAR does not contain shaded classes"
        }
        check(archiveContainsEntryPrefix(relocatedFailureaccessJar.get().asFile.toPath(), shadedPrefix)) {
            "Relocated failureaccess JAR does not contain shaded classes"
        }
        check(
            !archiveContainsEntryPrefix(
                relocatedGuavaJar.get().asFile.toPath(),
                "${shadedPrefix}hash/Hashing\$Crc32cMethodHandles.class",
            )
        ) {
            "Relocated guava JAR unexpectedly contains JRE-only Hashing\$Crc32cMethodHandles"
        }
        relocatedMedia3Aars.forEach { (module, archive) ->
            val aarPath = archive.outputFile.get().asFile.toPath()
            check(!aarContainsUtf8(aarPath, "com/google/common/")) {
                "Relocated media3-$module AAR still contains com/google/common/"
            }
        }
        check(aarContainsUtf8(commonAar, shadedPrefix)) {
            "Relocated media3-common AAR does not contain shaded guava references"
        }
        check(aarContainsUtf8(hlsAar, shadedPrefix)) {
            "Relocated media3-exoplayer-hls AAR does not contain shaded guava references"
        }
        check(!jarContainsUtf8(relocatedGuavaJar.get().asFile.toPath(), "com/google/common/")) {
            "Relocated guava class constants still contain com/google/common/"
        }
    }
}

android {
    namespace = "top.yogiczy.mytv"
    compileSdk = 34

    defaultConfig {
        applicationId = "top.yogiczy.mytv"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.4.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86_64"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        create("release") {
            storeFile =
                file(System.getenv("KEYSTORE") ?: keystoreProperties["storeFile"] ?: "keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
                ?: keystoreProperties.getProperty("storePassword")
            keyAlias = System.getenv("KEY_ALIAS") ?: keystoreProperties.getProperty("keyAlias")
            keyPassword =
                System.getenv("KEY_PASSWORD") ?: keystoreProperties.getProperty("keyPassword")
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    add(rawGuavaJar.name, "com.google.guava:guava:$guavaVersion")
    add(rawFailureaccessJar.name, "com.google.guava:failureaccess:$failureaccessVersion")
    relocatedMedia3Aars.forEach { (module, archive) ->
        add(archive.rawConfiguration.name, "androidx.media3:media3-$module:$media3Version@aar")
    }

    val relocatedGuavaDependency = files(relocatedGuavaJar).builtBy(relocateGuavaJar)
    val relocatedFailureaccessDependency =
        files(relocatedFailureaccessJar).builtBy(relocateFailureaccessJar)
    val relocatedMedia3Dependencies =
        relocatedMedia3Aars.mapValues { (_, archive) ->
            files(archive.outputFile).builtBy(archive.task)
        }

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.androidx.material.icons.extended)

    // TV Compose
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)

    // 播放器
    media3Modules.forEach { module ->
        implementation(relocatedMedia3Dependencies.getValue(module))
    }
    implementation(relocatedGuavaDependency)
    implementation(relocatedFailureaccessDependency)
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("org.checkerframework:checker-qual:3.42.0")
    implementation("com.google.errorprone:error_prone_annotations:2.26.1")
    implementation("com.google.j2objc:j2objc-annotations:3.0.0")

    // 序列化
    implementation(libs.kotlinx.serialization)

    // 网络请求
    implementation(libs.okhttp)
    implementation(libs.androidasync)

    // 图片加载
    implementation(libs.coil.compose)

    // 二维码
    implementation(libs.qrose)

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
