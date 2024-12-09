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

import static com.android.adservices.service.common.Throttler.ApiKey.ADID_API_APP_PACKAGE_NAME;
import static com.android.adservices.service.common.Throttler.ApiKey.APPSETID_API_APP_PACKAGE_NAME;
import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_FETCH_CUSTOM_AUDIENCE;
import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_GET_AD_SELECTION_DATA;
import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_LEAVE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_PERSIST_AD_SELECTION_RESULT;
import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_REPORT_IMPRESSIONS;
import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_REPORT_INTERACTION;
import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_SCHEDULE_CUSTOM_AUDIENCE_UPDATE;
import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_SELECT_ADS;
import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_SELECT_ADS_WITH_OUTCOMES;
import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_SET_APP_INSTALL_ADVERTISERS;
import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_UPDATE_AD_COUNTER_HISTOGRAM;
import static com.android.adservices.service.common.Throttler.ApiKey.MEASUREMENT_API_REGISTER_SOURCE;
import static com.android.adservices.service.common.Throttler.ApiKey.MEASUREMENT_API_REGISTER_SOURCES;
import static com.android.adservices.service.common.Throttler.ApiKey.MEASUREMENT_API_REGISTER_TRIGGER;
import static com.android.adservices.service.common.Throttler.ApiKey.MEASUREMENT_API_REGISTER_WEB_SOURCE;
import static com.android.adservices.service.common.Throttler.ApiKey.MEASUREMENT_API_REGISTER_WEB_TRIGGER;
import static com.android.adservices.service.common.Throttler.ApiKey.PROTECTED_SIGNAL_API_UPDATE_SIGNALS;
import static com.android.adservices.service.common.Throttler.ApiKey.UNKNOWN;
import static com.android.adservices.shared.testing.common.DumpHelper.dump;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.AdServicesMockitoTestCase;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** Unit tests for {@link Throttler}. */
public final class ThrottlerTest extends AdServicesMockitoTestCase {

    @Test
    public void testNewInstance_null() {
        assertThrows(NullPointerException.class, () -> Throttler.newInstance(null));
    }

    @Test
    public void testTryAcquire_skdName() throws Exception {
        // Create a throttler with 1 permit per second.
        Throttler throttler = createThrottler(1);

        // Sdk1 sends 2 requests almost concurrently. The first request is OK, the second fails
        // since the time between 2 requests is too small (less than the
        // sdkRequestPermitsPerSecond = 1)
        assertThat(throttler.tryAcquire(Throttler.ApiKey.TOPICS_API_SDK_NAME, "sdk1")).isTrue();
        assertThat(throttler.tryAcquire(Throttler.ApiKey.TOPICS_API_SDK_NAME, "sdk1")).isFalse();

        // Sdk2 is OK since there was no previous sdk2 request.
        assertThat(throttler.tryAcquire(Throttler.ApiKey.TOPICS_API_SDK_NAME, "sdk2")).isTrue();

        // Now sleep for 1 second so that the permits is refilled.
        Thread.sleep(1000L);

        // Now the request is OK again.
        assertThat(throttler.tryAcquire(Throttler.ApiKey.TOPICS_API_SDK_NAME, "sdk1")).isTrue();
        assertThat(throttler.tryAcquire(Throttler.ApiKey.TOPICS_API_SDK_NAME, "sdk2")).isTrue();
    }

    @Test
    public void testTryAcquire_appPackageName() throws Exception {
        // Create a throttler with 1 permit per second.
        Throttler throttler = createThrottler(1);

        // App1 sends 2 requests almost concurrently. The first request is OK, the second fails
        // since the time between 2 requests is too small (less than the
        // sdkRequestPermitsPerSecond = 1)
        assertThat(throttler.tryAcquire(Throttler.ApiKey.TOPICS_API_APP_PACKAGE_NAME, "app1"))
                .isTrue();
        assertThat(throttler.tryAcquire(Throttler.ApiKey.TOPICS_API_APP_PACKAGE_NAME, "app1"))
                .isFalse();
        // Sdk1 calling Topics API is ok since App and Sdk are throttled separately.
        assertThat(throttler.tryAcquire(Throttler.ApiKey.TOPICS_API_SDK_NAME, "sdk1")).isTrue();

        // App2 is OK since there was no previous App2 request.
        assertThat(throttler.tryAcquire(Throttler.ApiKey.TOPICS_API_APP_PACKAGE_NAME, "app2"))
                .isTrue();

        // Now sleep for 1 second so that the permits is refilled.
        Thread.sleep(1000L);

        // Now the request is OK again.
        assertThat(throttler.tryAcquire(Throttler.ApiKey.TOPICS_API_APP_PACKAGE_NAME, "app1"))
                .isTrue();
        assertThat(throttler.tryAcquire(Throttler.ApiKey.TOPICS_API_APP_PACKAGE_NAME, "app2"))
                .isTrue();
    }

    @Test
    public void testTryAcquire_skippingRateLimiting() {
        // Setting the permits per second to -1 so that we will skip the rate limiting.
        Throttler throttler = createThrottler(-1);
        assertThat(throttler.tryAcquire(Throttler.ApiKey.TOPICS_API_SDK_NAME, "sdk1")).isTrue();
        // Without skipping rate limiting, this second request would have failed.
        assertThat(throttler.tryAcquire(Throttler.ApiKey.TOPICS_API_SDK_NAME, "sdk1")).isTrue();

        // Another SDK.
        assertThat(throttler.tryAcquire(Throttler.ApiKey.TOPICS_API_SDK_NAME, "sdk2")).isTrue();
        assertThat(throttler.tryAcquire(Throttler.ApiKey.TOPICS_API_SDK_NAME, "sdk2")).isTrue();
    }

    @Test
    public void testTryAcquire_withThreePermitsPerSecond() {
        // Create a throttler with 3 permits per second.
        Throttler throttler = createThrottler(3);
        assertAcquireSeveralTimes(throttler, MEASUREMENT_API_REGISTER_SOURCE, 3);

        // Calling a different API.
        assertAcquireSeveralTimes(throttler, MEASUREMENT_API_REGISTER_TRIGGER, 3);
    }

    @Test
    public void testTryAcquire_withSeveralFlagsDifferentPermitsPerSecond() {
        // Create a throttler with several flags
        doReturn(1F).when(mMockFlags).getSdkRequestPermitsPerSecond();
        doReturn(2F).when(mMockFlags).getAdIdRequestPermitsPerSecond();
        doReturn(3F).when(mMockFlags).getAppSetIdRequestPermitsPerSecond();
        doReturn(4F).when(mMockFlags).getMeasurementRegisterSourceRequestPermitsPerSecond();
        doReturn(5F).when(mMockFlags).getMeasurementRegisterWebSourceRequestPermitsPerSecond();
        doReturn(6F).when(mMockFlags).getMeasurementRegisterTriggerRequestPermitsPerSecond();
        doReturn(7F).when(mMockFlags).getMeasurementRegisterWebTriggerRequestPermitsPerSecond();
        doReturn(8F).when(mMockFlags).getMeasurementRegisterSourcesRequestPermitsPerSecond();
        doReturn(9F).when(mMockFlags).getFledgeJoinCustomAudienceRequestPermitsPerSecond();
        doReturn(10F).when(mMockFlags).getFledgeFetchAndJoinCustomAudienceRequestPermitsPerSecond();
        doReturn(11F)
                .when(mMockFlags)
                .getFledgeScheduleCustomAudienceUpdateRequestPermitsPerSecond();
        doReturn(12F).when(mMockFlags).getFledgeLeaveCustomAudienceRequestPermitsPerSecond();
        doReturn(13F).when(mMockFlags).getFledgeUpdateSignalsRequestPermitsPerSecond();
        doReturn(14F).when(mMockFlags).getFledgeSelectAdsRequestPermitsPerSecond();
        doReturn(15F).when(mMockFlags).getFledgeSelectAdsWithOutcomesRequestPermitsPerSecond();
        doReturn(16F).when(mMockFlags).getFledgeGetAdSelectionDataRequestPermitsPerSecond();
        doReturn(17F).when(mMockFlags).getFledgeReportImpressionRequestPermitsPerSecond();
        doReturn(18F).when(mMockFlags).getFledgeReportInteractionRequestPermitsPerSecond();
        doReturn(19F).when(mMockFlags).getFledgePersistAdSelectionResultRequestPermitsPerSecond();
        doReturn(20F).when(mMockFlags).getFledgeSetAppInstallAdvertisersRequestPermitsPerSecond();
        doReturn(21F).when(mMockFlags).getFledgeUpdateAdCounterHistogramRequestPermitsPerSecond();

        Throttler throttler = Throttler.newInstance(mMockFlags);

        // Default ApiKey configured with 1 request per second
        assertAcquireSeveralTimes(throttler, UNKNOWN, 1);

        // Ad id ApiKey configured with 2 request per second
        assertAcquireSeveralTimes(throttler, ADID_API_APP_PACKAGE_NAME, 2);

        // App set id ApiKey configured with 3 request per second
        assertAcquireSeveralTimes(throttler, APPSETID_API_APP_PACKAGE_NAME, 3);

        // Register Source ApiKey configured with 4 request per second
        assertAcquireSeveralTimes(throttler, MEASUREMENT_API_REGISTER_SOURCE, 4);

        // Register Web Source ApiKey configured with 5 request per second
        assertAcquireSeveralTimes(throttler, MEASUREMENT_API_REGISTER_WEB_SOURCE, 5);

        // Register Trigger ApiKey configured with 6 request per second
        assertAcquireSeveralTimes(throttler, MEASUREMENT_API_REGISTER_TRIGGER, 6);

        // Register Web Trigger ApiKey configured with 7 request per second
        assertAcquireSeveralTimes(throttler, MEASUREMENT_API_REGISTER_WEB_TRIGGER, 7);

        // Register Sources ApiKey configured with 8 request per second
        assertAcquireSeveralTimes(throttler, MEASUREMENT_API_REGISTER_SOURCES, 8);

        // Register Sources ApiKey configured with 9 request per second
        assertAcquireSeveralTimes(throttler, FLEDGE_API_JOIN_CUSTOM_AUDIENCE, 9);

        // Register Sources ApiKey configured with 10 request per second
        assertAcquireSeveralTimes(throttler, FLEDGE_API_FETCH_CUSTOM_AUDIENCE, 10);

        // Register Sources ApiKey configured with 11 request per second
        assertAcquireSeveralTimes(throttler, FLEDGE_API_SCHEDULE_CUSTOM_AUDIENCE_UPDATE, 11);

        // Register Sources ApiKey configured with 11 request per second
        assertAcquireSeveralTimes(throttler, FLEDGE_API_LEAVE_CUSTOM_AUDIENCE, 12);

        // Register Sources ApiKey configured with 12 request per second
        assertAcquireSeveralTimes(throttler, PROTECTED_SIGNAL_API_UPDATE_SIGNALS, 13);

        // Register Sources ApiKey configured with 13 request per second
        assertAcquireSeveralTimes(throttler, FLEDGE_API_SELECT_ADS, 14);

        // Register Sources ApiKey configured with 14 request per second
        assertAcquireSeveralTimes(throttler, FLEDGE_API_SELECT_ADS_WITH_OUTCOMES, 15);

        // Register Sources ApiKey configured with 15 request per second
        assertAcquireSeveralTimes(throttler, FLEDGE_API_GET_AD_SELECTION_DATA, 16);

        // Register Sources ApiKey configured with 16 request per second
        assertAcquireSeveralTimes(throttler, FLEDGE_API_REPORT_IMPRESSIONS, 17);

        // Register Sources ApiKey configured with 17 request per second
        assertAcquireSeveralTimes(throttler, FLEDGE_API_REPORT_INTERACTION, 18);

        // Register Sources ApiKey configured with 18 request per second
        assertAcquireSeveralTimes(throttler, FLEDGE_API_PERSIST_AD_SELECTION_RESULT, 19);

        // Register Sources ApiKey configured with 19 request per second
        assertAcquireSeveralTimes(throttler, FLEDGE_API_SET_APP_INSTALL_ADVERTISERS, 20);

        // Register Sources ApiKey configured with 20 request per second
        assertAcquireSeveralTimes(throttler, FLEDGE_API_UPDATE_AD_COUNTER_HISTOGRAM, 21);
    }

    @Test
    public void testThrottler_withOnePermitPerSecond() {
        // Create a throttler with 1 permit per second from getInstance.
        mockFlags(1F);
        Throttler throttler = Throttler.newInstance(mMockFlags);

        // tryAcquire should return false after 1 permit
        assertAcquireSeveralTimes(throttler, MEASUREMENT_API_REGISTER_SOURCE, 1);

        // Calling a different API. tryAcquire should return false after 1 permit
        assertAcquireSeveralTimes(throttler, MEASUREMENT_API_REGISTER_TRIGGER, 1);
    }

    @Test
    public void testDump_noSdk() throws Exception {
        Throttler throttler = createThrottler(1);

        testDump(throttler, /* expectSdkRateLimitLines= */ 0);
    }

    @Test
    public void testDump_withSdk() throws Exception {
        Throttler throttler = createThrottler(1);

        throttler.tryAcquire(Throttler.ApiKey.TOPICS_API_SDK_NAME, "sdk1");
        throttler.tryAcquire(Throttler.ApiKey.TOPICS_API_APP_PACKAGE_NAME, "app2");
        throttler.tryAcquire(Throttler.ApiKey.TOPICS_API_APP_PACKAGE_NAME, "app1");

        testDump(throttler, /* expectSdkRateLimitLines= */ 3);
    }

    private void testDump(Throttler throttler, int expectedSdkRateLimitLinesLength)
            throws Exception {
        String dump = dump(pw -> throttler.dump(pw));
        mLog.v("Throttler.dump():\n%s", dump);

        // Ideally it should check the exact value of each entry, but it's an overkill - just
        // checking that the lines on each section are sorted is good enough...
        String[] lines = dump.split("\n");
        int lineNumber = 0; // lines iterator
        String line = null; // line being asserted

        List<String> sortedKeys =
                Arrays.stream(Throttler.ApiKey.values())
                        .map(Throttler.ApiKey::toString)
                        .collect(Collectors.toList());
        Collections.sort(sortedKeys);
        if (lines.length < sortedKeys.size() + 3) {
            expect.withMessage(
                            "dump() is missing some rate-limit API Key lines; assertions below"
                                    + " might be out of sync")
                    .fail();
        }
        expect.withMessage("line 0").that(lines[lineNumber++]).isEqualTo("Throttler");

        // First section - rate limit
        expect.withMessage("line 1").that(lines[lineNumber++]).isEqualTo("  Rate limit per API");
        for (String apiKey : sortedKeys) {
            line = lines[lineNumber++];
            if (lineNumber < lines.length - 1) {
                expect.withMessage("line %s", lineNumber)
                        .that(line)
                        .startsWith("    " + apiKey + ": ");
            } else {
                mLog.w("Ignoring dump() after line number %s", lineNumber);
                return;
            }
        }

        // Second section - rate limit
        if (expectedSdkRateLimitLinesLength == 0) {
            expect.withMessage("line %s", lineNumber)
                    .that(lines[lineNumber++])
                    .isEqualTo("  SDK rate limit per API: N/A");
        } else {
            expect.withMessage("line %s", lineNumber)
                    .that(lines[lineNumber++])
                    .isEqualTo("  SDK rate limit per API:");

            // We don't know - actually, we don't care :-) - what the lines are, just that they're
            // sorted. So, we scan all lines and asserts the actual / expected at the end
            List<String> actualSdkRateLimitLines = new ArrayList<>(expectedSdkRateLimitLinesLength);
            for (int i = 0; i < expectedSdkRateLimitLinesLength; i++) {
                line = lines[lineNumber];
                actualSdkRateLimitLines.add(line);
                lineNumber += i;
            }
            List<String> expectedSdkRateLimitLines = new ArrayList<>(actualSdkRateLimitLines);
            Collections.sort(expectedSdkRateLimitLines);

            expect.withMessage("sdk rate limit lines")
                    .that(actualSdkRateLimitLines)
                    .containsExactlyElementsIn(expectedSdkRateLimitLines)
                    .inOrder();
        }

        // Finally, makes sure there's nothing else left
        assertNoMoreLinesAfterLine(lines, lineNumber);
    }

    private void assertNoMoreLinesAfterLine(String[] lines, int lineNumber) {
        int numberExtraLines = lines.length - lineNumber;
        if (lineNumber == lines.length) {
            return;
        }
        String extraLines =
                Arrays.stream(lines, lineNumber, lines.length).collect(Collectors.joining("\n"));
        expect.withMessage("%s extra lines at the end: %s", numberExtraLines, extraLines).fail();
    }

    private void assertAcquireSeveralTimes(
            Throttler throttler, Throttler.ApiKey apiKey, int validTimes) {
        String defaultRequester = "requester";
        for (int i = 0; i < validTimes; i++) {
            assertThat(throttler.tryAcquire(apiKey, defaultRequester)).isTrue();
        }
        assertThat(throttler.tryAcquire(apiKey, defaultRequester)).isFalse();
    }

    private Throttler createThrottler(float permitsPerSecond) {
        mockFlags(permitsPerSecond);
        return Throttler.newInstance(mMockFlags);
    }

    private void mockFlags(float permitsPerSecond) {
        doReturn(permitsPerSecond).when(mMockFlags).getSdkRequestPermitsPerSecond();
        doReturn(permitsPerSecond)
                .when(mMockFlags)
                .getMeasurementRegisterSourceRequestPermitsPerSecond();
        doReturn(permitsPerSecond)
                .when(mMockFlags)
                .getMeasurementRegisterSourcesRequestPermitsPerSecond();
        doReturn(permitsPerSecond)
                .when(mMockFlags)
                .getMeasurementRegisterWebSourceRequestPermitsPerSecond();
        doReturn(permitsPerSecond)
                .when(mMockFlags)
                .getMeasurementRegisterTriggerRequestPermitsPerSecond();
        doReturn(permitsPerSecond)
                .when(mMockFlags)
                .getMeasurementRegisterWebTriggerRequestPermitsPerSecond();
        doReturn(permitsPerSecond).when(mMockFlags).getTopicsApiSdkRequestPermitsPerSecond();
        doReturn(permitsPerSecond).when(mMockFlags).getTopicsApiAppRequestPermitsPerSecond();
        doReturn(permitsPerSecond)
                .when(mMockFlags)
                .getFledgeJoinCustomAudienceRequestPermitsPerSecond();
        doReturn(permitsPerSecond)
                .when(mMockFlags)
                .getFledgeFetchAndJoinCustomAudienceRequestPermitsPerSecond();
        doReturn(permitsPerSecond)
                .when(mMockFlags)
                .getFledgeLeaveCustomAudienceRequestPermitsPerSecond();
        doReturn(permitsPerSecond).when(mMockFlags).getFledgeUpdateSignalsRequestPermitsPerSecond();
        doReturn(permitsPerSecond).when(mMockFlags).getFledgeSelectAdsRequestPermitsPerSecond();
        doReturn(permitsPerSecond)
                .when(mMockFlags)
                .getFledgeSelectAdsWithOutcomesRequestPermitsPerSecond();
        doReturn(permitsPerSecond)
                .when(mMockFlags)
                .getFledgeGetAdSelectionDataRequestPermitsPerSecond();
        doReturn(permitsPerSecond)
                .when(mMockFlags)
                .getFledgeReportImpressionRequestPermitsPerSecond();
        doReturn(permitsPerSecond)
                .when(mMockFlags)
                .getFledgeReportInteractionRequestPermitsPerSecond();
        doReturn(permitsPerSecond)
                .when(mMockFlags)
                .getFledgePersistAdSelectionResultRequestPermitsPerSecond();
        doReturn(permitsPerSecond)
                .when(mMockFlags)
                .getFledgeSetAppInstallAdvertisersRequestPermitsPerSecond();
        doReturn(permitsPerSecond)
                .when(mMockFlags)
                .getFledgeUpdateAdCounterHistogramRequestPermitsPerSecond();
    }
}
