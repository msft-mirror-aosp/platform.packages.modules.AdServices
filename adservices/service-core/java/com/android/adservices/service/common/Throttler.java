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

import android.annotation.NonNull;
import android.util.Pair;

import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.RateLimiter;

import java.util.concurrent.ConcurrentHashMap;

/** Class to throttle PPAPI requests. */
public class Throttler {
    // Enum for each PP API or entry point that will be throttled.
    public enum ApiKey {
        UNKNOWN,
        TOPICS_API
    }

    private static volatile Throttler sSingleton;

    private final double mSdkRequestPermitsPerSecond;

    // A Map from a Pair<ApiKey, Requester> to its RateLimiter.
    // The Requester could be a SdkName or an AppPackageName depending on the rate limiting needs.
    // Example Pair<TOPICS_API, "SomeSdkName">, Pair<TOPICS_API, "SomePackageName">.
    private final ConcurrentHashMap<Pair<ApiKey, String>, RateLimiter> mSdkRateLimitMap =
            new ConcurrentHashMap<>();

    /** Returns the singleton instance of the Throttler. */
    @NonNull
    public static Throttler getInstance(double sdkRequestPermitsPerSecond) {
        synchronized (Throttler.class) {
            if (null == sSingleton) {
                sSingleton = new Throttler(sdkRequestPermitsPerSecond);
            }
            return sSingleton;
        }
    }

    @VisibleForTesting
    Throttler(double sdkRequestPermitsPerSecond) {
        mSdkRequestPermitsPerSecond = sdkRequestPermitsPerSecond;
    }

    /**
     * Acquires a permit for an API and a Requester if it can be acquired immediately without delay.
     * Example: {@code tryAcquire(TOPICS_API, "SomeSdkName") }
     *
     * @return {@code true} if the permit was acquired, {@code false} otherwise
     */
    public boolean tryAcquire(ApiKey apiKey, String requester) {
        // Negative Permits Per Second turns off rate limiting.
        if (mSdkRequestPermitsPerSecond <= 0) {
            return true;
        }

        RateLimiter rateLimiter =
                mSdkRateLimitMap.computeIfAbsent(
                        Pair.create(apiKey, requester),
                        ignored -> RateLimiter.create(mSdkRequestPermitsPerSecond));
        return rateLimiter.tryAcquire();
    }
}
