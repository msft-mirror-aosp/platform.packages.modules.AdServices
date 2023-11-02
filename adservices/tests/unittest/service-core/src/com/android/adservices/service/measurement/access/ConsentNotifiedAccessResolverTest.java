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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;

import android.adservices.common.AdServicesStatusUtils;
import android.content.Context;

import com.android.adservices.service.Flags;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.enrollment.collection.BetaUxEnrollmentChannelCollection;
import com.android.adservices.service.ui.enrollment.collection.GaUxEnrollmentChannelCollection;
import com.android.adservices.service.ui.enrollment.collection.U18UxEnrollmentChannelCollection;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ConsentNotifiedAccessResolverTest {
    @Mock private UxStatesManager mUxStatesManager;
    @Mock private Context mContext;
    @Mock private Flags mFlags;

    private ConsentNotifiedAccessResolver mConsentNotifiedAccessResolver;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mConsentNotifiedAccessResolver =
                new ConsentNotifiedAccessResolver(mUxStatesManager, mFlags);
        doReturn(false).when(mFlags).getConsentNotifiedDebugMode();
    }

    @Test
    public void getErrorStatusCode_returnsExpectedErrorCode() {
        assertEquals(
                AdServicesStatusUtils.STATUS_USER_CONSENT_NOTIFICATION_NOT_DISPLAYED_YET,
                mConsentNotifiedAccessResolver.getErrorStatusCode());
    }

    @Test
    public void getErrorMessage_returnsExpectedErrorMessage() {
        assertEquals(
                "Consent notification has not been displayed.",
                mConsentNotifiedAccessResolver.getErrorMessage());
    }

    @Test
    public void isAllowed_returnsTrueWhenGaUXNotificationWasDisplayed() {
        // Setup
        doReturn(GaUxEnrollmentChannelCollection.ALREADY_ENROLLED_CHANNEL)
                .when(mUxStatesManager)
                .getEnrollmentChannel();

        // Assertion
        assertTrue(mConsentNotifiedAccessResolver.isAllowed(mContext));
    }

    @Test
    public void isAllowed_returnsTrueWhenBetaUXNotificationWasDisplayed() {
        // Setup
        doReturn(BetaUxEnrollmentChannelCollection.ALREADY_ENROLLED_CHANNEL)
                .when(mUxStatesManager)
                .getEnrollmentChannel();

        // Assertion
        assertTrue(mConsentNotifiedAccessResolver.isAllowed(mContext));
    }

    @Test
    public void isAllowed_returnsTrueWhenU18UXNotificationWasDisplayed() {
        // Setup
        doReturn(U18UxEnrollmentChannelCollection.ALREADY_ENROLLED_CHANNEL)
                .when(mUxStatesManager)
                .getEnrollmentChannel();

        // Assertion
        assertTrue(mConsentNotifiedAccessResolver.isAllowed(mContext));
    }

    @Test
    public void isAllowed_returnsTrueInDebugMode() {
        // Setup
        doReturn(null).when(mUxStatesManager).getEnrollmentChannel();
        doReturn(true).when(mFlags).getConsentNotifiedDebugMode();

        // Assertion
        assertTrue(mConsentNotifiedAccessResolver.isAllowed(mContext));
    }

    @Test
    public void isAllowed_returnsFalseWhenNotificationWasNotDisplayed() {
        // Setup
        doReturn(null).when(mUxStatesManager).getEnrollmentChannel();

        // Assertion
        assertFalse(mConsentNotifiedAccessResolver.isAllowed(mContext));
    }
}
