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

import static org.junit.Assert.assertThrows;

import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;

public final class OutcomeReceiverForTestsTest {

    private static final String RESULT = "Saul Goodman!";
    private static final Exception ERROR = new UnsupportedOperationException("D'OH!");

    @Rule public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Rule public final Expect expect = Expect.create();

    @Test
    public void testOnResult() {
        OutcomeReceiverForTests<String> receiver = new OutcomeReceiverForTests<>();

        receiver.onResult(RESULT);

        String result = receiver.assertSuccess();
        expect.withMessage("assertSuccess()").that(result).isEqualTo(RESULT);
        expect.withMessage("getResult()").that(receiver.getResult()).isEqualTo(RESULT);
        expect.withMessage("getError()").that(receiver.getError()).isNull();
    }

    @Test
    public void testOnResult_calledTwice() {
        OutcomeReceiverForTests<String> receiver = new OutcomeReceiverForTests<>();
        receiver.onResult(RESULT);
        String anotherError = "You Shall Not Pass!";

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> receiver.onResult(anotherError));

        expect.withMessage("exception")
                .that(exception)
                .hasMessageThat()
                .contains("onResult(" + anotherError + ") called after onResult(" + RESULT + ")");
    }

    @Test
    public void testOnResult_afterOnError() {
        OutcomeReceiverForTests<String> receiver = new OutcomeReceiverForTests<>();
        receiver.onError(ERROR);

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> receiver.onResult(RESULT));

        expect.withMessage("exception")
                .that(exception)
                .hasMessageThat()
                .contains("onResult(" + RESULT + ") called after onError(" + ERROR + ")");
    }

    @Test
    public void testOnError_nullArg() {
        OutcomeReceiverForTests<String> receiver = new OutcomeReceiverForTests<>();

        assertThrows(NullPointerException.class, () -> receiver.assertFailure(null));
    }

    @Test
    public void testOnError() {
        OutcomeReceiverForTests<String> receiver = new OutcomeReceiverForTests<>();

        receiver.onError(ERROR);

        Exception error = receiver.assertFailure(ERROR.getClass());
        expect.withMessage("assertFailure()").that(error).isSameInstanceAs(ERROR);
        expect.withMessage("getError()").that(receiver.getError()).isSameInstanceAs(ERROR);
        expect.withMessage("getResult()").that(receiver.getResult()).isNull();
    }

    @Test
    public void testOnError_calledTwice() {
        OutcomeReceiverForTests<String> receiver = new OutcomeReceiverForTests<>();
        receiver.onError(ERROR);
        Exception anotherError = new UnsupportedOperationException("Again?");

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> receiver.onError(anotherError));

        expect.withMessage("exception")
                .that(exception)
                .hasMessageThat()
                .contains("onError(" + anotherError + ") called after onError(" + ERROR + ")");
    }

    @Test
    public void testOnError_afterOnResult() {
        OutcomeReceiverForTests<String> receiver = new OutcomeReceiverForTests<>();
        receiver.onResult(RESULT);

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> receiver.onError(ERROR));

        expect.withMessage("exception")
                .that(exception)
                .hasMessageThat()
                .contains("onError(" + ERROR + ") called after onResult(" + RESULT + ")");
    }
}
