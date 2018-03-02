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
import com.fsmytsai.aiclock.model.Texts
import com.fsmytsai.aiclock.service.app.SharedService
import com.fsmytsai.aiclock.service.app.SpeechDownloader
import com.fsmytsai.aiclock.ui.activity.AlarmActivity
import com.google.gson.Gson
import java.io.File

class AlarmService : Service() {
    private val mBinder = LocalBinder()
    private var mAlarmActivity: AlarmActivity? = null
    private var mMPBGM = MediaPlayer()
    private var mMPNews = MediaPlayer()
    private var mIsPausing = false
    private var mSpeaker = -1
    private lateinit var mTexts: Texts
    private val mSoundList = ArrayList<String>()
    private var mNewsCount = 0

    override fun onBind(intent: Intent): IBinder? {
        //測試到目前為止發現，僅第一次綁定會呼叫(從startService後)
        mTexts = Gson().fromJson(intent.getStringExtra("TextsJsonStr"), Texts::class.java)
        if (mTexts.acId != 0) {
            val alarmClock = SharedService.getAlarmClock(this, mTexts.acId)!!

            mSpeaker = alarmClock.speaker

            //排列音檔播放順序
            for (text in mTexts.textList) {
                if (text.description != "time" && text.description != "weather") {
                    mNewsCount++
                    mSoundList.add("news$mNewsCount")
                }

                (0 until text.part_count).mapTo(mSoundList) { "${text.text_id}-$it-$mSpeaker" }
            }

            //判斷是否為舊資料
            if (mTexts.isOldData) {
                //第一個播放的如果是 time 則把提示加入 time 之後
                if (mTexts.textList[0].description == "time" && "${mTexts.textList[0].text_id}-0-$mSpeaker" == mSoundList[0]) {
                    SharedService.writeDebugLog(this, "AlarmService insert olddata after time")
                    mSoundList.add(1, "olddata")
                } else {
                    //否則第一個播放提示
                    SharedService.writeDebugLog(this, "AlarmService insert olddata to first")
                    mSoundList.add(0, "olddata")
                }
            }
        }

        //最後加上 bye
        mSoundList.add("bye")

        startBGM()

        return mBinder
    }

    fun resumePlay() {
        if (!mIsPausing)
            return

        SharedService.writeDebugLog(this, "AlarmService resumePlay")
        if (SharedService.isNewsPlaying) {
            mIsPausing = false
            mMPBGM.start()
            mMPNews.start()
        } else if (SharedService.reRunRunnable) {
            mIsPausing = false
            mMPBGM.start()
            SharedService.reRunRunnable = false
            mHandler.postDelayed(mRunnable, 5000)
        }
    }

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
        SharedService.isNewsPlaying = false
        SharedService.reRunRunnable = false
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
            SharedService.writeDebugLog(this, "AlarmService setDataSource failed uri = $uri")
            mMPNews.setDataSource("android.resource://$packageName/raw/${SharedService.speakerArr[mSpeaker]}_lost")
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
                    playNews(Uri.parse("android.resource://$packageName/raw/${SharedService.speakerArr[mSpeaker]}_${mSoundList[0]}"))
                else
                    playNews(Uri.fromFile(File("$filesDir/sounds/${mSoundList[0]}.wav")))
            } else {
                mMPBGM.setVolume(1f, 1f)
                SharedService.isNewsPlaying = false
            }
        }

        mMPNews.prepare()
        mMPNews.start()
        SharedService.isNewsPlaying = true
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun startBGM() {
        mMPBGM.setDataSource(this, Uri.parse("android.resource://$packageName/raw/bgm"))
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
        mMPBGM.setVolume(1f, 1f)
        mMPBGM.prepare()
        mMPBGM.start()

        //沒發生意外才開始播報新聞
        if (mSpeaker != -1) {
            mHandler.postDelayed(mRunnable, 5000)

            //準備此鬧鐘下一次的響鈴(最快24小時後響)
            val alarmClock = SharedService.getAlarmClock(this, mTexts.acId)
            if (alarmClock != null) {
                val speechDownloader = SpeechDownloader(this, null)
                speechDownloader.setAlarmClock(alarmClock)
            }
        }
    }

    private val mHandler = Handler()
    private val mRunnable = Runnable {
        mMPBGM.setVolume(0.1f, 0.1f)
        if (mSoundList[0].startsWith("news") || mSoundList[0] == "olddata") {
            playNews(Uri.parse("android.resource://$packageName/raw/${SharedService.speakerArr[mSpeaker]}_${mSoundList[0]}"))
        } else
            playNews(Uri.fromFile(File("$filesDir/sounds/${mSoundList[0]}.wav")))
    }
}
