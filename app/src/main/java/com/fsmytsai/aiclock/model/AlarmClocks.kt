package com.fsmytsai.aiclock.model

/**
 * Created by user on 2018/2/16.
 */
data class AlarmClocks(
        val alarmClockList: ArrayList<AlarmClock> = ArrayList()
)

data class AlarmClock(
        var acId: Int,
        var hour: Int,
        var minute: Int,
        var speaker: Int,
        var latitude: Double,
        var longitude: Double,
        var category: Int,
        var newsCount: Int,
        var isRepeatArr: BooleanArray,
        var isOpen: Boolean
)