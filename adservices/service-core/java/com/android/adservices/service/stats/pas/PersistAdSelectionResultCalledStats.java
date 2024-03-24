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

package com.android.adservices.service.stats.pas;

import android.adservices.common.AdsRelevanceStatusUtils;

import com.google.auto.value.AutoValue;

/** Class for persistAdSelectionResult API called stats. */
@AutoValue
public abstract class PersistAdSelectionResultCalledStats {
    /** Returns the type of auction winner. */
    @AdsRelevanceStatusUtils.WinnerType
    public abstract int getWinnerType();

    /** Returns generic builder */
    public static Builder builder() {
        return new AutoValue_PersistAdSelectionResultCalledStats.Builder();
    }

    /** Builder class for PersistAdSelectionResultCalledStats. */
    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setWinnerType(@AdsRelevanceStatusUtils.WinnerType int value);

        public abstract PersistAdSelectionResultCalledStats build();
    }
}
