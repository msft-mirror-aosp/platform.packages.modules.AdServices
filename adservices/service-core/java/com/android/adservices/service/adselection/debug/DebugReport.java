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

import android.adservices.common.AdTechIdentifier;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.adservices.data.adselection.CustomAudienceSignals;

import com.google.auto.value.AutoValue;

/**
 * Stores debugging information for buy-side, sell-side of a custom audience's outcome in an ad
 * selection auction.
 */
@AutoValue
public abstract class DebugReport {

    /** Gets the custom audience signals for debug report. */
    @NonNull
    public abstract CustomAudienceSignals getCustomAudienceSignals();

    /** Gets the seller adtech identifier for debug report. */
    @Nullable
    public abstract AdTechIdentifier getSeller();

    /** Gets the win URI for debug report. */
    @Nullable
    public abstract Uri getWinDebugReportUri();

    /** Gets the loss URI for debug report. */
    @Nullable
    public abstract Uri getLossDebugReportUri();

    /** Gets the seller rejection reason for debug report. */
    @Nullable
    public abstract String getSellerRejectReason();

    /** Builder for {@link DebugReport} */
    public static Builder builder() {
        return new AutoValue_DebugReport.Builder();
    }

    /** Builder class for {@link DebugReport} */
    @AutoValue.Builder
    public abstract static class Builder {

        /** Sets the custom audience signals in DebugReport#Builder. */
        public abstract Builder setCustomAudienceSignals(
                CustomAudienceSignals customAudienceSignals);

        /** Sets the seller in DebugReport#Builder. */
        public abstract Builder setSeller(AdTechIdentifier seller);

        /** Sets the win debug report URI in DebugReport#Builder. */
        public abstract Builder setWinDebugReportUri(Uri winDebugReportUri);

        /** Sets the loss debug report URI in DebugReport#Builder. */
        public abstract Builder setLossDebugReportUri(Uri lossDebugReportUri);

        /** Sets the seller reject reason in DebugReport#Builder. */
        public abstract Builder setSellerRejectReason(String sellerRejectReason);

        /** Builds DebugReport */
        public abstract DebugReport build();
    }
}
