package com.miuix.hyperschedule.reminder

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Bundle
import com.miuix.hyperschedule.MainActivity
import com.miuix.hyperschedule.R
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 小米超级岛：把倒计时塞进 `miui.focus.param`，系统按这份 JSON 排版 */
internal object SuperIsland {

    private const val CHANNEL = "class_island"

    /** 运营场景标识：系统按它归组同一门课的多条岛通知 */
    private const val BUSINESS_TAG = "course_reminder"

    /** 岛的最长存活时间，秒；到点由系统自动收起，避免残留 */
    private const val ISLAND_TIMEOUT_SECONDS = 3600

    /** 澎湃 OS 自报是否有超级岛；`get(String)` 读不到属性，只能用 `getBoolean` */
    private const val KEY_ISLAND_FEATURE = "persist.sys.feature.island"

    /** 系统按 sequence 判新旧，单调递增即可 */
    private val sequence = AtomicLong(System.currentTimeMillis() / 1000)

    /** 系统属性只能靠隐藏 API 读，读不到就当作不支持 */
    @SuppressLint("PrivateApi")
    fun isSupported(): Boolean =
        runCatching {
            Class.forName("android.os.SystemProperties")
                .getMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
                .invoke(null, KEY_ISLAND_FEATURE, false) as? Boolean
        }.getOrNull() == true

    /**
     * 设备支持、且 Shizuku 授权在手，才能真的把岛发出去。
     * 没授权时发岛必被系统拦，直接让实时动态接管，省掉一次注定失败的尝试。
     */
    fun canPost(): Boolean = isSupported() && ShizukuAccess.hasPermission

    /** 课前：挂一个距上课的倒计时岛，收起态左课名右地点，提示行挂上课倒计时 */
    fun notifyPreClass(context: Context, course: DayCourse, now: LocalDateTime): Boolean =
        post(
            context = context,
            id = ReminderNotifier.PRE_CLASS_ID,
            params =
                params(
                    component = component(context),
                    course = course,
                    // 课前弹一次悬浮窗，课中不再弹
                    enableFloat = true,
                    hintContent = "即将上课",
                    timerTarget = course.start.toEpochMillis(),
                    nowMillis = now.toEpochMillis(),
                    collapsedRight = course.position,
                ),
            title = course.name,
            // 岛没被系统接住时通知栏里就是这一行，与普通通知保持同一套文案
            text = plainBodyOf(course, preClassHead(course)),
            ongoing = false,
        )

    /** 课中：换成距下课的倒计时岛，接替课前那条；收起态右侧说正在上课 */
    fun notifyInClass(context: Context, course: DayCourse): Boolean {
        val end = course.end?.toEpochMillis() ?: return false
        ReminderNotifier.cancel(context, ReminderNotifier.PRE_CLASS_ID)
        return post(
            context = context,
            id = ReminderNotifier.IN_CLASS_ID,
            params =
                params(
                    component = component(context),
                    course = course,
                    enableFloat = false,
                    hintContent = "距离下课",
                    timerTarget = end,
                    nowMillis = System.currentTimeMillis(),
                    collapsedRight = "正在上课",
                ),
            title = course.name,
            text = plainBodyOf(course, inClassHead(end)),
            ongoing = true,
        )
    }

    /** `intent:` URI 里的组件名必须写全，系统不会替相对类名补包名 */
    private fun component(context: Context): String =
        "${context.packageName}/${MainActivity::class.java.name}"

    private fun params(
        component: String,
        course: DayCourse,
        enableFloat: Boolean,
        hintContent: String,
        timerTarget: Long,
        nowMillis: Long,
        collapsedRight: String,
    ): String =
        buildJsonObject {
            put(
                "param_v2",
                buildJsonObject {
                    put("business", BUSINESS_TAG)
                    put("protocol", 1)
                    // 课中不反复弹悬浮窗，只在课前弹一次
                    if (!enableFloat) put("islandFirstFloat", true)
                    put("enableFloat", enableFloat)
                    put("updatable", true)
                    // 岛展开时四周呼吸光效，系统认这一项就亮
                    put("outEffectSrc", "outer_glow")
                    put("reopen", "reopen")
                    put("sequence", sequence.incrementAndGet())
                    put("aodTitle", course.name)
                    put("baseInfo", baseInfo(course.name, course.position))
                    put(
                        "picInfo",
                        buildJsonObject {
                            put("type", 1)
                            put("pic", "")
                        },
                    )
                    put("hintInfo", hintInfo(hintContent, timerTarget, nowMillis, course.teacher, dndAction(component)))
                    put(
                        "param_island",
                        buildJsonObject {
                            put("islandProperty", 1)
                            put("islandTimeout", ISLAND_TIMEOUT_SECONDS)
                            put(
                                "bigIslandArea",
                                buildJsonObject {
                                    put("templateNo", 2)
                                    put(
                                        "imageTextInfoLeft",
                                        buildJsonObject {
                                            put("type", 1)
                                            put("textInfo", textInfo(course.name))
                                        },
                                    )
                                    put("textInfo", textInfo(collapsedRight))
                                },
                            )
                            put(
                                "smallIslandArea",
                                buildJsonObject {
                                    put(
                                        "picInfo",
                                        buildJsonObject {
                                            put("type", 1)
                                            put("pic", "miui.focus.pic_small")
                                            put("picDark", "miui.focus.pic_small_dark")
                                        },
                                    )
                                },
                            )
                        },
                    )
                },
            )
        }.toString()

    /** 主信息：标题课名、正文地点 */
    private fun baseInfo(title: String, content: String) =
        buildJsonObject {
            put("type", 2)
            put("title", title)
            put("content", content)
            put("subTitle", "")
            put("extraTitle", "")
            put("specialTitle", "")
            put("subContent", "")
            put("picFunction", "")
            put("showDivider", true)
            put("showContentDivider", false)
            put("colorTitle", "#111111")
            put("colorTitleDark", "#ffffff")
            put("colorContent", "#333333")
            put("colorContentDark", "#cccccc")
        }

    /** 提示行：倒计时 + 右下角那栏放教师；没填教师就空着，不拿时间顶替 */
    private fun hintInfo(
        content: String,
        timerTarget: Long,
        nowMillis: Long,
        teacher: String,
        action: JsonObject,
    ) =
        buildJsonObject {
            put("type", 2)
            put("title", "")
            put("content", content)
            put(
                "timerInfo",
                buildJsonObject {
                    // -1 = 倒计时到 timerWhen
                    put("timerType", -1)
                    put("timerWhen", timerTarget)
                    put("timerTotal", 0L)
                    put("timerSystemCurrent", nowMillis)
                },
            )
            put("subContent", if (teacher.isBlank()) "" else "教师")
            put("subTitle", teacher)
            put("colorTitle", "#222222")
            put("colorTitleDark", "#eeeeee")
            put("colorContent", "#666666")
            put("colorContentDark", "#aaaaaa")
            put("colorSubTitle", "#222222")
            put("colorSubTitleDark", "#eeeeee")
            put("colorSubContent", "#666666")
            put("colorSubContentDark", "#aaaaaa")
            put("actionInfo", action)
        }

    private fun textInfo(title: String) =
        buildJsonObject {
            put("frontTitle", "")
            put("title", title)
            put("content", "")
            put("showHighlightColor", false)
            put("narrowFont", false)
        }

    /** 按钮组件：发广播落到 [ClassDndReceiver]，课前课中都是同一个勿扰开关 */
    private fun dndAction(appComponent: String) =
        buildJsonObject {
            put("actionTitle", "上课勿扰")
            // 2 = 发广播
            put("actionIntentType", 2)
            put(
                "actionIntent",
                "intent:#Intent;action=${ClassDndReceiver.ACTION_TOGGLE};" +
                    "component=${appComponent.substringBefore('/')}/${ClassDndReceiver::class.java.name};end",
            )
        }

    /** 发岛：断网绕开系统校验后再 notify，返回是否真的绕行了 */
    private fun post(
        context: Context,
        id: Int,
        params: String,
        title: String,
        text: String,
        ongoing: Boolean,
    ): Boolean {
        ensureChannel(context)
        val appIcon = Icon.createWithResource(context, R.mipmap.ic_launcher)
        val builder =
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(openApp(context))
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                // 不设 category：系统按 miui.focus.param 自己排版，多给一个分类反而会被分到别的渲染分支
                .addExtras(
                    Bundle().apply {
                        putBundle(
                            "miui.focus.pics",
                            Bundle().apply {
                                putParcelable("miui.focus.pic_app_icon", appIcon)
                                putParcelable("miui.focus.pic_app_icon_dark", appIcon)
                                putParcelable("miui.focus.pic_small", appIcon)
                                putParcelable("miui.focus.pic_small_dark", appIcon)
                            },
                        )
                    },
                )
        if (ongoing) builder.setOngoing(true) else builder.setAutoCancel(true)
        val notification = builder.build()
        // 这份参数是系统读岛内容的唯一来源，只能 build 之后直接塞进 extras
        notification.extras.putString("miui.focus.param", params)
        return ShizukuBypass.withBypass(context) {
            runCatching { notificationManager(context)?.notify(id, notification) }
        }
    }

    private fun ensureChannel(context: Context) {
        val manager = notificationManager(context) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "超级岛", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "课前与课中常驻显示倒计时"
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                // 上课自动勿扰打开后，用户特意开的提醒不能跟着被一起挡掉
                setBypassDnd(true)
            },
        )
    }
}
