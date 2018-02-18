package com.fsmytsai.aiclock.service.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.fsmytsai.aiclock.AlarmReceiver
import com.fsmytsai.aiclock.PrepareReceiver
import com.fsmytsai.aiclock.model.Texts
import com.fsmytsai.aiclock.model.TextsList
import com.google.gson.Gson


/**
 * Created by user on 2018/2/17.
 */
class SharedService {
    companion object {
        var isNewsPlaying = false
        var reRunRunnable = false

        fun cancelAlarm(context: Context, acId: Int) {
            //取消準備
            var intent = Intent(context, PrepareReceiver::class.java)
            intent.putExtra("ACId", acId)
            var pi = PendingIntent.getBroadcast(context, acId, intent, PendingIntent.FLAG_ONE_SHOT)
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pi)

            //取消響鈴
            intent = Intent(context, AlarmReceiver::class.java)
            intent.putExtra("ACId", acId)
            pi = PendingIntent.getBroadcast(context, acId, intent, PendingIntent.FLAG_ONE_SHOT)
            am.cancel(pi)
        }

        fun deleteOldTextsData(context: Context, deleteACId: Int, ChangeTexts: Texts?, isAdd: Boolean) {
            val spDatas = context.getSharedPreferences("Datas", Context.MODE_PRIVATE)
            val textsList: TextsList
            val textsListJsonStr = spDatas.getString("TextsListJsonStr", "")
            if (textsListJsonStr != "") {
                textsList = Gson().fromJson(textsListJsonStr, TextsList::class.java)
                for (texts in textsList.textsList) {
                    if (texts.acId == deleteACId) {
                        textsList.textsList.remove(texts)
                        break
                    }
                }
                if (!isAdd)
                    spDatas.edit().putString("TextsListJsonStr", Gson().toJson(textsList)).apply()
            } else {
                textsList = TextsList(ArrayList())
            }
            if (isAdd) {
                textsList.textsList.add(ChangeTexts!!)
                spDatas.edit().putString("TextsListJsonStr", Gson().toJson(textsList)).apply()
            }
        }
    }
}