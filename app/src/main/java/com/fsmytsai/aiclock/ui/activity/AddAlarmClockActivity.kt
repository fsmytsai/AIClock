package com.fsmytsai.aiclock.ui.activity

import android.annotation.TargetApi
import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.util.Log
import com.fsmytsai.aiclock.R
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import com.fsmytsai.aiclock.AlarmReceiver
import com.fsmytsai.aiclock.model.AlarmClock
import com.fsmytsai.aiclock.model.Text
import com.fsmytsai.aiclock.model.Texts
import com.fsmytsai.aiclock.model.TextsList
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_add_alarm_clock.*
import okhttp3.*
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList
import com.liulishuo.filedownloader.FileDownloader
import com.liulishuo.filedownloader.BaseDownloadTask
import com.liulishuo.filedownloader.FileDownloadListener
import kotlinx.android.synthetic.main.block_download_dialog.view.*


class AddAlarmClockActivity : AppCompatActivity() {
    private lateinit var mAlarmClock: AlarmClock
    private var mTexts = Texts(0, ArrayList())
    private var mIsNew = true
    private var mIsSpeakerPlaying = false
    private var mMPSpeaker = MediaPlayer()
    private lateinit var mTimePickerDialog: TimePickerDialog
    private var mNeedDownloadCount = 0f
    private var mDownloadedCount = 0f
    private lateinit var pbDownloading: ProgressBar
    private lateinit var tvDownloading: TextView
    private lateinit var dialogDownloading: AlertDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_alarm_clock)
        getAlarmClockData()
        initViews()
    }

    override fun onStop() {
        super.onStop()
        mMPSpeaker.release()
    }

    private fun initViews() {
        if (mIsNew)
            tv_toolBar.setText("新增智能鬧鐘")
        else {
            tv_toolBar_delete.visibility = View.VISIBLE
            tv_toolBar_delete.setOnClickListener {
                AlertDialog.Builder(this)
                        .setTitle("刪除智能鬧鐘")
                        .setMessage("您確定要刪除嗎?")
                        .setPositiveButton("確定", { _, _ ->
                            cancelAlarm()
                            intent.putExtra("AlarmClockJsonStr", Gson().toJson(mAlarmClock))
                            intent.putExtra("IsDelete", true)
                            setResult(Activity.RESULT_OK, intent)
                            finish()
                        })
                        .setNegativeButton("取消", null)
                        .show()
            }
            tv_toolBar.setText("編輯智能鬧鐘")
        }

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        et_Time.setText("${String.format("%02d", mAlarmClock.hour)}:${String.format("%02d", mAlarmClock.minute)}")
        setTimeField()
        et_Time.setOnClickListener {
            mTimePickerDialog.show()
        }

        when (mAlarmClock.speaker) {
            0 -> rb_f1.isChecked = true
            1 -> rb_f2.isChecked = true
            2 -> rb_m1.isChecked = true
        }

        rg_speaker.setOnCheckedChangeListener { radioGroup, checkedId ->
            if (mIsSpeakerPlaying)
                mMPSpeaker?.stop()
            var uri: Uri? = null
            when (checkedId) {
                R.id.rb_f1 -> {
                    mAlarmClock.speaker = 0
                    uri = Uri.parse("android.resource://${packageName}/raw/f1")
                }
                R.id.rb_f2 -> {
                    mAlarmClock.speaker = 1
                    uri = Uri.parse("android.resource://${packageName}/raw/f2")
                }
                R.id.rb_m1 -> {
                    mAlarmClock.speaker = 2
                    uri = Uri.parse("android.resource://${packageName}/raw/m1")
                }
            }
            startPlaying(uri!!, false)
        }

        when (mAlarmClock.category) {
            -1 -> rb_no.isChecked = true
            0 -> rb_general.isChecked = true
            1 -> rb_business.isChecked = true
            2 -> rb_entertainment.isChecked = true
            3 -> rb_health.isChecked = true
            4 -> rb_science.isChecked = true
            5 -> rb_sports.isChecked = true
            6 -> rb_technology.isChecked = true
        }

        rg_NewsType.setOnCheckedChangeListener { radioGroup, checkedId ->
            when (checkedId) {
                R.id.rb_no -> mAlarmClock.category = -1
                R.id.rb_general -> mAlarmClock.category = 0
                R.id.rb_business -> mAlarmClock.category = 1
                R.id.rb_entertainment -> mAlarmClock.category = 2
                R.id.rb_health -> mAlarmClock.category = 3
                R.id.rb_science -> mAlarmClock.category = 4
                R.id.rb_sports -> mAlarmClock.category = 5
                R.id.rb_technology -> mAlarmClock.category = 6
            }
        }

        val circleTextviewFull = ContextCompat.getDrawable(this, R.drawable.circle_textview_full)
        for (i in 0..6) {
            if (mAlarmClock.isRepeatArr[i])
                when (i) {
                    0 -> tv_sun.background = circleTextviewFull
                    1 -> tv_mon.background = circleTextviewFull
                    2 -> tv_tues.background = circleTextviewFull
                    3 -> tv_wednes.background = circleTextviewFull
                    4 -> tv_thurs.background = circleTextviewFull
                    5 -> tv_fri.background = circleTextviewFull
                    6 -> tv_satur.background = circleTextviewFull
                }
        }

    }

    private fun getAlarmClockData() {
        val AlarmClockJsonStr = intent.getStringExtra("AlarmClockJsonStr")
        if (AlarmClockJsonStr != null) {
            mIsNew = false
            mAlarmClock = Gson().fromJson(AlarmClockJsonStr, AlarmClock::class.java)
            mAlarmClock.isOpen = true
        } else {
            val newCalendar = Calendar.getInstance()
            val acId = intent.getIntExtra("acId", 0)
            mAlarmClock = AlarmClock(acId,
                    newCalendar.get(Calendar.HOUR_OF_DAY),
                    newCalendar.get(Calendar.MINUTE),
                    -1,
                    -1,
                    booleanArrayOf(false, false, false, false, false, false, false),
                    true)
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun startPlaying(uri: Uri, isReturn: Boolean) {
        mMPSpeaker = MediaPlayer()
        mMPSpeaker.setDataSource(this, uri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
            mMPSpeaker.setAudioAttributes(audioAttributes)
        } else {
            mMPSpeaker.setAudioStreamType(AudioManager.STREAM_ALARM)
        }
        mMPSpeaker.setOnCompletionListener {
            mIsSpeakerPlaying = false
            if (isReturn) {
                dialogDownloading.dismiss()
                returnData()
            }
        }
        mMPSpeaker.setVolume(1f, 1f)
        mMPSpeaker.prepare()
        mMPSpeaker.start()
        mIsSpeakerPlaying = true
    }

    private fun setTimeField() {
        mTimePickerDialog = TimePickerDialog(this, object : TimePickerDialog.OnTimeSetListener {
            override fun onTimeSet(p0: TimePicker?, p1: Int, p2: Int) {
                et_Time.setText("${String.format("%02d", p1)}:${String.format("%02d", p2)}")
                mAlarmClock.hour = p1
                mAlarmClock.minute = p2
            }
        }, mAlarmClock.hour, mAlarmClock.minute, true)
    }

    fun clickRepeat(view: View) {
        val index = when (view) {
            tv_sun -> 0
            tv_mon -> 1
            tv_tues -> 2
            tv_wednes -> 3
            tv_thurs -> 4
            tv_fri -> 5
            tv_satur -> 6
            else -> -1
        }
        val circleTextview = ContextCompat.getDrawable(this@AddAlarmClockActivity, R.drawable.circle_textview)
        val circleTextviewFull = ContextCompat.getDrawable(this@AddAlarmClockActivity, R.drawable.circle_textview_full)
        if (mAlarmClock.isRepeatArr[index]) {
            view.background = circleTextview
            mAlarmClock.isRepeatArr[index] = false
        } else {
            view.background = circleTextviewFull
            mAlarmClock.isRepeatArr[index] = true
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        val id = item?.getItemId()

        if (id == android.R.id.home) {
            finish()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    fun save(view: View) {
        if (mAlarmClock.speaker == -1) {
            Toast.makeText(this, "請選擇播報者", Toast.LENGTH_SHORT).show()
            return
        }
        var isRepeatChoosed = false
        for (isRepeat in mAlarmClock.isRepeatArr) {
            if (isRepeat) {
                isRepeatChoosed = true
                break
            }
        }
        if (!isRepeatChoosed) {
            Toast.makeText(this, "請選擇重複天數", Toast.LENGTH_SHORT).show()
            return
        }

        getTextData()
    }

    private fun getTextData() {
        val dialogView = layoutInflater.inflate(R.layout.block_download_dialog, null)
        pbDownloading = dialogView.pb_downloading
        tvDownloading = dialogView.tv_downloading
        dialogDownloading = AlertDialog.Builder(this)
                .setView(dialogView)
                .create()
        dialogDownloading.setCanceledOnTouchOutside(false)
        dialogDownloading.show()

        val request = Request.Builder()
                .url("${getString(R.string.server_url)}api/getTextData?hour=${mAlarmClock.hour}&" +
                        "minute=${mAlarmClock.minute}&speaker=${mAlarmClock.speaker}&category=${mAlarmClock.category}")
                .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call?, e: IOException?) {
                Toast.makeText(this@AddAlarmClockActivity, "請檢察網路連線", Toast.LENGTH_SHORT).show()
            }

            override fun onResponse(call: Call?, response: Response?) {
                val statusCode = response?.code()
                val resMessage = response?.body()?.string()
                runOnUiThread {
                    if (statusCode == 200) {
                        mTexts = Gson().fromJson(resMessage, Texts::class.java)
                        if (mTexts.textList.size > 0) {
                            mTexts.acId = mAlarmClock.acId
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                                pbDownloading.setProgress(10, true)
                            else
                                pbDownloading.setProgress(10)
                            tvDownloading.setText("10/100")
                            downloadSound()
                        } else {
                            Toast.makeText(this@AddAlarmClockActivity, "Error", Toast.LENGTH_SHORT).show()
                            dialogDownloading.dismiss()
                            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                        }
                    } else {
                        Toast.makeText(this@AddAlarmClockActivity, "$statusCode Error", Toast.LENGTH_SHORT).show()
                        dialogDownloading.dismiss()
                        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                    }
                }
            }
        })
    }

    private fun downloadSound() {
        FileDownloader.setup(this)
        for (text in mTexts.textList) {
            for (i in 0..text.part_count - 1) {
                FileDownloader.getImpl().create("${getString(R.string.server_url)}sounds/${text.text_id}-${i}.wav")
                        .setPath("${filesDir.absolutePath}/sounds/${text.text_id}-${i}.wav")
                        .setTag(0, "${text.text_id}-${i}")
                        .setTag(1, text)
                        .setCallbackProgressTimes(0)
                        .setListener(queueTarget)
                        .asInQueueTask()
                        .enqueue()
                mNeedDownloadCount++
            }
        }
        FileDownloader.getImpl().start(queueTarget, false)
    }

    val queueTarget: FileDownloadListener = object : FileDownloadListener() {
        override fun pending(task: BaseDownloadTask, soFarBytes: Int, totalBytes: Int) {}

        override fun connected(task: BaseDownloadTask?, etag: String?, isContinue: Boolean, soFarBytes: Int, totalBytes: Int) {}

        override fun progress(task: BaseDownloadTask, soFarBytes: Int, totalBytes: Int) {}

        override fun blockComplete(task: BaseDownloadTask?) {}

        override fun retry(task: BaseDownloadTask?, ex: Throwable?, retryingTimes: Int, soFarBytes: Int) {}

        override fun completed(task: BaseDownloadTask) {
            val text = task.getTag(1) as Text
            text.completeDownloadCount++
            Log.d("AddAlarmClockActivity", "file ${task.getTag(0) as String} text_id = ${text.text_id} completeDownloadCount = ${text.completeDownloadCount}")
            mDownloadedCount++

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                pbDownloading.setProgress(10 + (mDownloadedCount / mNeedDownloadCount * 90).toInt(), true)
            else
                pbDownloading.setProgress(10 + (mDownloadedCount / mNeedDownloadCount * 90).toInt())

            tvDownloading.setText("${10 + (mDownloadedCount / mNeedDownloadCount * 90).toInt()}/100")

            if (mDownloadedCount == mNeedDownloadCount) {
                pbDownloading.setProgress(100)
                tvDownloading.setText("100/100")

                Log.d("AddAlarmClockActivity", "Download Finish")
                var uri: Uri? = null
                when (mAlarmClock.speaker) {
                    0 -> uri = Uri.parse("android.resource://${packageName}/raw/setfinishf1")
                    1 -> uri = Uri.parse("android.resource://${packageName}/raw/setfinishf2")
                    2 -> uri = Uri.parse("android.resource://${packageName}/raw/setfinishm1")
                }
                startPlaying(uri!!, true)
            }
        }

        override fun paused(task: BaseDownloadTask, soFarBytes: Int, totalBytes: Int) {}

        override fun error(task: BaseDownloadTask, e: Throwable) {
            Log.d("AddAlarmClockActivity", "error file ${task.getTag(0) as String}")
//            DownloadedCount++
//            if (DownloadedCount == needDownloadCount) {
//                Log.d("AddAlarmClockActivity", "Download Finish")
//                var uri: Uri? = null
//                when (mAlarmClock.speaker) {
//                    0 -> {
//                        uri = Uri.parse("android.resource://${packageName}/raw/setfinishf1")
//                    }
//                    1 -> {
//                        uri = Uri.parse("android.resource://${packageName}/raw/setfinishf2")
//                    }
//                    2 -> {
//                        uri = Uri.parse("android.resource://${packageName}/raw/setfinishm1")
//                    }
//                }
//                startPlaying(uri!!, true)
//            }
        }

        override fun warn(task: BaseDownloadTask) {}
    }

    private fun returnData() {
        val spDatas = getSharedPreferences("Datas", Context.MODE_PRIVATE)
        val textsList: TextsList
        val textsListJsonStr = spDatas.getString("TextsListJsonStr", "")
        if (textsListJsonStr != "") {
            textsList = Gson().fromJson(textsListJsonStr, TextsList::class.java)
            for (texts in textsList.textsList) {
                if (texts.acId == mAlarmClock.acId)
                    textsList.textsList.remove(texts)
            }
        } else {
            textsList = TextsList(ArrayList())
        }
        textsList.textsList.add(mTexts)
        spDatas.edit().putString("TextsListJsonStr", Gson().toJson(textsList)).apply()

        setAlarm()

        intent.putExtra("AlarmClockJsonStr", Gson().toJson(mAlarmClock))
        intent.putExtra("IsNew", mIsNew)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private fun setAlarm() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MINUTE, 1)
        val intent = Intent(this, AlarmReceiver::class.java)
        intent.putExtra("ACId", mAlarmClock.acId)
        val pi = PendingIntent.getBroadcast(this, mAlarmClock.acId, intent, PendingIntent.FLAG_ONE_SHOT)
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi)
    }

    private fun cancelAlarm() {
        val intent = Intent(this, AlarmReceiver::class.java)
        intent.putExtra("IsAlarm", true)

        val pi = PendingIntent.getBroadcast(this, mAlarmClock.acId, intent, PendingIntent.FLAG_ONE_SHOT)
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi)
    }
}
