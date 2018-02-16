package com.fsmytsai.aiclock.ui.activity

import android.annotation.TargetApi
import android.app.Activity
import android.app.TimePickerDialog
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.content.ContextCompat
import com.fsmytsai.aiclock.R
import android.view.MenuItem
import android.view.View
import android.widget.TimePicker
import android.widget.Toast
import com.fsmytsai.aiclock.model.AlarmClock
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_add_alarm_clock.*
import java.util.*


class AddAlarmClockActivity : AppCompatActivity() {
    private lateinit var alarmClock: AlarmClock
    private var isNew = true
    private var isSpeakerPlaying = false
    private var mpSpeaker = MediaPlayer()
    private lateinit var timePickerDialog: TimePickerDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_alarm_clock)
        initViews()
    }

    override fun onStop() {
        super.onStop()
        mpSpeaker?.release()
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun initViews() {
        getAlarmClockData()
        if(isNew)
            tv_toolBar.setText("新增智能鬧鐘")
        else
            tv_toolBar.setText("編輯智能鬧鐘")

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        et_Time.setText("${alarmClock.hour}:${alarmClock.minute}")
        setTimeField()
        et_Time.setOnClickListener {
            timePickerDialog.show()
        }

        when (alarmClock.speaker) {
            0 -> rb_f1.isChecked = true
            1 -> rb_f2.isChecked = true
            2 -> rb_m1.isChecked = true
        }

        rg_speaker.setOnCheckedChangeListener { radioGroup, checkedId ->
            if (isSpeakerPlaying)
                mpSpeaker?.stop()
            var uri: Uri? = null
            when (checkedId) {
                R.id.rb_f1 -> {
                    alarmClock.speaker = 0
                    uri = Uri.parse("android.resource://${packageName}/raw/f1")
                }
                R.id.rb_f2 -> {
                    alarmClock.speaker = 1
                    uri = Uri.parse("android.resource://${packageName}/raw/f2")
                }
                R.id.rb_m1 -> {
                    alarmClock.speaker = 2
                    uri = Uri.parse("android.resource://${packageName}/raw/m1")
                }
            }
            mpSpeaker = MediaPlayer()
            mpSpeaker.setDataSource(this, uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                mpSpeaker.setAudioAttributes(audioAttributes)
            } else {
                mpSpeaker.setAudioStreamType(AudioManager.STREAM_ALARM)
            }
            mpSpeaker.setOnCompletionListener {
                isSpeakerPlaying = false
            }
            mpSpeaker.setVolume(1f, 1f)
            mpSpeaker.prepare()
            mpSpeaker.start()
            isSpeakerPlaying = true
        }

        when (alarmClock.category) {
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
                R.id.rb_no -> alarmClock.category = -1
                R.id.rb_general -> alarmClock.category = 0
                R.id.rb_business -> alarmClock.category = 1
                R.id.rb_entertainment -> alarmClock.category = 2
                R.id.rb_health -> alarmClock.category = 3
                R.id.rb_science -> alarmClock.category = 4
                R.id.rb_sports -> alarmClock.category = 5
                R.id.rb_technology -> alarmClock.category = 6
            }
        }

        val circleTextviewFull = ContextCompat.getDrawable(this, R.drawable.circle_textview_full)
        for (i in 0..6) {
            if (alarmClock.isRepeatArr[i])
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

    private fun getAlarmClockData(){
        val AlarmClockJsonStr = intent.getStringExtra("AlarmClockJsonStr")
        if (AlarmClockJsonStr != null) {
            isNew = false
            alarmClock = Gson().fromJson(AlarmClockJsonStr, AlarmClock::class.java)
            alarmClock.isOpen = true
        } else {
            val newCalendar = Calendar.getInstance()
            val acId = intent.getIntExtra("acId", 0)
            alarmClock = AlarmClock(acId,
                    newCalendar.get(Calendar.HOUR_OF_DAY),
                    newCalendar.get(Calendar.MINUTE),
                    -1,
                    -1,
                    booleanArrayOf(false, false, false, false, false, false, false),
                    true)
        }
    }

    private fun setTimeField() {
        timePickerDialog = TimePickerDialog(this, object : TimePickerDialog.OnTimeSetListener {
            override fun onTimeSet(p0: TimePicker?, p1: Int, p2: Int) {
                et_Time.setText("${String.format("%02d", p1)}:${String.format("%02d", p2)}")
                alarmClock.hour = p1
                alarmClock.minute = p2
            }
        }, alarmClock.hour, alarmClock.minute, true)
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
        if (alarmClock.isRepeatArr[index]) {
            view.background = circleTextview
            alarmClock.isRepeatArr[index] = false
        } else {
            view.background = circleTextviewFull
            alarmClock.isRepeatArr[index] = true
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
        if (alarmClock.speaker == -1) {
            Toast.makeText(this, "請選擇播報者", Toast.LENGTH_SHORT).show()
            return
        }
        var isRepeatChoosed = false
        for(isRepeat in alarmClock.isRepeatArr){
            if(isRepeat){
                isRepeatChoosed = true
                break
            }
        }

        if(!isRepeatChoosed){
            Toast.makeText(this, "請選擇重複天數", Toast.LENGTH_SHORT).show()
            return
        }

        intent.putExtra("AlarmClockJsonStr", Gson().toJson(alarmClock))
        intent.putExtra("IsNew", isNew)
        setResult(Activity.RESULT_OK, intent)
        finish()

    }
}
