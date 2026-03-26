package top.yogiczy.mytv.ui.screens.leanback.video.player

import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
import androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER

internal object Media3RendererPolicy {
    private val ac3MimeTypes =
        setOf(
            MimeTypes.AUDIO_AC3,
            MimeTypes.AUDIO_E_AC3,
            MimeTypes.AUDIO_E_AC3_JOC,
        )

    fun shouldAvoidExtensionRendererPreference(
        metadata: LeanbackVideoPlayer.Metadata,
        deviceInfo: Media3DeviceInfo = Media3DeviceInfo(),
    ): Boolean {
        if (Media3AudioTrackBufferPolicy.isLegacyAmlogicBox(deviceInfo)) {
            return false
        }

        val is4kHevc =
            (metadata.videoWidth >= 3840 || metadata.videoHeight >= 2160) &&
                (
                    metadata.videoMimeType.equals(MimeTypes.VIDEO_H265, ignoreCase = true) ||
                        metadata.videoMimeType.contains("hevc", ignoreCase = true)
                )
        val isAc3Family = metadata.audioMimeType in ac3MimeTypes
        val isSoftwareAudioDecoder =
            metadata.audioDecoder.contains("ffmpeg", ignoreCase = true) ||
                metadata.audioDecoder.contains("a52", ignoreCase = true)

        return is4kHevc && isAc3Family && isSoftwareAudioDecoder
    }

    fun extensionRendererModeFor(
        metadata: LeanbackVideoPlayer.Metadata,
        deviceInfo: Media3DeviceInfo = Media3DeviceInfo(),
        currentMode: Int,
    ): Int {
        return if (
            currentMode == EXTENSION_RENDERER_MODE_PREFER &&
            shouldAvoidExtensionRendererPreference(metadata, deviceInfo)
        ) {
            EXTENSION_RENDERER_MODE_ON
        } else {
            currentMode
        }
    }
}
