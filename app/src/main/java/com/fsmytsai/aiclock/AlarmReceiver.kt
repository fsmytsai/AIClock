package com.fsmytsai.aiclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fsmytsai.aiclock.ui.activity.AlarmActivity


class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val acId = intent.getIntExtra("ACId", 0)
        if (acId != 0) {
            val startMainIntent = Intent(context, AlarmActivity::class.java)
            startMainIntent.putExtra("ACId", acId)
            startMainIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(startMainIntent)
        }
    }
}
