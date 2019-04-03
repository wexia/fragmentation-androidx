package me.yokeyword.sample.demo_wechat.base;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import me.yokeyword.fragmentation_swipeback.SwipeBackFragment;
import me.yokeyword.sample.R;

/**
 * Created by YoKeyword on 16/2/7.
 */
public class BaseBackFragment extends SwipeBackFragment {

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setParallaxOffset(0.5f);
    }

    protected void initToolbarNav(Toolbar toolbar) {
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white_24dp);
        toolbar.setNavigationOnClickListener(v -> _mActivity.onBackPressed());
    }
}
