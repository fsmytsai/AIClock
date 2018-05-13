package com.fsmytsai.aiclock.service.app

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import java.util.*

class LocationService {
    companion object {

        @SuppressLint("MissingPermission")
        fun getLocation(context: Context, getLocationListener: GetLocationListener) {

            val nowCalendar = Calendar.getInstance()
            val spDatas = context.getSharedPreferences("Datas", Context.MODE_PRIVATE)
            val lastGetLocationTime = spDatas.getLong("LastGetLocationTime", 0)
            //小於 5 分鐘則直接使用舊資料
            if (nowCalendar.timeInMillis - lastGetLocationTime < 5 * 60 * 1000) {
                SharedService.writeDebugLog(context, "LocationService getLocation use last location")
                val lastLatitude = spDatas.getFloat("LastLatitude", 1000f)
                val lastLongitude = spDatas.getFloat("LastLongitude", 0f)
                getLocationListener.success(lastLatitude.toDouble(), lastLongitude.toDouble())
                return
            }

            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = locationManager.getProviders(true)

            providers.remove("passive")

            if (providers.size > 1)
                providers.remove("gps")

            SharedService.writeDebugLog(context, "LocationService getLocation providers size = ${providers.size}")

            if (providers.size == 0)
                getLocationListener.failed()

            var callbackCount = 0
            var bestAccuracy = 0f
            var bestLatitude = 0.0
            var bestLongitude = 0.0

            for (provider in providers) {
                SharedService.writeDebugLog(context, "LocationService provider = $provider requestSingleUpdate")
                locationManager.requestSingleUpdate(provider, object : LocationListener {
                    override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {
                        SharedService.writeDebugLog(context, "LocationService onStatusChanged")
                    }

                    override fun onProviderEnabled(p0: String?) {
                        callbackCount++
                        SharedService.writeDebugLog(context, "LocationService onProviderEnabled provider = $provider callbackCount = $callbackCount")
                        if (callbackCount == providers.size) {
                            if (bestAccuracy != 0f)
                                getLocationListener.success(bestLatitude, bestLongitude)
                            else
                                getLocationListener.failed()
                        }
                    }

                    override fun onProviderDisabled(p0: String?) {
                        callbackCount++
                        SharedService.writeDebugLog(context, "LocationService onProviderDisabled provider = $provider callbackCount = $callbackCount")
                        if (callbackCount == providers.size) {
                            if (bestAccuracy != 0f)
                                getLocationListener.success(bestLatitude, bestLongitude)
                            else
                                getLocationListener.failed()
                        }
                    }

                    override fun onLocationChanged(location: Location?) {
                        callbackCount++
                        if (location == null) {
                            SharedService.writeDebugLog(context, "LocationService onLocationChanged provider = $provider callbackCount = $callbackCount location is null")
                            return
                        }
                        if (location.accuracy > bestAccuracy) {
                            SharedService.writeDebugLog(context, "LocationService onLocationChanged provider = $provider callbackCount = $callbackCount ac = ${location.accuracy} lat = ${location.latitude} lon = ${location.longitude}")
                            bestAccuracy = location.accuracy
                            bestLatitude = location.latitude
                            bestLongitude = location.longitude
                        } else
                            SharedService.writeDebugLog(context, "LocationService onLocationChanged provider = $provider callbackCount = $callbackCount ac = ${location.accuracy}")

                        if (callbackCount == providers.size) {
                            if (bestAccuracy != 0f) {
                                spDatas.edit().putLong("LastGetLocationTime", nowCalendar.timeInMillis)
                                        .putFloat("LastLatitude", bestLatitude.toFloat())
                                        .putFloat("LastLongitude", bestLongitude.toFloat())
                                        .apply()

                                getLocationListener.success(bestLatitude, bestLongitude)
                            } else
                                getLocationListener.failed()
                        }
                    }

                }, context.mainLooper)
            }


        }
    }

    interface GetLocationListener {
        fun success(latitude: Double, longitude: Double)
        fun failed()
    }
}