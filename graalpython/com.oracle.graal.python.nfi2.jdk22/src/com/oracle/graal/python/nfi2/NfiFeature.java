/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nfi2;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.FunctionDescriptor;
import java.util.List;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeForeignAccess;

public class NfiFeature implements Feature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        // TODO(NFI2) these are graalpy-specific, remove this from nfi2
        List<FunctionDescriptor> descs = List.of(FunctionDescriptor.of(JAVA_LONG, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_LONG),
                        FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG), FunctionDescriptor.of(JAVA_INT, JAVA_INT),
                        FunctionDescriptor.of(JAVA_INT, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT),
                        FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG),
                        FunctionDescriptor.ofVoid(JAVA_INT, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_BYTE),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_DOUBLE),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG,
                                        JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_INT, JAVA_LONG),
                        FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_INT),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG),
                        FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT),
                        FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS),
                        FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG, JAVA_INT),
                        FunctionDescriptor.ofVoid(JAVA_INT, JAVA_LONG, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_LONG),
                        FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG, JAVA_LONG),
                        FunctionDescriptor.ofVoid(JAVA_INT),
                        FunctionDescriptor.ofVoid(JAVA_LONG),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_INT),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT),
                        FunctionDescriptor.ofVoid(),
                        FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_INT, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_DOUBLE, JAVA_DOUBLE),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_INT),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_INT, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_LONG),
                        FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT),
                        FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_INT),
                        FunctionDescriptor.of(JAVA_DOUBLE, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_INT),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_INT),
                        FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS),
                        FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_LONG),
                        FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_INT),
                        FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_LONG));
        for (FunctionDescriptor desc : descs) {
            RuntimeForeignAccess.registerForDowncall(desc);
            RuntimeForeignAccess.registerForUpcall(desc);
        }
    }
}
