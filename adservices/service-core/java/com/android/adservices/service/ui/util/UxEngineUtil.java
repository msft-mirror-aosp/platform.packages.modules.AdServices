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

package com.android.adservices.service.ui.util;

import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.enrollment.collection.PrivacySandboxEnrollmentChannelCollection;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;

import java.util.stream.Stream;

/** Util class for UxEngine. */
@RequiresApi(Build.VERSION_CODES.S)
public class UxEngineUtil {

    /* Select the first eligible UX based on UX states, falls back to UNSUPPORTED_UX. */
    PrivacySandboxUxCollection getEligibleUxCollection(
            ConsentManager consentManager, UxStatesManager uxStatesManager) {
        return Stream.of(PrivacySandboxUxCollection.values())
                .filter(
                        collection ->
                                collection.getUx().isEligible(consentManager, uxStatesManager))
                .findFirst()
                .orElse(PrivacySandboxUxCollection.UNSUPPORTED_UX);
    }

    /* Select the first eligible enrollment channel for the selected UX. */
    PrivacySandboxEnrollmentChannelCollection getEligibleEnrollmentChannelCollection(
            PrivacySandboxUxCollection uxCollection,
            ConsentManager consentManager,
            UxStatesManager uxStatesManager) {
        return Stream.of(uxCollection.getEnrollmentChannelCollection())
                .filter(
                        collection ->
                                collection
                                        .getEnrollmentChannel()
                                        .isEligible(uxCollection, consentManager, uxStatesManager))
                .findFirst()
                .orElse(null);
    }
}
