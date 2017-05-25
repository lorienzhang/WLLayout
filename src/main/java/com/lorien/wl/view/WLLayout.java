package com.lorien.wl.view;

import android.content.Context;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListAdapter;
import android.widget.Scroller;

import com.lorien.wl.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by lorienzhang on 2017/5/20.
 */

public class WLLayout extends ViewGroup {

    public static final String TAG = WLLayout.class.getSimpleName();
    /**
     * ListView和WebView的边界处，如果duration为无效数据，就用默认值代替
     */
    public static final int DEFUALT_DURATION = 1000;
    /**
     * 位置状态，初始状态
     */
    public static final int STATE_POS_NONE = 0;
    /**
     * 位置状态：mCurrentPos == POS_START || mCurrentPos == POS_END
     * 该状态下Scroller要finish掉
     */
    public static final int STATE_POS_BOUNDARY = 1;
    /**
     * 位置状态: POS_START < mCurrentPos < POS_END
     */
    public static final int STATE_POS_MOVED = 2;
    /**
     * WebView和ListView交界处scroll的方向
     */
    public static final int SCROLL_ORIENTATION_DOWN = 0x10;
    /**
     * WebView和ListView交界处scroll的方向
     */
    public static final int SCROLL_ORIENTATION_UP = 0x11;
    /**
     * WebView和ListView交界处scroll的方向
     */
    public static final int SCROLL_ORIENTATION_NONE = 0x00;
    /**
     * mCurrent的起始位置
     */
    private static int POS_START;
    /**
     * mCurrent的结束位置
     */
    private static int POS_END;
    /**
     * 记录WebView和ListView交界处scroll方向
     */
    private int mScrollOrientation = SCROLL_ORIENTATION_NONE;
    /**
     * 位置状态位
     */
    private int mPositionState = STATE_POS_NONE;
    /**
     * 辅助变量，记录当前位置
     * 当POS_START < mCurrentPos < POS_END时，可以move
     */
    private int mCurrentPos;
    /**
     * 记录上一次位置
     */
    private int mLastPos;
    /**
     * 用户是否进行了拖拽
     */
    private boolean mIsBeingDragged = false;
    /**
     * ListView的header距离顶部的距离
     * 用来计算ListView快滑动到顶部的加速度
     */
    private int mHeaderDistance;
    /**
     * 速度追踪
     */
    private VelocityTracker mVelocityTracker;
    /**
     * Y方向上的速度
     */
    private int mVelocityY;
    /**
     * 控制位：只有手指抬起，才对ListView和WebView执行fling
     */
    private boolean mCanWLFling;
    /**
     * fling速度的最小值
     */
    private int mMinimumVelocity;
    /**
     * fling速度的最大值
     */
    private int mMaximumVelocity;
    /**
     * ListView是否有header view
     */
    private boolean mIsListViewHasHeader = false;
    /**
     * WebView内容的总高度
     * 用来计算fling时的minY和maxY
     */
    private int mWebViewContentHeight;
    /**
     * 记录手指最近一次和屏幕交互的时间
     * backup，当startTime == 0时，就用这个变量
     */
    private long mUpdateActionTime;
    /**
     * 子view, webView
     */
    private MyWebView mWebView;
    /**
     * 子view, ListView
     */
    private MyListView mListView;
    /**
     * 数据列表，记录WebView滑动过程中的top，time；
     * 用来计算当WebView快速滑动到底部时的加速度
     */
    private List<WVHolder> mWVholderList = new ArrayList<>();
    /**
     * 辅助变量， Scroller
     */
    private ScrollChecker mScroller;
    /**
     * WebView的高度
     */
    private int mWebViewHeight;
    /**
     * ListView的高度
     */
    private int mListHeight;
    /**
     * 触摸事件控制变量，mTempY
     */
    private int mTempY;
    /**
     * 触摸事件控制变量，mDownY
     */
    private int mDownY;
    /**
     * WebView的滚动监听
     */
    private WebViewScrollChangedCallback mWebViewScrollChangeCallback;
    /**
     * ListView的滚动监听
     */
    private AbsListView.OnScrollListener mListViewScrollLisenter;

    public WLLayout(Context context) {
        this(context, null);
    }

    public WLLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WLLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mScroller = new ScrollChecker(context);
        mMinimumVelocity = ViewConfiguration.get(context).getScaledMinimumFlingVelocity();
        mMaximumVelocity = ViewConfiguration.get(context).getScaledMaximumFlingVelocity();
    }


    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!isEnabled() || mWebView == null || mListView == null) {
            return super.dispatchTouchEvent(ev);
        }

        mCanWLFling = false;
        mVelocityY = getScrollVelocityY();
        // 当startTime == 0 就使用该时间计算加速度
        mUpdateActionTime = System.currentTimeMillis();
        // 当startTime == 0 就使用该distance计算加速度
        View header = mListView.getChildAt(0);
        if (header != null) {
            mHeaderDistance = Math.abs(header.getTop());
        }

        switch (ev.getAction()) {
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mIsBeingDragged) {
                    mIsBeingDragged = false;
                    final VelocityTracker velocityTracker = mVelocityTracker;
                    velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                    int initialVelocity = (int) velocityTracker.getYVelocity();
                    mVelocityY = initialVelocity;

                    Log.d(TAG, "ACTION_UP---velocity: " + initialVelocity);
                    if (Math.abs(initialVelocity) > mMinimumVelocity) {
                        flingWL(initialVelocity);
                    }
                }
                stopVelocityTracker();
                mCanWLFling = true;
                return super.dispatchTouchEvent(ev);

            case MotionEvent.ACTION_DOWN:
                mTempY = mDownY = (int) ev.getRawY();
                // down，如果处于fling状态，则finish
                if (!mScroller.isFinished()) {
                    mScroller.abortAnimation();
                }
                // 清空WVHolder列表
                mWVholderList.clear();
                return super.dispatchTouchEvent(ev);

            case MotionEvent.ACTION_MOVE:

                int moveY = (int) ev.getRawY();
                int deltaY = moveY - mTempY;
                mTempY = moveY;
                boolean moveDown = deltaY > 0;
                boolean canMoveDown = (!mListView.canScrollVertically(-1)
                        && mCurrentPos < POS_END)
                        || (mCurrentPos > POS_START
                                && mCurrentPos < POS_END);
                boolean moveUp = !moveDown;
                boolean canMoveUp = (!mWebView.canScrollVertically(1)
                        && mCurrentPos > POS_START)
                        || (mCurrentPos > POS_START
                                && mCurrentPos < POS_END);
                if (moveDown && !canMoveDown) {
                    mPositionState = STATE_POS_NONE;
                    return super.dispatchTouchEvent(ev);
                }
                if (moveUp && !canMoveUp) {
                    mPositionState = STATE_POS_NONE;
                    return super.dispatchTouchEvent(ev);
                }
                // 追踪velocity
                startVelocityTracker(ev);
                mVelocityY = getScrollVelocityY();

                if (moveDown || moveUp) {
                    mIsBeingDragged = true;
                    movePos(deltaY);
                    return true;
                }
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * 手指抬起后，滑动webview和listview
     *
     * @param velocityY
     */
    private void flingWL(int velocityY) {
        final boolean canFling = (mCurrentPos > POS_START || velocityY > 0)
                && (mCurrentPos < POS_END || velocityY < 0);
        if (canFling) {
            fling(velocityY);
        }
    }

    /**
     * fling the content(WebView && ListView)
     *
     * @param velocityY
     */
    private void fling(int velocityY) {
        if (getChildCount() > 0) {
            mScroller.tryToFling(velocityY);
        }
    }

    /**
     * 垂直方向上，移动WebView和ListView
     *
     * @param deltaY
     */
    private void movePos(int deltaY) {
        // 更新Scroll的方向
        if (deltaY < 0) {
            mScrollOrientation = SCROLL_ORIENTATION_UP;
        }
        if (deltaY > 0) {
            mScrollOrientation = SCROLL_ORIENTATION_DOWN;
        }

        mCurrentPos = mCurrentPos + deltaY;
        mCurrentPos = mCurrentPos < POS_START ? POS_START : mCurrentPos;
        mCurrentPos = mCurrentPos > POS_END ? POS_END : mCurrentPos;

        int distanceY = mCurrentPos - mLastPos;
        mLastPos = mCurrentPos;

        mWebView.offsetTopAndBottom(distanceY);
        mListView.offsetTopAndBottom(distanceY);
    }


    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mWebView = (MyWebView) getChildAt(0);
        mListView = (MyListView) getChildAt(1);

        if (mWebView == null || mListView == null) {
            // should throw a exception
            Log.d(TAG, "WLLayout must contain two child views: WebView and ListView");
        }

        mWebViewScrollChangeCallback = new WebViewScrollChangedCallback();
        mWebView.setOnScrollChangedCallback(mWebViewScrollChangeCallback);

        mListViewScrollLisenter = new ListViewScrollListener();
        mListView.setOnScrollListener(mListViewScrollLisenter);
    }

    /**
     * 对WebView和ListView进行测量
     *
     * @param widthMeasureSpec
     * @param heightMeasureSpec
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        // 请求子view测量
        mWebView.measure(widthMeasureSpec, heightMeasureSpec);
        mListView.measure(widthMeasureSpec, heightMeasureSpec);

        mWebViewHeight = mWebView.getMeasuredHeight();
        mListHeight = mListView.getMeasuredHeight();

        POS_START = 0;
        POS_END = mWebViewHeight;
        mLastPos = mCurrentPos = POS_END;
    }

    /**
     * 对WebView和ListView进行布局
     *
     * @param changed
     * @param l
     * @param t
     * @param r
     * @param b
     */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (changed) {
            int left = 0;
            int top = 0;
            int right = left + mWebView.getMeasuredWidth();
            int bottom = top + mWebView.getMeasuredHeight();
            mWebView.layout(left, top, right, bottom);

            left = 0;
            top = mWebViewHeight;
            right = left + mListView.getMeasuredWidth();
            bottom = top + mListView.getMeasuredHeight();
            mListView.layout(left, top, right, bottom);

            // 记录ListView是否有Header
            mIsListViewHasHeader = mListView.getHeaderViewsCount() > 0;
            Log.d(TAG, "mIsListViewHasHeader: " + mIsListViewHasHeader);
        }
    }

    @Override
    public void computeScroll() {
        mScroller.run();
    }

    /**
     * 求出ListView中所有子view高度的总和
     * @return
     */
    private int getListViewContentHeight() {
        int height = 0;
        if (mListView != null) {
            ListAdapter adapter = mListView.getAdapter();
            View view = mListView.getChildAt(1);
            int itemHeight = view.getMeasuredHeight();
            int count = adapter.getCount();
            height += itemHeight * count;
            Log.d(TAG, "ALL item height: " + height);
            // 如果有header，计算header的高度
            View header = mListView.findViewById(R.id.header_view);
            if (header != null) {
                height += header.getMeasuredHeight();
            }
            Log.d(TAG, "getListViewContentHeight(): " + height);

        }
        return height;
    }

    /**
     * Scroller finish后，
     * 根据当前位置(mCurrentPos)对ListView或者WebView进行快速滑动
     *
     * @param velocity
     */
    private void onScrollCheckerFinish(int velocity) {
        if (mCurrentPos == POS_START) {
            // KITKAT 4.4
            mListView.flingY(velocity);
        }
        if (mCurrentPos == POS_END) {
            mWebView.flingScroll(0, -velocity);
        }
    }

    /**
     * 开始追踪速度
     * @param event
     */
    private void startVelocityTracker(MotionEvent event) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);
    }

    /**
     * 停止速度追踪
     */
    private void stopVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    /**
     * 获取追踪到的Y方向的速度
     *
     * @return
     */
    private int getScrollVelocityY() {
        if (mVelocityTracker != null) {
            // 设置单位, 1000表示1s内移动的像素值
            mVelocityTracker.computeCurrentVelocity(1000);
            int velocityY = (int) mVelocityTracker.getYVelocity();
            return velocityY;
        } else {
            return 0;
        }
    }

    /**
     * Scroller控制器
     */
    class ScrollChecker implements Runnable {

        private Scroller mScroller;
        /**
         * 上一次Y位置
         */
        private int mLastFlingY;

        public ScrollChecker(Context context) {
            mScroller = new Scroller(context, null, true);
        }

        @Override
        public void run() {

            // computeScrollOffset()用于计算当前滑动偏移量
            boolean finish = !mScroller.computeScrollOffset()
                    || mScroller.isFinished()
                    || (mPositionState == STATE_POS_BOUNDARY);
            int curY = mScroller.getCurrY();
            int deltaY = curY - mLastFlingY;

            if (!finish) {
                mLastFlingY = curY;
                // 移动ListView和WebView
                movePos(deltaY);
                // 重绘，确保调用computeScroll()
                WLLayout.this.invalidate();
            } else {
                finish();
            }

            // 更新偏移状态标志位,目的是取消结尾处多余的动画
            if ((mCurrentPos == POS_START || mCurrentPos == POS_END)
                    && mPositionState == STATE_POS_MOVED) {
                mPositionState = STATE_POS_BOUNDARY;
            }
            if (mCurrentPos > POS_START && mCurrentPos < POS_END) {
                mPositionState = STATE_POS_MOVED;
            }
        }

        private void finish() {
            if (mPositionState == STATE_POS_BOUNDARY) {
                int leftVelocity = (int) mScroller.getCurrVelocity();
                Log.d(TAG, "finish(), leftVelocity: " + leftVelocity);
                onScrollCheckerFinish(leftVelocity);
            }
            mScroller.abortAnimation();
            mLastFlingY = 0;
            mPositionState = STATE_POS_NONE;
        }

        public void tryToFling(int velocityY) {
            if (!isFinished()) {
                return;
            }
            mLastFlingY = 0;

            Log.d(TAG, "tryToFling---velocityY: " + velocityY);

            // down，确定滑动的最大值
//            int maxY = POS_END - mCurrentPos + mWebViewContentHeight;
            int maxY = Integer.MAX_VALUE;
            // up, 确定滑动的最小值
//            int minY = mCurrentPos + getListViewContentHeight();
            int minY = Integer.MAX_VALUE;
            Log.d(TAG, "maxY: " + maxY + " ,minY: " + minY);

            mScroller.fling(0, 0, 0, velocityY, 0, 0, -minY, maxY);
            WLLayout.this.invalidate();
        }

        public boolean isFinished() {
            return mScroller.isFinished();
        }

        public void abortAnimation() {
            mScroller.forceFinished(true);
        }
    }

    /**
     * WebView滚动监听
     *
     * 负责处理: WebView是否滚动到底部，计算WebView滚动底部时的加速度计算。
     * 如果手指抬起，还要对WebView和ListView进行整体快速滑动
     */
    class WebViewScrollChangedCallback implements MyWebView.OnScrollChangedCallback {
        @Override
        public void onScroll(int left, int top, int dx, int dy, boolean canScrollUp) {

            // 记录WebView内容的高度，用于计算maxY
            if (top > mWebViewContentHeight) {
                mWebViewContentHeight = top;
            }

            // 只有手指抬起，才记录
            if (mCanWLFling) {
                WVHolder wvHolder = new WVHolder(System.currentTimeMillis(), top);
                mWVholderList.add(wvHolder);
            }
            /**
             * canScrollUp: WebView是否到达最底端
             * mCanWLFling: 手指是否抬起
             */
            if (!canScrollUp
                    && mCanWLFling) {
                // 计算当前WebView的加速度
                int initialVelocity = calcWebViewBottomVelocity();
                if (Math.abs(initialVelocity) > mMinimumVelocity) {
                    mPositionState = STATE_POS_NONE;
                    Log.d(TAG, "calcWebViewVelocity: " + initialVelocity);
                    flingWL(initialVelocity);
                }
            }
        }

        /**
         * 根据集合中记录的最新数据，计算出velocity，最多使用10个数据
         *
         * @return
         */
        private int calcWebViewBottomVelocity() {
            int velocity;

            if (mWVholderList.size() > 1) {
                int size = mWVholderList.size();
                int index = size - 1;
                int endTop = mWVholderList.get(index).curTop;
                long startTime = mWVholderList.get(size - 1).time;
                int count = 0;
                for (; index > 0 && count < 10; index--, count++);
                int distance = mWVholderList.get(index).curTop - endTop;
                long totalTime = (startTime - mWVholderList.get(index).time);
                velocity = (int) ((distance * 1.0f / totalTime) * 1000);
            } else {
                velocity = -1000;
            }

            return velocity;
        }
    }

    /**
     * ListView的滚动监听
     *
     * 负责处理: 当ListView滚到顶部时，加速度的计算.
     * 如果是快速滑动，需要对WebView和ListView进行整体快速滑动
     */
    class ListViewScrollListener implements AbsListView.OnScrollListener {

        // 记录当前的ScrollState
        int mScrollState;
        // canScrollDown: ListView是否已经滑到顶部
        boolean canScrollDown = false;
        // 计算加速度的起始时间
        long startTime = 0;

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            mScrollState = scrollState;
            if (scrollState == SCROLL_STATE_IDLE) {
                mPositionState = STATE_POS_NONE;
            }
        }

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            if (mPositionState != STATE_POS_NONE) {
                return;
            }
            // ListView有header view
            if (mIsListViewHasHeader) {
                if (firstVisibleItem == 1) {
                    startTime = System.currentTimeMillis();
                }
            } else {
                // ListView没有Header View
                // TODO
            }

            // ListView是否已经滑到顶部
            canScrollDown = mListView.canScrollVertically(-1);
            /**
             * mCanWLFling: 手指是否已经离开屏幕
             * canScrollDown: ListView是否已经滑到顶部
             * mScrollOrientation != SCROLL_ORIENTATION_UP: 方向不能从WebView进入ListView
             */
            if (mCanWLFling
                    && !canScrollDown
                    && mScrollState != SCROLL_ORIENTATION_UP
                    && mScrollState != SCROLL_ORIENTATION_NONE) {
                mPositionState = STATE_POS_NONE;
                long endTime = System.currentTimeMillis();
                // 如果没有记录到startTime，使用mUpdateActionTime
                long duration;
                if (startTime == 0) {
                    duration = endTime - mUpdateActionTime;
                } else {
                    duration = endTime - startTime;
                }
                int initialVelocity = calcListViewTopVelocity(startTime, duration, mIsListViewHasHeader);
                startTime = 0;
                Log.d(TAG, "### calcListViewTopVelocity(): " + initialVelocity);
                if (Math.abs(initialVelocity) > mMinimumVelocity) {
                    flingWL(initialVelocity);
                }
            }
        }

        /**
         * 如果ListView有Header：
         *          velocityY = (headerHeight + firstItemHeight) / (endTime - startTime)
         * 如果ListView没有Header：
         *          velocityY = (firstItemHeight + secondItemHeight) / (endTime - startTime)
         *
         *  @param startTime 仅用于判断
         */
        private int calcListViewTopVelocity(long startTime, long duration, boolean isListViewHasHeader) {
            int velocityY = 888;
            int distance = 0;
            // ListView有HeaderView
            if (isListViewHasHeader) {
                View header = mListView.findViewById(R.id.header_view);
                // header的高度
                if (header != null) {
                    distance += header.getMeasuredHeight();
                }
                // 第一个item的高度
                View firstItemView = mListView.getChildAt(1);
                // 第一个item的高度的一半
                if (firstItemView != null) {
                    distance += (firstItemView.getMeasuredHeight() / 2);
                }
                // 如果大于10s，或者是0s，认为是无效数据
                if (duration > 10 * 1000 || duration == 0) {
                    duration = DEFUALT_DURATION;
                }
                // 如果没有记录到起始时间，使用backup distance
                if (startTime == 0) {
                    distance = Math.abs(mHeaderDistance);
                    Log.d(TAG, "### backup distance: " + distance);
                }
                Log.d(TAG, "### duration: " + duration + " ,distance: " + distance);
                velocityY = (int) (((distance * 1.0f) / duration) * 1000);
            } else {
                // ListView没有HeaderView
                // TODO
            }

            return  velocityY;
        }
    }

    /**
     * 数据结构
     * 用于计算WebView滑到底部时的加速度
     */
    class WVHolder {
        long time;  // millisSecs
        int curTop; // 当前scroll的top
        public WVHolder(long time, int curTop) {
            this.time = time;
            this.curTop = curTop;
        }
    }


}
