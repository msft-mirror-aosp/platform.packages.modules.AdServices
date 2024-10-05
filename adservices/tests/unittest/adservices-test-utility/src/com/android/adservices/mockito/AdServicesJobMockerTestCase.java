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
import static org.mockito.Mockito.when;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.mockito.AbstractStaticMocker.ClassNotSpiedOrMockedException;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.shared.spe.logging.JobSchedulingLogger;
import com.android.adservices.spe.AdServicesJobServiceFactory;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

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

    // Used by assertNoOp to make sure a methods was mocked to do nothing
    private final UnsupportedOperationException mUnsupportedOperation =
            new UnsupportedOperationException("D'OH!");

    @Mock private Flags mMockFlags;
    @Mock private DebugFlags mMockDebugFlags;
    @Mock private AdServicesJobServiceFactory mMockFactory;
    @Mock private AdServicesJobServiceLogger mAdServicesJobServiceLogger;

    @Rule(order = 11)
    public final AdServicesExtendedMockitoRule extendedMockito =
            new AdServicesExtendedMockitoRule.Builder(this).build();

    protected abstract T getMocker(
            StaticClassChecker checker, Flags mockFlags, DebugFlags mockDebugFlags);

    private T getMocker() {
        return getMocker(extendedMockito, mMockFlags, mMockDebugFlags);
    }

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

    @Test
    public final void testMockGetAdServicesJobServiceLogger_null() {
        assertThrows(
                NullPointerException.class,
                () -> getMocker().mockGetAdServicesJobServiceLogger(null));
    }

    @Test
    public final void testMockGetAdServicesJobServiceLogger_staticClassNotMocked() {
        assertThrows(
                ClassNotSpiedOrMockedException.class,
                () -> getMocker().mockGetAdServicesJobServiceLogger(mAdServicesJobServiceLogger));
    }

    @Test
    @MockStatic(AdServicesJobServiceLogger.class)
    public final void testMockGetAdServicesJobServiceLogger() {
        getMocker().mockGetAdServicesJobServiceLogger(mAdServicesJobServiceLogger);

        expect.withMessage("AdServicesJobServiceLogger.getInstance()")
                .that(AdServicesJobServiceLogger.getInstance())
                .isSameInstanceAs(mAdServicesJobServiceLogger);
    }

    @Test
    public void testMockNoOpAdServicesJobServiceLogger_null() {
        assertThrows(
                NullPointerException.class,
                () ->
                        getMocker()
                                .mockNoOpAdServicesJobServiceLogger(
                                        /* context= */ null, mMockFlags));
        assertThrows(
                NullPointerException.class,
                () -> getMocker().mockNoOpAdServicesJobServiceLogger(mContext, /* flags= */ null));
    }

    @Test
    public void testMockNoOpAdServicesJobServiceLogger_staticClassNotMocked() {
        assertThrows(
                ClassNotSpiedOrMockedException.class,
                () -> getMocker().mockNoOpAdServicesJobServiceLogger(mContext, mMockFlags));
    }

    @Test
    @MockStatic(AdServicesJobServiceLogger.class)
    public void testMockNoOpAdServicesJobServiceLogger() {
        var logger = getMocker().mockNoOpAdServicesJobServiceLogger(mContext, mMockFlags);

        expect.withMessage("AdServicesJobServiceLogger.getInstance()")
                .that(AdServicesJobServiceLogger.getInstance())
                .isSameInstanceAs(logger);

        // There's no real way to assert the logger does nothing other than forcing its real methods
        // them to throw an exception, so we need to "leak" some implementation detail in order to
        // do so - in this case, we're mocking the guarding flag to throw...
        when(mMockFlags.getBackgroundJobsLoggingEnabled()).thenThrow(mUnsupportedOperation);

        assertNoOp("recordOnStartJob()", () -> logger.recordOnStartJob(42));
        assertNoOp("recordOnStopJob()", () -> logger.recordOnStopJob(null, 666, true));
        assertNoOp("recordJobSkipped()", () -> logger.recordJobSkipped(42, 666));
        assertNoOp("recordJobFinished()", () -> logger.recordJobFinished(42, true, true));
    }

    private void assertNoOp(String methodName, Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            if (e.equals(mUnsupportedOperation)) {
                expect.withMessage("%s is not mocked", methodName).fail();
            } else {
                expect.withMessage("%s failed with %s", methodName, e).fail();
            }
        }
    }
}
