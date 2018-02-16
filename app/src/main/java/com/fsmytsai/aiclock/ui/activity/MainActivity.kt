package com.fsmytsai.aiclock.ui.activity

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import com.fsmytsai.aiclock.R
import com.fsmytsai.aiclock.ui.fragment.AlarmClockFragment
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
    }

    private fun initViews() {
        tv_toolBar.text = "AI 智能鬧鐘"
        supportFragmentManager
                .beginTransaction()
                .replace(R.id.fl_main_container, AlarmClockFragment(), "AlarmClockFragment")
                .commit()
    }
}
