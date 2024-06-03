package com.openbrd.android

import java.time.Instant
import java.util.Date


enum class EventTypes{
    START,
    PAUSE,
    STOP,
}
data class Event(val date: Date, val type:EventTypes){
    constructor(date:Long, type:EventTypes) : this(Date.from(Instant.ofEpochSecond(date)), type)
    constructor(date:Instant, type:EventTypes) : this(Date.from(date), type)

    override fun toString(): String {
        return "${date.toInstant().epochSecond}$TYPE_SEPARATOR$type"
    }

    companion object{
        private const val TYPE_SEPARATOR = ','
        private const val ITEMS_SEPARATOR = ';'.toString()
        fun fromString(string: String): Event {
            val sepIndex = string.indexOf(TYPE_SEPARATOR)
            val epochSecond = string.substring(0, sepIndex).toLong()
            val type = EventTypes.valueOf(string.substring(sepIndex + 1))
            val date = Date.from(Instant.ofEpochSecond(epochSecond))
            return Event(date, type)
        }
        fun saveEvents(events: List<Event>): String {
            return events.joinToString(ITEMS_SEPARATOR) { it.toString() }
        }

        fun loadEvents(string: String): List<Event> {
            return string.split(ITEMS_SEPARATOR).map { fromString(it) }
        }
    }
}