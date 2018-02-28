package com.fsmytsai.aiclock.ui.activity

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.view.Menu
import android.view.MenuItem
import com.fsmytsai.aiclock.R
import com.fsmytsai.aiclock.ResetAlarmService
import com.fsmytsai.aiclock.service.app.SharedService
import com.fsmytsai.aiclock.ui.fragment.AlarmClockFragment
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.dialog_wish.view.*
import java.io.File
import java.util.*
import okhttp3.*
import java.io.IOException


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
        inflater.inflate(R.menu.menu_main, menu)
        return true
//        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when (item?.itemId) {
            R.id.item_error_report -> {
                if (SharedService.checkNetWork(this, true)) {
                    val dialogView = layoutInflater.inflate(R.layout.dialog_error_report, null)
                    AlertDialog.Builder(this)
                            .setView(dialogView)
                            .setNegativeButton("取消", null)
                            .setPositiveButton("送出", { _, _ ->
                                val content = dialogView.et_content.text.toString()
                                val email = dialogView.et_email.text.toString()
                                report(content, 0, email)
                            })
                            .show()
                }
                true
            }
            R.id.item_wish -> {
                if (SharedService.checkNetWork(this, true)) {
                    val dialogView = layoutInflater.inflate(R.layout.dialog_wish, null)
                    AlertDialog.Builder(this)
                            .setView(dialogView)
                            .setNegativeButton("取消", null)
                            .setPositiveButton("送出", { _, _ ->
                                val content = dialogView.et_content.text.toString()
                                val email = dialogView.et_email.text.toString()
                                report(content, 1, email)
                            })
                            .show()
                }
                true
            }
            R.id.item_about -> {
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

    private fun report(content: String, type: Int, email: String) {
        val builder = MultipartBody.Builder()
        builder.setType(MultipartBody.FORM)
        if (type == 0) {
            val nowCalendar = Calendar.getInstance()
            for (i in 0..2) {
                nowCalendar.add(Calendar.DATE, -i)
                val logFileName = "${nowCalendar.get(Calendar.YEAR)}-${nowCalendar.get(Calendar.MONTH) + 1}-${nowCalendar.get(Calendar.DAY_OF_MONTH)}.txt"
                val file = File("$filesDir/logs/$logFileName")
                if (!file.exists())
                    continue
                builder.addFormDataPart("log_files[]", logFileName, RequestBody.create(MediaType.parse("text/plain"), file))
            }
        }

        builder.addFormDataPart("content", content)
                .addFormDataPart("type", "$type")
        if (email != "")
            builder.addFormDataPart("email", "$email")

        val body = builder.build()

        val request = Request.Builder()
                .url("${getString(R.string.server_url)}api/createReport")
                .post(body)
                .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call?, e: IOException?) {
                runOnUiThread {
                    SharedService.showTextToast(this@MainActivity, "請檢查網路連線")
                }
            }

            override fun onResponse(call: Call?, response: Response?) {
                val statusCode = response?.code()
                val resMessage = response?.body()?.string()

                runOnUiThread {
                    if (statusCode == 200) {
                        val typeStr = if (type == 0) "問題回報" else "願望"
                        SharedService.showTextToast(this@MainActivity, "成功送出$typeStr")
                    } else {
                        SharedService.handleError(this@MainActivity, statusCode!!, resMessage!!)
                    }
                }

            }
        })
    }
}
