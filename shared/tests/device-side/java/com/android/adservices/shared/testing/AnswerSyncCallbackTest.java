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
    public void testForVoidAnswer() throws Exception {
        AnswerSyncCallback<Void> callback = AnswerSyncCallback.forVoidAnswer();
        doAnswer(callback).when(mDarthVoider).voidVoid();

        mDarthVoider.voidVoid();

        callback.assertCalled();
    }

    @Test
    public void testForAnswer() throws Exception {
        AnswerSyncCallback<String> callback = AnswerSyncCallback.forAnswer("Luke's Father");
        when(mDarthVoider.toString()).then(callback);

        String toString = mDarthVoider.toString();

        callback.assertCalled();
        expect.withMessage("toString()").that(toString).isEqualTo("Luke's Father");
    }

    @Test
    public void testForFailure_void() throws Exception {
        AnswerSyncCallback<Void> callback = AnswerSyncCallback.forFailure(Void.class, mFailure);
        doAnswer(callback).when(mDarthVoider).voidVoid();

        Throwable thrown = assertThrows(Throwable.class, () -> mDarthVoider.voidVoid());

        expect.withMessage("thrown exception").that(thrown).isSameInstanceAs(mFailure);
        callback.assertCalled();
    }

    @Test
    public void testForFailure_nonVoid() throws Exception {
        AnswerSyncCallback<Void> callback = AnswerSyncCallback.forFailure(Void.class, mFailure);
        when(mDarthVoider.toString()).then(callback);

        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> mDarthVoider.toString());

        expect.withMessage("thrown exception").that(thrown).isSameInstanceAs(mFailure);
        callback.assertCalled();
    }

    public interface Voider {
        /** Malkovoid! */
        void voidVoid();
    }
}
