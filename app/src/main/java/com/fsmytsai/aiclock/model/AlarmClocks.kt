package com.fsmytsai.aiclock.model

data class AlarmClocks(
        val alarmClockList: ArrayList<AlarmClock> = ArrayList()
) {
    data class AlarmClock(
            var acId: Int,
            var hour: Int,
            var minute: Int,
            var speaker: Int,
            var latitude: Double,
            var longitude: Double,
            var category: String,
            var newsCount: Int,
            var isRepeatArr: BooleanArray,
            var isOpen: Boolean,
            var backgroundMusic: String? = null
    )
}

