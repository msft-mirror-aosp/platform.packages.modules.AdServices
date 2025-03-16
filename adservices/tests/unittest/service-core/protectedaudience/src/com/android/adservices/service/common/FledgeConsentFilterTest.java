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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_CONSENT_FILTER_ALL_APIS_CONSENT_DISABLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_CONSENT_FILTER_CONSENT_REVOKED_FOR_APP;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_CONSENT_FILTER_MISSING_ANY_NOTIFICATION_DISPLAYED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_CONSENT_FILTER_USER_CONSENT_FOR_API_IS_NOT_GIVEN;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__LEAVE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PERSIST_AD_SELECTION_RESULT;
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
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.stats.AdServicesLogger;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

@SetErrorLogUtilDefaultParams(
        throwable = ExpectErrorLogUtilWithExceptionCall.Any.class)
public class FledgeConsentFilterTest extends AdServicesExtendedMockitoTestCase {
    @Mock private ConsentManager mConsentManagerMock;
    @Mock private AdServicesLogger mAdServicesLoggerMock;

    private FledgeConsentFilter mFledgeConsentFilter;

    private static final boolean ENFORCE_NOTIFICATION_ENABLED = true;
    private static final boolean ENFORCE_CONSENT_ENABLED = true;
    private static final boolean ENFORCE_NOTIFICATION_DISABLED = false;
    private static final boolean ENFORCE_CONSENT_DISABLED = false;

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
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_CONSENT_FILTER_USER_CONSENT_FOR_API_IS_NOT_GIVEN,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA)
    public void testAssertCallerHasApiUserConsent_revokedConsent_throwsGetAdSelectionCel() {
        when(mConsentManagerMock.getConsent(any())).thenReturn(AdServicesApiConsent.REVOKED);

        assertThrows(
                ConsentManager.RevokedConsentException.class,
                () ->
                        mFledgeConsentFilter.assertCallerHasApiUserConsent(
                                CommonFixture.TEST_PACKAGE_NAME,
                                AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA));
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
    @ExpectErrorLogUtilCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_CONSENT_FILTER_CONSENT_REVOKED_FOR_APP,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__LEAVE_CUSTOM_AUDIENCE)
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

    @Test
    public void testAssertEnrollmentShouldBeScheduled_enforceConsentFalse_doesNotThrowOrLogError() {
        enableUXNotification();

        stubAllAPIsDisabledToTrue();

        mFledgeConsentFilter.assertEnrollmentShouldBeScheduled(
                ENFORCE_CONSENT_DISABLED,
                ENFORCE_NOTIFICATION_ENABLED,
                CommonFixture.TEST_PACKAGE_NAME,
                AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS);

        verifyNoMoreInteractions(mAdServicesLoggerMock);
    }

    @Test
    public void
            testAssertEnrollmentShouldBeScheduled_enforceNotificationFalse_doesNotThrowOrLogError() {
        disableUXNotification();

        stubAllAPIsDisabledToFalse();

        mFledgeConsentFilter.assertEnrollmentShouldBeScheduled(
                ENFORCE_CONSENT_ENABLED,
                ENFORCE_NOTIFICATION_DISABLED,
                CommonFixture.TEST_PACKAGE_NAME,
                AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS);

        verifyNoMoreInteractions(mAdServicesLoggerMock);
    }

    @Test
    public void
            testAssertEnrollmentShouldBeScheduled_enforceConsentFalse_doesNotThrowOrLogErrorEvenWhenApisDisabled() {
        enableUXNotification();

        stubAllAPIsDisabledToTrue();

        mFledgeConsentFilter.assertEnrollmentShouldBeScheduled(
                ENFORCE_CONSENT_DISABLED,
                ENFORCE_NOTIFICATION_ENABLED,
                CommonFixture.TEST_PACKAGE_NAME,
                AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS);

        verifyNoMoreInteractions(mAdServicesLoggerMock);
    }

    @Test
    public void
            testAssertEnrollmentShouldBeScheduled_enforceNotificationFalse_doesNotThrowOrLogErrorEvenWhenNotificationNotShown() {
        disableUXNotification();

        stubAllAPIsDisabledToFalse();

        mFledgeConsentFilter.assertEnrollmentShouldBeScheduled(
                ENFORCE_CONSENT_ENABLED,
                ENFORCE_NOTIFICATION_DISABLED,
                CommonFixture.TEST_PACKAGE_NAME,
                AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS);

        verifyNoMoreInteractions(mAdServicesLoggerMock);
    }

    @Test
    public void
            testAssertEnrollmentShouldBeScheduled_noNotificationsDisplayed_throwsAndLogsError() {
        disableUXNotification();
        assertThrows(
                ConsentManager.RevokedConsentException.class,
                () ->
                        mFledgeConsentFilter.assertEnrollmentShouldBeScheduled(
                                ENFORCE_CONSENT_ENABLED,
                                ENFORCE_NOTIFICATION_ENABLED,
                                CommonFixture.TEST_PACKAGE_NAME,
                                AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(CommonFixture.TEST_PACKAGE_NAME),
                        eq(
                                AdServicesStatusUtils
                                        .STATUS_USER_CONSENT_NOTIFICATION_NOT_DISPLAYED_YET),
                        anyInt());
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_CONSENT_FILTER_MISSING_ANY_NOTIFICATION_DISPLAYED,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PERSIST_AD_SELECTION_RESULT)
    public void
    testAssertEnrollmentShouldBeScheduled_noNotificationsDisplayed_throwsPersistAdSelectionResultCel() {
        disableUXNotification();
        assertThrows(
                ConsentManager.RevokedConsentException.class,
                () ->
                        mFledgeConsentFilter.assertEnrollmentShouldBeScheduled(
                                ENFORCE_CONSENT_ENABLED,
                                ENFORCE_NOTIFICATION_ENABLED,
                                CommonFixture.TEST_PACKAGE_NAME,
                                AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT));
    }

    @Test
    public void testAssertEnrollmentShouldBeScheduled_allAPIsDisabled_throwsAndLogsError() {
        enableUXNotification();

        stubAllAPIsDisabledToTrue();

        assertThrows(
                ConsentManager.RevokedConsentException.class,
                () ->
                        mFledgeConsentFilter.assertEnrollmentShouldBeScheduled(
                                ENFORCE_CONSENT_ENABLED,
                                ENFORCE_NOTIFICATION_ENABLED,
                                CommonFixture.TEST_PACKAGE_NAME,
                                AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(CommonFixture.TEST_PACKAGE_NAME),
                        eq(AdServicesStatusUtils.STATUS_CONSENT_REVOKED_ALL_APIS),
                        anyInt());
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_CONSENT_FILTER_ALL_APIS_CONSENT_DISABLED,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PERSIST_AD_SELECTION_RESULT)
    public void
    testAssertEnrollmentShouldBeScheduled_allAPIsDisabled_throwsPersistAdSelectionResultCel() {
        enableUXNotification();

        stubAllAPIsDisabledToTrue();

        assertThrows(
                ConsentManager.RevokedConsentException.class,
                () ->
                        mFledgeConsentFilter.assertEnrollmentShouldBeScheduled(
                                ENFORCE_CONSENT_ENABLED,
                                ENFORCE_NOTIFICATION_ENABLED,
                                CommonFixture.TEST_PACKAGE_NAME,
                                AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT));
    }

    private void enableUXNotification() {
        when(mConsentManagerMock.wasNotificationDisplayed()).thenReturn(true);
        when(mConsentManagerMock.wasGaUxNotificationDisplayed()).thenReturn(true);
        when(mConsentManagerMock.wasU18NotificationDisplayed()).thenReturn(true);
        when(mConsentManagerMock.wasPasNotificationDisplayed()).thenReturn(true);
    }

    private void disableUXNotification() {
        when(mConsentManagerMock.wasNotificationDisplayed()).thenReturn(false);
        when(mConsentManagerMock.wasGaUxNotificationDisplayed()).thenReturn(false);
        when(mConsentManagerMock.wasU18NotificationDisplayed()).thenReturn(false);
        when(mConsentManagerMock.wasPasNotificationDisplayed()).thenReturn(false);
    }

    private void stubAllAPIsDisabledToFalse() {
        when(mConsentManagerMock.areAllApisDisabled()).thenReturn(false);
    }

    private void stubAllAPIsDisabledToTrue() {
        when(mConsentManagerMock.areAllApisDisabled()).thenReturn(true);
    }
}
