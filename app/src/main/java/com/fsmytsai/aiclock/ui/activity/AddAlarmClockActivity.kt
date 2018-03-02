package com.fsmytsai.aiclock.ui.activity

import android.annotation.TargetApi
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import com.fsmytsai.aiclock.R
import android.view.MenuItem
import android.view.View
import android.widget.RadioButton
import com.bigkoo.pickerview.OptionsPickerView
import com.fsmytsai.aiclock.model.*
import com.fsmytsai.aiclock.service.app.SharedService
import com.fsmytsai.aiclock.service.app.SpeechDownloader
import com.fsmytsai.aiclock.ui.view.MyRadioGroup
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_add_alarm_clock.*
import java.util.*
import com.bigkoo.pickerview.TimePickerView


class AddAlarmClockActivity : DownloadSpeechActivity() {
    private lateinit var mAlarmClock: AlarmClock
    private var mIsNew = true
    private var mIsSpeakerPlaying = false
    private var mMPSpeaker = MediaPlayer()

    private val REQUEST_LOCATION = 888

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_alarm_clock)
        getAlarmClock()
        initViews()
    }

    override fun onStop() {
        super.onStop()
        mMPSpeaker.release()
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun initViews() {
        if (mIsNew)
            tv_toolBar.text = "新增智能鬧鐘"
        else {
            tv_toolBar_delete.visibility = View.VISIBLE
            tv_toolBar_delete.setOnClickListener {
                AlertDialog.Builder(this)
                        .setTitle("刪除智能鬧鐘")
                        .setMessage("您確定要刪除嗎?")
                        .setPositiveButton("確定", { _, _ ->
                            SharedService.cancelAlarm(this, mAlarmClock.acId)
                            SharedService.deleteAlarmClock(this, mAlarmClock.acId)
                            SharedService.deleteOldTextsData(this, mAlarmClock.acId, null, false)
                            intent.putExtra("AlarmClockJsonStr", Gson().toJson(mAlarmClock))
                            intent.putExtra("IsDelete", true)
                            setResult(Activity.RESULT_OK, intent)
                            finish()
                        })
                        .setNegativeButton("取消", null)
                        .show()
            }
            tv_toolBar.text = "編輯智能鬧鐘"
        }

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        pvTime.setKeyBackCancelable(false)
        pvTime.show(ll_time)

        when (mAlarmClock.speaker) {
            0 -> rb_f1.isChecked = true
            1 -> rb_f2.isChecked = true
            2 -> rb_m1.isChecked = true
        }

        rg_speaker.setOnCheckedChangeListener { _, checkedId ->
            if (mIsSpeakerPlaying)
                mMPSpeaker.stop()
            var uri: Uri? = null
            when (checkedId) {
                R.id.rb_f1 -> {
                    mAlarmClock.speaker = 0
                    uri = Uri.parse("android.resource://$packageName/raw/f1_hello")
                }
                R.id.rb_f2 -> {
                    mAlarmClock.speaker = 1
                    uri = Uri.parse("android.resource://$packageName/raw/f2_hello")
                }
                R.id.rb_m1 -> {
                    mAlarmClock.speaker = 2
                    uri = Uri.parse("android.resource://$packageName/raw/m1_hello")
                }
            }
            startPlaying(uri!!)
        }

        sb_weather.isChecked = mAlarmClock.latitude != 1000.0

        sb_weather.setOnCheckedChangeListener { view, isChecked ->
            if (isChecked) {
                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                        ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    val isSuccess = SharedService.setLocation(this, mAlarmClock)
                    if (!isSuccess) {
                        SharedService.showTextToast(this, "取得位置失敗")
                        Handler().postDelayed({
                            sb_weather.isChecked = false
                        }, 1000)
                    }
                } else {
                    requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_LOCATION)
                }
            } else
                mAlarmClock.latitude = 1000.0
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

        rg_category.setOnCheckedChangeListener(object : MyRadioGroup.OnCheckedChangeListener {
            override fun onCheckedChanged(group: MyRadioGroup, checkedId: Int) {
                val rbCategory = findViewById<RadioButton>(checkedId)
                mAlarmClock.category = rbCategory.tag.toString().toInt()
                if (mAlarmClock.category != -1)
                    ll_news_count.visibility = View.VISIBLE
                else
                    ll_news_count.visibility = View.GONE
            }
        })

        if (mAlarmClock.category != -1)
            ll_news_count.visibility = View.VISIBLE
        else
            ll_news_count.visibility = View.GONE

        et_news_count.setText("${mAlarmClock.newsCount}")
        et_news_count.setOnClickListener {
            pvNewsCount.setNPicker(arrayListOf("6", "7", "8", "9", "10", "11", "12"), null, null)
            pvNewsCount.setSelectOptions(mAlarmClock.newsCount - 6)
            pvNewsCount.show()
        }

        val circleTextviewFull = ContextCompat.getDrawable(this, R.drawable.circle_textview_full)
        (0..6).filter { mAlarmClock.isRepeatArr[it] }
                .forEach {
                    when (it) {
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

    private fun getAlarmClock() {
        val alarmClockJsonStr = intent.getStringExtra("AlarmClockJsonStr")
        if (alarmClockJsonStr != null) {
            mIsNew = false
            mAlarmClock = Gson().fromJson(alarmClockJsonStr, AlarmClock::class.java)
            mAlarmClock.isOpen = true
        } else {
            val newCalendar = Calendar.getInstance()
            val acId = intent.getIntExtra("acId", 0)
            mAlarmClock = AlarmClock(acId,
                    newCalendar.get(Calendar.HOUR_OF_DAY),
                    newCalendar.get(Calendar.MINUTE),
                    -1,
                    1000.0,
                    0.0,
                    -1,
                    6,
                    booleanArrayOf(false, false, false, false, false, false, false),
                    true)
        }
    }

    private val pvTime by lazy {
        val mAlarmTimeCalendar = Calendar.getInstance()
        mAlarmTimeCalendar.set(Calendar.HOUR_OF_DAY, mAlarmClock.hour)
        mAlarmTimeCalendar.set(Calendar.MINUTE, mAlarmClock.minute)
        TimePickerView.Builder(this, object : TimePickerView.OnTimeSelectListener {

            override fun onTimeSelect(date: Date, v: View) {
                val calendar = Calendar.getInstance()
                calendar.time = date
                mAlarmClock.hour = calendar.get(Calendar.HOUR_OF_DAY)
                mAlarmClock.minute = calendar.get(Calendar.MINUTE)
            }
        }).setType(booleanArrayOf(false, false, false, true, true, false))
                .setLayoutRes(R.layout.block_time_picker, { v ->

                })
                .setContentSize(24)
                .setLineSpacingMultiplier(1.5f)
                .setOutSideCancelable(false)
                .isCyclic(true)
                .setDividerColor(ContextCompat.getColor(this, R.color.colorPaleBlue))
                .setTextColorCenter(ContextCompat.getColor(this, R.color.colorBlue))
                .setTextColorOut(Color.BLACK)
                .setBgColor(ContextCompat.getColor(this, R.color.colorPaleYellow))
                .setDate(mAlarmTimeCalendar)
                .setLabel(null, null, null, "點", "分", null)
                .isCenterLabel(true)
                .setDecorView(ll_time)
                .isDialog(false)
                .build()
    }

    private val pvNewsCount by lazy {
        OptionsPickerView.Builder(this, OptionsPickerView.OnOptionsSelectListener { options1, _, _, _ ->
            mAlarmClock.newsCount = options1 + 6
            et_news_count.setText("${options1 + 6}")
        }).setContentTextSize(24)
                .setLineSpacingMultiplier(1.5f)
                .setOutSideCancelable(false)
                .setDividerColor(ContextCompat.getColor(this, R.color.colorPaleBlue))
                .setTextColorCenter(ContextCompat.getColor(this, R.color.colorBlue))
                .setSelectOptions(mAlarmClock.newsCount - 6)
                .setTextColorOut(Color.BLACK)
                .setBgColor(ContextCompat.getColor(this, R.color.colorPaleYellow))
                .setCancelText("取消")
                .setCancelColor(ContextCompat.getColor(this, R.color.colorBlue))
                .setSubmitText("確定")
                .setSubmitColor(ContextCompat.getColor(this, R.color.colorBlue))
                .setTitleText("播報新聞篇數")
                .setTitleBgColor(ContextCompat.getColor(this, R.color.colorPaleYellow))
                .setTitleColor(Color.BLACK)
                .build()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION && grantResults.size == 2 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            val isSuccess = SharedService.setLocation(this, mAlarmClock)
            if (!isSuccess) {
                SharedService.showTextToast(this, "取得位置失敗")
                Handler().postDelayed({
                    sb_weather.isChecked = false
                }, 1000)
            }
        } else {
            sb_weather.isChecked = false
            SharedService.showTextToast(this, "您拒絕了天氣播報權限")
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun startPlaying(uri: Uri) {
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
        }
        mMPSpeaker.setVolume(1f, 1f)
        mMPSpeaker.prepare()
        mMPSpeaker.start()
        mIsSpeakerPlaying = true
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
        val id = item?.itemId

        if (id == android.R.id.home) {
            finish()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    fun save(view: View) {
        pvTime.returnData()
        //檢查時間是否重複
        val alarmClocks = SharedService.getAlarmClocks(this)
        for (i in 0 until alarmClocks.alarmClockList.size)
            if (mAlarmClock.hour == alarmClocks.alarmClockList[i].hour &&
                    mAlarmClock.minute == alarmClocks.alarmClockList[i].minute &&
                    mAlarmClock.acId != alarmClocks.alarmClockList[i].acId) {
                //時間完全一樣則判斷天數是否有重複
                for (day in 0..6) {
                    if (mAlarmClock.isRepeatArr[day] && alarmClocks.alarmClockList[i].isRepeatArr[day]) {
                        SharedService.showTextToast(this, "錯誤，已有相同時間。")
                        return
                    }
                }

            }

        if (mAlarmClock.speaker == -1) {
            SharedService.showTextToast(this, "請選擇播報者")
            return
        }

        bindDownloadService(object : CanStartDownloadCallback {
            override fun start() {
                startDownload(mAlarmClock, object : SpeechDownloader.DownloadFinishListener {
                    override fun cancel() {

                    }

                    override fun startSetData() {
                        updateAlarmClock()
                    }

                    override fun allFinished() {
                        finish()
                    }
                })
            }
        })
    }

    private fun updateAlarmClock() {
        val alarmClocks = SharedService.getAlarmClocks(this)

        //檢查修改後順序有沒有改變，有改變則刪掉舊資料
        var isChangePosition = false
        if (!mIsNew) {
            for (i in 0 until alarmClocks.alarmClockList.size)
                if (alarmClocks.alarmClockList[i].acId == mAlarmClock.acId) {
                    //避免自動開啟造成的下載
                    if (!alarmClocks.alarmClockList[i].isOpen)
                        intent.putExtra("IsAutoOn", true)

                    //非第一個alarmClock，新小時小於上一個alarmClock小時 或 新小時等於上一個alarmClock小時且新分鐘小於上一個alarmClock分鐘
                    if (i > 0 && (mAlarmClock.hour < alarmClocks.alarmClockList[i - 1].hour ||
                                    (mAlarmClock.hour == alarmClocks.alarmClockList[i - 1].hour &&
                                            mAlarmClock.minute < alarmClocks.alarmClockList[i - 1].minute)))
                        isChangePosition = true

                    //非最後一個alarmClock，新小時大於下一個alarmClock小時 或 新小時等於下一個alarmClock小時且新分鐘大於下一個alarmClock分鐘
                    if (i < alarmClocks.alarmClockList.size - 1 && (mAlarmClock.hour > alarmClocks.alarmClockList[i + 1].hour ||
                                    (mAlarmClock.hour == alarmClocks.alarmClockList[i + 1].hour &&
                                            mAlarmClock.minute > alarmClocks.alarmClockList[i + 1].minute)))
                        isChangePosition = true

                    if (isChangePosition) {
                        intent.putExtra("IsChangePosition", true)
                        alarmClocks.alarmClockList.removeAt(i)
                    } else {
                        //沒換位置則直接更新資料
                        alarmClocks.alarmClockList[i] = mAlarmClock
                    }
                    break
                }
        }

        //新資料或有更新位置則插入資料
        if (mIsNew || isChangePosition) {
            //取得應該插入的位置
            var index = 0
            for (i in 0 until alarmClocks.alarmClockList.size)
            //新小時大於當前alarmClock小時 或 (新小時等於當前alarmClock小時 且 新分鐘大於當前alarmClock分鐘則繼續找)
                if (mAlarmClock.hour > alarmClocks.alarmClockList[i].hour ||
                        (mAlarmClock.hour == alarmClocks.alarmClockList[i].hour &&
                                mAlarmClock.minute > alarmClocks.alarmClockList[i].minute))
                    index = i + 1
                //否則直接結束
                else
                    break

            //插入此次資料
            alarmClocks.alarmClockList.add(index, mAlarmClock)
            intent.putExtra("NewPosition", index)
        }

        //更新資料儲存
        SharedService.updateAlarmClocks(this, alarmClocks)

        //已正確設置資料
        intent.putExtra("AlarmClockJsonStr", Gson().toJson(mAlarmClock))
        intent.putExtra("IsNew", mIsNew)
        setResult(Activity.RESULT_OK, intent)
    }

}
