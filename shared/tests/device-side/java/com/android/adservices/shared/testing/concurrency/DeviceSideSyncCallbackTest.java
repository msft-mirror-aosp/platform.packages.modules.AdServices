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

import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class DeviceSideSyncCallbackTest
        extends IBinderSyncCallbackTestCase<DeviceSideSyncCallback> {

    @Override
    protected DeviceSideSyncCallback newCallback(SyncCallbackSettings settings) {
        return new ConcreteDeviceSideyncCallback(settings);
    }

    @Test
    public void testSetCalled_calledOnMainThread_fails() throws Exception {
        ConcreteDeviceSideyncCallback callback = new ConcreteDeviceSideyncCallback();
        expect.withMessage("toString()")
                .that(callback.toString())
                .contains("failIfCalledOnMainThread=true");

        runOnMainThread(() -> callback.setCalled());

        CalledOnMainThreadException thrown =
                assertThrows(CalledOnMainThreadException.class, () -> callback.assertCalled());

        expect.withMessage("thrownn")
                .that(thrown)
                .hasMessageThat()
                .contains("setCalled() called on main thread");
        expect.withMessage("toString() after thrown")
                .that(callback.toString())
                .contains("internalFailure=" + thrown);
    }

    @Test
    public void testSetCalled_calledOnMainThread_pass() throws Exception {
        ConcreteDeviceSideyncCallback callback =
                new ConcreteDeviceSideyncCallback(
                        SyncCallbackFactory.newSettingsBuilder()
                                .setFailIfCalledOnMainThread(false)
                                .build());
        expect.withMessage("toString()")
                .that(callback.toString())
                .contains("failIfCalledOnMainThread=false");

        runOnMainThread(() -> callback.setCalled());

        callback.assertCalled();
    }

    @Test
    public void testPostAssertCalled_afterSetInternalFailure() throws Exception {
        ConcreteDeviceSideyncCallback callback = new ConcreteDeviceSideyncCallback();
        RuntimeException failure = new RuntimeException("D'OH!");

        callback.setInternalFailure(failure);
        RuntimeException thrown =
                assertThrows(RuntimeException.class, () -> callback.postAssertCalled());

        expect.withMessage("exception").that(thrown).isSameInstanceAs(failure);
    }

    @Test
    public void testSetInternalFailure_null() throws Exception {
        ConcreteDeviceSideyncCallback callback = new ConcreteDeviceSideyncCallback();

        assertThrows(NullPointerException.class, () -> callback.setInternalFailure(null));
    }

    @Test
    public void testToString() {
        ConcreteDeviceSideyncCallback callback = new ConcreteDeviceSideyncCallback();

        String toString = callback.toString();

        // Asserts only relevant info that were not already asserted in other tests
        expect.withMessage("toString()").that(toString).contains("epoch=");
        expect.withMessage("toString()").that(toString).contains("internalFailure=null");
    }

    private static final class ConcreteDeviceSideyncCallback extends DeviceSideSyncCallback {

        ConcreteDeviceSideyncCallback() {
            this(SyncCallbackFactory.newSettingsBuilder().build());
        }

        ConcreteDeviceSideyncCallback(SyncCallbackSettings settings) {
            super(settings);
        }
    }
}
