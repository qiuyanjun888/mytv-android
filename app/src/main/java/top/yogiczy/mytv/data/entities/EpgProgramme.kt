package top.yogiczy.mytv.data.entities

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 频道节目
 */
@Serializable
data class EpgProgramme(
    /**
     * 开始时间（时间戳）
     */
    val startAt: Long = 0,

    /**
     * 结束时间（时间戳）
     */
    val endAt: Long = 0,

    /**
     * 节目名称
     */
    val title: String = "",
) {
    companion object {
        /**
         * 是否正在直播
         */
        fun EpgProgramme.isLive() = System.currentTimeMillis() in startAt..<endAt

        /**
         * 节目进度
         */
        fun EpgProgramme.progress() =
            (System.currentTimeMillis() - startAt).toFloat() / (endAt - startAt)

        /**
         * 是否在回看有效期内（已结束且未超过回看天数）
         */
        fun EpgProgramme.isCatchupAvailable(catchupDays: Int): Boolean {
            if (catchupDays <= 0) return false
            val now = System.currentTimeMillis()
            val cutoffTime = now - catchupDays * 24L * 60 * 60 * 1000
            return endAt < now && startAt >= cutoffTime
        }

        /**
         * 构建回看地址，替换 {utc:YmdHMS} 和 {utcend:YmdHMS} 占位符
         */
        fun EpgProgramme.buildCatchupUrl(catchupSource: String): String {
            val localFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
            val utcFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            return catchupSource
                .replace("{utc:YmdHMS}", localFormat.format(Date(startAt)))
                .replace("{utcend:YmdHMS}", localFormat.format(Date(endAt)))
                .replace("{UTC:YmdHMS}", utcFormat.format(Date(startAt)))
                .replace("{UTCend:YmdHMS}", utcFormat.format(Date(endAt)))
        }
    }
}