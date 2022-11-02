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

package com.android.adservices.service.stats;

import com.google.auto.value.AutoValue;

/** Class for runAdSelection process reported stats. */
@AutoValue
public abstract class RunAdSelectionProcessReportedStats {
    /** @return isRemarketingAdsWon. */
    public abstract boolean getIsRemarketingAdsWon();

    /** @return DBAdSelectionSizeInBytes. */
    public abstract int getDBAdSelectionSizeInBytes();

    /** @return persistAdSelectionLatencyInMills. */
    public abstract int getPersistAdSelectionLatencyInMillis();

    /** @return persistAdSelectionResultCode. */
    public abstract int getPersistAdSelectionResultCode();

    /** @return runAdSelectionLatencyInMillis. */
    public abstract int getRunAdSelectionLatencyInMillis();

    /** @return runAdSelectionResultCode. */
    public abstract int getRunAdSelectionResultCode();

    /** @return generic builder. */
    static Builder builder() {
        return new AutoValue_RunAdSelectionProcessReportedStats.Builder();
    }

    /** Builder class for {@link RunAdSelectionProcessReportedStats}. */
    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder setIsRemarketingAdsWon(boolean value);

        abstract Builder setDBAdSelectionSizeInBytes(int value);

        abstract Builder setPersistAdSelectionLatencyInMillis(int value);

        abstract Builder setPersistAdSelectionResultCode(int value);

        abstract Builder setRunAdSelectionLatencyInMillis(int value);

        abstract Builder setRunAdSelectionResultCode(int value);

        abstract RunAdSelectionProcessReportedStats build();
    }
}
