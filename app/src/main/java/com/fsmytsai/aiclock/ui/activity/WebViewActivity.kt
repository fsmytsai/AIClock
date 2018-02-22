package com.fsmytsai.aiclock.ui.activity

import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.widget.LinearLayout
import com.fsmytsai.aiclock.R
import com.just.agentweb.AgentWeb
import com.just.agentweb.ChromeClientCallbackManager
import kotlinx.android.synthetic.main.activity_web_view.*

class WebViewActivity : AppCompatActivity() {
    private lateinit var mAgentWeb: AgentWeb

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        hideBottomUIMenu()
        setContentView(R.layout.activity_web_view)

        initViews()
    }

    private fun initViews() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        var link = intent.getStringExtra("URL")
        if (!link.contains("http"))
            link = "http://" + link
        mAgentWeb = AgentWeb.with(this)
                .setAgentWebParent(ll_container, LinearLayout.LayoutParams(-1, -1))
                .useDefaultIndicator()
                .setIndicatorColor(ContextCompat.getColor(this, R.color.colorBlue))
                .setReceivedTitleCallback(object : ChromeClientCallbackManager.ReceivedTitleCallback {
                    override fun onReceivedTitle(view: WebView, title: String) {
                        tv_toolBar.text = title
                    }
                })
                .createAgentWeb()
                .ready()
                .go(link)
    }

    private fun hideBottomUIMenu() {
        if (Build.VERSION.SDK_INT < 19) {
            val v = this.window.decorView
            v.systemUiVisibility = View.GONE
        } else {
            val decorView = window.decorView
            val uiOptions = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN)
            decorView.systemUiVisibility = uiOptions
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        val id = item?.itemId

        if (id == android.R.id.home) {
            finish()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if (mAgentWeb.getWebCreator().get().canGoBack())
            mAgentWeb.getWebCreator().get().goBack()
        else
            super.onBackPressed()
    }

    override fun onPause() {
        mAgentWeb.getWebLifeCycle().onPause() //暂停应用内所有 WebView ， 需谨慎。

        super.onPause()

    }

    override fun onResume() {
        mAgentWeb.getWebLifeCycle().onResume()
        super.onResume()
    }

    override fun onDestroy() {
        mAgentWeb.getWebLifeCycle().onDestroy()
        super.onDestroy()
    }
}
