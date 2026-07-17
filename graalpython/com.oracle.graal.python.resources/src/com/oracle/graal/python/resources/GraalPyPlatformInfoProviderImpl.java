/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.resources;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.oracle.graal.python.runtime.platform.GraalPyPlatformInfoProvider;
import com.oracle.truffle.api.InternalResource.CPUArchitecture;
import com.oracle.truffle.api.InternalResource.OS;

public final class GraalPyPlatformInfoProviderImpl implements GraalPyPlatformInfoProvider {
    /**
     * The version generated at build time is stored in an ASCII-compatible way. At build time, we
     * added the ordinal value of some base character (in this case {@code '!'}) to ensure that we
     * have a printable character.
     */
    private static final int VERSION_BASE = '!';

    private static final class VersionsInfoHolder {
        private static final VersionsInfo INFO = readVersionsInfo(OS.getCurrent(), CPUArchitecture.getCurrent());
    }

    @Override
    public PlatformInfo getPlatformInfo() {
        return getVersionsInfo().platformInfo();
    }

    static VersionsInfo getVersionsInfo() {
        return VersionsInfoHolder.INFO;
    }

    static VersionsInfo readVersionsInfo(OS os, CPUArchitecture cpuArchitecture) {
        String resource = "/META-INF/resources/" + os + "/" + cpuArchitecture + "/graalpy_versions";
        try (InputStream stream = GraalPyPlatformInfoProviderImpl.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing GraalPy platform metadata resource: " + resource);
            }
            List<String> lines = new BufferedReader(new InputStreamReader(stream, StandardCharsets.US_ASCII)).lines().toList();
            if (lines.size() < 4 || lines.get(0).length() < 7) {
                throw new IllegalStateException("Malformed GraalPy platform metadata resource: " + resource);
            }
            String versions = lines.get(0);
            return new VersionsInfo(
                            versions.charAt(0) - VERSION_BASE,
                            versions.charAt(1) - VERSION_BASE,
                            versions.charAt(3) - VERSION_BASE,
                            versions.charAt(4) - VERSION_BASE,
                            lines.get(1).strip(),
                            new PlatformInfo(lines.get(2).strip(), lines.get(3).strip()));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read GraalPy platform metadata resource: " + resource, e);
        }
    }

    record VersionsInfo(int pythonMajor, int pythonMinor, int graalVMajor, int graalVMinor, String pythonAbiFlags, PlatformInfo platformInfo) {
    }
}
