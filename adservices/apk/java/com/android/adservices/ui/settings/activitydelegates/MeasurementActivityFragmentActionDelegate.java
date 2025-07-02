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

import static com.android.adservices.ui.settings.expressivefragments.MeasurementActivityFragment.MEASUREMENT_FOOTER;
import static com.android.adservices.ui.settings.expressivefragments.MeasurementActivityFragment.MEASUREMENT_LEARN_MORE_LINK;
import static com.android.adservices.ui.settings.expressivefragments.MeasurementActivityFragment.MEASUREMENT_RESET_BUTTON;
import static com.android.adservices.ui.settings.expressivefragments.MeasurementActivityFragment.MEASUREMENT_SWITCH_BAR;

import android.os.Build;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.lifecycle.Observer;
import androidx.preference.Preference;

import com.android.adservices.api.R;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.UiStatsLogger;
import com.android.adservices.ui.settings.DialogFragmentManager;
import com.android.adservices.ui.settings.activities.MeasurementActivity;
import com.android.adservices.ui.settings.expressivefragments.MeasurementActivityFragment;
import com.android.adservices.ui.settings.viewmodels.MeasurementViewModel;
import com.android.adservices.ui.settings.viewmodels.MeasurementViewModel.MeasurementViewModelUiEvent;
import com.android.settingslib.widget.FooterPreference;
import com.android.settingslib.widget.MainSwitchPreference;

import java.util.Objects;

/**
 * Delegate class that helps AdServices Settings fragments to respond to all view model/user events.
 */
@RequiresApi(Build.VERSION_CODES.S)
public class MeasurementActivityFragmentActionDelegate extends BaseActionDelegate {
    private final MeasurementViewModel mMeasurementViewModel;
    private final MeasurementActivityFragment mFragment;

    public MeasurementActivityFragmentActionDelegate(
            MeasurementActivity measurementActivity,
            MeasurementViewModel measurementViewModel,
            MeasurementActivityFragment fragment) {
        super(measurementActivity);
        this.mMeasurementViewModel = measurementViewModel;
        this.mFragment = fragment;
        listenToMeasurementViewModelUiEvents();
    }

    @Override
    public void initGA() {
        mActivity.setTitle(R.string.settingsUI_measurement_ga_title);
        configureSharedElements();
    }

    @Override
    public void initU18() {
        mActivity.setTitle(R.string.settingsUI_measurement_ga_title);
        configureSharedElements();
    }

    @Override
    public void initGaUxWithPas() {
        initGA();
        FooterPreference footer =
                Objects.requireNonNull(mFragment.findPreference(MEASUREMENT_FOOTER));
        String footerSummary =
                mActivity
                        .getResources()
                        .getString(R.string.settingsUI_pas_msmt_view_fragment_footer);
        footer.setSummary(footerSummary);
        footer.setLearnMoreText(
                mActivity
                        .getResources()
                        .getString(R.string.settingsUI_pas_msmt_view_fragment_footer_learn_more));
        footer.setLearnMoreAction(view -> setLinkAction(mActivity, MEASUREMENT_LEARN_MORE_LINK));
    }

    private void configureSharedElements() {
        configureMeasurementConsentSwitch();

        // reset msmt button
        Preference resetMeasurementPreference =
                Objects.requireNonNull(mFragment.findPreference(MEASUREMENT_RESET_BUTTON));
        resetMeasurementPreference.setOnPreferenceClickListener(
                preference -> {
                    mMeasurementViewModel.resetMeasurementButtonClickHandler();
                    return true;
                });
    }

    private void configureMeasurementConsentSwitch() {
        MainSwitchPreference measurementSwitchToggle =
                Objects.requireNonNull(mFragment.findPreference(MEASUREMENT_SWITCH_BAR));
        measurementSwitchToggle.setOnPreferenceChangeListener(
                (preference, intendedDebugLoggingValue) -> {
                    mMeasurementViewModel.consentSwitchPreferenceClickHandler(
                            (MainSwitchPreference) measurementSwitchToggle);
                    return true;
                });
        mMeasurementViewModel
                .getMeasurementConsent()
                .observe(mFragment, measurementSwitchToggle::setChecked);
    }

    private void listenToMeasurementViewModelUiEvents() {
        Observer<MeasurementViewModelUiEvent> observer =
                event -> {
                    if (event == null) {
                        return;
                    }
                    try {
                        switch (event) {
                            case SWITCH_ON_MEASUREMENT:
                                if (FlagsFactory.getFlags().getToggleSpeedBumpEnabled()) {
                                    DialogFragmentManager.showOptInMeasurementDialog(mActivity);
                                }
                                mMeasurementViewModel.setMeasurementConsent(true);
                                break;
                            case SWITCH_OFF_MEASUREMENT:
                                if (FlagsFactory.getFlags().getToggleSpeedBumpEnabled()) {
                                    DialogFragmentManager.showOptOutMeasurementDialog(
                                            mActivity, mMeasurementViewModel);
                                } else {
                                    mMeasurementViewModel.setMeasurementConsent(false);
                                }
                                break;
                            case RESET_MEASUREMENT:
                                UiStatsLogger.logResetMeasurementSelected();
                                mMeasurementViewModel.resetMeasurement();
                                Toast.makeText(
                                                mActivity,
                                                R.string.settingsUI_measurement_are_reset,
                                                Toast.LENGTH_SHORT)
                                        .show();
                                break;
                        }
                    } finally {
                        mMeasurementViewModel.uiEventHandled();
                    }
                };
        mMeasurementViewModel.getUiEvents().observe(mActivity, observer);
    }
}
