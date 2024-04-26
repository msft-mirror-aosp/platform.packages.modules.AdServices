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

import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.shared.testing.LogCall;

import java.util.Objects;

/** Corresponding class for {@link ExpectErrorLogUtilCall}. */
public final class ErrorLogUtilCall extends LogCall {
    public final Class<? extends Throwable> mThrowable;
    public final int mErrorCode;
    public final int mPpapiName;

    /** Init ErrorLogUtilCall with default number of times (i.e. 1). */
    public ErrorLogUtilCall(Class<? extends Throwable> throwable, int errorCode, int ppapiName) {
        this(throwable, errorCode, ppapiName, ExpectErrorLogUtilCall.DEFAULT_TIMES);
    }

    /** Init ErrorLogUtilCall with specific number of times. */
    public ErrorLogUtilCall(
            Class<? extends Throwable> throwable, int errorCode, int ppapiName, int times) {
        super(times);
        mThrowable = throwable;
        mErrorCode = errorCode;
        mPpapiName = ppapiName;
    }

    @Override
    public boolean isIdenticalInvocation(LogCall o) {
        if (!(o instanceof ErrorLogUtilCall other)) {
            return false;
        }

        return Objects.equals(mThrowable, other.mThrowable)
                && this.mErrorCode == other.mErrorCode
                && this.mPpapiName == other.mPpapiName;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mThrowable, mErrorCode, mPpapiName);
    }

    @Override
    public String logInvocationToString() {
        if (Objects.equals(mThrowable, ExpectErrorLogUtilCall.None.class)) {
            return "ErrorLogUtil.e(" + mErrorCode + ", " + mPpapiName + ")";
        }

        return "ErrorLogUtil.e("
                + mThrowable.getSimpleName()
                + ", "
                + mErrorCode
                + ", "
                + mPpapiName
                + ")";
    }
}
