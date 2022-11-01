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
import android.util.Log;
import android.util.Pair;
import android.view.View;

import androidx.lifecycle.Observer;

import com.android.adservices.api.R;
import com.android.adservices.service.PhFlags;
import com.android.adservices.service.consent.App;
import com.android.adservices.ui.settings.DialogManager;
import com.android.adservices.ui.settings.activities.AppsActivity;
import com.android.adservices.ui.settings.activities.BlockedAppsActivity;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsAppsFragment;
import com.android.adservices.ui.settings.viewmodels.AppsViewModel;
import com.android.adservices.ui.settings.viewmodels.AppsViewModel.AppsViewModelUiEvent;

import java.io.IOException;

/**
 * Delegate class that helps AdServices Settings fragments to respond to all view model/user events.
 */
public class AppsActionDelegate extends BaseActionDelegate {
    private final AppsActivity mAppsActivity;
    private final AppsViewModel mAppsViewModel;

    public AppsActionDelegate(AppsActivity appsActivity, AppsViewModel appsViewModel) {
        super(appsActivity);
        mAppsActivity = appsActivity;
        mAppsViewModel = appsViewModel;
        listenToAppsViewModelUiEvents();
    }

    private void listenToAppsViewModelUiEvents() {
        Observer<Pair<AppsViewModelUiEvent, App>> observer =
                eventAppPair -> {
                    if (eventAppPair == null) {
                        return;
                    }
                    AppsViewModelUiEvent event = eventAppPair.first;
                    App app = eventAppPair.second;
                    if (event == null) {
                        return;
                    }
                    try {
                        switch (event) {
                            case BLOCK_APP:
                                logUIAction(ActionEnum.BLOCK_APP_SELECTED);
                                if (PhFlags.getInstance().getUIDialogsFeatureEnabled()) {
                                    DialogManager.showBlockAppDialog(
                                            mAppsActivity, mAppsViewModel, app);
                                } else {
                                    mAppsViewModel.revokeAppConsent(app);
                                }
                                break;
                            case RESET_APPS:
                                logUIAction(ActionEnum.RESET_APP_SELECTED);
                                if (PhFlags.getInstance().getUIDialogsFeatureEnabled()) {
                                    DialogManager.showResetAppDialog(mAppsActivity, mAppsViewModel);
                                } else {
                                    mAppsViewModel.resetApps();
                                }
                                break;
                            case DISPLAY_BLOCKED_APPS_FRAGMENT:
                                Intent intent =
                                        new Intent(mAppsActivity, BlockedAppsActivity.class);
                                mAppsActivity.startActivity(intent);
                                break;
                            default:
                                Log.e("AdservicesUI", "Unknown Action for UI Logging");
                        }
                    } catch (IOException e) {
                        Log.e(
                                "AdServicesUI",
                                "Error while processing AppsViewModelUiEvent " + event + ":" + e);
                    } finally {
                        mAppsViewModel.uiEventHandled();
                    }
                };
        mAppsViewModel.getUiEvents().observe(mAppsActivity, observer);
    }

    /**
     * Configure all UI elements (except apps list) in {@link AdServicesSettingsAppsFragment} to
     * handle user actions.
     */
    public void initAppsFragment(AdServicesSettingsAppsFragment fragment) {
        mAppsActivity.setTitle(R.string.settingsUI_apps_view_title);
        configureBlockedAppsFragmentButton(fragment);
        configureResetAppsButton(fragment);
    }

    private void configureBlockedAppsFragmentButton(AdServicesSettingsAppsFragment fragment) {
        View blockedAppsButton = fragment.requireView().findViewById(R.id.blocked_apps_button);
        View blockedAppsWhenEmptyListButton =
                fragment.requireView().findViewById(R.id.blocked_apps_when_empty_state_button);

        blockedAppsButton.setOnClickListener(
                view -> {
                    mAppsViewModel.blockedAppsFragmentButtonClickHandler();
                });
        blockedAppsWhenEmptyListButton.setOnClickListener(
                view -> {
                    mAppsViewModel.blockedAppsFragmentButtonClickHandler();
                });
    }

    private void configureResetAppsButton(AdServicesSettingsAppsFragment fragment) {
        View resetAppsButton = fragment.requireView().findViewById(R.id.reset_apps_button);

        resetAppsButton.setOnClickListener(view -> mAppsViewModel.resetAppsButtonClickHandler());
    }
}
