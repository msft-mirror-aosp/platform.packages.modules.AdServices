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

package com.android.adservices.ohttp;

import com.android.internal.annotations.VisibleForTesting;

/** Holds the reference to the constant native AEAD algorithm */
public class AeadNativeRef extends NativeRef {
    private AeadNativeRef(ReferenceManager referenceManager) {
        super(referenceManager);
    }

    /** Returns a reference to the AEAD algorithm AES-256-GCM */
    public static AeadNativeRef getHpkeAeadAes256GcmReference() {
        return getHpkeAeadAes256GcmReference(OhttpJniWrapper.getInstance());
    }

    @VisibleForTesting
    static AeadNativeRef getHpkeAeadAes256GcmReference(IOhttpJniWrapper ohttpJniWrapper) {
        ReferenceManager referenceManager =
                new ReferenceManager() {
                    @Override
                    public long getOrCreate() {
                        return ohttpJniWrapper.hpkeAeadAes256Gcm();
                    }

                    @Override
                    public void doRelease(long address) {
                        // do nothing;
                    }
                };
        return new AeadNativeRef(referenceManager);
    }
}
