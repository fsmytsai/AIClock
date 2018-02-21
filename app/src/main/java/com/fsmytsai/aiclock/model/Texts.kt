package com.fsmytsai.aiclock.model

/**
 * Created by user on 2018/2/17.
 */

data class Texts(
        var acId: Int = 0,
        var isOldData: Boolean = true,
        val textList: ArrayList<Text> = ArrayList()
)

data class Text(
        val text_id: Int,
        val speaker: String,
        val category: String,
        val title: String,
        val description: String,
        val part_count: Int,
        val created_at: String
)