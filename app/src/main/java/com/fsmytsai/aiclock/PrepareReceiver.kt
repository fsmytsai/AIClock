package com.fsmytsai.aiclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.fsmytsai.aiclock.service.app.SharedService

class PrepareReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val acId = intent.getIntExtra("ACId", 0)
        SharedService.writeDebugLog(context, "PrepareReceiver ACId = $acId")
        if (acId != 0) {
            val serviceIntent = Intent(context, PrepareService::class.java)
            serviceIntent.putExtra("ACId", acId)
            context.startService(serviceIntent)
        }
    }
}
