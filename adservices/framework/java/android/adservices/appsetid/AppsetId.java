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
package android.adservices.appsetid;

import android.annotation.IntDef;
import android.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Represents the response from the {@link AppsetIdManager#getAppsetId(Executor, OutcomeReceiver)}
 * API.
 */
public class AppsetId {
    @NonNull private final String mAppsetId;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        SCOPE_APP,
        SCOPE_DEVELOPER,
    })
    public @interface AppsetIdScope {}
    /** The appsetId is scoped to an app. All apps on a device will have a different appsetId. */
    public static final int SCOPE_APP = 1;

    /**
     * The appsetId is scoped to a developer account on an app store. All apps from the same
     * developer on a device will have the same developer scoped appsetId.
     */
    public static final int SCOPE_DEVELOPER = 2;

    private final @AppsetIdScope int mAppsetIdScope;

    public AppsetId(@NonNull String appsetId, @AppsetIdScope int appsetIdScope) {
        mAppsetId = appsetId;
        mAppsetIdScope = appsetIdScope;
    }

    /** Retrieves the appsetId. The api always returns a non-null appsetId. */
    public @NonNull String getAppsetId() {
        return mAppsetId;
    }

    /** Retrieves the scope of the appsetId. */
    public @AppsetIdScope int getAppsetIdScope() {
        return mAppsetIdScope;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AppsetId)) {
            return false;
        }
        AppsetId that = (AppsetId) o;
        return mAppsetId.equals(that.mAppsetId) && (mAppsetIdScope == that.mAppsetIdScope);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAppsetId, mAppsetIdScope);
    }
}
