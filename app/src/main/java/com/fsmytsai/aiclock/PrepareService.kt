package com.fsmytsai.aiclock

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.fsmytsai.aiclock.service.app.SharedService
import com.fsmytsai.aiclock.service.app.SpeechDownloader

class PrepareService : Service() {
    override fun onBind(intent: Intent?): IBinder {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val acId = intent?.getIntExtra("ACId", 0)
        if (acId != 0) {
            val alarmClock = SharedService.getAlarmClock(this, acId!!)
            if (alarmClock == null)
                stopSelf()
            else {
                val speechDownloader = SpeechDownloader(this, null)
                speechDownloader.setFinishListener(object : SpeechDownloader.DownloadFinishListener {
                    override fun cancel() {
                        stopSelf()
                    }

                    override fun startSetData() {

                    }

                    override fun allFinished() {
                        stopSelf()
                    }
                })
                speechDownloader.setAlarmClock(alarmClock)
            }

        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        Log.d("PrepareService", "onDestroy")
        super.onDestroy()
    }
}
