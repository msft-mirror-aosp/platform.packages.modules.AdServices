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

import static com.android.adservices.service.js.JSScriptArgument.arrayArg;
import static com.android.adservices.service.js.JSScriptArgument.jsonArg;
import static com.android.adservices.service.js.JSScriptArgument.stringArrayArg;

import static com.google.common.util.concurrent.Futures.transform;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdWithBid;
import android.adservices.common.AdData;
import android.adservices.common.AdSelectionSignals;
import android.annotation.NonNull;
import android.content.Context;

import com.android.adservices.LogUtil;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.service.exception.JSExecutionException;
import com.android.adservices.service.js.IsolateSettings;
import com.android.adservices.service.js.JSScriptArgument;
import com.android.adservices.service.js.JSScriptEngine;
import com.android.adservices.service.stats.AdSelectionExecutionLogger;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Utility class to execute an auction script. Current implementation is thread safe but relies on a
 * singleton JS execution environment and will serialize calls done either using the same or
 * different instances of {@link AdSelectionScriptEngine}. This will change once we will use the new
 * WebView API.
 *
 * <p>This class is thread safe but, for performance reasons, it is suggested to use one instance
 * per thread. See the threading comments for {@link JSScriptEngine}.
 */
public class AdSelectionScriptEngine {

    private static final String JS_EXECUTION_STATUS_UNSUCCESSFUL =
            "Outcome selection script failed with status '%s' or returned unexpected result '%s'";

    private static final String JS_EXECUTION_RESULT_INVALID =
            "Result of outcome selection script result is invalid: %s";

    // TODO: (b/228094391): Put these common constants in a separate class
    private static final String SCRIPT_ARGUMENT_NAME_IGNORED = "ignored";
    public static final String FUNCTION_NAMES_ARG_NAME = "__rb_functionNames";
    public static final String RESULTS_FIELD_NAME = "results";
    public static final String STATUS_FIELD_NAME = "status";
    // This is a local variable and doesn't need any prefix.
    public static final String AD_VAR_NAME = "ad";
    public static final String ADS_ARG_NAME = "__rb_ads";
    public static final String AUCTION_SIGNALS_ARG_NAME = "__rb_auction_signals";
    public static final String PER_BUYER_SIGNALS_ARG_NAME = "__rb_per_buyer_signals";
    public static final String TRUSTED_BIDDING_SIGNALS_ARG_NAME = "__rb_trusted_bidding_signals";
    public static final String CONTEXTUAL_SIGNALS_ARG_NAME = "__rb_contextual_signals";
    public static final String CUSTOM_AUDIENCE_BIDDING_SIGNALS_ARG_NAME =
            "__rb_custom_audience_bidding_signals";
    public static final String CUSTOM_AUDIENCE_SCORING_SIGNALS_ARG_NAME =
            "__rb_custom_audience_scoring_signals";
    public static final String AUCTION_CONFIG_ARG_NAME = "__rb_auction_config";
    public static final String SELLER_SIGNALS_ARG_NAME = "__rb_seller_signals";
    public static final String TRUSTED_SCORING_SIGNALS_ARG_NAME = "__rb_trusted_scoring_signals";
    /**
     * Template for the iterative invocation function. The two tokens to expand are the list of
     * parameters and the invocation of the actual per-ad function.
     */
    public static final String AD_SELECTION_ITERATIVE_PROCESSING_JS =
            "function "
                    + JSScriptEngine.ENTRY_POINT_FUNC_NAME
                    + "(%s) {\n"
                    + " let status = 0;\n"
                    + " const results = []; \n"
                    + " for (const "
                    + AD_VAR_NAME
                    + " of "
                    + ADS_ARG_NAME
                    + ") {\n"
                    + "   //Short circuit the processing of all ads if there was any failure.\n"
                    + "   const script_result = %s;\n"
                    + "   if (script_result === Object(script_result) && \n"
                    + "         'status' in script_result) {\n"
                    + "      status = script_result.status;\n"
                    + "   } else {\n"
                    + "     // invalid script\n"
                    + "     status = -1;\n"
                    + "   } \n"
                    + "   if (status != 0) break;\n"
                    + "   results.push(script_result);\n"
                    + "  }\n"
                    + "  return { 'status': status, 'results': results};\n"
                    + "};";

    /**
     * Template for the batch invocation function. The two tokens to expand are the list of
     * parameters and the invocation of the actual per-ad function.
     */
    public static final String AD_SELECTION_BATCH_PROCESSING_JS =
            "function "
                    + JSScriptEngine.ENTRY_POINT_FUNC_NAME
                    + "(%s) {\n"
                    + "  let status = 0;\n"
                    + "  const results = []; \n"
                    + "  const script_result = %s;\n"
                    + "  if (script_result === Object(script_result) && \n"
                    + "      'status' in script_result && \n"
                    + "      'result' in script_result) {\n"
                    + "    status = script_result.status;\n"
                    + "    results.push(script_result.result)\n"
                    + "  } else {\n"
                    + "    // invalid script\n"
                    + "    status = -1;\n"
                    + "  }\n"
                    + "  return { 'status': status, 'results': results};\n"
                    + "};";

    public static final String CHECK_FUNCTIONS_EXIST_JS =
            "function "
                    + JSScriptEngine.ENTRY_POINT_FUNC_NAME
                    + "(names) {\n"
                    + " for (const name of names) {\n"
                    + "   if (typeof name != 'function') return false;\n"
                    + " }\n"
                    + " return true;\n"
                    + "}";
    private static final String TAG = AdSelectionScriptEngine.class.getName();
    private static final int JS_SCRIPT_STATUS_SUCCESS = 0;
    private final JSScriptEngine mJsEngine;
    // Used for the Futures.transform calls to compose futures.
    private final Executor mExecutor = MoreExecutors.directExecutor();
    private final Supplier<Boolean> mEnforceMaxHeapSizeFeatureSupplier;
    private final Supplier<Long> mMaxHeapSizeBytesSupplier;

    public AdSelectionScriptEngine(
            Context context,
            Supplier<Boolean> enforceMaxHeapSizeFeatureSupplier,
            Supplier<Long> maxHeapSizeBytesSupplier) {
        mJsEngine = JSScriptEngine.getInstance(context);
        mEnforceMaxHeapSizeFeatureSupplier = enforceMaxHeapSizeFeatureSupplier;
        mMaxHeapSizeBytesSupplier = maxHeapSizeBytesSupplier;
    }

    /**
     * @return The result of invoking the {@code generateBid} function in the given {@code
     *     generateBidJS} JS script for the list of {@code ads} and signals provided. Will return an
     *     empty list if the script fails for any reason.
     * @throws JSONException If any of the signals is not a valid JSON object.
     */
    public ListenableFuture<List<AdWithBid>> generateBids(
            @NonNull String generateBidJS,
            @NonNull List<AdData> ads,
            @NonNull AdSelectionSignals auctionSignals,
            @NonNull AdSelectionSignals perBuyerSignals,
            @NonNull AdSelectionSignals trustedBiddingSignals,
            @NonNull AdSelectionSignals contextualSignals,
            @NonNull CustomAudienceSignals customAudienceSignals)
            throws JSONException {
        Objects.requireNonNull(generateBidJS);
        Objects.requireNonNull(ads);
        Objects.requireNonNull(auctionSignals);
        Objects.requireNonNull(perBuyerSignals);
        Objects.requireNonNull(trustedBiddingSignals);
        Objects.requireNonNull(contextualSignals);
        Objects.requireNonNull(customAudienceSignals);

        ImmutableList<JSScriptArgument> signals =
                ImmutableList.<JSScriptArgument>builder()
                        .add(jsonArg(AUCTION_SIGNALS_ARG_NAME, auctionSignals.toString()))
                        .add(jsonArg(PER_BUYER_SIGNALS_ARG_NAME, perBuyerSignals.toString()))
                        .add(
                                jsonArg(
                                        TRUSTED_BIDDING_SIGNALS_ARG_NAME,
                                        trustedBiddingSignals.toString()))
                        .add(jsonArg(CONTEXTUAL_SIGNALS_ARG_NAME, contextualSignals.toString()))
                        .add(
                                CustomAudienceBiddingSignalsArgument.asScriptArgument(
                                        CUSTOM_AUDIENCE_BIDDING_SIGNALS_ARG_NAME,
                                        customAudienceSignals))
                        .build();

        ImmutableList.Builder<JSScriptArgument> adDataArguments = new ImmutableList.Builder<>();
        for (AdData currAd : ads) {
            // Ads are going to be in an array their individual name is ignored.
            adDataArguments.add(AdDataArgument.asScriptArgument("ignored", currAd));
        }
        return transform(
                runAuctionScriptIterative(
                        generateBidJS, adDataArguments.build(), signals, this::callGenerateBid),
                this::handleGenerateBidsOutput,
                mExecutor);
    }

    /**
     * @return The scored ads for this custom audiences given the list of Ads with associated bid
     *     and the set of signals. Will return an empty list if the script fails for any reason.
     * @throws JSONException If any of the data is not a valid JSON object.
     */
    public ListenableFuture<List<Double>> scoreAds(
            @NonNull String scoreAdJS,
            @NonNull List<AdWithBid> adsWithBid,
            @NonNull AdSelectionConfig adSelectionConfig,
            @NonNull AdSelectionSignals sellerSignals,
            @NonNull AdSelectionSignals trustedScoringSignals,
            @NonNull AdSelectionSignals contextualSignals,
            @NonNull List<CustomAudienceSignals> customAudienceSignalsList,
            @NonNull AdSelectionExecutionLogger adSelectionExecutionLogger)
            throws JSONException {
        Objects.requireNonNull(scoreAdJS);
        Objects.requireNonNull(adsWithBid);
        Objects.requireNonNull(adSelectionConfig);
        Objects.requireNonNull(sellerSignals);
        Objects.requireNonNull(trustedScoringSignals);
        Objects.requireNonNull(contextualSignals);
        Objects.requireNonNull(customAudienceSignalsList);
        Objects.requireNonNull(adSelectionExecutionLogger);
        ImmutableList<JSScriptArgument> args =
                ImmutableList.<JSScriptArgument>builder()
                        .add(
                                AdSelectionConfigArgument.asScriptArgument(
                                        adSelectionConfig, AUCTION_CONFIG_ARG_NAME))
                        .add(jsonArg(SELLER_SIGNALS_ARG_NAME, sellerSignals.toString()))
                        .add(
                                jsonArg(
                                        TRUSTED_SCORING_SIGNALS_ARG_NAME,
                                        trustedScoringSignals.toString()))
                        .add(jsonArg(CONTEXTUAL_SIGNALS_ARG_NAME, contextualSignals.toString()))
                        .add(
                                CustomAudienceScoringSignalsArgument.asScriptArgument(
                                        CUSTOM_AUDIENCE_SCORING_SIGNALS_ARG_NAME,
                                        customAudienceSignalsList))
                        .build();

        ImmutableList.Builder<JSScriptArgument> adWithBidArguments = new ImmutableList.Builder<>();
        for (AdWithBid currAdWithBid : adsWithBid) {
            // Ad with bids are going to be in an array their individual name is ignored.
            adWithBidArguments.add(
                    AdWithBidArgument.asScriptArgument(
                            SCRIPT_ARGUMENT_NAME_IGNORED, currAdWithBid));
        }
        // Start scoreAds script execution process.
        adSelectionExecutionLogger.startScoreAds();
        return FluentFuture.from(
                        runAuctionScriptIterative(
                                scoreAdJS, adWithBidArguments.build(), args, this::callScoreAd))
                .transform(
                        result -> handleScoreAdsOutput(result, adSelectionExecutionLogger),
                        mExecutor);
    }

    /**
     * Runs selection logic on map of {@code long} ad selection id {@code double} bid
     *
     * @return either one of the ad selection ids passed in {@code adSelectionIdBidPairs} or {@code
     *     null}
     * @throws JSONException if any input or the result is failed to parse
     * @throws IllegalStateException If JS script fails to run or returns an illegal results (i.e.
     *     two ad selection ids or empty)
     */
    public ListenableFuture<Long> selectOutcome(
            @NonNull String selectionLogic,
            @NonNull List<AdSelectionIdWithBidAndRenderUri> adSelectionIdWithBidAndRenderUris,
            @NonNull AdSelectionSignals selectionSignals)
            throws JSONException, IllegalStateException {
        Objects.requireNonNull(selectionLogic);
        Objects.requireNonNull(adSelectionIdWithBidAndRenderUris);
        Objects.requireNonNull(selectionSignals);

        ImmutableList<JSScriptArgument> args =
                ImmutableList.<JSScriptArgument>builder()
                        .add(jsonArg("selection_signals", selectionSignals.toString()))
                        .build();
        LogUtil.v("Other args creates " + args);

        ImmutableList.Builder<JSScriptArgument> adSelectionIdWithBidArguments =
                new ImmutableList.Builder<>();
        for (AdSelectionIdWithBidAndRenderUri curr : adSelectionIdWithBidAndRenderUris) {
            // Ad with bids are going to be in an array their individual name is ignored.
            adSelectionIdWithBidArguments.add(
                    SelectAdsFromOutcomesArgument.asScriptArgument(
                            SCRIPT_ARGUMENT_NAME_IGNORED, curr));
        }
        ImmutableList<JSScriptArgument> advertArgs = adSelectionIdWithBidArguments.build();
        LogUtil.v("Advert args created " + advertArgs);

        return transform(
                runAuctionScriptBatch(selectionLogic, advertArgs, args, this::callSelectOutcome),
                this::handleSelectOutcomesOutput,
                mExecutor);
    }

    /**
     * Parses the output from the invocation of the {@code generateBid} JS function on a list of ads
     * and convert it to a list of {@link AdWithBid} objects. The script output has been pre-parsed
     * into an {@link AuctionScriptResult} object that will contain the script status code and the
     * list of ads. The method will return an empty list of ads if the status code is not {@link
     * #JS_SCRIPT_STATUS_SUCCESS} or if there has been any problem parsing the JS response.
     */
    private List<AdWithBid> handleGenerateBidsOutput(AuctionScriptResult batchBidResult) {
        if (batchBidResult.status != JS_SCRIPT_STATUS_SUCCESS) {
            LogUtil.v("Bid script failed, returning empty result.");
            return ImmutableList.of();
        } else {
            try {
                ImmutableList.Builder<AdWithBid> result = ImmutableList.builder();
                for (int i = 0; i < batchBidResult.results.length(); i++) {
                    result.add(
                            AdWithBidArgument.parseJsonResponse(
                                    batchBidResult.results.optJSONObject(i)));
                }
                return result.build();
            } catch (IllegalArgumentException e) {
                LogUtil.w(
                        e,
                        "Invalid ad with bid returned by a generateBid script. Returning empty"
                                + " list of ad with bids.");
                return ImmutableList.of();
            }
        }
    }

    /**
     * Parses the output from the invocation of the {@code scoreAd} JS function on a list of ad with
     * associated bids {@link Double}. The script output has been pre-parsed into an {@link
     * AuctionScriptResult} object that will contain the script status code and the list of scores.
     * The method will return an empty list of ads if the status code is not {@link
     * #JS_SCRIPT_STATUS_SUCCESS} or if there has been any problem parsing the JS response.
     */
    private List<Double> handleScoreAdsOutput(
            AuctionScriptResult batchBidResult,
            AdSelectionExecutionLogger adSelectionExecutionLogger) {
        ImmutableList.Builder<Double> result = ImmutableList.builder();
        if (batchBidResult.status != JS_SCRIPT_STATUS_SUCCESS) {
            LogUtil.v("Scoring script failed, returning empty result.");
        } else {
            for (int i = 0; i < batchBidResult.results.length(); i++) {
                // If the output of the score for this advert is invalid JSON or doesn't have a
                // score we are dropping the advert by scoring it with 0.
                result.add(batchBidResult.results.optJSONObject(i).optDouble("score", 0.0));
            }
        }
        adSelectionExecutionLogger.endScoreAds();
        return result.build();
    }

    /**
     * Parses the output from the invocation of the {@code selectOutcome} JS function on a list of
     * ad selection ids {@link Double} with associated bids {@link Double}. The script output has
     * been pre-parsed into an {@link AuctionScriptResult} object that will contain the script
     * status code and the results as a list. This handler expects a single result in the {@code
     * results} or an empty list which is also valid as long as {@code status} is {@link
     * #JS_SCRIPT_STATUS_SUCCESS}
     *
     * <p>The method will return a status code is not {@link #JS_SCRIPT_STATUS_SUCCESS} if there has
     * been any problem executing JS script or parsing the JS response.
     *
     * @throws IllegalStateException is thrown in case the status is not success or the results has
     *     more than one item.
     */
    private Long handleSelectOutcomesOutput(AuctionScriptResult scriptResults)
            throws IllegalStateException {
        if (scriptResults.status != JS_SCRIPT_STATUS_SUCCESS
                || scriptResults.results.length() != 1) {
            String errorMsg =
                    String.format(
                            JS_EXECUTION_STATUS_UNSUCCESSFUL,
                            scriptResults.status,
                            scriptResults.results);
            LogUtil.v(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        if (scriptResults.results.isNull(0)) {
            return null;
        }

        try {
            JSONObject resultOutcomeJson = scriptResults.results.getJSONObject(0);
            // Use Long class to parse from string
            return Long.valueOf(
                    resultOutcomeJson.optString(SelectAdsFromOutcomesArgument.ID_FIELD_NAME));
        } catch (JSONException e) {
            String errorMsg = String.format(JS_EXECUTION_RESULT_INVALID, scriptResults.results);
            LogUtil.v(errorMsg);
            throw new IllegalStateException(errorMsg);
        }
    }

    /**
     * Runs the function call generated by {@code auctionFunctionCallGenerator} in the JS script
     * {@code jsScript} for the list of {code ads} provided. The function will be called by a
     * generated extra function that is responsible for iterating through all arguments and causing
     * an early failure if the result of the function invocations is not an object containing a
     * 'status' field or the value of the 'status' is not 0. In case of success status is 0, if the
     * result doesn't have a status field, status is -1 otherwise the status is the non-zero status
     * returned by the failed invocation. The 'results' field contains the JSON array with the
     * results of the function invocations. The parameter {@code auctionFunctionCallGenerator} is
     * responsible for generating the call to the auction function by splitting the advert data
     *
     * <p>The inner function call generated by {@code auctionFunctionCallGenerator} will receive for
     * every call one of the ads or ads with bid and the extra arguments specified using {@code
     * otherArgs} in the order they are specified.
     *
     * @return A future with the result of the function or failing with {@link
     *     IllegalArgumentException} if the script is not valid, doesn't contain {@code
     *     auctionFunctionName}.
     */
    ListenableFuture<AuctionScriptResult> runAuctionScriptIterative(
            String jsScript,
            List<JSScriptArgument> ads,
            List<JSScriptArgument> otherArgs,
            Function<List<JSScriptArgument>, String> auctionFunctionCallGenerator) {
        try {
            return transform(
                    callAuctionScript(
                            jsScript,
                            ads,
                            otherArgs,
                            auctionFunctionCallGenerator,
                            AD_SELECTION_ITERATIVE_PROCESSING_JS),
                    this::parseAuctionScriptResult,
                    mExecutor);
        } catch (JSONException e) {
            throw new JSExecutionException(
                    "Illegal result returned by our internal iterative calling function.", e);
        }
    }

    /**
     * Runs the function call generated by {@code auctionFunctionCallGenerator} in the JS script
     * {@code jsScript} for the list of {@code ads} provided. The function will be called by a
     * generated extra function that is responsible for calling the JS script.
     *
     * <p>If the result of the function invocations is not an object containing a 'status' or
     * 'results' field, or the value of the 'status' is not 0 then will return failure status.
     *
     * <p>In case of success status is 0. The 'results' field contains the JSON array with the
     * results of the function invocations. The parameter {@code auctionFunctionCallGenerator} is
     * responsible for generating the call to the auction function by passing the list of advert
     * data
     *
     * <p>The inner function call generated by {@code auctionFunctionCallGenerator} will receive the
     * list of ads and the extra arguments specified using {@code otherArgs} in the order they are
     * specified.
     *
     * @return A future with the result of the function or failing with {@link
     *     IllegalArgumentException} if the script is not valid, doesn't contain {@code
     *     auctionFunctionName}.
     */
    ListenableFuture<AuctionScriptResult> runAuctionScriptBatch(
            String jsScript,
            List<JSScriptArgument> ads,
            List<JSScriptArgument> otherArgs,
            Function<List<JSScriptArgument>, String> auctionFunctionCallGenerator) {
        try {
            return transform(
                    callAuctionScript(
                            jsScript,
                            ads,
                            otherArgs,
                            auctionFunctionCallGenerator,
                            AD_SELECTION_BATCH_PROCESSING_JS),
                    this::parseAuctionScriptResult,
                    mExecutor);
        } catch (JSONException e) {
            throw new JSExecutionException(
                    "Illegal result returned by our internal batch calling function.", e);
        }
    }

    /**
     * @return A {@link ListenableFuture} containing the result of the validation of the given
     *     {@code jsScript} script. A script is valid if it is valid JS code and it contains all the
     *     functions specified in {@code expectedFunctionsNames} are defined in the script. There is
     *     no validation of the expected signature.
     */
    ListenableFuture<Boolean> validateAuctionScript(
            String jsScript, List<String> expectedFunctionsNames) {
        IsolateSettings isolateSettings =
                mEnforceMaxHeapSizeFeatureSupplier.get()
                        ? IsolateSettings.forMaxHeapSizeEnforcementEnabled(
                                mMaxHeapSizeBytesSupplier.get())
                        : IsolateSettings.forMaxHeapSizeEnforcementDisabled();
        return transform(
                mJsEngine.evaluate(
                        jsScript + "\n" + CHECK_FUNCTIONS_EXIST_JS,
                        ImmutableList.of(
                                stringArrayArg(FUNCTION_NAMES_ARG_NAME, expectedFunctionsNames)),
                        isolateSettings),
                Boolean::parseBoolean,
                mExecutor);
    }

    private AuctionScriptResult parseAuctionScriptResult(String auctionScriptResult) {
        try {
            if (auctionScriptResult.isEmpty()) {
                throw new IllegalArgumentException(
                        "The auction script either doesn't contain the required function or the"
                                + " function returns null");
            }

            JSONObject jsonResult = new JSONObject(auctionScriptResult);

            return new AuctionScriptResult(
                    jsonResult.getInt(STATUS_FIELD_NAME),
                    jsonResult.getJSONArray(RESULTS_FIELD_NAME));
        } catch (JSONException e) {
            throw new RuntimeException(
                    "Illegal result returned by our internal batch calling function.", e);
        }
    }

    /**
     * @return a {@link ListenableFuture} containing the string representation of a JSON object
     *     containing two fields:
     *     <p>
     *     <ul>
     *       <li>{@code status} field that will be 0 in case of successful processing of all ads or
     *           non-zero if any of the calls to processed an ad returned a non-zero status. In the
     *           last case the returned status will be the same returned in the failing invocation.
     *           The function {@code auctionFunctionName} is assumed to return a JSON object
     *           containing at least a {@code status} field.
     *       <li>{@code results} with the results of the invocation of {@code auctionFunctionName}
     *           to all the given ads.
     *     </ul>
     *     <p>
     */
    private ListenableFuture<String> callAuctionScript(
            String jsScript,
            List<JSScriptArgument> adverts,
            List<JSScriptArgument> otherArgs,
            Function<List<JSScriptArgument>, String> auctionFunctionCallGenerator,
            String adSelectionProcessorJS)
            throws JSONException {
        ImmutableList.Builder<JSScriptArgument> advertsArg = ImmutableList.builder();
        advertsArg.addAll(adverts);
        LogUtil.v(
                "script: %s%nadverts: %s%nother args: %s%nprocessor script: %s%n",
                jsScript, advertsArg, otherArgs, adSelectionProcessorJS);

        List<JSScriptArgument> allArgs =
                ImmutableList.<JSScriptArgument>builder()
                        .add(arrayArg(ADS_ARG_NAME, advertsArg.build()))
                        .addAll(otherArgs)
                        .build();

        String argPassing =
                allArgs.stream().map(JSScriptArgument::name).collect(Collectors.joining(", "));

        IsolateSettings isolateSettings =
                mEnforceMaxHeapSizeFeatureSupplier.get()
                        ? IsolateSettings.forMaxHeapSizeEnforcementEnabled(
                                mMaxHeapSizeBytesSupplier.get())
                        : IsolateSettings.forMaxHeapSizeEnforcementDisabled();

        return mJsEngine.evaluate(
                jsScript
                        + "\n"
                        + String.format(
                                adSelectionProcessorJS,
                                argPassing,
                                auctionFunctionCallGenerator.apply(otherArgs)),
                allArgs,
                isolateSettings);
    }

    private String callGenerateBid(List<JSScriptArgument> otherArgs) {
        // The first argument is the local variable "ad" defined in AD_SELECTION_BATCH_PROCESSING_JS
        StringBuilder callArgs = new StringBuilder(AD_VAR_NAME);
        for (JSScriptArgument currArg : otherArgs) {
            callArgs.append(String.format(",%s", currArg.name()));
        }
        return String.format("generateBid(%s)", callArgs.toString());
    }

    private String callScoreAd(List<JSScriptArgument> otherArgs) {
        StringBuilder callArgs =
                new StringBuilder(
                        String.format(
                                "%s.%s, %s.%s",
                                AD_VAR_NAME,
                                AdWithBidArgument.AD_FIELD_NAME,
                                AD_VAR_NAME,
                                AdWithBidArgument.BID_FIELD_NAME));
        for (JSScriptArgument currArg : otherArgs) {
            callArgs.append(String.format(",%s", currArg.name()));
        }
        return String.format("scoreAd(%s)", callArgs.toString());
    }

    private String callSelectOutcome(List<JSScriptArgument> otherArgs) {
        StringBuilder callArgs = new StringBuilder(ADS_ARG_NAME);
        for (JSScriptArgument currArg : otherArgs) {
            callArgs.append(String.format(",%s", currArg.name()));
        }
        return String.format("selectOutcome(%s)", callArgs);
    }

    static class AuctionScriptResult {
        public final int status;
        public final JSONArray results;

        AuctionScriptResult(int status, JSONArray results) {
            this.status = status;
            this.results = results;
        }
    }
}
