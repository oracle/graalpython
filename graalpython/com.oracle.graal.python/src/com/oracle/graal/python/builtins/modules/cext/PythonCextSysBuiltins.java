/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.cext;

import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_WRITE;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectBorrowed;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.VA_LIST_PTR;
import static com.oracle.graal.python.nodes.BuiltinNames.T_STDERR;
import static com.oracle.graal.python.nodes.BuiltinNames.T_STDOUT;
import static com.oracle.graal.python.nodes.BuiltinNames.T_SYS;

import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiTernaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.PromoteBorrowedValue;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.UnicodeFromFormatNode;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextSysBuiltins {

    @CApiBuiltin(ret = PyObjectBorrowed, args = {ConstCharPtrAsTruffleString}, call = Direct)
    abstract static class PySys_GetObject extends CApiUnaryBuiltinNode {

        @Specialization
        Object getObject(TruffleString name,
                        @Bind Node inliningTarget,
                        @Cached PromoteBorrowedValue promoteNode,
                        @Cached PyObjectLookupAttr lookupNode,
                        @Cached PyObjectSetAttr setAttrNode) {
            try {
                PythonModule sys = getCore().lookupBuiltinModule(T_SYS);
                Object value = lookupNode.execute(null, inliningTarget, sys, name);
                if (value == PNone.NO_VALUE) {
                    return getNativeNull();
                }
                Object promotedValue = promoteNode.execute(inliningTarget, value);
                if (promotedValue != null) {
                    setAttrNode.execute(inliningTarget, sys, name, promotedValue);
                    return promotedValue;
                }
                return value;
            } catch (PException e) {
                // PySys_GetObject delegates to PyDict_GetItem
                // which suppresses all exceptions for historical reasons
                return getNativeNull();
            }
        }
    }

    private static Object selectOut(int fd) {
        CompilerAsserts.neverPartOfCompilation();
        Object file;
        PythonModule sys = PythonContext.get(null).lookupBuiltinModule(T_SYS);
        if (fd == 0) {
            file = sys.getAttribute(T_STDOUT);
        } else {
            file = sys.getAttribute(T_STDERR);
        }
        return file;
    }

    @CApiBuiltin(ret = Int, args = {Int, ConstCharPtrAsTruffleString}, call = Ignored)
    abstract static class PyTruffleSys_WriteStd extends CApiBinaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static Object doGeneric(int fd, TruffleString msg) {
            try {
                PyObjectCallMethodObjArgs.executeUncached(selectOut(fd), T_WRITE, msg);
                return 0;
            } catch (PException e) {
                return -1;
            }
        }
    }

    @CApiBuiltin(ret = Int, args = {Int, ConstCharPtrAsTruffleString, VA_LIST_PTR}, call = Ignored)
    abstract static class PyTruffleSys_FormatStd extends CApiTernaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static Object doGeneric(int fd, TruffleString format, Object vaList) {
            try {
                Object msg = UnicodeFromFormatNode.executeUncached(format, vaList);
                PyObjectCallMethodObjArgs.executeUncached(selectOut(fd), T_WRITE, msg);
                return 0;
            } catch (PException e) {
                // do not propagate any exception to native
                return -1;
            }
        }
    }
}
