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
package com.android.adservices.shared.testing.device;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.meta_testing.FakeDeviceGateway;
import com.android.adservices.shared.meta_testing.SharedSidelessTestCase;
import com.android.adservices.shared.testing.AndroidSdk.Level;

import org.junit.Test;

public final class AbstractDeviceGatewayTest extends SharedSidelessTestCase {

    private final FakeDeviceGateway mFakeGateway = new FakeDeviceGateway();
    private final ConcreteDeviceGateway mDeviceGateway = new ConcreteDeviceGateway(mFakeGateway);

    @Test
    @SuppressWarnings("FormatStringAnnotation")
    public void testRunShellCommand_nullArgs() {
        assertThrows(NullPointerException.class, () -> mDeviceGateway.runShellCommand(null));
    }

    @Test
    public void testRunShellCommand_realRunShellCommandReturned_null() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> mDeviceGateway.runShellCommand("whatever"));
    }

    @Test
    public void testRunShellCommand_withFormatting_noErr() {
        mFakeGateway.onCommand(new ShellCommandInput("I am"), new ShellCommandOutput("Groot!"));

        String result = mDeviceGateway.runShellCommand("%s %s", "I", "am");

        expect.withMessage("result").that(result).isEqualTo("Groot!");
    }

    @Test
    public void testRunShellCommand_noFormatting_noErr() {
        mFakeGateway.onCommand(new ShellCommandInput("I am"), new ShellCommandOutput("Groot!"));

        String result = mDeviceGateway.runShellCommand("I am");

        expect.withMessage("result").that(result).isEqualTo("Groot!");
    }

    @Test
    public void testRunShellCommand_withFormatting_errOnly() {
        ShellCommandInput input = new ShellCommandInput("annoyed grunt");
        ShellCommandOutput output = new ShellCommandOutput.Builder().setErr("D'OH!").build();
        mFakeGateway.onCommand(input, output);

        var thrown =
                assertThrows(
                        InvalidShellCommandResultException.class,
                        () -> mDeviceGateway.runShellCommand("%s %s", "annoyed", "grunt"));

        expect.withMessage("input on exception").that(thrown.getInput()).isEqualTo(input);
        expect.withMessage("output on exception").that(thrown.getOutput()).isSameInstanceAs(output);
    }

    @Test
    public void testRunShellCommand_noFormatting_errOnly() {
        ShellCommandInput input = new ShellCommandInput("annoyed grunt");
        ShellCommandOutput output = new ShellCommandOutput.Builder().setErr("D'OH!").build();
        mFakeGateway.onCommand(input, output);

        var thrown =
                assertThrows(
                        InvalidShellCommandResultException.class,
                        () -> mDeviceGateway.runShellCommand("annoyed grunt"));

        expect.withMessage("input on exception").that(thrown.getInput()).isEqualTo(input);
        expect.withMessage("output on exception").that(thrown.getOutput()).isSameInstanceAs(output);
    }

    @Test
    public void testRunShellCommand_withFormatting_outAndErr() {
        ShellCommandInput input = new ShellCommandInput("annoyed grunt");
        ShellCommandOutput output =
                new ShellCommandOutput.Builder().setOut("Homer says").setErr("D'OH!").build();
        mFakeGateway.onCommand(input, output);

        var thrown =
                assertThrows(
                        InvalidShellCommandResultException.class,
                        () -> mDeviceGateway.runShellCommand("%s %s", "annoyed", "grunt"));

        expect.withMessage("input on exception").that(thrown.getInput()).isEqualTo(input);
        expect.withMessage("output on exception").that(thrown.getOutput()).isSameInstanceAs(output);
    }

    @Test
    public void testRunShellCommand_noFormatting_outAndErr() {
        ShellCommandInput input = new ShellCommandInput("annoyed grunt");
        ShellCommandOutput output =
                new ShellCommandOutput.Builder().setOut("Homer says").setErr("D'OH!").build();
        mFakeGateway.onCommand(input, output);

        var thrown =
                assertThrows(
                        InvalidShellCommandResultException.class,
                        () -> mDeviceGateway.runShellCommand("annoyed grunt"));

        expect.withMessage("input on exception").that(thrown.getInput()).isEqualTo(input);
        expect.withMessage("output on exception").that(thrown.getOutput()).isSameInstanceAs(output);
    }

    private static final class ConcreteDeviceGateway extends AbstractDeviceGateway {
        private final FakeDeviceGateway mFakeGateway;

        ConcreteDeviceGateway(FakeDeviceGateway fakeGateway) {
            mFakeGateway = fakeGateway;
        }

        @Override
        public ShellCommandOutput runShellCommandRwe(ShellCommandInput input) {
            return mFakeGateway.runShellCommandRwe(input);
        }

        @Override
        public Level getSdkLevel() {
            throw new UnsupportedOperationException("Not used in these tests");
        }
    }
}
