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

import static org.junit.Assert.assertThrows;

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
    protected abstract R newRule(
            SdkSandbox sdkSandbox, DeviceConfig deviceConfig, SyncDisabledModeForTest syncMode);

    private R newRule(DeviceConfig deviceConfig, SyncDisabledModeForTest syncMode) {
        return newRule(mFakeSdkSandbox, deviceConfig, syncMode);
    }

    @Test
    public final void testConstructor_nullArgs() {
        assertThrows(
                NullPointerException.class,
                () -> newRule(mFakeSdkSandbox, /* deviceConfig= */ null, PERSISTENT));
        assertThrows(
                NullPointerException.class,
                () -> newRule(/* sdkSandbox= */ null, mFakeDeviceConfig, PERSISTENT));
        assertThrows(
                NullPointerException.class,
                () -> newRule(mFakeSdkSandbox, mFakeDeviceConfig, /* syncMode= */ null));
    }

    @Test
    public final void testConstructor_unsupported() throws Throwable {
        assertThrows(IllegalArgumentException.class, () -> newRule(mFakeDeviceConfig, UNSUPPORTED));
    }

    @Test
    public final void testThrowsIfNotUsedAsClassRule() {
        var rule = newRule(mFakeDeviceConfig, PERSISTENT);

        assertThrows(IllegalStateException.class, () -> runRule(rule, mTest));
    }

    @Test
    public final void testWhenGetModeReturnsUnsupported() throws Throwable {
        mFakeDeviceConfig.setSyncDisabledMode(UNSUPPORTED);
        mFakeDeviceConfig.onSetSyncDisabledModeCallback(failWith("Supported I'm Not!"));

        AtomicReference<SyncDisabledModeForTest> modeSetDuringTest = new AtomicReference<>();
        mTestBody.onEvaluate(() -> modeSetDuringTest.set(mFakeDeviceConfig.getSyncDisabledMode()));
        var rule = newRule(mFakeDeviceConfig, PERSISTENT);
        runRule(rule);

        expect.withMessage("mode during test").that(modeSetDuringTest.get()).isEqualTo(UNSUPPORTED);
        expect.withMessage("mode after test")
                .that(mFakeDeviceConfig.getSyncDisabledMode())
                .isEqualTo(UNSUPPORTED);
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
        mFakeDeviceConfig.onSetSyncDisabledModeCallback(failWith("Ready, Set, Throw!"));
        AtomicReference<SyncDisabledModeForTest> modeSetDuringTest = new AtomicReference<>();
        mTestBody.onEvaluate(() -> modeSetDuringTest.set(mFakeDeviceConfig.getSyncDisabledMode()));

        var rule = newRule(mFakeDeviceConfig, PERSISTENT);
        runRule(rule);

        // Was not changed at all
        expect.withMessage("mode during test").that(modeSetDuringTest.get()).isEqualTo(NONE);
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
        var rule = newRule(mFakeDeviceConfig, PERSISTENT);

        assertThrows(NullPointerException.class, () -> rule.setSyncDisabledModeForTest(null));
    }

    @Test
    public final void testSetSyncDisabledModeForTest_returnsSelf() throws Throwable {
        var rule = newRule(mFakeDeviceConfig, PERSISTENT);

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
    public final void testSetyncDisabledModeForTest_duringTest() throws Throwable {
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
    public final void testSetSdkSandboxState_returnsSelf() throws Throwable {
        var rule = newRule(mFakeDeviceConfig, PERSISTENT);

        expect.withMessage("return of setSdkSandboxState()")
                .that(rule.setSdkSandboxState(/* enabled= */ true))
                .isSameInstanceAs(rule);
    }

    @Test
    public final void testSetSdkSandboxState_beforeTest() throws Throwable {
        var rule = newRule(mFakeSdkSandbox, mFakeDeviceConfig, PERSISTENT);
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
        var rule = newRule(mFakeSdkSandbox, mFakeDeviceConfig, PERSISTENT);
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

    private void runRule(R rule) throws Throwable {
        runRule(rule, mSuite);
    }

    private void runRule(R rule, Description description) throws Throwable {
        rule.apply(mTestBody, description).evaluate();
    }

    private Runnable failWith(String message) {
        return () -> {
            throw new IllegalStateException(message);
        };
    }

    // Use to create the Description fixtures
    private static class AClassHasNoNothingAtAll {}
}
