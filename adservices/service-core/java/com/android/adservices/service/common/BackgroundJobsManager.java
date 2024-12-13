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

package com.android.adservices.service.common;

import static com.android.adservices.spe.AdServicesJobInfo.AD_PACKAGE_DENY_PRE_PROCESS_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.COBALT_LOGGING_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.CONSENT_NOTIFICATION_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.ENCRYPTION_KEY_PERIODIC_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.FLEDGE_AD_SELECTION_DEBUG_REPORT_SENDER_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.FLEDGE_BACKGROUND_FETCH_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MAINTENANCE_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_ASYNC_REGISTRATION_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_ATTRIBUTION_FALLBACK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_ATTRIBUTION_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_DELETE_EXPIRED_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_DELETE_UNINSTALLED_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_EVENT_MAIN_REPORTING_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_REPORTING_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.PERIODIC_SIGNALS_ENCODING_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.TOPICS_EPOCH_JOB;

import android.annotation.NonNull;
import android.app.job.JobScheduler;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.cobalt.CobaltJobService;
import com.android.adservices.download.MddJob;
import com.android.adservices.download.MddJobService;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.MaintenanceJobService;
import com.android.adservices.service.adselection.debug.DebugReportSenderJobService;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.encryptionkey.EncryptionKeyJobService;
import com.android.adservices.service.measurement.DeleteExpiredJobService;
import com.android.adservices.service.measurement.DeleteUninstalledJobService;
import com.android.adservices.service.measurement.attribution.AttributionFallbackJobService;
import com.android.adservices.service.measurement.attribution.AttributionJobService;
import com.android.adservices.service.measurement.registration.AsyncRegistrationFallbackJob;
import com.android.adservices.service.measurement.registration.AsyncRegistrationQueueJobService;
import com.android.adservices.service.measurement.reporting.AggregateFallbackReportingJobService;
import com.android.adservices.service.measurement.reporting.AggregateReportingJobService;
import com.android.adservices.service.measurement.reporting.DebugReportingFallbackJobService;
import com.android.adservices.service.measurement.reporting.EventFallbackReportingJobService;
import com.android.adservices.service.measurement.reporting.EventReportingJobService;
import com.android.adservices.service.measurement.reporting.ImmediateAggregateReportingJobService;
import com.android.adservices.service.measurement.reporting.ReportingJobService;
import com.android.adservices.service.measurement.reporting.VerboseDebugReportingFallbackJobService;
import com.android.adservices.service.topics.EpochJob;
import com.android.adservices.service.topics.EpochJobService;

import java.util.Objects;

/** Provides functionality to schedule or unschedule all relevant background jobs. */
@RequiresApi(Build.VERSION_CODES.S)
// TODO(b/311183933): Remove passed in Context from static method.
@SuppressWarnings("AvoidStaticContext")
public class BackgroundJobsManager {
    /**
     * Tries to schedule all the relevant background jobs.
     *
     * @param context application context.
     */
    public static void scheduleAllBackgroundJobs(@NonNull Context context) {
        scheduleFledgeBackgroundJobs(context);

        scheduleTopicsBackgroundJobs(context);

        // TODO(b/296146348): Remove MDD Background Jobs from scheduleAllBackgroundJobs
        scheduleMddBackgroundJobs();

        scheduleMeasurementBackgroundJobs(context);
    }

    /**
     * Tries to schedule all the relevant background jobs per api.
     *
     * @param context application context.
     */
    public static void scheduleJobsPerApi(@NonNull Context context, AdServicesApiType apiType) {
        switch (apiType) {
            case FLEDGE:
                scheduleFledgeBackgroundJobs(context);
                break;
            case TOPICS:
                scheduleTopicsBackgroundJobs(context);
                break;
            case MEASUREMENTS:
                scheduleMeasurementBackgroundJobs(context);
                break;
        }
    }

    /** Tries to unschedule all the relevant background jobs per api. */
    public static void unscheduleJobsPerApi(
            @NonNull JobScheduler jobScheduler, AdServicesApiType apiType) {
        switch (apiType) {
            case FLEDGE:
                unscheduleFledgeBackgroundJobs(jobScheduler);
                break;
            case TOPICS:
                unscheduleTopicsBackgroundJobs(jobScheduler);
                break;
            case MEASUREMENTS:
                unscheduleMeasurementBackgroundJobs(jobScheduler);
                break;
        }
    }

    /**
     * Tries to schedule all the Fledge related background jobs if the FledgeSelectAdsKillSwitch is
     * disabled.
     *
     * <p>Those services are:
     *
     * <ul>
     *   <li>{@link MaintenanceJobService}
     * </ul>
     *
     * @param context application context.
     */
    public static void scheduleFledgeBackgroundJobs(@NonNull Context context) {
        Flags flags = FlagsFactory.getFlags();
        if (!flags.getFledgeSelectAdsKillSwitch()) {
            MaintenanceJobService.scheduleIfNeeded(context, /* forceSchedule= */ false);
            if (flags.getFledgeEventLevelDebugReportingEnabled()) {
                DebugReportSenderJobService.scheduleIfNeeded(context, false);
            }
        }
    }

    /**
     * Tries to schedule all the Topics related background jobs if the TopicsKillSwitch is disabled.
     *
     * <p>Those services are:
     *
     * <ul>
     *   <li>{@link EpochJobService}
     *   <li>{@link MaintenanceJobService}
     *   <li>{@link MddJobService}
     *   <li>{@link EncryptionKeyJobService}
     *   <li>{@link CobaltJobService}
     * </ul>
     *
     * @param context application context.
     */
    public static void scheduleTopicsBackgroundJobs(@NonNull Context context) {
        if (!FlagsFactory.getFlags().getTopicsKillSwitch()) {
            EpochJob.schedule();
            MaintenanceJobService.scheduleIfNeeded(context, /* forceSchedule= */ false);
            scheduleMddBackgroundJobs();
            scheduleEncryptionKeyBackgroundJobs(context);
            scheduleCobaltBackgroundJob(context);
            scheduleAdPackageDenyPreProcessBackgroundJob();
        }
    }

    /**
     * Tries to schedule all the Mdd related background jobs if the MddBackgroundTaskKillSwitch is
     * disabled.
     */
    public static void scheduleMddBackgroundJobs() {
        if (!FlagsFactory.getFlags().getMddBackgroundTaskKillSwitch()) {
            MddJob.scheduleAllMddJobs();
        }
    }

    /**
     * Tries to schedule EncryptionKey related background jobs if the
     * EncryptionKeyPeriodicFetchKillSwitch is disabled.
     *
     * @param context application context.
     */
    public static void scheduleEncryptionKeyBackgroundJobs(@NonNull Context context) {
        if (!FlagsFactory.getFlags().getEncryptionKeyPeriodicFetchKillSwitch()) {
            EncryptionKeyJobService.scheduleIfNeeded(context, /* forceSchedule */ false);
        }
    }

    /**
     * Tries to schedule all the Measurement related background jobs if the MeasurementKillSwitch is
     * disabled.
     *
     * <p>Those services are:
     *
     * <ul>
     *   <li>{@link AggregateReportingJobService}
     *   <li>{@link AggregateFallbackReportingJobService}
     *   <li>{@link ImmediateAggregateReportingJobService}
     *   <li>{@link ReportingJobService}
     *   <li>{@link AttributionJobService}
     *   <li>{@link EventReportingJobService}
     *   <li>{@link EventFallbackReportingJobService}
     *   <li>{@link DeleteExpiredJobService}
     *   <li>{@link DeleteUninstalledJobService}
     *   <li>{@link AsyncRegistrationQueueJobService}
     *   <li>{@link AsyncRegistrationFallbackJob}
     *   <li>{@link MddJobService}
     *   <li>{@link EncryptionKeyJobService}
     *   <li>{@link CobaltJobService}
     * </ul>
     *
     * @param context application context.
     */
    public static void scheduleMeasurementBackgroundJobs(@NonNull Context context) {
        if (FlagsFactory.getFlags().getMeasurementEnabled()) {
            AggregateReportingJobService.scheduleIfNeeded(context, /* forceSchedule= */ false);
            AggregateFallbackReportingJobService.scheduleIfNeeded(
                    context, /* forceSchedule= */ false);
            ImmediateAggregateReportingJobService.scheduleIfNeeded(
                    context, /* forceSchedule= */ false);
            ReportingJobService.scheduleIfNeeded(context, /* forceSchedule= */ false);
            AttributionJobService.scheduleIfNeeded(context, /* forceSchedule= */ false);
            AttributionFallbackJobService.scheduleIfNeeded(context, /* forceSchedule= */ false);
            EventReportingJobService.scheduleIfNeeded(context, /* forceSchedule= */ false);
            EventFallbackReportingJobService.scheduleIfNeeded(context, /* forceSchedule= */ false);
            DeleteExpiredJobService.scheduleIfNeeded(context, /* forceSchedule= */ false);
            DeleteUninstalledJobService.scheduleIfNeeded(context, /* forceSchedule= */ false);
            AsyncRegistrationQueueJobService.scheduleIfNeeded(context, /* forceSchedule= */ false);
            AsyncRegistrationFallbackJob.schedule();
            VerboseDebugReportingFallbackJobService.scheduleIfNeeded(
                    context, /* forceSchedule= */ false);
            DebugReportingFallbackJobService.scheduleIfNeeded(context, /* forceSchedule= */ false);
            scheduleMddBackgroundJobs();
            scheduleEncryptionKeyBackgroundJobs(context);
            scheduleCobaltBackgroundJob(context);
            scheduleAdPackageDenyPreProcessBackgroundJob();
        }
    }

    /**
     * Tries to schedule Cobalt logging related background jobs if the CobaltLoggingEnabled is true.
     *
     * @param context application context
     */
    public static void scheduleCobaltBackgroundJob(Context context) {
        if (FlagsFactory.getFlags().getCobaltLoggingEnabled()) {
            CobaltJobService.scheduleIfNeeded(context, /* forceSchedule= */ false);
        }
    }

    /** Tries to schedule Package Deny related background jobs */
    public static void scheduleAdPackageDenyPreProcessBackgroundJob() {
        if (FlagsFactory.getFlags().getEnablePackageDenyBgJob()) {
            AdPackageDenyPreProcessJobService.scheduleIfNeeded();
        }
    }

    /**
     * Tries to unschedule all the relevant background jobs.
     *
     * @param jobScheduler Job scheduler to cancel the jobs.
     */
    public static void unscheduleAllBackgroundJobs(@NonNull JobScheduler jobScheduler) {
        Objects.requireNonNull(jobScheduler);

        unscheduleTopicsBackgroundJobs(jobScheduler);

        unscheduleMeasurementBackgroundJobs(jobScheduler);

        unscheduleFledgeBackgroundJobs(jobScheduler);

        unscheduleMaintenanceJobs(jobScheduler);

        jobScheduler.cancel(CONSENT_NOTIFICATION_JOB.getJobId());

        MddJob.unscheduleAllJobs(jobScheduler);

        jobScheduler.cancel(ENCRYPTION_KEY_PERIODIC_JOB.getJobId());

        unscheduleCobaltJob(jobScheduler);

        unscheduleAdPackageDenyPreProcessJob(jobScheduler);
    }

    /**
     * Tries to unschedule all the Measurement related background jobs.
     *
     * @param jobScheduler Job scheduler to cancel the jobs.
     */
    public static void unscheduleMeasurementBackgroundJobs(@NonNull JobScheduler jobScheduler) {
        Objects.requireNonNull(jobScheduler);

        jobScheduler.cancel(MEASUREMENT_EVENT_MAIN_REPORTING_JOB.getJobId());
        jobScheduler.cancel(MEASUREMENT_DELETE_EXPIRED_JOB.getJobId());
        jobScheduler.cancel(MEASUREMENT_DELETE_UNINSTALLED_JOB.getJobId());
        jobScheduler.cancel(MEASUREMENT_ATTRIBUTION_JOB.getJobId());
        jobScheduler.cancel(MEASUREMENT_ATTRIBUTION_FALLBACK_JOB.getJobId());
        jobScheduler.cancel(MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB.getJobId());
        jobScheduler.cancel(MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB.getJobId());
        jobScheduler.cancel(MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB.getJobId());
        jobScheduler.cancel(MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB.getJobId());
        jobScheduler.cancel(MEASUREMENT_REPORTING_JOB.getJobId());
        jobScheduler.cancel(MEASUREMENT_ASYNC_REGISTRATION_JOB.getJobId());
        jobScheduler.cancel(MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB.getJobId());
        jobScheduler.cancel(MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB.getJobId());
        jobScheduler.cancel(MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB.getJobId());
    }

    /**
     * Tries to unschedule all the Topics related background jobs.
     *
     * @param jobScheduler Job scheduler to cancel the jobs.
     */
    public static void unscheduleTopicsBackgroundJobs(@NonNull JobScheduler jobScheduler) {
        Objects.requireNonNull(jobScheduler);

        jobScheduler.cancel(TOPICS_EPOCH_JOB.getJobId());
    }

    /**
     * Tries to unschedule all the Fledge related background jobs.
     *
     * @param jobScheduler Job scheduler to cancel the jobs.
     */
    public static void unscheduleFledgeBackgroundJobs(@NonNull JobScheduler jobScheduler) {
        Objects.requireNonNull(jobScheduler);

        jobScheduler.cancel(FLEDGE_BACKGROUND_FETCH_JOB.getJobId());
        jobScheduler.cancel(FLEDGE_AD_SELECTION_DEBUG_REPORT_SENDER_JOB.getJobId());
        jobScheduler.cancel(PERIODIC_SIGNALS_ENCODING_JOB.getJobId());
        jobScheduler.cancel(FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB.getJobId());
    }

    /**
     * Tries to unschedule all the maintenance background jobs.
     *
     * @param jobScheduler Job scheduler to cancel the jobs.
     */
    public static void unscheduleMaintenanceJobs(@NonNull JobScheduler jobScheduler) {
        Objects.requireNonNull(jobScheduler);

        jobScheduler.cancel(MAINTENANCE_JOB.getJobId());
    }

    /** Tries to unschedule Cobalt background job. */
    public static void unscheduleCobaltJob(@NonNull JobScheduler jobScheduler) {
        Objects.requireNonNull(jobScheduler);

        jobScheduler.cancel(COBALT_LOGGING_JOB.getJobId());
    }

    /** Tries to unschedule Package Deny background job. */
    public static void unscheduleAdPackageDenyPreProcessJob(@NonNull JobScheduler jobScheduler) {
        Objects.requireNonNull(jobScheduler);

        jobScheduler.cancel(AD_PACKAGE_DENY_PRE_PROCESS_JOB.getJobId());
    }
}
