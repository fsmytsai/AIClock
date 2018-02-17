package com.fsmytsai.aiclock.ui.fragment


import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.fsmytsai.aiclock.R
import com.fsmytsai.aiclock.model.TextsList
import com.fsmytsai.aiclock.service.app.SharedService
import com.fsmytsai.aiclock.ui.activity.MainActivity
import com.google.gson.Gson
import kotlinx.android.synthetic.main.fragment_news.view.*


/**
 * A simple [Fragment] subclass.
 */
class NewsFragment : Fragment() {
    private lateinit var mMainActivity: MainActivity

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_news, container, false)
        mMainActivity = activity as MainActivity
        initViews(view)
        if(SharedService.isNewsPlaying || SharedService.reRunRunnable)
            mMainActivity.bindAlarmService(null)
        else
            getTexts()
        return view
    }


    private fun initViews(view: View) {
        view.tv_test.text = "sdfjdsiofosd"
    }

    private fun getTexts() {
        val spDatas = mMainActivity.getSharedPreferences("Datas", Context.MODE_PRIVATE)
        val textsList: TextsList
        val textsListJsonStr = spDatas.getString("TextsListJsonStr", "")
        textsList = Gson().fromJson(textsListJsonStr, TextsList::class.java)
        for (texts in textsList.textsList) {
            if (texts.acId == mMainActivity.acId) {
                mMainActivity.bindAlarmService(texts)
                break
            }
        }

    }

}
