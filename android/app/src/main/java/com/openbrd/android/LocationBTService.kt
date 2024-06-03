package com.openbrd.android

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.IMyLocationConsumer
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider
import java.time.Instant
import java.util.UUID


class LocationBTService : Service() {
    private lateinit var gpsHelper:GpsHelper
    private lateinit var btHelper:BtHelper
    private lateinit var dbHandle:AppDatabase

    private lateinit var startPendingIntent: PendingIntent
    private lateinit var pausePendingIntent: PendingIntent
    private lateinit var stopPendingIntent: PendingIntent
    private lateinit var exitPendingIntent: PendingIntent

    private enum class GpsState{
        RUNNING,
        PAUSED,
        STOPPED
    }

    private inner class GpsHelper : IMyLocationConsumer {
        var snapshots = mutableListOf<LocationSnapshot>()

        var events = mutableListOf<Event>()
        private var locationProvider:GpsMyLocationProvider = GpsMyLocationProvider(applicationContext)

        private var looper: Looper? = null
        private var handler: Handler? = null

        var state = GpsState.STOPPED

        override fun onLocationChanged(location: Location?, source: IMyLocationProvider?) {
            if(location!=null){
                snapshots.add(LocationSnapshot(Instant.now(), location))


                Log.d("LOCATION_LATITUDE",location.latitude.toString())
                Log.d("LOCATION_LONGITUDE",location.longitude.toString())
            }
        }

        init {
            locationProvider.addLocationSource(LocationManager.NETWORK_PROVIDER)
            locationProvider.addLocationSource(LocationManager.GPS_PROVIDER)
        }
        // starts the gps logging along with it's own thread
        fun start(){
            if(gpsHelper.state == GpsState.RUNNING){
                return
            }

            Log.d("GPS_START", snapshots.size.toString())
            Thread {
                Looper.prepare()
                looper = Looper.myLooper()
                handler = Handler(looper!!)
                locationProvider.startLocationProvider(this)
                Looper.loop()
            }.start()
            events.add(Event(Instant.now(),EventTypes.START))
            state = GpsState.RUNNING
        }

        private fun stopProvider(){
            looper?.let {
                handler?.post {
                    locationProvider.stopLocationProvider()
                    it.quitSafely()
                }
            }
        }

        fun pause(){
            if(gpsHelper.state == GpsState.STOPPED || gpsHelper.state == GpsState.PAUSED){
                return
            }
            stopProvider()
            Log.d("GPS_PAUSE", snapshots.size.toString())
            events.add(Event(Instant.now(),EventTypes.PAUSE))
            state = GpsState.PAUSED
        }
        fun stop(){
            if(gpsHelper.state == GpsState.STOPPED){
                return
            }

            stopProvider()

            events.add(Event(Instant.now(),EventTypes.STOP))

            Log.d("GPS_STOP", snapshots.size.toString())

            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch coroutine@{
                if(snapshots.size==0 || events.size<2){
                    events.clear()
                    return@coroutine
                }
                dbHandle.pathDao().insertAll(Path(path = LocationSnapshot.saveLocations(snapshots), events = Event.saveEvents(events)))
                snapshots.clear()
                events.clear()
            }

            state = GpsState.STOPPED
        }

    }
    private inner class BtHelper {
        private var bluetoothAdapter: BluetoothAdapter? = null
        private var bluetoothManager:BluetoothManager? = null
        private var btGatt: BluetoothGatt? = null


        private val OPENBRD_SERVICE_UUID = UUID.fromString("f49a9476-8be3-4a35-9f61-a3edc7b4872d")
        private val OPENBRD_CHARACTERISTIC_UUID = UUID.fromString("52141f9e-7dd5-45de-989f-f9d0836f365c")

        init {
            this.init()
        }
        @SuppressLint("MissingPermission")
        fun init(){
            bluetoothManager = applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
            if (bluetoothManager == null){
                Log.e("BLUETOOTH", "Unable to obtain a bluetoothManager.")
                return
            }
            bluetoothAdapter =  bluetoothManager!!.adapter
            if (bluetoothAdapter == null) {
                Log.e("BLUETOOTH", "Unable to obtain a BluetoothAdapter.")
                return
            }
            // TODO: maybe check for bluetoothAdapter. mManagerCallback onBluetoothServiceDown and onBluetoothServiceUp
            // adapt the code to connectGatt again on these events

            for (device in bluetoothAdapter!!.bondedDevices){
                if (device.name == "openBrd"){
                    btGatt = device.connectGatt(applicationContext,true,BtCallback())
                }
            }
        }
        @SuppressLint("MissingPermission")
        fun destroy(){
            btGatt?.close()
        }

        private inner class BtCallback : BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED){
                    Log.d("BLUETOOTH", "CONNECTED")
                } else{
                    Log.d("BLUETOOTH", "DISCONNECTED")
                    return
                }

                if(status == BluetoothGatt.GATT_SUCCESS && gatt != null){
                    gatt.discoverServices()
                }
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if(status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("BLUETOOTH", "discovered services")
                } else {
                    Log.d("BLUETOOTH", "failed to discover services")
                    return
                }
                val service = gatt?.getService(OPENBRD_SERVICE_UUID)?: return

                val characteristic = service.getCharacteristic(OPENBRD_CHARACTERISTIC_UUID)?: return

                // reading from the characteristic to get the peripheral state
                val res = gatt.readCharacteristic(characteristic)
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                // in our case write will always fail because the peripheral goes to sleep
                if(status == BluetoothGatt.GATT_SUCCESS){
                    Log.d("BLUETOOTH", "characteristic written: ${characteristic?.uuid}")
                } else {
                    Log.d("BLUETOOTH", "characteristic written: false")
                }

            }

            @SuppressLint("MissingPermission")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                if(status == BluetoothGatt.GATT_SUCCESS){
                    Log.d("BLUETOOTH", "characteristic read from: ${characteristic.uuid}")
                } else{
                    Log.d("BLUETOOTH", "characteristic read from: false")
                    return
                }
                Log.d("BLUETOOTH", "characteristic value: ${value[0]}")
                if(value[0].toInt() == 1){
                    startPendingIntent.send()
                } else{
                    stopPendingIntent.send()
                }
                // writing back puts the device to sleep
                val bytes = ByteArray(1)
                val res = gatt.writeCharacteristic(characteristic,bytes,WRITE_TYPE_DEFAULT)
            }
        }
    }

    private fun handleStartAction() {
        Log.d("SERVICE_STATE","START")
        gpsHelper.start()
    }
    private fun handlePauseAction() {
        Log.d("SERVICE_STATE","PAUSE")
        gpsHelper.pause()
    }
    private fun handleStopAction() {
        Log.d("SERVICE_STATE","STOP")
        gpsHelper.stop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let {
            when (it) {
                ACTION_START -> handleStartAction()
                ACTION_PAUSE -> handlePauseAction()
                ACTION_STOP -> handleStopAction()
                ACTION_EXIT -> {
                    btHelper.destroy()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                else -> {}
            }
        }
        if(!isRunning){
            isRunning = true
            btHelper = BtHelper()
            gpsHelper = GpsHelper()
            dbHandle = AppDatabase.getDatabase(applicationContext)
        }



        val playIntent = Intent(this, LocationBTService::class.java).apply {
            action = ACTION_START
        }
        startPendingIntent = PendingIntent.getService(
            this,
            0,
            playIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pauseIntent = Intent(this, LocationBTService::class.java).apply {
            action = ACTION_PAUSE
        }
        pausePendingIntent = PendingIntent.getService(
            this,
            0,
            pauseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, LocationBTService::class.java).apply {
            action = ACTION_STOP
        }
        stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )


        val openIntent = Intent(this, MainActivity::class.java)
        openIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val openPendingIntent: PendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val exitIntent = Intent(this, LocationBTService::class.java).apply {
            action = ACTION_EXIT
        }
        exitPendingIntent = PendingIntent.getService(
            this,
            0,
            exitIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        createNotificationChannel()

        // Create custom notification layout
        val customView = RemoteViews(packageName, R.layout.notification_layout)

        // Update title
        customView.setTextViewText(R.id.notification_title, "OpenBRD")

        customView.setOnClickPendingIntent(R.id.action_button_icon_exit, exitPendingIntent)

        val setActionButton = fun(customView: RemoteViews, buttonId: Int, iconId: Int, textId: Int, actionText: String, iconResId: Int, pendingIntent: PendingIntent) {
            customView.setImageViewResource(iconId, iconResId)
            customView.setTextViewText(textId, actionText)
            customView.setOnClickPendingIntent(buttonId, pendingIntent)
        }
        // Set action buttons based on state
        when (gpsHelper.state) {
            GpsState.RUNNING -> {
                setActionButton(customView, R.id.action_button_1, R.id.action_button_icon_1, R.id.action_button_text_1, "Pause", R.drawable.baseline_pause_circle_24, pausePendingIntent)
                setActionButton(customView, R.id.action_button_2, R.id.action_button_icon_2, R.id.action_button_text_2, "Stop", R.drawable.baseline_stop_circle_24, stopPendingIntent)
                customView.setViewVisibility(R.id.action_button_1, View.VISIBLE)
                customView.setViewVisibility(R.id.action_button_2, View.VISIBLE)
            }
            GpsState.PAUSED -> {
                setActionButton(customView, R.id.action_button_1, R.id.action_button_icon_1, R.id.action_button_text_1, "Resume", R.drawable.baseline_play_circle_24, startPendingIntent)
                setActionButton(customView, R.id.action_button_2, R.id.action_button_icon_2, R.id.action_button_text_2, "Stop", R.drawable.baseline_stop_circle_24, stopPendingIntent)
                customView.setViewVisibility(R.id.action_button_1, View.VISIBLE)
                customView.setViewVisibility(R.id.action_button_2, View.VISIBLE)
            }
            GpsState.STOPPED -> {
                setActionButton(customView, R.id.action_button_1, R.id.action_button_icon_1, R.id.action_button_text_1, "Start", R.drawable.baseline_play_circle_24, startPendingIntent)
                customView.setViewVisibility(R.id.action_button_1, View.VISIBLE)
                customView.setViewVisibility(R.id.action_button_2, View.GONE)
            }
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_skateboarding_24)
            .setCustomContentView(customView)
            .setContentIntent(openPendingIntent)
            .build()

        startForeground(FOREGROUND_SERVICE_ID, notification)


        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Foreground Service Channel",
            NotificationManager.IMPORTANCE_HIGH
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        gpsHelper.stop()
        isRunning = false
    }

    companion object {
        private const val CHANNEL_ID = "ForegroundServiceChannel"
        private const val FOREGROUND_SERVICE_ID = 101

        private const val ACTION_START = "com.openbrd.android.LocationBTService.start"
        private const val ACTION_PAUSE = "com.openbrd.android.LocationBTService.pause"
        private const val ACTION_STOP = "com.openbrd.android.LocationBTService.stop"
        private const val ACTION_EXIT = "com.openbrd.android.LocationBTService.exit"

        var isRunning = false

    }
}
