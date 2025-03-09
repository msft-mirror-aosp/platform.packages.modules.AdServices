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

package com.android.adservices.cobalt;

import android.content.Context;
import android.content.res.AssetManager;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.Flags;
import com.android.cobalt.domain.Project;
import com.android.cobalt.registry.RegistryMerger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.cobalt.CobaltRegistry;
import com.google.common.io.ByteStreams;

import java.io.InputStream;
import java.util.Optional;

/** Loads the Cobalt registry from a APK asset. */
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
public final class CobaltRegistryLoader {
    private static final String REGISTRY_ASSET_FILE = "cobalt/cobalt_registry.binarypb";
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();

    /**
     * Get the Cobalt registry from the APK asset directory.
     *
     * @return the CobaltRegistry
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    // TODO(b/311183933): Remove passed in Context from static method.
    @SuppressWarnings("AvoidStaticContext")
    public static Project getRegistry(Context context, Flags flags)
            throws CobaltInitializationException {
        if (!CobaltRegistryValidated.IS_REGISTRY_VALIDATED) {
            throw new AssertionError(
                    "Cobalt registry was not validated at build time, something is very wrong");
        }

        AssetManager assetManager = context.getAssets();
        try (InputStream inputStream = assetManager.open(REGISTRY_ASSET_FILE)) {
            CobaltRegistry baseRegistry =
                    CobaltRegistry.parseFrom(ByteStreams.toByteArray(inputStream));

            if (flags.getCobaltFallBackToDefaultBaseRegistry()) {
                sLogger.d(
                        "Use base Cobalt registry because fall back to default base registry flag"
                                + " is enabled.");
                return Project.create(baseRegistry);
            }

            if (flags.getCobaltRegistryOutOfBandUpdateEnabled()) {
                Optional<CobaltRegistry> mddRegistry =
                        CobaltDownloadRegistryManager.getInstance().getMddRegistry();
                if (mddRegistry.isPresent()) {
                    sLogger.d("Use merged Cobalt registry.");
                    return Project.create(
                            RegistryMerger.mergeRegistries(baseRegistry, mddRegistry.get()));
                }
            }
            sLogger.d("Use base Cobalt registry.");
            return Project.create(baseRegistry);
        } catch (Exception e) {
            throw new CobaltInitializationException("Exception while reading registry", e);
        }
    }
}
