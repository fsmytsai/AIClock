package com.fsmytsai.aiclock.ui.activity

import android.content.*
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.IBinder
import android.support.v7.app.AlertDialog
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import com.fsmytsai.aiclock.R
import com.fsmytsai.aiclock.StartDownloadService
import com.fsmytsai.aiclock.model.AlarmClock
import com.fsmytsai.aiclock.service.app.SharedService
import com.fsmytsai.aiclock.service.app.SpeechDownloader
import kotlinx.android.synthetic.main.block_download_dialog.view.*

open class DownloadSpeechActivity : AppCompatActivity() {
    private var mDownloadService: StartDownloadService? = null
    private lateinit var pbDownloading: ProgressBar
    private lateinit var tvDownloading: TextView
    private lateinit var mDownloadingDialog: AlertDialog
    private lateinit var mCanStartDownloadCallback: CanStartDownloadCallback
    private var mIsStartedSetData = false
    private var mIsAccidentCanceled = false

    fun bindDownloadService(canStartDownloadCallback: CanStartDownloadCallback) {
        mCanStartDownloadCallback = canStartDownloadCallback
        if (mDownloadService == null) {
            val intent = Intent(this, StartDownloadService::class.java)
            bindService(intent, mDownloadServiceConnection, Context.BIND_AUTO_CREATE)
        } else {
            mCanStartDownloadCallback.start()
        }
    }

    fun startDownload(alarmClock: AlarmClock, dfl: SpeechDownloader.DownloadFinishListener?) {
        outDownloadFinishListener = dfl
        mDownloadService?.startDownload(alarmClock, inDownloadFinishListener)
    }

    var outDownloadFinishListener: SpeechDownloader.DownloadFinishListener? = null

    private val inDownloadFinishListener = object : SpeechDownloader.DownloadFinishListener {
        override fun cancel() {
            if (mIsStartedSetData) {
                mIsAccidentCanceled = true
                SharedService.showTextToast(this@DownloadSpeechActivity, "已完成設置，無法取消")
            } else {
                outDownloadFinishListener?.cancel()
                unbindService(mDownloadServiceConnection)
                mDownloadService = null
            }
        }

        override fun startSetData() {
            mIsStartedSetData = true
            outDownloadFinishListener?.startSetData()
        }

        override fun finish() {
            //避免在 AddAlarmClockActivity 已完成後才按取消，且 destroy 頁面造成 service 自殺，再次 unbind 造成的 crash
            if (!mIsAccidentCanceled) {
                outDownloadFinishListener?.finish()
                unbindService(mDownloadServiceConnection)
            }
            mDownloadService = null
        }
    }

    private val mDownloadServiceConnection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as StartDownloadService.LocalBinder
            mDownloadService = binder.service
            mDownloadService!!.setActivity(this@DownloadSpeechActivity) // register
            mCanStartDownloadCallback.start()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
        }
    }

    fun showDownloadingDialog() {
        val dialogView = layoutInflater.inflate(R.layout.block_download_dialog, null)
        pbDownloading = dialogView.pb_downloading
        tvDownloading = dialogView.tv_downloading

        mDownloadingDialog = AlertDialog.Builder(this)
                .setCancelable(false)
                .setView(dialogView)
                .setNegativeButton("取消", { dialog, which ->
                    mDownloadService?.cancelDownloadSound()

                    //已在 inDownloadFinishListener 中解除綁定
//                    unbindService(mDownloadServiceConnection)
//                    mDownloadService = null
                })
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

    override fun onStop() {
        mDownloadService?.stopDownloadSound()
        super.onStop()
    }

    override fun onResume() {
        mDownloadService?.resumeDownloadSound()
        super.onResume()
    }

    fun dismissDownloadingDialog() {
        mDownloadingDialog.dismiss()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    interface CanStartDownloadCallback {
        fun start()
    }
}
