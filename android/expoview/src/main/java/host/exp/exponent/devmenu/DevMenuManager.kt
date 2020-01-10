package host.exp.exponent.devmenu

import android.content.Context
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.facebook.react.ReactRootView
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.UiThreadUtil
import com.facebook.react.bridge.WritableMap
import com.facebook.react.common.ShakeDetector
import host.exp.exponent.di.NativeModuleDepsProvider
import host.exp.exponent.experience.ExperienceActivity
import host.exp.exponent.kernel.Kernel
import versioned.host.exp.exponent.ReactUnthemedRootView
import java.util.*
import javax.inject.Inject

private const val DEV_MENU_JS_MODULE_NAME = "HomeMenu"
private const val DEV_MENU_ANIMATION_DELAY = 100L
private const val DEV_MENU_ANIMATION_DURATION = 200L
private const val DEV_MENU_ANIMATION_INITIAL_ALPHA = 0.0f
private const val DEV_MENU_ANIMATION_INITIAL_SCALE = 1.1f
private const val DEV_MENU_ANIMATION_TARGET_ALPHA = 1.0f
private const val DEV_MENU_ANIMATION_TARGET_SCALE = 1.0f

class DevMenuManager {
  private var shakeDetector: ShakeDetector? = null
  private var reactRootView: ReactRootView? = null
  private val devMenuModulesRegistry = WeakHashMap<ExperienceActivity, DevMenuModule>()

  @Inject
  internal val kernel: Kernel? = null

  init {
    NativeModuleDepsProvider.getInstance().inject(DevMenuManager::class.java, this)
  }

  //region publics

  fun maybeStartDetectingShakes(context: Context) {
    if (shakeDetector != null) {
      return
    }
    shakeDetector = ShakeDetector { this.onShakeGesture() }
    shakeDetector?.start(context.getSystemService(Context.SENSOR_SERVICE) as SensorManager)
  }

  fun registerDevMenuModuleForActivity(devMenuModule: DevMenuModule, activity: ExperienceActivity) {
    // Start shake detector once the first DevMenuModule registers in the manager.
    maybeStartDetectingShakes(activity.applicationContext)
    devMenuModulesRegistry[activity] = devMenuModule
  }

  fun showInActivity(activity: ExperienceActivity) {
    UiThreadUtil.runOnUiThread {
      val devMenuModule = devMenuModulesRegistry[activity] ?: return@runOnUiThread
      val devMenuView = prepareRootView(devMenuModule.getInitialProps())

      activity.addView(devMenuView)
      kernel?.reactInstanceManager?.onHostResume(activity)

      devMenuView.animate().apply {
        startDelay = DEV_MENU_ANIMATION_DELAY
        duration = DEV_MENU_ANIMATION_DURATION

        alpha(DEV_MENU_ANIMATION_TARGET_ALPHA)
        scaleX(DEV_MENU_ANIMATION_TARGET_SCALE)
        scaleY(DEV_MENU_ANIMATION_TARGET_SCALE)
      }
    }
  }

  fun hideInActivity(activity: ExperienceActivity) {
    UiThreadUtil.runOnUiThread {
      reactRootView?.let {
        it.animate().apply {
          duration = DEV_MENU_ANIMATION_DURATION

          alpha(DEV_MENU_ANIMATION_INITIAL_ALPHA)
          withEndAction {
            val parentView = it.parent as FrameLayout?
            it.visibility = View.GONE
            parentView?.removeView(it)
            kernel?.reactInstanceManager?.onHostPause(activity)
          }
        }
      }
    }
  }

  fun hideInCurrentActivity() {
    val currentActivity = ExperienceActivity.getCurrentActivity()

    if (currentActivity != null) {
      hideInActivity(currentActivity)
    }
  }

  fun toggleInActivity(activity: ExperienceActivity) {
    if (isDevMenuVisible()) {
      hideInActivity(activity)
    } else {
      showInActivity(activity)
    }
  }

  fun getMenuItems(): WritableMap {
    val devMenuModule = getCurrentDevMenuModule()
    return devMenuModule?.getMenuItems() ?: Arguments.createMap()
  }

  fun selectItemWithKey(itemKey: String) {
    getCurrentDevMenuModule()?.selectItemWithKey(itemKey)
  }

  fun isShownInActivity(activity: ExperienceActivity): Boolean {
    return reactRootView != null && activity.hasView(reactRootView)
  }

  //endregion publics
  //region internals

  private fun prepareRootView(initialProps: Bundle): ReactRootView {
    if (reactRootView == null) {
      reactRootView = ReactUnthemedRootView(kernel?.activityContext)
      reactRootView?.startReactApplication(kernel?.reactInstanceManager, DEV_MENU_JS_MODULE_NAME, initialProps)
    } else {
      reactRootView?.appProperties = initialProps
    }

    val rootView = reactRootView!!

    rootView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    rootView.visibility = View.VISIBLE
    rootView.alpha = DEV_MENU_ANIMATION_INITIAL_ALPHA
    rootView.scaleX = DEV_MENU_ANIMATION_INITIAL_SCALE
    rootView.scaleY = DEV_MENU_ANIMATION_INITIAL_SCALE

    return rootView
  }

  private fun getCurrentDevMenuModule(): DevMenuModule? {
    val currentActivity = getCurrentExperienceActivity()
    return devMenuModulesRegistry[currentActivity]
  }

  private fun getCurrentExperienceActivity(): ExperienceActivity? {
    return ExperienceActivity.getCurrentActivity()
  }

  private fun isDevMenuVisible(): Boolean {
    return reactRootView?.parent != null
  }

  private fun onShakeGesture() {
    val currentActivity = ExperienceActivity.getCurrentActivity()

    if (currentActivity != null) {
      toggleInActivity(currentActivity)
    }
  }

  //endregion internals
}