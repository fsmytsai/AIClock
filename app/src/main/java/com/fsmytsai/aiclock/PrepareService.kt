package com.fsmytsai.aiclock

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.fsmytsai.aiclock.model.AlarmClocks
import com.fsmytsai.aiclock.service.app.SharedService
import com.fsmytsai.aiclock.service.app.SpeechDownloader

class PrepareService : Service() {
    private val mWaitToPrepareAlarmClocks = AlarmClocks()
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val acId = intent?.getIntExtra("ACId", 0)
        if (acId != null && acId != 0) {
            val alarmClock = SharedService.getAlarmClock(this, acId)
            if (alarmClock == null || !SharedService.checkAlarmClockIsOpen(this, acId)) {
                SharedService.writeDebugLog(this, "PrepareService alarmClock is null or not open")
                stopSelf()
            } else {
                if (mWaitToPrepareAlarmClocks.alarmClockList.size == 0) {
                    mWaitToPrepareAlarmClocks.alarmClockList.add(alarmClock)
                    SharedService.writeDebugLog(this, "PrepareService startDownload")
                    startDownload()
                } else {
                    mWaitToPrepareAlarmClocks.alarmClockList.add(alarmClock)
                    SharedService.writeDebugLog(this, "PrepareService add to wait No. ${mWaitToPrepareAlarmClocks.alarmClockList.size}")
                }
            }
        } else {
            SharedService.writeDebugLog(this, "PrepareService acId is null or 0")
            stopSelf()
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
