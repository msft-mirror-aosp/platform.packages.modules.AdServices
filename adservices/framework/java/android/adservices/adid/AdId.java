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
package android.adservices.adid;

import android.annotation.NonNull;

import java.util.Objects;

/** Represents the response from the {@link AdIdManager#getAdId(Executor, OutcomeReceiver)} API. */
public class AdId {
    @NonNull private final String mAdId;
    private final boolean mLimitAdTrackingEnabled;

    public AdId(@NonNull String adId, boolean limitAdTrackingEnabled) {
        mAdId = adId;
        mLimitAdTrackingEnabled = limitAdTrackingEnabled;
    }

    /**
     * Retrieves the advertising ID. When {@link #isLimitAdTrackingEnabled()} is true, the returned
     * value of this API is 00000000-0000-0000-0000-000000000000 regardless of the app’s target SDK
     * level. Apps with target API level set to 33 (Android 13) or later must declare the normal
     * permission com.google.android.gms.permission.AD_ID in the AndroidManifest.xml in order to use
     * this API. If this permission is not declared, the returned value is
     * 00000000-0000-0000-0000-000000000000. On API level lower than 33, we can return empty adid
     * from the provider side in situations when the provider cannot retrieve this information.
     */
    public @NonNull String getAdId() {
        return mAdId;
    }

    /**
     * Retrieves the limit ad tracking enabled setting, true if user has limit ad tracking enabled.
     * False, otherwise. When the returned value is true, the returned value of {@link #getAdId}
     * will always be 00000000-0000-0000-0000-000000000000".
     */
    public boolean isLimitAdTrackingEnabled() {
        return mLimitAdTrackingEnabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AdId)) {
            return false;
        }
        AdId that = (AdId) o;
        return mAdId.equals(that.mAdId)
                && (mLimitAdTrackingEnabled == that.mLimitAdTrackingEnabled);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAdId, mLimitAdTrackingEnabled);
    }
}
