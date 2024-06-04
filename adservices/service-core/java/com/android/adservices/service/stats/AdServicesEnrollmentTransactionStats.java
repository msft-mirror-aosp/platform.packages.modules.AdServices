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

package com.android.adservices.service.stats;

import com.google.auto.value.AutoValue;

/** Class for AdServicesEncryptionKeyFetched atom. */
@AutoValue
public abstract class AdServicesEnrollmentTransactionStats {
    /**
     * @return query type.
     */
    public abstract TransactionType transactionType();

    /**
     * @return transaction status.
     */
    public abstract TransactionStatus transactionStatus();

    /**
     * @return transaction parameter count.
     */
    public abstract int transactionParameterCount();

    /**
     * @return transaction result count.
     */
    public abstract int transactionResultCount();

    /**
     * @return query result count.
     */
    public abstract int queryResultCount();

    /**
     * @return Datasource Record count pre query.
     */
    public abstract int dataSourceRecordCountPre();

    /**
     * @return Datasource Record count post query.
     */
    public abstract int dataSourceRecordCountPost();

    /**
     * @return Enrollment File Id.
     */
    public abstract int enrollmentFileBuildId();

    /**
     * @return generic builder.
     */
    public static AdServicesEnrollmentTransactionStats.Builder builder() {
        return new AutoValue_AdServicesEnrollmentTransactionStats.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {

        /** Set query parameter collection count */
        public abstract Builder setTransactionParameterCount(int value);

        /** Set query result count. */
        public abstract Builder setQueryResultCount(int value);

        /** Set transaction result count. After query processing. */
        public abstract Builder setTransactionResultCount(int value);

        /** Set source record count prior to transaction. */
        public abstract Builder setDataSourceRecordCountPre(int value);

        /** Set source record count following transaction. */
        public abstract Builder setDataSourceRecordCountPost(int value);

        /** Set Enrollment File Identifier record at time of operation. */
        public abstract Builder setEnrollmentFileBuildId(int value);

        /** Set Enrollment Transaction Type. */
        public abstract Builder setTransactionType(TransactionType value);

        /** Set Enrollment Transaction Status. */
        public abstract Builder setTransactionStatus(TransactionStatus value);

        /** Build AdServicesEnrollmentTransactionStats */
        public abstract AdServicesEnrollmentTransactionStats build();
    }

    public enum TransactionType {
        UNKNOWN(0),
        INSERT(1),
        DELETE(2),
        DELETE_ALL(3),
        OVERWRITE_DATA(4),
        GET_ENROLLMENT_DATA(5),
        GET_ALL_ENROLLMENT_DATA(6),
        GET_ENROLLMENT_DATA_FROM_SDK_NAME(7),
        GET_ENROLLMENT_DATA_FROM_MEASUREMENT_URL(8),
        GET_ALL_FLEDGE_ENROLLED_ADTECHS(9),
        GET_ENROLLMENT_DATA_FOR_FLEDGE_BY_ADTECH_IDENTIFIER(10),
        GET_ENROLLMENT_DATA_FOR_FLEDGE_BY_MATCHING_ADTECH_IDENTIFIER(11),
        GET_ALL_PAS_ENROLLED_ADTECHS(12),

        GET_ENROLLMENT_DATA_FOR_PAS_BY_ADTECH_IDENTIFIER(13),
        GET_ENROLLMENT_DATA_FOR_PAS_BY_MATCHING_ADTECH_IDENTIFIER(14);

        final int mValue;

        TransactionType(int val) {
            this.mValue = val;
        }

        public int getValue() {
            return mValue;
        }
    }

    public enum TransactionStatus {
        UNKNOWN(0),
        SUCCESS(1),
        DB_NOT_FOUND(2),
        INVALID_INPUT(3),
        INVALID_OUTPUT(4),
        MATCH_NOT_FOUND(5),
        DATASTORE_EXCEPTION(6);
        final int mValue;

        TransactionStatus(int val) {
            this.mValue = val;
        }

        public int getValue() {
            return mValue;
        }
    }
}
