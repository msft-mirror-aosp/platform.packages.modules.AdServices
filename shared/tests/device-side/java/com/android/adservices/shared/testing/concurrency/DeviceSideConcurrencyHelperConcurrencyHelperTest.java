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
package com.android.adservices.shared.testing.concurrency;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.adservices.shared.SharedExtendedMockitoTestCase;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

/**
 * Tests for {@link DeviceSideConcurrencyHelper} methods that delegate their implementation to the
 * (sideless) {@link ConcurrencyHelper} - Malkovich!
 *
 * <p><b>Note: </b>these tests just need to assert that the equivalent method on {@link
 * ConcurrencyHelper} is called - the logic of those called methods is tested by {@link
 * ConcurrencyHelperTest}
 */
@SpyStatic(DeviceSideConcurrencyHelper.class)
public final class DeviceSideConcurrencyHelperConcurrencyHelperTest
        extends SharedExtendedMockitoTestCase {

    @Mock private ConcurrencyHelper mMockConcurrencyHelper;
    @Mock private Thread mMockThread;
    @Mock private Runnable mMockRunnable;

    @Before
    public void mockGetConcurrencyHelperInstance() {
        doReturn(mMockConcurrencyHelper).when(DeviceSideConcurrencyHelper::getConcurrencyHelper);
    }

    @Test
    public void testRunAsync() {
        when(mMockConcurrencyHelper.runAsync(42, mMockRunnable)).thenReturn(mMockThread);

        expect.withMessage("runAsync()")
                .that(DeviceSideConcurrencyHelper.runAsync(42, mMockRunnable))
                .isSameInstanceAs(mMockThread);
    }

    @Test
    public void testStartNewThread() {
        when(mMockConcurrencyHelper.startNewThread(mMockRunnable)).thenReturn(mMockThread);

        expect.withMessage("startNewThread()")
                .that(DeviceSideConcurrencyHelper.startNewThread(mMockRunnable))
                .isSameInstanceAs(mMockThread);
    }

    @Test
    public void testSleep() {
        DeviceSideConcurrencyHelper.sleep(108, "Numbers: %s %s %s %s %s %s", 4, 8, 15, 16, 23, 42);

        verify(mMockConcurrencyHelper)
                .sleep(108, "Numbers: %s %s %s %s %s %s", 4, 8, 15, 16, 23, 42);
    }

    @Test
    public void testSleepOnly() throws Exception {
        DeviceSideConcurrencyHelper.sleepOnly(
                108, "Numbers: %s %s %s %s %s %s", 4, 8, 15, 16, 23, 42);

        verify(mMockConcurrencyHelper)
                .sleepOnly(108, "Numbers: %s %s %s %s %s %s", 4, 8, 15, 16, 23, 42);
    }
}
