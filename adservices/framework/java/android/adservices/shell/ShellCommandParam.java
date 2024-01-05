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

package android.adservices.shell;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * Represents request which contains command and args as an input to runShellCommand API.
 *
 * @hide
 */
public class ShellCommandParam implements Parcelable {

    /* Array containing command name with all the args */
    private final String[] mCommandArgs;

    public ShellCommandParam(String... commandArgs) {
        mCommandArgs = Objects.requireNonNull(commandArgs);
    }

    private ShellCommandParam(Parcel in) {
        this(in.createStringArray());
    }

    public static final Creator<ShellCommandParam> CREATOR =
            new Parcelable.Creator<>() {
                @Override
                public ShellCommandParam createFromParcel(Parcel in) {
                    return new ShellCommandParam(in);
                }

                @Override
                public ShellCommandParam[] newArray(int size) {
                    return new ShellCommandParam[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStringArray(mCommandArgs);
    }

    /** Get the command name with all the args as a list. */
    public String[] getCommandArgs() {
        return mCommandArgs;
    }
}
