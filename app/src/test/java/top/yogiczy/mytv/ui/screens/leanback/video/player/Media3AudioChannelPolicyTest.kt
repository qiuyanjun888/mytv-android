package top.yogiczy.mytv.ui.screens.leanback.video.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Media3AudioChannelPolicyTest {
    @Test
    fun `legacy amlogic box should cap audio output to stereo`() {
        val deviceInfo = Media3DeviceInfo(
            model = "MagicBox_M17",
            hardware = "amlogic",
            board = "gxl_p281",
            sdkInt = 23,
        )
        val maxAudioChannelCount = Media3AudioChannelPolicy.maxAudioChannelCountFor(deviceInfo)

        assertEquals(2, maxAudioChannelCount)
        assertEquals(true, Media3AudioChannelPolicy.shouldForceStereoPcmDownmix(deviceInfo))
    }

    @Test
    fun `modern non amlogic device should not cap audio output channel count`() {
        val deviceInfo = Media3DeviceInfo(
            model = "ADT-4",
            hardware = "qcom",
            board = "bengal",
            sdkInt = 33,
        )
        val maxAudioChannelCount = Media3AudioChannelPolicy.maxAudioChannelCountFor(deviceInfo)

        assertNull(maxAudioChannelCount)
        assertEquals(false, Media3AudioChannelPolicy.shouldForceStereoPcmDownmix(deviceInfo))
    }
}
