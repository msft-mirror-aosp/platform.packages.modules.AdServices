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

package com.android.adservices.service.measurement.util;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.util.Log;

import com.android.adservices.LoggerFactory;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

@MockStatic(LoggerFactory.class)
public final class JobLockHolderTest extends AdServicesExtendedMockitoTestCase {

    private static final JobLockHolder.Type sLockType = JobLockHolder.Type.EVENT_REPORTING;

    @Mock private Runnable mMockRunnable;
    @Mock private ReentrantLock mMockLock;

    // TODO(b/333416758): use AdServicesLoggingUsageRule instead
    @Mock private LoggerFactory.Logger mLogger;

    private JobLockHolder mHolder;

    @Before
    public void setFixtures() {
        doReturn(mLogger).when(() -> LoggerFactory.getMeasurementLogger());

        mHolder = new JobLockHolder(sLockType, mMockLock);
    }

    @Test
    public void testGetInstance() {
        Set<JobLockHolder> previousHolders = new LinkedHashSet<>();
        for (var type : JobLockHolder.Type.values()) {
            JobLockHolder holder1 = JobLockHolder.getInstance(type);
            Log.v(mTag, "holder for type " + type + ": " + holder1);
            expect.withMessage("first holder for type %s", type).that(holder1).isNotNull();
            expect.withMessage("toString() of holder of type %s", type)
                    .that(holder1.toString())
                    .contains(type.toString());
            JobLockHolder holder2 = JobLockHolder.getInstance(type);
            expect.withMessage("second holder for type %s", type)
                    .that(holder2)
                    .isSameInstanceAs(holder1);
            expect.withMessage("previous holders").that(previousHolders).doesNotContain(holder1);
            previousHolders.add(holder1);
        }
    }

    @Test
    public void testRunWithLock_null() {
        assertThrows(
                NullPointerException.class, () -> mHolder.runWithLock(/* tag= */ null, () -> {}));
        assertThrows(
                NullPointerException.class, () -> mHolder.runWithLock(mTag, /* runnable= */ null));
    }

    @Test
    public void testRunWithLock_success() {
        mockAcquireLock(true);

        mHolder.runWithLock(mTag, mMockRunnable);

        verify(mMockRunnable).run();
        verify(mMockLock).unlock();
        verifyErrorNeverLogged();
    }

    @Test
    public void testRunWithLock_runableThrows() {
        mockAcquireLock(true);

        RuntimeException exception = new RuntimeException("D'OH!");
        doThrow(exception).when(mMockRunnable).run();

        RuntimeException thrown =
                assertThrows(
                        RuntimeException.class, () -> mHolder.runWithLock(mTag, mMockRunnable));

        expect.withMessage("thrown exception").that(thrown).isSameInstanceAs(exception);
        verify(mMockLock).unlock();
        verifyErrorNeverLogged();
    }

    @Test
    public void testRunWithLock_failedToAcquireLock() {
        mockAcquireLock(false);

        mHolder.runWithLock(mTag, mMockRunnable);

        verify(mMockRunnable, never()).run();
        verify(mMockLock, never()).unlock();
        verify(mLogger).e("%s.runWithLock(%s) failed to acquire lock", mTag, sLockType);
    }

    @Test
    public void testCallWithLock_null() {
        assertThrows(
                NullPointerException.class,
                () -> mHolder.callWithLock(/* tag= */ null, () -> this, /* failureResult= */ null));
        assertThrows(
                NullPointerException.class,
                () -> mHolder.callWithLock(mTag, /* callable= */ null, /* failureResult= */ null));
    }

    @Test
    public void testCallWithLock_success() {
        mockAcquireLock(true);

        String result =
                mHolder.callWithLock(mTag, () -> "Saul Goodman!", /* failureResult= */ null);

        expect.withMessage("result").that(result).isEqualTo("Saul Goodman!");
        verify(mMockLock).unlock();
        verifyErrorNeverLogged();
    }

    @Test
    public void testCallWithLock_callableThrows() {
        mockAcquireLock(true);
        RuntimeException exception = new RuntimeException("D'OH!");

        RuntimeException thrown =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                mHolder.callWithLock(
                                        mTag,
                                        () -> {
                                            throw exception;
                                        },
                                        /* failureResult= */ null));

        expect.withMessage("thrown exception").that(thrown).isSameInstanceAs(exception);

        verify(mMockLock).unlock();
        verifyErrorNeverLogged();
    }

    @Test
    public void testCallWithLock_failedToAcquireLock() {
        mockAcquireLock(false);
        String failureResult = "D'OH!";

        String result =
                mHolder.callWithLock(
                        mTag,
                        () -> {
                            throw new UnsupportedOperationException("should not have been called");
                        },
                        failureResult);

        expect.withMessage("result").that(result).isEqualTo(failureResult);
        verify(mMockLock, never()).unlock();
        verify(mLogger)
                .e(
                        "%s.callWithLock(%s) failed to acquire lock; returning %s",
                        mTag, sLockType, failureResult);
    }

    @Test
    public void testToString() throws Exception {
        String toString = mHolder.toString();
        expect.withMessage("toString()").that(toString).startsWith("JobLockHolder");
        expect.withMessage("toString()").that(toString).contains("mType=" + sLockType);
        expect.withMessage("toString()").that(toString).contains("mLock=" + mMockLock);
    }

    private void mockAcquireLock(boolean result) {
        when(mMockLock.tryLock()).thenReturn(result);
    }

    // TODO(b/333416758): use AdServicesLoggingUsageRule instead
    @SuppressWarnings("FormatStringAnnotation")
    private void verifyErrorNeverLogged() {
        verify(mLogger, never()).e(any(Throwable.class), any(String.class), any(Object[].class));
        verify(mLogger, never()).e(any(String.class), any(Object[].class));
    }
}
