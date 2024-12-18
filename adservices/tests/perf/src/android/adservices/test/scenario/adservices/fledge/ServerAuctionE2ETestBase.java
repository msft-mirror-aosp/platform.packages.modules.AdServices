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

package android.adservices.test.scenario.adservices.fledge;

import android.adservices.adselection.GetAdSelectionDataOutcome;
import android.adservices.adselection.GetAdSelectionDataRequest;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudience;
import android.adservices.test.scenario.adservices.fledge.utils.CustomAudienceTestFixture;
import android.adservices.test.scenario.adservices.fledge.utils.FakeAdExchangeServer;
import android.content.Context;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

public abstract class ServerAuctionE2ETestBase {
    protected static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    protected static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    protected static final int API_RESPONSE_TIMEOUT_SECONDS = 100;
    protected static final AdSelectionClient AD_SELECTION_CLIENT =
            new AdSelectionClient.Builder()
                    .setContext(CONTEXT)
                    .setExecutor(CALLBACK_EXECUTOR)
                    .build();

    protected abstract String getTag();

    protected void makeWarmUpNetworkCall(String endpointUrl) {
        try {
            URL url = new URL(endpointUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000); // Adjust timeout as needed
            connection.setReadTimeout(5000); // Adjust timeout as needed

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                Log.w(getTag(), "Warm-up call successful for : " + endpointUrl);
            } else {
                Log.w(getTag(), "Failed to make warm-up call. Response code: " + responseCode);
            }
            connection.disconnect();
        } catch (IOException e) {
            Log.w(getTag(), "Error while trying to warm up encryption key server : " + e);
        }
    }

    protected byte[] warmupBiddingAuctionServer(
            String caFileName,
            String seller,
            String contextualSignalsFileName,
            String sfeAddress,
            boolean serverResponseLoggingEnabled)
            throws Exception {
        // The first warm up call brings ups the sfe
        List<CustomAudience> customAudiences =
                CustomAudienceTestFixture.readCustomAudiences(caFileName);
        CustomAudienceTestFixture.joinCustomAudiences(customAudiences);

        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setSeller(AdTechIdentifier.fromString(seller))
                        .build();
        GetAdSelectionDataOutcome outcome =
                retryOnCondition(
                        () ->
                                AD_SELECTION_CLIENT
                                        .getAdSelectionData(request)
                                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                        matchOnTimeoutExecutionException(),
                        /* maxRetries= */ 3,
                        /* retryIntervalMillis= */ 2000L,
                        "getAdSelectionData");

        CustomAudienceTestFixture.leaveCustomAudience(customAudiences);

        runServerAuction(
                contextualSignalsFileName,
                outcome.getAdSelectionData(),
                sfeAddress,
                serverResponseLoggingEnabled);
        return outcome.getAdSelectionData();
    }

    protected void runServerAuction(
            String contextualSignalsFileName,
            byte[] getAdSelectionData,
            String sfeAddress,
            boolean serverResponseLoggingEnabled) {
        try {
            FakeAdExchangeServer.runServerAuction(
                    contextualSignalsFileName,
                    getAdSelectionData,
                    sfeAddress,
                    serverResponseLoggingEnabled);
        } catch (Exception e) {
            Log.w(
                    getTag(),
                    "Exception encountered during first runServerAuction warmup: "
                            + e.getMessage()
                            + ". Continuing execution.");
        }
    }

    protected <T> T retryOnCondition(
            Callable<T> callable,
            Predicate<Exception> retryCondition,
            int maxRetries,
            long retryIntervalMillis,
            String funcName)
            throws Exception {

        int attempt = 1;
        while (attempt <= maxRetries) {
            Log.w(getTag(), String.format("Trying %s. Attempt: %d", funcName, attempt));
            try {
                return callable.call();
            } catch (Exception e) {
                if (retryCondition.test(e)) {
                    attempt++;
                    if (attempt > maxRetries) {
                        throw e; // Rethrow the exception after exceeding retries
                    }
                    Thread.sleep(retryIntervalMillis);
                } else {
                    throw e; // Rethrow immediately for non-retryable exceptions
                }
            }
        }
        return null; // Unreachable in practice, but required for compilation
    }

    protected Predicate<Exception> matchOnTimeoutExecutionException() {
        return e -> e instanceof ExecutionException && e.getCause() instanceof TimeoutException;
    }
}
