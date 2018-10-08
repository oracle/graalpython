/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.TruffleCextBuiltinsFactory.CheckFunctionResultNodeGen;
import com.oracle.graal.python.builtins.modules.TruffleCextBuiltinsFactory.GetByteArrayNodeGen;
import com.oracle.graal.python.builtins.modules.TruffleCextBuiltinsFactory.TrufflePInt_AsPrimitiveFactory;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.CArrayWrappers.CByteArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.MayRaiseBinaryNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.MayRaiseNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.MayRaiseNodeFactory;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.MayRaiseTernaryNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.MayRaiseUnaryNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.MayRaiseBinaryNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.MayRaiseTernaryNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.MayRaiseUnaryNodeGen;
import com.oracle.graal.python.builtins.objects.cext.HandleCache;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PythonClassInitNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PythonClassNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PythonObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeNull;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.cext.UnicodeObjectNodes.UnicodeAsWideCharNode;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.Arity;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.PythonCallable;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadVarArgsNode;
import com.oracle.graal.python.nodes.argument.ReadVarKeywordsNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.PythonCallNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.ExceptionUtils;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(defineModule = "python_cext")
public class TruffleCextBuiltins extends PythonBuiltins {

    private static final String ERROR_HANDLER = "error_handler";
    private static final String NATIVE_NULL = "native_null";

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TruffleCextBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(PythonCore core) {
        super.initialize(core);
        PythonClass errorHandlerClass = core.factory().createPythonClass(core.lookupType(PythonBuiltinClassType.PythonClass), "CErrorHandler",
                        new PythonClass[]{core.lookupType(PythonBuiltinClassType.PythonObject)});
        builtinConstants.put("CErrorHandler", errorHandlerClass);
        builtinConstants.put(ERROR_HANDLER, core.factory().createPythonObject(errorHandlerClass));
        builtinConstants.put(NATIVE_NULL, new PythonNativeNull());
    }

    /**
     * Called mostly from our C code to convert arguments into a wrapped representation for
     * consumption in Java.
     */
    @Builtin(name = "to_java", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class AsPythonObjectNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object run(Object object,
                        @Cached("create()") CExtNodes.AsPythonObjectNode toJavaNode) {
            return toJavaNode.execute(object);
        }
    }

    /**
     * Called from C when they actually want a {@code const char*} for a Python string
     */
    @Builtin(name = "to_char_pointer", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class TruffleString_AsString extends NativeBuiltin {

        @Specialization(guards = "isString(str)")
        Object run(Object str,
                        @Cached("create()") CExtNodes.AsCharPointer asCharPointerNode) {
            return asCharPointerNode.execute(str);
        }

        @Fallback
        Object run(Object o) {
            return raiseNative(PNone.NO_VALUE, PythonErrorType.SystemError, "Cannot convert object of type %p to C string.", o, o.getClass().getName());
        }
    }

    /**
     * This is used in the ExternalFunctionNode below, so all arguments passed from Python code into
     * a C function are automatically unwrapped if they are wrapped. This function is also called
     * all over the place in C code to make sure return values have the right representation in
     * Sulong land.
     */
    @Builtin(name = "to_sulong", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ToSulongNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object run(Object obj,
                        @Cached("create()") CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(obj);
        }
    }

    @Builtin(name = "PyTruffle_Type", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class PyTruffle_Type extends NativeBuiltin {
        @Specialization
        @TruffleBoundary
        Object doI(String typeName) {
            PythonCore core = getCore();
            for (PythonBuiltinClassType type : PythonBuiltinClassType.VALUES) {
                if (type.getName().equals(typeName)) {
                    return core.lookupType(type);
                }
            }
            Object attribute = core.lookupBuiltinModule("python_cext").getAttribute(typeName);
            if (attribute != PNone.NO_VALUE) {
                return attribute;
            }
            attribute = core.lookupBuiltinModule("builtins").getAttribute(typeName);
            if (attribute != PNone.NO_VALUE) {
                return attribute;
            }
            throw raise(PythonErrorType.KeyError, "'%s'", typeName);
        }
    }

    @Builtin(name = "PyTuple_SetItem", fixedNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyTuple_SetItem extends NativeBuiltin {
        @Specialization
        int doI(PTuple tuple, Object position, Object element,
                        @Cached("createSetItem()") SequenceStorageNodes.SetItemNode setItemNode) {
            setItemNode.execute(tuple.getSequenceStorage(), position, element);
            return 0;
        }

        protected static SequenceStorageNodes.SetItemNode createSetItem() {
            return SequenceStorageNodes.SetItemNode.create(NormalizeIndexNode.forTupleAssign(), "invalid item for assignment");
        }
    }

    @Builtin(name = "CreateBuiltinMethod", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class CreateBuiltinMethodNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object runWithoutCWrapper(PBuiltinFunction descriptor, Object self) {
            return factory().createBuiltinMethod(self, descriptor);
        }
    }

    @Builtin(name = "CreateFunction", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class CreateFunctionNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(cwrapper)")
        @TruffleBoundary
        PBuiltinFunction runWithoutCWrapper(String name, TruffleObject callable, @SuppressWarnings("unused") PNone cwrapper, PythonClass type) {
            CompilerDirectives.transferToInterpreter();
            RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(new ExternalFunctionNode(getRootNode().getLanguage(PythonLanguage.class), name, null, callable));
            return factory().createBuiltinFunction(name, type, createArity(name), callTarget);
        }

        @Specialization(guards = {"isNoValue(cwrapper)", "isNoValue(type)"})
        @TruffleBoundary
        PBuiltinFunction runWithoutCWrapper(String name, TruffleObject callable, @SuppressWarnings("unused") PNone cwrapper, @SuppressWarnings("unused") PNone type) {
            CompilerDirectives.transferToInterpreter();
            RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(new ExternalFunctionNode(getRootNode().getLanguage(PythonLanguage.class), name, null, callable));
            return factory().createBuiltinFunction(name, null, createArity(name), callTarget);
        }

        @Specialization(guards = {"!isNoValue(cwrapper)", "isNoValue(type)"})
        @TruffleBoundary
        PBuiltinFunction runWithoutCWrapper(String name, TruffleObject callable, TruffleObject cwrapper, @SuppressWarnings("unused") PNone type) {
            CompilerDirectives.transferToInterpreter();
            RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(new ExternalFunctionNode(getRootNode().getLanguage(PythonLanguage.class), name, cwrapper, callable));
            return factory().createBuiltinFunction(name, null, createArity(name), callTarget);
        }

        @Specialization(guards = "!isNoValue(cwrapper)")
        @TruffleBoundary
        PBuiltinFunction run(String name, TruffleObject callable, TruffleObject cwrapper, PythonClass type) {
            CompilerDirectives.transferToInterpreter();
            RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(new ExternalFunctionNode(getRootNode().getLanguage(PythonLanguage.class), name, cwrapper, callable));
            return factory().createBuiltinFunction(name, type, createArity(name), callTarget);
        }

        private static Arity createArity(String name) {
            return Arity.createVarArgsAndKwArgsOnly(name);
        }
    }

    @Builtin(name = "PyErr_Restore", fixedNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyErrRestoreNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object run(PNone typ, PNone val, PNone tb) {
            getContext().setCurrentException(null);
            return PNone.NONE;
        }

        @Specialization
        Object run(@SuppressWarnings("unused") PythonClass typ, PBaseException val, @SuppressWarnings("unused") PTraceback tb) {
            val.reifyException();
            if (val.getException() != null) {
                getContext().setCurrentException(val.getException());
            } else {
                PException pException = PException.fromObject(val, this);
                getContext().setCurrentException(pException);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "PyErr_Occurred", minNumOfPositionalArgs = 0, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyErrOccurred extends PythonUnaryBuiltinNode {
        @Specialization
        Object run(Object errorMarker,
                        @Cached("createBinaryProfile()") ConditionProfile getClassProfile) {
            PException currentException = getContext().getCurrentException();
            if (currentException != null) {
                currentException.getExceptionObject().reifyException();
                return getPythonClass(currentException.getExceptionObject().getLazyPythonClass(), getClassProfile);
            }
            return errorMarker;
        }
    }

    /**
     * Exceptions are usually printed using the traceback module or the hook function
     * {@code sys.excepthook}. This is the last resort if the hook function itself failed.
     */
    @Builtin(name = "PyErr_Display", fixedNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyErrDisplay extends PythonBuiltinNode {

        @Specialization
        @SuppressWarnings("unused")
        Object run(PythonClass typ, PBaseException val, PTraceback tb) {
            if (val.getException() != null) {
                ExceptionUtils.printPythonLikeStackTrace(val.getException());
            }
            return PNone.NO_VALUE;
        }
    }

    @Builtin(name = "PyUnicode_FromString", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyUnicodeFromStringNode extends PythonBuiltinNode {
        @Specialization
        PString run(String str) {
            return factory().createString(str);
        }

        @Specialization
        PString run(PString str) {
            return str;
        }
    }

    @Builtin(name = "do_richcompare", fixedNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class RichCompareNode extends PythonBuiltinNode {
        private static final String[] opstrings = new String[]{"<", "<=", "==", "!=", ">", ">="};
        private static final String[] opnames = new String[]{
                        SpecialMethodNames.__LT__, SpecialMethodNames.__LE__, SpecialMethodNames.__EQ__, SpecialMethodNames.__NE__, SpecialMethodNames.__GT__, SpecialMethodNames.__GE__};
        private static final String[] reversals = new String[]{
                        SpecialMethodNames.__GT__, SpecialMethodNames.__GE__, SpecialMethodNames.__EQ__, SpecialMethodNames.__NE__, SpecialMethodNames.__GT__, SpecialMethodNames.__GE__};

        protected static BinaryComparisonNode create(int op) {
            return BinaryComparisonNode.create(opnames[op], reversals[op], opstrings[op]);
        }

        @Specialization(guards = "op == 0")
        Object op0(Object a, Object b, @SuppressWarnings("unused") int op,
                        @Cached("create(op)") BinaryComparisonNode compNode) {
            return compNode.executeWith(a, b);
        }

        @Specialization(guards = "op == 1")
        Object op1(Object a, Object b, @SuppressWarnings("unused") int op,
                        @Cached("create(op)") BinaryComparisonNode compNode) {
            return compNode.executeWith(a, b);
        }

        @Specialization(guards = "op == 2")
        Object op2(Object a, Object b, @SuppressWarnings("unused") int op,
                        @Cached("create(op)") BinaryComparisonNode compNode) {
            return compNode.executeWith(a, b);
        }

        @Specialization(guards = "op == 3")
        Object op3(Object a, Object b, @SuppressWarnings("unused") int op,
                        @Cached("create(op)") BinaryComparisonNode compNode) {
            return compNode.executeWith(a, b);
        }

        @Specialization(guards = "op == 4")
        Object op4(Object a, Object b, @SuppressWarnings("unused") int op,
                        @Cached("create(op)") BinaryComparisonNode compNode) {
            return compNode.executeWith(a, b);
        }

        @Specialization(guards = "op == 5")
        Object op5(Object a, Object b, @SuppressWarnings("unused") int op,
                        @Cached("create(op)") BinaryComparisonNode compNode) {
            return compNode.executeWith(a, b);
        }
    }

    @Builtin(name = "PyType_Dict", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyType_DictNode extends PythonBuiltinNode {
        @Specialization
        PHashingCollection getDict(PythonNativeClass object) {
            PHashingCollection dict = object.getDict();
            assert dict instanceof PDict;
            return dict;
        }
    }

    @Builtin(name = "PyTruffle_SetAttr", fixedNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyObject_Setattr extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object setattr(PythonBuiltinClass object, String key, Object value) {
            object.setAttributeUnsafe(key, value);
            return PNone.NONE;
        }

        @Specialization(guards = {"!isPythonBuiltinClass(object)"})
        @TruffleBoundary
        Object setattr(PythonObject object, String key, Object value) {
            object.getStorage().define(key, value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "PyType_Ready", fixedNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class PyType_ReadyNode extends PythonBuiltinNode {
        @Child WriteAttributeToObjectNode writeNode = WriteAttributeToObjectNode.create();
        @Child private HashingStorageNodes.GetItemNode getItemNode;

        private HashingStorageNodes.GetItemNode getGetItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(HashingStorageNodes.GetItemNode.create());
            }
            return getItemNode;
        }

        @Specialization
        Object run(Object typestruct, PythonObjectNativeWrapper metaClass, PythonObjectNativeWrapper baseClasses, PythonObjectNativeWrapper nativeMembers,
                        @Cached("create()") CExtNodes.ToJavaNode toJavaNode) {
            // TODO(fa) use recursive node
            return run(typestruct, (PythonClass) toJavaNode.execute(metaClass), (PTuple) toJavaNode.execute(baseClasses), (PDict) toJavaNode.execute(nativeMembers));
        }

        @Specialization
        Object run(Object typestruct, PythonClass metaClass, PTuple baseClasses, PDict nativeMembers) {
            Object[] array = baseClasses.getArray();
            PythonClass[] bases = new PythonClass[array.length];
            for (int i = 0; i < array.length; i++) {
                bases[i] = (PythonClass) array[i];
            }

            // 'tp_name' contains the fully-qualified name, i.e., 'module.A.B...'
            String fqname = getStringItem(nativeMembers, "tp_name");
            String doc = getStringItem(nativeMembers, "tp_doc");
            // the qualified name (i.e. without module name) like 'A.B...'
            String qualName = getQualName(fqname);
            PythonNativeClass cclass = factory().createNativeClassWrapper(typestruct, metaClass, qualName, bases);
            writeNode.execute(cclass, SpecialAttributeNames.__DOC__, doc);
            writeNode.execute(cclass, SpecialAttributeNames.__BASICSIZE__, getLongItem(nativeMembers, "tp_basicsize"));
            String moduleName = getModuleName(fqname);
            if (moduleName != null) {
                writeNode.execute(cclass, SpecialAttributeNames.__MODULE__, moduleName);
            }
            return new PythonClassInitNativeWrapper(cclass);
        }

        private static String getQualName(String fqname) {
            int firstDot = fqname.indexOf('.');
            if (firstDot != -1) {
                return fqname.substring(firstDot + 1);
            }
            return fqname;
        }

        private static String getModuleName(String fqname) {
            int firstDotIdx = fqname.indexOf('.');
            if (firstDotIdx != -1) {
                return fqname.substring(0, firstDotIdx);
            }
            return null;
        }

        private String getStringItem(PDict nativeMembers, String key) {
            Object item = getGetItemNode().execute(nativeMembers.getDictStorage(), key);
            if (item instanceof PString) {
                return ((PString) item).getValue();
            }
            return (String) item;
        }

        private Object getLongItem(PDict nativeMembers, String key) {
            Object item = getGetItemNode().execute(nativeMembers.getDictStorage(), key);
            if (item instanceof PInt || item instanceof Number) {
                return item;
            }
            return (long) item;
        }
    }

    // roughly equivalent to _Py_CheckFunctionResult in Objects/call.c
    @ImportStatic(PGuards.class)
    abstract static class CheckFunctionResultNode extends PNodeWithContext {

        @Child private Node isNullNode;

        public abstract Object execute(String name, Object result);

        @Specialization
        Object doNativeWrapper(String name, PythonObjectNativeWrapper result,
                        @Cached("create()") CheckFunctionResultNode recursive) {
            return recursive.execute(name, result.getDelegate());
        }

        @Specialization(guards = "!isPythonObjectNativeWrapper(result)")
        Object doPrimitiveWrapper(String name, @SuppressWarnings("unused") PythonNativeWrapper result) {
            checkFunctionResult(name, false);
            return result;
        }

        @Specialization(guards = "isNoValue(result)")
        Object doNoValue(String name, @SuppressWarnings("unused") PNone result) {
            checkFunctionResult(name, true);
            return PNone.NO_VALUE;
        }

        @Specialization(guards = "!isNoValue(result)")
        Object doNativeWrapper(String name, @SuppressWarnings("unused") PythonAbstractObject result) {
            checkFunctionResult(name, false);
            return result;
        }

        @Specialization
        Object doPythonNativeNull(String name, @SuppressWarnings("unused") PythonNativeNull result) {
            checkFunctionResult(name, true);
            return result;
        }

        @Specialization(guards = {"isForeignObject(result)", "!isNativeNull(result)"})
        Object doForeign(String name, TruffleObject result,
                        @Cached("createBinaryProfile()") ConditionProfile isNullProfile) {
            checkFunctionResult(name, isNullProfile.profile(isNull(result)));
            return result;
        }

        @Fallback
        Object doGeneric(String name, Object result) {
            assert result != null;
            checkFunctionResult(name, false);
            return result;
        }

        private void checkFunctionResult(String name, boolean isNull) {
            PythonContext context = getContext();
            PException currentException = context.getCurrentException();
            boolean errOccurred = currentException != null;
            if (isNull) {
                // consume exception
                context.setCurrentException(null);
                if (!errOccurred) {
                    throw raise(PythonErrorType.SystemError, "%s returned NULL without setting an error", name);
                } else {
                    throw currentException;
                }
            } else if (errOccurred) {
                // consume exception
                context.setCurrentException(null);
                throw raise(PythonErrorType.SystemError, "%s returned a result with an error set", name);
            }
        }

        private boolean isNull(TruffleObject result) {
            if (isNullNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isNullNode = insert(Message.IS_NULL.createNode());
            }
            return ForeignAccess.sendIsNull(isNullNode, result);
        }

        protected static boolean isNativeNull(TruffleObject object) {
            return object instanceof PythonNativeNull;
        }

        protected static boolean isPythonObjectNativeWrapper(PythonNativeWrapper object) {
            return object instanceof PythonObjectNativeWrapper;
        }

        public static CheckFunctionResultNode create() {
            return CheckFunctionResultNodeGen.create();
        }
    }

    static class ExternalFunctionNode extends RootNode {
        private final TruffleObject cwrapper;
        private final TruffleObject callable;
        private final String name;
        @CompilationFinal ContextReference<PythonContext> ctxt;
        @Child private Node executeNode;
        @Child CExtNodes.AllToSulongNode toSulongNode = CExtNodes.AllToSulongNode.create();
        @Child CExtNodes.AsPythonObjectNode asPythonObjectNode = CExtNodes.AsPythonObjectNode.create();
        @Child private CheckFunctionResultNode checkResultNode = CheckFunctionResultNode.create();
        @Child private PForeignToPTypeNode fromForeign = PForeignToPTypeNode.create();

        public ExternalFunctionNode(PythonLanguage lang, String name, TruffleObject cwrapper, TruffleObject callable) {
            super(lang);
            this.name = name;
            this.cwrapper = cwrapper;
            this.callable = callable;
            this.executeNode = Message.EXECUTE.createNode();
        }

        public TruffleObject getCallable() {
            return callable;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object[] frameArgs = frame.getArguments();
            try {
                TruffleObject fun;
                Object[] arguments;

                if (cwrapper != null) {
                    fun = cwrapper;
                    arguments = new Object[1 + frameArgs.length - PArguments.USER_ARGUMENTS_OFFSET];
                    arguments[0] = callable;
                    toSulongNode.executeInto(frameArgs, PArguments.USER_ARGUMENTS_OFFSET, arguments, 1);
                } else {
                    fun = callable;
                    arguments = new Object[frameArgs.length - PArguments.USER_ARGUMENTS_OFFSET];
                    toSulongNode.executeInto(frameArgs, PArguments.USER_ARGUMENTS_OFFSET, arguments, 0);
                }
                // save current exception state
                PException exceptionState = getContext().getCurrentException();
                // clear current exception such that native code has clean environment
                getContext().setCurrentException(null);

                Object result = fromNative(asPythonObjectNode.execute(checkResultNode.execute(name, ForeignAccess.sendExecute(executeNode, fun, arguments))));

                // restore previous exception state
                getContext().setCurrentException(exceptionState);
                return result;
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new RuntimeException(e.toString());
            }
        }

        private Object fromNative(Object result) {
            return fromForeign.executeConvert(result);
        }

        public final PythonCore getCore() {
            return getContext().getCore();
        }

        public final PythonContext getContext() {
            if (ctxt == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                ctxt = PythonLanguage.getContextRef();
            }
            return ctxt.get();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return "<external function root " + name + ">";
        }

        @Override
        public boolean isCloningAllowed() {
            return true;
        }
    }

    @Builtin(name = "Py_NoValue", fixedNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class Py_NoValue extends PythonBuiltinNode {
        @Specialization
        PNone doNoValue() {
            return PNone.NO_VALUE;
        }
    }

    @Builtin(name = "Py_None", fixedNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class PyNoneNode extends PythonBuiltinNode {
        @Specialization
        PNone doNativeNone() {
            return PNone.NONE;
        }
    }

    abstract static class NativeBuiltin extends PythonBuiltinNode {

        @Child private Node hasSizeNode;
        @Child private Node getSizeNode;
        @Child private Node isBoxedNode;
        @Child private Node unboxNode;
        @Child private GetByteArrayNode getByteArrayNode;

        protected void transformToNative(PException p) {
            p.getExceptionObject().reifyException();
            getContext().setCurrentException(p);
        }

        protected <T> T raiseNative(T defaultValue, PythonBuiltinClassType errType, String fmt, Object... args) {
            try {
                throw raise(errType, fmt, args);
            } catch (PException p) {
                transformToNative(p);
                return defaultValue;
            }
        }

        protected boolean isByteArray(TruffleObject o) {
            return o instanceof PySequenceArrayWrapper || o instanceof CByteArrayWrapper || ForeignAccess.sendHasSize(getHasSizeNode(), o);
        }

        protected byte[] getByteArray(TruffleObject o) {
            if (getByteArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getByteArrayNode = insert(GetByteArrayNode.create());
            }
            if (getSizeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getSizeNode = insert(Message.GET_SIZE.createNode());
            }
            Object sizeObj;
            try {
                sizeObj = ForeignAccess.sendGetSize(getSizeNode, o);
                long size;
                if (sizeObj instanceof Integer) {
                    size = (int) sizeObj;
                } else if (sizeObj instanceof Long) {
                    size = (long) sizeObj;
                } else {
                    if (isBoxedNode == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        isBoxedNode = insert(Message.IS_BOXED.createNode());
                    }
                    if (unboxNode == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        unboxNode = insert(Message.UNBOX.createNode());
                    }
                    if (sizeObj instanceof TruffleObject && ForeignAccess.sendIsBoxed(isBoxedNode, (TruffleObject) sizeObj)) {
                        size = (int) ForeignAccess.sendUnbox(unboxNode, (TruffleObject) sizeObj);
                    } else {
                        throw new RuntimeException("invalid size type");
                    }
                }
                return getByteArrayNode.execute(o, size);
            } catch (UnsupportedMessageException e) {
                throw e.raise();
            }
        }

        protected byte[] getByteArray(TruffleObject o, long size) {
            if (getByteArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getByteArrayNode = insert(GetByteArrayNode.create());
            }
            return getByteArrayNode.execute(o, size);
        }

        protected Object raiseBadArgument(Object errorMarker) {
            return raiseNative(errorMarker, PythonErrorType.TypeError, "bad argument type for built-in operation");
        }

        private Node getHasSizeNode() {
            if (hasSizeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                hasSizeNode = insert(Message.HAS_SIZE.createNode());
            }
            return hasSizeNode;
        }
    }

    abstract static class NativeUnicodeBuiltin extends NativeBuiltin {
        private static final int NATIVE_ORDER = 0;
        private static Charset UTF32;
        private static Charset UTF32LE;
        private static Charset UTF32BE;

        protected static Charset getUTF32Charset(int byteorder) {
            String utf32Name = getUTF32Name(byteorder);
            if (byteorder == NativeUnicodeBuiltin.NATIVE_ORDER) {
                if (UTF32 == null) {
                    UTF32 = Charset.forName(utf32Name);
                }
                return UTF32;
            } else if (byteorder < NativeUnicodeBuiltin.NATIVE_ORDER) {
                if (UTF32LE == null) {
                    UTF32LE = Charset.forName(utf32Name);
                }
                return UTF32LE;
            }
            if (UTF32BE == null) {
                UTF32BE = Charset.forName(utf32Name);
            }
            return UTF32BE;
        }

        protected static String getUTF32Name(int byteorder) {
            String csName;
            if (byteorder == 0) {
                csName = "UTF-32";
            } else if (byteorder < 0) {
                csName = "UTF-32LE";
            } else {
                csName = "UTF-32BE";
            }
            return csName;
        }
    }

    @Builtin(name = "TrufflePInt_AsPrimitive", fixedNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class TrufflePInt_AsPrimitive extends NativeBuiltin {

        public abstract Object executeWith(Object o, int signed, long targetTypeSize, String targetTypeName);

        public abstract long executeLong(Object o, int signed, long targetTypeSize, String targetTypeName);

        public abstract int executeInt(Object o, int signed, long targetTypeSize, String targetTypeName);

        @Specialization(guards = "targetTypeSize == 4")
        int doInt4(int obj, @SuppressWarnings("unused") int signed, @SuppressWarnings("unused") long targetTypeSize, @SuppressWarnings("unused") String targetTypeName) {
            return obj;
        }

        @Specialization(guards = "targetTypeSize == 8")
        long doInt8(int obj, int signed, @SuppressWarnings("unused") long targetTypeSize, @SuppressWarnings("unused") String targetTypeName) {
            if (signed != 0) {
                return obj;
            } else {
                return obj & 0xFFFFFFFFL;
            }
        }

        @Specialization(guards = {"targetTypeSize != 4", "targetTypeSize != 8"})
        int doIntOther(@SuppressWarnings("unused") int obj, @SuppressWarnings("unused") int signed, long targetTypeSize, @SuppressWarnings("unused") String targetTypeName) {
            return raiseNative(-1, PythonErrorType.SystemError, "Unsupported target size: %d", targetTypeSize);
        }

        @Specialization(guards = "targetTypeSize == 4")
        int doLong4(@SuppressWarnings("unused") long obj, @SuppressWarnings("unused") int signed, @SuppressWarnings("unused") long targetTypeSize, String targetTypeName) {
            return raiseNative(-1, PythonErrorType.OverflowError, "Python int too large to convert to C %s", targetTypeName);
        }

        @Specialization(guards = "targetTypeSize == 8")
        long doLong8(long obj, @SuppressWarnings("unused") int signed, @SuppressWarnings("unused") int targetTypeSize, @SuppressWarnings("unused") String targetTypeName) {
            return obj;
        }

        @Specialization(guards = "targetTypeSize == 8")
        long doLong8(long obj, @SuppressWarnings("unused") int signed, @SuppressWarnings("unused") long targetTypeSize, @SuppressWarnings("unused") String targetTypeName) {
            return obj;
        }

        @Specialization(guards = {"targetTypeSize != 4", "targetTypeSize != 8"})
        int doPInt(@SuppressWarnings("unused") long obj, @SuppressWarnings("unused") int signed, long targetTypeSize, @SuppressWarnings("unused") String targetTypeName) {
            return raiseNative(-1, PythonErrorType.SystemError, "Unsupported target size: %d", targetTypeSize);
        }

        @Specialization(guards = "targetTypeSize == 4")
        int doPInt4(PInt obj, int signed, @SuppressWarnings("unused") long targetTypeSize, String targetTypeName) {
            try {
                if (signed != 0) {
                    return obj.intValueExact();
                } else if (obj.bitCount() <= 32) {
                    return obj.intValue();
                } else {
                    throw new ArithmeticException();
                }
            } catch (ArithmeticException e) {
                return raiseNative(-1, PythonErrorType.OverflowError, "Python int too large to convert to C %s", targetTypeName);
            }
        }

        @Specialization(guards = "targetTypeSize == 8")
        long doPInt8(PInt obj, int signed, @SuppressWarnings("unused") long targetTypeSize, String targetTypeName) {
            try {
                if (signed != 0) {
                    return obj.longValueExact();
                } else if (obj.bitCount() <= 64) {
                    return obj.longValue();
                } else {
                    throw new ArithmeticException();
                }
            } catch (ArithmeticException e) {
                return raiseNative(-1, PythonErrorType.OverflowError, "Python int too large to convert to C %s", targetTypeName);
            }
        }

        @Specialization(guards = {"targetTypeSize != 4", "targetTypeSize != 8"})
        int doPInt(@SuppressWarnings("unused") PInt obj, @SuppressWarnings("unused") int signed, long targetTypeSize, @SuppressWarnings("unused") String targetTypeName) {
            return raiseNative(-1, PythonErrorType.SystemError, "Unsupported target size: %d", targetTypeSize);
        }

        @Specialization(guards = {"!isInteger(obj)", "!isPInt(obj)"})
        @SuppressWarnings("unused")
        int doGeneric(Object obj, boolean signed, int targetTypeSize, String targetTypeName) {
            return raiseNative(-1, PythonErrorType.TypeError, "an integer is required", obj);
        }
    }

    @Builtin(name = "PyTruffle_Unicode_FromWchar", fixedNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class PyTruffle_Unicode_FromWchar extends NativeUnicodeBuiltin {
        @Specialization(guards = "isByteArray(o)")
        @TruffleBoundary
        Object doBytes(TruffleObject o, long elementSize, Object errorMarker) {
            try {
                ByteBuffer bytes = ByteBuffer.wrap(getByteArray(o));
                CharBuffer decoded;
                if (elementSize == 2L) {
                    decoded = bytes.asCharBuffer();
                } else if (elementSize == 4L) {
                    decoded = getUTF32Charset(0).newDecoder().decode(bytes);
                } else {
                    return raiseNative(errorMarker, PythonErrorType.ValueError, "unsupported 'wchar_t' size; was: %d", elementSize);
                }
                return decoded.toString();
            } catch (CharacterCodingException e) {
                return raiseNative(errorMarker, PythonErrorType.UnicodeError, e.getMessage());
            } catch (IllegalArgumentException e) {
                return raiseNative(errorMarker, PythonErrorType.LookupError, e.getMessage());
            }
        }

        @Specialization(guards = "isByteArray(o)")
        @TruffleBoundary
        Object doBytes(TruffleObject o, PInt elementSize, Object errorMarker) {
            try {
                return doBytes(o, elementSize.longValueExact(), errorMarker);
            } catch (ArithmeticException e) {
                return raiseNative(errorMarker, PythonErrorType.ValueError, "invalid parameters");
            }
        }
    }

    @Builtin(name = "PyTruffle_Unicode_FromUTF8", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(Message.class)
    abstract static class PyTruffle_Unicode_FromUTF8 extends NativeBuiltin {

        @Specialization(guards = "isByteArray(o)")
        @TruffleBoundary
        Object doBytes(TruffleObject o, Object errorMarker) {
            try {
                CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
                CharBuffer cbuf = decoder.decode(ByteBuffer.wrap(getByteArray(o)));
                return cbuf.toString();
            } catch (CharacterCodingException e) {
                return raiseNative(errorMarker, PythonErrorType.UnicodeError, e.getMessage());
            }
        }
    }

    abstract static class NativeEncoderNode extends NativeBuiltin {
        private final Charset charset;

        protected NativeEncoderNode(Charset charset) {
            this.charset = charset;
        }

        @Specialization(guards = "isNoValue(errors)")
        Object doUnicode(PString s, @SuppressWarnings("unused") PNone errors, Object error_marker) {
            return doUnicode(s, "strict", error_marker);
        }

        @Specialization
        @TruffleBoundary
        Object doUnicode(PString s, String errors, Object error_marker) {
            try {
                CharsetEncoder encoder = charset.newEncoder();
                CodingErrorAction action = BytesBuiltins.toCodingErrorAction(errors, this);
                encoder.onMalformedInput(action).onUnmappableCharacter(action);
                CharBuffer buf = CharBuffer.allocate(s.len());
                buf.put(s.getValue());
                buf.flip();
                ByteBuffer encoded = encoder.encode(buf);
                byte[] barr = new byte[encoded.remaining()];
                encoded.get(barr);
                return factory().createBytes(barr);
            } catch (PException e) {
                transformToNative(e);
                return error_marker;
            } catch (CharacterCodingException e) {
                return raiseNative(error_marker, PythonErrorType.UnicodeEncodeError, e.getMessage());
            }
        }

        @Fallback
        Object doUnicode(@SuppressWarnings("unused") Object s, @SuppressWarnings("unused") Object errors, Object errorMarker) {
            return raiseBadArgument(errorMarker);
        }
    }

    @Builtin(name = "_PyUnicode_AsUTF8String", fixedNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class _PyUnicode_AsUTF8String extends NativeEncoderNode {

        protected _PyUnicode_AsUTF8String() {
            super(StandardCharsets.UTF_8);
        }
    }

    @Builtin(name = "_PyTruffle_Unicode_AsLatin1String", fixedNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class _PyTruffle_Unicode_AsLatin1String extends NativeEncoderNode {
        protected _PyTruffle_Unicode_AsLatin1String() {
            super(StandardCharsets.ISO_8859_1);
        }
    }

    @Builtin(name = "_PyTruffle_Unicode_AsASCIIString", fixedNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class _PyTruffle_Unicode_AsASCIIString extends NativeEncoderNode {
        protected _PyTruffle_Unicode_AsASCIIString() {
            super(StandardCharsets.US_ASCII);
        }
    }

    @Builtin(name = "PyTruffle_Unicode_AsUnicodeAndSize", fixedNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyTruffle_Unicode_AsUnicodeAndSize extends NativeBuiltin {
        @Specialization
        @TruffleBoundary
        Object doUnicode(PString s) {
            char[] charArray = s.getValue().toCharArray();
            // stuff into byte[]
            ByteBuffer allocate = ByteBuffer.allocate(charArray.length * 2);
            for (int i = 0; i < charArray.length; i++) {
                allocate.putChar(charArray[i]);
            }
            return getContext().getEnv().asGuestValue(allocate.array());
        }
    }

    @Builtin(name = "PyTruffle_Unicode_DecodeUTF32", fixedNumOfPositionalArgs = 5)
    @GenerateNodeFactory
    abstract static class PyTruffle_Unicode_DecodeUTF32 extends NativeUnicodeBuiltin {

        @Child private CExtNodes.ToSulongNode toSulongNode;

        @Specialization(guards = {"isByteArray(o)", "isNoValue(errors)"})
        Object doUnicode(TruffleObject o, long size, @SuppressWarnings("unused") PNone errors, int byteorder, Object errorMarker) {
            return doUnicode(o, size, "strict", byteorder, errorMarker);
        }

        @Specialization(guards = "isByteArray(o)")
        @TruffleBoundary
        Object doUnicode(TruffleObject o, long size, String errors, int byteorder, Object errorMarker) {
            try {
                CharsetDecoder decoder = getUTF32Charset(byteorder).newDecoder();
                CodingErrorAction action = BytesBuiltins.toCodingErrorAction(errors, this);
                CharBuffer decode = decoder.onMalformedInput(action).onUnmappableCharacter(action).decode(ByteBuffer.wrap(getByteArray(o, size), 0, (int) size));
                return getToSulongNode().execute(decode.toString());
            } catch (CharacterCodingException e) {
                return raiseNative(errorMarker, PythonErrorType.UnicodeEncodeError, e.getMessage());
            } catch (IllegalArgumentException e) {
                String csName = getUTF32Name(byteorder);
                return raiseNative(errorMarker, PythonErrorType.LookupError, "unknown encoding: " + csName);
            }
        }

        private CExtNodes.ToSulongNode getToSulongNode() {
            if (toSulongNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toSulongNode = insert(CExtNodes.ToSulongNode.create());
            }
            return toSulongNode;
        }
    }

    abstract static class GetByteArrayNode extends PNodeWithContext {

        @Child private Node readNode;

        public abstract byte[] execute(TruffleObject o, long size);

        public static GetByteArrayNode create() {
            return GetByteArrayNodeGen.create();
        }

        @Specialization
        byte[] doCArrayWrapper(CByteArrayWrapper o, @SuppressWarnings("unused") long size) {
            return o.getDelegate();
        }

        @Specialization
        byte[] doSequenceArrayWrapper(PySequenceArrayWrapper o, @SuppressWarnings("unused") long size,
                        @Cached("create()") BytesNodes.ToBytesNode toBytesNode) {
            return toBytesNode.execute(o.getDelegate());
        }

        @Fallback
        byte[] doTruffleObject(TruffleObject o, long size) {
            try {
                byte[] bytes = new byte[(int) size];
                for (long i = 0; i < size; i++) {
                    if (readNode == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        readNode = insert(Message.READ.createNode());
                    }
                    bytes[(int) i] = (byte) ForeignAccess.sendRead(readNode, o, i);
                }
                return bytes;
            } catch (InteropException e) {
                throw e.raise();
            }
        }

    }

    @Builtin(name = "PyTruffle_Unicode_AsWideChar", fixedNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class PyTruffle_Unicode_AsWideChar extends NativeUnicodeBuiltin {
        @Child private UnicodeAsWideCharNode asWideCharNode;

        @Specialization
        Object doUnicode(String s, long elementSize, @SuppressWarnings("unused") PNone elements, Object errorMarker) {
            return doUnicode(s, elementSize, -1, errorMarker);
        }

        @Specialization
        @TruffleBoundary
        Object doUnicode(String s, long elementSize, long elements, Object errorMarker) {
            try {
                if (asWideCharNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    asWideCharNode = insert(UnicodeAsWideCharNode.create(-1));
                }

                PBytes wchars = asWideCharNode.execute(s, elementSize, elements);
                if (wchars != null) {
                    return wchars;
                } else {
                    return raiseNative(errorMarker, PythonErrorType.ValueError, "unsupported wchar size; was: %d", elementSize);
                }
            } catch (IllegalArgumentException e) {
                // TODO
                return raiseNative(errorMarker, PythonErrorType.LookupError, e.getMessage());
            }
        }

        @Specialization
        Object doUnicode(String s, PInt elementSize, @SuppressWarnings("unused") PNone elements, Object errorMarker) {
            try {
                return doUnicode(s, elementSize.longValueExact(), -1, errorMarker);
            } catch (ArithmeticException e) {
                return raiseNative(errorMarker, PythonErrorType.ValueError, "invalid parameters");
            }
        }

        @Specialization
        Object doUnicode(String s, PInt elementSize, PInt elements, Object errorMarker) {
            try {
                return doUnicode(s, elementSize.longValueExact(), elements.longValueExact(), errorMarker);
            } catch (ArithmeticException e) {
                return raiseNative(errorMarker, PythonErrorType.ValueError, "invalid parameters");
            }
        }
    }

    @Builtin(name = "PyTruffle_Bytes_AsString", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTruffle_Bytes_AsString extends NativeBuiltin {
        @Specialization
        Object doBytes(PBytes bytes, @SuppressWarnings("unused") Object errorMarker) {
            return new PySequenceArrayWrapper(bytes, 1);
        }

        @Specialization
        Object doUnicode(PString str, @SuppressWarnings("unused") Object errorMarker) {
            return new CStringWrapper(str.getValue());
        }

        @Fallback
        Object doUnicode(Object o, Object errorMarker) {
            return raiseNative(errorMarker, PythonErrorType.TypeError, "expected bytes, %p found", o);
        }
    }

    @Builtin(name = "PyHash_Imag", fixedNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class PyHashImagNode extends PythonBuiltinNode {
        @Specialization
        long getHash() {
            return PComplex.IMAG_MULTIPLIER;
        }
    }

    @Builtin(name = "PyTruffleFrame_New", fixedNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class PyTruffleFrameNewNode extends PythonBuiltinNode {
        @Specialization
        Object newFrame(Object threadState, PCode code, PythonObject globals, Object locals) {
            return factory().createPFrame(threadState, code, globals, locals);
        }
    }

    @Builtin(name = "PyTruffleTraceBack_Here", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTruffleTraceBack_HereNode extends PythonBuiltinNode {
        @Specialization
        Object tbHere(PTraceback next, PFrame frame) {
            PTraceback newTb = next.getException().putTracebackOnTop(factory());
            newTb.setPFrame(frame);
            return 0;
        }
    }

    @Builtin(name = "PyTruffle_GetTpFlags", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffle_GetTpFlags extends NativeBuiltin {

        @Child private GetClassNode getClassNode;

        @Specialization
        long doPythonObject(PythonNativeWrapper nativeWrapper) {
            PythonClass pclass = getClassNode().execute(nativeWrapper.getDelegate());
            return pclass.getFlags();
        }

        @Specialization
        long doPythonObject(PythonAbstractObject object) {
            PythonClass pclass = getClassNode().execute(object);
            return pclass.getFlags();
        }

        private GetClassNode getClassNode() {
            if (getClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getClassNode = insert(GetClassNode.create());
            }
            return getClassNode;
        }
    }

    @Builtin(name = "PyTruffle_Set_Ptr", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTruffle_Set_Ptr extends NativeBuiltin {

        @Specialization
        int doPythonObject(PythonAbstractObject nativeWrapper, TruffleObject ptr) {
            return doNativeWrapper(nativeWrapper.getNativeWrapper(), ptr);
        }

        @Specialization
        int doNativeWrapper(PythonNativeWrapper nativeWrapper, TruffleObject ptr) {
            if (nativeWrapper.isNative()) {
                PythonContext.getSingleNativeContextAssumption().invalidate();
            } else {
                nativeWrapper.setNativePointer(ptr);
            }
            return 0;
        }
    }

    @Builtin(name = "PyTruffle_Set_SulongType", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTruffle_Set_SulongType extends NativeBuiltin {

        @Specialization
        Object doPythonObject(PythonClassNativeWrapper klass, Object ptr) {
            ((PythonClass) klass.getPythonObject()).setSulongType(ptr);
            return ptr;
        }
    }

    @Builtin(name = "PyTruffle_SetBufferProcs", fixedNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyTruffle_SetBufferProcs extends NativeBuiltin {

        @Specialization
        Object doNativeWrapper(PythonClassNativeWrapper nativeWrapper, Object getBufferProc, Object releaseBufferProc) {
            nativeWrapper.setGetBufferProc(getBufferProc);
            nativeWrapper.setReleaseBufferProc(releaseBufferProc);
            return PNone.NO_VALUE;
        }

        @Specialization
        Object doPythonObject(PythonClass obj, Object getBufferProc, Object releaseBufferProc) {
            return doNativeWrapper(obj.getNativeWrapper(), getBufferProc, releaseBufferProc);
        }
    }

    @Builtin(name = "PyTruffle_ThreadState_GetDict", fixedNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class PyTruffle_ThreadState_GetDict extends NativeBuiltin {

        @Specialization
        Object get() {
            return getContext().getCustomThreadState();
        }
    }

    @Builtin(name = "PyTruffleSlice_GetIndicesEx", fixedNumOfPositionalArgs = 4)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class PyTruffleSlice_GetIndicesEx extends NativeBuiltin {
        @Specialization
        Object doUnpack(int start, int stop, int step, int length) {
            PSlice tmpSlice = factory().createSlice(start, stop, step);
            SliceInfo actualIndices = tmpSlice.computeIndices(length);
            return factory().createTuple(new Object[]{actualIndices.start, actualIndices.stop, actualIndices.step, actualIndices.length});
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        Object doUnpackLong(long start, long stop, long step, long length) {
            PSlice tmpSlice = factory().createSlice(PInt.intValueExact(start), PInt.intValueExact(stop), PInt.intValueExact(step));
            SliceInfo actualIndices = tmpSlice.computeIndices(PInt.intValueExact(length));
            return factory().createTuple(new Object[]{actualIndices.start, actualIndices.stop, actualIndices.step, actualIndices.length});
        }

        @Specialization(replaces = {"doUnpackLong", "doUnpack"})
        Object doUnpackLongOvf(long start, long stop, long step, long length) {
            try {
                PSlice tmpSlice = factory().createSlice(PInt.intValueExact(start), PInt.intValueExact(stop), PInt.intValueExact(step));
                SliceInfo actualIndices = tmpSlice.computeIndices(length > Integer.MAX_VALUE ? Integer.MAX_VALUE : PInt.intValueExact(length));
                return factory().createTuple(new Object[]{actualIndices.start, actualIndices.stop, actualIndices.step, actualIndices.length});
            } catch (ArithmeticException e) {
                throw raiseIndexError();
            }
        }
    }

    @Builtin(name = "PyTruffle_Add_Subclass", fixedNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyTruffle_Add_Subclass extends NativeBuiltin {

        @Specialization
        int doManagedSubclass(PythonClassNativeWrapper base, @SuppressWarnings("unused") Object key, PythonClassNativeWrapper value) {
            addToSet((PythonClass) base.getPythonObject(), (PythonClass) value.getPythonObject());
            return 0;
        }

        @Fallback
        int doGeneric(@SuppressWarnings("unused") Object base, @SuppressWarnings("unused") Object key, @SuppressWarnings("unused") Object value) {
            return raiseNative(-1, SystemError, "Builtin can only handle managed base class.");
        }

        @TruffleBoundary
        private static void addToSet(PythonClass base, PythonClass value) {
            base.getSubClasses().add(value);
        }
    }

    @Builtin(name = "PyTruffle_GetSetDescriptor", keywordArguments = {"fget", "fset", "name", "owner"})
    @GenerateNodeFactory
    public abstract static class GetSetDescriptorNode extends PythonBuiltinNode {
        @Specialization
        Object call(PythonCallable get, PythonCallable set, String name, PythonClass owner) {
            return factory().createGetSetDescriptor(get, set, name, owner);
        }

        @Specialization
        Object call(PythonCallable get, @SuppressWarnings("unused") PNone set, String name, PythonClass owner) {
            return factory().createGetSetDescriptor(get, null, name, owner);
        }

        @Specialization
        Object call(@SuppressWarnings("unused") PNone get, PythonCallable set, String name, PythonClass owner) {
            return factory().createGetSetDescriptor(null, set, name, owner);
        }
    }

    @Builtin(name = "PyTruffle_SeqIter_New", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class SeqIterNewNode extends PythonBuiltinNode {
        @Specialization
        PSequenceIterator call(Object seq) {
            return factory().createSequenceIterator(seq);
        }
    }

    @Builtin(name = "PyTruffle_BuiltinMethod", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class BuiltinMethodNode extends PythonBuiltinNode {
        @Specialization
        Object call(Object self, PBuiltinFunction function) {
            return factory().createBuiltinMethod(self, function);
        }
    }

    abstract static class MethodDescriptorRoot extends RootNode {
        @Child protected DirectCallNode directCallNode;
        @Child protected ReadIndexedArgumentNode readSelfNode;
        protected final PythonObjectFactory factory;

        @TruffleBoundary
        protected MethodDescriptorRoot(PythonLanguage language, PythonObjectFactory factory, CallTarget callTarget) {
            super(language);
            this.factory = factory;
            this.readSelfNode = ReadIndexedArgumentNode.create(0);
            this.directCallNode = Truffle.getRuntime().createDirectCallNode(callTarget);
        }

        @Override
        public boolean isCloningAllowed() {
            return true;
        }

        @Override
        public String getName() {
            return directCallNode.getCurrentRootNode().getName();
        }

        @Override
        public String toString() {
            return "<METH root " + directCallNode.getCurrentRootNode().getName() + ">";
        }
    }

    static class MethKeywordsRoot extends MethodDescriptorRoot {
        @Child private ReadVarArgsNode readVarargsNode;
        @Child private ReadVarKeywordsNode readKwargsNode;

        protected MethKeywordsRoot(PythonLanguage language, PythonObjectFactory factory, CallTarget callTarget) {
            super(language, factory, callTarget);
            this.readVarargsNode = ReadVarArgsNode.create(1, true);
            this.readKwargsNode = ReadVarKeywordsNode.create(new String[0]);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
            Object[] args = readVarargsNode.executeObjectArray(frame);
            PKeyword[] kwargs = readKwargsNode.executePKeyword(frame);
            Object[] arguments = PArguments.create(3);
            PArguments.setArgument(arguments, 0, self);
            PArguments.setArgument(arguments, 1, factory.createTuple(args));
            PArguments.setArgument(arguments, 2, factory.createDict(kwargs));
            return directCallNode.call(arguments);
        }
    }

    static class MethVarargsRoot extends MethodDescriptorRoot {
        @Child private ReadVarArgsNode readVarargsNode;

        protected MethVarargsRoot(PythonLanguage language, PythonObjectFactory factory, CallTarget callTarget) {
            super(language, factory, callTarget);
            this.readVarargsNode = ReadVarArgsNode.create(1, true);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
            Object[] args = readVarargsNode.executeObjectArray(frame);
            Object[] arguments = PArguments.create(2);
            PArguments.setArgument(arguments, 0, self);
            PArguments.setArgument(arguments, 1, factory.createTuple(args));
            return directCallNode.call(arguments);
        }
    }

    static class MethNoargsRoot extends MethodDescriptorRoot {
        protected MethNoargsRoot(PythonLanguage language, PythonObjectFactory factory, CallTarget callTarget) {
            super(language, factory, callTarget);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
            Object[] arguments = PArguments.create(2);
            PArguments.setArgument(arguments, 0, self);
            PArguments.setArgument(arguments, 1, PNone.NONE);
            return directCallNode.call(arguments);
        }
    }

    static class MethORoot extends MethodDescriptorRoot {
        @Child private ReadIndexedArgumentNode readArgNode;

        protected MethORoot(PythonLanguage language, PythonObjectFactory factory, CallTarget callTarget) {
            super(language, factory, callTarget);
            this.readArgNode = ReadIndexedArgumentNode.create(1);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
            Object arg = readArgNode.execute(frame);
            Object[] arguments = PArguments.create(2);
            PArguments.setArgument(arguments, 0, self);
            PArguments.setArgument(arguments, 1, factory.createTuple(new Object[]{arg}));
            return directCallNode.call(arguments);
        }
    }

    static class MethFastcallRoot extends MethodDescriptorRoot {
        @Child private ReadVarArgsNode readVarargsNode;
        @Child private ReadVarKeywordsNode readKwargsNode;

        protected MethFastcallRoot(PythonLanguage language, PythonObjectFactory factory, CallTarget callTarget) {
            super(language, factory, callTarget);
            this.readVarargsNode = ReadVarArgsNode.create(1, true);
            this.readKwargsNode = ReadVarKeywordsNode.create(new String[0]);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object self = readSelfNode.execute(frame);
            Object[] args = readVarargsNode.executeObjectArray(frame);
            PKeyword[] kwargs = readKwargsNode.executePKeyword(frame);
            Object[] arguments = PArguments.create(4);
            PArguments.setArgument(arguments, 0, self);
            PArguments.setArgument(arguments, 1, factory.createTuple(args));
            PArguments.setArgument(arguments, 2, args.length);
            PArguments.setArgument(arguments, 3, factory.createDict(kwargs));
            return directCallNode.call(arguments);
        }
    }

    @Builtin(name = "METH_KEYWORDS", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class MethKeywordsNode extends PythonUnaryBuiltinNode {
        @TruffleBoundary
        @Specialization
        Object call(PBuiltinFunction function) {
            return factory().createBuiltinFunction(function.getName(), function.getEnclosingType(), function.getArity(),
                            Truffle.getRuntime().createCallTarget(new MethKeywordsRoot(getRootNode().getLanguage(PythonLanguage.class), factory(), function.getCallTarget())));
        }
    }

    @Builtin(name = "METH_VARARGS", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class MethVarargsNode extends PythonUnaryBuiltinNode {
        @TruffleBoundary
        @Specialization
        Object call(PBuiltinFunction function) {
            return factory().createBuiltinFunction(function.getName(), function.getEnclosingType(), function.getArity(),
                            Truffle.getRuntime().createCallTarget(new MethVarargsRoot(getRootNode().getLanguage(PythonLanguage.class), factory(), function.getCallTarget())));
        }
    }

    @Builtin(name = "METH_NOARGS", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class MethNoargsNode extends PythonUnaryBuiltinNode {
        @TruffleBoundary
        @Specialization
        Object call(PBuiltinFunction function) {
            return factory().createBuiltinFunction(function.getName(), function.getEnclosingType(), function.getArity(),
                            Truffle.getRuntime().createCallTarget(new MethNoargsRoot(getRootNode().getLanguage(PythonLanguage.class), factory(), function.getCallTarget())));
        }
    }

    @Builtin(name = "METH_O", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class MethONode extends PythonUnaryBuiltinNode {
        @TruffleBoundary
        @Specialization
        Object call(PBuiltinFunction function) {
            return factory().createBuiltinFunction(function.getName(), function.getEnclosingType(), function.getArity(),
                            Truffle.getRuntime().createCallTarget(new MethORoot(getRootNode().getLanguage(PythonLanguage.class), factory(), function.getCallTarget())));
        }
    }

    @Builtin(name = "METH_FASTCALL", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class MethFastcallNode extends PythonUnaryBuiltinNode {
        @TruffleBoundary
        @Specialization
        Object call(PBuiltinFunction function) {
            return factory().createBuiltinFunction(function.getName(), function.getEnclosingType(), function.getArity(),
                            Truffle.getRuntime().createCallTarget(new MethFastcallRoot(getRootNode().getLanguage(PythonLanguage.class), factory(), function.getCallTarget())));
        }
    }

    @Builtin(name = "PyTruffle_Bytes_EmptyWithCapacity", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTruffle_Bytes_EmptyWithCapacity extends NativeBuiltin {

        @Specialization
        PBytes doInt(int size, @SuppressWarnings("unused") Object errorMarker) {
            return factory().createBytes(new byte[size]);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        PBytes doLong(long size, Object errorMarker) {
            return doInt(PInt.intValueExact(size), errorMarker);
        }

        @Specialization(replaces = "doLong")
        PBytes doLongOvf(long size, Object errorMarker) {
            try {
                return doInt(PInt.intValueExact(size), errorMarker);
            } catch (ArithmeticException e) {
                throw raiseIndexError();
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        PBytes doPInt(PInt size, Object errorMarker) {
            return doInt(size.intValueExact(), errorMarker);
        }

        @Specialization(replaces = "doPInt")
        PBytes doPIntOvf(PInt size, Object errorMarker) {
            try {
                return doInt(size.intValueExact(), errorMarker);
            } catch (ArithmeticException e) {
                throw raiseIndexError();
            }
        }

        @Fallback
        Object doGeneric(Object size, Object errorMarker) {
            return raiseNative(errorMarker, TypeError, "expected 'int', but was '%p'", size);
        }

        @TruffleBoundary
        private static void addToSet(PythonClass base, PythonClass value) {
            base.getSubClasses().add(value);
        }
    }

    @Builtin(name = "PyTruffle_Upcall", minNumOfPositionalArgs = 3, takesVarArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class UpcallNode extends PythonBuiltinNode {
        @Child CExtNodes.AsPythonObjectNode toJavaNode = CExtNodes.AsPythonObjectNode.create();
        @Child CExtNodes.ToSulongNode toSulongNode = CExtNodes.ToSulongNode.create();
        @Child CExtNodes.ObjectUpcallNode upcallNode = CExtNodes.ObjectUpcallNode.create();
        @Child ReadAttributeFromObjectNode readErrorHandlerNode;

        @Specialization
        Object upcall(VirtualFrame frame, PythonModule cextModule, Object receiver, String name, Object[] args) {
            try {
                return toSulongNode.execute(upcallNode.execute(frame, toJavaNode.execute(receiver), name, args));
            } catch (PException e) {
                if (readErrorHandlerNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    readErrorHandlerNode = ReadAttributeFromObjectNode.create();
                }
                getContext().setCurrentException(e);
                return toSulongNode.execute(readErrorHandlerNode.execute(cextModule, NATIVE_NULL));
            }
        }
    }

    @Builtin(name = "PyTruffle_Upcall_l", minNumOfPositionalArgs = 2, takesVarArgs = true)
    @GenerateNodeFactory
    abstract static class UpcallLNode extends PythonBuiltinNode {
        @Child CExtNodes.AsPythonObjectNode toJavaNode = CExtNodes.AsPythonObjectNode.create();
        @Child CExtNodes.AsLong asLongNode = CExtNodes.AsLong.create();
        @Child CExtNodes.ObjectUpcallNode upcallNode = CExtNodes.ObjectUpcallNode.create();

        @Specialization
        long upcall(VirtualFrame frame, Object receiver, String name, Object[] args,
                        @Cached("create()") BranchProfile errorProfile) {
            try {
                return asLongNode.execute(upcallNode.execute(frame, toJavaNode.execute(receiver), name, args));
            } catch (PException e) {
                errorProfile.enter();
                getContext().setCurrentException(e);
                return -1;
            }
        }
    }

    @Builtin(name = "PyTruffle_Upcall_d", minNumOfPositionalArgs = 2, takesVarArgs = true)
    @GenerateNodeFactory
    abstract static class UpcallDNode extends PythonBuiltinNode {
        @Child CExtNodes.AsPythonObjectNode toJavaNode = CExtNodes.AsPythonObjectNode.create();
        @Child CExtNodes.AsDouble asDoubleNode = CExtNodes.AsDouble.create();
        @Child CExtNodes.ObjectUpcallNode upcallNode = CExtNodes.ObjectUpcallNode.create();

        @Specialization
        double upcall(VirtualFrame frame, Object receiver, String name, Object[] args,
                        @Cached("create()") BranchProfile errorProfile) {
            try {
                return asDoubleNode.execute(upcallNode.execute(frame, toJavaNode.execute(receiver), name, args));
            } catch (PException e) {
                errorProfile.enter();
                getContext().setCurrentException(e);
                return -1.0;
            }
        }
    }

    @Builtin(name = "PyTruffle_Upcall_ptr", minNumOfPositionalArgs = 2, takesVarArgs = true)
    @GenerateNodeFactory
    abstract static class UpcallPtrNode extends PythonBuiltinNode {
        @Child CExtNodes.AsPythonObjectNode toJavaNode = CExtNodes.AsPythonObjectNode.create();
        @Child CExtNodes.ObjectUpcallNode upcallNode = CExtNodes.ObjectUpcallNode.create();

        @Specialization
        Object upcall(VirtualFrame frame, Object receiver, String name, Object[] args,
                        @Cached("create()") BranchProfile errorProfile) {
            try {
                return upcallNode.execute(frame, toJavaNode.execute(receiver), name, args);
            } catch (PException e) {
                errorProfile.enter();
                getContext().setCurrentException(e);
                return 0;
            }
        }
    }

    @Builtin(name = "PyTruffle_Cext_Upcall", minNumOfPositionalArgs = 2, takesVarArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class UpcallCextNode extends PythonBuiltinNode {
        @Child CExtNodes.ToSulongNode toSulongNode = CExtNodes.ToSulongNode.create();
        @Child CExtNodes.CextUpcallNode upcallNode = CExtNodes.CextUpcallNode.create();

        @Specialization
        Object upcall(VirtualFrame frame, PythonModule cextModule, String name, Object[] args) {
            return toSulongNode.execute(upcallNode.execute(frame, cextModule, name, args));
        }
    }

    @Builtin(name = "PyTruffle_Cext_Upcall_d", minNumOfPositionalArgs = 2, takesVarArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class UpcallCextDNode extends PythonBuiltinNode {
        @Child CExtNodes.AsDouble asDoubleNode = CExtNodes.AsDouble.create();
        @Child CExtNodes.CextUpcallNode upcallNode = CExtNodes.CextUpcallNode.create();

        @Specialization
        double upcall(VirtualFrame frame, PythonModule cextModule, String name, Object[] args) {
            return asDoubleNode.execute(upcallNode.execute(frame, cextModule, name, args));
        }
    }

    @Builtin(name = "PyTruffle_Cext_Upcall_l", minNumOfPositionalArgs = 2, takesVarArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class UpcallCextLNode extends PythonBuiltinNode {
        @Child CExtNodes.AsLong asLongNode = CExtNodes.AsLong.create();
        @Child CExtNodes.CextUpcallNode upcallNode = CExtNodes.CextUpcallNode.create();

        @Specialization
        long upcall(VirtualFrame frame, PythonModule cextModule, String name, Object[] args) {
            return asLongNode.execute(upcallNode.execute(frame, cextModule, name, args));
        }
    }

    @Builtin(name = "PyTruffle_Cext_Upcall_ptr", minNumOfPositionalArgs = 2, takesVarArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class UpcallCextPtrNode extends PythonBuiltinNode {
        @Child CExtNodes.CextUpcallNode upcallNode = CExtNodes.CextUpcallNode.create();

        @Specialization
        Object upcall(VirtualFrame frame, PythonModule cextModule, String name, Object[] args) {
            return upcallNode.execute(frame, cextModule, name, args);
        }
    }

    @Builtin(name = "make_may_raise_wrapper", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class MakeMayRaiseWrapperNode extends PythonBuiltinNode {
        private static final Builtin unaryBuiltin = MayRaiseUnaryNode.class.getAnnotation(Builtin.class);
        private static final Builtin binaryBuiltin = MayRaiseBinaryNode.class.getAnnotation(Builtin.class);
        private static final Builtin ternaryBuiltin = MayRaiseTernaryNode.class.getAnnotation(Builtin.class);
        private static final Builtin varargsBuiltin = MayRaiseNode.class.getAnnotation(Builtin.class);

        @Specialization
        Object make(PFunction func, Object errorResult) {
            CompilerDirectives.transferToInterpreter();
            func.getFunctionRootNode().accept(new NodeVisitor() {
                public boolean visit(Node node) {
                    if (node instanceof PythonCallNode) {
                        node.replace(((PythonCallNode) node).asSpecialCall());
                    }
                    return true;
                }
            });

            RootNode rootNode = null;
            Arity arity = func.getArity();
            if (arity.takesFixedNumOfPositionalArgs()) {
                switch (arity.getMinNumOfArgs()) {
                    case 1:
                        rootNode = new BuiltinFunctionRootNode(getRootNode().getLanguage(PythonLanguage.class), unaryBuiltin,
                                        new MayRaiseNodeFactory<PythonUnaryBuiltinNode>(MayRaiseUnaryNodeGen.create(func, errorResult)),
                                        true);
                        break;
                    case 2:
                        rootNode = new BuiltinFunctionRootNode(getRootNode().getLanguage(PythonLanguage.class), binaryBuiltin,
                                        new MayRaiseNodeFactory<PythonBinaryBuiltinNode>(MayRaiseBinaryNodeGen.create(func, errorResult)),
                                        true);
                        break;
                    case 3:
                        rootNode = new BuiltinFunctionRootNode(getRootNode().getLanguage(PythonLanguage.class), ternaryBuiltin,
                                        new MayRaiseNodeFactory<PythonTernaryBuiltinNode>(MayRaiseTernaryNodeGen.create(func, errorResult)),
                                        true);
                        break;
                    default:
                        break;
                }
            }
            if (rootNode == null) {
                rootNode = new BuiltinFunctionRootNode(getRootNode().getLanguage(PythonLanguage.class), varargsBuiltin,
                                new MayRaiseNodeFactory<PythonBuiltinNode>(new MayRaiseNode(func, errorResult)),
                                true);
            }

            return factory().createBuiltinFunction(func.getName(), null, arity, Truffle.getRuntime().createCallTarget(rootNode));
        }
    }

    @Builtin(name = "to_long", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AsLong extends PythonBuiltinNode {
        @Child CExtNodes.AsLong asLongNode = CExtNodes.AsLong.create();

        @Specialization
        long doIt(Object object) {
            return asLongNode.execute(object);
        }
    }

    @Builtin(name = "to_double", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AsDouble extends PythonBuiltinNode {
        @Child CExtNodes.AsDouble asDoubleNode = CExtNodes.AsDouble.create();

        @Specialization
        double doIt(Object object) {
            return asDoubleNode.execute(object);
        }
    }

    @Builtin(name = "PyTruffle_Register_NULL", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffle_Register_NULL extends PythonUnaryBuiltinNode {
        @Specialization
        Object doIt(Object object,
                        @Cached("create()") ReadAttributeFromObjectNode writeAttrNode) {
            Object wrapper = writeAttrNode.execute(getCore().lookupBuiltinModule("python_cext"), NATIVE_NULL);
            if (wrapper instanceof PythonNativeNull) {
                ((PythonNativeNull) wrapper).setPtr(object);
            }

            return wrapper;
        }
    }

    @Builtin(name = "PyTruffle_HandleCache_Create", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffleHandleCacheCreate extends PythonUnaryBuiltinNode {
        @Specialization
        Object createCache(TruffleObject ptrToResolveHandle) {
            return new HandleCache(ptrToResolveHandle);
        }
    }

    @Builtin(name = "PyLong_FromLongLong", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyLong_FromLongLong extends PythonBinaryBuiltinNode {
        @Specialization(guards = "signed != 0")
        Object doSignedInt(int n, @SuppressWarnings("unused") int signed,
                        @Cached("create()") CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(n);
        }

        @Specialization(guards = "signed == 0")
        Object doUnsignedInt(int n, @SuppressWarnings("unused") int signed,
                        @Cached("create()") CExtNodes.ToSulongNode toSulongNode) {
            if (n < 0) {
                return toSulongNode.execute(n & 0xFFFFFFFFL);
            }
            return toSulongNode.execute(n);
        }

        @Specialization(guards = "signed != 0")
        Object doSignedLong(long n, @SuppressWarnings("unused") int signed,
                        @Cached("create()") CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(n);
        }

        @Specialization(guards = {"signed == 0", "n >= 0"})
        Object doUnsignedLongPositive(long n, @SuppressWarnings("unused") int signed,
                        @Cached("create()") CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(n);
        }

        @Specialization(guards = {"signed == 0", "n < 0"})
        Object doUnsignedLongNegative(long n, @SuppressWarnings("unused") int signed,
                        @Cached("create()") CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(factory().createInt(convertToBigInteger(n)));
        }

        @TruffleBoundary
        private static BigInteger convertToBigInteger(long n) {
            return BigInteger.valueOf(n).add(BigInteger.ONE.shiftLeft(64));
        }

        @Specialization
        Object doPointer(PythonNativeObject n, @SuppressWarnings("unused") int signed,
                        @Cached("create()") CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(factory().createNativeVoidPtr((TruffleObject) n.object));
        }
    }

    @Builtin(name = "PyLong_AsVoidPtr", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyLong_AsVoidPtr extends PythonUnaryBuiltinNode {
        @Child private TrufflePInt_AsPrimitive asPrimitiveNode;

        @Specialization
        long doPointer(int n) {
            return n;
        }

        @Specialization
        long doPointer(long n) {
            return n;
        }

        @Specialization
        long doPointer(PInt n,
                        @Cached("create()") BranchProfile overflowProfile) {
            try {
                return n.longValueExact();
            } catch (ArithmeticException e) {
                overflowProfile.enter();
                throw raise(OverflowError);
            }
        }

        @Specialization
        TruffleObject doPointer(PythonNativeVoidPtr n) {
            return n.object;
        }

        @Fallback
        long doGeneric(Object n) {
            if (asPrimitiveNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                asPrimitiveNode = insert(TrufflePInt_AsPrimitiveFactory.create(null));
            }
            return asPrimitiveNode.executeLong(n, 0, Long.BYTES, "void*");
        }
    }
}
