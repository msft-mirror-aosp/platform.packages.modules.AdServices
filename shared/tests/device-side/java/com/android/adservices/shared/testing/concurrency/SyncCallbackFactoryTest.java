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

import static com.android.adservices.shared.testing.ConcurrencyHelper.runOnMainThread;
import static com.android.adservices.shared.testing.concurrency.SyncCallbackSettings.DEFAULT_TIMEOUT_MS;

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.adservices.shared.SharedUnitTestCase;

import org.junit.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class SyncCallbackFactoryTest extends SharedUnitTestCase {

    @Test
    public void testNewDefaultSettings() {
        var settings1 = SyncCallbackFactory.newDefaultSettings();
        expectDefaultValues("settings1", settings1);

        var settings2 = SyncCallbackFactory.newDefaultSettings();
        expectDefaultValues("settings2", settings2);
        expect.withMessage("settings2").that(settings2).isNotSameInstanceAs(settings1);
    }

    @Test
    public void testNewSettingsBuilder() {
        var builder1 = SyncCallbackFactory.newSettingsBuilder();
        expectDefaultValues("settings from builder1", builder1.build());

        var builder2 = SyncCallbackFactory.newSettingsBuilder();
        expectDefaultValues("settings from builder2", builder1.build());

        expect.withMessage("builder2").that(builder2).isNotSameInstanceAs(builder1);
    }

    @Test
    public void testIsMainThread_mainThread() throws Exception {
        var settings = SyncCallbackFactory.newDefaultSettings();
        ArrayBlockingQueue<Boolean> holder = new ArrayBlockingQueue<>(1);

        runOnMainThread(() -> holder.add(settings.isMainThread()));

        Boolean result = holder.poll(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        expect.withMessage("SyncCallbackSettings(%s).isMainThread()", settings)
                .that(result)
                .isTrue();
    }

    @Test
    public void testIsMainThread_notMainThread() {
        var settings = SyncCallbackFactory.newDefaultSettings();

        expect.withMessage("SyncCallbackSettings( %s).isMainThread()", settings)
                .that(settings.isMainThread())
                .isFalse();
    }

    private void expectDefaultValues(String name, SyncCallbackSettings settings) {
        assertWithMessage("settings").that(settings).isNotNull();
        expect.withMessage("%s.getMaxTimeoutMs()", name)
                .that(settings.getMaxTimeoutMs())
                .isEqualTo(DEFAULT_TIMEOUT_MS);
        expect.withMessage("%s.isFailIfCalledOnMainThread()", name)
                .that(settings.isFailIfCalledOnMainThread())
                .isTrue();
        expect.withMessage("%s.getLogger()", name).that(settings.getLogger()).isNotNull();
    }
}
