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

package com.android.adservices.service.ui.data;

import static com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection.BETA_UX;
import static com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection.GA_UX;
import static com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection.U18_UX;
import static com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection.UNSUPPORTED_UX;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import android.adservices.common.AdServicesStates;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.consent.DeviceRegionProvider;
import com.android.adservices.service.ui.enrollment.collection.PrivacySandboxEnrollmentChannelCollection;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class UxStatesManagerTest {

    private Context mContext;
    private UxStatesManager mUxStatesManager;
    private MockitoSession mStaticMockSession;

    @Mock private Flags mMockFlags;
    @Mock private ConsentManager mMockConsentManager;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.initMocks(this);

        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(UxStatesManager.class)
                        .spyStatic(ConsentManager.class)
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(DeviceRegionProvider.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();

        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
        ExtendedMockito.doReturn(mMockConsentManager).when(() -> ConsentManager.getInstance(any()));

        mContext = ApplicationProvider.getApplicationContext();

        // Set up the test map before calling the UxStatesManager c-tor.
        setUpTestMap();

        mUxStatesManager = new UxStatesManager(mContext, mMockFlags, mMockConsentManager);
    }

    @After
    public void teardown() throws IOException {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    private void setUpTestMap() {
        Map<String, Boolean> testMap = new HashMap<>();
        testMap.put("TRUE_FLAG_KEY", true);
        testMap.put("FALSE_FLAG_KEY", false);
        doReturn(testMap).when(mMockFlags).getUxFlags();
    }

    @Test
    public void persistAdServicesStatesTest() {
        mUxStatesManager.persistAdServicesStates(new AdServicesStates.Builder().build());

        verify(mMockConsentManager).setAdIdEnabled(anyBoolean());
        verify(mMockConsentManager).setU18Account(anyBoolean());
        verify(mMockConsentManager).setAdultAccount(anyBoolean());
        verify(mMockConsentManager).setEntryPointEnabled(anyBoolean());
    }

    @Test
    public void getFlagTest_emptyFlagKey() {
        assertThat(mUxStatesManager.getFlag("")).isFalse();
    }

    @Test
    public void getFlagTest_invalidFlagKey() {
        assertThat(mUxStatesManager.getFlag("INVALID_FLAG_KEY")).isFalse();
    }

    @Test
    public void getFlagTest_trueFlagKey() {
        assertThat(mUxStatesManager.getFlag("TRUE_FLAG_KEY")).isTrue();
    }

    @Test
    public void getFlagTest_falseFlagKey() {
        assertThat(mUxStatesManager.getFlag("FALSE_FLAG_KEY")).isFalse();
    }

    @Test
    public void isEeaDeviceTest_rowDevice() {
        ExtendedMockito.doReturn(false).when(() -> DeviceRegionProvider.isEuDevice(any()));

        mUxStatesManager = new UxStatesManager(mContext, mMockFlags, mMockConsentManager);

        assertThat(mUxStatesManager.isEeaDevice()).isFalse();
    }

    @Test
    public void isEeaDeviceTest_eeaDevice() {
        ExtendedMockito.doReturn(true).when(() -> DeviceRegionProvider.isEuDevice(any()));
        mUxStatesManager = new UxStatesManager(mContext, mMockFlags, mMockConsentManager);

        assertThat(mUxStatesManager.isEeaDevice()).isTrue();
    }

    @Test
    public void getUxTest_processStable() {
        assertThat(mUxStatesManager.getUx()).isNotNull();

        Stream.of(PrivacySandboxUxCollection.values())
                .forEach(
                        ux -> {
                            doReturn(ux).when(mMockConsentManager).getUx();

                            mUxStatesManager =
                                    new UxStatesManager(mContext, mMockFlags, mMockConsentManager);
                            assertThat(mUxStatesManager.getUx()).isEqualTo(ux);

                            // Changing the UX before the process dies does not affect the result.
                            doReturn(null).when(mMockConsentManager).getUx();
                            assertThat(mUxStatesManager.getUx()).isEqualTo(ux);
                        });
    }

    @Test
    public void getEnrollmentChannelTest_processStable() {
        assertThat(mUxStatesManager.getEnrollmentChannel()).isNull();

        Stream.of(PrivacySandboxUxCollection.values())
                .forEach(
                        ux -> {
                            for (PrivacySandboxEnrollmentChannelCollection channel :
                                    ux.getEnrollmentChannelCollection()) {
                                doReturn(ux).when(mMockConsentManager).getUx();
                                doReturn(channel)
                                        .when(mMockConsentManager)
                                        .getEnrollmentChannel(any());

                                mUxStatesManager =
                                        new UxStatesManager(
                                                mContext, mMockFlags, mMockConsentManager);
                                assertThat(mUxStatesManager.getEnrollmentChannel())
                                        .isEqualTo(channel);

                                // Changing the enrollment channel before the process dies does
                                // not affect the result.
                                doReturn(null)
                                        .when(mMockConsentManager)
                                        .getEnrollmentChannel(any());

                                assertThat(mUxStatesManager.getEnrollmentChannel())
                                        .isEqualTo(channel);
                            }
                        });
    }

    @Test
    public void isEnrolledUserTest_unsupportUx() {
        doReturn(UNSUPPORTED_UX).when(mMockConsentManager).getUx();

        mUxStatesManager = new UxStatesManager(mContext, mMockFlags, mMockConsentManager);
        assertThat(mUxStatesManager.isEnrolledUser()).isFalse();

        doReturn(true).when(mMockConsentManager).wasNotificationDisplayed();
        assertThat(mUxStatesManager.isEnrolledUser()).isFalse();

        doReturn(true).when(mMockConsentManager).wasU18NotificationDisplayed();
        assertThat(mUxStatesManager.isEnrolledUser()).isFalse();

        doReturn(true).when(mMockConsentManager).wasGaUxNotificationDisplayed();
        assertThat(mUxStatesManager.isEnrolledUser()).isFalse();
    }

    @Test
    public void isEnrolledUserTest_nullUx() {
        doReturn(null).when(mMockConsentManager).getUx();

        mUxStatesManager = new UxStatesManager(mContext, mMockFlags, mMockConsentManager);
        assertThat(mUxStatesManager.isEnrolledUser()).isFalse();

        doReturn(true).when(mMockConsentManager).wasNotificationDisplayed();
        assertThat(mUxStatesManager.isEnrolledUser()).isFalse();

        doReturn(true).when(mMockConsentManager).wasU18NotificationDisplayed();
        assertThat(mUxStatesManager.isEnrolledUser()).isFalse();

        doReturn(true).when(mMockConsentManager).wasGaUxNotificationDisplayed();
        assertThat(mUxStatesManager.isEnrolledUser()).isFalse();
    }

    @Test
    public void isEnrolledUserTest_betaUx() {
        doReturn(BETA_UX).when(mMockConsentManager).getUx();

        mUxStatesManager = new UxStatesManager(mContext, mMockFlags, mMockConsentManager);
        assertThat(mUxStatesManager.isEnrolledUser()).isFalse();

        doReturn(true).when(mMockConsentManager).wasNotificationDisplayed();
        assertThat(mUxStatesManager.isEnrolledUser()).isTrue();
    }

    @Test
    public void isEnrolledUserTest_u18Ux() {
        doReturn(U18_UX).when(mMockConsentManager).getUx();

        mUxStatesManager = new UxStatesManager(mContext, mMockFlags, mMockConsentManager);
        assertThat(mUxStatesManager.isEnrolledUser()).isFalse();

        doReturn(true).when(mMockConsentManager).wasU18NotificationDisplayed();
        assertThat(mUxStatesManager.isEnrolledUser()).isTrue();
    }

    @Test
    public void isEnrolledUserTest_gaUx() {
        doReturn(GA_UX).when(mMockConsentManager).getUx();

        mUxStatesManager = new UxStatesManager(mContext, mMockFlags, mMockConsentManager);
        assertThat(mUxStatesManager.isEnrolledUser()).isFalse();

        doReturn(true).when(mMockConsentManager).wasGaUxNotificationDisplayed();
        assertThat(mUxStatesManager.isEnrolledUser()).isTrue();
    }
}
