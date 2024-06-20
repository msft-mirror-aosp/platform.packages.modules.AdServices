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

package com.android.adservices.tests.cts.customaudience;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.eventually;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.utils.MockWebServerRule;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Process;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.LoggerFactory;
import com.android.adservices.LoggerFactory.Logger;
import com.android.adservices.common.AdservicesTestHelper;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multiset;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * This CTS test suite tests the core flow of the Custom Audiences API. It has the following goals:
 *
 * <ol>
 *   <li>Test updating of Custom Audience data in response to app installs and uninstalls.
 * </ol>
 *
 * <p>Because custom audience data is not exposed directly to callers by any API, correct behavior
 * must be verified indirectly by running an ad auction. By interacting with the Custom Audiences
 * API in ways designed to produce specific ad auction results, and then running the corresponding
 * auctions, we verify that the underlying API behavior is sound.
 *
 * <p>To this end, the test communicates with two test apps, which are installed and uninstalled,
 * and upon interaction each join a different custom audience. One app is the high bidder, whose
 * custom audience will always bid higher in an ad auction than the other, the low bidder.
 *
 * <p>Because running an ad auction requires a complex set of server endpoints (providing static
 * data such as bidding and decision logic), this test additionally sets up a mock server with basic
 * hardcoded data. Bidding and scoring logic simply follow the larger bid; all other fields return
 * placeholder data.
 *
 * <p>The general test flow is as follows:
 *
 * <ul>
 *   <li>Run a baseline ad auction with expected result (failure).
 *   <li>Install each test app and await system broadcasts confirming their installation.
 *   <li>Invoke each test app, providing them the mock server domain and address in the invocation
 *       intent.
 *   <li>Each test app creates and joins its respective custom audience in its <code>onCreate()
 *       </code> method, and sends a broadcast back to the test suite with data identifying the
 *       created custom audience.
 *   <li>After awaiting the app broadcasts, run another auction and assert the high bidder wins.
 *   <li>Uninstall the high bidder and await system broadcast confirming its installation.
 *   <li>Run a third auction and assert the low bidder wins, verifying that custom audience data for
 *       the high bidder was purged on uninstall.
 *   <li>Uninstall the low bidder and conclude the test.
 * </ul>
 *
 * <p>All tests below iterate on some variation of this process: run a baseline ad auction, modify
 * custom audiences via test apps, and run a new ad auction with different expected results.
 */
public final class CustomAudienceTest extends CtsAdServicesCustomAudienceTestCase {
    private static final String TAG = "CustomAudienceTest";
    private static final String TEST_PACKAGE_NAME =
            "com.android.adservices.tests.cts.customaudience";
    private static final String HIGH_BID_APK_PATH =
            "/data/local/tmp/cts/install/CtsSampleCustomAudienceAppHighBid.apk";
    private static final String HIGH_BID_PACKAGE_NAME = TEST_PACKAGE_NAME + ".sampleapphighbid";
    private static final String HIGH_BID_ACTIVITY_NAME = HIGH_BID_PACKAGE_NAME + ".MainActivity";
    private static final ComponentName HIGH_BID_COMPONENT_NAME =
            new ComponentName(HIGH_BID_PACKAGE_NAME, HIGH_BID_ACTIVITY_NAME);

    private static final String LOW_BID_APK_PATH =
            "/data/local/tmp/cts/install/CtsSampleCustomAudienceAppLowBid.apk";
    private static final String LOW_BID_PACKAGE_NAME = TEST_PACKAGE_NAME + ".sampleapplowbid";
    private static final String LOW_BID_ACTIVITY_NAME = LOW_BID_PACKAGE_NAME + ".MainActivity";
    private static final ComponentName LOW_BID_COMPONENT_NAME =
            new ComponentName(LOW_BID_PACKAGE_NAME, LOW_BID_ACTIVITY_NAME);

    private static final String BIDDING_LOGIC_JS =
            "function generateBid(ad, auction_signals, per_buyer_signals,"
                    + " trusted_bidding_signals, contextual_signals,"
                    + " custom_audience_signals) { \n"
                    + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                    + "}";
    private static final AdSelectionSignals TRUSTED_BIDDING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"example\": \"example\",\n"
                            + "\t\"valid\": \"Also valid\",\n"
                            + "\t\"list\": \"list\",\n"
                            + "\t\"of\": \"of\",\n"
                            + "\t\"keys\": \"trusted bidding signal Values\"\n"
                            + "}");
    private static final String DECISION_LOGIC_JS =
            "function scoreAd(ad, bid, auction_config, seller_signals,"
                    + " trusted_scoring_signals, contextual_signal, user_signal,"
                    + " custom_audience_signal) { \n"
                    + "  return {'status': 0, 'score': bid };\n"
                    + "}";
    private static final AdSelectionSignals TRUSTED_SCORING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"render_uri_1\": \"signals_for_1\",\n"
                            + "\t\"render_uri_2\": \"signals_for_2\"\n"
                            + "}");

    private static final String BUYER_BIDDING_LOGIC_URI_PATH = "/buyer/bidding/logic/";
    private static final String BUYER_TRUSTED_SIGNAL_URI_PATH = "/trusted/bidding/";
    private static final String SELLER_DECISION_LOGIC_URI_PATH = "/ssp/decision/logic/";
    public static final String SELLER_TRUSTED_SIGNAL_URI_PATH = "/kv/seller/signals/";

    private static final String PPAPI_ALLOWLIST =
            String.join(",", TEST_PACKAGE_NAME, HIGH_BID_PACKAGE_NAME, LOW_BID_PACKAGE_NAME);

    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    // Intent keys to pass data between test and buyer apps.
    private static final String SERVER_BASE_ADDRESS_INTENT_KEY =
            TEST_PACKAGE_NAME + ".serverBaseAddress";
    private static final String LOCALHOST_BUYER_DOMAIN_INTENT_KEY =
            TEST_PACKAGE_NAME + ".localhostBuyerDomain";
    private static final String AD_RENDER_URIS_INTENT_KEY = TEST_PACKAGE_NAME + ".adRenderUris";

    // Expected broadcasts confirming custom audiences are joined, in order.
    private static final String JOINED_CUSTOM_AUDIENCE_HIGH_BID_BROADCAST =
            TEST_PACKAGE_NAME + ".JOINED_CUSTOM_AUDIENCE_HIGH_BID";
    private static final String JOINED_CUSTOM_AUDIENCE_LOW_BID_BROADCAST =
            TEST_PACKAGE_NAME + ".JOINED_CUSTOM_AUDIENCE_LOW_BID";

    private static final int API_RESPONSE_TIMEOUT_SECONDS = 100;

    private static final Logger sLogger = LoggerFactory.getFledgeLogger();

    private final Random mCacheBusterRandom = new Random();

    // TODO(b/343272481): Replace CountDownLatch with SyncCallback subclasses
    private BroadcastReceiver mJoinedCustomAudienceBroadcastReceiver;
    private CountDownLatch mJoinedCustomAudienceBroadcastLatch;
    private Multiset<String> mExpectedJoinedCustomAudienceBroadcasts;

    private BroadcastReceiver mAppInstallBroadcastReceiver;
    private CountDownLatch mAppInstallBroadcastLatch;
    private Multiset<String> mExpectedAppInstallBroadcasts;

    private BroadcastReceiver mAppUninstallBroadcastReceiver;
    private CountDownLatch mAppUninstallBroadcastLatch;
    private Multiset<String> mExpectedAppUninstallBroadcasts;

    private List<Uri> mCustomAudienceHighBidAdRenderUris;
    private List<Uri> mCustomAudienceLowBidAdRenderUris;

    private MockWebServer mMockWebServer;
    private String mServerBaseAddress;
    private Uri mLocalhostBuyerDomain;
    private AdTechIdentifier mAdTechIdentifier;

    private int mUserId;

    // Prefix added to all requests to bust cache.
    private int mCacheBuster;

    @Rule(order = 11)
    public final MockWebServerRule mockWebServerRule =
            MockWebServerRule.forHttps(
                    ApplicationProvider.getApplicationContext(),
                    "adservices_untrusted_test_server.p12",
                    "adservices_test");

    @Before
    public void setup() throws Exception {
        // Kill AdServices process so that background jobs don't get skipped due to starting
        // with same params.
        AdservicesTestHelper.killAdservicesProcess(
                Objects.requireNonNull(
                        AdservicesTestHelper.getAdServicesPackageName(mContext, mTag)));

        mUserId = Process.myUserHandle().getIdentifier();
        mCacheBuster = mCacheBusterRandom.nextInt();

        flags.setPpapiAppAllowList(PPAPI_ALLOWLIST);

        setupMockWebServer();

        // Register broadcast receivers.
        registerJoinedCustomAudienceBroadcastReceiver();
        registerAppInstallBroadcastReceiver();
        registerAppUninstallBroadcastReceiver();
    }

    @After
    public void teardown() throws IOException {
        // Unregister broadcast receivers.
        mContext.unregisterReceiver(mJoinedCustomAudienceBroadcastReceiver);
        mContext.unregisterReceiver(mAppInstallBroadcastReceiver);
        mContext.unregisterReceiver(mAppUninstallBroadcastReceiver);

        mMockWebServer.shutdown();
    }

    @Ignore("Flaky test. Bug ID: 342636189")
    @Test
    public void testCustomAudience_dataPurgedForUninstalledApp() throws Exception {
        AdSelectionClient adSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(mContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
        AdSelectionConfig adSelectionConfig = getAdSelectionConfig();

        // Run ad selection, expect failure as no CAs have been joined.
        assertThrows(
                ExecutionException.class,
                () ->
                        adSelectionClient
                                .selectAds(adSelectionConfig)
                                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS));

        // Register expected app install broadcasts.
        mExpectedAppInstallBroadcasts =
                ConcurrentHashMultiset.create(
                        ImmutableList.of(HIGH_BID_PACKAGE_NAME, LOW_BID_PACKAGE_NAME));
        mAppInstallBroadcastLatch = new CountDownLatch(mExpectedAppInstallBroadcasts.size());

        // Install high and low bid apps.
        installApp(HIGH_BID_APK_PATH);
        installApp(LOW_BID_APK_PATH);

        // Wait to receive broadcasts confirming apps were installed.
        assertThat(mAppInstallBroadcastLatch.await(EXECUTION_WAITING_TIME, TimeUnit.MILLISECONDS))
                .isTrue();
        assertThat(mExpectedAppInstallBroadcasts).isEmpty();

        // Register expected joined custom audience broadcasts.
        mExpectedJoinedCustomAudienceBroadcasts =
                ConcurrentHashMultiset.create(
                        ImmutableList.of(
                                JOINED_CUSTOM_AUDIENCE_HIGH_BID_BROADCAST,
                                JOINED_CUSTOM_AUDIENCE_LOW_BID_BROADCAST));
        mJoinedCustomAudienceBroadcastLatch =
                new CountDownLatch(mExpectedJoinedCustomAudienceBroadcasts.size());

        // Invoke high bid app, which joins its CA on create.
        Intent highBidAppIntent =
                new Intent()
                        .setComponent(HIGH_BID_COMPONENT_NAME)
                        .addFlags(FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(SERVER_BASE_ADDRESS_INTENT_KEY, mServerBaseAddress)
                        .putExtra(LOCALHOST_BUYER_DOMAIN_INTENT_KEY, mLocalhostBuyerDomain);
        mContext.startActivity(highBidAppIntent);

        // Invoke low bid app, which joins its CA on create.
        Intent lowBidAppIntent =
                new Intent()
                        .setComponent(LOW_BID_COMPONENT_NAME)
                        .addFlags(FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(SERVER_BASE_ADDRESS_INTENT_KEY, mServerBaseAddress)
                        .putExtra(LOCALHOST_BUYER_DOMAIN_INTENT_KEY, mLocalhostBuyerDomain);
        mContext.startActivity(lowBidAppIntent);

        // Wait to receive broadcasts confirming CAs were joined.
        assertThat(
                        mJoinedCustomAudienceBroadcastLatch.await(
                                EXECUTION_WAITING_TIME, TimeUnit.MILLISECONDS))
                .isTrue();
        assertThat(mExpectedJoinedCustomAudienceBroadcasts).isEmpty();

        // Run ad selection again, expect high bidder to win auction.
        AdSelectionOutcome adSelectionOutcome =
                adSelectionClient
                        .selectAds(adSelectionConfig)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(mCustomAudienceHighBidAdRenderUris).contains(adSelectionOutcome.getRenderUri());

        // Register expected app uninstall broadcasts.
        mExpectedAppUninstallBroadcasts =
                HashMultiset.create(ImmutableList.of(HIGH_BID_PACKAGE_NAME));
        mAppUninstallBroadcastLatch = new CountDownLatch(mExpectedAppUninstallBroadcasts.size());

        // Uninstall high bid app.
        uninstallApp(HIGH_BID_PACKAGE_NAME);

        // Wait to receive broadcast confirming app was uninstalled.
        assertThat(mAppUninstallBroadcastLatch.await(EXECUTION_WAITING_TIME, TimeUnit.MILLISECONDS))
                .isTrue();
        assertThat(mExpectedAppUninstallBroadcasts).isEmpty();

        // Run ad selection again, expect low bidder to win auction,
        // showing uninstalled app's CA data was purged.
        eventually(
                () ->
                        assertThat(mCustomAudienceLowBidAdRenderUris)
                                .contains(
                                        adSelectionClient
                                                .selectAds(adSelectionConfig)
                                                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                                                .getRenderUri()));

        // Finally, uninstall low bid app.
        // Register expected app uninstall broadcasts.
        mExpectedAppUninstallBroadcasts =
                HashMultiset.create(ImmutableList.of(LOW_BID_PACKAGE_NAME));
        mAppUninstallBroadcastLatch = new CountDownLatch(mExpectedAppUninstallBroadcasts.size());

        // Uninstall low bid app.
        uninstallApp(LOW_BID_PACKAGE_NAME);

        // Wait to receive broadcast confirming app was uninstalled.
        assertThat(mAppUninstallBroadcastLatch.await(EXECUTION_WAITING_TIME, TimeUnit.MILLISECONDS))
                .isTrue();
        assertThat(mExpectedAppUninstallBroadcasts).isEmpty();
    }

    @Ignore("Flaky test. Bug ID: 342636189")
    @Test
    public void testCustomAudience_doesNotPersistAfterAppUninstallAndReinstall() throws Exception {
        AdSelectionClient adSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(mContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
        AdSelectionConfig adSelectionConfig = getAdSelectionConfig();

        // Register expected app install broadcasts.
        mExpectedAppInstallBroadcasts =
                ConcurrentHashMultiset.create(ImmutableList.of(HIGH_BID_PACKAGE_NAME));
        mAppInstallBroadcastLatch = new CountDownLatch(mExpectedAppInstallBroadcasts.size());

        // Install app.
        installApp(HIGH_BID_APK_PATH);

        // Wait to receive broadcast confirming app was installed.
        assertThat(mAppInstallBroadcastLatch.await(EXECUTION_WAITING_TIME, TimeUnit.MILLISECONDS))
                .isTrue();
        assertThat(mExpectedAppInstallBroadcasts).isEmpty();

        // Register expected joined custom audience broadcasts.
        mExpectedJoinedCustomAudienceBroadcasts =
                ConcurrentHashMultiset.create(
                        ImmutableList.of(JOINED_CUSTOM_AUDIENCE_HIGH_BID_BROADCAST));
        mJoinedCustomAudienceBroadcastLatch =
                new CountDownLatch(mExpectedJoinedCustomAudienceBroadcasts.size());

        // Invoke app, which joins its CA on create.
        Intent highBidAppIntent =
                new Intent()
                        .setComponent(HIGH_BID_COMPONENT_NAME)
                        .addFlags(FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(SERVER_BASE_ADDRESS_INTENT_KEY, mServerBaseAddress)
                        .putExtra(LOCALHOST_BUYER_DOMAIN_INTENT_KEY, mLocalhostBuyerDomain);
        mContext.startActivity(highBidAppIntent);

        // Wait to receive broadcast confirming CA was joined.
        assertThat(
                        mJoinedCustomAudienceBroadcastLatch.await(
                                EXECUTION_WAITING_TIME, TimeUnit.MILLISECONDS))
                .isTrue();
        assertThat(mExpectedJoinedCustomAudienceBroadcasts).isEmpty();

        // Run ad selection, expect only bidder to win auction.
        AdSelectionOutcome adSelectionOutcome =
                adSelectionClient
                        .selectAds(adSelectionConfig)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(mCustomAudienceHighBidAdRenderUris).contains(adSelectionOutcome.getRenderUri());

        // Save initial ad render URIs locally.
        List<Uri> initialAdRenderUris = mCustomAudienceHighBidAdRenderUris;

        // Register expected app uninstall broadcasts.
        mExpectedAppUninstallBroadcasts =
                HashMultiset.create(ImmutableList.of(HIGH_BID_PACKAGE_NAME));
        mAppUninstallBroadcastLatch = new CountDownLatch(mExpectedAppUninstallBroadcasts.size());

        // Uninstall app.
        uninstallApp(HIGH_BID_PACKAGE_NAME);

        // Wait to receive broadcast confirming app was uninstalled.
        assertThat(mAppUninstallBroadcastLatch.await(EXECUTION_WAITING_TIME, TimeUnit.MILLISECONDS))
                .isTrue();
        assertThat(mExpectedAppUninstallBroadcasts).isEmpty();

        // Run ad selection again, expect failure as CA data is purged on uninstall.
        // Retry call to allow time for uninstall to propagate to service.
        eventually(
                () ->
                        assertThrows(
                                ExecutionException.class,
                                () ->
                                        adSelectionClient
                                                .selectAds(adSelectionConfig)
                                                .get(
                                                        API_RESPONSE_TIMEOUT_SECONDS,
                                                        TimeUnit.SECONDS)));

        // Reset expected app install broadcasts.
        mExpectedAppInstallBroadcasts.add(HIGH_BID_PACKAGE_NAME);
        mAppInstallBroadcastLatch = new CountDownLatch(mExpectedAppInstallBroadcasts.size());

        // Reinstall app.
        installApp(HIGH_BID_APK_PATH);

        // Wait to receive broadcast confirming app was installed.
        assertThat(mAppInstallBroadcastLatch.await(EXECUTION_WAITING_TIME, TimeUnit.MILLISECONDS))
                .isTrue();
        assertThat(mExpectedAppInstallBroadcasts).isEmpty();

        // Reset expected joined custom audience broadcasts.
        mExpectedJoinedCustomAudienceBroadcasts.add(JOINED_CUSTOM_AUDIENCE_HIGH_BID_BROADCAST);
        mJoinedCustomAudienceBroadcastLatch =
                new CountDownLatch(mExpectedJoinedCustomAudienceBroadcasts.size());

        // Invoke app again.
        mContext.startActivity(highBidAppIntent);

        // Wait to receive broadcast confirming CA was joined.
        assertThat(
                        mJoinedCustomAudienceBroadcastLatch.await(
                                EXECUTION_WAITING_TIME, TimeUnit.MILLISECONDS))
                .isTrue();
        assertThat(mExpectedJoinedCustomAudienceBroadcasts).isEmpty();

        // Run ad selection again, expect same bidder to win auction.
        adSelectionOutcome =
                adSelectionClient
                        .selectAds(adSelectionConfig)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(mCustomAudienceHighBidAdRenderUris).contains(adSelectionOutcome.getRenderUri());

        // Check that this win came from a distinct custom audience with distinct ad renderURIs,
        // and not just the same custom audience restored upon reinstall.
        assertThat(initialAdRenderUris).doesNotContain(adSelectionOutcome.getRenderUri());

        // Finally, uninstall app.
        // Register expected app uninstall broadcasts.
        mExpectedAppUninstallBroadcasts =
                HashMultiset.create(ImmutableList.of(HIGH_BID_PACKAGE_NAME));
        mAppUninstallBroadcastLatch = new CountDownLatch(mExpectedAppUninstallBroadcasts.size());

        uninstallApp(HIGH_BID_PACKAGE_NAME);

        // Wait to receive broadcast confirming app was uninstalled.
        assertThat(mAppUninstallBroadcastLatch.await(EXECUTION_WAITING_TIME, TimeUnit.MILLISECONDS))
                .isTrue();
        assertThat(mExpectedAppUninstallBroadcasts).isEmpty();
    }

    // Broadcast Receiver to receive joinedCustomAudience broadcast from test apps.
    // Broadcast identifies which custom audience was joined and provides ad render URIs
    // to check against auction results.
    private void registerJoinedCustomAudienceBroadcastReceiver() {
        IntentFilter joinedCustomAudienceIntentFilter = new IntentFilter();
        joinedCustomAudienceIntentFilter.addAction(JOINED_CUSTOM_AUDIENCE_HIGH_BID_BROADCAST);
        joinedCustomAudienceIntentFilter.addAction(JOINED_CUSTOM_AUDIENCE_LOW_BID_BROADCAST);

        mJoinedCustomAudienceBroadcastReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String action = Objects.requireNonNull(intent.getAction());
                        List<Uri> adRenderUris =
                                Objects.requireNonNull(
                                        intent.getParcelableArrayListExtra(
                                                AD_RENDER_URIS_INTENT_KEY, Uri.class));

                        sLogger.v(
                                "%s - Received joined custom audience broadcast of type: %s "
                                        + "with render URIs: %s",
                                TAG, action, Arrays.toString(adRenderUris.toArray()));

                        // Verify the joined custom audience and save ad render URIs
                        // for broadcast type to check against auction results.
                        if (action.equals(JOINED_CUSTOM_AUDIENCE_HIGH_BID_BROADCAST)) {
                            mCustomAudienceHighBidAdRenderUris = adRenderUris;
                        } else if (action.equals(JOINED_CUSTOM_AUDIENCE_LOW_BID_BROADCAST)) {
                            mCustomAudienceLowBidAdRenderUris = adRenderUris;
                        } else {
                            sLogger.v(
                                    "%s - Unexpected joined custom audience broadcast of type: %s",
                                    TAG, action);
                            return;
                        }

                        mExpectedJoinedCustomAudienceBroadcasts.remove(action);
                        mJoinedCustomAudienceBroadcastLatch.countDown();
                    }
                };

        mContext.registerReceiver(
                mJoinedCustomAudienceBroadcastReceiver,
                joinedCustomAudienceIntentFilter,
                Context.RECEIVER_EXPORTED);
    }

    // Broadcast Receiver to receive app install broadcasts from device.
    // Broadcast identifies which app was installed.
    private void registerAppInstallBroadcastReceiver() {
        IntentFilter appInstallIntentFilter = new IntentFilter();
        appInstallIntentFilter.addDataScheme("package");
        appInstallIntentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);

        mAppInstallBroadcastReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String packageName =
                                Objects.requireNonNull(intent.getData()).getSchemeSpecificPart();

                        sLogger.v(
                                "%s - Received app install broadcast for app: %s",
                                TAG, packageName);

                        // Verify the installed app.
                        if (!mExpectedAppInstallBroadcasts.contains(packageName)) {
                            sLogger.v(
                                    "%s - Unexpected app install broadcast for app: %s, returning",
                                    TAG, packageName);
                            return;
                        }

                        mExpectedAppInstallBroadcasts.remove(packageName);
                        mAppInstallBroadcastLatch.countDown();
                    }
                };

        mContext.registerReceiver(
                mAppInstallBroadcastReceiver,
                appInstallIntentFilter,
                Context.RECEIVER_EXPORTED /*UNAUDITED*/);
    }

    // Broadcast Receiver to receive app uninstall broadcasts from device.
    // Broadcast identifies which app was uninstalled.
    private void registerAppUninstallBroadcastReceiver() {
        IntentFilter appUninstallIntentFilter = new IntentFilter();
        appUninstallIntentFilter.addDataScheme("package");
        appUninstallIntentFilter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);

        mAppUninstallBroadcastReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String packageName =
                                Objects.requireNonNull(intent.getData()).getSchemeSpecificPart();

                        sLogger.v(
                                "%s - Received app uninstall broadcast for app: %s",
                                TAG, packageName);

                        // Verify the installed app.
                        if (!mExpectedAppUninstallBroadcasts.contains(packageName)) {
                            sLogger.v(
                                    "%s - Unexpected app uninstall broadcast for app: %s, "
                                            + "returning",
                                    TAG, packageName);
                            return;
                        }

                        mExpectedAppUninstallBroadcasts.remove(packageName);
                        mAppUninstallBroadcastLatch.countDown();
                    }
                };

        mContext.registerReceiver(
                mAppUninstallBroadcastReceiver,
                appUninstallIntentFilter,
                Context.RECEIVER_EXPORTED /*UNAUDITED*/);
    }

    private AdSelectionConfig getAdSelectionConfig() {
        AdTechIdentifier buyer =
                AdTechIdentifier.fromString(
                        Objects.requireNonNull(mLocalhostBuyerDomain.getHost()));
        return AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                .setCustomAudienceBuyers(ImmutableList.of(buyer))
                .setSeller(mAdTechIdentifier)
                .setDecisionLogicUri(Uri.parse(mServerBaseAddress + SELLER_DECISION_LOGIC_URI_PATH))
                .setTrustedScoringSignalsUri(
                        Uri.parse(mServerBaseAddress + SELLER_TRUSTED_SIGNAL_URI_PATH))
                .setPerBuyerSignals(
                        ImmutableMap.of(
                                buyer, AdSelectionSignals.fromString("{\"buyer_signals\":0}")))
                .build();
    }

    private void setupMockWebServer() throws Exception {
        if (mMockWebServer != null) {
            mMockWebServer.shutdown();
        }
        mMockWebServer = getMockWebServer();
        mServerBaseAddress = getServerBaseAddress();
        mLocalhostBuyerDomain = Uri.parse(mServerBaseAddress);
        mAdTechIdentifier = AdTechIdentifier.fromString(mMockWebServer.getHostName());
    }

    private MockWebServer getMockWebServer() throws Exception {
        return mockWebServerRule.startMockWebServer(
                request -> {
                    // Remove cache buster prefix.
                    String requestPath = request.getPath().replace(getCacheBusterPrefix(), "");
                    if (requestPath.startsWith(BUYER_BIDDING_LOGIC_URI_PATH)) {
                        return new MockResponse().setBody(BIDDING_LOGIC_JS);
                    } else if (requestPath.startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                        return new MockResponse().setBody(TRUSTED_BIDDING_SIGNALS.toString());
                    } else if (requestPath.startsWith(SELLER_DECISION_LOGIC_URI_PATH)) {
                        return new MockResponse().setBody(DECISION_LOGIC_JS);
                    } else if (requestPath.startsWith(SELLER_TRUSTED_SIGNAL_URI_PATH)) {
                        return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                    } else {
                        return new MockResponse().setResponseCode(404);
                    }
                });
    }

    private String getCacheBusterPrefix() {
        return String.format("/%s", mCacheBuster);
    }

    private String getServerBaseAddress() {
        return String.format(
                "https://%s:%s%s",
                mMockWebServer.getHostName(), mMockWebServer.getPort(), getCacheBusterPrefix());
    }

    // Install app and verify the installation.
    private void installApp(String apkPath) {
        String installMessage = runShellCommand("pm install --user %d -r %s", mUserId, apkPath);
        assertThat(installMessage).contains("Success");
    }

    // Uninstall app.
    private void uninstallApp(String packageName) {
        runShellCommand("pm uninstall --user %d %s", mUserId, packageName);
    }
}
