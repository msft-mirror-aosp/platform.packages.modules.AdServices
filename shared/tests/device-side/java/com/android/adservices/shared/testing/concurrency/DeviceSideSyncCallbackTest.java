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

import static com.android.adservices.shared.testing.concurrency.DeviceSideConcurrencyHelper.runOnMainThread;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class DeviceSideSyncCallbackTest
        extends IBinderSyncCallbackTestCase<DeviceSideSyncCallback> {

    @Override
    protected DeviceSideSyncCallback newCallback(SyncCallbackSettings settings) {
        return new ConcreteDeviceSideySncCallback(settings);
    }

    // Note: SyncCallbackTestCase already tests what happens when called on main thread, but it's
    // not the "real" main thread, as it's emulated by the SyncCallbackSettings supplier
    @Test
    public void testSetCalled_calledOnMainThread_fails() throws Exception {
        ConcreteDeviceSideySncCallback callback =
                new ConcreteDeviceSideySncCallback(SyncCallbackFactory.newDefaultSettings());
        expect.withMessage("toString()")
                .that(callback.toString())
                .contains("failIfCalledOnMainThread=true");

        runOnMainThread(() -> call(callback));

        CalledOnMainThreadException thrown =
                assertThrows(CalledOnMainThreadException.class, () -> callback.assertCalled());

        expect.withMessage("thrown")
                .that(thrown)
                .hasMessageThat()
                .contains("setCalled() called on main thread");
        expect.withMessage("toString() after thrown")
                .that(callback.toString())
                .contains("onAssertCalledException=" + thrown);
    }

    // Note: SyncCallbackTestCase already tests what happens when called on main thread, but it's
    // not the "real" main thread, as it's emulated by the SyncCallbackSettings supplier
    @Test
    public void testSetCalled_calledOnMainThread_pass() throws Exception {
        ConcreteDeviceSideySncCallback callback =
                new ConcreteDeviceSideySncCallback(
                        SyncCallbackFactory.newSettingsBuilder()
                                .setFailIfCalledOnMainThread(false)
                                .build());
        expect.withMessage("toString()")
                .that(callback.toString())
                .contains("failIfCalledOnMainThread=false");

        runOnMainThread(() -> call(callback));

        callback.assertCalled();
    }

    private static final class ConcreteDeviceSideySncCallback extends DeviceSideSyncCallback
            implements ResultlessSyncCallback {

        @SuppressWarnings("unused") // Called by superclass using reflection
        ConcreteDeviceSideySncCallback() {
            this(SyncCallbackFactory.newSettingsBuilder().build());
        }

        ConcreteDeviceSideySncCallback(SyncCallbackSettings settings) {
            super(settings);
        }

        @Override
        public void setCalled() {
            internalSetCalled("setCalled()");
        }
    }
}
