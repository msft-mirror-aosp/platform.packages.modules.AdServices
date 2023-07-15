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
import java.util.function.Supplier;

/**
 * Rule used to properly check a test behavior depending on whether the device supports a certain
 * SDK level constraint.
 */
public final class SdkLevelSupportRule extends AbstractSupportedFeatureRule {
    private static final String TAG = SdkLevelSupportRule.class.getSimpleName();
    private final Supplier<Boolean> mSdkLevelConstraint;

    public SdkLevelSupportRule(Supplier<Boolean> sdkLevelConstraint) {
        this(Mode.SUPPORTED_BY_DEFAULT, sdkLevelConstraint);
    }

    public SdkLevelSupportRule(Mode mode, Supplier<Boolean> sdkLevelConstraint) {
        super(mode, TAG);
        this.mSdkLevelConstraint = sdkLevelConstraint;
    }

    @Override
    boolean isFeatureSupported() {
        return mSdkLevelConstraint.get();
    }

    @Override
    protected void throwFeatureNotSupportedAVE() {
        throw new AssumptionViolatedException("Device doesn't support desired SDK level.");
    }

    @Override
    protected void throwFeatureSupportedAVE() {
        throw new AssumptionViolatedException("Device supports SDK Level.");
    }

    @Override
    protected boolean isFeatureSupportedAnnotation(Annotation annotation) {
        return annotation instanceof SdkLevelSupportRule.RequiresSdkLevelSupported;
    }

    @Override
    protected boolean isFeatureNotSupportedAnnotation(Annotation annotation) {
        return annotation instanceof SdkLevelSupportRule.RequiresSdkLevelNotSupported;
    }

    /**
     * Annotation used to indicate that a test should only be run when the device supports a
     * particular SDK level constraint.
     *
     * <p>Typically used when the rule was created with {@link Mode#NOT_SUPPORTED_BY_DEFAULT} or
     * {@link Mode#ANNOTATION_ONLY}.
     */
    @Retention(RUNTIME)
    @Target({TYPE, METHOD})
    public static @interface RequiresSdkLevelSupported {}

    /**
     * Annotation used to indicate that a test should only be run when the device does NOT support a
     * SDK level constraint, and that the test should throw a {@link UnsupportedOperationException}.
     *
     * <p>Typically used when the rule was created with {@link Mode#SUPPORTED_BY_DEFAULT} (which is
     * also the rule's default behavior) or {@link Mode#ANNOTATION_ONLY}.
     */
    @Retention(RUNTIME)
    @Target({TYPE, METHOD})
    public static @interface RequiresSdkLevelNotSupported {}
}
