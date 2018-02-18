package com.fsmytsai.aiclock.service.app

import android.annotation.TargetApi
import android.app.Activity
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
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.fsmytsai.aiclock.AlarmReceiver
import com.fsmytsai.aiclock.R
import com.fsmytsai.aiclock.model.AlarmClock
import com.fsmytsai.aiclock.model.PromptData
import com.fsmytsai.aiclock.model.Texts
import com.google.gson.Gson
import com.liulishuo.filedownloader.BaseDownloadTask
import com.liulishuo.filedownloader.FileDownloadListener
import com.liulishuo.filedownloader.FileDownloader
import kotlinx.android.synthetic.main.block_download_dialog.view.*
import okhttp3.*
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList

/**
 * Created by tsaiminyuan on 2018/2/18.
 */

class SpeechDownloader(context: Context, inActivity: Boolean) {
    private var mContext = context
    private val mActivity: Activity? = if (inActivity) context as Activity else null
    private var mInActivity = inActivity
    private var mNeedDownloadCount = 0f
    private var mDownloadedCount = 0f
    private lateinit var mAlarmClock: AlarmClock
    var publicTexts = Texts(0, ArrayList())
    private var mPromptData: PromptData? = null
    private lateinit var pbDownloading: ProgressBar
    private lateinit var tvDownloading: TextView
    private lateinit var dialogDownloading: AlertDialog
    private val mPromptDataList = ArrayList<Long>()

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
        getPrompt()
    }

    private fun getPrompt() {
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

        if (differenceSecond < 40) {
            Toast.makeText(mContext, "不可設置太接近當前時間", Toast.LENGTH_SHORT).show()
            return
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

        if (mInActivity)
            getPromptData()
        else
            getTextData()
    }

    private fun getPromptData() {
        val dialogView = mActivity!!.layoutInflater.inflate(R.layout.block_download_dialog, null)
        pbDownloading = dialogView.pb_downloading
        tvDownloading = dialogView.tv_downloading
        dialogDownloading = AlertDialog.Builder(mActivity)
                .setView(dialogView)
                .create()
        dialogDownloading.setCanceledOnTouchOutside(false)
        dialogDownloading.show()

        val request = Request.Builder()
                .url("${mActivity.getString(R.string.server_url)}api/getPromptData?day=${mPromptDataList[0]}&hour=${mPromptDataList[1]}&" +
                        "minute=${mPromptDataList[2]}&second=${mPromptDataList[3]}&speaker=${mAlarmClock.speaker}")
                .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call?, e: IOException?) {
                Toast.makeText(mActivity, "請檢察網路連線", Toast.LENGTH_SHORT).show()
            }

            override fun onResponse(call: Call?, response: Response?) {
                val statusCode = response?.code()
                val resMessage = response?.body()?.string()

                mActivity.runOnUiThread {
                    if (statusCode == 200) {
                        mPromptData = Gson().fromJson(resMessage, PromptData::class.java)
                        if (mPromptData!!.is_success) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                                pbDownloading.setProgress(5, true)
                            else
                                pbDownloading.progress = 5
                            tvDownloading.text = "5/100"
                            getTextData()
                        } else {
                            Toast.makeText(mActivity, "Error", Toast.LENGTH_SHORT).show()
                            dialogDownloading.dismiss()
                            mActivity.window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                        }
                    } else {
                        Toast.makeText(mActivity, "$statusCode Error", Toast.LENGTH_SHORT).show()
                        dialogDownloading.dismiss()
                        mActivity.window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                    }
                }

            }
        })
    }

    private fun getTextData() {
        val url = if (mPromptDataList[0] > 0 || mPromptDataList[1] > 0 || mPromptDataList[2] > 30)
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
                Toast.makeText(mContext, "請檢察網路連線", Toast.LENGTH_SHORT).show()
            }

            override fun onResponse(call: Call?, response: Response?) {
                val statusCode = response?.code()
                val resMessage = response?.body()?.string()
                if (mInActivity)
                    mActivity!!.runOnUiThread {
                        if (statusCode == 200) {
                            publicTexts = Gson().fromJson(resMessage, Texts::class.java)
                            if (publicTexts.textList.size > 0) {
                                publicTexts.acId = mAlarmClock.acId
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                                    pbDownloading.setProgress(10, true)
                                else
                                    pbDownloading.progress = 10
                                tvDownloading.text = "10/100"
                                downloadSound()
                            } else {
                                Toast.makeText(mActivity, "Error", Toast.LENGTH_SHORT).show()
                                dialogDownloading.dismiss()
                                mActivity.window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                            }
                        } else {
                            Toast.makeText(mActivity, "$statusCode Error", Toast.LENGTH_SHORT).show()
                            dialogDownloading.dismiss()
                            mActivity.window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
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

        if (mInActivity) {
            FileDownloader.getImpl().create("${mContext.getString(R.string.server_url)}sounds/${mPromptData!!.data.text_id}-0.wav")
                    .setPath("${mContext.filesDir}/sounds/${mPromptData!!.data.text_id}-0.wav")
                    .setTag(0, "${mPromptData!!.data.text_id}-0")
                    .setTag(2, true)
                    .setCallbackProgressTimes(0)
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
                        .setTag(2, false)
                        .setCallbackProgressTimes(0)
                        .setListener(mQueueTarget)
                        .asInQueueTask()
                        .enqueue()
                mNeedDownloadCount++
            }
        }
        FileDownloader.getImpl().start(mQueueTarget, false)
    }

    private val mQueueTarget: FileDownloadListener = object : FileDownloadListener() {
        override fun pending(task: BaseDownloadTask, soFarBytes: Int, totalBytes: Int) {}

        override fun connected(task: BaseDownloadTask?, etag: String?, isContinue: Boolean, soFarBytes: Int, totalBytes: Int) {}

        override fun progress(task: BaseDownloadTask, soFarBytes: Int, totalBytes: Int) {}

        override fun blockComplete(task: BaseDownloadTask?) {}

        override fun retry(task: BaseDownloadTask?, ex: Throwable?, retryingTimes: Int, soFarBytes: Int) {}

        override fun completed(task: BaseDownloadTask) {
            Log.d("AddAlarmClockActivity", "file ${task.getTag(0) as String}")

            mDownloadedCount++

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                pbDownloading.setProgress(10 + (mDownloadedCount / mNeedDownloadCount * 90).toInt(), true)
            else
                pbDownloading.progress = 10 + (mDownloadedCount / mNeedDownloadCount * 90).toInt()

            tvDownloading.text = "${10 + (mDownloadedCount / mNeedDownloadCount * 90).toInt()}/100"

            if (mDownloadedCount == mNeedDownloadCount && mInActivity) {
                mDownloadedCount = 0f
                mNeedDownloadCount = 0f
                pbDownloading.progress = 100
                tvDownloading.text = "100/100"

                Log.d("AddAlarmClockActivity", "Download Finish")
                var uri: Uri? = null
                when (mAlarmClock.speaker) {
                    0 -> uri = Uri.parse("android.resource://${mContext.packageName}/raw/setfinishf1")
                    1 -> uri = Uri.parse("android.resource://${mContext.packageName}/raw/setfinishf2")
                    2 -> uri = Uri.parse("android.resource://${mContext.packageName}/raw/setfinishm1")
                }
                startPlaying(uri!!, true)
            }
        }

        override fun paused(task: BaseDownloadTask, soFarBytes: Int, totalBytes: Int) {}

        override fun error(task: BaseDownloadTask, e: Throwable) {
            Log.d("AddAlarmClockActivity", "error file ${task.getTag(0) as String}")
        }

        override fun warn(task: BaseDownloadTask) {}
    }

    private lateinit var mMPFinish: MediaPlayer

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun startPlaying(uri: Uri, isSetFinish: Boolean) {
        mMPFinish = MediaPlayer()
        mMPFinish.setDataSource(mContext, uri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
            mMPFinish.setAudioAttributes(audioAttributes)
        } else {
            mMPFinish.setAudioStreamType(AudioManager.STREAM_ALARM)
        }
        mMPFinish.setOnCompletionListener {
            if (isSetFinish) {
                val promptUri = Uri.fromFile(File("${mContext.filesDir}/sounds/${mPromptData!!.data.text_id}-0.wav"))
                startPlaying(promptUri, false)
            } else {
                dialogDownloading.dismiss()
                setAlarm()
                mFinishListener?.finish()
            }
        }
        mMPFinish.setVolume(1f, 1f)
        mMPFinish.prepare()
        mMPFinish.start()
    }

    private val mAlarmCalendar = Calendar.getInstance()

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun setAlarm() {
        //超過30分鐘才響鈴則設置一個提前30分鐘的任務重新抓取新聞天氣
        val intent = if (mPromptDataList[0] > 0 || mPromptDataList[1] > 0 || mPromptDataList[2] > 30) {
            mAlarmCalendar.add(Calendar.MINUTE, -30)
            Intent(mContext, AlarmReceiver::class.java)
        } else {
            Intent(mContext, AlarmReceiver::class.java)
        }
        intent.putExtra("ACId", mAlarmClock.acId)
        val pi = PendingIntent.getBroadcast(mContext, mAlarmClock.acId, intent, PendingIntent.FLAG_ONE_SHOT)
        val am = mContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(mAlarmCalendar.timeInMillis, pi)
                am.setAlarmClock(alarmClockInfo, pi)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> am.setExact(AlarmManager.RTC_WAKEUP, mAlarmCalendar.timeInMillis, pi)
            else -> am.set(AlarmManager.RTC_WAKEUP, mAlarmCalendar.timeInMillis, pi)
        }
    }

    private var mFinishListener: FinishListener? = null
    fun setFinishListener(finishListener: FinishListener) {
        mFinishListener = finishListener
    }

    interface FinishListener {
        fun finish()
    }
}