package com.fsmytsai.aiclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fsmytsai.aiclock.ui.activity.AlarmActivity

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val acId = intent.getIntExtra("ACId", 0)
        if (acId != 0) {
            val startAlarmIntent = Intent(context, AlarmActivity::class.java)
            startAlarmIntent.putExtra("ACId", acId)
            startAlarmIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(startAlarmIntent)
        }
    }
}
