package com.fsmytsai.aiclock

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Context.NOTIFICATION_SERVICE
import com.fsmytsai.aiclock.service.app.SharedService


class CloseReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val acId = intent.getIntExtra("ACId", 0)
        SharedService.writeDebugLog(context, "CloseReceiver acId = $acId")
        if (acId != 0) {
            val manager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(acId)
        }
    }
}
