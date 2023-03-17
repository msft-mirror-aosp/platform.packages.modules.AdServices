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
package com.android.adservices.ui.ganotifications;

import static com.android.adservices.ui.notifications.ConsentNotificationActivity.NotificationFragmentEnum.CONFIRMATION_PAGE_DISMISSED;
import static com.android.adservices.ui.notifications.ConsentNotificationActivity.NotificationFragmentEnum.CONFIRMATION_PAGE_DISPLAYED;
import static com.android.adservices.ui.notifications.ConsentNotificationActivity.NotificationFragmentEnum.CONFIRMATION_PAGE_OPT_IN_GOT_IT_BUTTON_CLICKED;
import static com.android.adservices.ui.notifications.ConsentNotificationActivity.NotificationFragmentEnum.CONFIRMATION_PAGE_OPT_IN_MORE_INFO_CLICKED;
import static com.android.adservices.ui.notifications.ConsentNotificationActivity.NotificationFragmentEnum.CONFIRMATION_PAGE_OPT_IN_SETTINGS_CLICKED;
import static com.android.adservices.ui.notifications.ConsentNotificationActivity.NotificationFragmentEnum.CONFIRMATION_PAGE_OPT_OUT_GOT_IT_BUTTON_CLICKED;
import static com.android.adservices.ui.notifications.ConsentNotificationActivity.NotificationFragmentEnum.CONFIRMATION_PAGE_OPT_OUT_MORE_INFO_CLICKED;
import static com.android.adservices.ui.notifications.ConsentNotificationActivity.NotificationFragmentEnum.CONFIRMATION_PAGE_OPT_OUT_SETTINGS_CLICKED;
import static com.android.adservices.ui.settings.activities.AdServicesSettingsMainActivity.FROM_NOTIFICATION_KEY;

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
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.ui.notifications.ConsentNotificationActivity;
import com.android.adservices.ui.settings.activities.AdServicesSettingsMainActivity;

/**
 * Fragment for the confirmation view after accepting or rejecting to be part of Privacy Sandbox
 * Beta.
 */
public class ConsentNotificationConfirmationGaFragment extends Fragment {
    public static final String IS_FlEDGE_MEASUREMENT_INFO_VIEW_EXPANDED_KEY =
            "is_fledge_measurement_info_view_expanded";
    private boolean mIsInfoViewExpanded = false;

    private boolean mTopicsOptIn;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        AdServicesApiConsent topicsConsent =
                ConsentManager.getInstance(requireContext()).getConsent(AdServicesApiType.TOPICS);
        mTopicsOptIn = topicsConsent != null ? topicsConsent.isGiven() : false;

        ConsentManager.getInstance(requireContext())
                .enable(requireContext(), AdServicesApiType.FLEDGE);
        ConsentManager.getInstance(requireContext())
                .enable(requireContext(), AdServicesApiType.MEASUREMENTS);
        return inflater.inflate(
                R.layout.consent_notification_fledge_measurement_fragment_eu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        setupListeners(savedInstanceState);

        ConsentNotificationActivity.handleAction(CONFIRMATION_PAGE_DISPLAYED, getContext());
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);

        ConsentNotificationActivity.handleAction(CONFIRMATION_PAGE_DISMISSED, getContext());
    }

    private void setupListeners(Bundle savedInstanceState) {
        TextView howItWorksExpander =
                requireActivity().findViewById(R.id.how_it_works_fledge_measurement_expander);
        if (savedInstanceState != null) {
            setInfoViewState(
                    savedInstanceState.getBoolean(
                            IS_FlEDGE_MEASUREMENT_INFO_VIEW_EXPANDED_KEY, false));
        }
        howItWorksExpander.setOnClickListener(
                view -> {
                    if (mTopicsOptIn) {
                        ConsentNotificationActivity.handleAction(
                                CONFIRMATION_PAGE_OPT_IN_MORE_INFO_CLICKED, getContext());
                    } else {
                        ConsentNotificationActivity.handleAction(
                                CONFIRMATION_PAGE_OPT_OUT_MORE_INFO_CLICKED, getContext());
                    }

                    setInfoViewState(!mIsInfoViewExpanded);
                });

        Button leftControlButton =
                requireActivity().findViewById(R.id.leftControlButtonConfirmation);
        leftControlButton.setOnClickListener(
                view -> {
                    if (mTopicsOptIn) {
                        ConsentNotificationActivity.handleAction(
                                CONFIRMATION_PAGE_OPT_IN_SETTINGS_CLICKED, getContext());
                    } else {
                        ConsentNotificationActivity.handleAction(
                                CONFIRMATION_PAGE_OPT_OUT_SETTINGS_CLICKED, getContext());
                    }

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
                    if (mTopicsOptIn) {
                        ConsentNotificationActivity.handleAction(
                                CONFIRMATION_PAGE_OPT_IN_GOT_IT_BUTTON_CLICKED, getContext());
                    } else {
                        ConsentNotificationActivity.handleAction(
                                CONFIRMATION_PAGE_OPT_OUT_GOT_IT_BUTTON_CLICKED, getContext());
                    }

                    // acknowledge and dismiss
                    requireActivity().finish();
                });
    }

    private void setInfoViewState(boolean expanded) {
        View text =
                requireActivity().findViewById(R.id.how_it_works_fledge_measurement_expanded_text);
        TextView expander =
                requireActivity().findViewById(R.id.how_it_works_fledge_measurement_expander);
        if (expanded) {
            mIsInfoViewExpanded = true;
            text.setVisibility(View.VISIBLE);
            expander.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    0, 0, R.drawable.ic_minimize, 0);
        } else {
            mIsInfoViewExpanded = false;
            text.setVisibility(View.GONE);
            expander.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_expand, 0);
        }
    }
}
