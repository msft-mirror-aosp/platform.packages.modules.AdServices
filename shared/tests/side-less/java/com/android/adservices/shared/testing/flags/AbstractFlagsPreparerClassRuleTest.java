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
package com.android.adservices.shared.testing.flags;

import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.PERSISTENT;
import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.UNSUPPORTED;
import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.UNTIL_REBOOT;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.meta_testing.FakeDeviceConfig;
import com.android.adservices.shared.meta_testing.SimpleStatement;
import com.android.adservices.shared.testing.Logger.RealLogger;
import com.android.adservices.shared.testing.SharedSidelessTestCase;
import com.android.adservices.shared.testing.StandardStreamsLogger;
import com.android.adservices.shared.testing.device.DeviceConfig;
import com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

// TODO(297085722): update javadoc which device-side / host-side commands
/**
 * Test case for {@link AbstractFlagsPreparerClassRule} implementations.
 *
 * <p>By default, it uses a {@link FakeFlagsPreparerClassRule bogus rule} so it can be run by IDEs,\
 * but subclasses should implement {@link #newRule(DeviceConfig, SyncDisabledModeForTest)}.
 *
 * <p>Notice that currently there is not Android project to run these side-less tests, so you would
 * need to use either the device-side ({@code AdServicesSharedLibrariesUnitTests}) or host-side
 * ({@code AdServicesSharedLibrariesHostTests}) project:
 *
 * <ul>
 *   <li>{@code atest AdServicesSharedLibrariesUnitTests:AbstractFlagsPreparerClassRuleTest}
 *   <li>{@code atest AdServicesSharedLibrariesHostTests:AbstractFlagsPreparerClassRuleTest}
 * </ul>
 *
 * <p>Notice that when running the host-side tests, you can use the {@code --host} option so it
 * doesn't require a connected device.
 */
public class AbstractFlagsPreparerClassRuleTest extends SharedSidelessTestCase {

    // Not using NONE because that's the default value of FakeDeviceCOnfig
    private SyncDisabledModeForTest mModeBeforeTest = UNTIL_REBOOT;
    private SyncDisabledModeForTest mModeDuringTest = PERSISTENT;

    private final SimpleStatement mTestBody = new SimpleStatement();

    // Need a suite with a test to emulate a class
    private Description mTest =
            Description.createTestDescription(AClassHasNoNothingAtAll.class, "butItHasATest");
    private Description mSuite = Description.createSuiteDescription(AClassHasNoNothingAtAll.class);

    private FakeDeviceConfig mDeviceConfig = new FakeDeviceConfig();

    @Before
    public final void setTestFixtures() {
        mSuite.addChild(mTest);
        mDeviceConfig.setSyncDisabledMode(mModeBeforeTest);
    }

    /**
     * Gets a new concrete implementation of the rule.
     *
     * @return {@code FakeFlagsPreparerClassRule} by default, but should be override by device-side
     *     and host-side tests to return their version.
     */
    protected AbstractFlagsPreparerClassRule newRule(
            DeviceConfig deviceConfig, SyncDisabledModeForTest syncMode) {
        return new FakeFlagsPreparerClassRule(deviceConfig, syncMode);
    }

    @Test
    public void testConstructor_nullArgs() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new FakeFlagsPreparerClassRule(
                                /* realLogger= */ null, mDeviceConfig, mModeDuringTest));
        assertThrows(
                NullPointerException.class,
                () -> new FakeFlagsPreparerClassRule(/* deviceConfig= */ null, mModeDuringTest));
        assertThrows(
                NullPointerException.class,
                () -> new FakeFlagsPreparerClassRule(mDeviceConfig, /* syncMode= */ null));
    }

    @Test
    public void testConstructor_unsupported() throws Throwable {
        assertThrows(
                IllegalArgumentException.class,
                () -> new FakeFlagsPreparerClassRule(mDeviceConfig, UNSUPPORTED));
    }

    @Test
    public void testThrowsIfUsedAsRule() {
        var rule = newRule(mDeviceConfig, PERSISTENT);

        assertThrows(IllegalStateException.class, () -> rule.evaluate(mTestBody, mTest));
    }

    @Test
    public void testWhenGetModeReturnsUnsupported() throws Throwable {
        mDeviceConfig.setSyncDisabledMode(UNSUPPORTED);
        mDeviceConfig.onSetSyncDisabledModeCallback(failWith("Supported I'm Not!"));

        AtomicReference<SyncDisabledModeForTest> modeSetDuringTest = new AtomicReference<>();
        mTestBody.onEvaluate(() -> modeSetDuringTest.set(mDeviceConfig.getSyncDisabledMode()));
        var rule = newRule(mDeviceConfig, mModeDuringTest);
        rule.evaluate(mTestBody, mSuite);

        expect.withMessage("mode during test").that(modeSetDuringTest.get()).isEqualTo(UNSUPPORTED);
        expect.withMessage("mode after test")
                .that(mDeviceConfig.getSyncDisabledMode())
                .isEqualTo(UNSUPPORTED);
    }

    @Test
    public void testWhenTestPass() throws Throwable {
        AtomicReference<SyncDisabledModeForTest> modeSetDuringTest = new AtomicReference<>();
        mTestBody.onEvaluate(() -> modeSetDuringTest.set(mDeviceConfig.getSyncDisabledMode()));
        var rule = newRule(mDeviceConfig, mModeDuringTest);
        rule.evaluate(mTestBody, mSuite);

        expect.withMessage("mode during test")
                .that(modeSetDuringTest.get())
                .isEqualTo(mModeDuringTest);
        expect.withMessage("mode after test")
                .that(mDeviceConfig.getSyncDisabledMode())
                .isEqualTo(mModeBeforeTest);
    }

    @Test
    public void testWhenTestFail() throws Throwable {
        RuntimeException testFailure = new RuntimeException("TEST, Y U NO PASS?");
        AtomicReference<SyncDisabledModeForTest> modeSetDuringTest = new AtomicReference<>();
        mTestBody.onEvaluate(
                () -> {
                    modeSetDuringTest.set(mDeviceConfig.getSyncDisabledMode());
                    throw testFailure;
                });
        var rule = newRule(mDeviceConfig, mModeDuringTest);
        Throwable thrown = assertThrows(Throwable.class, () -> rule.evaluate(mTestBody, mSuite));

        expect.withMessage("thrown exception ").that(thrown).isSameInstanceAs(testFailure);
        expect.withMessage("mode during test")
                .that(modeSetDuringTest.get())
                .isEqualTo(mModeDuringTest);
        expect.withMessage("mode after test")
                .that(mDeviceConfig.getSyncDisabledMode())
                .isEqualTo(mModeBeforeTest);
    }

    @Test
    public void testWhenGetSyncModeFails() throws Throwable {
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
        rule.evaluate(mTestBody, mSuite);

        expect.withMessage("mode during test")
                .that(modeSetDuringTest.get())
                .isEqualTo(mModeDuringTest);
        // Not restored at the end because it didn't get the initial mode
        expect.withMessage("mode after test")
                .that(mDeviceConfig.getSyncDisabledMode())
                .isEqualTo(mModeDuringTest);
    }

    @Test
    public void testWhenSetSyncModeFails() throws Throwable {
        mDeviceConfig.onSetSyncDisabledModeCallback(failWith("Ready, Set, Throw!"));
        AtomicReference<SyncDisabledModeForTest> modeSetDuringTest = new AtomicReference<>();
        mTestBody.onEvaluate(() -> modeSetDuringTest.set(mDeviceConfig.getSyncDisabledMode()));

        var rule = newRule(mDeviceConfig, mModeDuringTest);
        rule.evaluate(mTestBody, mSuite);

        // Was not changed at all
        expect.withMessage("mode during test")
                .that(modeSetDuringTest.get())
                .isEqualTo(mModeBeforeTest);
        expect.withMessage("mode after test")
                .that(mDeviceConfig.getSyncDisabledMode())
                .isEqualTo(mModeBeforeTest);
    }

    @Test
    public void testWhenTestPassAndRestoreFails() throws Throwable {
        AtomicReference<SyncDisabledModeForTest> modeSetDuringTest = new AtomicReference<>();
        mTestBody.onEvaluate(
                () -> {
                    modeSetDuringTest.set(mDeviceConfig.getSyncDisabledMode());
                    mDeviceConfig.onSetSyncDisabledModeCallback(failWith("Failed at end"));
                });
        var rule = newRule(mDeviceConfig, mModeDuringTest);
        rule.evaluate(mTestBody, mSuite);

        expect.withMessage("mode during test")
                .that(modeSetDuringTest.get())
                .isEqualTo(mModeDuringTest);
        expect.withMessage("mode after test")
                .that(mDeviceConfig.getSyncDisabledMode())
                .isEqualTo(mModeDuringTest);
    }

    @Test
    public void testSyncModeNotSetWhenItsTheSame() throws Throwable {
        AtomicBoolean setCalled = new AtomicBoolean();
        mDeviceConfig.onSetSyncDisabledModeCallback(() -> setCalled.set(true));

        var rule = newRule(mDeviceConfig, mModeBeforeTest);
        rule.evaluate(mTestBody, mSuite);

        if (setCalled.get()) {
            expect.withMessage("Rule shouldn't have called deviceConfig.setSyncDisabledMode()")
                    .fail();
        }
    }

    private Runnable failWith(String message) {
        return () -> {
            throw new IllegalStateException(message);
        };
    }

    private static final class FakeFlagsPreparerClassRule extends AbstractFlagsPreparerClassRule {

        FakeFlagsPreparerClassRule(
                RealLogger realLogger, DeviceConfig deviceConfig, SyncDisabledModeForTest mode) {
            super(realLogger, deviceConfig, isValid(mode));
        }

        protected FakeFlagsPreparerClassRule(
                DeviceConfig deviceConfig, SyncDisabledModeForTest mode) {
            this(StandardStreamsLogger.getInstance(), deviceConfig, mode);
        }
    }

    private static SyncDisabledModeForTest isValid(SyncDisabledModeForTest mode) {
        if (mode.equals(UNSUPPORTED)) {
            throw new IllegalArgumentException("invalid mode: " + mode);
        }
        return mode;
    }

    // Use to create the Description fixtures
    private static class AClassHasNoNothingAtAll {}
}
