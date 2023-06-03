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

package com.android.adservices.service.adselection;

import android.adservices.common.AdTechIdentifier;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;

@AutoValue
abstract class ScoreAdResult {
    abstract Double getAdScore();

    @Nullable
    abstract String getCustomAudienceName();

    @Nullable
    abstract String getOwnerAppPackage();

    @Nullable
    abstract AdTechIdentifier getPublisher();

    @Nullable
    abstract AdTechIdentifier getCustomAudienceBuyer();

    @Nullable
    abstract Uri getWinDebugReportUri();

    @Nullable
    abstract Uri getLossDebugReportUri();

    @Nullable
    abstract String getSellerRejectReason();

    static Builder builder() {
        return new AutoValue_ScoreAdResult.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder setAdScore(Double adScore);

        abstract Builder setCustomAudienceName(String customAudienceName);

        abstract Builder setOwnerAppPackage(String ownerAppPackage);

        abstract Builder setPublisher(AdTechIdentifier value);

        abstract Builder setCustomAudienceBuyer(AdTechIdentifier customAudienceOwner);

        abstract Builder setWinDebugReportUri(Uri winDebugReportUri);

        abstract Builder setLossDebugReportUri(Uri lossDebugReportUri);

        abstract Builder setSellerRejectReason(String sellerRejectReason);

        abstract ScoreAdResult build();
    }
}
