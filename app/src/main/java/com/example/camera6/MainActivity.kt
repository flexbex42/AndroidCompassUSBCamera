package com.example.camera6

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
//import com.example.cupcake.ui.theme.AusbcTheme // update if needed
import com.jiangdg.ausbc.utils.CheckCameraPermissionUseCaseImpl
//import com.vsh.uvc.preview.CameraPreviewActivity // adjust import if needed
import com.vsh.uvc.CheckRequirementsImpl
import com.vsh.uvc.JpegBenchmarkImpl
import com.vsh.uvc.UsbDeviceMonitorImpl
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : ComponentActivity() {

    // ViewModel to track the list of USB devices and app state
    // lateinit var viewModel: DeviceListViewModel

    // ContextWrapper used for registering broadcast receiver
    // val contextWrapper = ContextWrapper(this)

    // BroadcastReceiver to handle USB permission results
    /*
    private val usbReceiver = object : BroadcastReceiver() {
        // Called when a broadcast matching ACTION_USB_PERMISSION is received
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_USB_PERMISSION) {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                // Inform the ViewModel whether permission was granted for the device
                if (device != null && granted) {
                    viewModel.onUsbDevicePermissionResult(device.deviceId, true)
                } else {
                    viewModel.onUsbDevicePermissionResult(0, false)
                }
            }
        }
    }
    */

    // Standard Android activity lifecycle method - called when activity is created
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge() // Enables edge-to-edge display
        super.onCreate(savedInstanceState)
        // Set white background for the window
        window.decorView.setBackgroundColor(Color.White.toArgb())
        // Initialize ViewModel with USB device monitor, benchmark, and requirements checkers
        /*
        viewModel = ViewModelProvider(
            this, DeviceListViewModelFactory(
                usbDevicesMonitor = UsbDeviceMonitorImpl(
                    usbManager = applicationContext.getSystemService(USB_SERVICE) as UsbManager,
                    contextWrapper = contextWrapper
                ),
                // Remove or comment out this line:
                 jpegBenchmark = JpegBenchmarkImpl(),

                checkRequirements = CheckRequirementsImpl(
                    checkCameraPermissionUseCase = CheckCameraPermissionUseCaseImpl(
                        applicationContext
                    ),
                    usbManager = applicationContext.getSystemService(Context.USB_SERVICE) as UsbManager
                )
            )
        ).get(DeviceListViewModel::class.java)
        */
        // Set the Compose UI - AusbcTheme and AusbcApp build the UI for device list and selection
        Toast.makeText(this, "MainActivity started", Toast.LENGTH_SHORT).show()
    }

    // Called when the activity comes to the foreground
    override fun onResume() {
        super.onResume()
        /*
        // Register the USB permission broadcast receiver
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        contextWrapper.registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
        // Start monitoring for USB devices
        viewModel.begin()

        // Collect and react to changes in ViewModel state
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                // Handle camera permission request
                if (state.requestCameraPermission) {
                    viewModel.onCameraPermissionRequested()
                    requestPermissions(
                        arrayOf(android.Manifest.permission.CAMERA),
                        CAMERA_PERMISSION_REQUEST_CODE
                    )
                }
                // Handle USB device permission request
                if (state.requestUsbDevicePermission) {
                    viewModel.onUsbDevicePermissionRequested()
                    val usbManager =
                        applicationContext.getSystemService(Context.USB_SERVICE) as UsbManager
                    val usbDevice = usbManager.deviceList.values.firstOrNull {
                        it.deviceId == state.selectedDeviceId
                    }

                    val pendingIntent: PendingIntent =
                        PendingIntent.getBroadcast(
                            this@MainActivity,
                            0,
                            Intent(ACTION_USB_PERMISSION),
                            PendingIntent.FLAG_IMMUTABLE
                        )

                    // Request permission for the selected USB device
                    usbDevice?.let {
                        usbManager.requestPermission(it, pendingIntent)
                    }
                }
                // If a preview should be opened, launch CameraPreviewActivity for the selected device
                // if (state.openPreviewDevice) {
                //     viewModel.onPreviewOpened()
                //     val intent = CameraPreviewActivity.newInstance(applicationContext, state.selectedDeviceId)
                //     startActivity(intent)
                // }
            }
        }
        // Collect and react to benchmark results, allowing user to share results
        lifecycleScope.launch {
            viewModel.benchmarkState.collect {
                if (it.needToShareText) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_TEXT, it.text)
                        type = "text/plain"
                    }
                    val chooser = Intent.createChooser(shareIntent, "Share via")
                    startActivity(chooser)
                    viewModel.onShareBenchmarkResultsDismissed()
                }
            }
        }
        */
    }

    // Called when the activity goes to the background
    override fun onPause() {
        /*
        // Unregister the USB permission receiver and stop device monitoring
        contextWrapper.unregisterReceiver(usbReceiver)
        viewModel.stop()
        */
        super.onPause()
    }

    // Handles permission result callbacks for camera and USB devices
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        deviceId: Int
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId)
        /*
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (permissions.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Timber.d("Camera permission granted")
                viewModel.onCameraPermissionGranted()
            } else {
                Timber.d("Camera permission denied")
                viewModel.onCameraPermissionDenied()
            }
        }
        */
    }

    companion object {
        // Constant for permission request code
        const val CAMERA_PERMISSION_REQUEST_CODE = 1
        // Action string for USB permission broadcast
        const val ACTION_USB_PERMISSION: String = "com.flexbex42.androidcompassusbcamera.USB_PERMISSION"
    }
}