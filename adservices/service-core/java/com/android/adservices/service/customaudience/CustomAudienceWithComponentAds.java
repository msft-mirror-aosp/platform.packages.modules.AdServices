/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.adservices.service.customaudience;

import com.android.adservices.data.customaudience.DBCustomAudience;

import com.google.auto.value.AutoValue;

import java.util.List;

/** POJO holding a custom audience and it's associated component ad render ids. */
@AutoValue
public abstract class CustomAudienceWithComponentAds {

    /** Returns the {@link DBCustomAudience}. */
    public abstract DBCustomAudience getDBCustomAudience();

    /** Returns an ordered list of component ad render IDs associated with the custom audience. */
    public abstract List<String> getComponentAdRenderIds();

    /** Creates a new instance of {@link CustomAudienceWithComponentAds}. */
    public static CustomAudienceWithComponentAds create(
            DBCustomAudience dbCustomAudience, List<String> componentAdRenderIds) {
        return new AutoValue_CustomAudienceWithComponentAds(dbCustomAudience, componentAdRenderIds);
    }
}
