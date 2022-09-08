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

package android.app.sdksandbox.testutils;

import android.annotation.NonNull;
import android.app.sdksandbox.ISdkToServiceCallback;
import android.content.pm.SharedLibraryInfo;
import android.content.pm.VersionedPackage;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.List;

public class StubSdkToServiceLink extends ISdkToServiceCallback.Stub {

    public static final SharedLibraryInfo SHARED_LIBRARY_INFO =
            new SharedLibraryInfo(
                    "testpath",
                    "test",
                    new ArrayList<>(),
                    "test",
                    0L,
                    SharedLibraryInfo.TYPE_STATIC,
                    new VersionedPackage("test", 0L),
                    null,
                    null,
                    false /* isNative */);

    @Override
    @NonNull
    public List<SharedLibraryInfo> getLoadedSdkLibrariesInfo(String clientName)
            throws RemoteException {
        ArrayList<SharedLibraryInfo> list = new ArrayList<>();
        list.add(SHARED_LIBRARY_INFO);
        return list;
    }
}
