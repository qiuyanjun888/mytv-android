package top.yogiczy.mytv.data.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ConstantsTest {
    @Test
    fun defaultIptvAndEpgUrls_matchExpectedLocalServer() {
        assertEquals(
            "http://192.168.8.8:9000/iptv/sh/test.m3u",
            Constants.IPTV_SOURCE_URL,
        )
        assertEquals(
            "http://192.168.8.8:9000/iptv/sh/tel-epg.xml",
            Constants.EPG_XML_URL,
        )
    }
}
