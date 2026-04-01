package top.yogiczy.mytv.data.entities

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 回看请求，携带 URL 模板和节目时间范围，支持通过偏移量重建时移地址
 */
data class CatchupRequest(
    val urlTemplate: String,
    val startAtMs: Long,
    val endAtMs: Long,
) {
    /** 根据当前偏移量（毫秒）构建实际播放 URL */
    fun buildUrl(seekOffsetMs: Long = 0L): String {
        val effectiveStartMs = (startAtMs + seekOffsetMs)
            .coerceIn(startAtMs, (endAtMs - 1000L).coerceAtLeast(startAtMs))
        val localFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
        val utcFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return urlTemplate
            .replace("{utc:YmdHMS}", localFormat.format(Date(effectiveStartMs)))
            .replace("{utcend:YmdHMS}", localFormat.format(Date(endAtMs)))
            .replace("{UTC:YmdHMS}", utcFormat.format(Date(effectiveStartMs)))
            .replace("{UTCend:YmdHMS}", utcFormat.format(Date(endAtMs)))
    }
}

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
         * 构建回看请求对象（保留模板和时间范围，支持 seek 重建 URL）
         */
        fun EpgProgramme.toCatchupRequest(catchupSource: String): CatchupRequest =
            CatchupRequest(urlTemplate = catchupSource, startAtMs = startAt, endAtMs = endAt)

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