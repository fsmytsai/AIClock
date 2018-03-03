package com.fsmytsai.aiclock.ui.fragment


import android.app.Activity.RESULT_OK
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.fsmytsai.aiclock.R
import com.fsmytsai.aiclock.model.AlarmClock
import com.fsmytsai.aiclock.model.AlarmClocks
import com.fsmytsai.aiclock.service.app.SharedService
import com.fsmytsai.aiclock.service.app.SpeechDownloader
import com.fsmytsai.aiclock.ui.activity.AddAlarmClockActivity
import com.fsmytsai.aiclock.ui.activity.DownloadSpeechActivity
import com.fsmytsai.aiclock.ui.activity.MainActivity
import com.google.gson.Gson
import kotlinx.android.synthetic.main.block_alarm_clock.view.*
import kotlinx.android.synthetic.main.footer.view.*
import kotlinx.android.synthetic.main.fragment_alarm_clock.view.*


class AlarmClockFragment : Fragment() {
    private lateinit var rvAlarmClock: RecyclerView
    private lateinit var mMainActivity: MainActivity
    private lateinit var mAlarmClocks: AlarmClocks
    private val ADD_ALARM_CLOCK = 10
    private val dayOfWeekArr = arrayOf("日", "一", "二", "三", "四", "五", "六")
    private var isAutoOn = false
    private var mNowPosition = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_alarm_clock, container, false)
        mMainActivity = activity as MainActivity
        mAlarmClocks = SharedService.getAlarmClocks(mMainActivity)
        initViews(view)
        return view
    }

    private fun initViews(view: View) {
        rvAlarmClock = view.rv_alarm_clock
        rvAlarmClock.layoutManager = LinearLayoutManager(mMainActivity, LinearLayoutManager.VERTICAL, false)
        rvAlarmClock.adapter = AlarmClockAdapter()
        view.iv_add_alarm_clock.setOnClickListener {
            val intent = Intent(mMainActivity, AddAlarmClockActivity::class.java)
            var biggestACId = 0
            for (alarmClock in mAlarmClocks.alarmClockList) {
                if (alarmClock.acId > biggestACId)
                    biggestACId = alarmClock.acId
            }
            intent.putExtra("acId", ++biggestACId)
            startActivityForResult(intent, ADD_ALARM_CLOCK)
        }
    }

    private inner class AlarmClockAdapter : RecyclerView.Adapter<AlarmClockAdapter.ViewHolder>() {

        val TYPE_FOOTER = 1  //说明是带有Footer的
        val TYPE_NORMAL = 2  //说明是不带有header和footer的
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
            val view = LayoutInflater.from(context).inflate(R.layout.block_alarm_clock, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            if (getItemViewType(position) == TYPE_FOOTER) {
                if (mAlarmClocks.alarmClockList.size == 0)
                    holder.tvFooter.text = "點右下角新增智能鬧鐘!"
                else
                    holder.tvFooter.text = "沒有更多鬧鐘囉!"
                return
            }

            val ac = mAlarmClocks.alarmClockList[position]
            holder.tvTime.text = "${String.format("%02d", ac.hour)}:${String.format("%02d", ac.minute)}"
            var repeat = ""
            for (i in 0..6) {
                if (ac.isRepeatArr[i])
                    repeat += dayOfWeekArr[i] + " "
            }
            holder.tvRepeat.text = repeat
            holder.tvRepeat.setTextColor(Color.BLUE)
            holder.sbSwitch.isChecked = ac.isOpen
            holder.sbSwitch.setOnCheckedChangeListener { view, isChecked ->
                //AddAlarmClockActivity 關閉前已更新資料
                if (isAutoOn) {
                    isAutoOn = false
                } else {
                    //更新開關資料
                    ac.isOpen = isChecked
                    SharedService.updateAlarmClocks(mMainActivity, mAlarmClocks)
                    //開啟就下載音檔並設置鬧鐘，關閉則取消鬧鐘及刪除 texts 資料
                    if (isChecked) {
                        mMainActivity.bindDownloadService(object : DownloadSpeechActivity.CanStartDownloadCallback {
                            override fun start() {
                                mMainActivity.startDownload(ac, object : SpeechDownloader.DownloadFinishListener {
                                    override fun cancel() {
                                        Handler().postDelayed({
                                            view.isChecked = false
                                        }, 1000)
                                    }

                                    override fun startSetData() {

                                    }

                                    override fun allFinished() {

                                    }
                                })
                            }
                        })

                    } else {
                        SharedService.cancelAlarm(mMainActivity, ac.acId)
                        SharedService.deleteOldTextsData(mMainActivity, ac.acId, null, false)
                    }
                }
            }

            holder.rlAlarmClockBlock.setOnClickListener {
                val intent = Intent(mMainActivity, AddAlarmClockActivity::class.java)
                intent.putExtra("AlarmClockJsonStr", Gson().toJson(ac))
                mNowPosition = position
                startActivityForResult(intent, ADD_ALARM_CLOCK)
            }

            holder.rlAlarmClockBlock.setOnLongClickListener {
                AlertDialog.Builder(mMainActivity)
                        .setTitle("刪除鬧鐘")
                        .setMessage("您確定要刪除 ${String.format("%02d", ac.hour)}:${String.format("%02d", ac.minute)} 的鬧鐘嗎?")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("刪除", { text, listener ->
                            mAlarmClocks.alarmClockList.removeAt(position)
                            rvAlarmClock.adapter.notifyItemRemoved(position)
                            rvAlarmClock.adapter.notifyItemRangeChanged(position, mAlarmClocks.alarmClockList.size - position)
                            SharedService.cancelAlarm(mMainActivity, ac.acId)
                            SharedService.deleteAlarmClock(mMainActivity, ac.acId)
                            SharedService.deleteOldTextsData(mMainActivity, ac.acId, null, false)
                        })
                        .show()

                true
            }
        }

        override fun getItemCount(): Int {
            return mAlarmClocks.alarmClockList.size + 1
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvTime = itemView.tv_time
            val tvRepeat = itemView.tv_repeat
            val sbSwitch = itemView.sb_switch
            val rlAlarmClockBlock = itemView.rl_alarm_clock_block
            val tvFooter = itemView.tv_footer
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ADD_ALARM_CLOCK && resultCode == RESULT_OK) {
            if (data!!.getBooleanExtra("IsDelete", false)) {
                mAlarmClocks.alarmClockList.removeAt(mNowPosition)
                rvAlarmClock.adapter.notifyItemRemoved(mNowPosition)
                rvAlarmClock.adapter.notifyItemRangeChanged(mNowPosition, mAlarmClocks.alarmClockList.size - mNowPosition)
                return
            }

            isAutoOn = data.getBooleanExtra("IsAutoOn", false)

            val alarmClock = Gson().fromJson(data.getStringExtra("AlarmClockJsonStr"), AlarmClock::class.java)

            val newPosition = data.getIntExtra("NewPosition", -1)

            //檢查有沒有更新位置
            if (data.getBooleanExtra("IsChangePosition", false)) {
                mAlarmClocks.alarmClockList.removeAt(mNowPosition)
                mAlarmClocks.alarmClockList.add(newPosition, alarmClock)
                rvAlarmClock.adapter.notifyItemMoved(mNowPosition, newPosition)
                if (newPosition < mNowPosition)
                    rvAlarmClock.adapter.notifyItemRangeChanged(newPosition, mAlarmClocks.alarmClockList.size - newPosition)
                else
                    rvAlarmClock.adapter.notifyItemRangeChanged(mNowPosition, mAlarmClocks.alarmClockList.size - mNowPosition)
                return
            }

            if (data.getBooleanExtra("IsNew", false)) {
                mAlarmClocks.alarmClockList.add(newPosition, alarmClock)
                rvAlarmClock.adapter.notifyItemInserted(newPosition)
                rvAlarmClock.adapter.notifyItemRangeChanged(newPosition, mAlarmClocks.alarmClockList.size - newPosition)
            } else {
                mAlarmClocks.alarmClockList[mNowPosition] = alarmClock
                rvAlarmClock.adapter.notifyDataSetChanged()
            }

            mNowPosition = -1
        }
    }

}
