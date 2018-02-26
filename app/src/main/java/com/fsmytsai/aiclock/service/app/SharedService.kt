package com.fsmytsai.aiclock.service.app

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.support.v7.app.AlertDialog
import com.fsmytsai.aiclock.AlarmReceiver
import com.fsmytsai.aiclock.PrepareReceiver
import com.fsmytsai.aiclock.model.AlarmClock
import com.fsmytsai.aiclock.model.AlarmClocks
import com.fsmytsai.aiclock.model.Texts
import com.fsmytsai.aiclock.model.TextsList
import com.google.gson.Gson
import android.widget.Toast
import android.net.ConnectivityManager
import android.util.Log


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

        fun checkAlarmClockIsOpen(context: Context, acId: Int): Boolean {
            val alarmClock = getAlarmClock(context, acId)
            if (alarmClock != null)
                return alarmClock.isOpen
            else
                return false
        }

        fun getAlarmClocks(context: Context): AlarmClocks {
            val spDatas = context.getSharedPreferences("Datas", Context.MODE_PRIVATE)
            val alarmClocksJsonStr = spDatas.getString("AlarmClocksJsonStr", "")
            return if (alarmClocksJsonStr != "")
                Gson().fromJson(alarmClocksJsonStr, AlarmClocks::class.java)
            else
                AlarmClocks(ArrayList())
        }

        fun getAlarmClock(context: Context, acId: Int): AlarmClock? {
            val alarmClocks = getAlarmClocks(context)
            return alarmClocks.alarmClockList.firstOrNull { it.acId == acId }
        }

        fun getTextsList(context: Context): TextsList? {
            val spDatas = context.getSharedPreferences("Datas", Context.MODE_PRIVATE)
            val textsListJsonStr = spDatas.getString("TextsListJsonStr", "")
            val textsList = Gson().fromJson(textsListJsonStr, TextsList::class.java)
            return textsList
        }

        fun getTexts(context: Context, acId: Int): Texts? {
            val textsList = getTextsList(context)
            return textsList?.textsList?.firstOrNull { it.acId == acId }
        }

        fun updateAlarmClocks(context: Context, alarmClocks: AlarmClocks) {
            val spDatas = context.getSharedPreferences("Datas", Context.MODE_PRIVATE)
            spDatas.edit().putString("AlarmClocksJsonStr", Gson().toJson(alarmClocks)).apply()
        }

        fun deleteAlarmClock(context: Context, acId: Int) {
            val alarmClocks = getAlarmClocks(context)
            for (i in 0 until alarmClocks.alarmClockList.size)
                if (alarmClocks.alarmClockList[i].acId == acId) {
                    alarmClocks.alarmClockList.removeAt(i)
                    SharedService.updateAlarmClocks(context, alarmClocks)
                    return
                }
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

        fun getVersionCode(context: Context): Int {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            return pInfo.versionCode
        }

        fun checkNetWork(context: Context): Boolean {
            val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkInfo = connManager.activeNetworkInfo
            if (networkInfo == null || !networkInfo.isConnected || !networkInfo.isAvailable) {
                showTextToast(context, "請檢查網路連線")
                return false
            }
            return true
        }

        @SuppressLint("MissingPermission")
        fun setLocation(context: Context, alarmClock: AlarmClock): Boolean {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = locationManager.getProviders(true)
            var bestLocation: Location? = null
            for (provider in providers) {
                val l = locationManager.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                    bestLocation = l
                }
            }

            if (bestLocation == null) {
                return false
            } else {
                alarmClock.latitude = bestLocation.latitude
                alarmClock.longitude = bestLocation.longitude
                return true
            }
        }

        fun writeDebugLog(logContent: String) {
            Log.d("AIClockDebugLog", logContent)
        }

        //避免重複Toast
        private var toast: Toast? = null

        fun showTextToast(context: Context, content: String) {
            if (toast == null) {
                toast = Toast.makeText(context, content, Toast.LENGTH_SHORT)
            } else {
                toast!!.setText(content)
            }
            toast!!.show()
        }

        fun handleError(context: Context, statusCode: Int, responseMessage: String) {
            val errorMessageList = ArrayList<String>()
            if (statusCode == 400) {
                errorMessageList.addAll(Gson().fromJson(responseMessage, ArrayList<String>()::class.java))
                SharedService.showErrorDialog(context, errorMessageList)
            } else {
                errorMessageList.add("ERROR:$statusCode\n請告知開發人員")
                SharedService.showErrorDialog(context, errorMessageList)
            }
        }

        fun showErrorDialog(context: Context, errorMessageList: ArrayList<String>) {
            var errorMessage = ""
            for (i in errorMessageList.indices) {
                errorMessage += errorMessageList[i]
                if (i != errorMessageList.size - 1) {
                    errorMessage += "\n"
                }
            }
            AlertDialog.Builder(context)
                    .setTitle("錯誤訊息")
                    .setMessage(errorMessage)
                    .setPositiveButton("知道了", null)
                    .show()
        }
    }
}