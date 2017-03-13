package com.zhangwx.swipestackview.UI;

import android.view.MotionEvent;
import android.view.View;

/**
 * Created by wensefu on 17-2-12.
 */
public interface ISwipeTouchHelper {

    boolean onInterceptTouchEvent(MotionEvent ev);

    boolean onTouchEvent(MotionEvent ev);

    void onCoverChanged(View cover);

    boolean isCoverIdle();
}
