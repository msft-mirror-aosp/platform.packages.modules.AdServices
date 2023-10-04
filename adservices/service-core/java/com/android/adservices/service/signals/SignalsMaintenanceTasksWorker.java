/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.signals;

import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.internal.annotations.VisibleForTesting;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Utility class to perform Protected Signals maintenance tasks. */
public class SignalsMaintenanceTasksWorker {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    @NonNull private final ProtectedSignalsDao mProtectedSignalsDao;
    @NonNull private final EnrollmentDao mEnrollmentDao;
    @NonNull private final Flags mFlags;
    @NonNull private final Clock mClock;
    @NonNull private final PackageManager mPackageManager;

    @VisibleForTesting
    public SignalsMaintenanceTasksWorker(
            @NonNull Flags flags,
            @NonNull ProtectedSignalsDao protectedSignalsDao,
            @NonNull EnrollmentDao enrollmentDao,
            @NonNull Clock clock,
            @NonNull PackageManager packageManager) {
        Objects.requireNonNull(flags);
        Objects.requireNonNull(protectedSignalsDao);
        Objects.requireNonNull(enrollmentDao);
        Objects.requireNonNull(clock);
        Objects.requireNonNull(packageManager);

        mFlags = flags;
        mProtectedSignalsDao = protectedSignalsDao;
        mEnrollmentDao = enrollmentDao;
        mClock = clock;
        mPackageManager = packageManager;
    }

    private SignalsMaintenanceTasksWorker(@NonNull Context context) {
        Objects.requireNonNull(context);
        mFlags = FlagsFactory.getFlags();
        mProtectedSignalsDao = ProtectedSignalsDatabase.getInstance(context).protectedSignalsDao();
        mEnrollmentDao = EnrollmentDao.getInstance(context);
        mClock = Clock.systemUTC();
        mPackageManager = context.getPackageManager();
    }

    /** Creates a new instance of {@link SignalsMaintenanceTasksWorker}. */
    public static SignalsMaintenanceTasksWorker create(@NonNull Context context) {
        Objects.requireNonNull(context);
        return new SignalsMaintenanceTasksWorker(context);
    }

    /**
     * Clears invalid signals from the protected signals table. Not flagged since the job will only
     * run if protected signals is enabled.
     *
     * <p>Invalid histogram data includes:
     *
     * <ul>
     *   <li>Expired signals
     *   <li>Disallowed buyer signals
     *   <li>Disallowed source app signals
     *   <li>Uninstalled source app signals
     * </ul>
     */
    public void clearInvalidSignals() {
        Instant expirationInstant =
                mClock.instant().minusSeconds(ProtectedSignal.EXPIRATION_SECONDS);

        sLogger.v("Clearing expired signals older than %s", expirationInstant);
        int numExpiredSignals = mProtectedSignalsDao.deleteSignalsBeforeTime(expirationInstant);
        sLogger.v("Cleared %d expired signals", numExpiredSignals);

        // Read from flags directly, since this maintenance task worker is attached to a background
        //  job with unknown lifetime
        if (mFlags.getDisableFledgeEnrollmentCheck()) {
            sLogger.v(
                    "FLEDGE enrollment check disabled; skipping disallowed buyer signal"
                            + " maintenance");
        } else {
            sLogger.v("Clearing signals for disallowed buyer ad techs");
            int numDisallowedBuyerEvents =
                    mProtectedSignalsDao.deleteDisallowedBuyerSignals(mEnrollmentDao);
            sLogger.v("Cleared %d signals for disallowed buyer ad techs", numDisallowedBuyerEvents);
        }

        // Read from flags directly, since this maintenance task worker is attached to a background
        //  job with unknown lifetime
        sLogger.v("Clearing signals for disallowed source apps");
        int numDisallowedSourceAppSignals =
                mProtectedSignalsDao.deleteAllDisallowedPackageSignals(mPackageManager, mFlags);
        sLogger.v("Cleared %d signals for disallowed source apps", numDisallowedSourceAppSignals);
    }
}
