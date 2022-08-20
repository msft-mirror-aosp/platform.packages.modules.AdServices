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
 * A unique, per-device, per developer-account user-resettable ID for non-monetizing advertising
 * usecases.
 *
 * <p>Represents the appSetID and scope of this appSetId from the {@link
 * AppSetIdManager#getAppSetId(Executor, OutcomeReceiver)} API. The scope of the ID can be per app
 * or per developer account associated with the user. AppSetId is used for analytics, spam
 * detection, frequency capping and fraud prevention use cases, on a given device, that one may need
 * to correlate usage or actions across a set of apps owned by an organization.
 */
public class AppSetId {
    @NonNull private final String mAppSetId;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        SCOPE_APP,
        SCOPE_DEVELOPER,
    })
    public @interface AppSetIdScope {}
    /** The appSetId is scoped to an app. All apps on a device will have a different appSetId. */
    public static final int SCOPE_APP = 1;

    /**
     * The appSetId is scoped to a developer account on an app store. All apps from the same
     * developer on a device will have the same developer scoped appSetId.
     */
    public static final int SCOPE_DEVELOPER = 2;

    private final @AppSetIdScope int mAppSetIdScope;

    /**
     * Creates an instance of {@link AppSetId}
     *
     * @param appSetId generated by the provider service.
     * @param appSetIdScope scope of the appSetId.
     */
    public AppSetId(@NonNull String appSetId, @AppSetIdScope int appSetIdScope) {
        mAppSetId = appSetId;
        mAppSetIdScope = appSetIdScope;
    }

    /** Retrieves the appSetId. The api always returns a non-null appSetId. */
    public @NonNull String getId() {
        return mAppSetId;
    }

    /** Retrieves the scope of the appSetId. */
    public @AppSetIdScope int getScope() {
        return mAppSetIdScope;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AppSetId)) {
            return false;
        }
        AppSetId that = (AppSetId) o;
        return mAppSetId.equals(that.mAppSetId) && (mAppSetIdScope == that.mAppSetIdScope);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAppSetId, mAppSetIdScope);
    }
}
