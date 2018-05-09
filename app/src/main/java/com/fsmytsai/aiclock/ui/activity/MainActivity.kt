package com.fsmytsai.aiclock.ui.activity

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AlertDialog
import com.fsmytsai.aiclock.R
import com.fsmytsai.aiclock.ResetAlarmService
import com.fsmytsai.aiclock.service.app.SharedService
import com.fsmytsai.aiclock.ui.fragment.AlarmClockFragment
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*
import okhttp3.*
import java.io.IOException
import android.support.design.widget.TabLayout
import android.support.v4.app.Fragment
import com.fsmytsai.aiclock.service.app.ViewPagerAdapter
import com.fsmytsai.aiclock.ui.fragment.HomeFragment
import com.fsmytsai.aiclock.ui.fragment.SettingFragment


class MainActivity : DownloadSpeechActivity() {
    lateinit var spDatas: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        spDatas = getSharedPreferences("Datas", Context.MODE_PRIVATE)
        initViews()
        checkLatestUrl()
        //改成每次開啟都檢查是否有失效鬧鐘
        val resetAlarmServiceIntent = Intent(this, ResetAlarmService::class.java)
        resetAlarmServiceIntent.putExtra("IsFromMain", true)
        startService(resetAlarmServiceIntent)

        //評分提示
        val alarmTimes = spDatas.getInt("AlarmTimes", 0)
        if (alarmTimes >= 3) {
            if (!spDatas.getBoolean("NeverStar", false)) {
                AlertDialog.Builder(this@MainActivity)
                        .setCancelable(false)
                        .setTitle("使用者體驗調查")
                        .setMessage("不知道您對免費的 AI 智能鬧鐘是否滿意呢？\n\n" +
                                "希望您能前往 Play 商店給予 AI 智能鬧鐘 5 星評分，鼓勵快爆肝的作者\uD83D\uDE2D\n\n" +
                                "也可以使用許願、問題回報等功能告知作者希望改進的部分唷！")
                        .setNegativeButton("不再顯示", { _, _ ->
                            spDatas.edit().putBoolean("NeverStar", true).apply()
                        })
                        .setNeutralButton("關閉", null)
                        .setPositiveButton("前往 Play 商店評分", { _, _ ->
                            spDatas.edit().putBoolean("NeverStar", true).apply()
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
                            } catch (anfe: android.content.ActivityNotFoundException) {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
                            }

                        })
                        .show()
            }
        }

    }

    private fun initViews() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        val fragments = ArrayList<Fragment>()
        fragments.add(HomeFragment())
        fragments.add(AlarmClockFragment())
        fragments.add(SettingFragment())

        val viewPagerAdapter = ViewPagerAdapter(supportFragmentManager, fragments, this)
        viewPagerAdapter.tabTitles = arrayOf("", "", "")
        viewPagerAdapter.tabIcons = intArrayOf(R.drawable.home, R.drawable.alarm, R.drawable.setting)

        vp_home.adapter = viewPagerAdapter
        vp_home.offscreenPageLimit = 2
        vp_home.currentItem = 1

        tl_home.tabMode = TabLayout.MODE_FIXED
        tl_home.tabGravity = TabLayout.GRAVITY_FILL
        tl_home.setupWithViewPager(vp_home)
    }

    private fun checkLatestUrl() {
        val request = Request.Builder()
                .url("${SharedService.getLatestUrl(this)}api/getLatestUrl")
                .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call?, e: IOException?) {
            }

            override fun onResponse(call: Call?, response: Response?) {
                val statusCode = response?.code()
                val resMessage = response?.body()?.string()

                runOnUiThread {
                    if (statusCode == 200) {
                        val latestUrl = Gson().fromJson(resMessage, String::class.java)
                        if (SharedService.getLatestUrl(this@MainActivity) != latestUrl) {
                            SharedService.writeDebugLog(this@MainActivity, "MainActivity update latestUrl to $latestUrl")
                            spDatas.edit().putString("LatestUrl", latestUrl).apply()
                        }
                        checkLatestVersion()
                    } else {
                        SharedService.handleError(this@MainActivity, statusCode!!, resMessage!!)
                    }
                }

            }
        })
    }

    private fun checkLatestVersion() {
        val request = Request.Builder()
                .url("${SharedService.getLatestUrl(this)}api/getLatestVersionCode")
                .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call?, e: IOException?) {
            }

            override fun onResponse(call: Call?, response: Response?) {
                val statusCode = response?.code()
                val resMessage = response?.body()?.string()

                runOnUiThread {
                    if (statusCode == 200) {
                        val latestVersionCode = Gson().fromJson(resMessage, Int::class.java)
                        if (spDatas.getInt("DoNotUpdateVersionCode", 0) < latestVersionCode && SharedService.getVersionCode(this@MainActivity) < latestVersionCode) {
                            SharedService.writeDebugLog(this@MainActivity, "MainActivity found new version $latestVersionCode")
                            AlertDialog.Builder(this@MainActivity)
                                    .setTitle("提示")
                                    .setMessage("有新版本囉!")
                                    .setNegativeButton("忽略", { _, _ ->
                                        spDatas.edit().putInt("DoNotUpdateVersionCode", latestVersionCode).apply()
                                    })
                                    .setNeutralButton("稍後提醒", null)
                                    .setPositiveButton("前往 Play 商店更新", { _, _ ->
                                        try {
                                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
                                        } catch (anfe: android.content.ActivityNotFoundException) {
                                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
                                        }

                                    })
                                    .show()
                        }
                    } else {
                        SharedService.handleError(this@MainActivity, statusCode!!, resMessage!!)
                    }
                }

            }
        })
    }

}
