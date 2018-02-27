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
import java.io.File
import java.util.*
import kotlin.collections.ArrayList


/**
 * Created by user on 2018/2/17.
 */
class SharedService {
    companion object {
        var isNewsPlaying = false
        var reRunRunnable = false
        val waitToPrepareAlarmClockList = ArrayList<AlarmClock>()

        fun cancelAlarm(context: Context, acId: Int) {
            val appContext = context.applicationContext
            //取消準備
            var intent = Intent(appContext, PrepareReceiver::class.java)
            intent.putExtra("ACId", acId)
            var pi = PendingIntent.getBroadcast(appContext, acId, intent, PendingIntent.FLAG_NO_CREATE)
            val am = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (pi == null) {
                writeDebugLog(context, "SharedService cancelAlarm PrepareReceiver pi dose not exist")
            } else {
                writeDebugLog(context, "SharedService cancelAlarm PrepareReceiver success")
                am.cancel(pi)
                pi.cancel()
            }

            //取消響鈴
            intent = Intent(appContext, AlarmReceiver::class.java)
            intent.putExtra("ACId", acId)
            pi = PendingIntent.getBroadcast(appContext, acId, intent, PendingIntent.FLAG_NO_CREATE)
            if (pi == null) {
                writeDebugLog(context, "SharedService cancelAlarm AlarmReceiver pi dose not exist")
            } else {
                writeDebugLog(context, "SharedService cancelAlarm AlarmReceiver success")
                am.cancel(pi)
                pi.cancel()
            }
        }

        fun checkAlarmClockIsOpen(context: Context, acId: Int): Boolean {
            val alarmClock = getAlarmClock(context, acId)
            if (alarmClock != null)
                return alarmClock.isOpen
            else
                return false
        }

        //當兩種PI都回傳null，表示AlarmManager內不存在此鬧鐘
        fun checkNeedReset(context: Context, acId: Int): Boolean {
            val alarmClock = getAlarmClock(context, acId)
            if (alarmClock != null) {
                val appContext = context.applicationContext
                val prepareReceiverIntent = Intent(appContext, PrepareReceiver::class.java)
                prepareReceiverIntent.putExtra("ACId", acId)
                val prepareReceiverPI = PendingIntent.getBroadcast(appContext, acId, prepareReceiverIntent, PendingIntent.FLAG_NO_CREATE)
                val alarmReceiverIntent = Intent(appContext, AlarmReceiver::class.java)
                alarmReceiverIntent.putExtra("ACId", acId)
                val alarmReceiverPI = PendingIntent.getBroadcast(appContext, acId, alarmReceiverIntent, PendingIntent.FLAG_NO_CREATE)
                return prepareReceiverPI == null && alarmReceiverPI == null
            } else
                return false
        }

//        fun checkAlarmClockTime(context: Context, acId: Int): Boolean {
//            val alarmClock = getAlarmClock(context, acId)
//            if (alarmClock != null) {
//                val nowCalendar = Calendar.getInstance()
//                val alarmCalendar = Calendar.getInstance()
//                alarmCalendar.set(Calendar.HOUR_OF_DAY, alarmClock.hour)
//                alarmCalendar.set(Calendar.MINUTE, alarmClock.minute)
//                alarmCalendar.set(Calendar.SECOND, 0)
//
//                val differenceSecond = kotlin.math.abs(nowCalendar.timeInMillis - alarmCalendar.timeInMillis) / 1000
//                writeDebugLog(context, "SharedService checkAlarmClockTime differenceSecond = $differenceSecond")
//                //檢查誤差秒數是否小於 60
//                if (differenceSecond < 60)
//                    return true
//            }
//            return false
//        }

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

        fun checkNetWork(context: Context, showToast: Boolean): Boolean {
            val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkInfo = connManager.activeNetworkInfo
            if (networkInfo == null || !networkInfo.isConnected || !networkInfo.isAvailable) {
                if (showToast)
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

        fun writeDebugLog(context: Context, logContent: String) {
            Log.d("AIClockDebugLog", logContent)
            val nowCalendar = Calendar.getInstance()
            val logFileName = "${nowCalendar.get(Calendar.YEAR)}-${nowCalendar.get(Calendar.MONTH) + 1}-${nowCalendar.get(Calendar.DAY_OF_MONTH)}.txt"
            val file = File("${context.filesDir}/logs/$logFileName")

            File("${context.filesDir}/logs/").mkdir()

            val time = "${nowCalendar.get(Calendar.HOUR_OF_DAY)}:${nowCalendar.get(Calendar.MINUTE)}:${nowCalendar.get(Calendar.SECOND)}"
            try {
                file.appendText("$time - $logContent\n")
            } catch (e: Exception) {
                Log.d("AIClockDebugLog", e.message)
            }
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