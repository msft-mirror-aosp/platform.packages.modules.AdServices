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

package com.android.adservices.service.measurement.attribution;

import java.util.Random;

/**
 * Class for random selection utilities.
 */
public class RandomSelector {

    /**
     * Function to select true or randomly pick from possible value based on probability.
     *
     * @param randomProb     probability to use for selecting a random state.
     * @param trueValue      truth value
     * @param possibleValues set of all possible value for the result
     * @return selected value
     */
    public static <T> T selectRandomDataWithProbability(
            double randomProb, T trueValue, T[] possibleValues) {
        Random rand = new Random();
        double value = rand.nextDouble();
        if (value < randomProb) {
            return possibleValues[rand.nextInt(possibleValues.length)];
        } else {
            return trueValue;
        }
    }
}
