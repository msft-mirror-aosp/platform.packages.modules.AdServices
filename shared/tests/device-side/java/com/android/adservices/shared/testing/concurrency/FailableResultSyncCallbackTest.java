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

import static com.android.adservices.shared.testing.ConcurrencyHelper.runAsync;
import static com.android.adservices.shared.testing.concurrency.AbstractSyncCallback.LOG_TAG;
import static com.android.adservices.shared.testing.concurrency.FailableResultSyncCallback.INJECT_RESULT_OR_FAILURE;
import static com.android.adservices.shared.testing.concurrency.FailableResultSyncCallback.MSG_WRONG_ERROR_RECEIVED;

import static org.junit.Assert.assertThrows;

import android.util.Log;

import com.android.adservices.mockito.LogInterceptor;
import com.android.adservices.shared.testing.LogEntry.Level;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Test;

public final class FailableResultSyncCallbackTest
        extends ResultTestSyncCallbackTestCase<
                Object, FailableResultSyncCallback<Object, RuntimeException>> {

    private final UnsupportedOperationException mFailure =
            new UnsupportedOperationException("D'OH!");
    private final UnsupportedOperationException mAnotherFailure =
            new UnsupportedOperationException("No can do");

    @Override
    protected Object newResult() {
        return new Object();
    }

    @Override
    protected FailableResultSyncCallback<Object, RuntimeException> newCallback(
            SyncCallbackSettings settings) {
        return new FailableResultSyncCallback<Object, RuntimeException>(settings);
    }

    @Test
    public void testInjectResult_checkFailure() throws Exception {
        expect.withMessage("%s.getFailure() before injectResult()", mCallback)
                .that(mCallback.getFailure())
                .isNull();
        String toStringBefore = mCallback.toString();
        expect.withMessage("toString() before injectResult()")
                .that(toStringBefore)
                .contains("(no result yet)");
        expect.withMessage("toString() before injectResult()")
                .that(toStringBefore)
                .contains("(no failure yet)");

        runAsync(INJECTION_TIMEOUT_MS, () -> mCallback.injectResult(null));
        mCallback.assertCalled();

        expect.withMessage("%s.getFailure() after injectResult()", mCallback)
                .that(mCallback.getFailure())
                .isNull();
        String toStringAfter = mCallback.toString();
        expect.withMessage("toString() after injectResult()")
                .that(toStringAfter)
                .doesNotContain("(no result yet)");
        expect.withMessage("toString() after injectResult()")
                .that(toStringAfter)
                .doesNotContain("(no failure yet)");
        expect.withMessage("toString() after injectResult()")
                .that(toStringAfter)
                .contains("result=null");
    }

    @Test
    public void testInjectFailure_null() {
        assertThrows(NullPointerException.class, () -> mCallback.injectFailure(null));
    }

    @Test
    public void testInjectFailure() throws Exception {
        expect.withMessage("%s.getFailure() before injectFailure()", mCallback)
                .that(mCallback.getFailure())
                .isNull();
        expect.withMessage("%s.getResult() before injectFailure()", mCallback)
                .that(mCallback.getResult())
                .isNull();
        expect.withMessage("%s.isCalled() before injectFailure()", mCallback)
                .that(mCallback.isCalled())
                .isFalse();

        runAsync(INJECTION_TIMEOUT_MS, () -> mCallback.injectFailure(mFailure));

        RuntimeException failure = mCallback.assertFailureReceived();

        expect.withMessage("%s.assertFailureReceived()", mCallback)
                .that(failure)
                .isSameInstanceAs(mFailure);

        expect.withMessage("%s.getFailure() after assertFailureReceived()", mCallback)
                .that(mCallback.getFailure())
                .isSameInstanceAs(mFailure);
        expect.withMessage("%s.getResult() after assertFailureReceived()", mCallback)
                .that(mCallback.getResult())
                .isNull();
        expect.withMessage("%s.isCalled() after assertFailureReceived()", mCallback)
                .that(mCallback.isCalled())
                .isTrue();
        String toString = mCallback.toString();
        expect.withMessage("toString() after assertFailureReceived()")
                .that(toString)
                .contains("result=" + mFailure);
        expect.withMessage("toString() after assertFailureReceived()")
                .that(toString)
                .doesNotContain("(no result yet)");
        expect.withMessage("toString() after assertFailureReceived()")
                .that(toString)
                .doesNotContain("(no failure yet)");
    }

    @Test
    public void testInjectFailure_calledTwice() {
        mCallback.injectFailure(mFailure);
        mCallback.injectFailure(mAnotherFailure);

        CallbackAlreadyCalledException thrown =
                assertThrows(
                        CallbackAlreadyCalledException.class,
                        () -> mCallback.assertFailureReceived());

        thrown.assertWith(expect, INJECT_RESULT_OR_FAILURE, mFailure, mAnotherFailure);
    }

    @Test
    public void testInjectFailure_calledAfterInjectResult() {
        mCallback.injectResult(null);
        mCallback.injectFailure(mFailure);

        CallbackAlreadyCalledException thrown =
                assertThrows(CallbackAlreadyCalledException.class, () -> mCallback.assertCalled());

        thrown.assertWith(expect, INJECT_RESULT_OR_FAILURE, null, mFailure);
    }

    @Test
    public void testInjectResult_calledAfterInjectFailure() {
        mCallback.injectFailure(mFailure);
        mCallback.injectResult(null);

        CallbackAlreadyCalledException thrown =
                assertThrows(CallbackAlreadyCalledException.class, () -> mCallback.assertCalled());

        thrown.assertWith(expect, INJECT_RESULT_OR_FAILURE, mFailure, null);
    }

    @Test
    public void testAssertFailureReceived_null() throws Exception {
        assertThrows(NullPointerException.class, () -> mCallback.assertFailureReceived(null));
    }

    @Test
    public void testAssertFailureReceived_rightClass() throws Exception {
        mCallback.injectFailure(mFailure);

        UnsupportedOperationException failure =
                mCallback.assertFailureReceived(UnsupportedOperationException.class);

        expect.withMessage("%s.assertFailureReceived(...)", mCallback)
                .that(failure)
                .isSameInstanceAs(mFailure);
    }

    @Test
    public void testAssertFailureReceived_wrongClass() throws Exception {
        mCallback.injectFailure(mFailure);

        IllegalStateException thrown =
                assertThrows(
                        IllegalStateException.class,
                        () -> mCallback.assertFailureReceived(ArithmeticException.class));

        expect.withMessage("thrown")
                .that(thrown)
                .hasMessageThat()
                .isEqualTo(
                        String.format(
                                MSG_WRONG_ERROR_RECEIVED, ArithmeticException.class, mFailure));
    }

    @SpyStatic(Log.class)
    @Test
    public void testLogE() {
        String tag = LOG_TAG;
        LogInterceptor logInterceptor = mocker.interceptLogE(tag);

        mCallback.logE("Answer=%d", 42);

        expect.withMessage("Log.*() calls to tag %s", tag)
                .that(logInterceptor.getAllEntries(tag))
                .hasSize(1);
        expect.withMessage("Log.e() calls to tag %s", tag)
                .that(logInterceptor.getPlainMessages(tag, Level.ERROR))
                .containsExactly(mCallback + ": Answer=42");
    }

    @SpyStatic(Log.class)
    @Test
    public void testLogD() {
        String tag = LOG_TAG;
        LogInterceptor logInterceptor = mocker.interceptLogD(tag);

        mCallback.logD("Answer=%d", 42);

        expect.withMessage("Log.*() calls to tag %s", tag)
                .that(logInterceptor.getAllEntries(tag))
                .hasSize(1);
        expect.withMessage("Log.d() calls to tag %s", tag)
                .that(logInterceptor.getPlainMessages(tag, Level.DEBUG))
                .containsExactly(
                        "[FailableResultSyncCallback#" + mCallback.getId() + "]: Answer=42");
    }

    @SpyStatic(Log.class)
    @Test
    public void testLogV() {
        String tag = LOG_TAG;
        LogInterceptor logInterceptor = mocker.interceptLogV(tag);

        mCallback.logV("Answer=%d", 42);

        expect.withMessage("Log.*() calls to tag %s", tag)
                .that(logInterceptor.getAllEntries(tag))
                .hasSize(1);
        expect.withMessage("Log.v() calls to tag %s", tag)
                .that(logInterceptor.getPlainMessages(tag, Level.VERBOSE))
                .containsExactly(mCallback + ": Answer=42");
    }

    @Test
    public void testToString() {
        String toString = mCallback.toString();

        expect.withMessage("toString()").that(toString).startsWith("[FailableResultSyncCallback: ");
        expect.withMessage("toString()").that(toString).contains(mCallback.getId());
        expect.withMessage("toString()").that(toString).endsWith("]");
    }
}
