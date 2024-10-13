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

import static com.android.adservices.shared.testing.concurrency.SyncCallback.LOG_TAG;
import static com.android.adservices.shared.testing.concurrency.SyncCallbackSettings.DEFAULT_TIMEOUT_MS;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.meta_testing.SharedSidelessTestCase;
import com.android.adservices.shared.testing.Logger;

import org.junit.Test;

public final class SyncCallbackSettingsTest extends SharedSidelessTestCase {

    @Test
    public void testBuilderWithInvalidArgs() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new SyncCallbackSettings.Builder(
                                /* realLogger= */ null, () -> Boolean.FALSE));
        assertThrows(
                NullPointerException.class,
                () ->
                        new SyncCallbackSettings.Builder(
                                mFakeRealLogger, /* isMainThreadSupplier= */ null));
    }

    @Test
    public void testDefaultBuilder() {
        SyncCallbackSettings settings = newDefaultBuilder().build();
        assertWithMessage("1st call").that(settings).isNotNull();

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

        // Should always return a new instance
        SyncCallbackSettings settings2 = newDefaultBuilder().build();
        assertWithMessage("2nd call()").that(settings2).isNotSameInstanceAs(settings);
    }

    @Test
    public void testCustomBuilder() {
        SyncCallbackSettings settings =
                newDefaultBuilder()
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
        SyncCallbackSettings.Builder builder = newDefaultBuilder();

        SyncCallbackSettings settings1 = builder.build();
        SyncCallbackSettings settings2 = builder.build();

        expect.withMessage("2nd built object").that(settings2).isNotSameInstanceAs(settings1);
    }

    @Test
    public void testInvalidBuilderArgs() {
        SyncCallbackSettings.Builder builder = newDefaultBuilder();

        assertThrows(IllegalArgumentException.class, () -> builder.setExpectedNumberCalls(0));
        assertThrows(IllegalArgumentException.class, () -> builder.setExpectedNumberCalls(-1));
        assertThrows(IllegalArgumentException.class, () -> builder.setMaxTimeoutMs(0));
        assertThrows(IllegalArgumentException.class, () -> builder.setMaxTimeoutMs(-1));
    }

    @Test
    public void testGetId() {
        SyncCallbackSettings settings1 = newDefaultBuilder().build();
        String id1 = settings1.getId();
        expect.withMessage("callback1.getId()").that(id1).isNotEmpty();

        SyncCallbackSettings settings2 = newDefaultBuilder().build();
        String id2 = settings2.getId();
        expect.withMessage("callback2.getId()").that(id2).isNotEmpty();
        expect.withMessage("callback2.getId()").that(id2).isNotEqualTo(id1);

        String toString = settings1.toString();
        expect.withMessage("toString()").that(toString).contains("settingsId=" + id1);
    }

    @Test
    public void testGetLogger() {
        SyncCallbackSettings settings = newDefaultBuilder().build();

        Logger logger = settings.getLogger();
        expect.withMessage("getLogger()").that(logger).isNotNull();
        expect.withMessage("getLogger().getTag()").that(logger.getTag()).isEqualTo(LOG_TAG);
    }

    @Test
    public void testCheckCanFailOnMainThread_null() {
        assertThrows(
                NullPointerException.class,
                () -> SyncCallbackSettings.checkCanFailOnMainThread(null));
    }

    @Test
    public void testCheckCanFailOnMainThread_YesItCan() {
        SyncCallbackSettings settings =
                SyncCallbackSettings.checkCanFailOnMainThread(
                        newDefaultBuilder().setFailIfCalledOnMainThread(false).build());
        expect.withMessage("isFailIfCalledOnMainThread()")
                .that(settings.isFailIfCalledOnMainThread())
                .isFalse();
    }

    @Test
    public void testCheckCanFailOnMainThread_nope() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SyncCallbackSettings.checkCanFailOnMainThread(
                                newDefaultBuilder().setFailIfCalledOnMainThread(true).build()));
    }

    private SyncCallbackSettings.Builder newDefaultBuilder() {
        return new SyncCallbackSettings.Builder(mFakeRealLogger, () -> Boolean.FALSE);
    }
}
