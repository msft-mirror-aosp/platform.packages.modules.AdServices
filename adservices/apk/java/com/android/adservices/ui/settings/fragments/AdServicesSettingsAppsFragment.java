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
package com.android.adservices.ui.settings.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.adservices.api.R;
import com.android.adservices.ui.settings.ActionDelegate;
import com.android.adservices.ui.settings.AdServicesSettingsActivity;
import com.android.adservices.ui.settings.viewadatpors.AppsListViewAdapter;
import com.android.adservices.ui.settings.viewmodels.AppsViewModel;

/** Fragment for the apps view of the AdServices Settings App. */
public class AdServicesSettingsAppsFragment extends Fragment {

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.apps_fragment, container, false);

        setupViewModel(rootView);

        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        initActionListeners();
    }

    // initialize all action listeners except for actions in topics list
    private void initActionListeners() {
        ActionDelegate actionDelegate =
                ((AdServicesSettingsActivity) requireActivity()).getActionDelegate();
        actionDelegate.initAppsFragment(this);
    }

    // initializes view model connection with topics list.
    // (Action listeners for each item in the list will be handled by the adapter)
    private void setupViewModel(View rootView) {
        AppsViewModel viewModel =
                ((AdServicesSettingsActivity) requireActivity())
                        .getViewModelProvider()
                        .get(AppsViewModel.class);
        RecyclerView recyclerView = rootView.findViewById(R.id.apps_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        AppsListViewAdapter adapter = new AppsListViewAdapter(viewModel, false);
        recyclerView.setAdapter(adapter);

        View noAppsMessage = rootView.findViewById(R.id.no_apps_message);
        View emptyAppsHiddenSection = rootView.findViewById(R.id.empty_apps_hidden_section);
        View blockedAppsBtn = rootView.findViewById(R.id.blocked_apps_button);

        // "Empty State": the state when the non-blocked list of apps/topics is empty.
        // blocked_apps_when_empty_state_button is added to noAppsMessage
        // noAppsMessages is visible only when Empty State
        // blocked_apps_when_empty_state_button differs from blocked_apps_button
        // in style with rounded corners, centered, blue/grey
        viewModel
                .getApps()
                .observe(
                        getViewLifecycleOwner(),
                        appsList -> {
                            if (appsList.isEmpty()) {
                                noAppsMessage.setVisibility(View.VISIBLE);
                                emptyAppsHiddenSection.setVisibility(View.GONE);
                                blockedAppsBtn.setVisibility(View.GONE);
                            } else {
                                noAppsMessage.setVisibility(View.GONE);
                                emptyAppsHiddenSection.setVisibility(View.VISIBLE);
                                blockedAppsBtn.setVisibility(View.VISIBLE);
                            }
                            adapter.notifyDataSetChanged();
                        });

        Button blockedAppsWhenEmptyStateButton =
                rootView.findViewById(R.id.blocked_apps_when_empty_state_button);
        viewModel
                .getBlockedApps()
                .observe(
                        getViewLifecycleOwner(),
                        blockedAppsList -> {
                            if (blockedAppsList.isEmpty()) {
                                blockedAppsWhenEmptyStateButton.setEnabled(false);
                                blockedAppsWhenEmptyStateButton.setAlpha(
                                        getResources().getFloat(R.dimen.disabled_button_alpha));
                                blockedAppsWhenEmptyStateButton.setText(
                                        R.string.settingsUI_apps_view_no_blocked_apps_text);
                            } else {
                                blockedAppsWhenEmptyStateButton.setEnabled(true);
                                blockedAppsWhenEmptyStateButton.setAlpha(
                                        getResources().getFloat(R.dimen.enabled_button_alpha));
                                blockedAppsWhenEmptyStateButton.setText(
                                        R.string.settingsUI_blocked_apps_title);
                            }
                        });
    }
}
