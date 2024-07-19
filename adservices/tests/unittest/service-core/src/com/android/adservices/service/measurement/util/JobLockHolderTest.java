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

import static com.android.adservices.service.measurement.util.JobLockHolder.Type.EVENT_REPORTING;
import static com.android.adservices.shared.testing.concurrency.DeviceSideConcurrencyHelper.getConcurrencyHelper;
import static com.android.adservices.shared.util.Preconditions.checkState;

import static com.google.common.truth.Truth.assertWithMessage;

import android.util.Log;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.concurrency.CallableSyncCallback;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class JobLockHolderTest extends AdServicesUnitTestCase {

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
        JobLockHolder holder = getFreshInstance(EVENT_REPORTING);
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
        JobLockHolder holder = getFreshInstance(EVENT_REPORTING);

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

    @Test
    public void testToString() throws Exception {
        String threadName = Thread.currentThread().getName();

        JobLockHolder holder = getFreshInstance(EVENT_REPORTING);
        String toStringInitially = holder.toString();
        expect.withMessage("initial toString()")
                .that(toStringInitially)
                .startsWith("JobLockHolder");
        expect.withMessage("initial toString()")
                .that(toStringInitially)
                .contains("mType=" + EVENT_REPORTING);
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

    /** Gets an instance that should be unlocked (or fail if it isn't) */
    private static JobLockHolder getFreshInstance(JobLockHolder.Type type) {
        JobLockHolder holder = JobLockHolder.getInstance(type);
        checkState(!holder.isLocked(), "%s is already locked", holder);
        return holder;
    }
}
