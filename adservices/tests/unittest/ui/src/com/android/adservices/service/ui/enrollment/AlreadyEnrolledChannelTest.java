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

package com.android.adservices.service.ui.enrollment;

import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockGetFlags;
import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_ALREADY_INTERACTED_FIX_ENABLE;
import static com.android.adservices.service.consent.ConsentManager.MANUAL_INTERACTIONS_RECORDED;

import static org.mockito.Mockito.doReturn;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.stats.UiStatsLogger;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.enrollment.impl.AlreadyEnrolledChannel;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

public class AlreadyEnrolledChannelTest extends AdServicesUnitTestCase {
    private final AlreadyEnrolledChannel mAlreadyEnrolledChannel = new AlreadyEnrolledChannel();

    @Mock private PrivacySandboxUxCollection mPrivacySandboxUxCollection;
    @Mock private UxStatesManager mUxStatesManager;
    @Mock private ConsentManager mConsentManager;

    @Mock private Flags mMockFlags;

    @Rule
    public final AdServicesExtendedMockitoRule mExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .spyStatic(FlagsFactory.class)
                    .mockStatic(UiStatsLogger.class)
                    .build();

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.initMocks(this);
        mockGetFlags(mMockFlags);
    }

    @Test
    public void isEligibleTest_gaUxGaNotificationAlreadyDisplayed() {
        doReturn(true).when(mConsentManager).wasGaUxNotificationDisplayed();

        expect.that(
                        mAlreadyEnrolledChannel.isEligible(
                                mPrivacySandboxUxCollection.GA_UX,
                                mConsentManager,
                                mUxStatesManager))
                .isTrue();
    }

    @Test
    public void isEligibleTest_gaUxGaNotificationNotDisplayed() {
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        expect.that(
                        mAlreadyEnrolledChannel.isEligible(
                                mPrivacySandboxUxCollection.GA_UX,
                                mConsentManager,
                                mUxStatesManager))
                .isFalse();
    }

    @Test
    public void isEligibleTest_betaUxNotificationAlreadyDisplayed() {
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();

        expect.that(
                        mAlreadyEnrolledChannel.isEligible(
                                mPrivacySandboxUxCollection.BETA_UX,
                                mConsentManager,
                                mUxStatesManager))
                .isTrue();
    }

    @Test
    public void isEligibleTest_betaUxNotificationNotDisplayed() {
        doReturn(false).when(mConsentManager).wasNotificationDisplayed();

        expect.that(
                        mAlreadyEnrolledChannel.isEligible(
                                mPrivacySandboxUxCollection.BETA_UX,
                                mConsentManager,
                                mUxStatesManager))
                .isFalse();
    }

    @Test
    public void isEligibleTest_u18UxNotificationAlreadyDisplayed() {
        doReturn(true).when(mConsentManager).wasU18NotificationDisplayed();

        expect.that(
                        mAlreadyEnrolledChannel.isEligible(
                                mPrivacySandboxUxCollection.U18_UX,
                                mConsentManager,
                                mUxStatesManager))
                .isTrue();
    }

    @Test
    public void isEligibleTest_alreadyInteractedFlagOff() {
        doReturn(true).when(mMockFlags).getConsentManagerLazyEnableMode();
        doReturn(MANUAL_INTERACTIONS_RECORDED)
                .when(mConsentManager)
                .getUserManualInteractionWithConsent();
        expect.that(
                        mAlreadyEnrolledChannel.isEligible(
                                mPrivacySandboxUxCollection.U18_UX,
                                mConsentManager,
                                mUxStatesManager))
                .isFalse();
    }

    @Test
    public void isEligibleTest_alreadyInteracted() {
        doReturn(true).when(mUxStatesManager).getFlag(KEY_CONSENT_ALREADY_INTERACTED_FIX_ENABLE);
        doReturn(MANUAL_INTERACTIONS_RECORDED)
                .when(mConsentManager)
                .getUserManualInteractionWithConsent();
        doReturn(true).when(mConsentManager).wasU18NotificationDisplayed();
        expect.that(
                        mAlreadyEnrolledChannel.isEligible(
                                mPrivacySandboxUxCollection.U18_UX,
                                mConsentManager,
                                mUxStatesManager))
                .isTrue();
    }

    @Test
    public void isEligibleTest_u18UxNotificationNotDisplayed() {
        doReturn(false).when(mConsentManager).wasU18NotificationDisplayed();
        expect.that(
                        mAlreadyEnrolledChannel.isEligible(
                                mPrivacySandboxUxCollection.U18_UX,
                                mConsentManager,
                                mUxStatesManager))
                .isFalse();
    }

    //    @Test
    //    public void enrollTest_enrollDoesNotTriggerNotification() {
    //        mAlreadyEnrolledChannel.enroll(appContext.get(), mConsentManager);
    //        verify(
    //                () ->
    //                        ConsentNotificationJobService.schedule(
    //                                any(Context.class), anyBoolean(), anyBoolean()),
    //                never());
    //    }

}
