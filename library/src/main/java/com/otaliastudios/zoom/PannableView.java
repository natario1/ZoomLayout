package com.otaliastudios.vitae.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.AnimationUtils;
import android.widget.EdgeEffect;
import android.widget.FrameLayout;
import android.widget.OverScroller;

import java.util.List;


/**
 * A copy of platform {@link android.widget.HorizontalScrollView} that can actually
 * scroll in two dimensions (that is, panning).
 */
@Deprecated
class PannableView extends FrameLayout {

    private static final int ANIMATED_SCROLL_GAP = 250;
    private static final float MAX_SCROLL_FACTOR = 0.5f;

    private static final String TAG = PannableView.class.getSimpleName();

    private long mLastScroll;

    private final Rect mTempRect = new Rect();
    private OverScroller mScroller;
    private EdgeEffect mEdgeGlowLeft;
    private EdgeEffect mEdgeGlowRight;
    private EdgeEffect mEdgeGlowTop;
    private EdgeEffect mEdgeGlowBottom;

    /**
     * Position of the last motion event.
     */
    private int mLastMotionX;
    private int mLastMotionY;

    /**
     * True when the layout has changed but the traversal has not come through yet.
     * Ideally the view hierarchy would keep track of this for us.
     */
    private boolean mIsLayoutDirty = true;

    /**
     * The child to give focus to in the event that a child has requested focus while the
     * layout is dirty. This prevents the scroll from being wrong if the child has not been
     * laid out before requesting focus.
     */
    private View mChildToScrollTo = null;

    /**
     * True if the user is currently dragging this ScrollView around. This is
     * not the same as 'is being flinged', which can be checked by
     * mScroller.isFinished() (flinging begins when the user lifts his finger).
     */
    private boolean mIsBeingDragged = false;

    /**
     * Determines speed during touch scrolling
     */
    private VelocityTracker mVelocityTracker;

    /**
     * Whether arrow scrolling is animated.
     */
    private boolean mSmoothScrollingEnabled = true;

    private boolean mClipToPadding = true;

    private int mTouchSlop;
    private int mMinimumVelocity;
    private int mMaximumVelocity;

    private int mOverscrollDistance;
    private int mOverflingDistance;


    /**
     * ID of the active pointer. This is used to retain consistency during
     * drags/flings if multiple pointers are used.
     */
    private int mActivePointerId = INVALID_POINTER;

    /**
     * Sentinel value for no current active pointer.
     * Used by {@link #mActivePointerId}.
     */
    private static final int INVALID_POINTER = -1;


    public PannableView(Context context) {
        this(context, null);
    }

    public PannableView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PannableView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mScroller = new OverScroller(getContext());
        setFocusable(true);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setWillNotDraw(false);
        final ViewConfiguration configuration = ViewConfiguration.get(context);
        mTouchSlop = configuration.getScaledTouchSlop();
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
        mOverscrollDistance = configuration.getScaledOverscrollDistance();
        mOverflingDistance = configuration.getScaledOverflingDistance();
        setClipToPadding(false);
    }

    @Override
    public void setClipToPadding(boolean clipToPadding) {
        super.setClipToPadding(clipToPadding);
        mClipToPadding = clipToPadding;
    }

    @Override
    protected float getLeftFadingEdgeStrength() {
        if (getChildCount() == 0) {
            return 0.0f;
        }

        final int length = getHorizontalFadingEdgeLength();
        if (getScrollX() < length) {
            return getScrollX() / (float) length;
        }

        return 1.0f;
    }

    @Override
    protected float getRightFadingEdgeStrength() {
        if (getChildCount() == 0) {
            return 0.0f;
        }

        final int length = getHorizontalFadingEdgeLength();
        final int rightEdge = getWidth() - getPaddingRight();
        final int span = (int) getChildRight(getChildAt(0)) - getScrollX() - rightEdge;
        if (span < length) {
            return span / (float) length;
        }

        return 1.0f;
    }

    /**
     * @return The maximum amount this scroll view will scroll in response to
     *   an arrow event.
     */
    public int getMaxScrollAmountX() {
        return (int) (MAX_SCROLL_FACTOR * (getRight() - getLeft()));
    }

    public int getMaxScrollAmountY() {
        return (int) (MAX_SCROLL_FACTOR * (getBottom() - getTop()));
    }

    private void checkChild() {
        if (getChildCount() > 0) {
            throw new IllegalStateException(TAG + " can host only one direct child");
        }
    }

    @Override
    public void addView(View child) {
        checkChild();
        super.addView(child);
    }

    @Override
    public void addView(View child, int index) {
        checkChild();
        super.addView(child, index);
    }

    @Override
    public void addView(View child, ViewGroup.LayoutParams params) {
        checkChild();
        super.addView(child, params);
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        checkChild();
        super.addView(child, index, params);
    }

    protected int getChildWidth(View child) {
        // Log.e(TAG, "ChildWidth:" + child.getWidth());
        return child.getWidth();
    }

    protected int getChildHeight(View child) {
        // Log.e(TAG, "ChildHeight:" + child.getHeight());
        return child.getHeight();
    }

    private int getChildTop(View child) {
        return child.getTop();
    }

    private int getChildLeft(View child) {
        return child.getLeft();
    }

    private int getChildBottom(View child) {
        return getChildTop(child) + getChildHeight(child);
    }

    private int getChildRight(View child) {
        return getChildLeft(child) + getChildWidth(child);
    }

    private boolean canScrollX() {
        View child = getChildAt(0);
        if (child != null) {
            return getWidth() < getChildWidth(child) + getPaddingLeft() + getPaddingRight();
        }
        return false;
    }

    private boolean canScrollY() {
        View child = getChildAt(0);
        if (child != null) {
            return getHeight() < getChildHeight(child) + getPaddingTop() + getPaddingBottom();
        }
        return false;
    }

    /**
     * @return Whether arrow scrolling will animate its transition.
     */
    public boolean isSmoothScrollingEnabled() {
        return mSmoothScrollingEnabled;
    }

    /**
     * Set whether arrow scrolling will animate its transition.
     * @param smoothScrollingEnabled whether arrow scrolling will animate its transition
     */
    public void setSmoothScrollingEnabled(boolean smoothScrollingEnabled) {
        mSmoothScrollingEnabled = smoothScrollingEnabled;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Let the focused view and/or our descendants get the key first
        return super.dispatchKeyEvent(event) || executeKeyEvent(event);
    }

    /**
     * You can call this function yourself to have the scroll view perform
     * scrolling from a key event, just as if the event had been dispatched to
     * it by the view hierarchy.
     *
     * @param event The key event to execute.
     * @return Return true if the event was handled, else false.
     */
    public boolean executeKeyEvent(KeyEvent event) {
        mTempRect.setEmpty();

        boolean handled = false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (!canScrollX()) return passFocus();
                    if (!event.isAltPressed()) {
                        handled = arrowScroll(View.FOCUS_LEFT);
                    } else {
                        handled = fullScroll(View.FOCUS_LEFT);
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (!canScrollX()) return passFocus();
                    if (!event.isAltPressed()) {
                        handled = arrowScroll(View.FOCUS_RIGHT);
                    } else {
                        handled = fullScroll(View.FOCUS_RIGHT);
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (!canScrollY()) return passFocus();
                    if (!event.isAltPressed()) {
                        handled = arrowScroll(View.FOCUS_UP);
                    } else {
                        handled = fullScroll(View.FOCUS_UP);
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (!canScrollY()) return passFocus();
                    if (!event.isAltPressed()) {
                        handled = arrowScroll(View.FOCUS_DOWN);
                    } else {
                        handled = fullScroll(View.FOCUS_DOWN);
                    }
                    break;
            }
        }

        return handled;
    }

    private boolean passFocus() {
        if (isFocused()) {
            View currentFocused = findFocus();
            if (currentFocused == this) currentFocused = null;
            View nextFocused = FocusFinder.getInstance().findNextFocus(this,
                    currentFocused, View.FOCUS_FORWARD);
            return nextFocused != null && nextFocused != this &&
                    nextFocused.requestFocus(View.FOCUS_FORWARD);
        }
        return false;
    }

    private boolean inChild(int x, int y) {
        if (getChildCount() > 0) {
            final int scrollX = getScrollX();
            final View child = getChildAt(0);
            return !(y < getChildTop(child)
                    || y >= getChildBottom(child)
                    || x < getChildLeft(child) - scrollX
                    || x >= getChildRight(child) - scrollX);
        }
        return false;
    }

    private void initOrResetVelocityTracker() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        } else {
            mVelocityTracker.clear();
        }
    }

    private void initVelocityTrackerIfNotExists() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
    }

    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (disallowIntercept) {
            recycleVelocityTracker();
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        /*
         * This method JUST determines whether we want to intercept the motion.
         * If we return true, onMotionEvent will be called and we do the actual
         * scrolling there.
         */

        /*
        * Shortcut the most recurring case: the user is in the dragging
        * state and he is moving his finger.  We want to intercept this
        * motion.
        */
        final int action = ev.getAction();
        if ((action == MotionEvent.ACTION_MOVE) && (mIsBeingDragged)) {
            return true;
        }

        if (super.onInterceptTouchEvent(ev)) {
            return true;
        }

        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_MOVE: {
                /*
                 * mIsBeingDragged == false, otherwise the shortcut would have caught it. Check
                 * whether the user has moved far enough from his original down touch.
                 */

                /*
                * Locally do absolute value. mLastMotionX is set to the x value
                * of the down event.
                */
                final int activePointerId = mActivePointerId;
                if (activePointerId == INVALID_POINTER) {
                    // If we don't have a valid id, the touch down wasn't on content.
                    break;
                }

                final int pointerIndex = ev.findPointerIndex(activePointerId);
                if (pointerIndex == -1) {
                    Log.e(TAG, "Invalid pointerId=" + activePointerId
                            + " in onInterceptTouchEvent");
                    break;
                }

                final int x = (int) ev.getX(pointerIndex);
                final int y = (int) ev.getY(pointerIndex);
                final int deltaX = x - mLastMotionX;
                final int deltaY = y - mLastMotionY;
                final int diff = (int) Math.sqrt(Math.pow(deltaX, 2) + Math.pow(deltaY, 2));
                if (diff > mTouchSlop) {
                    mIsBeingDragged = true;
                    mLastMotionX = x;
                    mLastMotionY = y;
                    initVelocityTrackerIfNotExists();
                    mVelocityTracker.addMovement(ev);
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                }
                break;
            }

            case MotionEvent.ACTION_DOWN: {
                final int x = (int) ev.getX();
                final int y = (int) ev.getY();
                if (!inChild(x, y)) {
                    mIsBeingDragged = false;
                    recycleVelocityTracker();
                    break;
                }

                /*
                 * Remember location of down touch.
                 * ACTION_DOWN always refers to pointer index 0.
                 */
                mLastMotionX = x;
                mLastMotionY = y;
                mActivePointerId = ev.getPointerId(0);

                initOrResetVelocityTracker();
                mVelocityTracker.addMovement(ev);

                /*
                * If being flinged and user touches the screen, initiate drag;
                * otherwise don't.  mScroller.isFinished should be false when
                * being flinged.
                */
                mIsBeingDragged = !mScroller.isFinished();
                break;
            }

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                /* Release the drag */
                mIsBeingDragged = false;
                mActivePointerId = INVALID_POINTER;
                if (mScroller.springBack(getScrollX(), getScrollY(),
                        0, getHorizontalScrollRange(),
                        0, getVerticalScrollRange())) {
                    postInvalidateOnAnimation();
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN: {
                final int index = ev.getActionIndex();
                mLastMotionX = (int) ev.getX(index);
                mLastMotionY = (int) ev.getY(index);
                mActivePointerId = ev.getPointerId(index);
                break;
            }
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                mLastMotionX = (int) ev.getX(ev.findPointerIndex(mActivePointerId));
                mLastMotionY = (int) ev.getY(ev.findPointerIndex(mActivePointerId));
                break;
        }

        /*
        * The only time we want to intercept motion events is if we are in the
        * drag mode.
        */
        return mIsBeingDragged;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        initVelocityTrackerIfNotExists();
        mVelocityTracker.addMovement(ev);

        final int action = ev.getAction();

        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                if (getChildCount() == 0) {
                    return false;
                }
                if ((mIsBeingDragged = !mScroller.isFinished())) {
                    final ViewParent parent = getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                }

                /*
                 * If being flinged and user touches, stop the fling. isFinished
                 * will be false if being flinged.
                 */
                if (!mScroller.isFinished()) {
                    mScroller.abortAnimation();
                }

                // Remember where the motion event started
                mLastMotionX = (int) ev.getX();
                mLastMotionY = (int) ev.getY();
                mActivePointerId = ev.getPointerId(0);
                break;
            }
            case MotionEvent.ACTION_MOVE:
                final int activePointerIndex = ev.findPointerIndex(mActivePointerId);
                if (activePointerIndex == -1) {
                    Log.e(TAG, "Invalid pointerId=" + mActivePointerId + " in onTouchEvent");
                    break;
                }

                final int x = (int) ev.getX(activePointerIndex);
                final int y = (int) ev.getY(activePointerIndex);
                int deltaX = mLastMotionX - x;
                int deltaY = mLastMotionY - y;
                int delta = (int) Math.sqrt(Math.pow(deltaX, 2) + Math.pow(deltaY, 2));
                if (!mIsBeingDragged && delta > mTouchSlop) {
                    final ViewParent parent = getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                    mIsBeingDragged = true;

                    /* TODO: Removing this. Can't deal with it easily.
                    if (deltaX > 0) {
                        deltaX -= mTouchSlop;
                    } else {
                        deltaX += mTouchSlop;
                    } */
                }
                if (mIsBeingDragged) {
                    // Scroll to follow the motion event
                    mLastMotionX = x;
                    mLastMotionY = y;

                    final int oldX = getScrollX();
                    final int oldY = getScrollY();
                    final int rangeX = getHorizontalScrollRange();
                    final int rangeY = getVerticalScrollRange();
                    final int overscrollMode = getOverScrollMode();
                    final boolean canOverscrollX = overscrollMode == OVER_SCROLL_ALWAYS ||
                            (overscrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && rangeX > 0);
                    final boolean canOverscrollY = overscrollMode == OVER_SCROLL_ALWAYS ||
                            (overscrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && rangeY > 0);

                    // Calling overScrollBy will call onOverScrolled, which
                    // calls onScrollChanged if applicable.
                    if (overScrollBy(deltaX, deltaY, getScrollX(), getScrollY(), rangeX, rangeY,
                            mOverscrollDistance, mOverscrollDistance, true)) {
                        // Break our velocity if we hit a scroll barrier.
                        mVelocityTracker.clear();
                    }

                    if (canOverscrollX) {
                        final int pulledToX = oldX + deltaX;
                        if (pulledToX < 0) {
                            mEdgeGlowLeft.onPull((float) deltaX / getWidth());
                            // Displacement (API21) 1.f - ev.getY(activePointerIndex) / getHeight());
                            if (!mEdgeGlowRight.isFinished()) mEdgeGlowRight.onRelease();
                        } else if (pulledToX > rangeX) {
                            mEdgeGlowRight.onPull((float) deltaX / getWidth());
                            // Displacement (API21) ev.getY(activePointerIndex) / getHeight());
                            if (!mEdgeGlowLeft.isFinished()) mEdgeGlowLeft.onRelease();
                        }
                        if (!mEdgeGlowLeft.isFinished() || !mEdgeGlowRight.isFinished()) {
                            postInvalidateOnAnimation();
                        }
                    }

                    if (canOverscrollY) {
                        final int pulledToY = oldY + deltaY;
                        if (pulledToY < 0) {
                            mEdgeGlowTop.onPull((float) deltaY / getHeight());
                            if (!mEdgeGlowBottom.isFinished()) mEdgeGlowBottom.onRelease();
                        } else if (pulledToY > rangeY) {
                            mEdgeGlowBottom.onPull((float) deltaY / getHeight());
                            if (!mEdgeGlowTop.isFinished()) mEdgeGlowTop.onRelease();
                        }
                        if (!mEdgeGlowTop.isFinished() || !mEdgeGlowBottom.isFinished()) {
                            postInvalidateOnAnimation();
                        }
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mIsBeingDragged) {
                    final VelocityTracker velocityTracker = mVelocityTracker;
                    velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                    float initialVelocityX = velocityTracker.getXVelocity(mActivePointerId);
                    float initialVelocityY = velocityTracker.getYVelocity(mActivePointerId);
                    float meanVelocity = (0.5f * (Math.abs(initialVelocityX) + Math.abs(initialVelocityY)));
                    if (getChildCount() > 0) {
                        if (meanVelocity > mMinimumVelocity) {
                            fling((int) -initialVelocityX, (int) -initialVelocityY);
                        } else {
                            if (mScroller.springBack(getScrollX(), getScrollY(),
                                    0, getHorizontalScrollRange(),
                                    0, getVerticalScrollRange())) {
                                postInvalidateOnAnimation();
                            }
                        }
                    }

                    mActivePointerId = INVALID_POINTER;
                    mIsBeingDragged = false;
                    recycleVelocityTracker();

                    if (mEdgeGlowLeft != null) {
                        mEdgeGlowLeft.onRelease();
                        mEdgeGlowRight.onRelease();
                        mEdgeGlowTop.onRelease();
                        mEdgeGlowBottom.onRelease();
                    }
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mIsBeingDragged && getChildCount() > 0) {
                    if (mScroller.springBack(getScrollX(), getScrollY(),
                            0, getHorizontalScrollRange(),
                            0, getVerticalScrollRange())) {
                        postInvalidateOnAnimation();
                    }
                    mActivePointerId = INVALID_POINTER;
                    mIsBeingDragged = false;
                    recycleVelocityTracker();

                    if (mEdgeGlowLeft != null) {
                        mEdgeGlowLeft.onRelease();
                        mEdgeGlowRight.onRelease();
                        mEdgeGlowTop.onRelease();
                        mEdgeGlowBottom.onRelease();
                    }
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
        }
        return true;
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = (ev.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >>
                MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            // TODO: Make this decision more intelligent.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mLastMotionX = (int) ev.getX(newPointerIndex);
            mLastMotionY = (int) ev.getY(newPointerIndex);
            mActivePointerId = ev.getPointerId(newPointerIndex);
            if (mVelocityTracker != null) {
                mVelocityTracker.clear();
            }
        }
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return true;
    }

    @Override
    protected void onOverScrolled(int scrollX, int scrollY,
                                  boolean clampedX, boolean clampedY) {
        // Treat animating scrolls differently; see #computeScroll() for why.
        if (!mScroller.isFinished()) {
            final int oldX = getScrollX();
            final int oldY = getScrollY();
            setScrollX(scrollX);
            setScrollY(scrollY);
            onScrollChanged(scrollX, scrollY, oldX, oldY);
            if (clampedX || clampedY) {
                mScroller.springBack(scrollX, scrollY,
                        0, getHorizontalScrollRange(),
                        0, getVerticalScrollRange());
            }
        } else {
            super.scrollTo(scrollX, scrollY);
        }

        awakenScrollBars();
    }


    private int getHorizontalScrollRange() {
        int scrollRange = 0;
        if (getChildCount() > 0) {
            View child = getChildAt(0);
            scrollRange = Math.max(0,
                    (int) getChildWidth(child) - (getWidth() - getPaddingLeft() - getPaddingRight()));
        }
        return scrollRange;
    }

    private int getVerticalScrollRange() {
        int scrollRange = 0;
        if (getChildCount() > 0) {
            View child = getChildAt(0);
            scrollRange = Math.max(0,
                    (int) getChildHeight(child) - (getHeight() - getPaddingTop() - getPaddingBottom()));
        }
        return scrollRange;
    }

    /**
     * <p>
     * Finds the next focusable component that fits in this View's bounds
     * (excluding fading edges) pretending that this View's left is located at
     * the parameter left.
     * </p>
     *
     * @param leftFocus          look for a candidate is the one at the left of the bounds
     *                           if leftFocus is true, or at the right of the bounds if leftFocus
     *                           is false
     * @param left               the left offset of the bounds in which a focusable must be
     *                           found (the fading edge is assumed to start at this position)
     * @param preferredFocusable the View that has highest priority and will be
     *                           returned if it is within my bounds (null is valid)
     * @return the next focusable component in the bounds or null if none can be found
     */
    private View findFocusableViewInMyBounds(final boolean leftFocus,
                                             final int left, View preferredFocusable, boolean horizontal) {
        /*
         * The fading edge's transparent side should be considered for focus
         * since it's mostly visible, so we divide the actual fading edge length
         * by 2.
         */
        final int fadingEdgeLength = horizontal ? (getHorizontalFadingEdgeLength() / 2) : (getVerticalFadingEdgeLength() / 2);
        final int dim = horizontal ? getWidth() : getHeight();
        final int leftWithoutFadingEdge = left + fadingEdgeLength;
        final int rightWithoutFadingEdge = left + dim - fadingEdgeLength;

        if (preferredFocusable != null) {
            final int preferredFocusableLeft = horizontal ? preferredFocusable.getLeft() : preferredFocusable.getTop();
            final int preferredFocusableRight = horizontal ? preferredFocusable.getRight() : preferredFocusable.getBottom();
            if ((preferredFocusableLeft < rightWithoutFadingEdge) &&
                    (preferredFocusableRight > leftWithoutFadingEdge)) {
                return preferredFocusable;
            }
        }

        return findFocusableViewInBounds(leftFocus, leftWithoutFadingEdge,
                rightWithoutFadingEdge, horizontal);
    }

    /**
     * <p>
     * Finds the next focusable component that fits in the specified bounds.
     * </p>
     *
     * @param leftFocus look for a candidate is the one at the left of the bounds
     *                  if leftFocus is true, or at the right of the bounds if
     *                  leftFocus is false
     * @param left      the left offset of the bounds in which a focusable must be
     *                  found
     * @param right     the right offset of the bounds in which a focusable must
     *                  be found
     * @return the next focusable component in the bounds or null if none can
     *         be found
     */
    private View findFocusableViewInBounds(boolean leftFocus, int left, int right, boolean horizontal) {

        List<View> focusables = getFocusables(View.FOCUS_FORWARD);
        View focusCandidate = null;

        /*
         * A fully contained focusable is one where its left is below the bound's
         * left, and its right is above the bound's right. A partially
         * contained focusable is one where some part of it is within the
         * bounds, but it also has some part that is not within bounds.  A fully contained
         * focusable is preferred to a partially contained focusable.
         */
        boolean foundFullyContainedFocusable = false;

        int count = focusables.size();
        for (int i = 0; i < count; i++) {
            View view = focusables.get(i);
            int viewLeft = horizontal ? view.getLeft() : view.getTop();
            int viewRight = horizontal ? view.getRight() : view.getBottom();

            if (left < viewRight && viewLeft < right) {
                /*
                 * the focusable is in the target area, it is a candidate for
                 * focusing
                 */

                final boolean viewIsFullyContained = (left < viewLeft) &&
                        (viewRight < right);

                if (focusCandidate == null) {
                    /* No candidate, take this one */
                    focusCandidate = view;
                    foundFullyContainedFocusable = viewIsFullyContained;
                } else {
                    int focusCandidateLeft = horizontal ? focusCandidate.getLeft() : focusCandidate.getTop();
                    int focusCandidateRight = horizontal ? focusCandidate.getRight() : focusCandidate.getBottom();
                    final boolean viewIsCloserToBoundary =
                            (leftFocus && viewLeft < focusCandidateLeft) ||
                                    (!leftFocus && viewRight > focusCandidateRight);

                    if (foundFullyContainedFocusable) {
                        if (viewIsFullyContained && viewIsCloserToBoundary) {
                            /*
                             * We're dealing with only fully contained views, so
                             * it has to be closer to the boundary to beat our
                             * candidate
                             */
                            focusCandidate = view;
                        }
                    } else {
                        if (viewIsFullyContained) {
                            /* Any fully contained view beats a partially contained view */
                            focusCandidate = view;
                            foundFullyContainedFocusable = true;
                        } else if (viewIsCloserToBoundary) {
                            /*
                             * Partially contained view beats another partially
                             * contained view if it's closer
                             */
                            focusCandidate = view;
                        }
                    }
                }
            }
        }

        return focusCandidate;
    }

    /**
     * <p>Handles scrolling in response to a "page up/down" shortcut press. This
     * method will scroll the view by one page left or right and give the focus
     * to the leftmost/rightmost component in the new visible area. If no
     * component is a good candidate for focus, this scrollview reclaims the
     * focus.</p>
     *
     * @param direction the scroll direction: {@link View#FOCUS_LEFT}
     *                  to go one page left or {@link View#FOCUS_RIGHT}
     *                  to go one page right
     * @return true if the key event is consumed by this method, false otherwise
     */
    public boolean pageScroll(int direction) {
        boolean horizontal = direction == View.FOCUS_RIGHT || direction == View.FOCUS_LEFT;
        boolean positive = horizontal ? direction == View.FOCUS_RIGHT : direction == View.FOCUS_DOWN;
        int dim = horizontal ? getWidth() : getHeight();
        int scroll = horizontal ? getScrollX() : getScrollY();

        int dimStart;
        if (positive) {
            dimStart = scroll + dim;
            int count = getChildCount();
            if (count > 0) {
                View view = getChildAt(0);
                float viewEnd = horizontal ? getChildRight(view) : getChildBottom(view);
                if (dimStart + dim > viewEnd) {
                    dimStart = (int) viewEnd - dim;
                }
            }
        } else {
            dimStart = scroll - dim;
            if (dimStart < 0) {
                dimStart = 0;
            }
        }

        if (horizontal) {
            mTempRect.left = dimStart;
            mTempRect.right = mTempRect.left + dim;
            return scrollAndFocus(direction, mTempRect.left, mTempRect.right);
        } else {
            mTempRect.top = dimStart;
            mTempRect.bottom = mTempRect.top + dim;
            return scrollAndFocus(direction, mTempRect.top, mTempRect.bottom);

        }
    }

    /**
     * <p>Handles scrolling in response to a "home/end" shortcut press. This
     * method will scroll the view to the left or right and give the focus
     * to the leftmost/rightmost component in the new visible area. If no
     * component is a good candidate for focus, this scrollview reclaims the
     * focus.</p>
     *
     * @param direction the scroll direction: {@link View#FOCUS_LEFT}
     *                  to go the left of the view or {@link View#FOCUS_RIGHT}
     *                  to go the right
     * @return true if the key event is consumed by this method, false otherwise
     */
    public boolean fullScroll(int direction) {
        boolean horizontal = direction == View.FOCUS_RIGHT || direction == View.FOCUS_LEFT;
        boolean positive = horizontal ? direction == View.FOCUS_RIGHT : direction == View.FOCUS_DOWN;
        int dim = horizontal ? getWidth() : getHeight();

        if (horizontal) {
            mTempRect.left = 0;
            mTempRect.right = dim;
        } else {
            mTempRect.top = 0;
            mTempRect.bottom = dim;
        }

        if (positive) {
            int count = getChildCount();
            if (count > 0) {
                View view = getChildAt(0);
                if (horizontal) {
                    mTempRect.right = (int) getChildRight(view);
                    mTempRect.left = mTempRect.right - dim;
                } else {
                    mTempRect.bottom = (int) getChildBottom(view);
                    mTempRect.top = mTempRect.bottom - dim;
                }
            }
        }

        if (horizontal) {
            return scrollAndFocus(direction, mTempRect.left, mTempRect.right);
        } else {
            return scrollAndFocus(direction, mTempRect.top, mTempRect.bottom);
        }
    }

    /**
     * <p>Scrolls the view to make the area defined by <code>left</code> and
     * <code>right</code> visible. This method attempts to give the focus
     * to a component visible in this area. If no component can be focused in
     * the new visible area, the focus is reclaimed by this scrollview.</p>
     *
     * @param direction the scroll direction: {@link View#FOCUS_LEFT}
     *                  to go left {@link View#FOCUS_RIGHT} to right
     * @return true if the key event is consumed by this method, false otherwise
     */
    private boolean scrollAndFocus(int direction, int start, int end) {

        boolean horizontal = direction == View.FOCUS_RIGHT || direction == View.FOCUS_LEFT;
        boolean negative = horizontal ? direction == View.FOCUS_LEFT : direction == View.FOCUS_UP;

        boolean handled = true;

        int width = getWidth();
        int containerLeft = getScrollX();
        int containerRight = containerLeft + getWidth();
        int containerTop = getScrollY();
        int containerBottom = containerTop + getHeight();

        View newFocused = findFocusableViewInBounds(negative, start, end, horizontal);
        if (newFocused == null) {
            newFocused = this;
        }

        if (horizontal) {
            if (start >= containerLeft && end <= containerRight) {
                handled = false;
            } else {
                int delta = negative ? (start - containerLeft) : (end - containerRight);
                doScroll(delta, 0);
            }
        } else {
            if (start >= containerTop && end <= containerBottom) {
                handled = false;
            } else {
                int delta = negative ? (start - containerTop) : (end - containerBottom);
                doScroll(0, delta);
            }
        }

        if (newFocused != findFocus()) newFocused.requestFocus(direction);
        return handled;
    }

    /**
     * Handle scrolling in response to a left or right arrow click.
     *
     * @param direction The direction corresponding to the arrow key that was
     *                  pressed
     * @return True if we consumed the event, false otherwise
     */
    public boolean arrowScroll(int direction) {
        boolean horizontal = direction == View.FOCUS_RIGHT || direction == View.FOCUS_LEFT;
        boolean positive = horizontal ? direction == View.FOCUS_RIGHT : direction == View.FOCUS_DOWN;

        View currentFocused = findFocus();
        if (currentFocused == this) currentFocused = null;
        View nextFocused = FocusFinder.getInstance().findNextFocus(this, currentFocused, direction);

        final int maxJump = horizontal ? getMaxScrollAmountX() : getMaxScrollAmountY();

        if (nextFocused != null && isWithinDeltaOfScreen(nextFocused, maxJump, horizontal)) {
            nextFocused.getDrawingRect(mTempRect);
            offsetDescendantRectToMyCoords(nextFocused, mTempRect);
            int scrollDelta = computeScrollDeltaToGetChildRectOnScreen(mTempRect, horizontal);
            if (horizontal) {
                doScroll(scrollDelta, 0);
            } else {
                doScroll(0, scrollDelta);
            }
            nextFocused.requestFocus(direction);
        } else {
            // no new focus
            int scrollDelta = maxJump;
            int scroll = horizontal ? getScrollX() : getScrollY();

            if ((!positive) && scroll < scrollDelta) {
                scrollDelta = scroll;
            } else if (positive && getChildCount() > 0) {
                View child = getChildAt(0);
                int daEnd = horizontal ? (int) getChildRight(child) : (int) getChildBottom(child);
                int screenEnd = scroll + (horizontal ? getWidth() : getHeight());
                if (daEnd - screenEnd < maxJump) {
                    scrollDelta = daEnd - screenEnd;
                }
            }
            if (scrollDelta == 0) {
                return false;
            }

            if (horizontal) {
                doScroll(positive ? scrollDelta : -scrollDelta, 0);
            } else {
                doScroll(0, positive ? scrollDelta : -scrollDelta);
            }
        }

        if (currentFocused != null && currentFocused.isFocused()
                && isOffScreen(currentFocused, horizontal)) {
            // previously focused item still has focus and is off screen, give
            // it up (take it back to ourselves)
            // (also, need to temporarily force FOCUS_BEFORE_DESCENDANTS so we are
            // sure to
            // get it)
            final int descendantFocusability = getDescendantFocusability();  // save
            setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
            requestFocus();
            setDescendantFocusability(descendantFocusability);  // restore
        }
        return true;
    }

    /**
     * @return whether the descendant of this scroll view is scrolled off
     *  screen.
     */
    private boolean isOffScreen(View descendant, boolean horizontal) {
        return !isWithinDeltaOfScreen(descendant, 0, horizontal);
    }

    /**
     * @return whether the descendant of this scroll view is within delta
     *  pixels of being on the screen.
     */
    private boolean isWithinDeltaOfScreen(View descendant, int delta, boolean horizontal) {
        descendant.getDrawingRect(mTempRect);
        offsetDescendantRectToMyCoords(descendant, mTempRect);

        if (horizontal) {
            return (mTempRect.right + delta) >= getScrollX()
                    && (mTempRect.left - delta) <= (getScrollX() + getWidth());
        } else {
            return (mTempRect.bottom + delta) >= getScrollY()
                    && (mTempRect.top - delta) <= (getScrollY() + getHeight());
        }
    }

    /**
     * Smooth scroll by a X delta
     *
     * @param deltaX the number of pixels to scroll by on the X axis
     */
    private void doScroll(int deltaX, int deltaY) {
        if (deltaX != 0 || deltaY != 0) {
            if (mSmoothScrollingEnabled) {
                smoothScrollBy(deltaX, deltaY);
            } else {
                scrollBy(deltaX, deltaY);
            }
        }
    }

    /**
     * Like {@link View#scrollBy}, but scroll smoothly instead of immediately.
     *
     * @param dx the number of pixels to scroll by on the X axis
     * @param dy the number of pixels to scroll by on the Y axis
     */
    public final void smoothScrollBy(int dx, int dy) {
        if (getChildCount() == 0) {
            // Nothing to do.
            return;
        }
        long duration = AnimationUtils.currentAnimationTimeMillis() - mLastScroll;
        if (duration > ANIMATED_SCROLL_GAP) {
            final int width = getWidth() - getPaddingRight() - getPaddingLeft();
            final int height = getHeight() - getPaddingTop() - getPaddingBottom();
            final int right = (int) getChildWidth(getChildAt(0));
            final int bottom = (int) getChildHeight(getChildAt(0));
            final int maxX = Math.max(0, right - width);
            final int maxY = Math.max(0, bottom - height);
            final int scrollX = getScrollX();
            final int scrollY = getScrollY();
            dx = Math.max(0, Math.min(scrollX + dx, maxX)) - scrollX;
            dy = Math.max(0, Math.min(scrollY + dy, maxY)) - scrollY;
            mScroller.startScroll(scrollX, scrollY, dx, dy);
            postInvalidateOnAnimation();
        } else {
            if (!mScroller.isFinished()) {
                mScroller.abortAnimation();
            }
            scrollBy(dx, dy);
        }
        mLastScroll = AnimationUtils.currentAnimationTimeMillis();
    }

    /**
     * Like {@link #scrollTo}, but scroll smoothly instead of immediately.
     *
     * @param x the position where to scroll on the X axis
     * @param y the position where to scroll on the Y axis
     */
    public final void smoothScrollTo(int x, int y) {
        smoothScrollBy(x - getScrollX(), y - getScrollY());
    }

    /**
     * <p>The scroll range of a scroll view is the overall width of all of its
     * children.</p>
     */
    @Override
    protected int computeHorizontalScrollRange() {
        final int count = getChildCount();
        final int contentWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        if (count == 0) {
            return contentWidth;
        }

        int scrollRange = (int) getChildRight(getChildAt(0));
        final int scrollX = getScrollX();
        final int overscrollRight = Math.max(0, scrollRange - contentWidth);
        if (scrollX < 0) {
            scrollRange -= scrollX;
        } else if (scrollX > overscrollRight) {
            scrollRange += scrollX - overscrollRight;
        }

        return scrollRange;
    }

    @Override
    protected int computeVerticalScrollRange() {
        final int count = getChildCount();
        final int contentHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        if (count == 0) {
            return contentHeight;
        }

        int scrollRange = (int) getChildBottom(getChildAt(0));
        final int scrollY = getScrollY();
        final int overscrollBottom = Math.max(0, scrollRange - contentHeight);
        if (scrollY < 0) {
            scrollRange -= scrollY;
        } else if (scrollY > overscrollBottom) {
            scrollRange += scrollY - overscrollBottom;
        }

        return scrollRange;
    }

    @Override
    protected int computeHorizontalScrollOffset() {
        return Math.max(0, super.computeHorizontalScrollOffset());
    }

    @Override
    protected int computeVerticalScrollOffset() {
        return Math.max(0, super.computeVerticalScrollOffset());
    }

    @Override
    protected void measureChild(View child, int parentWidthMeasureSpec, int parentHeightMeasureSpec) {
        final int horizontalPadding = getPaddingLeft() + getPaddingRight();
        final int verticalPadding = getPaddingTop() + getPaddingBottom();
        final int width = Math.max(0, MeasureSpec.getSize(parentWidthMeasureSpec) - horizontalPadding);
        final int height = Math.max(0, MeasureSpec.getSize(parentHeightMeasureSpec) - verticalPadding);
        final int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.UNSPECIFIED);
        final int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED);
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    @Override
    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed,
                                           int parentHeightMeasureSpec, int heightUsed) {
        final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
        final int usedWidthTotal = widthUsed + getPaddingLeft() + getPaddingRight() + lp.leftMargin + lp.rightMargin;
        final int usedHeightTotal = heightUsed + getPaddingTop() + getPaddingBottom() + lp.topMargin + lp.bottomMargin;
        final int width = Math.max(0, MeasureSpec.getSize(parentWidthMeasureSpec) - usedWidthTotal);
        final int height = Math.max(0, MeasureSpec.getSize(parentHeightMeasureSpec) - usedHeightTotal);
        final int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.UNSPECIFIED);
        final int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED);
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            // This is called at drawing time by ViewGroup.  We don't want to
            // re-show the scrollbars at this point, which scrollTo will do,
            // so we replicate most of scrollTo here.
            //
            //         It's a little odd to call onScrollChanged from inside the drawing.
            //
            //         It is, except when you remember that computeScroll() is used to
            //         animate scrolling. So unless we want to defer the onScrollChanged()
            //         until the end of the animated scrolling, we don't really have a
            //         choice here.
            //
            //         I agree.  The alternative, which I think would be worse, is to post
            //         something and tell the subclasses later.  This is bad because there
            //         will be a window where mScrollX/Y is different from what the app
            //         thinks it is.
            //
            int oldX = getScrollX();
            int oldY = getScrollY();
            int x = mScroller.getCurrX();
            int y = mScroller.getCurrY();

            if (oldX != x || oldY != y) {
                final int rangeX = getHorizontalScrollRange();
                final int rangeY = getVerticalScrollRange();
                final int overscrollMode = getOverScrollMode();
                final boolean canOverscrollX = overscrollMode == OVER_SCROLL_ALWAYS ||
                        (overscrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && rangeX > 0);
                final boolean canOverscrollY = overscrollMode == OVER_SCROLL_ALWAYS ||
                        (overscrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && rangeY > 0);

                overScrollBy(x - oldX, y - oldY,
                        oldX, oldY, rangeX, rangeY,
                        mOverflingDistance, mOverflingDistance, false);
                onScrollChanged(getScrollX(), getScrollY(), oldX, oldY);

                if (canOverscrollX) {
                    if (x < 0 && oldX >= 0) {
                        mEdgeGlowLeft.onAbsorb((int) mScroller.getCurrVelocity());
                    } else if (x > rangeX && oldX <= rangeX) {
                        mEdgeGlowRight.onAbsorb((int) mScroller.getCurrVelocity());
                    }
                }

                if (canOverscrollY) {
                    if (y < 0 && oldY >= 0) {
                        mEdgeGlowTop.onAbsorb((int) mScroller.getCurrVelocity());
                    } else if (y > rangeY && oldY <= rangeY) {
                        mEdgeGlowBottom.onAbsorb((int) mScroller.getCurrVelocity());
                    }
                }
            }

            if (!awakenScrollBars()) {
                postInvalidateOnAnimation();
            }
        }
    }

    /**
     * Scrolls the view to the given child.
     *
     * @param child the View to scroll to
     */
    private void scrollToChild(View child) {
        child.getDrawingRect(mTempRect);

        /* Offset from child's local coordinates to ScrollView coordinates */
        offsetDescendantRectToMyCoords(child, mTempRect);

        int scrollDeltaX = computeScrollDeltaToGetChildRectOnScreen(mTempRect, true);
        int scrollDeltaY = computeScrollDeltaToGetChildRectOnScreen(mTempRect, false);

        if (scrollDeltaX != 0 || scrollDeltaY != 0) {
            scrollBy(scrollDeltaX, scrollDeltaY);
        }
    }

    /**
     * If rect is off screen, scroll just enough to get it (or at least the
     * first screen size chunk of it) on screen.
     *
     * @param rect      The rectangle.
     * @param immediate True to scroll immediately without animation
     * @return true if scrolling was performed
     */
    private boolean scrollToChildRect(Rect rect, boolean immediate) {
        final int deltaX = computeScrollDeltaToGetChildRectOnScreen(rect, true);
        final int deltaY = computeScrollDeltaToGetChildRectOnScreen(rect, false);
        final boolean scroll = deltaX != 0 || deltaY != 0;
        if (scroll) {
            if (immediate) {
                scrollBy(deltaX, deltaY);
            } else {
                smoothScrollBy(deltaX, deltaY);
            }
        }
        return scroll;
    }

    /**
     * Compute the amount to scroll in the X direction in order to get
     * a rectangle completely on the screen (or, if taller than the screen,
     * at least the first screen size chunk of it).
     *
     * @param rect The rect.
     * @return The scroll delta.
     */
    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect, boolean horizontal) {
        if (getChildCount() == 0) return 0;

        int dim = horizontal ? getWidth() : getHeight();
        int screenStart = horizontal ? getScrollX() : getScrollY();
        int screenEnd = screenStart + dim;
        int fadingEdge = horizontal ? getHorizontalFadingEdgeLength() : getVerticalFadingEdgeLength();

        // leave room for left fading edge as long as rect isn't at very left
        int rectStart = horizontal ? rect.left : rect.top;
        int rectEnd = horizontal ? rect.right : rect.bottom;
        int rectDim = horizontal ? rect.width() : rect.height();
        if (rectStart > 0) {
            screenStart += fadingEdge;
        }

        // leave room for right fading edge as long as rect isn't at very right
        View child = getChildAt(0);
        int childDim = horizontal ? (int) getChildWidth(child) : (int) getChildHeight(child);
        if (rectEnd < childDim) {
            screenEnd -= fadingEdge;
        }

        int scrollDelta = 0;

        if (rectEnd > screenEnd && rectStart > screenStart) {
            // need to move right to get it in view: move right just enough so
            // that the entire rectangle is in view (or at least the first
            // screen size chunk).

            if (rectDim > dim) {
                // just enough to get screen size chunk on
                scrollDelta += (rectStart - screenStart);
            } else {
                // get entire rect at right of screen
                scrollDelta += (rectEnd - screenEnd);
            }

            // make sure we aren't scrolling beyond the end of our content
            int end = horizontal ? (int) getChildRight(child) : (int) getChildBottom(child);
            int distanceToEnd = end - screenEnd;
            scrollDelta = Math.min(scrollDelta, distanceToEnd);

        } else if (rectStart < screenStart && rectEnd < screenEnd) {
            // need to move right to get it in view: move right just enough so that
            // entire rectangle is in view (or at least the first screen
            // size chunk of it).

            if (rectDim > dim) {
                // screen size chunk
                scrollDelta -= (screenEnd - rectEnd);
            } else {
                // entire rect at left
                scrollDelta -= (screenStart - rectStart);
            }

            // make sure we aren't scrolling any further than the left our content
            scrollDelta = Math.max(scrollDelta, horizontal ? -getScrollX() : -getScrollY());
        }
        return scrollDelta;
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        if (focused != null /* && focused.getRevealOnFocusHint() */) {
            if (!mIsLayoutDirty) {
                scrollToChild(focused);
            } else {
                // The child may not be laid out yet, we can't compute the scroll yet
                mChildToScrollTo = focused;
            }
        }
        super.requestChildFocus(child, focused);
    }


    /**
     * When looking for focus in children of a scroll view, need to be a little
     * more careful not to give focus to something that is scrolled off screen.
     *
     * This is more expensive than the default {@link ViewGroup}
     * implementation, otherwise this behavior might have been made the default.
     */
    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {

        // convert from forward / backward notation to up / down / left / right
        // (ugh).
        if (direction == View.FOCUS_FORWARD) {
            direction = View.FOCUS_DOWN;
        } else if (direction == View.FOCUS_BACKWARD) {
            direction = View.FOCUS_UP;
        }
        boolean horizontal = direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT;

        final View nextFocus = previouslyFocusedRect == null ?
                FocusFinder.getInstance().findNextFocus(this, null, direction) :
                FocusFinder.getInstance().findNextFocusFromRect(this,
                        previouslyFocusedRect, direction);

        if (nextFocus == null) {
            return false;
        }

        if (isOffScreen(nextFocus, horizontal)) {
            return false;
        }

        return nextFocus.requestFocus(direction, previouslyFocusedRect);
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rectangle,
                                                 boolean immediate) {
        // offset into coordinate space of this scroll view
        rectangle.offset(child.getLeft() - child.getScrollX(),
                child.getTop() - child.getScrollY());

        return scrollToChildRect(rectangle, immediate);
    }

    @Override
    public void requestLayout() {
        mIsLayoutDirty = true;
        super.requestLayout();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int childWidth = 0;
        int childHeight = 0;
        // int childMargins = 0;
        if (getChildCount() > 0) {
            childWidth = getChildAt(0).getMeasuredWidth();
            childHeight = getChildAt(0).getMeasuredHeight();
            // LayoutParams childParams = (LayoutParams) getChildAt(0).getLayoutParams();
            // childMargins = childParams.leftMargin + childParams.rightMargin;
        }
        // final int available = r - l - getPaddingLeft() - getPaddingRight() - childMargins;
        // final boolean forceLeftGravity = (childWidth > available);
        // layoutChildren(l, t, r, b, forceLeftGravity);

        // Replacing with super call...
        super.onLayout(changed, l, t, r, b);

        mIsLayoutDirty = false;
        // Give a child focus if it needs it
        if (mChildToScrollTo != null && isViewDescendantOf(mChildToScrollTo, this)) {
            scrollToChild(mChildToScrollTo);
        }
        mChildToScrollTo = null;

        int scrollX = getScrollX();
        int scrollY = getScrollY();
        if (!isLaidOut()) {
            final int scrollRangeX = Math.max(0, childWidth - (r - l - getPaddingLeft() - getPaddingRight()));
            final int scrollRangeY = Math.max(0, childHeight - (b - t - getPaddingTop() - getPaddingBottom()));

            if (getResources().getConfiguration().getLayoutDirection() == LAYOUT_DIRECTION_RTL) {
                scrollX = scrollRangeX - scrollX;
            } // mScrollX default value is "0" for LTR

            // Don't forget to clamp
            if (scrollX > scrollRangeX) {
                scrollX = scrollRangeX;
            } else if (scrollX < 0) {
                scrollX = 0;
            }

            if (scrollY > scrollRangeY) {
                scrollY = scrollRangeY;
            } else if (scrollY < 0) {
                scrollY = 0;
            }
        }

        scrollTo(scrollX, scrollY);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        View currentFocused = findFocus();
        if (null == currentFocused || this == currentFocused)
            return;

        if (isWithinDeltaOfScreen(currentFocused, getRight() - getLeft(), true) &&
                isWithinDeltaOfScreen(currentFocused, getBottom() - getTop(), false)) {
            currentFocused.getDrawingRect(mTempRect);
            offsetDescendantRectToMyCoords(currentFocused, mTempRect);
            int scrollDeltaX = computeScrollDeltaToGetChildRectOnScreen(mTempRect, true);
            int scrollDeltaY = computeScrollDeltaToGetChildRectOnScreen(mTempRect, false);
            doScroll(scrollDeltaX, scrollDeltaY);
        }
    }

    /**
     * Return true if child is a descendant of parent, (or equal to the parent).
     */
    private static boolean isViewDescendantOf(View child, View parent) {
        if (child == parent) {
            return true;
        }

        final ViewParent theParent = child.getParent();
        return (theParent instanceof ViewGroup) && isViewDescendantOf((View) theParent, parent);
    }

    /**
     * Fling the scroll view
     *
     * @param velocityX The initial velocity in the X direction. Positive
     *                  numbers mean that the finger/cursor is moving down the screen,
     *                  which means we want to scroll towards the left.
     */
    public void fling(int velocityX, int velocityY) {
        if (getChildCount() > 0) {
            int width = getWidth() - getPaddingRight() - getPaddingLeft();
            int height = getHeight() - getPaddingTop() - getPaddingBottom();
            int right = (int) getChildRight(getChildAt(0));
            int bottom = (int) getChildBottom(getChildAt(0));

            mScroller.fling(getScrollX(), getScrollY(),
                    velocityX, velocityY,
                    0, Math.max(0, right - width),
                    0, Math.max(0, bottom - height),
                    width/2, height/2);

            boolean horizontal = Math.abs(velocityX) > Math.abs(velocityY);
            boolean negative = horizontal ? velocityX > 0 : velocityY > 0;
            int finalValue = horizontal ? mScroller.getFinalX() : mScroller.getFinalY();
            View currentFocused = findFocus();
            View newFocused = findFocusableViewInMyBounds(negative, finalValue, currentFocused, horizontal);

            if (newFocused == null) {
                newFocused = this;
            }

            if (newFocused != currentFocused) {
                int direction =
                        (horizontal && negative) ? View.FOCUS_LEFT :
                        (horizontal) ? View.FOCUS_RIGHT :
                        (negative) ? View.FOCUS_UP : View.FOCUS_DOWN;
                newFocused.requestFocus(direction);
            }

            postInvalidateOnAnimation();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>This version also clamps the scrolling to the bounds of our child.
     */
    @Override
    public void scrollTo(int x, int y) {
        // we rely on the fact the View.scrollBy calls scrollTo.
        if (getChildCount() > 0) {
            View child = getChildAt(0);
            x = clamp(x, getWidth() - getPaddingRight() - getPaddingLeft(), (int) getChildWidth(child));
            y = clamp(y, getHeight() - getPaddingBottom() - getPaddingTop(), (int) getChildHeight(child));
            if (x != getScrollX() || y != getScrollY()) {
                super.scrollTo(x, y);
            }
        }
    }

    @Override
    public void setOverScrollMode(int mode) {
        if (mode != OVER_SCROLL_NEVER) {
            if (mEdgeGlowLeft == null) {
                Context context = getContext();
                mEdgeGlowLeft = new EdgeEffect(context);
                mEdgeGlowRight = new EdgeEffect(context);
                mEdgeGlowTop = new EdgeEffect(context);
                mEdgeGlowBottom = new EdgeEffect(context);
            }
        } else {
            mEdgeGlowLeft = null;
            mEdgeGlowRight = null;
            mEdgeGlowTop = null;
            mEdgeGlowBottom = null;
        }
        super.setOverScrollMode(mode);
    }

    @SuppressWarnings({"SuspiciousNameCombination"})
    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (mEdgeGlowLeft != null) {
            final int scrollX = getScrollX();
            final int scrollY = getScrollY();
            final int width = getWidth();
            final int height = getHeight();
            // Visible padding.
            int vpTop, vpBottom, vpLeft, vpRight;
            if (mClipToPadding) {
                vpTop = getPaddingTop();
                vpBottom = getPaddingBottom();
                vpLeft = getPaddingLeft();
                vpRight = getPaddingRight();
            } else {
                vpTop = Math.max(getPaddingTop() - scrollY, 0);
                vpBottom = Math.max(scrollY - getVerticalScrollRange() + getPaddingBottom(), 0);
                vpLeft = Math.max(getPaddingLeft() - scrollX, 0);
                Log.e(TAG, "onDraw:" + " scrollX: " + scrollX + " rangeX: " + getHorizontalScrollRange());
                vpRight = Math.max(scrollX - getHorizontalScrollRange() + getPaddingRight(), 0);
            }
            final int avWidth = width - vpLeft - vpRight;
            final int avHeight = height - vpTop - vpBottom;

            if (!mEdgeGlowLeft.isFinished()) {
                final int restoreCount = canvas.save();
                canvas.rotate(270);
                canvas.translate(-(height + scrollY - vpBottom), 0);
                mEdgeGlowLeft.setSize(avHeight, avWidth);
                if (mEdgeGlowLeft.draw(canvas)) postInvalidateOnAnimation();
                canvas.restoreToCount(restoreCount);
            }

            if (!mEdgeGlowRight.isFinished()) {
                final int restoreCount = canvas.save();
                canvas.rotate(90);
                canvas.translate(scrollY + vpTop, -(width + scrollX));
                mEdgeGlowRight.setSize(avHeight, avWidth);
                if (mEdgeGlowRight.draw(canvas)) postInvalidateOnAnimation();
                canvas.restoreToCount(restoreCount);
            }

            if (!mEdgeGlowBottom.isFinished()) {
                final int restoreCount = canvas.save();

                canvas.rotate(180);
                canvas.translate(-(width + scrollX - vpRight), -(height + scrollY));
                mEdgeGlowBottom.setSize(avWidth, avHeight);
                if (mEdgeGlowBottom.draw(canvas)) postInvalidateOnAnimation();
                canvas.restoreToCount(restoreCount);
            }

            if (!mEdgeGlowTop.isFinished()) {
                final int restoreCount = canvas.save();

                canvas.rotate(0);
                canvas.translate(scrollX + vpLeft, 0);
                mEdgeGlowTop.setSize(avWidth, avHeight);
                if (mEdgeGlowTop.draw(canvas)) postInvalidateOnAnimation();
                canvas.restoreToCount(restoreCount);
            }
        }
    }

    private static int clamp(int n, int my, int child) {
        if (my >= child || n < 0) {
            return 0;
        }
        if ((my + n) > child) {
            return child - my;
        }
        return n;
    }
}
