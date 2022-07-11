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

import static com.android.adservices.ResultCode.RESULT_UNAUTHORIZED_CALL;

import android.adservices.common.CallerMetadata;
import android.adservices.exceptions.GetTopicsException;
import android.annotation.CallbackExecutor;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.app.sdksandbox.SandboxedSdkContext;
import android.content.Context;
import android.os.OutcomeReceiver;
import android.os.RemoteException;
import android.os.SystemClock;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.LogUtil;
import com.android.adservices.ServiceBinder;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Topics Manager.
 */
public class TopicsManager {

    /**
     * Result codes from {@link TopicsManager#getTopics(GetTopicsRequest, Executor,
     * OutcomeReceiver)} methods.
     *
     * @hide
     */
    @IntDef(
            value = {
                RESULT_OK,
                RESULT_INTERNAL_ERROR,
                RESULT_INVALID_ARGUMENT,
                RESULT_IO_ERROR,
                RESULT_RATE_LIMIT_REACHED,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ResultCode {}

    /** The call was successful. */
    public static final int RESULT_OK = 0;

    /**
     * An internal error occurred within Topics API, which the caller cannot address.
     *
     * <p>This error may be considered similar to {@link IllegalStateException}
     */
    public static final int RESULT_INTERNAL_ERROR = 1;

    /**
     * The caller supplied invalid arguments to the call.
     *
     * <p>This error may be considered similar to {@link IllegalArgumentException}.
     */
    public static final int RESULT_INVALID_ARGUMENT = 2;

    /**
     * An issue occurred reading or writing to storage. The call might succeed if repeated.
     *
     * <p>This error may be considered similar to {@link java.io.IOException}.
     */
    public static final int RESULT_IO_ERROR = 3;

    /**
     * The caller has reached the API call limit.
     *
     * <p>The caller should back off and try later.
     */
    public static final int RESULT_RATE_LIMIT_REACHED = 4;

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
            throw new IllegalStateException("Unable to find the service");
        }
        return service;
    }

    /**
     * Return the topics.
     *
     * @param getTopicsRequest The request for obtaining Topics.
     * @param executor The executor to run callback.
     * @param callback The callback that's called after topics are available or an error occurs.
     * @throws SecurityException if caller is not authorized to call this API.
     * @throws GetTopicsException if call results in an internal error.
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
        // First check if context is SandboxedSdkContext or not
        Context getTopicsRequestContext = getTopicsRequest.getContext();
        if (getTopicsRequestContext instanceof SandboxedSdkContext) {
            appPackageName = ((SandboxedSdkContext) getTopicsRequestContext).getClientPackageName();
        } else { // This is the case without the Sandbox.
            appPackageName = getTopicsRequestContext.getPackageName();
        }
        try {
            service.getTopics(
                    new GetTopicsParam.Builder()
                            .setAppPackageName(appPackageName)
                            .setSdkName(sdkName)
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
                                                    new GetTopicsException(
                                                            resultParcel.getResultCode()));
                                        }
                                    });
                        }

                        @Override
                        public void onFailure(int resultCode) {
                            executor.execute(
                                    () -> {
                                        if (resultCode == RESULT_UNAUTHORIZED_CALL) {
                                            callback.onError(
                                                    new SecurityException(
                                                            "Caller is not authorized to call this"
                                                                    + " API."));
                                        }
                                    });
                        }
                    });
        } catch (RemoteException e) {
            LogUtil.e("RemoteException", e);
            callback.onError(new GetTopicsException(RESULT_INTERNAL_ERROR, "Internal Error!"));
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
    public void unbindFromService() {
        mServiceBinder.unbindFromService();
    }
}
