/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.ctypes;

import static com.oracle.graal.python.nodes.ErrorMessages.CANNOT_BUILD_PARAMETER;
import static com.oracle.graal.python.nodes.ErrorMessages.MEMORY_LEAK_IN_CALLBACK_FUNCTION;
import static com.oracle.graal.python.nodes.ErrorMessages.ON_CALLING_CTYPES_CALLBACK_FUNCTION;
import static com.oracle.graal.python.nodes.ErrorMessages.ON_CONVERTING_RESULT_OF_CTYPES_CALLBACK_FUNCTION;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.RuntimeWarning;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins.WarnNode;
import com.oracle.graal.python.builtins.modules.ctypes.CFieldBuiltins.GetFuncNode;
import com.oracle.graal.python.builtins.modules.ctypes.CFieldBuiltins.SetFuncNode;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.PyTypeCheck;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FFI_TYPES;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldDesc;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldGet;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldSet;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyTypeStgDictNode;
import com.oracle.graal.python.builtins.modules.ctypes.memory.Pointer;
import com.oracle.graal.python.builtins.modules.ctypes.memory.PointerNodes;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBaseClassNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.InlinedIsSameTypeNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.WriteUnraisableNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;

public final class CThunkObject extends PythonBuiltinObject {

    Object pcl_write; // ffi_closure /* the C callable, writeable */
    Object pcl_exec; /* the C callable, executable */
    Object cif; // ffi_cif
    int flags;
    Object[] converters;
    Object callable;
    Object restype;
    FieldSet setfunc;
    FFIType ffi_restype;
    FFIType[] atypes;

    public CThunkObject(Object cls, Shape instanceShape, int nArgs) {
        super(cls, instanceShape);
        atypes = new FFIType[nArgs];
    }

    @ExportLibrary(InteropLibrary.class)
    protected static class CtypeCallback implements TruffleObject {
        private final CThunkObject thunk;

        public CtypeCallback(CThunkObject thunk) {
            this.thunk = thunk;
        }

        protected static void checkArity(Object[] arguments, int expectedArity) throws ArityException {
            if (arguments.length != expectedArity) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(expectedArity, expectedArity, arguments.length);
            }
        }

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        // void *mem: result storage location which isn't needed for us.
        // corresponds to _CallPythonObject
        @ExportMessage
        Object execute(Object[] pArgs,
                        @CachedLibrary(limit = "2") InteropLibrary lib,
                        @Cached GilNode gil,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Bind("$node") Node inliningTarget,
                        @Cached InlinedIsSameTypeNode isSameTypeNode,
                        @Cached GetBaseClassNode getBaseClassNode,
                        @Cached SetFuncNode setFuncNode,
                        @Cached GetFuncNode getFuncNode,
                        @Cached CallNode callNode,
                        @Cached WriteUnraisableNode writeUnraisableNode,
                        @Cached WarnNode warnNode,
                        @Cached PointerNodes.MemcpyNode memcpyNode,
                        @Cached PRaiseNode raiseNode) throws ArityException {
            Object[] converters = thunk.converters;
            int nArgs = converters.length;
            checkArity(pArgs, nArgs);
            FFIType restype = thunk.ffi_restype;
            FieldSet setfunc = thunk.setfunc;
            Object callable = thunk.callable;
            boolean mustRelease = gil.acquire();
            try {
                Object[] arglist = new Object[nArgs];
                for (int i = 0; i < nArgs; ++i) {
                    Object arg = pArgs[i];
                    if (lib.isPointer(arg)) {
                        try {
                            arg = lib.asPointer(arg);
                        } catch (UnsupportedMessageException e) {
                            throw CompilerDirectives.shouldNotReachHere(e);
                        }
                    }
                    FFIType argType = thunk.atypes[i];
                    Pointer value = Pointer.create(argType, argType.size, arg, 0);
                    StgDictObject dict = pyTypeStgDictNode.execute(converters[i]);
                    if (dict != null &&
                                    dict.getfunc != FieldGet.nil &&
                                    !pyTypeCheck.ctypesSimpleInstance(inliningTarget, converters[i], getBaseClassNode, isSameTypeNode)) {
                        arglist[i] = getFuncNode.execute(dict.getfunc, value, dict.size);
                    } else if (dict != null) {
                        CDataObject obj = (CDataObject) callNode.execute(converters[i]);
                        memcpyNode.execute(inliningTarget, obj.b_ptr, value, dict.size);
                        arglist[i] = obj;
                    } else {
                        throw raiseNode.raise(TypeError, CANNOT_BUILD_PARAMETER);
                        // PrintError("Parsing argument %zd\n", i);
                    }
                }

                Object result = null;
                try {
                    result = callNode.execute(callable, arglist, PKeyword.EMPTY_KEYWORDS);
                } catch (PException e) {
                    writeUnraisableNode.execute(e.getEscapedException(), ON_CALLING_CTYPES_CALLBACK_FUNCTION, callable);
                }

                if (restype.type != FFI_TYPES.FFI_TYPE_VOID && result != null) {
                    assert (setfunc != FieldSet.nil);

                    /*
                     * keep is an object we have to keep alive so that the result stays valid. If
                     * there is no such object, the setfunc will have returned Py_None.
                     *
                     * If there is such an object, we have no choice than to keep it alive forever -
                     * but a refcount and/or memory leak will be the result. EXCEPT when restype is
                     * py_object - Python itself knows how to manage the refcount of these objects.
                     */
                    Object keep = PNone.NONE;
                    try {
                        Pointer mem = Pointer.allocate(restype, restype.size);
                        keep = setFuncNode.execute(null, setfunc, mem, result, 0);
                    } catch (PException e) {
                        /* Could not convert callback result. */
                        writeUnraisableNode.execute(e.getEscapedException(),
                                        ON_CONVERTING_RESULT_OF_CTYPES_CALLBACK_FUNCTION,
                                        callable);
                    }
                    if (keep == PNone.NONE) {
                        /* Nothing to keep */
                    } else if (setfunc != FieldDesc.O.setfunc) {
                        try {
                            warnNode.warnEx(null, RuntimeWarning,
                                            MEMORY_LEAK_IN_CALLBACK_FUNCTION, 1);
                        } catch (PException e) {
                            writeUnraisableNode.execute(e.getEscapedException(),
                                            ON_CONVERTING_RESULT_OF_CTYPES_CALLBACK_FUNCTION,
                                            callable);
                        }
                    }
                } else {
                    return 0;
                }
                return result;
            } finally {
                gil.release(mustRelease);
            }
        }
    }
}
