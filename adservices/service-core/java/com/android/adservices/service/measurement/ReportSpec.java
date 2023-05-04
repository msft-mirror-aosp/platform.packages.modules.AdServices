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

import com.android.adservices.service.measurement.noising.Combinatorics;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A class wrapper for the trigger specification from the input argument during source registration
 */
public class ReportSpec {
    private final TriggerSpec[] mTriggerSpecs;
    private final int mMaxBucketIncrements;
    private PrivacyComputationParams mPrivacyParams = null;

    public ReportSpec(JSONArray json, int maxBucketIncrements, boolean shouldValidateAndCompute)
            throws JSONException {
        mMaxBucketIncrements = maxBucketIncrements;
        mTriggerSpecs = new TriggerSpec[json.length()];
        for (int i = 0; i < json.length(); i++) {
            mTriggerSpecs[i] = new TriggerSpec.Builder(json.getJSONObject(i)).build();
        }
        if (shouldValidateAndCompute) {
            mPrivacyParams = new PrivacyComputationParams();
        }
    }

    /** @return the probability to use fake report */
    public double getFlipProbability() {
        return getPrivacyParams().getFlipProbability();
    }

    private PrivacyComputationParams getPrivacyParams() {
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

    public int getMaxReports() {
        return mMaxBucketIncrements;
    }

    private int[] computerPerTypeNumWindowList() {
        List<Integer> list = new ArrayList<>();
        for (TriggerSpec triggerSpec : mTriggerSpecs) {
            for (int ignored : triggerSpec.getTriggerData()) {
                list.add(triggerSpec.getEventReportWindowsEnd().size());
            }
        }
        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    private int[] computerPerTypeCapList() {
        List<Integer> list = new ArrayList<>();
        for (TriggerSpec triggerSpec : mTriggerSpecs) {
            for (int ignored : triggerSpec.getTriggerData()) {
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

        if (mTriggerSpecs.length != t.mTriggerSpecs.length) {
            return false;
        }
        for (int i = 0; i < mTriggerSpecs.length; i++) {
            if (!mTriggerSpecs[i].equals(t.mTriggerSpecs[i])) {
                return false;
            }
        }
        return mMaxBucketIncrements == t.mMaxBucketIncrements;
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(mTriggerSpecs), mMaxBucketIncrements);
    }

    /**
     * Encode the all parameter to JSON
     *
     * @return json object encode this class
     */
    public JSONArray encodeTriggerSpecsToJSON() throws JSONException {
        JSONObject[] triggerSpecsArray = new JSONObject[mTriggerSpecs.length];
        for (int i = 0; i < mTriggerSpecs.length; i++) {
            triggerSpecsArray[i] = mTriggerSpecs[i].encodeJSON();
        }
        return new JSONArray(triggerSpecsArray);
    }

    private class PrivacyComputationParams {
        private final int[] mPerTypeNumWindowList;
        private final int[] mPerTypeCapList;
        private final int mNumStates;
        private final double mFlipProbability;
        private final double mInformationGain;

        PrivacyComputationParams() throws JSONException {
            mPerTypeNumWindowList = computerPerTypeNumWindowList();
            mPerTypeCapList = computerPerTypeCapList();

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
            Set<Integer> seen = new HashSet<>();
            for (TriggerSpec triggerSpec : mTriggerSpecs) {
                for (int num : triggerSpec.getTriggerData()) {
                    if (!seen.add(num)) {
                        throw new IllegalArgumentException("Duplication in Trigger Data");
                    }
                }
            }

            // computer number of state and other privacy parameters
            mNumStates =
                    Combinatorics.getNumStatesFlexAPI(
                            mMaxBucketIncrements, mPerTypeNumWindowList, mPerTypeCapList);
            mFlipProbability = Combinatorics.getFlipProbability(mNumStates);
            mInformationGain = Combinatorics.getInformationGain(mNumStates, mFlipProbability);
            if (mInformationGain > PrivacyParams.getMaxFlexibleEventInformationGain()) {
                throw new IllegalArgumentException(
                        "Information Gain Exceeds "
                                + PrivacyParams.getMaxFlexibleEventInformationGain());
            }
        }

        private double getFlipProbability() {
            return mFlipProbability;
        }
    }
}
