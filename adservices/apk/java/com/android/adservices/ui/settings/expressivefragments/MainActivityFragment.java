/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.adservices.ui.settings.expressivefragments;

import android.annotation.Nullable;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.lifecycle.ViewModelProvider;

import com.android.adservices.api.R;
import com.android.adservices.ui.settings.activities.AdServicesSettingsMainActivity;
import com.android.adservices.ui.settings.activitydelegates.MainActivityFragmentActionDelegate;
import com.android.adservices.ui.settings.viewmodels.MainViewModel;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

@RequiresApi(Build.VERSION_CODES.S)
public class MainActivityFragment extends SettingsBasePreferenceFragment {
    public static final String TOPICS_PREFERENCE = "topics_preference";
    public static final String APPS_PREFERENCE = "apps_preference";
    public static final String MEASUREMENT_PREFERENCE = "measurement_preference";
    public static final String MAIN_VIEW_FOOTER = "main_view_footer";
    public static final String MAIN_LEARN_MORE_LINK =
            "https://support.google.com/android?p=ad_privacy";
    private final int mResId;
    private MainActivityFragmentActionDelegate mMainActivityFragmentActionDelegate;

    public MainActivityFragment(int resId) {
        mResId = resId;
    }

    public MainActivityFragment() {
        mResId = R.layout.main_activity_fragment;
    }

    @Override
    @Nullable
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View mainView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(mainView, savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(mResId, rootKey);
        mMainActivityFragmentActionDelegate.initWithUx();
    }

    @Override
    public void onResume() {
        super.onResume();
        mMainActivityFragmentActionDelegate.initWithUx();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mMainActivityFragmentActionDelegate =
                new MainActivityFragmentActionDelegate(
                        (AdServicesSettingsMainActivity) requireActivity(),
                        new ViewModelProvider(this).get(MainViewModel.class),
                        this);
    }
}
