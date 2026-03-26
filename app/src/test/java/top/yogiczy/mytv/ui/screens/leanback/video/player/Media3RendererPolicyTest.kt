package top.yogiczy.mytv.ui.screens.leanback.video.player

import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
import androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3RendererPolicyTest {
    @Test
    fun `4k hevc with ffmpeg ac3 should avoid extension preference`() {
        val metadata = LeanbackVideoPlayer.Metadata(
            videoMimeType = MimeTypes.VIDEO_H265,
            videoWidth = 3840,
            videoHeight = 2160,
            audioMimeType = MimeTypes.AUDIO_AC3,
            audioDecoder = "ffmpeg-a52",
        )

        assertTrue(Media3RendererPolicy.shouldAvoidExtensionRendererPreference(metadata))
        assertEquals(
            EXTENSION_RENDERER_MODE_ON,
            Media3RendererPolicy.extensionRendererModeFor(
                metadata = metadata,
                currentMode = EXTENSION_RENDERER_MODE_PREFER,
            ),
        )
    }

    @Test
    fun `legacy amlogic 4k hevc with ffmpeg ac3 should keep extension preference`() {
        val metadata = LeanbackVideoPlayer.Metadata(
            videoMimeType = MimeTypes.VIDEO_H265,
            videoWidth = 3840,
            videoHeight = 2160,
            audioMimeType = MimeTypes.AUDIO_AC3,
            audioDecoder = "ffmpeg-a52",
        )
        val deviceInfo = Media3DeviceInfo(
            model = "MagicBox_M17",
            hardware = "amlogic",
            board = "gxl_p212",
            sdkInt = 23,
        )

        assertFalse(
            Media3RendererPolicy.shouldAvoidExtensionRendererPreference(
                metadata = metadata,
                deviceInfo = deviceInfo,
            )
        )
        assertEquals(
            EXTENSION_RENDERER_MODE_PREFER,
            Media3RendererPolicy.extensionRendererModeFor(
                metadata = metadata,
                deviceInfo = deviceInfo,
                currentMode = EXTENSION_RENDERER_MODE_PREFER,
            ),
        )
    }

    @Test
    fun `aac stream should keep extension preference`() {
        val metadata = LeanbackVideoPlayer.Metadata(
            videoMimeType = MimeTypes.VIDEO_H264,
            videoWidth = 1920,
            videoHeight = 1080,
            audioMimeType = MimeTypes.AUDIO_AAC,
            audioDecoder = "c2.android.aac.decoder",
        )

        assertFalse(Media3RendererPolicy.shouldAvoidExtensionRendererPreference(metadata))
        assertEquals(
            EXTENSION_RENDERER_MODE_PREFER,
            Media3RendererPolicy.extensionRendererModeFor(
                metadata = metadata,
                currentMode = EXTENSION_RENDERER_MODE_PREFER,
            ),
        )
    }
}
