package top.yogiczy.mytv.ui.screens.leanback.video.player

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import java.net.URI

internal data class Media3ProgressivePlaybackProfile(
    val mediaItemMimeType: String? = null,
    val useDedicatedTsExtractorFactory: Boolean = false,
    val tsExtractorMode: Int = TsExtractor.MODE_SINGLE_PMT,
    val tsExtractorFlags: Int = 0,
    val tsTimestampSearchBytes: Int = TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES,
    val continueLoadingCheckIntervalBytes: Int =
        ProgressiveMediaSource.DEFAULT_LOADING_CHECK_INTERVAL_BYTES,
)

internal object Media3ProgressivePlaybackProfilePolicy {
    const val HTTP_TS_CONTINUE_LOADING_CHECK_INTERVAL_BYTES = 256 * 1024
    const val HTTP_TS_TIMESTAMP_SEARCH_BYTES = TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES * 8

    private val transportStreamExtensions = setOf(".ts", ".mts", ".m2ts")
    private val transportStreamPathMarkers = setOf("/rtp/", "/udp/", "/mpegts/", "/ts/")
    private val transportStreamQueryHints = setOf("format=ts", "output=ts", "type=ts")

    fun progressiveProfileFor(
        url: String,
        resolvedContentType: Int,
    ): Media3ProgressivePlaybackProfile {
        if (resolvedContentType != C.CONTENT_TYPE_OTHER) {
            return Media3ProgressivePlaybackProfile()
        }

        if (!isLikelyHttpTransportStream(url)) {
            return Media3ProgressivePlaybackProfile()
        }

        return Media3ProgressivePlaybackProfile(
            mediaItemMimeType = MimeTypes.VIDEO_MP2T,
            useDedicatedTsExtractorFactory = true,
            tsExtractorMode = TsExtractor.MODE_SINGLE_PMT,
            tsExtractorFlags = DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM,
            tsTimestampSearchBytes = HTTP_TS_TIMESTAMP_SEARCH_BYTES,
            continueLoadingCheckIntervalBytes = HTTP_TS_CONTINUE_LOADING_CHECK_INTERVAL_BYTES,
        )
    }

    private fun isLikelyHttpTransportStream(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false

        val normalizedPath = (uri.path ?: "").lowercase()
        val normalizedQuery = (uri.rawQuery ?: "").lowercase()

        return transportStreamExtensions.any(normalizedPath::endsWith) ||
            transportStreamPathMarkers.any(normalizedPath::contains) ||
            transportStreamQueryHints.any(normalizedQuery::contains)
    }
}
