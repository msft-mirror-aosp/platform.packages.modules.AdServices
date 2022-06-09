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

import android.content.Context;
import android.os.Bundle;

import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

import com.android.adservices.LogUtil;
import com.android.adservices.api.R;

import java.io.IOException;
import java.util.Objects;

/**
 * Fragment for the main view of the AdServices Settings App.
 */
public class AdServicesSettingsMainFragment extends PreferenceFragmentCompat {
    public static final String ERROR_MESSAGE_VIEW_MODEL_EXCEPTION_WHILE_GET_CONSENT =
            "getConsent method failed. Will not change consent value in view model.";
    public static final String TOPICS_PREFERENCE_BUTTON_KEY = "topics_preference";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.main_preferences, rootKey);
        try {
            setupViewModel(requireContext());
        } catch (IOException e) {
            LogUtil.e(e, ERROR_MESSAGE_VIEW_MODEL_EXCEPTION_WHILE_GET_CONSENT);
        }
    }

    private void setupViewModel(Context context) throws IOException {
        SwitchPreference switchPreference = Objects.requireNonNull(findPreference(
                context.getResources().getString(
                        R.string.settingsUI_privacy_sandbox_beta_switch_key)));

        MainViewModel model;
        model = new ViewModelProvider(this).get(MainViewModel.class);
        model.getConsent().observe(this, switchPreference::setChecked);

        switchPreference.setOnPreferenceChangeListener((preference, newConsent) -> {
            try {
                model.setConsent((Boolean) newConsent);
                return true;
            } catch (IOException e) {
                LogUtil.e(e, ERROR_MESSAGE_VIEW_MODEL_EXCEPTION_WHILE_GET_CONSENT);
            }
            return false;
        });
    }
}
