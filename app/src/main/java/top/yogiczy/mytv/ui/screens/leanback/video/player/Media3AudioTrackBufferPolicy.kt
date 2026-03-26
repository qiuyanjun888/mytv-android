package top.yogiczy.mytv.ui.screens.leanback.video.player

internal data class Media3DeviceInfo(
    val manufacturer: String = "",
    val model: String = "",
    val hardware: String = "",
    val board: String = "",
    val sdkInt: Int = Int.MAX_VALUE,
)

internal data class Media3AudioTrackBufferProfile(
    val useLargeBuffer: Boolean = false,
    val minPcmBufferDurationUs: Int = 250_000,
    val maxPcmBufferDurationUs: Int = 750_000,
    val pcmBufferMultiplicationFactor: Int = 4,
    val passthroughBufferDurationUs: Int = 250_000,
    val ac3BufferMultiplicationFactor: Int = 2,
)

internal object Media3AudioTrackBufferPolicy {
    fun profileFor(deviceInfo: Media3DeviceInfo): Media3AudioTrackBufferProfile {
        return if (isLegacyAmlogicBox(deviceInfo)) {
            Media3AudioTrackBufferProfile(
                useLargeBuffer = true,
                minPcmBufferDurationUs = 500_000,
                maxPcmBufferDurationUs = 1_500_000,
                pcmBufferMultiplicationFactor = 8,
                passthroughBufferDurationUs = 500_000,
                ac3BufferMultiplicationFactor = 4,
            )
        } else {
            Media3AudioTrackBufferProfile()
        }
    }

    fun isLegacyAmlogicBox(deviceInfo: Media3DeviceInfo): Boolean {
        if (deviceInfo.sdkInt > 23) return false

        val manufacturer = deviceInfo.manufacturer.lowercase()
        val model = deviceInfo.model.lowercase()
        val hardware = deviceInfo.hardware.lowercase()
        val board = deviceInfo.board.lowercase()

        return hardware.contains("amlogic") ||
            board.startsWith("gxl") ||
            manufacturer.contains("amlogic") ||
            model.contains("magicbox")
    }
}
