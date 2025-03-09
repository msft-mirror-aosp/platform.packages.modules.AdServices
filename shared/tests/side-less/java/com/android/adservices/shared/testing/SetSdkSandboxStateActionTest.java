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

import static com.android.adservices.shared.testing.SdkSandbox.State.DISABLED;
import static com.android.adservices.shared.testing.SdkSandbox.State.ENABLED;
import static com.android.adservices.shared.testing.SdkSandbox.State.UNKNOWN;
import static com.android.adservices.shared.testing.SdkSandbox.State.UNSUPPORTED;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.meta_testing.FakeSdkSandbox;
import com.android.adservices.shared.meta_testing.SharedSidelessTestCase;
import com.android.adservices.shared.testing.SdkSandbox.State;

import org.junit.Test;

public final class SetSdkSandboxStateActionTest extends SharedSidelessTestCase {

    private final FakeSdkSandbox mFakeSdkSandbox = new FakeSdkSandbox();

    @Test
    public void testConstructor_null() {
        assertThrows(
                NullPointerException.class,
                () -> new SetSdkSandboxStateAction(mFakeLogger, /* sandbox= */ null, ENABLED));
        assertThrows(
                NullPointerException.class,
                () -> new SetSdkSandboxStateAction(/* logger= */ null, mFakeSdkSandbox, ENABLED));
        assertThrows(
                NullPointerException.class,
                () ->
                        new SetSdkSandboxStateAction(
                                mFakeLogger, mFakeSdkSandbox, /* state= */ null));
    }

    @Test
    public void testConstructor_notSettable() {
        for (State state : State.values()) {
            if (!state.isSettable()) {
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new SetSdkSandboxStateAction(mFakeLogger, mFakeSdkSandbox, state));
            }
        }
    }

    @Test
    public void testGetState() {
        for (State state : State.values()) {
            if (state.isSettable()) {
                var action = new SetSdkSandboxStateAction(mFakeLogger, mFakeSdkSandbox, state);
                expect.withMessage("getState()").that(action.getState()).isEqualTo(state);
            }
        }
    }

    @Test
    public void testExecuteAndRevert_getPreviousFail() throws Exception {
        mFakeSdkSandbox.onGetStateThrows(new RuntimeException("D'OH!"));

        var action = new SetSdkSandboxStateAction(mFakeLogger, mFakeSdkSandbox, ENABLED);

        boolean result = action.execute();

        expect.withMessage("execute()").that(result).isFalse();

        mFakeSdkSandbox.onSetStateThrows(new RuntimeException("Y U CALLED ME!"));
        action.revert();
    }

    @Test
    public void testExecuteAndRevert_notChanged() throws Exception {
        mFakeSdkSandbox.setState(ENABLED);
        var action = new SetSdkSandboxStateAction(mFakeLogger, mFakeSdkSandbox, ENABLED);

        boolean result = action.execute();

        expect.withMessage("execute()").that(result).isFalse();
    }

    @Test
    public void testExecuteAndRevert_previousReturnNull() throws Exception {
        mFakeSdkSandbox.setState(null);
        var action = new SetSdkSandboxStateAction(mFakeLogger, mFakeSdkSandbox, ENABLED);

        boolean result = action.execute();

        expect.withMessage("execute()").that(result).isFalse();
        expect.withMessage("state after execute()")
                .that(mFakeSdkSandbox.getState())
                .isEqualTo(ENABLED);

        action.revert();
        expect.withMessage("state after revert()")
                .that(mFakeSdkSandbox.getState())
                .isEqualTo(ENABLED);
    }

    @Test
    public void testExecuteAndRevert_previousReturnUnknown() throws Exception {
        mFakeSdkSandbox.setState(UNKNOWN);
        var action = new SetSdkSandboxStateAction(mFakeLogger, mFakeSdkSandbox, ENABLED);

        boolean result = action.execute();

        expect.withMessage("execute()").that(result).isFalse();
        expect.withMessage("state after execute()")
                .that(mFakeSdkSandbox.getState())
                .isEqualTo(ENABLED);

        action.revert();
        expect.withMessage("state after revert()")
                .that(mFakeSdkSandbox.getState())
                .isEqualTo(ENABLED);
    }

    @Test
    public void testExecuteAndRevert_previousReturnUnsupported() throws Exception {
        mFakeSdkSandbox.setState(UNSUPPORTED);
        var action = new SetSdkSandboxStateAction(mFakeLogger, mFakeSdkSandbox, ENABLED);

        boolean result = action.execute();

        expect.withMessage("execute()").that(result).isFalse();
        expect.withMessage("state after execute()")
                .that(mFakeSdkSandbox.getState())
                .isEqualTo(UNSUPPORTED);

        action.revert();
        expect.withMessage("state after revert()")
                .that(mFakeSdkSandbox.getState())
                .isEqualTo(UNSUPPORTED);
    }

    @Test
    public void testExecuteAndRevert_changed() throws Exception {
        mFakeSdkSandbox.setState(DISABLED);
        var action = new SetSdkSandboxStateAction(mFakeLogger, mFakeSdkSandbox, ENABLED);

        boolean result = action.execute();

        expect.withMessage("execute()").that(result).isTrue();
        expect.withMessage("state after execute()")
                .that(mFakeSdkSandbox.getState())
                .isEqualTo(ENABLED);

        action.revert();

        expect.withMessage("state after revert()")
                .that(mFakeSdkSandbox.getState())
                .isEqualTo(DISABLED);
    }

    @Test
    public void testOnRevertWhenNotExecuted() throws Exception {
        // This is kind of an "overkill" test, as onRevert() should not be called directly, but it
        // doesn't hurt to be sure...
        mFakeSdkSandbox.setState(ENABLED);
        var action = new SetSdkSandboxStateAction(mFakeLogger, mFakeSdkSandbox, ENABLED);

        action.execute();

        assertThrows(IllegalStateException.class, () -> action.onRevertLocked());
    }

    @Test
    public void testOnReset() throws Exception {
        mFakeSdkSandbox.setState(DISABLED);
        var action = new SetSdkSandboxStateAction(mFakeLogger, mFakeSdkSandbox, ENABLED);
        expect.withMessage("previous mode initially ").that(action.getPreviousState()).isNull();
        action.execute();
        expect.withMessage("previous mode after execute")
                .that(action.getPreviousState())
                .isEqualTo(DISABLED);

        action.onResetLocked();

        expect.withMessage("previous mode before reset").that(action.getPreviousState()).isNull();
    }

    @Test
    public void testEqualsAndHashCode() {
        var baseline = new SetSdkSandboxStateAction(mFakeLogger, mFakeSdkSandbox, ENABLED);
        var different = new SetSdkSandboxStateAction(mFakeLogger, mFakeSdkSandbox, ENABLED);
        var et = new EqualsTester(expect);

        et.expectObjectsAreNotEqual(baseline, different);
    }

    @Test
    public void testToString() throws Exception {
        var action = new SetSdkSandboxStateAction(mFakeLogger, mFakeSdkSandbox, ENABLED);

        expect.withMessage("toString() before execute()")
                .that(action.toString())
                .isEqualTo("SetSdkSandboxStateAction[state=ENABLED, previousState=null]");

        action.execute();

        expect.withMessage("toString() after execute()")
                .that(action.toString())
                .isEqualTo("SetSdkSandboxStateAction[state=ENABLED, previousState=UNKNOWN]");
    }
}
