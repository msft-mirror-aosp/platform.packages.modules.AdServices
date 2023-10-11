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

package com.android.cobalt;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@RunWith(AndroidJUnit4.class)
public class NoOpCobaltPeriodicJobTest {
    private static ExecutorService sExecutor = Executors.newCachedThreadPool();

    @Test
    public void testGenerateAggregatedObservations() throws Exception {
        CobaltPeriodicJob logger = new NoOpCobaltPeriodicJob(sExecutor);
        Future<Void> log = logger.generateAggregatedObservations();
        assertThat(log.get()).isNull();
    }
}