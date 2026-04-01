package top.yogiczy.mytv.ui.screens.leanback.main.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yogiczy.mytv.AppGlobal
import top.yogiczy.mytv.data.entities.EpgList
import top.yogiczy.mytv.data.entities.EpgList.Companion.currentProgrammes
import top.yogiczy.mytv.data.entities.IptvGroupList
import top.yogiczy.mytv.data.entities.IptvGroupList.Companion.iptvIdx
import top.yogiczy.mytv.data.entities.IptvGroupList.Companion.iptvList
import top.yogiczy.mytv.ui.screens.leanback.classicpanel.LeanbackClassicPanelScreen
import top.yogiczy.mytv.ui.screens.leanback.components.LeanbackVisible
import top.yogiczy.mytv.ui.screens.leanback.monitor.LeanbackMonitorScreen
import top.yogiczy.mytv.ui.screens.leanback.panel.LeanbackPanelChannelNoSelectScreen
import top.yogiczy.mytv.ui.screens.leanback.panel.LeanbackPanelDateTimeScreen
import top.yogiczy.mytv.ui.screens.leanback.panel.LeanbackPanelScreen
import top.yogiczy.mytv.ui.screens.leanback.panel.LeanbackPanelTempScreen
import top.yogiczy.mytv.ui.screens.leanback.panel.rememberLeanbackPanelChannelNoSelectState
import top.yogiczy.mytv.ui.screens.leanback.quickpanel.LeanbackQuickPanelScreen
import top.yogiczy.mytv.ui.screens.leanback.settings.LeanbackSettingsScreen
import top.yogiczy.mytv.ui.screens.leanback.settings.LeanbackSettingsViewModel
import top.yogiczy.mytv.ui.screens.leanback.toast.LeanbackToastState
import top.yogiczy.mytv.ui.screens.leanback.update.LeanbackUpdateScreen
import top.yogiczy.mytv.ui.screens.leanback.video.LeanbackCatchupControlsScreen
import top.yogiczy.mytv.ui.screens.leanback.video.LeanbackVideoScreen
import top.yogiczy.mytv.ui.screens.leanback.video.rememberLeanbackVideoPlayerState
import top.yogiczy.mytv.ui.utils.SP
import top.yogiczy.mytv.ui.utils.handleLeanbackDragGestures
import top.yogiczy.mytv.ui.utils.handleLeanbackKeyEvents

@Composable
fun LeanbackMainContent(
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit = {},
    iptvGroupList: IptvGroupList = IptvGroupList(),
    epgList: EpgList = EpgList(),
    settingsViewModel: LeanbackSettingsViewModel = viewModel(),
) {
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()

    val videoPlayerState = rememberLeanbackVideoPlayerState(
        defaultAspectRatioProvider = {
            when (settingsViewModel.videoPlayerAspectRatio) {
                SP.VideoPlayerAspectRatio.ORIGINAL -> null
                SP.VideoPlayerAspectRatio.SIXTEEN_NINE -> 16f / 9f
                SP.VideoPlayerAspectRatio.FOUR_THREE -> 4f / 3f
                SP.VideoPlayerAspectRatio.AUTO -> {
                    configuration.screenHeightDp.toFloat() / configuration.screenWidthDp.toFloat()
                }
            }
        }
    )
    val mainContentState = rememberLeanbackMainContentState(
        videoPlayerState = videoPlayerState,
        iptvGroupList = iptvGroupList,
    )
    val panelChannelNoSelectState = rememberLeanbackPanelChannelNoSelectState(
        onChannelNoConfirm = {
            val channelNo = it.toInt() - 1

            if (channelNo in iptvGroupList.iptvList.indices) {
                mainContentState.changeCurrentIptv(iptvGroupList.iptvList[channelNo])
            }
        }
    )

    // 回看连续快进/快退档位（1次=10s, 2次=20s, 3次=30s）
    var catchupSeekLevel by remember { mutableIntStateOf(0) }
    var catchupSeekResetPending by remember { mutableStateOf(false) }

    // 回看控制菜单显示/隐藏（默认隐藏，操作后显示，20s 无操作自动隐藏）
    var catchupControlsVisible by remember { mutableStateOf(false) }
    // 每次操作更新此时间戳，LaunchedEffect 监听到变化后重新计时 20s
    var catchupLastActionMs by remember { mutableLongStateOf(0L) }

    // 当退出回看时，隐藏控制菜单
    LaunchedEffect(mainContentState.isCatchupPlaying) {
        if (!mainContentState.isCatchupPlaying) {
            catchupControlsVisible = false
            catchupLastActionMs = 0L
        }
    }

    // 20 秒无操作后自动隐藏菜单
    LaunchedEffect(catchupLastActionMs) {
        if (catchupLastActionMs > 0L) {
            catchupControlsVisible = true
            delay(20_000L)
            catchupControlsVisible = false
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // 防止切换到其他界面时焦点丢失
        // TODO 换一个更好的解决方案
        while (true) {
            if (!mainContentState.isPanelVisible
                && !mainContentState.isSettingsVisible
                && !mainContentState.isQuickPanelVisible
            ) {
                focusRequester.requestFocus()
            }
            delay(100)
        }
    }

    LeanbackBackPressHandledArea(
        modifier = modifier,
        onBackPressed = {
            if (mainContentState.isCatchupPlaying) mainContentState.stopCatchup()
            else if (mainContentState.isPanelVisible) mainContentState.isPanelVisible = false
            else if (mainContentState.isSettingsVisible) mainContentState.isSettingsVisible = false
            else if (mainContentState.isQuickPanelVisible) mainContentState.isQuickPanelVisible =
                false
            else onBackPressed()
        },
    ) {
        LeanbackVideoScreen(
            state = videoPlayerState,
            showMetadataProvider = { settingsViewModel.debugShowVideoPlayerMetadata },
            modifier = Modifier
                .focusRequester(focusRequester)
                .focusable()
                .handleLeanbackKeyEvents(
                    onUp = {
                        if (mainContentState.isCatchupPlaying) {
                            // 回看模式：上键加速（1x → 2x → 3x → 1x 循环）
                            videoPlayerState.changePlaybackSpeed(
                                when (videoPlayerState.playbackSpeed) {
                                    1f -> 2f
                                    2f -> 3f
                                    else -> 1f
                                }
                            )
                            catchupLastActionMs = System.currentTimeMillis()
                        } else {
                            if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentIptvToNext()
                            else mainContentState.changeCurrentIptvToPrev()
                        }
                    },
                    onDown = {
                        if (mainContentState.isCatchupPlaying) {
                            // 回看模式：下键减速（3x → 2x → 1x）
                            videoPlayerState.changePlaybackSpeed(
                                when (videoPlayerState.playbackSpeed) {
                                    3f -> 2f
                                    2f -> 1f
                                    else -> 1f
                                }
                            )
                            catchupLastActionMs = System.currentTimeMillis()
                        } else {
                            if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentIptvToPrev()
                            else mainContentState.changeCurrentIptvToNext()
                        }
                    },
                    onLeft = {
                        if (mainContentState.isCatchupPlaying) {
                            // 回看模式：连续按左键依次快退 10s/20s/30s
                            if (catchupSeekResetPending) {
                                catchupSeekLevel = minOf(catchupSeekLevel + 1, 3)
                            } else {
                                catchupSeekLevel = 1
                                catchupSeekResetPending = true
                            }
                            val seekAmount = catchupSeekLevel * 10_000L
                            mainContentState.catchupSeekBy(-seekAmount)
                            catchupLastActionMs = System.currentTimeMillis()
                            coroutineScope.launch {
                                delay(1200)
                                catchupSeekResetPending = false
                                catchupSeekLevel = 0
                            }
                        } else if (mainContentState.currentIptv.urlList.size > 1) {
                            mainContentState.changeCurrentIptv(
                                iptv = mainContentState.currentIptv,
                                urlIdx = mainContentState.currentIptvUrlIdx - 1,
                            )
                        }
                    },
                    onRight = {
                        if (mainContentState.isCatchupPlaying) {
                            // 回看模式：连续按右键依次快进 10s/20s/30s
                            if (catchupSeekResetPending) {
                                catchupSeekLevel = minOf(catchupSeekLevel + 1, 3)
                            } else {
                                catchupSeekLevel = 1
                                catchupSeekResetPending = true
                            }
                            val seekAmount = catchupSeekLevel * 10_000L
                            mainContentState.catchupSeekBy(seekAmount)
                            catchupLastActionMs = System.currentTimeMillis()
                            coroutineScope.launch {
                                delay(1200)
                                catchupSeekResetPending = false
                                catchupSeekLevel = 0
                            }
                        } else if (mainContentState.currentIptv.urlList.size > 1) {
                            mainContentState.changeCurrentIptv(
                                iptv = mainContentState.currentIptv,
                                urlIdx = mainContentState.currentIptvUrlIdx + 1,
                            )
                        }
                    },
                    onSelect = {
                        if (mainContentState.isCatchupPlaying) {
                            // 回看模式：OK 键暂停/播放，同时呼出菜单
                            videoPlayerState.togglePause()
                            catchupLastActionMs = System.currentTimeMillis()
                        } else {
                            mainContentState.isPanelVisible = true
                        }
                    },
                    onLongSelect = { mainContentState.isQuickPanelVisible = true },
                    onSettings = {
                        if (mainContentState.isCatchupPlaying) {
                            // 回看模式：设置键呼出/切换菜单显示
                            catchupLastActionMs = System.currentTimeMillis()
                        } else {
                            mainContentState.isQuickPanelVisible = true
                        }
                    },
                    onNumber = {
                        if (settingsViewModel.iptvChannelNoSelectEnable) {
                            panelChannelNoSelectState.input(it)
                        }
                    },
                    onLongDown = { mainContentState.isQuickPanelVisible = true },
                )
                .handleLeanbackDragGestures(
                    onSwipeDown = {
                        if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentIptvToNext()
                        else mainContentState.changeCurrentIptvToPrev()
                    },
                    onSwipeUp = {
                        if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentIptvToPrev()
                        else mainContentState.changeCurrentIptvToNext()
                    },
                    onSwipeRight = {
                        if (mainContentState.currentIptv.urlList.size > 1) {
                            mainContentState.changeCurrentIptv(
                                iptv = mainContentState.currentIptv,
                                urlIdx = mainContentState.currentIptvUrlIdx - 1,
                            )
                        }
                    },
                    onSwipeLeft = {
                        if (mainContentState.currentIptv.urlList.size > 1) {
                            mainContentState.changeCurrentIptv(
                                iptv = mainContentState.currentIptv,
                                urlIdx = mainContentState.currentIptvUrlIdx + 1,
                            )
                        }
                    },
                ),
        )

        CompositionLocalProvider(
            LocalDensity provides Density(
                density = LocalDensity.current.density * settingsViewModel.uiDensityScaleRatio,
                fontScale = LocalDensity.current.fontScale * settingsViewModel.uiFontScaleRatio,
            )
        ) {
            LeanbackVisible({
                !mainContentState.isTempPanelVisible
                        && !mainContentState.isSettingsVisible
                        && !mainContentState.isPanelVisible
                        && !mainContentState.isQuickPanelVisible
                        && panelChannelNoSelectState.channelNo.isEmpty()
            }) {
                LeanbackPanelDateTimeScreen(
                    showModeProvider = { settingsViewModel.uiTimeShowMode }
                )
            }

            LeanbackPanelChannelNoSelectScreen(
                channelNoProvider = { panelChannelNoSelectState.channelNo }
            )

            LeanbackVisible({ mainContentState.isCatchupPlaying && catchupControlsVisible }) {
                LeanbackCatchupControlsScreen(
                    isPlayingProvider = { videoPlayerState.isPlaying },
                    currentPositionProvider = { mainContentState.catchupOffsetMs + videoPlayerState.currentPosition.coerceAtLeast(0L) },
                    durationProvider = { mainContentState.catchupDurationMs },
                    playbackSpeedProvider = { videoPlayerState.playbackSpeed },
                    seekLevelProvider = { catchupSeekLevel },
                )
            }

            LeanbackVisible({
                mainContentState.isTempPanelVisible
                        && !mainContentState.isCatchupPlaying
                        && !mainContentState.isSettingsVisible
                        && !mainContentState.isPanelVisible
                        && !mainContentState.isQuickPanelVisible
                        && panelChannelNoSelectState.channelNo.isEmpty()
            }) {
                LeanbackPanelTempScreen(
                    channelNoProvider = { iptvGroupList.iptvIdx(mainContentState.currentIptv) + 1 },
                    currentIptvProvider = { mainContentState.currentIptv },
                    currentIptvUrlIdxProvider = { mainContentState.currentIptvUrlIdx },
                    currentProgrammesProvider = { epgList.currentProgrammes(mainContentState.currentIptv) },
                    showProgrammeProgressProvider = { settingsViewModel.uiShowEpgProgrammeProgress },
                )
            }

            LeanbackVisible({ !settingsViewModel.uiUseClassicPanelScreen && mainContentState.isPanelVisible }) {
                LeanbackPanelScreen(
                    iptvGroupListProvider = { iptvGroupList },
                    epgListProvider = { epgList },
                    currentIptvProvider = { mainContentState.currentIptv },
                    currentIptvUrlIdxProvider = { mainContentState.currentIptvUrlIdx },
                    videoPlayerMetadataProvider = { videoPlayerState.metadata },
                    showProgrammeProgressProvider = { settingsViewModel.uiShowEpgProgrammeProgress },
                    onIptvSelected = { mainContentState.changeCurrentIptv(it) },
                    onIptvFavoriteToggle = {
                        if (!settingsViewModel.iptvChannelFavoriteEnable) return@LeanbackPanelScreen

                        if (settingsViewModel.iptvChannelFavoriteList.contains(it.channelName)) {
                            settingsViewModel.iptvChannelFavoriteList -= it.channelName
                            LeanbackToastState.I.showToast("取消收藏: ${it.channelName}")
                        } else {
                            settingsViewModel.iptvChannelFavoriteList += it.channelName
                            LeanbackToastState.I.showToast("已收藏: ${it.channelName}")
                        }
                    },
                    iptvFavoriteListProvider = { settingsViewModel.iptvChannelFavoriteList.toImmutableList() },
                    iptvFavoriteListVisibleProvider = { settingsViewModel.iptvChannelFavoriteListVisible },
                    onIptvFavoriteListVisibleChange = {
                        settingsViewModel.iptvChannelFavoriteListVisible = it
                    },
                    onClose = { mainContentState.isPanelVisible = false },
                    onCatchupPlay = { mainContentState.playCatchup(it) },
                )
            }

            LeanbackVisible({ settingsViewModel.uiUseClassicPanelScreen && mainContentState.isPanelVisible }) {
                LeanbackClassicPanelScreen(
                    iptvGroupListProvider = { iptvGroupList },
                    epgListProvider = { epgList },
                    currentIptvProvider = { mainContentState.currentIptv },
                    showProgrammeProgressProvider = { settingsViewModel.uiShowEpgProgrammeProgress },
                    onIptvSelected = { mainContentState.changeCurrentIptv(it) },
                    onIptvFavoriteToggle = {
                        if (!settingsViewModel.iptvChannelFavoriteEnable) return@LeanbackClassicPanelScreen

                        if (settingsViewModel.iptvChannelFavoriteList.contains(it.channelName)) {
                            settingsViewModel.iptvChannelFavoriteList -= it.channelName
                            LeanbackToastState.I.showToast("取消收藏: ${it.channelName}")
                        } else {
                            settingsViewModel.iptvChannelFavoriteList += it.channelName
                            LeanbackToastState.I.showToast("已收藏: ${it.channelName}")
                        }
                    },
                    iptvFavoriteListProvider = { settingsViewModel.iptvChannelFavoriteList.toImmutableList() },
                    iptvFavoriteListVisibleProvider = { settingsViewModel.iptvChannelFavoriteListVisible },
                    onIptvFavoriteListVisibleChange = {
                        settingsViewModel.iptvChannelFavoriteListVisible = it
                    },
                    onClose = { mainContentState.isPanelVisible = false },
                    iptvFavoriteEnableProvider = { settingsViewModel.iptvChannelFavoriteEnable },
                    onCatchupPlay = { mainContentState.playCatchup(it) },
                )
            }
        }

        LeanbackVisible({ mainContentState.isQuickPanelVisible && !mainContentState.isSettingsVisible }) {
            LeanbackQuickPanelScreen(
                currentIptvProvider = { mainContentState.currentIptv },
                currentIptvUrlIdxProvider = { mainContentState.currentIptvUrlIdx },
                currentProgrammesProvider = { epgList.currentProgrammes(mainContentState.currentIptv) },
                currentIptvChannelNoProvider = {
                    (iptvGroupList.iptvIdx(mainContentState.currentIptv) + 1).toString()
                        .padStart(2, '0')
                },
                videoPlayerMetadataProvider = { videoPlayerState.metadata },
                videoPlayerAspectRatioProvider = { videoPlayerState.aspectRatio },
                onChangeVideoPlayerAspectRatio = { videoPlayerState.aspectRatio = it },
                onIptvUrlIdxChange = {
                    mainContentState.changeCurrentIptv(
                        iptv = mainContentState.currentIptv,
                        urlIdx = it,
                    )
                },
                onClearCache = {
                    settingsViewModel.iptvPlayableHostList = emptySet()
                    coroutineScope.launch {
                        AppGlobal.cacheDir.deleteRecursively()
                    }
                    LeanbackToastState.I.showToast("缓存已清除，请重启应用")
                },
                onMoreSettings = { mainContentState.isSettingsVisible = true },
                onClose = { mainContentState.isQuickPanelVisible = false },
            )
        }

        LeanbackVisible({ mainContentState.isSettingsVisible }) {
            LeanbackSettingsScreen()
        }

        LeanbackVisible({ settingsViewModel.debugShowFps }) {
            LeanbackMonitorScreen()
        }

        LeanbackUpdateScreen()
    }
}

@Composable
fun LeanbackBackPressHandledArea(
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) = Box(
    modifier = Modifier
        .onPreviewKeyEvent {
            if (it.key == Key.Back && it.type == KeyEventType.KeyUp) {
                onBackPressed()
                true
            } else {
                false
            }
        }
        .then(modifier),
    content = content,
)