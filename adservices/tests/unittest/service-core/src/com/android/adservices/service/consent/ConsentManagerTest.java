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

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.common.BooleanFileDatastore;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

public class ConsentManagerTest {
    private static final String CONSENT_KEY = "CONSENT";

    @Mock
    private BooleanFileDatastore mDatastore;
    private ConsentManager mConsentManager;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.initMocks(this);
        mConsentManager = ConsentManager.getInstance(ApplicationProvider.getApplicationContext());
    }

    @Test
    public void testConsentIsGivenToAllApiTypes() {
        when(mDatastore.get(CONSENT_KEY)).thenReturn(true);

        assertTrue(
                mConsentManager.getConsent(ApplicationProvider.getApplicationContext()).isGiven());
    }

    @Test
    public void testConsentIsRevokedToAllApiTypes() {
        when(mDatastore.get(CONSENT_KEY)).thenReturn(false);

        assertFalse(
                mConsentManager.getConsent(ApplicationProvider.getApplicationContext()).isGiven());
    }

    @Test
    public void testConsentIsGivenAfterEnabling() {
        when(mDatastore.get(CONSENT_KEY)).thenReturn(true);

        mConsentManager.enable(ApplicationProvider.getApplicationContext());

        assertTrue(
                mConsentManager.getConsent(ApplicationProvider.getApplicationContext()).isGiven());
    }

    @Test
    public void testConsentIsRevokedAfterDisabling() {
        when(mDatastore.get(CONSENT_KEY)).thenReturn(false);

        mConsentManager.disable(ApplicationProvider.getApplicationContext());

        assertFalse(
                mConsentManager.getConsent(ApplicationProvider.getApplicationContext()).isGiven());
    }
}
