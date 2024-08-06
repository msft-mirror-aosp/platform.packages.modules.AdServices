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

package com.android.adservices.cobalt;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;

import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RequiresSdkLevelAtLeastS
public final class CobaltDataServiceFactoryTest extends AdServicesUnitTestCase {
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();

    @Test
    public void nullContext_throwsNullPointerException() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> {
                    CobaltDataServiceFactory.createDataService(null, EXECUTOR_SERVICE);
                });
    }

    @Test
    public void nullExecutorService_throwsNullPointerException() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> {
                    CobaltDataServiceFactory.createDataService(sContext, null);
                });
    }
}
