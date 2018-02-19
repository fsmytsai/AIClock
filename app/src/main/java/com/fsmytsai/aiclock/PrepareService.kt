package com.fsmytsai.aiclock

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.fsmytsai.aiclock.model.AlarmClocks
import com.fsmytsai.aiclock.service.app.SpeechDownloader
import com.google.gson.Gson

class PrepareService : Service() {
    override fun onBind(intent: Intent?): IBinder {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val acId = intent?.getIntExtra("ACId", 0)
        if (acId != 0) {
            val spDatas = getSharedPreferences("Datas", Context.MODE_PRIVATE)
            val alarmClocksJsonStr = spDatas.getString("AlarmClocksJsonStr", "")
            val alarmClocks = Gson().fromJson(alarmClocksJsonStr, AlarmClocks::class.java)
            for (alarmClock in alarmClocks.alarmClockList) {
                if (alarmClock.acId == acId) {
                    val speechDownloader = SpeechDownloader(this, null)
                    speechDownloader.setFinishListener(object : SpeechDownloader.DownloadFinishListener {
                        override fun finish() {
                            stopSelf()
                        }
                    })
                    speechDownloader.setAlarmClock(alarmClock)
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }
}
