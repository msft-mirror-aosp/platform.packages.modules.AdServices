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

package com.android.adservices.service.measurement;

import android.annotation.NonNull;
import android.annotation.Nullable;

import java.util.Objects;

/** POJO for ServingAdtechNetwork. */
public class ServingAdtechNetwork {

    @Nullable private final Long mOffset;

    private ServingAdtechNetwork(@NonNull ServingAdtechNetwork.Builder builder) {
        mOffset = builder.mOffset;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ServingAdtechNetwork)) {
            return false;
        }
        ServingAdtechNetwork servingAdtechNetwork = (ServingAdtechNetwork) obj;
        return Objects.equals(mOffset, servingAdtechNetwork.mOffset);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mOffset);
    }

    /** Returns the value of offset as Long */
    @Nullable
    public Long getOffset() {
        return mOffset;
    }

    /** Builder for {@link ServingAdtechNetwork}. */
    public static final class Builder {
        private Long mOffset;

        public Builder() {}

        /** See {@link ServingAdtechNetwork#getOffset()}. */
        @NonNull
        public Builder setOffset(@Nullable Long offset) {
            mOffset = offset;
            return this;
        }

        /** Build the {@link ServingAdtechNetwork}. */
        @NonNull
        public ServingAdtechNetwork build() {
            return new ServingAdtechNetwork(this);
        }
    }
}
