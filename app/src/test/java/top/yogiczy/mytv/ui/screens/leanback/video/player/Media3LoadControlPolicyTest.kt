package top.yogiczy.mytv.ui.screens.leanback.video.player

import androidx.media3.exoplayer.DefaultLoadControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3LoadControlPolicyTest {
    @Test
    fun `fast channel switch profile should lower startup thresholds only`() {
        val profile = Media3LoadControlPolicy.fastChannelSwitchProfile()

        assertEquals(DefaultLoadControl.DEFAULT_MIN_BUFFER_MS, profile.minBufferMs)
        assertEquals(DefaultLoadControl.DEFAULT_MAX_BUFFER_MS, profile.maxBufferMs)
        assertTrue(
            profile.bufferForPlaybackMs < DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS
        )
        assertTrue(
            profile.bufferForPlaybackAfterRebufferMs <
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
        )
        assertEquals(DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES, profile.targetBufferBytes)
        assertEquals(
            DefaultLoadControl.DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS,
            profile.prioritizeTimeOverSizeThresholds,
        )
        assertEquals(
            DefaultLoadControl.DEFAULT_BACK_BUFFER_DURATION_MS,
            profile.backBufferDurationMs,
        )
        assertEquals(
            DefaultLoadControl.DEFAULT_RETAIN_BACK_BUFFER_FROM_KEYFRAME,
            profile.retainBackBufferFromKeyframe,
        )
    }

    @Test
    fun `fast channel switch profile should use aggressive but bounded startup values`() {
        val profile = Media3LoadControlPolicy.fastChannelSwitchProfile()

        assertEquals(500, profile.bufferForPlaybackMs)
        assertEquals(1_000, profile.bufferForPlaybackAfterRebufferMs)
    }
}
