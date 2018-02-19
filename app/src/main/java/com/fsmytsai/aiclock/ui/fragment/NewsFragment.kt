package com.fsmytsai.aiclock.ui.fragment


import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.fsmytsai.aiclock.R
import com.fsmytsai.aiclock.service.app.SharedService
import com.fsmytsai.aiclock.ui.activity.MainActivity
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
        if (SharedService.isNewsPlaying || SharedService.reRunRunnable)
            mMainActivity.startAlarmService(null)
        else
            getTexts()
        return view
    }


    private fun initViews(view: View) {
        view.bt_stop_news.setOnClickListener {
            mMainActivity.clearFlags()
            mMainActivity.stopAlarmService()
            mMainActivity.finish()
        }
    }

    private fun getTexts() {
        val texts = SharedService.getTexts(mMainActivity, mMainActivity.acId)
        //綁定鬧鐘服務，開始播放
        mMainActivity.startAlarmService(texts)
    }

}
