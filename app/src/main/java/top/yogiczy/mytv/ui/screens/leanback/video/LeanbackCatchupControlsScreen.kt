package top.yogiczy.mytv.ui.screens.leanback.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LeanbackCatchupControlsScreen(
    modifier: Modifier = Modifier,
    isPlayingProvider: () -> Boolean = { true },
    currentPositionProvider: () -> Long = { -1L },
    durationProvider: () -> Long = { -1L },
    playbackSpeedProvider: () -> Float = { 1f },
    seekLevelProvider: () -> Int = { 0 },
) {
    val isPlaying = isPlayingProvider()
    val currentPosition = currentPositionProvider()
    val duration = durationProvider()
    val playbackSpeed = playbackSpeedProvider()
    val seekLevel = seekLevelProvider()

    val hasValidDuration = duration > 0 && currentPosition >= 0
    val progress = if (hasValidDuration) {
        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val remaining = if (hasValidDuration) duration - currentPosition else -1L

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 28.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 第一行：状态 + 时间信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 播放/暂停图标
                Text(
                    text = if (isPlaying) "▶" else "⏸",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )

                // 已播时间
                if (hasValidDuration) {
                    Text(
                        text = formatDuration(currentPosition),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                    )
                }

                // 进度条
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.25f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(6.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }

                // 剩余时间
                if (hasValidDuration) {
                    Text(
                        text = "-${formatDuration(remaining)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.75f),
                    )

                    Text(
                        text = "/ ${formatDuration(duration)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                }

                // 倍速标记
                if (playbackSpeed != 1f) {
                    Text(
                        text = "${formatSpeed(playbackSpeed)}x",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // 当前档位提示（快进/快退按下时显示）
                if (seekLevel > 0) {
                    Text(
                        text = "± ${seekLevel * 10}s",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // 第二行：操作提示
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "OK 暂停/播放  ◀▶ 快退/快进(10/20/30s)  ↑↓ 倍速(1x/2x/3x)  返回 退出回看",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms < 0) return "--:--"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun formatSpeed(speed: Float): String {
    return if (speed == speed.toLong().toFloat()) speed.toLong().toString()
    else "%.1f".format(speed)
}
