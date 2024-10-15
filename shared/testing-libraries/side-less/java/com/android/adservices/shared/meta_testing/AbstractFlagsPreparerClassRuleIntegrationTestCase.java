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
import static org.junit.Assume.assumeTrue;

import com.android.adservices.shared.testing.DynamicLogger;
import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.SdkSandbox;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.annotations.RequiresSdkRange;
import com.android.adservices.shared.testing.annotations.SetSdkSandboxStateEnabled;
import com.android.adservices.shared.testing.annotations.SetSyncDisabledModeForTest;
import com.android.adservices.shared.testing.device.DeviceConfig;
import com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest;
import com.android.adservices.shared.testing.flags.AbstractFlagsPreparerClassRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;

import java.lang.annotation.Annotation;
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

    // TODO(b/361771436): remove after final refactorings
    private static final boolean CHECK_ORDER = false;

    private final AtomicReference<SyncDisabledModeForTest> mSyncModeOnTest =
            new AtomicReference<>();
    private final AtomicReference<SdkSandbox.State> mSdkSandboxStateOnTest =
            new AtomicReference<>();

    private final List<Object> mCalls = new ArrayList<>();
    // mCalls is used to keep track of the relevant calls made to the objects below
    private final MyDeviceConfigWrapper mDeviceConfig = new MyDeviceConfigWrapper();
    private final SdkSandboxWrapper mSdkSandbox = new MySdkSandboxWrapper();

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
        assumeInitialSdkSandboxStateIsNot(SdkSandbox.State.UNSUPPORTED);

        Description test =
                newTestMethodForClassRule(
                        AClassHasNoNothingAtAll.class,
                        new SetSdkSandboxStateEnabledAnnotation(true));

        mRule.apply(mStatement, test).evaluate();

        expect.withMessage("sdkSandbox.getState() on test")
                .that(mSdkSandboxStateOnTest.get())
                .isEqualTo(ENABLED);
        expect.withMessage("sdkSandbox.getState() after test")
                .that(mSdkSandbox.getState())
                .isEqualTo(DISABLED);

        expectCalls(ENABLED, DISABLED);
    }

    @Test
    public final void testMethodAnnotation_setSdkSandboxStateDisabledAnnotation() throws Throwable {
        assumeDeviceConfigSyncModeIsSupportedAndInitialModeIsNot(NONE);
        assumeSdkSandboxStateIsSupportedAndInitialStateIs(ENABLED);

        Description test =
                newTestMethodForClassRule(
                        AClassHasNoNothingAtAll.class,
                        new SetSdkSandboxStateEnabledAnnotation(false));

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
    @RequiresSdkLevelAtLeastS(reason = "device_config on R doesn't support SyncDisabledModeForTest")
    public final void testMethodAnnotation_setSyncDisabledModeForTestAnnotation() throws Throwable {
        SyncDisabledModeForTest newMode = mSyncModeBefore.equals(NONE) ? PERSISTENT : NONE;
        Description test =
                newTestMethodForClassRule(
                        AClassHasNoNothingAtAll.class,
                        new SetSyncDisabledModeForTestAnnotation(newMode));

        mRule.apply(mStatement, test).evaluate();

        expect.withMessage("deviceConfig.getSyncDisabledMode() on test")
                .that(mSyncModeOnTest.get())
                .isEqualTo(newMode);
        expect.withMessage("deviceConfig.getSyncDisabledMode() after test")
                .that(mDeviceConfig.getSyncDisabledMode())
                .isEqualTo(mSyncModeBefore);

        expectCalls(newMode, mSyncModeBefore);
    }

    @Test
    public final void testClassAnnotation_setSdkSandboxStateEnabled() throws Throwable {
        assumeSdkSandboxStateIsSupportedAndInitialStateIs(DISABLED);
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
        assumeSdkSandboxStateIsSupportedAndInitialStateIs(ENABLED);
        Description test = newTestMethodForClassRule(AClassDisablesSdkSandbox.class);

        mRule.apply(mStatement, test).evaluate();

        expect.withMessage("sdkSandbox.getState() on test")
                .that(mSdkSandboxStateOnTest.get())
                .isEqualTo(DISABLED);
        expect.withMessage("sdkSandbox.getState() after test")
                .that(mSdkSandbox.getState())
                .isEqualTo(mSdkSandboxStateBefore);

        expectCalls(DISABLED, mSdkSandboxStateBefore);
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
        assumeDeviceConfigSyncModeIsSupportedAndInitialModeIsNot(UNTIL_REBOOT);
        Description test = newTestMethodForClassRule(AClassDisablesDeviceConfigUntilReboot.class);

        mRule.apply(mStatement, test).evaluate();

        expect.withMessage("deviceConfig.getSyncDisabledMode() on test")
                .that(mSyncModeOnTest.get())
                .isEqualTo(expectedModeAfterSet);
        expect.withMessage("deviceConfig.getSyncDisabledMode() after test")
                .that(mDeviceConfig.getSyncDisabledMode())
                .isEqualTo(mSyncModeBefore);

        expectCalls(UNTIL_REBOOT, mSyncModeBefore);
    }
    @Test
    public final void
            testClassAndMethodAnnotation_setSyncDisabledModeForTestAnnotation_untilRebootThenNone()
                    throws Throwable {
        assumeDeviceConfigSyncModeIsSupportedAndInitialModeIsNot(UNTIL_REBOOT, NONE);

        // NOTE: ideally it should test PERSISTENT, but somehow tradefed is setting that mode before
        // the test is executed
        Description test =
                newTestMethodForClassRule(
                        AClassDisablesDeviceConfigUntilReboot.class,
                        new SetSyncDisabledModeForTestAnnotation(NONE));

        mRule.apply(mStatement, test).evaluate();

        expect.withMessage("deviceConfig.getSyncDisabledMode() on test")
                .that(mSyncModeOnTest.get())
                .isEqualTo(NONE);
        expect.withMessage("deviceConfig.getSyncDisabledMode() after test")
                .that(mDeviceConfig.getSyncDisabledMode())
                .isEqualTo(mSyncModeBefore);

        expectCalls(NONE, mSyncModeBefore);
    }

    @Test
    public final void testAnnotationOrderPrevails() throws Throwable {
        assumeDeviceConfigSyncModeIsSupportedAndInitialModeIsNot(NONE);
        assumeSdkSandboxStateIsSupportedAndInitialStateIs(DISABLED);

        Description test =
                newTestMethodForClassRule(
                        AClassHasNoNothingAtAll.class,
                        new SetSdkSandboxStateEnabledAnnotation(true),
                        new SetSyncDisabledModeForTestAnnotation(NONE));

        mRule.setSyncDisabledModeForTest(PERSISTENT);
        mRule.setSdkSandboxState(false);

        mRule.apply(mStatement, test).evaluate();

        expect.withMessage("deviceConfig.getSyncDisabledMode() on test")
                .that(mSyncModeOnTest.get())
                .isEqualTo(NONE);
        expect.withMessage("deviceConfig.getSyncDisabledMode() after test")
                .that(mDeviceConfig.getSyncDisabledMode())
                .isEqualTo(mSyncModeBefore);

        expect.withMessage("sdkSandbox.getState() on test")
                .that(mSdkSandboxStateOnTest.get())
                .isEqualTo(ENABLED);
        expect.withMessage("sdkSandbox.getState() after test")
                .that(mSdkSandbox.getState())
                .isEqualTo(DISABLED);

        expectCalls(ENABLED, NONE, mSyncModeBefore, DISABLED);
    }

    private void expectCalls(Object... calls) {
        if (!CHECK_ORDER) {
            mLog.w("expectCalls(%s): temporarily ignoring", Arrays.toString(calls));
            return;
        }
        expect.withMessage("calls").that(mCalls).containsExactly(calls).inOrder();
    }

    public final class MyDeviceConfigWrapper extends DeviceConfigWrapper {

        public MyDeviceConfigWrapper() {
            super(new Logger(DynamicLogger.getInstance(), "DeviceConfigWrapper"));
        }

        @Override
        public void setSyncDisabledMode(SyncDisabledModeForTest mode) {
            mCalls.add(mode);
            super.setSyncDisabledMode(mode);
        }
    }

    public final class MySdkSandboxWrapper extends SdkSandboxWrapper {

        public MySdkSandboxWrapper() {
            super(new Logger(DynamicLogger.getInstance(), "SdkSandboxWrapper"));
        }

        @Override
        public void setState(State state) {
            mCalls.add(state);
            super.setState(state);
        }
    }

    private void assumeDeviceConfigSyncModeIsSupportedAndInitialModeIsNot(
            SyncDisabledModeForTest... modes) {
        assumeInitialDeviceConfigSyncModeIsNot(SyncDisabledModeForTest.UNSUPPORTED);
        for (var mode : modes) {
            assumeInitialDeviceConfigSyncModeIsNot(mode);
        }
    }

    private void assumeInitialDeviceConfigSyncModeIsNot(SyncDisabledModeForTest mode) {
        assumeFalse("initial device_config mode is " + mode, mSyncModeBefore.equals(mode));
    }

    private void assumeSdkSandboxStateIsSupportedAndInitialStateIs(SdkSandbox.State state) {
        assumeInitialSdkSandboxStateIsNot(SdkSandbox.State.UNSUPPORTED);
        assumeInitialSdkSandboxStateIs(state);
    }

    private void assumeInitialSdkSandboxStateIsNot(SdkSandbox.State state) {
        assumeFalse("initial sdk_sandbox state is " + state, mSdkSandboxStateBefore.equals(state));
    }

    private void assumeInitialSdkSandboxStateIs(SdkSandbox.State state) {
        assumeTrue(
                "initial sdk_sandbox state is not " + state, mSdkSandboxStateBefore.equals(state));
    }

    // Needs to create a test suite with a child to emulate running as a classRule
    private static Description newTestMethodForClassRule(
            Class<?> clazz, Annotation... annotations) {
        Description child = Description.createTestDescription(clazz, "butItHasATest");

        Description suite = Description.createSuiteDescription(clazz, annotations);
        suite.addChild(child);

        return suite;
    }

    // Classes used to create the Description fixtures

    private static class AClassHasNoNothingAtAll {}

    @SetSdkSandboxStateEnabled(true)
    private static class AClassEnablesSdkSandbox {}

    @SetSdkSandboxStateEnabled(false)
    private static class AClassDisablesSdkSandbox {}

    @SetSyncDisabledModeForTest(UNTIL_REBOOT)
    private static class AClassDisablesDeviceConfigUntilReboot {}
}
