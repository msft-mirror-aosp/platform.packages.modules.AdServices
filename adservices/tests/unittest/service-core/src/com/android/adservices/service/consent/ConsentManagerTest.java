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

package com.android.adservices.service.consent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

@SmallTest
public class ConsentManagerTest {
    private static final String EEA_DEVICE = "com.google.android.feature.EEA_DEVICE";

    private ConsentManager mConsentManager;
    @Mock private PackageManager mPackageManager;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.initMocks(this);

        mConsentManager = ConsentManager.getInstance(ApplicationProvider.getApplicationContext());
    }

    @Test
    public void testConsentIsGivenAfterEnabling() {
        mConsentManager.enable();

        assertTrue(mConsentManager.getConsent().isGiven());
    }

    @Test
    public void testConsentIsRevokedAfterDisabling() {
        mConsentManager.disable();

        assertFalse(mConsentManager.getConsent().isGiven());
    }

    @Test
    public void testConsentIsEnabledForEuConfig() {
        when(mPackageManager.hasSystemFeature(EEA_DEVICE)).thenReturn(true);

        assertFalse(mConsentManager.getInitialConsent(mPackageManager));
    }

    @Test
    public void testConsentIsEnabledForNonEuConfig() {
        when(mPackageManager.hasSystemFeature(EEA_DEVICE)).thenReturn(false);

        assertTrue(mConsentManager.getInitialConsent(mPackageManager));
    }
}
