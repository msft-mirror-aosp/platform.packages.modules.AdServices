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
package com.android.adservices.shared.testing.concurrency;

import static com.android.adservices.shared.testing.concurrency.SyncCallbackSettings.DEFAULT_TIMEOUT_MS;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.testing.SharedSidelessTestCase;

import org.junit.Test;

public final class SyncCallbackSettingsTest extends SharedSidelessTestCase {

    @Test
    public void testDefaultBuilder() {
        SyncCallbackSettings settings = new SyncCallbackSettings.Builder().build();
        assertWithMessage("builder.build()").that(settings).isNotNull();

        expect.withMessage("getExpectedNumberCalls()")
                .that(settings.getExpectedNumberCalls())
                .isEqualTo(1);
        expect.withMessage("getMaxTimeoutMs()")
                .that(settings.getMaxTimeoutMs())
                .isEqualTo(DEFAULT_TIMEOUT_MS);
        expect.withMessage("isFailIfCalledOnMainThread()")
                .that(settings.isFailIfCalledOnMainThread())
                .isTrue();

        String toString = settings.toString();
        expect.withMessage("toString()").that(toString).contains("expectedNumberCalls=1");
        expect.withMessage("toString()")
                .that(toString)
                .contains("maxTimeoutMs=" + DEFAULT_TIMEOUT_MS);
        expect.withMessage("toString()").that(toString).contains("failIfCalledOnMainThread=true");
    }

    @Test
    public void testCustomBuilder() {
        SyncCallbackSettings settings =
                new SyncCallbackSettings.Builder()
                        .setExpectedNumberCalls(42)
                        .setMaxTimeoutMs(108)
                        .setFailIfCalledOnMainThread(false)
                        .build();

        expect.withMessage("getExpectedNumberCalls()")
                .that(settings.getExpectedNumberCalls())
                .isEqualTo(42);
        expect.withMessage("getMaxTimeoutMs()").that(settings.getMaxTimeoutMs()).isEqualTo(108);
        expect.withMessage("isFailIfCalledOnMainThread()")
                .that(settings.isFailIfCalledOnMainThread())
                .isFalse();

        String toString = settings.toString();
        expect.withMessage("toString()").that(toString).contains("expectedNumberCalls=42");
        expect.withMessage("toString()").that(toString).contains("maxTimeoutMs=108");
        expect.withMessage("toString()").that(toString).contains("failIfCalledOnMainThread=false");
    }

    @Test
    public void testBuildeReturnsUniqueObjects() {
        SyncCallbackSettings.Builder builder = new SyncCallbackSettings.Builder();

        SyncCallbackSettings settings1 = builder.build();
        SyncCallbackSettings settings2 = builder.build();

        expect.withMessage("2nd built object").that(settings2).isNotSameInstanceAs(settings1);
    }

    @Test
    public void testInvalidBuilderArgs() {
        SyncCallbackSettings.Builder builder = new SyncCallbackSettings.Builder();

        assertThrows(IllegalArgumentException.class, () -> builder.setExpectedNumberCalls(0));
        assertThrows(IllegalArgumentException.class, () -> builder.setExpectedNumberCalls(-1));
        assertThrows(IllegalArgumentException.class, () -> builder.setMaxTimeoutMs(0));
        assertThrows(IllegalArgumentException.class, () -> builder.setMaxTimeoutMs(-1));
    }
}
