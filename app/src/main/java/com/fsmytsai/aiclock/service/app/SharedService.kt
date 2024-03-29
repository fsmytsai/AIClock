package com.fsmytsai.aiclock.service.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.support.v7.app.AlertDialog
import com.fsmytsai.aiclock.AlarmReceiver
import com.fsmytsai.aiclock.PrepareReceiver
import com.fsmytsai.aiclock.model.AlarmClocks
import com.fsmytsai.aiclock.model.Texts
import com.fsmytsai.aiclock.model.TextsList
import com.google.gson.Gson
import android.widget.Toast
import android.net.ConnectivityManager
import android.support.v7.app.AppCompatActivity
import android.util.Log
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import com.fsmytsai.aiclock.BuildConfig
import com.fsmytsai.aiclock.R


class SharedService {
    companion object {
        val speakerArr = arrayOf("f1", "f2", "m1")

        fun getLatestUrl(context: Context): String {
            val spDatas = context.getSharedPreferences("Datas", Context.MODE_PRIVATE)
            val latestUrl = spDatas.getString("LatestUrl", "http://aiclock.southeastasia.cloudapp.azure.com/")
            return latestUrl
        }

        fun cancelAlarm(context: Context, acId: Int) {
            //取消準備
            var intent = Intent(context, PrepareReceiver::class.java)
            intent.putExtra("ACId", acId)
            var pi = PendingIntent.getBroadcast(context, acId, intent, PendingIntent.FLAG_NO_CREATE)
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (pi == null) {
                writeDebugLog(context, "SharedService cancelAlarm PrepareReceiver pi dose not exist")
            } else {
                writeDebugLog(context, "SharedService cancelAlarm PrepareReceiver success")
                am.cancel(pi)
                pi.cancel()
            }

            //取消響鈴
            intent = Intent(context, AlarmReceiver::class.java)
            intent.putExtra("ACId", acId)
            pi = PendingIntent.getBroadcast(context, acId, intent, PendingIntent.FLAG_NO_CREATE)
            if (pi == null) {
                writeDebugLog(context, "SharedService cancelAlarm AlarmReceiver pi dose not exist")
            } else {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(acId)
                writeDebugLog(context, "SharedService cancelAlarm AlarmReceiver success")
                am.cancel(pi)
                pi.cancel()
            }

            FixedNotificationManagement.check(context)
        }

        fun checkAlarmClockIsOpen(context: Context, acId: Int): Boolean {
            val alarmClock = getAlarmClock(context, acId)
            return alarmClock?.isOpen ?: false
        }

        //當兩種PI都回傳null，表示AlarmManager內不存在此鬧鐘
        fun checkNeedReset(context: Context, acId: Int, isCheckTime: Boolean): Boolean {
            //找不到 texts 直接重設
            val texts = getTexts(context, acId) ?: return true

            val appContext = context.applicationContext
            val prepareReceiverIntent = Intent(appContext, PrepareReceiver::class.java)
            prepareReceiverIntent.putExtra("ACId", acId)
            val prepareReceiverPI = PendingIntent.getBroadcast(appContext, acId, prepareReceiverIntent, PendingIntent.FLAG_NO_CREATE)
            val alarmReceiverIntent = Intent(appContext, AlarmReceiver::class.java)
            alarmReceiverIntent.putExtra("ACId", acId)
            val alarmReceiverPI = PendingIntent.getBroadcast(appContext, acId, alarmReceiverIntent, PendingIntent.FLAG_NO_CREATE)

            //確定是舊資料才多檢查時間
            if (isCheckTime && texts.isOldData) {
                val nowCalendar = Calendar.getInstance()
                val alarmClock = getAlarmClock(context, acId) ?: return false
                val alarmCalendar = getAlarmCalendar(alarmClock, false)
                val differenceMinute = (alarmCalendar.timeInMillis - nowCalendar.timeInMillis) / (1000 * 60)
                return differenceMinute in 0..39 || (prepareReceiverPI == null && alarmReceiverPI == null)
            }

            return prepareReceiverPI == null && alarmReceiverPI == null
        }

        fun checkAlarmClockTime(context: Context, acId: Int): Boolean {
            val alarmClock = getAlarmClock(context, acId)
            if (alarmClock != null) {
                val nowCalendar = Calendar.getInstance()
                val alarmCalendar = Calendar.getInstance()
                alarmCalendar.set(Calendar.HOUR_OF_DAY, alarmClock.hour)
                alarmCalendar.set(Calendar.MINUTE, alarmClock.minute)
                alarmCalendar.set(Calendar.SECOND, 0)

                val differenceSecond = kotlin.math.abs(nowCalendar.timeInMillis - alarmCalendar.timeInMillis) / 1000
                writeDebugLog(context, "SharedService checkAlarmClockTime differenceSecond = $differenceSecond")
                //檢查誤差秒數是否小於 540 秒
                if (differenceSecond < 540)
                    return true
            }
            return false
        }

        fun isAlarmClockTimeRepeat(context: Context, alarmClock: AlarmClocks.AlarmClock, isLater: Boolean): Boolean {
            val alarmCalendar = getAlarmCalendar(alarmClock, false)
            val alarmClocks = SharedService.getAlarmClocks(context, isLater)
            for (otherAlarmClock in alarmClocks.alarmClockList.filter { it.acId != alarmClock.acId }) {
                val otherAlarmCalendar = getAlarmCalendar(otherAlarmClock, false)
                if (alarmCalendar.timeInMillis / 1000 == otherAlarmCalendar.timeInMillis / 1000)
                    return true
            }
            return false
        }

        fun getAlarmCalendar(alarmClock: AlarmClocks.AlarmClock, isCancel: Boolean): Calendar {
            val nowCalendar = Calendar.getInstance()
            val alarmCalendar = Calendar.getInstance()
            if (alarmClock.isRepeatArr.none { it }) {
                //全部沒選，只響一次
                alarmCalendar.set(Calendar.HOUR_OF_DAY, alarmClock.hour)
                alarmCalendar.set(Calendar.MINUTE, alarmClock.minute)
                alarmCalendar.set(Calendar.SECOND, 0)
                if (alarmCalendar.timeInMillis < nowCalendar.timeInMillis) {
                    //已過去，補一天
                    alarmCalendar.add(Calendar.DATE, 1)
                }
            } else {
                var addDate = 0
                var isIgnore = isCancel

                //排列7天內的DAY_OF_WEEK，由於 SUNDAY = 1 ，所以要減回來。
                val days = ArrayList<Int>()
                for (i in (nowCalendar.get(Calendar.DAY_OF_WEEK) - 1)..(nowCalendar.get(Calendar.DAY_OF_WEEK) + 5)) {
                    days.add(i % 7)
                }
                for (i in days) {
                    if (alarmClock.isRepeatArr[i]) {
                        //當天有圈，判斷設置的時間是否大於現在時間
                        if (i == nowCalendar.get(Calendar.DAY_OF_WEEK) - 1) {
                            if (alarmClock.hour > nowCalendar.get(Calendar.HOUR_OF_DAY)) {
                                //忽略第一次
                                if (isIgnore) {
                                    isIgnore = false
                                    addDate++
                                } else
                                    break
                            } else if (alarmClock.hour == nowCalendar.get(Calendar.HOUR_OF_DAY) &&
                                    alarmClock.minute > nowCalendar.get(Calendar.MINUTE)) {
                                //忽略第一次
                                if (isIgnore) {
                                    isIgnore = false
                                    addDate++
                                } else
                                    break
                            } else {
                                //當天有圈但設置時間小於現在時間，等於下星期的今天
                                addDate++
                            }

                        } else {
                            //忽略第一次
                            if (isIgnore) {
                                isIgnore = false
                                addDate++
                            } else {
                                //不是當天代表可結束計算
                                break
                            }
                        }
                    } else {
                        //沒圈，繼續加
                        addDate++
                    }
                }

                alarmCalendar.add(Calendar.DATE, addDate)
                alarmCalendar.set(Calendar.HOUR_OF_DAY, alarmClock.hour)
                alarmCalendar.set(Calendar.MINUTE, alarmClock.minute)
                alarmCalendar.set(Calendar.SECOND, 0)
            }
            return alarmCalendar
        }

        fun getAlarmClocks(context: Context, isLater: Boolean): AlarmClocks {
            val spDatas = context.getSharedPreferences("Datas", Context.MODE_PRIVATE)
            val key = if (isLater) "LaterAlarmClocksJsonStr" else "AlarmClocksJsonStr"
            val alarmClocksJsonStr = spDatas.getString(key, "")
            return if (alarmClocksJsonStr != "")
                Gson().fromJson(alarmClocksJsonStr, AlarmClocks::class.java)
            else
                AlarmClocks(ArrayList())
        }

        fun getAlarmClock(context: Context, acId: Int): AlarmClocks.AlarmClock? {
            val alarmClocks = getAlarmClocks(context, acId > 1000)
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

        fun updateAlarmClocks(context: Context, alarmClocks: AlarmClocks, isLater: Boolean) {
            val spDatas = context.getSharedPreferences("Datas", Context.MODE_PRIVATE)
            val key = if (isLater) "LaterAlarmClocksJsonStr" else "AlarmClocksJsonStr"
            spDatas.edit().putString(key, Gson().toJson(alarmClocks)).apply()
        }

        fun deleteAlarmClock(context: Context, acId: Int) {
            val alarmClocks = getAlarmClocks(context, acId > 1000)
            for (i in 0 until alarmClocks.alarmClockList.size)
                if (alarmClocks.alarmClockList[i].acId == acId) {
                    alarmClocks.alarmClockList.removeAt(i)
                    updateAlarmClocks(context, alarmClocks, acId > 1000)
                    break
                }

            SharedService.deleteOldTextsData(context, acId, null, false)
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

//        fun isScreenOn(context: Context): Boolean {
//            if (android.os.Build.VERSION.SDK_INT >= 20) {
//                val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
//                val displays = dm.displays
//                for (display in displays) {
//                    if (display.state == Display.STATE_ON || display.state == Display.STATE_UNKNOWN) {
//                        return true
//                    }
//                }
//                return false
//            }
//
//            val powerManager = context.getSystemService(POWER_SERVICE) as PowerManager
//            return powerManager.isScreenOn
//        }

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

        fun writeDebugLog(context: Context, logContent: String) {
            if (BuildConfig.DEBUG) {
                Log.d("AIClockDebugLog", logContent)
            }
            File("${context.filesDir}/logs/").mkdir()
            val nowCalendar = Calendar.getInstance()
            val logFileName = "${nowCalendar.get(Calendar.YEAR)}-${nowCalendar.get(Calendar.MONTH) + 1}-${nowCalendar.get(Calendar.DAY_OF_MONTH)}.txt"
            val file = File("${context.filesDir}/logs/$logFileName")

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

        private fun showErrorDialog(context: Context, errorMessageList: ArrayList<String>) {
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

        fun getActionBarSize(context: Context): Int {
            val styledAttributes = context.theme.obtainStyledAttributes(intArrayOf(android.R.attr.actionBarSize))
            val actionBarSize = styledAttributes.getDimension(0, 0f).toInt()
            styledAttributes.recycle()
            return actionBarSize
        }

        private var mLoadingDialog: Dialog? = null

        fun showLoadingDialog(activity: AppCompatActivity) {
            val dialogView = activity.layoutInflater.inflate(R.layout.dialog_loading, null)
            mLoadingDialog = android.app.AlertDialog.Builder(activity)
                    .setView(dialogView)
                    .setCancelable(false)
                    .create()
            mLoadingDialog!!.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            mLoadingDialog!!.show()
        }

        fun hideLoadingDialog() {
            mLoadingDialog?.dismiss()
            mLoadingDialog = null
        }
    }
}