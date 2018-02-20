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
import com.fsmytsai.aiclock.ui.activity.MainActivity
import com.google.gson.Gson
import java.io.File

class AlarmService : Service() {
    private val mBinder = LocalBinder()
    private var mMainActivity: MainActivity? = null
    private var mMPBGM = MediaPlayer()
    private var mMPNews = MediaPlayer()
    private var mIsByePlaying = false
    private var mIsPausing = false
    private lateinit var mTexts: Texts
    private val mSoundList = ArrayList<String>()
    private var mNewsCount = 0

    override fun onBind(intent: Intent): IBinder? {
        //測試到目前為止發現，僅第一次綁定會呼叫(從startService後)

        mTexts = Gson().fromJson(intent.getStringExtra("TextsJsonStr"), Texts::class.java)

        //檢查檔案是否存在
        for (text in mTexts.textList) {
            val addToSoundList = (0 until text.part_count)
                    .map { File("$filesDir/sounds/${text.text_id}-$it.wav") }
                    .all { it.exists() }
            if (addToSoundList) {
                if (text.description != "time" && text.description != "weather") {
                    mNewsCount++
                    mSoundList.add("news$mNewsCount")
                }

                (0 until text.part_count).mapTo(mSoundList) { "${text.text_id}-$it" }
            }
        }

        startBGM()

        return mBinder
    }

    fun resumePlay() {
        if (!mIsPausing)
            return

        SharedService.writeDebugLog("AlarmService resumePlay")
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

    fun pausePlay() {
        SharedService.writeDebugLog("AlarmService pausePlay")
        mIsPausing = true
        mMPBGM.pause()
        if (SharedService.isNewsPlaying)
            mMPNews.pause()
        else {
            SharedService.reRunRunnable = true
            mHandler.removeCallbacksAndMessages(null)
        }
    }

    override fun onDestroy() {
        SharedService.writeDebugLog("AlarmService onDestroy")
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

    fun setMainActivity(activity: MainActivity?) {
        mMainActivity = activity
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun playNews(uri: Uri) {
        mMPNews.setDataSource(this, uri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
            mMPNews.setAudioAttributes(audioAttributes)
        } else {
            mMPNews.setAudioStreamType(AudioManager.STREAM_ALARM)
        }
        mMPNews.setOnCompletionListener {
            if (!mIsByePlaying) {
                mSoundList.removeAt(0)
            }

            mMPNews.release()

            if (mSoundList.size > 0) {
                mMPNews = MediaPlayer()
                if (mSoundList[0].startsWith("news")) {
                    var spk = "f1"
                    if (mTexts.textList[0].speaker == "HanHanRUS")
                        spk = "f2"
                    else if (mTexts.textList[0].speaker == "Zhiwei, Apollo")
                        spk = "m1"
                    playNews(Uri.parse("android.resource://$packageName/raw/${spk}_${mSoundList[0]}"))
                } else
                    playNews(Uri.fromFile(File("$filesDir/sounds/${mSoundList[0]}.wav")))
            } else if (!mIsByePlaying) {
                mMPNews = MediaPlayer()
                //播放掰掰
                mIsByePlaying = true
                var spk = "f1"
                if (mTexts.textList[0].speaker == "HanHanRUS")
                    spk = "f2"
                else if (mTexts.textList[0].speaker == "Zhiwei, Apollo")
                    spk = "m1"
                playNews(Uri.parse("android.resource://$packageName/raw/${spk}_bye"))
            } else {
                //結束播放
                mIsByePlaying = false
                mMPBGM.setVolume(1f, 1f)
                SharedService.isNewsPlaying = false
            }
        }
        mMPNews.setVolume(1f, 1f)
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

        mHandler.postDelayed(mRunnable, 5000)

        //準備此鬧鐘下一次的響鈴(最快24小時後響)
        val alarmClock = SharedService.getAlarmClock(this, mTexts.acId)
        if (alarmClock != null) {
            val speechDownloader = SpeechDownloader(this, null)
            speechDownloader.setAlarmClock(alarmClock)
        }
    }

    private val mHandler = Handler()
    private val mRunnable = Runnable {
        mMPBGM.setVolume(0.1f, 0.1f)
        playNews(Uri.fromFile(File("$filesDir/sounds/${mSoundList[0]}.wav")))
    }
}
