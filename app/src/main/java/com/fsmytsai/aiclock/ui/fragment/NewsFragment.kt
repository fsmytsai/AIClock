package com.fsmytsai.aiclock.ui.fragment


import android.annotation.TargetApi
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.fsmytsai.aiclock.R
import com.fsmytsai.aiclock.model.Texts
import com.fsmytsai.aiclock.model.TextsList
import com.fsmytsai.aiclock.ui.activity.MainActivity
import com.google.gson.Gson
import java.io.File


/**
 * A simple [Fragment] subclass.
 */
class NewsFragment : Fragment() {
    private lateinit var mMainActivity: MainActivity
    private var mMPBGM = MediaPlayer()
    private var mMPNews = MediaPlayer()
    private var mIsNewsPlaying = false
    private lateinit var mTexts: Texts
    private val mSoundList = ArrayList<String>()
    private var bye = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_news, container, false)
        mMainActivity = activity as MainActivity
        getTexts()
        initViews(view)
        startBGM()

        return view
    }

    override fun onStop() {
        super.onStop()
        mMPNews.release()
        mMPBGM.release()
    }

    private fun initViews(view: View) {}

    private fun getTexts() {
        val spDatas = mMainActivity.getSharedPreferences("Datas", Context.MODE_PRIVATE)
        val textsList: TextsList
        val textsListJsonStr = spDatas.getString("TextsListJsonStr", "")
        textsList = Gson().fromJson(textsListJsonStr, TextsList::class.java)
        for (texts in textsList.textsList) {
            if (texts.acId == mMainActivity.acId) {
                mTexts = texts
                break
            }
        }

        for (text in mTexts.textList) {
            if (text.completeDownloadCount == text.part_count)
                for (i in 0..text.part_count - 1) {
                    mSoundList.add("${text.text_id}-$i")
                }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun playNews(uri: Uri) {
        mMPNews = MediaPlayer()
        mMPNews.setDataSource(mMainActivity, uri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
            mMPNews.setAudioAttributes(audioAttributes)
        } else {
            mMPNews.setAudioStreamType(AudioManager.STREAM_ALARM)
        }
        mMPNews.setOnCompletionListener {
            mIsNewsPlaying = false
            mSoundList.removeAt(0)
            if (mSoundList.size != 0)
                playNews(Uri.fromFile(File("${mMainActivity.filesDir}/sounds/${mSoundList[0]}.wav")))
            else if (!bye) {
                bye = true
                mMPBGM.stop()
                var spk = "f1"
                if (mTexts.textList[0].speaker == "HanHanRUS")
                    spk = "f2"
                else if (mTexts.textList[0].speaker == "Zhiwei, Apollo")
                    spk = "m1"
                playNews(Uri.parse("android.resource://${mMainActivity.packageName}/raw/bye$spk"))
            }
        }
        mMPNews.setVolume(1f, 1f)
        mMPNews.prepare()
        mMPNews.start()
        mIsNewsPlaying = true
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun startBGM() {
        mMPBGM.setDataSource(mMainActivity, Uri.parse("android.resource://${mMainActivity.packageName}/raw/bgm"))
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

        Handler().postDelayed({
            mMPBGM.setVolume(0.2f, 0.2f)
            playNews(Uri.fromFile(File("${mMainActivity.filesDir}/sounds/${mSoundList[0]}.wav")))
        }, 5000)
    }
}
