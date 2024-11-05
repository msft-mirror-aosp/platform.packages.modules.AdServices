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
package com.android.adservices.shared.testing;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.meta_testing.FakeAction;
import com.android.adservices.shared.meta_testing.SharedSidelessTestCase;
import com.android.adservices.shared.testing.Logger.LogLevel;

import com.google.common.collect.ImmutableList;

import org.junit.Test;

public final class SafeActionTest extends SharedSidelessTestCase {

    private final FakeAction mFakeAction = new FakeAction();
    private final Exception mException = new Exception("D'OH!");

    private final SafeAction mSafeAction =
            new SafeAction(new Logger(mFakeRealLogger, SafeActionTest.class), mFakeAction);

    @Test
    public void testConstructor_null() {
        assertThrows(NullPointerException.class, () -> new SafeAction(mLog, /* action= */ null));
        assertThrows(
                NullPointerException.class, () -> new SafeAction(/* logger= */ null, mFakeAction));
    }

    @Test
    public void testExecute_returnsTrue() {
        mFakeAction.onExecuteReturn(true);

        expect.withMessage("%s.execute()", mSafeAction).that(mSafeAction.execute()).isTrue();

        expect.withMessage("isExecuted()").that(mSafeAction.isExecuted()).isTrue();
        expect.withMessage("FakeAcion.execute() called").that(mFakeAction.isExecuted()).isTrue();
    }

    @Test
    public void testExecute_returnsFalse() {
        mFakeAction.onExecuteReturn(false);

        expect.withMessage("%s.execute()", mSafeAction).that(mSafeAction.execute()).isFalse();

        expect.withMessage("isExecuted()").that(mSafeAction.isExecuted()).isTrue();
        expect.withMessage("FakeAcion.execute() called").that(mFakeAction.isExecuted()).isTrue();
    }

    @Test
    public void testExecute_throws() {
        mFakeAction.onExecuteThrows(mException);

        expect.withMessage("%s.execute()", mSafeAction).that(mSafeAction.execute()).isFalse();

        expect.withMessage("isExecuted()").that(mSafeAction.isExecuted()).isTrue();
        expect.withMessage("FakeAcion.execute() called").that(mFakeAction.isExecuted()).isTrue();
        expectErrorLogged("execute");
    }

    @Test
    public void testRevert() {
        mSafeAction.revert();

        expect.withMessage("isReverted()").that(mSafeAction.isReverted()).isTrue();
        expect.withMessage("FakeAcion.revert() called").that(mFakeAction.isReverted()).isTrue();
    }

    @Test
    public void testRevert_throws() {
        mFakeAction.onRevertThrows(mException);

        mSafeAction.revert();

        expect.withMessage("isReverted()").that(mSafeAction.isReverted()).isTrue();
        expect.withMessage("FakeAcion.revert() called").that(mFakeAction.isReverted()).isTrue();
        expectErrorLogged("revert");
    }

    @Test
    public void testReset() {
        mSafeAction.execute();
        mSafeAction.revert();
        mSafeAction.reset();

        expect.withMessage("isExecuted()").that(mSafeAction.isExecuted()).isFalse();
        expect.withMessage("isReverted()").that(mSafeAction.isReverted()).isFalse();
    }

    @Test
    public void testReset_throws() {
        RuntimeException exception = new RuntimeException("D'OH!");
        mFakeAction.onResetThrows(exception);
        mSafeAction.execute();
        mSafeAction.revert();

        mSafeAction.reset();

        expect.withMessage("isExecuted()").that(mSafeAction.isExecuted()).isTrue();
        expect.withMessage("isReverted()").that(mSafeAction.isReverted()).isTrue();
    }

    @Test
    public void test_toString() {
        CustomStringAction action = new CustomStringAction();

        action.mString = "Action[Jackson]";
        expect.withMessage("toString()")
                .that(new SafeAction(mLog, action).toString())
                .isEqualTo("SafeAction[Jackson]");

        action.mString = "Action Jackson";
        expect.withMessage("toString()")
                .that(new SafeAction(mLog, action).toString())
                .isEqualTo("SafeAction[Action Jackson]");
    }

    private void expectErrorLogged(String method) {
        ImmutableList<LogEntry> logEntries = mFakeRealLogger.getEntries();
        assertWithMessage("log entries").that(logEntries).hasSize(1);
        LogEntry logEntry = logEntries.get(0);
        expect.withMessage("log entry").that(logEntry.level).isEqualTo(LogLevel.ERROR);
        expect.withMessage("log entry")
                .that(logEntry.message)
                .isEqualTo("Failed to " + method + " action " + mFakeAction);
        expect.withMessage("log entry").that(logEntry.throwable).isSameInstanceAs(mException);
    }

    private static final class CustomStringAction implements Action {

        private String mString;

        @Override
        public boolean execute() throws Exception {
            throw new UnsupportedOperationException("shouldn't be called");
        }

        @Override
        public boolean isExecuted() {
            throw new UnsupportedOperationException("shouldn't be called");
        }

        @Override
        public void revert() throws Exception {
            throw new UnsupportedOperationException("shouldn't be called");
        }

        @Override
        public boolean isReverted() {
            throw new UnsupportedOperationException("shouldn't be called");
        }

        @Override
        public void reset() {
            throw new UnsupportedOperationException("shouldn't be called");
        }

        @Override
        public String toString() {
            return mString;
        }
    }
}
