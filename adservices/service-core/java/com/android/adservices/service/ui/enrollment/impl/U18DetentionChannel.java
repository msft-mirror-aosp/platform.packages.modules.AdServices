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

import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.ux.PrivacySandboxUxCollection;

/** Enroll through the U18 detention channel. */
@RequiresApi(Build.VERSION_CODES.S)
public class U18DetentionChannel implements PrivacySandboxEnrollmentChannel {

    /** Checks if user is eligible for the U18 dention channel. */
    public boolean isEligible(
            PrivacySandboxUxCollection uxCollection,
            ConsentManager consentManager,
            UxStatesManager uxStatesManager) {
        return uxCollection == PrivacySandboxUxCollection.U18_UX
                && consentManager.wasGaUxNotificationDisplayed();
    }

    /** Enroll U18 users upon graduation. */
    public void enroll(Context context, ConsentManager consentManager) {}
}
