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
import android.annotation.NonNull;
import android.content.Context;

import com.android.adservices.LogUtil;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.service.exception.JSExecutionException;
import com.android.adservices.service.js.JSScriptArgument;
import com.android.adservices.service.js.JSScriptEngine;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Function;
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

    private static final String TAG = AdSelectionScriptEngine.class.getName();

    // TODO: (b/228094391): Put these common constants in a separate class
    public static final String FUNCTION_NAMES_ARG_NAME = "__rb_functionNames";
    private static final int JS_SCRIPT_STATUS_SUCCESS = 0;
    public static final String RESULTS_FIELD_NAME = "results";
    public static final String STATUS_FIELD_NAME = "status";
    // This is a local variable and doesn't need any prefix.
    public static final String AD_VAR_NAME = "ad";
    public static final String ADS_ARG_NAME = "__rb_ads";
    public static final String AUCTION_SIGNALS_ARG_NAME = "__rb_auction_signals";
    public static final String PER_BUYER_SIGNALS_ARG_NAME = "__rb_per_buyer_signals";
    public static final String TRUSTED_BIDDING_SIGNALS_ARG_NAME = "__rb_trusted_bidding_signals";
    public static final String CONTEXTUAL_SIGNALS_ARG_NAME = "__rb_contextual_signals";
    public static final String USER_SIGNALS_ARG_NAME = "__rb_user_signals";
    public static final String CUSTOM_AUDIENCE_SIGNALS_ARG_NAME = "__rb_custom_audience_signals";
    public static final String AUCTION_CONFIG_ARG_NAME = "__rb_auction_config";
    public static final String SELLER_SIGNALS_ARG_NAME = "__rb_seller_signals";
    public static final String TRUSTED_SCORING_SIGNALS_ARG_NAME = "__rb_trusted_scoring_signals";

    /**
     * Template for the batch invocation function. The two tokens to expand are the list of
     * parameters and the invocation of the actual per-ad function.
     */
    public static final String AD_SELECTION_BATCH_PROCESSING_JS =
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

    public static final String CHECK_FUNCTIONS_EXIST_JS =
            "function "
                    + JSScriptEngine.ENTRY_POINT_FUNC_NAME
                    + "(names) {\n"
                    + " for (const name of names) {\n"
                    + "   if (typeof name != 'function') return false;\n"
                    + " }\n"
                    + " return true;\n"
                    + "}";

    private final JSScriptEngine mJsEngine;
    // Used for the Futures.transform calls to compose futures.
    private final Executor mExecutor = MoreExecutors.directExecutor();

    public AdSelectionScriptEngine(Context context) {
        mJsEngine = JSScriptEngine.getInstance(context);
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
            @NonNull String auctionSignals,
            @NonNull String perBuyerSignals,
            @NonNull String trustedBiddingSignals,
            @NonNull String contextualSignals,
            @NonNull String userSignals,
            @NonNull CustomAudienceSignals customAudienceSignals)
            throws JSONException {
        Objects.requireNonNull(generateBidJS);
        Objects.requireNonNull(ads);
        Objects.requireNonNull(auctionSignals);
        Objects.requireNonNull(perBuyerSignals);
        Objects.requireNonNull(trustedBiddingSignals);
        Objects.requireNonNull(contextualSignals);
        Objects.requireNonNull(userSignals);
        Objects.requireNonNull(customAudienceSignals);

        ImmutableList<JSScriptArgument> signals =
                ImmutableList.<JSScriptArgument>builder()
                        .add(jsonArg(AUCTION_SIGNALS_ARG_NAME, auctionSignals))
                        .add(jsonArg(PER_BUYER_SIGNALS_ARG_NAME, perBuyerSignals))
                        .add(jsonArg(TRUSTED_BIDDING_SIGNALS_ARG_NAME, trustedBiddingSignals))
                        .add(jsonArg(CONTEXTUAL_SIGNALS_ARG_NAME, contextualSignals))
                        .add(jsonArg(USER_SIGNALS_ARG_NAME, userSignals))
                        .add(
                                CustomAudienceSignalsArgument.asScriptArgument(
                                        customAudienceSignals, CUSTOM_AUDIENCE_SIGNALS_ARG_NAME))
                        .build();

        ImmutableList.Builder<JSScriptArgument> adDataArguments = new ImmutableList.Builder<>();
        for (AdData currAd : ads) {
            // Ads are going to be in an array their individual name is ignored.
            adDataArguments.add(AdDataArgument.asScriptArgument("ignored", currAd));
        }
        return transform(
                runAuctionScript(
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
            @NonNull String sellerSignals,
            @NonNull String trustedScoringSignals,
            @NonNull String contextualSignals,
            @NonNull CustomAudienceSignals customAudienceSignals)
            throws JSONException {
        Objects.requireNonNull(scoreAdJS);
        Objects.requireNonNull(adsWithBid);
        Objects.requireNonNull(adSelectionConfig);
        Objects.requireNonNull(sellerSignals);
        Objects.requireNonNull(trustedScoringSignals);
        Objects.requireNonNull(contextualSignals);
        Objects.requireNonNull(customAudienceSignals);

        ImmutableList<JSScriptArgument> args =
                ImmutableList.<JSScriptArgument>builder()
                        .add(
                                AdSelectionConfigArgument.asScriptArgument(
                                        adSelectionConfig, AUCTION_CONFIG_ARG_NAME))
                        .add(jsonArg(SELLER_SIGNALS_ARG_NAME, sellerSignals))
                        .add(jsonArg(TRUSTED_SCORING_SIGNALS_ARG_NAME, trustedScoringSignals))
                        .add(jsonArg(CONTEXTUAL_SIGNALS_ARG_NAME, contextualSignals))
                        .add(
                                CustomAudienceSignalsArgument.asScriptArgument(
                                        customAudienceSignals, CUSTOM_AUDIENCE_SIGNALS_ARG_NAME))
                        .build();

        ImmutableList.Builder<JSScriptArgument> adWithBidArguments = new ImmutableList.Builder<>();
        for (AdWithBid currAdWithBid : adsWithBid) {
            // Ad with bids are going to be in an array their individual name is ignored.
            adWithBidArguments.add(AdWithBidArgument.asScriptArgument("ignored", currAdWithBid));
        }
        return transform(
                runAuctionScript(scoreAdJS, adWithBidArguments.build(), args, this::callScoreAd),
                this::handleScoreAdsOutput,
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
    private List<Double> handleScoreAdsOutput(AuctionScriptResult batchBidResult) {
        if (batchBidResult.status != JS_SCRIPT_STATUS_SUCCESS) {
            LogUtil.v("Scoring script failed, returning empty result.");
            return ImmutableList.of();
        } else {
            ImmutableList.Builder<Double> result = ImmutableList.builder();
            for (int i = 0; i < batchBidResult.results.length(); i++) {
                // If the output of the score for this advert is invalid JSON or doesn't have a
                // score we are dropping the advert by scoring it with 0.
                result.add(batchBidResult.results.optJSONObject(i).optDouble("score", 0.0));
            }
            return result.build();
        }
    }

    /**
     * Runs the function call generated by {@code auctionFunctionCallGenerator} in the JS script
     * {@see jsScript} for the list of {code ads} provided. The function will be called by a
     * generated extra function that is responsible for iterating through all arguments and causing
     * an early failure if the result of any of the function invocations is not an object containing
     * a 'status' field or the value of the 'status' is not 0. In case of success status is 0, if
     * the result doesn't have a status field, status is -1 otherwise the status is the non-zero
     * status returned by the failed invocation. The 'results' field contains the JSON array with
     * the results of the function invocations. The parameter {@code auctionFunctionCallGenerator}
     * is responsible for generating the call to the auction function by splitting the advert data
     *
     * <p>The inner function call generated by {@code auctionFunctionCallGenerator} will receive for
     * every call one of the ads or ads with bid and the extra arguments specified using {@see
     * otherArgs} in the order they are specified.
     *
     * @return A future with the result of the function or failing with {@link
     *     IllegalArgumentException} if the script is not valid, doesn't contain {@see
     *     auctionFunctionName}.
     */
    ListenableFuture<AuctionScriptResult> runAuctionScript(
            String jsScript,
            List<JSScriptArgument> ads,
            List<JSScriptArgument> otherArgs,
            Function<List<JSScriptArgument>, String> auctionFunctionCallGenerator) {
        try {
            return transform(
                    callAuctionScript(jsScript, ads, otherArgs, auctionFunctionCallGenerator),
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
        return transform(
                mJsEngine.evaluate(
                        jsScript + "\n" + CHECK_FUNCTIONS_EXIST_JS,
                        ImmutableList.of(
                                stringArrayArg(FUNCTION_NAMES_ARG_NAME, expectedFunctionsNames))),
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
            Function<List<JSScriptArgument>, String> auctionFunctionCallGenerator)
            throws JSONException {
        ImmutableList.Builder<JSScriptArgument> advertsArg = ImmutableList.builder();
        advertsArg.addAll(adverts);

        List<JSScriptArgument> allArgs =
                ImmutableList.<JSScriptArgument>builder()
                        .add(arrayArg(ADS_ARG_NAME, advertsArg.build()))
                        .addAll(otherArgs)
                        .build();

        String argPassing =
                allArgs.stream().map(JSScriptArgument::name).collect(Collectors.joining(", "));

        return mJsEngine.evaluate(
                jsScript
                        + "\n"
                        + String.format(
                                AD_SELECTION_BATCH_PROCESSING_JS,
                                argPassing,
                                auctionFunctionCallGenerator.apply(otherArgs)),
                allArgs);
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

    static class AuctionScriptResult {
        public final int status;
        public final JSONArray results;

        AuctionScriptResult(int status, JSONArray results) {
            this.status = status;
            this.results = results;
        }
    }
}
