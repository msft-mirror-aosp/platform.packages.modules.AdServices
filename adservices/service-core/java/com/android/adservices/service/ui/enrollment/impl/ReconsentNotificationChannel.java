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

package com.android.adservices.service.ui.enrollment;

import static com.android.adservices.service.consent.ConsentManager.NO_MANUAL_INTERACTIONS_RECORDED;

import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.service.common.ConsentNotificationJobService;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.ui.UxStatesManager;
import com.android.adservices.service.ui.ux.PrivacySandboxUxCollection;

/** Enroll through consent notification debug mode. Currently only supported for GA UX. */
@RequiresApi(Build.VERSION_CODES.S)
public class ReconsentNotificationChannel implements PrivacySandboxEnrollmentChannel {

    /** Determines if user is eligible for the reconsent enrollment channel. */
    public boolean isEligible(
            PrivacySandboxUxCollection uxCollection,
            ConsentManager consentManager,
            UxStatesManager uxStatesManager) {
        return consentManager.wasNotificationDisplayed()
                && (consentManager.getConsent().isGiven()
                        || consentManager.getUserManualInteractionWithConsent()
                                == NO_MANUAL_INTERACTIONS_RECORDED);
    }

    /** Enroll user through the reconsent enrollment channel. */
    public void enroll(Context context, ConsentManager consentManager) {
        ConsentNotificationJobService.schedule(
                context,
                /* AdIdEnabled= */ consentManager.isAdIdEnabled(),
                /* IsReconsent= */ true);
    }
}
