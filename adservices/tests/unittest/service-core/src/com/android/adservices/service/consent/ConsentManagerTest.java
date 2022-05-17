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

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class ConsentManagerTest {
    private ConsentManager mConsentManager;

    @Before
    public void setup() throws IOException {
        mConsentManager = ConsentManager.getInstance(ApplicationProvider.getApplicationContext());
    }

    @Test
    public void testConsentIsGivenAfterEnabling() {
        mConsentManager.enable(ApplicationProvider.getApplicationContext());

        assertTrue(
                mConsentManager.getConsent(ApplicationProvider.getApplicationContext()).isGiven());
    }

    @Test
    public void testConsentIsRevokedAfterDisabling() {
        mConsentManager.disable(ApplicationProvider.getApplicationContext());

        assertFalse(
                mConsentManager.getConsent(ApplicationProvider.getApplicationContext()).isGiven());
    }
}
