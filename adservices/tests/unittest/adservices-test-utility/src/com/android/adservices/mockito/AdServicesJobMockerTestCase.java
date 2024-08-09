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

import static com.android.adservices.shared.testing.mockito.MockitoHelper.isMock;
import static com.android.adservices.shared.testing.mockito.MockitoHelper.isSpy;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.service.Flags;
import com.android.adservices.shared.spe.logging.JobSchedulingLogger;
import com.android.adservices.spe.AdServicesJobServiceFactory;
import com.android.adservices.spe.AdServicesJobServiceLogger;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

// NOTE: ErrorProne complains that mock objects should not be called directly, but in this test
// they need to, as the test verifies that they would return what is set by the mock
// expectaction methods.
/**
 * Base class for all {@link AdServicesJobServiceMocker} implementations.
 *
 * @param <T> mocker implementation
 */
@SuppressWarnings("DirectInvocationOnMock")
public abstract class AdServicesJobMockerTestCase<T extends AdServicesJobMocker>
        extends AdServicesUnitTestCase {

    @Mock private Flags mMockFlags;
    @Mock private AdServicesJobServiceFactory mMockFactory;

    @Rule public final MockitoRule mockito = MockitoJUnit.rule().strictness(Strictness.LENIENT);

    protected abstract T getMocker();

    @Before
    public final void verifyMocker() {
        assertWithMessage("getMocker()").that(getMocker()).isNotNull();
    }

    @Test
    public final void testGetSpiedAdServicesJobServiceLogger_null() {
        T mocker = getMocker();
        assertThrows(
                NullPointerException.class,
                () -> mocker.getSpiedAdServicesJobServiceLogger(/* context= */ null, mMockFlags));
        assertThrows(
                NullPointerException.class,
                () -> mocker.getSpiedAdServicesJobServiceLogger(mContext, /* flags= */ null));
    }

    @Test
    public final void testGetSpiedAdServicesJobServiceLogger() {
        AdServicesJobServiceLogger spy =
                getMocker().getSpiedAdServicesJobServiceLogger(mContext, mMockFlags);
        expect.withMessage("getSpiedAdServicesJobServiceLogger()").that(spy).isNotNull();
        expect.withMessage("getSpiedAdServicesJobServiceLogger() is a spy")
                .that(isSpy(spy))
                .isTrue();

        spy.recordOnStartJob(42);
        verify(spy).recordOnStartJob(42);
    }

    @Test
    public final void testMockJobSchedulingLogger_null() {
        assertThrows(NullPointerException.class, () -> getMocker().mockJobSchedulingLogger(null));
    }

    @Test
    @SuppressWarnings("NewApi") // TODO(b/357944639): remove
    public final void testMockJobSchedulingLogger() {
        JobSchedulingLogger logger = getMocker().mockJobSchedulingLogger(mMockFactory);
        assertWithMessage("mockJobSchedulingLogger()").that(logger).isNotNull();
        expect.withMessage("logger is a mock").that(isMock(logger)).isTrue();

        JobSchedulingLogger fromFactory = mMockFactory.getJobSchedulingLogger();
        expect.withMessage("logger from factory").that(fromFactory).isSameInstanceAs(logger);
    }
}
