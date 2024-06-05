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

package com.android.adservices.service.stats;

import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__TARGETING;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.EqualsTester;

import org.junit.Test;

/** Unit test for {@link ApiCallStats}. */
public final class ApiCallStatsTest extends AdServicesUnitTestCase {

    private static final int LATENCY_MS = 100;

    private final String mAppPackageName = "com.android.test";
    private final String mSdkPackageName = "com.android.container";

    @Test
    public void testBuilderCreateSuccess() {
        ApiCallStats stats =
                newCanonicalBuilder()
                        .setCode(AD_SERVICES_API_CALLED)
                        .setApiClass(AD_SERVICES_API_CALLED__API_CLASS__TARGETING)
                        .setApiName(AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS)
                        .setLatencyMillisecond(LATENCY_MS)
                        .setResultCode(STATUS_INVALID_ARGUMENT)
                        .build();

        expect.withMessage("%s.getCode()", stats)
                .that(stats.getCode())
                .isEqualTo(AD_SERVICES_API_CALLED);
        expect.withMessage("%s.getApiClass()", stats)
                .that(stats.getApiClass())
                .isEqualTo(AD_SERVICES_API_CALLED__API_CLASS__TARGETING);
        expect.withMessage("%s.getApiName()", stats)
                .that(stats.getApiName())
                .isEqualTo(AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS);
        expect.withMessage("%s.getAppPackageName()", stats)
                .that(stats.getAppPackageName())
                .isEqualTo(mAppPackageName);
        expect.withMessage("%s.getSdkPackageName()", stats)
                .that(stats.getSdkPackageName())
                .isEqualTo(mSdkPackageName);
        expect.withMessage("%s.getLatencyMillisecond()", stats)
                .that(stats.getLatencyMillisecond())
                .isEqualTo(LATENCY_MS);

        expect.withMessage("%s.getResultCode()", stats)
                .that(stats.getResultCode())
                .isEqualTo(STATUS_INVALID_ARGUMENT);
    }

    @Test
    public void testNullSdkPackageName_throwsNPE() {
        assertThrows(
                NullPointerException.class,
                () -> new ApiCallStats.Builder().setSdkPackageName(null));
    }

    @Test
    public void testNullAppPackageName_throwsNPE() {
        assertThrows(
                NullPointerException.class,
                () -> new ApiCallStats.Builder().setAppPackageName(null));
    }

    @Test
    public void testBuild_noAppPackageName() {
        assertThrows(
                IllegalStateException.class,
                () -> new ApiCallStats.Builder().setSdkPackageName("package.I.am").build());
    }

    @Test
    public void testBuild_noSdkPackageName() {
        assertThrows(
                IllegalStateException.class,
                () -> new ApiCallStats.Builder().setAppPackageName("package.I.am").build());
    }

    @Test
    public void testToString() {
        ApiCallStats stats =
                newCanonicalBuilder()
                        .setCode(AD_SERVICES_API_CALLED)
                        .setApiClass(AD_SERVICES_API_CALLED__API_CLASS__TARGETING)
                        .setApiName(AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS)
                        .setLatencyMillisecond(LATENCY_MS)
                        .setResultCode(STATUS_INVALID_ARGUMENT)
                        .build();

        String toString = stats.toString();

        expect.that(toString).startsWith("ApiCallStats");
        expect.that(toString).contains("Code=" + AD_SERVICES_API_CALLED);
        expect.that(toString).contains("ApiClass=" + AD_SERVICES_API_CALLED__API_CLASS__TARGETING);
        expect.that(toString).contains("ApiName=" + AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS);
        expect.that(toString).contains("AppPackageName='" + mAppPackageName + "'");
        expect.that(toString).contains("SdkPackageName='" + mSdkPackageName + "'");
        expect.that(toString).contains("LatencyMillisecond=" + LATENCY_MS);
        expect.that(toString).contains("ResultCode=" + STATUS_INVALID_ARGUMENT);
    }

    @Test
    public void testEqualsHashCode() {
        EqualsTester et = new EqualsTester(expect);
        int baseCode = AD_SERVICES_API_CALLED;
        int baseApiClass = AD_SERVICES_API_CALLED__API_CLASS__TARGETING;
        int baseApiName = AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS;
        String baseAppPackageName = mAppPackageName;
        String baseSdkPackageName = mSdkPackageName;
        int baseLatencyMs = LATENCY_MS;
        int resultCode = STATUS_INVALID_ARGUMENT;

        ApiCallStats equals1 =
                new ApiCallStats.Builder()
                        .setCode(baseCode)
                        .setApiClass(baseApiClass)
                        .setApiName(baseApiName)
                        .setAppPackageName(baseAppPackageName)
                        .setSdkPackageName(baseSdkPackageName)
                        .setLatencyMillisecond(baseLatencyMs)
                        .setResultCode(resultCode)
                        .build();

        ApiCallStats equals2 =
                new ApiCallStats.Builder()
                        .setCode(baseCode)
                        .setApiClass(baseApiClass)
                        .setApiName(baseApiName)
                        .setAppPackageName(baseAppPackageName)
                        .setSdkPackageName(baseSdkPackageName)
                        .setLatencyMillisecond(baseLatencyMs)
                        .setResultCode(resultCode)
                        .build();

        ApiCallStats different1 =
                new ApiCallStats.Builder()
                        .setCode(baseCode + 42)
                        .setApiClass(baseApiClass)
                        .setApiName(baseApiName)
                        .setAppPackageName(baseAppPackageName)
                        .setSdkPackageName(baseSdkPackageName)
                        .setLatencyMillisecond(baseLatencyMs)
                        .setResultCode(resultCode)
                        .build();

        ApiCallStats different2 =
                new ApiCallStats.Builder()
                        .setCode(baseCode)
                        .setApiClass(baseApiClass + 42)
                        .setApiName(baseApiName)
                        .setAppPackageName(baseAppPackageName)
                        .setSdkPackageName(baseSdkPackageName)
                        .setLatencyMillisecond(baseLatencyMs)
                        .setResultCode(resultCode)
                        .build();

        ApiCallStats different3 =
                new ApiCallStats.Builder()
                        .setCode(baseCode)
                        .setApiClass(baseApiClass)
                        .setApiName(baseApiName + 42)
                        .setAppPackageName(baseAppPackageName)
                        .setSdkPackageName(baseSdkPackageName)
                        .setLatencyMillisecond(baseLatencyMs)
                        .setResultCode(resultCode)
                        .build();

        ApiCallStats different4 =
                new ApiCallStats.Builder()
                        .setCode(baseCode)
                        .setApiClass(baseApiClass)
                        .setApiName(baseApiName)
                        .setAppPackageName(baseAppPackageName + ".doh")
                        .setSdkPackageName(baseSdkPackageName)
                        .setLatencyMillisecond(baseLatencyMs)
                        .setResultCode(resultCode)
                        .build();

        ApiCallStats different5 =
                new ApiCallStats.Builder()
                        .setCode(baseCode)
                        .setApiClass(baseApiClass)
                        .setApiName(baseApiName)
                        .setAppPackageName(baseAppPackageName)
                        .setSdkPackageName(baseSdkPackageName + ".doh")
                        .setLatencyMillisecond(baseLatencyMs)
                        .setResultCode(resultCode)
                        .build();

        ApiCallStats different6 =
                new ApiCallStats.Builder()
                        .setCode(baseCode)
                        .setApiClass(baseApiClass)
                        .setApiName(baseApiName)
                        .setAppPackageName(baseAppPackageName)
                        .setSdkPackageName(baseSdkPackageName)
                        .setLatencyMillisecond(baseLatencyMs + 42)
                        .setResultCode(resultCode)
                        .build();

        ApiCallStats different7 =
                new ApiCallStats.Builder()
                        .setCode(baseCode)
                        .setApiClass(baseApiClass)
                        .setApiName(baseApiName)
                        .setAppPackageName(baseAppPackageName)
                        .setSdkPackageName(baseSdkPackageName)
                        .setLatencyMillisecond(baseLatencyMs)
                        .setResultCode(resultCode + 42)
                        .build();

        et.expectObjectsAreEqual(equals1, equals1);
        et.expectObjectsAreEqual(equals1, equals2);

        et.expectObjectsAreNotEqual(equals1, null);
        et.expectObjectsAreNotEqual(equals1, "STATS, Y U NO STRING?");

        et.expectObjectsAreNotEqual(equals1, different1);
        et.expectObjectsAreNotEqual(equals1, different2);
        et.expectObjectsAreNotEqual(equals1, different3);
        et.expectObjectsAreNotEqual(equals1, different4);
        et.expectObjectsAreNotEqual(equals1, different5);
        et.expectObjectsAreNotEqual(equals1, different6);
        et.expectObjectsAreNotEqual(equals1, different7);
    }

    // Creates a builder with the bare minimum required state.
    private ApiCallStats.Builder newCanonicalBuilder() {
        return new ApiCallStats.Builder()
                .setAppPackageName(mAppPackageName)
                .setSdkPackageName(mSdkPackageName);
    }

}
