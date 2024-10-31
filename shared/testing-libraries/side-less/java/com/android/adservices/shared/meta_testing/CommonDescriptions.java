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

import static com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest.UNTIL_REBOOT;

import com.android.adservices.shared.testing.annotations.SetSdkSandboxStateEnabled;
import com.android.adservices.shared.testing.annotations.SetSyncDisabledModeForTest;

import org.junit.runner.Description;

import java.lang.annotation.Annotation;

/** Provides common test descriptions and helpers for tests that test JUnit stuff. */
public final class CommonDescriptions {

    private CommonDescriptions() {
        throw new UnsupportedOperationException("provides only static methods");
    }

    /** Creates a description that emulates a test that can only be used in a class rule context. */
    public static Description newTestMethodForClassRule(Class<?> clazz, Annotation... annotations) {
        Description child = Description.createTestDescription(clazz, "butItHasATest");
        Description suite = Description.createSuiteDescription(clazz, annotations);
        suite.addChild(child);
        return suite;
    }

    public static class AClassHasNoNothingAtAll {}

    @SetSdkSandboxStateEnabled(true)
    public static class AClassEnablesSdkSandbox {}

    @SetSdkSandboxStateEnabled(false)
    public static class AClassDisablesSdkSandbox {}

    @SetSyncDisabledModeForTest(UNTIL_REBOOT)
    public static class AClassDisablesDeviceConfigUntilReboot {}

    @SetSyncDisabledModeForTest
    public static class AClassWithDefaultSetSyncDisabledModeForTest {}

    @SetSdkSandboxStateEnabled
    @SetSyncDisabledModeForTest(UNTIL_REBOOT)
    public static class AClassEnablesSdkSandboxAndDisablesDeviceConfigUntilReboot {}

    @SetSdkSandboxStateEnabled(true)
    @SetSyncDisabledModeForTest(UNTIL_REBOOT)
    public static class ASubClassEnablesSdkSandboxAndAlsoDisablesDeviceConfigUntilReboot
            extends AClassDisablesDeviceConfigUntilReboot {}

    @SetSyncDisabledModeForTest(UNTIL_REBOOT)
    @SetSdkSandboxStateEnabled(true)
    public static class ASubClassDisablesDeviceConfigUntilRebootAndAlsoEnablesSdkSandbox
            extends AClassDisablesDeviceConfigUntilReboot {}

    /** A "typical" superclass, like a "root" CTS class" - it only disables sync mode. */
    @SetSyncDisabledModeForTest
    public static class ATypicalSuperClass {}

    /** A "typical" subclass - it extends superclass and don't have any annotation. */
    public static class ATypicalSubclass extends ATypicalSuperClass {}

    @SetSdkSandboxStateEnabled
    public interface AnInterfaceEnablesSdkSandbox {}

    public static class ATypicalSubclassThatImplementsAnInterfaceThatEnablesSdkSandbox
            extends ATypicalSuperClass implements AnInterfaceEnablesSdkSandbox {}
}
