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

package com.android.adservices.service.ui.ux;

import static com.android.adservices.service.FlagsConstants.KEY_GA_UX_FEATURE_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doReturn;

import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.ux.impl.GaUx;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.IOException;

public class GaUxTest {

    private final GaUx mGaUx = new GaUx();
    @Mock private UxStatesManager mUxStatesManager;
    @Mock private ConsentManager mConsentManager;
    private MockitoSession mStaticMockSession;

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
    }

    @After
    public void teardown() throws IOException {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void isEligibleTest_adultUserWithGaFlagOn() {
        doReturn(true).when(mConsentManager).isAdultAccount();
        doReturn(true).when(mUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);

        assertThat(mGaUx.isEligible(mConsentManager, mUxStatesManager)).isTrue();
    }

    @Test
    public void isEligibleTest_adultUserWithGaFlagOff() {
        doReturn(true).when(mConsentManager).isAdultAccount();
        doReturn(false).when(mUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);

        assertThat(mGaUx.isEligible(mConsentManager, mUxStatesManager)).isFalse();
    }

    @Test
    public void isEligibleTest_nonAdultUserWithGaFlagOn() {
        doReturn(false).when(mConsentManager).isAdultAccount();
        doReturn(true).when(mUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);

        assertThat(mGaUx.isEligible(mConsentManager, mUxStatesManager)).isFalse();
    }

    @Test
    public void isEligibleTest_nonAdultUserWithGaFlagOff() {
        doReturn(false).when(mConsentManager).isAdultAccount();
        doReturn(false).when(mUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);

        assertThat(mGaUx.isEligible(mConsentManager, mUxStatesManager)).isFalse();
    }
}