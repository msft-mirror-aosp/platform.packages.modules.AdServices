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

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

// TODO(b/284971005): move to module-utils
// TODO(b/284971005): improve javadoc / add examples
// TODO(b/284971005): add unit tests
/**
 * Rule used to properly check a test behavior depending on whether the device supports a given
 * feature.
 *
 * <p>This rule is abstract so subclass can define what a "feature" means. It also doesn't have any
 * dependency on Android code, so it can be used both on device-side and host-side tests.
 */
public abstract class AbstractSupportedFeatureRule implements TestRule {

    // TODO(b/284971005): document enum
    enum Mode {
        SUPPORTED_BY_DEFAULT,
        NOT_SUPPORTED_BY_DEFAULT,
        ANNOTATION_ONLY
    }

    private final Mode mMode;

    public AbstractSupportedFeatureRule(Mode mode) {
        mMode = mode;
        logD("Constructor: mode=%s", mode);
    }

    // NOTE: ideally should be final and provide proper hooks for subclasses, but we might make it
    // non-final in the feature if needed
    @Override
    public final Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                boolean supported = isFeatureSupported();
                logD("Evaluating %s when supported is %b", description, supported);

                switch (mMode) {
                    case SUPPORTED_BY_DEFAULT:
                        if (!supported) {
                            throwFeatureNotSupportedAVE();
                        }
                        break;
                    case NOT_SUPPORTED_BY_DEFAULT:
                        if (supported) {
                            throwFeatureSupportedAVE();
                        }
                        break;
                    case ANNOTATION_ONLY:
                        // TODO(b/284971005): implement
                        throw new UnsupportedOperationException("Not implemented yet");
                }

                Throwable thrown = null;
                try {
                    base.evaluate();
                } catch (Throwable t) {
                    thrown = t;
                }
                logD("Base evaluated: thrown=%s", thrown);
                switch (mMode) {
                    case SUPPORTED_BY_DEFAULT:
                        if (thrown != null) {
                            throw thrown;
                        }
                        break;
                    case NOT_SUPPORTED_BY_DEFAULT:
                        if (thrown == null) {
                            throwUnsupporteTestDidntThrowExpectedExceptionError();
                        }
                        assertUnsupportedTestThrewRightException(thrown);
                        break;
                    case ANNOTATION_ONLY:
                        // TODO(b/284971005): implement
                        throw new UnsupportedOperationException("Not implemented yet");
                }
            }
        };
    }

    // TODO(b/284971005): document methods below

    protected void throwFeatureNotSupportedAVE() {
        throw new AssumptionViolatedException("Device doesn't support the feature");
    }

    protected void throwFeatureSupportedAVE() {
        throw new AssumptionViolatedException("Device supports the feature");
    }

    protected void throwUnsupporteTestDidntThrowExpectedExceptionError() {
        throw new AssertionError(
                "test should have thrown an UnsupportedOperationException, but didn't throw any");
    }

    protected void assertUnsupportedTestThrewRightException(Throwable thrown) {
        if (thrown instanceof UnsupportedOperationException) {
            logD("test threw UnsupportedOperationException as expected: %s", thrown);
            return;
        }
        throw new AssertionError(
                "test should have thrown an UnsupportedOperationException, but instead threw "
                        + thrown,
                thrown);
    }

    @FormatMethod
    protected void logI(@FormatString String msgFmt, Object... msgArgs) {
        logOnSystemOut("logI", msgFmt, msgArgs);
    }

    @FormatMethod
    protected void logD(@FormatString String msgFmt, Object... msgArgs) {
        logOnSystemOut("logD", msgFmt, msgArgs);
    }

    @FormatMethod
    protected void logV(@FormatString String msgFmt, Object... msgArgs) {
        logOnSystemOut("logV", msgFmt, msgArgs);
    }

    abstract boolean isFeatureSupported();

    @FormatMethod
    private void logOnSystemOut(String logLevel, @FormatString String msgFmt, Object... msgArgs) {
        // Logs on System.out by default; subclasses whould use Log / CLog
        System.out.printf("[%s] ", logLevel);
        System.out.printf(msgFmt, msgArgs);
        System.out.println();
    }
}
