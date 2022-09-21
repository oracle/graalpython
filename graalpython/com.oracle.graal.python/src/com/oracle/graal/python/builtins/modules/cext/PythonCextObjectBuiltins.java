/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.nodes.ErrorMessages.UNHASHABLE_TYPE_P;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___BYTES__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.BytesNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.IsInstanceNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.IsSubClassNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CastArgsNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CastKwargsNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBytesBuiltins.PyBytesFromObjectNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext.LLVMType;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiGuards;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.GetLLVMType;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PRaiseNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.AsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.AsPythonObjectBaseNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.ResolveHandleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.GetRefCntNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeReferenceCacheFactory.ResolveNativeReferenceNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins.GetAttributeNode;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins.SetattrNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyObjectAsFileDescriptor;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectDelItem;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsObjectNode;
import com.oracle.graal.python.lib.PyObjectSetItem;
import com.oracle.graal.python.lib.PyObjectStrAsObjectNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetAnyAttributeNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import java.io.PrintWriter;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendsModule = PythonCextBuiltins.PYTHON_CEXT)
@GenerateNodeFactory
public class PythonCextObjectBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PythonCextObjectBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    // directly called without landing function
    @Builtin(name = "PyObject_Call", parameterNames = {"callee", "args", "kwargs", "single_arg"})
    @GenerateNodeFactory
    abstract static class PyObjectCallNode extends PythonQuaternaryBuiltinNode {
        @Specialization
        static Object doGeneric(VirtualFrame frame, Object callableObj, Object argsObj, Object kwargsObj, int singleArg,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Cached CastArgsNode castArgsNode,
                        @Cached CastKwargsNode castKwargsNode,
                        @Cached CallNode callNode,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached CExtNodes.ToSulongNode nullToSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {

            try {
                Object callable = asPythonObjectNode.execute(callableObj);
                Object[] args;
                if (singleArg != 0) {
                    args = new Object[]{asPythonObjectNode.execute(argsObj)};
                } else {
                    args = castArgsNode.execute(frame, argsObj);
                }
                PKeyword[] keywords = castKwargsNode.execute(kwargsObj);
                return toNewRefNode.execute(callNode.execute(frame, callable, args, keywords));
            } catch (PException e) {
                // transformExceptionToNativeNode acts as a branch profile
                transformExceptionToNativeNode.execute(frame, e);
                return nullToSulongNode.execute(PythonContext.get(nullToSulongNode).getNativeNull());
            }
        }
    }

    // directly called without landing function
    @Builtin(name = "PyObject_CallFunctionObjArgs", parameterNames = {"callable", "va_list"})
    @GenerateNodeFactory
    abstract static class PyObjectCallFunctionObjArgsNode extends PythonBinaryBuiltinNode {

        @Specialization(limit = "1")
        static Object doFunction(VirtualFrame frame, Object callableObj, Object vaList,
                        @CachedLibrary("vaList") InteropLibrary argsArrayLib,
                        @Shared("argLib") @CachedLibrary(limit = "2") InteropLibrary argLib,
                        @Cached CallNode callNode,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Cached CExtNodes.ToJavaNode toJavaNode,
                        @Cached GetLLVMType getLLVMType,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached CExtNodes.ToSulongNode nullToSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object callable = asPythonObjectNode.execute(callableObj);
                return toNewRefNode.execute(callFunction(frame, callable, vaList, argsArrayLib, argLib, callNode, toJavaNode, getLLVMType));
            } catch (PException e) {
                // transformExceptionToNativeNode acts as a branch profile
                transformExceptionToNativeNode.execute(frame, e);
                return nullToSulongNode.execute(PythonContext.get(nullToSulongNode).getNativeNull());
            }
        }

        static Object callFunction(VirtualFrame frame, Object callable, Object vaList,
                        InteropLibrary argsArrayLib,
                        InteropLibrary argLib,
                        CallNode callNode,
                        CExtNodes.ToJavaNode toJavaNode,
                        GetLLVMType getLLVMType) {
            if (argsArrayLib.hasArrayElements(vaList)) {
                try {
                    /*
                     * Function 'PyObject_CallFunctionObjArgs' expects a va_list that contains just
                     * 'PyObject *' and is terminated by 'NULL'. Hence, we allocate an argument
                     * array with one element less than the va_list object says (since the last
                     * element is expected to be 'NULL'; this is best effort). However, we must also
                     * stop at the first 'NULL' element we encounter since a user could pass several
                     * 'NULL'.
                     */
                    long arraySize = argsArrayLib.getArraySize(vaList);
                    Object[] args = new Object[PInt.intValueExact(arraySize) - 1];
                    int filled = 0;
                    Object llvmPyObjectPtrType = getLLVMType.execute(LLVMType.PyObject_ptr_t);
                    for (int i = 0; i < args.length; i++) {
                        try {
                            Object object = argsArrayLib.invokeMember(vaList, "get", i, llvmPyObjectPtrType);
                            if (argLib.isNull(object)) {
                                break;
                            }
                            args[i] = toJavaNode.execute(object);
                            filled++;
                        } catch (ArityException | UnknownIdentifierException | UnsupportedTypeException e) {
                            throw CompilerDirectives.shouldNotReachHere();
                        }
                    }
                    if (filled < args.length) {
                        args = PythonUtils.arrayCopyOf(args, filled);
                    }
                    return callNode.execute(frame, callable, args);
                } catch (UnsupportedMessageException | OverflowException e) {
                    // I think we can just assume that there won't be more than
                    // Integer.MAX_VALUE arguments.
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    // directly called without landing function
    @Builtin(name = "PyObject_CallMethodObjArgs", parameterNames = {"receiver", "method_name", "va_list"})
    @GenerateNodeFactory
    abstract static class PyObjectCallMethodObjArgsNode extends PythonTernaryBuiltinNode {

        @Specialization(limit = "1")
        static Object doMethod(VirtualFrame frame, Object receiverObj, Object methodNameObj, Object vaList,
                        @CachedLibrary("vaList") InteropLibrary argsArrayLib,
                        @Shared("argLib") @CachedLibrary(limit = "2") InteropLibrary argLib,
                        @Cached CallNode callNode,
                        @Cached GetAnyAttributeNode getAnyAttributeNode,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Cached CExtNodes.ToJavaNode toJavaNode,
                        @Cached GetLLVMType getLLVMType,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached CExtNodes.ToSulongNode nullToSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {

            try {
                Object receiver = asPythonObjectNode.execute(receiverObj);
                Object methodName = asPythonObjectNode.execute(methodNameObj);
                Object method = getAnyAttributeNode.executeObject(frame, receiver, methodName);
                return toNewRefNode.execute(PyObjectCallFunctionObjArgsNode.callFunction(frame, method, vaList, argsArrayLib, argLib, callNode, toJavaNode, getLLVMType));
            } catch (PException e) {
                // transformExceptionToNativeNode acts as a branch profile
                transformExceptionToNativeNode.execute(frame, e);
                return nullToSulongNode.execute(PythonContext.get(nullToSulongNode).getNativeNull());
            }
        }
    }

    // directly called without landing function
    @Builtin(name = "PyObject_CallMethod", parameterNames = {"object", "method_name", "args", "single_arg"})
    @GenerateNodeFactory
    abstract static class PyObjectCallMethodNode extends PythonQuaternaryBuiltinNode {
        @Specialization
        static Object doGeneric(VirtualFrame frame, Object receiverObj, TruffleString methodName, Object argsObj, int singleArg,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Cached CastArgsNode castArgsNode,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached CExtNodes.ToSulongNode nullToSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {

            try {
                Object receiver = asPythonObjectNode.execute(receiverObj);
                Object[] args;
                if (singleArg != 0) {
                    args = new Object[]{asPythonObjectNode.execute(argsObj)};
                } else {
                    args = castArgsNode.execute(frame, argsObj);
                }
                return toNewRefNode.execute(callMethod.execute(frame, receiver, methodName, args));
            } catch (PException e) {
                // transformExceptionToNativeNode acts as a branch profile
                transformExceptionToNativeNode.execute(frame, e);
                return nullToSulongNode.execute(PythonContext.get(nullToSulongNode).getNativeNull());
            }
        }
    }

    // directly called without landing function
    @Builtin(name = "PyObject_FastCallDict", parameterNames = {"callable", "argsArray", "kwargs"})
    @GenerateNodeFactory
    abstract static class PyObjectFastCallDictNode extends PythonTernaryBuiltinNode {

        @Specialization(limit = "1")
        static Object doGeneric(VirtualFrame frame, Object callableObj, Object argsArray, Object kwargsObj,
                        @CachedLibrary("argsArray") InteropLibrary argsArrayLib,
                        @Cached CExtNodes.ToJavaNode toJavaNode,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Cached CastKwargsNode castKwargsNode,
                        @Cached CallNode callNode,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached CExtNodes.ToSulongNode nullToSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            if (argsArrayLib.hasArrayElements(argsArray)) {
                try {
                    try {
                        // consume all arguments of the va_list
                        long arraySize = argsArrayLib.getArraySize(argsArray);
                        Object[] args = new Object[PInt.intValueExact(arraySize)];
                        for (int i = 0; i < args.length; i++) {
                            try {
                                args[i] = toJavaNode.execute(argsArrayLib.readArrayElement(argsArray, i));
                            } catch (InvalidArrayIndexException e) {
                                throw CompilerDirectives.shouldNotReachHere();
                            }
                        }
                        Object callable = asPythonObjectNode.execute(callableObj);
                        PKeyword[] keywords = castKwargsNode.execute(kwargsObj);
                        return toNewRefNode.execute(callNode.execute(frame, callable, args, keywords));
                    } catch (UnsupportedMessageException | OverflowException e) {
                        // I think we can just assume that there won't be more than
                        // Integer.MAX_VALUE arguments.
                        throw CompilerDirectives.shouldNotReachHere();
                    }
                } catch (PException e) {
                    // transformExceptionToNativeNode acts as a branch profile
                    transformExceptionToNativeNode.execute(frame, e);
                    return nullToSulongNode.execute(PythonContext.get(nullToSulongNode).getNativeNull());
                }
            }
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @Builtin(name = "PyObject_Str", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyObjectStrNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doGeneric(VirtualFrame frame, Object obj,
                        @Cached PyObjectStrAsObjectNode strNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return strNode.execute(frame, obj);
            } catch (PException e) {
                // transformExceptionToNativeNode acts as a branch profile
                transformExceptionToNativeNode.execute(frame, e);
                return getContext().getNativeNull();
            }
        }
    }

    @Builtin(name = "PyObject_Repr", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyObjectReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doGeneric(VirtualFrame frame, Object obj,
                        @Cached PyObjectReprAsObjectNode reprNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return reprNode.execute(frame, obj);
            } catch (PException e) {
                // transformExceptionToNativeNode acts as a branch profile
                transformExceptionToNativeNode.execute(frame, e);
                return getContext().getNativeNull();
            }
        }
    }

    @Builtin(name = "PyObject_DelItem", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyObjectDelItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doGeneric(VirtualFrame frame, Object obj, Object k,
                        @Cached PyObjectDelItem delNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                delNode.execute(frame, obj, k);
                return 0;
            } catch (PException e) {
                // transformExceptionToNativeNode acts as a branch profile
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
        }
    }

    @Builtin(name = "PyObject_SetItem", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyObjectSetItemNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object doGeneric(VirtualFrame frame, Object obj, Object k, Object v,
                        @Cached PyObjectSetItem setItemNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                setItemNode.execute(frame, obj, k, v);
                return 0;
            } catch (PException e) {
                // transformExceptionToNativeNode acts as a branch profile
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
        }
    }

    @Builtin(name = "PyObject_IsInstance", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyObjectIsInstanceNode extends PythonBinaryBuiltinNode {
        @Specialization
        static int doGeneric(VirtualFrame frame, Object obj, Object typ,
                        @Cached IsInstanceNode isInstanceNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            return ((boolean) isInstanceNode.execute(frame, obj, typ)) ? 1 : 0;
        }
    }

    @Builtin(name = "PyObject_IsSubclass", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyObjectIsSubclassNode extends PythonBinaryBuiltinNode {
        @Specialization
        static int doGeneric(VirtualFrame frame, Object obj, Object typ,
                        @Cached IsSubClassNode isSubclassNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            return ((boolean) isSubclassNode.execute(frame, obj, typ)) ? 1 : 0;
        }
    }

    @Builtin(name = "PyObject_RichCompare", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyObjectRichCompareNode extends PythonTernaryBuiltinNode {

        @Specialization(guards = "op == 0")
        Object op0(VirtualFrame frame, Object a, Object b, @SuppressWarnings("unused") int op,
                        @Cached BinaryComparisonNode.LtNode compNode,
                        @Shared("e") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return compNode.executeObject(frame, a, b);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = "op == 1")
        Object op1(VirtualFrame frame, Object a, Object b, @SuppressWarnings("unused") int op,
                        @Cached BinaryComparisonNode.LeNode compNode,
                        @Shared("e") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return compNode.executeObject(frame, a, b);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = "op == 2")
        Object op2(VirtualFrame frame, Object a, Object b, @SuppressWarnings("unused") int op,
                        @Cached BinaryComparisonNode.EqNode compNode,
                        @Shared("e") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return compNode.executeObject(frame, a, b);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = "op == 3")
        Object op3(VirtualFrame frame, Object a, Object b, @SuppressWarnings("unused") int op,
                        @Cached BinaryComparisonNode.NeNode compNode,
                        @Shared("e") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return compNode.executeObject(frame, a, b);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = "op == 4")
        Object op4(VirtualFrame frame, Object a, Object b, @SuppressWarnings("unused") int op,
                        @Cached BinaryComparisonNode.GtNode compNode,
                        @Shared("e") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return compNode.executeObject(frame, a, b);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = "op == 5")
        Object op5(VirtualFrame frame, Object a, Object b, @SuppressWarnings("unused") int op,
                        @Cached BinaryComparisonNode.GeNode compNode,
                        @Shared("e") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return compNode.executeObject(frame, a, b);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return getContext().getNativeNull();
            }
        }
    }

    @Builtin(name = "PyObject_AsFileDescriptor", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyObjectAsFileDescriptorNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object asFileDescriptor(VirtualFrame frame, Object obj,
                        @Cached PyObjectAsFileDescriptor asFileDescriptorNode) {
            return asFileDescriptorNode.execute(frame, obj);
        }
    }

    @Builtin(name = "PyObject_GenericGetAttr", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyObjectGenericGetAttrNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object getAttr(VirtualFrame frame, Object obj, Object attr,
                        @Cached GetAttributeNode getAttrNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return getAttrNode.execute(frame, obj, attr);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return getContext().getNativeNull();
            }
        }
    }

    @Builtin(name = "PyObject_GenericSetAttr", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyObjectGenericSetAttrNode extends PythonTernaryBuiltinNode {
        @Specialization
        static int setAttr(VirtualFrame frame, Object obj, Object attr, Object value,
                        @Cached SetattrNode setAttrNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                setAttrNode.execute(frame, obj, attr, value);
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
        }
    }

    @Builtin(name = "PyObject_HasAttr", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyObjectHasAttrNode extends PythonBinaryBuiltinNode {
        @Specialization
        static int hasAttr(VirtualFrame frame, Object obj, Object attr,
                        @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached BranchProfile exceptioBranchProfile) {
            try {
                return lookupAttrNode.execute(frame, obj, attr) != PNone.NO_VALUE ? 1 : 0;
            } catch (PException e) {
                exceptioBranchProfile.enter();
                return 0;
            }
        }
    }

    @Builtin(name = "PyObject_HashNotImplemented", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyObjectHashNotImplementedNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object unhashable(VirtualFrame frame, Object obj,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, TypeError, UNHASHABLE_TYPE_P, obj);
        }
    }

    @Builtin(name = "PyObject_IsTrue", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyObjectIsTrueNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int isTrue(VirtualFrame frame, Object obj,
                        @Cached com.oracle.graal.python.lib.PyObjectIsTrueNode isTrueNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return isTrueNode.execute(frame, obj) ? 1 : 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
        }
    }

    @Builtin(name = "PyObject_Bytes", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyObjectBytesNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object bytes(PBytesLike bytes) {
            return bytes;
        }

        @Specialization(guards = {"!isBytes(bytes)", "isBytesSubtype(frame, bytes, getClassNode, isSubtypeNode)"})
        static Object bytes(VirtualFrame frame, Object bytes,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            return bytes;
        }

        @Specialization(guards = {"!isBytes(obj)", "!isBytesSubtype(frame, obj, getClassNode, isSubtypeNode)", "hasBytes(frame, obj, lookupAttrNode)"}, limit = "1")
        Object bytes(VirtualFrame frame, Object obj,
                        @Shared("getClass") @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @Shared("isSubtype") @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached BytesNode bytesNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return bytesNode.execute(frame, PythonBuiltinClassType.PBytes, obj, PNone.NO_VALUE, PNone.NO_VALUE);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = {"!isBytes(obj)", "!isBytesSubtype(frame, obj, getClassNode, isSubtypeNode)", "!hasBytes(frame, obj, lookupAttrNode)"}, limit = "1")
        static Object bytes(VirtualFrame frame, Object obj,
                        @Shared("getClass") @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @Shared("isSubtype") @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached PyBytesFromObjectNode fromObjectNode) {
            return fromObjectNode.execute(frame, obj);
        }

        protected static boolean hasBytes(VirtualFrame frame, Object obj, PyObjectLookupAttr lookupAttrNode) {
            return lookupAttrNode.execute(frame, obj, T___BYTES__) != PNone.NO_VALUE;
        }

        protected static boolean isBytesSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PBytes);
        }
    }

    @Builtin(name = "Py_NotImplemented")
    @GenerateNodeFactory
    public abstract static class PyNotImplementedNode extends PythonBuiltinNode {
        @Specialization
        static Object run() {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = "Py_DECREF", minNumOfPositionalArgs = 1)
    @Builtin(name = "Py_INCREF", minNumOfPositionalArgs = 1)
    @Builtin(name = "Py_XINCREF", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyChangeREFNode extends PythonUnaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public static Object values(Object obj) {
            // pass
            return PNone.NONE;
        }
    }

    @Builtin(name = "Py_NoValue")
    @GenerateNodeFactory
    abstract static class PyNoValue extends PythonBuiltinNode {
        @Specialization
        static PNone doNoValue() {
            return PNone.NO_VALUE;
        }
    }

    @Builtin(name = "Py_None")
    @GenerateNodeFactory
    abstract static class PyNoneNode extends PythonBuiltinNode {
        @Specialization
        static PNone doNativeNone() {
            return PNone.NONE;
        }
    }

    // directly called without landing function
    @Builtin(name = "_PyObject_Dump", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyObjectDump extends PythonUnaryBuiltinNode {

        @Specialization
        @CompilerDirectives.TruffleBoundary
        int doGeneric(Object ptrObject) {
            PythonContext context = getContext();
            PrintWriter stderr = new PrintWriter(context.getStandardErr());
            CApiContext cApiContext = context.getCApiContext();
            InteropLibrary lib = InteropLibrary.getUncached(ptrObject);
            CExtNodes.PCallCapiFunction callNode = CExtNodes.PCallCapiFunction.getUncached();

            // There are three cases we need to distinguish:
            // 1) The pointer object is a native pointer and is NOT a handle
            // 2) The pointer object is a native pointer and is a handle
            // 3) The pointer object is one of our native wrappers

            boolean isWrapper = CApiGuards.isNativeWrapper(ptrObject);
            boolean pointsToHandleSpace = !isWrapper && (boolean) callNode.call(NativeCAPISymbol.FUN_POINTS_TO_HANDLE_SPACE, ptrObject);
            boolean isValidHandle = pointsToHandleSpace && (boolean) callNode.call(NativeCAPISymbol.FUN_IS_HANDLE, ptrObject);

            /*
             * If the pointer points to the handle space but it's not a valid handle or if we do
             * memory tracing and we know that the pointer is not allocated (was free'd), we assumed
             * it's a use-after-free.
             */
            boolean traceNativeMemory = context.getOption(PythonOptions.TraceNativeMemory);
            if (pointsToHandleSpace && !isValidHandle || traceNativeMemory && !isWrapper && !cApiContext.isAllocated(ptrObject)) {
                stderr.println(PythonUtils.formatJString("<object at %s is freed>", CApiContext.asPointer(ptrObject, lib)));
                stderr.flush();
                return 0;
            }

            /*
             * At this point we don't know if the pointer is invalid, so we try to resolve it to an
             * object.
             */
            Object resolved = isWrapper ? ptrObject : ResolveHandleNodeGen.getUncached().execute(ptrObject);
            Object pythonObject;
            long refCnt;
            // We need again check if 'resolved' is a wrapper in case we resolved a handle.
            if (CApiGuards.isNativeWrapper(resolved)) {
                PythonNativeWrapper wrapper = (PythonNativeWrapper) resolved;
                refCnt = wrapper.getRefCount();
                pythonObject = AsPythonObjectBaseNodeGen.getUncached().execute(wrapper);
            } else {
                long obRefCnt = GetRefCntNodeGen.getUncached().execute(cApiContext, ptrObject);
                /*
                 * The upper 32-bits of the native field 'ob_refcnt' encode an ID into the native
                 * object reference table. We mask them out to get the real reference count.
                 */
                refCnt = obRefCnt & (CApiContext.REFERENCE_COUNT_MARKER - 1);
                pythonObject = AsPythonObjectNodeGen.getUncached().execute(ResolveNativeReferenceNodeGen.getUncached().execute(resolved, obRefCnt, false));
            }

            // first, write fields which are the least likely to crash
            stderr.println("ptrObject address  : " + ptrObject);
            stderr.println("ptrObject refcount : " + refCnt);
            stderr.flush();

            Object type = GetClassNode.getUncached().execute(pythonObject);
            stderr.println("object type     : " + type);
            stderr.println("object type name: " + TypeNodes.GetNameNode.getUncached().execute(type));

            // the most dangerous part
            stderr.println("object repr     : ");
            stderr.flush();
            try {
                Object reprObj = PyObjectCallMethodObjArgs.getUncached().execute(null, context.getBuiltins(), BuiltinNames.T_REPR, pythonObject);
                stderr.println(CastToJavaStringNode.getUncached().execute(reprObj));
            } catch (PException | CannotCastException e) {
                // errors are ignored at this point
            }
            stderr.flush();
            return 0;
        }
    }
}
