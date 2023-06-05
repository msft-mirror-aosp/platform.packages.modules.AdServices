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

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.auto.value.AutoValue;

@AutoValue
abstract class ScoreAdResult {
    abstract Double getAdScore();

    @Nullable
    abstract Uri getWinDebugReportUri();

    @Nullable
    abstract Uri getLossDebugReportUri();

    static ScoreAdResult of(
            Double adScore, @Nullable Uri winDebugReportUri, @Nullable Uri lossDebugReportUri) {
        // TODO(b/284449758): Handle seller reject reason when scoring work is complete.
        return new AutoValue_ScoreAdResult(adScore, winDebugReportUri, lossDebugReportUri);
    }

    static ScoreAdResult of(Double adScore) {
        return new AutoValue_ScoreAdResult(adScore, Uri.EMPTY, Uri.EMPTY);
    }
}
