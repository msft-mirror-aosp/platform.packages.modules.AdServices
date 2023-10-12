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

function encodeSignals(signals, maxSize) {
    var base64Array = ['MA==', 'MQ==', 'Mg==', 'Mw==', 'NA==', 'NQ==', 'Ng==', 'Nw==',
        'OA==', 'OQ==', 'Og==', 'Ow==', 'PA==', 'PQ==', 'Pg==', 'Pw==', 'QA==', 'QQ==', 'Qg==', 'Qw==',
    ];
    return { status: 0, results: base64Array[signals.length] };
}
