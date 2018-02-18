package com.fsmytsai.aiclock.ui.fragment


import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.fsmytsai.aiclock.R
import com.fsmytsai.aiclock.model.AlarmClock
import com.fsmytsai.aiclock.model.AlarmClocks
import com.fsmytsai.aiclock.service.app.SharedService
import com.fsmytsai.aiclock.service.app.SpeechDownloader
import com.fsmytsai.aiclock.ui.activity.AddAlarmClockActivity
import com.fsmytsai.aiclock.ui.activity.MainActivity
import com.google.gson.Gson
import kotlinx.android.synthetic.main.block_alarm_clock.view.*
import kotlinx.android.synthetic.main.footer.view.*
import kotlinx.android.synthetic.main.fragment_alarm_clock.view.*


class AlarmClockFragment : Fragment() {
    private lateinit var rvAlarmClock: RecyclerView
    private lateinit var mMainActivity: MainActivity
    private lateinit var spDatas: SharedPreferences
    private var alarmClocks = AlarmClocks(ArrayList())
    private val ADD_ALARM_CLOCK = 10
    private val dayOfWeekArr = arrayOf("日", "一", "二", "三", "四", "五", "六")
    private var isAutoOn = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_alarm_clock, container, false)
        mMainActivity = activity as MainActivity
        initViews(view)
        return view
    }

    private fun initViews(view: View) {
        rvAlarmClock = view.rv_alarm_clock
        getAlarmClockData()
        view.iv_add_alarm_clock.setOnClickListener {
            val intent = Intent(mMainActivity, AddAlarmClockActivity::class.java)
            var biggestACId = 0
            for (alarmClock in alarmClocks.alarmClockList) {
                if (alarmClock.acId > biggestACId)
                    biggestACId = alarmClock.acId
            }
            intent.putExtra("acId", ++biggestACId)
            startActivityForResult(intent, ADD_ALARM_CLOCK)
        }
    }

    private fun getAlarmClockData() {
        spDatas = mMainActivity.getSharedPreferences("Datas", Context.MODE_PRIVATE)
        val alarmClocksJsonStr = spDatas.getString("AlarmClocksJsonStr", "")
        if (alarmClocksJsonStr != "")
            alarmClocks = Gson().fromJson(alarmClocksJsonStr, AlarmClocks::class.java)
        rvAlarmClock.layoutManager = LinearLayoutManager(mMainActivity, LinearLayoutManager.VERTICAL, false)
        val alarmClockAdapter = AlarmClockAdapter()
        rvAlarmClock.adapter = alarmClockAdapter
    }

    private inner class AlarmClockAdapter : RecyclerView.Adapter<AlarmClockAdapter.ViewHolder>() {
        val TYPE_FOOTER = 1  //说明是带有Footer的
        val TYPE_NORMAL = 2  //说明是不带有header和footer的
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
            val view = LayoutInflater.from(context).inflate(R.layout.block_alarm_clock, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
            if (getItemViewType(position) == TYPE_FOOTER) {
                if (alarmClocks.alarmClockList.size == 0)
                    holder!!.tvFooter.text = "快點新增智能鬧鐘吧!"
                else
                    holder!!.tvFooter.text = "沒有更多鬧鐘囉!"
                return
            }

            val ac = alarmClocks.alarmClockList[position]
            holder!!.tvTime.text = "${String.format("%02d", ac.hour)}:${String.format("%02d", ac.minute)}"
            var repeat = ""
            for (i in 0..6) {
                if (ac.isRepeatArr[i])
                    repeat += dayOfWeekArr[i] + " "
            }
            holder.tvRepeat.text = repeat
            holder.tvRepeat.setTextColor(Color.BLUE)
            holder.sbSwitch.isChecked = ac.isOpen
            holder.sbSwitch.setOnCheckedChangeListener { view, isChecked ->
                alarmClocks.alarmClockList[position].isOpen = isChecked
                spDatas.edit().putString("AlarmClocksJsonStr", Gson().toJson(alarmClocks)).apply()

                if (isChecked && !isAutoOn) {
                    val speechDownloader = SpeechDownloader(mMainActivity, true)
                    val isSuccess = speechDownloader.setAlarmClock(alarmClocks.alarmClockList[position])
                    if(!isSuccess)
                        view.isChecked = false
                } else
                    SharedService.cancelAlarm(mMainActivity, alarmClocks.alarmClockList[position].acId)

                if (isAutoOn)
                    isAutoOn = false
            }

            holder.rlAlarmClockBlock.setOnClickListener {
                val intent = Intent(mMainActivity, AddAlarmClockActivity::class.java)
                intent.putExtra("AlarmClockJsonStr", Gson().toJson(alarmClocks.alarmClockList[position]))
                startActivityForResult(intent, ADD_ALARM_CLOCK)
            }
        }

        override fun getItemCount(): Int {
            return alarmClocks.alarmClockList.size + 1
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
            val alarmClock = Gson().fromJson(data!!.getStringExtra("AlarmClockJsonStr"), AlarmClock::class.java)

            if (data.getBooleanExtra("IsDelete", false)) {
                for (i in 0 until alarmClocks.alarmClockList.size)
                    if (alarmClocks.alarmClockList[i].acId == alarmClock.acId) {
                        alarmClocks.alarmClockList.removeAt(i)
                        rvAlarmClock.adapter.notifyItemRemoved(i)
                        rvAlarmClock.adapter.notifyItemRangeChanged(i, alarmClocks.alarmClockList.size - i)
                        spDatas.edit().putString("AlarmClocksJsonStr", Gson().toJson(alarmClocks)).apply()
                        return
                    }
            }

//            //檢查時間是否重複
//            for (i in 0 until alarmClocks.alarmClockList.size)
//                if (alarmClock.hour == alarmClocks.alarmClockList[i].hour &&
//                        alarmClock.minute == alarmClocks.alarmClockList[i].minute &&
//                        alarmClock.acId != alarmClocks.alarmClockList[i].acId) {
//                    Toast.makeText(mMainActivity, "錯誤，已有相同時間。", Toast.LENGTH_SHORT).show()
//                    return
//                }

            //檢查修改後順序有沒有改變，有改變則刪掉舊資料
            var isChangePosition = false
            if (!data.getBooleanExtra("IsNew", false)) {
                for (i in 0 until alarmClocks.alarmClockList.size)
                    if (alarmClocks.alarmClockList[i].acId == alarmClock.acId) {
                        //避免自動開啟新增頁面
                        if (alarmClock.isOpen != alarmClocks.alarmClockList[i].isOpen)
                            isAutoOn = true

                        //非第一個alarmClock，新小時小於上一個alarmClock小時 或 新小時等於上一個alarmClock小時且新分鐘小於上一個alarmClock分鐘
                        if (i > 0 && (alarmClock.hour < alarmClocks.alarmClockList[i - 1].hour ||
                                        (alarmClock.hour == alarmClocks.alarmClockList[i - 1].hour &&
                                                alarmClock.minute < alarmClocks.alarmClockList[i - 1].minute)))
                            isChangePosition = true

                        //非最後一個alarmClock，新小時大於下一個alarmClock小時 或 新小時等於下一個alarmClock小時且新分鐘大於下一個alarmClock分鐘
                        if (i < alarmClocks.alarmClockList.size - 1 && (alarmClock.hour > alarmClocks.alarmClockList[i + 1].hour ||
                                        (alarmClock.hour == alarmClocks.alarmClockList[i + 1].hour &&
                                                alarmClock.minute > alarmClocks.alarmClockList[i + 1].minute)))
                            isChangePosition = true

                        if (isChangePosition) {
                            alarmClocks.alarmClockList.removeAt(i)
                            rvAlarmClock.adapter.notifyItemRemoved(i)
                            rvAlarmClock.adapter.notifyItemRangeChanged(i, alarmClocks.alarmClockList.size - i)
                        } else {
                            //沒換位置則直接更新資料
                            alarmClocks.alarmClockList.set(i, alarmClock)
                            rvAlarmClock.adapter.notifyDataSetChanged()
                        }
                        break
                    }
            }

            //新資料或有更新位置則插入資料
            if (data.getBooleanExtra("IsNew", false) || isChangePosition) {
                //取得應該插入的位置
                var index = 0
                for (i in 0 until alarmClocks.alarmClockList.size)
                //新小時大於當前alarmClock小時 或 (新小時等於當前alarmClock小時 且 新分鐘大於當前alarmClock分鐘則繼續找)
                    if (alarmClock.hour > alarmClocks.alarmClockList[i].hour ||
                            (alarmClock.hour == alarmClocks.alarmClockList[i].hour &&
                                    alarmClock.minute > alarmClocks.alarmClockList[i].minute))
                        index = i + 1
                    //否則直接結束
                    else
                        break

                //插入此次資料
                alarmClocks.alarmClockList.add(index, alarmClock)
                rvAlarmClock.adapter.notifyItemInserted(index)
                rvAlarmClock.adapter.notifyItemRangeChanged(index, alarmClocks.alarmClockList.size - index)
            }

            //更新資料儲存
            spDatas.edit().putString("AlarmClocksJsonStr", Gson().toJson(alarmClocks)).apply()
        }
    }

}
