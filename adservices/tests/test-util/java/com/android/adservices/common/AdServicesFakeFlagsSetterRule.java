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

package com.android.adservices.common;

import com.android.adservices.common.AdServicesFakeFlagsSetterRule.FakeFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsConstants;
import com.android.adservices.shared.testing.AndroidLogger;
import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.NameValuePair;

import com.google.common.annotations.VisibleForTesting;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** {@code FlagsSetterRule} that uses a fake flags implementation. */
public final class AdServicesFakeFlagsSetterRule
        extends AdServicesFlagsSetterRuleForUnitTests<AdServicesFakeFlagsSetterRule, FakeFlags> {

    /** Default constructor. */
    public AdServicesFakeFlagsSetterRule() {
        this(new FakeFlags());
    }

    // TODO(b/384798806): remove if not used.
    @VisibleForTesting
    AdServicesFakeFlagsSetterRule(FakeFlags fakeFlags) {
        super(fakeFlags, f -> fakeFlags.set(f));
    }

    // NOTE: this class is private on purpose, so tests use the rule approach. But we could make it
    // standalone if needed (but it must be public because it's used in the constructor that takes a
    // Consumer<NameValuePair> lambda
    public static final class FakeFlags implements Flags {

        private final Logger mLog =
                new Logger(AndroidLogger.getInstance(), AdServicesFakeFlagsSetterRule.class);

        private final Map<String, NameValuePair> mFlags = new HashMap<>();

        private void set(NameValuePair flag) {
            mLog.v("set(%s)", flag);
            Objects.requireNonNull(flag, "internal error: NameValuePair cannot be null");
            mFlags.put(flag.name, flag);
        }

        private boolean getBoolean(String name) {
            NameValuePair nvp = mFlags.get(name);
            boolean value = nvp == null ? false : Boolean.valueOf(nvp.value);
            mLog.v("getBoolean(%s): mapping=%s, returning %b", name, nvp, value);
            return value;
        }

        // TODO(b/384798806): remove methods below once it extends AbstractFlags
        private String getString(String name) {
            NameValuePair nvp = mFlags.get(name);
            String value = nvp.value;
            mLog.v("getString(%s): mapping=%s, returning %s", name, nvp, value);
            return value;
        }

        private long getLong(String name) {
            NameValuePair nvp = mFlags.get(name);
            long value = nvp == null ? -1 : Long.valueOf(nvp.value);
            mLog.v("getLong(%s): mapping=%s, returning %d", name, nvp, value);
            return value;
        }

        private int getInt(String name) {
            NameValuePair nvp = mFlags.get(name);
            int value = nvp == null ? -1 : Integer.valueOf(nvp.value);
            mLog.v("getInt(%s): mapping=%s, returning %d", name, nvp, value);
            return value;
        }

        @Override
        public boolean getGlobalKillSwitch() {
            return getBoolean(FlagsConstants.KEY_GLOBAL_KILL_SWITCH);
        }

        // Used by ScheduleCustomAudienceUpdateImplTest
        @Override
        public boolean getFledgeFrequencyCapFilteringEnabled() {
            return getBoolean(FlagsConstants.KEY_FLEDGE_FREQUENCY_CAP_FILTERING_ENABLED);
        }

        @Override
        public boolean getFledgeAppInstallFilteringEnabled() {
            return getBoolean(FlagsConstants.KEY_FLEDGE_APP_INSTALL_FILTERING_ENABLED);
        }

        // Used by SignalsIntakeE2ETest
        @Override
        public String getPasAppAllowList() {
            return getString(FlagsConstants.KEY_PAS_APP_ALLOW_LIST);
        }

        // Used by setDefaultFledgeFlags()
        @Override
        public long getAdSelectionBiddingTimeoutPerCaMs() {
            return getLong(FlagsConstants.KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS);
        }

        @Override
        public long getAdSelectionScoringTimeoutMs() {
            return getLong(FlagsConstants.KEY_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS);
        }

        @Override
        public long getAdSelectionOverallTimeoutMs() {
            return getLong(FlagsConstants.KEY_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS);
        }

        @Override
        public boolean getDisableFledgeEnrollmentCheck() {
            return getBoolean(FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK);
        }

        @Override
        public boolean getFledgeRegisterAdBeaconEnabled() {
            return getBoolean(FlagsConstants.KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED);
        }

        @Override
        public boolean getFledgeFetchCustomAudienceEnabled() {
            return getBoolean(FlagsConstants.KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_ENABLED);
        }

        @Override
        public boolean getFledgeScheduleCustomAudienceUpdateEnabled() {
            return getBoolean(FlagsConstants.KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ENABLED);
        }

        @Override
        public int getFledgeScheduleCustomAudienceMinDelayMinsOverride() {
            return getInt(
                    FlagsConstants
                            .KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_MIN_DELAY_MINS_OVERRIDE);
        }

        @Override
        public boolean getEnableLoggedTopic() {
            return getBoolean(FlagsConstants.KEY_ENABLE_LOGGED_TOPIC);
        }

        @Override
        public boolean getEnableDatabaseSchemaVersion8() {
            return getBoolean(FlagsConstants.KEY_ENABLE_DATABASE_SCHEMA_VERSION_8);
        }

        @Override
        public boolean getFledgeAuctionServerEnabled() {
            return getBoolean(FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_ENABLED);
        }

        @Override
        public boolean getFledgeEventLevelDebugReportingEnabled() {
            return getBoolean(FlagsConstants.KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_ENABLED);
        }

        @Override
        public boolean getFledgeBeaconReportingMetricsEnabled() {
            return getBoolean(FlagsConstants.KEY_FLEDGE_BEACON_REPORTING_METRICS_ENABLED);
        }

        @Override
        public boolean getFledgeAppPackageNameLoggingEnabled() {
            return getBoolean(FlagsConstants.KEY_FLEDGE_APP_PACKAGE_NAME_LOGGING_ENABLED);
        }

        @Override
        public boolean getFledgeAuctionServerKeyFetchMetricsEnabled() {
            return getBoolean(FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_KEY_FETCH_METRICS_ENABLED);
        }

        @Override
        public boolean getPasExtendedMetricsEnabled() {
            return getBoolean(FlagsConstants.KEY_PAS_EXTENDED_METRICS_ENABLED);
        }
    }
}
