package com.fsmytsai.aiclock.service.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.support.v4.app.NotificationCompat
import android.view.View
import android.widget.RemoteViews
import com.fsmytsai.aiclock.CloseReceiver
import com.fsmytsai.aiclock.R

class FixedNotificationManagement {
    companion object {
        private fun show(context: Context) {
            val remoteViews = RemoteViews(context.packageName, R.layout.block_alarm_notification)

            remoteViews.setTextViewText(R.id.tv_content, "已開啟鬧鐘")

            val closeIntent = Intent(context, CloseReceiver::class.java)
            closeIntent.putExtra("ACId", 999)
            val closePendingIntent = PendingIntent.getBroadcast(context, 999, closeIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            remoteViews.setOnClickPendingIntent(R.id.tv_close, closePendingIntent)

            remoteViews.setViewVisibility(R.id.tv_cancel, View.GONE)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val builder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = NotificationChannel("AlarmNotification",
                        "AI Clock", NotificationManager.IMPORTANCE_DEFAULT)
                channel.enableLights(true)
                channel.lightColor = Color.BLUE
                channel.enableVibration(true)
                channel.vibrationPattern = longArrayOf(0)
                channel.importance = NotificationManager.IMPORTANCE_LOW

                notificationManager.createNotificationChannel(channel)
                NotificationCompat.Builder(context, "AlarmNotification")
            } else {
                NotificationCompat.Builder(context)
            }

            builder.setContent(remoteViews)
            builder.setOngoing(true)
            builder.setSmallIcon(R.drawable.icon)
            builder.setVibrate(longArrayOf(0))
            builder.setDefaults(0)
            builder.setSound(null)
            val notification = builder.build()
            notificationManager.notify(999, notification)
        }

        private fun hide(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(999)
        }

        fun check(context: Context) {
            val spDatas = context.getSharedPreferences("Datas", Context.MODE_PRIVATE)
            val isFixed = spDatas.getBoolean("IsFixed", false)
            if (isFixed) {
                var alarmClocks = SharedService.getAlarmClocks(context, false)
                var isShow = false
                if (!alarmClocks.alarmClockList.none { it.isOpen })
                    isShow = true
                if (!isShow) {
                    alarmClocks = SharedService.getAlarmClocks(context, true)
                    if (!alarmClocks.alarmClockList.none { it.isOpen })
                        isShow = true
                }
                if (isShow) {
                    SharedService.writeDebugLog(context, "FixedNotificationManagement check showFixedNotification")
                    show(context)
                } else {
                    SharedService.writeDebugLog(context, "FixedNotificationManagement check hideFixedNotification")
                    hide(context)
                }
            } else
                hide(context)
        }
    }
}