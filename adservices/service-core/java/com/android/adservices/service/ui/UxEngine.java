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

import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.enrollment.PrivacySandboxEnrollmentChannel;
import com.android.adservices.service.ui.ux.PrivacySandboxUx;
import com.android.adservices.service.ui.ux.PrivacySandboxUxCollection;

import java.util.stream.Stream;

/* UxEngine for coordinating UX components such as UXs, enrollment channels, and modes. */
@RequiresApi(Build.VERSION_CODES.S)
public class UxEngine {
    private Context mContext;
    private ConsentManager mConsentManager;
    private UxStatesManager mUxStatesManager;

    public UxEngine(
            Context context, ConsentManager consentManager, UxStatesManager uxStatesManager) {
        mContext = context;
        mConsentManager = consentManager;
        mUxStatesManager = uxStatesManager;
    }

    PrivacySandboxUxCollection getEligibleUxCollection() {
        return Stream.of(PrivacySandboxUxCollection.values())
                .filter(
                        collection ->
                                collection.getUx().isEligible(mConsentManager, mUxStatesManager))
                .findFirst()
                .orElse(PrivacySandboxUxCollection.UNSUPPORTED_UX);
    }
}
