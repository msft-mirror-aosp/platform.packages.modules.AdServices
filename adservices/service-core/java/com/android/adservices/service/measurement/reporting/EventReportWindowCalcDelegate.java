/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.measurement.reporting;

import android.annotation.NonNull;

import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.PrivacyParams;
import com.android.adservices.service.measurement.ReportSpec;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Does event report window related calculations, e.g. count, reporting time. */
public class EventReportWindowCalcDelegate {
    private static final long ONE_HOUR_IN_MILLIS = TimeUnit.HOURS.toMillis(1);

    private Flags mFlags;

    public EventReportWindowCalcDelegate(@NonNull Flags flags) {
        mFlags = flags;
    }

    /**
     * Max reports count based on conversion destination type and installation state.
     *
     * @return maximum number of reports allowed
     * @param isInstallCase is app installed
     */
    public int getMaxReportCount(@NonNull Source source, boolean isInstallCase) {
        if (isInstallCase) {
            return source.getSourceType() == Source.SourceType.EVENT
                    ? PrivacyParams.INSTALL_ATTR_EVENT_SOURCE_MAX_REPORTS
                    : PrivacyParams.INSTALL_ATTR_NAVIGATION_SOURCE_MAX_REPORTS;
        }
        return source.getSourceType() == Source.SourceType.EVENT
                ? PrivacyParams.EVENT_SOURCE_MAX_REPORTS
                : PrivacyParams.NAVIGATION_SOURCE_MAX_REPORTS;
    }

    /**
     * Calculates the reporting time based on the {@link Trigger} time, {@link Source}'s expiry and
     * trigger destination type.
     *
     * @return the reporting time
     */
    public long getReportingTime(
            @NonNull Source source, long triggerTime, @EventSurfaceType int destinationType) {
        if (triggerTime < source.getEventTime()) {
            return -1;
        }

        // Cases where source could have both web and app destinations, there if the trigger
        // destination is an app and it was installed, then installState should be considered true.
        boolean isAppInstalled = isAppInstalled(source, destinationType);
        List<Long> reportingWindows = getEarlyReportingWindows(source, isAppInstalled);
        for (Long window : reportingWindows) {
            if (triggerTime <= window) {
                return window + ONE_HOUR_IN_MILLIS;
            }
        }
        return source.getEventReportWindow() + ONE_HOUR_IN_MILLIS;
    }

    /**
     * Return reporting time by index for noising based on the index
     *
     * @param windowIndex index of the reporting window for which
     * @return reporting time in milliseconds
     */
    public long getReportingTimeForNoising(
            @NonNull Source source, int windowIndex, boolean isInstallCase) {
        List<Long> windowList = getEarlyReportingWindows(source, isInstallCase);
        return windowIndex < windowList.size()
                ? windowList.get(windowIndex) + ONE_HOUR_IN_MILLIS
                : source.getEventReportWindow() + ONE_HOUR_IN_MILLIS;
    }

    /**
     * Returns event reporting windows count for noising cases.
     *
     * @param source source for which the count is requested
     * @param isInstallCase true of cooldown window was specified
     */
    public int getReportingWindowCountForNoising(@NonNull Source source, boolean isInstallCase) {
        // Early Count + expiry
        return getEarlyReportingWindows(source, isInstallCase).size() + 1;
    }

    private boolean isAppInstalled(Source source, int destinationType) {
        return destinationType == EventSurfaceType.APP && source.isInstallAttributed();
    }

    private ImmutableList<Long> getEarlyReportingWindows(Source source, boolean installState) {
        long[] earlyWindows;
        if (installState) {
            earlyWindows =
                    source.getSourceType() == Source.SourceType.EVENT
                            ? PrivacyParams.INSTALL_ATTR_EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS
                            : PrivacyParams
                                    .INSTALL_ATTR_NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS;
        } else {
            earlyWindows =
                    source.getSourceType() == Source.SourceType.EVENT
                            ? PrivacyParams.EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS
                            : PrivacyParams.NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS;
        }

        List<Long> windowList = new ArrayList<>();

        for (long windowDelta : earlyWindows) {
            long window = source.getEventTime() + windowDelta;
            if (source.getEventReportWindow() <= window) {
                continue;
            }
            windowList.add(window);
        }
        return ImmutableList.copyOf(windowList);
    }

    /**
     * Returns reporting time for noising with flex event API.
     *
     * @param windowIndex window index corresponding to which the reporting time should be returned
     * @param triggerDataIndex trigger data state index
     * @param reportSpec flex event report spec
     */
    public long getReportingTimeForNoisingFlexEventAPI(
            int windowIndex, int triggerDataIndex, ReportSpec reportSpec) {
        return reportSpec.getWindowEndTime(triggerDataIndex, windowIndex) + ONE_HOUR_IN_MILLIS;
    }
}
