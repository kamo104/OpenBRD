package com.openbrd.android

import android.location.Location
import org.osmdroid.util.GeoPoint
import java.time.Instant
import java.util.Date



data class LocationSnapshot(val date: Date, val location: GeoPoint){
    constructor(date:Date, location:Location) : this(date, GeoPoint(location))
    constructor(date:Long, location:GeoPoint) : this(Date.from(Instant.ofEpochSecond(date)), location)
    constructor(date:Long, location:Location) : this(date, GeoPoint(location))
    constructor(date:Instant, location:Location) : this(Date.from(date), GeoPoint(location))
    constructor(date:Instant, location:GeoPoint) : this(Date.from(date), location)

    override fun toString(): String {
        return "${date.toInstant().epochSecond}$TYPE_SEPARATOR${location.latitude}$TYPE_SEPARATOR${location.longitude}"
    }
    var latitude
        get() = this.location.latitude
        set(lat){this.location.latitude = lat}
    var longitude
        get() = this.location.longitude
        set(long){this.location.longitude = long}

    companion object{
        private const val TYPE_SEPARATOR = ','
        private const val ITEMS_SEPARATOR = ';'.toString()
        fun fromString(string: String): LocationSnapshot {
            val sepIndex = string.indexOf(TYPE_SEPARATOR)

            val epochSecond = string.substring(0, sepIndex).toLong()
            val date = Date.from(Instant.ofEpochSecond(epochSecond))

            val tmp  = string.substring(sepIndex+1)
            val sep2Index = tmp.indexOf(TYPE_SEPARATOR)
            val location = GeoPoint(tmp.substring(0,sep2Index).toDouble(),tmp.substring(sep2Index+1).toDouble())
            return LocationSnapshot(date,location)
        }
        fun saveLocations(snapshots: Collection<LocationSnapshot>): String {
            return snapshots.joinToString(ITEMS_SEPARATOR) { it.toString() }
        }
        fun toGeoPoints(snapshots: Collection<LocationSnapshot>): List<GeoPoint> {
            return snapshots.map { GeoPoint(it) }
        }

        fun loadLocations(string: String): List<LocationSnapshot> {
            return string.split(ITEMS_SEPARATOR).map { fromString(it) }
        }
    }
}