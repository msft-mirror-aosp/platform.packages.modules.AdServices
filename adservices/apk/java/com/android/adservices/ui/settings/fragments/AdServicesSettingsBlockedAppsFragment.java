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

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.adservices.api.R;
import com.android.adservices.ui.settings.ActionDelegate;
import com.android.adservices.ui.settings.AdServicesSettingsActivity;
import com.android.adservices.ui.settings.viewadatpors.AppsListViewAdapter;
import com.android.adservices.ui.settings.viewmodels.AppsViewModel;

/** Fragment for the blocked apps view of the AdServices Settings App. */
public class AdServicesSettingsBlockedAppsFragment extends Fragment {

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.blocked_apps_fragment, container, false);

        setupViewModel(rootView);
        initActionListeners();

        return rootView;
    }

    // initialize all action listeners except for actions in blocked apps list
    private void initActionListeners() {
        ActionDelegate actionDelegate =
                ((AdServicesSettingsActivity) requireActivity()).getActionDelegate();
        actionDelegate.initBlockedAppsFragment();
    }

    /**
     * Initializes view model connection with blocked apps list. (Action listeners for each item in
     * the list will be handled by the adapter).
     */
    private void setupViewModel(View rootView) {
        AppsViewModel viewModel =
                ((AdServicesSettingsActivity) requireActivity())
                        .getViewModelProvider()
                        .get(AppsViewModel.class);
        RecyclerView recyclerView = rootView.findViewById(R.id.blocked_apps_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        AppsListViewAdapter adapter = new AppsListViewAdapter(viewModel, true);
        recyclerView.setAdapter(adapter);
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        View noBlockedAppsMessage = rootView.findViewById(R.id.no_blocked_apps_message);

        viewModel
                .getBlockedApps()
                .observe(getViewLifecycleOwner(), blockedAppsList -> {
                    noBlockedAppsMessage.setVisibility(
                            blockedAppsList.isEmpty() ? View.VISIBLE : View.GONE);
                    adapter.notifyDataSetChanged();
                });
    }
}
