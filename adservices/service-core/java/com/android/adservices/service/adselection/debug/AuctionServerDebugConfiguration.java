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

import androidx.annotation.Nullable;

import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.ConsentedDebugConfiguration;

import com.google.auto.value.AutoValue;

/** Class to provide all debugging and egress related configurations for server auction payload */
@AutoValue
public abstract class AuctionServerDebugConfiguration {

    /** Returns the consented debug configuration. */
    @Nullable
    public abstract ConsentedDebugConfiguration getConsentedDebugConfiguration();

    /** Returns whether debug reporting is enabled. */
    public abstract boolean isDebugReportingEnabled();

    /** Returns whether prod debug is enabled. */
    public abstract boolean isProdDebugEnabled();

    /** Returns whether unlimited egress is enabled. */
    public abstract boolean isUnlimitedEgressEnabled();

    /** Returns a new builder for {@link AuctionServerDebugConfiguration}. */
    public static AuctionServerDebugConfiguration.Builder builder() {
        return new AutoValue_AuctionServerDebugConfiguration.Builder();
    }

    /** Returns a string representation of {@link AuctionServerDebugConfiguration}. */
    public final String toString() {
        StringBuilder stringBuilder =
                new StringBuilder()
                        .append("isDebugReportingEnabled: ")
                        .append(isDebugReportingEnabled())
                        .append(" isProdDebugEnabled: ")
                        .append(isProdDebugEnabled())
                        .append(" isUnlimitedEgressEnabled: ")
                        .append(isUnlimitedEgressEnabled())
                        .append(" consentedDebugConfiguration: ");
        if (getConsentedDebugConfiguration() == null) {
            stringBuilder.append(" null");
        } else {
            stringBuilder
                    .append(" isConsented: ")
                    .append(getConsentedDebugConfiguration().getIsConsented())
                    .append(", token: ")
                    .append(getConsentedDebugConfiguration().getToken())
                    .append(", isDebugInfoInResponse: ")
                    .append(getConsentedDebugConfiguration().getIsDebugInfoInResponse());
        }
        return stringBuilder.toString();
    }

    /** Builder for {@link AuctionServerDebugConfiguration}. */
    @AutoValue.Builder
    public abstract static class Builder {

        /** Sets the consented debug configuration. */
        public abstract Builder setConsentedDebugConfiguration(
                @Nullable ConsentedDebugConfiguration consentedDebugConfiguration);

        /** Sets whether debug reporting is enabled. */
        public abstract Builder setDebugReportingEnabled(boolean isDebugReportingEnabled);

        /** Sets whether prod debug is enabled. */
        public abstract Builder setProdDebugEnabled(boolean isProdDebugEnabled);

        /** Sets whether unlimited egress is enabled. */
        public abstract Builder setUnlimitedEgressEnabled(boolean isUnlimitedEgressEnabled);

        /** Builds a new {@link AuctionServerDebugConfiguration}. */
        public abstract AuctionServerDebugConfiguration build();
    }
}
