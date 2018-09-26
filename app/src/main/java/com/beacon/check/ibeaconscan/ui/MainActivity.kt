package com.beacon.check.ibeaconscan.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.*
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.widget.Toast
import com.beacon.check.ibeaconscan.R
import com.beacon.check.ibeaconscan.core.service.BlueToothService
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    companion object {
        val updateDeviceList = 123332
        val startScan = 332
    }

    val PERMISSIONS_REQUEST = 1337

    lateinit var serviceBinder: BlueToothService.BluetoothServiceBinder
    val messenger = Messenger(IncomingHandler())

    private var isServiceActive = false
    private var bAdapter: BluetoothAdapter? = null

    val devices: HashSet<in String> = HashSet()
    lateinit var radapter: RecyclerAdapter
    private val serviceConnection: ServiceConnection by lazy {
        object : ServiceConnection {

            override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
                isServiceActive = true
                serviceBinder = p1 as BlueToothService.BluetoothServiceBinder
                bAdapter = p1.getBtAdapter()
                serviceBinder.setMessenger(messenger)
                Toast.makeText(this@MainActivity, "Service started", Toast.LENGTH_SHORT).show()
            }

            override fun onServiceDisconnected(p0: ComponentName?) {
                isServiceActive = false
            }
        }

    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        getPermissions()
        btnBindService.setOnClickListener {
            serviceBinder.scanDevices()
        }
        radapter = RecyclerAdapter()

        rView.adapter = radapter
        rView.layoutManager = LinearLayoutManager(this)

    }

    override fun onStop() {
//        if (isServiceActive) {
//            unbindService(serviceConnection)
//        }
        super.onStop()
    }

    private fun getPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                    PERMISSIONS_REQUEST)

        } else {
            //todo handle onPermissionRequestService
            bindBlueToothService()

        }
    }

    private fun bindBlueToothService() {
        bindService(Intent(this, BlueToothService::class.java), serviceConnection, Service.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        if (isServiceActive) {
            unbindService(serviceConnection)
            isServiceActive = false
        }
        super.onDestroy()

    }

    @SuppressLint("HandlerLeak")
    inner class IncomingHandler : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                updateDeviceList -> {
                    devices.addAll(serviceBinder.getDeviceList())
                    radapter.updateAdapter(devices.toList())
                    tvScanState.text = "DeviceList"
                }
                startScan -> tvScanState.text = "Scanning"

                else -> super.handleMessage(msg)
            }
        }
    }

}
