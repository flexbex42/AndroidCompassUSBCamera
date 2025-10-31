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
 * DemoFragment demonstrates how to use AUSBC's CameraFragment:
 *
 * Responsibilities:
 * - Inflate the fragment UI with ViewBinding (FragmentDemoBinding).
 * - Provide the preview "camera view" (AspectRatioTextureView) via getCameraView().
 * - Provide the container to attach the preview into via getCameraViewContainer().
 * - React to AUSBC camera lifecycle callbacks (OPENED/CLOSED/ERROR).
 * - Observe AUSBC event bus for frame rate and render-ready events.
 * - Offer a dialog to change the preview resolution at runtime.
 *
 * Preconditions:
 * - fragment_demo.xml should define:
 *   - A ViewGroup container with id: uvcCameraPreviewContainer (for the preview).
 *   - A TextView with id: frameRateTv (to display FPS).
 *   - An ImageView with id: uvcLogoIv (shown when camera is closed/error).
 *   - A Button with id: resolutionBtn (to open resolution dialog).
 *
 * Device selection:
 * - getSelectedDeviceId() reads the usbDeviceId from arguments (set by MainActivity.newInstance()).
 *   AUSBC uses this id to pick and open the correct USB camera device.
 */
class DemoFragment : CameraFragment(), View.OnClickListener {
    // ViewBinding for fragment_demo.xml; initialized in getRootView()
    private lateinit var mViewBinding: FragmentDemoBinding

    /**
     * Called after root view is created. Good place to init click listeners or toolbar, etc.
     * Note: super.initView() sets up internal AUSBC components.
     */
    override fun initView() {
        super.initView()
        // Resolution settings button
        mViewBinding.resolutionBtn.setOnClickListener(this)
    }

    /**
     * Called after initView(). Set up data observers and any asynchronous data here.
     * EventBus:
     * - KEY_FRAME_RATE: emits current preview FPS; we display it in frameRateTv.
     * - KEY_RENDER_READY: signals when renderer is ready to draw; can be used for deferred actions.
     */
    override fun initData() {
        super.initData()

        // Update FPS text when AUSBC posts frame rate events
        EventBus.with<Int>(BusKey.KEY_FRAME_RATE).observe(this, { fps ->
            mViewBinding.frameRateTv.text = "frame rate:  $fps fps"
        })

        // Listen for render readiness; you can hook actions when 'ready' turns true
        EventBus.with<Boolean>(BusKey.KEY_RENDER_READY).observe(this, { ready ->
            if (!ready) return@observe
            // Renderer is ready. Optionally perform actions that require a ready surface.
        })
    }

    /**
     * AUSBC camera state machine callback.
     * - OPENED: preview should be running; show FPS label, hide logo.
     * - CLOSED: camera stopped; hide FPS label, show logo.
     * - ERROR: camera failed to open or stream; display a toast and show logo.
     */
    override fun onCameraState(
        self: CameraUVC,
        code: ICameraStateCallBack.State,
        msg: String?
    ) {
        Log.d("DemoFragment", "onCameraState: $code, $msg")
        when (code) {
            ICameraStateCallBack.State.OPENED -> handleCameraOpened()
            ICameraStateCallBack.State.CLOSED -> handleCameraClosed()
            ICameraStateCallBack.State.ERROR -> handleCameraError(msg)
        }
    }

    // UI helpers for state transitions
    private fun handleCameraError(msg: String?) {
        mViewBinding.uvcLogoIv.visibility = View.VISIBLE
        mViewBinding.frameRateTv.visibility = View.GONE
        Toast.makeText(requireContext(), "camera opened error: $msg", Toast.LENGTH_LONG).show()
    }

    private fun handleCameraClosed() {
        mViewBinding.uvcLogoIv.visibility = View.VISIBLE
        mViewBinding.frameRateTv.visibility = View.GONE
        // Toast on close is optional; often noisy if users navigate frequently.
        // Toast.makeText(requireContext(), "camera closed", Toast.LENGTH_SHORT).show()
    }

    private fun handleCameraOpened() {
        mViewBinding.uvcLogoIv.visibility = View.GONE
        mViewBinding.frameRateTv.visibility = View.VISIBLE
        Toast.makeText(requireContext(), "camera opened success", Toast.LENGTH_SHORT).show()
    }

    /**
     * Provide the preview view instance for AUSBC to render into.
     * AspectRatioTextureView helps maintain correct aspect ratio.
     * You could return other IAspectRatio implementations if desired.
     */
    override fun getCameraView(): IAspectRatio {
        Log.d("DemoFragment", "getCameraView called")
        return AspectRatioTextureView(requireContext())
    }

    /**
     * Provide the ViewGroup in which AUSBC will attach the preview view returned by getCameraView().
     * This MUST reference a real container from fragment_demo.xml.
     */
    override fun getCameraViewContainer(): ViewGroup {
        Log.d("DemoFragment", "getCameraViewContainer called")
        return mViewBinding.uvcCameraPreviewContainer
    }

    /**
     * Inflate and return the fragment root view via ViewBinding.
     * AUSBC calls this to obtain the root UI before attaching the camera view.
     */
    override fun getRootView(inflater: LayoutInflater, container: ViewGroup?): View {
        mViewBinding = FragmentDemoBinding.inflate(inflater, container, false)
        return mViewBinding.root
    }

    /**
     * Positioning hint for the preview inside its container (CENTER by default).
     * Some layouts may prefer Gravity.FILL or custom positions.
     */
    override fun getGravity(): Int = Gravity.CENTER

    /**
     * Single onClick handler for the fragment's clickable views (currently only resolutionBtn).
     * Plays a short click animation, then dispatches to the appropriate action.
     */
    override fun onClick(v: View?) {
        // v!! for brevity; in production consider null-checking defensively.
        clickAnimation(v!!, object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                when (v) {
                    mViewBinding.resolutionBtn -> {
                        showResolutionDialog()
                    }
                    // Add more view handlers here as needed
                    else -> Unit
                }
            }
        })
    }

    /**
     * Builds and shows a single-choice dialog listing all supported preview sizes.
     * Selecting a new size calls AUSBC's updateResolution(width, height), which restarts the stream.
     *
     * Requires the Material Dialogs dependency:
     * implementation "com.afollestad.material-dialogs:core:3.3.0"
     * implementation "com.afollestad.material-dialogs:list:3.3.0"
     */
    @SuppressLint("CheckResult")
    private fun showResolutionDialog() {
        getAllPreviewSizes().let { previewSizes ->
            if (previewSizes.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "Get camera preview size failed", Toast.LENGTH_LONG).show()
                return
            }
            val list = arrayListOf<String>()
            var selectedIndex = -1

            // Build labels and find the currently active size
            for (index in 0 until previewSizes.size) {
                val w = previewSizes[index].width
                val h = previewSizes[index].height
                getCurrentPreviewSize()?.apply {
                    if (width == w && height == h) {
                        selectedIndex = index
                    }
                }
                list.add("$w x $h")
            }

            // Show a material single-choice list dialog
            MaterialDialog(requireContext()).show {
                listItemsSingleChoice(
                    items = list,
                    initialSelection = selectedIndex
                ) { _, index, _ ->
                    // Ignore if user selects the current size
                    if (selectedIndex == index) return@listItemsSingleChoice
                    // Apply new resolution; AUSBC will handle restarting the preview
                    updateResolution(previewSizes[index].width, previewSizes[index].height)
                }
            }
        }
    }

    /**
     * Small tap animation for click feedback.
     */
    private fun clickAnimation(v: View, listener: Animator.AnimatorListener) {
        val scaleXAnim = ObjectAnimator.ofFloat(v, "scaleX", 1.0f, 0.4f, 1.0f)
        val scaleYAnim = ObjectAnimator.ofFloat(v, "scaleY", 1.0f, 0.4f, 1.0f)
        val alphaAnim = ObjectAnimator.ofFloat(v, "alpha", 1.0f, 0.4f, 1.0f)
        val animatorSet = AnimatorSet().apply {
            duration = 150
            addListener(listener)
            playTogether(scaleXAnim, scaleYAnim, alphaAnim)
        }
        animatorSet.start()
    }

    /**
     * AUSBC calls this to know which USB device to open.
     * The id is passed in from MainActivity via newInstance(argument).
     */
    override fun getSelectedDeviceId(): Int =
        requireArguments().getInt(MainActivity.KEY_USB_DEVICE)

    companion object {
        /**
         * Factory to create the fragment with the required usbDeviceId argument.
         * Always use this to ensure getSelectedDeviceId() returns a valid id.
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