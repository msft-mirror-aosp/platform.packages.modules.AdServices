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

import android.annotation.NonNull;

import com.android.adservices.LogUtil;
import com.android.adservices.service.measurement.noising.Combinatorics;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A class wrapper for the trigger specification from the input argument during source registration
 */
public class ReportSpec {
    private final TriggerSpec[] mTriggerSpecs;
    private final int mMaxBucketIncrements;
    private final PrivacyComputationParams mPrivacyParams;
    private List<AttributedTrigger> mAttributedTriggers;

    /**
     * This constructor is called during the attribution process. Current trigger status will be
     * read and process to determine the outcome of incoming trigger.
     *
     * @param triggerSpecsString input trigger specs from ad tech
     * @param maxBucketIncrementsString max bucket increments from ad tech
     * @param eventAttributionStatusString current triggers to this source
     * @param privacyParametersString computed privacy parameters
     * @throws JSONException JSON exception
     */
    public ReportSpec(
            String triggerSpecsString,
            String maxBucketIncrementsString,
            String eventAttributionStatusString,
            String privacyParametersString)
            throws JSONException {
        if (triggerSpecsString == null || triggerSpecsString.isEmpty()) {
            throw new JSONException("the source is not registered as flexible event report API");
        }
        JSONArray triggerSpecs = new JSONArray(triggerSpecsString);
        mTriggerSpecs = new TriggerSpec[triggerSpecs.length()];
        for (int i = 0; i < triggerSpecs.length(); i++) {
            mTriggerSpecs[i] = new TriggerSpec.Builder(triggerSpecs.getJSONObject(i)).build();
        }

        mMaxBucketIncrements = Integer.parseInt(maxBucketIncrementsString);

        if (eventAttributionStatusString != null && !eventAttributionStatusString.isEmpty()) {
            JSONArray eventAttributionStatus = new JSONArray(eventAttributionStatusString);
            mAttributedTriggers = new ArrayList<>();
            for (int i = 0; i < eventAttributionStatus.length(); i++) {
                JSONObject json = eventAttributionStatus.getJSONObject(i);
                mAttributedTriggers.add(new AttributedTrigger(json));
            }
        }
        mPrivacyParams = new PrivacyComputationParams(privacyParametersString);
    }

    /**
     * This constructor is called during the source registration process.
     *
     * @param triggerSpecsString input trigger specs from ad tech
     * @param maxBucketIncrementsString max bucket increments from ad tech
     * @throws JSONException JSON exception
     */
    public ReportSpec(@NonNull String triggerSpecsString, @NonNull String maxBucketIncrementsString)
            throws JSONException {
        if (triggerSpecsString.isEmpty()) {
            throw new JSONException("the source is not registered as flexible event report API");
        }
        JSONArray triggerSpecs = new JSONArray(triggerSpecsString);

        mTriggerSpecs = new TriggerSpec[triggerSpecs.length()];
        for (int i = 0; i < triggerSpecs.length(); i++) {
            mTriggerSpecs[i] = new TriggerSpec.Builder(triggerSpecs.getJSONObject(i)).build();
        }
        if (maxBucketIncrementsString.isEmpty()) {
            mMaxBucketIncrements = Integer.MAX_VALUE;
        } else {
            mMaxBucketIncrements = Integer.parseInt(maxBucketIncrementsString);
        }
        mPrivacyParams = new PrivacyComputationParams();
        mAttributedTriggers = new ArrayList<>();
    }

    /**
     * @return the information gain
     */
    public double getInformationGain() {
        return getPrivacyParams().mInformationGain;
    }
    /** @return the probability to use fake report */
    public double getFlipProbability() {
        return getPrivacyParams().getFlipProbability();
    }

    /** @return the number of states */
    public int getNumberState() {
        return getPrivacyParams().getNumStates();
    }

    /**
     * Get the parameters for the privacy computation. 1st element: total report cap, an array with
     * 1 element is used to store the integer; 2nd element: number of windows per trigger data type;
     * 3rd element: number of report cap per trigger data type.
     *
     * @return the parameters to computer number of states and fake report
     */
    public int[][] getPrivacyParamsForComputation() {
        int[][] params = new int[3][];
        params[0] = new int[] {mMaxBucketIncrements};
        params[1] = mPrivacyParams.getPerTypeNumWindowList();
        params[2] = mPrivacyParams.getPerTypeCapList();
        return params;
    }

    /**
     * get the privacy parameters
     *
     * @return the privacy params
     */
    public PrivacyComputationParams getPrivacyParams() {
        return mPrivacyParams;
    }

    /**
     * getter method for mTriggerSpecs
     *
     * @return the array of TriggerSpec
     */
    public TriggerSpec[] getTriggerSpecs() {
        return mTriggerSpecs;
    }

    /** @return Max bucket increments. (a.k.a max number of reports) */
    public int getMaxReports() {
        return mMaxBucketIncrements;
    }

    /**
     * Get the trigger data type given a trigger data index. In the flexible event API, the trigger
     * data is not necessary input as [0, 1, 2..]
     *
     * @param triggerDataIndex The index of the triggerData
     * @return the value of the trigger data
     */
    public UnsignedLong getTriggerDataValue(int triggerDataIndex) {
        for (TriggerSpec triggerSpec : mTriggerSpecs) {
            int prevTriggerDataIndex = triggerDataIndex;
            triggerDataIndex -= triggerSpec.getTriggerData().size();
            if (triggerDataIndex < 0) {
                return triggerSpec.getTriggerData().get(prevTriggerDataIndex);
            }
        }
        // will not reach here
        return null;
    }

    /**
     * Define the report level priority if multiple trigger contribute to a report. Incoming
     * priority will be compared with previous triggers priority to get the highest priority
     *
     * @param triggerData the trigger data
     * @param incomingPriority the priority of incoming trigger of this trigger data
     * @return the highest priority of this trigger data
     */
    public long getHighestPriorityOfAttributedAndIncomingTriggers(
            UnsignedLong triggerData, Long incomingPriority) {
        long highestPriority = Long.MIN_VALUE;
        for (AttributedTrigger trigger : mAttributedTriggers) {
            if (Objects.equals(trigger.getTriggerData(), triggerData)) {
                highestPriority = Long.max(highestPriority, trigger.getPriority());
            }
        }
        highestPriority = Long.max(highestPriority, incomingPriority);
        return highestPriority;
    }

    private int[] computePerTypeNumWindowList() {
        List<Integer> list = new ArrayList<>();
        for (TriggerSpec triggerSpec : mTriggerSpecs) {
            for (UnsignedLong ignored : triggerSpec.getTriggerData()) {
                list.add(triggerSpec.getEventReportWindowsEnd().size());
            }
        }
        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    private int[] computePerTypeCapList() {
        List<Integer> list = new ArrayList<>();
        for (TriggerSpec triggerSpec : mTriggerSpecs) {
            for (UnsignedLong ignored : triggerSpec.getTriggerData()) {
                list.add(triggerSpec.getSummaryBucket().size());
            }
        }
        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ReportSpec)) {
            return false;
        }
        ReportSpec t = (ReportSpec) obj;

        return mMaxBucketIncrements == t.mMaxBucketIncrements
                && Objects.equals(mAttributedTriggers, t.mAttributedTriggers)
                && Arrays.equals(mTriggerSpecs, t.mTriggerSpecs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Arrays.hashCode(mTriggerSpecs),
                mMaxBucketIncrements,
                mPrivacyParams,
                mAttributedTriggers);
    }

    /**
     * Encode the privacy reporting parameters to JSON
     *
     * @return json object encode this class
     */
    public String encodeTriggerSpecsToJSON() {
        try {
        JSONObject[] triggerSpecsArray = new JSONObject[mTriggerSpecs.length];
        for (int i = 0; i < mTriggerSpecs.length; i++) {
            triggerSpecsArray[i] = mTriggerSpecs[i].encodeJSON();
        }
            return new JSONArray(triggerSpecsArray).toString();
        } catch (JSONException e) {
            LogUtil.e("ReportSpec::encodeTriggerSpecsToJSON is unable to encode TriggerSpecs");
            return null;
        }
    }

    /**
     * Encode the result of privacy parameters computed based on input parameters to JSON
     *
     * @return String encoded the privacy parameters
     */
    public String encodePrivacyParametersToJSONString() {
        JSONObject json = new JSONObject();
        try {
            json.put(
                    ReportSpecUtil.FlexEventReportJsonKeys.FLIP_PROBABILITY,
                    mPrivacyParams.mFlipProbability);
        } catch (JSONException e) {
            LogUtil.e(
                    "ReportSpec::encodePrivacyParametersToJSONString is unable to encode"
                            + " PrivacyParams to JSON");
            return null;
        }
        return json.toString();
    }

    /**
     * @return the JSON encoded current status
     */
    public JSONArray encodeTriggerStatusToJSON() {
        JSONArray jsonArray = new JSONArray();
        for (AttributedTrigger trigger : mAttributedTriggers) {
            jsonArray.put(trigger.encodeToJSON());
        }
        return jsonArray;
    }

    /**
     * Obtaining trigger value from trigger id.
     *
     * @param triggerId the trigger id for query
     * @return the value from the queried trigger id
     */
    public long getTriggerValue(String triggerId) {
        for (AttributedTrigger trigger : mAttributedTriggers) {
            if (trigger.getTriggerId().equals(triggerId)) {
                return trigger.getValue();
            }
        }
        return 0L;
    }

    /**
     * Record the trigger in the attribution status
     *
     * @param eventReport incoming report
     */
    public void insertAttributedTrigger(EventReport eventReport) {
        mAttributedTriggers.add(
                new AttributedTrigger(
                        eventReport.getTriggerId(),
                        eventReport.getTriggerPriority(),
                        eventReport.getTriggerData(),
                        eventReport.getTriggerValue(),
                        eventReport.getTriggerTime(),
                        eventReport.getTriggerDedupKey()));
    }

    /**
     * Delete the history of an event report
     *
     * @param eventReport the event report to be deleted
     */
    public boolean deleteFromAttributedValue(EventReport eventReport) {
        Iterator<AttributedTrigger> iterator = mAttributedTriggers.iterator();
        while (iterator.hasNext()) {
            AttributedTrigger element = iterator.next();
            if (element.getTriggerId().equals(eventReport.getTriggerId())) {
                iterator.remove();
                return true;
            }
        }
        LogUtil.e("ReportSpec::deleteFromAttributedValue: eventReport cannot be found");
        return false;
    }

    long findCurrentAttributedValue(UnsignedLong triggerData) {
        long result = 0;
        for (AttributedTrigger trigger : mAttributedTriggers) {
            if (Objects.equals(trigger.mTriggerData, triggerData)) {
                result += trigger.getValue();
            }
        }
        return result;
    }

    /**
     * @param triggerData the triggerData to be checked
     * @return whether the triggerData is registered
     */
    public boolean containsTriggerData(UnsignedLong triggerData) {
        for (TriggerSpec triggerSpec : mTriggerSpecs) {
            for (UnsignedLong registeredTriggerData : triggerSpec.getTriggerData()) {
                if (Objects.equals(registeredTriggerData, triggerData)) {
                    return true;
                }
            }
        }
        return false;
    }

    @VisibleForTesting
    public List<AttributedTrigger> getAttributedTriggers() {
        return mAttributedTriggers;
    }

    /**
     * @return all of the trigger id in format of String list
     */
    public List<String> getAllTriggerIds() {
        List<String> result = new ArrayList<>();
        for (AttributedTrigger trigger : mAttributedTriggers) {
            result.add(trigger.mTriggerId);
        }
        return result;
    }

    private class PrivacyComputationParams {
        private final int[] mPerTypeNumWindowList;
        private final int[] mPerTypeCapList;
        private final int mNumStates;
        private final double mFlipProbability;
        private final double mInformationGain;

        PrivacyComputationParams() {
            mPerTypeNumWindowList = computePerTypeNumWindowList();
            mPerTypeCapList = computePerTypeCapList();

            // Check the upper bound of the parameters
            if (Math.min(mMaxBucketIncrements, Arrays.stream(mPerTypeCapList).sum())
                    > PrivacyParams.getMaxFlexibleEventReports()) {
                throw new IllegalArgumentException(
                        "Max Event Reports Exceeds " + PrivacyParams.getMaxFlexibleEventReports());
            }
            if (mPerTypeNumWindowList.length
                    > PrivacyParams.getMaxFlexibleEventTriggerDataCardinality()) {
                throw new IllegalArgumentException(
                        "Trigger Data Cardinality Exceeds "
                                + PrivacyParams.getMaxFlexibleEventTriggerDataCardinality());
            }

            // check duplication of the trigger data
            Set<UnsignedLong> seen = new HashSet<>();
            for (TriggerSpec triggerSpec : mTriggerSpecs) {
                for (UnsignedLong num : triggerSpec.getTriggerData()) {
                    if (!seen.add(num)) {
                        throw new IllegalArgumentException("Duplication in Trigger Data");
                    }
                }
            }

            // compute number of state and other privacy parameters
            mNumStates =
                    Combinatorics.getNumStatesFlexAPI(
                            mMaxBucketIncrements, mPerTypeNumWindowList, mPerTypeCapList);
            mFlipProbability = Combinatorics.getFlipProbability(mNumStates);
            mInformationGain = Combinatorics.getInformationGain(mNumStates, mFlipProbability);
        }

        PrivacyComputationParams(String inputLine) throws JSONException {
            JSONObject json = new JSONObject(inputLine);
            mFlipProbability =
                    json.getDouble(ReportSpecUtil.FlexEventReportJsonKeys.FLIP_PROBABILITY);
            mPerTypeNumWindowList = null;
            mPerTypeCapList = null;
            mNumStates = -1;
            mInformationGain = -1.0;
        }

        private double getFlipProbability() {
            return mFlipProbability;
        }

        private int getNumStates() {
            return mNumStates;
        }

        private int[] getPerTypeNumWindowList() {
            return mPerTypeNumWindowList;
        }

        private int[] getPerTypeCapList() {
            return mPerTypeCapList;
        }
    }

    private static class AttributedTrigger {
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
            return Objects.hash(mTriggerId, mPriority, mTriggerData, mValue, mTriggerTime);
        }

        private AttributedTrigger(JSONObject json) throws JSONException {
            mTriggerId = json.getString(ReportSpecUtil.FlexEventReportJsonKeys.TRIGGER_ID);
            mPriority = json.getLong(ReportSpecUtil.FlexEventReportJsonKeys.PRIORITY);
            mTriggerData =
                    new UnsignedLong(
                            json.getString(ReportSpecUtil.FlexEventReportJsonKeys.TRIGGER_DATA));
            mValue = json.getLong(ReportSpecUtil.FlexEventReportJsonKeys.VALUE);
            mTriggerTime = json.getLong(ReportSpecUtil.FlexEventReportJsonKeys.TRIGGER_TIME);
            mDedupKey =
                    new UnsignedLong(
                            json.getString(ReportSpecUtil.FlexEventReportJsonKeys.DEDUP_KEY));
        }

        private AttributedTrigger(
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

        @VisibleForTesting
        public UnsignedLong getTriggerData() {
            return mTriggerData;
        }

        @VisibleForTesting
        public long getPriority() {
            return mPriority;
        }

        @VisibleForTesting
        public long getValue() {
            return mValue;
        }

        @VisibleForTesting
        public String getTriggerId() {
            return mTriggerId;
        }

        private JSONObject encodeToJSON() {
            JSONObject json = new JSONObject();
            try {
                json.put(ReportSpecUtil.FlexEventReportJsonKeys.TRIGGER_ID, mTriggerId);
                json.put(
                        ReportSpecUtil.FlexEventReportJsonKeys.TRIGGER_DATA,
                        mTriggerData.toString());
                json.put(ReportSpecUtil.FlexEventReportJsonKeys.TRIGGER_TIME, mTriggerTime);
                json.put(ReportSpecUtil.FlexEventReportJsonKeys.VALUE, mValue);
                json.put(ReportSpecUtil.FlexEventReportJsonKeys.DEDUP_KEY, mDedupKey.toString());
                json.put(ReportSpecUtil.FlexEventReportJsonKeys.PRIORITY, mPriority);
            } catch (JSONException e) {
                LogUtil.e("ReportSpec::encodeToJSON cannot encode AttributedTrigger to JSON");
                return null;
            }
            return json;
        }
    }
}
