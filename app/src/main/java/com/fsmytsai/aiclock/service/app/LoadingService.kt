package com.fsmytsai.aiclock.service.app

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.support.v7.app.AppCompatActivity
import android.view.WindowManager
import com.fsmytsai.aiclock.R

class LoadingService{
    companion object {
        private var mLoadingDialog: Dialog? = null

        fun showLoadingDialog(activity: AppCompatActivity) {
            val dialogView = activity.layoutInflater.inflate(R.layout.dialog_loading, null)
            mLoadingDialog = AlertDialog.Builder(activity)
                    .setView(dialogView)
                    .setCancelable(false)
                    .create()
            mLoadingDialog!!.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            mLoadingDialog!!.window.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            mLoadingDialog!!.show()
            mLoadingDialog!!.window.decorView.systemUiVisibility = activity.window.decorView.systemUiVisibility
            mLoadingDialog!!.window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }

        fun hideLoadingDialog() {
            mLoadingDialog?.dismiss()
            mLoadingDialog = null
        }
    }
}