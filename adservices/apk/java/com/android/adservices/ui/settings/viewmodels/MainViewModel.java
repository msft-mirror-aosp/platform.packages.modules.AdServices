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
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.consent.ConsentManagerV2;
import com.android.adservices.ui.settings.activities.AdServicesSettingsMainActivity;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsAppsFragment;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsMeasurementFragment;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsTopicsFragment;
import com.android.settingslib.widget.MainSwitchBar;

/**
 * View model for the main view of the AdServices Settings App. This view model is responsible for
 * serving consent to the main view, and interacting with the {@link ConsentManager} that persists
 * the user consent data in a storage.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class MainViewModel extends AndroidViewModel {
    private final MutableLiveData<MainViewModelUiEvent> mEventTrigger = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mAdServicesConsent;
    private final ConsentManager mConsentManager;

    private final ConsentManagerV2 mConsentManagerV2;

    /** UI event triggered by view model */
    public enum MainViewModelUiEvent {
        SWITCH_ON_PRIVACY_SANDBOX_BETA,
        SWITCH_OFF_PRIVACY_SANDBOX_BETA,
        DISPLAY_TOPICS_FRAGMENT,
        DISPLAY_APPS_FRAGMENT,
        DISPLAY_MEASUREMENT_FRAGMENT,
    }

    public MainViewModel(@NonNull Application application) {
        super(application);
        if (FlagsFactory.getFlags().getEnableConsentManagerV2()) {
            mConsentManagerV2 = ConsentManagerV2.getInstance();
            mConsentManager = null;
            mAdServicesConsent = new MutableLiveData<>(getConsentFromConsentManager());
        } else {
            mConsentManagerV2 = null;
            mConsentManager = ConsentManager.getInstance();
            mAdServicesConsent = new MutableLiveData<>(getConsentFromConsentManager());
        }
    }

    @VisibleForTesting
    public MainViewModel(@NonNull Application application, ConsentManagerV2 consentManagerV2) {
        super(application);
        mConsentManagerV2 = consentManagerV2;
        mConsentManager = null;
        mAdServicesConsent = new MutableLiveData<>(getConsentFromConsentManager());
    }

    /**
     * Provides {@link AdServicesApiConsent} displayed in {@link AdServicesSettingsMainActivity} as
     * a Switch value.
     *
     * @return {@link #mAdServicesConsent} indicates if user has consented to PP API usage.
     */
    public MutableLiveData<Boolean> getConsent() {
        return mAdServicesConsent;
    }

    /**
     * Sets the user consent for PP APIs.
     *
     * @param newConsentValue the new value that user consent should be set to for PP APIs.
     */
    public void setConsent(Boolean newConsentValue) {

        if (FlagsFactory.getFlags().getEnableConsentManagerV2()) {
            if (newConsentValue) {
                mConsentManagerV2.enable(getApplication());
            } else {
                mConsentManagerV2.disable(getApplication());
            }
            mAdServicesConsent.postValue(getConsentFromConsentManager());
            if (FlagsFactory.getFlags().getRecordManualInteractionEnabled()) {
                ConsentManagerV2.getInstance()
                        .recordUserManualInteractionWithConsent(
                                ConsentManagerV2.MANUAL_INTERACTIONS_RECORDED);
            }
        } else {
            if (newConsentValue) {
                mConsentManager.enable(getApplication());
            } else {
                mConsentManager.disable(getApplication());
            }
            mAdServicesConsent.postValue(getConsentFromConsentManager());
            if (FlagsFactory.getFlags().getRecordManualInteractionEnabled()) {
                ConsentManager.getInstance()
                        .recordUserManualInteractionWithConsent(
                                ConsentManager.MANUAL_INTERACTIONS_RECORDED);
            }
        }

    }

    /** Returns an observable but immutable event enum representing an view action on UI. */
    public LiveData<MainViewModelUiEvent> getUiEvents() {
        return mEventTrigger;
    }

    /**
     * Sets the UI Event as handled so the action will not be handled again if activity is
     * recreated.
     */
    public void uiEventHandled() {
        mEventTrigger.postValue(null);
    }

    /** Triggers {@link AdServicesSettingsTopicsFragment}. */
    public void topicsButtonClickHandler() {
        mEventTrigger.postValue(MainViewModelUiEvent.DISPLAY_TOPICS_FRAGMENT);
    }

    /** Triggers {@link AdServicesSettingsAppsFragment}. */
    public void appsButtonClickHandler() {
        mEventTrigger.postValue(MainViewModelUiEvent.DISPLAY_APPS_FRAGMENT);
    }

    /** Triggers {@link AdServicesSettingsMeasurementFragment} */
    public void measurementClickHandler() {
        mEventTrigger.postValue(MainViewModelUiEvent.DISPLAY_MEASUREMENT_FRAGMENT);
    }

    /**
     * Triggers opt out process for Privacy Sandbox. Also reverts the switch state, since
     * confirmation dialog will handle switch change.
     */
    public void consentSwitchClickHandler(MainSwitchBar mainSwitchBar) {
        if (mainSwitchBar.isChecked()) {
            mainSwitchBar.setChecked(false);
            mEventTrigger.postValue(MainViewModelUiEvent.SWITCH_ON_PRIVACY_SANDBOX_BETA);
        } else {
            mainSwitchBar.setChecked(true);
            mEventTrigger.postValue(MainViewModelUiEvent.SWITCH_OFF_PRIVACY_SANDBOX_BETA);
        }
    }

    private boolean getConsentFromConsentManager() {
        if (FlagsFactory.getFlags().getEnableConsentManagerV2()) {
            return mConsentManagerV2.getConsent().isGiven();
        } else {
            return mConsentManager.getConsent().isGiven();
        }
    }

    public boolean getMeasurementConsentFromConsentManager() {
        if (FlagsFactory.getFlags().getEnableConsentManagerV2()) {
            return mConsentManagerV2.getConsent(AdServicesApiType.MEASUREMENTS).isGiven();
        } else {
            return mConsentManager.getConsent(AdServicesApiType.MEASUREMENTS).isGiven();
        }
    }

    public boolean getTopicsConsentFromConsentManager() {
        if (FlagsFactory.getFlags().getEnableConsentManagerV2()) {
            return mConsentManagerV2.getConsent(AdServicesApiType.TOPICS).isGiven();
        } else {
            return mConsentManager.getConsent(AdServicesApiType.TOPICS).isGiven();
        }
    }

    public boolean getAppsConsentFromConsentManager() {
        if (FlagsFactory.getFlags().getEnableConsentManagerV2()) {
            return mConsentManagerV2.getConsent(AdServicesApiType.FLEDGE).isGiven();
        } else {
            return mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven();
        }
    }

    public int getCountOfTopics() {
        if (FlagsFactory.getFlags().getEnableConsentManagerV2()) {
            return mConsentManagerV2.getKnownTopicsWithConsent().size();
        } else {
            return mConsentManager.getKnownTopicsWithConsent().size();
        }
    }

    public int getCountOfApps() {
        if (FlagsFactory.getFlags().getEnableConsentManagerV2()) {
            return mConsentManagerV2.getKnownAppsWithConsent().size();
        } else {
            return mConsentManager.getKnownAppsWithConsent().size();
        }
    }
}
