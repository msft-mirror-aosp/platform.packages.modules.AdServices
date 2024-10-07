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

import com.android.adservices.shared.testing.concurrency.DeviceSideSyncCallback;
import com.android.adservices.shared.testing.concurrency.SyncCallbackFactory;
import com.android.adservices.shared.testing.concurrency.SyncCallbackSettings;
import com.android.adservices.shared.testing.mockito.MockitoHelper;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * {@code SyncCallback} to be used when setting Mockito expectations with an {@link Answer}.
 *
 * @param <T> return type of the method being "answered".
 */
public final class AnswerSyncCallback<T> extends DeviceSideSyncCallback implements Answer<T> {

    @Nullable private final T mAnswer;
    @Nullable private final Throwable mFailure;

    private AnswerSyncCallback(T answer, Throwable failure, int numberOfExpectedCalls) {
        this(
                answer,
                failure,
                SyncCallbackFactory.newSettingsBuilder()
                        .setExpectedNumberCalls(numberOfExpectedCalls)
                        .build());
    }

    private AnswerSyncCallback(T answer, Throwable failure, SyncCallbackSettings settings) {
        super(settings);
        mAnswer = answer;
        mFailure = failure;
    }

    /**
     * Convenience method for {@link #forVoidAnswers(SyncCallbackSettings)} using a {@link
     * SyncCallbackSettings settings} that expects just 1 call.
     */
    public static AnswerSyncCallback<Void> forSingleVoidAnswer() {
        return new AnswerSyncCallback<Void>(
                /* answer= */ null, /* failure= */ null, /* numberOfExpectedCalls= */ 1);
    }

    /**
     * Convenience method for {@link #forAnswers(SyncCallbackSettings)} using a {@link
     * SyncCallbackSettings settings} that expects just 1 call.
     */
    public static <A> AnswerSyncCallback<A> forSingleAnswer(A answer) {
        return new AnswerSyncCallback<A>(
                answer, /* failure= */ null, /* numberOfExpectedCalls= */ 1);
    }

    /**
     * Convenience method for {@link #forVoidAnswers(SyncCallbackSettings)} using a {@link
     * SyncCallbackSettings settings} that expects {@code numberOfExpectedCalls} calls.
     */
    public static AnswerSyncCallback<Void> forMultipleVoidAnswers(int numberOfExpectedCalls) {
        return new AnswerSyncCallback<>(
                /* answer= */ null, /* failure= */ null, numberOfExpectedCalls);
    }

    /**
     * Convenience method for {@link #forAnswers(SyncCallbackSettings)} using a {@link
     * SyncCallbackSettings settings} that expects {@code numberOfExpectedCalls} calls.
     */
    public static <A> AnswerSyncCallback<A> forMultipleAnswers(
            A answer, int numberOfExpectedCalls) {
        return new AnswerSyncCallback<A>(answer, /* failure= */ null, numberOfExpectedCalls);
    }

    /**
     * Factory method for answers that return {@code Void} and take a generic {@link
     * SyncCallbackSettings}.
     */
    public static AnswerSyncCallback<Void> forVoidAnswers(SyncCallbackSettings settings) {
        return new AnswerSyncCallback<>(/* answer= */ null, /* failure= */ null, settings);
    }

    /** Factory method for answers that return non-{@code Void}. */
    public static <A> AnswerSyncCallback<A> forAnswers(A answer, SyncCallbackSettings settings) {
        return new AnswerSyncCallback<A>(answer, /* failure= */ null, settings);
    }

    /**
     * Factory method for {@link Answer}s that should thrown an exception.
     *
     * @param clazz type of the object that should be returned by the {@link Answer}
     * @param failure exception that will be thrown.
     */
    public static <A> AnswerSyncCallback<A> forSingleFailure(Class<A> clazz, Throwable failure) {
        return new AnswerSyncCallback<A>(
                /* answer= */ null, failure, /* numberOfExpectedCalls= */ 1);
    }

    @Override
    public T answer(InvocationOnMock invocation) throws Throwable {
        String invocationString = MockitoHelper.toString(invocation);
        super.internalSetCalled("answer(" + invocationString + ")");
        if (mFailure != null) {
            logV("Throwing '%s' on %s", mFailure, invocationString);
            throw mFailure;
        }
        logV("Answering '%s' on %s", mAnswer, invocationString);
        return mAnswer;
    }
}
