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

package com.android.adservices.service.ui.enrollment.collection;

import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.service.ui.enrollment.base.PrivacySandboxEnrollmentChannel;
import com.android.adservices.service.ui.enrollment.impl.AlreadyEnrolledChannel;
import com.android.adservices.service.ui.enrollment.impl.ConsentNotificationDebugChannel;
import com.android.adservices.service.ui.enrollment.impl.ConsentNotificationResetChannel;
import com.android.adservices.service.ui.enrollment.impl.FirstConsentNotificationChannel;
import com.android.adservices.service.ui.enrollment.impl.GaGraduationChannel;
import com.android.adservices.service.ui.enrollment.impl.PasFirstConsentNotificationChannel;
import com.android.adservices.service.ui.enrollment.impl.PasReconsentNotificationChannel;
import com.android.adservices.service.ui.enrollment.impl.ReconsentNotificationChannel;
import com.android.adservices.service.ui.enrollment.impl.RvcPostOTAChannel;

/* Collection of GA UX enrollment channels. */
@RequiresApi(Build.VERSION_CODES.S)
public enum GaUxEnrollmentChannelCollection implements PrivacySandboxEnrollmentChannelCollection {
    CONSENT_NOTIFICATION_DEBUG_CHANNEL(/* priority= */ 0, new ConsentNotificationDebugChannel()),

    CONSENT_NOTIFICATION_RESET_CHANNEL(/* priority= */ 1, new ConsentNotificationResetChannel()),

    ALREADY_ENROLLED_CHANNEL(/* priority= */ 2, new AlreadyEnrolledChannel()),

    PAS_FIRST_CONSENT_NOTIFICATION_CHANNEL(
            /* priority= */ 3, new PasFirstConsentNotificationChannel()),

    PAS_RECONSENT_NOTIFICATION_CHANNEL(/* priority= */ 4, new PasReconsentNotificationChannel()),

    FIRST_CONSENT_NOTIFICATION_CHANNEL(/* priority= */ 5, new FirstConsentNotificationChannel()),

    RECONSENT_NOTIFICATION_CHANNEL(/* priority= */ 6, new ReconsentNotificationChannel()),

    GA_GRADUATION_CHANNEL(/* priority= */ 7, new GaGraduationChannel()),

    RVC_POST_OTA_CHANNEL(/* priority= */ 8, new RvcPostOTAChannel());

    private final int mPriority;

    private final PrivacySandboxEnrollmentChannel mEnrollmentChannel;

    GaUxEnrollmentChannelCollection(
            int priority, PrivacySandboxEnrollmentChannel enrollmentChannel) {
        mPriority = priority;
        mEnrollmentChannel = enrollmentChannel;
    }

    public int getPriority() {
        return mPriority;
    }

    public PrivacySandboxEnrollmentChannel getEnrollmentChannel() {
        return mEnrollmentChannel;
    }
}
