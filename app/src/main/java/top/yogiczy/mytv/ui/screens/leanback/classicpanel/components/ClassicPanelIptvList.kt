package top.yogiczy.mytv.ui.screens.leanback.classicpanel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyListState
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.ListItemDefaults
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.flow.distinctUntilChanged
import top.yogiczy.mytv.data.entities.EpgList
import top.yogiczy.mytv.data.entities.EpgList.Companion.currentProgrammes
import top.yogiczy.mytv.data.entities.EpgProgramme.Companion.progress
import top.yogiczy.mytv.data.entities.EpgProgrammeCurrent
import top.yogiczy.mytv.data.entities.Iptv
import top.yogiczy.mytv.data.entities.IptvGroup
import top.yogiczy.mytv.data.entities.IptvList
import top.yogiczy.mytv.ui.theme.LeanbackTheme
import top.yogiczy.mytv.ui.utils.handleLeanbackKeyEvents
import kotlin.math.max

@Composable
fun LeanbackClassicPanelIptvList(
    modifier: Modifier = Modifier,
    iptvGroupProvider: () -> IptvGroup = { IptvGroup() },
    iptvListProvider: () -> IptvList = { IptvList() },
    epgListProvider: () -> EpgList = { EpgList() },
    initialIptvProvider: () -> Iptv = { Iptv() },
    onIptvSelected: (Iptv) -> Unit = {},
    onIptvFavoriteToggle: (Iptv) -> Unit = {},
    onIptvFocused: (Iptv, FocusRequester) -> Unit = { _, _ -> },
    showProgrammeProgressProvider: () -> Boolean = { false },
    isFavoriteListProvider: () -> Boolean = { false },
    onUserAction: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val iptvList = iptvListProvider()
    val initialIptv = initialIptvProvider()

    var hasFocused by rememberSaveable { mutableStateOf(!iptvList.contains(initialIptv)) }
    val itemFocusRequesterList = remember(iptvList) {
        List(iptvList.size) { FocusRequester() }
    }
    var focusedIptv by remember(iptvList) { mutableStateOf(initialIptv) }

    LaunchedEffect(iptvList) {
        if (iptvList.isNotEmpty()) {
            if (hasFocused) {
                onIptvFocused(iptvList[0], itemFocusRequesterList[0])
            } else {
                onIptvFocused(
                    initialIptv,
                    itemFocusRequesterList[max(0, iptvList.indexOf(initialIptv))],
                )
            }
        }
    }

    val listState = remember(iptvGroupProvider()) {
        TvLazyListState(
            if (hasFocused) 0
            else max(0, iptvList.indexOf(initialIptv) - 2)
        )
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { _ -> onUserAction() }
    }

    TvLazyColumn(
        state = listState,
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = modifier
            .fillMaxHeight()
            .width(380.dp)
            .background(MaterialTheme.colorScheme.background.copy(0.4f)),
    ) {
        itemsIndexed(iptvList, key = { _, iptv -> iptv.hashCode() }) { index, iptv ->
            val isSelected by remember { derivedStateOf { iptv == focusedIptv } }
            val initialFocused by remember {
                derivedStateOf { !hasFocused && iptv == initialIptv }
            }

            LeanbackClassicPanelIptvItem(
                modifier = Modifier.fillParentMaxHeight(1f / 6f),
                iptvProvider = { iptv },
                epgProgrammeCurrentProvider = { epgListProvider().currentProgrammes(iptv) },
                focusRequesterProvider = { itemFocusRequesterList[index] },
                isSelectedProvider = { isSelected },
                initialFocusedProvider = { initialFocused },
                onInitialFocused = { hasFocused = true },
                onFocused = {
                    focusedIptv = iptv
                    onIptvFocused(iptv, itemFocusRequesterList[index])
                },
                onSelected = { onIptvSelected(iptv) },
                onFavoriteToggle = {
                    if (isFavoriteListProvider()) {
                        if (iptvList.size == 1) {
                            focusManager.moveFocus(FocusDirection.Left)
                        } else if (iptvList.first() == iptv) {
                            focusManager.moveFocus(FocusDirection.Down)
                        } else if (iptvList.last() == iptv) {
                            focusManager.moveFocus(FocusDirection.Up)
                        } else {
                            focusManager.moveFocus(FocusDirection.Down)
                        }
                    }
                    onIptvFavoriteToggle(iptv)
                },
                showProgrammeProgressProvider = showProgrammeProgressProvider,
            )
        }
    }
}

@Composable
private fun LeanbackClassicPanelIptvItem(
    modifier: Modifier = Modifier,
    iptvProvider: () -> Iptv = { Iptv() },
    epgProgrammeCurrentProvider: () -> EpgProgrammeCurrent? = { null },
    focusRequesterProvider: () -> FocusRequester = { FocusRequester() },
    isSelectedProvider: () -> Boolean = { false },
    initialFocusedProvider: () -> Boolean = { false },
    onInitialFocused: () -> Unit = {},
    onFocused: () -> Unit = {},
    onSelected: () -> Unit = {},
    onFavoriteToggle: () -> Unit = {},
    showProgrammeProgressProvider: () -> Boolean = { false },
) {
    val iptv = iptvProvider()
    val focusRequester = focusRequesterProvider()
    val currentProgramme = epgProgrammeCurrentProvider()?.now

    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (initialFocusedProvider()) {
            onInitialFocused()
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = modifier.clip(ListItemDefaults.shape().shape),
    ) {
        androidx.tv.material3.ListItem(
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        isFocused = it.isFocused || it.hasFocus

                        if (isFocused) {
                            onFocused()
                        }
                    }
                    .handleLeanbackKeyEvents(
                        key = iptv.hashCode(),
                        onSelect = {
                            if (isFocused) onSelected()
                            else focusRequester.requestFocus()
                        },
                        onLongSelect = {
                            if (isFocused) onFavoriteToggle()
                            else focusRequester.requestFocus()
                        },
                    ),
                colors = ListItemDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = 0.5f
                    ),
                ),
                selected = isSelectedProvider(),
                onClick = { },
                headlineContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // 台标 Logo（始终透明，不参与高亮）
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (iptv.logo.isNotEmpty()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(iptv.logo)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = iptv.name,
                                    modifier = Modifier.size(72.dp),
                                    contentScale = ContentScale.Fit,
                                )
                            } else {
                                Text(
                                    text = iptv.name.take(1),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                        }

                        // 频道名和当前节目（仅此区域高亮）
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isFocused) MaterialTheme.colorScheme.onBackground
                                    else Color.Transparent
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = iptv.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Medium,
                                fontSize = 20.sp,
                                color = if (isFocused) MaterialTheme.colorScheme.background
                                else MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = currentProgramme?.title ?: "无节目",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.alpha(0.8f),
                                color = if (isFocused) MaterialTheme.colorScheme.background
                                else MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                },
            )

            if (showProgrammeProgressProvider() && currentProgramme != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(currentProgramme.progress())
                        .height(3.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        ),
                )
            }
        }
}

@Preview
@Composable
private fun LeanbackClassicPanelIptvListPreview() {
    LeanbackTheme {
        LeanbackClassicPanelIptvList(
            modifier = Modifier.padding(20.dp),
            iptvListProvider = { IptvList.EXAMPLE },
        )
    }
}