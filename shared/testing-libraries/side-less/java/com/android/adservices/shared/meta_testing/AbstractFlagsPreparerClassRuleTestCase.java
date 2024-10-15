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
package com.android.adservices.shared.meta_testing;

import static com.android.adservices.shared.testing.SdkSandbox.State.DISABLED;
import static com.android.adservices.shared.testing.SdkSandbox.State.ENABLED;
import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.NONE;
import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.PERSISTENT;
import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.UNSUPPORTED;
import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.UNTIL_REBOOT;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.testing.ActionExecutionException;
import com.android.adservices.shared.testing.SdkSandbox;
import com.android.adservices.shared.testing.device.DeviceConfig;
import com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest;
import com.android.adservices.shared.testing.flags.AbstractFlagsPreparerClassRule;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Base class for {@link AbstractFlagsPreparerClassRule} implementations.
 *
 * <p>It contains the base tests for all of them, although subclasses my add they extra tests.
 *
 * @param <R> type of the rule
 */
@SuppressWarnings("VisibleForTests") // TODO(b/343741206): Remove suppress warning once fixed.
public abstract class AbstractFlagsPreparerClassRuleTestCase<
                R extends AbstractFlagsPreparerClassRule<R>>
        extends SharedSidelessTestCase {

    protected final SimpleStatement mTestBody = new SimpleStatement();

    // Need a suite with a test to emulate a class
    protected final Description mTest =
            Description.createTestDescription(AClassHasNoNothingAtAll.class, "butItHasATest");
    protected final Description mSuite =
            Description.createSuiteDescription(AClassHasNoNothingAtAll.class);

    private FakeDeviceConfig mFakeDeviceConfig = new FakeDeviceConfig();
    private FakeSdkSandbox mFakeSdkSandbox = new FakeSdkSandbox();

    @Before
    public final void setTestFixtures() {
        mSuite.addChild(mTest);
        mFakeDeviceConfig.setSyncDisabledMode(NONE);
        mFakeSdkSandbox.setState(DISABLED);
    }

    /** Gets a new concrete implementation of the rule. */
    protected abstract R newRule(SdkSandbox sdkSandbox, DeviceConfig deviceConfig);

    // NOTE: this method was originally building a rule that set the mode right away, in the
    // constructor. The rule doesn't do it anymore, so some of these tests are redundant (as there
    // are tests specifics for calling setSyncDisabledModeForTest()), but they're kept to make sure
    // the refactoring didn't break anything...
    private R newRule(DeviceConfig deviceConfig, SyncDisabledModeForTest syncMode) {
        return newRule(deviceConfig).setSyncDisabledModeForTest(syncMode);
    }

    private R newRule(DeviceConfig deviceConfig) {
        return newRule(mFakeSdkSandbox, deviceConfig);
    }

    @Test
    public final void testConstructor_nullArgs() {
        assertThrows(
                NullPointerException.class,
                () -> newRule(mFakeSdkSandbox, /* deviceConfig= */ null));
        assertThrows(
                NullPointerException.class,
                () -> newRule(/* sdkSandbox= */ null, mFakeDeviceConfig));
    }

    @Test
    public final void testThrowsIfNotUsedAsClassRule() throws Exception {
        var rule = newRule(mFakeDeviceConfig);

        assertThrows(IllegalStateException.class, () -> runRule(rule, mTest));
    }

    @Test
    public final void testWhenGetModeReturnsUnsupported() throws Throwable {
        var realException = new IllegalStateException("Supported I'm Not!");
        mFakeDeviceConfig.setSyncDisabledMode(UNSUPPORTED);
        mFakeDeviceConfig.onSetSyncDisabledModeCallback(failWith(realException));
        var rule = newRule(mFakeDeviceConfig, PERSISTENT);

        runRule(rule);

        mTestBody.assertEvaluated();
    }

    @Test
    public final void testWhenTestPass() throws Throwable {
        AtomicReference<SyncDisabledModeForTest> modeSetDuringTest = new AtomicReference<>();
        mTestBody.onEvaluate(() -> modeSetDuringTest.set(mFakeDeviceConfig.getSyncDisabledMode()));
        var rule = newRule(mFakeDeviceConfig, PERSISTENT);
        runRule(rule);

        expect.withMessage("mode during test").that(modeSetDuringTest.get()).isEqualTo(PERSISTENT);
        expect.withMessage("mode after test")
                .that(mFakeDeviceConfig.getSyncDisabledMode())
                .isEqualTo(NONE);
    }

    @Test
    public final void testWhenTestFail() throws Throwable {
        RuntimeException testFailure = new RuntimeException("TEST, Y U NO PASS?");
        AtomicReference<SyncDisabledModeForTest> modeSetDuringTest = new AtomicReference<>();
        mTestBody.onEvaluate(
                () -> {
                    modeSetDuringTest.set(mFakeDeviceConfig.getSyncDisabledMode());
                    throw testFailure;
                });
        var rule = newRule(mFakeDeviceConfig, PERSISTENT);
        Throwable thrown = assertThrows(Throwable.class, () -> runRule(rule));

        expect.withMessage("thrown exception ").that(thrown).isSameInstanceAs(testFailure);
        expect.withMessage("mode during test").that(modeSetDuringTest.get()).isEqualTo(PERSISTENT);
        expect.withMessage("mode after test")
                .that(mFakeDeviceConfig.getSyncDisabledMode())
                .isEqualTo(NONE);
    }

    @Test
    public final void testWhenGetSyncModeFails() throws Throwable {
        mFakeDeviceConfig.onGetSyncDisabledModeCallback(
                failWith("(You Can't Get No) Satisfaction"));
        AtomicReference<SyncDisabledModeForTest> modeSetDuringTest = new AtomicReference<>();
        mTestBody.onEvaluate(
                () -> {
                    // reset it, otherwise next call would fail
                    mFakeDeviceConfig.onGetSyncDisabledModeCallback(null);
                    modeSetDuringTest.set(mFakeDeviceConfig.getSyncDisabledMode());
                    mFakeDeviceConfig.onSetSyncDisabledModeCallback(
                            failWith("Shouldn't have been called at the end"));
                });

        var rule = newRule(mFakeDeviceConfig, PERSISTENT);
        runRule(rule);

        expect.withMessage("mode during test").that(modeSetDuringTest.get()).isEqualTo(PERSISTENT);
        // Not restored at the end because it didn't get the initial mode
        expect.withMessage("mode after test")
                .that(mFakeDeviceConfig.getSyncDisabledMode())
                .isEqualTo(PERSISTENT);
    }

    @Test
    public final void testWhenSetSyncModeFails() throws Throwable {
        var realException = new IllegalStateException("Ready, Set, Throw!");
        mFakeDeviceConfig.onSetSyncDisabledModeCallback(failWith(realException));
        var rule = newRule(mFakeDeviceConfig, PERSISTENT);

        Throwable thrown = assertThrows(Throwable.class, () -> runRule(rule));

        expect.withMessage("exception").that(thrown).isSameInstanceAs(realException);

        mTestBody.assertNotEvaluated();
        expect.withMessage("mode after test")
                .that(mFakeDeviceConfig.getSyncDisabledMode())
                .isEqualTo(NONE);
    }

    @Test
    public final void testWhenTestPassAndRestoreFails() throws Throwable {
        AtomicReference<SyncDisabledModeForTest> modeSetDuringTest = new AtomicReference<>();
        mTestBody.onEvaluate(
                () -> {
                    modeSetDuringTest.set(mFakeDeviceConfig.getSyncDisabledMode());
                    mFakeDeviceConfig.onSetSyncDisabledModeCallback(failWith("Failed at end"));
                });
        var rule = newRule(mFakeDeviceConfig, PERSISTENT);
        runRule(rule);

        expect.withMessage("mode during test").that(modeSetDuringTest.get()).isEqualTo(PERSISTENT);
        expect.withMessage("mode after test")
                .that(mFakeDeviceConfig.getSyncDisabledMode())
                .isEqualTo(PERSISTENT);
    }

    @Test
    public final void testSyncModeNotSetWhenItsTheSame() throws Throwable {
        AtomicBoolean setCalled = new AtomicBoolean();
        mFakeDeviceConfig.onSetSyncDisabledModeCallback(() -> setCalled.set(true));

        var rule = newRule(mFakeDeviceConfig, NONE);
        runRule(rule);

        if (setCalled.get()) {
            expect.withMessage("Rule shouldn't have called deviceConfig.setSyncDisabledMode()")
                    .fail();
        }
    }

    @Test
    public final void testSetSyncDisabledModeForTest_null() throws Throwable {
        var rule = newRule(mFakeDeviceConfig);

        assertThrows(NullPointerException.class, () -> rule.setSyncDisabledModeForTest(null));
    }

    @Test
    public final void testSetSyncDisabledModeForTest_returnsSelf() throws Throwable {
        var rule = newRule(mFakeDeviceConfig);

        expect.withMessage("return of setSyncDisabledModeForTest()")
                .that(rule.setSyncDisabledModeForTest(UNTIL_REBOOT))
                .isSameInstanceAs(rule);
    }

    @Test
    public final void testSetSyncDisabledModeForTest_beforeTest() throws Throwable {
        AtomicReference<SyncDisabledModeForTest> modeSetDuringTest = new AtomicReference<>();
        mTestBody.onEvaluate(() -> modeSetDuringTest.set(mFakeDeviceConfig.getSyncDisabledMode()));
        var rule = newRule(mFakeDeviceConfig, PERSISTENT);

        rule.setSyncDisabledModeForTest(UNTIL_REBOOT);
        runRule(rule);

        expect.withMessage("mode during test")
                .that(modeSetDuringTest.get())
                .isEqualTo(UNTIL_REBOOT);
        expect.withMessage("mode after test")
                .that(mFakeDeviceConfig.getSyncDisabledMode())
                .isEqualTo(NONE);
    }

    @Test
    public final void testSetSyncDisabledModeForTest_duringTest() throws Throwable {
        var rule = newRule(mFakeDeviceConfig, PERSISTENT);
        AtomicReference<SyncDisabledModeForTest> modeSetDuringTest = new AtomicReference<>();
        mTestBody.onEvaluate(
                () -> {
                    rule.setSyncDisabledModeForTest(UNTIL_REBOOT);
                    modeSetDuringTest.set(mFakeDeviceConfig.getSyncDisabledMode());
                });

        runRule(rule);

        expect.withMessage("mode during test")
                .that(modeSetDuringTest.get())
                .isEqualTo(UNTIL_REBOOT);
        expect.withMessage("mode after test")
                .that(mFakeDeviceConfig.getSyncDisabledMode())
                .isEqualTo(NONE);
    }

    @Test
    public final void testSetSyncDisabledModeForTest_duringTest_deviceConfigThrows()
            throws Throwable {
        var realException = new IllegalStateException("Set? Why not get?");
        mFakeDeviceConfig.onSetSyncDisabledModeCallback(failWith(realException));
        var rule = newRule(mFakeDeviceConfig);

        AtomicReference<Exception> exceptionThrownDuringTest = new AtomicReference<>();
        mTestBody.onEvaluate(
                () -> {
                    try {
                        rule.setSyncDisabledModeForTest(UNTIL_REBOOT);
                    } catch (Exception e) {
                        exceptionThrownDuringTest.set(e);
                    }
                });

        runRule(rule);

        Exception thrown = exceptionThrownDuringTest.get();
        assertWithMessage("exception thrown during test").that(thrown).isNotNull();
        assertWithMessage("exception thrown during test")
                .that(thrown)
                .isInstanceOf(ActionExecutionException.class);
        assertWithMessage("real exception")
                .that(thrown)
                .hasCauseThat()
                .isSameInstanceAs(realException);
    }

    @Test
    public final void testSetSdkSandboxState_returnsSelf() throws Throwable {
        var rule = newRule(mFakeDeviceConfig);

        expect.withMessage("return of setSdkSandboxState()")
                .that(rule.setSdkSandboxState(/* enabled= */ true))
                .isSameInstanceAs(rule);
    }

    @Test
    public final void testSetSdkSandboxState_beforeTest() throws Throwable {
        var rule = newRule(mFakeSdkSandbox, mFakeDeviceConfig);
        AtomicReference<SdkSandbox.State> stateSetDuringTest = new AtomicReference<>();
        mTestBody.onEvaluate(() -> stateSetDuringTest.set(mFakeSdkSandbox.getState()));

        rule.setSdkSandboxState(true);
        runRule(rule);

        expect.withMessage("sdk_sandbox sdk state during test")
                .that(stateSetDuringTest.get())
                .isEqualTo(ENABLED);
        expect.withMessage("sdk_sandbox after test")
                .that(mFakeSdkSandbox.getState())
                .isEqualTo(DISABLED);
    }

    @Test
    public final void testSetSdkSandboxState_duringTest() throws Throwable {
        var rule = newRule(mFakeSdkSandbox, mFakeDeviceConfig);
        AtomicReference<SdkSandbox.State> stateSetDuringTest = new AtomicReference<>();
        mTestBody.onEvaluate(
                () -> {
                    rule.setSdkSandboxState(true);
                    stateSetDuringTest.set(mFakeSdkSandbox.getState());
                });

        runRule(rule);

        expect.withMessage("sdk_sandbox sdk state during test")
                .that(stateSetDuringTest.get())
                .isEqualTo(ENABLED);
        expect.withMessage("sdk_sandbox after test")
                .that(mFakeSdkSandbox.getState())
                .isEqualTo(ENABLED);
    }

    @Test
    public final void testSetSdkSandboxState_duringTest_sdkSandboxThrows() throws Throwable {
        var realException = new IllegalStateException("Set? Why not get?");
        mFakeSdkSandbox.onSetStateThrows(realException);
        // set it
        var rule = newRule(mFakeSdkSandbox, mFakeDeviceConfig);
        AtomicReference<Exception> exceptionThrownDuringTest = new AtomicReference<>();
        mTestBody.onEvaluate(
                () -> {
                    try {
                        rule.setSdkSandboxState(true);
                    } catch (Exception e) {
                        exceptionThrownDuringTest.set(e);
                    }
                });

        runRule(rule);

        Exception thrown = exceptionThrownDuringTest.get();
        assertWithMessage("exception thrown during test").that(thrown).isNotNull();
        assertWithMessage("exception thrown during test")
                .that(thrown)
                .isInstanceOf(ActionExecutionException.class);
        assertWithMessage("real exception")
                .that(thrown)
                .hasCauseThat()
                .isSameInstanceAs(realException);
    }

    private void runRule(R rule) throws Throwable {
        runRule(rule, mSuite);
    }

    private void runRule(R rule, Description description) throws Throwable {
        rule.apply(mTestBody, description).evaluate();
    }

    private Runnable failWith(String message) {
        return failWith(new IllegalStateException(message));
    }

    private Runnable failWith(RuntimeException exception) {
        return () -> {
            throw exception;
        };
    }

    // Use to create the Description fixtures
    private static class AClassHasNoNothingAtAll {}
}
