package me.yokeyword.fragmentation

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import me.yokeyword.fragmentation.Fragmentation.Companion.getDefault
import me.yokeyword.fragmentation.SupportFragmentDelegate.EnterAnimListener
import me.yokeyword.fragmentation.SupportHelper.getBackStackTopFragment
import me.yokeyword.fragmentation.SupportHelper.getPreFragment
import me.yokeyword.fragmentation.SupportHelper.getTopFragment
import me.yokeyword.fragmentation.exception.AfterSaveStateTransactionWarning
import me.yokeyword.fragmentation.helper.internal.ResultRecord
import me.yokeyword.fragmentation.helper.internal.TransactionRecord.SharedElement
import me.yokeyword.fragmentation.queue.Action
import me.yokeyword.fragmentation.queue.ActionQueue
import java.util.*

/**
 * Controller
 * Created by YoKeyword on 16/1/22.
 */
class TransactionDelegate internal constructor(private val supportA: ISupportActivity) {
    private val activity: FragmentActivity
    private var handler: Handler = Handler(Looper.getMainLooper())
    internal var actionQueue: ActionQueue

    init {
        if (supportA !is FragmentActivity) {
            throw RuntimeException("must extends FragmentActivity/AppCompatActivity")
        }
        this.activity = supportA
        actionQueue = ActionQueue(handler)
    }

    internal fun post(runnable: Runnable) {
        actionQueue.enqueue(object : Action(activity.supportFragmentManager) {
            override fun run() {
                runnable.run()
            }
        })
    }

    internal fun loadRootTransaction(fm: FragmentManager?,
                                     containerId: Int,
                                     to: ISupportFragment?,
                                     addToBackStack: Boolean,
                                     allowAnimation: Boolean) {
        enqueue(fm, object : Action(ACTION_LOAD, fm) {
            override fun run() {
                bindContainerId(containerId, to)

                var toFragmentTag = to?.javaClass?.name
                val transactionRecord = to?.getSupportDelegate()?.transactionRecord
                if (transactionRecord?.tag != null) {
                    toFragmentTag = transactionRecord.tag
                }

                start(fm, null, to, toFragmentTag, !addToBackStack, null, allowAnimation, TYPE_REPLACE)
            }
        })
    }

    internal fun loadMultipleRootTransaction(fm: FragmentManager?,
                                             containerId: Int,
                                             showPosition: Int,
                                             tos: Array<out ISupportFragment?>?) {
        enqueue(fm, object : Action(ACTION_LOAD, fm) {
            override fun run() {
                val ft = fm?.beginTransaction()

                tos?.let {
                    for (i in it.indices) {
                        val to = it[i] as? Fragment ?: continue
                        val args = getArguments(to)
                        args.putInt(FRAGMENTATION_ARG_ROOT_STATUS, SupportFragmentDelegate.STATUS_ROOT_ANIM_DISABLE)
                        bindContainerId(containerId, it[i])

                        val toName = to.javaClass.name
                        ft?.add(containerId, to, toName)

                        if (i != showPosition) {
                            ft?.hide(to)
                        }
                    }
                }

                supportCommit(fm, ft)
            }
        })
    }

    /**
     * Show showFragment then hide hideFragment
     */
    internal fun showHideFragment(fm: FragmentManager?,
                                  showFragment: ISupportFragment?,
                                  hideFragment: ISupportFragment?) {
        enqueue(fm, object : Action(fm) {
            override fun run() {
                doShowHideFragment(fm, showFragment, hideFragment)
            }
        })
    }

    private fun start(fm: FragmentManager?,
                      from: ISupportFragment?,
                      to: ISupportFragment?,
                      toFragmentTag: String?,
                      dontAddToBackStack: Boolean,
                      sharedElementList: ArrayList<SharedElement>?,
                      allowRootFragmentAnim: Boolean,
                      type: Int) {
        val ft = fm?.beginTransaction()
        val addMode = type == TYPE_ADD
                || type == TYPE_ADD_RESULT
                || type == TYPE_ADD_WITHOUT_HIDE
                || type == TYPE_ADD_RESULT_WITHOUT_HIDE
        val fromF = from as? Fragment
        val toF = to as? Fragment
        val args = getArguments(toF)
        args.putBoolean(FRAGMENTATION_ARG_REPLACE, !addMode)

        if (sharedElementList == null) {
            if (addMode) { // Replace mode forbidden animation, the replace animations exist overlapping Bug on support-v4.
                val record = to?.getSupportDelegate()?.transactionRecord
                if (record != null && record.targetFragmentEnter != Integer.MIN_VALUE) {
                    ft?.setCustomAnimations(record.targetFragmentEnter, record.currentFragmentPopExit,
                            record.currentFragmentPopEnter, record.targetFragmentExit)
                    args.putInt(FRAGMENTATION_ARG_CUSTOM_ENTER_ANIM, record.targetFragmentEnter)
                    args.putInt(FRAGMENTATION_ARG_CUSTOM_EXIT_ANIM, record.targetFragmentExit)
                    args.putInt(FRAGMENTATION_ARG_CUSTOM_POP_EXIT_ANIM, record.currentFragmentPopExit)
                } else {
                    ft?.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                }
            } else {
                args.putInt(FRAGMENTATION_ARG_ROOT_STATUS, SupportFragmentDelegate.STATUS_ROOT_ANIM_DISABLE)
            }
        } else {
            args.putBoolean(FRAGMENTATION_ARG_IS_SHARED_ELEMENT, true)
            for (item in sharedElementList) {
                ft?.addSharedElement(item.sharedElement, item.sharedName)
            }
        }
        if (from == null) {
            toF?.let { ft?.replace(args.getInt(FRAGMENTATION_ARG_CONTAINER), it, toFragmentTag) }
            if (!addMode) {
                ft?.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                args.putInt(FRAGMENTATION_ARG_ROOT_STATUS, if (allowRootFragmentAnim)
                    SupportFragmentDelegate.STATUS_ROOT_ANIM_ENABLE
                else
                    SupportFragmentDelegate.STATUS_ROOT_ANIM_DISABLE)
            }
        } else {
            if (addMode) {
                toF?.let { ft?.add(from.getSupportDelegate().containerId, it, toFragmentTag) }
                if (type != TYPE_ADD_WITHOUT_HIDE && type != TYPE_ADD_RESULT_WITHOUT_HIDE) {
                    fromF?.let { ft?.hide(it) }
                }
            } else {
                toF?.let { ft?.replace(from.getSupportDelegate().containerId, it, toFragmentTag) }
            }
        }

        if (!dontAddToBackStack && type != TYPE_REPLACE_DONT_BACK) {
            ft?.addToBackStack(toFragmentTag)
        }
        supportCommit(fm, ft)
    }

    /**
     * Start the target Fragment and pop itself
     */
    fun startWithPop(fm: FragmentManager?, from: ISupportFragment?, to: ISupportFragment?) {
        enqueue(fm, object : Action(ACTION_POP_MOCK, fm) {
            override fun run() {
                val top = getTopFragmentForStart(from, fm)
                        ?: throw NullPointerException("There is no Fragment in the FragmentManager," +
                                " maybe you need to call loadRootFragment() first!")

                val containerId = top.getSupportDelegate().containerId
                bindContainerId(containerId, to)

                top.getSupportDelegate().lockAnim = true
                if (fm?.isStateSaved == false) {
                    mockStartWithPopAnim(getTopFragment(fm), to, top.getSupportDelegate().animHelper?.popExitAnim)
                }
                handleAfterSaveInStateTransactionException(fm, "startWithPop()")
                removeTopFragment(fm)
                fm?.popBackStackImmediate()
            }
        })

        dispatchStartTransaction(fm, from, to, 0, ISupportFragment.STANDARD, TYPE_ADD)
    }

    fun startWithPopTo(fm: FragmentManager?,
                       from: ISupportFragment?,
                       to: ISupportFragment?,
                       fragmentTag: String?,
                       includeTargetFragment: Boolean) {
        enqueue(fm, object : Action(ACTION_POP_MOCK, fm) {
            override fun run() {
                var flag = 0
                if (includeTargetFragment) {
                    flag = FragmentManager.POP_BACK_STACK_INCLUSIVE
                }
                val willPopFragments = SupportHelper.getWillPopFragments(fm, fragmentTag, includeTargetFragment)
                if (willPopFragments.isEmpty()) return
                val top = getTopFragmentForStart(from, fm)
                        ?: throw NullPointerException("There is no Fragment in the FragmentManager, maybe you need to call loadRootFragment() first!")
                val containerId = top.getSupportDelegate().containerId
                bindContainerId(containerId, to)
                if (fm?.isStateSaved == false) {
                    mockStartWithPopAnim(getTopFragment(fm), to, top.getSupportDelegate().animHelper?.popExitAnim)
                }
                handleAfterSaveInStateTransactionException(fm, "startWithPopTo()")
                safePopTo(fragmentTag, fm, flag, willPopFragments)
            }
        })
        dispatchStartTransaction(fm, from, to, 0, ISupportFragment.STANDARD, TYPE_ADD)
    }

    /**
     * Remove
     * Only allowed in interfaces  {@link [ExtraTransaction.DontAddToBackStackTransaction] # [ExtraTransaction.remove] }
     */
    internal fun remove(fm: FragmentManager?, fragment: Fragment, showPreFragment: Boolean) {
        enqueue(fm, object : Action(ACTION_POP, fm) {
            override fun run() {
                val ft = fm?.beginTransaction()
                        ?.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
                        ?.remove(fragment)

                if (showPreFragment) {
                    val preFragment = getPreFragment(fragment)
                    if (preFragment is Fragment) {
                        ft?.show(preFragment)
                    }
                }
                supportCommit(fm, ft)
            }
        })
    }

    /**
     * Pop
     */
    fun pop(fm: FragmentManager?) {
        enqueue(fm, object : Action(ACTION_POP, fm) {
            override fun run() {
                handleAfterSaveInStateTransactionException(fm, "pop()")
                removeTopFragment(fm)
                fm?.popBackStackImmediate()
            }
        })
    }

    fun popQuiet(fm: FragmentManager?, fragment: Fragment?) {
        enqueue(fm, object : Action(ACTION_POP_MOCK, fm) {
            override fun run() {
                supportA.getSupportDelegate().popMultipleNoAnim = true
                removeTopFragment(fm)
                fm?.popBackStackImmediate(fragment?.tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                supportA.getSupportDelegate().popMultipleNoAnim = false
            }
        })
    }

    private fun removeTopFragment(fm: FragmentManager?) {
        try { // Safe popBackStack()
            val top = getBackStackTopFragment(fm)
            if (top is Fragment && fm != null) {
                fm.beginTransaction()
                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
                        .remove(top)
                        .commitAllowingStateLoss()
            }
        } catch (ignored: Exception) {
        }
    }

    /**
     * Pop the last fragment transition from the manager's fragment pop stack.
     *
     * @param targetFragmentTag     Tag
     * @param includeTargetFragment Whether it includes targetFragment
     */
    internal fun popTo(targetFragmentTag: String?,
                       includeTargetFragment: Boolean,
                       afterPopTransactionRunnable: Runnable?,
                       fm: FragmentManager?,
                       popAnim: Int) {
        enqueue(fm, object : Action(ACTION_POP_MOCK, fm) {
            override fun run() {
                doPopTo(targetFragmentTag, includeTargetFragment, fm, popAnim)
                afterPopTransactionRunnable?.run()
            }
        })
    }

    /**
     * Dispatch the start transaction.
     */
    internal fun dispatchStartTransaction(fm: FragmentManager?,
                                          from: ISupportFragment?,
                                          to: ISupportFragment?,
                                          requestCode: Int,
                                          launchMode: Int,
                                          type: Int) {
        enqueue(fm, object : Action(if (launchMode == ISupportFragment.SINGLETASK) ACTION_POP_MOCK else ACTION_NORMAL) {
            override fun run() {
                doDispatchStartTransaction(fm, from, to, requestCode, launchMode, type)
            }
        })
    }

    private fun doDispatchStartTransaction(fm: FragmentManager?,
                                           from: ISupportFragment?,
                                           to: ISupportFragment?,
                                           requestCode: Int,
                                           launchMode: Int,
                                           type: Int) {
        var fromTemp = from
        if ((type == TYPE_ADD_RESULT || type == TYPE_ADD_RESULT_WITHOUT_HIDE) && fromTemp != null) {
            if ((fromTemp as? Fragment)?.isAdded == false) {
                Log.w(TAG, fromTemp.javaClass.simpleName + " has not been attached yet! startForResult() converted to start()")
            } else {
                saveRequestCode(fm, fromTemp as? Fragment, to as? Fragment, requestCode)
            }
        }

        fromTemp = getTopFragmentForStart(fromTemp, fm)

        val containerId = getArguments(to as? Fragment).getInt(FRAGMENTATION_ARG_CONTAINER, 0)
        if (fromTemp == null && containerId == 0) {
            Log.e(TAG, "There is no Fragment in the FragmentManager, maybe you need to call loadRootFragment()!")
            return
        }

        if (fromTemp != null && containerId == 0) {
            bindContainerId(fromTemp.getSupportDelegate().containerId, to)
        }

        // process ExtraTransaction
        var toFragmentTag = to?.javaClass?.name
        var dontAddToBackStack = false
        var sharedElementList: ArrayList<SharedElement>? = null
        to?.getSupportDelegate()?.transactionRecord?.also {
            if (it.tag != null) {
                toFragmentTag = it.tag
            }
            dontAddToBackStack = it.dontAddToBackStack
            if (it.sharedElementList != null) {
                sharedElementList = it.sharedElementList
            }
        }

        if (handleLaunchMode(fm, fromTemp, to, toFragmentTag, launchMode)) return

        start(fm, fromTemp, to, toFragmentTag, dontAddToBackStack,
                sharedElementList, false, type)
    }

    private fun doShowHideFragment(fm: FragmentManager?,
                                   showFragment: ISupportFragment?,
                                   hideFragment: ISupportFragment?) {
        if (showFragment === hideFragment) return

        val ft = (showFragment as? Fragment)?.let { fm?.beginTransaction()?.show(it) }
        if (hideFragment == null) {
            val fragmentList = fm?.fragments ?: return
            for (fragment in fragmentList) {
                if (fragment != null && fragment !== showFragment) {
                    ft?.hide(fragment)
                }
            }
        } else {
            (hideFragment as? Fragment)?.let { ft?.hide(it) }
        }
        supportCommit(fm, ft)
    }

    private fun doPopTo(targetFragmentTag: String?,
                        includeTargetFragment: Boolean,
                        fm: FragmentManager?,
                        popAnim: Int) {
        handleAfterSaveInStateTransactionException(fm, "popTo()")

        val targetFragment = fm?.findFragmentByTag(targetFragmentTag)
        if (targetFragment == null) {
            Log.e(TAG, "Pop failure! Can't find FragmentTag:$targetFragmentTag in the FragmentManager's Stack.")
            return
        }

        var flag = 0
        if (includeTargetFragment) {
            flag = FragmentManager.POP_BACK_STACK_INCLUSIVE
        }

        val willPopFragments = SupportHelper
                .getWillPopFragments(fm, targetFragmentTag, includeTargetFragment)
        if (willPopFragments.isEmpty()) return

        val top = willPopFragments[0]
        mockPopToAnim(top, targetFragmentTag, fm, flag, willPopFragments, popAnim)
    }

    private fun mockPopToAnim(from: Fragment, targetFragmentTag: String?, fm: FragmentManager, flag: Int, willPopFragments: List<Fragment>, popAnim: Int) {
        if (from !is ISupportFragment) {
            safePopTo(targetFragmentTag, fm, flag, willPopFragments)
            return
        }
        val container = findContainerById(from, from.getSupportDelegate().containerId) ?: return
        val fromView = from.view ?: return
        if (fromView.animation != null) {
            fromView.clearAnimation()
        }
        container.removeViewInLayout(fromView)
        val mock = addMockView(fromView, container)
        safePopTo(targetFragmentTag, fm, flag, willPopFragments)

        val animation: Animation? = when (popAnim) {
            DEFAULT_POPTO_ANIM -> {
                from.getSupportDelegate().getExitAnim() ?: object : Animation() {}
            }
            0 -> {
                object : Animation() {}
            }
            else -> {
                AnimationUtils.loadAnimation(activity, popAnim)
            }
        }

        mock.startAnimation(animation)
        handler.postDelayed({
            try {
                if (mock.animation != null) {
                    mock.clearAnimation()
                }
                mock.removeViewInLayout(fromView)
                container.removeViewInLayout(mock)
            } catch (ignored: Exception) {
            }
        }, animation?.duration ?: 0)
    }

    private fun safePopTo(fragmentTag: String?,
                          fm: FragmentManager?,
                          flag: Int,
                          willPopFragments: List<Fragment>) {
        supportA.getSupportDelegate().popMultipleNoAnim = true

        // 批量删除fragment ，static final int OP_REMOVE = 3;
        val transaction = fm?.beginTransaction()
                ?.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
        for (fragment in willPopFragments) {
            transaction?.remove(fragment)
        }
        transaction?.commitAllowingStateLoss()

        // 弹栈到指定fragment，从数据上来看，和上面的效果完全一样，把栈中所有的backStackRecord 包含多个记录，每个记录包含多个操作 ，
        // 在每个记录中，对操作索引 按照从大到小的顺序，逐个进行反操作。
        // 除了第一个记录，其余每个记录都有两个操作，一个是添加OP_ADD = 1;(反操作是remove)  一个是OP_HIDE = 4;（反操作是show）（这是在start中设定的）
        // 之所以有上面的批量删除，在执行动画的时候，发现f.mView == null  就不去执行show动画。
        fm?.popBackStackImmediate(fragmentTag, flag)
        supportA.getSupportDelegate().popMultipleNoAnim = false
    }

    private fun mockStartWithPopAnim(from: ISupportFragment?,
                                     to: ISupportFragment?,
                                     exitAnim: Animation?) {
        val fromF = from as? Fragment ?: return
        val container = findContainerById(fromF, from.getSupportDelegate().containerId) ?: return

        val fromView = fromF.view ?: return
        if (fromView.animation != null) {
            fromView.clearAnimation()
        }
        container.removeViewInLayout(fromView)
        val mock = addMockView(fromView, container)

        to?.getSupportDelegate()?.enterAnimListener = object : EnterAnimListener {
            override fun onEnterAnimStart() {
                mock.startAnimation(exitAnim)
                handler.postDelayed({
                    try {
                        if (mock.animation != null) {
                            mock.clearAnimation()
                        }
                        mock.removeViewInLayout(fromView)
                        container.removeViewInLayout(mock)
                    } catch (ignored: Exception) {
                    }
                }, exitAnim?.duration ?: 0)
            }
        }
    }

    private fun addMockView(fromView: View, container: ViewGroup): ViewGroup {
        val mock = object : ViewGroup(activity) {
            override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {}
        }

        mock.addView(fromView)
        container.addView(mock)
        return mock
    }

    private fun getTopFragmentForStart(from: ISupportFragment?, fm: FragmentManager?): ISupportFragment? {
        return if (from == null) {
            getTopFragment(fm)
        } else {
            if (from.getSupportDelegate().containerId == 0) {
                val fromF = from as? Fragment
                check(fromF?.tag?.startsWith("android:switcher:") == false) {
                    "Can't find container, please call loadRootFragment() first!"
                }
            }
            getTopFragment(fm, from.getSupportDelegate().containerId)
        }
    }

    /**
     * Dispatch the pop-event. Priority of the top of the stack of Fragment
     */
    fun dispatchBackPressedEvent(activeFragment: ISupportFragment?): Boolean {
        if (activeFragment != null) {
            if (activeFragment.onBackPressedSupport()) return true

            val parentFragment = (activeFragment as? Fragment)?.parentFragment
            return dispatchBackPressedEvent(parentFragment as? ISupportFragment)
        }
        return false
    }

    fun handleResultRecord(from: Fragment?) {
        try {
            val args = from?.arguments ?: return
            val resultRecord: ResultRecord = args.getParcelable(FRAGMENTATION_ARG_RESULT_RECORD)
                    ?: return

            val targetFragment = from.fragmentManager
                    ?.getFragment(args, FRAGMENTATION_STATE_SAVE_RESULT) as? ISupportFragment
            targetFragment?.onFragmentResult(
                    resultRecord.requestCode, resultRecord.resultCode, resultRecord.resultBundle)
        } catch (ignored: IllegalStateException) {
            // Fragment no longer exists
        }
    }

    private fun enqueue(fm: FragmentManager?, action: Action) {
        if (fm == null) {
            Log.w(TAG, "FragmentManager is null, skip the action!")
            return
        }
        actionQueue.enqueue(action)
    }

    private fun bindContainerId(containerId: Int, to: ISupportFragment?) {
        val args = getArguments(to as? Fragment)
        args.putInt(FRAGMENTATION_ARG_CONTAINER, containerId)
    }

    private fun getArguments(fragment: Fragment?): Bundle {
        var bundle = fragment?.arguments
        if (bundle == null) {
            bundle = Bundle()
            fragment?.arguments = bundle
        }
        return bundle
    }

    private fun supportCommit(fm: FragmentManager?, transaction: FragmentTransaction?) {
        handleAfterSaveInStateTransactionException(fm, "commit()")
        transaction?.commitAllowingStateLoss()
    }

    private fun handleLaunchMode(fm: FragmentManager?,
                                 topFragment: ISupportFragment?,
                                 to: ISupportFragment?,
                                 toFragmentTag: String?,
                                 launchMode: Int): Boolean {
        if (topFragment == null) return false
        val stackToFragment = SupportHelper
                .findBackStackFragment(to?.javaClass, toFragmentTag, fm) ?: return false

        if (launchMode == ISupportFragment.SINGLETOP) {
            if (to === topFragment || to?.javaClass?.name == topFragment.javaClass.name) {
                handleNewBundle(to, stackToFragment)
                return true
            }
        } else if (launchMode == ISupportFragment.SINGLETASK) {
            doPopTo(toFragmentTag, false, fm, DEFAULT_POPTO_ANIM)
            handler.post { handleNewBundle(to, stackToFragment) }
            return true
        }
        return false
    }

    private fun handleNewBundle(toFragment: ISupportFragment?, stackToFragment: ISupportFragment?) {
        val argsNewBundle = toFragment?.getSupportDelegate()?.newBundle
        val args = getArguments(toFragment as? Fragment)
        if (args.containsKey(FRAGMENTATION_ARG_CONTAINER)) {
            args.remove(FRAGMENTATION_ARG_CONTAINER)
        }
        if (argsNewBundle != null) {
            args.putAll(argsNewBundle)
        }
        stackToFragment?.onNewBundle(args)
    }

    /**
     * save requestCode
     */
    private fun saveRequestCode(fm: FragmentManager?,
                                from: Fragment?,
                                to: Fragment?,
                                requestCode: Int) {
        val bundle = getArguments(to)
        val resultRecord = ResultRecord()
        resultRecord.requestCode = requestCode
        bundle.putParcelable(FRAGMENTATION_ARG_RESULT_RECORD, resultRecord)
        from?.let { fm?.putFragment(bundle, FRAGMENTATION_STATE_SAVE_RESULT, it) }
    }

    private fun findContainerById(fragment: Fragment, containerId: Int): ViewGroup? {
        if (fragment.view == null) return null
        val parentFragment = fragment.parentFragment
        val container = if (parentFragment != null) {
            if (parentFragment.view != null) {
                parentFragment.view?.findViewById(containerId)
            } else {
                findContainerById(parentFragment, containerId)
            }
        } else {
            activity.findViewById(containerId)
        }
        return if (container is ViewGroup) {
            container
        } else null
    }

    private fun handleAfterSaveInStateTransactionException(fm: FragmentManager?, action: String) {
        val stateSaved = fm?.isStateSaved
        if (stateSaved == true) {
            val e = AfterSaveStateTransactionWarning(action)
            getDefault().getExceptionHandler()?.onException(e)
        }
    }

    companion object {
        internal const val DEFAULT_POPTO_ANIM = Integer.MAX_VALUE
        internal const val FRAGMENTATION_ARG_RESULT_RECORD = "fragment_arg_result_record"
        internal const val FRAGMENTATION_ARG_ROOT_STATUS = "fragmentation_arg_root_status"
        internal const val FRAGMENTATION_ARG_IS_SHARED_ELEMENT = "fragmentation_arg_is_shared_element"
        internal const val FRAGMENTATION_ARG_CONTAINER = "fragmentation_arg_container"
        internal const val FRAGMENTATION_ARG_REPLACE = "fragmentation_arg_replace"
        internal const val FRAGMENTATION_ARG_CUSTOM_ENTER_ANIM = "fragmentation_arg_custom_enter_anim"
        internal const val FRAGMENTATION_ARG_CUSTOM_EXIT_ANIM = "fragmentation_arg_custom_exit_anim"
        internal const val FRAGMENTATION_ARG_CUSTOM_POP_EXIT_ANIM = "fragmentation_arg_custom_pop_exit_anim"
        internal const val FRAGMENTATION_STATE_SAVE_ANIMATOR = "fragmentation_state_save_animator"
        internal const val FRAGMENTATION_STATE_SAVE_IS_HIDDEN = "fragmentation_state_save_status"
        internal const val TYPE_ADD = 0
        internal const val TYPE_ADD_RESULT = 1
        internal const val TYPE_ADD_WITHOUT_HIDE = 2
        internal const val TYPE_ADD_RESULT_WITHOUT_HIDE = 3
        internal const val TYPE_REPLACE = 10
        internal const val TYPE_REPLACE_DONT_BACK = 11
        private const val TAG = "Fragmentation"
        private const val FRAGMENTATION_STATE_SAVE_RESULT = "fragmentation_state_save_result"
    }
}
