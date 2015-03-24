package com.getbase.floatingactionbutton;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.ColorRes;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import java.util.ArrayList;

public class FloatingActionsMenu extends ViewGroup {

    public static final int EXPAND_UP = 0;
    public static final int EXPAND_DOWN = 1;

    public static final int LABELS_ON_LEFT_SIDE = 0;
    public static final int LABELS_ON_RIGHT_SIDE = 1;

    private static final int ANIMATION_DURATION = 300;

    private static Interpolator sExpandInterpolator = new OvershootInterpolator();
    private static Interpolator sCollapseInterpolator = new DecelerateInterpolator(3f);
    private static Interpolator sAlphaExpandInterpolator = new DecelerateInterpolator();

    private AnimatorSet mExpandAnimation = new AnimatorSet().setDuration(ANIMATION_DURATION);
    private AnimatorSet mCollapseAnimation = new AnimatorSet().setDuration(ANIMATION_DURATION);

    private FloatingActionButton mMainButton;
    @Nullable
    private View mOverlayView;

    @DrawableRes
    private int mMainButtonIcon;
    @DrawableRes
    private int mExpandedMainButtonIcon;
    private String mMainButtonTitle;
    private int mMainButtonColorNormal;
    private int mMainButtonColorPressed;
    private int mMainButtonSize;
    private int mLabelsStyle;
    private boolean mShowOverlay;
    private int mExpandDirection;

    private boolean mExpanded;
    private int mButtonSpacing;
    private int mLabelsMargin;
    private int mLabelsVerticalOffset;
    private int mMaxButtonWidth;
    private int mLabelsPosition;
    private int mButtonsCount;

    private Rect mTouchAreaRect;

    private TouchDelegateGroup mTouchDelegateGroup;

    private OnFloatingActionsMenuUpdateListener mMenuUpdateListener;

    private OnActionsMenuItemClickListener mMenuClickListener;

    public interface OnActionsMenuItemClickListener {
        void onMainItemClick();

        void onSecondaryItemClick(int itemId);
    }

    public interface OnFloatingActionsMenuUpdateListener {
        void onMenuExpanded();

        void onMenuCollapsed();
    }

    public FloatingActionsMenu(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public FloatingActionsMenu(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        int childrenAmount = getChildCount();
        for (int i = 0; i < childrenAmount; i++) {
            final View child = getChildAt(i);
            final int viewId = child.getId();
            child.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (viewId == R.id.fab_expand_menu_button) {
                        handleMainButtonClick();
                    } else if (mMenuClickListener != null) {
                        mMenuClickListener.onSecondaryItemClick(viewId);
                    }
                }
            });
        }

        bringChildToFront(mMainButton);
        mButtonsCount = getChildCount();
        createLabels();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        final ViewGroup parentViewGroup = (ViewGroup) getParent();
        if (mShowOverlay && parentViewGroup != null) {
            // Prepare an overlaying view which will be shown when the menu is expanded.
            ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            mOverlayView = new View(getContext());
            mOverlayView.setLayoutParams(lp);
            mOverlayView.setBackgroundColor(getColor(R.color.overlay_color));
            mOverlayView.setVisibility(View.INVISIBLE);
            mOverlayView.setOnClickListener(new OuterAreaClickListener());
            parentViewGroup.addView(mOverlayView);
            bringToFront();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        measureChildren(widthMeasureSpec, heightMeasureSpec);

        int width = 0;
        int height = 0;

        mMaxButtonWidth = 0;
        int maxLabelWidth = 0;

        for (int i = 0; i < mButtonsCount; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }

            mMaxButtonWidth = Math.max(mMaxButtonWidth, child.getMeasuredWidth());
            height += child.getMeasuredHeight();

            TextView label = (TextView) child.getTag(R.id.fab_label);
            if (label != null) {
                maxLabelWidth = Math.max(maxLabelWidth, label.getMeasuredWidth());
            }
        }

        width = mMaxButtonWidth + (maxLabelWidth > 0 ? maxLabelWidth + mLabelsMargin : 0);
        height += mButtonSpacing * (getChildCount() - 1);
        height = adjustForOvershoot(height);

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        boolean expandUp = mExpandDirection == EXPAND_UP;

        if (changed) {
            mTouchDelegateGroup.clearTouchDelegates();
        }

        int mainButtonY = expandUp ? b - t - mMainButton.getMeasuredHeight() : 0;
        // Ensure mAddButton is centered on the line where the buttons should be
        int buttonsHorizontalCenter = mLabelsPosition == LABELS_ON_LEFT_SIDE
                ? r - l - mMaxButtonWidth / 2
                : mMaxButtonWidth / 2;
        int mainButtonLeft = buttonsHorizontalCenter - mMainButton.getMeasuredWidth() / 2;
        mMainButton.layout(mainButtonLeft, mainButtonY, mainButtonLeft + mMainButton.getMeasuredWidth(),
                mainButtonY + mMainButton.getMeasuredHeight());

        int labelsOffset = mMaxButtonWidth / 2 + mLabelsMargin;
        int labelsXNearButton = mLabelsPosition == LABELS_ON_LEFT_SIDE
                ? buttonsHorizontalCenter - labelsOffset
                : buttonsHorizontalCenter + labelsOffset;

        int nextY = expandUp ?
                mainButtonY - mButtonSpacing :
                mainButtonY + mMainButton.getMeasuredHeight() + mButtonSpacing;

        // Add label for the main menu button
        View mainBtnLabel = (View) mMainButton.getTag(R.id.fab_label);
        if (mainBtnLabel != null) {
            int mbLabelXAwayFromButton = mLabelsPosition == LABELS_ON_LEFT_SIDE
                    ? labelsXNearButton - mainBtnLabel.getMeasuredWidth()
                    : labelsXNearButton + mainBtnLabel.getMeasuredWidth();
            int mbLabelLeft = mLabelsPosition == LABELS_ON_LEFT_SIDE
                    ? mbLabelXAwayFromButton
                    : labelsXNearButton;
            int mbLabelRight = mLabelsPosition == LABELS_ON_LEFT_SIDE
                    ? labelsXNearButton
                    : mbLabelXAwayFromButton;
            int mbLabelTop = mainButtonY - mLabelsVerticalOffset
                    + (mMainButton.getMeasuredHeight() - mainBtnLabel.getMeasuredHeight()) / 2;
            mainBtnLabel.layout(mbLabelLeft, mbLabelTop, mbLabelRight, mbLabelTop + mainBtnLabel.getMeasuredHeight());
            mainBtnLabel.setAlpha(mExpanded ? 1f : 0f);

            mTouchAreaRect = new Rect(
                    Math.min(mainButtonLeft, mbLabelLeft),
                    mainButtonY - mButtonSpacing / 2,
                    Math.max(mainButtonLeft + mMainButton.getMeasuredWidth(), mbLabelRight),
                    mainButtonY + mMainButton.getMeasuredHeight() + mButtonSpacing / 2);
            mTouchDelegateGroup.addTouchDelegate(new TouchDelegate(mTouchAreaRect, mMainButton));
        }

        for (int i = mButtonsCount - 1; i >= 0; i--) {
            final View child = getChildAt(i);

            if (child == mMainButton || child.getVisibility() == GONE) continue;

            int childX = buttonsHorizontalCenter - child.getMeasuredWidth() / 2;
            int childY = expandUp ? nextY - child.getMeasuredHeight() : nextY;
            child.layout(childX, childY, childX + child.getMeasuredWidth(), childY + child.getMeasuredHeight());

            float collapsedTranslation = mainButtonY - childY;
            float expandedTranslation = 0f;

            child.setTranslationY(mExpanded ? expandedTranslation : collapsedTranslation);
            child.setAlpha(mExpanded ? 1f : 0f);

            LayoutParams params = (LayoutParams) child.getLayoutParams();
            params.mCollapseDir.setFloatValues(expandedTranslation, collapsedTranslation);
            params.mExpandDir.setFloatValues(collapsedTranslation, expandedTranslation);
            params.setAnimationsTarget(child);

            View label = (View) child.getTag(R.id.fab_label);
            if (label != null) {
                int labelXAwayFromButton = mLabelsPosition == LABELS_ON_LEFT_SIDE
                        ? labelsXNearButton - label.getMeasuredWidth()
                        : labelsXNearButton + label.getMeasuredWidth();

                int labelLeft = mLabelsPosition == LABELS_ON_LEFT_SIDE
                        ? labelXAwayFromButton
                        : labelsXNearButton;

                int labelRight = mLabelsPosition == LABELS_ON_LEFT_SIDE
                        ? labelsXNearButton
                        : labelXAwayFromButton;

                int labelTop = childY - mLabelsVerticalOffset
                        + (child.getMeasuredHeight() - label.getMeasuredHeight()) / 2;

                label.layout(labelLeft, labelTop, labelRight, labelTop + label.getMeasuredHeight());

                mTouchAreaRect = new Rect(
                        Math.min(childX, labelLeft),
                        childY - mButtonSpacing / 2,
                        Math.max(childX + child.getMeasuredWidth(), labelRight),
                        childY + child.getMeasuredHeight() + mButtonSpacing / 2);
                mTouchDelegateGroup.addTouchDelegate(new TouchDelegate(mTouchAreaRect, child));

                label.setTranslationY(mExpanded ? expandedTranslation : collapsedTranslation);
                label.setAlpha(mExpanded ? 1f : 0f);

                LayoutParams labelParams = (LayoutParams) label.getLayoutParams();
                labelParams.mCollapseDir.setFloatValues(expandedTranslation, collapsedTranslation);
                labelParams.mExpandDir.setFloatValues(collapsedTranslation, expandedTranslation);
                labelParams.setAnimationsTarget(label);
            }

            nextY = expandUp ?
                    childY - mButtonSpacing :
                    childY + child.getMeasuredHeight() + mButtonSpacing;
        }

        ObjectAnimator expandAnimator = ObjectAnimator.ofFloat(mainBtnLabel, "alpha", 0f, 1f);
        expandAnimator.setInterpolator(sAlphaExpandInterpolator);
        ArrayList<Animator> list1 = mExpandAnimation.getChildAnimations();
        list1.add(expandAnimator);
        mExpandAnimation = new AnimatorSet().setDuration(ANIMATION_DURATION);
        mExpandAnimation.playTogether(list1);

        ObjectAnimator collapseAnimator = ObjectAnimator.ofFloat(mainBtnLabel, "alpha", 1f, 0f);
        collapseAnimator.setInterpolator(sAlphaExpandInterpolator);
        ArrayList<Animator> list2 = mCollapseAnimation.getChildAnimations();
        list2.add(collapseAnimator);
        mCollapseAnimation = new AnimatorSet().setDuration(ANIMATION_DURATION);
        mCollapseAnimation.playTogether(list2);
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(super.generateDefaultLayoutParams());
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(super.generateLayoutParams(attrs));
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(super.generateLayoutParams(p));
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return super.checkLayoutParams(p);
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState savedState = new SavedState(superState);
        savedState.mExpanded = mExpanded;
        return savedState;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (state instanceof SavedState) {
            SavedState savedState = (SavedState) state;
            mExpanded = savedState.mExpanded;
            mTouchDelegateGroup.setEnabled(mExpanded);
            super.onRestoreInstanceState(savedState.getSuperState());
        } else {
            super.onRestoreInstanceState(state);
        }
    }

    @Override
    public void setOnClickListener(OnClickListener l) {
        throw new UnsupportedOperationException("Unsupported operation. To track click events use " +
                "setOnActionsMenuItemClickListener() instead.");
    }

    public void setOnActionsMenuItemClickListener(OnActionsMenuItemClickListener listener) {
        this.mMenuClickListener = listener;
    }

    public void setOnFloatingActionsMenuUpdateListener(OnFloatingActionsMenuUpdateListener listener) {
        mMenuUpdateListener = listener;
    }

    public void addButton(FloatingActionButton button) {
        addView(button, mButtonsCount - 1);
        mButtonsCount++;
        createLabels();
    }

    public void removeButton(FloatingActionButton button) {
        removeView(button.getLabelView());
        removeView(button);
        mButtonsCount--;
    }

    public void setOverlayEnabled(boolean isEnabled) {
        this.mShowOverlay = isEnabled;
    }

    public boolean isOverlayEnabled() {
        return mShowOverlay;
    }

    public void collapse() {
        if (mExpanded) {
            mExpanded = false;
            if (mShowOverlay && mOverlayView != null) mOverlayView.setVisibility(View.INVISIBLE);
            mTouchDelegateGroup.setEnabled(false);
            mCollapseAnimation.start();
            mExpandAnimation.cancel();

            mCollapseAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    mMainButton.setIcon(mMainButtonIcon);
                }
            });

            if (mMenuUpdateListener != null) {
                mMenuUpdateListener.onMenuCollapsed();
            }
        }
    }

    public void expand() {
        if (!mExpanded) {
            mExpanded = true;
            if (mShowOverlay && mOverlayView != null) mOverlayView.setVisibility(View.VISIBLE);
            mTouchDelegateGroup.setEnabled(true);
            mCollapseAnimation.cancel();
            mExpandAnimation.start();

            mExpandAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    mMainButton.setIcon(mExpandedMainButtonIcon);
                }
            });

            if (mMenuUpdateListener != null) {
                mMenuUpdateListener.onMenuExpanded();
            }
        }
    }

    public boolean isExpanded() {
        return mExpanded;
    }

    private void init(Context context, AttributeSet attributeSet) {
        mButtonSpacing = (int) (getResources().getDimension(R.dimen.fab_actions_spacing)
                - getResources().getDimension(R.dimen.fab_shadow_radius)
                - getResources().getDimension(R.dimen.fab_shadow_offset));
        mLabelsMargin = getResources().getDimensionPixelSize(R.dimen.fab_labels_margin);
        mLabelsVerticalOffset = getResources().getDimensionPixelSize(R.dimen.fab_shadow_offset);

        mTouchDelegateGroup = new TouchDelegateGroup(this);
        setTouchDelegate(mTouchDelegateGroup);

        TypedArray attr = context.obtainStyledAttributes(attributeSet, R.styleable.FloatingActionsMenu, 0, 0);
        mMainButtonIcon = attr.getResourceId(R.styleable.FloatingActionsMenu_fab_mainButtonIcon, 0);
        mExpandedMainButtonIcon = attr.getResourceId(R.styleable.FloatingActionsMenu_fab_expandedMainButtonIcon, mMainButtonIcon);
        mMainButtonColorNormal = attr.getColor(R.styleable.FloatingActionsMenu_fab_mainButtonColorNormal, getColor(android.R.color.holo_blue_dark));
        mMainButtonColorPressed = attr.getColor(R.styleable.FloatingActionsMenu_fab_mainButtonColorPressed, ColorUtils.darkenColor(mMainButtonColorNormal));
        mMainButtonTitle = attr.getString(R.styleable.FloatingActionsMenu_fab_mainButtonTitle);
        mMainButtonSize = attr.getInt(R.styleable.FloatingActionsMenu_fab_mainButtonSize, FloatingActionButton.SIZE_NORMAL);
        mShowOverlay = attr.getBoolean(R.styleable.FloatingActionsMenu_fab_showOverlay, false);
        mExpandDirection = attr.getInt(R.styleable.FloatingActionsMenu_fab_expandDirection, EXPAND_UP);
        mLabelsStyle = attr.getResourceId(R.styleable.FloatingActionsMenu_fab_labelStyle, R.style.default_labels_style);
        mLabelsPosition = attr.getInt(R.styleable.FloatingActionsMenu_fab_labelsPosition, LABELS_ON_LEFT_SIDE);
        attr.recycle();

        createMainButtonButton(context);
    }

    private void handleMainButtonClick() {
        if (mExpanded) {
            if (mMenuClickListener != null) mMenuClickListener.onMainItemClick();
        } else expand();
    }

    private void createMainButtonButton(Context context) {
        mMainButton = new FloatingActionButton(context);
        mMainButton.setId(R.id.fab_expand_menu_button);
        mMainButton.setIcon(mMainButtonIcon);
        mMainButton.setSize(mMainButtonSize);
        mMainButton.setColorNormal(mMainButtonColorNormal);
        mMainButton.setColorPressed(mMainButtonColorPressed);
        addView(mMainButton, super.generateDefaultLayoutParams());
    }

    private int getColor(@ColorRes int id) {
        return getResources().getColor(id);
    }


    private int adjustForOvershoot(int dimension) {
        return dimension * 12 / 10;
    }

    private void createLabels() {
        Context context = new ContextThemeWrapper(getContext(), mLabelsStyle);
        for (int i = 0; i < mButtonsCount; i++) {
            FloatingActionButton button = (FloatingActionButton) getChildAt(i);
            int id = button.getId();
            String title = (id == R.id.fab_expand_menu_button) ? mMainButtonTitle : button.getTitle();

            if (title == null || button.getTag(R.id.fab_label) != null) continue;

            TextView label = new TextView(context);
            label.setTextAppearance(getContext(), mLabelsStyle);
            label.setText(title);
            addView(label);

            button.setTag(R.id.fab_label, label);
        }
    }

    private final class OuterAreaClickListener implements OnClickListener {

        @Override
        public void onClick(View view) {
            if (isExpanded()) collapse();
        }
    }

    private static class SavedState extends BaseSavedState {

        public boolean mExpanded;

        public SavedState(Parcelable parcel) {
            super(parcel);
        }

        private SavedState(Parcel in) {
            super(in);
            mExpanded = in.readInt() == 1;
        }

        @Override
        public void writeToParcel(@NonNull Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(mExpanded ? 1 : 0);
        }

        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {

            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    private class LayoutParams extends ViewGroup.LayoutParams {

        private ObjectAnimator mExpandDir = new ObjectAnimator();
        private ObjectAnimator mExpandAlpha = new ObjectAnimator();
        private ObjectAnimator mCollapseDir = new ObjectAnimator();
        private ObjectAnimator mCollapseAlpha = new ObjectAnimator();
        private boolean animationsSetToPlay;

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);

            mExpandDir.setInterpolator(sExpandInterpolator);
            mExpandAlpha.setInterpolator(sAlphaExpandInterpolator);
            mCollapseDir.setInterpolator(sCollapseInterpolator);
            mCollapseAlpha.setInterpolator(sCollapseInterpolator);

            mCollapseAlpha.setProperty(View.ALPHA);
            mCollapseAlpha.setFloatValues(1f, 0f);

            mExpandAlpha.setProperty(View.ALPHA);
            mExpandAlpha.setFloatValues(0f, 1f);

            mCollapseDir.setProperty(View.TRANSLATION_Y);
            mExpandDir.setProperty(View.TRANSLATION_Y);
        }

        public void setAnimationsTarget(View view) {
            mCollapseAlpha.setTarget(view);
            mCollapseDir.setTarget(view);
            mExpandAlpha.setTarget(view);
            mExpandDir.setTarget(view);

            // Now that the animations have targets, set them to be played
            if (!animationsSetToPlay) {
                mCollapseAnimation.play(mCollapseAlpha);
                mCollapseAnimation.play(mCollapseDir);
                mExpandAnimation.play(mExpandAlpha);
                mExpandAnimation.play(mExpandDir);
                animationsSetToPlay = true;
            }
        }
    }
}
