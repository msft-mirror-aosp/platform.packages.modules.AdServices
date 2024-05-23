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

import static com.android.adservices.shared.testing.concurrency.AbstractSyncCallback.LOG_TAG;
import static com.android.adservices.shared.testing.ConcurrencyHelper.runOnMainThread;

import static org.junit.Assert.assertThrows;

import android.util.Log;

import com.android.adservices.mockito.LogInterceptor;
import com.android.adservices.shared.SharedExtendedMockitoTestCase;
import com.android.adservices.shared.testing.LogEntry.Level;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Test;

public final class AbstractTestSyncCallbackTest extends SharedExtendedMockitoTestCase {

    @Test
    @SpyStatic(Log.class)
    public void testLogV() {
        ConcreteTestSyncCallback callback = new ConcreteTestSyncCallback();
        String tag = LOG_TAG;
        LogInterceptor logInterceptor = mocker.interceptLogV(tag);

        callback.logV("Answer=%d", 42);

        expect.withMessage("Log.*() calls to tag %s", tag)
                .that(logInterceptor.getAllEntries(tag))
                .hasSize(1);
        expect.withMessage("Log.v() calls to tag %s", tag)
                .that(logInterceptor.getPlainMessages(tag, Level.VERBOSE))
                .containsExactly(callback + ": Answer=42");
    }

    @Test
    @SpyStatic(Log.class)
    public void testLogD() {
        ConcreteTestSyncCallback callback = new ConcreteTestSyncCallback();
        String tag = LOG_TAG;
        LogInterceptor logInterceptor = mocker.interceptLogD(tag);

        callback.logD("Answer=%d", 42);

        expect.withMessage("Log.*() calls to tag %s", tag)
                .that(logInterceptor.getAllEntries(tag))
                .hasSize(1);
        expect.withMessage("Log.d() calls to tag %s", tag)
                .that(logInterceptor.getPlainMessages(tag, Level.DEBUG))
                .containsExactly("[" + callback.getId() + "]: Answer=42");
    }

    @Test
    @SpyStatic(Log.class)
    public void testLogE() {
        ConcreteTestSyncCallback callback = new ConcreteTestSyncCallback();
        String tag = LOG_TAG;
        LogInterceptor logInterceptor = mocker.interceptLogE(tag);

        callback.logE("Answer=%d", 42);

        expect.withMessage("Log.*() calls to tag %s", tag)
                .that(logInterceptor.getAllEntries(tag))
                .hasSize(1);
        expect.withMessage("Log.e() calls to tag %s", tag)
                .that(logInterceptor.getPlainMessages(tag, Level.ERROR))
                .containsExactly(callback + ": Answer=42");
    }

    @Test
    public void testSetCalled_calledOnMainThread_fails() throws Exception {
        ConcreteTestSyncCallback callback = new ConcreteTestSyncCallback();
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
        ConcreteTestSyncCallback callback =
                new ConcreteTestSyncCallback(
                        new SyncCallbackSettings.Builder()
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
        ConcreteTestSyncCallback callback = new ConcreteTestSyncCallback();
        RuntimeException failure = new RuntimeException("D'OH!");

        callback.setInternalFailure(failure);
        RuntimeException thrown =
                assertThrows(RuntimeException.class, () -> callback.postAssertCalled());

        expect.withMessage("exception").that(thrown).isSameInstanceAs(failure);
    }

    @Test
    public void testSetInternalFailure_null() throws Exception {
        ConcreteTestSyncCallback callback = new ConcreteTestSyncCallback();

        assertThrows(NullPointerException.class, () -> callback.setInternalFailure(null));
    }

    @Test
    public void testToString() {
        ConcreteTestSyncCallback callback = new ConcreteTestSyncCallback();

        String toString = callback.toString();

        // Asserts only relevant info that were not already asserted in other tests
        expect.withMessage("toString()").that(toString).contains("epoch=");
        expect.withMessage("toString()").that(toString).contains("internalFailure=null");
    }

    private static final class ConcreteTestSyncCallback extends AbstractTestSyncCallback {
        ConcreteTestSyncCallback() {
            this(new SyncCallbackSettings.Builder().build());
        }

        ConcreteTestSyncCallback(SyncCallbackSettings settings) {
            super(settings);
        }
    }
}
