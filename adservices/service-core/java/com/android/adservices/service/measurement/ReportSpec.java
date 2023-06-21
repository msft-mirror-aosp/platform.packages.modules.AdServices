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

import static com.android.adservices.service.measurement.Source.ONE_HOUR_IN_MILLIS;

import android.annotation.NonNull;
import android.util.Pair;

import com.android.adservices.LogUtil;
import com.android.adservices.service.measurement.noising.Combinatorics;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.adservices.service.stats.Clock;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A class wrapper for the trigger specification from the input argument during source registration
 */
public class ReportSpec {
    private final TriggerSpec[] mTriggerSpecs;
    private final int mMaxBucketIncrements;
    private PrivacyComputationParams mPrivacyParams = null;
    private List<AttributedTrigger> mAttributedTriggers;

    /**
     * This constructor is called in source registration and when no attribution to this source.
     *
     * @param inputParams input trigger specs from ad tech
     * @param maxBucketIncrements max bucket increments from ad tech
     * @param shouldValidateAndCompute whether the parameters (e.g. number of state) will be
     *     computed.
     */
    @VisibleForTesting
    public ReportSpec(
            JSONArray inputParams, int maxBucketIncrements, boolean shouldValidateAndCompute)
            throws JSONException {
        mMaxBucketIncrements = maxBucketIncrements;
        mTriggerSpecs = new TriggerSpec[inputParams.length()];
        for (int i = 0; i < inputParams.length(); i++) {
            mTriggerSpecs[i] = new TriggerSpec.Builder(inputParams.getJSONObject(i)).build();
        }
        if (shouldValidateAndCompute) {
            mPrivacyParams = new PrivacyComputationParams();
        }
        mAttributedTriggers = new ArrayList<>();
    }

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
        mMaxBucketIncrements = Integer.parseInt(maxBucketIncrementsString);

        mTriggerSpecs = new TriggerSpec[triggerSpecs.length()];
        for (int i = 0; i < triggerSpecs.length(); i++) {
            mTriggerSpecs[i] = new TriggerSpec.Builder(triggerSpecs.getJSONObject(i)).build();
        }
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
        if (maxBucketIncrementsString.isEmpty() || maxBucketIncrementsString.equals("")) {
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
     * Get the reporting window end time given a trigger data and window index
     *
     * @param triggerDataIndex The index of the triggerData
     * @param windowIndex the window index, not the actual window end time
     * @return the report window end time
     */
    public long getWindowEndTime(int triggerDataIndex, int windowIndex) {
        for (TriggerSpec triggerSpec : mTriggerSpecs) {
            triggerDataIndex -= triggerSpec.getTriggerData().size();
            if (triggerDataIndex < 0) {
                return triggerSpec.getEventReportWindowsEnd().get(windowIndex);
            }
        }
        // will not reach here
        return -1;
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
            json.put(FieldsKey.FLIP_PROBABILITY, mPrivacyParams.mFlipProbability);
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
     * Process incoming report, including updating the current attributed value and generate the
     * report should be deleted and number of new report to be generated
     *
     * @param bucketIncrements number of the bucket increments
     * @param proposedEventReport incoming event report
     * @param currentReports existing reports retrieved from DB
     * @return The pair consist 1) The event reports should be deleted. Return empty list if no
     *     deletion is needed. 2) number of reports need to be created
     */
    public Pair<List<EventReport>, Integer> processIncomingReport(
            int bucketIncrements,
            EventReport proposedEventReport,
            List<EventReport> currentReports) {
        if (bucketIncrements == 0
                || bucketIncrements + currentReports.size() <= mMaxBucketIncrements) {
            // No competing condition.
            return new Pair<>(new ArrayList<>(), bucketIncrements);
        }
        long currentTime = Clock.SYSTEM_CLOCK.elapsedRealtime();
        insertAttributedTrigger(proposedEventReport);
        List<EventReport> pendingEventReports =
                currentReports.stream()
                        .filter((r) -> r.getReportTime() > currentTime)
                        .collect(Collectors.toList());
        int numDeliveredReport =
                (int)
                        currentReports.stream()
                                .filter((r) -> r.getReportTime() <= currentTime)
                                .count();

        for (EventReport report : currentReports) {
            if (Objects.equals(report.getTriggerData(), proposedEventReport.getTriggerData())
                    && report.getTriggerPriority() < proposedEventReport.getTriggerPriority()) {
                report.setTriggerPriority(proposedEventReport.getTriggerPriority());
            }
        }

        for (int i = 0; i < bucketIncrements; i++) {
            pendingEventReports.add(proposedEventReport);
        }

        List<EventReport> sortedEventReports =
                pendingEventReports.stream()
                        .sorted(
                                Comparator.comparing(
                                                EventReport::getReportTime,
                                                Comparator.reverseOrder())
                                        .thenComparingLong(EventReport::getTriggerPriority)
                                        .thenComparing(
                                                EventReport::getTriggerTime,
                                                Comparator.reverseOrder()))
                        .collect(Collectors.toList());

        int numOfNewReportGenerated = bucketIncrements;
        List<EventReport> result = new ArrayList<>();
        while (sortedEventReports.size() > mMaxBucketIncrements - numDeliveredReport
                && sortedEventReports.size() > 0) {
            EventReport lowestPriorityEventReport = sortedEventReports.remove(0);
            if (lowestPriorityEventReport.equals(proposedEventReport)) {
                // the new report fall into deletion set. New report count reduce 1 and no need to
                // add to the report to be deleted.
                numOfNewReportGenerated--;
            } else {
                result.add(lowestPriorityEventReport);
            }
        }
        return new Pair<>(result, numOfNewReportGenerated);
    }

    /**
     * Calculates the reporting time based on the {@link Trigger} time for flexible event report API
     *
     * @return the reporting time
     */
    public long getFlexEventReportingTime(
            long sourceRegistrationTime, long triggerTime, UnsignedLong triggerData) {
        if (triggerTime < sourceRegistrationTime) {
            return -1;
        }
        if (triggerTime
                < findReportingStartTimeForTriggerData(triggerData) + sourceRegistrationTime) {
            return -1;
        }

        List<Long> reportingWindows = findReportingEndTimesForTriggerData(triggerData);
        for (Long window : reportingWindows) {
            if (triggerTime <= window + sourceRegistrationTime) {
                return sourceRegistrationTime + window + ONE_HOUR_IN_MILLIS;
            }
        }
        // If trigger time is larger than all window end time, it means the trigger has expired.
        return -1;
    }

    /**
     * @param proposedEventReport the incoming event report
     * @return number of bucket generated
     */
    public int countBucketIncrements(EventReport proposedEventReport) {
        UnsignedLong proposedTriggerData = proposedEventReport.getTriggerData();
        List<Long> summaryWindows = findSummaryBucketForTriggerData(proposedTriggerData);
        if (summaryWindows == null) {
            return 0;
        }
        long currentValue = findCurrentAttributedValue(proposedTriggerData);
        long incomingValue = proposedEventReport.getTriggerValue();
        // current value has already reached to the top of the bucket
        if (currentValue >= summaryWindows.get(summaryWindows.size() - 1)) {
            return 0;
        }

        int currentBucket = -1;
        int newBucket = -1;
        for (int i = 0; i < summaryWindows.size(); i++) {
            if (currentValue >= summaryWindows.get(i)) {
                currentBucket = i;
            }
            if (currentValue + incomingValue >= summaryWindows.get(i)) {
                newBucket = i;
            }
        }
        return newBucket - currentBucket;
    }

    /**
     * @param deletingEventReport the report proposed to be deleted
     * @return number of bucket eliminated
     */
    public int numDecrementingBucket(EventReport deletingEventReport) {
        UnsignedLong proposedEventReportDataType = deletingEventReport.getTriggerData();
        List<Long> summaryWindows = findSummaryBucketForTriggerData(proposedEventReportDataType);
        if (summaryWindows == null) {
            return 0;
        }
        long currentValue = findCurrentAttributedValue(proposedEventReportDataType);
        long incomingValue = getTriggerValue(deletingEventReport.getTriggerId());
        // current value doesn't reach the 1st bucket
        if (currentValue < summaryWindows.get(0)) {
            return 0;
        }

        int currentBucket = -1;
        int newBucket = -1;
        for (int i = 0; i < summaryWindows.size(); i++) {
            if (currentValue >= summaryWindows.get(i)) {
                currentBucket = i;
            }
            if (currentValue - incomingValue >= summaryWindows.get(i)) {
                newBucket = i;
            }
        }
        return currentBucket - newBucket;
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

    @VisibleForTesting
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

    private List<Long> findSummaryBucketForTriggerData(UnsignedLong triggerData) {
        for (TriggerSpec triggerSpec : mTriggerSpecs) {
            if (triggerSpec.getTriggerData().contains(triggerData)) {
                return triggerSpec.getSummaryBucket();
            }
        }
        return null;
    }

    private List<Long> findReportingEndTimesForTriggerData(UnsignedLong triggerData) {
        for (TriggerSpec triggerSpec : mTriggerSpecs) {
            if (triggerSpec.getTriggerData().contains(triggerData)) {
                return triggerSpec.getEventReportWindowsEnd();
            }
        }
        return new ArrayList<>();
    }

    private Long findReportingStartTimeForTriggerData(UnsignedLong triggerData) {
        for (TriggerSpec triggerSpec : mTriggerSpecs) {
            if (triggerSpec.getTriggerData().contains(triggerData)) {
                return triggerSpec.getEventReportWindowsStart();
            }
        }
        return 0L;
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
            mFlipProbability = json.getDouble(FieldsKey.FLIP_PROBABILITY);
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
            mTriggerId = json.getString(FieldsKey.TRIGGER_ID);
            mPriority = json.getLong(FieldsKey.PRIORITY);
            mTriggerData = new UnsignedLong(json.getString(FieldsKey.TRIGGER_DATA));
            mValue = json.getLong(FieldsKey.VALUE);
            mTriggerTime = json.getLong(FieldsKey.TRIGGER_TIME);
            mDedupKey = new UnsignedLong(json.getString(FieldsKey.DEDUP_KEY));
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
                json.put(FieldsKey.TRIGGER_ID, mTriggerId);
                json.put(FieldsKey.TRIGGER_DATA, mTriggerData.toString());
                json.put(FieldsKey.TRIGGER_TIME, mTriggerTime);
                json.put(FieldsKey.VALUE, mValue);
                json.put(FieldsKey.DEDUP_KEY, mDedupKey.toString());
                json.put(FieldsKey.PRIORITY, mPriority);
            } catch (JSONException e) {
                LogUtil.e("ReportSpec::encodeToJSON cannot encode AttributedTrigger to JSON");
                return null;
            }
            return json;
        }
    }

    private interface FieldsKey {
        String TRIGGER_ID = "trigger_id";
        String VALUE = "value";
        String PRIORITY = "priority";
        String TRIGGER_TIME = "trigger_time";
        String DEDUP_KEY = "dedup_key";
        String TRIGGER_DATA = "trigger_data";
        String FLIP_PROBABILITY = "flip_probability";
    }
}
