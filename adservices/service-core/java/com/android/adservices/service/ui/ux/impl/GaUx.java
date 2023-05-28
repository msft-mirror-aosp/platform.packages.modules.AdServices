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
package com.android.adservices.service.ui.ux;

import static com.android.adservices.service.PhFlags.KEY_GA_UX_FEATURE_ENABLED;

import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.ui.data.UxStatesManager;

/** The privacy sandbox (general availability) GA UX. */
@RequiresApi(Build.VERSION_CODES.S)
public class GaUx implements PrivacySandboxUx {

    /** Whether a user is eligible for the privacy sandbox GA UX. */
    public boolean isEligible(ConsentManager consentManager, UxStatesManager uxStatesManager) {
        return uxStatesManager.getFlag(KEY_GA_UX_FEATURE_ENABLED)
                && consentManager.isAdultAccount();
    }

    /** Enroll user through one of the available GA UX enrollment channels if needed. */
    public void selectEnrollmentChannel(
            Context context, ConsentManager consentManager, UxStatesManager uxStatesManager) {
        // TO-DO(b/284172919): Add enrollment logic.
    }

    /** Select one of the available GA UX modes for the user. */
    public void selectMode(
            Context context, ConsentManager consentManager, UxStatesManager uxStatesManager) {
        // TO-DO(b/284175944): Add mode logic.
    }
}
