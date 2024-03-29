package com.fsmytsai.aiclock

import android.annotation.TargetApi
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import com.fsmytsai.aiclock.model.AlarmClocks
import com.fsmytsai.aiclock.model.Texts
import com.fsmytsai.aiclock.service.app.FixedNotificationManagement
import com.fsmytsai.aiclock.service.app.SharedService
import com.fsmytsai.aiclock.service.app.SpeechDownloader
import com.fsmytsai.aiclock.ui.activity.AlarmActivity
import com.google.gson.Gson
import java.io.File

class AlarmService : Service() {
    private var mAlarmActivity: AlarmActivity? = null
    private val mBinder = LocalBinder()
    private var mMPBGM = MediaPlayer()
    private var mMPNews = MediaPlayer()

    //control
//    private var mIsPausing = false
    private var mNewsCount = 0

    //data
    private var mAlarmClock: AlarmClocks.AlarmClock? = null
    private lateinit var mTexts: Texts
    private val mSoundList = ArrayList<String>()

    override fun onBind(intent: Intent): IBinder? {
        //測試到目前為止發現，僅第一次綁定會呼叫(從startService後)
        mTexts = Gson().fromJson(intent.getStringExtra("TextsJsonStr"), Texts::class.java)
        SharedService.writeDebugLog(this, "AlarmService onBind acId = ${mTexts.acId}")
        mAlarmClock = SharedService.getAlarmClock(this, mTexts.acId)
        if (mAlarmClock != null && mTexts.textList.isNotEmpty()) {

            //排列音檔播放順序
            for (text in mTexts.textList) {
                if (text.description != "time" && text.description != "weather") {
                    mNewsCount++
                    mSoundList.add("news$mNewsCount")
                }

                (0 until text.part_count).mapTo(mSoundList) { "${text.text_id}-$it-${mAlarmClock!!.speaker}" }
            }

            //判斷是否為舊資料
            if (mTexts.isOldData) {
                //第一個播放的如果是 time 則把提示加入 time 之後
                if (mTexts.textList[0].description == "time" && "${mTexts.textList[0].text_id}-0-${mAlarmClock!!.speaker}" == mSoundList[0]) {
                    SharedService.writeDebugLog(this, "AlarmService insert olddata after time")
                    mSoundList.add(1, "olddata")
                } else {
                    //否則第一個播放提示
                    SharedService.writeDebugLog(this, "AlarmService insert olddata to first")
                    mSoundList.add(0, "olddata")
                }
            }

            //最後加上 bye
            mSoundList.add("bye")
        }

        startBGM()

        return mBinder
    }

//    fun resumePlay() {
//        if (!mIsPausing)
//            return
//
//        SharedService.writeDebugLog(this, "AlarmService resumePlay")
//        if (SharedService.isNewsPlaying) {
//            mIsPausing = false
//            mMPBGM.start()
//            mMPNews.start()
//        } else if (SharedService.reRunRunnable) {
//            mIsPausing = false
//            mMPBGM.start()
//            SharedService.reRunRunnable = false
//            mHandler.postDelayed(mRunnable, 5000)
//        }
//    }

//    fun pausePlay() {
//        SharedService.writeDebugLog(this, "AlarmService pausePlay")
//        mIsPausing = true
//        mMPBGM.pause()
//        if (SharedService.isNewsPlaying)
//            mMPNews.pause()
//        else {
//            SharedService.reRunRunnable = true
//            mHandler.removeCallbacksAndMessages(null)
//        }
//    }

    override fun onDestroy() {
        SharedService.writeDebugLog(this, "AlarmService onDestroy")
        mHandler.removeCallbacksAndMessages(null)
        mMPNews.release()
        mMPBGM.release()
        super.onDestroy()
    }

    inner class LocalBinder : Binder() {
        val service: AlarmService
            get() = this@AlarmService
    }

    fun setAlarmActivity(activity: AlarmActivity?) {
        mAlarmActivity = activity
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun playNews(uri: Uri) {
        //雖然開始播放前已全部檢查過，但保險起見也加上 try catch
        try {
            mMPNews.setDataSource(this, uri)
        } catch (e: Exception) {
            SharedService.writeDebugLog(this, "AlarmService playNews setDataSource failed uri = $uri")
            mMPNews.setDataSource(this, Uri.parse("android.resource://$packageName/raw/${SharedService.speakerArr[mAlarmClock!!.speaker]}_lost"))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
            mMPNews.setAudioAttributes(audioAttributes)
        } else {
            mMPNews.setAudioStreamType(AudioManager.STREAM_ALARM)
        }

        mMPNews.setOnCompletionListener {
            mMPNews.release()

            //如果還沒播放 bye 則一直刪，由於是播放完才刪除，所以不可放進下面的 if 中
            if (mSoundList.size > 0)
                mSoundList.removeAt(0)

            if (mSoundList.size > 0) {
                mMPNews = MediaPlayer()
                if (mSoundList[0].startsWith("news") || mSoundList[0] == "olddata" || mSoundList[0] == "bye")
                    playNews(Uri.parse("android.resource://$packageName/raw/${SharedService.speakerArr[mAlarmClock!!.speaker]}_${mSoundList[0]}"))
                else
                    playNews(Uri.fromFile(File("$filesDir/sounds/${mSoundList[0]}.wav")))
            } else {
                mMPBGM.setVolume(1f, 1f)
            }
        }

        mMPNews.prepare()
        mMPNews.start()
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun startBGM() {
        if (mAlarmClock?.backgroundMusic == null)
            mMPBGM.setDataSource(this, Uri.parse("android.resource://$packageName/raw/bgm"))
        else {
            val uri = Uri.parse("$filesDir/bgmSounds/${mAlarmClock!!.backgroundMusic}")
            try {
                mMPBGM.setDataSource(this, uri)
            } catch (e: Exception) {
                SharedService.writeDebugLog(this, "AlarmService startBGM setDataSource failed uri = $uri")
                mMPBGM.setDataSource(this, Uri.parse("android.resource://$packageName/raw/bgm"))
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
            mMPBGM.setAudioAttributes(audioAttributes)
        } else {
            mMPBGM.setAudioStreamType(AudioManager.STREAM_ALARM)
        }
        mMPBGM.setOnCompletionListener {
            mMPBGM.start()
        }
        mMPBGM.prepare()
        mMPBGM.start()

        if (mAlarmClock != null) {
            //只響一次，關閉它
            if (mAlarmClock!!.isRepeatArr.all { !it }) {
                SharedService.writeDebugLog(this, "AlarmService only alarm once")
                val alarmClocks = SharedService.getAlarmClocks(this, mAlarmClock!!.acId > 1000)
                alarmClocks.alarmClockList.find { it.acId == mAlarmClock!!.acId }!!.isOpen = false
                SharedService.updateAlarmClocks(this, alarmClocks, mAlarmClock!!.acId > 1000)

                SharedService.writeDebugLog(this, "AlarmService check Fixed")
                FixedNotificationManagement.check(this)

                SharedService.writeDebugLog(this@AlarmService, "AlarmService start reset within 40m old data alarm")
                resetAlarm()
            } else {
                SharedService.writeDebugLog(this, "AlarmService set next alarm")
                val speechDownloader = SpeechDownloader(this, null)
                speechDownloader.setKeepFileName(mSoundList)
                speechDownloader.setFinishListener(object : SpeechDownloader.DownloadFinishListener {
                    override fun cancel() {
                        SharedService.writeDebugLog(this@AlarmService, "AlarmService cancel start reset within 40m old data alarm")
                        resetAlarm()
                    }

                    override fun startSetData() {

                    }

                    override fun allFinished() {
                        SharedService.writeDebugLog(this@AlarmService, "AlarmService allFinished start reset within 40m old data alarm")
                        resetAlarm()
                    }
                })
                speechDownloader.setAlarmClock(mAlarmClock!!)
            }
        }

        //沒發生意外才開始播報新聞
        if (mSoundList.isNotEmpty())
            mHandler.postDelayed(mRunnable, 5000)

    }

    private fun resetAlarm() {
        val serviceIntent = Intent(this@AlarmService, ResetAlarmService::class.java)
        serviceIntent.putExtra("IsFromMain", true)
        serviceIntent.putExtra("IsCheckTime", true)
        startService(serviceIntent)
    }

    private val mHandler = Handler()
    private val mRunnable = Runnable {
        mMPBGM.setVolume(0.1f, 0.1f)
        if (mSoundList[0].startsWith("news") || mSoundList[0] == "olddata") {
            playNews(Uri.parse("android.resource://$packageName/raw/${SharedService.speakerArr[mAlarmClock!!.speaker]}_${mSoundList[0]}"))
        } else
            playNews(Uri.fromFile(File("$filesDir/sounds/${mSoundList[0]}.wav")))
    }
}
