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
package com.android.adservices.common.logging;

import androidx.annotation.Nullable;

import java.util.Objects;

// TODO(b/355696393): integrate with rule and/or remove
final class ErrorLogUtilInvocation {

    @Nullable public final Throwable throwable;
    public final int errorCode;
    public final int ppapiName;

    ErrorLogUtilInvocation(Throwable throwable, int errorCode, int ppapiName) {
        this.throwable = throwable;
        this.errorCode = errorCode;
        this.ppapiName = ppapiName;
    }

    @Override
    public int hashCode() {
        return Objects.hash(errorCode, ppapiName, throwable);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ErrorLogUtilInvocation other = (ErrorLogUtilInvocation) obj;
        return errorCode == other.errorCode
                && ppapiName == other.ppapiName
                && Objects.equals(throwable, other.throwable);
    }

    @Override
    public String toString() {
        return "ErrorLogUtilInvocation [exception="
                + throwable
                + ", errorCode="
                + errorCode
                + ", ppapiName="
                + ppapiName
                + "]";
    }
}
