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

package android.adservices.debuggablects;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.adservices.clients.measurement.MeasurementClient;
import android.adservices.measurement.DeletionRequest;
import android.adservices.measurement.MeasurementManager;
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.content.Context;
import android.net.Uri;
import android.os.OutcomeReceiver;
import android.provider.DeviceConfig;
import android.test.suitebuilder.annotation.SmallTest;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.android.adservices.service.Flags;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.measurement.MeasurementServiceImpl;
import com.android.modules.utils.testing.TestableDeviceConfig;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class MeasurementManagerCtsTest {
    // This rule is used for configuring P/H flags
    @Rule
    public final TestableDeviceConfig.TestableDeviceConfigRule mDeviceConfigRule =
            new TestableDeviceConfig.TestableDeviceConfigRule();

    private MeasurementClient mMeasurementClient;
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private static final String KEY_SDK_REQUEST_PERMITS_PER_SECOND =
            "sdk_request_permits_per_second";
    private static final String INVALID_SERVER_ADDRESS = "http://example.com";
    private static final Uri SOURCE_ORIGIN = Uri.parse("http://source-origin.com");
    private static final Uri DESTINATION = Uri.parse("http://trigger-origin.com");
    private static final Uri OS_DESTINATION = Uri.parse("android-app://com.os.destination");
    private static final Uri WEB_DESTINATION = Uri.parse("http://web-destination.com");
    private static final Uri ORIGIN_URI = Uri.parse("https://sample.example1.com");
    private static final Uri DOMAIN_URI = Uri.parse("https://example2.com");
    private static final String ALLOW_LIST_ALL = "*";
    private final ExecutorService mExecutorService = Executors.newCachedThreadPool();

    protected static final Context sContext = ApplicationProvider.getApplicationContext();

    @Before
    public void setup() {
        // To avoid throttling on this test, setting max value of request per second
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_SDK_REQUEST_PERMITS_PER_SECOND,
                Integer.toString(Integer.MAX_VALUE),
                /* makeDefault */ false);

        // Mocking context passed to measurement client so updated DeviceConfigs can be read
        final Context mockContext = Mockito.mock(Context.class);
        final Flags mockFlags = Mockito.mock(Flags.class);
        final MeasurementManager mm = Mockito.spy(new MeasurementManager(sContext));
        Mockito.doReturn(mm).when(mockContext).getSystemService(MeasurementManager.class);
        Mockito.doReturn(
                        new MeasurementServiceImpl(
                                sContext, ConsentManager.getInstance(sContext), mockFlags))
                .when(mm)
                .getService();
        Mockito.doReturn(ALLOW_LIST_ALL)
                .when(mockFlags)
                .getWebContextRegistrationClientAppAllowList();

        mMeasurementClient =
                new MeasurementClient.Builder()
                        .setContext(mockContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
    }

    @Test
    public void testRegisterSource_withCallbackButNoServerSetup_NoErrors() throws Exception {
        ListenableFuture<Void> result =
                mMeasurementClient.registerSource(
                        Uri.parse(INVALID_SERVER_ADDRESS), /* inputEvent = */ null);
        assertThat(result.get()).isNull();
    }

    @Test
    public void testRegisterTrigger_withCallbackButNoServerSetup_NoErrors() throws Exception {
        ListenableFuture<Void> result =
                mMeasurementClient.registerTrigger(Uri.parse(INVALID_SERVER_ADDRESS));
        assertThat(result.get()).isNull();
    }

    @Test
    public void registerWebSource_withCallback_NoErrors() throws Exception {
        WebSourceParams webSourceParams =
                new WebSourceParams.Builder(Uri.parse(INVALID_SERVER_ADDRESS))
                        .setDebugKeyAllowed(false)
                        .build();

        WebSourceRegistrationRequest webSourceRegistrationRequest =
                new WebSourceRegistrationRequest.Builder(
                                Collections.singletonList(webSourceParams), SOURCE_ORIGIN)
                        .setInputEvent(null)
                        .setAppDestination(OS_DESTINATION)
                        .setWebDestination(WEB_DESTINATION)
                        .setVerifiedDestination(null)
                        .build();

        ListenableFuture<Void> result =
                mMeasurementClient.registerWebSource(webSourceRegistrationRequest);
        assertThat(result.get()).isNull();
    }

    @Test
    public void registerWebTrigger_withCallback_NoErrors() throws Exception {
        WebTriggerParams webTriggerParams =
                new WebTriggerParams.Builder(Uri.parse(INVALID_SERVER_ADDRESS)).build();
        WebTriggerRegistrationRequest webTriggerRegistrationRequest =
                new WebTriggerRegistrationRequest.Builder(
                                Collections.singletonList(webTriggerParams), DESTINATION)
                        .build();

        ListenableFuture<Void> result =
                mMeasurementClient.registerWebTrigger(webTriggerRegistrationRequest);
        assertThat(result.get()).isNull();
    }

    @Test
    public void testDeleteRegistrations_withRequest_withNoOrigin_withNoRange_withCallback_NoErrors()
            throws Exception {
        DeletionRequest deletionRequest = new DeletionRequest.Builder().build();
        ListenableFuture<Void> result = mMeasurementClient.deleteRegistrations(deletionRequest);
        assertNull(result.get());
    }

    @Test
    public void testDeleteRegistrations_withRequest_withNoRange_withCallback_NoErrors()
            throws Exception {
        DeletionRequest deletionRequest =
                new DeletionRequest.Builder()
                        .setOriginUris(Collections.singletonList(ORIGIN_URI))
                        .setDomainUris(Collections.singletonList(DOMAIN_URI))
                        .build();
        ListenableFuture<Void> result = mMeasurementClient.deleteRegistrations(deletionRequest);
        assertNull(result.get());
    }

    @Test
    public void testDeleteRegistrations_withRequest_withEmptyLists_withRange_withCallback_NoErrors()
            throws Exception {
        DeletionRequest deletionRequest =
                new DeletionRequest.Builder()
                        .setOriginUris(Collections.emptyList())
                        .setDomainUris(Collections.emptyList())
                        .setStart(Instant.ofEpochMilli(0))
                        .setEnd(Instant.now())
                        .build();
        ListenableFuture<Void> result = mMeasurementClient.deleteRegistrations(deletionRequest);
        assertNull(result.get());
    }

    @Test
    public void testDeleteRegistrations_withRequest_withUris_withRange_withCallback_NoErrors()
            throws Exception {
        DeletionRequest deletionRequest =
                new DeletionRequest.Builder()
                        .setOriginUris(Collections.singletonList(ORIGIN_URI))
                        .setDomainUris(Collections.singletonList(DOMAIN_URI))
                        .setStart(Instant.ofEpochMilli(0))
                        .setEnd(Instant.now())
                        .build();
        ListenableFuture<Void> result = mMeasurementClient.deleteRegistrations(deletionRequest);
        assertNull(result.get());
    }

    @Test
    public void testDeleteRegistrations_withRequest_withInvalidArguments_withCallback_hasError()
            throws Exception {
        final MeasurementManager manager = sContext.getSystemService(MeasurementManager.class);
        Objects.requireNonNull(manager);

        CompletableFuture<Void> future = new CompletableFuture<>();
        OutcomeReceiver<Object, Exception> callback =
                new OutcomeReceiver<Object, Exception>() {
                    @Override
                    public void onResult(@NonNull Object ignoredResult) {
                        fail();
                    }

                    @Override
                    public void onError(Exception error) {
                        future.complete(null);
                        assertTrue(error instanceof IllegalArgumentException);
                    }
                };
        DeletionRequest request =
                new DeletionRequest.Builder()
                        .setOriginUris(Collections.singletonList(ORIGIN_URI))
                        .setDomainUris(Collections.singletonList(DOMAIN_URI))
                        .setEnd(Instant.now())
                        .build();

        manager.deleteRegistrations(request, mExecutorService, callback);

        Assert.assertNull(future.get());
    }

    @Test
    public void testMeasurementApiStatus_returnResultStatus() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final MeasurementManager manager = sContext.getSystemService(MeasurementManager.class);
        List<Integer> resultCodes = new ArrayList<>();

        manager.getMeasurementApiStatus(
                mExecutorService,
                result -> {
                    resultCodes.add(result);
                    countDownLatch.countDown();
                });

        assertThat(countDownLatch.await(500, TimeUnit.MILLISECONDS)).isTrue();
        Assert.assertNotNull(resultCodes);
        Assert.assertEquals(1, resultCodes.size());
    }
}
