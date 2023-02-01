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
package com.android.adservices.ui.notifications;

import static com.android.adservices.ui.settings.activities.AdServicesSettingsMainActivity.FROM_NOTIFICATION_KEY;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.android.adservices.api.R;
import com.android.adservices.service.stats.UiStatsLogger;
import com.android.adservices.ui.settings.activities.AdServicesSettingsMainActivity;

/**
 * Fragment for the confirmation view after accepting or rejecting to be part of Privacy Sandbox
 * Beta.
 */
public class ConsentNotificationConfirmationFragment extends Fragment {
    public static final String IS_CONSENT_GIVEN_ARGUMENT_KEY = "isConsentGiven";

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        boolean optedIn = getArguments().getBoolean(IS_CONSENT_GIVEN_ARGUMENT_KEY, false);
        if (optedIn) {
            return inflater.inflate(
                    R.layout.consent_notification_accept_confirmation_fragment, container, false);
        }
        return inflater.inflate(
                R.layout.consent_notification_decline_confirmation_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        UiStatsLogger.logConfirmationPageDisplayed(getContext());
        setupListeners();
    }

    private void setupListeners() {
        Button leftControlButton =
                requireActivity().findViewById(R.id.leftControlButtonConfirmation);
        leftControlButton.setOnClickListener(
                view -> {
                    // go to settings activity
                    Intent intent =
                            new Intent(requireActivity(), AdServicesSettingsMainActivity.class);
                    intent.putExtra(FROM_NOTIFICATION_KEY, true);
                    startActivity(intent);
                    requireActivity().finish();
                });

        Button rightControlButton =
                requireActivity().findViewById(R.id.rightControlButtonConfirmation);
        rightControlButton.setOnClickListener(
                view -> {
                    // acknowledge and dismiss
                    requireActivity().finish();
                });
    }
}
