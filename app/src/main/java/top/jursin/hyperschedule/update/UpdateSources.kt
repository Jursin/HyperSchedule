package top.jursin.hyperschedule.update

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import top.jursin.hyperschedule.data.remote.AppHttp
import top.jursin.hyperschedule.data.remote.AppUserAgent
import top.jursin.hyperschedule.data.remote.proxiedUrl
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.Request

data class UpdateSource(
    val host: String,
    val prefix: String,
    val builtIn: Boolean,
) {
    val title: String get() = if (prefix.isEmpty()) "$host（直连）" else host

    val key: String get() = prefix.removePrefix("https://")

    companion object {
        val Direct = UpdateSource(host = "github.com", prefix = "", builtIn = true)
        val BuiltIns: List<UpdateSource> =
            listOf(Direct) +
                listOf("ghfast.top", "gh.dpik.top", "gh-proxy.net").map {
                    UpdateSource(host = it, prefix = proxyPrefix(it), builtIn = true)
                }
    }
}

fun sourcesOf(custom: List<String>): List<UpdateSource> {
    val builtInHosts = UpdateSource.BuiltIns.mapTo(mutableSetOf()) { it.host }
    val mirrors =
        custom
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && it !in builtInHosts }
            .distinct()
            .map { UpdateSource(host = it, prefix = proxyPrefix(it), builtIn = false) }
    return UpdateSource.BuiltIns + mirrors
}

private val DomainPattern = Regex("^([a-z0-9]([a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,}$")

fun mirrorHost(input: String): String? =
    input
        .trim()
        .lowercase()
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')
        .takeIf { DomainPattern.matches(it) }

fun proxyPrefix(key: String): String = if (key.isEmpty()) "" else "https://$key"

sealed interface SourceSpeed {
    data object Untested : SourceSpeed
    data object Testing : SourceSpeed
    data class Reachable(val bytesPerSecond: Long) : SourceSpeed
    data object Timeout : SourceSpeed
}

private const val SpeedTestUrl = "https://github.com/microsoft/vscode/archive/refs/heads/main.zip"
private const val SpeedSampleBytes = 1024 * 1024
private const val SpeedSampleMs = 5000L

/** 测速结果按前缀缓存，退出页面时清空 */
object UpdateSpeed {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val results = mutableStateMapOf<String, SourceSpeed>()

    var testing by mutableStateOf(false)
        private set

    // 每次清空换个代次，上一轮迟到的结果直接丢掉
    private var generation = 0

    private val client =
        AppHttp.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()

    fun speedOf(source: UpdateSource): SourceSpeed = results[source.prefix] ?: SourceSpeed.Untested

    /** 并发测全部源，测速期间再次点击直接忽略 */
    fun testAll(sources: List<UpdateSource>) {
        if (testing || sources.isEmpty()) return
        testing = true
        val run = generation
        scope.launch {
            try {
                sources.forEach { results[it.prefix] = SourceSpeed.Testing }
                coroutineScope {
                    sources
                        .map { source ->
                            async {
                                val speed = measure(source)
                                if (run == generation) results[source.prefix] = speed
                            }
                        }.awaitAll()
                }
            } finally {
                if (run == generation) testing = false
            }
        }
    }

    /** 退出页面时清掉，下次进来重新测 */
    fun clear() {
        generation++
        testing = false
        results.clear()
    }

    /** 排队等响应头，然后按时间窗口读正文算吞吐，首包延迟不计入速度 */
    private fun measure(source: UpdateSource): SourceSpeed {
        val request =
            Request.Builder()
                .url(proxiedUrl(SpeedTestUrl, source.prefix))
                .header("User-Agent", AppUserAgent)
                .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return SourceSpeed.Timeout
                val body = response.body.byteStream()
                val buffer = ByteArray(16 * 1024)
                val start = SystemClock.elapsedRealtime()
                var read = 0L
                while (read < SpeedSampleBytes) {
                    val count = body.read(buffer)
                    if (count <= 0) break
                    read += count
                    if (SystemClock.elapsedRealtime() - start >= SpeedSampleMs) break
                }
                val elapsed = (SystemClock.elapsedRealtime() - start).coerceAtLeast(1L)
                if (read <= 0) SourceSpeed.Timeout else SourceSpeed.Reachable(read * 1000 / elapsed)
            }
        } catch (_: Exception) {
            // 连不上、超时、被重置都归成超时
            SourceSpeed.Timeout
        }
    }
}
