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
package com.android.adservices.service.common;

import android.app.adservices.AdServicesManager;
import android.content.Context;

import com.android.adservices.LogUtil;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.shared.common.ApplicationContextProvider;
import com.android.adservices.shared.common.ApplicationContextSingleton;

import com.google.common.annotations.VisibleForTesting;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Provider used to set the application context singleton and other common stuff (like dumping data
 * not associated with a service).
 */
public final class AdServicesInternalProvider extends ApplicationContextProvider {

    @VisibleForTesting static final String DUMP_ARG_FULL_QUIET = "--quiet";
    @VisibleForTesting static final String DUMP_ARG_SHORT_QUIET = "-q";

    @Override
    public boolean onCreate() {
        LogUtil.d("AdServicesInternalProvider.onCreate()");
        return super.onCreate();
    }

    @SuppressWarnings("NewApi")
    @Override
    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        boolean quiet = false;
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case DUMP_ARG_SHORT_QUIET:
                    case DUMP_ARG_FULL_QUIET:
                        quiet = true;
                        break;
                    default:
                        LogUtil.w("invalid arg at index %d: %s", i, arg);
                }
            }
        }

        try {
            Context appContext = ApplicationContextSingleton.get();
            writer.printf("ApplicationContext: %s\n", appContext);
            if (appContext != null) {
                AppManifestConfigMetricsLogger.dump(appContext, writer);
            }
        } catch (Exception e) {
            writer.printf("Failed to get ApplicationContextSingleton: %s\n", e);
        }

        AdServicesManager.dump(writer);

        if (!quiet) {
            Flags flags = FlagsFactory.getFlags();
            writer.printf("\nFlags (from %s):\n", flags.getClass().getName());
            flags.dump(writer, args);

            DebugFlags debugFlags = DebugFlags.getInstance();
            writer.printf("\nDebugFlags (from %s):\n", debugFlags.getClass().getName());
            debugFlags.dump(writer);
        }
    }
}
