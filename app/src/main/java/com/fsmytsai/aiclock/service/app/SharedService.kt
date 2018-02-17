package com.fsmytsai.aiclock.service.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.fsmytsai.aiclock.AlarmReceiver


/**
 * Created by user on 2018/2/17.
 */
class SharedService {
    companion object {
        var isNewsPlaying = false
        var reRunRunnable = false

        fun cancelAlarm(context: Context,acId : Int) {
            val intent = Intent(context, AlarmReceiver::class.java)
            intent.putExtra("ACId", acId)
            val pi = PendingIntent.getBroadcast(context, acId, intent, PendingIntent.FLAG_ONE_SHOT)
            val am =context. getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pi)
        }
    }
}