package com.fsmytsai.aiclock.ui.activity

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
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
import com.fsmytsai.aiclock.model.Texts
import com.fsmytsai.aiclock.service.app.SharedService
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_alarm.*
import kotlinx.android.synthetic.main.block_news.view.*
import kotlinx.android.synthetic.main.footer.view.*
import okhttp3.*
import java.io.File
import java.io.IOException

class AlarmActivity : AppCompatActivity() {
    private var mAlarmService: AlarmService? = null
    private var mRealTexts: Texts = Texts()
    private var mIgnoreCount = 0

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
        setContentView(R.layout.activity_alarm)

        setRealTexts(intent)
        initViews()
        startAlarmService()
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
        if (intent != null)
            reSetAll(intent)
    }

    private fun reSetAll(intent: Intent) {
        setRealTexts(intent)
        rv_news.adapter.notifyDataSetChanged()
        stopAlarmService()
        startAlarmService()
    }

    private fun setRealTexts(intent: Intent){
        val acId = intent.getIntExtra("ACId", 0)
        SharedService.writeDebugLog("AlarmActivity ACId = $acId")

        //初始化圖片快取
        initCache()
        val alarmClock = SharedService.getAlarmClock(this, acId)
        val texts = SharedService.getTexts(this, acId)
        if (alarmClock != null && texts != null) {
            //初始化 mRealTexts
            mRealTexts = Texts()
            mRealTexts.acId = texts.acId
            mRealTexts.isOldData = texts.isOldData

            //過濾掉缺少音檔的 text
            for (text in texts.textList) {
                val addToRealTextsList = (0 until text.part_count)
                        .map { File("$filesDir/sounds/${text.text_id}-$it-${alarmClock.speaker}.wav") }
                        .all { it.exists() }
                if (addToRealTextsList) mRealTexts.textList.add(text)
            }
            mIgnoreCount = mRealTexts.textList.filter { it.description == "time" || it.description == "weather" }.size
        }
    }

    private fun initViews() {
        rv_news.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        rv_news.adapter = NewsAdapter()
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
            mAlarmService!!.resumePlay()
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

        override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
            val context = parent?.context
            if (viewType == TYPE_FOOTER) {
                mFooterView = LayoutInflater.from(context).inflate(R.layout.footer, parent, false)
                return ViewHolder(mFooterView!!)
            }
            val view = LayoutInflater.from(context).inflate(R.layout.block_news, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
            if (getItemViewType(position) == TYPE_FOOTER) {
                if (mRealTexts.textList.size == 0)
                    holder!!.tvFooter.text = "發生意外，無新聞資料!\n建議刪除此鬧鐘資料並重新設置"
                else
                    holder!!.tvFooter.text = "到底囉!"
                return
            }

            val text = mRealTexts.textList[position + mIgnoreCount]

            holder!!.tvTitle.text = text.title
            holder.tvDescription.text = text.description
            val previewImage = text.preview_image
            if (previewImage != "") {
                holder.ivNews.tag = previewImage
                showImage(holder.ivNews, previewImage, holder.pbNews)
            } else {
                holder.pbNews.visibility = View.GONE
                holder.ivNews.visibility = View.GONE
            }

            holder.llNews.setOnClickListener {
                if (SharedService.checkNetWork(this@AlarmActivity)) {
                    val intent = Intent(this@AlarmActivity, WebViewActivity::class.java)
                    intent.putExtra("URL", text.url)
                    startActivity(intent)
                }
            }
        }

        override fun getItemCount(): Int {
            return mRealTexts.textList.size - mIgnoreCount + 1
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvTitle = itemView.tv_title
            val tvDescription = itemView.tv_description
            val ivNews = itemView.iv_news
            val pbNews = itemView.pb_news
            val llNews = itemView.ll_news
            val tvFooter = itemView.tv_footer
        }

    }

    private var mMemoryCaches: LruCache<String, Bitmap>? = null

    protected fun initCache() {
        val maxMemory = Runtime.getRuntime().maxMemory() / 3
        mMemoryCaches = object : LruCache<String, Bitmap>(maxMemory.toInt()) {
            override fun sizeOf(key: String?, value: Bitmap?): Int {
                return value!!.byteCount
            }
        }
    }

    fun getBitmapFromLrucache(imageName: String): Bitmap? {
        return mMemoryCaches?.get(imageName)
    }

    fun addBitmapToLrucaches(imageName: String, bitmap: Bitmap) {
        if (getBitmapFromLrucache(imageName) == null) {
            mMemoryCaches?.put(imageName, bitmap)
        }
    }

    fun removeBitmapFromLrucaches(imageName: String) {
        if (getBitmapFromLrucache(imageName) == null) {
            mMemoryCaches?.remove(imageName)
        }
    }

    private val wImageViewList = ArrayList<ImageView>()
    private val loadingImgNameList = ArrayList<String>()
    private val mOkHttpClient = OkHttpClient()

    private fun showImage(imageView: ImageView, url: String, progressBar: ProgressBar) {
        val bitmap = getBitmapFromLrucache(url)
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
        for (mImgView in wImageViewList) {
            if (mImgView == imageView)
                return
        }
        wImageViewList.add(imageView)

        for (imgName in loadingImgNameList) {
            if (imgName == url)
                return
        }
        loadingImgNameList.add(url)

        progressBar.visibility = View.VISIBLE
        imageView.visibility = View.GONE

        val request = Request.Builder()
                .url(url)
                .build()

        mOkHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                loadingImgNameList.remove(url)
                var i = 0
                while (i < wImageViewList.size) {
                    if (wImageViewList[i].tag == url) {
                        wImageViewList.removeAt(i)
                        i--
                    }
                    i++
                }
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val inputStream = response.body()!!.byteStream()
                val bitmap = BitmapFactory.decodeStream(inputStream)
                try {
                    runOnUiThread {
                        if (bitmap != null) {
                            addBitmapToLrucaches(url, bitmap)
                            loadingImgNameList.remove(url)
                            var i = 0
                            while (i < wImageViewList.size) {
                                if (wImageViewList[i].tag == url) {
                                    progressBar.visibility = View.GONE
                                    imageView.visibility = View.VISIBLE
                                    wImageViewList[i].setImageBitmap(bitmap)
                                    wImageViewList.removeAt(i)
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

    fun close(view: View) {
        finish()
    }

    override fun onBackPressed() {
        SharedService.showTextToast(this, "請點叉叉關閉")
    }
}
