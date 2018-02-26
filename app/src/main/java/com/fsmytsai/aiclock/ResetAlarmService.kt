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


class ResetAlarmService : Service() {
    private var mNeedResetCount = 0
    private var mIsStartedForeground = false

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    @TargetApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmClocks = SharedService.getAlarmClocks(this)
        val isFromMain = intent?.getBooleanExtra("IsFromMain", false) ?: false
        for (alarmClock in alarmClocks.alarmClockList) {
            if (alarmClock.isOpen) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !mIsStartedForeground && !isFromMain) {
                    mIsStartedForeground = true
                    SharedService.writeDebugLog("ResetAlarmService resetAlarm in Android O")
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

                mNeedResetCount++
                SharedService.writeDebugLog("ResetAlarmService start download mNeedResetCount = $mNeedResetCount")

                val speechDownloader = SpeechDownloader(this, null)
                speechDownloader.setFinishListener(object : SpeechDownloader.DownloadFinishListener {
                    override fun cancel() {
                        mNeedResetCount--
                        SharedService.writeDebugLog("ResetAlarmService cancel download mNeedResetCount = $mNeedResetCount")
                        if (mNeedResetCount == 0)
                            stopSelf()
                    }

                    override fun startSetData() {

                    }

                    override fun allFinished() {
                        mNeedResetCount--
                        SharedService.writeDebugLog("ResetAlarmService finish download mNeedResetCount = $mNeedResetCount")
                        if (mNeedResetCount == 0)
                            stopSelf()
                    }
                })
                speechDownloader.setAlarmClock(alarmClock)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        SharedService.writeDebugLog("ResetAlarmService onDestroy")
        super.onDestroy()
    }
}
