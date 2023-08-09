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
package android.app.sdksandbox.testutils;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.android.adservices.common.AbstractSupportedFeatureRule;
import com.android.adservices.common.AbstractSupportedFeatureRule.Mode;
import com.android.adservices.common.AdServicesSupportHelper;
import com.android.adservices.common.AndroidLogger;

import org.junit.AssumptionViolatedException;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Rule used to properly check a test behavior depending on whether the device supports {@code
 * SdkSandbox}.
 *
 * <p>See {@link com.android.adservices.common.AdServicesDeviceSupportedRule} for usage - this rule
 * is analogous to it.
 */
public final class SdkSandboxDeviceSupportedRule extends AbstractSupportedFeatureRule {

    private static final AndroidLogger sLogger =
            new AndroidLogger(SdkSandboxDeviceSupportedRule.class);

    /** Creates a rule using {@link Mode#SUPPORTED_BY_DEFAULT}. */
    public SdkSandboxDeviceSupportedRule() {
        this(Mode.SUPPORTED_BY_DEFAULT);
    }

    /** Creates a rule with the given mode. */
    public SdkSandboxDeviceSupportedRule(Mode mode) {
        super(sLogger, mode);
    }

    @Override
    public boolean isFeatureSupported() {
        boolean isSupported = AdServicesSupportHelper.isDeviceSupported();
        mLog.v("isFeatureSupported(): %b", isSupported);
        return isSupported;
    }

    @Override
    protected void throwUnsupporteTestDidntThrowExpectedExceptionError() {
        throw new AssertionError(
                "test should have thrown an UnsupportedOperationException, but didn't throw any");
    }

    @Override
    protected void throwFeatureNotSupportedAssumptionViolatedException() {
        throw new AssumptionViolatedException("Device doesn't support SdkSandbox");
    }

    @Override
    protected void throwFeatureSupportedAssumptionViolatedException() {
        throw new AssumptionViolatedException("Device supports SdkSandbox");
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
        return annotation instanceof RequiresSdkSandboxSupportedOnDevice;
    }

    @Override
    protected boolean isFeatureNotSupportedAnnotation(Annotation annotation) {
        return annotation instanceof RequiresSdkSandboxNotSupportedOnDevice;
    }

    @Override
    protected boolean isFeatureSupportedOrNotAnnotation(Annotation annotation) {
        return annotation instanceof RequiresSdkSandboxSupportedOrNotOnDevice;
    }

    /**
     * Annotation used to indicate that a test should only be run when the device supports {@code
     * SdkSandbox}.
     *
     * <p>Typically used to override the behavior defined in the rule constructor or annotations
     * defined in the class / superclass.
     */
    @Retention(RUNTIME)
    @Target({TYPE, METHOD})
    public static @interface RequiresSdkSandboxSupportedOnDevice {}

    /**
     * Annotation used to indicate that a test should only be run when the device does NOT support
     * {@code SdkSandbox}, and that the test should throw a {@link UnsupportedOperationException}.
     *
     * <p>Typically used to override the behavior defined in the rule constructor or annotations
     * defined in the class / superclass.
     */
    @Retention(RUNTIME)
    @Target({TYPE, METHOD})
    public static @interface RequiresSdkSandboxNotSupportedOnDevice {}

    /**
     * Annotation used to indicate that a test should always run, whether the device supports {@code
     * SdkSandbox} or not.
     *
     * <p>Typically used to override the behavior defined in the rule constructor or annotations
     * defined in the class / superclass.
     */
    @Retention(RUNTIME)
    @Target({TYPE, METHOD})
    public static @interface RequiresSdkSandboxSupportedOrNotOnDevice {}
}
