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

import static com.android.adservices.shared.testing.concurrency.DeviceSideConcurrencyHelper.getConcurrencyHelper;
import static com.android.adservices.shared.util.Preconditions.checkState;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.util.Log;

import com.android.adservices.LoggerFactory;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.shared.testing.concurrency.CallableSyncCallback;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

@MockStatic(LoggerFactory.class)
public final class JobLockHolderTest extends AdServicesExtendedMockitoTestCase {

    private static final JobLockHolder.Type sLockType = JobLockHolder.Type.EVENT_REPORTING;

    @Mock private Runnable mMockRunnable;
    @Mock private ReentrantLock mMockLock;

    // TODO(b/333416758): use AdServicesLoggingUsageRule instead
    @Mock private LoggerFactory.Logger mLogger;

    // NOTE: only checking before the test, as a failure on @After would hide the real test failure.
    @Before
    public void assertNoLockedInstanceBeforeTest() {
        List<JobLockHolder> lockedHolders = new ArrayList<>();
        for (var type : JobLockHolder.Type.values()) {
            JobLockHolder holder = JobLockHolder.getInstance(type);
            if (holder.isLocked()) {
                lockedHolders.add(holder);
            }
        }

        // Ideally we should call unlock, but @Before / @After is run on the main thread, while
        // tests are run on their own thread. We could change the tests to run on main, but it's
        // easier to just make sure they're unlocked...
        checkState(
                lockedHolders.isEmpty(),
                "%d holders already locked before test: %s",
                lockedHolders.size(),
                lockedHolders);
    }

    @Before
    public void setDefaultExpectations() {
        doReturn(mLogger).when(() -> LoggerFactory.getMeasurementLogger());
    }

    @Test
    public void testGetInstance() {
        Set<JobLockHolder> previousHolders = new LinkedHashSet<>();
        for (var type : JobLockHolder.Type.values()) {
            JobLockHolder holder1 = getFreshInstance(type);
            Log.v(mTag, "holder for type " + type + ": " + holder1);
            expect.withMessage("first holder for type %s", type).that(holder1).isNotNull();
            expect.withMessage("toString() of holder of type %s", type)
                    .that(holder1.toString())
                    .contains(type.toString());
            JobLockHolder holder2 = getFreshInstance(type);
            expect.withMessage("second holder for type %s", type)
                    .that(holder2)
                    .isSameInstanceAs(holder1);
            expect.withMessage("previous holders").that(previousHolders).doesNotContain(holder1);
            previousHolders.add(holder1);
        }
    }

    @Test
    public void testTryLockAndUnlock_sameThread() {
        JobLockHolder holder = getFreshInstance(sLockType);
        boolean firstCall = holder.tryLock();
        assertWithMessage("1st call to %s.tryLock()", holder).that(firstCall).isTrue();
        assertWithMessage("%s.isLocked() after successful tryLock()", holder)
                .that(holder.isLocked())
                .isTrue();

        boolean secondCall = holder.tryLock();
        assertWithMessage("2nd call to %s.tryLock()", holder).that(secondCall).isTrue();
        assertWithMessage("%s.isLocked() after failed tryLock()", holder)
                .that(holder.isLocked())
                .isTrue();

        // tryToLock() was called 2x, so we must call unlock twice as well
        holder.unlock();
        assertWithMessage("%s.isLocked() after 1st call to unlock()", holder)
                .that(holder.isLocked())
                .isTrue();

        holder.unlock();
        assertWithMessage("%s.isLocked() after 2nd call to unlock()", holder)
                .that(holder.isLocked())
                .isFalse();

        boolean thirdCall = holder.tryLock();
        assertWithMessage("%s.tryLock() after unlock()", holder).that(thirdCall).isTrue();
        assertWithMessage("%s.isLocked() after successful tryLock()", holder)
                .that(holder.isLocked())
                .isTrue();

        holder.unlock();
        assertWithMessage("%s.isLocked() after final call to unlock()", holder)
                .that(holder.isLocked())
                .isFalse();
    }

    @Test
    public void testTryLockAndUnlock_differentThreads() throws Exception {
        CallableSyncCallback<Boolean> callback = new CallableSyncCallback<>();
        JobLockHolder holder = getFreshInstance(sLockType);

        boolean firstCall = holder.tryLock();
        assertWithMessage("1st call to %s.tryLock()", holder).that(firstCall).isTrue();

        Thread bgThread =
                getConcurrencyHelper()
                        .startNewThread(() -> callback.injectCallable(() -> holder.tryLock()));

        boolean secondCall = callback.assertResultReceived();
        assertWithMessage("call to %s.tryLock() on another thread (%s)", holder, bgThread)
                .that(secondCall)
                .isFalse();

        holder.unlock();
        assertWithMessage("%s.isLocked() after unlock()", holder).that(holder.isLocked()).isFalse();
    }

    // TODO(b/354007915): once the deprecated methods are removed, use a mHolder below

    @Test
    public void testRunWithLock_null() {
        JobLockHolder holder = new JobLockHolder(sLockType, mMockLock);

        assertThrows(
                NullPointerException.class, () -> holder.runWithLock(/* tag= */ null, () -> {}));
        assertThrows(
                NullPointerException.class, () -> holder.runWithLock(mTag, /* runnable= */ null));
    }

    @Test
    public void testRunWithLock_success() {
        mockAcquireLock(true);
        JobLockHolder holder = new JobLockHolder(sLockType, mMockLock);

        holder.runWithLock(mTag, mMockRunnable);

        verify(mMockRunnable).run();
        verify(mMockLock).unlock();
        verifyErrorNeverLogged();
    }

    @Test
    public void testRunWithLock_runableThrows() {
        mockAcquireLock(true);
        JobLockHolder holder = new JobLockHolder(sLockType, mMockLock);

        RuntimeException exception = new RuntimeException("D'OH!");
        doThrow(exception).when(mMockRunnable).run();

        RuntimeException thrown =
                assertThrows(RuntimeException.class, () -> holder.runWithLock(mTag, mMockRunnable));

        expect.withMessage("thrown exception").that(thrown).isSameInstanceAs(exception);
        verify(mMockLock).unlock();
        verifyErrorNeverLogged();
    }

    @Test
    public void testRunWithLock_failedToAcquireLock() {
        mockAcquireLock(false);
        JobLockHolder holder = new JobLockHolder(sLockType, mMockLock);

        holder.runWithLock(mTag, mMockRunnable);

        verify(mMockRunnable, never()).run();
        verify(mMockLock, never()).unlock();
        verify(mLogger).e("%s.runWithLock(%s) failed to acquire lock", mTag, sLockType);
    }

    @Test
    public void testCallWithLock_null() {
        JobLockHolder holder = new JobLockHolder(sLockType, mMockLock);

        assertThrows(
                NullPointerException.class,
                () -> holder.callWithLock(/* tag= */ null, () -> this, /* failureResult= */ null));
        assertThrows(
                NullPointerException.class,
                () -> holder.callWithLock(mTag, /* callable= */ null, /* failureResult= */ null));
    }

    @Test
    public void testCallWithLock_success() {
        mockAcquireLock(true);
        JobLockHolder holder = new JobLockHolder(sLockType, mMockLock);

        String result = holder.callWithLock(mTag, () -> "Saul Goodman!", /* failureResult= */ null);

        expect.withMessage("result").that(result).isEqualTo("Saul Goodman!");
        verify(mMockLock).unlock();
        verifyErrorNeverLogged();
    }

    @Test
    public void testCallWithLock_callableThrows() {
        mockAcquireLock(true);
        JobLockHolder holder = new JobLockHolder(sLockType, mMockLock);
        RuntimeException exception = new RuntimeException("D'OH!");

        RuntimeException thrown =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                holder.callWithLock(
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
        JobLockHolder holder = new JobLockHolder(sLockType, mMockLock);
        String failureResult = "D'OH!";

        String result =
                holder.callWithLock(
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
        String threadName = Thread.currentThread().getName();

        JobLockHolder holder = getFreshInstance(sLockType);
        String toStringInitially = holder.toString();
        expect.withMessage("initial toString()")
                .that(toStringInitially)
                .startsWith("JobLockHolder");
        expect.withMessage("initial toString()")
                .that(toStringInitially)
                .contains("mType=" + sLockType);
        expect.withMessage("initial toString()")
                .that(toStringInitially)
                .contains("isLocked()=false");
        expect.withMessage("initial toString()").that(toStringInitially).doesNotContain(threadName);

        holder.tryLock();
        String toStringAfterTryLock = holder.toString();
        expect.withMessage("toString() after tryLock()")
                .that(toStringAfterTryLock)
                .contains("isLocked()=true");
        expect.withMessage("toString() afterTryLock()")
                .that(toStringAfterTryLock)
                .contains(threadName);

        holder.unlock();
        String toStringAfterUnlock = holder.toString();
        expect.withMessage("toString() after unlock()")
                .that(toStringAfterUnlock)
                .startsWith("JobLockHolder");
        expect.withMessage("initial toString()")
                .that(toStringAfterUnlock)
                .contains("isLocked()=false");
        expect.withMessage("initial toString()")
                .that(toStringAfterUnlock)
                .doesNotContain(threadName);
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

    /** Gets an instance that should be unlocked (or fail if it isn't) */
    private static JobLockHolder getFreshInstance(JobLockHolder.Type type) {
        JobLockHolder holder = JobLockHolder.getInstance(type);
        checkState(!holder.isLocked(), "%s is already locked", holder);
        return holder;
    }
}
