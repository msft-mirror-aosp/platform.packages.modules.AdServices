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
package android.adservices.topics;

import static android.adservices.common.AdServicesStatusUtils.ILLEGAL_STATE_EXCEPTION_ERROR_MESSAGE;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.CallerMetadata;
import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.annotation.TestApi;
import android.app.sdksandbox.SandboxedSdkContext;
import android.content.Context;
import android.os.OutcomeReceiver;
import android.os.RemoteException;
import android.os.SystemClock;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.LogUtil;
import com.android.adservices.ServiceBinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * TopicsManager provides APIs for App and Ad-Sdks to get the user interest topics in a privacy
 * preserving way.
 */
public final class TopicsManager {

    public static final String TOPICS_SERVICE = "topics_service";

    // Whent an app calls the Topics API directly, it sets the SDK name to empty string.
    static final String EMPTY_SDK = "";

    private final Context mContext;
    private final ServiceBinder<ITopicsService> mServiceBinder;

    /**
     * Create TopicsManager
     *
     * @hide
     */
    public TopicsManager(Context context) {
        mContext = context;
        mServiceBinder =
                ServiceBinder.getServiceBinder(
                        context,
                        AdServicesCommon.ACTION_TOPICS_SERVICE,
                        ITopicsService.Stub::asInterface);
    }

    @NonNull
    private ITopicsService getService() {
        ITopicsService service = mServiceBinder.getService();
        if (service == null) {
            throw new IllegalStateException(ILLEGAL_STATE_EXCEPTION_ERROR_MESSAGE);
        }
        return service;
    }

    /**
     * Return the topics.
     *
     * @param getTopicsRequest The request for obtaining Topics.
     * @param executor The executor to run callback.
     * @param callback The callback that's called after topics are available or an error occurs.
     * @throws Exception if caller is not authorized to call this API.
     * @throws Exception if call results in an internal error.
     */
    @NonNull
    public void getTopics(
            @NonNull GetTopicsRequest getTopicsRequest,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<GetTopicsResponse, Exception> callback) {
        Objects.requireNonNull(getTopicsRequest);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);
        CallerMetadata callerMetadata = new CallerMetadata.Builder()
                .setBinderElapsedTimestamp(SystemClock.elapsedRealtime())
                .build();
        final ITopicsService service = getService();
        String sdkName = getTopicsRequest.getSdkName();
        String appPackageName = "";
        String sdkPackageName = "";
        // First check if context is SandboxedSdkContext or not
        Context getTopicsRequestContext = getTopicsRequest.getContext();
        if (getTopicsRequestContext instanceof SandboxedSdkContext) {
            SandboxedSdkContext requestContext = ((SandboxedSdkContext) getTopicsRequestContext);
            sdkPackageName = requestContext.getSdkPackageName();
            appPackageName = requestContext.getClientPackageName();
        } else { // This is the case without the Sandbox.
            appPackageName = getTopicsRequestContext.getPackageName();
        }
        try {
            service.getTopics(
                    new GetTopicsParam.Builder()
                            .setAppPackageName(appPackageName)
                            .setSdkName(sdkName)
                            .setSdkPackageName(sdkPackageName)
                            .build(),
                    callerMetadata,
                    new IGetTopicsCallback.Stub() {
                        @Override
                        public void onResult(GetTopicsResult resultParcel) {
                            executor.execute(
                                    () -> {
                                        if (resultParcel.isSuccess()) {
                                            callback.onResult(
                                                    new GetTopicsResponse.Builder()
                                                            .setTopics(getTopicList(resultParcel))
                                                            .build());
                                        } else {
                                            // TODO: Errors should be returned in onFailure method.
                                            callback.onError(
                                                    AdServicesStatusUtils.asException(
                                                            resultParcel));
                                        }
                                    });
                        }

                        @Override
                        public void onFailure(int resultCode) {
                            executor.execute(
                                    () ->
                                            callback.onError(
                                                    AdServicesStatusUtils.asException(resultCode)));
                        }
                    });
        } catch (RemoteException e) {
            LogUtil.e(e, "RemoteException");
            callback.onError(e);
        }
    }

    private List<Topic> getTopicList(GetTopicsResult resultParcel) {
        List<Long> taxonomyVersionsList = resultParcel.getTaxonomyVersions();
        List<Long> modelVersionsList = resultParcel.getModelVersions();
        List<Integer> topicsCodeList = resultParcel.getTopics();
        List<Topic> topicList = new ArrayList<>();
        int size = taxonomyVersionsList.size();
        for (int i = 0; i < size; i++) {
            Topic topic =
                    new Topic(
                            taxonomyVersionsList.get(i),
                            modelVersionsList.get(i),
                            topicsCodeList.get(i));
            topicList.add(topic);
        }

        return topicList;
    }

    /**
     * If the service is in an APK (as opposed to the system service), unbind it from the service to
     * allow the APK process to die.
     *
     * @hide Not sure if we'll need this functionality in the final API. For now, we need it for
     *     performance testing to simulate "cold-start" situations.
     */
    // TODO: change to @VisibleForTesting
    @TestApi
    public void unbindFromService() {
        mServiceBinder.unbindFromService();
    }
}
