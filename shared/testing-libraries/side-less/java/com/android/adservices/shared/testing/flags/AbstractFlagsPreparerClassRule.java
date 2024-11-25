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

import com.android.adservices.shared.testing.Action;
import com.android.adservices.shared.testing.ActionBasedRule;
import com.android.adservices.shared.testing.Logger.RealLogger;
import com.android.adservices.shared.testing.SdkSandbox;
import com.android.adservices.shared.testing.SetSdkSandboxStateAction;
import com.android.adservices.shared.testing.TestHelper;
import com.android.adservices.shared.testing.annotations.SetSdkSandboxStateEnabled;
import com.android.adservices.shared.testing.annotations.SetSyncDisabledModeForTest;
import com.android.adservices.shared.testing.device.DeviceConfig;
import com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A rule used to prepare the Flags before a test class is executed.
 *
 * <p>For now it's just setting the {@link SyncDisabledModeForTest}, but in the future it will be
 * extended to perform more actions like clearing all the flags.
 *
 * @param <R> concrete rule class
 */
public abstract class AbstractFlagsPreparerClassRule<R extends AbstractFlagsPreparerClassRule<R>>
        extends ActionBasedRule<R> {

    private final DeviceConfig mDeviceConfig;
    private final SdkSandbox mSdkSandbox;

    protected AbstractFlagsPreparerClassRule(
            RealLogger logger, SdkSandbox sdkSandbox, DeviceConfig deviceConfig) {
        super(logger);

        mDeviceConfig = Objects.requireNonNull(deviceConfig, "deviceConfig cannot be null");
        mSdkSandbox = Objects.requireNonNull(sdkSandbox, "sdkSandbox cannot be null");
    }

    @Override
    protected final ImmutableList<Action> createActionsForTest(
            Statement base, Description description) {
        TestHelper.throwIfTest(description);

        return createActionsForTest(description);
    }

    /** Checkstyle, do you feel happy now, punk? */
    @VisibleForTesting
    public final ImmutableList<Action> createActionsForTest(Description description) {
        List<Action> actions = new ArrayList<Action>();

        // This rule can only be used as class rule, but the annotations below could be set on
        // methods as well (as they could be used by FlagSetter), so the code below must explicitly
        // start from the annotations from the class, not from the description
        var testClass = description.getTestClass();

        // NOTE: ideally we should add a new helper method (on TestHelper, ActionBasedRule, or
        // another class) to convert annotations into Actions, but apparently Class.getAnnotations()
        // doesn't guarantee the order, and we need to make sure SetSdkSandboxStateEnabled is
        // applied before SetSyncDisabledModeForTest (as that was the case on AndroidText.xml prior
        // to the rule conversion), so we're explicitly checking each annotation in order (but it
        // might still be useful to use such helper on FlagsSetter, when it's refactored)
        var setSdkSandboxStateEnabledAnnotation =
                TestHelper.getAnnotationFromTypesOnly(testClass, SetSdkSandboxStateEnabled.class);
        if (setSdkSandboxStateEnabledAnnotation != null) {
            mLog.d("Found %s", setSdkSandboxStateEnabledAnnotation);
            actions.add(
                    new SetSdkSandboxStateAction(
                            mLog,
                            mSdkSandbox,
                            setSdkSandboxStateEnabledAnnotation.value()
                                    ? SdkSandbox.State.ENABLED
                                    : SdkSandbox.State.DISABLED));
        }
        var setSyncDisabledModeForTestAnnotation =
                TestHelper.getAnnotationFromTypesOnly(testClass, SetSyncDisabledModeForTest.class);
        if (setSyncDisabledModeForTestAnnotation != null) {
            mLog.d("Found %s", setSyncDisabledModeForTestAnnotation);
            actions.add(
                    new SetSyncModeAction(
                            mLog, mDeviceConfig, setSyncDisabledModeForTestAnnotation.value()));
        }
        return ImmutableList.copyOf(actions);
    }

    @Override
    protected void decorateToString(StringBuilder string) {
        super.decorateToString(string);

        string.append(", mSdkSandbox=")
                .append(mSdkSandbox)
                .append(", mDeviceConfig=")
                .append(mDeviceConfig);
    }
}
