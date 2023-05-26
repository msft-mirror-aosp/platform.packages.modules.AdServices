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

package com.android.adservices.service.measurement.reporting;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Report related utility methods. */
public class ReportUtil {
    /**
     * Prepares a list of attribution destinations for report JSON.
     *
     * @param destinations a list of attribution destinations
     * @return an Object that is either String or JSONArray
     */
    public static Object serializeAttributionDestinations(List<Uri> destinations)
            throws JSONException {
        if (destinations.size() == 0) {
            throw new IllegalArgumentException("Destinations list is empty");
        }

        if (destinations.size() == 1) {
            return destinations.get(0).toString();
        } else {
            List<Uri> destinationsCopy = new ArrayList<>(destinations);
            destinationsCopy.sort(Comparator.comparing(Uri::toString));
            return new JSONArray(
                    destinationsCopy.stream()
                            .map(Uri::toString)
                            .collect(Collectors.toList()));
        }
    }
}
