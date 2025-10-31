package com.example.camera6

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.camera6.databinding.ActivityMainBinding
import android.content.Context.RECEIVER_NOT_EXPORTED

/**
 * MainActivity:
 * - Registers a receiver for the USB permission result.
 * - Requests permission for the first detected USB device (webcam).
 * - If permission is already granted, launches the fragment immediately (no broadcast is sent).
 *
 * Important on Android 12+:
 * - Use FLAG_MUTABLE for the PendingIntent passed to UsbManager.requestPermission(),
 *   because the framework adds extras (EXTRA_DEVICE/EXTRA_PERMISSION_GRANTED) when firing it.
 *   Using FLAG_IMMUTABLE can prevent the system from attaching these extras or even break delivery.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding
    private var mWakeLock: PowerManager.WakeLock? = null

    private val TAG = "MainActivity"
    private val usbPermissionAction = "com.example.camera6.USB_PERMISSION"

    // Will hold the selected USB device id
    private var usbDeviceId: Int = -1

    // Receives the result of UsbManager.requestPermission()
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != usbPermissionAction) return

            // On API 33+, prefer: intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

            Log.d(TAG, "USB permission callback: granted=$granted, device=$device")
            if (granted && device != null) {
                usbDeviceId = device.deviceId
                Toast.makeText(context, "USB permission granted!", Toast.LENGTH_SHORT).show()
                launchDemoFragment(usbDeviceId)
            } else {
                Toast.makeText(context, "USB permission denied!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Register receiver for our custom permission action (Android 12+ requires a visibility flag).
        registerReceiver(
            usbReceiver,
            IntentFilter(usbPermissionAction),
            RECEIVER_NOT_EXPORTED
        )

        // Find attached USB devices
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList = usbManager.deviceList
        Log.d(TAG, "USB devices found: ${deviceList.size}")

        if (deviceList.isNotEmpty()) {
            // For simplicity, use the first device. You could filter/show a chooser if multiple devices exist.
            val device = deviceList.values.first()
            Log.d(TAG, "Selected device: vendorId=${device.vendorId}, productId=${device.productId}, id=${device.deviceId}")

            // If we already have permission, the system will NOT send a broadcast. Handle it now.
            if (usbManager.hasPermission(device)) {
                Log.d(TAG, "Already have permission for deviceId=${device.deviceId}")
                usbDeviceId = device.deviceId
                Toast.makeText(this, "USB permission already granted", Toast.LENGTH_SHORT).show()
                launchDemoFragment(usbDeviceId)
                return
            }

            // Request permission: use FLAG_MUTABLE so the framework can attach extras to the PendingIntent.
            val permissionIntent = PendingIntent.getBroadcast(
                this,
                0,
                Intent(usbPermissionAction).setPackage(packageName), // make the callback explicit to this app
                PendingIntent.FLAG_MUTABLE // REQUIRED for framework to inject extras on Android 12+
            )
            Log.d(TAG, "Requesting USB permission…")
            usbManager.requestPermission(device, permissionIntent)
            // The result will arrive in usbReceiver.onReceive()
        } else {
            Toast.makeText(this, "No USB devices found", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Avoid leaks from dynamic receiver
        runCatching { unregisterReceiver(usbReceiver) }.onFailure {
            Log.w(TAG, "Receiver was not registered or already unregistered: ${it.message}")
        }
    }

    private fun launchDemoFragment(deviceId: Int) {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.cameraViewContainer, DemoFragment.newInstance(deviceId))
            .commit()
    }

    companion object {
        const val KEY_USB_DEVICE = "usbDeviceId"

        fun newInstance(context: Context, usbDeviceId: Int) =
            Intent(context, MainActivity::class.java).apply {
                putExtra(KEY_USB_DEVICE, usbDeviceId)
            }
    }
}