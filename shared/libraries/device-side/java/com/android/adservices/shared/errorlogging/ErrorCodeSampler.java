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

package com.android.adservices.shared.errorlogging;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ERROR_CODE_PRESENT_MULTIPLE_TIMES_IN_PROTO;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;

import android.util.Log;

import com.android.adservices.shared.common.flags.ModuleSharedFlags;
import com.android.adservices.shared.proto.ErrorCodeList;
import com.android.adservices.shared.proto.ErrorCodeSampleInterval;
import com.android.adservices.shared.util.ProtoParser;
import com.android.internal.annotations.VisibleForTesting;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

/**
 * Implements custom sampling per error code.
 *
 * <p>Reads Ph Flags and determines the sampling interval for the error code.
 */
public final class ErrorCodeSampler {
    private static final String ERROR_CODE_SAMPLE_INTERVAL_PROPERTY = "error_code_sample_interval";
    private static final String TAG = ErrorCodeSampler.class.getSimpleName();

    private final Random mRandom;
    private final AdServicesErrorLogger mErrorLogger;
    private int mDefaultSampleInterval = 1;
    private Map<Integer, Integer> mErrorCodeToSampleInterval = new HashMap<>();

    public ErrorCodeSampler(ModuleSharedFlags flags, AdServicesErrorLogger errorLogger) {
        this(flags, errorLogger, new Random());
    }

    @VisibleForTesting
    ErrorCodeSampler(ModuleSharedFlags flags, AdServicesErrorLogger errorLogger, Random random) {
        mErrorLogger = errorLogger;
        mRandom = random;
        // Reads and parses the flag and creates the map: errorCode -> sampleInterval in the
        // constructor so that we only have to do it once.
        parseFlagAndInitMap(flags);
    }

    private void parseFlagAndInitMap(ModuleSharedFlags flags) {
        String errorCodeSampleIntervalFlag = flags.getEncodedErrorCodeListPerSampleInterval();

        ErrorCodeSampleInterval errorCodeSampleInterval =
                ProtoParser.parseBase64EncodedStringToProto(
                        ErrorCodeSampleInterval.parser(),
                        mErrorLogger,
                        ERROR_CODE_SAMPLE_INTERVAL_PROPERTY,
                        errorCodeSampleIntervalFlag);
        if (errorCodeSampleInterval == null) {
            return;
        }
        if (errorCodeSampleInterval.hasDefaultSampleInterval()) {
            mDefaultSampleInterval = errorCodeSampleInterval.getDefaultSampleInterval();
        }
        toErrorCodeToSampleInterval(errorCodeSampleInterval.getSampleIntervalToErrorCodesMap());
    }

    private void toErrorCodeToSampleInterval(
            Map<Integer, ErrorCodeList> sampleIntervalToErrorCodeMap) {
        for (Entry<Integer, ErrorCodeList> entry : sampleIntervalToErrorCodeMap.entrySet()) {
            ErrorCodeList errorCodeList = entry.getValue();
            int sampleInterval = entry.getKey();
            for (int errorCode : errorCodeList.getErrorCodeList()) {
                if (mErrorCodeToSampleInterval.containsKey(errorCode)) {
                    Log.e(TAG, String.format("Error code: %d present multiple times", errorCode));
                    mErrorLogger.logError(
                            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ERROR_CODE_PRESENT_MULTIPLE_TIMES_IN_PROTO,
                            AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
                }
                mErrorCodeToSampleInterval.put(errorCode, sampleInterval);
            }
        }
    }

    /** Performs random sampling to determine whether to log error or not. */
    public boolean shouldLog(int errorCode) {
        int sampleInterval = mDefaultSampleInterval;
        if (mErrorCodeToSampleInterval.containsKey(errorCode)) {
            sampleInterval = mErrorCodeToSampleInterval.get(errorCode);
        }

        boolean logError = shouldLogBasedOnSampleInterval(sampleInterval);
        Log.d(
                TAG,
                String.format(
                        "Logging error code: %d for the sample interval %d: %b",
                        errorCode, sampleInterval, logError));
        return logError;
    }

    private boolean shouldLogBasedOnSampleInterval(int sampleInterval) {
        if (sampleInterval > 0) {
            return sampleInterval == 1 || mRandom.nextInt(sampleInterval) == 1;
        }
        return false;
    }
}
