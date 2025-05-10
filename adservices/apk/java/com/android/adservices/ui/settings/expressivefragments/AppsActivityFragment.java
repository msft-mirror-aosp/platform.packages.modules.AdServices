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
import com.android.adservices.ui.settings.activities.AppsActivity;
import com.android.adservices.ui.settings.activitydelegates.AppsActivityFragmentActionDelegate;
import com.android.adservices.ui.settings.viewmodels.AppsViewModel;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

@RequiresApi(Build.VERSION_CODES.S)
public class AppsActivityFragment extends SettingsBasePreferenceFragment {
    public static final String APPS_TOP_INTRO = "apps_ga_introduction";
    public static final String APPS_SWITCH_BAR = "apps_switch_bar";
    public static final String APPS_NO_TOPICS_STATE = "no_apps_state";
    public static final String APPS_BLOCKED_APPS_WHEN_EMPTY_STATE_BUTTON =
            "blocked_apps_when_empty_state_button";
    public static final String APPS_BLCOKED_TOIPCS_BUTTON = "blocked_apps_button";
    public static final String APPS_RESET_TOPICS_BUTTON = "reset_apps_button";
    public static final String APPS_LIST = "apps_list";

    public static final String APPS_EMPTY_APPS_HIDDEN_SECTION = "empty_apps_hidden_section";
    public static final String APPS_NO_APPS_MESSAGE_SECTION = "no_apps_message";
    public static final String APPS_FOOTER = "apps_view_ga_footer";
    public static final String APPS_PRIVACY_LEARN_MORE_LINK =
            "https://support.google.com/android?p=ad_privacy";
    public static final String APPS_FOOTER_LEARN_MORE_LINK =
            "https://support.google.com/android?p=manage_appsuggested_ads";

    private AppsActivityFragmentActionDelegate mAppsActivityFragmentActionDelegate;

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
        setPreferencesFromResource(R.layout.apps_activity_fragment, rootKey);

        mAppsActivityFragmentActionDelegate.initWithUx();
    }

    @Override
    public void onResume() {
        super.onResume();
        mAppsActivityFragmentActionDelegate.initWithUx();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        mAppsActivityFragmentActionDelegate =
                new AppsActivityFragmentActionDelegate(
                        (AppsActivity) requireActivity(),
                        new ViewModelProvider(this).get(AppsViewModel.class),
                        this);
    }
}
