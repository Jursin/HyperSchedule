package top.jursin.hyperschedule.ui.state

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.LocalTime
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** 前台核对墙钟的间隔 */
private const val WallClockCheckMillis = 3_000L

/** 墙钟与单调钟的偏差超过它，说明时间被改过 */
private const val WallClockJumpMillis = 1_000L

@Composable
fun rememberSystemTimeChanges(): Int {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var version by remember { mutableIntStateOf(0) }
    val filter =
        remember {
            IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
        }

    DisposableEffect(context, lifecycleOwner, filter) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    version++
                }
            }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        // 后台期间广播不保证送达，回到前台补一次
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) version++
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            context.unregisterReceiver(receiver)
        }
    }

    // 墙钟兜底
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var lastWall = System.currentTimeMillis()
            var lastMono = SystemClock.elapsedRealtime()
            while (true) {
                delay(WallClockCheckMillis.milliseconds)
                val wall = System.currentTimeMillis()
                // elapsedRealtime 含休眠时间，息屏期间不会算成偏差
                val mono = SystemClock.elapsedRealtime()
                val drift = wall - lastWall - (mono - lastMono)
                lastWall = wall
                lastMono = mono
                if (abs(drift) > WallClockJumpMillis) version++
            }
        }
    }
    return version
}

internal fun millisToNextTick(stepSeconds: Int, now: LocalTime = LocalTime.now()): Long {
    val step = stepSeconds * 1_000L
    val elapsed = now.toSecondOfDay() * 1_000L + now.nano / 1_000_000L
    return step - elapsed % step
}
