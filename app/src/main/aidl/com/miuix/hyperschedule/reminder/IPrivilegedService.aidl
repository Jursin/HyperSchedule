// 只在 Shizuku 启动的 shell 进程里跑：应用进程调不到的隐藏 API 得由它代劳
package com.miuix.hyperschedule.reminder;

interface IPrivilegedService {
    boolean setPackageNetworkingEnabled(int uid, boolean enabled);
}
