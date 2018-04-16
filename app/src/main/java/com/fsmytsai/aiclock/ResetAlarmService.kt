package com.fsmytsai.aiclock

import android.annotation.TargetApi
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.fsmytsai.aiclock.service.app.SharedService
import com.fsmytsai.aiclock.service.app.SpeechDownloader
import android.app.NotificationManager
import android.app.NotificationChannel
import android.content.Context
import android.support.v4.app.NotificationCompat
import com.fsmytsai.aiclock.model.AlarmClocks


class ResetAlarmService : Service() {
    private var mIsStartedForeground = false
    private var mNeedResetAlarmClocks = AlarmClocks()

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    @TargetApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val isFromMain = intent?.getBooleanExtra("IsFromMain", false) ?: false
        val isFromReceiver = intent?.getBooleanExtra("IsFromReceiver", false) ?: false

        if (isFromMain || isFromReceiver) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !mIsStartedForeground && !isFromMain) {
                mIsStartedForeground = true
                SharedService.writeDebugLog(this, "ResetAlarmService resetAlarm in Android O")
                val CHANNEL_ID = "resetAlarm"
                val channel = NotificationChannel(CHANNEL_ID,
                        "AI Clock NotificationChannel Name",
                        NotificationManager.IMPORTANCE_DEFAULT)

                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)

                val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle("AI 智能鬧鐘")
                        .setContentText("重新設定 AI 智能鬧鐘中...").build()

                startForeground(1, notification)
            }

            val alarmClocks = SharedService.getAlarmClocks(this, false)

            val isCheckTime = intent?.getBooleanExtra("IsCheckTime", false) ?: false

            alarmClocks.alarmClockList.filter { it.isOpen && SharedService.checkNeedReset(this, it.acId, isCheckTime) }
                    .mapTo(mNeedResetAlarmClocks.alarmClockList) { it }

            SharedService.writeDebugLog(this, "ResetAlarmService start download mNeedResetCount = ${mNeedResetAlarmClocks.alarmClockList.size}")

            if (mNeedResetAlarmClocks.alarmClockList.size == 0)
                stopSelf()
            else {
                startReset()
            }
        } else {
            stopSelf()
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun startReset() {
        val speechDownloader = SpeechDownloader(this, null)
        speechDownloader.setFinishListener(object : SpeechDownloader.DownloadFinishListener {
            override fun cancel() {
                if (mNeedResetAlarmClocks.alarmClockList.isNotEmpty()) {
                    mNeedResetAlarmClocks.alarmClockList.removeAt(0)
                    SharedService.writeDebugLog(this@ResetAlarmService, "ResetAlarmService cancel download mNeedResetCount = ${mNeedResetAlarmClocks.alarmClockList.size}")
                } else
                    SharedService.writeDebugLog(this@ResetAlarmService, "ResetAlarmService cancel download isEmpty")

                if (mNeedResetAlarmClocks.alarmClockList.isEmpty()) {
                    stopSelf()
                } else
                    startReset()
            }

            override fun startSetData() {

            }

            override fun allFinished() {
                if (mNeedResetAlarmClocks.alarmClockList.isNotEmpty()) {
                    mNeedResetAlarmClocks.alarmClockList.removeAt(0)
                    SharedService.writeDebugLog(this@ResetAlarmService, "ResetAlarmService finished download mNeedResetCount = ${mNeedResetAlarmClocks.alarmClockList.size}")
                } else
                    SharedService.writeDebugLog(this@ResetAlarmService, "ResetAlarmService finished download isEmpty")
                if (mNeedResetAlarmClocks.alarmClockList.isEmpty()) {
                    stopSelf()
                } else
                    startReset()
            }
        })
        speechDownloader.setAlarmClock(mNeedResetAlarmClocks.alarmClockList[0])
    }

    override fun onDestroy() {
        SharedService.writeDebugLog(this, "ResetAlarmService onDestroy")
        super.onDestroy()
    }
}
