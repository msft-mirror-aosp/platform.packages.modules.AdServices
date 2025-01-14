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

package com.android.adservices.flags;

import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_DATABASE_SCHEMA_VERSION_8;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_LOGGED_TOPIC;
import static com.android.adservices.service.FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_SCHEDULE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_SIGNALS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_APP_PACKAGE_NAME_LOGGING_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_KEY_FETCH_METRICS_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_BEACON_REPORTING_METRICS_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_ENABLE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ADDITIONAL_SCHEDULE_REQUESTS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_MIN_DELAY_MINS_OVERRIDE;
import static com.android.adservices.service.FlagsConstants.KEY_GA_UX_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_GLOBAL_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_PAS_EXTENDED_METRICS_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_PROTECTED_SIGNALS_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_PROTECTED_SIGNALS_PERIODIC_ENCODING_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_PERIOD_MS;

import static org.mockito.Mockito.when;

import com.android.adservices.service.Flags;
import com.android.adservices.shared.testing.AndroidLogger;
import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.NameValuePair;
import com.android.adservices.shared.testing.mockito.MockitoHelper;

import org.mockito.stubbing.Answer;

import java.util.Objects;

/**
 * @deprecated tests should use {@link AdServicesFakeFlagsSetterRule} instead.
 */
@Deprecated
public final class AdServicesMockFlagsSetterRule
        extends AdServicesFlagsSetterRuleForUnitTests<AdServicesMockFlagsSetterRule> {

    private static final Logger sLog =
            new Logger(AndroidLogger.getInstance(), AdServicesMockFlagsSetterRule.class);

    /**
     * Default constructor.
     *
     * @param mockFlags mock whose expectations will be set
     */
    public AdServicesMockFlagsSetterRule(Flags mockFlags) {
        super(
                mockFlags,
                f -> {
                    setExpectation(mockFlags, f);
                    return null;
                });
        if (!MockitoHelper.isMock(mockFlags)) {
            throw new IllegalArgumentException("not a mock: " + mockFlags);
        }
    }

    @Override
    public AdServicesMockFlagsSetterRule setMissingFlagBehavior(MissingFlagBehavior behavior) {
        mLog.e("setMissingFlagBehavior(%b)", behavior);
        if (!MissingFlagBehavior.USES_JAVA_LANGUAGE_DEFAULT.equals(behavior)) {
            throw new UnsupportedOperationException(
                    "can only call with "
                            + MissingFlagBehavior.USES_JAVA_LANGUAGE_DEFAULT
                            + "(which is a no-op regardless), but called with "
                            + behavior);
        }
        return getThis();
    }

    @Override
    public Flags getFlagsSnapshot() {
        throw new UnsupportedOperationException("cannot clone mock flags");
    }

    private static Answer<Boolean> answerBoolean(NameValuePair flag) {
        return inv -> {
            boolean result = Boolean.valueOf(flag.value);
            sLog.v("%s: returning %b", MockitoHelper.toString(inv), result);
            return result;
        };
    }

    // NOTE: new flags should be added to the switch statement on demand.
    // We could use reflection / code generation to support all flags, but in the long term it'd be
    // better for them to use AdServicesFakeFlagsSetterRule instead...
    private static void setExpectation(Flags mockFlags, NameValuePair flag) {
        sLog.v("setExpectation(%s)", flag);
        Objects.requireNonNull(flag, "internal error: NameValuePair cannot be null");

        switch (flag.name) {
                // Main kill switches
            case KEY_GLOBAL_KILL_SWITCH:
                when(mockFlags.getGlobalKillSwitch()).then(answerBoolean(flag));
                return;
                // Used by setFlagsForTests
            case KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS:
                when(mockFlags.getAdSelectionBiddingTimeoutPerCaMs())
                        .thenReturn(Long.valueOf(flag.value));
                return;
            case KEY_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS:
                when(mockFlags.getAdSelectionScoringTimeoutMs())
                        .thenReturn(Long.valueOf(flag.value));
                return;
            case KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED:
                when(mockFlags.getFledgeRegisterAdBeaconEnabled())
                        .thenReturn(Boolean.valueOf(flag.value));
                return;
            case KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_ENABLED:
                when(mockFlags.getFledgeFetchCustomAudienceEnabled())
                        .thenReturn(Boolean.valueOf(flag.value));
                return;
            case KEY_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS:
                when(mockFlags.getAdSelectionOverallTimeoutMs())
                        .thenReturn(Long.valueOf(flag.value));
                return;
            case KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_MIN_DELAY_MINS_OVERRIDE:
                when(mockFlags.getFledgeScheduleCustomAudienceMinDelayMinsOverride())
                        .thenReturn(Integer.valueOf(flag.value));
                return;
            case KEY_ENABLE_LOGGED_TOPIC:
                when(mockFlags.getEnableLoggedTopic()).thenReturn(Boolean.valueOf(flag.value));
                return;
            case KEY_ENABLE_DATABASE_SCHEMA_VERSION_8:
                when(mockFlags.getEnableDatabaseSchemaVersion8())
                        .thenReturn(Boolean.valueOf(flag.value));
                return;
            case KEY_FLEDGE_AUCTION_SERVER_ENABLED:
                when(mockFlags.getFledgeAuctionServerEnabled())
                        .thenReturn(Boolean.valueOf(flag.value));
                return;
            case KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_ENABLED:
                when(mockFlags.getFledgeEventLevelDebugReportingEnabled())
                        .thenReturn(Boolean.valueOf(flag.value));
                return;
            case KEY_FLEDGE_BEACON_REPORTING_METRICS_ENABLED:
                when(mockFlags.getFledgeBeaconReportingMetricsEnabled())
                        .thenReturn(Boolean.valueOf(flag.value));
                return;
            case KEY_FLEDGE_APP_PACKAGE_NAME_LOGGING_ENABLED:
                when(mockFlags.getFledgeAppPackageNameLoggingEnabled())
                        .thenReturn(Boolean.valueOf(flag.value));
                return;
            case KEY_FLEDGE_AUCTION_SERVER_KEY_FETCH_METRICS_ENABLED:
                when(mockFlags.getFledgeAuctionServerKeyFetchMetricsEnabled())
                        .thenReturn(Boolean.valueOf(flag.value));
                return;
            case KEY_PAS_EXTENDED_METRICS_ENABLED:
                when(mockFlags.getPasExtendedMetricsEnabled())
                        .thenReturn(Boolean.valueOf(flag.value));
                return;

                // Used by ScheduleCustomAudienceUpdateImplTest
            case KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK:
                when(mockFlags.getDisableFledgeEnrollmentCheck())
                        .thenReturn(Boolean.valueOf(flag.value));
                return;
            case KEY_ENFORCE_FOREGROUND_STATUS_SCHEDULE_CUSTOM_AUDIENCE:
                when(mockFlags.getEnforceForegroundStatusForScheduleCustomAudience())
                        .thenReturn(Boolean.valueOf(flag.value));
                return;
            case KEY_ENFORCE_FOREGROUND_STATUS_SIGNALS:
                when(mockFlags.getEnforceForegroundStatusForSignals())
                        .thenReturn(Boolean.valueOf(flag.value));
                return;
            case KEY_FLEDGE_ENABLE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ADDITIONAL_SCHEDULE_REQUESTS:
                when(mockFlags
                                .getFledgeEnableScheduleCustomAudienceUpdateAdditionalScheduleRequests())
                        .thenReturn(Boolean.valueOf(flag.value));
                return;
            case KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ENABLED:
                when(mockFlags.getFledgeScheduleCustomAudienceUpdateEnabled())
                        .thenReturn(Boolean.valueOf(flag.value));
                return;

                // Used by PeriodicEncodingJobServiceTest
            case KEY_GA_UX_FEATURE_ENABLED:
                when(mockFlags.getGaUxFeatureEnabled()).thenReturn(Boolean.valueOf(flag.value));
                return;
            case KEY_PROTECTED_SIGNALS_ENABLED:
                when(mockFlags.getProtectedSignalsEnabled())
                        .thenReturn(Boolean.valueOf(flag.value));
                return;
            case KEY_PROTECTED_SIGNALS_PERIODIC_ENCODING_ENABLED:
                when(mockFlags.getProtectedSignalsPeriodicEncodingEnabled())
                        .thenReturn(Boolean.valueOf(flag.value));
                return;
            case KEY_PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_PERIOD_MS:
                when(mockFlags.getProtectedSignalPeriodicEncodingJobPeriodMs())
                        .thenReturn(Long.valueOf(flag.value));
                return;

            default:
                throw new UnsupportedOperationException("Don't know how to mock " + flag);
        }
    }
}
