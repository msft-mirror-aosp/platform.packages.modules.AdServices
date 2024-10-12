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

import com.android.adservices.shared.meta_testing.FakeRealLogger;

import org.junit.Test;

public final class AbstractSyncCallbackTest extends SyncCallbackTestCase<AbstractSyncCallback> {

    @Override
    protected AbstractSyncCallback newCallback(SyncCallbackSettings settings) {
        return new ConcreteSyncCallback(settings);
    }

    @Override
    protected String callCallback(AbstractSyncCallback callback) {
        return callback.internalSetCalled("internalSetCalled()");
    }

    @Override
    protected void assertCalled(AbstractSyncCallback callback, long timeoutMs)
            throws InterruptedException {
        callback.internalAssertCalled(timeoutMs);
    }

    @Test
    public void testToString() {
        var cb = newCallback(mDefaultSettings);

        String toString = cb.toString();

        expect.withMessage("toString()")
                .that(toString)
                .startsWith("[" + cb.getClass().getSimpleName() + ":");
        expect.withMessage("toString()").that(toString).contains("id=" + cb.getId());
        expect.withMessage("toString()")
                .that(toString)
                .contains("numberActualCalls=" + cb.getNumberActualCalls());
        expect.withMessage("toString()").that(toString).contains(mDefaultSettings.toString());
        expect.withMessage("toString()").that(toString).contains("onAssertCalledException=null");
        expect.withMessage("toString()").that(toString).endsWith("]");
    }

    @Test
    public void testToStringLite() {
        var callback = newCallback(mDefaultSettings);

        String toStringLite = callback.toStringLite();

        expect.withMessage("toStringLite()")
                .that(toStringLite)
                .isEqualTo(
                        "[" + callback.getClass().getSimpleName() + "#" + callback.getId() + "]");
    }

    @Test
    public void testCustomizeToString() {
        AbstractSyncCallback callback =
                new AbstractSyncCallback(mDefaultSettings) {
                    protected void customizeToString(StringBuilder string) {
                        string.append("I AM GROOT");
                    }
                };
        expect.withMessage("customized toString() ")
                .that(callback.toString())
                .contains("I AM GROOT");
    }

    @Test
    public void testLogE() {
        var callback = newCallback(mDefaultSettings);
        var log = new LogChecker(callback);

        callback.logE("Danger, %s %s!", "Will", "Robinson");

        expectLoggedCalls(log.e("Danger, Will Robinson!"));
    }

    @Test
    public void testLogD() {
        var callback = newCallback(mDefaultSettings);
        var log = new LogChecker(callback);

        callback.logD("Danger, %s %s!", "Will", "Robinson");

        expectLoggedCalls(log.d("Danger, Will Robinson!"));
    }

    @Test
    public void testLogV() {
        var callback = newCallback(mDefaultSettings);
        var log = new LogChecker(callback);

        callback.logV("Danger, %s %s!", "Will", "Robinson");

        expectLoggedCalls(log.v("Danger, Will Robinson!"));
    }

    private static final class ConcreteSyncCallback extends AbstractSyncCallback {

        @SuppressWarnings("unused") // Called by superclass using reflection
        ConcreteSyncCallback() {
            super(new SyncCallbackSettings.Builder(new FakeRealLogger()).build());
        }

        ConcreteSyncCallback(SyncCallbackSettings settings) {
            super(settings);
        }
    }
}
