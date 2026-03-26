package top.yogiczy.mytv.ui.screens.leanback.video.player

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3ProgressivePlaybackProfilePolicyTest {
    @Test
    fun `http rtp transport stream should use dedicated live ts profile`() {
        val profile = Media3ProgressivePlaybackProfilePolicy.progressiveProfileFor(
            url = "http://192.168.8.8:5140/rtp/233.18.204.224:5140?fcc=124.75.25.213%3A7777",
            resolvedContentType = C.CONTENT_TYPE_OTHER,
        )

        assertEquals(MimeTypes.VIDEO_MP2T, profile.mediaItemMimeType)
        assertTrue(profile.useDedicatedTsExtractorFactory)
        assertEquals(TsExtractor.MODE_SINGLE_PMT, profile.tsExtractorMode)
        assertEquals(
            DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM,
            profile.tsExtractorFlags,
        )
        assertEquals(
            Media3ProgressivePlaybackProfilePolicy.HTTP_TS_TIMESTAMP_SEARCH_BYTES,
            profile.tsTimestampSearchBytes,
        )
        assertEquals(
            Media3ProgressivePlaybackProfilePolicy.HTTP_TS_CONTINUE_LOADING_CHECK_INTERVAL_BYTES,
            profile.continueLoadingCheckIntervalBytes,
        )
    }

    @Test
    fun `plain progressive http stream should keep default profile`() {
        val profile = Media3ProgressivePlaybackProfilePolicy.progressiveProfileFor(
            url = "http://192.168.8.8:9000/videos/demo.mp4",
            resolvedContentType = C.CONTENT_TYPE_OTHER,
        )

        assertNull(profile.mediaItemMimeType)
        assertFalse(profile.useDedicatedTsExtractorFactory)
        assertEquals(TsExtractor.MODE_SINGLE_PMT, profile.tsExtractorMode)
        assertEquals(0, profile.tsExtractorFlags)
        assertEquals(TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES, profile.tsTimestampSearchBytes)
        assertEquals(
            ProgressiveMediaSource.DEFAULT_LOADING_CHECK_INTERVAL_BYTES,
            profile.continueLoadingCheckIntervalBytes,
        )
    }
}
