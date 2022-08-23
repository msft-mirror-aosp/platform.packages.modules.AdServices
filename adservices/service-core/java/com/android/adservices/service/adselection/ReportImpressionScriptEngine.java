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

package com.android.adservices.service.adselection;

import static com.android.adservices.service.js.JSScriptArgument.jsonArg;
import static com.android.adservices.service.js.JSScriptArgument.numericArg;
import static com.android.adservices.service.js.JSScriptArgument.stringArg;

import static com.google.common.util.concurrent.Futures.transform;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.common.AdSelectionSignals;
import android.annotation.NonNull;
import android.content.Context;
import android.net.Uri;

import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.service.js.IsolateSettings;
import com.android.adservices.service.js.JSScriptArgument;
import com.android.adservices.service.js.JSScriptEngine;
import com.android.internal.util.Preconditions;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Utility class to execute a reporting script. Current implementation is thread safe but relies on
 * a singleton JS execution environment and will serialize calls done either using the same or
 * different instances of {@link ReportImpressionScriptEngine}. This will change once we will use
 * the new WebView API.
 *
 * <p>This class is thread safe but, for performance reasons, it is suggested to use one instance
 * per thread. See the threading comments for {@link JSScriptEngine}.
 */
public class ReportImpressionScriptEngine {
    private static final String TAG = "ReportImpressionScriptEngine";

    // TODO: (b/228094391): Put these common constants in a separate class
    private static final int JS_SCRIPT_STATUS_SUCCESS = 0;
    public static final String RESULTS_FIELD_NAME = "results";
    public static final String STATUS_FIELD_NAME = "status";
    public static final String AD_SELECTION_SIGNALS_ARG_NAME = "selection_signals";
    public static final String PER_BUYER_SIGNALS_ARG_NAME = "per_buyer_signals";
    public static final String SIGNALS_FOR_BUYER_ARG_NAME = "signals_for_buyer";
    public static final String CONTEXTUAL_SIGNALS_ARG_NAME = "contextual_signals";
    public static final String CUSTOM_AUDIENCE_SIGNALS_ARG_NAME = "custom_audience_signals";
    public static final String AD_SELECTION_CONFIG_ARG_NAME = "ad_selection_config";
    public static final String BID_ARG_NAME = "bid";
    public static final String RENDER_URL_ARG_NAME = "render_url";
    public static final String SIGNALS_FOR_BUYER_RESPONSE_NAME = "signals_for_buyer";
    public static final String REPORTING_URL_RESPONSE_NAME = "reporting_url";
    public static final String REPORT_RESULT_FUNC_NAME = "reportResult";
    public static final String REPORT_WIN_FUNC_NAME = "reportWin";

    private final JSScriptEngine mJsEngine;
    // Used for the Futures.transform calls to compose futures.
    private final Executor mExecutor = MoreExecutors.directExecutor();
    private final Supplier<Boolean> mEnforceMaxHeapSizeFeatureSupplier;
    private final Supplier<Long> mMaxHeapSizeBytesSupplier;

    public ReportImpressionScriptEngine(
            Context context,
            Supplier<Boolean> enforceMaxHeapSizeFeatureSupplier,
            Supplier<Long> maxHeapSizeBytesSupplier) {
        mJsEngine = JSScriptEngine.getInstance(context);
        mEnforceMaxHeapSizeFeatureSupplier = enforceMaxHeapSizeFeatureSupplier;
        mMaxHeapSizeBytesSupplier = maxHeapSizeBytesSupplier;
    }

    /**
     * @return The result of invoking the {@code reportResult} function in the given {@code
     *     decisionLogicJS} JS script for the {@code adSelectionConfig} bid, and signals provided.
     *     Will return an empty Uri if the script fails for any reason.
     * @param decisionLogicJS Javascript containing the reportResult() function
     * @param adSelectionConfig Configuration object passed by the SDK containing various signals to
     *     be used in ad selection and reporting. See {@link AdSelectionConfig} for more details
     * @param renderUrl Url to render the advert, is an input to the reportResult() function
     * @param bid Bid for the winning ad, is an input to the reportResult() function
     * @param contextualSignals another input to reportResult(), contains fields such as appName
     * @throws JSONException If any of the signals are not a valid JSON object.
     */
    public ListenableFuture<SellerReportingResult> reportResult(
            @NonNull String decisionLogicJS,
            @NonNull AdSelectionConfig adSelectionConfig,
            @NonNull Uri renderUrl,
            @NonNull double bid,
            @NonNull AdSelectionSignals contextualSignals)
            throws JSONException, IllegalStateException {
        Objects.requireNonNull(decisionLogicJS);
        Objects.requireNonNull(adSelectionConfig);
        Objects.requireNonNull(renderUrl);
        Objects.requireNonNull(contextualSignals);

        ImmutableList<JSScriptArgument> arguments =
                ImmutableList.<JSScriptArgument>builder()
                        .add(
                                AdSelectionConfigArgument.asScriptArgument(
                                        adSelectionConfig, AD_SELECTION_CONFIG_ARG_NAME))
                        .add(stringArg(RENDER_URL_ARG_NAME, renderUrl.toString()))
                        .add(numericArg(BID_ARG_NAME, bid))
                        .add(jsonArg(CONTEXTUAL_SIGNALS_ARG_NAME, contextualSignals.toString()))
                        .build();

        return transform(
                runReportingScript(decisionLogicJS, REPORT_RESULT_FUNC_NAME, arguments),
                this::handleReportResultOutput,
                mExecutor);
    }

    /**
     * @return The result of invoking the {@code reportResult} function in the given {@code
     *     decisionLogicJS} JS script for the {@code adSelectionConfig} bid, and signals provided.
     *     Will return an empty Uri if the script fails for any reason.
     * @param biddingLogicJS Javascript containing the reportWin() function
     * @param adSelectionSignals One of the opaque fields of {@link AdSelectionConfig} that is an
     *     input to reportWin()
     * @param perBuyerSignals The value associated with the key {@code buyer} from the {@code
     *     perBuyerSignals} map in {@link AdSelectionConfig}
     * @param signalsForBuyer One of the fields returned by the seller's reportResult(), intended to
     *     be passed to the buyer
     * @param contextualSignals another input to reportWin(), contains fields such as appName
     * @param customAudienceSignals an input to reportWin(), which contains information about the
     *     custom audience the winning ad originated from
     * @throws JSONException If any of the signals are not a valid JSON object.
     */
    public ListenableFuture<Uri> reportWin(
            @NonNull String biddingLogicJS,
            @NonNull AdSelectionSignals adSelectionSignals,
            @NonNull AdSelectionSignals perBuyerSignals,
            @NonNull AdSelectionSignals signalsForBuyer,
            @NonNull AdSelectionSignals contextualSignals,
            @NonNull CustomAudienceSignals customAudienceSignals)
            throws JSONException, IllegalStateException {
        Objects.requireNonNull(biddingLogicJS);
        Objects.requireNonNull(adSelectionSignals);
        Objects.requireNonNull(perBuyerSignals);
        Objects.requireNonNull(signalsForBuyer);
        Objects.requireNonNull(contextualSignals);
        Objects.requireNonNull(customAudienceSignals);

        ImmutableList<JSScriptArgument> arguments =
                ImmutableList.<JSScriptArgument>builder()
                        .add(jsonArg(AD_SELECTION_SIGNALS_ARG_NAME, adSelectionSignals.toString()))
                        .add(jsonArg(PER_BUYER_SIGNALS_ARG_NAME, perBuyerSignals.toString()))
                        .add(jsonArg(SIGNALS_FOR_BUYER_ARG_NAME, signalsForBuyer.toString()))
                        .add(jsonArg(CONTEXTUAL_SIGNALS_ARG_NAME, contextualSignals.toString()))
                        .add(
                                CustomAudienceBiddingSignalsArgument.asScriptArgument(
                                        CUSTOM_AUDIENCE_SIGNALS_ARG_NAME, customAudienceSignals))
                        .build();

        return transform(
                runReportingScript(biddingLogicJS, REPORT_WIN_FUNC_NAME, arguments),
                this::handleReportWinOutput,
                mExecutor);
    }

    ListenableFuture<ReportingScriptResult> runReportingScript(
            String jsScript, String functionName, List<JSScriptArgument> args) {
        try {
            return transform(
                    callReportingScript(jsScript, functionName, args),
                    this::parseReportingOutput,
                    mExecutor);
        } catch (Exception e) {
            throw new IllegalStateException("Illegal result returned by our calling function.", e);
        }
    }

    /**
     * @return a {@link ListenableFuture} containing the string representation of a JSON object
     *     containing two fields:
     *     <p>
     *     <ul>
     *       <li>{@code status} field that will be 0 in case of success or non-zero in case a
     *           failure is encountered. The function {@code reportingFunctionName} is assumed to
     *           return a JSON object containing at least a {@code status} field.
     *       <li>{@code results} with the results of the invocation of {@code reportingFunctionName}
     *     </ul>
     *     <p>
     */
    private ListenableFuture<String> callReportingScript(
            String jsScript, String functionName, List<JSScriptArgument> args)
            throws JSONException {
        IsolateSettings isolateSettings =
                mEnforceMaxHeapSizeFeatureSupplier.get()
                        ? IsolateSettings.forMaxHeapSizeEnforcementEnabled(
                                mMaxHeapSizeBytesSupplier.get())
                        : IsolateSettings.forMaxHeapSizeEnforcementDisabled();
        return mJsEngine.evaluate(jsScript, args, functionName, isolateSettings);
    }

    /**
     * Parses the output from the invocation of the {@code reportResult} JS function and convert it
     * to a {@code reportingUrl}. The script output has been pre-parsed into an {@link
     * ReportingScriptResult} object that will contain the script status code and JSONObject that
     * holds the reportingUrl. The method will throw an exception if the status code is not {@link
     * #JS_SCRIPT_STATUS_SUCCESS} or if there has been any problem parsing the JS response.
     *
     * @throws IllegalStateException If the result is unsuccessful or doesn't match the expected
     *     structure.
     */
    @NonNull
    private SellerReportingResult handleReportResultOutput(
            @NonNull ReportingScriptResult reportResult) {
        Objects.requireNonNull(reportResult);

        Preconditions.checkState(
                reportResult.status == JS_SCRIPT_STATUS_SUCCESS, "Report Result script failed!");
        Preconditions.checkState(
                reportResult.results.length() == 2, "Result does not match expected structure!");
        try {
            return new SellerReportingResult(
                    AdSelectionSignals.fromString(
                            reportResult.results.getString(SIGNALS_FOR_BUYER_RESPONSE_NAME)),
                    Uri.parse(reportResult.results.getString(REPORTING_URL_RESPONSE_NAME)));
        } catch (Exception e) {
            throw new IllegalStateException("Result does not match expected structure!");
        }
    }

    /**
     * Parses the output from the invocation of the {@code reportWin} JS function and convert it to
     * a {@link SellerReportingResult}. The script output has been pre-parsed into an {@link
     * ReportingScriptResult} object that will contain the script status code and JSONObject that
     * holds both signalsForBuyer and reportingUrl. The method will throw an exception if the status
     * code is not {@link #JS_SCRIPT_STATUS_SUCCESS} or if there has been any problem parsing the JS
     * response.
     *
     * @throws IllegalStateException If the result is unsuccessful or doesn't match the expected
     *     structure.
     */
    @NonNull
    private Uri handleReportWinOutput(@NonNull ReportingScriptResult reportResult) {
        Objects.requireNonNull(reportResult);

        Preconditions.checkState(
                reportResult.status == JS_SCRIPT_STATUS_SUCCESS, "Report Result script failed!");
        Preconditions.checkState(
                reportResult.results.length() == 1, "Result does not match expected structure!");
        try {
            return Uri.parse(reportResult.results.getString(REPORTING_URL_RESPONSE_NAME));
        } catch (Exception e) {
            throw new IllegalStateException("Result does not match expected structure!");
        }
    }

    @NonNull
    private ReportingScriptResult parseReportingOutput(@NonNull String reportScriptResult) {
        Objects.requireNonNull(reportScriptResult);
        try {
            Preconditions.checkState(
                    !reportScriptResult.equals("null"),
                    "Null string result returned by report script!");

            JSONObject jsonResult = new JSONObject(reportScriptResult);

            return new ReportingScriptResult(
                    jsonResult.getInt(STATUS_FIELD_NAME),
                    jsonResult.getJSONObject(RESULTS_FIELD_NAME));
        } catch (JSONException e) {
            throw new IllegalStateException("Illegal result returned by our calling function.", e);
        }
    }

    static class ReportingScriptResult {
        public final int status;
        @NonNull public final JSONObject results;

        ReportingScriptResult(int status, @NonNull JSONObject results) {
            Objects.requireNonNull(results);

            this.status = status;
            this.results = results;
        }
    }

    static class SellerReportingResult {
        @NonNull private final AdSelectionSignals mSignalsForBuyer;
        @NonNull private final Uri mReportingUrl;

        SellerReportingResult(
                @NonNull AdSelectionSignals signalsForBuyer, @NonNull Uri reportingUrl) {
            Objects.requireNonNull(signalsForBuyer);
            Objects.requireNonNull(reportingUrl);

            this.mSignalsForBuyer = signalsForBuyer;
            this.mReportingUrl = reportingUrl;
        }

        public AdSelectionSignals getSignalsForBuyer() {
            return mSignalsForBuyer;
        }

        public Uri getReportingUrl() {
            return mReportingUrl;
        }
    }
}
