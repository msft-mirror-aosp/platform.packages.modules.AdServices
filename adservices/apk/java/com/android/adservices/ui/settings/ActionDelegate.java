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

import android.view.View;
import android.widget.Toast;

import androidx.fragment.app.FragmentManager;

import com.android.adservices.api.R;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.consent.App;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsAppsFragment;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsBlockedAppsFragment;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsBlockedTopicsFragment;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsMainFragment;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsTopicsFragment;
import com.android.adservices.ui.settings.viewmodels.AppsViewModel;
import com.android.adservices.ui.settings.viewmodels.MainViewModel;
import com.android.adservices.ui.settings.viewmodels.TopicsViewModel;
import com.android.adservices.ui.settings.viewmodels.TopicsViewModel.TopicsViewModelUiEvent;
import com.android.settingslib.widget.MainSwitchBar;

import java.io.IOException;

/**
 * Delegate class that helps AdServices Settings fragments to respond to all view model/user events.
 */
public class ActionDelegate {

    private final AdServicesSettingsActivity mAdServicesSettingsActivity;
    private final FragmentManager mFragmentManager;
    private final MainViewModel mMainViewModel;
    private final TopicsViewModel mTopicsViewModel;
    private final AppsViewModel mAppsViewModel;

    public ActionDelegate(
            AdServicesSettingsActivity adServicesSettingsActivity,
            FragmentManager fragmentManager,
            MainViewModel mainViewModel,
            TopicsViewModel topicsViewModel,
            AppsViewModel appsViewModel) {
        mAdServicesSettingsActivity = adServicesSettingsActivity;
        mFragmentManager = fragmentManager;
        mMainViewModel = mainViewModel;
        mTopicsViewModel = topicsViewModel;
        mAppsViewModel = appsViewModel;

        listenToMainViewModelUiEvents();
        listenToTopicsViewModelUiEvents();
        listenToAppsViewModelUiEvents();
    }

    // ---------------------------------------------------------------------------------------------
    // UI Event Listeners
    // ---------------------------------------------------------------------------------------------

    private void listenToMainViewModelUiEvents() {
        mMainViewModel
                .getUiEvents()
                .observe(
                        mAdServicesSettingsActivity,
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
                                    case DISPLAY_APPS_FRAGMENT:
                                        mFragmentManager
                                                .beginTransaction()
                                                .replace(
                                                        R.id.fragment_container_view,
                                                        AdServicesSettingsAppsFragment.class,
                                                        null)
                                                .setReorderingAllowed(true)
                                                .addToBackStack(null)
                                                .commit();
                                        mAppsViewModel.refresh();
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
                                        mTopicsViewModel.refresh();
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
                        mAdServicesSettingsActivity,
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
                                        mTopicsViewModel.refresh();
                                        break;
                                }
                            } finally {
                                mTopicsViewModel.uiEventHandled();
                            }
                        });
    }

    private void listenToAppsViewModelUiEvents() {
        mAppsViewModel
                .getUiEvents()
                .observe(
                        mAdServicesSettingsActivity,
                        eventAppPair -> {
                            AppsViewModel.AppsViewModelUiEvent event = eventAppPair.first;
                            App app = eventAppPair.second;
                            if (event == null) {
                                return;
                            }
                            switch (event) {
                                case BLOCK_APP:
                                    try {
                                        mAppsViewModel.revokeAppConsent(app);
                                    } catch (IOException e) {
                                        Toast.makeText(
                                                mMainViewModel.getApplication(),
                                                "Block app failed",
                                                Toast.LENGTH_SHORT);
                                    }
                                    break;
                                case RESTORE_APP:
                                    try {
                                        mAppsViewModel.restoreAppConsent(app);
                                    } catch (IOException e) {
                                        Toast.makeText(
                                                mMainViewModel.getApplication(),
                                                "Unblock app failed",
                                                Toast.LENGTH_SHORT);
                                    }
                                    break;
                                case DISPLAY_BLOCKED_APPS_FRAGMENT:
                                    mFragmentManager
                                            .beginTransaction()
                                            .replace(
                                                    R.id.fragment_container_view,
                                                    AdServicesSettingsBlockedAppsFragment.class,
                                                    null)
                                            .setReorderingAllowed(true)
                                            .addToBackStack(null)
                                            .commit();
                                    mAppsViewModel.refresh();
                                    break;
                            }
                        });
    }

    // ---------------------------------------------------------------------------------------------
    // Main Fragment
    // ---------------------------------------------------------------------------------------------

    /**
     * Configure all UI elements in {@link AdServicesSettingsMainFragment} to handle user
     * actions.
     *
     * @param fragment the fragment to be initialized.
     */
    public void initMainFragment(AdServicesSettingsMainFragment fragment) {
        mAdServicesSettingsActivity.setTitle(R.string.settingsUI_main_view_title);
        configureConsentSwitch(fragment);
        configureTopicsButton();
        configureAppsButton();
    }

    private void configureConsentSwitch(AdServicesSettingsMainFragment fragment) {
        MainSwitchBar mainSwitchBar =
                mAdServicesSettingsActivity.findViewById(R.id.main_switch_bar);

        mMainViewModel.getConsent().observe(fragment, mainSwitchBar::setChecked);

        mainSwitchBar.setOnClickListener(
                switchBar -> mMainViewModel.consentSwitchClickHandler(
                        ((MainSwitchBar) switchBar).isChecked()));
    }

    private void configureTopicsButton() {
        View topicsButton = mAdServicesSettingsActivity.findViewById(R.id.topics_preference);

        topicsButton.setOnClickListener(preference -> mMainViewModel.topicsButtonClickHandler());
    }

    private void configureAppsButton() {
        View appsButton = mAdServicesSettingsActivity.findViewById(R.id.apps_preference);

        appsButton.setOnClickListener(preference -> mMainViewModel.appsButtonClickHandler());
    }

    // ---------------------------------------------------------------------------------------------
    // Topics Fragment
    // ---------------------------------------------------------------------------------------------

    /**
     * Configure all UI elements (except topics list) in {@link AdServicesSettingsTopicsFragment} to
     * handle user actions.
     */
    public void initTopicsFragment(AdServicesSettingsTopicsFragment fragment) {
        mAdServicesSettingsActivity.setTitle(R.string.settingsUI_topics_view_title);
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

    // ---------------------------------------------------------------------------------------------
    // Apps Fragment
    // ---------------------------------------------------------------------------------------------

    /**
     * Configure all UI elements (except apps list) in {@link AdServicesSettingsAppsFragment} to
     * handle user actions.
     */
    public void initAppsFragment(AdServicesSettingsAppsFragment fragment) {
        mAdServicesSettingsActivity.setTitle(R.string.settingsUI_apps_view_title);
        configureBlockedAppsFragmentButton(fragment);
    }

    private void configureBlockedAppsFragmentButton(AdServicesSettingsAppsFragment fragment) {
        View blockedAppsButton = fragment.requireView().findViewById(R.id.blocked_apps_button);

        blockedAppsButton.setOnClickListener(
                view -> {
                    mAppsViewModel.blockedAppsFragmentButtonClickHandler();
                });
    }

    // ---------------------------------------------------------------------------------------------
    // Blocked Topics Fragment
    // ---------------------------------------------------------------------------------------------

    /**
     * Configure all UI elements (except blocked topics list) in
     * {@link AdServicesSettingsBlockedTopicsFragment} to handle user actions.
     */
    public void initBlockedTopicsFragment() {
        mAdServicesSettingsActivity.setTitle(R.string.settingsUI_blocked_topics_title);
    }

    // ---------------------------------------------------------------------------------------------
    // Blocked Apps Fragment
    // ---------------------------------------------------------------------------------------------

    /**
     * Configure all UI elements (except blocked apps list) in
     * {@link AdServicesSettingsBlockedAppsFragment} to handle user actions.
     */
    public void initBlockedAppsFragment() {
        mAdServicesSettingsActivity.setTitle(R.string.settingsUI_blocked_apps_title);
    }

}
