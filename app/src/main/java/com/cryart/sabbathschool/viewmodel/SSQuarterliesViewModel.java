/*
 * Copyright (c) 2016 Adventech <info@adventech.io>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.cryart.sabbathschool.viewmodel;

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.databinding.BindingAdapter;
import android.databinding.ObservableFloat;
import android.databinding.ObservableInt;
import android.graphics.Color;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;

import com.cryart.sabbathschool.R;
import com.cryart.sabbathschool.bus.SSBusProvider;
import com.cryart.sabbathschool.event.SSLanguageFilterChangeEvent;
import com.cryart.sabbathschool.misc.SSConstants;
import com.cryart.sabbathschool.model.SSQuarterly;
import com.cryart.sabbathschool.model.SSQuarterlyLanguage;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.IconicsDrawable;
import com.squareup.otto.Subscribe;

import java.util.ArrayList;
import java.util.List;

public class SSQuarterliesViewModel implements SSViewModel, SwipeRefreshLayout.OnRefreshListener {
    private static final String TAG = SSQuarterliesViewModel.class.getSimpleName();
    private static final int ANIMATION_DURATION = 300;

    private Context context;

    private List<SSQuarterly> ssQuarterlies;
    private List<SSQuarterlyLanguage> quarterlyLanguages;
    private DataListener dataListener;
    private DatabaseReference mDatabase;

    public ObservableInt ssQuarterliesLanguageFilterVisibility;
    public ObservableInt ssQuarterliesLoadingVisibility;
    public ObservableInt ssQuarterliesListVisibility;
    public ObservableInt ssQuarterliesErrorMessageVisibility;
    public ObservableInt ssQuarterliesEmptyStateVisibility;
    public ObservableInt ssQuarterliesErrorStateVisibility;

    public ObservableFloat ssQuarterliesListMarginTop;

    public SSQuarterliesViewModel(Context context, final DataListener dataListener) {
        this.context = context;
        this.dataListener = dataListener;
        ssQuarterliesLoadingVisibility = new ObservableInt(View.INVISIBLE);
        ssQuarterliesListVisibility = new ObservableInt(View.INVISIBLE);
        ssQuarterliesLanguageFilterVisibility = new ObservableInt(View.GONE);
        ssQuarterliesErrorMessageVisibility = new ObservableInt(View.INVISIBLE);
        ssQuarterliesEmptyStateVisibility = new ObservableInt(View.INVISIBLE);
        ssQuarterliesErrorStateVisibility = new ObservableInt(View.INVISIBLE);
        ssQuarterliesListMarginTop = new ObservableFloat(0);
        mDatabase = FirebaseDatabase.getInstance().getReference();
        mDatabase.keepSynced(true);

        this.quarterlyLanguages = new ArrayList<>();
        this.ssQuarterlies = new ArrayList<>();

        String[] language_codes = context.getResources().getStringArray(R.array.ss_quarterlies_language_codes);
        String[] language_names = context.getResources().getStringArray(R.array.ss_quarterlies_language_names);
        int[] language_selects = context.getResources().getIntArray(R.array.ss_quarterlies_language_selects);

        for (int i = 0; i < language_codes.length; i++){
            this.quarterlyLanguages.add(new SSQuarterlyLanguage(language_codes[i], language_names[i], language_selects[i]));
        }

        dataListener.onQuarterliesLanguagesChanged(quarterlyLanguages);

        SSBusProvider.getInstance().register(this);

        loadQuarterlies(getSelectedLanguage());
    }

    public void onFilterClick(MenuItem menuItem){
        if (ssQuarterliesLanguageFilterVisibility.get() == View.GONE) {
            View v = ((Activity)context).findViewById(R.id.ss_quarterlies_language_filter_holder);
            v.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

            ssQuarterliesListMarginTop.set(v.getMeasuredHeight());
            ssQuarterliesLanguageFilterVisibility.set(View.VISIBLE);

            menuItem.setIcon(new IconicsDrawable(context)
                    .icon(GoogleMaterial.Icon.gmd_close)
                    .color(Color.WHITE)
                    .sizeDp(18));
        } else {
            ssQuarterliesListMarginTop.set(0);
            ssQuarterliesLanguageFilterVisibility.set(View.GONE);
            menuItem.setIcon(new IconicsDrawable(context)
                    .icon(GoogleMaterial.Icon.gmd_filter_list)
                    .color(Color.WHITE)
                    .sizeDp(18));
        }
    }

    public void setDataListener(DataListener dataListener) {
        this.dataListener = dataListener;
    }

    private SSQuarterlyLanguage getSelectedLanguage(){
        SSQuarterlyLanguage ssQuarterlyLanguage = null;
        for(SSQuarterlyLanguage lang : quarterlyLanguages){
            if (lang.selected == 1){
                ssQuarterlyLanguage = lang;
                break;
            }
        }

        if (ssQuarterlyLanguage == null) ssQuarterlyLanguage = new SSQuarterlyLanguage("en", "English", 1);
        return ssQuarterlyLanguage;
    }

    private void loadQuarterlies(SSQuarterlyLanguage ssQuarterlyLanguage){
        ssQuarterliesLoadingVisibility.set(View.VISIBLE);
        ssQuarterliesListVisibility.set(View.INVISIBLE);
        ssQuarterliesErrorMessageVisibility.set(View.INVISIBLE);
        ssQuarterliesEmptyStateVisibility.set(View.INVISIBLE);
        ssQuarterliesErrorStateVisibility.set(View.INVISIBLE);
        mDatabase.child(SSConstants.SS_FIREBASE_QUARTERLIES_DATABASE)
                .child(ssQuarterlyLanguage.code)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot != null) {
                            Iterable<DataSnapshot> data = dataSnapshot.getChildren();
                            ssQuarterlies.clear();
                            for(DataSnapshot d: data){
                                Log.d(TAG, String.valueOf(d));
                                ssQuarterlies.add(d.getValue(SSQuarterly.class));
                            }
                            dataListener.onQuarterliesChanged(ssQuarterlies);

                            ssQuarterliesListVisibility.set(View.VISIBLE);
                            ssQuarterliesLoadingVisibility.set(View.INVISIBLE);
                            ssQuarterliesErrorMessageVisibility.set(View.INVISIBLE);
                            ssQuarterliesEmptyStateVisibility.set(View.INVISIBLE);
                            ssQuarterliesErrorStateVisibility.set(View.INVISIBLE);

                            if (ssQuarterlies.size() == 0) {
                                ssQuarterliesEmptyStateVisibility.set(View.VISIBLE);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        ssQuarterliesErrorMessageVisibility.set(View.VISIBLE);
                        ssQuarterliesListVisibility.set(View.INVISIBLE);
                        ssQuarterliesLoadingVisibility.set(View.INVISIBLE);
                        ssQuarterliesEmptyStateVisibility.set(View.INVISIBLE);
                        ssQuarterliesErrorStateVisibility.set(View.VISIBLE);
                    }
                });
    }

    @Override
    public void destroy() {
        context = null;
        dataListener = null;
        ssQuarterlies = null;
        quarterlyLanguages = null;
        SSBusProvider.getInstance().unregister(this);
    }


    @Override
    public void onRefresh() {
        dataListener.onRefreshFinished();
    }

    @BindingAdapter("android:layout_marginTop")
    public static void setLayoutTopMargin(final View v, float topMargin) {
        int start = (topMargin > 0) ? 0: ((ViewGroup.MarginLayoutParams)v.getLayoutParams()).topMargin;
        int end = (topMargin > 0) ? (int) topMargin : 0;

        ValueAnimator slideAnimator = ValueAnimator.ofInt(start, end).setDuration(ANIMATION_DURATION);
        slideAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                Integer value = (Integer) animation.getAnimatedValue();
                ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams)v.getLayoutParams();
                layoutParams.topMargin = value;
                v.setLayoutParams(layoutParams);
                v.requestLayout();
            }
        });

        AnimatorSet set = new AnimatorSet();
        set.play(slideAnimator);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.start();
    }

    @Subscribe
    public void onChangeLanguageEvent(SSLanguageFilterChangeEvent event){
        this.loadQuarterlies(getSelectedLanguage());
    }

    public interface DataListener {
        void onQuarterliesChanged(List<SSQuarterly> ssQuarterlies);
        void onQuarterliesLanguagesChanged(List<SSQuarterlyLanguage> quarterlyLanguages);
        void onRefreshFinished();
    }
}
