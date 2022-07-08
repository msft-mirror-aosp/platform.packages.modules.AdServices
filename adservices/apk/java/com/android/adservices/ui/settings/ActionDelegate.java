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

import static com.android.adservices.ui.settings.fragments.AdServicesSettingsMainFragment.PRIVACY_SANDBOX_BETA_SWITCH_KEY;
import static com.android.adservices.ui.settings.fragments.AdServicesSettingsMainFragment.TOPICS_PREFERENCE_BUTTON_KEY;

import android.view.View;

import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;

import com.android.adservices.api.R;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsBlockedTopicsFragment;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsMainFragment;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsTopicsFragment;
import com.android.adservices.ui.settings.viewmodels.MainViewModel;
import com.android.adservices.ui.settings.viewmodels.TopicsViewModel;
import com.android.adservices.ui.settings.viewmodels.TopicsViewModel.TopicsViewModelUiEvent;

import java.util.Objects;

/**
 * Delegate class that helps AdServices Settings fragments to respond to all view model/user events.
 */
public class ActionDelegate {

    private final LifecycleOwner mLifecycleOwner;
    private final FragmentManager mFragmentManager;
    private final MainViewModel mMainViewModel;
    private final TopicsViewModel mTopicsViewModel;

    public ActionDelegate(
            LifecycleOwner lifecycleOwner,
            FragmentManager fragmentManager,
            MainViewModel mainViewModel,
            TopicsViewModel topicsViewModel) {
        this.mLifecycleOwner = lifecycleOwner;
        this.mFragmentManager = fragmentManager;
        this.mMainViewModel = mainViewModel;
        this.mTopicsViewModel = topicsViewModel;

        listenToMainViewModelUiEvents();
        listenToTopicsViewModelUiEvents();
    }

    // ---------------------------------------------------------------------------------------------
    // UI Event Listeners
    // ---------------------------------------------------------------------------------------------

    private void listenToMainViewModelUiEvents() {
        mMainViewModel
                .getUiEvents()
                .observe(
                        mLifecycleOwner,
                        event -> {
                            if (event == null) {
                                return;
                            }
                            try {
                                switch (event) {
                                    case SWITCH_ON_PRIVACY_SANDBOX_BETA:
                                        mMainViewModel.setConsent(true);
                                        break;
                                    case SWITCH_OFF_PRIVACY_SANDBOX_BETA:
                                        // TODO(b/235138016): confirmation for privacy sandbox
                                        // consent
                                        mMainViewModel.setConsent(false);
                                        break;
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
                                        break;
                                }
                            } finally {
                                mMainViewModel.uiEventHandled();
                            }
                        });
    }

    private void listenToTopicsViewModelUiEvents() {
        mTopicsViewModel
                .getUiEvents()
                .observe(
                        mLifecycleOwner,
                        eventTopicPair -> {
                            if (eventTopicPair == null) {
                                return;
                            }
                            TopicsViewModelUiEvent event = eventTopicPair.first;
                            Topic topic = eventTopicPair.second;
                            if (event == null) {
                                return;
                            }
                            try {
                                switch (event) {
                                    case BLOCK_TOPIC:
                                        // TODO(b/229721429): show confirmation for blocking a
                                        // topic.
                                        mTopicsViewModel.revokeTopicConsent(topic);
                                        break;
                                    case RESTORE_TOPIC:
                                        // TODO(b/229721429): show confirmation for restoring a
                                        // topic.
                                        mTopicsViewModel.restoreTopicConsent(topic);
                                        break;
                                    case RESET_TOPICS:
                                        // TODO(b/229721429): show confirmation for resetting
                                        // topics.
                                        mTopicsViewModel.resetTopics();
                                        break;
                                    case DISPLAY_BLOCKED_TOPICS_FRAGMENT:
                                        mFragmentManager
                                                .beginTransaction()
                                                .replace(
                                                        R.id.fragment_container_view,
                                                        AdServicesSettingsBlockedTopicsFragment
                                                                .class,
                                                        null)
                                                .setReorderingAllowed(true)
                                                .addToBackStack(null)
                                                .commit();
                                        break;
                                }
                            } finally {
                                mTopicsViewModel.uiEventHandled();
                            }
                        });
    }

    // ---------------------------------------------------------------------------------------------
    // Main Fragment
    // ---------------------------------------------------------------------------------------------

    /**
     * Configure all UI elements in {@link AdServicesSettingsMainFragment} to handle user actions.
     */
    public void initMainFragment(AdServicesSettingsMainFragment fragment) {
        configureConsentSwitch(fragment);
        configureTopicsButton(fragment);
    }

    private void configureConsentSwitch(AdServicesSettingsMainFragment fragment) {
        SwitchPreference switchPreference =
                Objects.requireNonNull(fragment.findPreference(PRIVACY_SANDBOX_BETA_SWITCH_KEY));

        mMainViewModel.getConsent().observe(fragment, switchPreference::setChecked);

        switchPreference.setOnPreferenceClickListener(
                preference -> {
                    mMainViewModel.consentSwitchClickHandler(
                            ((SwitchPreference) preference).isChecked());
                    return true;
                });
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

    // ---------------------------------------------------------------------------------------------
    // Topics Fragment
    // ---------------------------------------------------------------------------------------------

    /**
     * Configure all UI elements (except topics list) in {@link AdServicesSettingsTopicsFragment} to
     * handle user actions.
     */
    public void initTopicsFragment(AdServicesSettingsTopicsFragment fragment) {
        configureBlockedTopicsFragmentButton(fragment);
        configureResetTopicsButton(fragment);
    }

    private void configureBlockedTopicsFragmentButton(AdServicesSettingsTopicsFragment fragment) {
        View blockedTopicsButton = fragment.requireView().findViewById(R.id.blocked_topics_button);

        blockedTopicsButton.setOnClickListener(
                view -> {
                    mTopicsViewModel.blockedTopicsFragmentButtonClickHandler();
                });
    }

    private void configureResetTopicsButton(AdServicesSettingsTopicsFragment fragment) {
        View resetTopicsButton = fragment.requireView().findViewById(R.id.reset_topics_button);

        resetTopicsButton.setOnClickListener(
                view -> {
                    mTopicsViewModel.resetTopicsButtonClickHandler();
                });
    }
}
