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

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import org.junit.AssumptionViolatedException;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

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
 * <p><b>NOTE: </b>this rule will mostly used to skip test on unsupported platforms - if you want to
 * test that your API checks if AdServices is enabled, you most likely need to use {@code
 * AdServicesSupportedRule} instead.
 */
public final class AdServicesDeviceSupportedRule extends AbstractSupportedFeatureRule {

    private static final AndroidLogger sLogger =
            new AndroidLogger(AdServicesDeviceSupportedRule.class);

    /** Creates a rule using {@link Mode#SUPPORTED_BY_DEFAULT}. */
    public AdServicesDeviceSupportedRule() {
        this(Mode.SUPPORTED_BY_DEFAULT);
    }

    /** Creates a rule with the given mode. */
    public AdServicesDeviceSupportedRule(Mode mode) {
        super(sLogger, mode);
    }

    @Override
    boolean isFeatureSupported() {
        boolean isSupported = AdServicesSupportHelper.isDeviceSupported();
        mLog.v("isFeatureSupported(): %b", isSupported);
        return isSupported;
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

    /**
     * Annotation used to indicate that a test should only be run when the device supports {@code
     * AdServices}.
     *
     * <p>Typically used to override the behavior defined in the rule constructor or annotations
     * defined in the class / superclass.
     */
    @Retention(RUNTIME)
    @Target({TYPE, METHOD})
    public static @interface RequiresDeviceSupported {}

    /**
     * Annotation used to indicate that a test should only be run when the device does NOT support
     * {@code AdServices}, and that the test should throw a {@link UnsupportedOperationException}.
     *
     * <p>Typically used to override the behavior defined in the rule constructor or annotations
     * defined in the class / superclass.
     */
    @Retention(RUNTIME)
    @Target({TYPE, METHOD})
    public static @interface RequiresDeviceNotSupported {}

    /**
     * Annotation used to indicate that a test should always run, whether the device supports {@code
     * AdServices} or not.
     *
     * <p>Typically used to override the behavior defined in the rule constructor or annotations
     * defined in the class / superclass.
     */
    @Retention(RUNTIME)
    @Target({TYPE, METHOD})
    public static @interface RequiresDeviceSupportedOrNot {}
}
