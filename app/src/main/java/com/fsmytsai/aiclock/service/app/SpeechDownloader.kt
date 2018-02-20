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
import android.support.v7.app.AlertDialog
import android.util.Log
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
    private var mNeedDownloadCount = 0f
    private var mErrorDownloadCount = 0
    private var mDownloadedCount = 0f
    private lateinit var mAlarmClock: AlarmClock
    var publicTexts = Texts(0, ArrayList())
    private var mPromptData: PromptData? = null
    private val mPromptDataList = ArrayList<Long>()
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

        //沒網路或離響鈴時間小於30秒取消下載
        if (!SharedService.checkNetWork(mContext) || !getPrompt())
            mDownloadFinishListener?.cancel()
    }

    private fun getPrompt(): Boolean {
        val nowCalendar = Calendar.getInstance()
        var addDate = 0

        //排列7天內的DAY_OF_WEEK
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

        mAlarmCalendar.add(Calendar.DATE, addDate)
        mAlarmCalendar.set(Calendar.HOUR_OF_DAY, mAlarmClock.hour)
        mAlarmCalendar.set(Calendar.MINUTE, mAlarmClock.minute)
        mAlarmCalendar.set(Calendar.SECOND, 0)

        var differenceSecond = (mAlarmCalendar.timeInMillis - nowCalendar.timeInMillis) / 1000

        if (differenceSecond < 30) {
            if (mDownloadSpeechActivity != null)
                AlertDialog.Builder(mDownloadSpeechActivity!!)
                        .setTitle("錯誤")
                        .setMessage("時間需自少超過當前時間 30 秒。")
                        .setPositiveButton("知道了", null)
                        .show()

            return false
        }

        if (differenceSecond > 60 * 60 * 24) {
            mPromptDataList.add(differenceSecond / (60 * 60 * 24))
            differenceSecond %= (60 * 60 * 24)
        } else
            mPromptDataList.add(0)

        if (differenceSecond > 60 * 60) {
            mPromptDataList.add(differenceSecond / (60 * 60))
            differenceSecond %= (60 * 60)
        } else
            mPromptDataList.add(0)

        if (differenceSecond > 60)
            mPromptDataList.add(differenceSecond / 60)
        else
            mPromptDataList.add(0)

        if (differenceSecond > 0 && mPromptDataList[0] == 0L && mPromptDataList[1] == 0L && mPromptDataList[2] == 0L)
            mPromptDataList.add(differenceSecond)
        else
            mPromptDataList.add(0)

        if (mDownloadSpeechActivity != null) getPromptData() else getTextData()

        return true
    }

    private fun getPromptData() {
        mDownloadSpeechActivity?.showDownloadingDialog()

        val request = Request.Builder()
                .url("${mDownloadSpeechActivity?.getString(R.string.server_url)}api/getPromptData?day=${mPromptDataList[0]}&hour=${mPromptDataList[1]}&" +
                        "minute=${mPromptDataList[2]}&second=${mPromptDataList[3]}&speaker=${mAlarmClock.speaker}")
                .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call?, e: IOException?) {
                mDownloadSpeechActivity?.runOnUiThread {
                    SharedService.showTextToast(mDownloadSpeechActivity!!, "請檢查網路連線")
                    mDownloadFinishListener?.cancel()
                    mDownloadSpeechActivity?.dismissDownloadingDialog()
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
                        mDownloadFinishListener?.cancel()
                        mDownloadSpeechActivity?.dismissDownloadingDialog()
                    }
                }

            }
        })
    }

    private fun getTextData() {
        val url = if (mPromptDataList[0] > 0 || mPromptDataList[1] > 0 || mPromptDataList[2] > 15)
            "${mContext.getString(R.string.server_url)}api/getTextData?hour=${mAlarmClock.hour}&" +
                    "minute=${mAlarmClock.minute}&speaker=${mAlarmClock.speaker}&category=-1&latitude=1000&longitude=0"
        else
            "${mContext.getString(R.string.server_url)}api/getTextData?hour=${mAlarmClock.hour}&" +
                    "minute=${mAlarmClock.minute}&speaker=${mAlarmClock.speaker}&category=${mAlarmClock.category}&" +
                    "latitude=${mAlarmClock.latitude}&longitude=${mAlarmClock.longitude}"
        val request = Request.Builder()
                .url(url)
                .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call?, e: IOException?) {
                mDownloadSpeechActivity?.runOnUiThread {
                    SharedService.showTextToast(mDownloadSpeechActivity!!, "請檢查網路連線")
                    mDownloadFinishListener?.cancel()
                    mDownloadSpeechActivity?.dismissDownloadingDialog()
                }
            }

            override fun onResponse(call: Call?, response: Response?) {
                val statusCode = response?.code()
                val resMessage = response?.body()?.string()
                if (mDownloadSpeechActivity != null)
                    mDownloadSpeechActivity?.runOnUiThread {
                        if (statusCode == 200) {
                            publicTexts = Gson().fromJson(resMessage, Texts::class.java)
                            if (publicTexts.textList.size > 0) {
                                publicTexts.acId = mAlarmClock.acId
                                mDownloadSpeechActivity?.setDownloadProgress(10)
                                downloadSound()
                            } else {
                                SharedService.showTextToast(mDownloadSpeechActivity!!, "無預期錯誤，請重試")
                                mDownloadSpeechActivity?.dismissDownloadingDialog()
                            }
                        } else {
                            SharedService.handleError(mDownloadSpeechActivity!!, statusCode!!, resMessage!!)
                            mDownloadFinishListener?.cancel()
                            mDownloadSpeechActivity?.dismissDownloadingDialog()
                        }
                    }
                else if (statusCode == 200) {
                    publicTexts = Gson().fromJson(resMessage, Texts::class.java)
                    if (publicTexts.textList.size > 0) {
                        publicTexts.acId = mAlarmClock.acId
                        downloadSound()
                    }
                }

            }
        })
    }

    private fun downloadSound() {
        FileDownloader.setup(mContext)

        if (mDownloadSpeechActivity != null) {
            FileDownloader.getImpl().create("${mContext.getString(R.string.server_url)}sounds/${mPromptData!!.data.text_id}-0.wav")
                    .setPath("${mContext.filesDir}/sounds/${mPromptData!!.data.text_id}-0.wav")
                    .setTag(0, "${mPromptData!!.data.text_id}-0")
                    .setCallbackProgressTimes(0)
                    .setAutoRetryTimes(1)
                    .setListener(mQueueTarget)
                    .asInQueueTask()
                    .enqueue()
            mNeedDownloadCount++
        }

        for (text in publicTexts.textList) {
            for (i in 0 until text.part_count) {
                FileDownloader.getImpl().create("${mContext.getString(R.string.server_url)}sounds/${text.text_id}-$i.wav")
                        .setPath("${mContext.filesDir}/sounds/${text.text_id}-$i.wav")
                        .setTag(0, "${text.text_id}-$i")
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

    fun cancelDownloadSound() {
        mIsCanceledDownloadSound = true
        FileDownloader.getImpl().pause(mQueueTarget)
        mDownloadSpeechActivity?.dismissDownloadingDialog()
        mDownloadFinishListener?.cancel()
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
            Log.d("SpeechDownloader", "complete file ${task.getTag(0) as String}")

            completeOrErrorDownload()
        }

        override fun paused(task: BaseDownloadTask, soFarBytes: Int, totalBytes: Int) {
            mPauseList.add(task.getTag(0) as String)
        }

        override fun error(task: BaseDownloadTask, e: Throwable) {
            Log.d("SpeechDownloader", "error file ${task.getTag(0) as String}")
            mErrorDownloadCount++
            //超過 1/4 失敗則取消
            if (mErrorDownloadCount > mNeedDownloadCount / 4) {
                if (mDownloadSpeechActivity != null)
                    SharedService.showTextToast(mDownloadSpeechActivity!!, "下載失敗")

                //避免重複呼叫取消
                if (!mIsCanceledDownloadSound)
                    cancelDownloadSound()
            } else
                completeOrErrorDownload()
        }

        override fun warn(task: BaseDownloadTask) {}
    }

    fun completeOrErrorDownload() {
        mDownloadedCount++
        mDownloadSpeechActivity?.setDownloadProgress(10 + (mDownloadedCount / mNeedDownloadCount * 90).toInt())

        if (mDownloadedCount == mNeedDownloadCount) {
            Log.d("SpeechDownloader", "Download Finish")
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
                realComplete()
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun startPlaying(uri: Uri, isSetFinish: Boolean) {
        val mpFinish = MediaPlayer()
        mpFinish.setDataSource(mContext, uri)
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
                val file = File("${mContext.filesDir}/sounds/${mPromptData!!.data.text_id}-0.wav")
                val promptUri = Uri.fromFile(file)
                startPlaying(promptUri, false)
            } else {
                realComplete()
            }
        }
        mpFinish.setVolume(1f, 1f)
        mpFinish.prepare()
        mpFinish.start()
    }

    private fun setData() {
        mDownloadFinishListener?.startSetData()
        setAlarm()
        SharedService.deleteOldTextsData(mContext, mAlarmClock.acId, publicTexts, true)
    }

    private fun realComplete() {
        mDownloadSpeechActivity?.dismissDownloadingDialog()
        mDownloadFinishListener?.finish()
    }

    private val mAlarmCalendar = Calendar.getInstance()

    @TargetApi(Build.VERSION_CODES.M)
    private fun setAlarm() {
        //設置前先嘗試取消，避免重複設置
        SharedService.cancelAlarm(mContext, mAlarmClock.acId)

        //超過15分鐘才響鈴則設置一個提前15分鐘的任務重新抓取新聞天氣
        val intent = if (mPromptDataList[0] > 0 || mPromptDataList[1] > 0 || mPromptDataList[2] > 15) {
            Log.d("SpeechDownloader", "設置鬧鐘成功，${mPromptDataList[0]}天${mPromptDataList[1]}小時${mPromptDataList[2]}分鐘後響鈴")
            mAlarmCalendar.add(Calendar.MINUTE, -15)
            Intent(mContext, PrepareReceiver::class.java)
        } else {
            Log.d("SpeechDownloader", "設置鬧鐘成功，15分鐘內響鈴，${mPromptDataList[2]}分鐘${mPromptDataList[3]}秒後響鈴")
            Intent(mContext, AlarmReceiver::class.java)
        }
        intent.putExtra("ACId", mAlarmClock.acId)
        val pi = PendingIntent.getBroadcast(mContext, mAlarmClock.acId, intent, PendingIntent.FLAG_ONE_SHOT)
        val am = mContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, mAlarmCalendar.timeInMillis, pi)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> am.setExact(AlarmManager.RTC_WAKEUP, mAlarmCalendar.timeInMillis, pi)
            else -> am.set(AlarmManager.RTC_WAKEUP, mAlarmCalendar.timeInMillis, pi)
        }
    }

    private var mDownloadFinishListener: DownloadFinishListener? = null
    fun setFinishListener(downloadFinishListener: DownloadFinishListener?) {
        mDownloadFinishListener = downloadFinishListener
    }

    interface DownloadFinishListener {
        fun cancel()
        fun startSetData()
        fun finish()
    }
}