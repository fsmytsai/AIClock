package com.fsmytsai.aiclock.ui.activity

import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.support.v4.content.ContextCompat
import android.support.v4.util.LruCache
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ProgressBar
import com.fsmytsai.aiclock.AlarmService
import com.fsmytsai.aiclock.R
import com.fsmytsai.aiclock.model.AlarmClocks
import com.fsmytsai.aiclock.model.Texts
import com.fsmytsai.aiclock.service.app.SharedService
import com.fsmytsai.aiclock.service.app.SpeechDownloader
import com.fsmytsai.aiclock.ui.view.DragImageView
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_alarm.*
import kotlinx.android.synthetic.main.block_news.view.*
import kotlinx.android.synthetic.main.footer.view.*
import okhttp3.*
import java.io.File
import java.io.IOException
import java.util.*

class AlarmActivity : DownloadSpeechActivity() {
    private var mAlarmService: AlarmService? = null
    private var mAlarmClock: AlarmClocks.AlarmClock? = null
    private var mRealTexts: Texts = Texts()
    private var mIgnoreCount = 0
    private var mNowACId = 0
    private lateinit var mSPDatas: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keepFullScreen = true
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        hideBottomUIMenu()
        setContentView(R.layout.activity_alarm)
        mSPDatas = getSharedPreferences("Datas", Context.MODE_PRIVATE)
        val acId = intent.getIntExtra("ACId", 0)

        if (SharedService.checkAlarmClockTime(this, acId)) {
            setRealTexts(acId)
            initViews()
            startAlarmService()
        } else {
            SharedService.writeDebugLog(this, "AlarmActivity onCreate 延遲超過 9 分鐘")
            finish()
        }
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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            val acId = intent.getIntExtra("ACId", 0)
            if (acId != mNowACId) {
                if (SharedService.checkAlarmClockTime(this, acId))
                    reSetAll(acId)
                else
                    SharedService.writeDebugLog(this, "AlarmActivity onNewIntent 延遲超過 9 分鐘")
            } else
                SharedService.writeDebugLog(this, "AlarmActivity ACId 與當前一樣，不動作以取消後來的")
        }
    }

    private fun reSetAll(acId: Int) {
        setRealTexts(acId)
        rv_news.adapter.notifyDataSetChanged()
        stopAlarmService()
        startAlarmService()
    }

    private fun setRealTexts(acId: Int) {
        mNowACId = acId
        SharedService.writeDebugLog(this, "AlarmActivity setRealTexts mNowACId = $mNowACId")

        //初始化圖片快取
        initCache()
        mAlarmClock = SharedService.getAlarmClock(this, acId)
        val texts = SharedService.getTexts(this, acId)
        if (mAlarmClock != null && texts != null) {
            //初始化 mRealTexts
            mRealTexts = Texts()
            mRealTexts.acId = texts.acId
            mRealTexts.isOldData = texts.isOldData

            //過濾掉缺少音檔的 text
            for (text in texts.textList) {
                val addToRealTextsList = (0 until text.part_count)
                        .map { File("$filesDir/sounds/${text.text_id}-$it-${mAlarmClock!!.speaker}.wav") }
                        .all { it.exists() }
                if (addToRealTextsList) mRealTexts.textList.add(text)
            }
            mIgnoreCount = mRealTexts.textList.filter { it.description == "time" }.size
        }
    }

    private fun initViews() {
        rv_news.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        rv_news.adapter = NewsAdapter()

        div_close.setMyDragListener(object : DragImageView.MyDragListener {
            override fun finished() {
                ll_mask.visibility = View.INVISIBLE
                div_close.visibility = View.VISIBLE
                tv_five_minute.setBackgroundColor(ContextCompat.getColor(this@AlarmActivity, R.color.colorTransparentPaleBlue))
                tv_ten_minute.setBackgroundColor(ContextCompat.getColor(this@AlarmActivity, R.color.colorTransparentPaleBlue))
                tv_close.setBackgroundColor(ContextCompat.getColor(this@AlarmActivity, R.color.colorTransparentPaleBlue))
            }

            override fun down() {
                ll_mask.visibility = View.VISIBLE
            }

            override fun up(type: Int) {
                if (type == 3) {
                    mSPDatas.edit().putBoolean("PromptBall", false).apply()
                    finish()
                } else if (type != 0) {
                    mSPDatas.edit().putBoolean("PromptBall", false).apply()
                    val alarmCalendar = Calendar.getInstance()
                    alarmCalendar.add(Calendar.MINUTE, if (type == 1) 5 else 10)

                    val alarmClocks = SharedService.getAlarmClocks(this@AlarmActivity, true)

                    var biggestACId = 1000
                    for (alarmClock in alarmClocks.alarmClockList) {
                        if (alarmClock.acId > biggestACId)
                            biggestACId = alarmClock.acId
                    }

                    val alarmClock = AlarmClocks.AlarmClock(biggestACId + 1,
                            alarmCalendar.get(Calendar.HOUR_OF_DAY),
                            alarmCalendar.get(Calendar.MINUTE),
                            mAlarmClock!!.speaker,
                            mAlarmClock!!.latitude,
                            mAlarmClock!!.longitude,
                            mAlarmClock!!.category,
                            mAlarmClock!!.newsCount,
                            booleanArrayOf(false, false, false, false, false, false, false),
                            true)

                    //檢查時間是否重複
                    if (SharedService.isAlarmClockTimeRepeat(this@AlarmActivity, alarmClock, false) &&
                            SharedService.isAlarmClockTimeRepeat(this@AlarmActivity, alarmClock, true)) {
                        SharedService.showTextToast(this@AlarmActivity, "錯誤，已有相同時間。")
                        return
                    }

                    bindDownloadService(object : DownloadSpeechActivity.CanStartDownloadCallback {
                        override fun start() {
                            startDownload(alarmClock, object : SpeechDownloader.DownloadFinishListener {
                                override fun cancel() {

                                }

                                override fun startSetData() {
                                    SharedService.writeDebugLog(this@AlarmActivity, "AlarmActivity set Later Success")
                                    //更新資料儲存
                                    alarmClocks.alarmClockList.add(alarmClock)
                                    SharedService.updateAlarmClocks(this@AlarmActivity, alarmClocks, true)
                                }

                                override fun allFinished() {
                                    finish()
                                }
                            })
                        }
                    })

                } else {
                    //沒事
                    if (mSPDatas.getBoolean("PromptBall", true))
                        SharedService.showTextToast(this@AlarmActivity, "請拖動小球至上方區塊")
                }
            }

            override fun dragging(type: Int) {
                when (type) {
                    0 -> {
                        tv_five_minute.setBackgroundColor(ContextCompat.getColor(this@AlarmActivity, R.color.colorTransparentPaleBlue))
                        tv_ten_minute.setBackgroundColor(ContextCompat.getColor(this@AlarmActivity, R.color.colorTransparentPaleBlue))
                        tv_close.setBackgroundColor(ContextCompat.getColor(this@AlarmActivity, R.color.colorTransparentPaleBlue))
                    }
                    1 -> {
                        tv_five_minute.setBackgroundColor(ContextCompat.getColor(this@AlarmActivity, R.color.colorTransparentBlue))
                        tv_ten_minute.setBackgroundColor(ContextCompat.getColor(this@AlarmActivity, R.color.colorTransparentPaleBlue))
                        tv_close.setBackgroundColor(ContextCompat.getColor(this@AlarmActivity, R.color.colorTransparentPaleBlue))
                    }
                    2 -> {
                        tv_five_minute.setBackgroundColor(ContextCompat.getColor(this@AlarmActivity, R.color.colorTransparentPaleBlue))
                        tv_ten_minute.setBackgroundColor(ContextCompat.getColor(this@AlarmActivity, R.color.colorTransparentBlue))
                        tv_close.setBackgroundColor(ContextCompat.getColor(this@AlarmActivity, R.color.colorTransparentPaleBlue))
                    }
                    3 -> {
                        tv_five_minute.setBackgroundColor(ContextCompat.getColor(this@AlarmActivity, R.color.colorTransparentPaleBlue))
                        tv_ten_minute.setBackgroundColor(ContextCompat.getColor(this@AlarmActivity, R.color.colorTransparentPaleBlue))
                        tv_close.setBackgroundColor(ContextCompat.getColor(this@AlarmActivity, R.color.colorTransparentBlue))
                    }
                }
            }

        })
    }

    private fun startAlarmService() {
        val intent = Intent(this, AlarmService::class.java)
        intent.putExtra("TextsJsonStr", Gson().toJson(mRealTexts))
        startService(intent)
        bindService(intent, alarmServiceConnection, 0)
    }

    private val alarmServiceConnection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as AlarmService.LocalBinder
            mAlarmService = binder.service
            mAlarmService!!.setAlarmActivity(this@AlarmActivity)
            //內部會自動判斷是否在暫停狀態
//            mAlarmService!!.resumePlay()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mMemoryCaches?.evictAll()
        stopAlarmService()
    }

    private fun stopAlarmService() {
        mAlarmService?.setAlarmActivity(null)
        if (mAlarmService != null) {
            unbindService(alarmServiceConnection)
        }
        val intent = Intent(this, AlarmService::class.java)
        stopService(intent)
        mAlarmService = null
    }

    private inner class NewsAdapter : RecyclerView.Adapter<NewsAdapter.ViewHolder>() {
        val TYPE_FOOTER = 1
        val TYPE_NORMAL = 2
        private var mFooterView: View? = null

        override fun getItemViewType(position: Int): Int {
            return if (position == itemCount - 1) TYPE_FOOTER else TYPE_NORMAL
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val context = parent.context
            if (viewType == TYPE_FOOTER) {
                mFooterView = LayoutInflater.from(context).inflate(R.layout.footer, parent, false)
                return ViewHolder(mFooterView!!)
            }
            val view = LayoutInflater.from(context).inflate(R.layout.block_news, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            if (getItemViewType(position) == TYPE_FOOTER) {
                if (mRealTexts.textList.size == 0)
                    holder.tvFooter.text = "發生意外，無新聞資料!\n建議刪除此鬧鐘資料並重新設置"
                else
                    holder.tvFooter.text = "到底囉!"
                return
            }

            val text = mRealTexts.textList[position + mIgnoreCount]

            if (text.description == "weather") {
                holder.tvWeather.visibility = View.VISIBLE
                holder.tvTitle.visibility = View.GONE
                holder.tvDescription.visibility = View.GONE
                var weather = text.title
                weather = weather.replace("維", "為")
                if (weather.contains("品質"))
                    weather = weather.replace("。", "\n\n")
                holder.tvWeather.text = weather

                holder.llNews.setOnClickListener(null)
            } else {
                holder.tvWeather.visibility = View.GONE
                holder.tvTitle.visibility = View.VISIBLE
                holder.tvDescription.visibility = View.VISIBLE
                holder.tvTitle.text = text.title
                holder.tvDescription.text = text.description

                holder.llNews.setOnClickListener {
                    if (SharedService.checkNetWork(this@AlarmActivity, true)) {
                        val intent = Intent(this@AlarmActivity, WebViewActivity::class.java)
                        intent.putExtra("URL", text.url)
                        startActivity(intent)
                    }
                }
            }

            val previewImage = text.preview_image
            if (previewImage != "") {
                holder.ivNews.tag = previewImage
                showImage(holder.ivNews, previewImage, holder.pbNews)
            } else {
                holder.pbNews.visibility = View.GONE
                holder.ivNews.visibility = View.GONE
            }

        }

        override fun getItemCount(): Int {
            return mRealTexts.textList.size - mIgnoreCount + 1
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvWeather = itemView.tv_weather
            val tvTitle = itemView.tv_title
            val tvDescription = itemView.tv_description
            val ivNews = itemView.iv_news
            val pbNews = itemView.pb_news
            val llNews = itemView.ll_news
            val tvFooter = itemView.tv_footer
        }

    }

    private var mMemoryCaches: LruCache<String, Bitmap>? = null

    private fun initCache() {
        val maxMemory = Runtime.getRuntime().maxMemory() / 3
        mMemoryCaches = object : LruCache<String, Bitmap>(maxMemory.toInt()) {
            override fun sizeOf(key: String?, value: Bitmap?): Int {
                return value!!.byteCount
            }
        }
    }

    private fun getBitmapFromCache(imageName: String): Bitmap? {
        return mMemoryCaches?.get(imageName)
    }

    private fun addBitmapToCaches(imageName: String, bitmap: Bitmap) {
        if (getBitmapFromCache(imageName) == null) {
            mMemoryCaches?.put(imageName, bitmap)
        }
    }

    private val mProgressBarList = ArrayList<ProgressBar>()
    private val mImageViewList = ArrayList<ImageView>()
    private val loadingImgNameList = ArrayList<String>()
    private val mOkHttpClient = OkHttpClient()

    private fun showImage(imageView: ImageView, url: String, progressBar: ProgressBar) {
        val bitmap = getBitmapFromCache(url)
        if (bitmap == null) {
            loadImgByOkHttp(
                    imageView,
                    url,
                    progressBar)
        } else {
            progressBar.visibility = View.GONE
            imageView.visibility = View.VISIBLE
            imageView.setImageBitmap(bitmap)
        }
    }

    private fun loadImgByOkHttp(imageView: ImageView, url: String, progressBar: ProgressBar) {
        for (mImgView in mImageViewList) {
            if (mImgView == imageView)
                return
        }
        mImageViewList.add(imageView)
        mProgressBarList.add(progressBar)

        progressBar.visibility = View.VISIBLE
        imageView.visibility = View.GONE

        for (imgName in loadingImgNameList) {
            if (imgName == url)
                return
        }
        loadingImgNameList.add(url)

        val request = Request.Builder()
                .url(url)
                .build()

        mOkHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    loadingImgNameList.remove(url)
                    var i = 0
                    while (i < mImageViewList.size) {
                        if (mImageViewList[i].tag == url) {
                            mImageViewList[i].visibility = View.GONE
                            mProgressBarList[i].visibility = View.GONE
                            mImageViewList.removeAt(i)
                            mProgressBarList.removeAt(i)
                            i--
                        }
                        i++
                    }
                }
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val inputStream = response.body()!!.byteStream()
                val bitmap = BitmapFactory.decodeStream(inputStream)
                try {
                    runOnUiThread {
                        if (bitmap != null) {
                            addBitmapToCaches(url, bitmap)
                            loadingImgNameList.remove(url)
                            var i = 0
                            while (i < mImageViewList.size) {
                                if (mImageViewList[i].tag == url) {
                                    mProgressBarList[i].visibility = View.GONE
                                    mImageViewList[i].visibility = View.VISIBLE
                                    mImageViewList[i].setImageBitmap(bitmap)
                                    mImageViewList.removeAt(i)
                                    mProgressBarList.removeAt(i)
                                    i--
                                }
                                i++
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

            }
        })
    }

//    fun close(view: View) {
//        finish()
//    }

    override fun onBackPressed() {
        SharedService.showTextToast(this, "請滑動叉叉關閉")
    }
}
