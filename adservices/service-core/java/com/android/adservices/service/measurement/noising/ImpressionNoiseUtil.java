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

package com.android.adservices.service.measurement.noising;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Util class for generating impression noise
 */
public final class ImpressionNoiseUtil {

    private ImpressionNoiseUtil() {}

    /**
     * Randomly generate report configs based on noise params
     *
     * @param noiseParams Noise parameters to use for state generation
     * @param rand random number generator
     * @return list of reporting configs
     */
    public static List<int[]> selectRandomStateAndGenerateReportConfigs(
            ImpressionNoiseParams noiseParams, Random rand) {
        // Get total possible combinations
        int numCombinations = Combinatorics.getNumberOfStarsAndBarsSequences(
                /*numStars=*/noiseParams.getReportCount(),
                /*numBars=*/noiseParams.getTriggerDataCardinality()
                        * noiseParams.getReportingWindowCount());
        // Choose a sequence index
        int sequenceIndex = rand.nextInt(numCombinations);
        return getReportConfigsForSequenceIndex(noiseParams, sequenceIndex);
    }

    @VisibleForTesting
    static List<int[]> getReportConfigsForSequenceIndex(
            ImpressionNoiseParams noiseParams, int sequenceIndex) {
        List<int[]> reportConfigs = new ArrayList<>();
        int triggerDataCardinality = noiseParams.getTriggerDataCardinality();
        // Get the configuration for the sequenceIndex
        int[] starIndices = Combinatorics.getStarIndices(
                /*numStars=*/noiseParams.getReportCount(),
                /*sequenceIndex=*/sequenceIndex);
        int[] barsPrecedingEachStar = Combinatorics.getBarsPrecedingEachStar(starIndices);
        // Generate fake reports
        // Stars: number of reports
        // Bars: (Number of windows) * (Trigger Data Cardinality)
        for (int numBars : barsPrecedingEachStar) {
            if (numBars == 0) {
                continue;
            }
            int windowIndex = (numBars - 1) / triggerDataCardinality;
            int triggerData = (numBars - 1) % triggerDataCardinality;
            reportConfigs.add(new int[] { triggerData, windowIndex });
        }
        return reportConfigs;
    }
}
