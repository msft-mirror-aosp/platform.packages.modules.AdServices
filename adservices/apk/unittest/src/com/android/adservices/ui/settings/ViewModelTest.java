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

package com.android.adservices.ui.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Application;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.ConsentManager;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

/**
 *  Tests for {@link MainViewModel}.
 */
public class ViewModelTest {
    private MainViewModel mMainViewModel;
    @Mock
    private ConsentManager mConsentManager;

    /**
     * Setup needed before every test in this class.
     */
    @Before
    public void setup() throws IOException {
        MockitoAnnotations.initMocks(this);
        Application app = ApplicationProvider.getApplicationContext();
        mMainViewModel = new MainViewModel(app);
        mMainViewModel.setConsentManager(mConsentManager);
    }

    /**
     * Test if getConsent returns true if the {@link ConsentManager} always returns true.
     */
    @Test
    public void testGetConsentReturnsTrue() {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManager).getConsent();

        assertTrue(mMainViewModel.getConsent().getValue());
    }

    /**
     * Test if getConsent returns false if the {@link ConsentManager} always returns false.
     */
    @Test
    public void testGetConsentReturnsFalse() {
        doReturn(AdServicesApiConsent.REVOKED).when(mConsentManager).getConsent();

        assertFalse(mMainViewModel.getConsent().getValue());
    }

    /**
     * Test if setConsent enables consent with a call to {@link ConsentManager}.
     */
    @Test
    public void testSetConsentTrue() throws IOException {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManager).getConsent();
        mMainViewModel.setConsent(true);

        verify(mConsentManager, times(1)).enable();
    }

    /**
     * Test if setConsent revokes consent with a call to {@link ConsentManager}.
     */
    @Test
    public void testSetConsentFalse() throws IOException {
        // It does not matter what the ConsentManager returns because it will be overwritten
        // immediately, and the case where setConsent is called before getConsent should not happen
        // in practice.
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManager).getConsent();
        mMainViewModel.setConsent(false);

        verify(mConsentManager, times(1)).disable();
    }
}

