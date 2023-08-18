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

// TODO(b/295269584): move to module-utils
// TODO(b/295269584): add examples
// TODO(b/295269584): add unit tests

/**
 * Rule used to skip a test when it's not supported by the device's SDK version.
 *
 * <p>This rule is abstract so subclass can define what a "feature" means. It also doesn't have any
 * dependency on Android code, so it can be used both on device-side and host-side tests.
 */
abstract class AbstractSdkLevelSupportedRule implements TestRule {

    private final AndroidSdkLevel mDefaultMinLevel;
    protected final Logger mLog;

    AbstractSdkLevelSupportedRule(RealLogger logger, AndroidSdkLevel defaultMinLevel) {
        mLog = new Logger(Objects.requireNonNull(logger));
        mDefaultMinLevel = Objects.requireNonNull(defaultMinLevel);
        mLog.d("Constructor: logger=%s, defaultMinLevel=%s", logger, defaultMinLevel);
    }

    AbstractSdkLevelSupportedRule(RealLogger logger) {
        this(logger, /* defaultMinLevel= */ AndroidSdkLevel.ANY);
    }

    @Override
    public final Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                String testName = description.getDisplayName();
                MinimumLevelRequired minLevelRequired = getMinimumLevelRequired(description);
                mLog.v("MinimumLevelRequired for %s: %s", testName, minLevelRequired);

                boolean skip = false;
                switch (minLevelRequired.level) {
                    case ANY:
                        break;
                    case R:
                        skip = !isDeviceAtLeastR();
                        break;
                    case S:
                        skip = !isDeviceAtLeastS();
                        break;
                    case S_V2:
                        skip = !isDeviceAtLeastS_V2();
                        break;
                    case T:
                        skip = !isDeviceAtLeastT();
                        break;
                    case U:
                        skip = !isDeviceAtLeastU();
                        break;
                    case V:
                        skip = !isDeviceAtLeastV();
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

    private MinimumLevelRequired getMinimumLevelRequired(Description description) {
        RequiresSdkLevelAtLeastV atLeastV =
                description.getAnnotation(RequiresSdkLevelAtLeastV.class);
        if (atLeastV != null) {
            return new MinimumLevelRequired(AndroidSdkLevel.V, atLeastV.reason());
        }
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
        RequiresSdkLevelAtLeastS_V2 atLeastS_V2 =
                description.getAnnotation(RequiresSdkLevelAtLeastS_V2.class);
        if (atLeastS_V2 != null) {
            return new MinimumLevelRequired(AndroidSdkLevel.S_V2, atLeastS_V2.reason());
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
    public abstract boolean isDeviceAtLeastR() throws Exception;

    /** Gets whether the device supports at least Android {@code S}. */
    public abstract boolean isDeviceAtLeastS() throws Exception;

    /** Gets whether the device supports at least Android {@code S_V2}. */
    public abstract boolean isDeviceAtLeastS_V2() throws Exception;

    /** Gets whether the device supports at least Android {@code T}. */
    public abstract boolean isDeviceAtLeastT() throws Exception;

    /** Gets whether the device supports at least Android {@code U}. */
    public abstract boolean isDeviceAtLeastU() throws Exception;

    /** Gets whether the device supports at least Android {@code V}. */
    public abstract boolean isDeviceAtLeastV() throws Exception;

    // This must match Build.VERSION_CODES.CUR_DEVELOPMENT
    private static final int CUR_DEVELOPMENT = 10_000;

    // NOTE: calling it AndroidSdkLevel to avoid conflict with SdkLevel
    protected enum AndroidSdkLevel {
        ANY(Integer.MIN_VALUE),
        R(30),
        S(31),
        S_V2(32),
        T(33),
        U(34),
        // TODO(b/295269584): figure out if there is a way to avoid CUR_DEVELOPMENT (for example,
        // by checking the release name), or remove support for unreleased versions (as for now the
        // only "client" is AdServices, which don't need that)
        V(CUR_DEVELOPMENT);

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
