package me.yokeyword.fragmentation

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import java.util.*

/**
 * Created by YoKey on 17/6/13.
 */
@Suppress("unused")
object SupportHelper {
    private const val SHOW_SPACE = 200L

    /**
     * 显示软键盘
     */
    @JvmStatic
    fun showSoftInput(view: View?) {
        if (view == null || view.context == null) return

        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        view.requestFocus()
        view.postDelayed({ imm?.showSoftInput(view, InputMethodManager.SHOW_FORCED) }, SHOW_SPACE)
    }

    /**
     * 隐藏软键盘
     */
    @JvmStatic
    fun hideSoftInput(view: View?) {
        if (view == null || view.context == null) return

        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    /**
     * 显示栈视图 dialog，调试时使用
     */
    @JvmStatic
    fun showFragmentStackHierarchyView(support: ISupportActivity?) {
        support?.getSupportDelegate()?.showFragmentStackHierarchyView()
    }

    /**
     * 显示栈视图日志，调试时使用
     */
    @JvmStatic
    fun logFragmentStackHierarchy(support: ISupportActivity?, tag: String) {
        support?.getSupportDelegate()?.logFragmentStackHierarchy(tag)
    }

    /**
     * 获得栈顶 SupportFragment
     */
    @JvmStatic
    fun getTopFragment(fragmentManager: FragmentManager?): ISupportFragment? {
        return getTopFragment(fragmentManager, 0)
    }

    @JvmStatic
    fun getTopFragment(fragmentManager: FragmentManager?, containerId: Int): ISupportFragment? {
        val fragmentList = fragmentManager?.fragments ?: return null
        for (i in fragmentList.indices.reversed()) {
            val fragment = fragmentList[i]
            if (fragment is ISupportFragment) {
                if (containerId == 0 || containerId == fragment.getSupportDelegate().containerId) {
                    return fragment
                }
            }
        }
        return null
    }

    /**
     * 获取目标 Fragment 的前一个 SupportFragment
     *
     * @param fragment 目标 Fragment
     */
    @JvmStatic
    fun getPreFragment(fragment: Fragment?): ISupportFragment? {
        val fragmentManager = fragment?.fragmentManager ?: return null
        val fragmentList = fragmentManager.fragments
        val index = fragmentList.indexOf(fragment)
        for (i in index - 1 downTo 0) {
            val preFragment = fragmentList[i]
            if (preFragment is ISupportFragment) {
                return preFragment
            }
        }
        return null
    }

    /**
     * Same as fragmentManager.findFragmentByTag(fragmentClass.getName());
     * find Fragment from FragmentStack
     */
    @JvmStatic
    fun <T : ISupportFragment> findFragment(fragmentManager: FragmentManager?,
                                            fragmentClass: Class<T>?): T? {
        return findAddedFragment(fragmentClass, null, fragmentManager)
    }

    /**
     * Same as fragmentManager.findFragmentByTag(fragmentTag);
     * find Fragment from FragmentStack
     */
    @JvmStatic
    fun <T : ISupportFragment> findFragment(fragmentManager: FragmentManager, fragmentTag: String): T? {
        return findAddedFragment(null, fragmentTag, fragmentManager)
    }

    /**
     * 从栈顶开始，寻找 FragmentManager 以及其所有子栈, 直到找到状态为 show & userVisible 的 Fragment
     */
    @JvmStatic
    fun getAddedFragment(fragmentManager: FragmentManager): ISupportFragment? {
        return getAddedFragment(fragmentManager, null)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : ISupportFragment> findAddedFragment(fragmentClass: Class<T>?,
                                                         toFragmentTag: String?,
                                                         fragmentManager: FragmentManager?): T? {
        var fragment: Fragment? = null
        if (toFragmentTag == null) {
            val fragmentList = fragmentManager?.fragments ?: return null
            for (i in fragmentList.indices.reversed()) {
                val brotherFragment = fragmentList[i]
                if (brotherFragment is ISupportFragment && brotherFragment.javaClass.name == fragmentClass?.name) {
                    fragment = brotherFragment
                    break
                }
            }
        } else {
            fragment = fragmentManager?.findFragmentByTag(toFragmentTag) ?: return null
        }
        return fragment as? T
    }

    private fun getAddedFragment(fragmentManager: FragmentManager, parentFragment: ISupportFragment?): ISupportFragment? {
        val fragmentList = fragmentManager.fragments
        if (fragmentList.isEmpty()) {
            return parentFragment
        }
        for (i in fragmentList.indices.reversed()) {
            val fragment = fragmentList[i]
            if (fragment is ISupportFragment) {
                if (fragment.isResumed && isFragmentVisible(fragment)) {
                    return getAddedFragment(fragment.childFragmentManager, fragment)
                }
            }
        }
        return parentFragment
    }

    /**
     * Get the topFragment from BackStack
     */
    @JvmStatic
    fun getBackStackTopFragment(fragmentManager: FragmentManager?): ISupportFragment? {
        return getBackStackTopFragment(fragmentManager, 0)
    }

    /**
     * Get the topFragment from BackStack
     */
    @JvmStatic
    fun getBackStackTopFragment(fragmentManager: FragmentManager?, containerId: Int): ISupportFragment? {
        val count = fragmentManager?.backStackEntryCount ?: 0
        for (i in count - 1 downTo 0) {
            val entry = fragmentManager?.getBackStackEntryAt(i)
            val fragment = fragmentManager?.findFragmentByTag(entry?.name)
            if (fragment is ISupportFragment) {
                val supportFragment = fragment as? ISupportFragment
                if (containerId == 0 || containerId == supportFragment?.getSupportDelegate()?.containerId) {
                    return supportFragment
                }
            }
        }
        return null
    }

    /**
     * Get the first Fragment from added list
     */
    fun getAddedFirstFragment(fragmentManager: FragmentManager?): ISupportFragment? {
        val fragmentList = fragmentManager?.fragments
        if (fragmentList == null || fragmentList.isEmpty()) {
            return null
        }
        val fragment = fragmentList[0]
        if (fragment is ISupportFragment) {
            if (fragment.isResumed && isFragmentVisible(fragment)) {
                return fragment
            }
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun isFragmentVisible(fragment: Fragment): Boolean {
        return !fragment.isHidden && fragment.userVisibleHint
    }

    @Suppress("UNCHECKED_CAST")
    internal fun <T : ISupportFragment> findBackStackFragment(fragmentClass: Class<T>?,
                                                              toFragmentTag: String?,
                                                              fragmentManager: FragmentManager?): T? {
        var toFragmentTagTemp = toFragmentTag
        if (toFragmentTagTemp == null) {
            toFragmentTagTemp = fragmentClass?.name
        }

        val count = fragmentManager?.backStackEntryCount ?: 0
        for (i in count - 1 downTo 0) {
            val entry = fragmentManager?.getBackStackEntryAt(i)
            if (toFragmentTagTemp == entry?.name) {
                val fragment = fragmentManager?.findFragmentByTag(entry?.name)
                if (fragment is ISupportFragment) {
                    return fragment as? T
                }
            }
        }
        return null
    }

    internal fun getWillPopFragments(fm: FragmentManager?, targetTag: String?, includeTarget: Boolean): List<Fragment> {
        val target = fm?.findFragmentByTag(targetTag)
        val willPopFragments = ArrayList<Fragment>()
        val fragmentList = fm?.fragments ?: return willPopFragments

        val size = fragmentList.size
        var startIndex = -1
        for (i in size - 1 downTo 0) {
            if (target === fragmentList[i]) {
                if (includeTarget) {
                    startIndex = i
                } else if (i + 1 < size) {
                    startIndex = i + 1
                }
                break
            }
        }

        if (startIndex == -1) {
            return willPopFragments
        }

        for (i in size - 1 downTo startIndex) {
            val fragment = fragmentList[i]
            if (fragment?.view != null) {
                willPopFragments.add(fragment)
            }
        }
        return willPopFragments
    }
}
