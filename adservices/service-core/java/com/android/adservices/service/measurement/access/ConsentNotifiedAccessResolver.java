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

package com.android.adservices.service.measurement.access;

import android.adservices.common.AdServicesStatusUtils;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.android.adservices.service.Flags;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.enrollment.collection.BetaUxEnrollmentChannelCollection;
import com.android.adservices.service.ui.enrollment.collection.GaUxEnrollmentChannelCollection;
import com.android.adservices.service.ui.enrollment.collection.PrivacySandboxEnrollmentChannelCollection;
import com.android.adservices.service.ui.enrollment.collection.U18UxEnrollmentChannelCollection;

/**
 * API access gate on whether consent notification was displayed. {@link #isAllowed(Context)} will
 * return true if the consent notification was displayed, false otherwise. {@link
 * UserConsentAccessResolver} should be applied after this to get the true user consent value.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class ConsentNotifiedAccessResolver implements IAccessResolver {
    private static final String ERROR_MESSAGE = "Consent notification has not been displayed.";
    private final UxStatesManager mUxStatesManager;
    private final Flags mFlags;

    public ConsentNotifiedAccessResolver(@NonNull UxStatesManager uxStatesManager, Flags flags) {
        mUxStatesManager = uxStatesManager;
        mFlags = flags;
    }

    @Override
    public boolean isAllowed(@NonNull Context context) {
        if (mFlags.getConsentNotifiedDebugMode()) {
            return true;
        }
        PrivacySandboxEnrollmentChannelCollection enrollmentChannel =
                mUxStatesManager.getEnrollmentChannel();
        return enrollmentChannel == GaUxEnrollmentChannelCollection.ALREADY_ENROLLED_CHANNEL
                || enrollmentChannel == BetaUxEnrollmentChannelCollection.ALREADY_ENROLLED_CHANNEL
                || enrollmentChannel == U18UxEnrollmentChannelCollection.ALREADY_ENROLLED_CHANNEL;
    }

    @Override
    public int getErrorStatusCode() {
        return AdServicesStatusUtils.STATUS_USER_CONSENT_NOTIFICATION_NOT_DISPLAYED_YET;
    }

    @NonNull
    @Override
    public String getErrorMessage() {
        return ERROR_MESSAGE;
    }
}
