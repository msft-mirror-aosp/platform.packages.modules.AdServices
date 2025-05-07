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

import static com.android.adservices.ui.settings.expressivefragments.BlockedAppsActivityFragment.BLOCKED_APPS_LIST;
import static com.android.adservices.ui.settings.expressivefragments.BlockedAppsActivityFragment.BLOCKED_APPS_NO_BLOCKED_APPS_MESSAGE;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.RequiresApi;
import androidx.lifecycle.Observer;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.adservices.api.R;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.App;
import com.android.adservices.service.stats.UiStatsLogger;
import com.android.adservices.ui.settings.DialogFragmentManager;
import com.android.adservices.ui.settings.DialogManager;
import com.android.adservices.ui.settings.activities.BlockedAppsActivity;
import com.android.adservices.ui.settings.expressivefragments.BlockedAppsActivityFragment;
import com.android.adservices.ui.settings.preferences.AdservicesTwoTargetPreference;
import com.android.adservices.ui.settings.viewmodels.BlockedAppsViewModel;

import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.util.Objects;

@RequiresApi(Build.VERSION_CODES.S)
public class BlockedAppsActivityFragmentActionDelegate extends BaseActionDelegate {
    private final BlockedAppsViewModel mBlockedAppsViewModel;
    private final BlockedAppsActivityFragment mFragment;

    public BlockedAppsActivityFragmentActionDelegate(
            BlockedAppsActivity blockedAppsActivity,
            BlockedAppsViewModel blockedAppsViewModel,
            BlockedAppsActivityFragment fragment) {
        super(blockedAppsActivity);
        this.mBlockedAppsViewModel = blockedAppsViewModel;
        this.mFragment = fragment;
        listenToBlockedAppsViewModelUiEvents();
    }

    @Override
    public void initGA() {
        mActivity.setTitle(R.string.settingsUI_blocked_topics_ga_title);
        configureSharedElements();
    }

    @Override
    public void initU18() {}

    @Override
    public void initGaUxWithPas() {
        initGA();
    }

    private void configureSharedElements() {
        mBlockedAppsViewModel.refresh();
        // no blocked topics message
        setUpAppsListObserve();
    }

    private void setUpAppsListObserve() {
        Preference noBlockedAppsGaMessage =
                (Preference)
                        Objects.requireNonNull(
                                mFragment.findPreference(BLOCKED_APPS_NO_BLOCKED_APPS_MESSAGE));
        PreferenceCategory apps_category =
                (PreferenceCategory)
                        Objects.requireNonNull(mFragment.findPreference(BLOCKED_APPS_LIST));

        Observer<ImmutableList<App>> blockedTopicsObserver =
                list -> {
                    apps_category.removeAll();
                    if (list.isEmpty()) {
                        noBlockedAppsGaMessage.setVisible(true);
                    } else {
                        noBlockedAppsGaMessage.setVisible(false);
                        for (App app : list) {
                            apps_category.addPreference(getAppPreference(app));
                        }
                    }
                };
        mBlockedAppsViewModel.getBlockedApps().observe(mActivity, blockedTopicsObserver);
    }

    private AdservicesTwoTargetPreference getAppPreference(App app) {
        Context context = mActivity.getApplicationContext();
        AdservicesTwoTargetPreference twoTargetPreference =
                new AdservicesTwoTargetPreference(
                        context, getResourcesString(R.string.settingsUI_unblock_app_title));
        twoTargetPreference.setOnClickListener(
                (v) -> mBlockedAppsViewModel.restoreAppConsentButtonClickHandler(app));
        twoTargetPreference.setTitle(app.getAppDisplayName(context.getPackageManager()));
        Drawable appIcon = app.getAppIcon(context);
        if (appIcon != null) {
            twoTargetPreference.setIcon(appIcon);
        }

        return twoTargetPreference;
    }

    private void listenToBlockedAppsViewModelUiEvents() {
        Observer<Pair<BlockedAppsViewModel.BlockedAppsViewModelUiEvent, App>> observer =
                eventAppPair -> {
                    if (eventAppPair == null) {
                        return;
                    }
                    BlockedAppsViewModel.BlockedAppsViewModelUiEvent event = eventAppPair.first;
                    App app = eventAppPair.second;
                    if (event == null) {
                        return;
                    }
                    try {
                        if (event == BlockedAppsViewModel.BlockedAppsViewModelUiEvent.RESTORE_APP) {
                            UiStatsLogger.logUnblockAppSelected();
                            mBlockedAppsViewModel.restoreAppConsent(app);
                            if (FlagsFactory.getFlags().getUiDialogsFeatureEnabled()) {
                                if (FlagsFactory.getFlags().getUiDialogFragmentEnabled()) {
                                    DialogFragmentManager.showUnblockAppDialog(mActivity, app);
                                } else {
                                    DialogManager.showUnblockAppDialog(mActivity, app);
                                }
                            }
                        } else {
                            Log.e("AdservicesUI", "Unknown Action for UI Logging");
                        }
                    } catch (IOException e) {
                        Log.e(
                                "AdServicesUI",
                                "Error while processing AppsViewModelUiEvent " + event + ":" + e);
                    } finally {
                        mBlockedAppsViewModel.uiEventHandled();
                    }
                };
        mBlockedAppsViewModel.getUiEvents().observe(mActivity, observer);
    }
}
