package com.fsmytsai.aiclock.service.app

import android.content.Context
import java.io.File

/**
 * Created by user on 2018/2/17.
 */
class SharedService {
    companion object {

        fun checkfileExist(context: Context, fileName: String): Boolean {
            val file = File("${context.filesDir.absolutePath}/$fileName")
            return file.exists()
        }
    }
}