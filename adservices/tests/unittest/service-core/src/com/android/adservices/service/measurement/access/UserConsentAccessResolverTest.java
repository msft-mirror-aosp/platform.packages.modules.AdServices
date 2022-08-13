/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.content.Context;
import android.content.pm.PackageManager;

import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.ConsentManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class UserConsentAccessResolverTest {

    private static final String ERROR_MESSAGE = "User has not consented.";

    @Mock private Context mContext;
    @Mock private ConsentManager mConsentManager;
    @Mock private PackageManager mPackageManager;

    private UserConsentAccessResolver mClassUnderTest;

    @Before
    public void setup() {
        doReturn(mPackageManager).when(mContext).getPackageManager();
        mClassUnderTest = new UserConsentAccessResolver(mConsentManager);
    }

    @Test
    public void isAllowed_consented_success() {
        // Setup
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManager).getConsent(mPackageManager);

        // Execution
        assertTrue(mClassUnderTest.isAllowed(mContext));
    }

    @Test
    public void isAllowed_notConsented_success() {
        // Setup
        doReturn(AdServicesApiConsent.REVOKED).when(mConsentManager).getConsent(mPackageManager);

        // Execution
        assertFalse(mClassUnderTest.isAllowed(mContext));
    }

    @Test
    public void getErrorMessage() {
        // Execution
        assertEquals(ERROR_MESSAGE, mClassUnderTest.getErrorMessage());
    }
}
