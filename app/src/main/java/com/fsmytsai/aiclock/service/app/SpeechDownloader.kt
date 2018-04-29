package com.fsmytsai.aiclock.service.app

import android.annotation.TargetApi
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.support.annotation.RequiresApi
import android.support.v7.app.AlertDialog
import com.fsmytsai.aiclock.AlarmReceiver
import com.fsmytsai.aiclock.PrepareReceiver
import com.fsmytsai.aiclock.R
import com.fsmytsai.aiclock.model.AlarmClocks
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
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class SpeechDownloader(context: Context, activity: DownloadSpeechActivity?) {
    private var mContext = context
    private var mDownloadSpeechActivity = activity

    //data
    private lateinit var mAlarmClock: AlarmClocks.AlarmClock
    private var mTexts: Texts? = null
    private var mPromptData: PromptData? = null
    private val mAlarmTimeList = ArrayList<Long>()
    private lateinit var mAlarmCalendar: Calendar

    //control
    private var mIsMute = false
    private var mNeedDownloadCount = 0f
    private var mErrorDownloadCount = 0
    private var mDownloadedCount = 0f
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

    private val mHandler = Handler()
    private val mRunnable = Runnable {
        if (mDownloadedCount == mNeedDownloadCount) {
            SharedService.writeDebugLog(mContext, "SpeechDownloader overtime but download is finished")
            return@Runnable
        }

        if (mIsCanceledDownloadSound) {
            SharedService.writeDebugLog(mContext, "SpeechDownloader overtime but download is canceled")
            return@Runnable
        }

        if (mDownloadSpeechActivity != null) {
            SharedService.writeDebugLog(mContext, "SpeechDownloader overtime foreground")
            SharedService.showTextToast(mDownloadSpeechActivity!!, "設置超時")
            foregroundCancelDownloadSound()
        } else {
            SharedService.writeDebugLog(mContext, "SpeechDownloader overtime background and reset alarm time")
            //mRunnable 時間可能已錯亂
            getAlarmTime()
            backgroundCancelDownloadSound()
        }
    }

    fun setAlarmClock(alarmClock: AlarmClocks.AlarmClock) {
        SharedService.writeDebugLog(mContext, "SpeechDownloader setAlarmClock ACId = ${alarmClock.acId}")

        mAlarmClock = alarmClock

        //倒數計時 60 秒
        mHandler.postDelayed(mRunnable, 60000)

        if (mDownloadSpeechActivity != null) {
            //前景
            val spDatas = mContext.getSharedPreferences("Datas", Context.MODE_PRIVATE)
            mIsMute = spDatas.getBoolean("IsMute", false)
            //延遲也靜音
            if (!mIsMute)
                mIsMute = mAlarmClock.acId > 1000

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
                            if (getAlarmTime())
                                checkLatestUrl()
                            else
                                foregroundCancelDownloadSound()
                        })
                        .setNegativeButton("取消", { _, _ ->
                            foregroundCancelDownloadSound()
                        })
                        .show()
            } else {
                //沒網路或離響鈴時間小於30秒取消下載
                if (getAlarmTime())
                    checkLatestUrl()
                else
                    foregroundCancelDownloadSound()
            }

        } else {
            //背景
            //沒網路或離響鈴時間小於30秒取消下載
            if (getAlarmTime())
                checkLatestUrl()
            else
                backgroundCancelDownloadSound()
        }
    }

    private fun getAlarmTime(): Boolean {
        val nowCalendar = Calendar.getInstance()
        mAlarmCalendar = SharedService.getAlarmCalendar(mAlarmClock)

        var differenceSecond = (mAlarmCalendar.timeInMillis - nowCalendar.timeInMillis) / 1000

        mAlarmTimeList.clear()

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

        if (differenceSecond >= 60) {
            mAlarmTimeList.add(differenceSecond / 60)
            differenceSecond %= 60
        } else
            mAlarmTimeList.add(0)

        if (differenceSecond > 0 && mAlarmTimeList[0] == 0L && mAlarmTimeList[1] == 0L)
            mAlarmTimeList.add(differenceSecond)
        else
            mAlarmTimeList.add(0)

        SharedService.writeDebugLog(mContext, "SpeechDownloader mAlarmTimeList = ${mAlarmTimeList[0]} ${mAlarmTimeList[1]} ${mAlarmTimeList[2]} ${mAlarmTimeList[3]}")

        //檢查網路，前景執行就顯示無網路提示
        if (!SharedService.checkNetWork(mContext, mDownloadSpeechActivity != null))
            return false

        return true
    }

    private fun checkLatestUrl() {
        SharedService.writeDebugLog(mContext, "SpeechDownloader checkLatestUrl")
        val request = Request.Builder()
                .url("${SharedService.getLatestUrl(mContext)}api/getLatestUrl")
                .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call?, e: IOException?) {
                if (mDownloadSpeechActivity != null)
                    mDownloadSpeechActivity?.runOnUiThread {
                        SharedService.showTextToast(mDownloadSpeechActivity!!, "請檢查網路連線")
                        foregroundCancelDownloadSound()
                    }
                else
                    backgroundCancelDownloadSound()
            }

            override fun onResponse(call: Call?, response: Response?) {
                val statusCode = response?.code()
                val resMessage = response?.body()?.string()

                when {
                    mDownloadSpeechActivity != null -> mDownloadSpeechActivity?.runOnUiThread {
                        if (statusCode == 200) {
                            val latestUrl = Gson().fromJson(resMessage, String::class.java)
                            if (SharedService.getLatestUrl(mContext) != latestUrl) {
                                SharedService.writeDebugLog(mContext, "SpeechDownloader update latestUrl to $latestUrl")
                                mContext.getSharedPreferences("Datas", Context.MODE_PRIVATE).edit().putString("LatestUrl", latestUrl).apply()
                            }
                            if (mIsMute) {
                                mDownloadSpeechActivity?.showDownloadingDialog()
                                getTextsData()
                            } else
                                getPromptData()
                        } else {
                            SharedService.handleError(mDownloadSpeechActivity!!, statusCode!!, resMessage!!)
                            foregroundCancelDownloadSound()
                        }
                    }
                    statusCode == 200 -> {
                        val latestUrl = Gson().fromJson(resMessage, String::class.java)
                        if (SharedService.getLatestUrl(mContext) != latestUrl) {
                            SharedService.writeDebugLog(mContext, "SpeechDownloader update latestUrl to $latestUrl")
                            mContext.getSharedPreferences("Datas", Context.MODE_PRIVATE).edit().putString("LatestUrl", latestUrl).apply()
                        }
                        //如果有開啟天氣且為背景則設置座標
                        if (mAlarmClock.latitude != 1000.0)
                            setLocation()
                        else
                            getTextsData()
                    }
                    else -> //背景失敗
                        backgroundCancelDownloadSound()
                }
            }
        })
    }

    private fun getPromptData() {
        SharedService.writeDebugLog(mContext, "SpeechDownloader getPromptData")
        mDownloadSpeechActivity?.showDownloadingDialog()

        val request = Request.Builder()
                .url("${SharedService.getLatestUrl(mContext)}api/getPromptData?day=${mAlarmTimeList[0]}&hour=${mAlarmTimeList[1]}&" +
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
                        getTextsData()
                    } else {
                        SharedService.handleError(mDownloadSpeechActivity!!, statusCode!!, resMessage!!)
                        foregroundCancelDownloadSound()
                    }
                }

            }
        })
    }

    private fun setLocation() {
        SharedService.writeDebugLog(mContext, "SpeechDownloader setLocation lat = ${mAlarmClock.latitude} lon = ${mAlarmClock.longitude}")
        val isSuccess = SharedService.setLocation(mContext, mAlarmClock)
        if (isSuccess) {
            SharedService.writeDebugLog(mContext, "SpeechDownloader setLocation success lat = ${mAlarmClock.latitude} lon = ${mAlarmClock.longitude}")
            getTextsData()
        } else
            mHandler.postDelayed({
                SharedService.writeDebugLog(mContext, "SpeechDownloader setLocation delay 3s lat = ${mAlarmClock.latitude} lon = ${mAlarmClock.longitude}")
                getTextsData()
            }, 1500)
    }

    private fun getTextsData() {
        SharedService.writeDebugLog(mContext, "SpeechDownloader getTextsData")

        val url = "${SharedService.getLatestUrl(mContext)}api/getTextData?" +
                "hour=${mAlarmClock.hour}&minute=${mAlarmClock.minute}&" +
                "speaker=${mAlarmClock.speaker}&category=${mAlarmClock.category}&news_count=${mAlarmClock.newsCount}&" +
                "latitude=${mAlarmClock.latitude}&longitude=${mAlarmClock.longitude}&" +
                "version_code=${SharedService.getVersionCode(mContext)}"

        val request = Request.Builder()
                .url(url)
                .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call?, e: IOException?) {
                if (mDownloadSpeechActivity != null)
                    mDownloadSpeechActivity?.runOnUiThread {
                        SharedService.showTextToast(mDownloadSpeechActivity!!, "請檢查網路連線")
                        foregroundCancelDownloadSound()
                    }
                else
                    backgroundCancelDownloadSound()
            }

            override fun onResponse(call: Call?, response: Response?) {
                val statusCode = response?.code()
                val resMessage = response?.body()?.string()
                val dateFormatter = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.TAIWAN)
                val nowCalendar = Calendar.getInstance()
                if (mDownloadSpeechActivity != null)
                    mDownloadSpeechActivity?.runOnUiThread {
                        if (statusCode == 200) {
                            mTexts = Gson().fromJson(resMessage, Texts::class.java)
                            if (mTexts!!.textList.size > 0) {
                                mDownloadSpeechActivity?.setDownloadProgress(10)
                                mTexts!!.acId = mAlarmClock.acId
                                mTexts!!.time = dateFormatter.format(nowCalendar.time)
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
                        mTexts!!.time = dateFormatter.format(nowCalendar.time)
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
        SharedService.writeDebugLog(mContext, "SpeechDownloader downloadSound")

        //初始化 FileDownloader
        FileDownloader.setup(mContext)

        if (mDownloadSpeechActivity != null && !mIsMute) {
            FileDownloader.getImpl().create("${SharedService.getLatestUrl(mContext)}sounds/${mPromptData!!.data.text_id}-0-${mAlarmClock.speaker}.wav")
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
                FileDownloader.getImpl().create("${SharedService.getLatestUrl(mContext)}sounds/${text.text_id}-$i-${mAlarmClock.speaker}.wav")
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

        if (!mIsStoppedDownloadSound && !mIsCanceledDownloadSound)
            FileDownloader.getImpl().start(mQueueTarget, false)
    }

    fun stopDownloadSound() {
        if (mIsStartedDownloadSound) {
            SharedService.writeDebugLog(mContext, "SpeechDownloader stopDownloadSound mIsStartedDownloadSound")
            FileDownloader.getImpl().pause(mQueueTarget)
            FileDownloader.getImpl().unBindService()
        } else
            SharedService.writeDebugLog(mContext, "SpeechDownloader stopDownloadSound")

        mIsStoppedDownloadSound = true
    }

    fun resumeDownloadSound() {
        if (mIsCanceledDownloadSound) {
            SharedService.writeDebugLog(mContext, "SpeechDownloader resumeDownloadSound but download is canceled")
            return
        }

        SharedService.writeDebugLog(mContext, "SpeechDownloader resumeDownloadSound")

        mIsStoppedDownloadSound = false

        //完成取得資料但還沒開始下載
        if (mTexts != null && !mIsStartedDownloadSound)
            FileDownloader.getImpl().start(mQueueTarget, false)

        //已開始下載，恢復task
        if (mIsStartedDownloadSound) {
            while (mPauseList.size > 0) {
                FileDownloader.getImpl().create("${SharedService.getLatestUrl(mContext)}sounds/${mPauseList[0]}.wav")
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
            SharedService.writeDebugLog(mContext, "SpeechDownloader download completed ${task.getTag(0) as String}")
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
        //取消倒數計時
        mHandler.removeCallbacksAndMessages(null)

        SharedService.writeDebugLog(mContext, "SpeechDownloader ACId = ${mAlarmClock.acId} foregroundCancelDownloadSound")
        mIsCanceledDownloadSound = true
        stopDownloadSound()

        mDownloadFinishListener?.cancel()
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun backgroundCancelDownloadSound() {
        //取消倒數計時
        mHandler.removeCallbacksAndMessages(null)

        SharedService.writeDebugLog(mContext, "SpeechDownloader ACId = ${mAlarmClock.acId} backgroundCancelDownloadSound")
        mIsCanceledDownloadSound = true
        stopDownloadSound()

        //離響鈴小於 30 秒(重開機時造成)， 10 分鐘後重試
        if (mAlarmTimeList.size == 0) {
            SharedService.writeDebugLog(mContext, "SpeechDownloader less than 30s,10m later retry ")
            retryDownloadSound(10)
        } else {
            //距離響鈴時間的分鐘數
            val alarmMinute = mAlarmTimeList[0] * 24 * 60 + mAlarmTimeList[1] * 60 + mAlarmTimeList[2]
            //離響鈴還超過 27 分鐘(重開機或響鈴的瞬間可能會取得超大分鐘數)， x 分鐘後重試(距離響鈴時間的 1/3 ，小於 10 分鐘的話則使用 10 分鐘)
            //避免 9 * 3 造成的延遲響鈴 (響鈴時間太接近的鬧鐘過多可能還是會造成響鈴延遲)
            if (alarmMinute > 27) {
                val retryMinute = if (alarmMinute / 3 > 10) alarmMinute / 3 else 10
                SharedService.writeDebugLog(mContext, "SpeechDownloader more than 27m($alarmMinute) ${retryMinute}m later retry")
                retryDownloadSound(retryMinute.toInt())
            } else {
                //剩下不到 27 分鐘響鈴，直接播放舊資料及音檔
                SharedService.writeDebugLog(mContext, "SpeechDownloader less than 27m($alarmMinute),display old data and play old sound")
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
            //取消倒數計時
            mHandler.removeCallbacksAndMessages(null)

            if (mIsCanceledDownloadSound) {
                SharedService.writeDebugLog(mContext, "SpeechDownloader download finish but download is canceled")
                return
            }

            SharedService.writeDebugLog(mContext, "SpeechDownloader download finish")
            FileDownloader.getImpl().unBindService()

            setData()
            if (mDownloadSpeechActivity != null) {
                if (mIsMute) {
                    var prompt = ""
                    if (mAlarmTimeList[0] > 0)
                        prompt += " ${mAlarmTimeList[0]} 天"
                    if (mAlarmTimeList[1] > 0)
                        prompt += " ${mAlarmTimeList[1]} 小時"
                    if (mAlarmTimeList[2] > 0)
                        prompt += " ${mAlarmTimeList[2]} 分鐘"
                    if (mAlarmTimeList[3] > 0)
                        prompt += " ${mAlarmTimeList[3]} 秒"

                    prompt += "後響鈴"

                    SharedService.showTextToast(mDownloadSpeechActivity!!, prompt)

                    allFinished()
                } else {
                    mDownloadSpeechActivity?.setDownloadProgress(100)
                    val uri = Uri.parse("android.resource://${mContext.packageName}/raw/" + when (mAlarmClock.speaker) {
                        0 -> "setfinishf1"
                        1 -> "setfinishf2"
                        2 -> "setfinishm1"
                        else -> ""
                    })
                    startPlaying(uri!!, true)
                }

            } else
                allFinished()
        }
    }

    private fun setData() {
        SharedService.writeDebugLog(mContext, "SpeechDownloader setData")
        mDownloadFinishListener?.startSetData()
        setAlarm(false)
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun setAlarm(isAbandon: Boolean) {
        SharedService.writeDebugLog(mContext, "SpeechDownloader setAlarm")

        val nowCalendar = Calendar.getInstance()
        val differenceSecond = (mAlarmCalendar.timeInMillis - nowCalendar.timeInMillis) / 1000
        //小於 8 秒就響鈴則靜音
        if (differenceSecond < 8)
            mIsMute = true

        //設置前先嘗試取消，避免重複設置
        SharedService.cancelAlarm(mContext, mAlarmClock.acId)

        //超過 40 分鐘才響鈴且需要更新資料及音檔(天氣或新聞)，則設置一個提前 40 分鐘的任務重新抓取新聞天氣
        val alarmIntent = if ((mAlarmTimeList[0] > 0 || mAlarmTimeList[1] > 0 || mAlarmTimeList[2] > 40) &&
                (mAlarmClock.latitude != 1000.0 || mAlarmClock.category != -1)) {
            SharedService.writeDebugLog(mContext, "SpeechDownloader setAlarm success ${mAlarmTimeList[0]}d ${mAlarmTimeList[1]}h ${mAlarmTimeList[2]}m later alarm")
            mAlarmCalendar.add(Calendar.MINUTE, -40)
            Intent(mContext, PrepareReceiver::class.java)
        } else {
            SharedService.writeDebugLog(mContext, "SpeechDownloader setAlarm success ${mAlarmTimeList[2]}m ${mAlarmTimeList[3]}s later alarm")
            //40分鐘內成功設置則轉為新資料
            if (!isAbandon)
                mTexts?.isOldData = false
            Intent(mContext, AlarmReceiver::class.java)
        }
        alarmIntent.putExtra("ACId", mAlarmClock.acId)

        //設置前先更新資料(不是放棄)
        if (!isAbandon)
            SharedService.deleteOldTextsData(mContext, mAlarmClock.acId, mTexts, true)

        val pi = PendingIntent.getBroadcast(mContext, mAlarmClock.acId, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val am = mContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, mAlarmCalendar.timeInMillis, pi)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> am.setExact(AlarmManager.RTC_WAKEUP, mAlarmCalendar.timeInMillis, pi)
            else -> am.set(AlarmManager.RTC_WAKEUP, mAlarmCalendar.timeInMillis, pi)
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun startPlaying(uri: Uri, isSetFinish: Boolean) {
        val mpFinish = MediaPlayer()
        try {
            mpFinish.setDataSource(mContext, uri)
        } catch (e: Exception) {
            SharedService.writeDebugLog(mContext, "SpeechDownloader setDataSource failed uri = $uri")
            mpFinish.setDataSource(mContext, Uri.parse("android.resource://${mContext.packageName}/raw/${SharedService.speakerArr[mAlarmClock.speaker]}_lost"))
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
        SharedService.writeDebugLog(mContext, "SpeechDownloader allFinished")

        //所有工作都結束後清舊資料
        cleanAllOldLater()

        mDownloadFinishListener?.allFinished()
    }

    private fun cleanAllOldLater() {
        SharedService.writeDebugLog(mContext, "SpeechDownloader cleanAllOldLater")
        val laterAlarmClockList = SharedService.getAlarmClocks(mContext, true).alarmClockList
        for (laterAlarmClock in laterAlarmClockList) {
            //刪除已被關或失效的延遲鬧鐘
            if (!laterAlarmClock.isOpen || SharedService.checkNeedReset(mContext, laterAlarmClock.acId, false)) {
                SharedService.writeDebugLog(mContext, "SpeechDownloader deleteLaterAlarmClock acId = ${laterAlarmClock.acId}")
                SharedService.deleteAlarmClock(mContext, laterAlarmClock.acId)
            }

        }
        cleanAllOldSound()
    }

    private val mKeepFileName = ArrayList<String>()

    fun setKeepFileName(keepFileName: ArrayList<String>) {
        keepFileName.mapTo(mKeepFileName) { "$it.wav" }
    }

    private fun cleanAllOldSound() {
        SharedService.writeDebugLog(mContext, "SpeechDownloader cleanAllOldSound")

        //先抓到所有還需要的檔名
        val allNeedFileName = ArrayList<String>()
        val textsList = SharedService.getTextsList(mContext) ?: return

        for (texts in textsList.textsList) {
            for (text in texts.textList)
                for (i in 0 until text.part_count)
                    allNeedFileName.add("${text.text_id}-$i-${mAlarmClock.speaker}.wav")
        }

        allNeedFileName.addAll(mKeepFileName)

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