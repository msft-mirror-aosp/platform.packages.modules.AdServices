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
import android.widget.TextView;

import androidx.lifecycle.Observer;

import com.android.adservices.api.R;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.PhFlags;
import com.android.adservices.ui.settings.DialogManager;
import com.android.adservices.ui.settings.activities.AdServicesSettingsMainActivity;
import com.android.adservices.ui.settings.activities.AppsActivity;
import com.android.adservices.ui.settings.activities.MeasurementActivity;
import com.android.adservices.ui.settings.activities.TopicsActivity;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsMainFragment;
import com.android.adservices.ui.settings.viewmodels.MainViewModel;
import com.android.settingslib.widget.MainSwitchBar;

/**
 * Delegate class that helps AdServices Settings fragments to respond to all view model/user events.
 */
public class MainActionDelegate extends BaseActionDelegate {
    private final AdServicesSettingsMainActivity mAdServicesSettingsMainActivity;
    private final MainViewModel mMainViewModel;

    public MainActionDelegate(
            AdServicesSettingsMainActivity mainSettingsActivity, MainViewModel mainViewModel) {
        super(mainSettingsActivity);
        mAdServicesSettingsMainActivity = mainSettingsActivity;
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
                                            mAdServicesSettingsMainActivity, mMainViewModel);
                                } else {
                                    mMainViewModel.setConsent(false);
                                }
                                break;
                            case DISPLAY_APPS_FRAGMENT:
                                logUIAction(ActionEnum.MANAGE_APPS_SELECTED);
                                mAdServicesSettingsMainActivity.startActivity(
                                        new Intent(
                                                mAdServicesSettingsMainActivity,
                                                AppsActivity.class));
                                break;
                            case DISPLAY_TOPICS_FRAGMENT:
                                logUIAction(ActionEnum.MANAGE_TOPICS_SELECTED);
                                mAdServicesSettingsMainActivity.startActivity(
                                        new Intent(
                                                mAdServicesSettingsMainActivity,
                                                TopicsActivity.class));
                                break;
                            case DISPLAY_MEASUREMENT_FRAGMENT:
                                logUIAction(ActionEnum.MANAGE_MEASUREMENT_SELECTED);
                                mAdServicesSettingsMainActivity.startActivity(
                                        new Intent(
                                                mAdServicesSettingsMainActivity,
                                                MeasurementActivity.class));
                                break;
                        }
                    } finally {
                        mMainViewModel.uiEventHandled();
                    }
                };
        mMainViewModel.getUiEvents().observe(mAdServicesSettingsMainActivity, observer);
    }

    /**
     * Configure all UI elements in {@link AdServicesSettingsMainFragment} to handle user actions.
     *
     * @param fragment the fragment to be initialized.
     */
    public void initMainFragment(AdServicesSettingsMainFragment fragment) {
        // Hide the main toggle and the entry point of Measurement
        // in Main page behind the GaUxFeature Flag

        int[] betaLayout =
                new int[] {
                    R.id.main_switch_bar,
                    R.id.above_pic_paragraph,
                    R.id.main_view_pic,
                    R.id.main_view_footer
                };

        int[] gaUxLayout = new int[] {R.id.main_view_ga_pic, R.id.main_view_ga_footer};

        if (FlagsFactory.getFlags().getGaUxFeatureEnabled()) {
            mAdServicesSettingsMainActivity.setTitle(R.string.settingsUI_main_view_ga_title);
            setLayoutVisibility(betaLayout, View.GONE);
            setLayoutVisibility(gaUxLayout, View.VISIBLE);

            configureMeasurementButton(fragment);
        } else {
            mAdServicesSettingsMainActivity.setTitle(R.string.settingsUI_main_view_title);
            setLayoutVisibility(betaLayout, View.VISIBLE);
            setLayoutVisibility(gaUxLayout, View.GONE);

            configureConsentSwitch(fragment);
        }

        configureTopicsButton(fragment);
        configureAppsButton(fragment);
    }

    private void setLayoutVisibility(int[] layoutList, int visibility) {
        for (int each : layoutList) {
            mAdServicesSettingsMainActivity.findViewById(each).setVisibility(visibility);
        }
    }

    private void configureConsentSwitch(AdServicesSettingsMainFragment fragment) {
        MainSwitchBar mainSwitchBar =
                mAdServicesSettingsMainActivity.findViewById(R.id.main_switch_bar);

        mMainViewModel.getConsent().observe(fragment, mainSwitchBar::setChecked);

        mainSwitchBar.setOnClickListener(
                switchBar -> mMainViewModel.consentSwitchClickHandler((MainSwitchBar) switchBar));
    }

    private void configureTopicsButton(AdServicesSettingsMainFragment fragment) {
        if (FlagsFactory.getFlags().getGaUxFeatureEnabled()) {
            TextView topicsPreferenceTitle =
                    fragment.requireView().findViewById(R.id.topics_preference_title);
            topicsPreferenceTitle.setText(R.string.settingsUI_topics_ga_title);
        }
        View topicsButton = fragment.requireView().findViewById(R.id.topics_preference);
        topicsButton.setOnClickListener(preference -> mMainViewModel.topicsButtonClickHandler());
    }

    private void configureAppsButton(AdServicesSettingsMainFragment fragment) {
        if (FlagsFactory.getFlags().getGaUxFeatureEnabled()) {
            TextView appsPreferenceTitle =
                    fragment.requireView().findViewById(R.id.apps_preference_title);
            appsPreferenceTitle.setText(R.string.settingsUI_apps_ga_title);
        }
        View appsButton = fragment.requireView().findViewById(R.id.apps_preference);
        appsButton.setOnClickListener(preference -> mMainViewModel.appsButtonClickHandler());
    }

    private void configureMeasurementButton(AdServicesSettingsMainFragment fragment) {
        View measurementButton = fragment.requireView().findViewById(R.id.measurement_preference);
        measurementButton.setVisibility(View.VISIBLE);
        measurementButton.setOnClickListener(
                preference -> mMainViewModel.measurementClickHandler());
    }

    /**
     * Configure the subtitles of topics/apps/measurement that can display the state of their
     * preferences (ON or OFF of the consent of topics/apps/measurement and the number of
     * topics/apps with consent) on the Settings main page
     *
     * @param fragment the fragment to be initialized.
     */
    public void configureSubtitles(AdServicesSettingsMainFragment fragment) {
        configureMeasurementSubtitle(fragment);
        configureAppsSubtitle(fragment);
        configureTopicsSubtitle(fragment);
    }

    /**
     * Configure the subtitle of measurement that can display the state (ON or OFF) of the
     * measurement consent on the Settings main page
     *
     * @param fragment the fragment to be initialized.
     */
    private void configureMeasurementSubtitle(AdServicesSettingsMainFragment fragment) {
        TextView measurementSubtitle =
                fragment.requireView().findViewById(R.id.measurement_preference_subtitle);
        if (mMainViewModel.getMeasurementConsentFromConsentManager()) {
            measurementSubtitle.setText(R.string.settingsUI_subtitle_consent_on);
        } else {
            measurementSubtitle.setText(R.string.settingsUI_subtitle_consent_off);
        }
    }

    /**
     * Configure the subtitle of topics that can display the state of topics preference (ON or OFF
     * of the topic consent and the number of topics with consent) on the Settings main page
     *
     * @param fragment the fragment to be initialized.
     */
    private void configureTopicsSubtitle(AdServicesSettingsMainFragment fragment) {
        TextView topicsSubtitle =
                fragment.requireView().findViewById(R.id.topics_preference_subtitle);
        topicsSubtitle.setVisibility(View.VISIBLE);
        if (mMainViewModel.getTopicsConsentFromConsentManager()) {
            topicsSubtitle.setText(
                    mAdServicesSettingsMainActivity
                            .getResources()
                            .getString(
                                    R.string.settingsUI_topics_subtitle,
                                    mMainViewModel.getCountOfTopics()));
        } else {
            topicsSubtitle.setText(R.string.settingsUI_subtitle_consent_off);
        }
    }

    /**
     * Configure the subtitle of apps that can display the state of apps preference (ON or OFF of
     * the topic consent and the number of topics with consent) on the Settings main page
     *
     * @param fragment the fragment to be initialized.
     */
    private void configureAppsSubtitle(AdServicesSettingsMainFragment fragment) {
        TextView appsSubtitle = fragment.requireView().findViewById(R.id.apps_preference_subtitle);
        appsSubtitle.setVisibility(View.VISIBLE);
        if (mMainViewModel.getAppsConsentFromConsentManager()) {
            appsSubtitle.setText(
                    mAdServicesSettingsMainActivity
                            .getResources()
                            .getString(
                                    R.string.settingsUI_apps_subtitle,
                                    mMainViewModel.getCountOfApps()));
        } else {
            appsSubtitle.setText(R.string.settingsUI_subtitle_consent_off);
        }
    }
}
