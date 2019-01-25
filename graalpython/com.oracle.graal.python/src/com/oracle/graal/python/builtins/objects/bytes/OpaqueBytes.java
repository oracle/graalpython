/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.bytes;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

public final class OpaqueBytes implements TruffleObject {
    private static final Assumption neverOpaqueAssumption = Truffle.getRuntime().createAssumption("all contexts use a readable filesystem");
    private static final Assumption alwaysOpaqueAssumption = Truffle.getRuntime().createAssumption("no context has a readable filesystem");
    private final byte[] bytes;

    public OpaqueBytes(byte[] bytes) {
        assert !neverOpaqueAssumption.isValid();
        this.bytes = bytes;
    }

    public ForeignAccess getForeignAccess() {
        return OpaqueBytesMessageResolutionForeign.ACCESS;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public static boolean isInstance(Object next) {
        if (neverOpaqueAssumption.isValid()) {
            return false;
        }
        return next instanceof OpaqueBytes;
    }

    public static boolean isEnabled(PythonContext context) {
        if (neverOpaqueAssumption.isValid()) {
            return false;
        } else if (alwaysOpaqueAssumption.isValid()) {
            return true;
        }
        return checkOption(context);
    }

    private static Boolean checkOption(PythonContext context) {
        return PythonOptions.getOption(context, PythonOptions.OpaqueFilesystem);
    }

    @TruffleBoundary
    public static boolean isInOpaqueFilesystem(String path, PythonContext context) {
        Path filePath = Paths.get(path);
        String prefixStr = PythonOptions.getOption(context, PythonOptions.OpaqueFilesystemPrefixes);
        String[] prefixes = prefixStr.split(Python3Core.PATH_SEPARATOR);
        for (int i = 0; i < prefixes.length; i++) {
            if (filePath.startsWith(Paths.get(prefixes[i]))) {
                return true;
            }
        }
        return false;
    }

    public static void initializeForNewContext(PythonContext context) {
        CompilerDirectives.transferToInterpreter();
        if (checkOption(context)) {
            neverOpaqueAssumption.invalidate();
        } else {
            alwaysOpaqueAssumption.invalidate();
        }
    }

    @MessageResolution(receiverType = OpaqueBytes.class)
    static class OpaqueBytesMessageResolution {
        @Resolve(message = "HAS_SIZE")
        abstract static class HasSizeNode extends Node {
            Object access(@SuppressWarnings("unused") OpaqueBytes object) {
                return true;
            }
        }

        @Resolve(message = "GET_SIZE")
        abstract static class SizeNode extends Node {
            Object access(OpaqueBytes object) {
                return object.bytes.length;
            }
        }

        @CanResolve
        abstract static class CheckFunction extends Node {
            protected static boolean test(TruffleObject receiver) {
                return receiver instanceof OpaqueBytes;
            }
        }
    }
}
