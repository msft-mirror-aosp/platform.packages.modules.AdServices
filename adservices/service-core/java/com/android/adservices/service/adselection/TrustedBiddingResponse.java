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

import com.google.auto.value.AutoValue;

import org.json.JSONObject;

@AutoValue
abstract class TrustedBiddingResponse {
    @NonNull
    abstract JSONObject getBody();

    @NonNull
    abstract JSONObject getHeaders();

    static TrustedBiddingResponse.Builder builder() {
        return new AutoValue_TrustedBiddingResponse.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
        public abstract Builder setBody(@NonNull JSONObject body);

        public abstract Builder setHeaders(@NonNull JSONObject headers);

        abstract TrustedBiddingResponse build();
    }
}
