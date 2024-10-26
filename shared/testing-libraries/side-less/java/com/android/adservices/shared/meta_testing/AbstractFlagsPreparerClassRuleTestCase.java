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
import static com.android.adservices.shared.testing.SdkSandbox.State.DISABLED;
import static com.android.adservices.shared.testing.SdkSandbox.State.ENABLED;
import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.NONE;
import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.UNTIL_REBOOT;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.meta_testing.CommonDescriptions.AClassDisablesDeviceConfigUntilReboot;
import com.android.adservices.shared.meta_testing.CommonDescriptions.AClassEnablesSdkSandbox;
import com.android.adservices.shared.meta_testing.CommonDescriptions.AClassEnablesSdkSandboxAndDisablesDeviceConfigUntilReboot;
import com.android.adservices.shared.meta_testing.CommonDescriptions.AClassHasNoNothingAtAll;
import com.android.adservices.shared.meta_testing.CommonDescriptions.ASubClassDisablesDeviceConfigUntilRebootAndAlsoEnablesSdkSandbox;
import com.android.adservices.shared.meta_testing.CommonDescriptions.ASubClassEnablesSdkSandboxAndAlsoDisablesDeviceConfigUntilReboot;
import com.android.adservices.shared.testing.Action;
import com.android.adservices.shared.testing.SdkSandbox;
import com.android.adservices.shared.testing.SetSdkSandboxStateAction;
import com.android.adservices.shared.testing.device.DeviceConfig;
import com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest;
import com.android.adservices.shared.testing.flags.AbstractFlagsPreparerClassRule;
import com.android.adservices.shared.testing.flags.SetSyncModeAction;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Base class for {@link AbstractFlagsPreparerClassRule} implementations.
 *
 * <p>It contains the base tests for all of them, although subclasses might add they extra tests.
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

    private final FakeDeviceConfig mFakeDeviceConfig =
            new FakeDeviceConfig().setSyncDisabledMode(NONE);
    private final FakeSdkSandbox mFakeSdkSandbox = new FakeSdkSandbox().setState(DISABLED);

    @Before
    public final void setTestFixtures() {
        mSuite.addChild(mTest);
    }

    /** Gets a new concrete implementation of the rule. */
    protected abstract R newRule(SdkSandbox sdkSandbox, DeviceConfig deviceConfig);

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
        var rule = newRule(mFakeSdkSandbox, mFakeDeviceConfig);

        assertThrows(IllegalStateException.class, () -> runRule(rule, mTest));
    }

    @Test
    public final void testCreateActionsForTest_annotationOnMethodIsIgnored() {
        var rule = newRule(mFakeSdkSandbox, mFakeDeviceConfig);

        var actions =
                rule.createActionsForTest(
                        newTestMethodForClassRule(
                                AClassHasNoNothingAtAll.class,
                                new SetSdkSandboxStateEnabledAnnotation(false)));

        expect.withMessage("actions").that(actions).isEmpty();
    }

    @Test
    public final void testCreateActionsForTest_classOnly_deviceConfigMode() {
        var rule = newRule(mFakeSdkSandbox, mFakeDeviceConfig);

        var actions =
                rule.createActionsForTest(
                        newTestMethodForClassRule(AClassDisablesDeviceConfigUntilReboot.class));

        expect.withMessage("actions").that(actions).hasSize(1);

        assertSetSyncModeAction(actions, 0, UNTIL_REBOOT);
    }

    @Test
    public final void testCreateActionsForTest_classOnly_sdkSandboxState() {
        var rule = newRule(mFakeSdkSandbox, mFakeDeviceConfig);

        var actions =
                rule.createActionsForTest(newTestMethodForClassRule(AClassEnablesSdkSandbox.class));

        expect.withMessage("actions").that(actions).hasSize(1);

        assertSdkSandboxStateAction(actions, 0, ENABLED);
    }

    @Test
    public final void testCreateActionsForTest_classOnly_deviceConfigModeAndsdkSandboxState() {
        var rule = newRule(mFakeSdkSandbox, mFakeDeviceConfig);

        var actions =
                rule.createActionsForTest(
                        newTestMethodForClassRule(
                                AClassEnablesSdkSandboxAndDisablesDeviceConfigUntilReboot.class));

        expect.withMessage("actions").that(actions).hasSize(2);

        assertSdkSandboxStateAction(actions, 0, ENABLED);
        assertSetSyncModeAction(actions, 1, UNTIL_REBOOT);
    }

    @Test
    public final void testCreateActionsForTest_classOnly_sdkSandboxStateAnddeviceConfigMode() {
        var rule = newRule(mFakeSdkSandbox, mFakeDeviceConfig);

        var actions =
                rule.createActionsForTest(
                        newTestMethodForClassRule(
                                ASubClassEnablesSdkSandboxAndAlsoDisablesDeviceConfigUntilReboot
                                        .class));

        expect.withMessage("actions").that(actions).hasSize(2);

        assertSdkSandboxStateAction(actions, 0, ENABLED);
        assertSetSyncModeAction(actions, 1, UNTIL_REBOOT);
    }

    @Test
    public final void testCreateActionsForTest_classAndSuperClass_sdkSandboxFirst() {
        var rule = newRule(mFakeSdkSandbox, mFakeDeviceConfig);

        var actions =
                rule.createActionsForTest(
                        newTestMethodForClassRule(
                                ASubClassEnablesSdkSandboxAndAlsoDisablesDeviceConfigUntilReboot
                                        .class));

        expect.withMessage("actions").that(actions).hasSize(2);

        assertSdkSandboxStateAction(actions, 0, ENABLED);
        assertSetSyncModeAction(actions, 1, UNTIL_REBOOT);
    }

    @Test
    public final void testCreateActionsForTest_classAndSuperClass_deviceConfigFirst() {
        var rule = newRule(mFakeSdkSandbox, mFakeDeviceConfig);

        var actions =
                rule.createActionsForTest(
                        newTestMethodForClassRule(
                                ASubClassDisablesDeviceConfigUntilRebootAndAlsoEnablesSdkSandbox
                                        .class));

        expect.withMessage("actions").that(actions).hasSize(2);

        assertSdkSandboxStateAction(actions, 0, ENABLED);
        assertSetSyncModeAction(actions, 1, UNTIL_REBOOT);
    }

    // Methods above test all - or most - annotation combinations on createActionsForTest(), but
    // they don't assert the actions were executed (as it relies on the fact that the rule extends
    // ActionBasedRule); this test is a simple "Integration" test to make sure they are
    @Test
    public final void testFullWorkflow() throws Throwable {
        AtomicReference<SyncDisabledModeForTest> modeSetDuringTest = new AtomicReference<>();
        AtomicReference<SdkSandbox.State> stateSetDuringTest = new AtomicReference<>();
        mTestBody.onEvaluate(
                () -> {
                    modeSetDuringTest.set(mFakeDeviceConfig.getSyncDisabledMode());
                    stateSetDuringTest.set(mFakeSdkSandbox.getState());
                });
        var rule = newRule(mFakeSdkSandbox, mFakeDeviceConfig);

        runRule(
                rule,
                newTestMethodForClassRule(
                        ASubClassDisablesDeviceConfigUntilRebootAndAlsoEnablesSdkSandbox.class));

        expect.withMessage("DeviceConfig mode during test")
                .that(modeSetDuringTest.get())
                .isEqualTo(UNTIL_REBOOT);
        expect.withMessage("SDK state during test")
                .that(stateSetDuringTest.get())
                .isEqualTo(ENABLED);
        expect.withMessage("DeviceConfig mode after test")
                .that(mFakeDeviceConfig.getSyncDisabledMode())
                .isEqualTo(NONE);
        expect.withMessage("SDK state after test")
                .that(mFakeSdkSandbox.getState())
                .isEqualTo(DISABLED);
    }

    private void runRule(R rule, Description description) throws Throwable {
        rule.apply(mTestBody, description).evaluate();
    }

    private void assertSetSyncModeAction(
            List<Action> actions, int index, SyncDisabledModeForTest expectedMode) {
        var action = actions.get(index);
        if (!(action instanceof SetSyncModeAction)) {
            expect.withMessage("action#%s (from %s) is not SetSyncModeAction", index, action)
                    .fail();
            return;
        }
        SetSyncModeAction castAction = (SetSyncModeAction) action;
        expect.withMessage("mode on action#%s ", index)
                .that(castAction.getMode())
                .isEqualTo(UNTIL_REBOOT);
    }

    private void assertSdkSandboxStateAction(
            List<Action> actions, int index, SdkSandbox.State expectedState) {
        var action = actions.get(index);
        if (!(action instanceof SetSdkSandboxStateAction)) {
            expect.withMessage("action#%s (from %s) is not SetSdkSandboxStateAction", index, action)
                    .fail();
            return;
        }
        SetSdkSandboxStateAction castAction = (SetSdkSandboxStateAction) action;
        expect.withMessage("state on action#%s ", index)
                .that(castAction.getState())
                .isEqualTo(expectedState);
    }
}
