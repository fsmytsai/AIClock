package com.fsmytsai.aiclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fsmytsai.aiclock.service.app.SharedService

class CancelReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val acId = intent.getIntExtra("ACId", 0)
        SharedService.writeDebugLog(context, "CancelReceiver acId = $acId")
        if (acId != 0) {
            val serviceIntent = Intent(context, CancelService::class.java)
            serviceIntent.putExtra("ACId", acId)
            context.startService(serviceIntent)
        }

    }
}
