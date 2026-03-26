package top.yogiczy.mytv.ui.screens.leanback.video.player

import androidx.media3.exoplayer.DefaultLoadControl

internal data class Media3LoadControlProfile(
    val minBufferMs: Int = DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
    val maxBufferMs: Int = DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
    val bufferForPlaybackMs: Int = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
    val bufferForPlaybackAfterRebufferMs: Int =
        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
    val targetBufferBytes: Int = DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES,
    val prioritizeTimeOverSizeThresholds: Boolean =
        DefaultLoadControl.DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS,
    val backBufferDurationMs: Int = DefaultLoadControl.DEFAULT_BACK_BUFFER_DURATION_MS,
    val retainBackBufferFromKeyframe: Boolean =
        DefaultLoadControl.DEFAULT_RETAIN_BACK_BUFFER_FROM_KEYFRAME,
)

internal object Media3LoadControlPolicy {
    const val FAST_BUFFER_FOR_PLAYBACK_MS = 500
    const val FAST_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 1_000

    fun fastChannelSwitchProfile(): Media3LoadControlProfile {
        return Media3LoadControlProfile(
            bufferForPlaybackMs = FAST_BUFFER_FOR_PLAYBACK_MS,
            bufferForPlaybackAfterRebufferMs = FAST_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
        )
    }

    fun buildFastChannelSwitchLoadControl(): DefaultLoadControl {
        val profile = fastChannelSwitchProfile()
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                profile.minBufferMs,
                profile.maxBufferMs,
                profile.bufferForPlaybackMs,
                profile.bufferForPlaybackAfterRebufferMs,
            )
            .setTargetBufferBytes(profile.targetBufferBytes)
            .setPrioritizeTimeOverSizeThresholds(profile.prioritizeTimeOverSizeThresholds)
            .setBackBuffer(
                profile.backBufferDurationMs,
                profile.retainBackBufferFromKeyframe,
            )
            .build()
    }
}
