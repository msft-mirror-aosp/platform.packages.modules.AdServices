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

package com.android.adservices.service.measurement;

import android.content.Context;

import androidx.annotation.Nullable;

import com.android.adservices.data.measurement.MeasurementDao;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

class AttributionJobHandler {

    private static AttributionJobHandler sSingleton;
    private final MeasurementDao mMeasurementDao;

    /**
     * @return instance of the {@link AttributionJobHandler}.
     */
    static synchronized AttributionJobHandler getInstance(Context context) {
        if (sSingleton == null) {
            sSingleton = new AttributionJobHandler(context);
        }
        return sSingleton;
    }

    private AttributionJobHandler(Context context) {
        mMeasurementDao = MeasurementDao.getInstance(context);
    }

    @VisibleForTesting
    AttributionJobHandler(MeasurementDao measurementDAO) {
        mMeasurementDao = measurementDAO;
    }

    /**
     * Finds the {@link AdtechUrl} when given a postback url.
     *
     * @param postbackUrl the postback url of the request AdtechUrl
     * @return the requested AdtechUrl; Null in case of SQL failure
     */
    @Nullable
    synchronized AdtechUrl findAdtechUrl(String postbackUrl) {
        if (postbackUrl == null) {
            return null;
        }
        return mMeasurementDao.getAdtechEnrollmentData(postbackUrl);
    }

    /**
     * Queries and returns all the postback urls with the same adtech id as the given postback url.
     *
     * @param postbackUrl the postback url of the request AdtechUrl
     * @return all the postback urls with the same adtech id; Null in case of SQL failure
     */
    public List<String> getAllAdtechUrls(String postbackUrl) {
        if (postbackUrl == null) {
            return new ArrayList<>();
        }
        return mMeasurementDao.getAllAdtechUrls(postbackUrl);
    }

    /**
     * Perform attribution by finding relevant {@link Source} and generates {@link EventReport}.
     *
     * @return false if there are datastore failures or pending {@link Trigger} left, true otherwise
     * // TODO: Use transactions for sequential DAO calls.
     */
    synchronized boolean performPendingAttributions() {
        List<String> pendingTriggers = mMeasurementDao.getPendingTriggerIds();
        if (pendingTriggers == null) {
            // Failure during trigger retrieval
            // Reschedule for retry
            return false;
        }
        for (int i = 0; i < pendingTriggers.size()
                && i < SystemHealthParams.MAX_ATTRIBUTIONS_PER_INVOCATION; i++) {
            boolean success = processTrigger(pendingTriggers.get(i));
            if (!success) {
                // Failure during trigger attribution
                // Reschedule for retry
                return false;
            }
        }

        // Reschedule if there are unprocessed pending triggers.
        return SystemHealthParams.MAX_ATTRIBUTIONS_PER_INVOCATION >= pendingTriggers.size();
    }

    private boolean processTrigger(String triggerId) {
        Trigger trigger = mMeasurementDao.getTrigger(triggerId);
        if (trigger.getStatus() != Trigger.Status.PENDING) {
            return true;
        }
        List<Source> matchingSources = mMeasurementDao.getMatchingActiveSources(trigger);
        if (matchingSources.isEmpty()) {
            trigger.setStatus(Trigger.Status.IGNORED);
            return mMeasurementDao.updateTriggerStatus(trigger);
        }
        matchingSources.sort((Comparator.comparingLong(Source::getPriority).reversed())
                .thenComparing(Comparator.comparingLong(Source::getEventTime).reversed()));
        Source selectedSource = matchingSources.get(0);
        matchingSources.remove(0);
        if (!matchingSources.isEmpty()) {
            matchingSources.forEach((s) -> s.setStatus(Source.Status.IGNORED));
            boolean success =
                    mMeasurementDao.updateSourceStatus(matchingSources, Source.Status.IGNORED);
            if (!success) {
                return false;
            }
        }
        if (trigger.getDedupKey() != null
                && selectedSource.getDedupKeys().contains(trigger.getDedupKey())) {
            return ignoreTrigger(trigger);
        }
        long attributionCount =
                mMeasurementDao.getAttributionsPerRateLimitWindow(selectedSource, trigger);
        if (attributionCount == -1) {
            return false;
        }
        if (attributionCount
                >= PrivacyParams.MAX_ATTRIBUTION_PER_RATE_LIMIT_WINDOW) {
            return ignoreTrigger(trigger);
        }
        EventReport newEventReport = new EventReport.Builder()
                .populateFromSourceAndTrigger(selectedSource, trigger).build();
        List<EventReport> sourceEventReports =
                mMeasurementDao.getSourceEventReports(selectedSource);
        if (isWithinReportLimit(selectedSource, sourceEventReports.size())) {
            return completeAttribution(trigger, selectedSource, newEventReport);
        }
        List<EventReport> relevantEventReports = sourceEventReports.stream()
                .filter((r) -> r.getStatus() == EventReport.Status.PENDING)
                .filter((r) -> r.getReportTime() == newEventReport.getReportTime())
                .sorted(Comparator.comparingLong(EventReport::getTriggerPriority)).collect(
                        Collectors.toList());
        if (relevantEventReports.isEmpty()) {
            return ignoreTrigger(trigger);
        }
        EventReport lowestPriorityEventReport = relevantEventReports.get(0);
        if (lowestPriorityEventReport.getTriggerPriority() >= newEventReport.getTriggerPriority()) {
            trigger.setStatus(Trigger.Status.IGNORED);
            return mMeasurementDao.updateTriggerStatus(trigger);
        }
        selectedSource.getDedupKeys().remove(lowestPriorityEventReport.getTriggerDedupKey());
        return mMeasurementDao.deleteEventReport(lowestPriorityEventReport)
                && completeAttribution(trigger, selectedSource, newEventReport);
    }

    private boolean completeAttribution(Trigger trigger, Source source, EventReport report) {
        trigger.setStatus(Trigger.Status.ATTRIBUTED);
        source.getDedupKeys().add(trigger.getDedupKey());
        return mMeasurementDao.addAttributionRateLimitEntry(source, trigger)
                && mMeasurementDao.updateTriggerStatus(trigger)
                && mMeasurementDao.updateSourceDedupKeys(source)
                && mMeasurementDao.insertEventReportToDB(report);
    }

    private boolean ignoreTrigger(Trigger trigger) {
        trigger.setStatus(Trigger.Status.IGNORED);
        return mMeasurementDao.updateTriggerStatus(trigger);
    }

    private boolean isWithinReportLimit(Source source, int existingReportCount) {
        return source.getMaxReportCount() > existingReportCount;
    }
}
