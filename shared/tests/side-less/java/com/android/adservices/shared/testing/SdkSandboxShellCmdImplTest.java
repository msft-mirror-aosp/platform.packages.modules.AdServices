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

import static com.android.adservices.shared.testing.AndroidSdk.Level.S;
import static com.android.adservices.shared.testing.AndroidSdk.Level.T;
import static com.android.adservices.shared.testing.SdkSandbox.State;
import static com.android.adservices.shared.testing.SdkSandbox.State.DISABLED;
import static com.android.adservices.shared.testing.SdkSandbox.State.ENABLED;
import static com.android.adservices.shared.testing.SdkSandbox.State.UNKNOWN;
import static com.android.adservices.shared.testing.SdkSandbox.State.UNSUPPORTED;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.meta_testing.FakeDeviceGateway;
import com.android.adservices.shared.meta_testing.SharedSidelessTestCase;
import com.android.adservices.shared.testing.SdkSandbox.State;
import com.android.adservices.shared.testing.device.InvalidShellCommandResultException;

import org.junit.Test;

public final class SdkSandboxShellCmdImplTest extends SharedSidelessTestCase {

    private final FakeDeviceGateway mFakeGateway = new FakeDeviceGateway().setSdkLevel(T);

    private final SdkSandboxShellCmdImpl mImpl =
            new SdkSandboxShellCmdImpl(mFakeRealLogger, mFakeGateway);

    @Test
    public void testNullConstructor() {
        assertThrows(
                NullPointerException.class,
                () -> new SdkSandboxShellCmdImpl(mFakeRealLogger, /* gateway= */ null));
        assertThrows(
                NullPointerException.class,
                () -> new SdkSandboxShellCmdImpl(/* realLogger= */ null, mFakeGateway));
    }

    @Test
    public void testGetState_TMinus() {
        mFakeGateway.setSdkLevel(S);

        expect.that(mImpl.getState()).isEqualTo(UNSUPPORTED);
    }

    @Test
    public void testGetState_lineNotFound() {
        mockDumpsysSdkSandbox("whatever you're expecting, it's not here!");

        expect.that(mImpl.getState()).isEqualTo(UNKNOWN);
    }

    @Test
    public void testGetState_emptyDumpsys() {
        mockDumpsysSdkSandbox("");

        expect.that(mImpl.getState()).isEqualTo(UNKNOWN);
    }

    @Test
    public void testGetState_enabled() {
        mockDumpsysSdkSandbox("\nWhatever\nKillswitch enabled: false");

        expect.that(mImpl.getState()).isEqualTo(ENABLED);
    }

    @Test
    public void testGetState_disabled() {
        mockDumpsysSdkSandbox("\nWhatever\nKillswitch enabled: true");

        expect.that(mImpl.getState()).isEqualTo(DISABLED);
    }

    @Test
    public void testSetState_null() {
        assertThrows(NullPointerException.class, () -> mImpl.setState(null));
    }

    @Test
    public void testSetState_TMinus() {
        mFakeGateway.setSdkLevel(S);

        for (State state : State.values()) {
            if (state.isSettable()) {
                var self = mImpl.setState(state);
                expect.withMessage("result of setState()").that(self).isSameInstanceAs(mImpl);
            } else {
                assertThrows(IllegalArgumentException.class, () -> mImpl.setState(state));
            }
            expect.withMessage("getState()").that(mImpl.getState()).isEqualTo(UNSUPPORTED);
        }
    }

    @Test
    public void testSetState_notSettable() {
        for (State state : State.values()) {
            if (!state.isSettable()) {
                assertThrows(IllegalArgumentException.class, () -> mImpl.setState(state));
            }
        }
    }

    @Test
    public void testSetState_badCmdOutput() {
        mockCmdSdkSandbox("set-state --enabled", "D'OH!");

        var thrown =
                assertThrows(
                        InvalidShellCommandResultException.class, () -> mImpl.setState(ENABLED));

        expect.withMessage("exception input")
                .that(thrown.getInput().toString())
                .isEqualTo("cmd sdk_sandbox set-state --enabled");
        expect.withMessage("exception output")
                .that(thrown.getOutput().toString())
                .isEqualTo("D'OH!");
    }

    @Test
    public void testSetState_enabled() {
        mockCmdSdkSandbox("set-state --enabled", "");

        var self = mImpl.setState(ENABLED);

        expect.withMessage("result of setState()").that(self).isSameInstanceAs(mImpl);
    }

    @Test
    public void testSetState_disabled() {
        mockCmdSdkSandbox("set-state --reset", "");

        var self = mImpl.setState(DISABLED);

        expect.withMessage("result of setState()").that(self).isSameInstanceAs(mImpl);
    }

    private void mockDumpsysSdkSandbox(String dump) {
        mFakeGateway.onCommand(dump, "dumpsys sdk_sandbox");
    }

    private void mockCmdSdkSandbox(String input, String output) {
        mFakeGateway.onCommand(output, "cmd sdk_sandbox %s", input);
    }
}
