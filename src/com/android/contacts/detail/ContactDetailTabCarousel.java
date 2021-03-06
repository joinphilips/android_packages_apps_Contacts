/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.contacts.detail;

import com.android.contacts.ContactLoader;
import com.android.contacts.R;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * This is a horizontally scrolling carousel with 2 tabs: one to see info about the contact and
 * one to see updates from the contact.
 */
public class ContactDetailTabCarousel extends HorizontalScrollView implements OnTouchListener {

    private static final String TAG = ContactDetailTabCarousel.class.getSimpleName();

    private static final int TAB_INDEX_ABOUT = 0;
    private static final int TAB_INDEX_UPDATES = 1;
    private static final int TAB_COUNT = 2;

    /** Tab width as defined as a fraction of the screen width */
    private float mTabWidthScreenWidthFraction;

    /** Tab height as defined as a fraction of the screen width */
    private float mTabHeightScreenWidthFraction;

    private ImageView mPhotoView;
    private TextView mStatusView;
    private ImageView mStatusPhotoView;

    private Listener mListener;

    private int mCurrentTab = TAB_INDEX_ABOUT;

    private CarouselTab mAboutTab;
    private CarouselTab mUpdatesTab;

    /** Last Y coordinate of the carousel when the tab at the given index was selected */
    private final float[] mYCoordinateArray = new float[TAB_COUNT];

    private int mTabDisplayLabelHeight;

    private boolean mScrollToCurrentTab = false;
    private int mLastScrollPosition;

    private int mAllowedHorizontalScrollLength = Integer.MIN_VALUE;
    private int mAllowedVerticalScrollLength = Integer.MIN_VALUE;

    private static final float MAX_ALPHA = 0.5f;

    /**
     * Interface for callbacks invoked when the user interacts with the carousel.
     */
    public interface Listener {
        public void onTouchDown();
        public void onTouchUp();
        public void onScrollChanged(int l, int t, int oldl, int oldt);
        public void onTabSelected(int position);
    }

    public ContactDetailTabCarousel(Context context, AttributeSet attrs) {
        super(context, attrs);

        setOnTouchListener(this);

        Resources resources = mContext.getResources();
        mTabDisplayLabelHeight = resources.getDimensionPixelSize(
                R.dimen.detail_tab_carousel_tab_label_height);
        mTabWidthScreenWidthFraction = resources.getFraction(
                R.fraction.tab_width_screen_width_percentage, 1, 1);
        mTabHeightScreenWidthFraction = resources.getFraction(
                R.fraction.tab_height_screen_width_percentage, 1, 1);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mAboutTab = (CarouselTab) findViewById(R.id.tab_about);
        mAboutTab.setLabel(mContext.getString(R.string.contactDetailAbout));

        mUpdatesTab = (CarouselTab) findViewById(R.id.tab_update);
        mUpdatesTab.setLabel(mContext.getString(R.string.contactDetailUpdates));

        mAboutTab.enableTouchInterceptor(mAboutTabTouchInterceptListener);
        mUpdatesTab.enableTouchInterceptor(mUpdatesTabTouchInterceptListener);

        // Retrieve the photo view for the "about" tab
        mPhotoView = (ImageView) mAboutTab.findViewById(R.id.photo);

        // Retrieve the social update views for the "updates" tab
        mStatusView = (TextView) mUpdatesTab.findViewById(R.id.status);
        mStatusPhotoView = (ImageView) mUpdatesTab.findViewById(R.id.status_photo);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int screenWidth = MeasureSpec.getSize(widthMeasureSpec);
        // Compute the width of a tab as a fraction of the screen width
        int tabWidth = (int) (mTabWidthScreenWidthFraction * screenWidth);

        // Find the allowed scrolling length by subtracting the current visible screen width
        // from the total length of the tabs.
        mAllowedHorizontalScrollLength = tabWidth * TAB_COUNT - screenWidth;

        int tabHeight = (int) (screenWidth * mTabHeightScreenWidthFraction);
        // Set the child {@link LinearLayout} to be TAB_COUNT * the computed tab width so that the
        // {@link LinearLayout}'s children (which are the tabs) will evenly split that width.
        if (getChildCount() > 0) {
            View child = getChildAt(0);
            child.measure(MeasureSpec.makeMeasureSpec(TAB_COUNT * tabWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(tabHeight, MeasureSpec.EXACTLY));
        }

        mAllowedVerticalScrollLength = tabHeight - mTabDisplayLabelHeight;
        setMeasuredDimension(
                resolveSize(screenWidth, widthMeasureSpec),
                resolveSize(tabHeight, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (mScrollToCurrentTab) {
            mScrollToCurrentTab = false;
            scrollTo(mCurrentTab == TAB_INDEX_ABOUT ? 0 : mAllowedHorizontalScrollLength, 0);
            updateAlphaLayers();
        }
    }

    private final OnClickListener mAboutTabTouchInterceptListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            mListener.onTabSelected(TAB_INDEX_ABOUT);
        }
    };

    private final OnClickListener mUpdatesTabTouchInterceptListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            mListener.onTabSelected(TAB_INDEX_UPDATES);
        }
    };

    private void updateAlphaLayers() {
        mAboutTab.setAlphaLayerValue(mLastScrollPosition * MAX_ALPHA /
                mAllowedHorizontalScrollLength);
        mUpdatesTab.setAlphaLayerValue(MAX_ALPHA - mLastScrollPosition * MAX_ALPHA /
                mAllowedHorizontalScrollLength);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        mListener.onScrollChanged(l, t, oldl, oldt);
        mLastScrollPosition = l;
        updateAlphaLayers();
    }

    /**
     * Reset the carousel to the start position (i.e. because new data will be loaded in for a
     * different contact).
     */
    public void reset() {
        scrollTo(0, 0);
        setCurrentTab(0);
        moveToYCoordinate(0, 0);
    }

    /**
     * Set the current tab that should be restored when the view is first laid out.
     */
    public void restoreCurrentTab(int position) {
        setCurrentTab(position);
        // It is only possible to scroll the view after onMeasure() has been called (where the
        // allowed horizontal scroll length is determined). Hence, set a flag that will be read
        // in onLayout() after the children and this view have finished being laid out.
        mScrollToCurrentTab = true;
    }

    /**
     * Restore the Y position of this view to the last manually requested value. This can be done
     * after the parent has been re-laid out again, where this view's position could have been
     * lost if the view laid outside its parent's bounds.
     */
    public void restoreYCoordinate() {
        setY(getStoredYCoordinateForTab(mCurrentTab));
    }

    /**
     * Request that the view move to the given Y coordinate. Also store the Y coordinate as the
     * last requested Y coordinate for the given tabIndex.
     */
    public void moveToYCoordinate(int tabIndex, float y) {
        setY(y);
        storeYCoordinate(tabIndex, y);
    }

    /**
     * Store this information as the last requested Y coordinate for the given tabIndex.
     */
    public void storeYCoordinate(int tabIndex, float y) {
        mYCoordinateArray[tabIndex] = y;
    }

    /**
     * Returns the stored Y coordinate of this view the last time the user was on the selected
     * tab given by tabIndex.
     */
    public float getStoredYCoordinateForTab(int tabIndex) {
        return mYCoordinateArray[tabIndex];
    }

    /**
     * Returns the number of pixels that this view can be scrolled horizontally.
     */
    public int getAllowedHorizontalScrollLength() {
        return mAllowedHorizontalScrollLength;
    }

    /**
     * Returns the number of pixels that this view can be scrolled vertically while still allowing
     * the tab labels to still show.
     */
    public int getAllowedVerticalScrollLength() {
        return mAllowedVerticalScrollLength;
    }

    /**
     * Updates the tab selection.
     */
    public void setCurrentTab(int position) {
        switch (position) {
            case TAB_INDEX_ABOUT:
                mAboutTab.showSelectedState();
                mUpdatesTab.showDeselectedState();
                break;
            case TAB_INDEX_UPDATES:
                mUpdatesTab.showSelectedState();
                mAboutTab.showDeselectedState();
                break;
            default:
                throw new IllegalStateException("Invalid tab position " + position);
        }
        mCurrentTab = position;
    }

    /**
     * Loads the data from the Loader-Result. This is the only function that has to be called
     * from the outside to fully setup the View
     */
    public void loadData(ContactLoader.Result contactData) {
        if (contactData == null) {
            return;
        }

        // TODO: Move this into the {@link CarouselTab} class when the updates fragment code is more
        // finalized
        ContactDetailDisplayUtils.setPhoto(mContext, contactData, mPhotoView);
        ContactDetailDisplayUtils.setSocialSnippet(mContext, contactData, mStatusView,
                mStatusPhotoView);
    }

    /**
     * Set the given {@link Listener} to handle carousel events.
     */
    public void setListener(Listener listener) {
        mListener = listener;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mListener.onTouchDown();
                return true;
            case MotionEvent.ACTION_UP:
                mListener.onTouchUp();
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        boolean interceptTouch = super.onInterceptTouchEvent(ev);
        if (interceptTouch) {
            mListener.onTouchDown();
        }
        return interceptTouch;
    }
}
