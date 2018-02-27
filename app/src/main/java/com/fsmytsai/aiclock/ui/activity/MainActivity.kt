package com.fsmytsai.aiclock.ui.activity

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.view.Menu
import android.view.MenuItem
import com.fsmytsai.aiclock.R
import com.fsmytsai.aiclock.ResetAlarmService
import com.fsmytsai.aiclock.ui.fragment.AlarmClockFragment
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : DownloadSpeechActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViews()
        //改成每次開啟都檢查是否有失效鬧鐘
        val resetAlarmServiceIntent = Intent(this, ResetAlarmService::class.java)
        resetAlarmServiceIntent.putExtra("IsFromMain", true)
        startService(resetAlarmServiceIntent)
    }

    private fun initViews() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportFragmentManager
                .beginTransaction()
                .replace(R.id.fl_main_container, AlarmClockFragment(), "AlarmClockFragment")
                .commit()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_news, menu)
        return true
//        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when (item?.itemId) {
            R.id.item_About -> {
                AlertDialog.Builder(this)
                        .setTitle("關於")
                        .setMessage("本程式所有新聞來源皆為newsapi.org\n\n背景音樂來自 youtube 的創作者工具箱")
                        .setPositiveButton("知道了", null)
                        .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
