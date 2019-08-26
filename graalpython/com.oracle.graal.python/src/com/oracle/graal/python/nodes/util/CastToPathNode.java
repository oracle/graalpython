/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.util;

import java.io.UnsupportedEncodingException;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ToByteArrayNode;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.NoAttributeHandler;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ValueProfile;

/**
 * Converts a Python object to a Path string
 */
@ImportStatic(SpecialMethodNames.class)
public abstract class CastToPathNode extends Node {
    private static final String ERROR_MESSAGE = "path should be string, bytes or os.PathLike, not %p";

    public abstract String execute(VirtualFrame frame, Object x);

    @Specialization
    String doBytes(PBytes x,
                    @Cached PRaiseNode raise,
                    @Cached ToByteArrayNode toBytes) {
        return newString(raise, toBytes.execute(x.getSequenceStorage()));
    }

    @Specialization
    String doBytearray(PByteArray x,
                    @Cached PRaiseNode raise,
                    @Cached ToByteArrayNode toBytes) {
        return newString(raise, toBytes.execute(x.getSequenceStorage()));
    }

    @Specialization
    String doMemoryview(VirtualFrame frame, PMemoryView x,
                    @Cached("create(TOBYTES)") LookupAndCallUnaryNode callToBytes,
                    @Cached PRaiseNode raise,
                    @Cached ToByteArrayNode toBytes) {
        Object toBytesResult = callToBytes.executeObject(frame, x);
        if (toBytesResult instanceof PBytes) {
            return newString(raise, toBytes.execute(((PBytes) toBytesResult).getSequenceStorage()));
        } else if (toBytesResult instanceof PByteArray) {
            return newString(raise, toBytes.execute(((PByteArray) toBytesResult).getSequenceStorage()));
        } else {
            throw raise.raise(PythonBuiltinClassType.TypeError, ERROR_MESSAGE, toBytesResult);
        }
    }

    @Specialization
    String doString(String x) {
        return x;
    }

    @Specialization
    String doPString(PString x) {
        return x.getValue();
    }

    @Specialization
    String doObject(VirtualFrame frame, Object object,
                    @Cached("createClassProfile()") ValueProfile resultTypeProfile,
                    @Cached PRaiseNode raise,
                    @Cached("createFsPathCall()") LookupAndCallUnaryNode callFsPath) {
        Object profiled = resultTypeProfile.profile(callFsPath.executeObject(frame, object));
        if (profiled instanceof String) {
            return (String) profiled;
        } else if (profiled instanceof PString) {
            return doPString((PString) profiled);
        }
        throw raise.raise(PythonBuiltinClassType.TypeError, "invalid type %p return from path-like object", profiled);
    }

    protected LookupAndCallUnaryNode createFsPathCall() {
        return LookupAndCallUnaryNode.create(SpecialMethodNames.__FSPATH__, () -> new NoAttributeHandler() {
            @Child PRaiseNode raise = PRaiseNode.create();

            @Override
            public Object execute(Object receiver) {
                throw raise.raise(PythonBuiltinClassType.TypeError, ERROR_MESSAGE, receiver);
            }
        });
    }

    public static CastToPathNode create() {
        return CastToPathNodeGen.create();
    }

    @TruffleBoundary
    private static String newString(PRaiseNode raise, byte[] ary) {
        try {
            return new String(ary, "ascii");
        } catch (UnsupportedEncodingException e) {
            throw raise.raise(PythonBuiltinClassType.UnicodeDecodeError, e);
        }
    }
}
