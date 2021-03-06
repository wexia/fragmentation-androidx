package me.yokeyword.fragmentation.helper.internal

import android.os.Bundle
import android.os.Looper
import android.os.MessageQueue.IdleHandler
import androidx.fragment.app.Fragment
import me.yokeyword.fragmentation.ISupportFragment

/**
 * Created by YoKey on 17/4/4.
 */
class VisibleDelegate(private val supportF: ISupportFragment) {
    // SupportVisible相关
    private var currentVisible = false
    private var needDispatch = true
    private var visibleWhenLeave = true

    //true = 曾经可见，也就是onLazyInitView 执行过一次
    private var isOnceVisible = false
    private var firstCreateViewCompatReplace = true
    private var abortInitVisible = false

    private var idleDispatchSupportVisible: IdleHandler? = null
    private var saveInstanceState: Bundle? = null

    private var fragment: Fragment

    init {
        if (supportF !is Fragment) {
            throw RuntimeException("Must extends Fragment")
        }
        fragment = supportF
    }

    fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            saveInstanceState = savedInstanceState
            // setUserVisibleHint() may be called before onCreate()
            visibleWhenLeave = savedInstanceState.getBoolean(FRAGMENTATION_STATE_SAVE_IS_VISIBLE_WHEN_LEAVE)
            firstCreateViewCompatReplace = savedInstanceState.getBoolean(FRAGMENTATION_STATE_SAVE_COMPAT_REPLACE)
        }
    }

    fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(FRAGMENTATION_STATE_SAVE_IS_VISIBLE_WHEN_LEAVE, visibleWhenLeave)
        outState.putBoolean(FRAGMENTATION_STATE_SAVE_COMPAT_REPLACE, firstCreateViewCompatReplace)
    }

    fun onActivityCreated() {
        if (!firstCreateViewCompatReplace
                && fragment.tag?.startsWith("android:switcher:") == true) {
            return
        }
        if (firstCreateViewCompatReplace) {
            firstCreateViewCompatReplace = false
        }
        initVisible()
    }

    private fun initVisible() {
        if (visibleWhenLeave && isFragmentVisible(fragment)) {
            if (fragment.parentFragment == null || isFragmentVisible(fragment.requireParentFragment())) {
                needDispatch = false
                enqueueDispatchVisible()
            }
        }
    }

    fun onResume() {
        if (isOnceVisible) {
            if (!currentVisible && visibleWhenLeave && isFragmentVisible(fragment)) {
                needDispatch = false
                enqueueDispatchVisible()
            }
        } else {
            if (abortInitVisible) {
                abortInitVisible = false
                initVisible()
            }
        }
    }

    fun onPause() {
        //界面还没有执行到initVisible 发出的任务taskDispatchSupportVisible，界面就已经pause。
        //为了让下次resume 时候，能正常的执行需要设置mAbortInitVisible ，来确保在resume的时候，可以执行完整initVisible

        //界面还没有执行到initVisible 发出的任务taskDispatchSupportVisible，界面就已经pause。
        //为了让下次resume 时候，能正常的执行需要设置mAbortInitVisible ，来确保在resume的时候，可以执行完整initVisible
        if (idleDispatchSupportVisible != null) {
            Looper.myQueue().removeIdleHandler(idleDispatchSupportVisible!!)
            abortInitVisible = true
            return
        }

        if (currentVisible && isFragmentVisible(fragment)) {
            needDispatch = false
            visibleWhenLeave = true
            dispatchSupportVisible(false)
        } else {
            visibleWhenLeave = false
        }
    }

    fun onHiddenChanged(hidden: Boolean) {
        if (!hidden && !fragment.isResumed) {
            //Activity 不是resumed 状态，不用显示其下的fragment，只需设置标志位，待OnResume时 显示出来
            //if fragment is shown but not resumed, ignore...
            onFragmentShownWhenNotResumed()
            return
        }
        if (hidden) {
            dispatchSupportVisible(false)
        } else {
            safeDispatchUserVisibleHint(true)
        }
    }

    private fun onFragmentShownWhenNotResumed() {
        //fragment 需要显示，但是Activity状态不是resumed，下次resumed的时候 fragment 需要显示， 所以可以认为离开的时候可见
        visibleWhenLeave = true
        abortInitVisible = true
        dispatchChildOnFragmentShownWhenNotResumed()
    }

    private fun dispatchChildOnFragmentShownWhenNotResumed() {
        val fragmentManager = fragment.childFragmentManager
        val childFragments = fragmentManager.fragments
        for (child in childFragments) {
            if (child is ISupportFragment && isFragmentVisible(child)) {
                child.getSupportDelegate().visibleDelegate.onFragmentShownWhenNotResumed()
            }
        }
    }

    fun onDestroyView() {
        isOnceVisible = false
    }

    fun setUserVisibleHint(isVisibleToUser: Boolean) {
        if (fragment.isResumed || !fragment.isAdded && isVisibleToUser) {
            if (!currentVisible && isVisibleToUser) {
                safeDispatchUserVisibleHint(true)
            } else if (currentVisible && !isVisibleToUser) {
                dispatchSupportVisible(false)
            }
        }
    }

    private fun safeDispatchUserVisibleHint(visible: Boolean) {
        if (visible) {
            enqueueDispatchVisible()
        } else {
            if (isOnceVisible) {
                dispatchSupportVisible(false)
            }
        }
    }

    private fun enqueueDispatchVisible() {
        idleDispatchSupportVisible = IdleHandler {
            dispatchSupportVisible(true)
            idleDispatchSupportVisible = null
            false
        }.apply {
            Looper.myQueue().addIdleHandler(this)
        }
    }

    private fun dispatchSupportVisible(visible: Boolean) {
        if (visible && isParentInvisible()) return
        if (currentVisible == visible) {
            needDispatch = true
            return
        }
        currentVisible = visible

        if (visible) {
            if (checkAddState()) return
            supportF.onSupportVisible()
            if (!isOnceVisible) {
                isOnceVisible = true
                supportF.onLazyInitView(saveInstanceState)
            }
            dispatchChild(true)
        } else {
            dispatchChild(false)
            supportF.onSupportInvisible()
        }
    }

    private fun dispatchChild(visible: Boolean) {
        if (!needDispatch) {
            needDispatch = true
        } else {
            if (checkAddState()) return
            val fragmentManager = fragment.childFragmentManager
            val childFragments = fragmentManager.fragments
            for (child in childFragments) {
                if (child is ISupportFragment && isFragmentVisible(child)) {
                    child.getSupportDelegate().visibleDelegate.dispatchSupportVisible(visible)
                }
            }
        }
    }

    private fun isParentInvisible(): Boolean {
        val parentFragment = fragment.parentFragment
        return if (parentFragment is ISupportFragment) {
            !parentFragment.isSupportVisible()
        } else parentFragment?.isVisible == false
    }

    private fun checkAddState(): Boolean {
        if (!fragment.isAdded) {
            currentVisible = !currentVisible
            return true
        }
        return false
    }

    @Suppress("DEPRECATION")
    private fun isFragmentVisible(fragment: Fragment): Boolean {
        return !fragment.isHidden && fragment.userVisibleHint
    }

    fun isSupportVisible(): Boolean {
        return currentVisible
    }

    companion object {
        private const val FRAGMENTATION_STATE_SAVE_IS_VISIBLE_WHEN_LEAVE = "fragmentation_invisible_when_leave"
        private const val FRAGMENTATION_STATE_SAVE_COMPAT_REPLACE = "fragmentation_compat_replace"
    }
}
