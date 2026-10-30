// 只在 Shizuku 启动的 shell 进程里跑：应用进程调不到的隐藏 API 得由它代劳
package com.miuix.hyperschedule.reminder;

import android.os.ParcelFileDescriptor;

interface IPrivilegedService {
    boolean setPackageNetworkingEnabled(int uid, boolean enabled);

    // 传进来的是 APK 管道，返回 pm install 的输出，含 Success 即安装成功
    String installApk(in ParcelFileDescriptor source);
}
