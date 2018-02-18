package com.fsmytsai.aiclock.ui.activity

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.util.Log
import com.fsmytsai.aiclock.R
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.fsmytsai.aiclock.AlarmReceiver
import com.fsmytsai.aiclock.model.*
import com.fsmytsai.aiclock.service.app.SharedService
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
import java.io.File


class AddAlarmClockActivity : AppCompatActivity() {
    private lateinit var mAlarmClock: AlarmClock
    private var mTexts = Texts(0, ArrayList())
    private var mPromptData: PromptData? = null
    private var mIsNew = true
    private var mIsSpeakerPlaying = false
    private var mMPSpeaker = MediaPlayer()
    private lateinit var mTimePickerDialog: TimePickerDialog
    private var mNeedDownloadCount = 0f
    private var mDownloadedCount = 0f
    private lateinit var pbDownloading: ProgressBar
    private lateinit var tvDownloading: TextView
    private lateinit var dialogDownloading: AlertDialog
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
                            deleteOldTextsData(false)
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
            startPlaying(uri!!, false, false)
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
    private fun startPlaying(uri: Uri, isSetFinish: Boolean, isPrompt: Boolean) {
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
            if (isSetFinish) {
                val promptUri = Uri.fromFile(File("$filesDir/sounds/${mPromptData!!.data.text_id}-0.wav"))
                startPlaying(promptUri, false, true)
            }
            if (isPrompt) {
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
//        val isRepeatChoosed = mAlarmClock.isRepeatArr.any { it }
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

        val promptDataList = getPrompt()

        if (promptDataList.size == 0) {
            Toast.makeText(this, "不可設置太接近當前時間", Toast.LENGTH_SHORT).show()
            return
        }

        getPromptData(promptDataList)
    }

    private fun getPromptData(dataList: ArrayList<Long>) {
        val dialogView = layoutInflater.inflate(R.layout.block_download_dialog, null)
        pbDownloading = dialogView.pb_downloading
        tvDownloading = dialogView.tv_downloading
        dialogDownloading = AlertDialog.Builder(this)
                .setView(dialogView)
                .create()
        dialogDownloading.setCanceledOnTouchOutside(false)
        dialogDownloading.show()

        val request = Request.Builder()
                .url("${getString(R.string.server_url)}api/getPromptData?day=${dataList[0]}&hour=${dataList[1]}&" +
                        "minute=${dataList[2]}&second=${dataList[3]}&speaker=${mAlarmClock.speaker}")
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
                        mPromptData = Gson().fromJson(resMessage, PromptData::class.java)
                        if (mPromptData!!.is_success) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                                pbDownloading.setProgress(5, true)
                            else
                                pbDownloading.progress = 5
                            tvDownloading.text = "5/100"
                            getTextData()
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

    private fun getTextData() {
        val request = Request.Builder()
                .url("${getString(R.string.server_url)}api/getTextData?hour=${mAlarmClock.hour}&" +
                        "minute=${mAlarmClock.minute}&speaker=${mAlarmClock.speaker}&category=${mAlarmClock.category}&" +
                        "latitude=${mAlarmClock.latitude}&longitude=${mAlarmClock.longitude}")
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
                                pbDownloading.progress = 10
                            tvDownloading.text = "10/100"
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
        FileDownloader.getImpl().create("${getString(R.string.server_url)}sounds/${mPromptData!!.data.text_id}-0.wav")
                .setPath("${filesDir.absolutePath}/sounds/${mPromptData!!.data.text_id}-0.wav")
                .setTag(0, "${mPromptData!!.data.text_id}-0")
                .setTag(2, true)
                .setCallbackProgressTimes(0)
                .setListener(mQueueTarget)
                .asInQueueTask()
                .enqueue()
        mNeedDownloadCount++
        for (text in mTexts.textList) {
            for (i in 0 until text.part_count) {
                FileDownloader.getImpl().create("${getString(R.string.server_url)}sounds/${text.text_id}-$i.wav")
                        .setPath("${filesDir.absolutePath}/sounds/${text.text_id}-$i.wav")
                        .setTag(0, "${text.text_id}-$i")
                        .setTag(1, text)
                        .setTag(2, false)
                        .setCallbackProgressTimes(0)
                        .setListener(mQueueTarget)
                        .asInQueueTask()
                        .enqueue()
                mNeedDownloadCount++
            }
        }
        FileDownloader.getImpl().start(mQueueTarget, false)
    }

    private val mQueueTarget: FileDownloadListener = object : FileDownloadListener() {
        override fun pending(task: BaseDownloadTask, soFarBytes: Int, totalBytes: Int) {}

        override fun connected(task: BaseDownloadTask?, etag: String?, isContinue: Boolean, soFarBytes: Int, totalBytes: Int) {}

        override fun progress(task: BaseDownloadTask, soFarBytes: Int, totalBytes: Int) {}

        override fun blockComplete(task: BaseDownloadTask?) {}

        override fun retry(task: BaseDownloadTask?, ex: Throwable?, retryingTimes: Int, soFarBytes: Int) {}

        override fun completed(task: BaseDownloadTask) {
            if (task.getTag(2) as Boolean) {

            } else {
                val text = task.getTag(1) as Text
                text.completeDownloadCount++
                Log.d("AddAlarmClockActivity", "file ${task.getTag(0) as String} text_id = ${text.text_id} completeDownloadCount = ${text.completeDownloadCount}")
            }
            mDownloadedCount++

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                pbDownloading.setProgress(10 + (mDownloadedCount / mNeedDownloadCount * 90).toInt(), true)
            else
                pbDownloading.progress = 10 + (mDownloadedCount / mNeedDownloadCount * 90).toInt()

            tvDownloading.text = "${10 + (mDownloadedCount / mNeedDownloadCount * 90).toInt()}/100"

            if (mDownloadedCount == mNeedDownloadCount) {
                pbDownloading.progress = 100
                tvDownloading.text = "100/100"

                Log.d("AddAlarmClockActivity", "Download Finish")
                var uri: Uri? = null
                when (mAlarmClock.speaker) {
                    0 -> uri = Uri.parse("android.resource://$packageName/raw/setfinishf1")
                    1 -> uri = Uri.parse("android.resource://$packageName/raw/setfinishf2")
                    2 -> uri = Uri.parse("android.resource://$packageName/raw/setfinishm1")
                }
                startPlaying(uri!!, true, false)
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

    private fun deleteOldTextsData(isAdd: Boolean) {
        val spDatas = getSharedPreferences("Datas", Context.MODE_PRIVATE)
        val textsList: TextsList
        val textsListJsonStr = spDatas.getString("TextsListJsonStr", "")
        if (textsListJsonStr != "") {
            textsList = Gson().fromJson(textsListJsonStr, TextsList::class.java)
            for (texts in textsList.textsList) {
                if (texts.acId == mAlarmClock.acId)
                    textsList.textsList.remove(texts)
            }
            if (!isAdd)
                spDatas.edit().putString("TextsListJsonStr", Gson().toJson(textsList)).apply()
        } else {
            textsList = TextsList(ArrayList())
        }
        if (isAdd) {
            textsList.textsList.add(mTexts)
            spDatas.edit().putString("TextsListJsonStr", Gson().toJson(textsList)).apply()
        }
    }

    private fun returnData() {
        setAlarm()
        deleteOldTextsData(true)

        intent.putExtra("AlarmClockJsonStr", Gson().toJson(mAlarmClock))
        intent.putExtra("IsNew", mIsNew)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private val mAlarmCalendar = Calendar.getInstance()

    private fun getPrompt(): ArrayList<Long> {
        val nowCalendar = Calendar.getInstance()
        var addDate = 0

        //排列7天內的DAY_OF_WEEK
        val days = ArrayList<Int>()
        for (i in (nowCalendar.get(Calendar.DAY_OF_WEEK) - 1)..(nowCalendar.get(Calendar.DAY_OF_WEEK) + 5)) {
            days.add(i % 7)
        }
        for (i in days) {
            if (mAlarmClock.isRepeatArr[i]) {
                //當天有圈，判斷設置的時間是否大於現在時間
                if (i == nowCalendar.get(Calendar.DAY_OF_WEEK) - 1) {
                    if (mAlarmClock.hour > nowCalendar.get(Calendar.HOUR_OF_DAY))
                        break
                    else if (mAlarmClock.hour == nowCalendar.get(Calendar.HOUR_OF_DAY) &&
                            mAlarmClock.minute > nowCalendar.get(Calendar.MINUTE))
                        break
                    else
                    //當天有圈但設置時間小於現在時間，等於下星期的今天
                        addDate++
                } else
                //不是當天代表可結束計算
                    break
            } else
                addDate++
        }

        mAlarmCalendar.add(Calendar.DATE, addDate)
        mAlarmCalendar.set(Calendar.HOUR_OF_DAY, mAlarmClock.hour)
        mAlarmCalendar.set(Calendar.MINUTE, mAlarmClock.minute)
        mAlarmCalendar.set(Calendar.SECOND, 0)
        
        var differenceSecond = (mAlarmCalendar.timeInMillis - nowCalendar.timeInMillis) / 1000

        val promptDataList = ArrayList<Long>()

        if (differenceSecond < 40)
            return promptDataList

        if (differenceSecond > 60 * 60 * 24) {
            promptDataList.add(differenceSecond / (60 * 60 * 24))
            differenceSecond %= (60 * 60 * 24)
        } else
            promptDataList.add(0)

        if (differenceSecond > 60 * 60) {
            promptDataList.add(differenceSecond / (60 * 60))
            differenceSecond %= (60 * 60)
        } else
            promptDataList.add(0)

        if (differenceSecond > 60)
            promptDataList.add(differenceSecond / 60)
        else
            promptDataList.add(0)

        if (differenceSecond > 0 && promptDataList[0] == 0L && promptDataList[1] == 0L && promptDataList[2] == 0L)
            promptDataList.add(differenceSecond)
        else
            promptDataList.add(0)

        return promptDataList
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun setAlarm() {
        val intent = Intent(this, AlarmReceiver::class.java)
        intent.putExtra("ACId", mAlarmClock.acId)
        val pi = PendingIntent.getBroadcast(this, mAlarmClock.acId, intent, PendingIntent.FLAG_ONE_SHOT)
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(mAlarmCalendar.timeInMillis, pi)
                am.setAlarmClock(alarmClockInfo, pi)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> am.setExact(AlarmManager.RTC_WAKEUP, mAlarmCalendar.timeInMillis, pi)
            else -> am.set(AlarmManager.RTC_WAKEUP, mAlarmCalendar.timeInMillis, pi)
        }
    }

}
