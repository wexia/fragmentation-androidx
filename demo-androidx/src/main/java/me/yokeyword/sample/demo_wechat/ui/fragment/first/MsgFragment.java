package me.yokeyword.sample.demo_wechat.ui.fragment.first;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import me.yokeyword.sample.R;
import me.yokeyword.sample.demo_wechat.adapter.MsgAdapter;
import me.yokeyword.sample.demo_wechat.base.BaseBackFragment;
import me.yokeyword.sample.demo_wechat.entity.Chat;
import me.yokeyword.sample.demo_wechat.entity.Msg;

/**
 * Created by YoKeyword on 16/6/30.
 */
@SuppressWarnings("FieldCanBeLocal")
public class MsgFragment extends BaseBackFragment {
    private static final String ARG_MSG = "arg_msg";

    private Toolbar mToolbar;
    private RecyclerView mRecy;
    private EditText mEtSend;
    private Button mBtnSend;

    private Chat mChat;
    private MsgAdapter mAdapter;

    public static MsgFragment newInstance(Chat msg) {
        final Bundle args = new Bundle();
        args.putParcelable(ARG_MSG, msg);
        final MsgFragment fragment = new MsgFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mChat = getArguments().getParcelable(ARG_MSG);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.wechat_fragment_tab_first_msg, container, false);
        initView(view);
        return attachToSwipeBack(view);
    }

    private void initView(View view) {
        mToolbar = view.findViewById(R.id.toolbar);
        mBtnSend = view.findViewById(R.id.btn_send);
        mEtSend = view.findViewById(R.id.et_send);
        mRecy = view.findViewById(R.id.recy);

        mToolbar.setTitle(mChat.name);
        initToolbarNav(mToolbar);
    }

    @Override
    public void onEnterAnimationEnd(Bundle savedInstanceState) {
        super.onEnterAnimationEnd(savedInstanceState);
        // 入场动画结束后执行  优化,防动画卡顿

        _mActivity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        mRecy.setLayoutManager(new LinearLayoutManager(_mActivity));
        mRecy.setHasFixedSize(true);
        mAdapter = new MsgAdapter(_mActivity);
        mRecy.setAdapter(mAdapter);

        mBtnSend.setOnClickListener(v -> {
            final String str = mEtSend.getText().toString().trim();
            if (TextUtils.isEmpty(str)) {
                return;
            }

            mAdapter.addMsg(new Msg(str));
            mEtSend.setText("");
            mRecy.scrollToPosition(mAdapter.getItemCount() - 1);
        });

        mAdapter.addMsg(new Msg(mChat.message));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mRecy = null;
        _mActivity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        hideSoftInput();
    }
}