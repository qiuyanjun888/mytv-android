package top.yogiczy.mytv.ui.screens.leanback.main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yogiczy.mytv.data.entities.CatchupRequest
import top.yogiczy.mytv.data.entities.Iptv
import top.yogiczy.mytv.data.entities.IptvGroupList
import top.yogiczy.mytv.data.entities.IptvGroupList.Companion.iptvIdx
import top.yogiczy.mytv.data.entities.IptvGroupList.Companion.iptvList
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.ui.screens.leanback.video.LeanbackVideoPlayerState
import top.yogiczy.mytv.ui.screens.leanback.video.rememberLeanbackVideoPlayerState
import top.yogiczy.mytv.ui.utils.SP
import top.yogiczy.mytv.utils.Loggable
import kotlin.math.max

@Stable
class LeanbackMainContentState(
    private val coroutineScope: CoroutineScope,
    private val videoPlayerState: LeanbackVideoPlayerState,
    private val iptvGroupList: IptvGroupList,
) : Loggable() {
    private var _currentIptv by mutableStateOf(Iptv())
    val currentIptv get() = _currentIptv

    private var _currentIptvUrlIdx by mutableIntStateOf(0)
    val currentIptvUrlIdx get() = _currentIptvUrlIdx

    private var _isPanelVisible by mutableStateOf(false)
    var isPanelVisible
        get() = _isPanelVisible
        set(value) {
            _isPanelVisible = value
        }

    private var _isSettingsVisible by mutableStateOf(false)
    var isSettingsVisible
        get() = _isSettingsVisible
        set(value) {
            _isSettingsVisible = value
        }

    private var _isTempPanelVisible by mutableStateOf(false)
    var isTempPanelVisible
        get() = _isTempPanelVisible
        set(value) {
            _isTempPanelVisible = value
        }

    private var _isQuickPanelVisible by mutableStateOf(false)
    var isQuickPanelVisible
        get() = _isQuickPanelVisible
        set(value) {
            _isQuickPanelVisible = value
        }

    init {
        changeCurrentIptv(iptvGroupList.iptvList.getOrElse(SP.iptvLastIptvIdx) {
            iptvGroupList.firstOrNull()?.iptvList?.firstOrNull() ?: Iptv()
        })

        videoPlayerState.onReady {
            coroutineScope.launch {
                val name = _currentIptv.name
                val urlIdx = _currentIptvUrlIdx
                delay(Constants.UI_TEMP_PANEL_SCREEN_SHOW_DURATION)
                if (name == _currentIptv.name && urlIdx == _currentIptvUrlIdx) {
                    _isTempPanelVisible = false
                }
            }

            // 记忆可播放的域名
            SP.iptvPlayableHostList += getUrlHost(_currentIptv.urlList[_currentIptvUrlIdx])
        }

        videoPlayerState.onError {
            if (_isCatchupPlaying) {
                // 回看模式下忽略所有播放错误（seek 切流会产生瞬时错误，不应触发退出）
                // 用户通过按返回键手动退出回看
                return@onError
            }

            if (_currentIptvUrlIdx < _currentIptv.urlList.size - 1) {
                changeCurrentIptv(_currentIptv, _currentIptvUrlIdx + 1)
            }

            // 从记忆中删除不可播放的域名
            SP.iptvPlayableHostList -= getUrlHost(_currentIptv.urlList[_currentIptvUrlIdx])
        }

        videoPlayerState.onCutoff {
            // 回看模式下不触发切台（避免因缓冲而跳回直播）
            if (!_isCatchupPlaying) {
                changeCurrentIptv(_currentIptv, _currentIptvUrlIdx)
            }
        }
    }

    private fun getPrevIptv(): Iptv {
        val currentIndex = iptvGroupList.iptvIdx(_currentIptv)
        return iptvGroupList.iptvList.getOrElse(currentIndex - 1) {
            iptvGroupList.lastOrNull()?.iptvList?.lastOrNull() ?: Iptv()
        }
    }

    private fun getNextIptv(): Iptv {
        val currentIndex = iptvGroupList.iptvIdx(_currentIptv)
        return iptvGroupList.iptvList.getOrElse(currentIndex + 1) {
            iptvGroupList.firstOrNull()?.iptvList?.firstOrNull() ?: Iptv()
        }
    }

    fun changeCurrentIptv(iptv: Iptv, urlIdx: Int? = null) {
        _isPanelVisible = false

        if (iptv == _currentIptv && urlIdx == null) return

        if (iptv == _currentIptv && urlIdx != _currentIptvUrlIdx) {
            SP.iptvPlayableHostList -= getUrlHost(_currentIptv.urlList[_currentIptvUrlIdx])
        }

        _isTempPanelVisible = true

        _currentIptv = iptv
        SP.iptvLastIptvIdx = iptvGroupList.iptvIdx(_currentIptv)

        _currentIptvUrlIdx = if (urlIdx == null) {
            // 优先从记忆中选择可播放的域名
            max(0, _currentIptv.urlList.indexOfFirst {
                SP.iptvPlayableHostList.contains(getUrlHost(it))
            })
        } else {
            (urlIdx + _currentIptv.urlList.size) % _currentIptv.urlList.size
        }

        val url = iptv.urlList[_currentIptvUrlIdx]
        log.d("播放${iptv.name}（${_currentIptvUrlIdx + 1}/${_currentIptv.urlList.size}）: $url")

        videoPlayerState.prepare(url)
    }

    fun changeCurrentIptvToPrev() {
        changeCurrentIptv(getPrevIptv())
    }

    fun changeCurrentIptvToNext() {
        changeCurrentIptv(getNextIptv())
    }

    private var _isCatchupPlaying by mutableStateOf(false)
    val isCatchupPlaying get() = _isCatchupPlaying

    private var _catchupRequest: CatchupRequest? = null

    private var _catchupOffsetMs by mutableLongStateOf(0L)
    private var _catchupSeekJob: Job? = null

    /** 回看当前偏移（毫秒），用于 UI 进度条 */
    val catchupOffsetMs get() = _catchupOffsetMs

    /** 回看节目总时长（毫秒），用于 UI 进度条 */
    val catchupDurationMs: Long
        get() = _catchupRequest?.let { it.endAtMs - it.startAtMs } ?: 0L

    fun playCatchup(request: CatchupRequest) {
        _isPanelVisible = false
        _isTempPanelVisible = true
        _isCatchupPlaying = true
        _catchupRequest = request
        _catchupOffsetMs = 0L
        val url = request.buildUrl(0L)
        log.d("回看URL: $url")
        videoPlayerState.prepare(url)
    }

    /**
     * 回看快进/快退：通过偏移量重建时移 URL（适用于时移流不支持原生 seek 的情况）
     * [deltaMs] 为正表示快进，为负表示快退
     */
    fun catchupSeekBy(deltaMs: Long) {
        val request = _catchupRequest ?: return
        val maxOffset = request.endAtMs - request.startAtMs - 1000L
        val isSeekActive = _catchupSeekJob?.isActive == true
        val playerPos = videoPlayerState.currentPosition.coerceAtLeast(0L)

        _catchupOffsetMs = calculateSeekOffset(
            currentOffset = _catchupOffsetMs,
            deltaMs = deltaMs,
            playerPosition = playerPos,
            isSeekActive = isSeekActive,
            maxOffset = maxOffset,
        )

        // 防抖：取消之前的 seek 任务，避免连续快进时多次 prepare
        _catchupSeekJob?.cancel()
        _catchupSeekJob = coroutineScope.launch {
            delay(300)
            val url = request.buildUrl(_catchupOffsetMs)
            log.d("回看 seek: offset=${_catchupOffsetMs / 1000}s, url=$url")
            val savedSpeed = videoPlayerState.playbackSpeed
            videoPlayerState.prepare(url)
            if (savedSpeed != 1f) {
                videoPlayerState.changePlaybackSpeed(savedSpeed)
            }
        }
    }

    companion object {
        /**
         * 计算快进/快退后的新偏移量（纯函数，方便单元测试）
         *
         * @param currentOffset 当前已记录的偏移量
         * @param deltaMs 本次快进/快退的增量（正=快进，负=快退）
         * @param playerPosition 播放器当前已播放的位置
         * @param isSeekActive 是否有正在执行的 seek 操作（连续按键时为 true）
         * @param maxOffset 最大允许偏移量（节目总时长 - 1秒）
         */
        fun calculateSeekOffset(
            currentOffset: Long,
            deltaMs: Long,
            playerPosition: Long,
            isSeekActive: Boolean,
            maxOffset: Long,
        ): Long {
            var offset = currentOffset
            // 第一次按键时（非连续快进），将已播放时间同步到偏移量，避免快进后位置倒退
            if (!isSeekActive) {
                offset += playerPosition.coerceAtLeast(0L)
            }
            return (offset + deltaMs).coerceIn(0L, maxOffset)
        }
    }

    fun stopCatchup() {
        _catchupSeekJob?.cancel()
        _catchupSeekJob = null
        _isCatchupPlaying = false
        _isTempPanelVisible = false
        _catchupRequest = null
        _catchupOffsetMs = 0L
        videoPlayerState.changePlaybackSpeed(1f)
        val url = _currentIptv.urlList[_currentIptvUrlIdx]
        videoPlayerState.prepare(url)
    }
}

@Composable
fun rememberLeanbackMainContentState(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    videoPlayerState: LeanbackVideoPlayerState = rememberLeanbackVideoPlayerState(),
    iptvGroupList: IptvGroupList = IptvGroupList(),
) = remember {
    LeanbackMainContentState(
        coroutineScope = coroutineScope,
        videoPlayerState = videoPlayerState,
        iptvGroupList = iptvGroupList,
    )
}

private fun getUrlHost(url: String): String {
    return url.split("://").getOrElse(1) { "" }.split("/").firstOrNull() ?: url
}