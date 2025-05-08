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
package com.android.adservices.ui.settings.activitydelegates;

import static com.android.adservices.ui.settings.expressivefragments.MainActivityFragment.APPS_PREFERENCE;
import static com.android.adservices.ui.settings.expressivefragments.MainActivityFragment.MAIN_LEARN_MORE_LINK;
import static com.android.adservices.ui.settings.expressivefragments.MainActivityFragment.MAIN_VIEW_FOOTER;
import static com.android.adservices.ui.settings.expressivefragments.MainActivityFragment.MEASUREMENT_PREFERENCE;
import static com.android.adservices.ui.settings.expressivefragments.MainActivityFragment.TOPICS_PREFERENCE;

import android.content.Intent;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.lifecycle.Observer;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.android.adservices.api.R;
import com.android.adservices.service.stats.UiStatsLogger;
import com.android.adservices.ui.settings.activities.AdServicesSettingsMainActivity;
import com.android.adservices.ui.settings.activities.AppsActivity;
import com.android.adservices.ui.settings.activities.MeasurementActivity;
import com.android.adservices.ui.settings.activities.TopicsActivity;
import com.android.adservices.ui.settings.viewmodels.MainViewModel;
import com.android.settingslib.widget.FooterPreference;

import java.util.Objects;

/**
 * Delegate class that helps AdServices Settings fragments to respond to all view model/user events.
 */
@RequiresApi(Build.VERSION_CODES.S)
public class MainActivityFragmentActionDelegate extends BaseActionDelegate {
    private final MainViewModel mMainViewModel;
    private final PreferenceFragmentCompat mFragment;

    public MainActivityFragmentActionDelegate(
            AdServicesSettingsMainActivity mainSettingsActivity,
            MainViewModel mainViewModel,
            PreferenceFragmentCompat fragment) {
        super(mainSettingsActivity);
        mMainViewModel = mainViewModel;
        mFragment = fragment;
        listenToMainViewModelUiEvents();
    }

    /** Refreshes static views with data from view model that may have changed. */
    public void refreshState() {
        initWithUx();
    }

    @Override
    public void initGA() {
        mActivity.setTitle(R.string.settingsUI_main_view_ga_title);
        // topics button
        Preference topicsPreference =
                Objects.requireNonNull(mFragment.findPreference(TOPICS_PREFERENCE));
        topicsPreference.setOnPreferenceClickListener(
                preference -> {
                    mMainViewModel.topicsButtonClickHandler();
                    return true;
                });
        if (mMainViewModel.getTopicsConsentFromConsentManager()) {
            topicsPreference.setSummary(
                    getQuantityString(
                            mMainViewModel.getCountOfTopics(),
                            R.string.settingsUI_topics_subtitle_plural));
        } else {
            topicsPreference.setSummary(R.string.settingsUI_subtitle_consent_off);
        }
        // apps button
        Preference appsPreference =
                Objects.requireNonNull(mFragment.findPreference(APPS_PREFERENCE));
        appsPreference.setOnPreferenceClickListener(
                preference -> {
                    mMainViewModel.appsButtonClickHandler();
                    return true;
                });
        if (mMainViewModel.getAppsConsentFromConsentManager()) {
            appsPreference.setSummary(
                    getQuantityString(
                            mMainViewModel.getCountOfApps(),
                            R.string.settingsUI_apps_subtitle_plural));
        } else {
            appsPreference.setSummary(R.string.settingsUI_subtitle_consent_off);
        }
        // measurement button
        setMeasurement();

        // footer
        FooterPreference footer =
                Objects.requireNonNull(mFragment.findPreference(MAIN_VIEW_FOOTER));
        footer.setLearnMoreText(
                mActivity
                        .getResources()
                        .getString(R.string.settingsU_main_view_fragment_learn_more));
        footer.setLearnMoreAction(view -> setLinkAction(mActivity, MAIN_LEARN_MORE_LINK));
    }

    @Override
    public void initU18() {
        mActivity.setTitle(R.string.settingsUI_main_view_ga_title);
        // measurement button
        setMeasurement();
    }

    @Override
    public void initGaUxWithPas() {
        initGA();
    }

    private void listenToMainViewModelUiEvents() {
        Observer<MainViewModel.MainViewModelUiEvent> observer =
                event -> {
                    if (event == null) {
                        return;
                    }
                    try {
                        switch (event) {
                            case DISPLAY_APPS_FRAGMENT:
                                UiStatsLogger.logManageAppsSelected();
                                mActivity.startActivity(new Intent(mActivity, AppsActivity.class));
                                break;
                            case DISPLAY_TOPICS_FRAGMENT:
                                UiStatsLogger.logManageTopicsSelected();
                                mActivity.startActivity(
                                        new Intent(mActivity, TopicsActivity.class));
                                break;
                            case DISPLAY_MEASUREMENT_FRAGMENT:
                                UiStatsLogger.logManageMeasurementSelected();
                                mActivity.startActivity(
                                        new Intent(mActivity, MeasurementActivity.class));
                                break;
                        }
                    } finally {
                        mMainViewModel.uiEventHandled();
                    }
                };
        mMainViewModel.getUiEvents().removeObservers(mActivity);
        mMainViewModel.getUiEvents().observe(mActivity, observer);
    }

    private void setMeasurement() {
        Preference measurementPreference =
                Objects.requireNonNull(mFragment.findPreference(MEASUREMENT_PREFERENCE));
        measurementPreference.setOnPreferenceClickListener(
                preference -> {
                    mMainViewModel.measurementClickHandler();
                    return true;
                });
        int measurementSummaryResId =
                mMainViewModel.getMeasurementConsentFromConsentManager()
                        ? R.string.settingsUI_subtitle_consent_on
                        : R.string.settingsUI_subtitle_consent_off;
        measurementPreference.setSummary(measurementSummaryResId);
    }
}
