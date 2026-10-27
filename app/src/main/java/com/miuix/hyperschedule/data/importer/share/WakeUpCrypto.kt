package com.miuix.hyperschedule.data.importer.share

import android.util.Base64
import java.net.URLEncoder
import java.security.MessageDigest

internal object WakeUpCrypto {
    /** MD5，小写十六进制 */
    fun md5Hex(text: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray())
        return digest.joinToString("") { hex2(it.toInt() and 0xff) }
    }

    fun desEncrypt(
        plain: ByteArray,
        key: ByteArray,
    ): ByteArray = desCipher(plain, key, decrypt = false)

    fun desDecrypt(
        cipher: ByteArray,
        key: ByteArray,
    ): ByteArray = desCipher(cipher, key, decrypt = true)

    /** 协议的十六进制：每个字节的两个半字节各自翻转位序 */
    fun nativeHexEncode(bytes: ByteArray): String =
        bytes.joinToString("") { byte ->
            val low = reverseNibble(byte.toInt() and 0x0f)
            val high = reverseNibble((byte.toInt() shr 4) and 0x0f)
            hex2(low) + hex2(high)
        }

    fun nativeHexDecode(text: String): ByteArray {
        // 每 4 个字符还原 1 个字节，尾部不足 4 位的丢弃
        val source = text.take(text.length / 4 * 4)
        val output = ByteArray(source.length / 4)
        for (index in source.indices step 4) {
            val low = reverseNibble(source[index + 1].digitToInt(16))
            val high = reverseNibble(source[index + 3].digitToInt(16))
            output[index / 4] = (low or (high shl 4)).toByte()
        }
        return output
    }

    fun rc4(
        data: ByteArray,
        key: ByteArray,
    ): ByteArray {
        val state = IntArray(256) { it }
        var swapIndex = 0
        for (index in 0 until 256) {
            swapIndex = (swapIndex + state[index] + (key[index % key.size].toInt() and 0xff)) and 0xff
            val tmp = state[index]
            state[index] = state[swapIndex]
            state[swapIndex] = tmp
        }
        val output = ByteArray(data.size)
        var left = 0
        var right = 0
        for (index in data.indices) {
            left = (left + 1) and 0xff
            right = (right + state[left]) and 0xff
            val tmp = state[left]
            state[left] = state[right]
            state[right] = tmp
            output[index] = (data[index].toInt() xor state[(state[left] + state[right]) and 0xff]).toByte()
        }
        return output
    }

    fun base64Encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun base64Decode(text: String): ByteArray = Base64.decode(text, Base64.DEFAULT)

    /** 与 Android 原生 `URLEncoder` + 少量差分编码对齐的表单转义 */
    fun androidQuote(value: String): String {
        val encoded =
            buildString {
                for (char in URLEncoder.encode(value, "UTF-8")) {
                    // Java 的 URLEncoder 不转义 *，而协议侧转义
                    if (char in "!'()*") {
                        append('%').append(char.code.toString(16).uppercase())
                    } else {
                        append(char)
                    }
                }
            }
        return encoded.replace("%7E", "~").replace("%20", "+")
    }

    fun formEncode(items: List<Pair<String, String?>>): String =
        items.joinToString("&") { (key, value) -> "$key=${androidQuote(value.orEmpty())}" }

    /** 设备唯一标识：固定 androidId 派生，用大写十六进制，保证多台设备行为一致 */
    fun cuid(androidId: String): String = md5Hex("com.baidu$androidId").uppercase() + "|0"

    fun adid(androidId: String): String {
        val prefix = md5Hex("alpha.beta$androidId")
        // 前后两半都是 64 位无符号数，用 ULong 才能原样异或
        val high = prefix.substring(0, 16).toULong(16)
        val low = prefix.substring(16).toULong(16)
        val folded = high xor low
        val checksum = ((folded shr 32) xor folded) and 0xffffffffuL
        return prefix + checksum.toString(16).padStart(8, '0')
    }

    /** 由 antispam 返回的 token 派生出 RC4 密钥 */
    fun rc4Key(
        token: String,
        keySalt: String,
        versionCode: Int,
    ): String {
        val salt = md5Hex(keySalt)
        val version = md5Hex(versionCode.toString())
        val raw = md5Hex("[$token]@")
        // md5 按 15/2/15 切开重排，首尾两段各自倒序
        val reversed = raw.substring(17).reversed() + raw.substring(15, 17) + raw.substring(0, 15).reversed()
        val chars = (salt + version + reversed).toCharArray()
        for (index in 0 until 3) {
            val right = chars.lastIndex - index
            val tmp = chars[index]
            chars[index] = chars[right]
            chars[right] = tmp
        }
        val joined = String(chars)
        // 接上自身 md5 后再做 60 次首尾交换
        val output = (joined + md5Hex(joined)).toCharArray()
        for (index in 0 until 60) {
            val right = output.lastIndex - index
            val tmp = output[index]
            output[index] = output[right]
            output[right] = tmp
        }
        return String(output)
    }

    fun sign(
        base64Params: String,
        token: String,
        magic: String,
    ): String = md5Hex("$magic[${md5Hex(token)}]@$base64Params")

    // DES 逐字移植自 WakeUp 客户端：置换表下标从 0 开始，与标准 DES 不同，不能换成 javax.crypto

    private fun desCipher(
        input: ByteArray,
        key: ByteArray,
        decrypt: Boolean,
    ): ByteArray {
        require(key.size == 8) { "WakeUp DES 密钥必须是 8 字节" }
        val data =
            if (decrypt) {
                require(input.size % 8 == 0) { "WakeUp DES 密文长度无效" }
                input
            } else {
                // 补满整块，块尾记录补了多少字节
                val paddedLength = (input.size and 7.inv()) + 8
                ByteArray(paddedLength).also {
                    input.copyInto(it)
                    it[paddedLength - 1] = (paddedLength - input.size).toByte()
                }
            }
        val subkeys = subkeys(key)
        val rounds = if (decrypt) subkeys.reversedArray() else subkeys
        val output = ByteArray(data.size)
        for (offset in data.indices step 8) {
            val block = data.copyOfRange(offset, offset + 8)
            desBlock(block, rounds).copyInto(output, offset)
        }
        if (!decrypt) return output
        val padLength = output.last().toInt() and 0xff
        require(padLength <= output.size) { "WakeUp DES 填充无效" }
        return output.copyOfRange(0, output.size - padLength)
    }

    private fun subkeys(key: ByteArray): Array<IntArray> {
        val bits = permute(bitsFromBytes(key), PC1)
        var left = bits.copyOfRange(0, 28)
        var right = bits.copyOfRange(28, 56)
        return Array(16) { round ->
            left = rotate(left, SHIFTS[round])
            right = rotate(right, SHIFTS[round])
            permute(left + right, PC2)
        }
    }

    private fun desBlock(
        block: ByteArray,
        subkeys: Array<IntArray>,
    ): ByteArray {
        val bits = permute(bitsFromBytes(block), IP)
        var left = bits.copyOfRange(0, 32)
        var right = bits.copyOfRange(32, 64)
        for (subkey in subkeys) {
            val mixed = feistel(right, subkey)
            val nextRight = IntArray(32) { index -> left[index] xor mixed[index] }
            left = right
            right = nextRight
        }
        return bytesFromBits(permute(right + left, FP))
    }

    private fun feistel(
        right: IntArray,
        subkey: IntArray,
    ): IntArray {
        val expanded = permute(right, E)
        val mixed = IntArray(expanded.size) { expanded[it] xor subkey[it] }
        val sboxed = IntArray(32)
        for (box in 0 until 8) {
            val offset = box * 6
            val row = mixed[offset] * 2 + mixed[offset + 5]
            val column =
                mixed[offset + 1] * 8 + mixed[offset + 2] * 4 + mixed[offset + 3] * 2 + mixed[offset + 4]
            val value = S_BOX[box][row][column]
            sboxed[box * 4] = (value shr 3) and 1
            sboxed[box * 4 + 1] = (value shr 2) and 1
            sboxed[box * 4 + 2] = (value shr 1) and 1
            sboxed[box * 4 + 3] = value and 1
        }
        return permute(sboxed, P)
    }

    private fun bitsFromBytes(bytes: ByteArray): IntArray =
        IntArray(bytes.size * 8) { index ->
            (bytes[index / 8].toInt() shr (index % 8)) and 1
        }

    private fun bytesFromBits(bits: IntArray): ByteArray =
        ByteArray(bits.size / 8) { index ->
            var value = 0
            for (bit in 0 until 8) value = value or ((bits[index * 8 + bit] and 1) shl bit)
            value.toByte()
        }

    private fun permute(
        bits: IntArray,
        table: IntArray,
    ): IntArray = IntArray(table.size) { bits[table[it]] }

    private fun rotate(
        source: IntArray,
        shift: Int,
    ): IntArray = IntArray(source.size) { source[(it + shift) % source.size] }

    private fun reverseNibble(value: Int): Int =
        ((value and 1) shl 3) or ((value and 2) shl 1) or ((value and 4) shr 1) or ((value and 8) shr 3)

    private fun hex2(value: Int): String = value.toString(16).padStart(2, '0')

    private val IP = intArrayOf(
        57, 49, 41, 33, 25, 17, 9, 1, 59, 51, 43, 35, 27, 19, 11, 3,
        61, 53, 45, 37, 29, 21, 13, 5, 63, 55, 47, 39, 31, 23, 15, 7,
        56, 48, 40, 32, 24, 16, 8, 0, 58, 50, 42, 34, 26, 18, 10, 2,
        60, 52, 44, 36, 28, 20, 12, 4, 62, 54, 46, 38, 30, 22, 14, 6,
    )
    private val FP = intArrayOf(
        39, 7, 47, 15, 55, 23, 63, 31, 38, 6, 46, 14, 54, 22, 62, 30,
        37, 5, 45, 13, 53, 21, 61, 29, 36, 4, 44, 12, 52, 20, 60, 28,
        35, 3, 43, 11, 51, 19, 59, 27, 34, 2, 42, 10, 50, 18, 58, 26,
        33, 1, 41, 9, 49, 17, 57, 25, 32, 0, 40, 8, 48, 16, 56, 24,
    )
    private val E = intArrayOf(
        31, 0, 1, 2, 3, 4, 3, 4, 5, 6, 7, 8, 7, 8, 9, 10, 11, 12, 11, 12, 13, 14, 15, 16,
        15, 16, 17, 18, 19, 20, 19, 20, 21, 22, 23, 24, 23, 24, 25, 26, 27, 28, 27, 28, 29, 30, 31, 0,
    )
    private val P = intArrayOf(
        15, 6, 19, 20, 28, 11, 27, 16, 0, 14, 22, 25, 4, 17, 30, 9,
        1, 7, 23, 13, 31, 26, 2, 8, 18, 12, 29, 5, 21, 10, 3, 24,
    )
    private val PC1 = intArrayOf(
        56, 48, 40, 32, 24, 16, 8, 0, 57, 49, 41, 33, 25, 17, 9, 1,
        58, 50, 42, 34, 26, 18, 10, 2, 59, 51, 43, 35, 62, 54, 46, 38,
        30, 22, 14, 6, 61, 53, 45, 37, 29, 21, 13, 5, 60, 52, 44, 36,
        28, 20, 12, 4, 27, 19, 11, 3,
    )
    private val PC2 = intArrayOf(
        13, 16, 10, 23, 0, 4, 2, 27, 14, 5, 20, 9, 22, 18, 11, 3,
        25, 7, 15, 6, 26, 19, 12, 1, 40, 51, 30, 36, 46, 54, 29, 39,
        50, 44, 32, 46, 43, 48, 38, 55, 33, 52, 45, 41, 49, 35, 28, 31,
    )
    private val SHIFTS = intArrayOf(1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1)
    private val S_BOX = arrayOf(
        arrayOf(
            intArrayOf(14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7),
            intArrayOf(0, 15, 7, 4, 14, 2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8),
            intArrayOf(4, 1, 14, 8, 13, 6, 2, 11, 15, 12, 9, 7, 3, 10, 5, 0),
            intArrayOf(15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13),
        ),
        arrayOf(
            intArrayOf(15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10),
            intArrayOf(3, 13, 4, 7, 15, 2, 8, 14, 12, 0, 1, 10, 6, 9, 11, 5),
            intArrayOf(0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12, 6, 9, 3, 2, 15),
            intArrayOf(13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9),
        ),
        arrayOf(
            intArrayOf(10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8),
            intArrayOf(13, 7, 0, 9, 3, 4, 6, 10, 2, 8, 5, 14, 12, 11, 15, 1),
            intArrayOf(13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2, 12, 5, 10, 14, 7),
            intArrayOf(1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12),
        ),
        arrayOf(
            intArrayOf(7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15),
            intArrayOf(13, 8, 11, 5, 6, 15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9),
            intArrayOf(10, 6, 9, 0, 12, 11, 7, 13, 15, 1, 3, 14, 5, 2, 8, 4),
            intArrayOf(3, 15, 0, 6, 10, 1, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14),
        ),
        arrayOf(
            intArrayOf(2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9),
            intArrayOf(14, 11, 2, 12, 4, 7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6),
            intArrayOf(4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12, 5, 6, 3, 0, 14),
            intArrayOf(11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3),
        ),
        arrayOf(
            intArrayOf(12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11),
            intArrayOf(10, 15, 4, 2, 7, 12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8),
            intArrayOf(9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4, 10, 1, 13, 11, 6),
            intArrayOf(4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13),
        ),
        arrayOf(
            intArrayOf(4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1),
            intArrayOf(13, 0, 11, 7, 4, 9, 1, 10, 14, 3, 5, 12, 2, 15, 8, 6),
            intArrayOf(1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6, 8, 0, 5, 9, 2),
            intArrayOf(6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12),
        ),
        arrayOf(
            intArrayOf(13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7),
            intArrayOf(1, 15, 13, 8, 10, 3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2),
            intArrayOf(7, 11, 4, 1, 9, 12, 14, 2, 0, 6, 10, 13, 15, 3, 5, 8),
            intArrayOf(2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11),
        ),
    )
}
