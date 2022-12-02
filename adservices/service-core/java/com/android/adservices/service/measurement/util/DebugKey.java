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

package com.android.adservices.service.measurement.util;

import android.util.Pair;

import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;

/** Util class for DebugKeys */
public class DebugKey {

    /** Returns DebugKey according to the permissions set */
    public static Pair<UnsignedLong, UnsignedLong> getDebugKeys(Source source, Trigger trigger) {
        UnsignedLong sourceDebugKey = null;
        UnsignedLong triggerDebugKey = null;
        boolean isFromSameApp = trigger.getRegistrant().equals(source.getRegistrant());
        if (trigger.getDestinationType() == EventSurfaceType.APP) {
            // App Registration
            if (source.hasAdIdPermission()) {
                sourceDebugKey = source.getDebugKey();
            }
            if (trigger.hasAdIdPermission()) {
                triggerDebugKey = trigger.getDebugKey();
            }
        } else {
            // Web Registration
            if (source.hasArDebugPermission() && (isFromSameApp || source.hasAdIdPermission())) {
                sourceDebugKey = source.getDebugKey();
            }
            if (trigger.hasArDebugPermission() && (isFromSameApp || trigger.hasAdIdPermission())) {
                triggerDebugKey = trigger.getDebugKey();
            }
        }
        return new Pair<>(sourceDebugKey, triggerDebugKey);
    }
}
