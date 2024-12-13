/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.exception;

import static com.google.common.truth.Truth.assertThat;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

public final class JSExecutionExceptionTest extends AdServicesUnitTestCase {

    public static final String ERROR_MESSAGE = "error_message";

    @Test
    public void testJSExecutionExceptionTestWithErrorMsg() {
        JSExecutionException exception = new JSExecutionException(ERROR_MESSAGE);
        assertThat(exception).hasMessageThat().isEqualTo(ERROR_MESSAGE);
    }

    @Test
    public void testJSExecutionExceptionTestWithCauseAndErrorMsg() {
        Throwable t = new IllegalStateException();
        JSExecutionException exception = new JSExecutionException(ERROR_MESSAGE, t);
        expect.that(exception).hasMessageThat().isEqualTo(ERROR_MESSAGE);
        expect.that(exception).hasCauseThat().isSameInstanceAs(t);
    }
}
