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

import com.android.adservices.errorlogging.ErrorLogUtil;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** Used to specify expected {@link ErrorLogUtil} calls. */
@Retention(RUNTIME)
@Target(METHOD)
@Repeatable(ExpectErrorLogUtilCalls.class)
public @interface ExpectErrorLogUtilCall {
    /** Default number of times to expect log call. */
    int DEFAULT_TIMES = 1;

    /** Default empty exception. */
    class None extends Throwable {
        private None() {}
    }

    /**
     * Optionally specify a Throwable to be logged, default is None which represents no throwable
     * was logged.
     */
    Class<? extends Throwable> throwable() default None.class;

    /** Error code to be logged */
    int errorCode();

    /** PPAPI name code to be logged */
    int ppapiName();

    /** Number of log calls, default set to 1 */
    int times() default DEFAULT_TIMES;
}
