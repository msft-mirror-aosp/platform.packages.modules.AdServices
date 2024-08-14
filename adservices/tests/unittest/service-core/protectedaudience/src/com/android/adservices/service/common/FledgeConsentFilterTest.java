/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.common;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyString;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyNoMoreInteractions;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static org.junit.Assert.assertThrows;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.CommonFixture;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

@RequiresSdkLevelAtLeastS()
public class FledgeConsentFilterTest extends AdServicesExtendedMockitoTestCase {
    @Mock private ConsentManager mConsentManagerMock;
    @Mock private AdServicesLogger mAdServicesLoggerMock;

    private FledgeConsentFilter mFledgeConsentFilter;

    @Before
    public void setup() {
        mFledgeConsentFilter = new FledgeConsentFilter(mConsentManagerMock, mAdServicesLoggerMock);
    }

    @Test
    public void testAssertCallerHasApiUserConsent_hasConsent_doesNotThrowOrLogError() {
        when(mConsentManagerMock.getConsent(any())).thenReturn(AdServicesApiConsent.GIVEN);

        mFledgeConsentFilter.assertCallerHasApiUserConsent(
                CommonFixture.TEST_PACKAGE_NAME, AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS);

        verifyNoMoreInteractions(mAdServicesLoggerMock);
    }

    @Test
    public void testAssertCallerHasApiUserConsent_revokedConsent_throwsAndLogsError() {
        when(mConsentManagerMock.getConsent(any())).thenReturn(AdServicesApiConsent.REVOKED);

        assertThrows(
                ConsentManager.RevokedConsentException.class,
                () ->
                        mFledgeConsentFilter.assertCallerHasApiUserConsent(
                                CommonFixture.TEST_PACKAGE_NAME,
                                AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(CommonFixture.TEST_PACKAGE_NAME),
                        eq(AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED),
                        anyInt());
    }

    @Test
    public void testAssertAndPersistCallerHasUserConsentForApp_hasConsent_doesNotThrowOrLogError() {
        when(mConsentManagerMock.isFledgeConsentRevokedForAppAfterSettingFledgeUse(anyString()))
                .thenReturn(false);

        mFledgeConsentFilter.assertAndPersistCallerHasUserConsentForApp(
                CommonFixture.TEST_PACKAGE_NAME,
                AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE);

        verifyNoMoreInteractions(mAdServicesLoggerMock);
    }

    @Test
    public void testAssertAndPersistCallerHasUserConsentForApp_revokedConsent_throwsAndLogsError() {
        when(mConsentManagerMock.isFledgeConsentRevokedForAppAfterSettingFledgeUse(anyString()))
                .thenReturn(true);

        assertThrows(
                ConsentManager.RevokedConsentException.class,
                () ->
                        mFledgeConsentFilter.assertAndPersistCallerHasUserConsentForApp(
                                CommonFixture.TEST_PACKAGE_NAME,
                                AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE),
                        eq(CommonFixture.TEST_PACKAGE_NAME),
                        eq(AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED),
                        anyInt());
    }
}
