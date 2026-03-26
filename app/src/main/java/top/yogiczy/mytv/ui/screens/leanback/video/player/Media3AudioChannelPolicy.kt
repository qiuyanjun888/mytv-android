package top.yogiczy.mytv.ui.screens.leanback.video.player

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix

internal object Media3AudioChannelPolicy {
    fun maxAudioChannelCountFor(deviceInfo: Media3DeviceInfo): Int? {
        return if (shouldForceStereoPcmDownmix(deviceInfo)) {
            2
        } else {
            null
        }
    }

    fun shouldForceStereoPcmDownmix(deviceInfo: Media3DeviceInfo): Boolean {
        return Media3AudioTrackBufferPolicy.isLegacyAmlogicBox(deviceInfo)
    }

    fun audioProcessorsFor(deviceInfo: Media3DeviceInfo): Array<AudioProcessor> {
        return if (shouldForceStereoPcmDownmix(deviceInfo)) {
            arrayOf(buildStereoDownmixProcessor())
        } else {
            emptyArray()
        }
    }

    private fun buildStereoDownmixProcessor(): AudioProcessor {
        return ChannelMixingAudioProcessor().apply {
            putChannelMixingMatrix(ChannelMixingMatrix.create(1, 2))
            putChannelMixingMatrix(ChannelMixingMatrix.create(2, 2))
            putChannelMixingMatrix(
                ChannelMixingMatrix(
                    3,
                    2,
                    floatArrayOf(
                        1f, 0f,
                        0f, 1f,
                        0.707f, 0.707f,
                    )
                )
            )
            putChannelMixingMatrix(
                ChannelMixingMatrix(
                    4,
                    2,
                    floatArrayOf(
                        1f, 0f,
                        0f, 1f,
                        0.707f, 0f,
                        0f, 0.707f,
                    )
                )
            )
            putChannelMixingMatrix(
                ChannelMixingMatrix(
                    5,
                    2,
                    floatArrayOf(
                        1f, 0f,
                        0f, 1f,
                        0.707f, 0.707f,
                        0.707f, 0f,
                        0f, 0.707f,
                    )
                )
            )
            putChannelMixingMatrix(
                ChannelMixingMatrix(
                    6,
                    2,
                    floatArrayOf(
                        1f, 0f,
                        0f, 1f,
                        0.707f, 0.707f,
                        0.5f, 0.5f,
                        0.707f, 0f,
                        0f, 0.707f,
                    )
                )
            )
            putChannelMixingMatrix(
                ChannelMixingMatrix(
                    7,
                    2,
                    floatArrayOf(
                        1f, 0f,
                        0f, 1f,
                        0.707f, 0.707f,
                        0.5f, 0.5f,
                        0.5f, 0.5f,
                        0.707f, 0f,
                        0f, 0.707f,
                    )
                )
            )
            putChannelMixingMatrix(
                ChannelMixingMatrix(
                    8,
                    2,
                    floatArrayOf(
                        1f, 0f,
                        0f, 1f,
                        0.707f, 0.707f,
                        0.5f, 0.5f,
                        0.5f, 0f,
                        0f, 0.5f,
                        0.5f, 0f,
                        0f, 0.5f,
                    )
                )
            )
        }
    }
}
