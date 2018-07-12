package com.fsmytsai.aiclock.model

data class Texts(
        var acId: Int = 0,
        var isOldData: Boolean = true,
        var time: String? = "",
        val textList: ArrayList<Text> = ArrayList()
) {
    data class Text(
            val text_id: Int = 0,
            val category: String = "",
            val title: String = "",
            val description: String = "",
            val part_count: Int = 0,
            val url: String = "",
            val preview_image: String = "",
            val created_at: String = ""
    )
}

