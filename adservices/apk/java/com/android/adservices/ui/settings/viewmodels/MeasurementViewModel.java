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

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.adservices.service.consent.ConsentManager;

import com.google.common.annotations.VisibleForTesting;

/**
 * View model for the Measurement view of the AdServices Settings App. This view model is
 * responsible for serving Measurement to the Measurement view, and interacting with the {@link
 * ConsentManager} that persists and changes the Measurement data in a storage.
 */
public class MeasurementViewModel extends AndroidViewModel {

    private final MutableLiveData<MeasurementViewModelUiEvent> mEventTrigger =
            new MutableLiveData<>();

    private final ConsentManager mConsentManager;

    /** UI event in measurement triggered by view model */
    public enum MeasurementViewModelUiEvent {
        RESET_MEASUREMENT
    }

    public MeasurementViewModel(@NonNull Application application) {
        super(application);

        mConsentManager = ConsentManager.getInstance(application);
    }

    @VisibleForTesting
    public MeasurementViewModel(@NonNull Application application, ConsentManager consentManager) {
        super(application);
        mConsentManager = consentManager;
    }

    /** Reset all information related to Measurement */
    public void resetMeasurement() {
        mConsentManager.resetMeasurement();
    }

    /** Returns an observable but immutable event enum representing an action on UI. */
    public LiveData<MeasurementViewModelUiEvent> getUiEvents() {
        return mEventTrigger;
    }

    /**
     * Sets the UI Event as handled so the action will not be handled again if activity is
     * recreated.
     */
    public void uiEventHandled() {
        mEventTrigger.postValue(null);
    }

    /** Triggers a reset of all Measurement related data. */
    public void resetMeasurementButtonClickHandler() {
        mEventTrigger.postValue(MeasurementViewModelUiEvent.RESET_MEASUREMENT);
    }
}
