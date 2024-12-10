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

package com.android.adservices.service.stats;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_ADID;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SCHEDULE_CUSTOM_AUDIENCE_UPDATE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__UPDATE_SIGNALS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_AUTHORIZATION_FILTER_PERMISSION_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_CONSENT_FILTER_ALL_APIS_CONSENT_DISABLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__LEAVE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PERSIST_AD_SELECTION_RESULT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PPAPI_NAME_UNSPECIFIED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__REPORT_IMPRESSION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__REPORT_INTERACTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__SCHEDULE_CUSTOM_AUDIENCE_UPDATE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_VERY_LARGE;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_VERY_SMALL;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.checkAndLogCelByApiNameLoggingId;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.checkPpapiNameAndLogCel;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.computeSize;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.getCelPpApiNameId;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.logCelInsideBinderThread;

import static org.junit.Assert.assertEquals;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;

import org.junit.Test;

public class AdsRelevanceStatusUtilsTest extends AdServicesExtendedMockitoTestCase {
    @Test
    public void testComputeSize() {
        long[] buckets = {10, 20, 30, 40};
        int rawValue = 5;
        for (@AdsRelevanceStatusUtils.Size int i = SIZE_VERY_SMALL; i <= SIZE_VERY_LARGE; i++) {
            assertEquals(i, computeSize(rawValue, buckets));
            rawValue += 10;
        }
    }

    @Test
    public void testGetCelPpApiNameId() {
        assertEquals(
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA,
                getCelPpApiNameId(AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA));

        assertEquals(
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PERSIST_AD_SELECTION_RESULT,
                getCelPpApiNameId(AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT));

        assertEquals(
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS,
                getCelPpApiNameId(AD_SERVICES_API_CALLED__API_NAME__UPDATE_SIGNALS));

        assertEquals(
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__JOIN_CUSTOM_AUDIENCE,
                getCelPpApiNameId(AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE));

        assertEquals(
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PPAPI_NAME_UNSPECIFIED,
                getCelPpApiNameId(AD_SERVICES_API_CALLED__API_NAME__GET_ADID));

        assertEquals(
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS,
                getCelPpApiNameId(AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS));

        assertEquals(
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__LEAVE_CUSTOM_AUDIENCE,
                getCelPpApiNameId(AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE));

        assertEquals(
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE,
                getCelPpApiNameId(
                        AD_SERVICES_API_CALLED__API_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE));

        assertEquals(
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__REPORT_IMPRESSION,
                getCelPpApiNameId(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION));

        assertEquals(
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__REPORT_INTERACTION,
                getCelPpApiNameId(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION));

        assertEquals(
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__SCHEDULE_CUSTOM_AUDIENCE_UPDATE,
                getCelPpApiNameId(
                        AD_SERVICES_API_CALLED__API_NAME__SCHEDULE_CUSTOM_AUDIENCE_UPDATE));

    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_AUTHORIZATION_FILTER_PERMISSION_FAILURE,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA)
    public void testLogCelInsideBinderThread() {
        logCelInsideBinderThread(
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_AUTHORIZATION_FILTER_PERMISSION_FAILURE,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_AUTHORIZATION_FILTER_PERMISSION_FAILURE,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA,
            throwable = IllegalStateException.class)
    public void testLogCelInsideBinderThreadWithThrowable() {
        logCelInsideBinderThread(
                new IllegalStateException("test exception"),
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_AUTHORIZATION_FILTER_PERMISSION_FAILURE,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_AUTHORIZATION_FILTER_PERMISSION_FAILURE,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA,
            throwable = IllegalStateException.class)
    @ExpectErrorLogUtilCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_AUTHORIZATION_FILTER_PERMISSION_FAILURE,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA)
    public void testCheckAndLogCelByApiNameLoggingId() {
        checkAndLogCelByApiNameLoggingId(new IllegalStateException(),
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_AUTHORIZATION_FILTER_PERMISSION_FAILURE,
                AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA);
        checkAndLogCelByApiNameLoggingId(null,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_AUTHORIZATION_FILTER_PERMISSION_FAILURE,
                AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA);
        checkAndLogCelByApiNameLoggingId(null,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_CONSENT_FILTER_ALL_APIS_CONSENT_DISABLED,
                AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_AUTHORIZATION_FILTER_PERMISSION_FAILURE,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA,
            throwable = IllegalStateException.class)
    @ExpectErrorLogUtilCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_AUTHORIZATION_FILTER_PERMISSION_FAILURE,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA)
    public void testCheckPpapiNameAndLogCel() {
        checkPpapiNameAndLogCel(new IllegalStateException(),
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_AUTHORIZATION_FILTER_PERMISSION_FAILURE,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA);
        checkPpapiNameAndLogCel(null,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_AUTHORIZATION_FILTER_PERMISSION_FAILURE,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__GET_AD_SELECTION_DATA);
        checkPpapiNameAndLogCel(null,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__FLEDGE_CONSENT_FILTER_ALL_APIS_CONSENT_DISABLED,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PPAPI_NAME_UNSPECIFIED);
    }
}
