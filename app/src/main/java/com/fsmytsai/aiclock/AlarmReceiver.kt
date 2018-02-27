package com.fsmytsai.aiclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fsmytsai.aiclock.service.app.SharedService
import com.fsmytsai.aiclock.ui.activity.AlarmActivity


class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val acId = intent.getIntExtra("ACId", 0)
        SharedService.writeDebugLog(context, "AlarmActivity ACId = $acId")
        val isOpen = SharedService.checkAlarmClockIsOpen(context, acId)
        //當設置完後單純按返回鍵沒殺掉整個App的話會抓到舊資料
//        val isRightTime = SharedService.checkAlarmClockTime(context, acId)
        SharedService.writeDebugLog(context, "AlarmReceiver isOpen = $isOpen")
        if (acId != 0 && isOpen) {
            val startAlarmIntent = Intent(context, AlarmActivity::class.java)
            startAlarmIntent.putExtra("ACId", acId)
            startAlarmIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(startAlarmIntent)
        }
    }
}
