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

package com.android.adservices.shared.spe.framework;

import static com.android.adservices.shared.spe.framework.ExecutionResult.CANCELLED_BY_SCHEDULER;
import static com.android.adservices.shared.spe.framework.ExecutionResult.FAILURE_WITHOUT_RETRY;
import static com.android.adservices.shared.spe.framework.ExecutionResult.FAILURE_WITH_RETRY;
import static com.android.adservices.shared.spe.framework.ExecutionResult.SUCCESS;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.EqualsTester;

import org.junit.Test;

/** Unit tests for {@link ExecutionResult}. */
public final class ExecutionResultTest extends AdServicesUnitTestCase {
    @Test
    public void testEqualsAndHashcode() {
        EqualsTester et = new EqualsTester(expect);

        et.expectObjectsAreEqual(SUCCESS, SUCCESS);
        et.expectObjectsAreEqual(FAILURE_WITH_RETRY, FAILURE_WITH_RETRY);
        et.expectObjectsAreEqual(FAILURE_WITHOUT_RETRY, FAILURE_WITHOUT_RETRY);
        et.expectObjectsAreEqual(CANCELLED_BY_SCHEDULER, CANCELLED_BY_SCHEDULER);

        et.expectObjectsAreNotEqual(SUCCESS, FAILURE_WITH_RETRY);
        et.expectObjectsAreNotEqual(SUCCESS, FAILURE_WITHOUT_RETRY);
        et.expectObjectsAreNotEqual(SUCCESS, CANCELLED_BY_SCHEDULER);
        et.expectObjectsAreNotEqual(FAILURE_WITH_RETRY, FAILURE_WITHOUT_RETRY);
        et.expectObjectsAreNotEqual(FAILURE_WITH_RETRY, CANCELLED_BY_SCHEDULER);
        et.expectObjectsAreNotEqual(FAILURE_WITHOUT_RETRY, CANCELLED_BY_SCHEDULER);
    }
}
