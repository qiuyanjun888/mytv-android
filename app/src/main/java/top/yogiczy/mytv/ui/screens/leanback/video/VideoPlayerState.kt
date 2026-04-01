package top.yogiczy.mytv.ui.screens.leanback.video

import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import top.yogiczy.mytv.ui.screens.leanback.video.player.LeanbackMedia3VideoPlayer
import top.yogiczy.mytv.ui.screens.leanback.video.player.LeanbackVideoPlayer

/**
 * 播放器状态
 */
@Stable
class LeanbackVideoPlayerState(
    private val instance: LeanbackVideoPlayer,
    private val defaultAspectRatioProvider: () -> Float? = { null },
) {
    /** 视频宽高比 */
    var aspectRatio by mutableFloatStateOf(16f / 9f)

    /** 错误 */
    var error by mutableStateOf<String?>(null)

    /** 元数据 */
    var metadata by mutableStateOf(LeanbackVideoPlayer.Metadata())

    /** 是否正在播放 */
    var isPlaying by mutableStateOf(false)

    /** 当前播放位置（ms），-1 表示未知 */
    var currentPosition by mutableLongStateOf(-1L)

    /** 总时长（ms），-1 表示未知（直播流） */
    var duration by mutableLongStateOf(-1L)

    /** 播放速度 */
    var playbackSpeed by mutableFloatStateOf(1f)

    fun prepare(url: String) {
        error = null
        currentPosition = -1L
        duration = -1L
        playbackSpeed = 1f
        instance.setPlaybackSpeed(1f)
        instance.prepare(url)
    }

    fun play() {
        instance.play()
    }

    fun pause() {
        instance.pause()
    }

    fun togglePause() {
        if (isPlaying) instance.pause() else instance.play()
    }

    fun seekTo(positionMs: Long) {
        instance.seekTo(positionMs.coerceAtLeast(0))
    }

    fun changePlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        instance.setPlaybackSpeed(speed)
    }

    fun setVideoSurfaceView(surfaceView: SurfaceView) {
        instance.setVideoSurfaceView(surfaceView)
    }

    private val onReadyListeners = mutableListOf<() -> Unit>()
    private val onErrorListeners = mutableListOf<() -> Unit>()
    private val onCutoffListeners = mutableListOf<() -> Unit>()

    fun onReady(listener: () -> Unit) {
        onReadyListeners.add(listener)
    }

    fun onError(listener: () -> Unit) {
        onErrorListeners.add(listener)
    }

    fun onCutoff(listener: () -> Unit) {
        onCutoffListeners.add(listener)
    }

    fun initialize() {
        instance.initialize()
        instance.onResolution { width, height ->
            val defaultAspectRatio = defaultAspectRatioProvider()

            if (defaultAspectRatio == null) {
                if (width > 0 && height > 0) aspectRatio = width.toFloat() / height
            } else {
                aspectRatio = defaultAspectRatio
            }
        }
        instance.onError { ex ->
            error = if (ex != null) "${ex.errorCodeName}(${ex.errorCode})"
            else null

            if (error != null) onErrorListeners.forEach { it.invoke() }

        }
        instance.onReady { onReadyListeners.forEach { it.invoke() } }
        instance.onBuffering { if (it) error = null }
        instance.onPrepared { }
        instance.onMetadata { metadata = it }
        instance.onCutoff { onCutoffListeners.forEach { it.invoke() } }
        instance.onCurrentPosition { positionMs ->
            currentPosition = positionMs
            duration = instance.getDuration()
        }
        instance.onIsPlayingChanged { playing ->
            isPlaying = playing
        }
    }

    fun release() {
        onReadyListeners.clear()
        onErrorListeners.clear()
        instance.release()
    }
}

@Composable
fun rememberLeanbackVideoPlayerState(
    defaultAspectRatioProvider: () -> Float? = { null },
): LeanbackVideoPlayerState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val state = remember {
        LeanbackVideoPlayerState(
            LeanbackMedia3VideoPlayer(context, coroutineScope),
            defaultAspectRatioProvider = defaultAspectRatioProvider,
        )
    }

    DisposableEffect(Unit) {
        state.initialize()

        onDispose {
            state.release()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state.play()
            } else if (event == Lifecycle.Event.ON_STOP) {
                state.pause()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return state
}
