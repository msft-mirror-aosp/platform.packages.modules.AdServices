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

package android.adservices.adselection;

import android.adservices.common.AdData;
import android.annotation.NonNull;

import java.util.Objects;

/**
 * This class represents the response returned by the {@link AdSelectionManager} as the result of a
 * successful {@code runAdSelection} call.
 *
 * @hide
 */
public final class AdSelectionOutcome{

    private final int mAdSelectionId;
    @NonNull private final AdData mAdData;

    public AdSelectionOutcome(@NonNull AdData adData, int adSelectionId) {
        mAdSelectionId = adSelectionId;
        mAdData = adData;
    }

    /** Returns the adData that the AdSelection returns. */
    @NonNull
    public AdData getAdData() {
        return mAdData;
    }

    /** Returns the adSelectionId that identifies the AdSelection. */
    @NonNull
    public int getAdSelectionId() {
        return mAdSelectionId;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof AdSelectionOutcome) {
            AdSelectionOutcome adSelectionOutcome = (AdSelectionOutcome) o;
            return mAdSelectionId == adSelectionOutcome.mAdSelectionId
                    && Objects.equals(mAdData, adSelectionOutcome.mAdData);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAdSelectionId, mAdData);
    }
}
