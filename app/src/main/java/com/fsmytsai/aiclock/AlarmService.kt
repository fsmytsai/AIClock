package com.fsmytsai.aiclock

import android.annotation.TargetApi
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import com.fsmytsai.aiclock.model.AlarmClocks
import com.fsmytsai.aiclock.model.Texts
import com.fsmytsai.aiclock.service.app.SharedService
import com.fsmytsai.aiclock.service.app.SpeechDownloader
import com.fsmytsai.aiclock.ui.activity.MainActivity
import com.google.gson.Gson
import java.io.File

class AlarmService : Service() {
    private var mIsFirstBind = true
    private val mBinder = LocalBinder()
    private var mMainActivity: MainActivity? = null
    private var mMPBGM = MediaPlayer()
    private var mMPNews = MediaPlayer()
    private var bye = false
    private lateinit var mTexts: Texts
    private val mSoundList = ArrayList<String>()
    private var mIsCompletePlayNews = false

    override fun onBind(intent: Intent): IBinder? {
        Log.d("AlarmService", "${SharedService.isNewsPlaying}")
        if (mIsFirstBind) {
            mIsFirstBind = false
            mTexts = Gson().fromJson(intent.getStringExtra("TextsJsonStr"), Texts::class.java)

            //檢查檔案是否存在
            for (text in mTexts.textList) {
                var addToSoundList = true
                for (i in 0 until text.part_count) {
                    val file = File("$filesDir/sounds/${text.text_id}-$i.wav")
                    if (!file.exists()) {
                        addToSoundList = false
                        break
                    }
                }
                if (addToSoundList) {
                    for (i in 0 until text.part_count)
                        mSoundList.add("${text.text_id}-$i")
                }
            }

            startBGM()
        } else if (SharedService.isNewsPlaying) {
            mMPBGM.start()
            mMPNews.start()
        }

        return mBinder
    }

    fun myReBind() {
        Log.d("AlarmService", "${SharedService.isNewsPlaying}")
        if (SharedService.isNewsPlaying) {
            mMPBGM.start()
            mMPNews.start()
        } else if (SharedService.reRunRunnable) {
            mMPBGM.start()
            mHandler.postDelayed(mRunnable, 5000)
        }
    }

    fun myUnBind() {
        Log.d("AlarmService", "暫停")
        mMPBGM.pause()
        if (SharedService.isNewsPlaying)
            mMPNews.pause()
        else if (!mIsCompletePlayNews) {
            SharedService.reRunRunnable = true
            mHandler.removeCallbacksAndMessages(null)
        }
    }

    override fun onDestroy() {
        Log.d("AlarmService", "釋放")
        mHandler.removeCallbacksAndMessages(null)
        mMPNews.release()
        mMPBGM.release()
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
            if (!bye) {
                mSoundList.removeAt(0)
                var soundListStr = ""
                for (soundStr in mSoundList) {
                    soundListStr += "$soundStr "
                }
                Log.d("AlarmService", soundListStr)
            }

            if (mSoundList.size > 0) {
                mMPNews.release()
                mMPNews = MediaPlayer()
                playNews(Uri.fromFile(File("$filesDir/sounds/${mSoundList[0]}.wav")))
            } else if (!bye) {
                mMPNews.release()
                mMPNews = MediaPlayer()
                //播放掰掰
                bye = true
                var spk = "f1"
                if (mTexts.textList[0].speaker == "HanHanRUS")
                    spk = "f2"
                else if (mTexts.textList[0].speaker == "Zhiwei, Apollo")
                    spk = "m1"
                playNews(Uri.parse("android.resource://$packageName/raw/bye$spk"))
            } else {
                //結束播放
                mMPNews.release()
                mMPBGM.setVolume(1f, 1f)
                mIsCompletePlayNews = true
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
        val spDatas = getSharedPreferences("Datas", Context.MODE_PRIVATE)
        val alarmClocksJsonStr = spDatas.getString("AlarmClocksJsonStr", "")
        val alarmClocks = Gson().fromJson(alarmClocksJsonStr, AlarmClocks::class.java)
        for (alarmClock in alarmClocks.alarmClockList) {
            if (alarmClock.acId == mTexts.acId) {
                val speechDownloader = SpeechDownloader(this, false)
                speechDownloader.setAlarmClock(alarmClock)
            }
        }
    }

    private val mHandler = Handler()
    private val mRunnable = Runnable {
        mMPBGM.setVolume(0.1f, 0.1f)
        playNews(Uri.fromFile(File("$filesDir/sounds/${mSoundList[0]}.wav")))
    }
}
