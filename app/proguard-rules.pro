# 项目专用 R8 规则

# Shizuku
# api 侧靠反射调用（Class.forName），类名与成员都不能改
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**
# 服务实现由 Shizuku 在 shell 进程里按类名反射实例化；AIDL 接口要保留 Binder 协议
-keep class com.miuix.hyperschedule.reminder.IPrivilegedService { *; }
-keep class com.miuix.hyperschedule.reminder.PrivilegedServiceImpl { <init>(); }
