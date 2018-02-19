package com.fsmytsai.aiclock.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.fsmytsai.aiclock.R
import com.fsmytsai.aiclock.model.Texts
import com.fsmytsai.aiclock.ui.fragment.AlarmClockFragment
import com.fsmytsai.aiclock.ui.fragment.NewsFragment
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.*
import android.view.WindowManager
import com.fsmytsai.aiclock.AlarmService
import android.content.ComponentName
import android.os.IBinder
import android.content.ServiceConnection
import android.view.Menu
import com.fsmytsai.aiclock.service.app.SharedService
import android.support.v7.app.AlertDialog
import android.view.MenuItem


class MainActivity : DownloadSpeechActivity() {
    var acId = 0
    private var mAlarmService: AlarmService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViews()
    }

    override fun onStop() {
        mAlarmService?.pausePlay()
        super.onStop()
    }

    override fun onResume() {
        //重開時 mAlarmService 還是 null
        mAlarmService?.resumePlay()
        super.onResume()
    }

    private fun initViews() {
        tv_toolBar.text = "AI 智能鬧鐘"
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        acId = intent.getIntExtra("ACId", 0)
        if (acId == 0 && !SharedService.isNewsPlaying && !SharedService.reRunRunnable) {
            stopAlarmService()
            supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.fl_main_container, AlarmClockFragment(), "AlarmClockFragment")
                    .commit()
        } else {
            if (Build.VERSION.SDK_INT >= 27) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.fl_main_container, NewsFragment(), "NewsFragment")
                    .commit()

        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        acId = intent.getIntExtra("ACId", 0)
        if (acId != 0 || SharedService.isNewsPlaying || SharedService.reRunRunnable) {
            val inflater = menuInflater
            inflater.inflate(R.menu.menu_news, menu)
            return true
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when (item?.itemId) {
            R.id.item_About -> {
                AlertDialog.Builder(this)
                        .setTitle("關於")
                        .setMessage("本程式所有新聞來源皆為\nnewsapi.org\n\n背景音樂來自 youtube 的創作者工具箱")
                        .setPositiveButton("知道了", null)
                        .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun startAlarmService(texts: Texts?) {
        val intent = Intent(this, AlarmService::class.java)
        intent.putExtra("TextsJsonStr", Gson().toJson(texts))
        startService(intent)
        bindService(intent, alarmServiceConnection, 0)
    }

    private val alarmServiceConnection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as AlarmService.LocalBinder
            mAlarmService = binder.service
            mAlarmService!!.setMainActivity(this@MainActivity) // register
            //內部會自動判斷是否在暫停狀態
            mAlarmService!!.resumePlay()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
        }
    }

    override fun onDestroy() {
        if (mAlarmService != null) {
            unbindService(alarmServiceConnection)
        }
        super.onDestroy()
    }

    fun stopAlarmService() {
        mAlarmService?.setMainActivity(null)
        if (mAlarmService != null) {
            unbindService(alarmServiceConnection)
        }
        val intent = Intent(this, AlarmService::class.java)
        stopService(intent)
        mAlarmService = null
    }

    fun clearFlags() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(false)
            setTurnScreenOn(false)
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
