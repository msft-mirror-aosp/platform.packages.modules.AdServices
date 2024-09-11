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
import static com.android.adservices.service.common.Throttler.ApiKey.MEASUREMENT_API_REGISTER_SOURCE;
import static com.android.adservices.service.common.Throttler.ApiKey.MEASUREMENT_API_REGISTER_SOURCES;
import static com.android.adservices.service.common.Throttler.ApiKey.MEASUREMENT_API_REGISTER_TRIGGER;
import static com.android.adservices.service.common.Throttler.ApiKey.MEASUREMENT_API_REGISTER_WEB_SOURCE;
import static com.android.adservices.service.common.Throttler.ApiKey.MEASUREMENT_API_REGISTER_WEB_TRIGGER;
import static com.android.adservices.service.common.Throttler.ApiKey.UNKNOWN;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.AdServicesMockitoTestCase;

import org.junit.Test;

/** Unit tests for {@link Throttler}. */
public final class ThrottlerTest extends AdServicesMockitoTestCase {

    @Test
    public void testNewInstance_null() {
        assertThrows(NullPointerException.class, () -> Throttler.newInstance(null));
    }

    @Test
    public void testTryAcquire_skdName() throws InterruptedException {
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
    public void testTryAcquire_appPackageName() throws InterruptedException {
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
    public void testGetInstance_onEmptyFlags_throwNullPointerException() {
        assertThrows(NullPointerException.class, () -> Throttler.getInstance(null));
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
    }
}
