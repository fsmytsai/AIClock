package com.fsmytsai.aiclock.ui.activity

import android.content.Intent
import android.os.Bundle
import com.fsmytsai.aiclock.R
import com.fsmytsai.aiclock.ResetAlarmService
import com.fsmytsai.aiclock.service.app.SharedService
import com.fsmytsai.aiclock.ui.fragment.AlarmClockFragment


class MainActivity : DownloadSpeechActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViews()
        if (SharedService.checkUpdate(this)) {
            val resetAlarmServiceIntent = Intent(this, ResetAlarmService::class.java)
            resetAlarmServiceIntent.putExtra("IsFromMain", true)
            startService(resetAlarmServiceIntent)
        }
    }

    private fun initViews() {
        supportFragmentManager
                .beginTransaction()
                .replace(R.id.fl_main_container, AlarmClockFragment(), "AlarmClockFragment")
                .commit()
    }
}
