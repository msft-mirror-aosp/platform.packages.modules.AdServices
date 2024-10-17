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
    public void testExecuteTwice() throws Exception {
        ConcreteAction action = new ConcreteAction(mLog);

        boolean result = action.execute();
        expect.withMessage("first call to execute()").that(result).isTrue();
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
    public void testRevertTwice() throws Exception {
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
    public void testRevertBeforeExecute() {
        ConcreteAction action = new ConcreteAction(mLog);
        assertThrows(IllegalStateException.class, () -> action.revert());
    }

    @Test
    public void testOnRevertNotCalledWhenExecutedReturnedFalse() throws Exception {
        ConcreteAction action = new ConcreteAction(mLog);
        action.onExecuteResult = false;
        action.execute();

        action.revert();

        expect.withMessage("number of onRevert() calls")
                .that(action.numberOnRevertCalls)
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
        protected boolean onExecute() {
            numberOnExecuteCalls++;
            return onExecuteResult;
        }

        @Override
        protected void onRevert() {
            numberOnRevertCalls++;
        }
    }
}
