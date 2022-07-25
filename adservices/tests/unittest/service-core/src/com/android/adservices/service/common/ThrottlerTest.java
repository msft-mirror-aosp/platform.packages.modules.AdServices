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

import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/** Unit tests for {@link Throttler}. */
@SmallTest
public class ThrottlerTest {
    @Test
    public void testTryAcquire() throws InterruptedException {
        // Create a throttler with 1 permit per second.
        Throttler throttler = new Throttler(1);

        // Sdk1 sends 2 requests almost concurrently. The first request is OK, the second fails
        // since the time between 2 requests is too small (less than the
        // sdkRequestPermitsPerSecond = 1)
        assertThat(throttler.tryAcquire(Throttler.ApiKey.TOPICS_API, "sdk1")).isTrue();
        assertThat(throttler.tryAcquire(Throttler.ApiKey.TOPICS_API, "sdk1")).isFalse();

        // Sdk2 is OK since there was no previous sdk2 request.
        assertThat(throttler.tryAcquire(Throttler.ApiKey.TOPICS_API, "sdk2")).isTrue();

        // Now sleep for 1 second so that the permits is refilled.
        Thread.sleep(1000L);

        // Now the request is OK again.
        assertThat(throttler.tryAcquire(Throttler.ApiKey.TOPICS_API, "sdk1")).isTrue();
        assertThat(throttler.tryAcquire(Throttler.ApiKey.TOPICS_API, "sdk2")).isTrue();
    }

    @Test
    public void testTryAcquire_skippingRateLimiting() {
        // Setting the permits per second to -1 so that we will skip the rate limiting.
        Throttler throttler = new Throttler(-1);
        assertThat(throttler.tryAcquire(Throttler.ApiKey.TOPICS_API, "sdk1")).isTrue();
        // Without skipping rate limiting, this second request would have failed.
        assertThat(throttler.tryAcquire(Throttler.ApiKey.TOPICS_API, "sdk1")).isTrue();

        // Another SDK.
        assertThat(throttler.tryAcquire(Throttler.ApiKey.TOPICS_API, "sdk2")).isTrue();
        assertThat(throttler.tryAcquire(Throttler.ApiKey.TOPICS_API, "sdk2")).isTrue();
    }
}
