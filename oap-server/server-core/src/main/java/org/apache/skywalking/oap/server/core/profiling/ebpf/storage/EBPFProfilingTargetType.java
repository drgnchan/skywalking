/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.core.profiling.ebpf.storage;

import org.apache.skywalking.oap.server.core.UnexpectedException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * eBPF type for profiling the process
 */
public enum EBPFProfilingTargetType {

    UNKNOWN(0),

    ON_CPU(1),

    OFF_CPU(2),
    ;
    private final int value;
    private static final Map<Integer, EBPFProfilingTargetType> DICTIONARY = new HashMap<>();

    static {
        Arrays.stream(EBPFProfilingTargetType.values()).collect(Collectors.toMap(EBPFProfilingTargetType::value, type -> type)).forEach(DICTIONARY::put);
    }

    EBPFProfilingTargetType(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static EBPFProfilingTargetType valueOf(int value) {
        EBPFProfilingTargetType type = DICTIONARY.get(value);
        if (type == null) {
            throw new UnexpectedException("Unknown EBPFProfilingTargetType value");
        }
        return type;
    }
}