package com.fsmytsai.aiclock.ui.fragment


import android.annotation.TargetApi
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.util.LruCache
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ProgressBar
import com.fsmytsai.aiclock.R
import com.fsmytsai.aiclock.model.AlarmClocks
import com.fsmytsai.aiclock.model.Texts
import com.fsmytsai.aiclock.service.app.LocationService
import com.fsmytsai.aiclock.service.app.SharedService
import com.fsmytsai.aiclock.service.app.SpeechDownloader
import com.fsmytsai.aiclock.ui.activity.AddAlarmClockActivity
import com.fsmytsai.aiclock.ui.activity.DownloadSpeechActivity
import com.fsmytsai.aiclock.ui.activity.MainActivity
import com.fsmytsai.aiclock.ui.activity.WebViewActivity
import com.google.gson.Gson
import kotlinx.android.synthetic.main.block_news.view.*
import kotlinx.android.synthetic.main.footer.view.*
import kotlinx.android.synthetic.main.fragment_home.view.*
import okhttp3.*
import java.io.File
import java.io.IOException
import java.util.ArrayList


class HomeFragment : Fragment() {
    private lateinit var mMainActivity: MainActivity
    private lateinit var mRootView: View
    private var mMPBGM = MediaPlayer()
    private var mMPNews = MediaPlayer()

    private var mTexts = Texts()
    private val mAlarmClock = AlarmClocks.AlarmClock(
            999,
            0,
            0,
            0,
            1000.0,
            0.0,
            0,
            6,
            booleanArrayOf(false, false, false, false, false, false, false),
            false)

    private var mFooterText = "取得位置中..."
    private var mIsDownloading = false
    private var mIsPlaying = false
    private var mIsDownloadComplete = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        mMainActivity = activity as MainActivity
        mRootView = inflater.inflate(R.layout.fragment_home, container, false)
        initViews()
        setAlarmData()
        return mRootView
    }

    private fun initViews() {
        mRootView.srl_home.setOnRefreshListener(SwipeRefreshLayout.OnRefreshListener {
            //檢查網路連線
            if (!SharedService.checkNetWork(mMainActivity, true)) {
                mRootView.srl_home.isRefreshing = false
                return@OnRefreshListener
            }

            refresh()
        })
        mRootView.srl_home.setColorSchemeResources(android.R.color.holo_blue_bright, android.R.color.holo_green_light,
                android.R.color.holo_orange_light, android.R.color.holo_red_light)
        mRootView.srl_home.setDistanceToTriggerSync(400)
        mRootView.srl_home.setSize(SwipeRefreshLayout.DEFAULT)

        mRootView.rv_home.layoutManager = LinearLayoutManager(mMainActivity, LinearLayoutManager.VERTICAL, false)
        mRootView.rv_home.adapter = NewsAdapter()

        mRootView.iv_control.setOnClickListener {
            if(mTexts.textList.size == 0){
                SharedService.showTextToast(mMainActivity, "無資料可播放")
                return@setOnClickListener
            }

            if (mIsDownloading || mRootView.srl_home.isRefreshing) {
                SharedService.showTextToast(mMainActivity, "載入資料中，請稍後再試...")
                return@setOnClickListener
            }

            if (mIsDownloadComplete) {
                if (mIsPlaying) {
                    pausePlay()
                } else {
                    mIsPlaying = true
                    mMPBGM.start()
                    mMPNews.start()
                    mRootView.iv_control.setImageResource(R.drawable.pause)
                    mMainActivity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }

            } else {
                mIsDownloading = true
                mMPBGM = MediaPlayer()
                mMPNews = MediaPlayer()
                if (mAlarmClock.backgroundMusic == null)
                    mMPBGM.setDataSource(mMainActivity, Uri.parse("android.resource://${mMainActivity.packageName}/raw/bgm"))
                else {
                    val uri = Uri.parse("${mMainActivity.filesDir}/bgmSounds/${mAlarmClock.backgroundMusic}")
                    try {
                        mMPBGM.setDataSource(mMainActivity, uri)
                    } catch (e: Exception) {
                        SharedService.writeDebugLog(mMainActivity, "HomeFragment startBGM setDataSource failed uri = $uri")
                        mMPBGM.setDataSource(mMainActivity, Uri.parse("android.resource://${mMainActivity.packageName}/raw/bgm"))
                    }
                }
                mMPBGM.setOnCompletionListener {
                    mMPBGM.start()
                }
                mMPBGM.prepare()
                mMPBGM.start()

                mMainActivity.downloadTitle = "取得資料中..."
                mMainActivity.bindDownloadService(object : DownloadSpeechActivity.CanStartDownloadCallback {
                    override fun start() {
                        mMainActivity.startDownload(mAlarmClock, object : SpeechDownloader.DownloadFinishListener {
                            override fun cancel() {
                                mIsDownloading = false
                                mMPBGM.pause()
                                mMPBGM.release()
                                mMPNews.release()
                            }

                            override fun startSetData() {

                            }

                            override fun allFinished() {
                                mIsDownloading = false
                                mIsPlaying = true
                                mIsDownloadComplete = true
                                mRootView.iv_control.setImageResource(R.drawable.pause)
                                setData()
                            }
                        })
                    }
                })
            }
        }
    }

    private fun setAlarmData() {
        val speaker = mMainActivity.spDatas.getInt("HomeSpeaker", 0)
        val category = mMainActivity.spDatas.getInt("HomeCategory", 0)
        val newsCount = mMainActivity.spDatas.getInt("HomeNewsCount", 6)
        val backgroundMusic = mMainActivity.spDatas.getString("HomeBackgroundMusic", null)

        mAlarmClock.speaker = speaker
        mAlarmClock.category = category
        mAlarmClock.newsCount = newsCount
        mAlarmClock.backgroundMusic = backgroundMusic

        val hasPermission = ActivityCompat.checkSelfPermission(mMainActivity, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(mMainActivity, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        val weather = mMainActivity.spDatas.getBoolean("HomeWeather", hasPermission)
        if (weather) {
            mRootView.srl_home.isRefreshing = true
            LocationService.getLocation(mMainActivity, object : LocationService.GetLocationListener {
                override fun success(latitude: Double, longitude: Double) {
                    mAlarmClock.latitude = latitude
                    mAlarmClock.longitude = longitude
                    SharedService.writeDebugLog(mMainActivity, "HomeFragment setLocation success lat = ${mAlarmClock.latitude} lon = ${mAlarmClock.longitude}")
                    refresh()
                }

                override fun failed() {
                    SharedService.writeDebugLog(mMainActivity, "HomeFragment setLocation failed")
                    refresh()
                }
            })
        } else {
            mAlarmClock.latitude = 1000.0
            mAlarmClock.longitude = 0.0
            refresh()
        }
    }

    private fun refresh() {
        mFooterText = "取得資料中..."
        mRootView.rv_home.adapter.notifyDataSetChanged()
        getOnlyTextData()
    }

    private fun getOnlyTextData() {
        mRootView.srl_home.isRefreshing = true
        val request = Request.Builder()
                .url("${SharedService.getLatestUrl(mMainActivity)}api/getOnlyTextData?version_code=${SharedService.getVersionCode(mMainActivity)}&" +
                        "latitude=${mAlarmClock.latitude}&longitude=${mAlarmClock.longitude}&" +
                        "category=${mAlarmClock.category}&news_count=${mAlarmClock.newsCount}")
                .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call?, e: IOException?) {
                mMainActivity.runOnUiThread {
                    mRootView.srl_home.isRefreshing = false
                    mFooterText = "請檢查網路連線"
                    mRootView.rv_home.adapter.notifyDataSetChanged()
                }
            }

            override fun onResponse(call: Call?, response: Response?) {
                val statusCode = response?.code()
                val resMessage = response?.body()?.string()

                mMainActivity.runOnUiThread {
                    mRootView.srl_home.isRefreshing = false
                    if (statusCode == 200) {
                        mTexts = Gson().fromJson(resMessage!!, Texts::class.java)
                        var textIdListStr = ""
                        for (text in mTexts.textList) {
                            textIdListStr += "${text.text_id},"
                        }
                        mMainActivity.spDatas.edit().putString("TextIdListStr", textIdListStr.dropLast(1)).apply()
                        initCache()
                        mFooterText = "祝您有美好的一天！"
                        mRootView.rv_home.adapter.notifyDataSetChanged()

                        if (mIsDownloadComplete) {
                            pausePlay()
                            releasePlay()
                        }

                    } else {
                        SharedService.handleError(mMainActivity, statusCode!!, resMessage!!)
                        mFooterText = "取得資料失敗"
                        mRootView.rv_home.adapter.notifyDataSetChanged()
                    }
                }

            }
        })
    }

    private inner class NewsAdapter : RecyclerView.Adapter<NewsAdapter.ViewHolder>() {
        val TYPE_FOOTER = 1
        val TYPE_NORMAL = 2

        override fun getItemViewType(position: Int): Int {
            return if (position == itemCount - 1) TYPE_FOOTER else TYPE_NORMAL
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = when (viewType) {
                TYPE_FOOTER -> LayoutInflater.from(parent.context).inflate(R.layout.footer, parent, false)
                else -> LayoutInflater.from(parent.context).inflate(R.layout.block_news, parent, false)
            }
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            if (getItemViewType(position) == TYPE_FOOTER) {
                holder.tvFooter.text = mFooterText
                return
            }

            val text = mTexts.textList[position]

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
                    if (SharedService.checkNetWork(mMainActivity, true)) {
                        val intent = Intent(mMainActivity, WebViewActivity::class.java)
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
            return mTexts.textList.size + 1
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

    override fun onStop() {
        SharedService.writeDebugLog(mMainActivity, "HomeFragment onStop")
        if (mIsPlaying)
            pausePlay()
        super.onStop()
    }

    override fun onDestroy() {
        SharedService.writeDebugLog(mMainActivity, "HomeFragment onDestroy")
        releasePlay()
        super.onDestroy()
    }

    private fun pausePlay() {
        mIsPlaying = false
        mMPBGM.pause()
        mMPNews.pause()
        mRootView.iv_control.setImageResource(R.drawable.play)
        mMainActivity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun releasePlay() {
        mIsDownloadComplete = false
        mMPNews.release()
        mMPBGM.release()
    }

    private val mSoundList = ArrayList<String>()
    private var mNewsCount = 0
    private fun setData() {
        mSoundList.clear()
        mNewsCount = 0
        mMainActivity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        //排列音檔播放順序
        for (text in mTexts.textList) {
            if (text.description != "weather") {
                mNewsCount++
                mSoundList.add("news$mNewsCount")
            }

            (0 until text.part_count).mapTo(mSoundList) { "${text.text_id}-$it-${mAlarmClock.speaker}" }
        }
        //最後加上 bye
        mSoundList.add("bye")

        mMPBGM.setVolume(0.1f, 0.1f)
        if (mSoundList[0].startsWith("news")) {
            playNews(Uri.parse("android.resource://${mMainActivity.packageName}/raw/${SharedService.speakerArr[mAlarmClock.speaker]}_${mSoundList[0]}"))
        } else
            playNews(Uri.fromFile(File("${mMainActivity.filesDir}/sounds/${mSoundList[0]}.wav")))
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun playNews(uri: Uri) {
        //雖然開始播放前已全部檢查過，但保險起見也加上 try catch
        try {
            mMPNews.setDataSource(mMainActivity, uri)
        } catch (e: Exception) {
            SharedService.writeDebugLog(mMainActivity, "HomeFragment playNews setDataSource failed uri = $uri")
            mMPNews.setDataSource(mMainActivity, Uri.parse("android.resource://${mMainActivity.packageName}/raw/${SharedService.speakerArr[mAlarmClock!!.speaker]}_lost"))
        }

        mMPNews.setOnCompletionListener {
            mMPNews.release()

            //如果還沒播放 bye 則一直刪，由於是播放完才刪除，所以不可放進下面的 if 中
            if (mSoundList.size > 0)
                mSoundList.removeAt(0)

            if (mSoundList.size > 0) {
                mMPNews = MediaPlayer()
                if (mSoundList[0].startsWith("news") || mSoundList[0] == "bye")
                    playNews(Uri.parse("android.resource://${mMainActivity.packageName}/raw/${SharedService.speakerArr[mAlarmClock.speaker]}_${mSoundList[0]}"))
                else
                    playNews(Uri.fromFile(File("${mMainActivity.filesDir}/sounds/${mSoundList[0]}.wav")))
            } else {
                mIsPlaying = false
                mIsDownloadComplete = false
                mRootView.iv_control.setImageResource(R.drawable.play)
                mMainActivity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                mMPBGM.pause()
                mMPBGM.release()
            }
        }

        mMPNews.prepare()
        mMPNews.start()
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
                mMainActivity.runOnUiThread {
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
                    mMainActivity.runOnUiThread {
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

    private val SETTING = 65

    fun setting() {
        val settingIntent = Intent(mMainActivity, AddAlarmClockActivity::class.java)
        settingIntent.putExtra("IsHome", true)
        settingIntent.putExtra("AlarmClock", Gson().toJson(mAlarmClock))
        startActivityForResult(settingIntent, SETTING)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == SETTING) {
            mMainActivity.spDatas.edit().putInt("HomeSpeaker", data!!.getIntExtra("Speaker", 0))
                    .putBoolean("HomeWeather", data.getBooleanExtra("Weather", false))
                    .putInt("HomeCategory", data.getIntExtra("Category", 0))
                    .putInt("HomeNewsCount", data.getIntExtra("NewsCount", 0))
                    .putString("HomeBackgroundMusic", data.getStringExtra("BackgroundMusic")).apply()
            setAlarmData()
        }
    }
}
