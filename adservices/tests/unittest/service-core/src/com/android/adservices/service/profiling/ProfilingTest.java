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

import static com.google.common.truth.Truth.assertThat;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

public final class ProfilingTest extends AdServicesUnitTestCase {
    @Test
    public void testStartReturnsActualStopWatch() {
        Profiler profiler = Profiler.createInstance("foo");
        assertThat(profiler.start("myName")).isInstanceOf(LogcatStopWatch.class);
    }

    @Test
    public void testStartReturnsFakeStopWatch() {
        Profiler profiler = Profiler.createNoOpInstance("foo");
        assertThat(profiler.start("myName")).isInstanceOf(FakeStopWatch.class);
    }
}
