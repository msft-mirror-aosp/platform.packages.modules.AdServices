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
import static com.android.adservices.shared.testing.SdkSandbox.State.UNKNOWN;
import static com.android.adservices.shared.testing.SdkSandbox.State.UNSUPPORTED;
import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.NONE;
import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.PERSISTENT;
import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.UNTIL_REBOOT;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.adservices.shared.testing.SdkSandbox;
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
        extends SharedSidelessTestCase {

    private final AtomicReference<SyncDisabledModeForTest> mSyncModeOnTest =
            new AtomicReference<>();
    private final AtomicReference<SdkSandbox.State> mSdkSandboxStateOnTest =
            new AtomicReference<>();

    private DeviceConfig mDeviceConfig;
    private SdkSandbox mSdkSandbox;
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

    /** Creates a new, side-specific instance of the {@link DeviceConfig} implementation. */
    protected abstract DeviceConfig newDeviceConfig();

    /** Creates a new, side-specific instance of the {@link SdkSandbox} implementation. */
    protected abstract SdkSandbox newSdkSandbox();

    /** Creates a new, side-specific instance of the rule. */
    protected abstract R newRule();

    @Before
    public final void setFixtures() {
        mRule = newRule();
        if (mRule == null) {
            assertWithMessage("newRule() returned null").fail();
        }
        mDeviceConfig = newDeviceConfig();
        if (mDeviceConfig == null) {
            assertWithMessage("newDeviceConfig() returned null").fail();
        }
        mSdkSandbox = newSdkSandbox();
        if (mSdkSandbox == null) {
            assertWithMessage("newSdkSandbox() returned null").fail();
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
            if (mSyncModeBefore.isValid()) {
                mDeviceConfig.setSyncDisabledMode(mSyncModeBefore);
            }
        } finally {
            if (mSdkSandboxStateBefore.isValid()) {
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
    }

    @Test
    public final void testMethodAnnotation_setSdkSandboxStateEnabledAnnotation() throws Throwable {
        assumeFalse("sdk sandbox state unsupported", mSdkSandboxStateBefore.equals(UNSUPPORTED));

        SdkSandbox.State newState;
        SetSdkSandboxStateEnabledAnnotation annotation;
        if (mSdkSandboxStateBefore.equals(ENABLED)) {
            newState = DISABLED;
            annotation = new SetSdkSandboxStateEnabledAnnotation(false);
        } else {
            newState = ENABLED;
            annotation = new SetSdkSandboxStateEnabledAnnotation(true);
        }
        Description test = newTestMethodForClassRule(AClassHasNoNothingAtAll.class, annotation);

        mRule.apply(mStatement, test).evaluate();

        expect.withMessage("sdkSandbox.getState() on test")
                .that(mSdkSandboxStateOnTest.get())
                .isEqualTo(newState);
        expect.withMessage("sdkSandbox.getState() after test")
                .that(mSdkSandbox.getState())
                .isEqualTo(
                        mSdkSandboxStateBefore.equals(UNKNOWN) ? newState : mSdkSandboxStateBefore);
    }

    @Test
    public final void testMethodAnnotation_setSyncDisabledModeForTestAnnotation() throws Throwable {
        // TODO(b/297085722): remove this check once tests don't run on R anymore
        assumeFalse(
                "device config mode unsupported",
                mSyncModeBefore.equals(SyncDisabledModeForTest.UNSUPPORTED));

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
    }

    @Test
    public final void tesClassAnnotation_setSdkSandboxStateEnabled() throws Throwable {
        assumeTrue(
                "sdk sandbox state is not disabled before",
                mSdkSandboxStateBefore.equals(DISABLED));
        Description test = newTestMethodForClassRule(AClassEnablesSdkSandbox.class);

        mRule.apply(mStatement, test).evaluate();

        expect.withMessage("sdkSandbox.getState() on test")
                .that(mSdkSandboxStateOnTest.get())
                .isEqualTo(ENABLED);
        expect.withMessage("sdkSandbox.getState() after test")
                .that(mSdkSandbox.getState())
                .isEqualTo(mSdkSandboxStateBefore);
    }

    @Test
    public final void tesClassAnnotation_setSdkSandboxStateDisabled() throws Throwable {
        assumeTrue(
                "sdk sandbox state is not enabled before", mSdkSandboxStateBefore.equals(ENABLED));
        Description test = newTestMethodForClassRule(AClassDisablesSdkSandbox.class);

        mRule.apply(mStatement, test).evaluate();

        expect.withMessage("sdkSandbox.getState() on test")
                .that(mSdkSandboxStateOnTest.get())
                .isEqualTo(DISABLED);
        expect.withMessage("sdkSandbox.getState() after test")
                .that(mSdkSandbox.getState())
                .isEqualTo(mSdkSandboxStateBefore);
    }

    @Test
    public final void testClassAnnotation_setSyncDisabledModeForTestAnnotation_untilReboot()
            throws Throwable {
        assumeFalse(
                "device config mode is already UNTIL_REBOOT", mSyncModeBefore.equals(UNTIL_REBOOT));
        Description test = newTestMethodForClassRule(AClassDisablesDeviceConfigUntilReboot.class);

        mRule.apply(mStatement, test).evaluate();

        expect.withMessage("deviceConfig.getSyncDisabledMode() on test")
                .that(mSyncModeOnTest.get())
                .isEqualTo(UNTIL_REBOOT);
        expect.withMessage("deviceConfig.getSyncDisabledMode() after test")
                .that(mDeviceConfig.getSyncDisabledMode())
                .isEqualTo(mSyncModeBefore);
    }

    @Test
    public final void
            testClassAndMethodAnnotation_setSyncDisabledModeForTestAnnotation_untilRebootThenNone()
                    throws Throwable {
        assumeFalse(
                "device config mode is already UNTIL_REBOOT", mSyncModeBefore.equals(UNTIL_REBOOT));

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
