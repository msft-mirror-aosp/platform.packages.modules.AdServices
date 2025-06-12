/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.adservices.test.longevity.concurrent;

import android.platform.test.option.IntegerOption;

import org.junit.Rule;

/** Base class for stress scenarios. */
public abstract class StressScenarioTestAction {
    // Common Options
    private static final String ALLOCATE_MEMORY_AMOUNT = "allocate_memory_amount";
    private static final String ALLOCATE_MEMORY_PERCENTAGE = "allocate_memory_percentage";
    private static final String ALLOCATE_DISK_AMOUNT = "allocate_disk_amount";
    private static final String ALLOCATE_DISK_PERCENTAGE = "allocate_disk_percentage";
    private static final String CPU_THREADS_COUNT = "cpu_threads_count";
    private static final String CPU_INNER_LOOP_ITERATIONS = "cpu_inner_loop_iterations";
    private static final String CPU_THREAD_SLEEP_MS = "cpu_thread_sleep_ms";

    protected static final String TAG = "StressScenarioTestAction";

    @Rule
    public final IntegerOption mAllocateMemoryAmountOption =
            new IntegerOption(ALLOCATE_MEMORY_AMOUNT).setRequired(false).setDefault(-1);

    @Rule
    public final IntegerOption mAllocateMemoryPercentageOption =
            new IntegerOption(ALLOCATE_MEMORY_PERCENTAGE).setRequired(false).setDefault(-1);

    @Rule
    public final IntegerOption mAllocateDiskAmountOption =
            new IntegerOption(ALLOCATE_DISK_AMOUNT).setRequired(false).setDefault(-1);

    @Rule
    public final IntegerOption mAllocateDiskPercentageOption =
            new IntegerOption(ALLOCATE_DISK_PERCENTAGE).setRequired(false).setDefault(-1);

    @Rule
    public final IntegerOption mCpuThreadsCountOption =
            new IntegerOption(CPU_THREADS_COUNT).setRequired(false).setDefault(-1);

    @Rule
    public final IntegerOption mCpuInnerLoopIterationsOption =
            new IntegerOption(CPU_INNER_LOOP_ITERATIONS).setRequired(false).setDefault(-1);

    @Rule
    public final IntegerOption mCpuThreadSleepMsOption =
            new IntegerOption(CPU_THREAD_SLEEP_MS).setRequired(false).setDefault(-1);
}
