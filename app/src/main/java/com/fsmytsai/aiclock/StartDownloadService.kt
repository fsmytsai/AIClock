package com.fsmytsai.aiclock

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Binder
import com.fsmytsai.aiclock.model.AlarmClock
import com.fsmytsai.aiclock.service.app.SpeechDownloader
import com.fsmytsai.aiclock.ui.activity.DownloadSpeechActivity


class StartDownloadService : Service() {
    private val mBinder = LocalBinder()
    private var mDownloadSpeechActivity: DownloadSpeechActivity? = null

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

    fun startDownload(alarmClock: AlarmClock, dfl: SpeechDownloader.DownloadFinishListener?): Boolean {
        outDownloadFinishListener = dfl
        val speechDownloader = SpeechDownloader(this, mDownloadSpeechActivity)
        speechDownloader.setFinishListener(inDownloadFinishListener)
        val isSuccess = speechDownloader.setAlarmClock(alarmClock)
        return isSuccess
    }

    var outDownloadFinishListener: SpeechDownloader.DownloadFinishListener? = null

    private val inDownloadFinishListener = object : SpeechDownloader.DownloadFinishListener {
        override fun finish() {
            outDownloadFinishListener?.finish()
            stopSelf()
        }
    }
}
