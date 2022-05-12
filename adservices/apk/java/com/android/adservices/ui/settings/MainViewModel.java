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

import android.content.Context;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.android.adservices.service.consent.ConsentManager;

import java.io.IOException;

/**
 * View model for the main view of the AdServices Settings App. This view model is responsible
 * for serving consent to the main view, and interacting with the {@link ConsentManager} that
 * persists the user consent data in a storage.
 */
public class MainViewModel extends ViewModel {
    private MutableLiveData<Boolean> mAdServicesConsent;

    /**
     * Provides {@link AdServicesApiConsent} displayed in {@link AdServicesSettingsMainFragment}
     * as a Switch value.
     *
     * @param context the context of {@link AdServicesSettingsActivity}
     * @return {@link mAdServicesConsent} indicates if user has consented to PP API usage.
     */
    public MutableLiveData<Boolean> getConsent(Context context) throws IOException {
        if (mAdServicesConsent == null) {
            mAdServicesConsent = new MutableLiveData<>();
            loadConsent(context);
        }
        return mAdServicesConsent;
    }

    private void loadConsent(Context context) throws IOException {
        Boolean consentGiven = ConsentManager.getInstance(context).getConsent(context).isGiven();
        mAdServicesConsent.setValue(consentGiven);
    }
}
