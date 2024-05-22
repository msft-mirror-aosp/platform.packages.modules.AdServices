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

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.android.adservices.shared.SharedMockitoTestCase;

import org.junit.Test;
import org.mockito.Mock;

public final class AnswerSyncCallbackTest extends SharedMockitoTestCase {

    private final IllegalStateException mFailure = new IllegalStateException("D'OH!");

    @Mock private Voider mDarthVoider;

    @Test
    public void testUnsupportedMethods() {
        AnswerSyncCallback<Void> callback = AnswerSyncCallback.forSingleVoidAnswer();

        assertThrows(UnsupportedOperationException.class, () -> callback.setCalled());
    }

    @Test
    public void testForSingleVoidAnswer() throws Exception {
        AnswerSyncCallback<Void> callback = AnswerSyncCallback.forSingleVoidAnswer();

        doAnswer(callback).when(mDarthVoider).voidVoid();

        mDarthVoider.voidVoid();

        callback.assertCalled();
        expect.withMessage("%%s.isCalled() aftercall", callback).that(callback.isCalled()).isTrue();
    }

    @Test
    public void testForSingleAnswer() throws Exception {
        AnswerSyncCallback<String> callback = AnswerSyncCallback.forSingleAnswer("Luke's Father");
        expect.withMessage("%%s.isCalled() before call", callback)
                .that(callback.isCalled())
                .isFalse();
        when(mDarthVoider.toString()).then(callback);

        expect.withMessage("%%s.isCalled() before call", callback)
                .that(callback.isCalled())
                .isFalse();
        String toString = mDarthVoider.toString();

        expect.withMessage("toString()").that(toString).isEqualTo("Luke's Father");
        callback.assertCalled();
        expect.withMessage("%%s.isCalled() aftercall", callback).that(callback.isCalled()).isTrue();
    }

    // It's testing doAnswer(), so it needs to call those methods...
    @SuppressWarnings("DirectInvocationOnMock")
    @Test
    public void testForMultipleAnswers() throws Exception {
        AnswerSyncCallback<String> callback =
                AnswerSyncCallback.forMultipleAnswers("Luke's Father", 2);
        when(mDarthVoider.toString()).then(callback);
        expect.withMessage("%%s.isCalled() before 1st call", callback)
                .that(callback.isCalled())
                .isFalse();

        String firstAnswer = mDarthVoider.toString();
        expect.withMessage("1st toString() result").that(firstAnswer).isEqualTo("Luke's Father");
        expect.withMessage("%s.isCalled() after first call", callback)
                .that(callback.isCalled())
                .isFalse();

        String secondAnswer = mDarthVoider.toString();

        expect.withMessage("2nd toString() result").that(secondAnswer).isEqualTo("Luke's Father");
        callback.assertCalled();
        expect.withMessage("%s.isCalled() after 2nd call", callback)
                .that(callback.isCalled())
                .isTrue();
    }

    @Test
    public void testForSingleFailure_void() throws Exception {
        AnswerSyncCallback<Void> callback =
                AnswerSyncCallback.forSingleFailure(Void.class, mFailure);
        expect.withMessage("%%s.isCalled() before call", callback)
                .that(callback.isCalled())
                .isFalse();
        doAnswer(callback).when(mDarthVoider).voidVoid();

        Throwable thrown = assertThrows(Throwable.class, () -> mDarthVoider.voidVoid());

        expect.withMessage("thrown exception").that(thrown).isSameInstanceAs(mFailure);
        callback.assertCalled();
        expect.withMessage("%%s.isCalled() aftercall", callback).that(callback.isCalled()).isTrue();
    }

    // It's testing doAnswer(), so it needs to call those methods...
    @SuppressWarnings("DirectInvocationOnMock")
    @Test
    public void testForSingleFailure_nonVoid() throws Exception {
        AnswerSyncCallback<String> callback =
                AnswerSyncCallback.forSingleFailure(String.class, mFailure);
        expect.withMessage("%%s.isCalled() before call", callback)
                .that(callback.isCalled())
                .isFalse();
        when(mDarthVoider.toString()).then(callback);

        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> mDarthVoider.toString());

        expect.withMessage("thrown exception").that(thrown).isSameInstanceAs(mFailure);
        callback.assertCalled();
        expect.withMessage("%%s.isCalled() aftercall", callback).that(callback.isCalled()).isTrue();
    }

    public interface Voider {
        /** Malkovoid! */
        void voidVoid();
    }
}
