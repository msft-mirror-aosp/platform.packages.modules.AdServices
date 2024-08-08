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
package com.android.adservices.mockito;

import static com.android.adservices.mockito.MockitoExpectations.getSpiedAdServicesJobServiceLogger;
import static com.android.adservices.mockito.MockitoExpectations.mockBackgroundJobsLoggingKillSwitch;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.service.Flags;
import com.android.adservices.spe.AdServicesJobServiceLogger;

import org.junit.Test;
import org.mockito.Mock;

// NOTE: ErrorProne complains that mock objects should not be called directly, but in this test
// they need to, as the test verifies that they would return what is set by the mock
// expectaction methods.
@SuppressWarnings("DirectInvocationOnMock")
public final class MockitoExpectationsTest extends AdServicesMockitoTestCase {

    @Mock private Flags mMockFlags;

    @Test
    public void testMockBackgroundJobsLoggingKillSwitch_null() {
        assertThrows(
                NullPointerException.class, () -> mockBackgroundJobsLoggingKillSwitch(null, true));
    }

    @Test
    public void testMockBackgroundJobsLoggingKillSwitch() {
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, true);

        boolean result = mMockFlags.getBackgroundJobsLoggingKillSwitch();

        expect.withMessage("flags.getBackgroundJobsLoggingKillSwitch()").that(result).isTrue();
    }

    @Test
    public void testGetSpiedAdServicesJobServiceLogger_null() {
        assertThrows(
                NullPointerException.class,
                () -> getSpiedAdServicesJobServiceLogger(/* context= */ null, mMockFlags));
        assertThrows(
                NullPointerException.class,
                () -> getSpiedAdServicesJobServiceLogger(mMockContext, /* flags= */ null));
    }

    @Test
    public void testGetSpiedAdServicesJobServiceLogger() {
        AdServicesJobServiceLogger spy =
                getSpiedAdServicesJobServiceLogger(mMockContext, mMockFlags);
        expect.withMessage("getSpiedAdServicesJobServiceLogger()").that(spy).isNotNull();

        spy.recordOnStartJob(42);
        verify(spy).recordOnStartJob(42);
    }
}
