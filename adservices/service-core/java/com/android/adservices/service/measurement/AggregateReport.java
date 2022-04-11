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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The aggregate report is the attribution report encrypted by the aggregation service. An aggregate
 * report contains all the information needed for sending the report to its reporting endpoint. All
 * nested information has already been serialized and encrypted as necessary.
 */
public class AggregateReport {
    private List<AggregationServicePayload> mPayloads;
    private String mSharedInfo;

    private AggregateReport() {
        mPayloads = new ArrayList<>();
        mSharedInfo = null;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AggregateReport)) {
            return false;
        }
        AggregateReport aggregateReport = (AggregateReport) obj;
        return mPayloads.equals(aggregateReport.mPayloads)
                && Objects.equals(mSharedInfo, aggregateReport.mSharedInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mPayloads, mSharedInfo);
    }

    /**
     * All AggregationServicePayload generated in the aggregate report.
     */
    public List<AggregationServicePayload> getPayloads() {
        return mPayloads;
    }

    /**
     * Returns sharedInfo string in the aggregate report.
     */
    public String getSharedInfo() {
        return mSharedInfo;
    }

    /**
     * Builder for {@link AggregateReport}
     */
    public static final class Builder {
        private final AggregateReport mAggregateReport;

        public Builder() {
            mAggregateReport = new AggregateReport();
        }

        /**
         * See {@link AggregateReport#getPayloads()} ()}.
         */
        public Builder setAggregationServicePayload(
                List<AggregationServicePayload> payloads) {
            mAggregateReport.mPayloads = payloads;
            return this;
        }

        /**
         * See {@link AggregateReport#getSharedInfo()} ()}.
         */
        public Builder setSharedInfo(String sharedInfo) {
            mAggregateReport.mSharedInfo = sharedInfo;
            return this;
        }

        /**
         * Build the {@link AggregateReport}.
         */
        public AggregateReport build() {
            return mAggregateReport;
        }
    }

    /**
     * This payload is constructed using the data in the AttributionReport and then encrypted with
     * one of `url`'s public keys. The plaintext of the encrypted payload is a serialized CBOR map
     * structured as follows:
     *  {
     *  "operation": "<chosen operation as string>",
     *  "data": [{
     *      "bucket": <a 16-byte (i.e. 128-bit) big-endian bytestring>,
     *      "value": <a 4-byte (i.e. 32-bit) big-endian bytestring>
     *  }, ...],
     *  }
     *  Note that the "data" array may contain multiple contributions.
     *  For the `kExperimentalPoplar` aggregation mode, the "data" field is
     *  replaced with:
     *  "dpf_key": <binary serialization of the DPF key>
     */
    public static class AggregationServicePayload {
        private List<Integer> mPayload;
        private String mKeyId;

        private AggregationServicePayload() {
            mPayload = new ArrayList<>();
            mKeyId = null;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof AggregationServicePayload)) {
                return false;
            }
            AggregationServicePayload aggregateReportPayload = (AggregationServicePayload) obj;
            return Objects.equals(mPayload, aggregateReportPayload.mPayload)
                    && Objects.equals(mKeyId, aggregateReportPayload.mKeyId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mPayload, mKeyId);
        }

        /**
         * All encrypted payloads for a {@link AggregationServicePayload}.
         */
        public List<Integer> getPayload() {
            return mPayload;
        }

        /**
         * Returns the chosen encryption key.
         */
        public String getKeyId() {
            return mKeyId;
        }

        /**
         * Builder for {@link AggregationServicePayload}.
         */
        public static final class Builder {
            private final AggregationServicePayload mAggregationServicePayload;

            public Builder() {
                mAggregationServicePayload = new AggregationServicePayload();
            }

            /**
             * See {@link AggregationServicePayload#getPayload()};
             */
            public Builder setPayload(List<Integer> payload) {
                mAggregationServicePayload.mPayload = payload;
                return this;
            }

            /**
             * See {@link AggregationServicePayload#getKeyId()};
             */
            public Builder setKeyId(String keyId) {
                mAggregationServicePayload.mKeyId = keyId;
                return this;
            }

            /**
             * Build the {@link AggregationServicePayload}.
             */
            public AggregationServicePayload build() {
                return mAggregationServicePayload;
            }
        }
    }
}
