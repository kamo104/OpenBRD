package com.openbrd.android

import android.content.SharedPreferences
import android.location.Location
import org.osmdroid.util.GeoPoint

fun GeoPoint(location:Location) = GeoPoint(location.latitude,location.longitude)
fun GeoPoint(snapshot:LocationSnapshot) = GeoPoint(snapshot.latitude,snapshot.longitude)


fun SharedPreferences.Editor.putDouble(key: String, double: Double): SharedPreferences.Editor =
    putLong(key, java.lang.Double.doubleToRawLongBits(double))

fun SharedPreferences.getDouble(key: String, default: Double) =
    java.lang.Double.longBitsToDouble(getLong(key, java.lang.Double.doubleToRawLongBits(default)))


fun constructPaths(snapshots:List<LocationSnapshot>, events:List<Event>): List<List<GeoPoint>>{
    val paths = mutableListOf<List<GeoPoint>>()

    var currentStart = events.first()
    for(event in events){
        when (event.type){
            EventTypes.START -> currentStart = event
            EventTypes.PAUSE, EventTypes.STOP -> {
                val currentPoints = snapshots.filter{ it.date.after(currentStart.date) && it.date.before(event.date)}.map { GeoPoint(it) }
                if(currentPoints.isNotEmpty())paths.add(currentPoints)
            }
        }
    }
    return paths
}