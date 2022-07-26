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
package android.adservices.appsetid;

import static com.android.adservices.ResultCode.RESULT_UNAUTHORIZED_CALL;

import android.adservices.common.CallerMetadata;
import android.annotation.CallbackExecutor;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.content.Context;
import android.os.OutcomeReceiver;
import android.os.RemoteException;
import android.os.SystemClock;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.LogUtil;
import com.android.adservices.ServiceBinder;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * AppsetId Manager provides APIs for app and ad-SDKs to access advertising ID. The appsetId is a
 * unique, user-resettable ID for non-monetizing advertising usecases. The scope of the ID can be
 * per app or per deveoper account on play store. AppsetId is used for analytics or fraud prevention
 * use cases, on a given device that one may need to correlate usage or actions across a set of apps
 * owned by an organization.
 */
public class AppsetIdManager {

    /**
     * Result codes from {@link AppsetIdManager#getAppsetId(Executor, OutcomeReceiver)} methods.
     *
     * @hide
     */
    @IntDef(
            value = {
                RESULT_OK,
                RESULT_INTERNAL_ERROR,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ResultCode {}

    /**
     * The call was successful.
     *
     * @hide
     */
    public static final int RESULT_OK = 0;

    /**
     * An internal error occurred within AppsetId API, which the caller cannot address.
     *
     * @hide
     */
    public static final int RESULT_INTERNAL_ERROR = 1;

    /**
     * Service used for registering AppsetIdManager in the system service registry.
     *
     * @hide
     */
    public static final String APPSETID_SERVICE = "appsetid_service";

    private final Context mContext;
    private final ServiceBinder<IAppsetIdService> mServiceBinder;

    /**
     * Create AppsetIdManager
     *
     * @hide
     */
    public AppsetIdManager(Context context) {
        mContext = context;
        mServiceBinder =
                ServiceBinder.getServiceBinder(
                        context,
                        AdServicesCommon.ACTION_APPSETID_SERVICE,
                        IAppsetIdService.Stub::asInterface);
    }

    @NonNull
    private IAppsetIdService getService() {
        IAppsetIdService service = mServiceBinder.getService();
        if (service == null) {
            throw new IllegalStateException("Unable to find the service");
        }
        return service;
    }

    @NonNull
    private Context getContext() {
        return mContext;
    }

    /**
     * Retrieve the AppsetId.
     *
     * @param executor The executor to run callback.
     * @param callback The callback that's called after appsetid are available or an error occurs.
     * @throws SecurityException if caller is not authorized to call this API.
     * @throws GetAppsetIdException if call results in an internal error.
     */
    @NonNull
    public void getAppsetId(
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<AppsetId, Exception> callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);
        CallerMetadata callerMetadata =
                new CallerMetadata.Builder()
                        .setBinderElapsedTimestamp(SystemClock.elapsedRealtime())
                        .build();
        final IAppsetIdService service = getService();
        try {
            service.getAppsetId(
                    callerMetadata,
                    new IGetAppsetIdCallback.Stub() {
                        @Override
                        public void onResult(GetAppsetIdResult resultParcel) {
                            executor.execute(
                                    () -> {
                                        if (resultParcel.isSuccess()) {
                                            callback.onResult(
                                                    new AppsetId(
                                                            resultParcel.getAppsetId(),
                                                            resultParcel.getAppsetIdScope()));
                                        } else {
                                            callback.onError(
                                                    new Exception(
                                                            "Got Exception and the result code is "
                                                                    + resultParcel
                                                                            .getResultCode()));
                                        }
                                    });
                        }

                        @Override
                        public void onFailure(int resultCode) {
                            executor.execute(
                                    () -> {
                                        if (resultCode == RESULT_UNAUTHORIZED_CALL) {
                                            callback.onError(
                                                    new Exception(
                                                            "Got SecurityException, Caller is not"
                                                                    + " authorized to call this"
                                                                    + " API."));
                                        }
                                    });
                        }
                    });
        } catch (RemoteException e) {
            LogUtil.e("RemoteException", e);
            callback.onError(e);
        }
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
