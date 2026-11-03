package com.hyperschedule.app.data.backup

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** WebDAV 目录列表解析，只认备份文件名，href 可能是绝对地址或带转义，坏响应不能让列表崩掉 */
@RunWith(RobolectricTestRunner::class)
class WebDavBackupListTest {

    /** 一个 `<response>` 块，[length] 传 null 表示服务器没给 getcontentlength */
    private fun response(href: String, length: String?) =
        buildString {
            append("<d:response><d:href>").append(href).append("</d:href>")
            if (length != null) {
                append("<d:propstat><d:prop><d:getcontentlength>").append(length)
                append("</d:getcontentlength></d:prop></d:propstat>")
            }
            append("</d:response>")
        }

    private fun multistatus(vararg body: String) =
        """<?xml version="1.0" encoding="utf-8"?><d:multistatus xmlns:d="DAV:">""" +
            body.joinToString("") + "</d:multistatus>"

    @Test
    fun keepsOnlyBackupFilesNewestFirst() {
        val xml =
            multistatus(
                // 目录自身没有文件名，自然跳过
                response("/dav/HyperSchedule/", "0"),
                response("/dav/HyperSchedule/20261102_080000.json", "1024"),
                response("/dav/HyperSchedule/20261103_190000.json", "4096"),
                response("/dav/HyperSchedule/notes.txt", null),
            )

        val files = parseBackupList(xml)

        assertEquals(listOf("20261103_190000.json", "20261102_080000.json"), files.map { it.name })
        assertEquals(listOf(4096L, 1024L), files.map { it.size })
        assertEquals(LocalDateTime.of(2026, 11, 3, 19, 0, 0), files.first().time)
    }

    @Test
    fun decodesAbsoluteAndEscapedHrefs() {
        val xml =
            multistatus(
                response("https://dav.example.com/dav/%E5%A4%87%E4%BB%BD/20261103_190000.json", "10"),
            )

        // 取最后一段再解码，中文目录不影响文件名
        assertEquals(listOf("20261103_190000.json"), parseBackupList(xml).map { it.name })
    }

    @Test
    fun missingOrInvalidSizeFallsBackToZero() {
        val xml =
            multistatus(
                response("/d/20261103_190000.json", null),
                response("/d/20261102_080000.json", "unknown"),
            )

        assertTrue(parseBackupList(xml).all { it.size == 0L })
    }

    @Test
    fun tagsWithoutNamespacePrefixAreStillMatched() {
        val xml =
            """<multistatus xmlns="DAV:"><response><href>/d/20261103_190000.json</href>""" +
                """<propstat><prop><getcontentlength>7</getcontentlength></prop></propstat></response></multistatus>"""

        val file = parseBackupList(xml).single()
        assertEquals("20261103_190000.json", file.name)
        assertEquals(7L, file.size)
    }

    @Test
    fun brokenOrEmptyResponseYieldsEmptyList() {
        assertTrue(parseBackupList("<d:multistatus xmlns:d=\"DAV:\"><d:response>").isEmpty())
        assertTrue(parseBackupList("").isEmpty())
        assertTrue(parseBackupList("not xml at all").isEmpty())
    }

    @Test
    fun folderSegmentsEscapeSpacesAndKeepPlusLiteral() {
        // 路径里 + 是字面加号，空格要转成 %20
        assertEquals("a%20b", encodeSegment("a b"))
        assertEquals("a%2Bb", encodeSegment("a+b"))
        assertEquals("a+b", decodeSegment("a+b"))
        assertEquals("a b", decodeSegment("a%20b"))
    }
}
