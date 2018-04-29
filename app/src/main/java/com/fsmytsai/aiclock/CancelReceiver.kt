package com.fsmytsai.aiclock

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.fsmytsai.aiclock.service.app.SharedService
import java.util.*

class CancelReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val acId = intent.getIntExtra("ACId", 0)
        SharedService.writeDebugLog(context, "CancelReceiver acId = $acId")
        val alarmClock = SharedService.getAlarmClock(context, acId)
        if (alarmClock != null) {
            SharedService.cancelAlarm(context, alarmClock.acId)

            if (alarmClock.isRepeatArr.none { it }) {
                SharedService.writeDebugLog(context, "CancelReceiver only alarm once")

                val alarmClocks = SharedService.getAlarmClocks(context, alarmClock.acId > 1000)
                alarmClocks.alarmClockList.find { it.acId == alarmClock.acId }!!.isOpen = false
                SharedService.updateAlarmClocks(context, alarmClocks, alarmClock.acId > 1000)
            } else {
                //跳過 40 分鐘內響鈴的這次
                val alarmCalendar = SharedService.getAlarmCalendar(alarmClock, true)
                alarmCalendar.add(Calendar.MINUTE, -40)
                val nowCalendar = Calendar.getInstance()
                val differenceSecond = (alarmCalendar.timeInMillis - nowCalendar.timeInMillis) / 1000

                SharedService.writeDebugLog(context, "CancelReceiver ${differenceSecond}s later prepare")
                val prepareIntent = Intent(context, PrepareReceiver::class.java)
                prepareIntent.putExtra("ACId", alarmClock.acId)

                val pi = PendingIntent.getBroadcast(context, alarmClock.acId, prepareIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmCalendar.timeInMillis, pi)
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> am.setExact(AlarmManager.RTC_WAKEUP, alarmCalendar.timeInMillis, pi)
                    else -> am.set(AlarmManager.RTC_WAKEUP, alarmCalendar.timeInMillis, pi)
                }
            }
        }
    }
}
