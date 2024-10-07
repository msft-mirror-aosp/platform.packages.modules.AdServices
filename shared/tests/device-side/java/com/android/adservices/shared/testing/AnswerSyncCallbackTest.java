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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.adservices.shared.testing.concurrency.SyncCallbackFactory;
import com.android.adservices.shared.testing.concurrency.SyncCallbackSettings;
import com.android.adservices.shared.testing.concurrency.SyncCallbackTestCase;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

// It's testing doAnswer(), so it needs to call those methods...
@SuppressWarnings("DirectInvocationOnMock")
public final class AnswerSyncCallbackTest extends SyncCallbackTestCase<AnswerSyncCallback<Void>> {

    private static final String ANSWER = "Luke's Father";

    @Rule public final MockitoRule mockito = MockitoJUnit.rule().strictness(Strictness.LENIENT);

    private final IllegalStateException mFailure = new IllegalStateException("D'OH!");
    private final SyncCallbackSettings mSettingsForTwoCalls =
            SyncCallbackFactory.newSettingsBuilder().setExpectedNumberCalls(2).build();

    @Mock private Voider mDarthVoider;

    @Override
    protected AnswerSyncCallback<Void> newCallback(SyncCallbackSettings settings) {
        return AnswerSyncCallback.forVoidAnswers(settings);
    }

    @Override
    protected String callCallback(AnswerSyncCallback<Void> callback) {
        InvocationOnMock mockInvocation = mock(InvocationOnMock.class);
        // Since mockInvocation is not a "real" InvocationOnMock (provided by Mockito), we need
        // to mock its toString(), otherwise it would be logged as "mMockInvocation" and we'd have
        // to return "mMockInvocation" here too (which would make the FakeLogger output confusing).
        String methodName = "mockedVoidMethod()";
        when(mockInvocation.toString()).thenReturn(methodName);
        try {
            callback.answer(mockInvocation);
            return "answer(" + methodName + ")";
        } catch (Throwable t) {
            // Shouldn't happen
            throw new IllegalStateException("callback.answer(mMockInvocation) failed", t);
        }
    }

    @Override
    protected void assertCalled(AnswerSyncCallback<Void> callback, long timeoutMs)
            throws InterruptedException {
        callback.internalAssertCalled(timeoutMs);
    }

    @Override
    protected boolean providesExpectedConstructors() {
        return false;
    }

    @Test
    public void testForSingleVoidAnswer() throws Exception {
        AnswerSyncCallback<Void> callback = AnswerSyncCallback.forSingleVoidAnswer();

        doAnswer(callback).when(mDarthVoider).voidVoid();

        mDarthVoider.voidVoid();

        callback.assertCalled();
        expect.withMessage("%%s.isCalled() after call", callback)
                .that(callback.isCalled())
                .isTrue();
    }

    @Test
    public void testForSingleAnswer() throws Exception {
        AnswerSyncCallback<String> callback = AnswerSyncCallback.forSingleAnswer(ANSWER);
        expect.withMessage("%%s.isCalled() before call", callback)
                .that(callback.isCalled())
                .isFalse();
        when(mDarthVoider.toString()).then(callback);

        expect.withMessage("%%s.isCalled() before call", callback)
                .that(callback.isCalled())
                .isFalse();
        String toString = mDarthVoider.toString();

        expect.withMessage("toString()").that(toString).isEqualTo(ANSWER);
        callback.assertCalled();
        expect.withMessage("%%s.isCalled() after call", callback)
                .that(callback.isCalled())
                .isTrue();
    }

    @Test
    public void testForMultipleVoidAnswers() throws Exception {
        forTwoVoidAnswers(AnswerSyncCallback.forMultipleVoidAnswers(2));
    }

    @Test
    public void testForVoidAnswers() throws Exception {
        forTwoVoidAnswers(AnswerSyncCallback.forVoidAnswers(mSettingsForTwoCalls));
    }

    private void forTwoVoidAnswers(AnswerSyncCallback<Void> callback) throws Exception {
        doAnswer(callback).when(mDarthVoider).voidVoid();
        expect.withMessage("%%s.isCalled() before 1st call", callback)
                .that(callback.isCalled())
                .isFalse();

        mDarthVoider.voidVoid();

        expect.withMessage("%s.isCalled() after 1st call", callback)
                .that(callback.isCalled())
                .isFalse();

        mDarthVoider.voidVoid();

        callback.assertCalled();
        expect.withMessage("%s.isCalled() after 2nd call", callback)
                .that(callback.isCalled())
                .isTrue();
    }

    @Test
    public void testForMultipleAnswers() throws Exception {
        forTwoAnswersTest(AnswerSyncCallback.forMultipleAnswers(ANSWER, 2));
    }

    @Test
    public void testForAnswers() throws Exception {
        forTwoAnswersTest(AnswerSyncCallback.forAnswers(ANSWER, mSettingsForTwoCalls));
    }

    private void forTwoAnswersTest(AnswerSyncCallback<String> callback) throws Exception {
        when(mDarthVoider.toString()).then(callback);
        expect.withMessage("%%s.isCalled() before 1st call", callback)
                .that(callback.isCalled())
                .isFalse();

        String firstAnswer = mDarthVoider.toString();
        expect.withMessage("1st toString() result").that(firstAnswer).isEqualTo(ANSWER);
        expect.withMessage("%s.isCalled() after first call", callback)
                .that(callback.isCalled())
                .isFalse();

        String secondAnswer = mDarthVoider.toString();

        expect.withMessage("2nd toString() result").that(secondAnswer).isEqualTo(ANSWER);
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
        expect.withMessage("%%s.isCalled() after call", callback)
                .that(callback.isCalled())
                .isTrue();
    }

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
        expect.withMessage("%%s.isCalled() after call", callback)
                .that(callback.isCalled())
                .isTrue();
    }

    public interface Voider {
        /** Malkovoid! */
        void voidVoid();
    }
}
