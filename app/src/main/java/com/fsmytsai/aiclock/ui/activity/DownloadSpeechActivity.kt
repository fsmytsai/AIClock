package com.fsmytsai.aiclock.ui.activity

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.IBinder
import android.support.v7.app.AlertDialog
import android.util.Log
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import com.fsmytsai.aiclock.R
import com.fsmytsai.aiclock.StartDownloadService
import com.fsmytsai.aiclock.model.AlarmClock
import com.fsmytsai.aiclock.service.app.SpeechDownloader
import kotlinx.android.synthetic.main.block_download_dialog.view.*

open class DownloadSpeechActivity : AppCompatActivity() {
    private var mBoundDownloadService = false
    private lateinit var mDownloadService: StartDownloadService

    private lateinit var pbDownloading: ProgressBar
    private lateinit var tvDownloading: TextView
    private lateinit var mDownloadingDialog: AlertDialog

    private lateinit var mCanStartDownloadCallback: CanStartDownloadCallback
    fun bindDownloadService(canStartDownloadCallback: CanStartDownloadCallback) {
        mCanStartDownloadCallback = canStartDownloadCallback
        val intent = Intent(this, StartDownloadService::class.java)
        bindService(intent, downloadServiceConnection, Context.BIND_AUTO_CREATE)
    }

    fun startDownload(alarmClock: AlarmClock, dfl: SpeechDownloader.DownloadFinishListener?): Boolean {
        Log.d("DownloadSpeechActivity", "2")
        val isSuccess = mDownloadService.startDownload(alarmClock, dfl)
        return isSuccess
    }

    private val downloadServiceConnection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as StartDownloadService.LocalBinder
            mDownloadService = binder.service
            mBoundDownloadService = true
            Log.d("DownloadSpeechActivity", "1")
            mDownloadService.setActivity(this@DownloadSpeechActivity) // register
            mCanStartDownloadCallback.start()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBoundDownloadService = false
        }
    }

    fun showDownloadingDialog() {
        val dialogView = layoutInflater.inflate(R.layout.block_download_dialog, null)
        pbDownloading = dialogView.pb_downloading
        tvDownloading = dialogView.tv_downloading

        mDownloadingDialog = AlertDialog.Builder(this)
                .setCancelable(false)
                .setView(dialogView)
                .show()

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun setDownloadProgress(p: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            pbDownloading.setProgress(p, true)
        else
            pbDownloading.progress = p
        tvDownloading.text = "$p/100"
    }

    fun dismissDownloadingDialog() {
        mDownloadingDialog.dismiss()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    interface CanStartDownloadCallback {
        fun start()
    }
}
