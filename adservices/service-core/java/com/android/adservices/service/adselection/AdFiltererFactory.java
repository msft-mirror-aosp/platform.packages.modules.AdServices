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

package com.android.adservices.service.adselection;

import android.content.Context;

import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.service.Flags;
/** Factory for implementations of the {@link AdFilterer} interface */
public final class AdFiltererFactory {
    /**
     * Returns the correct {@link AdFilterer} implementation to use based on the given flags.
     *
     * @param context The application context.
     * @param flags The current Adservices flags.
     * @return An instance of {@link AdFiltererImpl} if flags.getFledgeAdSelectionFilteringEnabled()
     *     is true and on instance of {@link AdFiltererNoOpImpl} otherwise.
     */
    public static AdFilterer getAdFilterer(Context context, Flags flags) {
        if (flags.getFledgeAdSelectionFilteringEnabled()) {
            return new AdFiltererImpl(SharedStorageDatabase.getInstance(context).appInstallDao());
        } else {
            return new AdFiltererNoOpImpl();
        }
    }
}
