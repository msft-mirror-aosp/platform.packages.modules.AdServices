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

package android.adservices.cts;

import static com.android.adservices.service.FlagsConstants.KEY_ADSERVICES_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_IS_GET_ADSERVICES_COMMON_STATES_API_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.adid.AdId;
import android.adservices.common.AdServicesCommonManager;
import android.adservices.common.AdServicesCommonStatesResponse;
import android.adservices.common.UpdateAdIdRequest;

import com.android.adservices.common.AdServicesOutcomeReceiverForTests;
import com.android.adservices.common.annotations.SetPpapiAppAllowList;
import com.android.adservices.shared.testing.OutcomeReceiverForTests;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;
import com.android.adservices.shared.testing.annotations.SetFlagFalse;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@SetPpapiAppAllowList
public final class AdServicesCommonManagerTest extends CtsAdServicesDeviceTestCase {

    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private AdServicesCommonManager mCommonManager;

    @Before
    public void setup() {
        // Initialize the manager before tests instead of in class member to allow overriding the
        // binder timeout.
        mCommonManager = AdServicesCommonManager.get(sContext);
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "uses OutcomeReceiver, which is only available on S+.")
    @SetFlagFalse(KEY_ADSERVICES_ENABLED)
    public void testStatusManagerNotAuthorizedOnSPlus() throws Exception {
        // At beginning, Sdk1 receives a false status.
        OutcomeReceiverForTests<Boolean> receiver = new OutcomeReceiverForTests<>();

        mCommonManager.isAdServicesEnabled(CALLBACK_EXECUTOR, receiver);

        receiver.assertFailure(SecurityException.class);
    }

    @Test
    @RequiresSdkLevelAtLeastS
    public void testSetStatusEnabledNotExecutedOnSPlus() throws Exception {
        mCommonManager.setAdServicesEnabled(true, true);
        OutcomeReceiverForTests<Boolean> receiver = new OutcomeReceiverForTests<>();

        mCommonManager.isAdServicesEnabled(CALLBACK_EXECUTOR, receiver);

        receiver.assertFailure(SecurityException.class);
    }

    @Test
    @SetFlagFalse(KEY_ADSERVICES_ENABLED)
    public void testStatusManagerNotAuthorizedCompat() throws Exception {
        // At beginning, Sdk1 receives a false status.
        AdServicesOutcomeReceiverForTests<Boolean> receiver =
                new AdServicesOutcomeReceiverForTests<>();

        mCommonManager.isAdServicesEnabled(CALLBACK_EXECUTOR, receiver);

        receiver.assertFailure(SecurityException.class);
    }

    @Test
    public void testSetStatusEnabledNotExecutedCompat() throws Exception {
        mCommonManager.setAdServicesEnabled(true, true);
        AdServicesOutcomeReceiverForTests<Boolean> receiver =
                new AdServicesOutcomeReceiverForTests<>();

        mCommonManager.isAdServicesEnabled(CALLBACK_EXECUTOR, receiver);

        receiver.assertFailure(SecurityException.class);
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "uses OutcomeReceiver, which is only available on T")
    public void testUpdateAdIdCache_notAuthorized_sPlus() throws Exception {
        UpdateAdIdRequest request = new UpdateAdIdRequest.Builder(AdId.ZERO_OUT).build();
        OutcomeReceiverForTests<Boolean> receiver = new OutcomeReceiverForTests<>();

        mCommonManager.updateAdId(request, CALLBACK_EXECUTOR, receiver);

        receiver.assertFailure(SecurityException.class);
    }

    @Test
    public void testUpdateAdIdCache_notAuthorized_rPlus() throws Exception {
        AdServicesOutcomeReceiverForTests<Boolean> receiver =
                new AdServicesOutcomeReceiverForTests<>();

        mCommonManager.updateAdId(
                new UpdateAdIdRequest.Builder(AdId.ZERO_OUT).build(), CALLBACK_EXECUTOR, receiver);

        receiver.assertFailure(SecurityException.class);
    }

    @Test
    // TODO(b/328794632): Need a real CTS test to successfully call the system API.
    public void testUpdateAdIdCache_coverage() {
        UpdateAdIdRequest request =
                new UpdateAdIdRequest.Builder(AdId.ZERO_OUT)
                        .setLimitAdTrackingEnabled(true)
                        .build();

        mCommonManager.updateAdId(
                request, CALLBACK_EXECUTOR, new AdServicesOutcomeReceiverForTests<>());

        assertThat(request.getAdId()).isEqualTo(AdId.ZERO_OUT);
        assertThat(request.isLimitAdTrackingEnabled()).isTrue();
        assertThat(request.describeContents()).isEqualTo(0);
    }

    @Test
    @SetFlagFalse(KEY_IS_GET_ADSERVICES_COMMON_STATES_API_ENABLED)
    public void testGetAdservicesCommonStates_notEnabled_rPlus() throws Exception {
        AdServicesOutcomeReceiverForTests<AdServicesCommonStatesResponse> receiver =
                new AdServicesOutcomeReceiverForTests<>();

        mCommonManager.getAdservicesCommonStates(CALLBACK_EXECUTOR, receiver);

        receiver.assertFailure(SecurityException.class);
    }
}
