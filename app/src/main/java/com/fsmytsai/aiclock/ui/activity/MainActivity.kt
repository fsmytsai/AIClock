package com.fsmytsai.aiclock.ui.activity

import android.os.Bundle
import com.fsmytsai.aiclock.R
import com.fsmytsai.aiclock.ui.fragment.AlarmClockFragment
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : DownloadSpeechActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViews()
    }

    private fun initViews() {
        supportFragmentManager
                .beginTransaction()
                .replace(R.id.fl_main_container, AlarmClockFragment(), "AlarmClockFragment")
                .commit()
    }
}
