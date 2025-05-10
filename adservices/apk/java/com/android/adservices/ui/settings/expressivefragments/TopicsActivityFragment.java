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
import com.android.adservices.ui.settings.activities.TopicsActivity;
import com.android.adservices.ui.settings.activitydelegates.TopicsActivityFragmentActionDelegate;
import com.android.adservices.ui.settings.viewmodels.TopicsViewModel;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

@RequiresApi(Build.VERSION_CODES.S)
public class TopicsActivityFragment extends SettingsBasePreferenceFragment {
    public static final String TOPICS_SWITCH_BAR = "topics_switch_bar";
    public static final String TOPICS_NO_TOPICS_STATE = "no_topics_state";
    public static final String TOPICS_BLOCKED_TOPICS_WHEN_EMPTY_STATE_BUTTON =
            "blocked_topics_when_empty_state_button";
    public static final String TOPICS_BLCOKED_TOIPCS_BUTTON = "blocked_topics_button";
    public static final String TOPICS_RESET_TOPICS_BUTTON = "reset_topics_button";
    public static final String TOPICS_LIST = "topics_list";
    public static final String TOPICS_FOOTER = "topics_view_ga_footer";
    public static final String TOPICS_EMPTY_TOPICS_HIDDEN_SECTION = "empty_topics_hidden_section";
    public static final String TOPICS_NO_TOPICS_MESSAGE_SECTION = "no_topics_message";
    public static final String TOPICS_LEARN_MORE_LINK =
            "https://support.google.com/android?p=ad_privacy";

    private TopicsActivityFragmentActionDelegate mTopicsActivityFragmentActionDelegate;

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
        setPreferencesFromResource(R.layout.topics_activity_fragment, rootKey);

        mTopicsActivityFragmentActionDelegate.initWithUx();
    }

    @Override
    public void onResume() {
        super.onResume();
        mTopicsActivityFragmentActionDelegate.initWithUx();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        mTopicsActivityFragmentActionDelegate =
                new TopicsActivityFragmentActionDelegate(
                        (TopicsActivity) requireActivity(),
                        new ViewModelProvider(this).get(TopicsViewModel.class),
                        this);
    }
}
