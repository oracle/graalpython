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
package com.oracle.graal.python.test.integration.advanced;

import java.nio.file.Files;
import java.nio.file.Path;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.io.IOAccess;

/** Runs in a fresh VM for each variant; deliberately does not use the Python launcher or a home. */
public final class ResourceVariantProbe {
    private ResourceVariantProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (System.getProperty("org.graalvm.language.python.home") != null || System.getenv("GRAAL_PYTHONHOME") != null) {
            throw new AssertionError("This probe must use JAR-backed resources, not a configured Python home");
        }
        boolean graalos = args[0].equals("musl-swcfi");
        Path target = Path.of(args[1]);
        if (!Engine.copyResources(target, "python")) {
            throw new AssertionError("Python resources were not copied");
        }
        try (var files = Files.walk(target)) {
            if (files.noneMatch(p -> p.getFileName().toString().equals("pyconfig.h"))) {
                throw new AssertionError("Missing native header in extracted resources");
            }
        }
        // The host JVM cannot load SW-CFI native libraries. Metadata and extraction must still
        // agree without loading them, and without a launcher having set any system properties.
        try (Context context = Context.newBuilder("python").allowExperimentalOptions(true).option("python.PosixModuleBackend", "java").allowNativeAccess(false).allowIO(IOAccess.ALL).build()) {
            String multiarch = context.eval("python", "import sys; sys.implementation._multiarch").asString();
            if (graalos != multiarch.equals("x86_64-graalos")) {
                throw new AssertionError("Wrong resource ABI: " + multiarch);
            }
            String sysconfigMultiarch = context.eval("python", "import sysconfig; sysconfig.get_config_var('MULTIARCH')").asString();
            if (!multiarch.equals(sysconfigMultiarch)) {
                throw new AssertionError("Metadata/sysconfig mismatch: " + multiarch + " != " + sysconfigMultiarch);
            }
        }
    }
}
