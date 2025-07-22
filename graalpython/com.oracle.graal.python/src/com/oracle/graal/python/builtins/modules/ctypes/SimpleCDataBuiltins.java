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
package com.oracle.graal.python.builtins.modules.ctypes;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SimpleCData;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TRUFFLE_CDATA_INIT_BUFFER_PROTOCOL;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import static com.oracle.graal.python.nodes.ErrorMessages.CANT_DELETE_ATTRIBUTE;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.KeepRefNode;
import com.oracle.graal.python.builtins.modules.ctypes.CFieldBuiltins.GetFuncNode;
import com.oracle.graal.python.builtins.modules.ctypes.CFieldBuiltins.SetFuncNode;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.PyTypeCheck;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldGet;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldSet;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyObjectStgDictNode;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyTypeStgDictNode;
import com.oracle.graal.python.builtins.modules.ctypes.memory.PointerNodes;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBaseClassNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotInquiry.NbBoolBuiltinNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = SimpleCData)
public final class SimpleCDataBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = SimpleCDataBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SimpleCDataBuiltinsFactory.getFactories();
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        core.getContext().registerCApiHook(() -> PCallCapiFunction.callUncached(FUN_PY_TRUFFLE_CDATA_INIT_BUFFER_PROTOCOL, PythonToNativeNode.executeUncached(SimpleCData)));
    }

    static void Simple_set_value(VirtualFrame frame, Node inliningTarget, CDataObject self, Object value,
                    PRaiseNode raiseNode,
                    PyObjectStgDictNode pyObjectStgDictNode,
                    SetFuncNode setFuncNode,
                    KeepRefNode keepRefNode) {
        StgDictObject dict = pyObjectStgDictNode.execute(inliningTarget, self);
        if (value == null) {
            throw raiseNode.raise(inliningTarget, TypeError, CANT_DELETE_ATTRIBUTE);
        }
        assert dict != null : "Cannot be NULL for CDataObject instances";
        assert dict.setfunc != FieldSet.nil;
        Object result = setFuncNode.execute(frame, dict.setfunc, self.b_ptr, value, dict.size);

        keepRefNode.execute(frame, inliningTarget, self, 0, result);
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class NewNode extends PythonBuiltinNode {
        @Specialization
        static Object newCData(Object type, @SuppressWarnings("unused") Object[] args, @SuppressWarnings("unused") PKeyword[] kwds,
                        @Bind Node inliningTarget,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached CtypesNodes.GenericPyCDataNewNode pyCDataNewNode,
                        @Cached PRaiseNode raiseNode) {
            StgDictObject dict = pyTypeStgDictNode.checkAbstractClass(inliningTarget, type, raiseNode);
            return pyCDataNewNode.execute(inliningTarget, type, dict);
        }
    }

    @Slot(value = SlotKind.tp_init, isComplex = true)
    @SlotSignature(minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class InitNode extends PythonVarargsBuiltinNode {

        @Specialization
        static Object Simple_init(VirtualFrame frame, CDataObject self, Object[] args, @SuppressWarnings("unused") PKeyword[] kwds,
                        @Bind Node inliningTarget,
                        @Cached SetFuncNode setFuncNode,
                        @Cached KeepRefNode keepRefNode,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached PRaiseNode raiseNode) {
            if (args.length > 0) {
                Simple_set_value(frame, inliningTarget, self, args[0], raiseNode, pyObjectStgDictNode, setFuncNode, keepRefNode);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "value", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "current value")
    @GenerateNodeFactory
    protected abstract static class SimpleValueNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNoValue(value)")
        static Object Simple_get_value(CDataObject self, @SuppressWarnings("unused") PNone value,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached GetFuncNode getFuncNode) {
            StgDictObject dict = pyObjectStgDictNode.execute(inliningTarget, self);
            assert dict != null : "Cannot be NULL for CDataObject instances";
            assert dict.getfunc != FieldGet.nil;
            return getFuncNode.execute(dict.getfunc, self.b_ptr, self.b_size);
        }

        @Specialization
        static Object set_value(VirtualFrame frame, CDataObject self, Object value,
                        @Bind Node inliningTarget,
                        @Cached SetFuncNode setFuncNode,
                        @Cached KeepRefNode keepRefNode,
                        @Exclusive @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached PRaiseNode raiseNode) {
            Simple_set_value(frame, inliningTarget, self, value, raiseNode, pyObjectStgDictNode, setFuncNode, keepRefNode);
            return PNone.NONE;
        }
    }

    @Builtin(name = "__ctypes_from_outparam__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    protected abstract static class CtypesFromOutparamNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object Simple_from_outparm(CDataObject self,
                        @Bind Node inliningTarget,
                        @Cached GetPythonObjectClassNode getClassNode,
                        @Cached IsSameTypeNode isSameTypeNode,
                        @Cached GetBaseClassNode getBaseClassNode,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Exclusive @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached GetFuncNode getFuncNode) {
            if (pyTypeCheck.ctypesSimpleInstance(inliningTarget, getClassNode.execute(inliningTarget, self), getBaseClassNode, isSameTypeNode)) {
                return self;
            }
            /* call stgdict.getfunc */
            StgDictObject dict = pyObjectStgDictNode.execute(inliningTarget, self);
            return getFuncNode.execute(dict.getfunc, self.b_ptr, self.b_size);
        }
    }

    @Slot(SlotKind.nb_bool)
    @GenerateUncached
    @GenerateNodeFactory
    protected abstract static class SimpleBoolNode extends NbBoolBuiltinNode {

        @SuppressWarnings("unused")
        @Specialization
        static boolean Simple_bool(CDataObject self,
                        @Bind Node inliningTarget,
                        @Cached PointerNodes.ReadBytesNode read) {
            if (self.b_ptr.isNull()) {
                return false;
            }
            byte[] bytes = read.execute(inliningTarget, self.b_ptr, self.b_size);
            for (byte b : bytes) {
                if (b != 0) {
                    return true;
                }
            }
            return false;
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        static TruffleString Simple_repr(CDataObject self,
                        @Bind Node inliningTarget,
                        @Cached GetPythonObjectClassNode getClassNode,
                        @Cached IsSameTypeNode isSameTypeNode,
                        @Cached GetNameNode getNameNode,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached GetFuncNode getFuncNode,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            Object clazz = getClassNode.execute(inliningTarget, self);
            if (!isSameTypeNode.execute(inliningTarget, clazz, SimpleCData)) {
                return simpleTruffleStringFormatNode.format("<%s object at %s>", getNameNode.execute(inliningTarget, clazz),
                                getNameNode.execute(inliningTarget, getClassNode.execute(inliningTarget, self)));
            }

            StgDictObject dict = pyObjectStgDictNode.execute(inliningTarget, self);
            TruffleString val = fromJavaStringNode.execute(toStringBoundary(getFuncNode.execute(dict.getfunc, self.b_ptr, self.b_size)), TS_ENCODING);
            return simpleTruffleStringFormatNode.format("%s(%s)", getNameNode.execute(inliningTarget, clazz), val);
        }

        @TruffleBoundary
        private static String toStringBoundary(Object o) {
            return o.toString();
        }
    }
}
