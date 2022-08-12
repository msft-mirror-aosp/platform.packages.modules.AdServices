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
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.adservices.service.consent.App;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsAppsFragment;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsBlockedAppsFragment;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import java.io.IOException;

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
    private final ConsentManager mConsentManager;

    /** UI event triggered by view model */
    public enum AppsViewModelUiEvent {
        BLOCK_APP,
        RESTORE_APP,
        RESET_APPS,
        DISPLAY_BLOCKED_APPS_FRAGMENT,
    }

    public AppsViewModel(@NonNull Application application) {
        super(application);

        mConsentManager = ConsentManager.getInstance(application);
        mApps = new MutableLiveData<>(getAppsFromConsentManager());
        mBlockedApps = new MutableLiveData<>(getBlockedAppsFromConsentManager());
    }

    @VisibleForTesting
    public AppsViewModel(@NonNull Application application, ConsentManager consentManager) {
        super(application);

        mConsentManager = consentManager;
        mApps = new MutableLiveData<>(getAppsFromConsentManager());
        mBlockedApps = new MutableLiveData<>(getBlockedAppsFromConsentManager());
    }

    // ---------------------------------------------------------------------------------------------
    // Apps View
    // ---------------------------------------------------------------------------------------------

    /**
     * Provides the apps displayed in {@link AdServicesSettingsAppsFragment}.
     *
     * @return {@link mApps} a list of apps that represents the apps that use specific interest
     *     groups.
     */
    public LiveData<ImmutableList<App>> getApps() {
        return mApps;
    }

    /**
     * Revoke the consent for the specified app (i.e. block the app).
     *
     * @param app the app to be blocked.
     */
    public void revokeAppConsent(App app) throws IOException {
        mConsentManager.revokeConsentForApp(app);
        refresh();
    }

    /**
     * Reads all the data from {@link ConsentManager}.
     *
     * <p>TODO(b/238387560): To be moved to private when is fixed.
     */
    public void refresh() {
        mApps.postValue(getAppsFromConsentManager());
        mBlockedApps.postValue(getBlockedAppsFromConsentManager());
    }

    /** Reset all information related to apps but blocked apps. */
    public void resetApps() throws IOException {
        mConsentManager.resetApps();
        mApps.postValue(getAppsFromConsentManager());
    }

    // ---------------------------------------------------------------------------------------------
    // Blocked Apps View
    // ---------------------------------------------------------------------------------------------

    /**
     * Provides the blocked apps displayed in {@link AdServicesSettingsBlockedAppsFragment}.
     *
     * @return {@link mBlockedApps} a list of apps that represents the apps the user has blocked
     *     from generating specific interest groups.
     */
    public LiveData<ImmutableList<App>> getBlockedApps() {
        return mBlockedApps;
    }

    /**
     * Restore the consent for the specified app (i.e. unblock the app).
     *
     * @param app the app to be restored.
     */
    public void restoreAppConsent(App app) throws IOException {
        mConsentManager.restoreConsentForApp(app);
        refresh();
    }

    // ---------------------------------------------------------------------------------------------
    // Action Handlers
    // ---------------------------------------------------------------------------------------------

    /** Returns an observable but immutable event enum representing an view action on UI. */
    public LiveData<Pair<AppsViewModelUiEvent, App>> getUiEvents() {
        return mEventTrigger;
    }

    /**
     * Sets the UI Event as handled so the action will not be handled again if activity is
     * recreated.
     */
    public void uiEventHandled() {
        mEventTrigger.postValue(new Pair<>(null, null));
    }

    /**
     * Triggers the block of the specified app in the list of apps in {@link
     * AdServicesSettingsAppsFragment}.
     *
     * @param app the app to be blocked.
     */
    public void revokeAppConsentButtonClickHandler(App app) {
        mEventTrigger.postValue(new Pair<>(AppsViewModelUiEvent.BLOCK_APP, app));
    }

    /**
     * Triggers the block of the specified app in the list of apps in {@link
     * AdServicesSettingsAppsFragment}.
     *
     * @param app the app to be blocked.
     */
    public void restoreAppConsentButtonClickHandler(App app) {
        mEventTrigger.postValue(new Pair<>(AppsViewModelUiEvent.RESTORE_APP, app));
    }

    /** Triggers a reset of all apps related data. */
    public void resetAppsButtonClickHandler() {
        mEventTrigger.postValue(new Pair<>(AppsViewModelUiEvent.RESET_APPS, null));
    }

    /** Triggers {@link AdServicesSettingsAppsFragment}. */
    public void blockedAppsFragmentButtonClickHandler() {
        mEventTrigger.postValue(
                new Pair<>(AppsViewModelUiEvent.DISPLAY_BLOCKED_APPS_FRAGMENT, null));
    }

    // ---------------------------------------------------------------------------------------------
    // Private Methods
    // ---------------------------------------------------------------------------------------------

    private ImmutableList<App> getAppsFromConsentManager() {
        return mConsentManager.getKnownAppsWithConsent();
    }

    private ImmutableList<App> getBlockedAppsFromConsentManager() {
        return mConsentManager.getAppsWithRevokedConsent();
    }
}
