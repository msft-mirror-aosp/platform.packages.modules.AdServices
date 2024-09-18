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

package android.adservices.cts;

import static android.adservices.common.AdServicesModuleState.MODULE_STATE_ENABLED;
import static android.adservices.common.AdServicesModuleUserChoice.USER_CHOICE_OPTED_OUT;
import static android.adservices.common.Module.MEASUREMENT;
import static android.adservices.common.Module.TOPICS;
import static android.adservices.common.NotificationType.NOTIFICATION_ONGOING;

import static com.android.adservices.shared.testing.AndroidSdk.RVC;

import android.adservices.adid.AdId;
import android.adservices.common.AdServicesCommonManager;
import android.adservices.common.AdServicesCommonStatesResponse;
import android.adservices.common.AdServicesModuleState;
import android.adservices.common.AdServicesModuleUserChoice;
import android.adservices.common.AdServicesStates;
import android.adservices.common.UpdateAdIdRequest;
import android.adservices.exceptions.AdServicesException;

import com.android.adservices.common.AdServicesOutcomeReceiverForTests;
import com.android.adservices.shared.testing.annotations.RequiresSdkRange;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RequiresSdkRange(atMost = RVC)
public final class AdServicesCommonManagerRvcTest extends CtsAdServicesDeviceTestCase {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private AdServicesCommonManager mCommonManager;

    @Before
    public void setup() {
        // Initialize the manager before tests instead of in class member to allow overriding the
        // binder timeout.
        mCommonManager = AdServicesCommonManager.get(mContext);
    }

    @Test
    public void testIsAdServicesEnabled_onR_invokesCallbackOnError() throws Exception {
        AdServicesOutcomeReceiverForTests<Boolean> receiver =
                new AdServicesOutcomeReceiverForTests<>();

        mCommonManager.isAdServicesEnabled(CALLBACK_EXECUTOR, receiver);

        receiver.assertFailure(AdServicesException.class);
    }

    @Test
    public void testSetAdServicesModuleOverrides_onR_invokesCallbackOnError() throws Exception {
        AdServicesOutcomeReceiverForTests<Void> receiver =
                new AdServicesOutcomeReceiverForTests<>();
        AdServicesModuleState moduleState =
                new AdServicesModuleState.Builder()
                        .setModule(MEASUREMENT)
                        .setModuleState(MODULE_STATE_ENABLED)
                        .build();
        List<AdServicesModuleState> adServicesModuleStateList = Arrays.asList(moduleState);

        mCommonManager.requestAdServicesModuleOverrides(
                adServicesModuleStateList, NOTIFICATION_ONGOING, CALLBACK_EXECUTOR, receiver);

        receiver.assertFailure(AdServicesException.class);
    }

    @Test
    public void testSetAdServicesModuleUserChoices_onR_invokesCallbackOnError() throws Exception {
        AdServicesOutcomeReceiverForTests<Void> receiver =
                new AdServicesOutcomeReceiverForTests<>();
        AdServicesModuleUserChoice adServicesModuleUserChoice =
                new AdServicesModuleUserChoice.Builder()
                        .setModule(TOPICS)
                        .setUserChoice(USER_CHOICE_OPTED_OUT)
                        .build();
        List<AdServicesModuleUserChoice> adServicesModuleUserChoiceList =
                Arrays.asList(adServicesModuleUserChoice);

        mCommonManager.requestAdServicesModuleUserChoices(
                adServicesModuleUserChoiceList, CALLBACK_EXECUTOR, receiver);

        receiver.assertFailure(AdServicesException.class);
    }

    @Test
    public void testEnableAdServices_onR_invokesCallbackOnError() throws Exception {
        AdServicesOutcomeReceiverForTests<Boolean> receiver =
                new AdServicesOutcomeReceiverForTests<>();
        AdServicesStates states = new AdServicesStates.Builder().build();

        mCommonManager.enableAdServices(states, CALLBACK_EXECUTOR, receiver);

        receiver.assertFailure(AdServicesException.class);
    }

    @Test
    public void testUpdateAdId_onR_invokesCallbackOnError() throws Exception {
        AdServicesOutcomeReceiverForTests<Boolean> receiver =
                new AdServicesOutcomeReceiverForTests<>();
        UpdateAdIdRequest request = new UpdateAdIdRequest.Builder(AdId.ZERO_OUT).build();

        mCommonManager.updateAdId(request, CALLBACK_EXECUTOR, receiver);

        receiver.assertFailure(AdServicesException.class);
    }

    @Test
    public void testGetAdServicesCommonStates_onR_invokesCallbackOnError() throws Exception {
        AdServicesOutcomeReceiverForTests<AdServicesCommonStatesResponse> receiver =
                new AdServicesOutcomeReceiverForTests<>();

        mCommonManager.getAdservicesCommonStates(CALLBACK_EXECUTOR, receiver);

        receiver.assertFailure(AdServicesException.class);
    }
}
