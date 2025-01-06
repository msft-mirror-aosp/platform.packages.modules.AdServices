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
package com.android.adservices.shared.meta_testing;

import com.android.adservices.shared.testing.AndroidSdk;
import com.android.adservices.shared.testing.AndroidSdk.Level;
import com.android.adservices.shared.testing.AndroidSdk.Range;
import com.android.adservices.shared.testing.ScreenSize;
import com.android.adservices.shared.testing.annotations.RequiresGoDevice;
import com.android.adservices.shared.testing.annotations.RequiresLowRamDevice;
import com.android.adservices.shared.testing.annotations.RequiresScreenSizeDevice;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastR;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS2;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastU;
import com.android.adservices.shared.testing.annotations.RequiresSdkRange;
import com.android.adservices.shared.testing.annotations.SetDoubleFlag;
import com.android.adservices.shared.testing.annotations.SetFlagDisabled;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.shared.testing.annotations.SetFlagFalse;
import com.android.adservices.shared.testing.annotations.SetFlagTrue;
import com.android.adservices.shared.testing.annotations.SetFloatFlag;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;
import com.android.adservices.shared.testing.annotations.SetLongFlag;
import com.android.adservices.shared.testing.annotations.SetStringArrayFlag;
import com.android.adservices.shared.testing.annotations.SetStringFlag;

import com.google.auto.value.AutoAnnotation;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Objects;

/** Provides {@code auto-value-annotation}s for annotations used on test cases. */
public final class TestAnnotations {

    private TestAnnotations() {
        throw new UnsupportedOperationException("provides only static methods");
    }

    /** Gets a new annotation for the given level and reason. */
    public static Annotation newAnnotationForAtLeast(Level level, String reason) {
        switch (level) {
            case R:
                return sdkLevelAtLeastR(reason);
            case S:
                return sdkLevelAtLeastS(reason);
            case S2:
                return sdkLevelAtLeastS2(reason);
            case T:
                return sdkLevelAtLeastT(reason);
            case U:
                return sdkLevelAtLeastU(reason);
            default:
                throw new UnsupportedOperationException(level.toString());
        }
    }

    /** Redundant javadoc to make checkstyle happy */
    @AutoAnnotation
    public static RequiresSdkLevelAtLeastR sdkLevelAtLeastR(String reason) {
        return new AutoAnnotation_TestAnnotations_sdkLevelAtLeastR(reason);
    }

    /** Redundant javadoc to make checkstyle happy */
    @AutoAnnotation
    public static RequiresSdkLevelAtLeastS sdkLevelAtLeastS(String reason) {
        return new AutoAnnotation_TestAnnotations_sdkLevelAtLeastS(reason);
    }

    /** Redundant javadoc to make checkstyle happy */
    @AutoAnnotation
    public static RequiresSdkLevelAtLeastS2 sdkLevelAtLeastS2(String reason) {
        return new AutoAnnotation_TestAnnotations_sdkLevelAtLeastS2(reason);
    }

    /** Redundant javadoc to make checkstyle happy */
    @AutoAnnotation
    public static RequiresSdkLevelAtLeastT sdkLevelAtLeastT(String reason) {
        return new AutoAnnotation_TestAnnotations_sdkLevelAtLeastT(reason);
    }

    /** Redundant javadoc to make checkstyle happy */
    @AutoAnnotation
    public static RequiresSdkLevelAtLeastU sdkLevelAtLeastU(String reason) {
        return new AutoAnnotation_TestAnnotations_sdkLevelAtLeastU(reason);
    }

    /** Redundant javadoc to make checkstyle happy */
    @AutoAnnotation
    public static RequiresSdkRange newAnnotationForLessThanT(String reason) {
        return sdkRange(Range.NO_MIN, AndroidSdk.PRE_T, reason);
    }

    /** Redundant javadoc to make checkstyle happy */
    @AutoAnnotation
    public static RequiresSdkRange sdkRange(int atLeast, int atMost, String reason) {
        return new AutoAnnotation_TestAnnotations_sdkRange(atLeast, atMost, reason);
    }

    /** Redundant javadoc to make checkstyle happy */
    @AutoAnnotation
    public static RequiresLowRamDevice requiresLowRamDevice() {
        return new AutoAnnotation_TestAnnotations_requiresLowRamDevice();
    }

    /** Redundant javadoc to make checkstyle happy */
    @AutoAnnotation
    public static RequiresScreenSizeDevice requiresScreenSizeDevice(ScreenSize value) {
        return new AutoAnnotation_TestAnnotations_requiresScreenSizeDevice(value);
    }

    /** Redundant javadoc to make checkstyle happy */
    @AutoAnnotation
    public static RequiresGoDevice requiresGoDevice() {
        return new AutoAnnotation_TestAnnotations_requiresGoDevice();
    }

    /** Redundant javadoc to make checkstyle happy */
    @AutoAnnotation
    public static SetStringFlag setStringFlag(String name, String value) {
        return new AutoAnnotation_TestAnnotations_setStringFlag(name, value);
    }

    /** Redundant javadoc to make checkstyle happy */
    @AutoAnnotation
    public static SetStringArrayFlag setStringArrayFlag(String name, String... value) {
        return new AutoAnnotation_TestAnnotations_setStringArrayFlag(name, value);
    }

    /** Redundant javadoc to make checkstyle happy */
    public static SetStringArrayFlag setStringArrayWithSeparatorFlag(
            String name, String separator, String... value) {
        return new SetStringArrayFlagAnnotation(name, separator, value);
    }

    /** Redundant javadoc to make checkstyle happy */
    @AutoAnnotation
    public static SetFlagTrue setFlagTrue(String value) {
        return new AutoAnnotation_TestAnnotations_setFlagTrue(value);
    }

    /** Redundant javadoc to make checkstyle happy */
    @AutoAnnotation
    public static SetFlagFalse setFlagFalse(String value) {
        return new AutoAnnotation_TestAnnotations_setFlagFalse(value);
    }

    /** Redundant javadoc to make checkstyle happy */
    @AutoAnnotation
    public static SetFlagEnabled setFlagEnabled(String value) {
        return new AutoAnnotation_TestAnnotations_setFlagEnabled(value);
    }

    /** Redundant javadoc to make checkstyle happy */
    @AutoAnnotation
    public static SetFlagDisabled setFlagDisabled(String value) {
        return new AutoAnnotation_TestAnnotations_setFlagDisabled(value);
    }

    /** Redundant javadoc to make checkstyle happy */
    @AutoAnnotation
    public static SetIntegerFlag setIntegerFlag(String name, int value) {
        return new AutoAnnotation_TestAnnotations_setIntegerFlag(name, value);
    }

    /** Redundant javadoc to make checkstyle happy */
    @AutoAnnotation
    public static SetLongFlag setLongFlag(String name, long value) {
        return new AutoAnnotation_TestAnnotations_setLongFlag(name, value);
    }

    /** Redundant javadoc to make checkstyle happy */
    @AutoAnnotation
    public static SetFloatFlag setFloatFlag(String name, float value) {
        return new AutoAnnotation_TestAnnotations_setFloatFlag(name, value);
    }

    /** Redundant javadoc to make checkstyle happy */
    @AutoAnnotation
    public static SetDoubleFlag setDoubleFlag(String name, double value) {
        return new AutoAnnotation_TestAnnotations_setDoubleFlag(name, value);
    }

    // TODO(b/340882758): figure out how to use @AutoAnnotation overriding default values (separator
    // in this case)
    public static final class SetStringArrayFlagAnnotation implements SetStringArrayFlag {

        private final String mName;
        private final String mSeparator;

        @SuppressWarnings("ImmutableAnnotationChecker")
        private final String[] mValue;

        public SetStringArrayFlagAnnotation(String name, String separator, String... value) {
            mName = name;
            mSeparator = separator;
            mValue = value;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return SetStringArrayFlag.class;
        }

        @Override
        public String name() {
            return mName;
        }

        @Override
        public String separator() {
            return mSeparator;
        }

        @Override
        public String[] value() {
            return mValue;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + Arrays.hashCode(mValue);
            result = prime * result + Objects.hash(mName, mSeparator);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            SetStringArrayFlagAnnotation other = (SetStringArrayFlagAnnotation) obj;
            return Objects.equals(mName, other.mName)
                    && Objects.equals(mSeparator, other.mSeparator)
                    && Arrays.equals(mValue, other.mValue);
        }

        @Override
        public String toString() {
            return "@SetStringArrayFlag(name="
                    + mName
                    + ", separator="
                    + mSeparator
                    + ", value="
                    + Arrays.toString(mValue)
                    + ")";
        }
    }
}
