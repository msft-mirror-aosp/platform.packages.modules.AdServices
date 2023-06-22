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

package com.android.adservices.service.measurement;

import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A class wrapper for the trigger specification from the input argument during source registration
 */
public class TriggerSpec {
    private List<UnsignedLong> mTriggerData;
    private Long mEventReportWindowsStart;
    private List<Long> mEventReportWindowsEnd;
    private SummaryOperatorType mSummaryWindowOperator;
    private List<Long> mSummaryBucket;

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TriggerSpec)) {
            return false;
        }
        TriggerSpec t = (TriggerSpec) obj;
        return Objects.equals(mTriggerData, t.mTriggerData)
                && mEventReportWindowsStart.equals(t.mEventReportWindowsStart)
                && mEventReportWindowsEnd.equals(t.mEventReportWindowsEnd)
                && mSummaryWindowOperator == t.mSummaryWindowOperator
                && mSummaryBucket.equals(t.mSummaryBucket);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mTriggerData,
                mEventReportWindowsStart,
                mEventReportWindowsEnd,
                mSummaryWindowOperator,
                mSummaryBucket);
    }

    /**
     * @return Trigger Data
     */
    public List<UnsignedLong> getTriggerData() {
        return mTriggerData;
    }

    /**
     * @return Event Report Windows Start
     */
    public Long getEventReportWindowsStart() {
        return mEventReportWindowsStart;
    }

    /** @return Event Report Windows End */
    public List<Long> getEventReportWindowsEnd() {
        return mEventReportWindowsEnd;
    }

    /** @return Summary Window Operator */
    public SummaryOperatorType getSummaryWindowOperator() {
        return mSummaryWindowOperator;
    }

    /**
     * @return Summary Bucket
     */
    public List<Long> getSummaryBucket() {
        return mSummaryBucket;
    }

    /**
     * Encode the parameter to JSON
     *
     * @return json object encode this class
     */
    public JSONObject encodeJSON() throws JSONException {
        JSONObject json = new JSONObject();
        json.put(
                "trigger_data",
                new JSONArray(
                        mTriggerData.stream()
                                .map(UnsignedLong::toString)
                                .collect(Collectors.toList())));
        JSONObject windows = new JSONObject();
        windows.put("start_time", mEventReportWindowsStart);
        windows.put("end_times", new JSONArray(mEventReportWindowsEnd));
        json.put("event_report_windows", windows);
        json.put("summary_window_operator", mSummaryWindowOperator.name().toLowerCase());
        json.put("summary_buckets", new JSONArray(mSummaryBucket));
        return json;
    }

    private static <T extends Comparable<T>> boolean isStrictIncreasing(List<T> list) {
        for (int i = 1; i < list.size(); i++) {
            if (list.get(i).compareTo(list.get(i - 1)) <= 0) {
                return false;
            }
        }
        return true;
    }

    /** The choice of the summary operator with the reporting window */
    public enum SummaryOperatorType {
        COUNT,
        VALUE_SUM
    }

    private static ArrayList<Long> getLongArrayFromJSON(JSONObject json, String key)
            throws JSONException {
        ArrayList<Long> result = new ArrayList<>();
        JSONArray valueArray = json.getJSONArray(key);
        for (int i = 0; i < valueArray.length(); i++) {
            result.add(valueArray.getLong(i));
        }
        return result;
    }

    private static ArrayList<UnsignedLong> getTriggerDataArrayFromJSON(JSONObject json, String key)
            throws JSONException {
        ArrayList<UnsignedLong> result = new ArrayList<>();
        JSONArray valueArray = json.getJSONArray(key);
        for (int i = 0; i < valueArray.length(); i++) {
            result.add(new UnsignedLong(valueArray.getString(i)));
        }
        return result;
    }

    private void validateParameters() {
        if (!isStrictIncreasing(mEventReportWindowsEnd)) {
            throw new IllegalArgumentException(
                    ReportSpecUtil.FlexEventReportJsonKeys.EVENT_REPORT_WINDOWS
                            + " not increasing");
        }
        if (!isStrictIncreasing(mSummaryBucket)) {
            throw new IllegalArgumentException(
                    ReportSpecUtil.FlexEventReportJsonKeys.SUMMARY_BUCKETS + " not increasing");
        }
        if (mEventReportWindowsStart < 0) {
            mEventReportWindowsStart = 0L;
        }
    }

    /** */
    public static final class Builder {
        private final TriggerSpec mBuilding;

        public Builder(JSONObject jsonObject) throws JSONException, IllegalArgumentException {
            mBuilding = new TriggerSpec();
            mBuilding.mSummaryWindowOperator = SummaryOperatorType.COUNT;
            mBuilding.mEventReportWindowsStart = 0L;
            mBuilding.mSummaryBucket = new ArrayList<>();
            mBuilding.mEventReportWindowsEnd = new ArrayList<>();

            this.setTriggerData(
                    getTriggerDataArrayFromJSON(
                            jsonObject, ReportSpecUtil.FlexEventReportJsonKeys.TRIGGER_DATA));
            if (mBuilding.mTriggerData.size()
                    > PrivacyParams.getMaxFlexibleEventTriggerDataCardinality()) {
                throw new IllegalArgumentException(
                        "Trigger Data Cardinality Exceeds "
                                + PrivacyParams.getMaxFlexibleEventTriggerDataCardinality());
            }
            JSONObject jsonReportWindows =
                    jsonObject.getJSONObject(
                            ReportSpecUtil.FlexEventReportJsonKeys.EVENT_REPORT_WINDOWS);
            if (!jsonReportWindows.isNull(ReportSpecUtil.FlexEventReportJsonKeys.START_TIME)) {
                this.setEventReportWindowsStart(
                        jsonReportWindows.getLong(
                                ReportSpecUtil.FlexEventReportJsonKeys.START_TIME));
            }
            this.setEventReportWindowsEnd(
                    getLongArrayFromJSON(
                            jsonReportWindows, ReportSpecUtil.FlexEventReportJsonKeys.END_TIME));
            if (mBuilding.mEventReportWindowsEnd.size()
                    > PrivacyParams.getMaxFlexibleEventReportingWindows()) {
                throw new IllegalArgumentException("Number of Reporting Windows Exceeds Limit");
            }

            if (!jsonObject.isNull(
                    ReportSpecUtil.FlexEventReportJsonKeys.SUMMARY_WINDOW_OPERATOR)) {
                try {
                    SummaryOperatorType op =
                            SummaryOperatorType.valueOf(
                                    jsonObject
                                            .getString(
                                                    ReportSpecUtil.FlexEventReportJsonKeys
                                                            .SUMMARY_WINDOW_OPERATOR)
                                            .toUpperCase());
                    this.setSummaryWindowOperator(op);
                } catch (IllegalArgumentException e) {
                    // if a summary window operator is defined, but not in the pre-defined list, it
                    // will throw to exception.
                    throw new IllegalArgumentException(
                            ReportSpecUtil.FlexEventReportJsonKeys.SUMMARY_WINDOW_OPERATOR
                                    + " invalid");
                }
            }
            this.setSummaryBucket(
                    getLongArrayFromJSON(
                            jsonObject, ReportSpecUtil.FlexEventReportJsonKeys.SUMMARY_BUCKETS));
            mBuilding.validateParameters();
        }

        /** See {@link TriggerSpec#getTriggerData()} ()}. */
        public Builder setTriggerData(List<UnsignedLong> triggerData) {
            mBuilding.mTriggerData = triggerData;
            return this;
        }

        /** See {@link TriggerSpec#getEventReportWindowsStart()} ()}. */
        public Builder setEventReportWindowsStart(Long eventReportWindowsStart) {
            mBuilding.mEventReportWindowsStart = eventReportWindowsStart;
            return this;
        }

        /** See {@link TriggerSpec#getEventReportWindowsEnd()} ()}. */
        public Builder setEventReportWindowsEnd(List<Long> eventReportWindowsEnd) {
            mBuilding.mEventReportWindowsEnd = eventReportWindowsEnd;
            return this;
        }

        /** See {@link TriggerSpec#getSummaryWindowOperator()} ()}. */
        public Builder setSummaryWindowOperator(SummaryOperatorType summaryWindowOperator) {
            mBuilding.mSummaryWindowOperator = summaryWindowOperator;
            return this;
        }

        /** See {@link TriggerSpec#getSummaryBucket()} ()}. */
        public Builder setSummaryBucket(List<Long> summaryBucket) {
            mBuilding.mSummaryBucket = summaryBucket;
            return this;
        }

        /** Build the {@link TriggerSpec}. */
        public TriggerSpec build() {
            return mBuilding;
        }
    }
}
