/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.ui;

import android.adservices.common.AdServicesStates;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.enrollment.collection.PrivacySandboxEnrollmentChannelCollection;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;

import java.util.stream.Stream;

/* UxEngine for coordinating UX components such as UXs, enrollment channels, and modes. */
@RequiresApi(Build.VERSION_CODES.S)
public class UxEngine {
    private final ConsentManager mConsentManager;
    private final UxStatesManager mUxStatesManager;

    UxEngine(ConsentManager consentManager, UxStatesManager uxStatesManager) {
        mConsentManager = consentManager;
        mUxStatesManager = uxStatesManager;
    }

    /**
     * Returns an instance of the UxEngine. This method should only be invoked by the common
     * manager.
     */
    public static UxEngine getInstance(Context context) {
        return new UxEngine(ConsentManager.getInstance(context), UxStatesManager.getInstance());
    }

    /**
     * Starts the UxEgine. In which the general UX flow would be carried out as the engine
     * orchestrates tasks and events between vairous UX components.
     */
    public void start(AdServicesStates adServicesStates) {}

    /* Select the first eligible UX based on UX states, falls back to UNSUPPORTED_UX. */
    PrivacySandboxUxCollection getEligibleUxCollection() {
        return Stream.of(PrivacySandboxUxCollection.values())
                .filter(
                        collection ->
                                collection.getUx().isEligible(mConsentManager, mUxStatesManager))
                .findFirst()
                .orElse(PrivacySandboxUxCollection.UNSUPPORTED_UX);
    }

    /* Select the first eligible enrollment channel for the selected UX. */
    PrivacySandboxEnrollmentChannelCollection getEligibleEnrollmentChannelCollection(
            PrivacySandboxUxCollection uxCollection) {
        return Stream.of(uxCollection.getEnrollmentChannelCollection())
                .filter(
                        collection ->
                                collection
                                        .getEnrollmentChannel()
                                        .isEligible(
                                                uxCollection, mConsentManager, mUxStatesManager))
                .findFirst()
                .orElse(null);
    }
}
