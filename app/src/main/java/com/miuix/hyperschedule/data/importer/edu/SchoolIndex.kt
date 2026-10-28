package com.miuix.hyperschedule.data.importer.edu

/** 教务学校索引，对应拾光仓库的 school_index.proto */
data class SchoolIndex(
    val protocolVersion: Int = 0,
    val versionId: String = "",
    val schools: List<School> = emptyList(),
)

data class School(
    val id: String = "",
    val name: String = "",
    val initial: String = "",
    val resourceFolder: String = "",
    val adapters: List<Adapter> = emptyList(),
)

data class Adapter(
    val id: String = "",
    val name: String = "",
    val category: Int = 0,
    val scriptPath: String = "",
    val importUrl: String? = null,
    val description: String = "",
    val maintainer: String = "",
) {
    companion object {
        /** 通用工具，非具体学校的教务系统 */
        const val TOOL = 1
        const val BACHELOR = 2
        const val POSTGRADUATE = 3
    }
}

/** 手写 protobuf 解析，不引入 protobuf 运行时 */
object SchoolIndexParser {
    private const val MAX_FIELD_BYTES = 8 * 1024 * 1024

    fun parse(data: ByteArray): SchoolIndex {
        var protocolVersion = 0
        var versionId = ""
        val schools = mutableListOf<School>()
        val r = Reader(data)
        while (r.hasMore) {
            val tag = r.varint().toInt()
            when (tag shr 3) {
                1 -> protocolVersion = r.varint().toInt()
                2 -> versionId = r.string()
                3 -> schools += readSchool(r.message())
                else -> r.skip(tag and 0x7)
            }
        }
        return SchoolIndex(protocolVersion, versionId, schools)
    }

    private fun readSchool(r: Reader): School {
        var id = ""
        var name = ""
        var initial = ""
        var folder = ""
        val adapters = mutableListOf<Adapter>()
        while (r.hasMore) {
            val tag = r.varint().toInt()
            when (tag shr 3) {
                1 -> id = r.string()
                2 -> name = r.string()
                3 -> initial = r.string()
                4 -> folder = r.string()
                5 -> adapters += readAdapter(r.message())
                else -> r.skip(tag and 0x7)
            }
        }
        return School(id, name, initial, folder, adapters)
    }

    private fun readAdapter(r: Reader): Adapter {
        var id = ""
        var name = ""
        var category = 0
        var scriptPath = ""
        var importUrl: String? = null
        var description = ""
        var maintainer = ""
        while (r.hasMore) {
            val tag = r.varint().toInt()
            when (tag shr 3) {
                1 -> id = r.string()
                2 -> name = r.string()
                3 -> category = r.varint().toInt()
                4 -> scriptPath = r.string()
                5 -> importUrl = r.string()
                6 -> description = r.string()
                7 -> maintainer = r.string()
                else -> r.skip(tag and 0x7)
            }
        }
        return Adapter(id, name, category, scriptPath, importUrl, description, maintainer)
    }

    /** 下标游标，嵌套消息只裁剪边界不复制字节 */
    private class Reader(
        private val data: ByteArray,
        private var pos: Int = 0,
        private val end: Int = data.size,
    ) {
        val hasMore: Boolean get() = pos < end

        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                require(pos < end) { "索引数据不完整" }
                val byte = data[pos++].toInt()
                result = result or ((byte.toLong() and 0x7F) shl shift)
                if (byte and 0x80 == 0) break
                shift += 7
                require(shift <= 63) { "索引数据损坏" }
            }
            return result
        }

        fun string(): String = bytes(varint().toInt()).decodeToString()

        fun bytes(length: Int): ByteArray {
            checkLength(length)
            require(pos + length <= end) { "索引数据不完整" }
            return data.copyOfRange(pos, pos + length).also { pos += length }
        }

        fun message(): Reader {
            val length = varint().toInt()
            checkLength(length)
            require(pos + length <= end) { "索引数据不完整" }
            return Reader(data, pos, pos + length).also { pos += length }
        }

        fun skip(wireType: Int) {
            when (wireType) {
                0 -> varint()
                1 -> skipBytes(8)
                2 -> skipBytes(varint().toInt())
                5 -> skipBytes(4)
                else -> throw IllegalArgumentException("未知的 wire type $wireType")
            }
        }

        private fun skipBytes(length: Int) {
            checkLength(length)
            require(pos + length <= end) { "索引数据不完整" }
            pos += length
        }

        private fun checkLength(length: Int) {
            require(length in 0..MAX_FIELD_BYTES) { "索引字段长度异常" }
        }
    }
}
