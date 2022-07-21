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

import static com.android.adservices.ui.notifications.ConsentNotificationConfirmationFragment.IS_CONSENT_GIVEN_ARGUMENT_KEY;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.android.adservices.api.R;
import com.android.adservices.ui.settings.AdServicesSettingsActivity;

/** Fragment for the topics view of the AdServices Settings App. */
public class ConsentNotificationFragment extends Fragment {
    public static final String EEA_DEVICE = "com.google.android.feature.EEA_DEVICE";
    public static final String IS_EU_DEVICE_ARGUMENT_KEY = "isEUDevice";

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return setupActivity(inflater, container);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        setupListeners();
    }

    private View setupActivity(LayoutInflater inflater, ViewGroup container) {
        boolean isEUDevice =
                requireActivity().getIntent().getBooleanExtra(IS_EU_DEVICE_ARGUMENT_KEY, true);
        View rootView;
        if (isEUDevice) {
            rootView =
                    inflater.inflate(R.layout.consent_notification_fragment_eu, container, false);
        } else {
            rootView = inflater.inflate(R.layout.consent_notification_fragment, container, false);
        }
        return rootView;
    }

    private void setupListeners() {
        boolean isEUDevice =
                requireActivity().getIntent().getBooleanExtra(IS_EU_DEVICE_ARGUMENT_KEY, true);

        TextView howItWorksExpander = requireActivity().findViewById(R.id.how_it_works_expander);
        howItWorksExpander.setOnClickListener(
                view -> {
                    View text = requireActivity().findViewById(R.id.how_it_works_expanded_text);
                    if (text.getVisibility() == View.VISIBLE) {
                        text.setVisibility(View.GONE);
                        howItWorksExpander.setCompoundDrawablesRelativeWithIntrinsicBounds(
                                0, 0, R.drawable.ic_expand, 0);
                    } else {
                        text.setVisibility(View.VISIBLE);
                        howItWorksExpander.setCompoundDrawablesRelativeWithIntrinsicBounds(
                                0, 0, R.drawable.ic_minimize, 0);
                    }
                });

        Button leftControlButton = requireActivity().findViewById(R.id.leftControlButton);
        leftControlButton.setOnClickListener(
                view -> {
                    if (isEUDevice) {
                        // opt-out confirmation activity
                        Bundle args = new Bundle();
                        args.putBoolean(IS_CONSENT_GIVEN_ARGUMENT_KEY, false);
                        startConfirmationFragment(args);
                    } else {
                        // go to settings activity
                        Intent intent =
                                new Intent(requireActivity(), AdServicesSettingsActivity.class);
                        startActivity(intent);
                    }
                });

        Button rightControlButton = requireActivity().findViewById(R.id.rightControlButton);
        rightControlButton.setOnClickListener(
                view -> {
                    if (isEUDevice) {
                        // opt-in confirmation activity
                        Bundle args = new Bundle();
                        args.putBoolean(IS_CONSENT_GIVEN_ARGUMENT_KEY, true);
                        startConfirmationFragment(args);
                    } else {
                        // acknowledge and dismiss
                        requireActivity().finish();
                    }
                });
    }

    private void startConfirmationFragment(Bundle args) {
        requireActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(
                        R.id.fragment_container_view,
                        ConsentNotificationConfirmationFragment.class,
                        args)
                .commit();
    }
}
