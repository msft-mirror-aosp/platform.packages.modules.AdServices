/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.adservices.shared.testing.annotations.SetSdkSandboxStateEnabled;

import java.lang.annotation.Annotation;
import java.util.Objects;

/**
 * {@link SetSdkSandboxStateEnabled} implementation for tests.
 *
 * <p>It's more convenient than using {@code AutoValue}.
 */
public final class SetSdkSandboxStateEnabledAnnotation implements SetSdkSandboxStateEnabled {
    private final boolean mValue;

    public SetSdkSandboxStateEnabledAnnotation(boolean value) {
        mValue = value;
    }

    @Override
    public boolean value() {
        return mValue;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return SetSdkSandboxStateEnabled.class;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mValue);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        SetSdkSandboxStateEnabledAnnotation other = (SetSdkSandboxStateEnabledAnnotation) obj;
        return mValue == other.mValue;
    }

    @Override
    public String toString() {
        return "SetSdkSandboxStateEnabledAnnotation[value=" + mValue + "]";
    }
}
