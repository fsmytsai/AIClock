package com.fsmytsai.aiclock.ui.activity

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import com.fsmytsai.aiclock.R
import android.widget.RadioButton
import com.bigkoo.pickerview.OptionsPickerView
import com.fsmytsai.aiclock.model.*
import com.fsmytsai.aiclock.service.app.SharedService
import com.fsmytsai.aiclock.service.app.SpeechDownloader
import com.fsmytsai.aiclock.ui.view.MyRadioGroup
import kotlinx.android.synthetic.main.activity_add_alarm_clock.*
import java.util.*
import com.bigkoo.pickerview.TimePickerView
import com.fsmytsai.aiclock.service.app.FileChooser
import kotlinx.android.synthetic.main.block_background_music.view.*
import kotlinx.android.synthetic.main.footer_background_music.view.*
import java.io.*
import kotlin.collections.ArrayList


class AddAlarmClockActivity : DownloadSpeechActivity() {
    private var mMediaPlayer = MediaPlayer()
    private lateinit var mFileChooser: FileChooser

    //data
    private lateinit var mAlarmClock: AlarmClocks.AlarmClock

    private val mBackgroundMusicList = ArrayList<String>()

    //control
    private var mIsNew = true
    private var mIsMute = false
    private var mIsPlaying = false
    private var mNowPlayingFileName = ""

    //CODE
    private val REQUEST_LOCATION = 888
    private val REQUEST_EXTERNAL_STORAGE = 18

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_alarm_clock)
        val spDatas = getSharedPreferences("Datas", Context.MODE_PRIVATE)
        mIsMute = spDatas.getBoolean("IsMute", false)
        getAlarmClock()
        setBackgroundMusicList()
        initViews()
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        val id = item?.itemId

        if (id == android.R.id.home) {
            finish()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onStop() {
        super.onStop()
        mMediaPlayer.release()
    }

    private fun getAlarmClock() {
        val acId = intent.getIntExtra("acId", 0)
        val alarmClock = SharedService.getAlarmClock(this, acId)
        if (alarmClock != null) {
            mIsNew = false
            mAlarmClock = alarmClock
        } else {
            val newCalendar = Calendar.getInstance()
            mAlarmClock = AlarmClocks.AlarmClock(acId,
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

    private fun setBackgroundMusicList() {
        mBackgroundMusicList.clear()
        mBackgroundMusicList.add("預設")
        File("$filesDir/bgmSounds/").mkdir()
        val directory = File("$filesDir/bgmSounds")
        directory.listFiles().mapTo(mBackgroundMusicList) { it.name }
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
//            1 -> rb_f2.isChecked = true
            2 -> rb_m1.isChecked = true
        }

        rg_speaker.setOnCheckedChangeListener { _, checkedId ->
            var uri: Uri? = null
            when (checkedId) {
                R.id.rb_f1 -> {
                    mAlarmClock.speaker = 0
                    uri = Uri.parse("android.resource://$packageName/raw/f1_hello")
                }
//                R.id.rb_f2 -> {
//                    mAlarmClock.speaker = 1
//                    uri = Uri.parse("android.resource://$packageName/raw/f2_hello")
//                }
                R.id.rb_m1 -> {
                    mAlarmClock.speaker = 2
                    uri = Uri.parse("android.resource://$packageName/raw/m1_hello")
                }
            }
            if (!mIsMute) {
                if (mIsPlaying)
                    mMediaPlayer.release()
                startPlaying(uri!!)
            }
        }

        sc_weather.isChecked = mAlarmClock.latitude != 1000.0

        sc_weather.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                        ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    val isSuccess = SharedService.setLocation(this, mAlarmClock)
                    if (!isSuccess)
                        SharedService.showTextToast(this, "取得位置中...")
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
//            6 -> rb_technology.isChecked = true
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
            pvNewsCount.setNPicker(arrayListOf("6", "7", "8", "9", "10"), null, null)
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

        rv_background_music.layoutManager = GridLayoutManager(this, 3)
        rv_background_music.adapter = BackgroundMusicAdapter()
    }

    private val pvTime by lazy {
        val mAlarmTimeCalendar = Calendar.getInstance()
        mAlarmTimeCalendar.set(Calendar.HOUR_OF_DAY, mAlarmClock.hour)
        mAlarmTimeCalendar.set(Calendar.MINUTE, mAlarmClock.minute)

        TimePickerView.Builder(this, TimePickerView.OnTimeSelectListener { date, _ ->
            val calendar = Calendar.getInstance()
            calendar.time = date
            mAlarmClock.hour = calendar.get(Calendar.HOUR_OF_DAY)
            mAlarmClock.minute = calendar.get(Calendar.MINUTE)
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
        if (requestCode == REQUEST_LOCATION)
            if (grantResults.size == 2 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                    grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                val isSuccess = SharedService.setLocation(this, mAlarmClock)
                if (!isSuccess)
                    SharedService.showTextToast(this, "取得位置中...")
            } else {
                sc_weather.isChecked = false
                SharedService.showTextToast(this, "您拒絕了天氣播報權限")
            }

        if (requestCode == REQUEST_EXTERNAL_STORAGE)
            if (grantResults.size == 1 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED)
                chooseAudio()
            else
                SharedService.showTextToast(this, "您拒絕了選擇檔案的權限")
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun startPlaying(uri: Uri) {
        mMediaPlayer = MediaPlayer()
        mMediaPlayer.setDataSource(this, uri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
            mMediaPlayer.setAudioAttributes(audioAttributes)
        } else {
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM)
        }
        mMediaPlayer.setOnCompletionListener {
            mNowPlayingFileName = ""
            mIsPlaying = false
            mMediaPlayer.release()
        }
        mMediaPlayer.setScreenOnWhilePlaying(true)
        mMediaPlayer.prepare()
        mMediaPlayer.start()
        mIsPlaying = true
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

    private inner class BackgroundMusicAdapter : RecyclerView.Adapter<BackgroundMusicAdapter.ViewHolder>() {

        val TYPE_FOOTER = 1  //说明是带有Footer的
        val TYPE_NORMAL = 2  //说明是不带有header和footer的

        override fun getItemViewType(position: Int): Int {
            return if (position == itemCount - 1) TYPE_FOOTER else TYPE_NORMAL
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = if (viewType == TYPE_FOOTER)
                LayoutInflater.from(parent.context).inflate(R.layout.footer_background_music, parent, false)
            else
                LayoutInflater.from(parent.context).inflate(R.layout.block_background_music, parent, false)

            return ViewHolder(view)
        }

        @TargetApi(Build.VERSION_CODES.M)
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            if (getItemViewType(position) == TYPE_FOOTER) {
                holder.tvSelect.setOnClickListener {
                    if (ActivityCompat.checkSelfPermission(this@AddAlarmClockActivity, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                        chooseAudio()
                    } else {
                        requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_EXTERNAL_STORAGE)
                    }
                }
                return
            }

            if (position == 0) {
                if (mAlarmClock.backgroundMusic == null) {
                    holder.tvBackgroundMusic.setBackgroundResource(R.drawable.button_rounded2)
                    holder.tvBackgroundMusic.setTextColor(Color.WHITE)
                } else {
                    holder.tvBackgroundMusic.setBackgroundResource(R.drawable.button_rounded)
                    holder.tvBackgroundMusic.setTextColor(Color.BLACK)
                }
            } else {
                if (mAlarmClock.backgroundMusic == mBackgroundMusicList[position]) {
                    holder.tvBackgroundMusic.setBackgroundResource(R.drawable.button_rounded2)
                    holder.tvBackgroundMusic.setTextColor(Color.WHITE)
                } else {
                    holder.tvBackgroundMusic.setBackgroundResource(R.drawable.button_rounded)
                    holder.tvBackgroundMusic.setTextColor(Color.BLACK)
                }
            }

            holder.tvBackgroundMusic.text = mBackgroundMusicList[position]
            if (position == 0)
                holder.ivDelete.visibility = View.GONE
            else {
                holder.ivDelete.visibility = View.VISIBLE
                holder.ivDelete.setOnClickListener {
                    if (mIsPlaying && mNowPlayingFileName == mBackgroundMusicList[position]) {
                        mMediaPlayer.release()
                        mNowPlayingFileName = ""
                    }

                    val deleteFile = File("$filesDir/bgmSounds/${mBackgroundMusicList[position]}")
                    if (deleteFile.delete()) {
                        mAlarmClock.backgroundMusic = null

                        var promptResetBGM = false

                        val alarmClocks = SharedService.getAlarmClocks(this@AddAlarmClockActivity, false)
                        for (alarmClock in alarmClocks.alarmClockList) {
                            if (alarmClock.backgroundMusic == mBackgroundMusicList[position]) {
                                alarmClock.backgroundMusic = null
                                promptResetBGM = true
                            }
                        }

                        if (promptResetBGM) {
                            SharedService.updateAlarmClocks(this@AddAlarmClockActivity, alarmClocks, false)
                            AlertDialog.Builder(this@AddAlarmClockActivity)
                                    .setTitle("提示")
                                    .setMessage("有鬧鐘的背景音樂使用此音檔，已將其重設為預設。")
                                    .setPositiveButton("知道了", null)
                                    .show()
                        }

                        mBackgroundMusicList.removeAt(position)
                        rv_background_music.adapter.notifyItemRemoved(position)
                        rv_background_music.adapter.notifyItemRangeChanged(position, mBackgroundMusicList.size - position)
                    } else
                        SharedService.showTextToast(this@AddAlarmClockActivity, "刪除失敗")
                }
            }

            val layout = holder.tvBackgroundMusic.layout
            holder.tvBackgroundMusic.gravity = Gravity.CENTER
            if (layout != null) {
                val lines = layout.lineCount
                if (lines > 0)
                    if (layout.getEllipsisCount(lines - 1) > 0) {
                        holder.tvBackgroundMusic.gravity = Gravity.START
                    }
            }

            holder.tvBackgroundMusic.setOnClickListener {
                if (position == 0)
                    mAlarmClock.backgroundMusic = null
                else
                    mAlarmClock.backgroundMusic = mBackgroundMusicList[position]

                rv_background_music.adapter.notifyDataSetChanged()

                if (!mIsMute) {
                    if (mIsPlaying)
                        mMediaPlayer.release()
                    val tempFileName: String
                    val uri = if (mAlarmClock.backgroundMusic != null) {
                        tempFileName = mBackgroundMusicList[position]
                        Uri.parse("$filesDir/bgmSounds/$tempFileName")
                    } else {
                        tempFileName = "bgm"
                        Uri.parse("android.resource://$packageName/raw/bgm")
                    }
                    if (mNowPlayingFileName == tempFileName)
                        mNowPlayingFileName = ""
                    else {
                        mNowPlayingFileName = tempFileName
                        SharedService.showTextToast(this@AddAlarmClockActivity, "再點一次停止試聽")
                        startPlaying(uri!!)
                    }
                }
            }
        }

        override fun getItemCount(): Int {
            return mBackgroundMusicList.size + 1
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvBackgroundMusic = itemView.tv_background_music
            val ivDelete = itemView.iv_delete
            val tvSelect = itemView.tv_select
        }

    }

    private fun chooseAudio() {
        mFileChooser = FileChooser(this)
        mFileChooser.showFileChooser("audio/*", "選擇音檔", false, true)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FileChooser.ACTIVITY_FILE_CHOOSER) {
            if (mFileChooser.onActivityResult(requestCode, resultCode, data) && mFileChooser.chosenFiles.isNotEmpty()) {
                val file = mFileChooser.chosenFiles[0]
                val bytes = ByteArray(file.length().toInt())
                val newFile = File("$filesDir/bgmSounds/${file.name}")

                if (newFile.exists())
                    SharedService.showTextToast(this, "音檔已存在")
                else {
                    try {
                        val buf = BufferedInputStream(FileInputStream(file))
                        buf.read(bytes, 0, bytes.size)
                        buf.close()
                        val outputStream = FileOutputStream(newFile)
                        outputStream.write(bytes)
                        outputStream.close()
                    } catch (e: FileNotFoundException) {
                        e.printStackTrace()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }

                    mBackgroundMusicList.add(file.name)
                    rv_background_music.adapter.notifyItemInserted(mBackgroundMusicList.lastIndex)
                }
            } else
                SharedService.showTextToast(this, "選擇檔案失敗")
        }
    }

    fun save(view: View) {
        if (mIsPlaying)
            mMediaPlayer.release()

        pvTime.returnData()

        //檢查時間是否重複
        if (SharedService.isAlarmClockTimeRepeat(this, mAlarmClock, false) ||
                SharedService.isAlarmClockTimeRepeat(this, mAlarmClock, true)) {
            SharedService.showTextToast(this, "錯誤，已有相同時間。")
            return
        }

        if (mAlarmClock.speaker == -1) {
            SharedService.showTextToast(this, "請選擇播報者")
            return
        }

        if (sc_weather.isChecked && mAlarmClock.latitude == 1000.0) {
            SharedService.showTextToast(this, "取得位置失敗")
            sc_weather.isChecked = false
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
        val alarmClocks = SharedService.getAlarmClocks(this, false)

        //檢查修改後順序有沒有改變，有改變則刪掉舊資料
        var isChangePosition = false
        if (!mIsNew) {
            for (i in 0 until alarmClocks.alarmClockList.size)
                if (alarmClocks.alarmClockList[i].acId == mAlarmClock.acId) {

                    if (!alarmClocks.alarmClockList[i].isOpen)
                        mAlarmClock.isOpen = true

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
        SharedService.updateAlarmClocks(this, alarmClocks, false)

        //已正確設置資料
        intent.putExtra("ACId", mAlarmClock.acId)
        intent.putExtra("IsNew", mIsNew)
        setResult(Activity.RESULT_OK, intent)
    }

}
