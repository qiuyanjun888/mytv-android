package top.yogiczy.mytv.data.entities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CatchupRequest.buildUrl() 的单元测试
 *
 * 验证 URL 模板替换和偏移量边界约束
 */
class CatchupRequestTest {

    private val template = "http://example.com/catchup?start={utc:YmdHMS}&end={utcend:YmdHMS}"
    private val startMs = 1700000000000L  // 固定时间戳
    private val endMs = startMs + 3600_000L  // 1小时节目

    private fun makeRequest(
        urlTemplate: String = template,
        startAtMs: Long = startMs,
        endAtMs: Long = endMs,
    ) = CatchupRequest(urlTemplate, startAtMs, endAtMs)

    @Test
    fun `buildUrl 默认偏移量为零时使用节目开始时间`() {
        val request = makeRequest()
        val url = request.buildUrl(0L)
        // URL 中不应包含模板占位符
        assertTrue("URL should not contain template placeholders", !url.contains("{utc:"))
        assertTrue("URL should not contain template placeholders", !url.contains("{utcend:"))
    }

    @Test
    fun `buildUrl 偏移量被限制不低于开始时间`() {
        val request = makeRequest()
        // 负偏移应被 coerceIn 限制到 startAtMs
        val urlNeg = request.buildUrl(-10_000L)
        val urlZero = request.buildUrl(0L)
        // 负偏移和零偏移应该产生相同的 URL（都从 startAtMs 开始）
        assertEquals(urlNeg, urlZero)
    }

    @Test
    fun `buildUrl 偏移量被限制不超过结束时间减1秒`() {
        val request = makeRequest()
        val duration = endMs - startMs  // 3600_000
        // 超过 duration - 1000 的偏移应被限制
        val urlOver = request.buildUrl(duration + 10_000L)
        val urlMax = request.buildUrl(duration - 1000L)
        assertEquals(urlOver, urlMax)
    }

    @Test
    fun `buildUrl endAtMs 字段始终使用节目结束时间`() {
        val request = makeRequest()
        val url1 = request.buildUrl(0L)
        val url2 = request.buildUrl(1800_000L) // 半小时偏移

        // utcend 部分应相同（都是节目结束时间）
        // 提取 &end= 后面的值
        val endPart1 = url1.substringAfter("end=")
        val endPart2 = url2.substringAfter("end=")
        assertEquals(endPart1, endPart2)
    }

    @Test
    fun `buildUrl start 部分随偏移量变化`() {
        val request = makeRequest()
        val url1 = request.buildUrl(0L)
        val url2 = request.buildUrl(60_000L) // 1分钟偏移

        val startPart1 = url1.substringAfter("start=").substringBefore("&")
        val startPart2 = url2.substringAfter("start=").substringBefore("&")
        // 不同的偏移应产生不同的开始时间
        assertTrue("Different offsets should produce different start times",
            startPart1 != startPart2)
    }

    @Test
    fun `buildUrl 支持 UTC 大写占位符`() {
        val utcTemplate = "http://example.com/?s={UTC:YmdHMS}&e={UTCend:YmdHMS}"
        val request = makeRequest(urlTemplate = utcTemplate)
        val url = request.buildUrl(0L)
        assertTrue(!url.contains("{UTC:"))
        assertTrue(!url.contains("{UTCend:"))
    }

    @Test
    fun `buildUrl 极短节目不会崩溃`() {
        // 500ms 的节目（endAtMs - 1000 < startAtMs 的边界情况）
        val shortRequest = makeRequest(endAtMs = startMs + 500L)
        val url = shortRequest.buildUrl(0L)
        // 不应崩溃，且不包含占位符
        assertTrue(!url.contains("{utc:"))
    }

    @Test
    fun `buildUrl 无占位符的模板原样返回`() {
        val plainTemplate = "http://example.com/plain-stream.m3u8"
        val request = makeRequest(urlTemplate = plainTemplate)
        assertEquals(plainTemplate, request.buildUrl(0L))
        assertEquals(plainTemplate, request.buildUrl(1800_000L))
    }
}
