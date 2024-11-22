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

package com.android.adservices.service.adselection.debug;

import android.annotation.NonNull;
import android.net.Uri;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionDebugReportDao;
import com.android.adservices.data.adselection.AdSelectionDebugReportingDatabase;
import com.android.adservices.data.adselection.DBAdSelectionDebugReport;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.SingletonRunner;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.DevContext;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Worker class to send and clean debug reports generated for ad selection. */
public final class DebugReportSenderWorker {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    public static final String JOB_DESCRIPTION = "Ad selection debug report sender job";
    private static final Object SINGLETON_LOCK = new Object();
    private static volatile DebugReportSenderWorker sDebugReportSenderWorker;
    private final AdSelectionDebugReportDao mAdSelectionDebugReportDao;
    private final AdServicesHttpsClient mAdServicesHttpsClient;
    private final Flags mFlags;
    private final Clock mClock;
    private final SingletonRunner<Void> mSingletonRunner =
            new SingletonRunner<>(JOB_DESCRIPTION, this::doRun);

    @VisibleForTesting
    protected DebugReportSenderWorker(
            AdSelectionDebugReportDao adSelectionDebugReportDao,
            AdServicesHttpsClient adServicesHttpsClient,
            Flags flags,
            Clock clock) {
        mAdSelectionDebugReportDao =
                Objects.requireNonNull(
                        adSelectionDebugReportDao, "adSelectionDebugReportDao cannot be null");
        mAdServicesHttpsClient =
                Objects.requireNonNull(
                        adServicesHttpsClient, "adServicesHttpsClient cannot be null");
        mFlags = Objects.requireNonNull(flags, "flags cannot be null");
        mClock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    /**
     * Gets an instance of a {@link DebugReportSenderWorker}. If an instance hasn't been
     * initialized, a new singleton will be created and returned.
     */
    @NonNull
    public static DebugReportSenderWorker getInstance() {
        if (sDebugReportSenderWorker == null) {
            synchronized (SINGLETON_LOCK) {
                if (sDebugReportSenderWorker == null) {
                    AdSelectionDebugReportDao adSelectionDebugReportDao =
                            AdSelectionDebugReportingDatabase.getInstance()
                                    .getAdSelectionDebugReportDao();
                    Flags flags = FlagsFactory.getFlags();
                    AdServicesHttpsClient adServicesHttpsClient =
                            new AdServicesHttpsClient(
                                    AdServicesExecutors.getBlockingExecutor(),
                                    flags.getFledgeDebugReportSenderJobNetworkConnectionTimeoutMs(),
                                    flags.getFledgeDebugReportSenderJobNetworkReadTimeoutMs(),
                                    AdServicesHttpsClient.DEFAULT_MAX_BYTES);
                    sDebugReportSenderWorker =
                            new DebugReportSenderWorker(
                                    adSelectionDebugReportDao,
                                    adServicesHttpsClient,
                                    flags,
                                    Clock.systemUTC());
                }
            }
        }
        return sDebugReportSenderWorker;
    }

    /**
     * Runs the debug report sender job for Ad Selection Debug Reports.
     *
     * @return A future to be used to check when the task has completed.
     */
    public FluentFuture<Void> runDebugReportSender() {
        sLogger.d("Starting %s", JOB_DESCRIPTION);
        return mSingletonRunner.runSingleInstance();
    }

    /** Requests that any ongoing work be stopped gracefully and waits for work to be stopped. */
    public void stopWork() {
        mSingletonRunner.stopWork();
    }

    private FluentFuture<List<DBAdSelectionDebugReport>> getDebugReports(
            Supplier<Boolean> shouldStop, Instant jobStartTime) {
        if (shouldStop.get()) {
            sLogger.d("Stopping " + JOB_DESCRIPTION);
            return FluentFuture.from(Futures.immediateFuture(ImmutableList.of()));
        }
        int batchSizeForDebugReports = mFlags.getFledgeEventLevelDebugReportingMaxItemsPerBatch();
        sLogger.v("Getting %d debug reports from database", batchSizeForDebugReports);
        return FluentFuture.from(
                AdServicesExecutors.getBackgroundExecutor()
                        .submit(
                                () -> {
                                    List<DBAdSelectionDebugReport> debugReports =
                                            mAdSelectionDebugReportDao.getDebugReportsBeforeTime(
                                                    jobStartTime, batchSizeForDebugReports);
                                    if (debugReports == null) {
                                        sLogger.v("no debug reports to send");
                                        return Collections.emptyList();
                                    }
                                    sLogger.v(
                                            "found %d debug reports from database",
                                            debugReports.size());
                                    return debugReports;
                                }));
    }

    private FluentFuture<Void> cleanupDebugReportsData(Instant jobStartTime) {
        sLogger.v("cleaning up old debug reports from the database at time %s", jobStartTime);
        return FluentFuture.from(
                AdServicesExecutors.getBackgroundExecutor()
                        .submit(
                                () -> {
                                    mAdSelectionDebugReportDao.deleteDebugReportsBeforeTime(
                                            jobStartTime);
                                    return null;
                                }));
    }

    private ListenableFuture<Void> sendDebugReports(
            List<DBAdSelectionDebugReport> dbAdSelectionDebugReports) {

        if (dbAdSelectionDebugReports.isEmpty()) {
            sLogger.d("No debug reports found to send");
            return FluentFuture.from(Futures.immediateVoidFuture());
        }

        sLogger.d("Sending %d debug reports", dbAdSelectionDebugReports.size());
        List<ListenableFuture<Void>> futures =
                dbAdSelectionDebugReports.stream()
                        .map(this::sendDebugReport)
                        .collect(Collectors.toList());
        return Futures.whenAllComplete(futures)
                .call(() -> null, AdServicesExecutors.getBlockingExecutor());
    }

    private ListenableFuture<Void> sendDebugReport(
            DBAdSelectionDebugReport dbAdSelectionDebugReport) {
        Uri debugReportUri = dbAdSelectionDebugReport.getDebugReportUri();
        DevContext devContext =
                DevContext.builder()
                        .setDeviceDevOptionsEnabled(dbAdSelectionDebugReport.getDevOptionsEnabled())
                        .build();
        sLogger.v("Sending debug report %s", debugReportUri);
        try {
            return mAdServicesHttpsClient.getAndReadNothing(debugReportUri, devContext);
        } catch (Exception ignored) {
            sLogger.v("Failed to send debug report %s", debugReportUri);
            return Futures.immediateVoidFuture();
        }
    }

    private FluentFuture<Void> doRun(Supplier<Boolean> shouldStop) {
        Instant jobStartTime = mClock.instant();
        return getDebugReports(shouldStop, jobStartTime)
                .transform(this::sendDebugReports, AdServicesExecutors.getBackgroundExecutor())
                .transformAsync(
                        ignored -> cleanupDebugReportsData(jobStartTime),
                        AdServicesExecutors.getBackgroundExecutor())
                .withTimeout(
                        mFlags.getFledgeDebugReportSenderJobMaxRuntimeMs(),
                        TimeUnit.MILLISECONDS,
                        AdServicesExecutors.getScheduler());
    }
}
