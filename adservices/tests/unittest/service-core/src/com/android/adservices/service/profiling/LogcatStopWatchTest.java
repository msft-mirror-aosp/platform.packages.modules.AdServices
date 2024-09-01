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

package com.android.adservices.service.profiling;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import android.util.Log;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Test;

@SpyStatic(Log.class)
public final class LogcatStopWatchTest extends AdServicesExtendedMockitoTestCase {
    @Test
    public void testSingleStopSingleWrite() {
        LogcatStopWatch watch = new LogcatStopWatch("tag", "name");
        watch.stop();

        ExtendedMockito.verify(() -> Log.d(eq("tag"), anyString()));
    }

    @Test
    public void testMultipleStopsSingleWrite() {
        LogcatStopWatch watch = new LogcatStopWatch("tag", "name");
        watch.stop();
        watch.stop();

        ExtendedMockito.verify(() -> Log.d(eq("tag"), anyString()));
    }
}
