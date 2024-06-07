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

package com.android.adservices.common.logging.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Used to specify expected {@code ErrorLogUtil.e(Throwable, int, int} calls over test methods.
 *
 * <ol>
 *   <li>To verify ErrorLogUtil.e(Throwable, int, int): @ExpectErrorLogUtilCall(E.class, X, Y)
 *   <li>To verify with any exception: @ExpectErrorLogUtilCall(Any.class, X, Y)
 *   <li>To verify multiple same calls, use the times arg: @ExpectErrorLogUtilCall(E.class, X, Y, 5)
 *   <li>To verify different invocations, use multiple annotations.
 * </ol>
 *
 * <p>See {@link ExpectErrorLogUtilCall} for {@code ErrorLogUtil.e(int, int} calls.
 */
@Retention(RUNTIME)
@Target(METHOD)
@Repeatable(ExpectErrorLogUtilWithExceptionCalls.class)
public @interface ExpectErrorLogUtilWithExceptionCall {
    /** Name of annotation */
    String NAME = ExpectErrorLogUtilWithExceptionCall.class.getSimpleName();

    /** Default number of times to expect log call. */
    int DEFAULT_TIMES = 1;

    /** Used to verify against any exception type for ErrorLogUtil.e(Throwable, int, int) calls. */
    class Any extends Throwable {
        private Any() {}
    }

    /** Throwable to be logged. */
    Class<? extends Throwable> throwable();

    /** Error code to be logged */
    int errorCode();

    /** PPAPI name code to be logged */
    int ppapiName();

    /** Number of log calls, default set to 1 */
    int times() default DEFAULT_TIMES;
}
