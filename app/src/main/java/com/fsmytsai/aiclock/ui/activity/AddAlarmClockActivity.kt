package com.fsmytsai.aiclock.ui.activity

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import com.fsmytsai.aiclock.R
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import com.fsmytsai.aiclock.model.*
import com.fsmytsai.aiclock.service.app.SharedService
import com.fsmytsai.aiclock.service.app.SpeechDownloader
import com.fsmytsai.aiclock.ui.view.MyRadioGroup
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_add_alarm_clock.*
import java.util.*


class AddAlarmClockActivity : DownloadSpeechActivity() {
    private lateinit var mAlarmClock: AlarmClock
    private var mIsNew = true
    private var mIsSpeakerPlaying = false
    private var mMPSpeaker = MediaPlayer()
    private lateinit var mTimePickerDialog: TimePickerDialog

    private val REQUEST_LOCATION = 888

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_alarm_clock)
        getAlarmClockData()
        initViews()
        if (intent.getBooleanExtra("IsOpen", false))
            save(View(this))
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
                mMPSpeaker.stop()
            var uri: Uri? = null
            when (checkedId) {
                R.id.rb_f1 -> {
                    mAlarmClock.speaker = 0
                    uri = Uri.parse("android.resource://$packageName/raw/f1")
                }
                R.id.rb_f2 -> {
                    mAlarmClock.speaker = 1
                    uri = Uri.parse("android.resource://$packageName/raw/f2")
                }
                R.id.rb_m1 -> {
                    mAlarmClock.speaker = 2
                    uri = Uri.parse("android.resource://$packageName/raw/m1")
                }
            }
            startPlaying(uri!!)
        }

        sb_weather.isChecked = mAlarmClock.latitude != 1000.0

        sb_weather.setOnCheckedChangeListener { view, isChecked ->
            if (isChecked) {
                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                        ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    locationServiceInitial()
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

        rg_NewsType.setOnCheckedChangeListener(object : MyRadioGroup.OnCheckedChangeListener {
            override fun onCheckedChanged(group: MyRadioGroup, checkedId: Int) {
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

        })

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

    private fun getAlarmClockData() {
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
                    booleanArrayOf(false, false, false, false, false, false, false),
                    true)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION && grantResults.size == 2 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            locationServiceInitial()
        } else {
            sb_weather.isChecked = false
            Toast.makeText(this, "取得位置權限失敗", Toast.LENGTH_SHORT).show()
        }
    }

    private lateinit var mLocationManager: LocationManager
    @SuppressLint("MissingPermission")
    private fun locationServiceInitial() {
        mLocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = mLocationManager.getProviders(true)
        var bestLocation: Location? = null
        for (provider in providers) {
            val l = mLocationManager.getLastKnownLocation(provider) ?: continue
            if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                bestLocation = l
            }
        }

        if (bestLocation == null) {
            Toast.makeText(this, "取得位置失敗", Toast.LENGTH_SHORT).show()
            sb_weather.isChecked = false
        } else {
            mAlarmClock.latitude = bestLocation.latitude
            mAlarmClock.longitude = bestLocation.longitude
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

    private fun setTimeField() {
        mTimePickerDialog = TimePickerDialog(this, TimePickerDialog.OnTimeSetListener { p0, p1, p2 ->
            et_Time.setText("${String.format("%02d", p1)}:${String.format("%02d", p2)}")
            mAlarmClock.hour = p1
            mAlarmClock.minute = p2
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
        val id = item?.itemId

        if (id == android.R.id.home) {
            finish()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    fun save(view: View) {
        //檢查時間是否重複
        val spDatas = getSharedPreferences("Datas", Context.MODE_PRIVATE)
        val alarmClocksJsonStr = spDatas.getString("AlarmClocksJsonStr", "")
        if (alarmClocksJsonStr != "") {
            val alarmClocks = Gson().fromJson(alarmClocksJsonStr, AlarmClocks::class.java)
            for (i in 0 until alarmClocks.alarmClockList.size)
                if (mAlarmClock.hour == alarmClocks.alarmClockList[i].hour &&
                        mAlarmClock.minute == alarmClocks.alarmClockList[i].minute &&
                        mAlarmClock.acId != alarmClocks.alarmClockList[i].acId) {
                    Toast.makeText(this, "錯誤，已有相同時間。", Toast.LENGTH_SHORT).show()
                    return
                }
        }

        if (mAlarmClock.speaker == -1) {
            Toast.makeText(this, "請選擇播報者", Toast.LENGTH_SHORT).show()
            return
        }

        var isRepeatChoose = false
        for (isRepeat in mAlarmClock.isRepeatArr) {
            if (isRepeat) {
                isRepeatChoose = true
                break
            }
        }

        if (!isRepeatChoose) {
            Toast.makeText(this, "請選擇重複天數", Toast.LENGTH_SHORT).show()
            return
        }

        bindDownloadService(object: CanStartDownloadCallback{
            override fun start() {
                val isSuccess = startDownload(mAlarmClock, object : SpeechDownloader.DownloadFinishListener {
                    override fun finish() {
                        returnData()
                    }
                })
            }
        })
    }

    private fun returnData() {
        intent.putExtra("AlarmClockJsonStr", Gson().toJson(mAlarmClock))
        intent.putExtra("IsNew", mIsNew)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

}
