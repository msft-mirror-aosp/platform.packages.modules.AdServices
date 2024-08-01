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

package android.adservices.test.scenario.adservices.iapc;

import static com.android.adservices.helpers.AdIdLatencyHelper.AD_ID_COLD_START_LATENCY_METRIC;
import static com.android.adservices.helpers.AdIdLatencyHelper.AD_ID_HOT_START_LATENCY_METRIC;
import static com.android.adservices.helpers.AdIdLatencyHelper.TAG;
import static com.android.adservices.shared.testing.concurrency.DeviceSideConcurrencyHelper.sleep;

import android.adservices.adid.AdId;
import android.adservices.adid.AdIdManager;
import android.util.Log;

import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.AdServicesOutcomeReceiverForTests;
import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.common.annotations.SetAllLogcatTags;

import org.junit.Rule;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

// How to run the test:
// Note: not using javadoc because of /*/
//
// m -j AdServicesScenarioTests && adb root && adb install $ANDROID_PRODUCT_OUT/testcases/AdServicesScenarioTests/*/AdServicesScenarioTests.apk && adb shell am instrument -w -e class android.adservices.test.scenario.adservices.iapc.GetAdIdApiCallMicrobenchmark -e iterations 3 android.platform.test.scenario/androidx.test.runner.AndroidJUnitRunner

/** The base class to measure the start-up latency for Ad ID API. */
// TODO(b/334161996): Enhance AdsServices performance tests.
@SetAllLogcatTags
abstract class GetAdIdApiCallBase extends AdServicesUnitTestCase {

    @Rule(order = 11)
    public AdServicesFlagsSetterRule flags = AdServicesFlagsSetterRule.newInstance();

    private static final Executor sCallbackExecutor = Executors.newCachedThreadPool();

    protected void measureGetAdIdCall() throws Exception {
        callGetAdId(AD_ID_COLD_START_LATENCY_METRIC);

        sleep(1000, "Need to sleep here to prevent going above the Rate Limit");

        callGetAdId(AD_ID_HOT_START_LATENCY_METRIC);
    }

    private void callGetAdId(String label) throws Exception {
        Log.i(TAG, "Calling getAdId()");
        long start = System.currentTimeMillis();

        AdServicesOutcomeReceiverForTests<AdId> callback =
                new AdServicesOutcomeReceiverForTests<>();
        AdIdManager adIdManager = AdIdManager.get(sContext);

        adIdManager.getAdId(sCallbackExecutor, callback);

        AdId resultAdId = callback.assertSuccess();

        long duration = System.currentTimeMillis() - start;
        // TODO(b/234452723): In the future, we will want to use either statsd or perfetto instead.
        Log.i(TAG, "(" + label + ": " + duration + ")");

        expect.withMessage("resultAdId").that(resultAdId).isNotNull();
        expect.withMessage("getAdId()").that(resultAdId.getAdId()).isNotNull();
    }
}
