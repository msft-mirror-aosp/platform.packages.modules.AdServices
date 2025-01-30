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

package com.android.adservices.service.common;

import static android.adservices.common.AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED_PACKAGE_NOT_IN_ALLOWLIST;

import static com.android.adservices.service.common.AppManifestConfigCall.API_AD_SELECTION;
import static com.android.adservices.service.common.AppManifestConfigCall.API_CUSTOM_AUDIENCES;
import static com.android.adservices.service.common.AppManifestConfigCall.API_PROTECTED_SIGNALS;
import static com.android.adservices.service.common.FledgeAllowListsFilter.AppNotAllowedException;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_ALLOW_LISTS_FILTER_PACKAGE_NOT_IN_ALLOW_LIST;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyNoMoreInteractions;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyZeroInteractions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.common.AdServicesStatusUtils;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.service.Flags;
import com.android.adservices.service.stats.AdServicesLogger;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public final class FledgeAllowListsFilterTest extends AdServicesExtendedMockitoTestCase {
    private static final int API_NAME_LOGGING_ID = 1;

    private static final String PACKAGE_ALLOWED_PAS_1 = "pas.package.1";
    private static final String PACKAGE_ALLOWED_PAS_2 = "pas.package.2";

    private static final String PACKAGE_ALLOWED_PPAPI_1 = "ppapi.package.1";
    private static final String PACKAGE_ALLOWED_PPAPI_2 = "ppapi.package.2";

    private static final String PACKAGE_ALLOWED_ALL = "pasandppapi.package";

    @Mock private AdServicesLogger mAdServicesLoggerMock;
    private FledgeAllowListsFilter mFledgeAllowListsFilter;

    @Before
    public void setup() {
        mFledgeAllowListsFilter =
                new FledgeAllowListsFilter(new AllowListTestFlags(), mAdServicesLoggerMock);
    }

    @Test
    public void testIsAllowedPpapi() {
        mFledgeAllowListsFilter.assertAppInAllowlist(
                PACKAGE_ALLOWED_PPAPI_1, API_NAME_LOGGING_ID, API_CUSTOM_AUDIENCES);

        verifyZeroInteractions(mAdServicesLoggerMock);
    }

    @Test
    public void testIsAllowedSignals() {
        mFledgeAllowListsFilter.assertAppInAllowlist(
                PACKAGE_ALLOWED_PAS_1, API_NAME_LOGGING_ID, API_PROTECTED_SIGNALS);

        verifyZeroInteractions(mAdServicesLoggerMock);
    }

    @Test
    public void testIsAllowedAdSelectionSignalsListOnly() {
        mFledgeAllowListsFilter.assertAppInAllowlist(
                PACKAGE_ALLOWED_PAS_1, API_NAME_LOGGING_ID, API_AD_SELECTION);

        verifyZeroInteractions(mAdServicesLoggerMock);
    }

    @Test
    public void testIsAllowedAdSelectionCaListOnly() {
        mFledgeAllowListsFilter.assertAppInAllowlist(
                PACKAGE_ALLOWED_PPAPI_1, API_NAME_LOGGING_ID, API_AD_SELECTION);

        verifyZeroInteractions(mAdServicesLoggerMock);
    }

    @Test
    public void testIsAllowedAdSelectionBothLists() {
        mFledgeAllowListsFilter.assertAppInAllowlist(
                PACKAGE_ALLOWED_ALL, API_NAME_LOGGING_ID, API_AD_SELECTION);

        verifyZeroInteractions(mAdServicesLoggerMock);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_ALLOW_LISTS_FILTER_PACKAGE_NOT_IN_ALLOW_LIST,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS,
            throwable = AppNotAllowedException.class)
    public void testIsNotAllowedAdSelection() {
        String notAllowedPackage = "not.an.allowed.package";
        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () ->
                                mFledgeAllowListsFilter.assertAppInAllowlist(
                                        notAllowedPackage,
                                        API_NAME_LOGGING_ID,
                                        API_PROTECTED_SIGNALS));

        assertEquals(
                AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE,
                exception.getMessage());
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME_LOGGING_ID),
                        eq(notAllowedPackage),
                        eq(STATUS_CALLER_NOT_ALLOWED_PACKAGE_NOT_IN_ALLOWLIST),
                        anyInt());

        verifyNoMoreInteractions(mAdServicesLoggerMock);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_ALLOW_LISTS_FILTER_PACKAGE_NOT_IN_ALLOW_LIST,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS,
            throwable = AppNotAllowedException.class)
    public void testNotAllowedPpapi() {
        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () ->
                                mFledgeAllowListsFilter.assertAppInAllowlist(
                                        PACKAGE_ALLOWED_PAS_1,
                                        API_NAME_LOGGING_ID,
                                        API_CUSTOM_AUDIENCES));

        assertEquals(
                AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE,
                exception.getMessage());
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME_LOGGING_ID),
                        eq(PACKAGE_ALLOWED_PAS_1),
                        eq(STATUS_CALLER_NOT_ALLOWED_PACKAGE_NOT_IN_ALLOWLIST),
                        anyInt());

        verifyNoMoreInteractions(mAdServicesLoggerMock);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_ALLOW_LISTS_FILTER_PACKAGE_NOT_IN_ALLOW_LIST,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS,
            throwable = AppNotAllowedException.class)
    public void testNotAllowedSignals() {
        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () ->
                                mFledgeAllowListsFilter.assertAppInAllowlist(
                                        PACKAGE_ALLOWED_PPAPI_1,
                                        API_NAME_LOGGING_ID,
                                        API_PROTECTED_SIGNALS));

        assertEquals(
                AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE,
                exception.getMessage());
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME_LOGGING_ID),
                        eq(PACKAGE_ALLOWED_PPAPI_1),
                        eq(STATUS_CALLER_NOT_ALLOWED_PACKAGE_NOT_IN_ALLOWLIST),
                        anyInt());

        verifyNoMoreInteractions(mAdServicesLoggerMock);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_ALLOW_LISTS_FILTER_PACKAGE_NOT_IN_ALLOW_LIST,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__JOIN_CUSTOM_AUDIENCE,
            throwable = AppNotAllowedException.class)
    public void testNotAllowed_logCel() {
        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () ->
                                mFledgeAllowListsFilter.assertAppInAllowlist(
                                        PACKAGE_ALLOWED_PAS_1,
                                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                                        API_CUSTOM_AUDIENCES));

        expect.withMessage("exception.getMessage")
                .that(exception)
                .hasMessageThat()
                .isEqualTo(
                        AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE);
    }

    @Test
    public void nullAppPackageName() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mFledgeAllowListsFilter.assertAppInAllowlist(
                                null, API_NAME_LOGGING_ID, API_CUSTOM_AUDIENCES));

        verifyZeroInteractions(mAdServicesLoggerMock);
    }

    public static class AllowListTestFlags implements Flags {

        @Override
        public String getPasAppAllowList() {
            return PACKAGE_ALLOWED_PAS_1
                    + ", "
                    + PACKAGE_ALLOWED_PAS_2
                    + ", "
                    + PACKAGE_ALLOWED_ALL;
        }

        @Override
        public String getPpapiAppAllowList() {
            return PACKAGE_ALLOWED_PPAPI_1
                    + ", "
                    + PACKAGE_ALLOWED_PPAPI_2
                    + ", "
                    + PACKAGE_ALLOWED_ALL;
        }
    }
}
