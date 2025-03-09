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

package android.adservices.rootcts;

import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.DebugFlagsConstants.KEY_PERIODIC_ENCODING_JOB_COMPLETE_BROADCAST_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_PROTECTED_APP_SIGNALS_ENCODER_LOGIC_REGISTERED_BROADCAST_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_AD_SERVICES_RETRY_STRATEGY_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_PAS_APP_ALLOW_LIST;
import static com.android.adservices.service.FlagsConstants.KEY_PROTECTED_SIGNALS_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_PROTECTED_SIGNALS_PERIODIC_ENCODING_ENABLED;
import static com.android.adservices.spe.AdServicesJobInfo.PERIODIC_SIGNALS_ENCODING_JOB;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.clients.signals.ProtectedSignalsClient;
import android.adservices.signals.UpdateSignalsRequest;
import android.adservices.utils.ScenarioDispatcher;
import android.adservices.utils.ScenarioDispatcherFactory;
import android.content.Intent;
import android.net.Uri;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.shared.testing.BroadcastReceiverSyncCallback;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.shared.testing.annotations.SetStringFlag;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SetFlagEnabled(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK)
@SetFlagEnabled(KEY_PROTECTED_SIGNALS_ENABLED)
@SetFlagEnabled(KEY_PROTECTED_SIGNALS_PERIODIC_ENCODING_ENABLED)
@SetStringFlag(name = KEY_PAS_APP_ALLOW_LIST, value = "*")
@SetFlagEnabled(KEY_AD_SERVICES_RETRY_STRATEGY_ENABLED) // Enabled retry for java script engine
@EnableDebugFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE)
@EnableDebugFlag(KEY_PERIODIC_ENCODING_JOB_COMPLETE_BROADCAST_ENABLED)
@EnableDebugFlag(KEY_PROTECTED_APP_SIGNALS_ENCODER_LOGIC_REGISTERED_BROADCAST_ENABLED)
@RequiresSdkLevelAtLeastT
public class PeriodicEncodingJobTest extends FledgeRootScenarioTest {
    private static final String POSTFIX = "/signals";
    private static final String ACTION_REGISTER_ENCODER_LOGIC_COMPLETE =
            "android.adservices.debug.REGISTER_ENCODER_LOGIC_COMPLETE";
    private static final String ACTION_PERIODIC_ENCODING_JOB_COMPLETE =
            "ACTION_PERIODIC_ENCODING_JOB_COMPLETE";

    private BackgroundJobHelper mBackgroundJobHelper;
    private ProtectedSignalsClient mProtectedSignalsClient;
    private String mServerBaseAddress;

    @Before
    public void setup() throws Exception {
        super.setUp();
        mBackgroundJobHelper = new BackgroundJobHelper(sContext);

        AdservicesTestHelper.killAdservicesProcess(mContext);
        ExecutorService executor = Executors.newCachedThreadPool();
        mProtectedSignalsClient =
                new ProtectedSignalsClient.Builder()
                        .setContext(mContext)
                        .setExecutor(executor)
                        .build();
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFile(
                                "scenarios/pas-update-signals.json"));

        mServerBaseAddress = dispatcher.getBaseAddressWithPrefix().toString();
    }

    @Test
    public void test_updateSignalsAPI_schedulesTheBackgroundJob() throws Exception {
        callUpdateSignalsAPI();
        BroadcastReceiverSyncCallback broadcastReceiverSyncCallback =
                new BroadcastReceiverSyncCallback(sContext, ACTION_REGISTER_ENCODER_LOGIC_COMPLETE);
        broadcastReceiverSyncCallback.assertResultReceived();

        assertThat(mBackgroundJobHelper.isJobScheduled(PERIODIC_SIGNALS_ENCODING_JOB.getJobId()))
                .isTrue();
    }

    @Test
    public void test_protectedSignalsPeriodicEncodingDisabled_cancelsTheBackgroundJob()
            throws Exception {
        callUpdateSignalsAPI();
        BroadcastReceiverSyncCallback broadcastReceiverSyncCallback =
                new BroadcastReceiverSyncCallback(sContext, ACTION_REGISTER_ENCODER_LOGIC_COMPLETE);
        broadcastReceiverSyncCallback.assertResultReceived();

        assertThat(mBackgroundJobHelper.isJobScheduled(PERIODIC_SIGNALS_ENCODING_JOB.getJobId()))
                .isTrue();

        flags.setFlag(KEY_PROTECTED_SIGNALS_PERIODIC_ENCODING_ENABLED, false);

        mBackgroundJobHelper.runJob(PERIODIC_SIGNALS_ENCODING_JOB.getJobId());

        assertThat(mBackgroundJobHelper.isJobScheduled(PERIODIC_SIGNALS_ENCODING_JOB.getJobId()))
                .isFalse();
    }

    @Test
    public void test_forceRunningTheJob_runsTheJob() throws Exception {
        callUpdateSignalsAPI();
        BroadcastReceiverSyncCallback broadcastReceiverSyncCallback =
                new BroadcastReceiverSyncCallback(sContext, ACTION_REGISTER_ENCODER_LOGIC_COMPLETE);
        broadcastReceiverSyncCallback.assertResultReceived();

        assertThat(mBackgroundJobHelper.isJobScheduled(PERIODIC_SIGNALS_ENCODING_JOB.getJobId()))
                .isTrue();

        Intent intentReceived =
                mBackgroundJobHelper.runJobWithBroadcastIntent(
                        PERIODIC_SIGNALS_ENCODING_JOB.getJobId(),
                        ACTION_PERIODIC_ENCODING_JOB_COMPLETE);
        boolean status = intentReceived.getBooleanExtra("status", false);

        assertThat(status).isTrue();
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private void callUpdateSignalsAPI() {
        Uri firstUri = Uri.parse(mServerBaseAddress + POSTFIX);
        UpdateSignalsRequest updateSignalsRequest =
                new UpdateSignalsRequest.Builder(firstUri).build();
        mProtectedSignalsClient.updateSignals(updateSignalsRequest);
    }
}
