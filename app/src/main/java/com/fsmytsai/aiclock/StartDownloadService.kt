package com.fsmytsai.aiclock

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Binder
import com.fsmytsai.aiclock.model.AlarmClocks
import com.fsmytsai.aiclock.service.app.SharedService
import com.fsmytsai.aiclock.service.app.SpeechDownloader
import com.fsmytsai.aiclock.ui.activity.DownloadSpeechActivity


class StartDownloadService : Service() {
    private val mBinder = LocalBinder()
    private var mDownloadSpeechActivity: DownloadSpeechActivity? = null
    private var mSpeechDownloader: SpeechDownloader? = null

    override fun onBind(intent: Intent): IBinder? {
        return mBinder
    }

    inner class LocalBinder : Binder() {
        val service: StartDownloadService
            get() = this@StartDownloadService
    }

    fun setActivity(activity: DownloadSpeechActivity) {
        mDownloadSpeechActivity = activity
    }

    fun startDownload(alarmClock: AlarmClocks.AlarmClock, dfl: SpeechDownloader.DownloadFinishListener?) {
        mSpeechDownloader = SpeechDownloader(this, mDownloadSpeechActivity)
        mSpeechDownloader!!.setFinishListener(dfl)
        mSpeechDownloader!!.setAlarmClock(alarmClock)
    }

    fun stopDownloadSound() {
        mSpeechDownloader?.stopDownloadSound()
    }

    fun resumeDownloadSound() {
        mSpeechDownloader?.resumeDownloadSound()
    }

    fun cancelDownloadSound() {
        mSpeechDownloader?.foregroundCancelDownloadSound()
    }

    override fun onDestroy() {
        SharedService.writeDebugLog(this, "StartDownloadService onDestroy")
        super.onDestroy()
    }
}
