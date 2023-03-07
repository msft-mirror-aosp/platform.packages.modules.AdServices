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

import static com.android.adservices.service.measurement.util.DebugKey.DebugApiScenarioEnum.SOURCE_APP_TRIGGER_APP;
import static com.android.adservices.service.measurement.util.DebugKey.DebugApiScenarioEnum.SOURCE_APP_TRIGGER_WEB;
import static com.android.adservices.service.measurement.util.DebugKey.DebugApiScenarioEnum.SOURCE_WEB_TRIGGER_APP;
import static com.android.adservices.service.measurement.util.DebugKey.DebugApiScenarioEnum.SOURCE_WEB_TRIGGER_WEB;

import android.util.Pair;

import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;

/** Util class for DebugKeys */
public class DebugKey {

    protected enum DebugApiScenarioEnum {
        SOURCE_APP_TRIGGER_APP,
        SOURCE_WEB_TRIGGER_WEB,
        SOURCE_APP_TRIGGER_WEB,
        SOURCE_WEB_TRIGGER_APP,
    }

    /** Returns DebugKey according to the permissions set */
    public static Pair<UnsignedLong, UnsignedLong> getDebugKeys(Source source, Trigger trigger) {
        UnsignedLong sourceDebugKey = null;
        UnsignedLong triggerDebugKey = null;
        switch (getDebugApiScenario(source, trigger)) {
            case SOURCE_APP_TRIGGER_APP:
                if (source.hasAdIdPermission()) {
                    sourceDebugKey = source.getDebugKey();
                }
                if (trigger.hasAdIdPermission()) {
                    triggerDebugKey = trigger.getDebugKey();
                }
                break;
            case SOURCE_WEB_TRIGGER_WEB:
                boolean isSameRegistrant = trigger.getRegistrant().equals(source.getRegistrant());
                if (isSameRegistrant) {
                    if (source.hasArDebugPermission()) {
                        sourceDebugKey = source.getDebugKey();
                    }
                    if (trigger.hasArDebugPermission()) {
                        triggerDebugKey = trigger.getDebugKey();
                    }
                }
                break;
            default:
                break;
        }
        return new Pair<>(sourceDebugKey, triggerDebugKey);
    }

    private static DebugApiScenarioEnum getDebugApiScenario(Source source, Trigger trigger) {
        DebugApiScenarioEnum scenario;
        boolean isSourceApp = source.getPublisherType() == EventSurfaceType.APP;
        if (trigger.getDestinationType() == EventSurfaceType.APP) {
            // App Conversion
            scenario = isSourceApp ? SOURCE_APP_TRIGGER_APP : SOURCE_WEB_TRIGGER_APP;
        } else {
            // Web Conversion
            scenario = isSourceApp ? SOURCE_APP_TRIGGER_WEB : SOURCE_WEB_TRIGGER_WEB;
        }
        return scenario;
    }
}
