/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.common;

import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_SCHEDULE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_SIGNALS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_ENABLE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ADDITIONAL_SCHEDULE_REQUESTS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_GLOBAL_KILL_SWITCH;

import static org.mockito.Mockito.when;

import com.android.adservices.service.Flags;
import com.android.adservices.shared.testing.AndroidLogger;
import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.NameValuePair;
import com.android.adservices.shared.testing.mockito.MockitoHelper;

import org.mockito.stubbing.Answer;

import java.util.Objects;

/**
 * @deprecated tests should use {@link AdServicesFakeFlagsSetterRule} instead.
 */
@Deprecated
public final class AdServicesMockFlagsSetterRule
        extends AdServicesFlagsSetterRuleForUnitTests<AdServicesMockFlagsSetterRule, Flags> {

    private static final Logger sLog =
            new Logger(AndroidLogger.getInstance(), AdServicesMockFlagsSetterRule.class);

    /**
     * Default constructor.
     *
     * @param mockFlags mock whose expectations will be set
     */
    public AdServicesMockFlagsSetterRule(Flags mockFlags) {
        super(mockFlags, f -> setExpectation(mockFlags, f));
        if (!MockitoHelper.isMock(mockFlags)) {
            throw new IllegalArgumentException("not a mock: " + mockFlags);
        }
    }

    private static Answer<Boolean> answerBoolean(NameValuePair flag) {
        return inv -> {
            boolean result = Boolean.valueOf(flag.value);
            sLog.v("%s: returning %b", MockitoHelper.toString(inv), result);
            return result;
        };
    }

    // NOTE: new flags should be added to the switch statement on demand.
    // We could use reflection / code generation to support all flags, but in the long term it'd be
    // better for them to use AdServicesFakeFlagsSetterRule instead...
    private static void setExpectation(Flags mockFlags, NameValuePair flag) {
        sLog.v("setExpectation(%s)", flag);
        Objects.requireNonNull(flag, "internal error: NameValuePair cannot be null");

        switch (flag.name) {
            case KEY_GLOBAL_KILL_SWITCH:
                when(mockFlags.getGlobalKillSwitch()).then(answerBoolean(flag));
                return;
            case KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK:
                when(mockFlags.getDisableFledgeEnrollmentCheck())
                        .thenReturn(Boolean.valueOf(flag.value));
                return;
            case KEY_ENFORCE_FOREGROUND_STATUS_SCHEDULE_CUSTOM_AUDIENCE:
                when(mockFlags.getEnforceForegroundStatusForScheduleCustomAudience())
                        .thenReturn(Boolean.valueOf(flag.value));
                return;
            case KEY_ENFORCE_FOREGROUND_STATUS_SIGNALS:
                when(mockFlags.getEnforceForegroundStatusForSignals())
                        .thenReturn(Boolean.valueOf(flag.value));
                return;
            case KEY_FLEDGE_ENABLE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ADDITIONAL_SCHEDULE_REQUESTS:
                when(mockFlags
                                .getFledgeEnableScheduleCustomAudienceUpdateAdditionalScheduleRequests())
                        .thenReturn(Boolean.valueOf(flag.value));
                return;
            case KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ENABLED:
                when(mockFlags.getFledgeScheduleCustomAudienceUpdateEnabled())
                        .thenReturn(Boolean.valueOf(flag.value));
                return;
            default:
                throw new UnsupportedOperationException("Don't know how to mock " + flag);
        }
    }
}
