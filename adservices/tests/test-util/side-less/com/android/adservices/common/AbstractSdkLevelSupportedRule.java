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
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.Objects;

// TODO(b/295269584): move to module-utils?
// TODO(b/295269584): add examples
// TODO(b/295269584): add unit tests
// TODO(b/295269584): rename to AbstractSdkLevelSupportRule

/**
 * Rule used to skip a test when it's not supported by the device's SDK version.
 *
 * <p>This rule is abstract so subclass can define what a "feature" means. It also doesn't have any
 * dependency on Android code, so it can be used both on device-side and host-side tests.
 *
 * <p><b>NOTE: </b>this class should NOT be used as {@code ClassRule}, as it would result in a "no
 * tests run" scenario if it throws a {@link AssumptionViolatedException}.
 */
abstract class AbstractSdkLevelSupportedRule implements TestRule {

    private static final String TAG = "SdkLevelSupportRule";

    // TODO(b/295269584): need to provide more flexible levels (min, max, range, etc..)

    private final AndroidSdkLevel mDefaultMinLevel;
    protected final Logger mLog;

    AbstractSdkLevelSupportedRule(RealLogger logger, AndroidSdkLevel defaultMinLevel) {
        mLog = new Logger(Objects.requireNonNull(logger), TAG);
        mDefaultMinLevel = Objects.requireNonNull(defaultMinLevel);
        mLog.d("Constructor: logger=%s, defaultMinLevel=%s", logger, defaultMinLevel);
    }

    AbstractSdkLevelSupportedRule(RealLogger logger) {
        this(logger, /* defaultMinLevel= */ AndroidSdkLevel.ANY);
    }

    @Override
    public final Statement apply(Statement base, Description description) {
        if (!description.isTest()) {
            throw new IllegalStateException(
                    "This rule can only be applied to individual tests, it cannot be used as"
                            + " @ClassRule or in a test suite");
        }
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                skipIfSdkLevelHigherThanMax(description);

                String testName = description.getDisplayName();
                MinimumLevelRequired minLevelRequired = getMinimumLevelRequired(description);
                mLog.v("MinimumLevelRequired for %s: %s", testName, minLevelRequired);

                boolean skip = false;
                switch (minLevelRequired.level) {
                    case ANY:
                        break;
                    case R:
                        skip = !isAtLeastR();
                        break;
                    case S:
                        skip = !isAtLeastS();
                        break;
                    case U:
                        skip = !isAtLeastU();
                        break;
                    case T:
                        skip = !isAtLeastT();
                        break;
                    default:
                        // Shouldn't happen
                        throw new IllegalStateException(
                                "Invalid MinimumLevelRequired: " + minLevelRequired.level);
                }

                if (skip) {
                    String message =
                            "requires SDK level "
                                    + minLevelRequired.level
                                    + ". Reason: "
                                    + minLevelRequired.reason;
                    mLog.i("Skipping %s, as it %s", testName, message);
                    throw new AssumptionViolatedException("Test " + message);
                }
                base.evaluate();
            }
        };
    }

    protected void skipIfSdkLevelHigherThanMax(Description description) throws Exception {
        // TODO(b/295269584): for now it only supports (no pun intended) "lessThanT", but
        // it should support others (in which case it would need explicit minLevel /
        // maxLevel
        RequiresSdkLevelLessThanT requiresLessThanT =
                description.getAnnotation(RequiresSdkLevelLessThanT.class);
        if (requiresLessThanT != null && isAtLeastT()) {
            String testName = description.getDisplayName();
            String reason = requiresLessThanT.reason();
            String message =
                    "Test annotated with @RequiresSdkLevelLessThanT "
                            + (reason.isEmpty()
                                    ? ""
                                    : "(reason=" + requiresLessThanT.reason() + ")");
            mLog.i("Skipping %s: %s", testName, message);
            throw new AssumptionViolatedException(message);
        }
    }

    // TODO(b/295269584): expose as @VisibleForTest and add unit test for it - that would be a more
    // pragmatic approach then adding more combinations to the existing tests
    private MinimumLevelRequired getMinimumLevelRequired(Description description) {
        RequiresSdkLevelAtLeastU atLeastU =
                description.getAnnotation(RequiresSdkLevelAtLeastU.class);
        if (atLeastU != null) {
            return new MinimumLevelRequired(AndroidSdkLevel.U, atLeastU.reason());
        }
        RequiresSdkLevelAtLeastT atLeastT =
                description.getAnnotation(RequiresSdkLevelAtLeastT.class);
        if (atLeastT != null) {
            return new MinimumLevelRequired(AndroidSdkLevel.T, atLeastT.reason());
        }
        RequiresSdkLevelAtLeastS atLeastS =
                description.getAnnotation(RequiresSdkLevelAtLeastS.class);
        if (atLeastS != null) {
            return new MinimumLevelRequired(AndroidSdkLevel.S, atLeastS.reason());
        }
        RequiresSdkLevelAtLeastR atLeastR =
                description.getAnnotation(RequiresSdkLevelAtLeastR.class);
        if (atLeastR != null) {
            return new MinimumLevelRequired(AndroidSdkLevel.R, atLeastR.reason());
        }

        return new MinimumLevelRequired(mDefaultMinLevel, "(level set on rule constructor)");
    }

    private static final class MinimumLevelRequired {
        public final AndroidSdkLevel level;
        public final String reason;

        MinimumLevelRequired(AndroidSdkLevel level, String reason) {
            this.level = level;
            this.reason = (reason == null || reason.isBlank()) ? "N/A" : reason;
        }

        @Override
        public String toString() {
            return "[level=" + level + ", reason=" + reason + "]";
        }
    }

    /** Gets whether the device supports at least Android {@code R}. */
    public abstract boolean isAtLeastR() throws Exception;

    /** Gets whether the device supports at least Android {@code S}. */
    public abstract boolean isAtLeastS() throws Exception;

    /** Gets whether the device supports at least Android {@code T}. */
    public abstract boolean isAtLeastT() throws Exception;

    /** Gets whether the device supports at least Android {@code U}. */
    public abstract boolean isAtLeastU() throws Exception;

    // NOTE: calling it AndroidSdkLevel to avoid conflict with SdkLevel
    protected enum AndroidSdkLevel {
        ANY(Integer.MIN_VALUE),
        R(30),
        S(31),
        T(33),
        U(34);

        private final int mLevel;

        AndroidSdkLevel(int level) {
            mLevel = level;
        }

        boolean isAtLeast(AndroidSdkLevel level) {
            return mLevel >= level.mLevel;
        }

        int getLevel() {
            return mLevel;
        }
    }
}
