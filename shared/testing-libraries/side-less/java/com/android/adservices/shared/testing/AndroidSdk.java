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
package com.android.adservices.shared.testing;

import com.google.common.annotations.VisibleForTesting;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/** Abstraction for Android SDK levels (so it can be used both on device and host side tests). */
public final class AndroidSdk {

    private static final Logger sLogger = new Logger(DynamicLogger.getInstance(), AndroidSdk.class);

    /** Android version {@code RVC}. */
    public static final int RVC = 30;

    /** Android version {@code SC}. */
    public static final int SC = 31;

    /** Android version {@code SC_V2}. */
    public static final int SC_V2 = 32;

    /** Android version {@code TM}. */
    public static final int TM = 33;

    /** Android version {@code UC}. */
    public static final int UDC = 34;

    /** Android version {@code VIC}. */
    public static final int VIC = 35;

    /** Android version for unreleased builds}. */
    public static final int CUR_DEVELOPMENT = 10_000; // Build.CUR_DEVELOPMENT.CUR_DEVELOPMENT

    /**
     * Convenience for ranges that are "less than T" (for example {@code
     * RequiresSdkRange(atMost=PRE_T)}), as S had 2 APIs (31 and 32)
     */
    public static final int PRE_T = SC_V2;

    // TODO(b/324919960): make it package-protected again or make sure it's unit tested.
    /** Represents a specific SDK level. */
    public enum Level {
        ANY(Integer.MIN_VALUE),
        DEV(CUR_DEVELOPMENT),
        R(RVC),
        S(SC),
        S2(SC_V2),
        T(TM),
        U(UDC),
        V(VIC);

        private final int mLevel;

        Level(int level) {
            mLevel = level;
        }

        // TODO(b/324919960): make it package-protected again or make sure it's unit tested.
        /** Checks if SDK is at least the given level. */
        public boolean isAtLeast(Level level) {
            return mLevel >= level.mLevel;
        }

        // TODO(b/324919960): make it package-protected again or make sure it's unit tested.
        /** Gets the numeric representation of the SDK level (like {@code 33}). */
        public int getLevel() {
            return mLevel;
        }

        /** Gets the level abstraction for the given level). */
        public static Level forLevel(int level) {
            switch (level) {
                case CUR_DEVELOPMENT:
                    return DEV;
                case RVC:
                    return R;
                case SC:
                    return S;
                case SC_V2:
                    return S2;
                case TM:
                    return T;
                case UDC:
                    return U;
                case VIC:
                    return V;
            }
            if (level > VIC) {
                sLogger.e(
                        "WARNING: Level.forLevel() called with unsupported / unreleased level (%d);"
                                + " returning DEV (%d)",
                        level, DEV.mLevel);
                return DEV;
            }
            throw new IllegalArgumentException("Unsupported level: " + level);
        }
    }

    // TODO(b/324919960): make it package-protected again or make sure it's unit tested.
    /** Represents a range of Android API levels. */
    public static final class Range {
        // TODO(b/324919960): make them package-protected again or make sure it's unit tested.
        public static final int NO_MIN = Integer.MIN_VALUE;
        public static final int NO_MAX = Integer.MAX_VALUE;

        private final int mMinLevel;
        private final int mMaxLevel;

        private Range(int minLevel, int maxLevel) {
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

        /** Gets a range without an upper boundary. */
        public static Range forAtLeast(int level) {
            return new Range(/* minLevel= */ level, NO_MAX);
        }

        /** Gets a range without a lower boundary. */
        public static Range forAtMost(int level) {
            return new Range(NO_MIN, /* maxLevel= */ level);
        }

        /** Gets a range for the specific levels. */
        public static Range forRange(int minLevel, int maxLevel) {
            return new Range(minLevel, maxLevel);
        }

        /** Gets a range for a specific level. */
        public static Range forExactly(int level) {
            return new Range(/* minLevel= */ level, /* maxLevel= */ level);
        }

        /** Gets a range that includes any level. */
        public static Range forAnyLevel() {
            return new Range(NO_MIN, NO_MAX);
        }

        /** Checks if the given level fits this range (inclusive). */
        public boolean isInRange(int level) {
            return level >= mMinLevel && level <= mMaxLevel;
        }

        @VisibleForTesting
        static Range merge(Range... ranges) {
            return merge(Arrays.asList(ranges));
        }

        static Range merge(Collection<Range> ranges) {
            Objects.requireNonNull(ranges, "ranges cannot be null");
            if (ranges.isEmpty()) {
                throw new IllegalArgumentException("ranges cannot be empty");
            }
            int minRange = NO_MIN;
            int maxRange = NO_MAX;
            for (Range range : ranges) {
                if (range == null) {
                    throw new IllegalArgumentException("ranges cannot have null range: " + ranges);
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
            Range other = (Range) obj;
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

    private AndroidSdk() {
        throw new UnsupportedOperationException();
    }
}
