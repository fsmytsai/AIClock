package com.fsmytsai.aiclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.fsmytsai.aiclock.service.app.SharedService

class ResetAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            SharedService.writeDebugLog(context, "ResetAlarmReceiver ACTION_BOOT_COMPLETED")
            val serviceIntent = Intent(context, ResetAlarmService::class.java)
            serviceIntent.putExtra("IsFromReceiver", true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } else {
            //確定是開啟網路才執行
            if (SharedService.checkNetWork(context, false)) {
                SharedService.writeDebugLog(context, "ResetAlarmReceiver open network")
                val serviceIntent = Intent(context, ResetAlarmService::class.java)
                serviceIntent.putExtra("IsFromReceiver", true)
                serviceIntent.putExtra("IsCheckTime", true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
