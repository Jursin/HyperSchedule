package top.jursin.hyperschedule

import android.app.Application
import top.jursin.hyperschedule.data.local.AppLog
import top.jursin.hyperschedule.data.local.SettingsStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

class HyperScheduleApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this, SettingsStore.get(this))
        graph = AppGraph(this)
    }
}

/** 后台组件（提醒、更新）投递提示，界面侧统一消费；界面不在时最多缓存 64 条 */
object AppMessages {
    private val channel = Channel<String>(Channel.BUFFERED)

    val messages: Flow<String> = channel.receiveAsFlow()

    fun post(text: String) {
        channel.trySend(text)
    }
}
