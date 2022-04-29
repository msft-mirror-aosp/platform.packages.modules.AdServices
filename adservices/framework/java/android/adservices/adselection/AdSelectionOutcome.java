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

import android.annotation.NonNull;
import android.net.Uri;


import com.android.internal.util.Preconditions;

import java.util.Objects;
/**
 * This class represents a field in the {@code OutcomeReceiver}, which is an input to the
 * {@code runAdSelection} in the {@link AdSelectionManager}.
 * This field is populated in the case of a successful {@code runAdSelection} call.
 *
 * @hide
 */
public class AdSelectionOutcome {
    private static final int UNSET = 0;

    private final long mAdSelectionId;
    @NonNull private final Uri mRenderUrl;

    private AdSelectionOutcome(long adSelectionId, @NonNull Uri renderUrl) {
        Objects.requireNonNull(renderUrl);

        mAdSelectionId = adSelectionId;
        mRenderUrl = renderUrl;
    }

    /** Returns the renderUrl that the AdSelection returns. */
    @NonNull
    public Uri getRenderUrl() {
        return mRenderUrl;
    }

    /** Returns the adSelectionId that identifies the AdSelection. */
    @NonNull
    public long getAdSelectionId() {
        return mAdSelectionId;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof AdSelectionOutcome) {
            AdSelectionOutcome adSelectionOutcome = (AdSelectionOutcome) o;
            return mAdSelectionId == adSelectionOutcome.mAdSelectionId
                    && Objects.equals(mRenderUrl, adSelectionOutcome.mRenderUrl);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAdSelectionId, mRenderUrl);
    }

    /**
     * Builder for {@link AdSelectionOutcome} objects.
     *
     * @hide
     */
    public static final class Builder {
        private long mAdSelectionId = UNSET;
        @NonNull private Uri mRenderUrl;;

        public Builder() {}

        /** Sets the mAdSelectionId. */
        @NonNull
        public AdSelectionOutcome.Builder setAdSelectionId(long adSelectionId) {
            this.mAdSelectionId = adSelectionId;
            return this;
        }

        /** Sets the RenderUrl. */
        @NonNull
        public AdSelectionOutcome.Builder setRenderUrl(@NonNull Uri renderUrl) {
            Objects.requireNonNull(renderUrl);

            mRenderUrl = renderUrl;
            return this;
        }

        /**
         * Builds a {@link AdSelectionOutcome} instance.
         *
         * @throws IllegalArgumentException if the adSelectionIid is not set
         *
         * @throws NullPointerException if the RenderUrl is null
         */
        @NonNull
        public AdSelectionOutcome build() {
            Objects.requireNonNull(mRenderUrl);

            Preconditions.checkArgument(
                    mAdSelectionId != UNSET, "AdSelectionId has not been set!");

            return new AdSelectionOutcome(mAdSelectionId, mRenderUrl);
        }
    }
}
