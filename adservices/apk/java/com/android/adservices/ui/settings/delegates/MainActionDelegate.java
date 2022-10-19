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
package com.android.adservices.ui.settings.delegates;

import android.content.Intent;
import android.view.View;

import androidx.lifecycle.Observer;

import com.android.adservices.api.R;
import com.android.adservices.service.PhFlags;
import com.android.adservices.ui.settings.DialogManager;
import com.android.adservices.ui.settings.activities.AdServicesSettingsMainActivity;
import com.android.adservices.ui.settings.activities.AppsActivity;
import com.android.adservices.ui.settings.activities.TopicsActivity;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsMainFragment;
import com.android.adservices.ui.settings.viewmodels.MainViewModel;
import com.android.settingslib.widget.MainSwitchBar;

/**
 * Delegate class that helps AdServices Settings fragments to respond to all view model/user events.
 */
public class MainActionDelegate extends BaseActionDelegate {
    private final AdServicesSettingsMainActivity mAdservicesSettingsMainActivity;
    private final MainViewModel mMainViewModel;

    public MainActionDelegate(
            AdServicesSettingsMainActivity mainSettingsActivity, MainViewModel mainViewModel) {
        super(mainSettingsActivity);
        mAdservicesSettingsMainActivity = mainSettingsActivity;
        mMainViewModel = mainViewModel;

        listenToMainViewModelUiEvents();
    }

    private void listenToMainViewModelUiEvents() {
        Observer<MainViewModel.MainViewModelUiEvent> observer =
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
                                if (PhFlags.getInstance().getUIDialogsFeatureEnabled()) {
                                    DialogManager.showOptOutDialog(
                                            mAdservicesSettingsMainActivity, mMainViewModel);
                                } else {
                                    mMainViewModel.setConsent(false);
                                }
                                break;
                            case DISPLAY_APPS_FRAGMENT:
                                logUIAction(ActionEnum.MANAGE_APPS_SELECTED);
                                mAdservicesSettingsMainActivity.startActivity(
                                        new Intent(
                                                mAdservicesSettingsMainActivity,
                                                AppsActivity.class));
                                break;
                            case DISPLAY_TOPICS_FRAGMENT:
                                logUIAction(ActionEnum.MANAGE_TOPICS_SELECTED);
                                mAdservicesSettingsMainActivity.startActivity(
                                        new Intent(
                                                mAdservicesSettingsMainActivity,
                                                TopicsActivity.class));
                                break;
                        }
                    } finally {
                        mMainViewModel.uiEventHandled();
                    }
                };
        mMainViewModel.getUiEvents().observe(mAdservicesSettingsMainActivity, observer);
    }

    /**
     * Configure all UI elements in {@link AdServicesSettingsMainFragment} to handle user actions.
     *
     * @param fragment the fragment to be initialized.
     */
    public void initMainFragment(AdServicesSettingsMainFragment fragment) {
        mAdservicesSettingsMainActivity.setTitle(R.string.settingsUI_main_view_title);
        configureConsentSwitch(fragment);
        configureTopicsButton(fragment);
        configureAppsButton(fragment);
    }

    private void configureConsentSwitch(AdServicesSettingsMainFragment fragment) {
        MainSwitchBar mainSwitchBar =
                mAdservicesSettingsMainActivity.findViewById(R.id.main_switch_bar);

        mMainViewModel.getConsent().observe(fragment, mainSwitchBar::setChecked);

        mainSwitchBar.setOnClickListener(
                switchBar -> mMainViewModel.consentSwitchClickHandler((MainSwitchBar) switchBar));
    }

    private void configureTopicsButton(AdServicesSettingsMainFragment fragment) {
        View topicsButton = fragment.requireView().findViewById(R.id.topics_preference);

        topicsButton.setOnClickListener(preference -> mMainViewModel.topicsButtonClickHandler());
    }

    private void configureAppsButton(AdServicesSettingsMainFragment fragment) {
        View appsButton = fragment.requireView().findViewById(R.id.apps_preference);

        appsButton.setOnClickListener(preference -> mMainViewModel.appsButtonClickHandler());
    }
}
