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

import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.PERSISTENT;
import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.UNSUPPORTED;
import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.UNTIL_REBOOT;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.testing.device.DeviceConfig;
import com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest;
import com.android.adservices.shared.testing.flags.AbstractFlagsPreparerClassRule;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("VisibleForTests") // TODO(b/343741206): Remove suppress warning once fixed.
/**
 * Base class for {@link AbstractFlagsPreparerClassRule} implementations.
 *
 * <p>It contains the base tests for all of them, although subclasses my add they extra tests.
 *
 * @param <R> type of the rule
 */
public abstract class AbstractFlagsPreparerClassRuleTestCase<
                R extends AbstractFlagsPreparerClassRule>
        extends SharedSidelessTestCase {

    // Not using NONE because that's the default value of FakeDeviceCOnfig
    private SyncDisabledModeForTest mModeBeforeTest = UNTIL_REBOOT;
    private SyncDisabledModeForTest mModeDuringTest = PERSISTENT;

    protected final SimpleStatement mTestBody = new SimpleStatement();

    // Need a suite with a test to emulate a class
    protected final Description mTest =
            Description.createTestDescription(AClassHasNoNothingAtAll.class, "butItHasATest");
    protected final Description mSuite =
            Description.createSuiteDescription(AClassHasNoNothingAtAll.class);

    private FakeDeviceConfig mDeviceConfig = new FakeDeviceConfig();

    @Before
    public final void setTestFixtures() {
        mSuite.addChild(mTest);
        mDeviceConfig.setSyncDisabledMode(mModeBeforeTest);
    }

    /** Gets a new concrete implementation of the rule. */
    protected abstract R newRule(DeviceConfig deviceConfig, SyncDisabledModeForTest syncMode);

    @Test
    public final void testConstructor_nullArgs() {
        assertThrows(
                NullPointerException.class,
                () -> newRule(/* deviceConfig= */ null, mModeDuringTest));
        assertThrows(
                NullPointerException.class, () -> newRule(mDeviceConfig, /* syncMode= */ null));
    }

    @Test
    public final void testConstructor_unsupported() throws Throwable {
        assertThrows(IllegalArgumentException.class, () -> newRule(mDeviceConfig, UNSUPPORTED));
    }

    @Test
    public final void testThrowsIfUsedAsRule() {
        var rule = newRule(mDeviceConfig, PERSISTENT);

        assertThrows(IllegalStateException.class, () -> runRule(rule, mTest));
    }

    @Test
    public final void testWhenGetModeReturnsUnsupported() throws Throwable {
        mDeviceConfig.setSyncDisabledMode(UNSUPPORTED);
        mDeviceConfig.onSetSyncDisabledModeCallback(failWith("Supported I'm Not!"));

        AtomicReference<SyncDisabledModeForTest> modeSetDuringTest = new AtomicReference<>();
        mTestBody.onEvaluate(() -> modeSetDuringTest.set(mDeviceConfig.getSyncDisabledMode()));
        var rule = newRule(mDeviceConfig, mModeDuringTest);
        runRule(rule);

        expect.withMessage("mode during test").that(modeSetDuringTest.get()).isEqualTo(UNSUPPORTED);
        expect.withMessage("mode after test")
                .that(mDeviceConfig.getSyncDisabledMode())
                .isEqualTo(UNSUPPORTED);
    }

    @Test
    public final void testWhenTestPass() throws Throwable {
        AtomicReference<SyncDisabledModeForTest> modeSetDuringTest = new AtomicReference<>();
        mTestBody.onEvaluate(() -> modeSetDuringTest.set(mDeviceConfig.getSyncDisabledMode()));
        var rule = newRule(mDeviceConfig, mModeDuringTest);
        runRule(rule);

        expect.withMessage("mode during test")
                .that(modeSetDuringTest.get())
                .isEqualTo(mModeDuringTest);
        expect.withMessage("mode after test")
                .that(mDeviceConfig.getSyncDisabledMode())
                .isEqualTo(mModeBeforeTest);
    }

    @Test
    public final void testWhenTestFail() throws Throwable {
        RuntimeException testFailure = new RuntimeException("TEST, Y U NO PASS?");
        AtomicReference<SyncDisabledModeForTest> modeSetDuringTest = new AtomicReference<>();
        mTestBody.onEvaluate(
                () -> {
                    modeSetDuringTest.set(mDeviceConfig.getSyncDisabledMode());
                    throw testFailure;
                });
        var rule = newRule(mDeviceConfig, mModeDuringTest);
        Throwable thrown = assertThrows(Throwable.class, () -> runRule(rule));

        expect.withMessage("thrown exception ").that(thrown).isSameInstanceAs(testFailure);
        expect.withMessage("mode during test")
                .that(modeSetDuringTest.get())
                .isEqualTo(mModeDuringTest);
        expect.withMessage("mode after test")
                .that(mDeviceConfig.getSyncDisabledMode())
                .isEqualTo(mModeBeforeTest);
    }

    @Test
    public final void testWhenGetSyncModeFails() throws Throwable {
        mDeviceConfig.onGetSyncDisabledModeCallback(failWith("(You Can't Get No) Satisfaction"));
        AtomicReference<SyncDisabledModeForTest> modeSetDuringTest = new AtomicReference<>();
        mTestBody.onEvaluate(
                () -> {
                    // reset it, otherwise next call would fail
                    mDeviceConfig.onGetSyncDisabledModeCallback(null);
                    modeSetDuringTest.set(mDeviceConfig.getSyncDisabledMode());
                    mDeviceConfig.onSetSyncDisabledModeCallback(
                            failWith("Shouldn't have been called at the end"));
                });

        var rule = newRule(mDeviceConfig, mModeDuringTest);
        runRule(rule);

        expect.withMessage("mode during test")
                .that(modeSetDuringTest.get())
                .isEqualTo(mModeDuringTest);
        // Not restored at the end because it didn't get the initial mode
        expect.withMessage("mode after test")
                .that(mDeviceConfig.getSyncDisabledMode())
                .isEqualTo(mModeDuringTest);
    }

    @Test
    public final void testWhenSetSyncModeFails() throws Throwable {
        mDeviceConfig.onSetSyncDisabledModeCallback(failWith("Ready, Set, Throw!"));
        AtomicReference<SyncDisabledModeForTest> modeSetDuringTest = new AtomicReference<>();
        mTestBody.onEvaluate(() -> modeSetDuringTest.set(mDeviceConfig.getSyncDisabledMode()));

        var rule = newRule(mDeviceConfig, mModeDuringTest);
        runRule(rule);

        // Was not changed at all
        expect.withMessage("mode during test")
                .that(modeSetDuringTest.get())
                .isEqualTo(mModeBeforeTest);
        expect.withMessage("mode after test")
                .that(mDeviceConfig.getSyncDisabledMode())
                .isEqualTo(mModeBeforeTest);
    }

    @Test
    public final void testWhenTestPassAndRestoreFails() throws Throwable {
        AtomicReference<SyncDisabledModeForTest> modeSetDuringTest = new AtomicReference<>();
        mTestBody.onEvaluate(
                () -> {
                    modeSetDuringTest.set(mDeviceConfig.getSyncDisabledMode());
                    mDeviceConfig.onSetSyncDisabledModeCallback(failWith("Failed at end"));
                });
        var rule = newRule(mDeviceConfig, mModeDuringTest);
        runRule(rule);

        expect.withMessage("mode during test")
                .that(modeSetDuringTest.get())
                .isEqualTo(mModeDuringTest);
        expect.withMessage("mode after test")
                .that(mDeviceConfig.getSyncDisabledMode())
                .isEqualTo(mModeDuringTest);
    }

    @Test
    public final void testSyncModeNotSetWhenItsTheSame() throws Throwable {
        AtomicBoolean setCalled = new AtomicBoolean();
        mDeviceConfig.onSetSyncDisabledModeCallback(() -> setCalled.set(true));

        var rule = newRule(mDeviceConfig, mModeBeforeTest);
        runRule(rule);

        if (setCalled.get()) {
            expect.withMessage("Rule shouldn't have called deviceConfig.setSyncDisabledMode()")
                    .fail();
        }
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
