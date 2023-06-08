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

package android.adservices.cobalt;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Parameters describing the encrypted Cobalt Envelope being sent.
 *
 * @hide
 */
@SystemApi
public final class EncryptedCobaltEnvelopeParams implements Parcelable {
    /**
     * Whether data is from a development or production device.
     *
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        ENVIRONMENT_DEV,
        ENVIRONMENT_PROD,
    })
    public @interface Environment {}

    /** Production environment. */
    public static final int ENVIRONMENT_PROD = 0;

    /** Development environment. */
    public static final int ENVIRONMENT_DEV = 1;

    private final @Environment int mEnvironment;
    private final int mKeyIndex;
    private final byte[] mCiphertext;

    public EncryptedCobaltEnvelopeParams(
            @Environment int environment, @NonNull int keyIndex, @NonNull byte[] ciphertext) {
        mEnvironment = environment;
        mKeyIndex = keyIndex;
        mCiphertext = ciphertext;
    }

    private EncryptedCobaltEnvelopeParams(@NonNull Parcel in) {
        mCiphertext = null;

        mEnvironment = in.readInt();
        mKeyIndex = in.readInt();
        in.readByteArray(mCiphertext);
    }

    public static final @NonNull Creator<EncryptedCobaltEnvelopeParams> CREATOR =
            new Parcelable.Creator<EncryptedCobaltEnvelopeParams>() {
                @Override
                public EncryptedCobaltEnvelopeParams createFromParcel(Parcel in) {
                    return new EncryptedCobaltEnvelopeParams(in);
                }

                @Override
                public EncryptedCobaltEnvelopeParams[] newArray(int size) {
                    return new EncryptedCobaltEnvelopeParams[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        out.writeInt(mEnvironment);
        out.writeInt(mKeyIndex);
        out.writeByteArray(mCiphertext);
    }

    /** Get the environment. */
    @NonNull
    public @Environment int getEnvironment() {
        return mEnvironment;
    }

    /**
     * Get index of the (public, private) key pair used to encrypted the Envelope.
     *
     * <p>There are multiple pairs on the server and it's cheaper to send the index than the actual
     * public key used.
     */
    @NonNull
    public int getKeyIndex() {
        return mKeyIndex;
    }

    /**
     * Get the encrypted Envelope.
     *
     * <p>Envelopes are will be on the order of 1KiB in size.
     */
    @NonNull
    public byte[] getCiphertext() {
        return mCiphertext;
    }
}
