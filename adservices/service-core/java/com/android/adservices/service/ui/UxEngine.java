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

import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.enrollment.collection.PrivacySandboxEnrollmentChannelCollection;
import com.android.adservices.service.ui.util.UxEngineUtil;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;

/* UxEngine for coordinating UX components such as UXs, enrollment channels, and modes. */
@RequiresApi(Build.VERSION_CODES.S)
public class UxEngine {
    private final ConsentManager mConsentManager;
    private final UxStatesManager mUxStatesManager;
    private final UxEngineUtil mUxEngineUtil;
    private final Context mContext;

    // TO-DO(b/287060615): Clean up dependencies between UX classes.
    UxEngine(
            Context context,
            ConsentManager consentManager,
            UxStatesManager uxStatesManager,
            UxEngineUtil uxEngineUtil) {
        mContext = context;
        mConsentManager = consentManager;
        mUxStatesManager = uxStatesManager;
        mUxEngineUtil = uxEngineUtil;
    }

    /**
     * Returns an instance of the UxEngine. This method should only be invoked by the common
     * manager.
     */
    public static UxEngine getInstance(Context context) {
        return new UxEngine(
                context,
                ConsentManager.getInstance(context),
                UxStatesManager.getInstance(context),
                UxEngineUtil.getInstance());
    }

    /**
     * Starts the UxEgine. In which the general UX flow would be carried out as the engine
     * orchestrates tasks and events between vairous UX components.
     */
    public void start(AdServicesStates adServicesStates) {
        mUxStatesManager.persistAdServicesStates(adServicesStates);

        PrivacySandboxUxCollection eligibleUx =
                mUxEngineUtil.getEligibleUxCollection(mConsentManager, mUxStatesManager);
        mConsentManager.setUx(eligibleUx);

        PrivacySandboxEnrollmentChannelCollection eligibleEnrollmentChannel =
                mUxEngineUtil.getEligibleEnrollmentChannelCollection(
                        eligibleUx, mConsentManager, mUxStatesManager);
        mConsentManager.setEnrollmentChannel(eligibleUx, eligibleEnrollmentChannel);

        // TO-DO: Add an UNSUPPORTED_ENROLLMENT_CHANNEL, rather than using null handling.
        // Entry point request should not trigger entrollment.
        if (!adServicesStates.isPrivacySandboxUiRequest() && eligibleEnrollmentChannel != null) {
            eligibleUx
                    .getUx()
                    .handleEnrollment(
                            eligibleEnrollmentChannel.getEnrollmentChannel(),
                            mContext,
                            mConsentManager);

            mUxEngineUtil.startBackgroundTasksUponConsent(mContext, FlagsFactory.getFlags());
        }
    }
}
