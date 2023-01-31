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

import static com.android.adservices.service.measurement.ServingAdtechNetwork.ServingAdtechNetworkContract.OFFSET;

import android.annotation.NonNull;
import android.annotation.Nullable;

import com.android.adservices.LogUtil;
import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

/** POJO for ServingAdtechNetwork. */
public class ServingAdtechNetwork {

    @Nullable private final UnsignedLong mOffset;

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
    public UnsignedLong getOffset() {
        return mOffset;
    }

    /**
     * Serializes the object as Json.
     *
     * @return serialized json object
     */
    @Nullable
    public JSONObject serializeAsJson() {
        JSONObject servingAdtechNetworkJson = new JSONObject();
        try {
            if (mOffset != null) {
                servingAdtechNetworkJson.put(OFFSET, mOffset.getValue());
            }
        } catch (JSONException e) {
            LogUtil.d(e, "Serialization of ServingAdtechNetwork failed.");
            return null;
        }
        return servingAdtechNetworkJson;
    }

    /** Builder for {@link ServingAdtechNetwork}. */
    public static final class Builder {
        private UnsignedLong mOffset;

        public Builder() {}

        public Builder(@NonNull JSONObject jsonObject) throws JSONException {
            if (!jsonObject.isNull(OFFSET)) {
                String offset = jsonObject.getString(OFFSET);
                // Unassigned in order to validate the long value
                try {
                    mOffset = new UnsignedLong(offset);
                } catch (NumberFormatException e) {
                    LogUtil.d(e, "ServingAdtechNetwork.Builder: Failed to parse offset.");
                    // Wrapped into JSONException so that it does not crash and becomes a checked
                    // Exception that is caught by the caller.
                    throw new JSONException(e);
                }
            }
        }

        /** See {@link ServingAdtechNetwork#getOffset()}. */
        @NonNull
        public Builder setOffset(@Nullable UnsignedLong offset) {
            mOffset = offset;
            return this;
        }

        /** Build the {@link ServingAdtechNetwork}. */
        @NonNull
        public ServingAdtechNetwork build() {
            return new ServingAdtechNetwork(this);
        }
    }

    /** Constants related to ServingAdTechNetwork. */
    public interface ServingAdtechNetworkContract {
        String OFFSET = "offset";
    }
}
