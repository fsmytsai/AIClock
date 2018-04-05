package com.fsmytsai.aiclock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import com.fsmytsai.aiclock.model.AlarmClocks
import com.fsmytsai.aiclock.service.app.SharedService
import com.fsmytsai.aiclock.service.app.SpeechDownloader

class PrepareService : Service() {
    private var mIsStartedForeground = false
    private val mWaitToPrepareAlarmClocks = AlarmClocks()
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val acId = intent?.getIntExtra("ACId", 0)
        if (acId ?: -1 != 0) {
            val alarmClock = SharedService.getAlarmClock(this, acId!!)
            if (alarmClock == null || !SharedService.checkAlarmClockIsOpen(this, acId))
                stopSelf()
            else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !mIsStartedForeground && !SharedService.isScreenOn(this)) {
                    mIsStartedForeground = true
                    SharedService.writeDebugLog(this, "PrepareService prepareAlarm in Android O and screenOff")
                    val CHANNEL_ID = "prepareAlarm"
                    val channel = NotificationChannel(CHANNEL_ID,
                            "AI Clock NotificationChannel Name",
                            NotificationManager.IMPORTANCE_DEFAULT)

                    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)

                    val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                            .setContentTitle("AI 智能鬧鐘")
                            .setContentText("準備 AI 智能鬧鐘中...").build()

                    startForeground(1, notification)
                }

                if (mWaitToPrepareAlarmClocks.alarmClockList.size == 0) {
                    mWaitToPrepareAlarmClocks.alarmClockList.add(alarmClock)
                    SharedService.writeDebugLog(this, "PrepareService startDownload")
                    startDownload()
                } else {
                    mWaitToPrepareAlarmClocks.alarmClockList.add(alarmClock)
                    SharedService.writeDebugLog(this, "PrepareService add to wait No. ${mWaitToPrepareAlarmClocks.alarmClockList.size}")
                }
            }

        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startDownload() {
        val speechDownloader = SpeechDownloader(this, null)
        speechDownloader.setFinishListener(object : SpeechDownloader.DownloadFinishListener {
            override fun cancel() {
                if (mWaitToPrepareAlarmClocks.alarmClockList.isNotEmpty())
                    mWaitToPrepareAlarmClocks.alarmClockList.removeAt(0)
                if (mWaitToPrepareAlarmClocks.alarmClockList.isEmpty())
                    stopSelf()
                else
                    startDownload()
            }

            override fun startSetData() {

            }

            override fun allFinished() {
                if (mWaitToPrepareAlarmClocks.alarmClockList.isNotEmpty())
                    mWaitToPrepareAlarmClocks.alarmClockList.removeAt(0)
                if (mWaitToPrepareAlarmClocks.alarmClockList.isEmpty())
                    stopSelf()
                else
                    startDownload()
            }
        })
        speechDownloader.setAlarmClock(mWaitToPrepareAlarmClocks.alarmClockList[0])
    }

    override fun onDestroy() {
        SharedService.writeDebugLog(this, "PrepareService onDestroy")
        super.onDestroy()
    }
}
