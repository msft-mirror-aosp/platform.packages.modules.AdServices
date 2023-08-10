/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.adservices.common;

import com.android.adservices.common.Logger.RealLogger;

import org.junit.AssumptionViolatedException;

import java.lang.annotation.Annotation;

// NOTE: this class is used by device and host side, so it cannot have any Android dependency
/**
 * Rule used to properly check a test behavior depending on whether the device supports {@code
 * AdService}.
 *
 * <p>Typical usage:
 *
 * <pre class="prettyprint">
 * &#064;Rule
 * public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
 *     new AdServicesDeviceSupportedRule();
 * </pre>
 *
 * <p>In the example above, it assumes that every test should only be executed when the device
 * supports {@code AdServices} - if the device doesn't support it, the test will be skipped (with an
 * {@link AssumptionViolatedException}).
 *
 * <p>The rule can also be used to make sure APIs throw {@link UnsupportedOperationException} when
 * the device doesn't support {@code AdServices}; in that case, you annotate the test method with
 * {@link RequiresDeviceNotSupported}, then simply call the API that should throw the exception on
 * its body - the rule will make sure the exception is thrown (and fail the test if it isn't).
 * Example:
 *
 * <pre class="prettyprint">
 * &#064;Test
 * &#064;RequiresDeviceNotSupported
 * public void testFoo_notSupported() {
 *    mObjectUnderTest.foo();
 * }
 * </pre>
 *
 * <p><b>NOTE: </b>this rule will mostly be used to skip test on unsupported platforms - if you want
 * to test that your API behaves correctly whether or not the global kill switch is disabled, you
 * most likely should use {@link GlobalKillSwitchRule} instead. In fact, there might be cases where
 * both rules are used, in which case it's recommended to run this one first (so the test is skipped
 * right away when not supported). Example:
 *
 * <pre class="prettyprint">
 * &#064;Rule(order = 0)
 * public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
 *     new AdServicesDeviceSupportedRule();
 *
 * &#064;Rule(order = 1)
 * public final GlobalKillSwitchRule globalKillSwitchRule = new GlobalKillSwitchRule();
 * </pre>
 *
 * <p>Generally speaking, you should organize the rules using the order of feature dependency. For
 * example, if the test also required a given SDK level:
 *
 * <pre class="prettyprint">
 * &#064;Rule(order = 0)
 * public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
 *     new AdServicesDeviceSupportedRule();
 *
 * &#064;Rule(order = 1)
 *   @Rule public final SdkLevelSupportRule sdkLevelRule = SdkLevelSupportRule.isAtLeastS();
 *
 * &#064;Rule(order = 2)
 * public final GlobalKillSwitchRule globalKillSwitchRule = new GlobalKillSwitchRule();
 * </pre>
 */
public abstract class AbstractAdServicesDeviceSupportedRule extends AbstractSupportedFeatureRule {

    protected AbstractAdServicesDeviceSupportedRule(RealLogger logger, Mode mode) {
        super(logger, mode);
    }

    @Override
    protected void throwFeatureNotSupportedAssumptionViolatedException() {
        throw new AssumptionViolatedException("Device doesn't support AdServices");
    }

    @Override
    protected void throwFeatureSupportedAssumptionViolatedException() {
        throw new AssumptionViolatedException("Device supports AdServices");
    }

    @Override
    protected void throwUnsupporteTestDidntThrowExpectedExceptionError() {
        throw new AssertionError(
                "test should have thrown an UnsupportedOperationException, but didn't throw any");
    }

    @Override
    protected void assertUnsupportedTestThrewRightException(Throwable thrown) {
        if (!(thrown instanceof UnsupportedOperationException)
                && (thrown.getCause() instanceof UnsupportedOperationException)) {
            return;
        }
        super.assertUnsupportedTestThrewRightException(thrown);
    }

    @Override
    protected boolean isFeatureSupportedAnnotation(Annotation annotation) {
        return annotation instanceof RequiresDeviceSupported;
    }

    @Override
    protected boolean isFeatureNotSupportedAnnotation(Annotation annotation) {
        return annotation instanceof RequiresDeviceNotSupported;
    }

    @Override
    protected boolean isFeatureSupportedOrNotAnnotation(Annotation annotation) {
        return annotation instanceof RequiresDeviceSupportedOrNot;
    }
}
