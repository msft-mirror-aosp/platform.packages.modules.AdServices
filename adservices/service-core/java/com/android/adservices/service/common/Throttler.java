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

import android.util.Log;
import android.util.Pair;

import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.RateLimiter;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Class to throttle PPAPI requests. */
public final class Throttler {

    private static final String TAG = Throttler.class.getSimpleName();

    // Enum for each PP API or entry point that will be throttled.
    public enum ApiKey {
        UNKNOWN,

        // Key to throttle AdId API, based on app package name.
        ADID_API_APP_PACKAGE_NAME,

        // Key to throttle AppSetId API, based on app package name.
        APPSETID_API_APP_PACKAGE_NAME,

        // Key to throttle Join Custom Audience API
        FLEDGE_API_JOIN_CUSTOM_AUDIENCE,

        // Key to throttle Fetch Custom Audience API
        FLEDGE_API_FETCH_CUSTOM_AUDIENCE,

        // Key to throttle Leave Custom Audience API
        FLEDGE_API_LEAVE_CUSTOM_AUDIENCE,

        // Key to throttle Report impressions API
        FLEDGE_API_REPORT_IMPRESSIONS,

        // Key to throttle Report impressions API
        FLEDGE_API_REPORT_INTERACTION,

        // Key to throttle Select Ads API
        FLEDGE_API_SELECT_ADS,

        // Key to throttle Get Ad Selection Data API
        FLEDGE_API_GET_AD_SELECTION_DATA,
        // Key to throttle Persist Ad Selection Result API
        FLEDGE_API_PERSIST_AD_SELECTION_RESULT,

        // Key to throttle Schedule Custom Audience Update API
        FLEDGE_API_SCHEDULE_CUSTOM_AUDIENCE_UPDATE,

        // Key to throttle Set App Install Advertisers API
        FLEDGE_API_SET_APP_INSTALL_ADVERTISERS,

        // Key to throttle FLEDGE updateAdCounterHistogram API
        FLEDGE_API_UPDATE_AD_COUNTER_HISTOGRAM,

        // Key to throttle Measurement Deletion Registration API
        MEASUREMENT_API_DELETION_REGISTRATION,

        // Key to throttle Measurement Register Source API
        MEASUREMENT_API_REGISTER_SOURCE,

        // Key to throttle Measurement Register Trigger API
        MEASUREMENT_API_REGISTER_TRIGGER,

        // Key to throttle Measurement Register Web Source API
        MEASUREMENT_API_REGISTER_WEB_SOURCE,

        // Key to throttle Measurement Register Web Trigger API
        MEASUREMENT_API_REGISTER_WEB_TRIGGER,

        // Key to throttle Measurement Register Sources API
        MEASUREMENT_API_REGISTER_SOURCES,
        // Key to throttle updateSignals API
        PROTECTED_SIGNAL_API_UPDATE_SIGNALS,

        // Key to throttle Topics API based on the App Package Name.
        TOPICS_API_APP_PACKAGE_NAME,

        // Key to throttle Topics API based on the Sdk Name.
        TOPICS_API_SDK_NAME,

        // Key to throttle select ads with outcomes api
        FLEDGE_API_SELECT_ADS_WITH_OUTCOMES,
    }

    private static final double DEFAULT_RATE_LIMIT = 1d;

    // A Map from a Pair<ApiKey, Requester> to its RateLimiter.
    // The Requester could be a SdkName or an AppPackageName depending on the rate limiting needs.
    // Example Pair<TOPICS_API, "SomeSdkName">, Pair<TOPICS_API, "SomePackageName">.
    private final ConcurrentHashMap<Pair<ApiKey, String>, RateLimiter> mSdkRateLimitMap =
            new ConcurrentHashMap<>();

    // Used as a configuration to determine the rate limit per API
    // Example:
    // - TOPICS_API_SDK_NAME, 1 request per second
    // - MEASUREMENT_API_REGISTER_SOURCE, 5 requests per second
    private final Map<ApiKey, Double> mRateLimitPerApiMap = new HashMap<>();

    // Lazy initialization holder class idiom for static fields as described in Effective Java Item
    // 83 - this is needed because otherwise the singleton would be initialized in unit tests, even
    // when they (correctly) call newInstance() instead of getInstance().
    private static final class FieldHolder {
        private static final Throttler sSingleton;

        static {
            Flags flags = FlagsFactory.getFlags();
            Log.v(TAG, "Initializing singleton with " + flags);
            sSingleton = new Throttler(flags);
        }
    }

    /** Returns the singleton instance of the Throttler. */
    public static Throttler getInstance() {
        return FieldHolder.sSingleton;
    }

    /** Factory method - should only be used for tests. */
    @VisibleForTesting
    public static Throttler newInstance(Flags flags) {
        return new Throttler(flags);
    }

    private Throttler(Flags flags) {
        Objects.requireNonNull(flags, "flags cannot be null");
        setRateLimitPerApiMap(flags);
    }

    /**
     * Acquires a permit for an API and a Requester if it can be acquired immediately without delay.
     * Example: {@code tryAcquire(TOPICS_API, "SomeSdkName") }
     *
     * @return {@code true} if the permit was acquired, {@code false} otherwise
     */
    public boolean tryAcquire(ApiKey apiKey, String requester) {
        // Negative Permits Per Second turns off rate limiting.
        double permitsPerSecond = mRateLimitPerApiMap.getOrDefault(apiKey, DEFAULT_RATE_LIMIT);
        if (permitsPerSecond <= 0) {
            return true;
        }

        RateLimiter rateLimiter =
                mSdkRateLimitMap.computeIfAbsent(
                        Pair.create(apiKey, requester), ignored -> create(permitsPerSecond));

        return rateLimiter.tryAcquire();
    }

    /** Configures permits per second per {@link ApiKey} */
    private void setRateLimitPerApiMap(Flags flags) {
        // Set default values first
        double defaultPermitsPerSecond = flags.getSdkRequestPermitsPerSecond();
        for (var key : ApiKey.values()) {
            mRateLimitPerApiMap.put(key, defaultPermitsPerSecond);
        }

        // Then override some using flags:
        double adIdPermitsPerSecond = flags.getAdIdRequestPermitsPerSecond();
        double appSetIdPermitsPerSecond = flags.getAppSetIdRequestPermitsPerSecond();
        double registerSource = flags.getMeasurementRegisterSourceRequestPermitsPerSecond();
        double registerWebSource = flags.getMeasurementRegisterWebSourceRequestPermitsPerSecond();
        double registerSources = flags.getMeasurementRegisterSourcesRequestPermitsPerSecond();
        double registerTrigger = flags.getMeasurementRegisterTriggerRequestPermitsPerSecond();
        double registerWebTrigger = flags.getMeasurementRegisterWebTriggerRequestPermitsPerSecond();
        double topicsApiAppRequestPermitsPerSecond = flags.getTopicsApiAppRequestPermitsPerSecond();
        double topicsApiSdkRequestPermitsPerSecond = flags.getTopicsApiSdkRequestPermitsPerSecond();
        double fledgeJoinCustomAudienceRequestPermitsPerSecond =
                flags.getFledgeJoinCustomAudienceRequestPermitsPerSecond();
        double fledgeFetchAndJoinCustomAudienceRequestPermitsPerSecond =
                flags.getFledgeFetchAndJoinCustomAudienceRequestPermitsPerSecond();
        double fledgeScheduleCustomAudienceUpdateRequestPermitsPerSecond =
                flags.getFledgeScheduleCustomAudienceUpdateRequestPermitsPerSecond();
        double fledgeLeaveCustomAudienceRequestPermitsPerSecond =
                flags.getFledgeLeaveCustomAudienceRequestPermitsPerSecond();
        double fledgeUpdateSignalsRequestPermitsPerSecond =
                flags.getFledgeUpdateSignalsRequestPermitsPerSecond();
        double fledgeSelectAdsRequestPermitsPerSecond =
                flags.getFledgeSelectAdsRequestPermitsPerSecond();
        double fledgeSelectAdsWithOutcomesRequestPermitsPerSecond =
                flags.getFledgeSelectAdsWithOutcomesRequestPermitsPerSecond();
        double fledgeGetAdSelectionDataRequestPermitsPerSecond =
                flags.getFledgeGetAdSelectionDataRequestPermitsPerSecond();
        double fledgeReportImpressionRequestPermitsPerSecond =
                flags.getFledgeReportImpressionRequestPermitsPerSecond();
        double fledgeReportInteractionRequestPermitsPerSecond =
                flags.getFledgeReportInteractionRequestPermitsPerSecond();
        double fledgePersistAdSelectionResultRequestPermitsPerSecond =
                flags.getFledgePersistAdSelectionResultRequestPermitsPerSecond();
        double fledgeSetAppInstallAdvertisersRequestPermitsPerSecond =
                flags.getFledgeSetAppInstallAdvertisersRequestPermitsPerSecond();
        double fledgeUpdateAdCounterHistogramRequestPermitsPerSecond =
                flags.getFledgeUpdateAdCounterHistogramRequestPermitsPerSecond();

        mRateLimitPerApiMap.put(ApiKey.ADID_API_APP_PACKAGE_NAME, adIdPermitsPerSecond);
        mRateLimitPerApiMap.put(ApiKey.APPSETID_API_APP_PACKAGE_NAME, appSetIdPermitsPerSecond);

        mRateLimitPerApiMap.put(ApiKey.MEASUREMENT_API_REGISTER_SOURCE, registerSource);
        mRateLimitPerApiMap.put(ApiKey.MEASUREMENT_API_REGISTER_TRIGGER, registerTrigger);
        mRateLimitPerApiMap.put(ApiKey.MEASUREMENT_API_REGISTER_WEB_SOURCE, registerWebSource);
        mRateLimitPerApiMap.put(ApiKey.MEASUREMENT_API_REGISTER_WEB_TRIGGER, registerWebTrigger);
        mRateLimitPerApiMap.put(ApiKey.MEASUREMENT_API_REGISTER_SOURCES, registerSources);

        mRateLimitPerApiMap.put(
                ApiKey.TOPICS_API_APP_PACKAGE_NAME, topicsApiAppRequestPermitsPerSecond);
        mRateLimitPerApiMap.put(ApiKey.TOPICS_API_SDK_NAME, topicsApiSdkRequestPermitsPerSecond);
        mRateLimitPerApiMap.put(
                ApiKey.FLEDGE_API_JOIN_CUSTOM_AUDIENCE,
                fledgeJoinCustomAudienceRequestPermitsPerSecond);
        mRateLimitPerApiMap.put(
                ApiKey.FLEDGE_API_FETCH_CUSTOM_AUDIENCE,
                fledgeFetchAndJoinCustomAudienceRequestPermitsPerSecond);
        mRateLimitPerApiMap.put(
                ApiKey.FLEDGE_API_SCHEDULE_CUSTOM_AUDIENCE_UPDATE,
                fledgeScheduleCustomAudienceUpdateRequestPermitsPerSecond);
        mRateLimitPerApiMap.put(
                ApiKey.FLEDGE_API_LEAVE_CUSTOM_AUDIENCE,
                fledgeLeaveCustomAudienceRequestPermitsPerSecond);
        mRateLimitPerApiMap.put(
                ApiKey.PROTECTED_SIGNAL_API_UPDATE_SIGNALS,
                fledgeUpdateSignalsRequestPermitsPerSecond);
        mRateLimitPerApiMap.put(
                ApiKey.FLEDGE_API_SELECT_ADS, fledgeSelectAdsRequestPermitsPerSecond);
        mRateLimitPerApiMap.put(
                ApiKey.FLEDGE_API_SELECT_ADS_WITH_OUTCOMES,
                fledgeSelectAdsWithOutcomesRequestPermitsPerSecond);
        mRateLimitPerApiMap.put(
                ApiKey.FLEDGE_API_GET_AD_SELECTION_DATA,
                fledgeGetAdSelectionDataRequestPermitsPerSecond);
        mRateLimitPerApiMap.put(
                ApiKey.FLEDGE_API_REPORT_IMPRESSIONS,
                fledgeReportImpressionRequestPermitsPerSecond);
        mRateLimitPerApiMap.put(
                ApiKey.FLEDGE_API_REPORT_INTERACTION,
                fledgeReportInteractionRequestPermitsPerSecond);
        mRateLimitPerApiMap.put(
                ApiKey.FLEDGE_API_PERSIST_AD_SELECTION_RESULT,
                fledgePersistAdSelectionResultRequestPermitsPerSecond);
        mRateLimitPerApiMap.put(
                ApiKey.FLEDGE_API_SET_APP_INSTALL_ADVERTISERS,
                fledgeSetAppInstallAdvertisersRequestPermitsPerSecond);
        mRateLimitPerApiMap.put(
                ApiKey.FLEDGE_API_UPDATE_AD_COUNTER_HISTOGRAM,
                fledgeUpdateAdCounterHistogramRequestPermitsPerSecond);
    }

    /**
     * Creates a Burst RateLimiter. This is a workaround since Guava does not support RateLimiter
     * with initial Burst.
     *
     * <p>The RateLimiter is created with {@link Double#POSITIVE_INFINITY} to open all permit slots
     * immediately. It is immediately overridden to the expected rate based on the permitsPerSecond
     * parameter. Then {@link RateLimiter#tryAcquire()} is called to use the first acquisition so
     * the expected bursting rate could kick in on the following calls. This flow enables initial
     * bursting, multiple simultaneous permits would be acquired as soon as RateLimiter is created.
     * Otherwise, if only {@link RateLimiter#create(double)} is called, after the 1st call
     * subsequent request would have to be spread out evenly over 1 second.
     */
    private RateLimiter create(double permitsPerSecond) {
        RateLimiter rateLimiter = RateLimiter.create(Double.POSITIVE_INFINITY);
        rateLimiter.setRate(permitsPerSecond);
        boolean unused = rateLimiter.tryAcquire();
        return rateLimiter;
    }

    /** Dump it! */
    public void dump(PrintWriter writer) {
        writer.println("Throttler");

        writer.println("  Rate limit per API");
        dumpsSortedMap(
                writer,
                mRateLimitPerApiMap,
                entry ->
                        String.format(
                                Locale.ENGLISH,
                                "%s: %3.2f",
                                entry.getKey(),
                                (Double) entry.getValue()));

        writer.printf("  SDK rate limit per API:");
        if (mSdkRateLimitMap.isEmpty()) {
            writer.println(" N/A");
            return;
        }
        writer.println();
        dumpsSortedMap(
                writer,
                mSdkRateLimitMap,
                entry -> {
                    Pair<?, ?> pair = (Pair<?, ?>) entry.getKey();
                    return String.format(
                            Locale.ENGLISH, "%s.%s: %s", pair.first, pair.second, entry.getValue());
                });
    }

    private void dumpsSortedMap(
            PrintWriter writer,
            Map<?, ?> map,
            Function<Map.Entry<?, ?>, String> entryStringanizer) {
        map.entrySet().stream()
                .map(entryStringanizer)
                .sorted()
                .forEachOrdered(line -> writer.printf("    %s\n", line));
    }
}
