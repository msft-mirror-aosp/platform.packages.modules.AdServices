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

import android.annotation.NonNull;

import com.android.adservices.data.adselection.DBAdSelection;

import java.util.Objects;

/**
 * Real implementation of the {@link AdCounterKeyCopier} for copying ad counter keys to a {@link
 * DBAdSelection} for caching in the ad selection database.
 */
public class AdCounterKeyCopierImpl implements AdCounterKeyCopier {
    public AdCounterKeyCopierImpl() {}

    @Override
    @NonNull
    public DBAdSelection.Builder copyAdCounterKeys(
            @NonNull DBAdSelection.Builder targetBuilder, @NonNull AdScoringOutcome sourceOutcome) {
        Objects.requireNonNull(targetBuilder);
        Objects.requireNonNull(sourceOutcome);
        return targetBuilder.setAdCounterKeys(
                sourceOutcome.getAdWithScore().getAdWithBid().getAdData().getAdCounterKeys());
    }
}
