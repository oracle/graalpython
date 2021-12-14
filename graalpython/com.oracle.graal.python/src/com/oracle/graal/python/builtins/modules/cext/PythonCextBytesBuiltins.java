/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P;
import static com.oracle.graal.python.nodes.ErrorMessages.CANNOT_CONVERT_P_OBJ_TO_S;
import static com.oracle.graal.python.nodes.ErrorMessages.NATIVE_S_SUBTYPES_NOT_IMPLEMENTED;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import java.util.List;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.BytesNode;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.StrNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.GetNativeNullNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PRaiseNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.EncodeNode;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.ModNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(defineModule = PythonCextBytesBuiltins.PYTHON_CEXT_BYTES)
@GenerateNodeFactory
public class PythonCextBytesBuiltins extends PythonBuiltins {

    public static final String PYTHON_CEXT_BYTES = "python_cext_bytes";

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PythonCextBytesBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }
    
    @Builtin(name = "PyBytes_Size", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyBytesSizeNode extends PythonUnaryBuiltinNode {
        @Specialization
        public int size(VirtualFrame frame, PBytes obj,
                        @Cached PyObjectSizeNode sizeNode) {
            return sizeNode.execute(frame, obj);
        }

        @Specialization(guards = {"!isPBytes(obj)", "isBytesSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public int sizeNative(VirtualFrame frame, @SuppressWarnings("unused") Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, PythonBuiltinClassType.NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "bytes");
        }

        @Specialization(guards = {"!isPBytes(obj)", "!isBytesSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public int size(VirtualFrame frame, Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached StrNode strNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, obj), obj);
        }

        protected boolean isBytesSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PBytes);
        }
    }

    @Builtin(name = "PyBytes_Check", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyBytesCheckNode extends PythonUnaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object check(PBytes obj) {
            return true;
        }

        @Specialization(guards = "!isPBytes(obj)")
        public Object check(VirtualFrame frame, Object obj,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PBytes);
        }
    }

    @Builtin(name = "PyBytes_Concat", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PyBytesConcatNode extends PythonBinaryBuiltinNode {
        @Specialization
        public Object concat(VirtualFrame frame, PBytes original, Object newPart,
                        @Cached BytesBuiltins.AddNode addNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return addNode.execute(frame, original, newPart);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }

        @Specialization(guards = {"!isPBytes(original)", "isBytesSubtype(frame, original, getClassNode, isSubtypeNode)"})
        public Object concatNative(VirtualFrame frame, @SuppressWarnings("unused") Object original, @SuppressWarnings("unused") Object newPart,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached GetNativeNullNode getNativeNullNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getNativeNullNode.execute(), PythonBuiltinClassType.NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "bytes");
        }

        @Specialization(guards = {"!isPBytes(original)", "!isBytesSubtype(frame, original, getClassNode, isSubtypeNode)"})
        public Object concat(VirtualFrame frame, Object original, @SuppressWarnings("unused") Object newPart,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached StrNode strNode,
                        @Cached GetNativeNullNode getNativeNullNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getNativeNullNode.execute(), SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, original), original);
        }

        protected boolean isBytesSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PBytes);
        }
    }

    @Builtin(name = "PyBytes_Join", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PyBytesJoinNode extends PythonBinaryBuiltinNode {
        @Specialization
        public Object join(VirtualFrame frame, PBytes original, Object newPart,
                        @Cached BytesBuiltins.JoinNode joinNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return joinNode.execute(frame, original, newPart);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }

        @Specialization(guards = {"!isPBytes(original)", "isBytesSubtype(frame, original, getClassNode, isSubtypeNode)"})
        public Object joinNative(VirtualFrame frame, @SuppressWarnings("unused") Object original, @SuppressWarnings("unused") Object newPart,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            return raiseNativeNode.raise(frame, getNativeNullNode.execute(), PythonBuiltinClassType.NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "bytes");
        }

        @Specialization(guards = {"!isPBytes(original)", "!isBytesSubtype(frame, original, getClassNode, isSubtypeNode)"})
        public Object join(VirtualFrame frame, @SuppressWarnings("unused") Object original, @SuppressWarnings("unused") Object newPart,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached StrNode strNode,
                        @Cached PRaiseNativeNode raiseNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            return raiseNativeNode.raise(frame, getNativeNullNode.execute(), SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, original), original);
        }

        protected boolean isBytesSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PBytes);
        }
    }

    @Builtin(name = "PyBytes_FromFormat", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PyBytesFromFormatNode extends PythonBinaryBuiltinNode {
        @Specialization
        public Object fromFormat(VirtualFrame frame, String fmt, Object args,
                        @Cached ModNode modeNode,
                        @Cached EncodeNode encodeNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                Object formated = modeNode.execute(frame, fmt, args);
                return encodeNode.execute(frame, formated, PNone.NONE, PNone.NONE);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }
    }

    @Builtin(name = "PyBytes_FromObject", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyBytesFromObjectNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = {"isPBytes(obj) || isBytesSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object fromObject(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            return obj;
        }

        @Specialization(guards = {"!isPBytes(obj)", "!isBytesSubtype(frame, obj, getClassNode, isSubtypeNode)", "isAcceptedSubtype(frame, obj, getClassNode, isSubtypeNode, lookupAttrNode)"})
        public Object fromObject(VirtualFrame frame, Object obj,
                        @Cached BytesNode bytesNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @SuppressWarnings("unused") @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return bytesNode.execute(frame, PythonBuiltinClassType.PBytes, obj, PNone.NO_VALUE, PNone.NO_VALUE);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }

        @Specialization(guards = {"!isPBytes(obj)", "!isBytesSubtype(frame, obj, getClassNode, isSubtypeNode)", "!isAcceptedSubtype(frame, obj, getClassNode, isSubtypeNode, lookupAttrNode)"})
        public Object fromObject(VirtualFrame frame, Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @SuppressWarnings("unused") @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached PRaiseNativeNode raiseNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            return raiseNativeNode.raise(frame, getNativeNullNode.execute(), TypeError, CANNOT_CONVERT_P_OBJ_TO_S, obj, "bytes");
        }

        protected boolean isBytesSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PBytes);
        }

        protected boolean isAcceptedSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode, PyObjectLookupAttr lookupAttrNode) {
            Object klass = getClassNode.execute(obj);
            return isSubtypeNode.execute(frame, klass, PythonBuiltinClassType.PList) ||
                            isSubtypeNode.execute(frame, klass, PythonBuiltinClassType.PTuple) ||
                            isSubtypeNode.execute(frame, klass, PythonBuiltinClassType.PMemoryView) ||
                            (!isSubtypeNode.execute(frame, klass, PythonBuiltinClassType.PString) && lookupAttrNode.execute(frame, obj, __ITER__) != PNone.NO_VALUE);
        }
    }
}
