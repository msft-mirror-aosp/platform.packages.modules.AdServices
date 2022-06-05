/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.adservices.ui.settings;

import static com.android.adservices.ui.settings.AdServicesSettingsMainFragment.TOPICS_PREFERENCE_BUTTON_KEY;

import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;

import com.android.adservices.api.R;

import java.util.Objects;

/**
 * Delegate class that helps AdServices Settings fragments to respond to all view model/user events.
 */
public class ActionDelegate {
    private final FragmentManager mFragmentManager;
    private final MainViewModel mMainViewModel;
    private final TopicsViewModel mTopicsViewModel;

    public ActionDelegate(
            FragmentManager fragmentManager,
            MainViewModel mainViewModel,
            TopicsViewModel topicsViewModel) {
        this.mFragmentManager = fragmentManager;
        this.mMainViewModel = mainViewModel;
        this.mTopicsViewModel = topicsViewModel;
    }

    /**
     * Initializes the connection between the {@link MainViewModel} and UI actions for {@link
     * AdServicesSettingsMainFragment}.
     */
    public void initMainFragment() {
        mFragmentManager.executePendingTransactions();
        AdServicesSettingsMainFragment fragment =
                (AdServicesSettingsMainFragment)
                        mFragmentManager.findFragmentById(R.id.fragment_container_view);

        configureTopicsButton(fragment);
        listenToViewModelUiEvents(fragment);
    }

    private void configureTopicsButton(AdServicesSettingsMainFragment fragment) {
        Preference topicsButton =
                Objects.requireNonNull(fragment.findPreference(TOPICS_PREFERENCE_BUTTON_KEY));

        topicsButton.setOnPreferenceClickListener(
                preference -> {
                    mMainViewModel.topicsButtonClickHandler();
                    return true;
                });
    }

    private void listenToViewModelUiEvents(AdServicesSettingsMainFragment fragment) {
        mMainViewModel
                .getUiEvents()
                .observe(
                        fragment,
                        event -> {
                            if (event == null) {
                                return;
                            }
                            switch (event) {
                                case DISPLAY_TOPICS_FRAGMENT:
                                    mFragmentManager
                                            .beginTransaction()
                                            .replace(
                                                    R.id.fragment_container_view,
                                                    AdServicesSettingsTopicsFragment.class,
                                                    null)
                                            .setReorderingAllowed(true)
                                            .addToBackStack(null)
                                            .commit();
                                    initTopicsFragment();
                                    break;
                            }
                        });
    }

    private void initTopicsFragment() {
        mFragmentManager.executePendingTransactions();
        AdServicesSettingsTopicsFragment fragment =
                (AdServicesSettingsTopicsFragment)
                        mFragmentManager.findFragmentById(R.id.fragment_container_view);

        listenToViewModelUiEvents(fragment);
    }

    // TODO(b/229721429): configure UI elements in Topics fragment.

    private void listenToViewModelUiEvents(AdServicesSettingsTopicsFragment fragment) {
        mTopicsViewModel
                .getUiEvents()
                .observe(
                        fragment,
                        event -> {
                            if (event == null) {
                                return;
                            }
                            // TODO(b/229721429): handle UI events.
                            switch (event) {
                                case DISPLAY_BLOCKED_TOPICS_FRAGMENT:
                                    break;
                            }
                        });
    }
}
