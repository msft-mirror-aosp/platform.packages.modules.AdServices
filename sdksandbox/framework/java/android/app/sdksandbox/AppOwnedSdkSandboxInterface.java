/*
 * Copyright (C) 2023 The Android Open Source Project
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
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Represents an Interface implemented by the App to interact with an SDK in the sandbox process.
 */
public final class AppOwnedSdkSandboxInterface implements Parcelable {

    private String mName;
    private long mVersion;
    private IBinder mInterface;

    public static final @NonNull Creator<AppOwnedSdkSandboxInterface> CREATOR =
            new Creator<AppOwnedSdkSandboxInterface>() {
                @Override
                public AppOwnedSdkSandboxInterface createFromParcel(Parcel in) {
                    return new AppOwnedSdkSandboxInterface(in);
                }

                @Override
                public AppOwnedSdkSandboxInterface[] newArray(int size) {
                    return new AppOwnedSdkSandboxInterface[size];
                }
            };

    public AppOwnedSdkSandboxInterface(
            @NonNull String name, long version, @NonNull IBinder binder) {
        mName = name;
        mVersion = version;
        mInterface = binder;
    }

    private AppOwnedSdkSandboxInterface(@NonNull Parcel in) {
        mName = in.readString();
        mVersion = in.readLong();
        mInterface = in.readStrongBinder();
    }

    /**
     * Returns the name used to register the AppOwnedSdkSandboxInterface.
     *
     * <p>App can register only one interface of given name.
     */
    @NonNull
    public String getName() {
        return mName;
    }

    /**
     * Returns the version used to register the AppOwnedSdkSandboxInterface.
     *
     * <p>A version may be chosen by an app, and used to communicate any updates the app makes to
     * this implementation.
     */
    @NonNull
    public long getVersion() {
        return mVersion;
    }

    /** Returns Binder associated with AppOwnedSdkSandboxInterface. */
    @NonNull
    public IBinder getInterface() {
        return mInterface;
    }

    /** {@inheritDoc} */
    @Override
    public int describeContents() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mName);
        dest.writeLong(mVersion);
        dest.writeStrongBinder(mInterface);
    }
}
