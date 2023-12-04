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

import java.util.Arrays;
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

    private static final String REASON_LEVEL_SET_ON_RULE_CONSTRUCTOR =
            "(level set on rule constructor)";

    private final AndroidSdkRange mDefaultRequiredRange;
    protected final Logger mLog;

    AbstractSdkLevelSupportedRule(RealLogger logger, AndroidSdkRange defaultRange) {
        mLog = new Logger(Objects.requireNonNull(logger), TAG);
        mDefaultRequiredRange = Objects.requireNonNull(defaultRange);
        mLog.d("Constructor: logger=%s, defaultRange=%s", logger, defaultRange);
    }

    AbstractSdkLevelSupportedRule(RealLogger logger) {
        this(logger, /* defaultRange= */ AndroidSdkRange.forAnyLevel());
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
                String testName = description.getDisplayName();
                RequiredRange requiredRange = getRequiredRange(description);
                mLog.v("required SDK range for %s: %s", testName, requiredRange);

                int deviceLevel = getDeviceApiLevel().mLevel;
                boolean skip = !requiredRange.range.isInRange(deviceLevel);
                if (skip) {
                    String message =
                            "requires "
                                    + requiredRange.range
                                    + " (and device level is "
                                    + deviceLevel
                                    + "). Reason: "
                                    + requiredRange.reason;
                    mLog.i("Skipping %s, as it %s", testName, message);
                    throw new AssumptionViolatedException("Test " + message);
                }
                base.evaluate();
            }
        };
    }

    // TODO(b/295269584): expose as @VisibleForTest and add unit tests for it - that would be a more
    // pragmatic approach then adding more combinations to the existing tests
    private RequiredRange getRequiredRange(Description description) {
        // Start with the levels set in the constructor
        int minLevel = mDefaultRequiredRange.mMinLevel;
        int maxLevel = mDefaultRequiredRange.mMaxLevel;
        String reason = REASON_LEVEL_SET_ON_RULE_CONSTRUCTOR;

        // Then check for the "atLeastX" method annotations
        RequiresSdkLevelAtLeastR atLeastR =
                description.getAnnotation(RequiresSdkLevelAtLeastR.class);
        if (atLeastR != null && minLevel <= AndroidSdkLevel.R.mLevel) {
            minLevel = AndroidSdkLevel.R.mLevel;
            reason = atLeastR.reason();
        }
        RequiresSdkLevelAtLeastS atLeastS =
                description.getAnnotation(RequiresSdkLevelAtLeastS.class);
        if (atLeastS != null && minLevel <= AndroidSdkLevel.S.mLevel) {
            minLevel = AndroidSdkLevel.S.mLevel;
            reason = atLeastS.reason();
        }
        RequiresSdkLevelAtLeastS2 atLeastS2 =
                description.getAnnotation(RequiresSdkLevelAtLeastS2.class);
        if (atLeastS2 != null && minLevel <= AndroidSdkLevel.S2.mLevel) {
            minLevel = AndroidSdkLevel.S2.mLevel;
            reason = atLeastS2.reason();
        }
        RequiresSdkLevelAtLeastT atLeastT =
                description.getAnnotation(RequiresSdkLevelAtLeastT.class);
        if (atLeastT != null && minLevel <= AndroidSdkLevel.T.mLevel) {
            minLevel = AndroidSdkLevel.T.mLevel;
            reason = atLeastT.reason();
        }
        RequiresSdkLevelAtLeastU atLeastU =
                description.getAnnotation(RequiresSdkLevelAtLeastU.class);
        if (atLeastU != null && minLevel <= AndroidSdkLevel.U.mLevel) {
            minLevel = AndroidSdkLevel.U.mLevel;
            reason = atLeastU.reason();
        }

        // Then check for the "atMostX" method annotations
        RequiresSdkLevelLessThanT requiresLessThanT =
                description.getAnnotation(RequiresSdkLevelLessThanT.class);
        if (requiresLessThanT != null && maxLevel > AndroidSdkLevel.S2.mLevel) {
            maxLevel = AndroidSdkLevel.S2.mLevel;
            reason = requiresLessThanT.reason();
        }
        try {
            return new RequiredRange(AndroidSdkRange.forRange(minLevel, maxLevel), reason);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid range when combining constructor range ("
                            + mDefaultRequiredRange
                            + ") and annotations ("
                            + description.getAnnotations()
                            + ")",
                    e);
        }
    }

    private static final class RequiredRange {
        public final AndroidSdkRange range;
        public final String reason;

        RequiredRange(AndroidSdkRange range, String reason) {
            this.range = Objects.requireNonNull(range);
            this.reason = (reason == null || reason.isBlank()) ? "N/A" : reason;
        }

        @Override
        public String toString() {
            return "[range=" + range + ", reason=" + reason + "]";
        }
    }
    /** Gets the device API level. */
    public abstract AndroidSdkLevel getDeviceApiLevel();

    /** Gets whether the device supports at least Android {@code R}. */
    public final boolean isAtLeastR() {
        return getDeviceApiLevel().isAtLeast(AndroidSdkLevel.R);
    }

    /** Gets whether the device supports at least Android {@code S}. */
    public final boolean isAtLeastS() {
        return getDeviceApiLevel().isAtLeast(AndroidSdkLevel.S);
    }

    /** Gets whether the device supports at least Android {@code SC_V2}. */
    public final boolean isAtLeastS2() {
        return getDeviceApiLevel().isAtLeast(AndroidSdkLevel.S2);
    }

    /** Gets whether the device supports at least Android {@code T}. */
    public final boolean isAtLeastT() {
        return getDeviceApiLevel().isAtLeast(AndroidSdkLevel.T);
    }

    /** Gets whether the device supports at least Android {@code U}. */
    public final boolean isAtLeastU() {
        return getDeviceApiLevel().isAtLeast(AndroidSdkLevel.U);
    }

    // NOTE: calling it AndroidSdkLevel to avoid conflict with SdkLevel
    protected enum AndroidSdkLevel {
        ANY(Integer.MIN_VALUE),
        R(30),
        S(31),
        S2(32),
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

        public static AndroidSdkLevel forLevel(int level) {
            switch (level) {
                case 30:
                    return R;
                case 31:
                    return S;
                case 32:
                    return S2;
                case 33:
                    return T;
                case 34:
                    return U;
            }
            throw new IllegalArgumentException("Unsupported level: " + level);
        }
    }

    /** Represents a range of Android API levels. */
    static final class AndroidSdkRange {
        static final int NO_MIN = Integer.MIN_VALUE;
        static final int NO_MAX = Integer.MAX_VALUE;

        private final int mMinLevel;
        private final int mMaxLevel;

        private AndroidSdkRange(int minLevel, int maxLevel) {
            if (minLevel > maxLevel || minLevel == NO_MAX || maxLevel == NO_MIN) {
                throw new IllegalArgumentException(
                        "maxLevel ("
                                + maxLevel
                                + ") must equal or higher than minLevel ("
                                + minLevel
                                + ")");
            }
            mMinLevel = minLevel;
            mMaxLevel = maxLevel;
        }

        public static AndroidSdkRange forAtLeast(int level) {
            return new AndroidSdkRange(/* minLevel= */ level, NO_MAX);
        }

        public static AndroidSdkRange forAtMost(int level) {
            return new AndroidSdkRange(NO_MIN, /* maxLevel= */ level);
        }

        public static AndroidSdkRange forRange(int minLevel, int maxLevel) {
            return new AndroidSdkRange(minLevel, maxLevel);
        }

        public static AndroidSdkRange forExactly(int level) {
            return new AndroidSdkRange(/* minLevel= */ level, /* maxLevel= */ level);
        }

        public static AndroidSdkRange forAnyLevel() {
            return new AndroidSdkRange(NO_MIN, NO_MAX);
        }

        public boolean isInRange(int level) {
            return level >= mMinLevel && level <= mMaxLevel;
        }

        protected static AndroidSdkRange merge(AndroidSdkRange... ranges) {
            Objects.requireNonNull(ranges, "ranges cannot be null");
            if (ranges.length == 0) {
                throw new IllegalArgumentException("ranges cannot be empty");
            }
            int minRange = NO_MIN;
            int maxRange = NO_MAX;
            for (AndroidSdkRange range : ranges) {
                if (range == null) {
                    throw new IllegalArgumentException(
                            "ranges cannot have null range: " + Arrays.toString(ranges));
                }
                minRange = Math.max(minRange, range.mMinLevel);
                maxRange = Math.min(maxRange, range.mMaxLevel);
            }
            return forRange(minRange, maxRange);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mMaxLevel, mMinLevel);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            AndroidSdkRange other = (AndroidSdkRange) obj;
            return mMaxLevel == other.mMaxLevel && mMinLevel == other.mMinLevel;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder("AndroidSdkRange[minLevel=");
            if (mMinLevel == NO_MIN) {
                builder.append("OPEN");
            } else {
                builder.append(mMinLevel);
            }
            builder.append(", maxLevel=");
            if (mMaxLevel == NO_MAX) {
                builder.append("OPEN");
            } else {
                builder.append(mMaxLevel);
            }
            return builder.append(']').toString();
        }
    }
}
