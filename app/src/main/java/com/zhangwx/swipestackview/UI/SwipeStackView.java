package com.zhangwx.swipestackview.UI;

import android.content.Context;
import android.database.Observable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by zhangwx on 2017/3/13.
 */

public class SwipeStackView extends FrameLayout {

    public static final String TAG = "SwipeStackView";

    public SwipeStackView(Context context) {
        super(context);
    }

    public SwipeStackView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setChildrenDrawingOrderEnabled(true);
    }

    public SwipeStackView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public static boolean DEBUG = true;

    /**
     * 左滑
     */
    public static final int SWIPE_LEFT = 1;

    /**
     * 右滑
     */
    public static final int SWIPE_RIGHT = 1 << 1;

    /**
     * 上滑
     */
    public static final int SWIPE_UP = 1 << 2;

    /**
     * 下滑
     */
    public static final int SWIPE_DOWN = 1 << 3;

    /**
     * 任意方向滑动
     */
    public static final int SWIPE_ALL = SWIPE_LEFT | SWIPE_RIGHT | SWIPE_UP | SWIPE_DOWN;

    private int mSwipeDirection = SWIPE_ALL;

    private Adapter mAdapter;

    /**
     * 静止时最多可以看到的卡片数
     */
    private int mMaxVisibleCnt = 3;

    /**
     * 同时最多add到控件的子view数
     */
    private int mLayerCnt = 4;

    /**
     * 层叠效果高度
     */
    private int mLayerEdgeHeight = 24;

    private InnerDataObserver mDataObserver;
    private boolean mHasRegisteredObserver;

    private static final float SCALE_FACTOR = 0.8f;
    private float mScaleFactor = SCALE_FACTOR;

    private static final float ALPHA_FACTOR = 0.6f;
    private float mAlphaFactor = ALPHA_FACTOR;

    private static final float SWIPE_TO_DISMISS_FACTOR = .3f;
    private float mDismissFactor = SWIPE_TO_DISMISS_FACTOR;

    //卡片消失时的透明度
    private static final float DISMISS_ALPHA = 0.3f;
    private float mDismissAlpha = DISMISS_ALPHA;

    //滑动时的最大旋转角度
    private float mMaxRotation = 8;

    private float[] mScaleArray;
    private float[] mAlphaArray;
    private float[] mTranslationYArray;

    private ISwipeTouchHelper mTouchHelper;

    private List<OnCardSwipedListener> mCardSwipedListenrs;

    private boolean mNeedAdjustChild;
    private boolean mSecondCardFadeInFaster = false;
    private Runnable mPendingTask;


    public interface OnCardSwipedListener {

        void onCardDismiss(int direction);

        void onChildrenChanged();
    }

    public void addOnCardSwipedListener(OnCardSwipedListener listener) {
        if (mCardSwipedListenrs == null) {
            mCardSwipedListenrs = new ArrayList<>();
            mCardSwipedListenrs.add(listener);
        } else if (!mCardSwipedListenrs.contains(listener)) {
            mCardSwipedListenrs.add(listener);
        }
    }

    public void removeOnCardSwipedListener(OnCardSwipedListener listener) {
        if (mCardSwipedListenrs != null && mCardSwipedListenrs.contains(listener)) {
            mCardSwipedListenrs.remove(listener);
        }
    }

    /**
     * 第二张卡片从半透明到不透明的速度加快
     *
     * @param faster
     */
    public void setSecondCardFadeInFaster(boolean faster) {
        mSecondCardFadeInFaster = faster;
    }

    public boolean isSecondCardFadeInFaster() {
        return mSecondCardFadeInFaster;
    }

    @Override
    public void addView(View child) {
        throw new UnsupportedOperationException("addView(View) is not supported");
    }

    @Override
    public void addView(View child, int index) {
        throw new UnsupportedOperationException("addView(View, int) is not supported");
    }

    @Override
    public void removeView(View child) {
        throw new UnsupportedOperationException("removeView(View) is not supported");
    }

    @Override
    public void removeViewAt(int index) {
        throw new UnsupportedOperationException("removeViewAt(int) is not supported");
    }

    @Override
    public void removeAllViews() {
        throw new UnsupportedOperationException("removeAllViews() is not supported");
    }

    /**
     * 设置可以滑动的方向,支持以下值:<br/>
     * {@link #SWIPE_ALL},<br/>
     * {@link #SWIPE_LEFT},<br/>
     * {@link #SWIPE_RIGHT},<br/>
     * {@link #SWIPE_UP},<br/>
     * {@link #SWIPE_DOWN},<br/>
     *
     * @param direction 方向
     */
    public void addSwipeDirection(int direction) {
        mSwipeDirection |= direction;
    }

    /**
     * 设置某个方向不可以滑动,支持以下值:<br/>
     * <p>
     * {@link #SWIPE_ALL},<br/>
     * {@link #SWIPE_LEFT},<br/>
     * {@link #SWIPE_RIGHT},<br/>
     * {@link #SWIPE_UP},<br/>
     * {@link #SWIPE_DOWN},<br/>
     *
     * @param direction 方向
     */
    public void removeSwipeDirection(int direction) {
        mSwipeDirection &= ~direction;
    }

    public int getSwipeDirection() {
        return mSwipeDirection;
    }

    float getMaxRotation() {
        return mMaxRotation;
    }

    public float getDismissDistance() {
        return mDismissFactor * getWidth();
    }

    public void setMaxRotatin(float rotation) {
        mMaxRotation = rotation;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        log(TAG, "onLayout: ");
        if (mNeedAdjustChild) {
            mNeedAdjustChild = false;
            adjustChildren();
        }
    }

    void adjustChildren() {
        final int cnt = getChildCount();
        if (cnt == 0) {
            return;
        }
        int layerIndex = 0;
        mScaleArray = new float[cnt];
        mAlphaArray = new float[cnt];
        mTranslationYArray = new float[cnt];
        mScaleArray[0] = 1;
        mAlphaArray[0] = 1;
        mTranslationYArray[0] = 0;
        for (int i = 0; i < cnt; i++) {
            final View child = getChildAt(i);
            final int half_childHeight = child.getMeasuredHeight() / 2;
            final float scale = 1 - layerIndex * (1 - mScaleFactor);
            mScaleArray[i] = scale;
            child.setScaleX(scale);
            child.setScaleY(scale);
            final float alpha = 1 - layerIndex * (1 - mAlphaFactor);
            mAlphaArray[i] = alpha;
            child.setAlpha(alpha);
            float translationY = half_childHeight * (1 - scale) + mLayerEdgeHeight * layerIndex;
            mTranslationYArray[i] = translationY;
            child.setTranslationY(translationY);

            if (i == 0 && mTouchHelper != null) {
                mTouchHelper.onCoverChanged(child);
                log(TAG, "adjustChildren: onCoverChanged");
            }
            if (layerIndex < mMaxVisibleCnt - 1) {
                layerIndex++;
            }
        }
    }

    void onCoverStatusChanged(boolean idle) {
        if (idle) {
            if (mPendingTask != null) {
                mPendingTask.run();
                mPendingTask = null;
            }
        }
    }

    void onCardDismissed(int direction) {
        if (mCardSwipedListenrs != null) {
            for (OnCardSwipedListener listener : mCardSwipedListenrs) {
                listener.onCardDismiss(direction);
            }
        }
    }

    void onCoverScrolled(float progress) {
        final int cnt = getChildCount();
        if (mScaleArray == null || mScaleArray.length < cnt) {
            if (DEBUG) {
                throw new RuntimeException("onCoverScrolled: mScaleArray does not match");
            } else {
                Log.e(TAG, "onCoverScrolled: mScaleArray does not match");
                return;
            }
        }
        float preScale;
        float preAlpha;
        float preTranslationY;
        float targetScale;
        float targetAlpha;
        float targetTranslationY;
        float progressScale;
        for (int i = 1; i < cnt; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != View.GONE) {
                if (mScaleArray != null) {
                    preScale = mScaleArray[i];
                    targetScale = mScaleArray[i - 1];
                    progressScale = preScale + (targetScale - preScale) * progress;
                    child.setScaleX(progressScale);
                    child.setScaleY(progressScale);
                }

                if (mAlphaArray != null) {
                    preAlpha = mAlphaArray[i];
                    targetAlpha = mAlphaArray[i - 1];
                    if (i == 1 && isSecondCardFadeInFaster()) {
                        child.setAlpha(preAlpha + 2 * (targetAlpha - preAlpha) * progress);
                    } else {
                        child.setAlpha(preAlpha + (targetAlpha - preAlpha) * progress);
                    }
                }

                if (mTranslationYArray != null) {
                    preTranslationY = mTranslationYArray[i];
                    targetTranslationY = mTranslationYArray[i - 1];
                    child.setTranslationY(preTranslationY + (targetTranslationY - preTranslationY) * progress);
                }
            }
        }
    }

    @Override
    protected int getChildDrawingOrder(int childCount, int i) {
        return childCount - 1 - i;
    }


    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        safeRegisterObserver();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        safeUnRegisterObserver();
    }

    private void safeUnRegisterObserver() {
        if (mAdapter != null && mDataObserver != null && mHasRegisteredObserver) {
            mAdapter.unregisterDataObserver(mDataObserver);
            mHasRegisteredObserver = false;
        }
    }

    private void safeRegisterObserver() {
        safeUnRegisterObserver();
        if (mDataObserver == null) {
            mDataObserver = new InnerDataObserver();
        }
        mAdapter.registerDataObserver(mDataObserver);
        mHasRegisteredObserver = true;
    }

    private LayoutParams getDefaultLayoutParams() {
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        return lp;
    }

    private void initChildren() {
        int cnt = mAdapter == null ? 0 : mAdapter.getCount();
        if (cnt == 0) {
            removeAllViewsInLayout();
            if (mTouchHelper != null) {
                mTouchHelper.onCoverChanged(null);
            }
        } else {
            removeAllViewsInLayout();
            if (mTouchHelper != null) {
                mTouchHelper.onCoverChanged(null);
            }
            cnt = Math.min(cnt, mLayerCnt);
            for (int i = 0; i < cnt; i++) {
                addViewInLayout(mAdapter.getView(i, null, this), -1, getDefaultLayoutParams(), false);
            }
            mNeedAdjustChild = true;
            log(TAG, "initChildren: mNeedAdjustChild set to true and requestLayout");
            requestLayout();
            log(TAG, "initChildren: requestLayout was called");
        }
        post(new Runnable() {
            @Override
            public void run() {
                if (mCardSwipedListenrs != null) {
                    for (OnCardSwipedListener listener : mCardSwipedListenrs) {
                        listener.onChildrenChanged();
                    }
                }
            }
        });
    }

    public void setAdapter(Adapter adapter) {
        safeUnRegisterObserver();
        mAdapter = adapter;
        safeRegisterObserver();
        initChildren();
    }

    private class InnerDataObserver extends CardDataObserver {

        @Override
        public void onDataSetChanged() {
            super.onDataSetChanged();
            if (mTouchHelper != null && !mTouchHelper.isCoverIdle()) {
                mPendingTask = new Runnable() {
                    @Override
                    public void run() {
                        initChildren();
                    }
                };
            } else {
                initChildren();
            }
        }

        @Override
        public void onItemInserted(int position) {
            super.onItemInserted(position);

        }

        @Override
        public void onItemRemoved(int position) {
            super.onItemRemoved(position);
            log(TAG, "onItemRemoved: position=" + position);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        final int action = ev.getAction() & MotionEvent.ACTION_MASK;
        log("SwipeTouchHelper", "dispatchTouchEvent: action=" + action);
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mTouchHelper == null) {
            mTouchHelper = new SwipeTouchHelper(this);
        }
        return mTouchHelper.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return mTouchHelper.onTouchEvent(ev);
    }

    public static abstract class Adapter {

        private final CardDataObservable mObservable = new CardDataObservable();

        public void registerDataObserver(CardDataObserver observer) {
            mObservable.registerObserver(observer);
        }

        public void unregisterDataObserver(CardDataObserver observer) {
            mObservable.unregisterObserver(observer);
        }

        public abstract int getCount();

        public abstract View getView(int position, View convertView, ViewGroup parent);

        public final void notifyDataSetChanged() {
            mObservable.notifyDataSetChanged();
        }

        public final void notifyItemInserted(int position) {
            mObservable.notifyItemInserted(position);
        }

        public final void notifyItemRemoved(int position) {
            mObservable.notifyItemRemoved(position);
        }
    }

    public static abstract class CardDataObserver {

        public void onDataSetChanged() {

        }

        public void onItemInserted(int position) {

        }

        public void onItemRemoved(int position) {

        }
    }

    static class CardDataObservable extends Observable<CardDataObserver> {

        public void notifyDataSetChanged() {
            for (int i = mObservers.size() - 1; i >= 0; i--) {
                mObservers.get(i).onDataSetChanged();
            }
        }

        public void notifyItemInserted(int position) {
            for (int i = mObservers.size() - 1; i >= 0; i--) {
                mObservers.get(i).onItemInserted(position);
            }
        }

        public void notifyItemRemoved(int position) {
            for (int i = mObservers.size() - 1; i >= 0; i--) {
                mObservers.get(i).onItemRemoved(position);
            }
        }
    }

    private static void log(String tag, String msg) {
        if (DEBUG) {
            Log.d(tag, msg);
        }
    }


}
