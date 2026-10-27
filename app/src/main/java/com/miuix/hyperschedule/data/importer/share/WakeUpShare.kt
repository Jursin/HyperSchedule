package com.miuix.hyperschedule.data.importer.share

import com.miuix.hyperschedule.data.importer.CourseJson
import com.miuix.hyperschedule.data.importer.CourseTableConfigJson
import com.miuix.hyperschedule.data.importer.CourseTableJson
import com.miuix.hyperschedule.data.importer.TimeSlotJson
import java.security.SecureRandom
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object WakeUpShare {
    private const val HOST = "https://api.wakeup.fun"
    private const val ANTISPAM_PATH = "/pluto/app/antispam"
    private const val SHARE_PATH = "/share_schedule/getv2"
    private const val PACKAGE_NAME = "com.suda.yzune.wakeupschedule"
    private const val VERSION_NAME = "6.1.70"
    private const val VERSION_CODE = 450
    private const val CHANNEL = "100271a"
    private const val PUBLIC_TOKEN = "1_XPXQH3c5HRPtFHkSwi3sCCURmT25QfxM"
    private const val SIGNATURE_MD5 = "318c6d4f74655d4f032fb0466bcfdfbc"
    private const val MAGIC = "8&%d*"
    private const val SIGN_A_KEY = "@fG2SuLA"
    private const val KEY_SALT = "@#AIjd83#@6B"

    /** 设备信息固定，换设备不影响取回结果 */
    private const val ANDROID_ID = "0000000000000000"
    private const val NETWORK_TYPE = "wifi"

    private const val RAND_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private const val RANDOM_LENGTH = 10

    fun fetch(code: String): CourseTableJson {
        val cuid = WakeUpCrypto.cuid(ANDROID_ID)
        val adid = WakeUpCrypto.adid(ANDROID_ID)
        val commonParams = commonParams(cuid, adid)
        val headers =
            mapOf(
                "na__zyb_source__" to "wakeup",
                "zyb-cuid" to cuid,
                "zyb-adid" to adid,
            )

        val token = requestToken(cuid, commonParams, headers)
        val shareData = requestShareData(code, token, commonParams, headers)
        return parseShareData(shareData)
    }

    /** 第一步：DES 签名换 antispam token */
    private fun requestToken(
        cuid: String,
        commonParams: List<Pair<String, String>>,
        headers: Map<String, String>,
    ): String {
        val rand10 = randomText()
        // 明文格式：magic##rand10##签名 md5##cuid
        val plain = "$MAGIC##$rand10##$SIGNATURE_MD5##$cuid"
        val signA = WakeUpCrypto.nativeHexEncode(WakeUpCrypto.desEncrypt(plain.toByteArray(), SIGN_A_KEY.toByteArray()))
        val body = WakeUpCrypto.formEncode(listOf("data" to signA) + commonParams) + "&"
        val response = ShareHttp.postForm(HOST + ANTISPAM_PATH, body, headers)
        val signB =
            extractSignB(runCatching { ShareJson.parseToJsonElement(response) }.getOrNull())
                ?: throw IllegalStateException("WakeUp 服务没有返回签名数据")

        val decrypted =
            WakeUpCrypto.desDecrypt(WakeUpCrypto.nativeHexDecode(signB), "${rand10.take(5)}#G4".toByteArray())
        val text = decrypted.toString(Charsets.ISO_8859_1)
        if (text.length < 12 + RANDOM_LENGTH || !text.startsWith(rand10)) {
            throw IllegalStateException("WakeUp 服务的签名校验没有通过")
        }
        // 前 12 字节是随机串与其后的填充，紧跟的 10 位才是 token
        return text.substring(12, 12 + RANDOM_LENGTH)
    }

    /** 第二步：带签名的表单请求，返回解密后的课表原文 */
    private fun requestShareData(
        code: String,
        token: String,
        commonParams: List<Pair<String, String>>,
        headers: Map<String, String>,
    ): String {
        val rc4Key = WakeUpCrypto.rc4Key(token, KEY_SALT, VERSION_CODE)
        val encryptedKey =
            WakeUpCrypto.base64Encode(
                WakeUpCrypto.rc4("key=${WakeUpCrypto.androidQuote(code)}".toByteArray(), rc4Key.toByteArray())
            )
        val serverTime = System.currentTimeMillis() / 1000
        val timestamp = System.currentTimeMillis()
        val signSource =
            buildList {
                add("data=$encryptedKey")
                commonParams.forEach { (key, value) -> add("$key=$value") }
                add("nt=$NETWORK_TYPE")
                add("_t_=$serverTime")
                add("kakorrhaphiophobia=$timestamp")
            }.sorted().joinToString("")
        val sign = WakeUpCrypto.sign(WakeUpCrypto.base64Encode(signSource.toByteArray()), token, MAGIC)
        val body =
            "&" +
                WakeUpCrypto.formEncode(listOf("data" to encryptedKey) + commonParams + ("nt" to NETWORK_TYPE)) +
                "&sign=$sign&_t_=$serverTime&kakorrhaphiophobia=$timestamp"

        val response = ShareHttp.postForm(HOST + SHARE_PATH, body, headers)
        val root =
            runCatching { ShareJson.parseToJsonElement(response).jsonObject }
                .getOrElse { throw IllegalStateException("WakeUp 课表的返回格式无法识别") }
        val payload = root["data"].let { if (it is JsonObject) it["data"] else it }
        val cipherText = (payload as? JsonPrimitive)?.contentOrNull
        if (cipherText.isNullOrEmpty()) throw IllegalStateException("WakeUp 没有返回课表数据")

        val decrypted =
            runCatching {
                WakeUpCrypto.rc4(WakeUpCrypto.base64Decode(cipherText), rc4Key.toByteArray()).decodeToString()
            }.getOrElse { throw IllegalStateException("WakeUp 课表解密失败") }
        val shareData =
            runCatching {
                ShareJson.parseToJsonElement(decrypted).jsonObject["shareData"]?.jsonPrimitive?.contentOrNull
            }.getOrNull()
        return shareData?.takeIf { it.isNotBlank() } ?: throw IllegalStateException("分享口令无效或已过期")
    }

    /**
     * 课表原文由 5 段 JSON 组成（基础配置、节次时间、界面配置、课程、课程详情），
     * 用换行分隔，逐段解析。
     */
    private fun parseShareData(raw: String): CourseTableJson {
        val parts = raw.trim().lines()
        if (parts.size < 5) throw IllegalStateException("WakeUp 课表数据结构不完整")
        fun <T> decode(
            index: Int,
            deserializer: DeserializationStrategy<T>,
        ): T =
            runCatching { ShareJson.decodeFromString(deserializer, parts[index]) }
                .getOrElse { throw IllegalStateException("WakeUp 课表数据的第${index + 1}段无法解析") }

        val baseConfig = decode(0, BaseConfig.serializer())
        val rawSlots = decode(1, ListSerializer(RawSlot.serializer()))
        val uiConfig = decode(2, UiConfig.serializer())
        val rawCourses = decode(3, ListSerializer(RawCourse.serializer()))
        val details = decode(4, ListSerializer(RawDetail.serializer()))

        val totalWeeks = clampTotalWeeks(uiConfig.maxWeek)
        val courseNames =
            rawCourses.mapNotNull { course ->
                val id = course.id?.keyOrNull() ?: return@mapNotNull null
                id to course.courseName
            }.toMap()

        val courses =
            details.mapNotNull { detail ->
                val id = detail.id?.keyOrNull() ?: return@mapNotNull null
                val name = courseNames[id]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val customTime = detail.ownTime && !detail.startTime.isNullOrBlank() && !detail.endTime.isNullOrBlank()
                CourseJson(
                    name = name,
                    teacher = detail.teacher.orEmpty(),
                    position = detail.room.orEmpty(),
                    day = detail.day,
                    startSection = if (customTime) null else detail.startNode,
                    endSection = if (customTime) null else detail.startNode + detail.step - 1,
                    weeks = detail.weeks(totalWeeks),
                    isCustomTime = customTime,
                    customStartTime = normalizeTime(detail.startTime).takeIf { customTime },
                    customEndTime = normalizeTime(detail.endTime).takeIf { customTime },
                )
            }
        if (courses.isEmpty()) throw IllegalStateException("分享口令里没有课程数据")

        val validNodes = uiConfig.nodes.nodeSet()
        val slots =
            continuousSlots(
                rawSlots.filter { it.startTime != "00:00" && it.endTime != "00:00" }
                    .filter { validNodes == null || it.node in validNodes }
                    .mapNotNull { slot ->
                        val start = normalizeTime(slot.startTime) ?: return@mapNotNull null
                        val end = normalizeTime(slot.endTime) ?: return@mapNotNull null
                        TimeSlotJson(number = slot.node, startTime = start, endTime = end)
                    }
            )

        return CourseTableJson(
            courses = mergeCourses(courses),
            timeSlots = slots.ifEmpty { null },
            config =
                CourseTableConfigJson(
                    semesterStartDate = normalizeDate(uiConfig.startDate),
                    semesterTotalWeeks = totalWeeks,
                    defaultClassDuration = baseConfig.courseLen?.takeIf { it in 1..240 },
                ),
        )
    }

    private fun commonParams(
        cuid: String,
        adid: String,
    ): List<Pair<String, String>> =
        listOf(
            "area" to "",
            "screensize" to "1080x2400",
            "cuid" to cuid,
            "os" to "android",
            "city" to "",
            "abis" to "arm64-v8a",
            "channel" to CHANNEL,
            "appBit" to "64",
            "vc" to VERSION_CODE.toString(),
            "deviceId" to "",
            "token" to PUBLIC_TOKEN,
            "adid" to adid,
            "province" to "",
            "pkgName" to PACKAGE_NAME,
            "appId" to "wakeup",
            "download_type" to "1",
            "vcname" to VERSION_NAME,
            "sdk" to "35",
            "device" to "Pixel 7",
            "brand" to "google",
            "operatorid" to "",
        )

    /** antispam 的签名可能挂在 data.data / data / result.data 上，逐层找 */
    private fun extractSignB(element: JsonElement?): String? {
        val root = element as? JsonObject ?: return null
        val data = root["data"]
        val nested = if (data is JsonObject) data["data"] else data
        (nested as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { return it }
        val result = root["result"] as? JsonObject
        return (result?.get("data") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }
    }

    private fun randomText(): String {
        val random = SecureRandom()
        val bytes = ByteArray(RANDOM_LENGTH).also { random.nextBytes(it) }
        return bytes.joinToString("") { RAND_ALPHABET[(it.toInt() and 0xff) % RAND_ALPHABET.length].toString() }
    }
}

/** 课程 id 可能是数字也可能是字符串，统一按字符串比对 */
private fun JsonElement.keyOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }

/** uiConfig.nodes 可能是节次号数组，也可能只是个数量 */
private fun JsonElement?.nodeSet(): Set<Int>? =
    when (this) {
        is JsonArray -> mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull() }.toSet()
        is JsonPrimitive -> contentOrNull?.toIntOrNull()?.takeIf { it > 0 }?.let { count -> (1..count).toSet() }
        else -> null
    }

/** WakeUp 的 type：0 每周、1 单周、2 双周 */
private fun RawDetail.weeks(totalWeeks: Int): List<Int> {
    val start = startWeek.coerceAtLeast(1)
    val end = endWeek.coerceAtLeast(start)
    val weeks =
        (start..end).filter { week ->
            when (type) {
                1 -> week % 2 == 1
                2 -> week % 2 == 0
                else -> true
            }
        }
    // 周次字段缺失时按整学期处理，否则课程导入后永远不会显示
    return weeks.ifEmpty { (1..totalWeeks).toList() }
}

@Serializable
private data class BaseConfig(
    /** 单节课时长，导入后落到课表专属作息 */
    val courseLen: Int? = null,
)

@Serializable
private data class RawSlot(
    val node: Int = 0,
    val startTime: String = "",
    val endTime: String = "",
)

@Serializable
private data class UiConfig(
    val nodes: JsonElement? = null,
    val startDate: String? = null,
    val maxWeek: Int? = null,
)

@Serializable
private data class RawCourse(
    val id: JsonElement? = null,
    val courseName: String = "",
)

@Serializable
private data class RawDetail(
    val id: JsonElement? = null,
    val teacher: String? = null,
    val room: String? = null,
    val day: Int = 1,
    val startWeek: Int = 1,
    val endWeek: Int = 1,
    val type: Int = 0,
    val ownTime: Boolean = false,
    val startTime: String? = null,
    val endTime: String? = null,
    val startNode: Int = 1,
    val step: Int = 1,
)
