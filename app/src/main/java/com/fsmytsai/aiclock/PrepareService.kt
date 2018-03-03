package com.fsmytsai.aiclock

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.fsmytsai.aiclock.service.app.SharedService
import com.fsmytsai.aiclock.service.app.SpeechDownloader

class PrepareService : Service() {
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val acId = intent?.getIntExtra("ACId", 0)
        if (acId != 0) {
            val alarmClock = SharedService.getAlarmClock(this, acId!!)
            if (alarmClock == null || !SharedService.checkAlarmClockIsOpen(this, acId) || !SharedService.checkAlarmClockTime(this, acId))
                stopSelf()
            else {
                if (SharedService.waitToPrepareAlarmClockList.size == 0) {
                    SharedService.waitToPrepareAlarmClockList.add(alarmClock)
                    SharedService.writeDebugLog(this, "PrepareService startDownload")
                    startDownload()
                } else {
                    SharedService.waitToPrepareAlarmClockList.add(alarmClock)
                    SharedService.writeDebugLog(this, "PrepareService add to wait No. ${SharedService.waitToPrepareAlarmClockList.size}")
                }
            }

        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startDownload() {
        val speechDownloader = SpeechDownloader(this, null)
        speechDownloader.setFinishListener(object : SpeechDownloader.DownloadFinishListener {
            override fun cancel() {
                SharedService.waitToPrepareAlarmClockList.removeAt(0)
                if (SharedService.waitToPrepareAlarmClockList.size == 0)
                    stopSelf()
                else
                    startDownload()
            }

            override fun startSetData() {

            }

            override fun allFinished() {
                SharedService.waitToPrepareAlarmClockList.removeAt(0)
                if (SharedService.waitToPrepareAlarmClockList.size == 0)
                    stopSelf()
                else
                    startDownload()
            }
        })
        speechDownloader.setAlarmClock(SharedService.waitToPrepareAlarmClockList[0])
    }

    override fun onDestroy() {
        SharedService.writeDebugLog(this, "PrepareService onDestroy")
        super.onDestroy()
    }
}
