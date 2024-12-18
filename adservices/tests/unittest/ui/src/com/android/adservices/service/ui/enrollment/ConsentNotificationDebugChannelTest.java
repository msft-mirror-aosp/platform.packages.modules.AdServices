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

import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;

import android.content.Context;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.service.common.ConsentNotificationJobService;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.enrollment.impl.ConsentNotificationDebugChannel;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;

@SpyStatic(ConsentNotificationJobService.class)
public class ConsentNotificationDebugChannelTest extends AdServicesExtendedMockitoTestCase {
    @Rule(order = 11)
    public final AdServicesFlagsSetterRule flags = AdServicesFlagsSetterRule.newInstance();

    private final ConsentNotificationDebugChannel mConsentNotificationDebugChannel =
            new ConsentNotificationDebugChannel();

    @Mock private Context mContext;
    @Mock private PrivacySandboxUxCollection mPrivacySandboxUxCollection;
    @Mock private UxStatesManager mUxStatesManager;
    @Mock private ConsentManager mConsentManager;

    @Before
    public void setup() throws IOException {
        doNothing()
                .when(
                        () ->
                                ConsentNotificationJobService.schedule(
                                        any(Context.class), anyBoolean(), anyBoolean()));
    }

    @Test
    public void isEligibleTest_consentDebugModeOn() {
        flags.setDebugFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE, true);

        assertThat(
                        mConsentNotificationDebugChannel.isEligible(
                                mPrivacySandboxUxCollection, mConsentManager, mUxStatesManager))
                .isTrue();
    }

    @Test
    public void isEligibleTest_consentDebugModeOff() {
        flags.setDebugFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE, false);

        assertThat(
                        mConsentNotificationDebugChannel.isEligible(
                                mPrivacySandboxUxCollection, mConsentManager, mUxStatesManager))
                .isFalse();
    }

    @Test
    public void enrollTest_adIdDisabledConsentNotification() {
        doReturn(false).when(mConsentManager).isAdIdEnabled();

        mConsentNotificationDebugChannel.enroll(mContext, mConsentManager);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), anyBoolean()),
                times(1));
    }

    @Test
    public void enrollTest_adIdEnabledConsentNotification() {
        doReturn(true).when(mConsentManager).isAdIdEnabled();

        mConsentNotificationDebugChannel.enroll(mContext, mConsentManager);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(true), anyBoolean()),
                times(1));
    }
}
