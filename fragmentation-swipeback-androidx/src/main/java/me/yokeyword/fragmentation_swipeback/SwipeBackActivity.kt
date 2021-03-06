package me.yokeyword.fragmentation_swipeback

import android.os.Bundle

import me.yokeyword.fragmentation.SupportActivity
import me.yokeyword.fragmentation.SwipeBackLayout
import me.yokeyword.fragmentation_swipeback.core.ISwipeBackActivity
import me.yokeyword.fragmentation_swipeback.core.SwipeBackActivityDelegate

/**
 * You can also refer to [SwipeBackActivity] to implement YourSwipeBackActivity
 * (extends Activity and impl [me.yokeyword.fragmentation.ISupportActivity])
 *
 * Created by YoKey on 16/4/19.
 */
open class SwipeBackActivity : SupportActivity(), ISwipeBackActivity {
    private val delegate = SwipeBackActivityDelegate(apply { })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        delegate.onCreate(savedInstanceState)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        delegate.onPostCreate(savedInstanceState)
    }

    override fun getSwipeBackLayout(): SwipeBackLayout? {
        return delegate.getSwipeBackLayout()
    }

    /**
     * 是否可滑动
     *
     * @param enable
     */
    override fun setSwipeBackEnable(enable: Boolean) {
        delegate.setSwipeBackEnable(enable)
    }

    override fun setEdgeLevel(edgeLevel: SwipeBackLayout.EdgeLevel) {
        delegate.setEdgeLevel(edgeLevel)
    }

    override fun setEdgeLevel(widthPixel: Int) {
        delegate.setEdgeLevel(widthPixel)
    }

    /**
     * 限制SwipeBack的条件,默认栈内Fragment数 <= 1时 , 优先滑动退出Activity , 而不是Fragment
     *
     * @return true: Activity优先滑动退出;  false: Fragment优先滑动退出
     */
    override fun swipeBackPriority(): Boolean {
        return delegate.swipeBackPriority()
    }
}
