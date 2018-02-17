package com.fsmytsai.aiclock.ui.activity

import android.content.Context
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import com.fsmytsai.aiclock.R
import com.fsmytsai.aiclock.model.Texts
import com.fsmytsai.aiclock.ui.fragment.AlarmClockFragment
import com.fsmytsai.aiclock.ui.fragment.NewsFragment
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.*
import android.view.WindowManager


class MainActivity : AppCompatActivity() {
    var acId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
    }

    private fun initViews() {
        tv_toolBar.text = "AI 智能鬧鐘"
        acId = intent.getIntExtra("ACId", 0)
        if (acId == 0)
            supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.fl_main_container, AlarmClockFragment(), "AlarmClockFragment")
                    .commit()
        else {
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
}
