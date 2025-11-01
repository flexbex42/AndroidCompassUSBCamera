package com.example.camera6

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import com.example.camera6.databinding.FragmentDemoBinding
import com.jiangdg.ausbc.base.CameraFragment
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.camera.CameraUVC
import com.jiangdg.ausbc.utils.bus.BusKey
import com.jiangdg.ausbc.utils.bus.EventBus
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.jiangdg.ausbc.widget.IAspectRatio

/**
 * DemoFragment - cleaned and reorganized.
 *
 * Reordering and cleanup performed:
 * - Grouped lifecycle / view creation methods together.
 * - Camera state callback & state UI helpers grouped.
 * - EventBus observers kept in initData().
 * - UI helpers (click animation and resolution dialog) placed after event handling.
 * - Removed some overly-verbose comments; left concise notes where behavior is important.
 *
 * NOTE: No functional changes to AUSBC integration—keeps using CameraFragment API (getCameraView, etc.).
 */
class DemoFragment : CameraFragment(), View.OnClickListener {
    // ViewBinding for fragment_demo.xml; initialized in getRootView()
    private lateinit var mViewBinding: FragmentDemoBinding

    // --------------------------------------------------------------------------------------------
    // Lifecycle / view creation
    // --------------------------------------------------------------------------------------------
    override fun getRootView(inflater: LayoutInflater, container: ViewGroup?): View {
        mViewBinding = FragmentDemoBinding.inflate(inflater, container, false)
        return mViewBinding.root
    }

    override fun initView() {
        super.initView()
        // Wire click listener(s)
        mViewBinding.resolutionBtn.setOnClickListener(this)
    }

    override fun initData() {
        super.initData()

        // Show FPS updates from AUSBC
        EventBus.with<Int>(BusKey.KEY_FRAME_RATE).observe(this) { fps ->
            mViewBinding.frameRateTv.text = "frame rate:  $fps fps"
        }

        // Listen for render readiness if you need to defer actions until renderer is ready
        EventBus.with<Boolean>(BusKey.KEY_RENDER_READY).observe(this) { ready ->
            if (!ready) return@observe
            // Renderer ready - optional hook
        }
    }

    // --------------------------------------------------------------------------------------------
    // Camera view / container required by CameraFragment
    // --------------------------------------------------------------------------------------------
    override fun getCameraView(): IAspectRatio {
        Log.d(TAG, "getCameraView called")
        // AspectRatioTextureView preserves preview aspect ratio
        return AspectRatioTextureView(requireContext())
    }

    override fun getCameraViewContainer(): ViewGroup {
        Log.d(TAG, "getCameraViewContainer called")
        return mViewBinding.uvcCameraPreviewContainer
    }

    override fun getGravity(): Int = Gravity.CENTER

    // --------------------------------------------------------------------------------------------
    // Camera state callback
    // --------------------------------------------------------------------------------------------
    override fun onCameraState(self: CameraUVC, code: ICameraStateCallBack.State, msg: String?) {
        Log.d(TAG, "onCameraState: $code, $msg")
        when (code) {
            ICameraStateCallBack.State.OPENED -> handleCameraOpened()
            ICameraStateCallBack.State.CLOSED -> handleCameraClosed()
            ICameraStateCallBack.State.ERROR -> handleCameraError(msg)
        }
    }

    private fun handleCameraOpened() {
        mViewBinding.uvcLogoIv.visibility = View.GONE
        mViewBinding.frameRateTv.visibility = View.VISIBLE
        Toast.makeText(requireContext(), "camera opened success", Toast.LENGTH_SHORT).show()
    }

    private fun handleCameraClosed() {
        mViewBinding.uvcLogoIv.visibility = View.VISIBLE
        mViewBinding.frameRateTv.visibility = View.GONE
        // Intentionally omitted toast on close to avoid noisy UX
    }

    private fun handleCameraError(msg: String?) {
        mViewBinding.uvcLogoIv.visibility = View.VISIBLE
        mViewBinding.frameRateTv.visibility = View.GONE
        Toast.makeText(requireContext(), "camera opened error: $msg", Toast.LENGTH_LONG).show()
    }

    // --------------------------------------------------------------------------------------------
    // UI interactions
    // --------------------------------------------------------------------------------------------
    override fun onClick(v: View?) {
        // We know only resolutionBtn is wired; keep defensive null-check
        v ?: return
        clickAnimation(v, object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (v === mViewBinding.resolutionBtn) showResolutionDialog()
            }
        })
    }

    /**
     * Show a single-choice list of supported preview sizes.
     * Selecting a size calls updateResolution(width, height) which AUSBC will apply.
     */
    @SuppressLint("CheckResult")
    private fun showResolutionDialog() {
        val previewSizes = getAllPreviewSizes()
        if (previewSizes.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Get camera preview size failed", Toast.LENGTH_LONG).show()
            return
        }

        val labels = ArrayList<String>(previewSizes.size)
        var selectedIndex = -1
        val current = getCurrentPreviewSize()
        for (i in previewSizes.indices) {
            val w = previewSizes[i].width
            val h = previewSizes[i].height
            if (current != null && current.width == w && current.height == h) selectedIndex = i
            labels.add("$w x $h")
        }

        MaterialDialog(requireContext()).show {
            listItemsSingleChoice(items = labels, initialSelection = selectedIndex) { _, index, _ ->
                if (index == selectedIndex) return@listItemsSingleChoice
                updateResolution(previewSizes[index].width, previewSizes[index].height)
            }
        }
    }

    private fun clickAnimation(v: View, listener: Animator.AnimatorListener) {
        val scaleXAnim = ObjectAnimator.ofFloat(v, "scaleX", 1f, 0.4f, 1f)
        val scaleYAnim = ObjectAnimator.ofFloat(v, "scaleY", 1f, 0.4f, 1f)
        val alphaAnim = ObjectAnimator.ofFloat(v, "alpha", 1f, 0.4f, 1f)
        AnimatorSet().apply {
            duration = 150
            addListener(listener)
            playTogether(scaleXAnim, scaleYAnim, alphaAnim)
            start()
        }
    }

    // --------------------------------------------------------------------------------------------
    // AUSBC helper override
    // --------------------------------------------------------------------------------------------
    override fun getSelectedDeviceId(): Int = requireArguments().getInt(MainActivity.KEY_USB_DEVICE)

    // --------------------------------------------------------------------------------------------
    // Companion
    // --------------------------------------------------------------------------------------------
    companion object {
        private const val TAG = "DemoFragment"

        /**
         * Factory to create the fragment with the required usbDeviceId argument.
         * Kept for compatibility with MainActivity.newInstance usage.
         */
        fun newInstance(usbDeviceId: Int): DemoFragment {
            val fragment = DemoFragment()
            fragment.arguments = Bundle().apply {
                putInt(MainActivity.KEY_USB_DEVICE, usbDeviceId)
            }
            return fragment
        }
    }
}