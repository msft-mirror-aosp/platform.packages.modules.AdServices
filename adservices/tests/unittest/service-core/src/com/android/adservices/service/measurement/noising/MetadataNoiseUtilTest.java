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

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Arrays;
import java.util.Objects;

public class MetadataNoiseUtilTest {

    @Test
    public void selectRandomDataWithProbability_event() {
        Integer trueValue = 1;
        Integer[] possibleValues = new Integer[]{0, 1};
        double randomProb = 0.50D;
        int randomCount = 0;
        for (int i = 0; i < 100; i++) {
            Integer result = MetadataNoiseUtil.selectRandomDataWithProbability(
                    randomProb, trueValue, possibleValues);
            assertTrue(Arrays.stream(possibleValues).anyMatch((v) -> result == v));
            if (!Objects.equals(result, trueValue)) {
                randomCount++;
            }
        }
        assertNotEquals(0, randomCount);
    }

    @Test
    public void selectRandomDataWithProbability_navigation() {
        Integer trueValue = 1;
        Integer[] possibleValues = new Integer[]{0, 1, 2, 3, 4, 5, 6, 7};
        double randomProb = 0.50D;
        int randomCount = 0;
        for (int i = 0; i < 100; i++) {
            Integer result = MetadataNoiseUtil.selectRandomDataWithProbability(
                    randomProb, trueValue, possibleValues);
            assertTrue(Arrays.stream(possibleValues).anyMatch((v) -> result == v));
            if (!Objects.equals(result, trueValue)) {
                randomCount++;
            }
        }
        assertNotEquals(0, randomCount);
    }
}
