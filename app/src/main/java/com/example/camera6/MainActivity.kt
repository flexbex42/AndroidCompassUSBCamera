package com.example.camera6

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.WindowInsets
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.drawerlayout.widget.DrawerLayout
import com.example.camera6.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationView
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan

/**
 * MainActivity:
 * - FAB-driven USB webcam scan/request
 * - Per-device settings load/save via SettingsManager
 * - Navigation drawer shows three groups; selected option in each group is highlighted by color
 * - Prompt to save on drawer close if pending changes differ from last saved
 * - WindowInsets handling to keep FAB above system navigation bar
 * - Hides the status bar for better toolbar visibility
 */
class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding

    private val TAG = "MainActivity"
    private val usbPermissionAction = "com.example.camera6.USB_PERMISSION"

    // Will hold the selected USB device id
    private var usbDeviceId: Int = -1

    // Settings snapshots
    private var lastLoadedSettings: CameraSettings? = null
    private var pendingSettings: CameraSettings? = null
    private var currentDeviceKey: String? = null

    // Group item id arrays (match your drawer menu item IDs)
    private val RES_IDS = intArrayOf(R.id.res_640x480, R.id.res_1280x720, R.id.res_1920x1080)
    private val FORMAT_IDS = intArrayOf(R.id.format_mjpeg, R.id.format_yuyv)
    private val FPS_IDS = intArrayOf(R.id.fps_auto, R.id.fps_15, R.id.fps_30)

    // Scan connected USB devices and request permission for the first one
    private fun scanAndRequestPermission() {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList = usbManager.deviceList
        Log.d(TAG, "FAB scan: USB devices found: ${deviceList.size}")
        if( deviceList.isEmpty()) {
            Toast.makeText(this, "No USB devices found", Toast.LENGTH_SHORT).show()
            return
        }
        val device = deviceList.values.first()
        val hasPerm = usbManager.hasPermission(device)
        handleDeviceFound(device, usbManager, hasPerm)
    }
    // Receives the result of UsbManager.requestPermission()
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != usbPermissionAction) return
            // inside usbReceiver.onReceive
            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            if (device != null) {
                // granted indicates whether permission was just given by system
                handleDeviceFound(device, getSystemService(Context.USB_SERVICE) as UsbManager, granted)
            }
        }
    }
    // Helper: centralize handling of a found USB device so scan and receiver can reuse it.
    private fun handleDeviceFound(device: UsbDevice, usbManager: UsbManager, permissionAlreadyGranted: Boolean) {
        // Compute stable device key and human-readable name
        val deviceKey = getUsbDeviceKey(device)
        val productName = device.productName ?: "USB_${device.vendorId}_${device.productId}"
        val humanName = SettingsManager.getDeviceName(this, deviceKey) ?: productName

        // Create defaults for new devices
        val hadSettings = SettingsManager.hasSettings(this, deviceKey)
        if (!hadSettings) {
            SettingsManager.createDefaultForDevice(this, deviceKey, humanName)
            Toast.makeText(this, "Found new $humanName", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Found again $humanName", Toast.LENGTH_SHORT).show()
        }

        // Remember current device
        currentDeviceKey = deviceKey
        usbDeviceId = device.deviceId

        if (permissionAlreadyGranted) {
            // If we already have permission, immediately launch the fragment
            loadSettingsIntoDrawer(deviceKey)
            launchDemoFragment(usbDeviceId)
        } else {
            // Otherwise request permission with the same PendingIntent used elsewhere
            val permissionIntent = PendingIntent.getBroadcast(
                this,
                0,
                Intent(usbPermissionAction).setPackage(packageName),
                PendingIntent.FLAG_MUTABLE
            )
            usbManager.requestPermission(device, permissionIntent)
            Log.d(TAG, "Requested USB permission for deviceId=${device.deviceId}")
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Hide status bar so top toolbar is more visible
        hideStatusBar()

        // Drawer toolbar and hamburger
        setSupportActionBar(viewBinding.toolbar)
        val toggle = ActionBarDrawerToggle(
            this,
            viewBinding.drawerLayout,
            viewBinding.toolbar,
            R.string.nav_open,
            R.string.nav_close
        )
        viewBinding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Navigation item clicks: update pendingSettings and recolor; do not rely on checked state
        viewBinding.navView.setNavigationItemSelectedListener(NavigationView.OnNavigationItemSelectedListener { item ->
            val current = pendingSettings ?: lastLoadedSettings ?: CameraSettings()
            val updated = when (item.itemId) {
                // Resolution
                R.id.res_640x480 -> current.copy(preferredResolution = "640x480")
                R.id.res_1280x720 -> current.copy(preferredResolution = "1280x720")
                R.id.res_1920x1080 -> current.copy(preferredResolution = "1920x1080")
                // Format
                R.id.format_mjpeg -> current.copy(preferredFormat = "MJPEG")
                R.id.format_yuyv -> current.copy(preferredFormat = "YUYV")
                // FPS
                R.id.fps_auto -> current.copy(preferredFps = 0)
                R.id.fps_15 -> current.copy(preferredFps = 15)
                R.id.fps_30 -> current.copy(preferredFps = 30)
                else -> current
            }
            pendingSettings = updated
            applyMenuStylesFromSettings(updated)
            // Keep drawer open so user can change multiple groups
            true
        })

        // Drawer open/close: load + color on open; prompt to save on close
        viewBinding.drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}

            override fun onDrawerOpened(drawerView: View) {
                currentDeviceKey?.let { key ->
                    // Load saved or create defaults, mirror into pending, then color
                    val saved = SettingsManager.loadSettings(this@MainActivity, key)
                        ?: SettingsManager.createDefaultForDevice(this@MainActivity, key, null)
                    lastLoadedSettings = saved
                    pendingSettings = saved.copy()
                    applyMenuStylesFromSettings(saved)
                }
            }

            override fun onDrawerClosed(drawerView: View) {
                // Compare pending to last; if changed, prompt to save
                val key = currentDeviceKey ?: return
                val before = lastLoadedSettings
                val after = pendingSettings
                if (before != null && after != null && after != before) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Save settings")
                        .setMessage("Save new settings for this camera?")
                        .setPositiveButton("Save") { _, _ ->
                            SettingsManager.saveSettings(this@MainActivity, key, after)
                            lastLoadedSettings = after
                            Toast.makeText(this@MainActivity, "Settings saved", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Discard") { _, _ ->
                            // Restore coloring to last saved
                            applyMenuStylesFromSettings(before)
                            pendingSettings = before.copy()
                        }
                        .setCancelable(true)
                        .show()
                }
            }

            override fun onDrawerStateChanged(newState: Int) {}
        })

        // Register receiver for USB permission
        registerReceiver(
            usbReceiver,
            IntentFilter(usbPermissionAction),
            RECEIVER_NOT_EXPORTED
        )

        // FAB: scan for webcams
        viewBinding.fabScan.setOnClickListener { scanAndRequestPermission() }

        // Keep FAB above nav bar
        ViewCompat.setOnApplyWindowInsetsListener(viewBinding.fabScan) { fabView, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val baseMarginPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                16f,
                resources.displayMetrics
            ).toInt()
            val newBottomMargin = insets.bottom + baseMarginPx
            fabView.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                this.bottomMargin = newBottomMargin
            }
            windowInsets
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(usbReceiver) }.onFailure {
            Log.w(TAG, "Receiver not registered or already unregistered: ${it.message}")
        }
    }


    //We have a working USB device and permission; launch the DemoFragment
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

    // ----- Drawer styling helpers (color highlights per group) -----

    private fun applyMenuStylesFromSettings(settings: CameraSettings) {
        val menu = viewBinding.navView.menu
        val selectedColor = colorFromAttr(com.google.android.material.R.attr.colorPrimary)
        val unselectedColor = colorFromAttr(com.google.android.material.R.attr.colorOnSurface)

        // Reset all to unselected
        RES_IDS.forEach { setMenuItemTitleColor(menu.findItem(it), unselectedColor) }
        FORMAT_IDS.forEach { setMenuItemTitleColor(menu.findItem(it), unselectedColor) }
        FPS_IDS.forEach { setMenuItemTitleColor(menu.findItem(it), unselectedColor) }

        // Resolution: highlight one
        val resId = when (settings.preferredResolution) {
            "640x480", "640×480", "640_480" -> R.id.res_640x480
            "1920x1080", "1920_1080" -> R.id.res_1920x1080
            else -> R.id.res_1280x720
        }
        setMenuItemTitleColor(menu.findItem(resId), selectedColor)

        // Format: highlight one
        val fmtId = when (settings.preferredFormat.uppercase()) {
            "YUYV" -> R.id.format_yuyv
            else -> R.id.format_mjpeg
        }
        setMenuItemTitleColor(menu.findItem(fmtId), selectedColor)

        // FPS: highlight one
        val fpsId = when (settings.preferredFps) {
            15 -> R.id.fps_15
            30 -> R.id.fps_30
            0 -> R.id.fps_auto
            else -> R.id.fps_auto
        }
        setMenuItemTitleColor(menu.findItem(fpsId), selectedColor)
    }

    private fun setMenuItemTitleColor(item: android.view.MenuItem?, color: Int) {
        if (item == null) return
        val titleText = item.title?.toString() ?: return
        val span = SpannableString(titleText).apply {
            setSpan(ForegroundColorSpan(color), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        item.title = span
        // Prevent NavigationView built-in "checked" visuals from interfering
        item.isCheckable = false
    }

    private fun colorFromAttr(attrResId: Int): Int {
        val tv = TypedValue()
        val resolved = theme.resolveAttribute(attrResId, tv, true)
        if (!resolved) return ContextCompat.getColor(this, android.R.color.black)
        return if (tv.resourceId != 0) {
            ContextCompat.getColor(this, tv.resourceId)
        } else {
            tv.data
        }
    }

    // ----- Settings load helpers -----

    private fun loadSettingsIntoDrawer(deviceKey: String) {
        val settings = SettingsManager.loadSettings(this, deviceKey)
            ?: SettingsManager.createDefaultForDevice(this, deviceKey, null)
        lastLoadedSettings = settings
        pendingSettings = settings.copy()
        applyMenuStylesFromSettings(settings)
    }

    // Build a stable-ish key for a UsbDevice to persist settings per-camera.
    private fun getUsbDeviceKey(device: UsbDevice): String {
        val vendor = device.vendorId
        val product = device.productId

        val serial: String? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) device.serialNumber else null
        } catch (t: Throwable) {
            null
        }

        val manufacturer = try { device.manufacturerName ?: "" } catch (t: Throwable) { "" }
        val productName = try { device.productName ?: "" } catch (t: Throwable) { "" }

        return when {
            !serial.isNullOrEmpty() -> "usb_${vendor}_${product}_$serial"
            manufacturer.isNotEmpty() || productName.isNotEmpty() -> {
                val man = manufacturer.replace("\\s+".toRegex(), "_")
                val prod = productName.replace("\\s+".toRegex(), "_")
                "usb_${vendor}_${product}_${man}_$prod"
            }
            else -> "usb_${vendor}_${product}"
        }
    }

    // Hide status bar for better toolbar visibility
    private fun hideStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        }
    }
}