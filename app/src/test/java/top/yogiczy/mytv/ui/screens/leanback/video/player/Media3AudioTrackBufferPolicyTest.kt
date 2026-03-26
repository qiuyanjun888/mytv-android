package top.yogiczy.mytv.ui.screens.leanback.video.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3AudioTrackBufferPolicyTest {
    @Test
    fun `legacy amlogic box should use larger audio track buffer`() {
        val profile = Media3AudioTrackBufferPolicy.profileFor(
            Media3DeviceInfo(
                model = "MagicBox_M17",
                hardware = "amlogic",
                board = "gxl_p281",
                sdkInt = 23,
            )
        )

        assertTrue(profile.useLargeBuffer)
        assertEquals(500_000, profile.minPcmBufferDurationUs)
        assertEquals(1_500_000, profile.maxPcmBufferDurationUs)
        assertEquals(8, profile.pcmBufferMultiplicationFactor)
        assertEquals(500_000, profile.passthroughBufferDurationUs)
        assertEquals(4, profile.ac3BufferMultiplicationFactor)
    }

    @Test
    fun `modern non amlogic device should keep default audio track buffer`() {
        val profile = Media3AudioTrackBufferPolicy.profileFor(
            Media3DeviceInfo(
                model = "ADT-4",
                hardware = "qcom",
                board = "bengal",
                sdkInt = 33,
            )
        )

        assertFalse(profile.useLargeBuffer)
        assertEquals(250_000, profile.minPcmBufferDurationUs)
        assertEquals(750_000, profile.maxPcmBufferDurationUs)
        assertEquals(4, profile.pcmBufferMultiplicationFactor)
        assertEquals(250_000, profile.passthroughBufferDurationUs)
        assertEquals(2, profile.ac3BufferMultiplicationFactor)
    }
}
