package com.fsmytsai.aiclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.fsmytsai.aiclock.service.app.SharedService
import java.util.*

class CancelService : Service() {

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val acId = intent?.getIntExtra("ACId", 0)
        if (acId != null && acId != 0) {
            val alarmClock = SharedService.getAlarmClock(this, acId)
            if (alarmClock == null || !SharedService.checkAlarmClockIsOpen(this, acId))
                SharedService.writeDebugLog(this, "CancelService alarmClock is null or not open")
            else {
                SharedService.cancelAlarm(this, alarmClock.acId)

                if (alarmClock.isRepeatArr.none { it }) {
                    SharedService.writeDebugLog(this, "CancelService only alarm once")

                    val alarmClocks = SharedService.getAlarmClocks(this, alarmClock.acId > 1000)
                    alarmClocks.alarmClockList.find { it.acId == alarmClock.acId }!!.isOpen = false
                    SharedService.updateAlarmClocks(this, alarmClocks, alarmClock.acId > 1000)
                } else {
                    //跳過 40 分鐘內響鈴的這次
                    val alarmCalendar = SharedService.getAlarmCalendar(alarmClock, true)
                    alarmCalendar.add(Calendar.MINUTE, -40)
                    val nowCalendar = Calendar.getInstance()
                    val differenceSecond = (alarmCalendar.timeInMillis - nowCalendar.timeInMillis) / 1000

                    SharedService.writeDebugLog(this, "CancelService ${differenceSecond}s later prepare")
                    val prepareIntent = Intent(this, PrepareReceiver::class.java)
                    prepareIntent.putExtra("ACId", alarmClock.acId)

                    val pi = PendingIntent.getBroadcast(this, alarmClock.acId, prepareIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                    val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmCalendar.timeInMillis, pi)
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> am.setExact(AlarmManager.RTC_WAKEUP, alarmCalendar.timeInMillis, pi)
                        else -> am.set(AlarmManager.RTC_WAKEUP, alarmCalendar.timeInMillis, pi)
                    }
                }
            }
        } else
            SharedService.writeDebugLog(this, "CancelService acId is null or 0")

        stopSelf()

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        SharedService.writeDebugLog(this, "CancelService onDestroy")
        super.onDestroy()
    }
}
