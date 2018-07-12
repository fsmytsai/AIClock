package com.fsmytsai.aiclock.model

data class PromptData(
        val is_success: Boolean,
        val data: Data
) {
    data class Data(
            val part_count: Int,
            val text_id: Int
    )
}

