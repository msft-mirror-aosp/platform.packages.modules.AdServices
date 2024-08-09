/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.appsearch;

import android.annotation.NonNull;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.appsearch.annotation.Document;
import androidx.appsearch.app.AppSearchSchema.StringPropertyConfig;
import androidx.appsearch.app.GlobalSearchSession;

import com.android.adservices.LogUtil;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * This class represents the data access object for the module enrollment state written to
 * AppSearch.
 */
@RequiresApi(Build.VERSION_CODES.S)
@Document
class AppSearchModuleEnrollmentStateDao extends AppSearchDao {
    public static final String NAMESPACE = "moduleEnrollmentState";

    // Column name used for preparing the query string, are not part of the @Document.
    private static final String USER_ID_COLNAME = "userId";

    /**
     * Identifier of the Consent Document; must be unique within the Document's `namespace`. This is
     * the row ID for consent data. It is a combination of user ID and api type.
     */
    @Document.Id private final String mId;

    @Document.StringProperty(indexingType = StringPropertyConfig.INDEXING_TYPE_EXACT_TERMS)
    private final String mUserId;

    /** Namespace of the Consent Document. Used to group documents during querying or deletion. */
    @Document.Namespace private final String mNamespace;

    @Document.StringProperty private String mModuleEnrollmentState;

    AppSearchModuleEnrollmentStateDao(
            @NonNull String id,
            @NonNull String userId,
            @NonNull String namespace,
            @NonNull String moduleEnrollmentState) {
        this.mId = id;
        this.mUserId = userId;
        this.mNamespace = namespace;
        this.mModuleEnrollmentState = moduleEnrollmentState;
    }

    AppSearchModuleEnrollmentStateDao(
            @NonNull String id, @NonNull String userId, @NonNull String namespace) {
        this.mId = id;
        this.mUserId = userId;
        this.mNamespace = namespace;
    }

    /** Returns the row ID that should be unique for the namespace. */
    public static String getRowId(@NonNull String uid) {
        return uid;
    }

    /**
     * Read the module enrollment state from AppSearch.
     *
     * @param searchSession we use GlobalSearchSession here to allow AdServices to read.
     * @param executor the Executor to use.
     * @param userId the user ID for the query.
     * @return whether the row is consented for this user ID and apiType.
     */
    public static AppSearchModuleEnrollmentStateDao readData(
            @NonNull ListenableFuture<GlobalSearchSession> searchSession,
            @NonNull Executor executor,
            @NonNull String userId,
            @NonNull String adServicesPackageName) {
        Objects.requireNonNull(searchSession);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(userId);

        String query = getQuery(userId);
        AppSearchModuleEnrollmentStateDao dao =
                AppSearchDao.readConsentData(
                        AppSearchModuleEnrollmentStateDao.class,
                        searchSession,
                        executor,
                        NAMESPACE,
                        query,
                        adServicesPackageName);
        LogUtil.d("AppSearch module enrollment state read: " + dao + " [ query: " + query + "]");
        return dao;
    }

    // Get the search query for AppSearch. Format specified at http://shortn/_RwVKmB74f3.
    // Note: AND as an operator is not supported by AppSearch on S or T.
    @VisibleForTesting
    static String getQuery(String userId) {
        return USER_ID_COLNAME + ":" + userId;
    }

    /**
     * Get the row ID for this row.
     *
     * @return ID
     */
    public String getId() {
        return mId;
    }

    /**
     * Get the user ID for this row.
     *
     * @return user ID
     */
    public String getUserId() {
        return mUserId;
    }

    /**
     * Get the namespace for this row.
     *
     * @return nameespace
     */
    public String getNamespace() {
        return mNamespace;
    }

    @NonNull
    public String getModuleEnrollmentState() {
        return mModuleEnrollmentState;
    }

    @NonNull
    public void setModuleEnrollmentState(@NonNull String moduleEnrollmentState) {
        mModuleEnrollmentState = moduleEnrollmentState;
    }

    public String toString() {
        return "id="
                + mId
                + "; userId="
                + mUserId
                + "; namespace="
                + mNamespace
                + "; moduleEnrollmentState="
                + mModuleEnrollmentState;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mUserId, mNamespace, mModuleEnrollmentState);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppSearchModuleEnrollmentStateDao)) return false;
        AppSearchModuleEnrollmentStateDao obj = (AppSearchModuleEnrollmentStateDao) o;
        return Objects.equals(this.mId, obj.mId)
                && Objects.equals(this.mUserId, obj.mUserId)
                && Objects.equals(this.mNamespace, obj.mNamespace)
                && Objects.equals(this.mModuleEnrollmentState, obj.mModuleEnrollmentState);
    }

    /** Read the current module enrollment state from AppSearch. */
    public static String readModuleEnrollmentState(
            @NonNull ListenableFuture<GlobalSearchSession> searchSession,
            @NonNull Executor executor,
            @NonNull String userId,
            @NonNull String adServicesPackageName) {
        AppSearchModuleEnrollmentStateDao dao =
                readData(searchSession, executor, userId, adServicesPackageName);
        if (dao == null) {
            return "";
        }

        return dao.getModuleEnrollmentState();
    }
}
