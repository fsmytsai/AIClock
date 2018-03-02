package com.fsmytsai.aiclock.service.app

import android.annotation.TargetApi
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.support.annotation.RequiresApi
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AlertDialog
import com.fsmytsai.aiclock.AlarmReceiver
import com.fsmytsai.aiclock.PrepareReceiver
import com.fsmytsai.aiclock.R
import com.fsmytsai.aiclock.model.AlarmClock
import com.fsmytsai.aiclock.model.PromptData
import com.fsmytsai.aiclock.model.Texts
import com.fsmytsai.aiclock.ui.activity.DownloadSpeechActivity
import com.google.gson.Gson
import com.liulishuo.filedownloader.BaseDownloadTask
import com.liulishuo.filedownloader.FileDownloadListener
import com.liulishuo.filedownloader.FileDownloader
import kotlinx.android.synthetic.main.dialog_prompt.view.*
import okhttp3.*
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList

/**
 * Created by tsaiminyuan on 2018/2/18.
 */

class SpeechDownloader(context: Context, activity: DownloadSpeechActivity?) {
    private var mContext = context
    private var mDownloadSpeechActivity = activity
    private lateinit var mAlarmClock: AlarmClock
    private val mAlarmTimeCalendar = Calendar.getInstance()
    private var mNeedDownloadCount = 0f
    private var mErrorDownloadCount = 0
    private var mDownloadedCount = 0f
    private var mTexts: Texts? = null
    private var mPromptData: PromptData? = null
    private val mAlarmTimeList = ArrayList<Long>()
    private var mIsFinishGetData = false
    private var mIsStartedDownloadSound = false
    private var mIsStoppedDownloadSound = false
    private var mIsCanceledDownloadSound = false
    private val mPauseList = ArrayList<String>()

    //    constructor(context: Context, inActivity: Boolean) {
//        mContext = context
//        mActivity = if (inActivity) context as Activity else null
//        mInActivity = inActivity
//    }

//    companion object {
//
//        @SuppressLint("StaticFieldLeak")
//        @Volatile
//        private var INSTANCE: SpeechDownloader? = null
//
//        fun getInstance(context: Context, inActivity: Boolean): SpeechDownloader =
//                INSTANCE ?: synchronized(this) {
//                    INSTANCE ?: SpeechDownloader(context, inActivity)
//                }
//    }

    fun setAlarmClock(alarmClock: AlarmClock) {
        mAlarmClock = alarmClock
        SharedService.writeDebugLog(mContext, "SpeechDownloader start ACId = ${mAlarmClock.acId}")
        //初始化 FileDownloader
        FileDownloader.setup(mContext)

        if (mDownloadSpeechActivity != null) {
            //前景
            val spDatas = mContext.getSharedPreferences("Datas", Context.MODE_PRIVATE)
            if (!spDatas.getBoolean("NeverPrompt", false)) {
                val dialogView = mDownloadSpeechActivity!!.layoutInflater.inflate(R.layout.dialog_prompt, null)
                AlertDialog.Builder(mDownloadSpeechActivity!!)
                        .setCancelable(false)
                        .setView(dialogView)
                        .setPositiveButton("開始設置鬧鐘", { _, _ ->
                            if (dialogView.cb_never_prompt.isChecked) {
                                spDatas.edit().putBoolean("NeverPrompt", true).apply()
                            }

                            //沒網路或離響鈴時間小於30秒取消下載
                            if (!getAlarmTime())
                                foregroundCancelDownloadSound()
                        })
                        .setNegativeButton("取消", { _, _ ->
                            foregroundCancelDownloadSound()
                        })
                        .show()
            } else {
                //沒網路或離響鈴時間小於30秒取消下載
                if (!getAlarmTime())
                    foregroundCancelDownloadSound()
            }

        } else {
            //背景
            //沒網路或離響鈴時間小於30秒取消下載
            if (!getAlarmTime())
                backgroundCancelDownloadSound()
        }
    }

    private fun getAlarmTime(): Boolean {
        val nowCalendar = Calendar.getInstance()

        if (mAlarmClock.isRepeatArr.all { !it }) {
            //全部沒選，只響一次
            mAlarmTimeCalendar.set(Calendar.HOUR_OF_DAY, mAlarmClock.hour)
            mAlarmTimeCalendar.set(Calendar.MINUTE, mAlarmClock.minute)
            mAlarmTimeCalendar.set(Calendar.SECOND, 0)
            if (mAlarmTimeCalendar.timeInMillis < nowCalendar.timeInMillis) {
                //已過去，補一天
                mAlarmTimeCalendar.add(Calendar.DATE, 1)
            }
        } else {
            var addDate = 0

            //排列7天內的DAY_OF_WEEK，由於 SUNDAY = 1 ，所以要減回來。
            val days = ArrayList<Int>()
            for (i in (nowCalendar.get(Calendar.DAY_OF_WEEK) - 1)..(nowCalendar.get(Calendar.DAY_OF_WEEK) + 5)) {
                days.add(i % 7)
            }
            for (i in days) {
                if (mAlarmClock.isRepeatArr[i]) {
                    //當天有圈，判斷設置的時間是否大於現在時間
                    if (i == nowCalendar.get(Calendar.DAY_OF_WEEK) - 1) {
                        if (mAlarmClock.hour > nowCalendar.get(Calendar.HOUR_OF_DAY))
                            break
                        else if (mAlarmClock.hour == nowCalendar.get(Calendar.HOUR_OF_DAY) &&
                                mAlarmClock.minute > nowCalendar.get(Calendar.MINUTE))
                            break
                        else
                        //當天有圈但設置時間小於現在時間，等於下星期的今天
                            addDate++
                    } else
                    //不是當天代表可結束計算
                        break
                } else
                    addDate++
            }

            mAlarmTimeCalendar.add(Calendar.DATE, addDate)
            mAlarmTimeCalendar.set(Calendar.HOUR_OF_DAY, mAlarmClock.hour)
            mAlarmTimeCalendar.set(Calendar.MINUTE, mAlarmClock.minute)
            mAlarmTimeCalendar.set(Calendar.SECOND, 0)
        }


        var differenceSecond = (mAlarmTimeCalendar.timeInMillis - nowCalendar.timeInMillis) / 1000

        if (differenceSecond < 30) {
            if (mDownloadSpeechActivity != null)
                AlertDialog.Builder(mDownloadSpeechActivity!!)
                        .setTitle("錯誤")
                        .setMessage("時間需自少超過當前時間 30 秒。")
                        .setPositiveButton("知道了", null)
                        .show()

            return false
        }

        if (differenceSecond >= 60 * 60 * 24) {
            mAlarmTimeList.add(differenceSecond / (60 * 60 * 24))
            differenceSecond %= (60 * 60 * 24)
        } else
            mAlarmTimeList.add(0)

        if (differenceSecond >= 60 * 60) {
            mAlarmTimeList.add(differenceSecond / (60 * 60))
            differenceSecond %= (60 * 60)
        } else
            mAlarmTimeList.add(0)

        if (differenceSecond >= 60)
            mAlarmTimeList.add(differenceSecond / 60)
        else
            mAlarmTimeList.add(0)

        if (differenceSecond > 0 && mAlarmTimeList[0] == 0L && mAlarmTimeList[1] == 0L && mAlarmTimeList[2] == 0L)
            mAlarmTimeList.add(differenceSecond)
        else
            mAlarmTimeList.add(0)

        SharedService.writeDebugLog(mContext, "SpeechDownloader nowTime = ${nowCalendar.get(Calendar.HOUR_OF_DAY)}:${nowCalendar.get(Calendar.MINUTE)}:${nowCalendar.get(Calendar.SECOND)}")
        SharedService.writeDebugLog(mContext, "SpeechDownloader mAlarmTimeList = ${mAlarmTimeList[0]} ${mAlarmTimeList[1]} ${mAlarmTimeList[2]} ${mAlarmTimeList[3]}")

        //檢查網路，前景執行就顯示無網路提示
        if (!SharedService.checkNetWork(mContext, mDownloadSpeechActivity != null))
            return false

        if (mDownloadSpeechActivity != null) getPromptData() else getTextData()

        return true
    }

    private fun getPromptData() {
        mDownloadSpeechActivity?.showDownloadingDialog()

        val request = Request.Builder()
                .url("${mDownloadSpeechActivity?.getString(R.string.server_url)}api/getPromptData?day=${mAlarmTimeList[0]}&hour=${mAlarmTimeList[1]}&" +
                        "minute=${mAlarmTimeList[2]}&second=${mAlarmTimeList[3]}&speaker=${mAlarmClock.speaker}&version_code=${SharedService.getVersionCode(mContext)}")
                .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call?, e: IOException?) {
                mDownloadSpeechActivity?.runOnUiThread {
                    SharedService.showTextToast(mDownloadSpeechActivity!!, "請檢查網路連線")
                    foregroundCancelDownloadSound()
                }
            }

            override fun onResponse(call: Call?, response: Response?) {
                val statusCode = response?.code()
                val resMessage = response?.body()?.string()

                mDownloadSpeechActivity?.runOnUiThread {
                    if (statusCode == 200) {
                        mPromptData = Gson().fromJson(resMessage, PromptData::class.java)
                        mDownloadSpeechActivity?.setDownloadProgress(5)
                        getTextData()
                    } else {
                        SharedService.handleError(mDownloadSpeechActivity!!, statusCode!!, resMessage!!)
                        foregroundCancelDownloadSound()
                    }
                }

            }
        })
    }

    private fun getTextData() {
        //如果有開啟位置且為背景則抓取位置
        if (mAlarmClock.latitude != 1000.0 && mDownloadSpeechActivity == null)
            if (ActivityCompat.checkSelfPermission(mContext, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(mContext, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                val isSuccess = SharedService.setLocation(mContext, mAlarmClock)
                if (!isSuccess) {
                    SharedService.writeDebugLog(mContext, "SpeechDownloader setLocation failed")
                }
            } else {
                SharedService.writeDebugLog(mContext, "SpeechDownloader no location permission")
            }

//        val url = if (mAlarmTimeList[0] > 0 || mAlarmTimeList[1] > 0 || mAlarmTimeList[2] > 15)
//            "${mContext.getString(R.string.server_url)}api/getTextData?hour=${mAlarmClock.hour}&" +
//                    "minute=${mAlarmClock.minute}&speaker=${mAlarmClock.speaker}&category=-1&latitude=1000&longitude=0"
//        else
        val url = "${mContext.getString(R.string.server_url)}api/getTextData?" +
                "hour=${mAlarmClock.hour}&minute=${mAlarmClock.minute}&" +
                "speaker=${mAlarmClock.speaker}&category=${mAlarmClock.category}&news_count=${mAlarmClock.newsCount}&" +
                "latitude=${mAlarmClock.latitude}&longitude=${mAlarmClock.longitude}&" +
                "version_code=${SharedService.getVersionCode(mContext)}"

        val request = Request.Builder()
                .url(url)
                .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call?, e: IOException?) {
                mDownloadSpeechActivity?.runOnUiThread {
                    SharedService.showTextToast(mDownloadSpeechActivity!!, "請檢查網路連線")
                    foregroundCancelDownloadSound()
                }
            }

            override fun onResponse(call: Call?, response: Response?) {
                val statusCode = response?.code()
                val resMessage = response?.body()?.string()
                if (mDownloadSpeechActivity != null)
                    mDownloadSpeechActivity?.runOnUiThread {
                        if (statusCode == 200) {
                            mTexts = Gson().fromJson(resMessage, Texts::class.java)
                            if (mTexts!!.textList.size > 0) {
                                mDownloadSpeechActivity?.setDownloadProgress(10)
                                mTexts!!.acId = mAlarmClock.acId
                                downloadSound()
                            } else {
                                SharedService.showTextToast(mDownloadSpeechActivity!!, "無預期錯誤，請重試")
                                foregroundCancelDownloadSound()
                            }
                        } else {
                            SharedService.handleError(mDownloadSpeechActivity!!, statusCode!!, resMessage!!)
                            foregroundCancelDownloadSound()
                        }
                    }
                else if (statusCode == 200) {
                    mTexts = Gson().fromJson(resMessage, Texts::class.java)
                    if (mTexts!!.textList.size > 0) {
                        mTexts!!.acId = mAlarmClock.acId
                        downloadSound()
                    }
                } else {
                    //背景失敗
                    backgroundCancelDownloadSound()
                }

            }
        })
    }

    private fun downloadSound() {
        if (mDownloadSpeechActivity != null) {
            FileDownloader.getImpl().create("${mContext.getString(R.string.server_url)}sounds/${mPromptData!!.data.text_id}-0-${mAlarmClock.speaker}.wav")
                    .setPath("${mContext.filesDir}/sounds/${mPromptData!!.data.text_id}-0-${mAlarmClock.speaker}.wav")
                    .setTag(0, "${mPromptData!!.data.text_id}-0-${mAlarmClock.speaker}")
                    .setCallbackProgressTimes(0)
                    .setAutoRetryTimes(1)
                    .setListener(mQueueTarget)
                    .asInQueueTask()
                    .enqueue()
            mNeedDownloadCount++
        }

        for (text in mTexts!!.textList) {
            for (i in 0 until text.part_count) {
                FileDownloader.getImpl().create("${mContext.getString(R.string.server_url)}sounds/${text.text_id}-$i-${mAlarmClock.speaker}.wav")
                        .setPath("${mContext.filesDir}/sounds/${text.text_id}-$i-${mAlarmClock.speaker}.wav")
                        .setTag(0, "${text.text_id}-$i-${mAlarmClock.speaker}")
                        .setCallbackProgressTimes(0)
                        .setAutoRetryTimes(1)
                        .setListener(mQueueTarget)
                        .asInQueueTask()
                        .enqueue()
                mNeedDownloadCount++
            }
        }
        mIsFinishGetData = true
        if (!mIsStoppedDownloadSound && !mIsCanceledDownloadSound)
            FileDownloader.getImpl().start(mQueueTarget, false)
    }

    fun stopDownloadSound() {
        if (mIsStartedDownloadSound) {
            FileDownloader.getImpl().pause(mQueueTarget)
        }
        mIsStoppedDownloadSound = true
    }

    fun resumeDownloadSound() {
        mIsStoppedDownloadSound = false

        if (mIsCanceledDownloadSound)
            return

        //完成取得資料但還沒開始下載
        if (mIsFinishGetData && !mIsStartedDownloadSound)
            FileDownloader.getImpl().start(mQueueTarget, false)

        //已開始下載，恢復task
        if (mIsStartedDownloadSound) {
            while (mPauseList.size > 0) {
                FileDownloader.getImpl().create("${mContext.getString(R.string.server_url)}sounds/${mPauseList[0]}.wav")
                        .setPath("${mContext.filesDir}/sounds/${mPauseList[0]}.wav")
                        .setTag(0, mPauseList[0])
                        .setCallbackProgressTimes(0)
                        .setAutoRetryTimes(1)
                        .setListener(mQueueTarget)
                        .asInQueueTask()
                        .enqueue()
                mPauseList.removeAt(0)
            }
            FileDownloader.getImpl().start(mQueueTarget, false)
        }
    }

    private val mQueueTarget: FileDownloadListener = object : FileDownloadListener() {
        override fun pending(task: BaseDownloadTask, soFarBytes: Int, totalBytes: Int) {}

        override fun connected(task: BaseDownloadTask?, etag: String?, isContinue: Boolean, soFarBytes: Int, totalBytes: Int) {
            mIsStartedDownloadSound = true
        }

        override fun progress(task: BaseDownloadTask, soFarBytes: Int, totalBytes: Int) {}

        override fun blockComplete(task: BaseDownloadTask?) {}

        override fun retry(task: BaseDownloadTask?, ex: Throwable?, retryingTimes: Int, soFarBytes: Int) {}

        override fun completed(task: BaseDownloadTask) {
            completeOrErrorDownload()
        }

        override fun paused(task: BaseDownloadTask, soFarBytes: Int, totalBytes: Int) {
            mPauseList.add(task.getTag(0) as String)
        }

        override fun error(task: BaseDownloadTask, e: Throwable) {
            SharedService.writeDebugLog(mContext, "SpeechDownloader download error ${task.getTag(0) as String}")
            mErrorDownloadCount++
            //超過 1/4 失敗則取消
            if (mErrorDownloadCount > mNeedDownloadCount / 4) {

                //避免多個檔案失敗造成重複呼叫取消
                if (!mIsCanceledDownloadSound) {
                    mIsCanceledDownloadSound = true
                    if (mDownloadSpeechActivity != null) {
                        SharedService.showTextToast(mDownloadSpeechActivity!!, "下載失敗，網路不穩")
                        foregroundCancelDownloadSound()
                    } else
                        backgroundCancelDownloadSound()
                }
            } else
                completeOrErrorDownload()
        }

        override fun warn(task: BaseDownloadTask) {}
    }

    fun foregroundCancelDownloadSound() {
        stopDownloadSound()
        mDownloadFinishListener?.cancel()
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun backgroundCancelDownloadSound() {
        FileDownloader.getImpl().pause(mQueueTarget)
        //離響鈴小於 30 秒(重開機時造成)， 10 分鐘後重試
        if (mAlarmTimeList.size == 0) {
            SharedService.writeDebugLog(mContext, "SpeechDownloader 背景取消下載，離響鈴小於 30 秒， 10 分鐘後重試")
            retryDownloadSound(10)
        } else {
            //距離響鈴時間的分鐘數
            val alarmMinute = mAlarmTimeList[0] * 24 * 60 + mAlarmTimeList[1] * 60 + mAlarmTimeList[2]
            //離響鈴還超過 27 分鐘(重開機或響鈴的瞬間可能會取得超大分鐘數)， X 分鐘後重試(距離響鈴時間的 1/3 ，小於 10 分鐘的話則使用 10 分鐘)
            //避免 9 * 3 造成的延遲響鈴 (響鈴時間太接近的鬧鐘過多可能還是會造成響鈴延遲)
            if (alarmMinute > 27) {
                val retryMinute = if (alarmMinute / 3 > 10) alarmMinute / 3 else 10
                SharedService.writeDebugLog(mContext, "SpeechDownloader 背景取消下載，離響鈴超過 27 分鐘($alarmMinute) $retryMinute 分鐘後重試")
                retryDownloadSound(retryMinute.toInt())
            } else {
                //剩下不到 27 分鐘響鈴，直接播放舊資料及音檔
                SharedService.writeDebugLog(mContext, "SpeechDownloader 背景取消下載，離響鈴不到 27 分鐘($alarmMinute)，直接播放舊資料及音檔")
                setAlarm(true)
            }
        }

        mDownloadFinishListener?.cancel()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun retryDownloadSound(retryMinute: Int) {
        val alarmIntent = Intent(mContext, PrepareReceiver::class.java)
        alarmIntent.putExtra("ACId", mAlarmClock.acId)

        //設置前先嘗試取消，避免重複設置
        SharedService.cancelAlarm(mContext, mAlarmClock.acId)

        val pi = PendingIntent.getBroadcast(mContext, mAlarmClock.acId, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val am = mContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val tenMinuteLaterCalendar = Calendar.getInstance()
        tenMinuteLaterCalendar.add(Calendar.MINUTE, retryMinute)

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tenMinuteLaterCalendar.timeInMillis, pi)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> am.setExact(AlarmManager.RTC_WAKEUP, tenMinuteLaterCalendar.timeInMillis, pi)
            else -> am.set(AlarmManager.RTC_WAKEUP, tenMinuteLaterCalendar.timeInMillis, pi)
        }
    }

    private fun completeOrErrorDownload() {
        mDownloadedCount++
        mDownloadSpeechActivity?.setDownloadProgress(10 + (mDownloadedCount / mNeedDownloadCount * 90).toInt())

        if (mDownloadedCount == mNeedDownloadCount) {
            SharedService.writeDebugLog(mContext, "SpeechDownloader download Finish")
            setData()
            if (mDownloadSpeechActivity != null) {
                mDownloadSpeechActivity?.setDownloadProgress(100)
                val uri = Uri.parse("android.resource://${mContext.packageName}/raw/" + when (mAlarmClock.speaker) {
                    0 -> "setfinishf1"
                    1 -> "setfinishf2"
                    2 -> "setfinishm1"
                    else -> ""
                })
                startPlaying(uri!!, true)
            } else
                allFinished()
        }
    }

    private fun setData() {
        mDownloadFinishListener?.startSetData()
        setAlarm(false)
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun setAlarm(isAbandon: Boolean) {
        val appContext = mContext.applicationContext

        //超過 40 分鐘才響鈴且需要更新資料及音檔(天氣或新聞)，則設置一個提前 40 分鐘的任務重新抓取新聞天氣
        val alarmIntent = if ((mAlarmTimeList[0] > 0 || mAlarmTimeList[1] > 0 || mAlarmTimeList[2] > 40) &&
                (mAlarmClock.latitude != 1000.0 || mAlarmClock.category != -1)) {
            SharedService.writeDebugLog(mContext, "SpeechDownloader 設置鬧鐘成功，${mAlarmTimeList[0]}天${mAlarmTimeList[1]}小時${mAlarmTimeList[2]}分鐘後響鈴")
            mTexts?.isOldData = true
            mAlarmTimeCalendar.add(Calendar.MINUTE, -40)
            Intent(appContext, PrepareReceiver::class.java)
        } else {
            SharedService.writeDebugLog(mContext, "SpeechDownloader 設置鬧鐘成功，40分鐘內響鈴，${mAlarmTimeList[2]}分鐘${mAlarmTimeList[3]}秒後響鈴")
            mTexts?.isOldData = isAbandon
            Intent(appContext, AlarmReceiver::class.java)
        }
        alarmIntent.putExtra("ACId", mAlarmClock.acId)

        //設置前先更新資料(有成功取得資料)
        if (mTexts != null)
            SharedService.deleteOldTextsData(appContext, mAlarmClock.acId, mTexts, true)

        //設置前先嘗試取消，避免重複設置
        SharedService.cancelAlarm(mContext, mAlarmClock.acId)

        val pi = PendingIntent.getBroadcast(appContext, mAlarmClock.acId, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, mAlarmTimeCalendar.timeInMillis, pi)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> am.setExact(AlarmManager.RTC_WAKEUP, mAlarmTimeCalendar.timeInMillis, pi)
            else -> am.set(AlarmManager.RTC_WAKEUP, mAlarmTimeCalendar.timeInMillis, pi)
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun startPlaying(uri: Uri, isSetFinish: Boolean) {
        val mpFinish = MediaPlayer()
        try {
            mpFinish.setDataSource(mContext, uri)
        } catch (e: Exception) {
            SharedService.writeDebugLog(mContext, "SpeechDownloader setDataSource failed uri = $uri")
            mpFinish.setDataSource("android.resource://${mContext.packageName}/raw/${SharedService.speakerArr[mAlarmClock.speaker]}_lost")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
            mpFinish.setAudioAttributes(audioAttributes)
        } else {
            mpFinish.setAudioStreamType(AudioManager.STREAM_ALARM)
        }
        mpFinish.setOnCompletionListener {
            mpFinish.release()
            if (isSetFinish) {
                val file = File("${mContext.filesDir}/sounds/${mPromptData!!.data.text_id}-0-${mAlarmClock.speaker}.wav")
                val promptUri = Uri.fromFile(file)
                startPlaying(promptUri, false)
            } else {
                allFinished()
            }
        }
        mpFinish.prepare()
        mpFinish.start()
    }

    private fun allFinished() {
        //所有工作都結束後清舊檔
        cleanAllOldSound()

        mDownloadFinishListener?.allFinished()
    }

    private fun cleanAllOldSound() {
        //先抓到所有還需要的檔名
        val allNeedFileName = ArrayList<String>()
        val textsList = SharedService.getTextsList(mContext) ?: return

        for (texts in textsList.textsList) {
            for (text in texts.textList)
                for (i in 0 until text.part_count)
                    allNeedFileName.add("${text.text_id}-$i-${mAlarmClock.speaker}.wav")
        }

        //遍歷所有檔案，檔名不在 allNeedFileName 裡的直接刪除
        val directory = File("${mContext.filesDir}/sounds")
        for (file in directory.listFiles()) {
            if (file.name !in allNeedFileName) {
                if (!file.delete())
                    SharedService.writeDebugLog(mContext, "SpeechDownloader delete failed ${file.name}")
            }
        }
    }

    private var mDownloadFinishListener: DownloadFinishListener? = null
    fun setFinishListener(downloadFinishListener: DownloadFinishListener?) {
        mDownloadFinishListener = downloadFinishListener
    }

    interface DownloadFinishListener {
        fun cancel()
        fun startSetData()
        fun allFinished()
    }
}