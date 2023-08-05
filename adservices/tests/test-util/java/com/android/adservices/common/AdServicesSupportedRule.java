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

// TODO(b/284971005): rename to AdServicesEnabledRule
/**
 * Rule used to properly check a test behavior depending on whether {@code AdServices} is enabled in
 * the device.
 *
 * <p>Typical usage:
 *
 * <pre class="prettyprint">
 * &#064;Rule
 * public final AdServicesSupportedRule adServicesFeatureSupportedRule = AdServicesSupportedRule();
 * </pre>
 *
 * <p>In the example above, it assumes that every test should only be executed when the device
 * supports {@code AdServices} - if the device doesn't support it, the test will be skipped (with an
 * {@link AssumptionViolatedException}).
 *
 * <p>The rule can also be used to make sure APIs throw {@link IllegalStateException} when the
 * device doesn't support {@code AdServices}; in that case, you annotate the test method with {@link
 * RequiresAdServicesNotSupported}, then simply call the API that should throw the exception on its
 * body - the rule will make sure the exception is thrown (and fail the test if it isn't). Example:
 *
 * <pre class="prettyprint">
 * &#064;Test
 * &#064;RequiresAdServicesNotSupported
 * public void testFoo_notSupported() {
 *    mObjectUnderTest.foo();
 * }
 * </pre>
 *
 * <p>Even better if the same method can be used whether the {@code AdServices} is enabled or not:
 *
 * <pre class="prettyprint">
 * &#064;Test
 * &#064;RequiresAdServicesSupportedOrNot
 * public void testBar() {
 *    boolean foo = mObjectUnderTest.bar(); // should throw ISE when not supported
 *    assertThat(foo).isTrue();             // should pass when supported
 * }
 * </pre>
 */
public final class AdServicesSupportedRule extends AbstractSupportedFeatureRule {

    private static final AndroidLogger sLogger = new AndroidLogger(AdServicesSupportedRule.class);

    /** Creates a rule using {@link Mode#NOT_SUPPORTED_BY_DEFAULT}. */
    public AdServicesSupportedRule() {
        this(Mode.SUPPORTED_BY_DEFAULT);
    }

    /** Creates a rule with the given mode. */
    public AdServicesSupportedRule(Mode mode) {
        super(sLogger, mode);
    }

    @Override
    public boolean isFeatureSupported() throws Exception {
        boolean isSupported = !AdServicesSupportHelper.getGlobalKillSwitch();
        mLog.v("isFeatureSupported(): %b", isSupported);
        return isSupported;
    }

    @Override
    protected void throwFeatureNotSupportedAssumptionViolatedException() {
        throw new AssumptionViolatedException("AdServices disabled by global kill-switch");
    }

    @Override
    protected void throwFeatureSupportedAssumptionViolatedException() {
        throw new AssumptionViolatedException("AdServices enabled by globak kill-switch");
    }

    // TODO(b/284971005): let constructor / build specify the expected exception
    @Override
    protected void assertUnsupportedTestThrewRightException(Throwable thrown) {
        if (!(thrown instanceof IllegalStateException)
                && (thrown.getCause() instanceof IllegalStateException)) {
            return;
        }
        super.assertUnsupportedTestThrewRightException(thrown);
    }

    @Override
    protected boolean isFeatureSupportedAnnotation(Annotation annotation) {
        return annotation instanceof RequiresAdServicesSupported;
    }

    @Override
    protected boolean isFeatureNotSupportedAnnotation(Annotation annotation) {
        return annotation instanceof RequiresAdServicesNotSupported;
    }

    @Override
    protected boolean isFeatureSupportedOrNotAnnotation(Annotation annotation) {
        return annotation instanceof RequiresAdServicesSupportedOrNot;
    }

    /**
     * Annotation used to indicate that a test should only be run when the {@code AdServices}
     * feature is enabled in the device.
     *
     * <p>Typically used to override the behavior defined in the rule constructor or annotations
     * defined in the class / superclass.
     */
    @Retention(RUNTIME)
    @Target({TYPE, METHOD})
    public static @interface RequiresAdServicesSupported {}

    /**
     * Annotation used to indicate that a test should only be run when the {@code AdServices}
     * feature is NOT enabled in the device.
     *
     * <p>Typically used to override the behavior defined in the rule constructor or annotations
     * defined in the class / superclass.
     */
    @Retention(RUNTIME)
    @Target({TYPE, METHOD})
    public static @interface RequiresAdServicesNotSupported {}

    /**
     * Annotation used to indicate that a test should always run, whether the {@code AdServices}
     * feature is enabled or not.
     *
     * <p>Typically used to override the behavior defined in the rule constructor or annotations
     * defined in the class / superclass.
     */
    @Retention(RUNTIME)
    @Target({TYPE, METHOD})
    public static @interface RequiresAdServicesSupportedOrNot {}
}
