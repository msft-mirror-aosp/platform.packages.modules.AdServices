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

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.adservices.service.consent.ConsentManager;

import java.io.IOException;

/**
 * View model for the main view of the AdServices Settings App. This view model is responsible
 * for serving consent to the main view, and interacting with the {@link ConsentManager} that
 * persists the user consent data in a storage.
 */
public class MainViewModel extends AndroidViewModel {
    private final MutableLiveData<MainViewModelUiEvent> mEventTrigger = new MutableLiveData<>();
    private MutableLiveData<Boolean> mAdServicesConsent;
    private ConsentManager mConsentManager;

    /** UI event triggered by view model */
    public enum MainViewModelUiEvent {
        DISPLAY_TOPICS_FRAGMENT
    }

    public MainViewModel(@NonNull Application application) throws IOException {
        super(application);
        setConsentManager(ConsentManager.getInstance(application));
    }

    /**
     * Provides {@link AdServicesApiConsent} displayed in {@link AdServicesSettingsMainFragment}
     * as a Switch value.
     *
     * @return {@link mAdServicesConsent} indicates if user has consented to PP API usage.
     */
    public MutableLiveData<Boolean> getConsent() {
        if (mAdServicesConsent == null) {
            mAdServicesConsent = new MutableLiveData<>(getConsentFromConsentManager());
        }
        return mAdServicesConsent;
    }

    /**
     * Sets the user consent for PP APIs.
     *
     * @param newConsent the new value that user consent should be set to for PP APIs.
     */
    public void setConsent(Boolean newConsent) throws IOException {
        if (mAdServicesConsent == null) {
            mAdServicesConsent = new MutableLiveData<>(getConsentFromConsentManager());
        }
        mAdServicesConsent.postValue(newConsent);
        if (newConsent) {
            mConsentManager.enable();
        } else {
            mConsentManager.disable();
        }
    }

    /** Returns an observable but immutable event enum representing an view action on UI. */
    public LiveData<MainViewModelUiEvent> getUiEvents() {
        return mEventTrigger;
    }

    /** Triggers {@link AdServicesSettingsTopicsFragment}. */
    public void topicsButtonClickHandler() {
        mEventTrigger.postValue(MainViewModelUiEvent.DISPLAY_TOPICS_FRAGMENT);
    }

    @VisibleForTesting
    void setConsentManager(ConsentManager consentManager) {
        mConsentManager = consentManager;
    }

    private boolean getConsentFromConsentManager() {
        return mConsentManager.getConsent().isGiven();
    }
}
