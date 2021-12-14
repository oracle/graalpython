/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.ErrorMessages.P_OBJ_DOES_NOT_SUPPORT_ITEM_ASSIGMENT;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;
import java.util.List;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.TupleNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.GetNativeNullNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PRaiseNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyObjectDelItem;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PySequenceCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.builtins.ListNodes.ConstructListNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.MulNode;
import com.oracle.graal.python.nodes.expression.ContainsNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(defineModule = PythonCextSequenceBuiltins.PYTHON_CEXT_SEQUENCE)
@GenerateNodeFactory
public class PythonCextSequenceBuiltins extends PythonBuiltins {

    public static final String PYTHON_CEXT_SEQUENCE = "python_cext_sequence";

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PythonCextSequenceBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    @Builtin(name = "PySequence_Tuple", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PySequenceTupleNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "isTuple(obj, getClassNode)")
        public PTuple values(PTuple obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode) {
            return obj;
        }

        @Specialization(guards = "!isTuple(obj, getClassNode)")
        public Object values(VirtualFrame frame, Object obj,
                        @Cached TupleNode tupleNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return tupleNode.execute(frame, PythonBuiltinClassType.PTuple, obj);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }

        protected boolean isTuple(Object obj, GetClassNode getClassNode) {
            return getClassNode.execute(obj) == PythonBuiltinClassType.PTuple;
        }
    }

    @Builtin(name = "PySequence_List", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PySequenceListNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object values(VirtualFrame frame, Object obj,
                        @Cached ConstructListNode listNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return listNode.execute(frame, obj);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }
    }

    @Builtin(name = "PySequence_SetItem", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class PySequenceSetItemNode extends PythonTernaryBuiltinNode {
        @Specialization(guards = "checkNode.execute(obj)")
        public Object setItem(VirtualFrame frame, Object obj, Object key, Object value,
                        @SuppressWarnings("unused") @Cached PySequenceCheckNode checkNode,
                        @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached ConditionProfile hasSetItem,
                        @Cached CallNode callNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object setItemCallable = lookupAttrNode.execute(frame, obj, __SETITEM__);
                if (hasSetItem.profile(setItemCallable == PNone.NO_VALUE)) {
                    throw raise(TypeError, P_OBJ_DOES_NOT_SUPPORT_ITEM_ASSIGMENT, obj);
                } else {
                    callNode.execute(setItemCallable, key, value);
                    return 0;
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = "!checkNode.execute(obj)")
        Object setItem(VirtualFrame frame, Object obj, @SuppressWarnings("unused") Object key, @SuppressWarnings("unused") Object value,
                        @SuppressWarnings("unused") @Cached PySequenceCheckNode checkNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, TypeError, ErrorMessages.IS_NOT_A_SEQUENCE, obj);
        }
    }

    @Builtin(name = "PySequence_GetSlice", minNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonTypes.class)
    @GenerateNodeFactory
    abstract static class PySequenceGetSliceNode extends PythonTernaryBuiltinNode {

        @Specialization(guards = "checkNode.execute(obj)")
        Object getSlice(VirtualFrame frame, Object obj, long iLow, long iHigh,
                        @SuppressWarnings("unused") @Cached PySequenceCheckNode checkNode,
                        @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached SliceLiteralNode sliceNode,
                        @Cached CallNode callNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                Object getItemCallable = lookupAttrNode.execute(frame, obj, __GETITEM__);
                return callNode.execute(getItemCallable, sliceNode.execute(frame, iLow, iHigh, PNone.NONE));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }

        @Specialization(guards = "!checkNode.execute(obj)")
        Object getSlice(VirtualFrame frame, Object obj, @SuppressWarnings("unused") Object key, @SuppressWarnings("unused") Object value,
                        @SuppressWarnings("unused") @Cached PySequenceCheckNode checkNode,
                        @Cached PRaiseNativeNode raiseNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            return raiseNativeNode.raise(frame, getNativeNullNode.execute(), TypeError, ErrorMessages.OBJ_IS_UNSLICEABLE, obj);
        }
    }

    @Builtin(name = "PySequence_Contains", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PySequenceContainsNode extends PythonBinaryBuiltinNode {

        @Specialization
        Object contains(VirtualFrame frame, Object haystack, Object needle,
                        @Cached ContainsNode containsNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return containsNode.executeObject(frame, needle, haystack);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }
    }

    @Builtin(name = "PySequence_Repeat", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PySequenceRepeatNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "checkNode.execute(obj)")
        Object repeat(VirtualFrame frame, Object obj, long n,
                        @SuppressWarnings("unused") @Cached PySequenceCheckNode checkNode,
                        @Cached("createMul()") MulNode mulNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return mulNode.executeObject(frame, obj, n);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }

        @Specialization(guards = "!checkNode.execute(obj)")
        protected Object repeat(VirtualFrame frame, Object obj, @SuppressWarnings("unused") Object n,
                        @SuppressWarnings("unused") @Cached PySequenceCheckNode checkNode,
                        @Cached PRaiseNativeNode raiseNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            return raiseNativeNode.raise(frame, getNativeNullNode.execute(), TypeError, ErrorMessages.OBJ_CANT_BE_REPEATED, obj);
        }

        protected MulNode createMul() {
            return (MulNode) BinaryArithmetic.Mul.create();
        }
    }

    @Builtin(name = "PySequence_InPlaceRepeat", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PySequenceInPlaceRepeatNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = {"checkNode.execute(obj)"})
        Object repeat(VirtualFrame frame, Object obj, long n,
                        @Cached PyObjectLookupAttr lookupNode,
                        @Cached CallNode callNode,
                        @Cached("createMul()") MulNode mulNode,
                        @SuppressWarnings("unused") @Cached PySequenceCheckNode checkNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                Object imulCallable = lookupNode.execute(frame, obj, __IMUL__);
                if (imulCallable != PNone.NO_VALUE) {
                    Object ret = callNode.execute(frame, imulCallable, n);
                    return ret;
                }
                return mulNode.executeObject(frame, obj, n);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }

        @Specialization(guards = "!checkNode.execute(obj)")
        protected Object repeat(VirtualFrame frame, Object obj, @SuppressWarnings("unused") Object n,
                        @SuppressWarnings("unused") @Cached PySequenceCheckNode checkNode,
                        @Cached PRaiseNativeNode raiseNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            return raiseNativeNode.raise(frame, getNativeNullNode.execute(), TypeError, ErrorMessages.OBJ_CANT_BE_REPEATED, obj);
        }

        protected MulNode createMul() {
            return (MulNode) BinaryArithmetic.Mul.create();
        }
    }

    @Builtin(name = "PySequence_Concat", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PySequenceConcatNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = {"checkNode.execute(s1)", "checkNode.execute(s1)"})
        Object concat(VirtualFrame frame, Object s1, Object s2,
                        @SuppressWarnings("unused") @Cached PySequenceCheckNode checkNode,
                        @Cached("createAdd()") BinaryArithmetic.AddNode addNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return addNode.executeObject(frame, s1, s2);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }

        @Specialization(guards = {"!checkNode.execute(s1) || checkNode.execute(s2)"})
        protected Object cantConcat(VirtualFrame frame, Object s1, @SuppressWarnings("unused") Object s2,
                        @SuppressWarnings("unused") @Cached PySequenceCheckNode checkNode,
                        @Cached PRaiseNativeNode raiseNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            return raiseNativeNode.raise(frame, getNativeNullNode.execute(), TypeError, ErrorMessages.OBJ_CANT_BE_CONCATENATED, s1);
        }

        protected BinaryArithmetic.AddNode createAdd() {
            return (BinaryArithmetic.AddNode) BinaryArithmetic.Add.create();
        }
    }

    @Builtin(name = "PySequence_InPlaceConcat", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PySequenceInPlaceConcatNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = {"checkNode.execute(s1)"})
        Object concat(VirtualFrame frame, Object s1, Object s2,
                        @Cached PyObjectLookupAttr lookupNode,
                        @Cached CallNode callNode,
                        @Cached("createAdd()") BinaryArithmetic.AddNode addNode,
                        @SuppressWarnings("unused") @Cached PySequenceCheckNode checkNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                Object iaddCallable = lookupNode.execute(frame, s1, __IADD__);
                if (iaddCallable != PNone.NO_VALUE) {
                    return callNode.execute(frame, iaddCallable, s2);
                }
                return addNode.executeObject(frame, s1, s2);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }

        @Specialization(guards = "!checkNode.execute(s1)")
        protected Object concat(VirtualFrame frame, Object s1, @SuppressWarnings("unused") Object s2,
                        @SuppressWarnings("unused") @Cached PySequenceCheckNode checkNode,
                        @Cached PRaiseNativeNode raiseNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            return raiseNativeNode.raise(frame, getNativeNullNode.execute(), TypeError, ErrorMessages.OBJ_CANT_BE_CONCATENATED, s1);
        }

        protected BinaryArithmetic.AddNode createAdd() {
            return (BinaryArithmetic.AddNode) BinaryArithmetic.Add.create();
        }
    }

    @Builtin(name = "PySequence_DelItem", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PySequence_DelItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object run(VirtualFrame frame, Object o, Object i,
                        @Cached PyObjectDelItem delItemNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                delItemNode.execute(frame, o, i);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
            return 0;
        }
    }

    @Builtin(name = "PySequence_Check", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PySequence_Check extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean check(Object object,
                        @Cached PySequenceCheckNode check) {
            return check.execute(object);
        }
    }

    @Builtin(name = "PySequence_GetItem", minNumOfPositionalArgs = 3, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class PySequenceGetItem extends PythonTernaryBuiltinNode {

        @Specialization
        Object doManaged(VirtualFrame frame, Object module, Object listWrapper, Object position,
                        @Cached PySequenceCheckNode pySequenceCheck,
                        @Cached com.oracle.graal.python.lib.PyObjectGetItem getItem,
                        @Cached AsPythonObjectNode listWrapperAsPythonObjectNode,
                        @Cached AsPythonObjectNode positionAsPythonObjectNode,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached GetNativeNullNode getNativeNullNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object delegate = listWrapperAsPythonObjectNode.execute(listWrapper);
                if (!pySequenceCheck.execute(delegate)) {
                    throw raise(TypeError, ErrorMessages.OBJ_DOES_NOT_SUPPORT_INDEXING, delegate);
                }
                Object item = getItem.execute(frame, delegate, positionAsPythonObjectNode.execute(position));
                return toNewRefNode.execute(item);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return toNewRefNode.execute(getNativeNullNode.execute(module));
            }
        }
    }

}
