package com.fsmytsai.aiclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fsmytsai.aiclock.model.AlarmClocks
import com.fsmytsai.aiclock.service.app.SharedService
import com.fsmytsai.aiclock.service.app.SpeechDownloader
import com.google.gson.Gson

class PrepareReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val acId = intent.getIntExtra("ACId", 0)
        SharedService.writeDebugLog("PrepareReceiver ACId = $acId")
        if (acId != 0 && SharedService.checkAlarmClockIsOpen(context, acId)) {
            val serviceIntent = Intent(context, PrepareService::class.java)
            serviceIntent.putExtra("ACId", acId)
            context.startService(serviceIntent)
        }
    }
}
