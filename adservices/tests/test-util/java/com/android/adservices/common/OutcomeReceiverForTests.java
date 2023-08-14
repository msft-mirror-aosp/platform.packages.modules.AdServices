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

import static com.google.common.truth.Truth.assertWithMessage;

import android.os.OutcomeReceiver;
import android.util.Log;

import androidx.annotation.Nullable;

/**
 * Simple implementation of {@link OutcomeReceiver} for tests.
 *
 * <p>Callers typically call {@link #assertSuccess()} or {@link #assertFailure(Class)} to assert the
 * expected result.
 */
public final class OutcomeReceiverForTests<T> implements OutcomeReceiver<T, Exception> {

    private static final String TAG = OutcomeReceiverForTests.class.getSimpleName();

    private @Nullable Exception mError;
    private @Nullable T mResult;
    private @Nullable String mMethodCalled;

    @Override
    public void onError(Exception error) {
        setMethodCalled("onError", error);
        mError = error;
    }

    @Override
    public void onResult(T result) {
        setMethodCalled("onResult", result);
        mResult = result;
    }

    public T assertSuccess() {
        assertWithMessage("result").that(mResult).isNotNull();
        assertWithMessage("error").that(mError).isNull();
        return mResult;
    }

    public <E extends Exception> E assertFailure(Class<E> expectedClass) {
        assertWithMessage("result").that(mResult).isNull();
        assertWithMessage("error").that(mError).isInstanceOf(expectedClass);
        return expectedClass.cast(mError);
    }

    public Exception getError() {
        return mError;
    }

    public T getResult() {
        return mResult;
    }

    private void setMethodCalled(String method, Object arg) {
        String methodCalled = method + "(" + arg + ")";
        Log.v(TAG, methodCalled);
        if (mMethodCalled != null) {
            throw new IllegalStateException(methodCalled + " called after " + mMethodCalled);
        }
        mMethodCalled = methodCalled;
    }
}
