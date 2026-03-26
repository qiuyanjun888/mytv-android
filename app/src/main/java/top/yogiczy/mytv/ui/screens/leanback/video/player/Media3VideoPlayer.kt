package top.yogiczy.mytv.ui.screens.leanback.video.player

import android.content.Context
import android.net.Uri
import android.os.Build
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
import androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.DefaultAudioTrackBufferSizeProvider
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.extractor.DefaultExtractorsFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yogiczy.mytv.ui.utils.SP
import androidx.media3.common.PlaybackException as Media3PlaybackException

@OptIn(UnstableApi::class)
class LeanbackMedia3VideoPlayer(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
) : LeanbackVideoPlayer(coroutineScope) {
    private var extensionRendererMode = EXTENSION_RENDERER_MODE_PREFER
    private val deviceInfo =
        Media3DeviceInfo(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            hardware = Build.HARDWARE.orEmpty(),
            board = Build.BOARD.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
        )
    private val audioTrackBufferProfile =
        Media3AudioTrackBufferPolicy.profileFor(deviceInfo)
    private var videoPlayer = createVideoPlayer(extensionRendererMode)
    private var currentUri: Uri? = null
    private var currentContentType: Int? = null
    private var videoSurfaceView: SurfaceView? = null
    private var hasRetriedWithPlatformPreferredRenderers = false
    private var lastPositionSampleMs: Long? = null
    private var positionSampleCount = 0
    private var consecutiveSlowPositionSamples = 0

    private fun createVideoPlayer(extensionRendererMode: Int): ExoPlayer {
        val trackSelector = DefaultTrackSelector(context).apply {
            val maxAudioChannelCount = Media3AudioChannelPolicy.maxAudioChannelCountFor(deviceInfo)
            if (maxAudioChannelCount != null) {
                log.w("命中老 Amlogic 盒子音频声道限制配置: maxAudioChannelCount=$maxAudioChannelCount")
                setParameters(
                    buildUponParameters()
                        .setMaxAudioChannelCount(maxAudioChannelCount)
                )
            }
        }

        return ExoPlayer.Builder(
            context,
            createRenderersFactory(extensionRendererMode)
        ).setTrackSelector(
            trackSelector
        ).build().apply {
            playWhenReady = true
        }
    }

    private fun createRenderersFactory(extensionRendererMode: Int): DefaultRenderersFactory {
        val audioProcessors = Media3AudioChannelPolicy.audioProcessorsFor(deviceInfo)
        if (audioTrackBufferProfile.useLargeBuffer) {
            log.w(
                "命中老 Amlogic 盒子音频缓冲加大配置: " +
                    "min=${audioTrackBufferProfile.minPcmBufferDurationUs} " +
                    "max=${audioTrackBufferProfile.maxPcmBufferDurationUs} " +
                    "factor=${audioTrackBufferProfile.pcmBufferMultiplicationFactor}"
            )
        }

        return object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink {
                val builder = DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)

                if (audioProcessors.isNotEmpty()) {
                    log.w("命中老 Amlogic 盒子 PCM 立体声 downmix 配置")
                    builder.setAudioProcessors(audioProcessors)
                }

                if (audioTrackBufferProfile.useLargeBuffer) {
                    builder.setAudioTrackBufferSizeProvider(
                        DefaultAudioTrackBufferSizeProvider.Builder()
                            .setMinPcmBufferDurationUs(audioTrackBufferProfile.minPcmBufferDurationUs)
                            .setMaxPcmBufferDurationUs(audioTrackBufferProfile.maxPcmBufferDurationUs)
                            .setPcmBufferMultiplicationFactor(audioTrackBufferProfile.pcmBufferMultiplicationFactor)
                            .setPassthroughBufferDurationUs(audioTrackBufferProfile.passthroughBufferDurationUs)
                            .setAc3BufferMultiplicationFactor(audioTrackBufferProfile.ac3BufferMultiplicationFactor)
                            .build()
                    )
                }

                return builder.build()
            }
        }
            // 默认保留扩展解码兜底，但重负载 4K 流会在运行时切回平台优先。
            .setExtensionRendererMode(extensionRendererMode)
            .setEnableDecoderFallback(true)
    }

    private val contentTypeAttempts = mutableMapOf<Int, Boolean>()
    private var updatePositionJob: Job? = null

    @OptIn(UnstableApi::class)
    private fun prepare(uri: Uri, contentType: Int? = null) {
        val dataSourceFactory =
            DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory().apply {
                setUserAgent(SP.videoPlayerUserAgent)
                setConnectTimeoutMs(SP.videoPlayerLoadTimeout.toInt())
                setReadTimeoutMs(SP.videoPlayerLoadTimeout.toInt())
                setKeepPostFor302Redirects(true)
                setAllowCrossProtocolRedirects(true)
            })

        currentUri = uri
        currentContentType = contentType
        val resolvedContentType = contentType ?: Util.inferContentType(uri)

        val mediaSource = when (resolvedContentType) {
            C.CONTENT_TYPE_HLS -> {
                HlsMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(uri))
            }

            C.CONTENT_TYPE_RTSP -> {
                RtspMediaSource.Factory().createMediaSource(MediaItem.fromUri(uri))
            }

            C.CONTENT_TYPE_OTHER -> {
                createProgressiveMediaSource(uri, dataSourceFactory, resolvedContentType)
            }

            else -> {
                triggerError(
                    PlaybackException.UNSUPPORTED_TYPE.copy(
                        errorCodeName =
                            "${PlaybackException.UNSUPPORTED_TYPE.message}_$resolvedContentType"
                    )
                )
                null
            }
        }

        if (mediaSource != null) {
            contentTypeAttempts[resolvedContentType] = true
            videoPlayer.setMediaSource(mediaSource)
            videoPlayer.prepare()
            triggerPrepared()
        }
        updatePositionJob?.cancel()
        updatePositionJob = null
    }

    private fun createProgressiveMediaSource(
        uri: Uri,
        dataSourceFactory: DefaultDataSource.Factory,
        resolvedContentType: Int,
    ): ProgressiveMediaSource {
        val profile = Media3ProgressivePlaybackProfilePolicy.progressiveProfileFor(
            url = uri.toString(),
            resolvedContentType = resolvedContentType,
        )
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .apply {
                if (profile.mediaItemMimeType == MimeTypes.VIDEO_MP2T) {
                    setMimeType(profile.mediaItemMimeType)
                }
            }
            .build()

        val factory = if (profile.useDedicatedTsExtractorFactory) {
            log.w("命中 HTTP TS 直播专用播放配置: $uri")
            ProgressiveMediaSource.Factory(
                dataSourceFactory,
                DefaultExtractorsFactory()
                    .setTsExtractorMode(profile.tsExtractorMode)
                    .setTsExtractorFlags(profile.tsExtractorFlags)
                    .setTsExtractorTimestampSearchBytes(profile.tsTimestampSearchBytes)
            )
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory)
        }

        return factory
            .setContinueLoadingCheckIntervalBytes(profile.continueLoadingCheckIntervalBytes)
            .createMediaSource(mediaItem)
    }

    private fun attachPlayer(player: ExoPlayer) {
        player.addListener(playerListener)
        player.addAnalyticsListener(metadataListener)
        player.addAnalyticsListener(eventLogger)
        videoSurfaceView?.let(player::setVideoSurfaceView)
    }

    private fun detachPlayer(player: ExoPlayer) {
        player.removeListener(playerListener)
        player.removeAnalyticsListener(metadataListener)
        player.removeAnalyticsListener(eventLogger)
    }

    @OptIn(UnstableApi::class)
    private fun maybeRetryWithPlatformPreferredRenderers() {
        if (hasRetriedWithPlatformPreferredRenderers) return

        val nextMode = Media3RendererPolicy.extensionRendererModeFor(
            metadata = metadata,
            deviceInfo = deviceInfo,
            currentMode = extensionRendererMode,
        )
        if (nextMode == extensionRendererMode) return

        val uri = currentUri ?: return
        val contentType = currentContentType
        val previousPlayer = videoPlayer

        hasRetriedWithPlatformPreferredRenderers = true
        extensionRendererMode = EXTENSION_RENDERER_MODE_ON
        updatePositionJob?.cancel()
        updatePositionJob = null
        metadata = Metadata()
        contentTypeAttempts.clear()

        log.w("检测到4K HEVC + AC3 软件音频解码，切换为平台优先解码器重试")

        detachPlayer(previousPlayer)
        previousPlayer.stop()
        previousPlayer.release()

        videoPlayer = createVideoPlayer(extensionRendererMode)
        attachPlayer(videoPlayer)
        prepare(uri, contentType)
    }

    private val playerListener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            triggerResolution(videoSize.width, videoSize.height)
        }

        override fun onPlayerError(ex: Media3PlaybackException) {
            if (ex.errorCode == Media3PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                videoPlayer.seekToDefaultPosition()
                videoPlayer.prepare()
            } else if (ex.errorCode == Media3PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED) {
                val uri = videoPlayer.currentMediaItem?.localConfiguration?.uri
                if (uri != null) {
                    if (contentTypeAttempts[C.CONTENT_TYPE_HLS] != true) {
                        prepare(uri, C.CONTENT_TYPE_HLS)
                    } else if (contentTypeAttempts[C.CONTENT_TYPE_OTHER] != true) {
                        prepare(uri, C.CONTENT_TYPE_OTHER)
                    } else if (contentTypeAttempts[C.CONTENT_TYPE_OTHER] != true) {
                        prepare(uri, C.CONTENT_TYPE_OTHER)
                    } else {
                        triggerError(PlaybackException.UNSUPPORTED_TYPE)
                    }
                }
            } else {
                triggerError(
                    PlaybackException(ex.errorCodeName, ex.errorCode)
                )
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_BUFFERING) {
                triggerError(null)
                triggerBuffering(true)
            } else if (playbackState == Player.STATE_READY) {
                triggerReady()

                updatePositionJob?.cancel()
                lastPositionSampleMs = null
                positionSampleCount = 0
                consecutiveSlowPositionSamples = 0
                updatePositionJob = coroutineScope.launch {
                    triggerCurrentPosition(-1)
                    while (true) {
                        val currentPositionMs = videoPlayer.currentPosition
                        val bufferedPositionMs = videoPlayer.bufferedPosition
                        val lastSampleMs = lastPositionSampleMs
                        val positionDeltaMs = lastSampleMs?.let { currentPositionMs - it }

                        if (
                            videoPlayer.isPlaying &&
                            videoPlayer.playbackState == Player.STATE_READY &&
                            positionDeltaMs != null &&
                            positionDeltaMs < 500
                        ) {
                            consecutiveSlowPositionSamples += 1
                        } else {
                            consecutiveSlowPositionSamples = 0
                        }

                        if (
                            positionSampleCount < 30 ||
                            !videoPlayer.isPlaying ||
                            videoPlayer.playbackState != Player.STATE_READY ||
                            positionDeltaMs == null ||
                            positionDeltaMs < 500 ||
                            positionDeltaMs > 1500
                        ) {
                            log.w(
                                "播放诊断: " +
                                    "state=${playbackStateName(videoPlayer.playbackState)} " +
                                    "isPlaying=${videoPlayer.isPlaying} " +
                                    "positionMs=$currentPositionMs " +
                                    "bufferedPositionMs=$bufferedPositionMs " +
                                    "bufferAheadMs=${(bufferedPositionMs - currentPositionMs).coerceAtLeast(0)} " +
                                    "positionDeltaMs=${positionDeltaMs ?: "NA"} " +
                                    "playbackSuppressionReason=${videoPlayer.playbackSuppressionReason}"
                            )
                        }

                        if (consecutiveSlowPositionSamples >= 2) {
                            log.w(
                                "检测到连续播放推进缓慢: " +
                                    "samples=$consecutiveSlowPositionSamples " +
                                    "positionMs=$currentPositionMs " +
                                    "bufferedPositionMs=$bufferedPositionMs"
                            )
                        }

                        triggerCurrentPosition(currentPositionMs)
                        lastPositionSampleMs = currentPositionMs
                        positionSampleCount += 1
                        delay(1000)
                    }
                }
            } else {
                lastPositionSampleMs = null
                positionSampleCount = 0
                consecutiveSlowPositionSamples = 0
            }

            if (playbackState != Player.STATE_BUFFERING) {
                triggerBuffering(false)
            }
        }
    }

    private val metadataListener = @UnstableApi object : AnalyticsListener {
        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            metadata = metadata.copy(
                videoMimeType = format.sampleMimeType ?: "",
                videoWidth = format.width,
                videoHeight = format.height,
                videoColor = format.colorInfo?.toLogString() ?: "",
                videoFrameRate = format.frameRate,
                videoBitrate = format.bitrate,
            )
            triggerMetadata(metadata)
            maybeRetryWithPlatformPreferredRenderers()
        }

        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            metadata = metadata.copy(videoDecoder = decoderName)
            triggerMetadata(metadata)
            maybeRetryWithPlatformPreferredRenderers()
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            metadata = metadata.copy(
                audioMimeType = format.sampleMimeType ?: "",
                audioChannels = format.channelCount,
                audioSampleRate = format.sampleRate,
            )
            triggerMetadata(metadata)
            maybeRetryWithPlatformPreferredRenderers()
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            metadata = metadata.copy(audioDecoder = decoderName)
            triggerMetadata(metadata)
            maybeRetryWithPlatformPreferredRenderers()
        }

        override fun onDroppedVideoFrames(
            eventTime: AnalyticsListener.EventTime,
            droppedFrames: Int,
            elapsedMs: Long,
        ) {
            log.w(
                "视频丢帧: " +
                    "droppedFrames=$droppedFrames " +
                    "elapsedMs=$elapsedMs " +
                    "currentPlaybackPositionMs=${eventTime.currentPlaybackPositionMs} " +
                    "totalBufferedDurationMs=${eventTime.totalBufferedDurationMs}"
            )
        }

        override fun onAudioUnderrun(
            eventTime: AnalyticsListener.EventTime,
            bufferSize: Int,
            bufferSizeMs: Long,
            elapsedSinceLastFeedMs: Long,
        ) {
            log.w(
                "音频供给不足: " +
                    "bufferSize=$bufferSize " +
                    "bufferSizeMs=$bufferSizeMs " +
                    "elapsedSinceLastFeedMs=$elapsedSinceLastFeedMs " +
                    "currentPlaybackPositionMs=${eventTime.currentPlaybackPositionMs}"
            )
        }

        override fun onVideoFrameProcessingOffset(
            eventTime: AnalyticsListener.EventTime,
            totalProcessingOffsetUs: Long,
            frameCount: Int,
        ) {
            if (frameCount <= 0) return

            val averageProcessingOffsetUs = totalProcessingOffsetUs / frameCount
            if (averageProcessingOffsetUs < -20_000 || averageProcessingOffsetUs > 50_000) {
                log.w(
                    "视频帧处理偏移异常: " +
                        "averageOffsetUs=$averageProcessingOffsetUs " +
                        "frameCount=$frameCount " +
                        "currentPlaybackPositionMs=${eventTime.currentPlaybackPositionMs}"
                )
            }
        }
    }

    private val eventLogger = EventLogger()

    private fun playbackStateName(playbackState: Int): String {
        return when (playbackState) {
            Player.STATE_IDLE -> "IDLE"
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"
            Player.STATE_ENDED -> "ENDED"
            else -> "UNKNOWN($playbackState)"
        }
    }

    override fun initialize() {
        super.initialize()
        attachPlayer(videoPlayer)
    }

    override fun release() {
        detachPlayer(videoPlayer)
        videoPlayer.release()
        super.release()
    }

    @UnstableApi
    override fun prepare(url: String) {
        contentTypeAttempts.clear()
        hasRetriedWithPlatformPreferredRenderers = false
        prepare(Uri.parse(url))
    }

    override fun play() {
        videoPlayer.play()
    }

    override fun pause() {
        videoPlayer.pause()
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView) {
        videoSurfaceView = surfaceView
        videoPlayer.setVideoSurfaceView(surfaceView)
    }
}
