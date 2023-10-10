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

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

/** POJO for attributed trigger.  */
public class AttributedTrigger {
    private final String mTriggerId;
    private final long mPriority;
    private final UnsignedLong mTriggerData;
    private final long mValue;
    private final long mTriggerTime;
    private final UnsignedLong mDedupKey;

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AttributedTrigger)) {
            return false;
        }
        AttributedTrigger t = (AttributedTrigger) obj;

        return mTriggerId.equals(t.mTriggerId)
                && mPriority == t.mPriority
                && Objects.equals(mTriggerData, t.mTriggerData)
                && mValue == t.mValue
                && mTriggerTime == t.mTriggerTime
                && Objects.equals(mDedupKey, t.mDedupKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTriggerId, mPriority, mTriggerData, mValue, mTriggerTime, mDedupKey);
    }

    public AttributedTrigger(JSONObject json) throws JSONException {
        mTriggerId = json.getString(JsonKeys.TRIGGER_ID);
        if (!json.isNull(ReportSpecUtil.FlexEventReportJsonKeys.PRIORITY)) {
            mPriority = json.getLong(ReportSpecUtil.FlexEventReportJsonKeys.PRIORITY);
        } else {
            mPriority = 0L;
        }
        if (!json.isNull(ReportSpecUtil.FlexEventReportJsonKeys.TRIGGER_DATA)) {
            mTriggerData = new UnsignedLong(
                    json.getString(ReportSpecUtil.FlexEventReportJsonKeys.TRIGGER_DATA));
        } else {
            mTriggerData = null;
        }
        if (!json.isNull(ReportSpecUtil.FlexEventReportJsonKeys.VALUE)) {
            mValue = json.getLong(ReportSpecUtil.FlexEventReportJsonKeys.VALUE);
        } else {
            mValue = 0L;
        }
        if (!json.isNull(ReportSpecUtil.FlexEventReportJsonKeys.TRIGGER_TIME)) {
            mTriggerTime = json.getLong(ReportSpecUtil.FlexEventReportJsonKeys.TRIGGER_TIME);
        } else {
            mTriggerTime = 0L;
        }
        if (!json.isNull(JsonKeys.DEDUP_KEY)) {
            mDedupKey = new UnsignedLong(
                    json.getString(JsonKeys.DEDUP_KEY));
        } else {
            mDedupKey = null;
        }
    }

    public AttributedTrigger(
            String triggerId,
            UnsignedLong triggerData,
            UnsignedLong dedupKey) {
        mTriggerId = triggerId;
        mDedupKey = dedupKey;
        mPriority = 0L;
        mTriggerData = triggerData;
        mValue = 0L;
        mTriggerTime = 0L;
    }

    public AttributedTrigger(
            String triggerId,
            long priority,
            UnsignedLong triggerData,
            long value,
            long triggerTime,
            UnsignedLong dedupKey) {
        mTriggerId = triggerId;
        mPriority = priority;
        mTriggerData = triggerData;
        mValue = value;
        mTriggerTime = triggerTime;
        mDedupKey = dedupKey;
    }

    public String getTriggerId() {
        return mTriggerId;
    }

    public long getPriority() {
        return mPriority;
    }

    public UnsignedLong getTriggerData() {
        return mTriggerData;
    }

    public long getValue() {
        return mValue;
    }

    public long getTriggerTime() {
        return mTriggerTime;
    }

    public UnsignedLong getDedupKey() {
        return mDedupKey;
    }

    /** Encodes the attributed trigger to a JSONObject */
    public JSONObject encodeToJson() {
        JSONObject json = new JSONObject();
        try {
            json.put(JsonKeys.TRIGGER_ID, mTriggerId);
            json.put(
                    ReportSpecUtil.FlexEventReportJsonKeys.TRIGGER_DATA,
                    mTriggerData.toString());
            json.put(JsonKeys.DEDUP_KEY, mDedupKey.toString());
        } catch (JSONException e) {
            LoggerFactory.getMeasurementLogger()
                    .e(e, "ReportSpec::encodeToJson cannot encode AttributedTrigger to JSON");
            return null;
        }
        return json;
    }

    /** Encodes the attributed trigger to a JSONObject */
    public JSONObject encodeToJsonFlexApi() {
        JSONObject json = new JSONObject();
        try {
            json.put(JsonKeys.TRIGGER_ID, mTriggerId);
            json.put(
                    ReportSpecUtil.FlexEventReportJsonKeys.TRIGGER_DATA,
                    mTriggerData.toString());
            json.put(ReportSpecUtil.FlexEventReportJsonKeys.TRIGGER_TIME, mTriggerTime);
            json.put(ReportSpecUtil.FlexEventReportJsonKeys.VALUE, mValue);
            if (mDedupKey != null) {
                json.put(JsonKeys.DEDUP_KEY, mDedupKey.toString());
            }
            json.put(ReportSpecUtil.FlexEventReportJsonKeys.PRIORITY, mPriority);
        } catch (JSONException e) {
            LoggerFactory.getMeasurementLogger()
                    .e(
                            e,
                            "ReportSpec::encodeToJsonFlexApi cannot encode AttributedTrigger to"
                                    + " JSON");
            return null;
        }
        return json;
    }

    private interface JsonKeys {
        String TRIGGER_ID = "trigger_id";
        String DEDUP_KEY = "dedup_key";
    }
}
