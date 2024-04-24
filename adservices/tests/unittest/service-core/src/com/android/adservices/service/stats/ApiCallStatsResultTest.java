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

package com.android.adservices.service.stats;

import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_UNSET;
import static android.adservices.common.AdServicesStatusUtils.STATUS_BACKGROUND_CALLER;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;

import static com.android.adservices.service.stats.ApiCallStats.failureResult;
import static com.android.adservices.service.stats.ApiCallStats.successResult;
import static com.android.adservices.service.stats.ApiCallStats.Result;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.EqualsTester;

import org.junit.Test;

/** Unit test for {@link ApiCallStats}. */
public final class ApiCallStatsResultTest extends AdServicesUnitTestCase {

    private static final int CODE = STATUS_BACKGROUND_CALLER + 42;
    private static final int FAILURE = FAILURE_REASON_UNSET + 108;

    @Test
    public void testForFailure() {
        Result result = failureResult(CODE, FAILURE);

        expect.withMessage("%s.getResultCode()", result)
                .that(result.getResultCode())
                .isEqualTo(CODE);
        expect.withMessage("%s.getFailureReason()", result)
                .that(result.getFailureReason())
                .isEqualTo(FAILURE);
    }

    @Test
    public void testForSuccess() {
        Result ok = successResult();
        expect.withMessage("%s.isSuccess()", ok).that(ok.isSuccess()).isTrue();
        expect.withMessage("%s.getResultCode()", ok)
                .that(ok.getResultCode())
                .isEqualTo(STATUS_SUCCESS);
        expect.withMessage("%s.getFailureReason()", ok)
                .that(ok.getFailureReason())
                .isEqualTo(FAILURE_REASON_UNSET);

        Result ok2 = successResult();
        expect.withMessage("2nd call of forSuccess()").that(ok2).isSameInstanceAs(ok);
    }

    @Test
    public void testToString() {
        Result result = failureResult(CODE, FAILURE);

        String toString = result.toString();

        expect.that(toString).startsWith("Result");
        expect.that(toString).contains("Code=" + CODE);
        expect.that(toString).contains("FailureReason=" + FAILURE);
    }

    @Test
    public void testEqualsHashCode() {
        EqualsTester et = new EqualsTester(expect);
        Result equals1 = failureResult(CODE, FAILURE);
        Result equals2 = failureResult(CODE, FAILURE);

        Result different1 = failureResult(CODE + 1, FAILURE);
        Result different2 = failureResult(CODE, FAILURE + 1);

        et.expectObjectsAreEqual(equals1, equals1);
        et.expectObjectsAreEqual(equals1, equals2);

        et.expectObjectsAreNotEqual(equals1, null);
        et.expectObjectsAreNotEqual(equals1, "STATS, Y U NO STRING?");

        et.expectObjectsAreNotEqual(equals1, different1);
        et.expectObjectsAreNotEqual(equals1, different2);
    }
}
