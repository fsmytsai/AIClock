package com.fsmytsai.aiclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fsmytsai.aiclock.model.AlarmClocks
import com.fsmytsai.aiclock.service.app.SpeechDownloader
import com.google.gson.Gson

class PrepareReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val acId = intent.getIntExtra("ACId", 0)
        if (acId != 0) {
            val spDatas = context.getSharedPreferences("Datas", Context.MODE_PRIVATE)
            val alarmClocksJsonStr = spDatas.getString("AlarmClocksJsonStr", "")
            val alarmClocks = Gson().fromJson(alarmClocksJsonStr, AlarmClocks::class.java)
            for (alarmClock in alarmClocks.alarmClockList) {
                if (alarmClock.acId == acId) {
                    val speechDownloader = SpeechDownloader(context, false)
                    speechDownloader.setAlarmClock(alarmClock)
                }
            }

        }
    }
}
