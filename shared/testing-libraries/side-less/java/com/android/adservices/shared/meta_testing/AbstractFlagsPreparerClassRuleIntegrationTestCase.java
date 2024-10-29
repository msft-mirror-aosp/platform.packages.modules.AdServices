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

import static com.android.adservices.shared.meta_testing.CommonDescriptions.newTestMethodForClassRule;
import static com.android.adservices.shared.testing.AndroidSdk.SC;
import static com.android.adservices.shared.testing.AndroidSdk.SC_V2;
import static com.android.adservices.shared.testing.SdkSandbox.State.DISABLED;
import static com.android.adservices.shared.testing.SdkSandbox.State.ENABLED;
import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.DISABLED_SOMEHOW;
import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.NONE;
import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.PERSISTENT;
import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.UNTIL_REBOOT;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;

import com.android.adservices.shared.meta_testing.CommonDescriptions.AClassDisablesDeviceConfigUntilReboot;
import com.android.adservices.shared.meta_testing.CommonDescriptions.AClassDisablesSdkSandbox;
import com.android.adservices.shared.meta_testing.CommonDescriptions.AClassEnablesSdkSandbox;
import com.android.adservices.shared.meta_testing.CommonDescriptions.AClassHasNoNothingAtAll;
import com.android.adservices.shared.meta_testing.CommonDescriptions.ASubClassDisablesDeviceConfigUntilRebootAndAlsoEnablesSdkSandbox;
import com.android.adservices.shared.meta_testing.CommonDescriptions.ASubClassEnablesSdkSandboxAndAlsoDisablesDeviceConfigUntilReboot;
import com.android.adservices.shared.meta_testing.CommonDescriptions.ATypicalSubclass;
import com.android.adservices.shared.meta_testing.CommonDescriptions.ATypicalSubclassThatImplementsAnInterfaceThatEnablesSdkSandbox;
import com.android.adservices.shared.testing.DynamicLogger;
import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.SdkSandbox;
import com.android.adservices.shared.testing.SetSdkSandboxStateAction;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.annotations.RequiresSdkRange;
import com.android.adservices.shared.testing.device.DeviceConfig;
import com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest;
import com.android.adservices.shared.testing.flags.AbstractFlagsPreparerClassRule;
import com.android.adservices.shared.testing.flags.SetSyncModeAction;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

// TODO(b/347083260): missing AdservicesHostSideFlagsPreparerClassRuleIntegrationTest
/**
 * Integration test for {@link SdkSandbox} implementations.
 *
 * <p>It executes the commands and checks the result; it's needed to make sure {@code cmd} and
 * {@code dumpsys} output are properly parsed.
 *
 * @param <R> concrete rule type
 */
public abstract class AbstractFlagsPreparerClassRuleIntegrationTestCase<
                R extends AbstractFlagsPreparerClassRule<R>>
        extends IntegrationTestCase {

    private final AtomicReference<SyncDisabledModeForTest> mSyncModeOnTest =
            new AtomicReference<>();
    private final AtomicReference<SdkSandbox.State> mSdkSandboxStateOnTest =
            new AtomicReference<>();

    private final List<Object> mCalls = new ArrayList<>();
    // mCalls is used to keep track of the relevant calls made to the objects below
    private final MyDeviceConfigWrapper mDeviceConfig = new MyDeviceConfigWrapper();
    private final SdkSandboxWrapper mSdkSandbox = new MySdkSandboxWrapper();

    // mSetupCalls contains the calls made to prepare the device for the test
    private final List<Object> mSetupCalls = new ArrayList<>();

    private R mRule;

    private SyncDisabledModeForTest mSyncModeBefore;
    private SdkSandbox.State mSdkSandboxStateBefore;

    private final SimpleStatement mStatement =
            new SimpleStatement()
                    .onEvaluate(
                            () -> {
                                SyncDisabledModeForTest syncDisabledMode =
                                        mDeviceConfig.getSyncDisabledMode();
                                SdkSandbox.State state = mSdkSandbox.getState();
                                mLog.d(
                                        "on test: deviceConfig.syncMode=%s, sdkSandbox.state=%s",
                                        syncDisabledMode, state);
                                mSyncModeOnTest.set(syncDisabledMode);
                                mSdkSandboxStateOnTest.set(state);
                            });

    /**
     * Creates a new, side-specific instance of the rule.
     *
     * @param sdkSandboxWrapper used to inject the real {@link SdkSandbox} used by the rule
     * @param deviceConfigWrapper used to inject the real {@link DeviceConfig} used by the rule
     * @return new instance
     */
    protected abstract R newRule(
            SdkSandboxWrapper sdkSandboxWrapper, DeviceConfigWrapper deviceConfigWrapper);

    @Before
    public final void setFixtures() {
        mRule = newRule(mSdkSandbox, mDeviceConfig);
        if (mRule == null) {
            assertWithMessage("newRule() returned null").fail();
        }
        mSyncModeBefore = mDeviceConfig.getSyncDisabledMode();
        mSdkSandboxStateBefore = mSdkSandbox.getState();
        mLog.d(
                "setFixtures(): deviceConfig.syncMode=%s, sdkSandbox.state=%s",
                mSyncModeBefore, mSdkSandboxStateBefore);
    }

    @After
    public final void restoreState() {
        try {
            if (mSyncModeBefore.isSettable()) {
                mDeviceConfig.setSyncDisabledMode(mSyncModeBefore);
            }
        } finally {
            if (mSdkSandboxStateBefore.isSettable()) {
                mSdkSandbox.setState(mSdkSandboxStateBefore);
            }
        }
    }

    @Test
    public final void testAnnotationLessRule() throws Throwable {
        Description test = newTestMethodForClassRule(AClassHasNoNothingAtAll.class);

        mRule.apply(mStatement, test).evaluate();

        expect.withMessage("deviceConfig.getSyncDisabledMode() on test")
                .that(mSyncModeOnTest.get())
                .isEqualTo(mSyncModeBefore);
        expect.withMessage("deviceConfig.getSyncDisabledMode() after test")
                .that(mDeviceConfig.getSyncDisabledMode())
                .isEqualTo(mSyncModeBefore);
        expect.withMessage("sdkSandbox.getState() on test")
                .that(mSdkSandboxStateOnTest.get())
                .isEqualTo(mSdkSandboxStateBefore);
        expect.withMessage("sdkSandbox.getState() after test")
                .that(mSdkSandbox.getState())
                .isEqualTo(mSdkSandboxStateBefore);

        expectCalls();
    }

    @Test
    public final void testMethodAnnotation_setSdkSandboxStateEnabledAnnotation() throws Throwable {
        setSdkSandboxState(DISABLED);

        Description test =
                newTestMethodForClassRule(
                        AClassHasNoNothingAtAll.class,
                        // Should be ignored - rule only uses annotations from types
                        new SetSdkSandboxStateEnabledAnnotation(true));

        mRule.apply(mStatement, test).evaluate();

        expect.withMessage("sdkSandbox.getState() on test")
                .that(mSdkSandboxStateOnTest.get())
                .isEqualTo(DISABLED);
        expect.withMessage("sdkSandbox.getState() after test")
                .that(mSdkSandbox.getState())
                .isEqualTo(DISABLED);

        expectCalls();
    }

    @Test
    public final void testMethodAnnotation_setSdkSandboxStateDisabledAnnotation() throws Throwable {
        setSdkSandboxState(ENABLED);

        Description test =
                newTestMethodForClassRule(
                        AClassHasNoNothingAtAll.class,
                        // Should be ignored - rule only uses annotations from types
                        new SetSdkSandboxStateEnabledAnnotation(false));

        mRule.apply(mStatement, test).evaluate();

        expect.withMessage("sdkSandbox.getState() on test")
                .that(mSdkSandboxStateOnTest.get())
                .isEqualTo(ENABLED);
        expect.withMessage("sdkSandbox.getState() after test")
                .that(mSdkSandbox.getState())
                .isEqualTo(ENABLED);

        expectCalls();
    }

    @Test
    public final void testMethodAnnotation_setSyncDisabledModeForTestAnnotation() throws Throwable {
        setSyncMode(NONE);
        Description test =
                newTestMethodForClassRule(
                        AClassHasNoNothingAtAll.class,
                        // Should be ignored - rule only uses annotations from types
                        new SetSyncDisabledModeForTestAnnotation(PERSISTENT));

        mRule.apply(mStatement, test).evaluate();

        expect.withMessage("deviceConfig.getSyncDisabledMode() on test")
                .that(mSyncModeOnTest.get())
                .isEqualTo(NONE);
        expect.withMessage("deviceConfig.getSyncDisabledMode() after test")
                .that(mDeviceConfig.getSyncDisabledMode())
                .isEqualTo(NONE);

        expectCalls();
    }


    @Test
    public final void testClassAnnotation_setSdkSandboxStateEnabled() throws Throwable {
        setSdkSandboxState(DISABLED);
        Description test = newTestMethodForClassRule(AClassEnablesSdkSandbox.class);

        mRule.apply(mStatement, test).evaluate();

        expect.withMessage("sdkSandbox.getState() on test")
                .that(mSdkSandboxStateOnTest.get())
                .isEqualTo(ENABLED);
        expect.withMessage("sdkSandbox.getState() after test")
                .that(mSdkSandbox.getState())
                .isEqualTo(mSdkSandboxStateBefore);

        expectCalls(ENABLED, mSdkSandboxStateBefore);
    }

    @Test
    public final void testClassAnnotation_setSdkSandboxStateDisabled() throws Throwable {
        setSdkSandboxState(ENABLED);
        Description test = newTestMethodForClassRule(AClassDisablesSdkSandbox.class);

        mRule.apply(mStatement, test).evaluate();

        expect.withMessage("sdkSandbox.getState() on test")
                .that(mSdkSandboxStateOnTest.get())
                .isEqualTo(DISABLED);
        expect.withMessage("sdkSandbox.getState() after test")
                .that(mSdkSandbox.getState())
                .isEqualTo(ENABLED);

        expectCalls(DISABLED, ENABLED);
    }

    @Test
    @RequiresSdkRange(atLeast = SC, atMost = SC_V2, reason = "disabled type is unknown on S")
    public final void testClassAnnotation_setSyncDisabledModeForTestAnnotation_untilReboot_S()
            throws Throwable {
        testClassAnnotation_setSyncDisabledModeForTestAnnotation_untilReboot(DISABLED_SOMEHOW);
    }

    @Test
    @RequiresSdkLevelAtLeastT(reason = "disabled type is unknown on S")
    public final void testClassAnnotation_setSyncDisabledModeForTestAnnotation_untilReboot_TPlus()
            throws Throwable {
        testClassAnnotation_setSyncDisabledModeForTestAnnotation_untilReboot(UNTIL_REBOOT);
    }

    private void testClassAnnotation_setSyncDisabledModeForTestAnnotation_untilReboot(
            SyncDisabledModeForTest expectedModeAfterSet) throws Throwable {
        setSyncMode(NONE);
        Description test = newTestMethodForClassRule(AClassDisablesDeviceConfigUntilReboot.class);

        mRule.apply(mStatement, test).evaluate();

        expect.withMessage("deviceConfig.getSyncDisabledMode() on test")
                .that(mSyncModeOnTest.get())
                .isEqualTo(expectedModeAfterSet);
        expect.withMessage("deviceConfig.getSyncDisabledMode() after test")
                .that(mDeviceConfig.getSyncDisabledMode())
                .isEqualTo(NONE);

        expectCalls(UNTIL_REBOOT, NONE);
    }

    @Test
    public final void
            testClassAndMethodAnnotation_setSyncDisabledModeForTestAnnotation_untilRebootThenNone()
                    throws Throwable {
        setSyncMode(NONE);
        Description test =
                newTestMethodForClassRule(
                        AClassDisablesDeviceConfigUntilReboot.class,
                        // should be ignored
                        new SetSyncDisabledModeForTestAnnotation(PERSISTENT));

        mRule.apply(mStatement, test).evaluate();

        expect.withMessage("deviceConfig.getSyncDisabledMode() on test")
                .that(mSyncModeOnTest.get())
                .isEqualTo(UNTIL_REBOOT);
        expect.withMessage("deviceConfig.getSyncDisabledMode() after test")
                .that(mDeviceConfig.getSyncDisabledMode())
                .isEqualTo(NONE);

        expectCalls(UNTIL_REBOOT, NONE);
    }

    @Test
    public final void testAnnotationsFromSubclassAreExecutedFirstAndInOrder_sdkSandboxFirst()
            throws Throwable {
        setSyncMode(PERSISTENT);
        setSdkSandboxState(DISABLED);
        Description test =
                newTestMethodForClassRule(
                        ASubClassEnablesSdkSandboxAndAlsoDisablesDeviceConfigUntilReboot.class);

        mRule.apply(mStatement, test).evaluate();

        expect.withMessage("deviceConfig.getSyncDisabledMode() on test")
                .that(mSyncModeOnTest.get())
                .isEqualTo(UNTIL_REBOOT);
        expect.withMessage("deviceConfig.getSyncDisabledMode() after test")
                .that(mDeviceConfig.getSyncDisabledMode())
                .isEqualTo(PERSISTENT);

        expect.withMessage("sdkSandbox.getState() on test")
                .that(mSdkSandboxStateOnTest.get())
                .isEqualTo(ENABLED);
        expect.withMessage("sdkSandbox.getState() after test")
                .that(mSdkSandbox.getState())
                .isEqualTo(DISABLED);

        expectCalls(ENABLED, UNTIL_REBOOT, PERSISTENT, DISABLED);
    }

    @Test
    public final void testAnnotationsFromSubclassAreExecutedFirstAndInOrder_deviceConfigFirst()
            throws Throwable {
        setSyncMode(PERSISTENT);
        setSdkSandboxState(DISABLED);
        Description test =
                newTestMethodForClassRule(
                        ASubClassDisablesDeviceConfigUntilRebootAndAlsoEnablesSdkSandbox.class);

        mRule.apply(mStatement, test).evaluate();

        expect.withMessage("deviceConfig.getSyncDisabledMode() on test")
                .that(mSyncModeOnTest.get())
                .isEqualTo(UNTIL_REBOOT);
        expect.withMessage("deviceConfig.getSyncDisabledMode() after test")
                .that(mDeviceConfig.getSyncDisabledMode())
                .isEqualTo(PERSISTENT);

        expect.withMessage("sdkSandbox.getState() on test")
                .that(mSdkSandboxStateOnTest.get())
                .isEqualTo(ENABLED);
        expect.withMessage("sdkSandbox.getState() after test")
                .that(mSdkSandbox.getState())
                .isEqualTo(DISABLED);

        // NOTE: ideally it should follow the annotations order and be:
        // UNTIL_REBOOT, ENABLED, DISABLED, PERSISTENT);
        // but we cannot guarantee the order (see comment on AbstractFlagsPreparerClassRule)
        expectCalls(ENABLED, UNTIL_REBOOT, PERSISTENT, DISABLED);
    }

    @Test
    public final void
            testAnnotationsFromSubclassWithInterfaceAreExecutedFirstAndInOrder_sdkSandboxFirst()
                    throws Throwable {
        setSyncMode(PERSISTENT);
        setSdkSandboxState(DISABLED);
        Description test =
                newTestMethodForClassRule(
                        ASubClassEnablesSdkSandboxAndAlsoDisablesDeviceConfigUntilReboot.class);

        mRule.apply(mStatement, test).evaluate();

        expect.withMessage("deviceConfig.getSyncDisabledMode() on test")
                .that(mSyncModeOnTest.get())
                .isEqualTo(UNTIL_REBOOT);
        expect.withMessage("deviceConfig.getSyncDisabledMode() after test")
                .that(mDeviceConfig.getSyncDisabledMode())
                .isEqualTo(PERSISTENT);

        expect.withMessage("sdkSandbox.getState() on test")
                .that(mSdkSandboxStateOnTest.get())
                .isEqualTo(ENABLED);
        expect.withMessage("sdkSandbox.getState() after test")
                .that(mSdkSandbox.getState())
                .isEqualTo(DISABLED);

        expectCalls(ENABLED, UNTIL_REBOOT, PERSISTENT, DISABLED);
    }

    @Test
    public final void testAnnotationsFromATypicalSubclass() throws Throwable {
        setSyncMode(NONE);

        // ATypicalSubclass doesn't declare any annotation, but its superclass sets sync mode as
        // persistent
        Description test = newTestMethodForClassRule(ATypicalSubclass.class);

        mRule.apply(mStatement, test).evaluate();

        expect.withMessage("deviceConfig.getSyncDisabledMode() on test")
                .that(mSyncModeOnTest.get())
                .isEqualTo(PERSISTENT);
        expect.withMessage("deviceConfig.getSyncDisabledMode() after test")
                .that(mDeviceConfig.getSyncDisabledMode())
                .isEqualTo(NONE);

        expect.withMessage("sdkSandbox.getState() on test")
                .that(mSdkSandboxStateOnTest.get())
                .isEqualTo(mSdkSandboxStateBefore);
        expect.withMessage("sdkSandbox.getState() after test")
                .that(mSdkSandbox.getState())
                .isEqualTo(mSdkSandboxStateBefore);

        expectCalls(PERSISTENT, NONE);
    }

    @Test
    public final void
            testAnnotationsFromATypicalSubclassThatImplementsAnInterfaceThatEnablesSdkSandbox()
                    throws Throwable {
        setSyncMode(NONE);
        setSdkSandboxState(DISABLED);

        // ATypicalSubclassThatImplementsAnInterfaceThatEnablesSdkSandbox doesn't declare any
        // annotation, but its superclass sets sync mode as persistent and the interface enables
        // SdkSandbox
        Description test =
                newTestMethodForClassRule(
                        ATypicalSubclassThatImplementsAnInterfaceThatEnablesSdkSandbox.class);

        mRule.apply(mStatement, test).evaluate();

        expect.withMessage("deviceConfig.getSyncDisabledMode() on test")
                .that(mSyncModeOnTest.get())
                .isEqualTo(PERSISTENT);
        expect.withMessage("deviceConfig.getSyncDisabledMode() after test")
                .that(mDeviceConfig.getSyncDisabledMode())
                .isEqualTo(NONE);

        expect.withMessage("sdkSandbox.getState() on test")
                .that(mSdkSandboxStateOnTest.get())
                .isEqualTo(ENABLED);
        expect.withMessage("sdkSandbox.getState() after test")
                .that(mSdkSandbox.getState())
                .isEqualTo(DISABLED);

        expectCalls(ENABLED, PERSISTENT, NONE, DISABLED);
    }

    /**
     * Asserts calls received by the test, in sequence.
     *
     * <p>Usually called with an even number of calls - the calls from execute() and the calls from
     * revert()
     */
    private void expectCalls(Object... calls) {
        List<Object> expectedCalls = new ArrayList<>(mSetupCalls);
        Arrays.stream(calls).forEach(call -> expectedCalls.add(call));

        expect.withMessage("calls").that(mCalls).containsExactlyElementsIn(expectedCalls).inOrder();
    }

    public final class MyDeviceConfigWrapper extends DeviceConfigWrapper {

        public MyDeviceConfigWrapper() {
            super(new Logger(DynamicLogger.getInstance(), "DeviceConfigWrapper"));
        }

        @Override
        public MyDeviceConfigWrapper setSyncDisabledMode(SyncDisabledModeForTest mode) {
            mCalls.add(mode);
            super.setSyncDisabledMode(mode);
            return this;
        }
    }

    public final class MySdkSandboxWrapper extends SdkSandboxWrapper {

        public MySdkSandboxWrapper() {
            super(new Logger(DynamicLogger.getInstance(), "SdkSandboxWrapper"));
        }

        @Override
        public MySdkSandboxWrapper setState(State state) {
            mCalls.add(state);
            super.setState(state);
            return this;
        }
    }

    private void setSyncMode(SyncDisabledModeForTest mode) throws Exception {
        assumeDeviceConfigSyncModeIsSupported();
        var action = new SetSyncModeAction(mFakeLogger, mDeviceConfig, mode);
        if (action.execute()) {
            mSetupCalls.add(mode);
        }
    }

    private void setSdkSandboxState(SdkSandbox.State state) throws Exception {
        assumeSdkSandboxStateIsSupported();
        var action = new SetSdkSandboxStateAction(mFakeLogger, mSdkSandbox, state);
        if (action.execute()) {
            mSetupCalls.add(state);
        }
    }

    private void assumeDeviceConfigSyncModeIsSupported() {
        assumeFalse(
                "initial device_config mode is " + mSyncModeBefore,
                SyncDisabledModeForTest.UNSUPPORTED.equals(mSyncModeBefore));
    }

    private void assumeSdkSandboxStateIsSupported() {
        assumeFalse(
                "initial sdk_sandbox state is " + mSdkSandboxStateBefore,
                SdkSandbox.State.UNSUPPORTED.equals(mSdkSandboxStateBefore));
    }
}
