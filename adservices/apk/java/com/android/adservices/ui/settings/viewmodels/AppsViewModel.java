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
package com.android.adservices.ui.settings.viewmodels;

import android.app.Application;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.adservices.service.consent.App;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsAppsFragment;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;

/**
 * View model for the apps view and blocked apps view of the AdServices Settings App. This view
 * model is responsible for serving apps to the apps view and blocked apps view, and interacting
 * with the {@link ConsentManager} that persists and changes the apps data in a storage.
 */
public class AppsViewModel extends AndroidViewModel {

    private final MutableLiveData<Pair<AppsViewModelUiEvent, App>> mEventTrigger =
            new MutableLiveData<>();
    private final MutableLiveData<ImmutableList<App>> mApps;
    private final MutableLiveData<ImmutableList<App>> mBlockedApps;
    private ConsentManager mConsentManager;

    /** UI event triggered by view model */
    public enum AppsViewModelUiEvent {
        BLOCK_APP,
        DISPLAY_BLOCKED_APPS_FRAGMENT,
        RESTORE_APP,
    }

    public AppsViewModel(@NonNull Application application) {
        super(application);
        setConsentManager(ConsentManager.getInstance(application));
        mApps = new MutableLiveData<>(getAppsFromConsentManager());
        mBlockedApps = new MutableLiveData<>(getBlockedAppsFromConsentManager());
    }

    @VisibleForTesting
    public AppsViewModel(@NonNull Application application, ConsentManager consentManager) {
        super(application);
        setConsentManager(consentManager);
        mApps = new MutableLiveData<>(getAppsFromConsentManager());
        mBlockedApps = new MutableLiveData<>(getBlockedAppsFromConsentManager());
    }

    // ---------------------------------------------------------------------------------------------
    // Apps View
    // ---------------------------------------------------------------------------------------------

    /**
     * Reads all the data from {@link ConsentManager}.
     *
     * <p>TODO(b/238387560): To be moved to private when is fixed.
     */
    public void refresh() {
        mApps.postValue(getAppsFromConsentManager());
        mBlockedApps.postValue(getBlockedAppsFromConsentManager());
    }

    /**
     * Provides the apps displayed in {@link AdServicesSettingsAppsFragment}.
     *
     * @return {@link #mApps} a list of apps that represents the user's interests.
     */
    public LiveData<ImmutableList<App>> getApps() {
        return mApps;
    }

    /**
     * Revoke the consent for the specified app (i.e. block the app).
     *
     * @param app the app to be blocked.
     */
    public void revokeAppConsent(App app) {
        mApps.postValue(getAppsFromConsentManager());
        mBlockedApps.postValue(getBlockedAppsFromConsentManager());
    }

    // ---------------------------------------------------------------------------------------------
    // Blocked Apps View
    // ---------------------------------------------------------------------------------------------

    /**
     * Provides the blocked apps displayed.
     *
     * @return {@link #mBlockedApps} a list of apps that represents the user's blocked interests.
     */
    public LiveData<ImmutableList<App>> getBlockedApps() {
        return mBlockedApps;
    }

    /**
     * Restore the consent for the specified app (i.e. unblock the app).
     *
     * @param app the {@link App} to be restored.
     */
    public void restoreAppConsent(App app) {
        mApps.postValue(getAppsFromConsentManager());
        mBlockedApps.postValue(getBlockedAppsFromConsentManager());
    }

    // ---------------------------------------------------------------------------------------------
    // Action Handlers
    // ---------------------------------------------------------------------------------------------

    /** Returns an observable but immutable event enum representing an view action on UI. */
    public LiveData<Pair<AppsViewModelUiEvent, App>> getUiEvents() {
        return mEventTrigger;
    }

    /**
     * Triggers the block of the specified app in the list of apps in {@link
     * AdServicesSettingsAppsFragment}.
     *
     * @param app the {@link App} to be blocked.
     */
    public void revokeAppConsentButtonClickHandler(App app) {
        mEventTrigger.postValue(new Pair<>(AppsViewModelUiEvent.BLOCK_APP, app));
    }

    /** Triggers {@link AdServicesSettingsAppsFragment}. */
    public void blockedAppsFragmentButtonClickHandler() {
        mEventTrigger.postValue(
                new Pair<>(AppsViewModelUiEvent.DISPLAY_BLOCKED_APPS_FRAGMENT, null));
    }

    /**
     * Triggers the block of the specified apps in the list of apps in {@link
     * AdServicesSettingsAppsFragment}.
     *
     * @param app the {@link App} to be unblocked.
     */
    public void restoreAppConsentButtonClickHandler(App app) {
        mEventTrigger.postValue(new Pair<>(AppsViewModelUiEvent.RESTORE_APP, app));
    }

    // ---------------------------------------------------------------------------------------------
    // Private Methods
    // ---------------------------------------------------------------------------------------------

    @VisibleForTesting
    void setConsentManager(ConsentManager consentManager) {
        mConsentManager = consentManager;
    }

    private ImmutableList<App> getAppsFromConsentManager() {
        List<App> tempList = new ArrayList<>();
        tempList.add(App.create(11));
        tempList.add(App.create(22));
        tempList.add(App.create(33));
        return ImmutableList.copyOf(tempList);
    }

    private ImmutableList<App> getBlockedAppsFromConsentManager() {
        List<App> tempList = new ArrayList<>();
        tempList.add(App.create(44));
        tempList.add(App.create(55));
        return ImmutableList.copyOf(tempList);
    }
}
