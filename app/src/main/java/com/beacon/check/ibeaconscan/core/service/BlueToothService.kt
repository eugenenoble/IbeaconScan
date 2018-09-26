package com.beacon.check.ibeaconscan.core.service

import android.app.*
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.*
import android.support.v4.app.NotificationCompat
import android.util.Log
import com.beacon.check.ibeaconscan.R
import com.beacon.check.ibeaconscan.ui.MainActivity.Companion.startScan
import com.beacon.check.ibeaconscan.ui.MainActivity.Companion.updateDeviceList
import java.util.*


const val REQUEST_ENABLE_BT = "bt_ON"
const val REQUEST_DISABLE_BT = "bt_OF"


class BlueToothService : Service() {

    private val JAALEE_UUID_MAIN_SERVICE = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    private val JAALEE_UUID_PASSWORD_CHAR = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
    private val JAALEE_UUID_BUTTON_SERVICE = UUID.fromString("0000aa10-0000-1000-8000-00805f9b34fb")
    private val JAALEE_UUID_BUTTON_CHAR = UUID.fromString("0000aa16-0000-1000-8000-00805f9b34fb")

    private val notificationChannelId = "beacon channel"
    private val notificationId = 7331

    private val stopAction = "StopService"
    private var messenger: Messenger? = null
    private val SCAN_PERIOD: Long = 10000
    private var mGatt: BluetoothGatt? = null
    private var mDevice: BluetoothDevice? = null

    private val mHandler: Handler? = Handler()
    private var lManager: LocationManager? = null

    lateinit var mLeScanner: BluetoothLeScanner
    lateinit var bAdapter: BluetoothAdapter
    val devices: HashSet<BluetoothDevice> = HashSet()

    private val mReceiver: BroadcastReceiver by lazy {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action
                when (action) {
                    REQUEST_DISABLE_BT -> {
                        callBtIntent()
                    }
                    REQUEST_ENABLE_BT -> {
                        initLeScanner()
                    }
                }

            }
        }
    }


    override fun onBind(p0: Intent?): IBinder? {
        return BluetoothServiceBinder()
    }


    override fun onCreate() {
        super.onCreate()
        bAdapter = BluetoothAdapter.getDefaultAdapter()
        lManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(notificationChannelId, "beacon in action", NotificationManager.IMPORTANCE_LOW)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(notificationChannel)
        }
        registerReceiver(mReceiver, IntentFilter(REQUEST_ENABLE_BT))

        if (lManager != null && !lManager!!.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            callGpsIntent()
        }

        if (!bAdapter.isEnabled) {
            callBtIntent()
            val bTIntent = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            registerReceiver(BluetoothReceiver(), bTIntent)
        } else {
            initLeScanner()
        }


        startService(Intent(this, BlueToothService::class.java))
        startForeground(notificationId, getNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.action != null && intent.action == stopAction) {
            stopForeground(true)
            stopSelf()
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private val mLeScanCallback by lazy {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val btDevice = result.device

                if (btDevice.name != null) {
                    devices.add(btDevice)
                    if (btDevice.name == "jaalee")
                        connectToDevice(btDevice)
                    try {
                        messenger?.send(Message.obtain(null, updateDeviceList))
                    } catch (e: RemoteException) {
                        e.printStackTrace()
                    }
                    Log.d("DEVICENAME", btDevice?.name)
                }
            }

            override fun onBatchScanResults(results: List<ScanResult>) {
                super.onBatchScanResults(results)
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
            }
        }
    }

    fun callBtIntent() {
        val enableBTIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(enableBTIntent)
    }

    fun callGpsIntent() {
        val gpsOptionsIntent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(gpsOptionsIntent)
    }


    private fun initLeScanner() {
        mLeScanner = bAdapter.bluetoothLeScanner
        scanLeDevice(true)

    }

    private fun scanLeDevice(enable: Boolean) {

        if (enable) {
            mHandler?.postDelayed({
                mLeScanner.startScan(mLeScanCallback)
            }, SCAN_PERIOD)
            try {
                messenger?.send(Message.obtain(null, startScan))
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
        } else {
            mLeScanner.stopScan(mLeScanCallback)
        }
    }


    override fun onDestroy() {
        unregisterReceiver(mReceiver)
        messenger = null
        mGatt?.disconnect()
        mGatt?.close()

        scanLeDevice(false)

        super.onDestroy()
    }

    fun connectToDevice(device: BluetoothDevice) {
        if (mGatt == null) {
            mDevice = device
            mGatt = device.connectGatt(this, false, gattCallback).apply {
                requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER)
            }
            scanLeDevice(false)// will stop after first device detection
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.i("onConnectionStateChange", "Status: $status")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i("gattCallback", "STATE_CONNECTED")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.e("gattCallback", "STATE_DISCONNECTED")
                    if (mDevice != null) {
                        mDevice!!.connectGatt(this@BlueToothService, false, this).apply {
                            requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER)
                        }
                    }
                }
            }

        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val services = gatt.services
            Log.i("onServicesDiscovered", services.toString())
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    if (writePassword(gatt)) {
//                        if (gatt.device.createBond()) {
//                            Log.i(TAG, "PAIR_OK")
//                        } else Log.i(TAG, "PAIR_FAIL")
                        //Log.i(TAG, "password write success")
                        //            ShowStringMessage("password write success");
                    } else {
                        Log.i(TAG, "password write fail")
                        //            ShowStringMessage("password write fail");

                    }
                }
            }
            //gatt.readCharacteristic(services[1].characteristics[0])
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic?, status: Int) {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    enableButtonNotification(gatt)
                }
                BluetoothGatt.GATT_FAILURE -> {

                }

            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            Log.i(TAG, "BUTTON CLICK MATHERFUCKER")
        }

    }

    private fun writePassword(gatt: BluetoothGatt): Boolean {
        val Service = gatt.getService(JAALEE_UUID_MAIN_SERVICE)
        for (i in 0 until Service.characteristics.size) {
            val Char = Service.characteristics[i]
            Log.i(TAG, "password" + Char.uuid.toString())
        }
        val passwordChar = Service.getCharacteristic(JAALEE_UUID_PASSWORD_CHAR)

        passwordChar.value = hexString2Bytes("666666")
        return gatt.writeCharacteristic(passwordChar)
    }

    private fun enableButtonNotification(gatt: BluetoothGatt) {
        val bluetoothGattService = gatt.getService(JAALEE_UUID_BUTTON_SERVICE)
        val buttonChar = bluetoothGattService.getCharacteristic(JAALEE_UUID_BUTTON_CHAR)
        gatt.setCharacteristicNotification(buttonChar, true)
        val buttonDescriptor = buttonChar.descriptors[0]
        buttonDescriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        if (gatt.writeDescriptor(buttonDescriptor)) {
            Log.i(TAG, "enable button notification success")
            //            ShowStringMessage("enable button notification success");
        } else {
            Log.i(TAG, "enable button notification fail")
            //            ShowStringMessage("enable button notification fail");
        }
    }

    private fun hexString2Bytes(hexstr: String): ByteArray {
        val b = ByteArray(hexstr.length / 2)
        var j = 0
        for (i in b.indices) {
            val c0 = hexstr[j++]
            val c1 = hexstr[j++]
            b[i] = (parse(c0) shl 4 or parse(c1)).toByte()
        }
        return b
    }

    private fun parse(c: Char): Int {
        if (c >= 'a')
            return c - 'a' + 10 and 0x0f
        return if (c >= 'A') c - 'A' + 10 and 0x0f else c - '0' and 0x0f
    }

    private fun getNotification(): Notification {
        val intent = Intent(this, BlueToothService::class.java)
        intent.action = stopAction
        val stopIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        val action = NotificationCompat.Action.Builder(R.mipmap.ic_launcher, "STOP", stopIntent).build()

        val builder = NotificationCompat.Builder(this, notificationChannelId).apply {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setSmallIcon(R.mipmap.ic_launcher_round)
            } else {
                setSmallIcon(R.mipmap.ic_launcher_round)
            }
            addAction(action)
            priority = NotificationCompat.PRIORITY_DEFAULT
        }

        return builder.build()
    }

    inner class BluetoothServiceBinder : Binder() {
        fun getBtAdapter(): BluetoothAdapter = bAdapter
        fun scanDevices() {
            scanLeDevice(true)
        }

        fun setMessenger(messenger: Messenger) {
            this@BlueToothService.messenger = messenger
        }

        fun getDeviceList(): HashSet<String> {
            val list = hashSetOf<String>()
            list.addAll(devices.map { it.name })
            return list
        }
    }


}

class BluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val state: Int
        when (action) {
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                when (state) {
                    BluetoothAdapter.STATE_ON -> {
                        context.sendBroadcast(Intent(REQUEST_ENABLE_BT))
                    }
                    BluetoothAdapter.STATE_OFF -> {
                        context.sendBroadcast(Intent(REQUEST_DISABLE_BT))
                    }
                }
            }
        }
    }
}