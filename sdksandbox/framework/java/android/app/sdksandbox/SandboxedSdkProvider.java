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

package android.app.sdksandbox;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.os.Bundle;
import android.view.SurfaceControlViewHost.SurfacePackage;
import android.view.View;


/**
 * Encapsulates API which SDK sandbox can use to interact with SDKs loaded into it.
 *
 * <p>SDK has to implement this abstract class to generate an entry point for SDK sandbox to be able
 * to call it through.
 *
 * <p>Note: All APIs defined in this class are not stable and subject to change.
 */
public abstract class SandboxedSdkProvider {
    private Context mContext;

    /**
     * Sets the SDK {@link Context} which can then be received using {@link
     * SandboxedSdkProvider#getContext()}. This is called before {@link
     * SandboxedSdkProvider#onLoadSdk} is invoked. No operations requiring a {@link Context} should
     * be performed before then, as {@link SandboxedSdkProvider#getContext} will return null until
     * this method has been called.
     *
     * <p>Throws IllegalStateException if a base context has already been set.
     *
     * @param context The new base context.
     */
    public final void attachContext(@NonNull Context context) {
        if (mContext != null) {
            throw new IllegalStateException("Context already set");
        }
        mContext = context;
    }

    /**
     * Return the {@link Context} previously set through {@link SandboxedSdkProvider#attachContext}.
     * This will return null if no context has been previously set.
     */
    @Nullable
    public Context getContext() {
        return mContext;
    }

    /**
     * Does the work needed for the SDK to start handling requests.
     *
     * <p>This function is called by the SDK sandbox after it loads the SDK.
     *
     * <p>SDK should do any work to be ready to handle upcoming requests. It should not include the
     * initialization logic that depends on other SDKs being loaded into the SDK sandbox. Any
     * further initialization can be triggered by the client using {@link
     * SdkSandboxManager#sendData}. The SDK should not do any operations requiring a {@link Context}
     * object before this method has been called.
     *
     * @param params list of params passed from the client when it loads the SDK. This can be empty.
     * @return Returns a LoadSdkResponse object, this is passed back to the client.
     */
    public abstract @NonNull LoadSdkResponse onLoadSdk(@NonNull Bundle params)
            throws LoadSdkException;
    /**
     * Does the work needed for the SDK to free its resources before being unloaded.
     *
     * <p>This function is called by the SDK sandbox manager before it unloads the SDK.
     */
    public void beforeUnloadSdk() {}

    /**
     * Requests a view to be remotely rendered to the client app process.
     *
     * <p>Returns {@link View} will be wrapped into {@link SurfacePackage}. the resulting {@link
     * SurfacePackage} will be sent back to the client application.
     *
     * @param windowContext the {@link Context} of the display which meant to show the view
     * @param params list of params passed from the client application requesting the view
     * @param width The view returned will be laid as if in a window of this width, in pixels.
     * @param height The view returned will be laid as if in a window of this height, in pixels.
     * @return a {@link View} which SDK sandbox pass to the client application requesting the view
     */
    @NonNull
    public abstract View getView(
            @NonNull Context windowContext, @NonNull Bundle params, int width, int height);

    /**
     * Called when data sent from the app is received by an SDK.
     *
     * @param data the data sent by the app.
     * @param callback to notify the app if the data has been successfully received.
     */
    public abstract void onDataReceived(
            @NonNull Bundle data, @NonNull DataReceivedCallback callback);

    /**
     * Callback for tracking the status of initializing the SDK.
     *
     * <p>This callback is created by the SDK sandbox, SDKs should use it to notify the SDK sandbox
     * about the status of {@link SandboxedSdkProvider#onLoadSdk}
     */
    public interface OnLoadSdkCallback {
        /**
         * Called when sdk is successfully loaded.
         *
         * <p>After SDK successfully initialized, it must call this method on the callback object.
         *
         * @param params list of params to be passed to the client application
         */
        void onLoadSdkFinished(@NonNull Bundle params);

        /**
         * If SDK failed to initialize, it must call this method on the callback object.
         *
         * @param errorMessage a String description of the error
         */
        void onLoadSdkError(@NonNull String errorMessage);
    }

    /**
     * Callback for tracking the status of data received from the client application.
     *
     * <p>This callback is created by the SDK sandbox. SDKs can use it to notify the SDK sandbox
     * about the status of processing the data received.
     */
    public interface DataReceivedCallback {
        /**
         * After the SDK has completed processing the data received, it can call this method on the
         * callback object and pass back any data if needed.
         *
         * @param params list of params to be passed to the client application.
         */
        void onDataReceivedSuccess(@NonNull Bundle params);

        /**
         * If the SDK fails to process the data received from the client application, it can call
         * this method on the callback object.
         *
         * @param errorMessage a String description of the error
         */
        void onDataReceivedError(@NonNull String errorMessage);
    }
}
