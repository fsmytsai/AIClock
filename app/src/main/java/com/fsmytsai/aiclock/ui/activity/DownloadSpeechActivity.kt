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
import com.fsmytsai.aiclock.model.AlarmClocks
import com.fsmytsai.aiclock.service.app.SharedService
import com.fsmytsai.aiclock.service.app.SpeechDownloader
import kotlinx.android.synthetic.main.dialog_download.view.*

open class DownloadSpeechActivity : AppCompatActivity() {
    private var mDownloadService: StartDownloadService? = null
    private lateinit var pbDownloading: ProgressBar
    private lateinit var tvDownloading: TextView
    private var mDownloadingDialog: AlertDialog? = null
    private lateinit var mCanStartDownloadCallback: CanStartDownloadCallback
    private var mIsStartedSetData = false
    private var mIsAccidentCanceled = false
    var keepFullScreen = false
    var downloadTitle = "設置智能鬧鐘中..."

    fun bindDownloadService(canStartDownloadCallback: CanStartDownloadCallback) {
        mCanStartDownloadCallback = canStartDownloadCallback
        if (mDownloadService == null) {
            val intent = Intent(this, StartDownloadService::class.java)
            bindService(intent, mDownloadServiceConnection, Context.BIND_AUTO_CREATE)
        } else {
            mCanStartDownloadCallback.start()
        }
    }

    fun startDownload(alarmClock: AlarmClocks.AlarmClock, dfl: SpeechDownloader.DownloadFinishListener?) {
        mIsStartedSetData = false
        mIsAccidentCanceled = false
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
                dismissDownloadingDialog()
                try {
                    unbindService(mDownloadServiceConnection)
                } catch (e: Exception) {
                    SharedService.writeDebugLog(this@DownloadSpeechActivity, "DownloadSpeechActivity cancel unbindService failed")
                }
                mDownloadService = null
                outDownloadFinishListener?.cancel()
            }
        }

        override fun startSetData() {
            mIsStartedSetData = true
            outDownloadFinishListener?.startSetData()
        }

        override fun allFinished() {
            //避免在 AddAlarmClockActivity 已完成後才按取消，且 destroy 頁面造成 service 自殺，再次 unbind 造成的 crash
            if (!mIsAccidentCanceled) {
                dismissDownloadingDialog()
            }
            try {
                unbindService(mDownloadServiceConnection)
            } catch (e: Exception) {
                SharedService.writeDebugLog(this@DownloadSpeechActivity, "DownloadSpeechActivity allFinished unbindService failed")
            }
            mDownloadService = null
            outDownloadFinishListener?.allFinished()
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
        val dialogView = layoutInflater.inflate(R.layout.dialog_download, null)
        dialogView.tv_title.text = downloadTitle
        pbDownloading = dialogView.pb_downloading
        tvDownloading = dialogView.tv_downloading

        mDownloadingDialog = AlertDialog.Builder(this)
                .setCancelable(false)
                .setView(dialogView)
                .setNegativeButton("取消", { _, _ ->
                    mDownloadService?.cancelDownloadSound()

                    //已在 inDownloadFinishListener 中解除綁定
//                    unbindService(mDownloadServiceConnection)
//                    mDownloadService = null
                })
                .create()

        if (keepFullScreen) {
            mDownloadingDialog!!.window.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            mDownloadingDialog!!.show()
            mDownloadingDialog!!.window.decorView.systemUiVisibility = window.decorView.systemUiVisibility
            mDownloadingDialog!!.window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            mDownloadingDialog!!.show()
        }
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

    private fun dismissDownloadingDialog() {
        mDownloadingDialog?.dismiss()
        if (!keepFullScreen)
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    interface CanStartDownloadCallback {
        fun start()
    }
}
