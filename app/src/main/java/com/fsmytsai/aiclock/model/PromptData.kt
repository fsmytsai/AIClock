package com.fsmytsai.aiclock.model

/**
 * Created by tsaiminyuan on 2018/2/18.
 */

data class PromptData(
        val is_success: Boolean,
        val data: Data
) {
    data class Data(
            val part_count: Int,
            val text_id: Int
    )
}

