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

package com.android.adservices.shared.testing;

import android.util.Log;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * {@code SyncCallback} to be used when setting Mockito expectations with an {@link Answer}.
 *
 * @param <T> return type of the method being "answered".
 */
public final class AnswerSyncCallback<T> implements Answer<T> {

    private static final String TAG = AnswerSyncCallback.class.getSimpleName();

    private final SimpleSyncCallback mCallback = new SimpleSyncCallback();
    @Nullable private final T mAnswer;
    @Nullable private final Throwable mFailure;

    private AnswerSyncCallback(T answer, Throwable failure) {
        mAnswer = answer;
        mFailure = failure;
    }

    /** Factory method for methods that return {@code Void}. */
    public static AnswerSyncCallback<Void> forVoidAnswer() {
        return new AnswerSyncCallback<Void>(/* answer= */ null, /* failure= */ null);
    }

    /**
     * Factory method for methods that return an object.
     *
     * @param answer object that will be returned by the {@link Answer}.
     */
    public static <A extends Object> AnswerSyncCallback<A> forAnswer(A answer) {
        return new AnswerSyncCallback<A>(answer, /* failure= */ null);
    }

    /**
     * Factory method for {@link Answer}s that should thrown an exception.
     *
     * @param clazz type of the object that should be returned by the {@link Answer}
     * @param failure exception that will be thrown.
     */
    public static <A extends Object> AnswerSyncCallback<A> forFailure(
            Class<A> clazz, Throwable failure) {
        return new AnswerSyncCallback<A>(/* answer= */ null, failure);
    }

    /** Asserts that mocked method was called or throws if it timed out. */
    public void assertCalled() throws InterruptedException {
        mCallback.assertCalled();
    }

    @Override
    public T answer(InvocationOnMock invocation) throws Throwable {
        mCallback.setCalled();
        if (mFailure != null) {
            Log.v(TAG, "Throwing '" + mFailure + "' on " + invocation);
            throw mFailure;
        }
        Log.v(TAG, "Answering '" + mAnswer + "' on " + invocation);
        return mAnswer;
    }
}