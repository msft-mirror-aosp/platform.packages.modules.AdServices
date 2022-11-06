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

import static com.android.adservices.service.common.Throttler.ApiKey.MEASUREMENT_API_REGISTER_SOURCE;
import static com.android.adservices.service.common.Throttler.ApiKey.MEASUREMENT_API_REGISTER_TRIGGER;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import androidx.test.filters.SmallTest;

import com.android.adservices.service.Flags;

import org.junit.Test;

/** Unit tests for {@link Throttler}. */
@SmallTest
public class ThrottlerTest {
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
        assertThat(throttler.tryAcquire(MEASUREMENT_API_REGISTER_SOURCE, "sdk1")).isTrue();
        assertThat(throttler.tryAcquire(MEASUREMENT_API_REGISTER_SOURCE, "sdk1")).isTrue();
        assertThat(throttler.tryAcquire(MEASUREMENT_API_REGISTER_SOURCE, "sdk1")).isTrue();

        // tryAcquire should return false as it has used the 3 permits per second
        assertThat(throttler.tryAcquire(MEASUREMENT_API_REGISTER_SOURCE, "sdk1")).isFalse();

        // Calling a different API.
        assertThat(throttler.tryAcquire(MEASUREMENT_API_REGISTER_TRIGGER, "sdk1")).isTrue();
        assertThat(throttler.tryAcquire(MEASUREMENT_API_REGISTER_TRIGGER, "sdk1")).isTrue();
        assertThat(throttler.tryAcquire(MEASUREMENT_API_REGISTER_TRIGGER, "sdk1")).isTrue();

        // tryAcquire should return false as it has used the 3 permits per second
        assertThat(throttler.tryAcquire(MEASUREMENT_API_REGISTER_TRIGGER, "sdk1")).isFalse();
    }

    @Test
    public void testGetInstance_withOnePermitPerSecond() {
        // Reset throttler state. There is no guarantee another class has initialized the Throttler
        // using getInstance, therefore destroying the throttler before testing. This is the only
        // method using getInstance while the others are using the constructor, therefore there is
        // no need to add this to the setup and tear down test phase.
        Throttler.destroyExistingThrottler();

        // Create a throttler with 1 permit per second from getInstance.
        final Flags flags = createFlags(1F);
        Throttler throttler = Throttler.getInstance(flags);

        // tryAcquire should return false after 1 permit
        assertThat(throttler.tryAcquire(MEASUREMENT_API_REGISTER_SOURCE, "sdk1")).isTrue();
        assertThat(throttler.tryAcquire(MEASUREMENT_API_REGISTER_SOURCE, "sdk1")).isFalse();

        // Calling a different API. tryAcquire should return false after 1 permit
        assertThat(throttler.tryAcquire(MEASUREMENT_API_REGISTER_TRIGGER, "sdk1")).isTrue();
        assertThat(throttler.tryAcquire(MEASUREMENT_API_REGISTER_TRIGGER, "sdk1")).isFalse();

        // Reset throttler state.
        // If another class calls getInstance, it can be initialized to its original state
        Throttler.destroyExistingThrottler();
    }

    @Test
    public void testGetInstance_onEmptyFlags_throwNullPointerException() {
        assertThrows(NullPointerException.class, () -> Throttler.getInstance(null));
    }

    private Throttler createThrottler(float permitsPerSecond) {
        final Flags flags = createFlags(permitsPerSecond);
        return new Throttler(flags);
    }

    private Flags createFlags(float permitsPerSecond) {
        final Flags flags = mock(Flags.class);
        doReturn(permitsPerSecond).when(flags).getSdkRequestPermitsPerSecond();
        return flags;
    }
}
