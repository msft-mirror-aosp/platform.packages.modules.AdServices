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

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.meta_testing.SharedSidelessTestCase;

import org.junit.Test;

public final class AbstractActionTest extends SharedSidelessTestCase {

    @Test
    public void testConstructor_null() {
        assertThrows(NullPointerException.class, () -> new ConcreteAction(/* logger= */ null));
    }

    @Test
    public void testNormalOverflow() throws Exception {
        ConcreteAction action = new ConcreteAction(mLog);
        expect.withMessage("isExecuted() initially").that(action.isExecuted()).isFalse();
        expect.withMessage("isReverted() initially").that(action.isReverted()).isFalse();

        boolean executeResult = action.execute();
        expect.withMessage("result of execute()").that(executeResult).isTrue();
        expect.withMessage("isExecuted() after execute()").that(action.isExecuted()).isTrue();
        expect.withMessage("isReverted() after execute()").that(action.isReverted()).isFalse();

        action.revert();
        expect.withMessage("isExecuted() after revert()").that(action.isExecuted()).isTrue();
        expect.withMessage("isReverted() after revert()").that(action.isReverted()).isTrue();

        action.reset();
        expect.withMessage("isExecuted() after reset()").that(action.isExecuted()).isFalse();
        expect.withMessage("isReverted() after reset()").that(action.isReverted()).isFalse();

        boolean executeResultAfterReset = action.execute();
        expect.withMessage("result of execute()").that(executeResultAfterReset).isTrue();
        expect.withMessage("isExecuted() after reset+execute").that(action.isExecuted()).isTrue();
        expect.withMessage("isReverted() after reset+execute").that(action.isReverted()).isFalse();

        action.revert();
        expect.withMessage("isExecuted() after reset+revert").that(action.isExecuted()).isTrue();
        expect.withMessage("isReverted() after reset+revert").that(action.isReverted()).isTrue();
    }

    @Test
    public void testExecuteTwice_fails() throws Exception {
        ConcreteAction action = new ConcreteAction(mLog);
        action.execute();

        var thrown = assertThrows(IllegalStateException.class, () -> action.execute());

        expect.withMessage("exception message")
                .that(thrown)
                .hasMessageThat()
                .contains(action.toString());
        expect.withMessage("number of onExecute() calls")
                .that(action.numberOnExecuteCalls)
                .isEqualTo(1);
    }

    @Test
    public void testRevertTwice_fails() throws Exception {
        ConcreteAction action = new ConcreteAction(mLog);

        action.execute();

        action.revert();
        var thrown = assertThrows(IllegalStateException.class, () -> action.revert());

        expect.withMessage("exception message")
                .that(thrown)
                .hasMessageThat()
                .contains(action.toString());
        expect.withMessage("number of onRevert() calls")
                .that(action.numberOnRevertCalls)
                .isEqualTo(1);
    }

    @Test
    public void testRevertBeforeExecute_fails() {
        ConcreteAction action = new ConcreteAction(mLog);
        assertThrows(IllegalStateException.class, () -> action.revert());
    }

    @Test
    public void testResetBeforeExecute_ok() throws Exception {
        ConcreteAction action = new ConcreteAction(mLog);

        action.reset();
    }

    @Test
    public void testResetBeforeRevert_fails() throws Exception {
        ConcreteAction action = new ConcreteAction(mLog);

        action.execute();

        assertThrows(IllegalStateException.class, () -> action.reset());
    }

    @Test
    public void testResetWhenExecutedReturnedFalse() throws Exception {
        ConcreteAction action = new ConcreteAction(mLog);

        action.execute();

        assertThrows(IllegalStateException.class, () -> action.reset());
    }

    @Test
    public void testOnRevertNotCalledWhenExecutedReturnedFalse() throws Exception {
        ConcreteAction action = new ConcreteAction(mLog);
        action.onExecuteResult = false;
        action.execute();

        action.reset();

        expect.withMessage("number of onExecute() calls")
                .that(action.numberOnExecuteCalls)
                .isEqualTo(0);
    }

    private static final class ConcreteAction extends AbstractAction {
        public boolean onExecuteResult = true;
        public int numberOnExecuteCalls;
        public int numberOnRevertCalls;

        private ConcreteAction(Logger logger) {
            super(logger);
        }

        @Override
        protected boolean onExecuteLocked() {
            numberOnExecuteCalls++;
            return onExecuteResult;
        }

        @Override
        protected void onRevertLocked() {
            numberOnRevertCalls++;
        }

        @Override
        protected void onResetLocked() {
            onExecuteResult = true;
            numberOnExecuteCalls = 0;
            numberOnRevertCalls = 0;
        }

        @Override
        public String toString() {
            return "ConcreteAction [onExecuteResult="
                    + onExecuteResult
                    + ", numberOnExecuteCalls="
                    + numberOnExecuteCalls
                    + ", numberOnRevertCalls="
                    + numberOnRevertCalls
                    + "]";
        }
    }
}
