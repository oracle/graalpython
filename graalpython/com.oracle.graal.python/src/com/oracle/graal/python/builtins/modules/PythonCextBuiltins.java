/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IndexError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__SLOTS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;

import java.io.PrintWriter;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.AllocFuncRootNode;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.ExternalFunctionNode;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.GetAttrFuncRootNode;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.MethFastcallRoot;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.MethFastcallWithKeywordsRoot;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.MethKeywordsRoot;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.MethNoargsRoot;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.MethORoot;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.MethPowRootNode;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.MethReverseRootNode;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.MethRichcmpOpRootNode;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.MethVarargsRoot;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.RichCmpFuncRootNode;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.SSizeObjArgProcRootNode;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.SetAttrFuncRootNode;
import com.oracle.graal.python.builtins.modules.PythonCextBuiltinsFactory.CheckFunctionResultNodeGen;
import com.oracle.graal.python.builtins.modules.PythonCextBuiltinsFactory.GetByteArrayNodeGen;
import com.oracle.graal.python.builtins.modules.PythonCextBuiltinsFactory.TrufflePInt_AsPrimitiveFactory;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PIBytesLike;
import com.oracle.graal.python.builtins.objects.cext.CArrayWrappers.CByteArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.AllToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.BinaryFirstToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ConvertArgsToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.FastCallArgsToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.FastCallWithKeywordsArgsToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.GetNativeNullNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.MayRaiseBinaryNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.MayRaiseNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.MayRaiseNodeFactory;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.MayRaiseTernaryNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.MayRaiseUnaryNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.TernaryFirstSecondToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.TernaryFirstThirdToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.MayRaiseBinaryNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.MayRaiseTernaryNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.MayRaiseUnaryNodeGen;
import com.oracle.graal.python.builtins.objects.cext.DynamicObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.HandleCache;
import com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols;
import com.oracle.graal.python.builtins.objects.cext.PThreadState;
import com.oracle.graal.python.builtins.objects.cext.PyCFunctionDecorator;
import com.oracle.graal.python.builtins.objects.cext.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.PythonClassInitNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.PythonClassNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeNull;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeWrapperLibrary;
import com.oracle.graal.python.builtins.objects.cext.UnicodeObjectNodes.UnicodeAsWideCharNode;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.GetTracebackNode;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.set.PBaseSet;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetTypeFlagsNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.HasInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.PythonCallNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.datamodel.IsSequenceNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.string.StringLenNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.nodes.util.CastToByteNode;
import com.oracle.graal.python.nodes.util.CastToIndexNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.ExceptionUtils;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.exception.PythonExitException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.utilities.CyclicAssumption;

@CoreFunctions(defineModule = PythonCextBuiltins.PYTHON_CEXT)
@GenerateNodeFactory
public class PythonCextBuiltins extends PythonBuiltins {

    public static final String PYTHON_CEXT = "python_cext";

    private static final String ERROR_HANDLER = "error_handler";
    public static final String NATIVE_NULL = "native_null";

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PythonCextBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(PythonCore core) {
        super.initialize(core);
        PythonClass errorHandlerClass = core.factory().createPythonClass(PythonBuiltinClassType.PythonClass, "CErrorHandler",
                        new PythonAbstractClass[]{core.lookupType(PythonBuiltinClassType.PythonObject)});
        builtinConstants.put("CErrorHandler", errorHandlerClass);
        builtinConstants.put(ERROR_HANDLER, core.factory().createPythonObject(errorHandlerClass));
        builtinConstants.put(NATIVE_NULL, new PythonNativeNull());
    }

    /**
     * Called mostly from Python code to convert arguments into a wrapped representation for
     * consumption in Python or Java.
     */
    @Builtin(name = "to_java", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class AsPythonObjectNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object run(Object object,
                        @Cached CExtNodes.AsPythonObjectNode toJavaNode) {
            return toJavaNode.execute(object);
        }
    }

    @Builtin(name = "to_java_type", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class AsPythonClassNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object run(Object object,
                        @Cached("createForceClass()") CExtNodes.AsPythonObjectNode toJavaNode) {
            return toJavaNode.execute(object);
        }
    }

    /**
     * Called from C when they actually want a {@code const char*} for a Python string
     */
    @Builtin(name = "to_char_pointer", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class TruffleString_AsString extends NativeBuiltin {

        @Specialization(guards = "isString(str)")
        Object run(Object str,
                        @Cached("create()") CExtNodes.AsCharPointerNode asCharPointerNode) {
            return asCharPointerNode.execute(str);
        }

        @Fallback
        Object run(VirtualFrame frame, Object o) {
            return raiseNative(frame, PNone.NO_VALUE, PythonErrorType.SystemError, "Cannot convert object of type %p to C string.", o, o.getClass().getName());
        }
    }

    /**
     * This is used in the ExternalFunctionNode below, so all arguments passed from Python code into
     * a C function are automatically unwrapped if they are wrapped. This function is also called
     * all over the place in C code to make sure return values have the right representation in
     * Sulong land.
     */
    @Builtin(name = "to_sulong", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ToSulongNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object run(Object obj,
                        @Cached("create()") CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(obj);
        }
    }

    @Builtin(name = "PyTruffle_Type", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class PyTruffle_Type extends NativeBuiltin {

        private static final String[] LOOKUP_MODULES = new String[]{
                        PythonCextBuiltins.PYTHON_CEXT,
                        "_weakref",
                        "builtins"
        };

        @Specialization
        @TruffleBoundary
        Object doI(String typeName) {
            PythonCore core = getCore();
            for (PythonBuiltinClassType type : PythonBuiltinClassType.VALUES) {
                if (type.getName().equals(typeName)) {
                    return core.lookupType(type);
                }
            }
            for (String module : LOOKUP_MODULES) {
                Object attribute = core.lookupBuiltinModule(module).getAttribute(typeName);
                if (attribute != PNone.NO_VALUE) {
                    return attribute;
                }
            }
            throw raise(PythonErrorType.KeyError, "'%s'", typeName);
        }
    }

    @Builtin(name = "PyTuple_New", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTuple_New extends PythonUnaryBuiltinNode {
        @Specialization
        PTuple doGeneric(Object size,
                        @Cached CastToIndexNode castToIntNode) {
            return factory().createTuple(new Object[castToIntNode.execute(size)]);
        }
    }

    @Builtin(name = "PyTuple_SetItem", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyTuple_SetItem extends PythonTernaryBuiltinNode {
        @Specialization
        int doManaged(PTuple tuple, Object position, Object element,
                        @Cached("createSetItem()") SequenceStorageNodes.SetItemNode setItemNode) {
            setItemNode.execute(tuple.getSequenceStorage(), position, element);
            return 0;
        }

        @Specialization
        int doNative(PythonNativeObject tuple, long position, Object element,
                        @Cached PCallCapiFunction callSetItem,
                        @Cached CExtNodes.ToSulongNode receiverToSulongNode,
                        @Cached CExtNodes.ToSulongNode elementToSulongNode) {
            // TODO(fa): This path should be avoided since this is called from native code to do a
            // native operation.
            callSetItem.call(NativeCAPISymbols.FUN_PY_TRUFFLE_TUPLE_SET_ITEM, receiverToSulongNode.execute(tuple), position, elementToSulongNode.execute(element));
            return 0;
        }

        protected static SequenceStorageNodes.SetItemNode createSetItem() {
            return SequenceStorageNodes.SetItemNode.create(NormalizeIndexNode.forTupleAssign(), "invalid item for assignment");
        }
    }

    @Builtin(name = "CreateBuiltinMethod", minNumOfPositionalArgs = 2)
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
        private static final Signature SIGNATURE = Signature.createVarArgsAndKwArgsOnly();

        @Specialization(limit = "3")
        Object doPythonCallable(@SuppressWarnings("unused") String name, PythonNativeWrapper callable, @SuppressWarnings("unused") Object wrapper,
                        @SuppressWarnings("unused") LazyPythonClass type,
                        @CachedLibrary("callable") PythonNativeWrapperLibrary nativeWrapperLibrary) {
            // This can happen if a native type inherits slots from a managed type. Therefore,
            // something like 'base->tp_new' will be a wrapper of the managed '__new__'. So, in this
            // case, we assume that the object is already callable.
            return nativeWrapperLibrary.getDelegate(callable);
        }

        @Specialization(guards = "!isNativeWrapper(callable)")
        @TruffleBoundary
        PBuiltinFunction doNativeCallableWithType(String name, Object callable, PExternalFunctionWrapper wrapper, LazyPythonClass type,
                        @Shared("lang") @CachedLanguage PythonLanguage lang) {
            RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(createExternalFunctionRootNode(lang, name, callable, wrapper.createConvertArgsToSulongNode()));
            RootCallTarget wrappedCallTarget = wrapper.createCallTarget(lang, callTarget);
            return factory().createBuiltinFunction(name, type, 0, wrappedCallTarget);
        }

        @Specialization(guards = {"isNoValue(type)", "!isNativeWrapper(callable)"})
        @TruffleBoundary
        PBuiltinFunction doNativeCallableWithoutType(String name, Object callable, PExternalFunctionWrapper wrapper, @SuppressWarnings("unused") PNone type,
                        @Shared("lang") @CachedLanguage PythonLanguage lang) {
            return doNativeCallableWithType(name, callable, wrapper, null, lang);
        }

        @Specialization(guards = {"isNoValue(wrapper)", "!isNativeWrapper(callable)"})
        @TruffleBoundary
        PBuiltinFunction doNativeCallableWithoutWrapper(String name, Object callable, LazyPythonClass type, @SuppressWarnings("unused") PNone wrapper,
                        @Shared("lang") @CachedLanguage PythonLanguage lang) {
            RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(createExternalFunctionRootNode(lang, name, callable, AllToSulongNode.create()));
            return factory().createBuiltinFunction(name, type, 0, callTarget);
        }

        @Specialization(guards = {"isNoValue(wrapper)", "isNoValue(type)", "!isNativeWrapper(callable)"})
        @TruffleBoundary
        PBuiltinFunction doNativeCallableWithoutWrapperAndType(String name, Object callable, PNone wrapper, @SuppressWarnings("unused") PNone type,
                        @Shared("lang") @CachedLanguage PythonLanguage lang) {
            return doNativeCallableWithoutWrapper(name, callable, null, wrapper, lang);
        }

        static boolean isNativeWrapper(Object obj) {
            return obj instanceof PythonNativeWrapper;
        }

        private static ExternalFunctionNode createExternalFunctionRootNode(PythonLanguage lang, String name, Object callable, ConvertArgsToSulongNode convertArgsNode) {
            return ExternalFunctionNode.create(lang, name, callable, SIGNATURE, convertArgsNode);
        }
    }

    @Builtin(name = "PyErr_Restore", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyErrRestoreNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object run(VirtualFrame frame, PNone typ, PNone val, PNone tb) {
            getContext().setCurrentException(null);
            return PNone.NONE;
        }

        @Specialization
        Object run(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") LazyPythonClass typ, PBaseException val, PTraceback tb) {
            val.setTraceback(tb);
            assert tb.getPFrame().getRef().isEscaped() : "It's impossible to have an unescaped PFrame";
            if (val.getException() != null) {
                getContext().setCurrentException(val.getException());
            } else {
                PException pException = PException.fromObject(val, this);
                getContext().setCurrentException(pException);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "PyErr_Fetch", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class PyErrFetchNode extends NativeBuiltin {
        @Specialization
        public Object run(VirtualFrame frame, Object module,
                        @Exclusive @Cached GetClassNode getClassNode,
                        @Exclusive @Cached CExtNodes.GetNativeNullNode getNativeNullNode,
                        @Cached MaterializeFrameNode materializeNode,
                        @Cached GetTracebackNode getTracebackNode) {
            PythonContext context = getContext();
            PException currentException = context.getCurrentException();
            Object result;
            if (currentException == null) {
                result = getNativeNullNode.execute(module);
            } else {
                PBaseException exception = currentException.getExceptionObject();
                // There is almost no way this exception hasn't been reified if
                // it has to be. If it came from Python land, it's frame was
                // reified on the boundary to C. BUT, that being said, someone
                // could (since this is python_cext API) call this from Python
                // instead of sys.exc_info() and then it should also work. So we
                // do do it here if it hasn't been done already.
                if (!exception.hasTraceback()) {
                    PFrame escapedFrame = materializeNode.execute(frame, this, true, false);
                    exception.reifyException(escapedFrame, factory());
                }
                result = factory().createTuple(new Object[]{getClassNode.execute(exception), exception, getTracebackNode.execute(frame, exception)});
                context.setCurrentException(null);
            }
            return result;
        }
    }

    @Builtin(name = "PyErr_Occurred", minNumOfPositionalArgs = 0, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyErrOccurred extends PythonUnaryBuiltinNode {
        @Specialization
        Object run(Object errorMarker,
                        @Cached("create()") GetClassNode getClass) {
            PException currentException = getContext().getCurrentException();
            if (currentException != null) {
                PBaseException exceptionObject = currentException.getExceptionObject();
                return getClass.execute(exceptionObject);
            }
            return errorMarker;
        }
    }

    @Builtin(name = "PyErr_SetExcInfo", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyErrSetExcInfo extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object doClear(PNone typ, PNone val, PNone tb) {
            getContext().setCaughtException(PException.NO_EXCEPTION);
            return PNone.NONE;
        }

        @Specialization
        Object doFull(@SuppressWarnings("unused") Object typ, PBaseException val, PTraceback tb) {
            val.setTraceback(tb);
            assert tb == null || tb.getPFrame().getRef().isEscaped() : "It's impossible to have an unescaped PFrame";
            if (val.getException() != null) {
                getContext().setCaughtException(val.getException());
            } else {
                PException pException = PException.fromObject(val, this);
                getContext().setCaughtException(pException);
            }
            return PNone.NONE;
        }

        @Specialization
        Object doWithoutTraceback(@SuppressWarnings("unused") Object typ, PBaseException val, @SuppressWarnings("unused") PNone tb) {
            return doFull(typ, val, null);
        }

        @Fallback
        @SuppressWarnings("unused")
        Object doFallback(Object typ, Object val, Object tb) {
            // TODO we should still store the values to return them with 'PyErr_GetExcInfo' (or
            // 'sys.exc_info')
            return PNone.NONE;
        }
    }

    /**
     * Exceptions are usually printed using the traceback module or the hook function
     * {@code sys.excepthook}. This is the last resort if the hook function itself failed.
     */
    @Builtin(name = "PyErr_Display", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyErrDisplay extends PythonBuiltinNode {

        @Specialization
        @SuppressWarnings("unused")
        Object run(LazyPythonClass typ, PBaseException val, PTraceback tb) {
            if (val.getException() != null) {
                ExceptionUtils.printPythonLikeStackTrace(val.getException());
            }
            return PNone.NO_VALUE;
        }
    }

    @Builtin(name = "PyUnicode_FromString", minNumOfPositionalArgs = 1)
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

    @Builtin(name = "do_richcompare", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class RichCompareNode extends PythonTernaryBuiltinNode {
        protected static BinaryComparisonNode create(int op) {
            return BinaryComparisonNode.create(SpecialMethodNames.getCompareName(op), SpecialMethodNames.getCompareReversal(op), SpecialMethodNames.getCompareOpString(op));
        }

        @Specialization(guards = "op == 0")
        Object op0(VirtualFrame frame, Object a, Object b, @SuppressWarnings("unused") int op,
                        @Cached("create(op)") BinaryComparisonNode compNode) {
            return compNode.executeWith(frame, a, b);
        }

        @Specialization(guards = "op == 1")
        Object op1(VirtualFrame frame, Object a, Object b, @SuppressWarnings("unused") int op,
                        @Cached("create(op)") BinaryComparisonNode compNode) {
            return compNode.executeWith(frame, a, b);
        }

        @Specialization(guards = "op == 2")
        Object op2(VirtualFrame frame, Object a, Object b, @SuppressWarnings("unused") int op,
                        @Cached("create(op)") BinaryComparisonNode compNode) {
            return compNode.executeWith(frame, a, b);
        }

        @Specialization(guards = "op == 3")
        Object op3(VirtualFrame frame, Object a, Object b, @SuppressWarnings("unused") int op,
                        @Cached("create(op)") BinaryComparisonNode compNode) {
            return compNode.executeWith(frame, a, b);
        }

        @Specialization(guards = "op == 4")
        Object op4(VirtualFrame frame, Object a, Object b, @SuppressWarnings("unused") int op,
                        @Cached("create(op)") BinaryComparisonNode compNode) {
            return compNode.executeWith(frame, a, b);
        }

        @Specialization(guards = "op == 5")
        Object op5(VirtualFrame frame, Object a, Object b, @SuppressWarnings("unused") int op,
                        @Cached("create(op)") BinaryComparisonNode compNode) {
            return compNode.executeWith(frame, a, b);
        }
    }

    @Builtin(name = "PyTruffle_SetAttr", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyObject_Setattr extends PythonTernaryBuiltinNode {
        @Specialization
        Object doBuiltinClass(PythonBuiltinClass object, String key, Object value,
                        @Exclusive @Cached("createForceType()") WriteAttributeToObjectNode writeAttrNode) {
            writeAttrNode.execute(object, key, value);
            return PNone.NONE;
        }

        @Specialization
        Object doNativeClass(PythonNativeClass object, String key, Object value,
                        @Exclusive @Cached("createForceType()") WriteAttributeToObjectNode writeAttrNode) {
            writeAttrNode.execute(object, key, value);
            return PNone.NONE;
        }

        @Specialization(guards = {"!isPythonBuiltinClass(object)"})
        Object doObject(PythonObject object, String key, Object value,
                        @Exclusive @Cached WriteAttributeToDynamicObjectNode writeAttrToDynamicObjectNode) {
            writeAttrToDynamicObjectNode.execute(object.getStorage(), key, value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "PyTruffle_Type_Slots", minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class PyTruffle_Type_SlotsNode extends NativeBuiltin {

        /**
         * A native class may inherit from a managed class. However, the managed class may define
         * custom slots at a time where the C API is not yet loaded. So we need to check if any of
         * the base classes defines custom slots and adapt the basicsize to allocate space for the
         * slots and add the native member slot descriptors.
         *
         */
        @Specialization
        Object slots(Object module, LazyPythonClass pythonClass,
                        @Exclusive @Cached LookupAttributeInMRONode.Dynamic lookupSlotsNode,
                        @Exclusive @Cached CExtNodes.GetNativeNullNode getNativeNullNode) {
            Object execute = lookupSlotsNode.execute(pythonClass, __SLOTS__);
            if (execute != PNone.NO_VALUE) {
                return execute;
            }
            return getNativeNullNode.execute(module);
        }
    }

    // roughly equivalent to _Py_CheckFunctionResult in Objects/call.c
    @ImportStatic(PGuards.class)
    abstract static class CheckFunctionResultNode extends PNodeWithContext {
        public abstract Object execute(String name, Object result);

        @Specialization(limit = "1")
        Object doNativeWrapper(String name, DynamicObjectNativeWrapper.PythonObjectNativeWrapper result,
                        @CachedLibrary(value = "result") PythonNativeWrapperLibrary lib,
                        @Cached("create()") CheckFunctionResultNode recursive) {
            return recursive.execute(name, lib.getDelegate(result));
        }

        @Specialization(guards = "!isPythonObjectNativeWrapper(result)")
        Object doPrimitiveWrapper(String name, @SuppressWarnings("unused") PythonNativeWrapper result,
                        @Shared("ctxt") @CachedContext(PythonLanguage.class) PythonContext context,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raise") @Cached PRaiseNode raise) {
            checkFunctionResult(name, false, false, context, raise, factory);
            return result;
        }

        @Specialization(guards = "isNoValue(result)")
        Object doNoValue(String name, @SuppressWarnings("unused") PNone result,
                        @Shared("ctxt") @CachedContext(PythonLanguage.class) PythonContext context,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raise") @Cached PRaiseNode raise) {
            checkFunctionResult(name, true, false, context, raise, factory);
            return PNone.NO_VALUE;
        }

        @Specialization(guards = "!isNoValue(result)")
        Object doPythonObject(String name, @SuppressWarnings("unused") PythonAbstractObject result,
                        @Shared("ctxt") @CachedContext(PythonLanguage.class) PythonContext context,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raise") @Cached PRaiseNode raise) {
            checkFunctionResult(name, false, false, context, raise, factory);
            return result;
        }

        @Specialization
        Object doPythonNativeNull(String name, @SuppressWarnings("unused") PythonNativeNull result,
                        @Shared("ctxt") @CachedContext(PythonLanguage.class) PythonContext context,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raise") @Cached PRaiseNode raise) {
            checkFunctionResult(name, true, false, context, raise, factory);
            return result;
        }

        @Specialization
        int doInteger(String name, int result,
                        @Shared("ctxt") @CachedContext(PythonLanguage.class) PythonContext context,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raise") @Cached PRaiseNode raise) {
            // If the native functions returns a primitive int, only a value '-1' indicates an
            // error.
            checkFunctionResult(name, result == -1, true, context, raise, factory);
            return result;
        }

        @Specialization
        long doLong(String name, long result,
                        @Shared("ctxt") @CachedContext(PythonLanguage.class) PythonContext context,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raise") @Cached PRaiseNode raise) {
            // If the native functions returns a primitive int, only a value '-1' indicates an
            // error.
            checkFunctionResult(name, result == -1, true, context, raise, factory);
            return result;
        }

        /*
         * Our fallback case, but with some cached params. PythonObjectNativeWrapper results should
         * be unwrapped and recursively delegated (see #doNativeWrapper) and PNone is treated
         * specially, because we consider it as null in #doNoValue and as not null in
         * #doPythonObject
         */
        @Specialization(guards = {"!isPythonObjectNativeWrapper(result)", "!isPNone(result)"})
        Object doForeign(String name, Object result,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile isNullProfile,
                        @Exclusive @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Shared("ctxt") @CachedContext(PythonLanguage.class) PythonContext context,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raise") @Cached PRaiseNode raise) {
            checkFunctionResult(name, isNullProfile.profile(lib.isNull(result)), false, context, raise, factory);
            return result;
        }

        private void checkFunctionResult(String name, boolean indicatesError, boolean isPrimitiveResult, PythonContext context, PRaiseNode raise, PythonObjectFactory factory) {
            PException currentException = context.getCurrentException();
            boolean errOccurred = currentException != null;
            if (indicatesError) {
                // consume exception
                context.setCurrentException(null);
                if (!errOccurred && !isPrimitiveResult) {
                    throw raise.raise(PythonErrorType.SystemError, "%s returned NULL without setting an error", name);
                } else {
                    throw currentException;
                }
            } else if (errOccurred) {
                // consume exception
                context.setCurrentException(null);
                PBaseException sysExc = factory.createBaseException(PythonErrorType.SystemError, "%s returned a result with an error set", new Object[]{name});
                // the exception here must have already been reified, because we
                // got it from the context
                sysExc.setAttribute(SpecialAttributeNames.__CAUSE__, currentException.getExceptionObject());
                throw PException.fromObject(sysExc, this);
            }
        }

        protected static boolean isNativeNull(TruffleObject object) {
            return object instanceof PythonNativeNull;
        }

        protected static boolean isPythonObjectNativeWrapper(Object object) {
            return object instanceof DynamicObjectNativeWrapper.PythonObjectNativeWrapper;
        }

        public static CheckFunctionResultNode create() {
            return CheckFunctionResultNodeGen.create();
        }
    }

    @Builtin(name = "Py_NoValue", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class Py_NoValue extends PythonBuiltinNode {
        @Specialization
        PNone doNoValue() {
            return PNone.NO_VALUE;
        }
    }

    @Builtin(name = "Py_None", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class PyNoneNode extends PythonBuiltinNode {
        @Specialization
        PNone doNativeNone() {
            return PNone.NONE;
        }
    }

    @TypeSystemReference(PythonTypes.class)
    abstract static class NativeBuiltin extends PythonBuiltinNode {

        protected void transformToNative(VirtualFrame frame, PException p) {
            NativeBuiltin.transformToNative(getContext(), PArguments.getCurrentFrameInfo(frame), p);
        }

        protected static void transformToNative(PythonContext context, VirtualFrame frame, PException p) {
            NativeBuiltin.transformToNative(context, PArguments.getCurrentFrameInfo(frame), p);
        }

        protected static void transformToNative(PythonContext context, PFrame.Reference frameInfo, PException p) {
            p.getExceptionObject().reifyException(frameInfo);
            context.setCurrentException(p);
        }

        protected <T> T raiseNative(VirtualFrame frame, T defaultValue, PythonBuiltinClassType errType, String fmt, Object... args) {
            return NativeBuiltin.raiseNative(this, PArguments.getCurrentFrameInfo(frame), defaultValue, errType, fmt, args);
        }

        protected static <T> T raiseNative(PythonBuiltinBaseNode n, VirtualFrame frame, T defaultValue, PythonBuiltinClassType errType, String fmt, Object... args) {
            return NativeBuiltin.raiseNative(n, PArguments.getCurrentFrameInfo(frame), defaultValue, errType, fmt, args);
        }

        protected static <T> T raiseNative(PythonBuiltinBaseNode n, PFrame.Reference frameInfo, T defaultValue, PythonBuiltinClassType errType, String fmt, Object... args) {
            try {
                throw n.raise(errType, fmt, args);
            } catch (PException p) {
                NativeBuiltin.transformToNative(n.getContext(), frameInfo, p);
                return defaultValue;
            }
        }

        protected Object raiseBadArgument(VirtualFrame frame, Object errorMarker) {
            return raiseNative(frame, errorMarker, PythonErrorType.TypeError, "bad argument type for built-in operation");
        }

        @TruffleBoundary(allowInlining = true)
        protected static ByteBuffer wrap(byte[] data) {
            return ByteBuffer.wrap(data);
        }

        @TruffleBoundary(allowInlining = true)
        protected static ByteBuffer wrap(byte[] data, int offset, int length) {
            return ByteBuffer.wrap(data, offset, length);
        }
    }

    abstract static class NativeUnicodeBuiltin extends NativeBuiltin {
        private static final int NATIVE_ORDER = 0;
        private static Charset UTF32;
        private static Charset UTF32LE;
        private static Charset UTF32BE;

        @TruffleBoundary
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

        @TruffleBoundary
        protected static CharBuffer allocateCharBuffer(int cap) {
            return CharBuffer.allocate(cap);
        }

        @TruffleBoundary
        protected static String toString(CharBuffer cb) {
            return cb.toString();
        }

        @TruffleBoundary
        protected static int remaining(ByteBuffer cb) {
            return cb.remaining();
        }

    }

    @Builtin(name = "TrufflePInt_AsPrimitive", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class TrufflePInt_AsPrimitive extends PythonTernaryBuiltinNode {

        public abstract Object executeWith(VirtualFrame frame, Object o, int signed, long targetTypeSize);

        public abstract long executeLong(VirtualFrame frame, Object o, int signed, long targetTypeSize);

        public abstract int executeInt(VirtualFrame frame, Object o, int signed, long targetTypeSize);

        @Specialization(guards = "targetTypeSize == 4")
        int doInt4(int obj, @SuppressWarnings("unused") int signed, @SuppressWarnings("unused") long targetTypeSize) {
            return obj;
        }

        @Specialization(guards = "targetTypeSize == 8")
        long doInt8(int obj, int signed, @SuppressWarnings("unused") long targetTypeSize) {
            if (signed != 0) {
                return obj;
            } else {
                return obj & 0xFFFFFFFFL;
            }
        }

        @Specialization(guards = {"targetTypeSize != 4", "targetTypeSize != 8"})
        int doIntOther(VirtualFrame frame, @SuppressWarnings("unused") int obj, @SuppressWarnings("unused") int signed, long targetTypeSize) {
            return raiseUnsupportedSize(frame, targetTypeSize);
        }

        @Specialization(guards = "targetTypeSize == 4")
        int doLong4(VirtualFrame frame, @SuppressWarnings("unused") long obj, @SuppressWarnings("unused") int signed, @SuppressWarnings("unused") long targetTypeSize) {
            return raiseTooLarge(frame, targetTypeSize);
        }

        @Specialization(guards = "targetTypeSize == 8")
        long doLong8(long obj, @SuppressWarnings("unused") int signed, @SuppressWarnings("unused") int targetTypeSize) {
            return obj;
        }

        @Specialization(guards = "targetTypeSize == 8")
        long doLong8(long obj, @SuppressWarnings("unused") int signed, @SuppressWarnings("unused") long targetTypeSize) {
            return obj;
        }

        @Specialization(guards = "targetTypeSize == 8")
        Object doVoid(PythonNativeVoidPtr obj, @SuppressWarnings("unused") int signed, @SuppressWarnings("unused") long targetTypeSize) {
            return obj;
        }

        @Specialization(guards = {"targetTypeSize != 4", "targetTypeSize != 8"})
        int doPInt(VirtualFrame frame, @SuppressWarnings("unused") long obj, @SuppressWarnings("unused") int signed, long targetTypeSize) {
            return raiseUnsupportedSize(frame, targetTypeSize);
        }

        @Specialization(guards = "targetTypeSize == 4")
        int doPInt4(VirtualFrame frame, PInt obj, int signed, @SuppressWarnings("unused") long targetTypeSize) {
            try {
                if (signed != 0) {
                    return obj.intValueExact();
                } else if (obj.bitCount() <= 32) {
                    return obj.intValue();
                } else {
                    throw new ArithmeticException();
                }
            } catch (ArithmeticException e) {
                return raiseTooLarge(frame, targetTypeSize);
            }
        }

        @Specialization(guards = "targetTypeSize == 8")
        long doPInt8(VirtualFrame frame, PInt obj, int signed, @SuppressWarnings("unused") long targetTypeSize) {
            try {
                if (signed != 0) {
                    return obj.longValueExact();
                } else if (obj.bitCount() <= 64) {
                    return obj.longValue();
                } else {
                    throw new ArithmeticException();
                }
            } catch (ArithmeticException e) {
                return raiseTooLarge(frame, targetTypeSize);
            }
        }

        @Specialization(guards = {"targetTypeSize != 4", "targetTypeSize != 8"})
        int doPInt(VirtualFrame frame, @SuppressWarnings("unused") PInt obj, @SuppressWarnings("unused") int signed, long targetTypeSize) {
            return raiseUnsupportedSize(frame, targetTypeSize);
        }

        @Specialization(guards = {"!isInteger(obj)", "!isPInt(obj)"})
        int doGeneric(VirtualFrame frame, Object obj, @SuppressWarnings("unused") boolean signed, @SuppressWarnings("unused") int targetTypeSize) {
            return raiseNative(frame, -1, PythonErrorType.TypeError, "an integer is required", obj);
        }

        private int raiseTooLarge(VirtualFrame frame, long targetTypeSize) {
            return raiseNative(frame, -1, PythonErrorType.OverflowError, "Python int too large to convert to %s-byte C type", targetTypeSize);
        }

        private Integer raiseUnsupportedSize(VirtualFrame frame, long targetTypeSize) {
            return raiseNative(frame, -1, PythonErrorType.SystemError, "Unsupported target size: %d", targetTypeSize);
        }

        private <T> T raiseNative(VirtualFrame frame, T defaultValue, PythonBuiltinClassType errType, String fmt, Object... args) {
            return NativeBuiltin.raiseNative(this, PArguments.getCurrentFrameInfo(frame), defaultValue, errType, fmt, args);
        }
    }

    @Builtin(name = "PyTruffle_Unicode_FromWchar", minNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(PythonOptions.class)
    abstract static class PyTruffle_Unicode_FromWchar extends NativeUnicodeBuiltin {
        @Specialization(guards = "elementSize == cachedElementSize", limit = "getVariableArgumentInlineCacheLimit()")
        Object doBytes(VirtualFrame frame, Object arr, long n, long elementSize, Object errorMarker,
                        @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Cached("elementSize") long cachedElementSize,
                        @CachedLibrary("arr") InteropLibrary lib,
                        @CachedLibrary(limit = "1") InteropLibrary elemLib) {
            try {
                ByteBuffer bytes;
                if (cachedElementSize == 1L || cachedElementSize == 2L || cachedElementSize == 4L) {
                    if (!lib.hasArrayElements(arr)) {
                        return raiseNative(frame, errorMarker, PythonErrorType.SystemError, "provided object is not an array", elementSize);
                    }
                    bytes = readWithSize(lib, elemLib, arr, PInt.intValueExact(n), (int) cachedElementSize);
                    bytes.flip();
                } else {
                    return raiseNative(frame, errorMarker, PythonErrorType.ValueError, "unsupported 'wchar_t' size; was: %d", elementSize);
                }
                return toSulongNode.execute(decode(bytes));
            } catch (ArithmeticException e) {
                return raiseNative(frame, errorMarker, PythonErrorType.ValueError, "array size too large");
            } catch (CharacterCodingException e) {
                return raiseNative(frame, errorMarker, PythonErrorType.UnicodeError, "%m", e);
            } catch (IllegalArgumentException e) {
                return raiseNative(frame, errorMarker, PythonErrorType.LookupError, "%m", e);
            } catch (InteropException e) {
                return raiseNative(frame, errorMarker, PythonErrorType.TypeError, "%m", e);
            } catch (IllegalElementTypeException e) {
                return raiseNative(frame, errorMarker, PythonErrorType.UnicodeDecodeError, "Invalid input element type '%p'", e.elem);
            }
        }

        @Specialization(limit = "getVariableArgumentInlineCacheLimit()")
        Object doBytes(VirtualFrame frame, Object arr, PInt n, PInt elementSize, Object errorMarker,
                        @Cached CExtNodes.ToSulongNode toSulongNode,
                        @CachedLibrary("arr") InteropLibrary lib,
                        @CachedLibrary(limit = "1") InteropLibrary elemLib) {
            try {
                long es = elementSize.longValueExact();
                return doBytes(frame, arr, n.longValueExact(), es, errorMarker, toSulongNode, es, lib, elemLib);
            } catch (ArithmeticException e) {
                return raiseNative(frame, errorMarker, PythonErrorType.ValueError, "invalid parameters");
            }
        }

        @TruffleBoundary
        private static String decode(ByteBuffer bytes) throws CharacterCodingException {
            return getUTF32Charset(0).newDecoder().decode(bytes).toString();
        }

        private static ByteBuffer readWithSize(InteropLibrary arrLib, InteropLibrary elemLib, Object o, int size, int elementSize)
                        throws UnsupportedMessageException, InvalidArrayIndexException, IllegalElementTypeException {
            ByteBuffer buf = allocate(size * Integer.BYTES);
            for (int i = 0; i < size; i += elementSize) {
                putInt(buf, readElement(arrLib, elemLib, o, i, elementSize));
            }
            return buf;
        }

        @ExplodeLoop
        private static int readElement(InteropLibrary arrLib, InteropLibrary elemLib, Object arr, int i, int elementSize)
                        throws InvalidArrayIndexException, UnsupportedMessageException, IllegalElementTypeException {
            byte[] barr = new byte[4];
            for (int j = 0; j < elementSize; j++) {
                Object elem = arrLib.readArrayElement(arr, i + j);
                // The array object could be one of our wrappers (e.g. 'PySequenceArrayWrapper').
                // Since the Interop library does not allow to specify how many bytes we want to
                // read when we do readArrayElement, our wrappers always return long. So, we check
                // for 'long' here and cast down to 'byte'.
                if (elemLib.fitsInLong(elem)) {
                    barr[j] = (byte) elemLib.asLong(elem);
                } else {
                    CompilerDirectives.transferToInterpreter();
                    throw new IllegalElementTypeException(elem);
                }
            }
            return toInt(barr);
        }

        @TruffleBoundary(allowInlining = true)
        private static int toInt(byte[] barr) {
            return ByteBuffer.wrap(barr).order(ByteOrder.LITTLE_ENDIAN).getInt();
        }

        @TruffleBoundary(allowInlining = true)
        private static ByteBuffer allocate(int cap) {
            return ByteBuffer.allocate(cap);
        }

        @TruffleBoundary(allowInlining = true)
        private static void putInt(ByteBuffer buf, int element) {
            buf.putInt(element);
        }

        private static final class IllegalElementTypeException extends Exception {
            private static final long serialVersionUID = 0L;
            private final Object elem;

            IllegalElementTypeException(Object elem) {
                this.elem = elem;
            }
        }
    }

    @Builtin(name = "PyTruffle_Unicode_FromUTF8", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTruffle_Unicode_FromUTF8 extends NativeBuiltin {

        @Specialization
        Object doBytes(VirtualFrame frame, TruffleObject o, Object errorMarker,
                        @Exclusive @Cached GetByteArrayNode getByteArrayNode) {
            try {
                return decodeUTF8(getByteArrayNode.execute(frame, o, -1));
            } catch (CharacterCodingException e) {
                return raiseNative(frame, errorMarker, PythonErrorType.UnicodeError, "%m", e);
            } catch (InteropException e) {
                return raiseNative(frame, errorMarker, PythonErrorType.TypeError, "%m", e);
            }
        }

        @TruffleBoundary
        private static String decodeUTF8(byte[] data) throws CharacterCodingException {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
            return decoder.decode(wrap(data)).toString();
        }
    }

    abstract static class NativeEncoderNode extends NativeBuiltin {
        private final Charset charset;

        protected NativeEncoderNode(Charset charset) {
            this.charset = charset;
        }

        @Specialization(guards = "isNoValue(errors)")
        Object doUnicode(VirtualFrame frame, PString s, @SuppressWarnings("unused") PNone errors, Object error_marker) {
            return doUnicode(frame, s, "strict", error_marker);
        }

        @Specialization
        Object doUnicode(VirtualFrame frame, PString s, String errors, Object error_marker) {
            try {
                return factory().createBytes(doEncode(s, errors));
            } catch (PException e) {
                transformToNative(frame, e);
                return error_marker;
            } catch (CharacterCodingException e) {
                return raiseNative(frame, error_marker, PythonErrorType.UnicodeEncodeError, "%m", e);
            }
        }

        @Fallback
        Object doUnicode(VirtualFrame frame, @SuppressWarnings("unused") Object s, @SuppressWarnings("unused") Object errors, Object errorMarker) {
            return raiseBadArgument(frame, errorMarker);
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        private byte[] doEncode(PString s, String errors) throws CharacterCodingException {
            CharsetEncoder encoder = charset.newEncoder();
            CodingErrorAction action = BytesBuiltins.toCodingErrorAction(errors, this);
            encoder.onMalformedInput(action).onUnmappableCharacter(action);
            CharBuffer buf = CharBuffer.allocate(StringLenNode.getUncached().execute(s));
            buf.put(s.getValue());
            buf.flip();
            ByteBuffer encoded = encoder.encode(buf);
            byte[] barr = new byte[encoded.remaining()];
            encoded.get(barr);
            return barr;
        }
    }

    @Builtin(name = "_PyUnicode_AsUTF8String", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class _PyUnicode_AsUTF8String extends NativeEncoderNode {

        protected _PyUnicode_AsUTF8String() {
            super(StandardCharsets.UTF_8);
        }
    }

    @Builtin(name = "_PyTruffle_Unicode_AsLatin1String", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class _PyTruffle_Unicode_AsLatin1String extends NativeEncoderNode {
        protected _PyTruffle_Unicode_AsLatin1String() {
            super(StandardCharsets.ISO_8859_1);
        }
    }

    @Builtin(name = "_PyTruffle_Unicode_AsASCIIString", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class _PyTruffle_Unicode_AsASCIIString extends NativeEncoderNode {
        protected _PyTruffle_Unicode_AsASCIIString() {
            super(StandardCharsets.US_ASCII);
        }
    }

    @Builtin(name = "PyTruffle_Unicode_AsUnicodeAndSize", minNumOfPositionalArgs = 3)
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

    @Builtin(name = "PyTruffle_Unicode_DecodeUTF32", minNumOfPositionalArgs = 5)
    @GenerateNodeFactory
    abstract static class PyTruffle_Unicode_DecodeUTF32 extends NativeUnicodeBuiltin {

        @Specialization(guards = "isNoValue(errors)")
        Object doUnicode(VirtualFrame frame, TruffleObject o, long size, @SuppressWarnings("unused") PNone errors, int byteorder, Object errorMarker,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Shared("getByteArrayNode") @Cached GetByteArrayNode getByteArrayNode) {
            return doUnicode(frame, o, size, "strict", byteorder, errorMarker, toSulongNode, getByteArrayNode);
        }

        @Specialization
        Object doUnicode(VirtualFrame frame, TruffleObject o, long size, String errors, int byteorder, Object errorMarker,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Shared("getByteArrayNode") @Cached GetByteArrayNode getByteArrayNode) {
            try {
                return toSulongNode.execute(decodeUTF32(getByteArrayNode.execute(frame, o, size), (int) size, errors, byteorder));
            } catch (CharacterCodingException e) {
                return raiseNative(frame, errorMarker, PythonErrorType.UnicodeEncodeError, "%m", e);
            } catch (IllegalArgumentException e) {
                String csName = getUTF32Name(byteorder);
                return raiseNative(frame, errorMarker, PythonErrorType.LookupError, "unknown encoding: " + csName);
            } catch (InteropException e) {
                return raiseNative(frame, errorMarker, PythonErrorType.TypeError, "%m", e);
            }
        }

        @TruffleBoundary
        private String decodeUTF32(byte[] data, int size, String errors, int byteorder) throws CharacterCodingException {
            CharsetDecoder decoder = getUTF32Charset(byteorder).newDecoder();
            CodingErrorAction action = BytesBuiltins.toCodingErrorAction(errors, this);
            CharBuffer decode = decoder.onMalformedInput(action).onUnmappableCharacter(action).decode(wrap(data, 0, size));
            return decode.toString();
        }
    }

    abstract static class GetByteArrayNode extends PNodeWithContext {

        public abstract byte[] execute(VirtualFrame frame, Object obj, long n) throws InteropException;

        public static GetByteArrayNode create() {
            return GetByteArrayNodeGen.create();
        }

        private static byte[] subRangeIfNeeded(byte[] ary, long n) {
            if (ary.length > n && n >= 0 && n < Integer.MAX_VALUE) {
                return Arrays.copyOf(ary, (int) n);
            } else {
                return ary;
            }
        }

        @Specialization(limit = "1")
        byte[] doCArrayWrapper(CByteArrayWrapper o, long n,
                        @CachedLibrary("o") PythonNativeWrapperLibrary lib) {
            return subRangeIfNeeded(o.getByteArray(lib), n);
        }

        @Specialization(limit = "1")
        byte[] doSequenceArrayWrapper(VirtualFrame frame, PySequenceArrayWrapper obj, long n,
                        @CachedLibrary(value = "obj") PythonNativeWrapperLibrary lib,
                        @Cached("create()") BytesNodes.ToBytesNode toBytesNode) {
            return subRangeIfNeeded(toBytesNode.execute(frame, lib.getDelegate(obj)), n);
        }

        @Specialization(limit = "5")
        byte[] doForeign(Object obj, long n,
                        @Cached("createBinaryProfile()") ConditionProfile profile,
                        @CachedLibrary("obj") InteropLibrary interopLib,
                        @Cached CastToByteNode castToByteNode) throws InteropException {
            long size;
            if (profile.profile(n < 0)) {
                size = interopLib.getArraySize(obj);
            } else {
                size = n;
            }
            return readWithSize(interopLib, castToByteNode, obj, size);
        }

        private static byte[] readWithSize(InteropLibrary interopLib, CastToByteNode castToByteNode, Object o, long size) throws UnsupportedMessageException, InvalidArrayIndexException {
            byte[] bytes = new byte[(int) size];
            for (long i = 0; i < size; i++) {
                Object elem = interopLib.readArrayElement(o, i);
                bytes[(int) i] = castToByteNode.execute(elem);
            }
            return bytes;
        }
    }

    @Builtin(name = "PyTruffle_Unicode_AsWideChar", minNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class PyTruffle_Unicode_AsWideChar extends NativeUnicodeBuiltin {
        @Child private UnicodeAsWideCharNode asWideCharNode;

        @Specialization
        Object doUnicode(VirtualFrame frame, String s, long elementSize, @SuppressWarnings("unused") PNone elements, Object errorMarker) {
            return doUnicode(frame, s, elementSize, -1, errorMarker);
        }

        @Specialization
        Object doUnicode(VirtualFrame frame, String s, long elementSize, long elements, Object errorMarker) {
            try {
                if (asWideCharNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    asWideCharNode = insert(UnicodeAsWideCharNode.createLittleEndian());
                }

                PBytes wchars = asWideCharNode.execute(s, elementSize, elements);
                if (wchars != null) {
                    return wchars;
                } else {
                    return raiseNative(frame, errorMarker, PythonErrorType.ValueError, "unsupported wchar size; was: %d", elementSize);
                }
            } catch (IllegalArgumentException e) {
                // TODO
                return raiseNative(frame, errorMarker, PythonErrorType.LookupError, "%m", e);
            }
        }

        @Specialization
        Object doUnicode(VirtualFrame frame, String s, PInt elementSize, @SuppressWarnings("unused") PNone elements, Object errorMarker) {
            try {
                return doUnicode(frame, s, elementSize.longValueExact(), -1, errorMarker);
            } catch (ArithmeticException e) {
                return raiseNative(frame, errorMarker, PythonErrorType.ValueError, "invalid parameters");
            }
        }

        @Specialization
        Object doUnicode(VirtualFrame frame, String s, PInt elementSize, PInt elements, Object errorMarker) {
            try {
                return doUnicode(frame, s, elementSize.longValueExact(), elements.longValueExact(), errorMarker);
            } catch (ArithmeticException e) {
                return raiseNative(frame, errorMarker, PythonErrorType.ValueError, "invalid parameters");
            }
        }
    }

    @Builtin(name = "PyTruffle_Bytes_AsString", minNumOfPositionalArgs = 2)
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
        Object doUnicode(VirtualFrame frame, Object o, Object errorMarker) {
            return raiseNative(frame, errorMarker, PythonErrorType.TypeError, "expected bytes, %p found", o);
        }
    }

    @Builtin(name = "PyHash_Imag", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class PyHashImagNode extends PythonBuiltinNode {
        @Specialization
        long getHash() {
            return PComplex.IMAG_MULTIPLIER;
        }
    }

    @Builtin(name = "PyTruffleFrame_New", minNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class PyTruffleFrameNewNode extends PythonBuiltinNode {
        @Specialization
        Object newFrame(Object threadState, PCode code, PythonObject globals, Object locals) {
            return factory().createPFrame(threadState, code, globals, locals);
        }
    }

    @Builtin(name = "PyTruffleTraceBack_Here", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTruffleTraceBackHereNode extends PythonBinaryBuiltinNode {
        @Specialization
        PTraceback tbHere(PFrame frame, PTraceback tb) {
            return factory().createTraceback(frame, tb);
        }
    }

    @Builtin(name = "PyTruffle_GetTpFlags", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffle_GetTpFlags extends NativeBuiltin {

        @Child private GetTypeFlagsNode getTypeFlagsNode;
        @Child private GetClassNode getClassNode;

        @Specialization(limit = "1")
        long doPythonObject(PythonNativeWrapper nativeWrapper,
                        @CachedLibrary("nativeWrapper") PythonNativeWrapperLibrary lib) {
            PythonAbstractClass pclass = getGetClassNode().execute(lib.getDelegate(nativeWrapper));
            return getGetTypeFlagsNode().execute(pclass);
        }

        @Specialization
        long doPythonObject(PythonAbstractObject object) {
            PythonAbstractClass pclass = getGetClassNode().execute(object);
            return getGetTypeFlagsNode().execute(pclass);
        }

        private GetClassNode getGetClassNode() {
            if (getClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getClassNode = insert(GetClassNode.create());
            }
            return getClassNode;
        }

        private GetTypeFlagsNode getGetTypeFlagsNode() {
            if (getTypeFlagsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getTypeFlagsNode = insert(GetTypeFlagsNode.create());
            }
            return getTypeFlagsNode;
        }
    }

    @Builtin(name = "PyTruffle_Set_SulongType", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTruffle_Set_SulongType extends NativeBuiltin {

        @Specialization(limit = "1")
        Object doPythonObject(PythonClassNativeWrapper klass, Object ptr,
                        @CachedLibrary("klass") PythonNativeWrapperLibrary lib) {
            ((PythonManagedClass) lib.getDelegate(klass)).setSulongType(ptr);
            return ptr;
        }
    }

    @Builtin(name = "PyTruffle_SetBufferProcs", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyTruffle_SetBufferProcs extends NativeBuiltin {

        @Specialization
        Object doNativeWrapper(PythonClassNativeWrapper nativeWrapper, Object getBufferProc, Object releaseBufferProc) {
            nativeWrapper.setGetBufferProc(getBufferProc);
            nativeWrapper.setReleaseBufferProc(releaseBufferProc);
            return PNone.NO_VALUE;
        }

        @Specialization
        Object doPythonObject(PythonManagedClass obj, Object getBufferProc, Object releaseBufferProc) {
            return doNativeWrapper(obj.getClassNativeWrapper(), getBufferProc, releaseBufferProc);
        }
    }

    @Builtin(name = "PyThreadState_Get", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class PyThreadState_Get extends NativeBuiltin {

        @Specialization
        PThreadState get() {
            // does not require a 'to_sulong' since it is already a native wrapper type
            return getContext().getCustomThreadState();
        }
    }

    @Builtin(name = "PyTruffle_GetSetDescriptor", parameterNames = {"fget", "fset", "name", "owner"})
    @GenerateNodeFactory
    public abstract static class GetSetDescriptorNode extends PythonBuiltinNode {
        @Specialization(guards = {"!isNoValue(get)", "!isNoValue(set)"})
        Object call(Object get, Object set, String name, LazyPythonClass owner) {
            return factory().createGetSetDescriptor(get, set, name, owner);
        }

        @Specialization(guards = {"!isNoValue(get)", "isNoValue(set)"})
        Object call(Object get, @SuppressWarnings("unused") PNone set, String name, LazyPythonClass owner) {
            return factory().createGetSetDescriptor(get, null, name, owner);
        }

        @Specialization(guards = {"isNoValue(get)", "!isNoValue(set)"})
        Object call(@SuppressWarnings("unused") PNone get, Object set, String name, LazyPythonClass owner) {
            return factory().createGetSetDescriptor(null, set, name, owner);
        }
    }

    @Builtin(name = "PyTruffle_SeqIter_New", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class SeqIterNewNode extends PythonBuiltinNode {
        @Specialization
        PSequenceIterator call(Object seq) {
            return factory().createSequenceIterator(seq);
        }
    }

    @Builtin(name = "PyTruffle_BuiltinMethod", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class BuiltinMethodNode extends PythonBuiltinNode {
        @Specialization
        Object call(Object self, PBuiltinFunction function) {
            return factory().createBuiltinMethod(self, function);
        }
    }

    public abstract static class PExternalFunctionWrapper extends PythonBuiltinObject {

        private final Supplier<ConvertArgsToSulongNode> convertArgsNodeSupplier;

        public PExternalFunctionWrapper(LazyPythonClass cls, Supplier<ConvertArgsToSulongNode> convertArgsNodeSupplier) {
            super(cls);
            this.convertArgsNodeSupplier = convertArgsNodeSupplier;
        }

        protected abstract RootCallTarget createCallTarget(PythonLanguage language, RootCallTarget callTarget);

        protected static RootCallTarget createCallTarget(RootNode n) {
            return Truffle.getRuntime().createCallTarget(n);
        }

        protected ConvertArgsToSulongNode createConvertArgsToSulongNode() {
            return convertArgsNodeSupplier.get();
        }
    }

    @Builtin(name = "METH_DIRECT", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class MethDirectNode extends PythonBuiltinNode {
        @Specialization
        PExternalFunctionWrapper call() {
            return new PExternalFunctionWrapper(PythonBuiltinClassType.PythonObject, AllToSulongNode::create) {

                @Override
                protected RootCallTarget createCallTarget(PythonLanguage language, RootCallTarget callTarget) {
                    return callTarget;
                }
            };
        }
    }

    @Builtin(name = "METH_KEYWORDS", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class MethKeywordsNode extends PythonBuiltinNode {
        @Specialization
        PExternalFunctionWrapper call() {
            return new PExternalFunctionWrapper(PythonBuiltinClassType.PythonObject, AllToSulongNode::create) {

                @Override
                protected RootCallTarget createCallTarget(PythonLanguage language, RootCallTarget callTarget) {
                    return createCallTarget(new MethKeywordsRoot(language, callTarget));
                }
            };
        }
    }

    @Builtin(name = "METH_VARARGS", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class MethVarargsNode extends PythonBuiltinNode {
        @Specialization
        PExternalFunctionWrapper call() {
            return new PExternalFunctionWrapper(PythonBuiltinClassType.PythonObject, AllToSulongNode::create) {

                @Override
                protected RootCallTarget createCallTarget(PythonLanguage language, RootCallTarget callTarget) {
                    return createCallTarget(new MethVarargsRoot(language, callTarget));
                }
            };
        }
    }

    @Builtin(name = "METH_NOARGS", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class MethNoargsNode extends PythonBuiltinNode {
        @Specialization
        PExternalFunctionWrapper call() {
            return new PExternalFunctionWrapper(PythonBuiltinClassType.PythonObject, AllToSulongNode::create) {

                @Override
                protected RootCallTarget createCallTarget(PythonLanguage language, RootCallTarget callTarget) {
                    return createCallTarget(new MethNoargsRoot(language, callTarget));
                }
            };
        }
    }

    @Builtin(name = "METH_O", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class MethONode extends PythonBuiltinNode {
        @Specialization
        PExternalFunctionWrapper call() {
            return new PExternalFunctionWrapper(PythonBuiltinClassType.PythonObject, AllToSulongNode::create) {

                @Override
                protected RootCallTarget createCallTarget(PythonLanguage language, RootCallTarget callTarget) {
                    return createCallTarget(new MethORoot(language, callTarget));
                }
            };
        }
    }

    @Builtin(name = "METH_FASTCALL", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class MethFastcallNode extends PythonBuiltinNode {
        @Specialization
        PExternalFunctionWrapper call() {
            return new PExternalFunctionWrapper(PythonBuiltinClassType.PythonObject, FastCallArgsToSulongNode::create) {

                @Override
                protected RootCallTarget createCallTarget(PythonLanguage language, RootCallTarget callTarget) {
                    return createCallTarget(new MethFastcallRoot(language, callTarget));
                }
            };
        }
    }

    @Builtin(name = "METH_FASTCALL_WITH_KEYWORDS", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class MethFastcallWithKeywordsNode extends PythonBuiltinNode {
        @Specialization
        PExternalFunctionWrapper call() {
            return new PExternalFunctionWrapper(PythonBuiltinClassType.PythonObject, FastCallWithKeywordsArgsToSulongNode::create) {

                @Override
                protected RootCallTarget createCallTarget(PythonLanguage language, RootCallTarget callTarget) {
                    return createCallTarget(new MethFastcallWithKeywordsRoot(language, callTarget));
                }
            };
        }
    }

    @Builtin(name = "METH_ALLOC", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class MethAllocNode extends PythonBuiltinNode {
        @Specialization
        PExternalFunctionWrapper call() {
            return new PExternalFunctionWrapper(PythonBuiltinClassType.PythonObject, BinaryFirstToSulongNode::create) {

                @Override
                protected RootCallTarget createCallTarget(PythonLanguage language, RootCallTarget callTarget) {
                    return createCallTarget(new AllocFuncRootNode(language, callTarget));
                }
            };
        }
    }

    @Builtin(name = "METH_GETATTR", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class MethGetattrNode extends PythonBuiltinNode {
        @Specialization
        PExternalFunctionWrapper call() {
            return new PExternalFunctionWrapper(PythonBuiltinClassType.PythonObject, BinaryFirstToSulongNode::create) {

                @Override
                protected RootCallTarget createCallTarget(PythonLanguage language, RootCallTarget callTarget) {
                    return createCallTarget(new GetAttrFuncRootNode(language, callTarget));
                }
            };
        }
    }

    @Builtin(name = "METH_SETATTR", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class MethSetattrNode extends PythonBuiltinNode {
        @Specialization
        PExternalFunctionWrapper call() {
            return new PExternalFunctionWrapper(PythonBuiltinClassType.PythonObject, TernaryFirstThirdToSulongNode::create) {

                @Override
                protected RootCallTarget createCallTarget(PythonLanguage language, RootCallTarget callTarget) {
                    return createCallTarget(new SetAttrFuncRootNode(language, callTarget));
                }
            };
        }
    }

    @Builtin(name = "METH_RICHCMP", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class MethRichcmpNode extends PythonBuiltinNode {
        @Specialization
        PExternalFunctionWrapper call() {
            return new PExternalFunctionWrapper(PythonBuiltinClassType.PythonObject, TernaryFirstSecondToSulongNode::create) {

                @Override
                protected RootCallTarget createCallTarget(PythonLanguage language, RootCallTarget callTarget) {
                    return createCallTarget(new RichCmpFuncRootNode(language, callTarget));
                }
            };
        }
    }

    @Builtin(name = "METH_SSIZE_OBJ_ARG", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class MethSSizeObjArgNode extends PythonBuiltinNode {
        @Specialization
        PExternalFunctionWrapper call() {
            return new PExternalFunctionWrapper(PythonBuiltinClassType.PythonObject, TernaryFirstThirdToSulongNode::create) {

                @Override
                protected RootCallTarget createCallTarget(PythonLanguage language, RootCallTarget callTarget) {
                    return createCallTarget(new SSizeObjArgProcRootNode(language, callTarget));
                }
            };
        }
    }

    @Builtin(name = "METH_REVERSE", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class MethReverseNode extends PythonBuiltinNode {
        @Specialization
        PExternalFunctionWrapper call() {
            return new PExternalFunctionWrapper(PythonBuiltinClassType.PythonObject, AllToSulongNode::create) {

                @Override
                protected RootCallTarget createCallTarget(PythonLanguage language, RootCallTarget callTarget) {
                    return createCallTarget(new MethReverseRootNode(language, callTarget));
                }
            };
        }
    }

    @Builtin(name = "METH_POW", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class MethPowNode extends PythonBuiltinNode {
        @Specialization
        PExternalFunctionWrapper call() {
            return new PExternalFunctionWrapper(PythonBuiltinClassType.PythonObject, AllToSulongNode::create) {

                @Override
                protected RootCallTarget createCallTarget(PythonLanguage language, RootCallTarget callTarget) {
                    return createCallTarget(new MethPowRootNode(language, callTarget));
                }
            };
        }
    }

    abstract static class MethRichcmpOpBaseNode extends PythonBuiltinNode {
        // op codes for binary comparisons (defined in 'object.h')
        static final int PY_LT = 0;
        static final int PY_LE = 1;
        static final int PY_EQ = 2;
        static final int PY_NE = 3;
        static final int PY_GT = 4;
        static final int PY_GE = 5;

        private final int op;

        MethRichcmpOpBaseNode(int op) {
            this.op = op;
        }

        @Specialization
        PExternalFunctionWrapper call() {
            return new PExternalFunctionWrapper(PythonBuiltinClassType.PythonObject, TernaryFirstSecondToSulongNode::create) {

                @Override
                protected RootCallTarget createCallTarget(PythonLanguage language, RootCallTarget callTarget) {
                    return createCallTarget(new MethRichcmpOpRootNode(language, callTarget, op));
                }
            };
        }
    }

    @Builtin(name = "METH_LT", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class MethLtNode extends MethRichcmpOpBaseNode {
        protected MethLtNode() {
            super(PY_LT);
        }
    }

    @Builtin(name = "METH_LE", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class MethLeNode extends MethRichcmpOpBaseNode {
        protected MethLeNode() {
            super(PY_LE);
        }
    }

    @Builtin(name = "METH_EQ", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class MethEqNode extends MethRichcmpOpBaseNode {
        protected MethEqNode() {
            super(PY_EQ);
        }
    }

    @Builtin(name = "METH_NE", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class MethNeNode extends MethRichcmpOpBaseNode {
        protected MethNeNode() {
            super(PY_NE);
        }
    }

    @Builtin(name = "METH_GT", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class MethGtNode extends MethRichcmpOpBaseNode {
        protected MethGtNode() {
            super(PY_GT);
        }
    }

    @Builtin(name = "METH_GE", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class MethGeNode extends MethRichcmpOpBaseNode {
        protected MethGeNode() {
            super(PY_GE);
        }
    }

    @Builtin(name = "PyTruffle_Bytes_EmptyWithCapacity", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffle_Bytes_EmptyWithCapacity extends PythonUnaryBuiltinNode {

        @Specialization
        PBytes doInt(int size) {
            return factory().createBytes(new byte[size]);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        PBytes doLong(long size) {
            return doInt(PInt.intValueExact(size));
        }

        @Specialization(replaces = "doLong")
        PBytes doLongOvf(long size) {
            try {
                return doInt(PInt.intValueExact(size));
            } catch (ArithmeticException e) {
                throw raiseIndexError();
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        PBytes doPInt(PInt size) {
            return doInt(size.intValueExact());
        }

        @Specialization(replaces = "doPInt")
        PBytes doPIntOvf(PInt size) {
            try {
                return doInt(size.intValueExact());
            } catch (ArithmeticException e) {
                throw raiseIndexError();
            }
        }
    }

    @Builtin(name = "PyTruffle_Upcall", minNumOfPositionalArgs = 3, takesVarArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class UpcallNode extends PythonBuiltinNode {
        @Child private ReadAttributeFromObjectNode readErrorHandlerNode;

        @Specialization
        Object upcall(VirtualFrame frame, PythonModule cextModule, Object receiver, String name, Object[] args,
                        @Cached CExtNodes.AsPythonObjectNode toJavaNode,
                        @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Cached CExtNodes.ObjectUpcallNode upcallNode) {
            try {
                return toSulongNode.execute(upcallNode.execute(frame, toJavaNode.execute(receiver), name, args));
            } catch (PException e) {
                if (readErrorHandlerNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    readErrorHandlerNode = insert(ReadAttributeFromObjectNode.create());
                }
                getContext().setCurrentException(e);
                return toSulongNode.execute(readErrorHandlerNode.execute(cextModule, NATIVE_NULL));
            }
        }
    }

    @Builtin(name = "PyTruffle_Upcall_l", minNumOfPositionalArgs = 2, takesVarArgs = true)
    @GenerateNodeFactory
    abstract static class UpcallLNode extends PythonBuiltinNode {

        @Specialization
        long upcall(VirtualFrame frame, Object receiver, String name, Object[] args,
                        @Cached("create()") BranchProfile errorProfile,
                        @Cached CExtNodes.AsPythonObjectNode toJavaNode,
                        @Cached CExtNodes.AsLong asLongNode,
                        @Cached CExtNodes.ObjectUpcallNode upcallNode) {
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

        @Specialization
        double upcall(VirtualFrame frame, Object receiver, String name, Object[] args,
                        @Cached("create()") BranchProfile errorProfile,
                        @Cached CExtNodes.AsPythonObjectNode toJavaNode,
                        @Cached CExtNodes.AsDouble asDoubleNode,
                        @Cached CExtNodes.ObjectUpcallNode upcallNode) {
            try {
                return asDoubleNode.execute(frame, upcallNode.execute(frame, toJavaNode.execute(receiver), name, args));
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

        @Specialization
        Object upcall(VirtualFrame frame, Object receiver, String name, Object[] args,
                        @Cached("create()") BranchProfile errorProfile,
                        @Cached CExtNodes.AsPythonObjectNode toJavaNode,
                        @Cached CExtNodes.ObjectUpcallNode upcallNode) {
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

        @Specialization
        Object upcall(VirtualFrame frame, PythonModule cextModule, String name, Object[] args,
                        @Cached CExtNodes.CextUpcallNode upcallNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(upcallNode.execute(frame, cextModule, name, args));
        }

        @Specialization(guards = "!isString(callable)")
        Object doDirect(VirtualFrame frame, @SuppressWarnings("unused") PythonModule cextModule, Object callable, Object[] args,
                        @Cached("create()") CExtNodes.DirectUpcallNode upcallNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(upcallNode.execute(frame, callable, args));
        }

    }

    @Builtin(name = "PyTruffle_Cext_Upcall_d", minNumOfPositionalArgs = 2, takesVarArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class UpcallCextDNode extends PythonBuiltinNode {
        @Child private CExtNodes.AsDouble asDoubleNode = CExtNodes.AsDouble.create();

        @Specialization
        double upcall(VirtualFrame frame, PythonModule cextModule, String name, Object[] args,
                        @Cached("create()") CExtNodes.CextUpcallNode upcallNode) {
            return asDoubleNode.execute(frame, upcallNode.execute(frame, cextModule, name, args));
        }

        @Specialization(guards = "!isString(callable)")
        double doDirect(VirtualFrame frame, @SuppressWarnings("unused") PythonModule cextModule, Object callable, Object[] args,
                        @Cached("create()") CExtNodes.DirectUpcallNode upcallNode) {
            return asDoubleNode.execute(frame, upcallNode.execute(frame, callable, args));
        }
    }

    @Builtin(name = "PyTruffle_Cext_Upcall_l", minNumOfPositionalArgs = 2, takesVarArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class UpcallCextLNode extends PythonBuiltinNode {
        @Child private CExtNodes.AsLong asLongNode = CExtNodes.AsLong.create();

        @Specialization
        Object upcall(VirtualFrame frame, PythonModule cextModule, String name, Object[] args,
                        @Cached("createBinaryProfile()") ConditionProfile isVoidPtr,
                        @Cached("create()") CExtNodes.CextUpcallNode upcallNode) {
            Object result = upcallNode.execute(frame, cextModule, name, args);
            if (isVoidPtr.profile(result instanceof PythonNativeVoidPtr)) {
                return ((PythonNativeVoidPtr) result).object;
            } else {
                return asLongNode.execute(result);
            }
        }

        @Specialization(guards = "!isString(callable)")
        Object doDirect(VirtualFrame frame, @SuppressWarnings("unused") PythonModule cextModule, Object callable, Object[] args,
                        @Cached("createBinaryProfile()") ConditionProfile isVoidPtr,
                        @Cached("create()") CExtNodes.DirectUpcallNode upcallNode) {
            Object result = upcallNode.execute(frame, callable, args);
            if (isVoidPtr.profile(result instanceof PythonNativeVoidPtr)) {
                return ((PythonNativeVoidPtr) result).object;
            } else {
                return asLongNode.execute(result);
            }
        }
    }

    @Builtin(name = "PyTruffle_Cext_Upcall_ptr", minNumOfPositionalArgs = 2, takesVarArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class UpcallCextPtrNode extends PythonBuiltinNode {

        @Specialization
        Object upcall(VirtualFrame frame, PythonModule cextModule, String name, Object[] args,
                        @Cached("create()") CExtNodes.CextUpcallNode upcallNode) {
            return upcallNode.execute(frame, cextModule, name, args);
        }

        @Specialization(guards = "!isString(callable)")
        Object doDirect(VirtualFrame frame, @SuppressWarnings("unused") PythonModule cextModule, Object callable, Object[] args,
                        @Cached("create()") CExtNodes.DirectUpcallNode upcallNode) {
            return upcallNode.execute(frame, callable, args);
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
        @TruffleBoundary
        Object make(PFunction func, Object errorResult,
                        @Exclusive @CachedLanguage PythonLanguage lang) {
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
            Signature funcSignature = func.getSignature();
            if (funcSignature.takesPositionalOnly()) {
                switch (funcSignature.getMaxNumOfPositionalArgs()) {
                    case 1:
                        rootNode = new BuiltinFunctionRootNode(lang, unaryBuiltin,
                                        new MayRaiseNodeFactory<PythonUnaryBuiltinNode>(MayRaiseUnaryNodeGen.create(func, errorResult)),
                                        true);
                        break;
                    case 2:
                        rootNode = new BuiltinFunctionRootNode(lang, binaryBuiltin,
                                        new MayRaiseNodeFactory<PythonBinaryBuiltinNode>(MayRaiseBinaryNodeGen.create(func, errorResult)),
                                        true);
                        break;
                    case 3:
                        rootNode = new BuiltinFunctionRootNode(lang, ternaryBuiltin,
                                        new MayRaiseNodeFactory<PythonTernaryBuiltinNode>(MayRaiseTernaryNodeGen.create(func, errorResult)),
                                        true);
                        break;
                    default:
                        break;
                }
            }
            if (rootNode == null) {
                rootNode = new BuiltinFunctionRootNode(lang, varargsBuiltin,
                                new MayRaiseNodeFactory<PythonBuiltinNode>(new MayRaiseNode(func, errorResult)),
                                true);
            }

            return factory().createBuiltinFunction(func.getName(), null, 0, Truffle.getRuntime().createCallTarget(rootNode));
        }
    }

    @Builtin(name = "to_long", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AsLong extends PythonBuiltinNode {
        @Child CExtNodes.AsLong asLongNode = CExtNodes.AsLong.create();

        @Specialization
        long doIt(Object object) {
            return asLongNode.execute(object);
        }
    }

    @Builtin(name = "to_double", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AsDouble extends PythonBuiltinNode {
        @Child private CExtNodes.AsDouble asDoubleNode = CExtNodes.AsDouble.create();

        @Specialization
        double doIt(VirtualFrame frame, Object object) {
            return asDoubleNode.execute(frame, object);
        }
    }

    @Builtin(name = "PyTruffle_Register_NULL", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffle_Register_NULL extends PythonUnaryBuiltinNode {
        @Specialization
        Object doIt(Object object,
                        @Cached("create()") ReadAttributeFromObjectNode readAttrNode) {
            Object wrapper = readAttrNode.execute(getCore().lookupBuiltinModule(PythonCextBuiltins.PYTHON_CEXT), NATIVE_NULL);
            if (wrapper instanceof PythonNativeNull) {
                ((PythonNativeNull) wrapper).setPtr(object);
            }

            return wrapper;
        }
    }

    @Builtin(name = "PyTruffle_HandleCache_Create", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffleHandleCacheCreate extends PythonUnaryBuiltinNode {
        @Specialization
        Object createCache(TruffleObject ptrToResolveHandle) {
            return new HandleCache(ptrToResolveHandle);
        }
    }

    @Builtin(name = "PyTruffle_Decorate_Function", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTruffleDecorateFunction extends PythonBinaryBuiltinNode {
        @Specialization
        PyCFunctionDecorator decorate(Object fun0, Object fun1) {
            return new PyCFunctionDecorator(fun0, fun1);
        }
    }

    @Builtin(name = "PyLong_FromLongLong", minNumOfPositionalArgs = 2)
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
            return toSulongNode.execute(factory().createNativeVoidPtr(n.getPtr()));
        }
    }

    @Builtin(name = "PyLong_AsVoidPtr", minNumOfPositionalArgs = 1)
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
        long doGeneric(VirtualFrame frame, Object n) {
            if (asPrimitiveNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                asPrimitiveNode = insert(TrufflePInt_AsPrimitiveFactory.create());
            }
            return asPrimitiveNode.executeLong(frame, n, 0, Long.BYTES);
        }
    }

    @Builtin(name = "PyType_IsSubtype", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyType_IsSubtype extends PythonBinaryBuiltinNode {

        @Child private IsSubtypeNode isSubtypeNode = IsSubtypeNode.create();
        @Child private CExtNodes.AsPythonObjectNode asPythonObjectNode = CExtNodesFactory.AsPythonObjectNodeGen.create();

        @Specialization(guards = {"a == cachedA", "b == cachedB"})
        int doCached(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") PythonNativeWrapper a, @SuppressWarnings("unused") PythonNativeWrapper b,
                        @Cached("a") @SuppressWarnings("unused") PythonNativeWrapper cachedA,
                        @Cached("b") @SuppressWarnings("unused") PythonNativeWrapper cachedB,
                        @Cached("isNativeSubtype(frame, a, b)") int result) {
            return result;
        }

        @Specialization(replaces = "doCached")
        int doUncached(VirtualFrame frame, PythonNativeWrapper a, PythonNativeWrapper b) {
            return isNativeSubtype(frame, a, b);
        }

        @Specialization
        int doGeneric(VirtualFrame frame, Object a, Object b,
                        @Cached CExtNodes.ToJavaNode leftToJavaNode,
                        @Cached CExtNodes.ToJavaNode rightToJavaNode) {
            return isSubtypeNode.execute(frame, leftToJavaNode.execute(a), rightToJavaNode.execute(b)) ? 1 : 0;
        }

        protected int isNativeSubtype(VirtualFrame frame, PythonNativeWrapper a, PythonNativeWrapper b) {
            assert a instanceof PythonClassNativeWrapper || a instanceof PythonClassInitNativeWrapper;
            assert b instanceof PythonClassNativeWrapper || b instanceof PythonClassInitNativeWrapper;
            return isSubtypeNode.execute(frame, asPythonObjectNode.execute(a), asPythonObjectNode.execute(b)) ? 1 : 0;
        }
    }

    @Builtin(name = "PyTuple_GetItem", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTuple_GetItem extends PythonBinaryBuiltinNode {

        @Specialization
        Object doPTuple(PTuple tuple, long key,
                        @Cached("create()") SequenceStorageNodes.LenNode lenNode,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode) {
            SequenceStorage sequenceStorage = tuple.getSequenceStorage();
            // we must do a bounds-check but we must not normalize the index
            if (key < 0 || key >= lenNode.execute(sequenceStorage)) {
                throw raise(IndexError, NormalizeIndexNode.TUPLE_OUT_OF_BOUNDS);
            }
            return getItemNode.execute(sequenceStorage, key);
        }

        @Fallback
        Object doPTuple(Object tuple, @SuppressWarnings("unused") Object key) {
            // TODO(fa) To be absolutely correct, we need to do a 'isinstance' check on the object.
            throw raise(SystemError, "bad argument to internal function, was '%s' (type '%p')", tuple, tuple);
        }
    }

    @Builtin(name = "PySequence_Check", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PySequence_Check extends PythonUnaryBuiltinNode {
        @Child private HasInheritedAttributeNode hasInheritedAttrNode;

        @Specialization(guards = "isPSequence(object)")
        int doSequence(@SuppressWarnings("unused") Object object) {
            return 1;
        }

        @Specialization
        int doDict(@SuppressWarnings("unused") PDict object) {
            return 0;
        }

        @Fallback
        int doGeneric(Object object) {
            if (hasInheritedAttrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                hasInheritedAttrNode = insert(HasInheritedAttributeNode.create(__GETITEM__));
            }
            return hasInheritedAttrNode.execute(object) ? 1 : 0;
        }

        protected static boolean isPSequence(Object object) {
            return object instanceof PList || object instanceof PTuple;
        }
    }

    @Builtin(name = "PyBytes_FromStringAndSize", minNumOfPositionalArgs = 3, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class PyBytes_FromStringAndSize extends NativeBuiltin {
        // n.b.: the specializations for PIBytesLike are quite common on
        // managed, when the PySequenceArrayWrapper that we used never went
        // native, and during the upcall to here it was simply unwrapped again
        // with the ToJava (rather than mapped from a native pointer back into a
        // PythonNativeObject)

        @Specialization
        Object doGeneric(VirtualFrame frame, @SuppressWarnings("unused") Object module, PIBytesLike object, long size,
                        @Exclusive @Cached BytesNodes.ToBytesNode getByteArrayNode) {
            byte[] ary = getByteArrayNode.execute(frame, object);
            if (size < Integer.MAX_VALUE && size >= 0 && size < ary.length) {
                return factory().createBytes(Arrays.copyOf(ary, (int) size));
            } else {
                return factory().createBytes(ary);
            }
        }

        @Specialization
        Object doGeneric(VirtualFrame frame, Object module, PythonNativeObject object, long size,
                        @Exclusive @Cached CExtNodes.GetNativeNullNode getNativeNullNode,
                        @Exclusive @Cached GetByteArrayNode getByteArrayNode) {
            try {
                return factory().createBytes(getByteArrayNode.execute(frame, object.getPtr(), size));
            } catch (InteropException e) {
                return raiseNative(frame, getNativeNullNode.execute(module), PythonErrorType.TypeError, "%m", e);
            }
        }
    }

    @Builtin(name = "PyFloat_AsDouble", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyFloat_AsDouble extends NativeBuiltin {

        @Specialization(guards = "!object.isDouble()")
        double doLongNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object) {
            return object.getLong();
        }

        @Specialization(guards = "object.isDouble()")
        double doDoubleNativeWrapper(DynamicObjectNativeWrapper.PrimitiveNativeWrapper object) {
            return object.getDouble();
        }

        @Specialization(rewriteOn = PException.class)
        double doGeneric(VirtualFrame frame, Object object,
                        @Shared("asPythonObjectNode") @Cached CExtNodes.AsPythonObjectNode asPythonObjectNode,
                        @Shared("asDoubleNode") @Cached CExtNodes.AsDouble asDoubleNode) {
            return asDoubleNode.execute(frame, asPythonObjectNode.execute(object));
        }

        @Specialization(replaces = "doGeneric")
        double doGenericErr(VirtualFrame frame, Object object,
                        @Shared("asPythonObjectNode") @Cached CExtNodes.AsPythonObjectNode asPythonObjectNode,
                        @Shared("asDoubleNode") @Cached CExtNodes.AsDouble asDoubleNode) {
            try {
                return doGeneric(frame, object, asPythonObjectNode, asDoubleNode);
            } catch (PException e) {
                transformToNative(frame, e);
                return -1.0;
            }
        }
    }

    @Builtin(name = "PyNumber_Float", minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class PyNumber_Float extends NativeBuiltin {

        @Child private BuiltinConstructors.FloatNode floatNode;

        @Specialization(guards = "object.isDouble()")
        Object doDoubleNativeWrapper(@SuppressWarnings("unused") Object module, DynamicObjectNativeWrapper.PrimitiveNativeWrapper object) {
            return object;
        }

        @Specialization(guards = "!object.isDouble()")
        Object doLongNativeWrapper(@SuppressWarnings("unused") Object module, DynamicObjectNativeWrapper.PrimitiveNativeWrapper object,
                        @Cached CExtNodes.ToSulongNode primitiveToSulongNode) {
            return primitiveToSulongNode.execute((double) object.getLong());
        }

        @Specialization(rewriteOn = PException.class)
        Object doGeneric(VirtualFrame frame, @SuppressWarnings("unused") Object module, Object object,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Shared("asPythonObjectNode") @Cached CExtNodes.AsPythonObjectNode asPythonObjectNode) {
            if (floatNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                floatNode = insert(BuiltinConstructorsFactory.FloatNodeFactory.create(null));
            }
            return toSulongNode.execute(floatNode.executeWith(frame, PythonBuiltinClassType.PFloat, asPythonObjectNode.execute(object)));
        }

        @Specialization(replaces = "doGeneric")
        Object doGenericErr(VirtualFrame frame, Object module, Object object,
                        @Exclusive @Cached CExtNodes.GetNativeNullNode getNativeNullNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Shared("asPythonObjectNode") @Cached CExtNodes.AsPythonObjectNode asPythonObjectNode) {
            try {
                return doGeneric(frame, module, object, toSulongNode, asPythonObjectNode);
            } catch (PException e) {
                transformToNative(frame, e);
                return getNativeNullNode.execute(module);
            }
        }
    }

    @Builtin(name = "PySet_Add", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PySet_Add extends PythonBinaryBuiltinNode {

        @Specialization
        int add(VirtualFrame frame, PBaseSet self, Object o,
                        @Cached("create()") HashingCollectionNodes.SetItemNode setItemNode) {
            try {
                setItemNode.execute(frame, self, o, PNone.NO_VALUE);
            } catch (PException e) {
                NativeBuiltin.transformToNative(getContext(), PArguments.getCurrentFrameInfo(frame), e);
                return -1;
            }
            return 0;
        }

        @Fallback
        int add(VirtualFrame frame, Object self, @SuppressWarnings("unused") Object o) {
            return NativeBuiltin.raiseNative(this, PArguments.getCurrentFrameInfo(frame), -1, SystemError, "expected a set object, not %p", self);
        }

    }

    @Builtin(name = "_PyBytes_Resize", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PyBytes_Resize extends PythonBinaryBuiltinNode {

        @Specialization
        int resize(PBytes self, long newSizeL,
                        @Cached("create()") SequenceStorageNodes.LenNode lenNode,
                        @Cached("create()") SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached("create()") CastToIndexNode castToIndexNode,
                        @Cached("create()") CastToByteNode castToByteNode) {

            SequenceStorage storage = self.getSequenceStorage();
            int newSize = castToIndexNode.execute(newSizeL);
            int len = lenNode.execute(storage);
            byte[] smaller = new byte[newSize];
            for (int i = 0; i < newSize && i < len; i++) {
                smaller[i] = castToByteNode.execute(getItemNode.execute(storage, i));
            }
            self.setSequenceStorage(new ByteSequenceStorage(smaller));
            return 0;
        }

        @Fallback
        int add(VirtualFrame frame, Object self, @SuppressWarnings("unused") Object o) {
            return NativeBuiltin.raiseNative(this, PArguments.getCurrentFrameInfo(frame), -1, SystemError, "expected a set object, not %p", self);
        }

    }

    @Builtin(name = "PyTruffle_Compute_Mro", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonTypes.class)
    public abstract static class PyTruffle_Compute_Mro extends PythonBinaryBuiltinNode {

        @Specialization
        Object doIt(PythonNativeObject self, String className) {
            PythonAbstractClass[] doSlowPath = TypeNodes.ComputeMroNode.doSlowPath(PythonNativeClass.cast(self));
            return factory().createTuple(new MroSequenceStorage(className, doSlowPath));
        }
    }

    @Builtin(name = "PyTruffle_Type_Modified", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @TypeSystemReference(PythonTypes.class)
    public abstract static class PyTruffle_Type_Modified extends PythonTernaryBuiltinNode {

        @Specialization(guards = "isNoValue(mroTuple)")
        Object doIt(PythonNativeClass clazz, String name, @SuppressWarnings("unused") PNone mroTuple) {
            CyclicAssumption nativeClassStableAssumption = getContext().getNativeClassStableAssumption(clazz, false);
            if (nativeClassStableAssumption != null) {
                nativeClassStableAssumption.invalidate("PyType_Modified(\"" + name + "\") (without MRO) called");
            }
            return PNone.NONE;
        }

        @Specialization
        Object doIt(PythonNativeClass clazz, String name, PTuple mroTuple,
                        @Cached("createClassProfile()") ValueProfile profile) {
            CyclicAssumption nativeClassStableAssumption = getContext().getNativeClassStableAssumption(clazz, false);
            if (nativeClassStableAssumption != null) {
                nativeClassStableAssumption.invalidate("PyType_Modified(\"" + name + "\") called");
            }
            SequenceStorage sequenceStorage = profile.profile(mroTuple.getSequenceStorage());
            if (sequenceStorage instanceof MroSequenceStorage) {
                ((MroSequenceStorage) sequenceStorage).lookupChanged();
            } else {
                throw new IllegalStateException("invalid MRO object for native type \"" + name + "\"");
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "PyTruffle_FatalError", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @TypeSystemReference(PythonTypes.class)
    public abstract static class PyTruffle_FatalError extends PythonBuiltinNode {
        private static final int SIGABRT_EXIT_CODE = 134;

        @Specialization
        @TruffleBoundary
        Object doStrings(String prefix, String msg, int status) {
            PrintWriter stderr = new PrintWriter(getContext().getStandardErr());
            stderr.print("Fatal Python error: ");
            if (prefix != null) {
                stderr.print(prefix);
                stderr.print(": ");
            }
            if (msg != null) {
                stderr.print(msg);
            } else {
                stderr.print("<message not set>");
            }
            stderr.println();
            stderr.flush();

            if (status < 0) {
                // In CPython, this will use 'abort()' which sets a special exit code.
                throw new PythonExitException(this, SIGABRT_EXIT_CODE);
            }
            throw new PythonExitException(this, status);
        }

        @Specialization
        Object doGeneric(Object prefixObj, Object msgObj, int status) {
            String prefix = prefixObj == PNone.NO_VALUE ? null : (String) prefixObj;
            String msg = msgObj == PNone.NO_VALUE ? null : (String) msgObj;
            return doStrings(prefix, msg, status);
        }
    }

    @Builtin(name = "PyUnicode_DecodeUTF8Stateful", minNumOfPositionalArgs = 4, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class PyUnicode_DecodeUTF8Stateful extends NativeUnicodeBuiltin {

        @Specialization
        Object doUtf8Decode(VirtualFrame frame, Object module, Object cByteArray, String errors, @SuppressWarnings("unused") int reportConsumed,
                        @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Cached GetByteArrayNode getByteArrayNode,
                        @Cached GetNativeNullNode getNativeNullNode) {

            try {
                ByteBuffer inputBuffer = wrap(getByteArrayNode.execute(frame, cByteArray, -1));
                int n = remaining(inputBuffer);
                CharBuffer resultBuffer = allocateCharBuffer(n * 4);
                decodeUTF8(resultBuffer, inputBuffer, errors);
                return toSulongNode.execute(factory().createTuple(new Object[]{toString(resultBuffer), n - remaining(inputBuffer)}));
            } catch (InteropException e) {
                return raiseNative(frame, getNativeNullNode.execute(module), PythonErrorType.TypeError, "%m", e);
            }
        }

        @TruffleBoundary
        private CoderResult decodeUTF8(CharBuffer resultBuffer, ByteBuffer inputBuffer, String errors) {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
            CodingErrorAction action = BytesBuiltins.toCodingErrorAction(errors, this);
            return decoder.onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(action).decode(inputBuffer, resultBuffer, true);
        }
    }

    @Builtin(name = "PyTruffle_IsSequence", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffle_IsSequence extends PythonUnaryBuiltinNode {

        @Specialization
        boolean doGeneric(VirtualFrame frame, Object object,
                        @Cached IsSequenceNode isSequenceNode,
                        @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            PythonContext context = contextRef.get();
            PException caughtException = IndirectCallContext.enter(frame, context, isSequenceNode);
            try {
                return isSequenceNode.execute(object);
            } finally {
                IndirectCallContext.exit(context, caughtException);
            }
        }
    }

    @Builtin(name = "PyTruffle_OS_StringToDouble", minNumOfPositionalArgs = 3, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class PyTruffle_OS_StringToDouble extends NativeBuiltin {

        @Specialization
        Object doGeneric(VirtualFrame frame, Object module, String source, int reportPos,
                        @Cached GetNativeNullNode getNativeNullNode) {

            if (reportPos != 0) {
                ParsePosition pp = new ParsePosition(0);
                Number parse = parse(source, pp);
                if (parse != null) {
                    return factory().createTuple(new Object[]{parse.doubleValue(), pp.getIndex()});
                }
            } else {
                try {
                    Number parse = parse(source);
                    return factory().createTuple(new Object[]{parse.doubleValue()});
                } catch (ParseException e) {
                }
            }
            return raiseNative(frame, getNativeNullNode.execute(module), PythonBuiltinClassType.ValueError, "could not convert string to float: %s", source);
        }

        @TruffleBoundary
        private static Number parse(String source, ParsePosition pp) {
            return DecimalFormat.getInstance().parse(source, pp);
        }

        @TruffleBoundary
        private static Number parse(String source) throws ParseException {
            return DecimalFormat.getInstance().parse(source);
        }
    }

    @Builtin(name = "PyUnicode_Decode", minNumOfPositionalArgs = 5, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class PyUnicode_Decode extends NativeUnicodeBuiltin {

        @Specialization
        Object doDecode(VirtualFrame frame, Object module, Object cByteArray, long size, String encoding, String errors,
                        @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Cached GetByteArrayNode getByteArrayNode,
                        @Cached GetNativeNullNode getNativeNullNode) {

            try {
                ByteBuffer inputBuffer = wrap(getByteArrayNode.execute(frame, cByteArray, size));
                int n = remaining(inputBuffer);
                CharBuffer resultBuffer = allocateCharBuffer(n * 4);
                decode(resultBuffer, inputBuffer, encoding, errors);
                return toSulongNode.execute(factory().createTuple(new Object[]{toString(resultBuffer), n - remaining(inputBuffer)}));
            } catch (IllegalArgumentException e) {
                return raiseNative(frame, getNativeNullNode.execute(module), PythonErrorType.LookupError, "unknown encoding: " + encoding);
            } catch (InteropException e) {
                return raiseNative(frame, getNativeNullNode.execute(module), PythonErrorType.TypeError, "%m", e);
            }
        }

        @TruffleBoundary
        private CoderResult decode(CharBuffer resultBuffer, ByteBuffer inputBuffer, String encoding, String errors) {
            CharsetDecoder decoder = Charset.forName(encoding).newDecoder();
            CodingErrorAction action = BytesBuiltins.toCodingErrorAction(errors, this);
            return decoder.onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(action).decode(inputBuffer, resultBuffer, true);
        }
    }

    @Builtin(name = "PyObject_Size", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @ImportStatic(SpecialMethodNames.class)
    abstract static class PyObject_Size extends PythonUnaryBuiltinNode {
        @Child private CExtNodes.AsLong castToIntNode;

        // n.b.: specializations 'doSequence' and 'doMapping' are not just shortcuts but also
        // required for correctness because CPython's implementation uses
        // 'type->tp_as_sequence->sq_length', 'type->tp_as_mapping->mp_length' which will bypass any
        // user implementation of '__len__'.
        @Specialization
        int doSequence(PSequence sequence,
                        @Cached SequenceNodes.LenNode seqLenNode) {
            return seqLenNode.execute(sequence);
        }

        @Specialization
        int doMapping(PHashingCollection container,
                        @Cached HashingCollectionNodes.LenNode seqLenNode) {
            return seqLenNode.execute(container);
        }

        @Specialization(guards = "!isMappingOrSequence(obj)")
        long doGenericUnboxed(VirtualFrame frame, Object obj,
                        @Cached("create(__LEN__)") LookupAndCallUnaryNode callLenNode,
                        @Cached("createBinaryProfile()") ConditionProfile noLenProfile,
                        @Cached BranchProfile errorProfile) {
            try {
                Object result = callLenNode.executeObject(frame, obj);
                if (noLenProfile.profile(result == PNone.NO_VALUE)) {
                    return -1;
                }
                return castToInt(result);
            } catch (PException e) {
                errorProfile.enter();
                NativeBuiltin.transformToNative(getContext(), frame, e);
                return -1;
            }
        }

        protected static boolean isMappingOrSequence(Object obj) {
            return obj instanceof PSequence || obj instanceof PHashingCollection;
        }

        private long castToInt(Object obj) {
            if (castToIntNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToIntNode = insert(CExtNodes.AsLong.create());
            }
            return castToIntNode.execute(obj);
        }
    }
}
