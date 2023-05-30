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

import static com.android.adservices.service.PhFlags.KEY_ADSERVICES_ENABLED;
import static com.android.adservices.service.PhFlags.KEY_GA_UX_FEATURE_ENABLED;
import static com.android.adservices.service.PhFlags.KEY_U18_UX_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doReturn;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.ux.PrivacySandboxUxCollection;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.IOException;

public class UxEngineTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    @Mock private UxStatesManager mUxStatesManager;
    @Mock private ConsentManager mConsentManager;
    private MockitoSession mStaticMockSession;
    private UxEngine mUxEngine;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.initMocks(this);

        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(UxStatesManager.class)
                        .spyStatic(ConsentManager.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();

        // Default states for testing supported UXs.
        doReturn(true).when(mConsentManager).isEntryPointEnabled();
        doReturn(true).when(mUxStatesManager).getFlag(KEY_ADSERVICES_ENABLED);

        mUxEngine = new UxEngine(mContext, mConsentManager, mUxStatesManager);
    }

    @After
    public void teardown() throws IOException {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void getEligibleUxCollectionTest_adServicesDisabled() {
        doReturn(false).when(mUxStatesManager).getFlag(KEY_ADSERVICES_ENABLED);

        assertThat(mUxEngine.getEligibleUxCollection())
                .isEqualTo(PrivacySandboxUxCollection.UNSUPPORTED_UX);
    }

    @Test
    public void getEligibleUxCollectionTest_entryPointDisabled() {
        doReturn(false).when(mConsentManager).isEntryPointEnabled();

        assertThat(mUxEngine.getEligibleUxCollection())
                .isEqualTo(PrivacySandboxUxCollection.UNSUPPORTED_UX);
    }

    @Test
    public void getEligibleUxCollectionTest_adultUserGaFlagOn() {
        doReturn(true).when(mUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);
        doReturn(true).when(mConsentManager).isAdultAccount();

        assertThat(mUxEngine.getEligibleUxCollection()).isEqualTo(PrivacySandboxUxCollection.GA_UX);
    }

    @Test
    public void getEligibleUxCollectionTest_adultUserGaFlagOff() {
        doReturn(false).when(mUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);
        doReturn(true).when(mConsentManager).isAdultAccount();

        assertThat(mUxEngine.getEligibleUxCollection())
                .isEqualTo(PrivacySandboxUxCollection.BETA_UX);
    }

    @Test
    public void getEligibleUxCollectionTest_u18UserU18FlagOn() {
        doReturn(true).when(mUxStatesManager).getFlag(KEY_U18_UX_ENABLED);
        doReturn(true).when(mConsentManager).isU18Account();

        assertThat(mUxEngine.getEligibleUxCollection())
                .isEqualTo(PrivacySandboxUxCollection.U18_UX);
    }

    @Test
    public void getEligibleUxCollectionTest_u18UserU18FlagOff() {
        doReturn(false).when(mUxStatesManager).getFlag(KEY_U18_UX_ENABLED);
        doReturn(true).when(mConsentManager).isU18Account();

        assertThat(mUxEngine.getEligibleUxCollection())
                .isEqualTo(PrivacySandboxUxCollection.UNSUPPORTED_UX);
    }
}
