package com.fsmytsai.aiclock.ui.fragment


import android.os.Build
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.fsmytsai.aiclock.R
import com.fsmytsai.aiclock.service.app.FixedNotificationManagement
import com.fsmytsai.aiclock.service.app.SharedService
import com.fsmytsai.aiclock.ui.activity.MainActivity
import kotlinx.android.synthetic.main.dialog_wish.view.*
import kotlinx.android.synthetic.main.fragment_setting.view.*
import okhttp3.*
import java.io.File
import java.io.IOException
import java.util.*


class SettingFragment : Fragment() {
    private lateinit var mMainActivity: MainActivity
    private lateinit var mRootView: View

    private var mErrorReportDialog: AlertDialog? = null
    private var mWishDialog: AlertDialog? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        mMainActivity = activity as MainActivity
        mRootView = inflater.inflate(R.layout.fragment_setting, container, false)
        initViews()
        return mRootView
    }

    private fun initViews() {
        mRootView.ll_mute.setOnClickListener {
            val isMute = mMainActivity.spDatas.getBoolean("IsMute", false)
            mRootView.sc_mute.isChecked = !isMute
            mMainActivity.spDatas.edit().putBoolean("IsMute", !isMute).apply()
        }
        mRootView.iv_mute_info.setOnClickListener {
            AlertDialog.Builder(mMainActivity)
                    .setCancelable(false)
                    .setTitle("設置鬧鐘時靜音")
                    .setMessage("在新增、編輯鬧鐘時，靜音播報者試聽、背景音樂試聽、設置完成提示音。")
                    .setPositiveButton("知道了", null)
                    .show()
        }
        val isMute = mMainActivity.spDatas.getBoolean("IsMute", false)
        mRootView.sc_mute.isChecked = isMute

        mRootView.ll_advance.setOnClickListener {
            val isAdvance = mMainActivity.spDatas.getBoolean("IsAdvance", false)
            mRootView.sc_advance.isChecked = !isAdvance
            mMainActivity.spDatas.edit().putBoolean("IsAdvance", !isAdvance).apply()
        }
        mRootView.iv_advance_info.setOnClickListener {
            AlertDialog.Builder(mMainActivity)
                    .setCancelable(false)
                    .setTitle("預先取得資料及音檔")
                    .setMessage("在設置鬧鐘時，不管距離響鈴多久，都預先取得天氣、空氣品質、最新頭條新聞及音檔。\n\n專門用於響鈴前無法正常取得最新資料時顯示，適合睡覺習慣關閉網路的人使用。")
                    .setPositiveButton("知道了", null)
                    .show()
        }
        val isAdvance = mMainActivity.spDatas.getBoolean("IsAdvance", false)
        mRootView.sc_advance.isChecked = isAdvance

        mRootView.ll_fixed.setOnClickListener {
            val isFixed = mMainActivity.spDatas.getBoolean("IsFixed", false)
            mRootView.sc_fixed.isChecked = !isFixed
            mMainActivity.spDatas.edit().putBoolean("IsFixed", !isFixed).apply()
            FixedNotificationManagement.check(mMainActivity)
        }
        mRootView.iv_fixed_info.setOnClickListener {
            AlertDialog.Builder(mMainActivity)
                    .setCancelable(false)
                    .setTitle("固定鬧鐘圖示")
                    .setMessage("當有設置鬧鐘時，永久固定一個通知並顯示鬧鐘圖示。")
                    .setPositiveButton("知道了", null)
                    .show()
        }
        val isFixed = mMainActivity.spDatas.getBoolean("IsFixed", false)
        mRootView.sc_fixed.isChecked = isFixed

        mRootView.ll_error_report.setOnClickListener {
            if (SharedService.checkNetWork(mMainActivity, true)) {
                val dialogView = layoutInflater.inflate(R.layout.dialog_error_report, null)
                mErrorReportDialog = AlertDialog.Builder(mMainActivity)
                        .setView(dialogView)
                        .setCancelable(false)
                        .setNegativeButton("取消", null)
                        .setPositiveButton("送出", null)
                        .show()

                mErrorReportDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val content = dialogView.et_content.text.toString()
                    val email = dialogView.et_email.text.toString()
                    report(content, 0, email)
                }
            }
        }

        mRootView.ll_wish.setOnClickListener {
            if (SharedService.checkNetWork(mMainActivity, true)) {
                val dialogView = layoutInflater.inflate(R.layout.dialog_wish, null)
                mWishDialog = AlertDialog.Builder(mMainActivity)
                        .setView(dialogView)
                        .setCancelable(false)
                        .setNegativeButton("取消", null)
                        .setPositiveButton("送出", null)
                        .show()

                mWishDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val content = dialogView.et_content.text.toString()
                    val email = dialogView.et_email.text.toString()
                    report(content, 1, email)
                }
            }
        }

        mRootView.ll_about.setOnClickListener {
            val pInfo = mMainActivity.packageManager.getPackageInfo(mMainActivity.packageName, 0)
            AlertDialog.Builder(mMainActivity)
                    .setTitle("關於")
                    .setMessage("當前版本：${pInfo.versionName}\n\n徵懂 UI、UX 設計的合作夥伴\n\n本程式所有新聞來源皆為newsapi.org\n\n背景音樂來自 youtube 的創作者工具箱")
                    .setPositiveButton("知道了", null)
                    .show()
        }
    }

    private fun report(content: String, type: Int, email: String) {
        SharedService.showLoadingDialog(mMainActivity)
        val builder = MultipartBody.Builder()
        builder.setType(MultipartBody.FORM)
        if (type == 0) {
            val nowCalendar = Calendar.getInstance()
            for (i in 0..2) {
                nowCalendar.add(Calendar.DATE, -i)
                val logFileName = "${nowCalendar.get(Calendar.YEAR)}-${nowCalendar.get(Calendar.MONTH) + 1}-${nowCalendar.get(Calendar.DAY_OF_MONTH)}.txt"
                val file = File("${mMainActivity.filesDir}/logs/$logFileName")
                if (!file.exists())
                    continue
                builder.addFormDataPart("log_files[]", logFileName, RequestBody.create(MediaType.parse("text/plain"), file))
            }
        }

        builder.addFormDataPart("content", content)
                .addFormDataPart("type", "$type")
                .addFormDataPart("version_code", "${SharedService.getVersionCode(mMainActivity)}")
                .addFormDataPart("brand_model", "${Build.BRAND}-${Build.MODEL}")
                .addFormDataPart("sdk_int", "${Build.VERSION.SDK_INT}")

        if (email != "")
            builder.addFormDataPart("email", "$email")

        val body = builder.build()

        val request = Request.Builder()
                .url("${SharedService.getLatestUrl(mMainActivity)}api/createReport")
                .post(body)
                .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call?, e: IOException?) {
                mMainActivity.runOnUiThread {
                    SharedService.hideLoadingDialog()
                    SharedService.showTextToast(mMainActivity, "請檢查網路連線")
                }
            }

            override fun onResponse(call: Call?, response: Response?) {
                val statusCode = response?.code()
                val resMessage = response?.body()?.string()

                mMainActivity.runOnUiThread {
                    SharedService.hideLoadingDialog()
                    if (statusCode == 200) {
                        if (type == 0) {
                            SharedService.showTextToast(mMainActivity, "成功送出問題回報")
                            mErrorReportDialog?.dismiss()
                        } else {
                            SharedService.showTextToast(mMainActivity, "成功送出願望")
                            mWishDialog?.dismiss()
                        }
                    } else {
                        SharedService.handleError(mMainActivity, statusCode!!, resMessage!!)
                    }
                }

            }
        })
    }
}
