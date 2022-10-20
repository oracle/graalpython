/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IndexError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.objects.cext.common.CExtContext.METH_CLASS;
import static com.oracle.graal.python.nodes.BuiltinNames.T_BUILTINS;
import static com.oracle.graal.python.nodes.BuiltinNames.T__WEAKREF;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.StringLiterals.J_LLVM_LANGUAGE;
import static com.oracle.graal.python.nodes.StringLiterals.T_DOT;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_OBJECT_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.GraalPythonModuleBuiltins.DebugNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.CreateFunctionNodeGen;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext.AllocInfo;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiGuards;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodes.ReadMemberNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodes.WriteMemberNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.CastToJavaDoubleNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.CastToNativeLongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.CextUpcallNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.CharPtrToJavaObjectNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.DirectUpcallNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ObjectUpcallNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PRaiseNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.UnicodeFromFormatNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.VoidPtrToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.AsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.FromCharPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.PRaiseNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.ToNewRefNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.TransformExceptionToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.GetterRoot;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.SetterRoot;
import com.oracle.graal.python.builtins.objects.cext.capi.HandleCache;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeReferenceCache;
import com.oracle.graal.python.builtins.objects.cext.capi.PyCFunctionDecorator;
import com.oracle.graal.python.builtins.objects.cext.capi.PyEvalNodes.PyEvalRestoreThread;
import com.oracle.graal.python.builtins.objects.cext.capi.PyEvalNodes.PyEvalSaveThread;
import com.oracle.graal.python.builtins.objects.cext.capi.PyGILStateNodes.PyGILStateEnsure;
import com.oracle.graal.python.builtins.objects.cext.capi.PyGILStateNodes.PyGILStateRelease;
import com.oracle.graal.python.builtins.objects.cext.capi.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyTruffleObjectAlloc;
import com.oracle.graal.python.builtins.objects.cext.capi.PyTruffleObjectFree;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonClassNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeNull;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapperLibrary;
import com.oracle.graal.python.builtins.objects.cext.capi.UnicodeObjectNodes.UnicodeAsWideCharNode;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtAsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.Charsets;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.EncodeNativeStringNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.GetByteArrayNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.UnicodeFromWcharNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.UnicodeFromWcharNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext.Store;
import com.oracle.graal.python.builtins.objects.cext.common.CExtParseArgumentsNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtParseArgumentsNode.SplitFormatStringNode;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemScalarNode;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.frame.PFrame.Reference;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.memoryview.BufferLifecycleManager;
import com.oracle.graal.python.builtins.objects.memoryview.MemoryViewNodes;
import com.oracle.graal.python.builtins.objects.memoryview.NativeBufferLifecycleManager;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.frame.GetCurrentFrameRef;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongLossyNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.BufferFormat;
import com.oracle.graal.python.util.Function;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.utilities.CyclicAssumption;
import com.oracle.truffle.llvm.api.Toolchain;

@CoreFunctions(defineModule = PythonCextBuiltins.PYTHON_CEXT)
@GenerateNodeFactory
public final class PythonCextBuiltins extends PythonBuiltins {

    public static final String PYTHON_CEXT = "python_cext";
    public static final TruffleString T_PYTHON_CEXT = tsLiteral(PYTHON_CEXT);

    /*
     * Native pointer to the PyMethodDef struct for functions created in C. We need to keep it
     * because the C program may expect to get its pointer back when accessing m_ml member of
     * methods.
     */
    public static final HiddenKey METHOD_DEF_PTR = new HiddenKey("method_def_ptr");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PythonCextBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        addBuiltinConstant("PyEval_SaveThread", new PyEvalSaveThread());
        addBuiltinConstant("PyEval_RestoreThread", new PyEvalRestoreThread());
        addBuiltinConstant("PyGILState_Ensure", new PyGILStateEnsure());
        addBuiltinConstant("PyGILState_Release", new PyGILStateRelease());
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        if (!core.getContext().getOption(PythonOptions.EnableDebuggingBuiltins)) {
            PythonModule mod = core.lookupBuiltinModule(T_PYTHON_CEXT);
            mod.setAttribute(toTruffleStringUncached("PyTruffle_ToNative"), PNone.NO_VALUE);
        }
    }

    @FunctionalInterface
    public interface TernaryFunction<T1, T2, T3, R> {
        R apply(T1 arg0, T2 arg1, T3 arg2);
    }

    /**
     * Called mostly from Python code to convert arguments into a wrapped representation for
     * consumption in Python or Java.
     */
    @Builtin(name = "to_java", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ToJavaObjectNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object run(Object object,
                        @Cached AsPythonObjectNode toJavaNode) {
            return toJavaNode.execute(object);
        }
    }

    /**
     * Called from Python code to convert arguments into a wrapped representation for consumption in
     * Python.
     */
    @Builtin(name = "voidptr_to_java", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class VoidPtrToJavaObjectNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object run(Object object,
                        @Cached VoidPtrToJavaNode voidPtrtoJavaNode) {
            return voidPtrtoJavaNode.execute(object);
        }
    }

    @Builtin(name = "to_java_type", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ToJavaClassNode extends ToJavaObjectNode {
    }

    /**
     * Called from C when they actually want a {@code const char*} for a Python string
     */
    @Builtin(name = "to_char_pointer", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class TruffleStringAsStringNode extends NativeBuiltin {

        @Specialization(guards = "isString(str)")
        static Object run(Object str,
                        @Cached AsCharPointerNode asCharPointerNode) {
            return asCharPointerNode.execute(str);
        }

        @Fallback
        Object run(VirtualFrame frame, Object o) {
            return raiseNative(frame, PNone.NO_VALUE, PythonErrorType.SystemError, ErrorMessages.CANNOT_CONVERT_OBJ_TO_C_STRING, o, o.getClass().getName());
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
        static Object run(Object obj,
                        @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(obj);
        }
    }

    @Builtin(name = "PyTruffle_Type", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffleTypeNode extends NativeBuiltin {

        private static final TruffleString[] LOOKUP_MODULES = new TruffleString[]{
                        PythonCextBuiltins.T_PYTHON_CEXT,
                        T__WEAKREF,
                        T_BUILTINS
        };

        @Specialization
        Object doI(Object typeNameObject,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached TruffleString.EqualNode eqNode) {
            TruffleString typeName = castToTruffleStringNode.execute(typeNameObject);
            Python3Core core = getCore();
            for (PythonBuiltinClassType type : PythonBuiltinClassType.VALUES) {
                if (eqNode.execute(type.getName(), typeName, TS_ENCODING)) {
                    return core.lookupType(type);
                }
            }
            for (TruffleString module : LOOKUP_MODULES) {
                Object attribute = core.lookupBuiltinModule(module).getAttribute(typeName);
                if (attribute != PNone.NO_VALUE) {
                    return attribute;
                }
            }
            throw raise(PythonErrorType.KeyError, ErrorMessages.APOSTROPHE_S, typeName);
        }
    }

    @Builtin(name = "import_c_func", minNumOfPositionalArgs = 2, parameterNames = {"name", "capi_library"})
    @ArgumentClinic(name = "name", conversion = ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class ImportCExtFunction extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PythonCextBuiltinsClinicProviders.ImportCExtFunctionClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object importCExtFunction(TruffleString name, Object capiLibrary,
                        @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode) {
            try {
                Object member = lib.readMember(capiLibrary, toJavaStringNode.execute(name));
                return PExternalFunctionWrapper.createWrapperFunction(name, member, null, 0,
                                PExternalFunctionWrapper.DIRECT, getLanguage(), factory(), true);
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @GenerateUncached
    abstract static class CreateFunctionNode extends PNodeWithContext {

        abstract Object execute(TruffleString name, Object callable, Object wrapper, Object type, Object flags, PythonObjectFactory factory);

        @Specialization(guards = {"isTypeNode.execute(type)", "isNoValue(wrapper)"}, limit = "3")
        static Object doPythonCallableWithoutWrapper(@SuppressWarnings("unused") TruffleString name, PythonNativeWrapper callable,
                        @SuppressWarnings("unused") PNone wrapper,
                        @SuppressWarnings("unused") Object type,
                        @SuppressWarnings("unused") Object flags,
                        @SuppressWarnings("unused") PythonObjectFactory factory,
                        @CachedLibrary("callable") PythonNativeWrapperLibrary nativeWrapperLibrary,
                        @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode) {
            // This can happen if a native type inherits slots from a managed type. Therefore,
            // something like 'base->tp_new' will be a wrapper of the managed '__new__'. So, in this
            // case, we assume that the object is already callable.
            return nativeWrapperLibrary.getDelegate(callable);
        }

        @Specialization(guards = "isTypeNode.execute(type)", limit = "1")
        @TruffleBoundary
        Object doPythonCallable(TruffleString name, PythonNativeWrapper callable, int signature, Object type, int flags, PythonObjectFactory factory,
                        @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode) {
            // This can happen if a native type inherits slots from a managed type. Therefore,
            // something like 'base->tp_new' will be a wrapper of the managed '__new__'. So, in this
            // case, we assume that the object is already callable.
            Object managedCallable = callable.getDelegateSlowPath();
            PBuiltinFunction function = PExternalFunctionWrapper.createWrapperFunction(name, managedCallable, type, flags, signature, getLanguage(), factory, false);
            return function != null ? function : managedCallable;
        }

        @Specialization(guards = {"isTypeNode.execute(type)", "isDecoratedManagedFunction(callable)", "isNoValue(wrapper)"}, limit = "1")
        @SuppressWarnings("unused")
        static Object doDecoratedManagedWithoutWrapper(@SuppressWarnings("unused") TruffleString name, PyCFunctionDecorator callable, PNone wrapper, Object type, Object flags,
                        PythonObjectFactory factory,
                        @CachedLibrary(limit = "3") PythonNativeWrapperLibrary nativeWrapperLibrary,
                        @Cached TypeNodes.IsTypeNode isTypeNode) {
            // This can happen if a native type inherits slots from a managed type. Therefore,
            // something like 'base->tp_new' will be a wrapper of the managed '__new__'. So, in this
            // case, we assume that the object is already callable.
            // Note, that this will also drop the 'native-to-java' conversion which is usually done
            // by 'callable.getFun1()'.
            return nativeWrapperLibrary.getDelegate(callable.getNativeFunction());
        }

        @Specialization(guards = "isDecoratedManagedFunction(callable)")
        @TruffleBoundary
        static Object doDecoratedManaged(TruffleString name, PyCFunctionDecorator callable, int signature, Object type, int flags, PythonObjectFactory factory,
                        @CachedLibrary(limit = "3") PythonNativeWrapperLibrary nativeWrapperLibrary) {
            // This can happen if a native type inherits slots from a managed type. Therefore,
            // something like 'base->tp_new' will be a wrapper of the managed '__new__'. So, in this
            // case, we assume that the object is already callable.
            // Note, that this will also drop the 'native-to-java' conversion which is usually done
            // by 'callable.getFun1()'.
            Object managedCallable = nativeWrapperLibrary.getDelegate(callable.getNativeFunction());
            PBuiltinFunction function = PExternalFunctionWrapper.createWrapperFunction(name, managedCallable, type, flags,
                            signature, PythonLanguage.get(nativeWrapperLibrary), factory, false);
            if (function != null) {
                return function;
            }

            // Special case: if the returned 'wrappedCallTarget' is null, this indicates we want to
            // call a Python callable without wrapping and arguments conversion. So, directly use
            // the callable.
            return managedCallable;
        }

        @Specialization(guards = {"isTypeNode.execute(type)", "!isNativeWrapper(callable)"}, limit = "1")
        @TruffleBoundary
        PBuiltinFunction doNativeCallableWithType(TruffleString name, Object callable, int signature, Object type, int flags, PythonObjectFactory factory,
                        @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode) {
            return PExternalFunctionWrapper.createWrapperFunction(name, callable, type, flags,
                            signature, getLanguage(), factory, true);
        }

        @Specialization(guards = {"isNoValue(type)", "!isNativeWrapper(callable)"})
        @TruffleBoundary
        PBuiltinFunction doNativeCallableWithoutType(TruffleString name, Object callable, int signature, @SuppressWarnings("unused") PNone type, int flags, PythonObjectFactory factory) {
            return doNativeCallableWithType(name, callable, signature, null, flags, factory, null);
        }

        @Specialization(guards = {"isTypeNode.execute(type)", "isNoValue(wrapper)", "!isNativeWrapper(callable)"}, limit = "1")
        @TruffleBoundary
        PBuiltinFunction doNativeCallableWithoutWrapper(TruffleString name, Object callable, Object type,
                        @SuppressWarnings("unused") PNone wrapper,
                        @SuppressWarnings("unused") Object flags, PythonObjectFactory factory,
                        @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode) {
            return PExternalFunctionWrapper.createWrapperFunction(name, callable, type, 0,
                            PExternalFunctionWrapper.DIRECT, getLanguage(), factory, true);
        }

        @Specialization(guards = {"isNoValue(wrapper)", "isNoValue(type)", "!isNativeWrapper(callable)"})
        @TruffleBoundary
        PBuiltinFunction doNativeCallableWithoutWrapperAndType(TruffleString name, Object callable, PNone wrapper, @SuppressWarnings("unused") PNone type, Object flags, PythonObjectFactory factory) {
            return doNativeCallableWithoutWrapper(name, callable, null, wrapper, flags, factory, null);
        }

        static boolean isNativeWrapper(Object obj) {
            return CApiGuards.isNativeWrapper(obj) || isDecoratedManagedFunction(obj);
        }

        static boolean isDecoratedManagedFunction(Object obj) {
            return obj instanceof PyCFunctionDecorator && CApiGuards.isNativeWrapper(((PyCFunctionDecorator) obj).getNativeFunction());
        }
    }

    @Builtin(name = "PyTruffle_SetAttr", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyObjectSetAttrNode extends PythonTernaryBuiltinNode {

        abstract Object execute(Object object, TruffleString key, Object value);

        @Specialization
        static Object doBuiltinClass(PythonBuiltinClass object, TruffleString key, Object value,
                        @Exclusive @Cached("createForceType()") WriteAttributeToObjectNode writeAttrNode) {
            writeAttrNode.execute(object, key, value);
            return PNone.NONE;
        }

        @Specialization
        static Object doNativeClass(PythonNativeClass object, TruffleString key, Object value,
                        @Exclusive @Cached("createForceType()") WriteAttributeToObjectNode writeAttrNode) {
            writeAttrNode.execute(object, key, value);
            return PNone.NONE;
        }

        @Specialization(guards = {"!isPythonBuiltinClass(object)"})
        static Object doObject(PythonObject object, TruffleString key, Object value,
                        @Exclusive @Cached WriteAttributeToDynamicObjectNode writeAttrToDynamicObjectNode) {
            writeAttrToDynamicObjectNode.execute(object.getStorage(), key, value);
            return PNone.NONE;
        }
    }

    // directly called without landing function
    @Builtin(name = "PyTruffle_Set_Native_Slots", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyTruffleSetNativeSlots extends NativeBuiltin {
        static final HiddenKey NATIVE_SLOTS = new HiddenKey("__native_slots__");

        @Specialization
        static int doPythonClass(PythonClassNativeWrapper pythonClassWrapper, Object nativeGetSets, Object nativeMembers,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Cached WriteAttributeToObjectNode writeAttrNode) {
            Object pythonClass = asPythonObjectNode.execute(pythonClassWrapper);
            assert pythonClass instanceof PythonManagedClass;
            writeAttrNode.execute(pythonClass, NATIVE_SLOTS, new Object[]{nativeGetSets, nativeMembers});
            return 0;
        }
    }

    @Builtin(name = "PyTruffle_Get_Inherited_Native_Slots", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTruffleGetInheritedNativeSlots extends NativeBuiltin {
        private static final int INDEX_GETSETS = 0;
        private static final int INDEX_MEMBERS = 1;

        private static final TruffleString T_MEMBERS = tsLiteral("members");
        private static final TruffleString T_GETSETS = tsLiteral("getsets");

        /**
         * A native class may inherit from a managed class. However, the managed class may define
         * custom slots at a time where the C API is not yet loaded. So we need to check if any of
         * the base classes defines custom slots and adapt the basicsize to allocate space for the
         * slots and add the native member slot descriptors.
         */
        @Specialization
        Object slots(Object pythonClass, TruffleString subKey,
                        @Cached GetMroStorageNode getMroStorageNode,
                        @Cached TruffleString.EqualNode eqNode) {
            int idx;
            if (eqNode.execute(T_GETSETS, subKey, TS_ENCODING)) {
                idx = INDEX_GETSETS;
            } else if (eqNode.execute(T_MEMBERS, subKey, TS_ENCODING)) {
                idx = INDEX_MEMBERS;
            } else {
                return getContext().getNativeNull();
            }

            Object[] values = collect(getMroStorageNode.execute(pythonClass), idx);
            return new PySequenceArrayWrapper(factory().createTuple(values), Long.BYTES);
        }

        @TruffleBoundary
        private static Object[] collect(MroSequenceStorage mro, int idx) {
            ArrayList<Object> l = new ArrayList<>();
            int mroLength = mro.length();
            for (int i = 0; i < mroLength; i++) {
                PythonAbstractClass kls = mro.getItemNormalized(i);
                Object value = ReadAttributeFromObjectNode.getUncachedForceType().execute(kls, PyTruffleSetNativeSlots.NATIVE_SLOTS);
                if (value != PNone.NO_VALUE) {
                    Object[] tuple = (Object[]) value;
                    assert tuple.length == 2;
                    l.add(new PythonAbstractNativeObject(tuple[idx]));
                }
            }
            return l.toArray();
        }
    }

    @TypeSystemReference(PythonTypes.class)
    abstract static class NativeBuiltin extends PythonBuiltinNode {

        @Child private TransformExceptionToNativeNode transformExceptionToNativeNode;
        @Child private PRaiseNativeNode raiseNativeNode;

        protected void transformToNative(VirtualFrame frame, PException p) {
            if (transformExceptionToNativeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                transformExceptionToNativeNode = insert(TransformExceptionToNativeNodeGen.create());
            }
            transformExceptionToNativeNode.execute(frame, p);
        }

        protected Object raiseNative(VirtualFrame frame, Object defaultValue, PythonBuiltinClassType errType, TruffleString fmt, Object... args) {
            return ensureRaiseNativeNode().execute(frame, defaultValue, errType, fmt, args);
        }

        protected Object raiseBadArgument(VirtualFrame frame, Object errorMarker) {
            return raiseNative(frame, errorMarker, PythonErrorType.TypeError, ErrorMessages.BAD_ARG_TYPE_FOR_BUILTIN_OP);
        }

        @TruffleBoundary(allowInlining = true)
        protected static ByteBuffer wrap(byte[] data) {
            return ByteBuffer.wrap(data);
        }

        @TruffleBoundary(allowInlining = true)
        protected static ByteBuffer wrap(byte[] data, int offset, int length) {
            return ByteBuffer.wrap(data, offset, length);
        }

        private PRaiseNativeNode ensureRaiseNativeNode() {
            if (raiseNativeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNativeNode = insert(PRaiseNativeNodeGen.create());
            }
            return raiseNativeNode;
        }
    }

    abstract static class NativeUnicodeBuiltin extends NativeBuiltin {
        @TruffleBoundary
        protected static CharBuffer allocateCharBuffer(int cap) {
            return CharBuffer.allocate(cap);
        }

        @TruffleBoundary
        protected static TruffleString toString(CharBuffer cb) {
            int len = cb.position();
            if (len > 0) {
                cb.rewind();
                return toTruffleStringUncached(cb.subSequence(0, len).toString());
            }
            return T_EMPTY_STRING;
        }

        @TruffleBoundary
        protected static int remaining(ByteBuffer cb) {
            return cb.remaining();
        }
    }

    @Builtin(name = "PyTruffle_Unicode_FromWchar", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyTruffleUnicodeFromWcharNode extends NativeUnicodeBuiltin {
        @Child private UnicodeFromWcharNode unicodeFromWcharNode;
        @Child private CExtNodes.ToNewRefNode toSulongNode;

        @Specialization
        Object doInt(VirtualFrame frame, Object arr, int elementSize, Object errorMarker) {
            try {
                /*
                 * If we receive a native wrapper here, we assume that it is one of the wrappers
                 * that emulates some C array (e.g. CArrayWrapper or PySequenceArrayWrapper). Those
                 * wrappers are directly handled by the node. Otherwise, it is assumed that the
                 * object is a typed pointer object.
                 */
                return ensureToSulongNode().execute(ensureUnicodeFromWcharNode().execute(arr, elementSize));
            } catch (PException e) {
                transformToNative(frame, e);
                return errorMarker;
            }
        }

        @Specialization(limit = "1")
        Object doGeneric(VirtualFrame frame, Object arr, Object elementSize, Object errorMarker,
                        @CachedLibrary("elementSize") InteropLibrary elementSizeLib) {

            if (elementSizeLib.fitsInInt(elementSize)) {
                try {
                    return doInt(frame, arr, elementSizeLib.asInt(elementSize), errorMarker);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }
            throw CompilerDirectives.shouldNotReachHere();
        }

        private UnicodeFromWcharNode ensureUnicodeFromWcharNode() {
            if (unicodeFromWcharNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                unicodeFromWcharNode = insert(UnicodeFromWcharNodeGen.create());
            }
            return unicodeFromWcharNode;
        }

        private CExtNodes.ToNewRefNode ensureToSulongNode() {
            if (toSulongNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toSulongNode = insert(ToNewRefNodeGen.create());
            }
            return toSulongNode;
        }
    }

    abstract static class NativeEncoderNode extends NativeBuiltin {
        private final Charset charset;

        protected NativeEncoderNode(Charset charset) {
            this.charset = charset;
        }

        @Specialization(guards = "isNoValue(errors)")
        Object doUnicode(VirtualFrame frame, PString s, @SuppressWarnings("unused") PNone errors, Object error_marker,
                        @Shared("encodeNode") @Cached EncodeNativeStringNode encodeNativeStringNode) {
            return doUnicode(frame, s, T_STRICT, error_marker, encodeNativeStringNode);
        }

        @Specialization
        Object doUnicode(VirtualFrame frame, PString s, TruffleString errors, Object error_marker,
                        @Shared("encodeNode") @Cached EncodeNativeStringNode encodeNativeStringNode) {
            try {
                return factory().createBytes(encodeNativeStringNode.execute(charset, s, errors));
            } catch (PException e) {
                transformToNative(frame, e);
                return error_marker;
            }
        }

        @Fallback
        Object doUnicode(VirtualFrame frame, @SuppressWarnings("unused") Object s, @SuppressWarnings("unused") Object errors, Object errorMarker) {
            return raiseBadArgument(frame, errorMarker);
        }
    }

    @Builtin(name = "_PyTruffle_Unicode_AsLatin1String", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyTruffleUnicodeAsLatin1StringNode extends NativeEncoderNode {
        protected PyTruffleUnicodeAsLatin1StringNode() {
            super(StandardCharsets.ISO_8859_1);
        }
    }

    @Builtin(name = "_PyTruffle_Unicode_AsASCIIString", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyTruffleUnicodeAsASCIIStringNode extends NativeEncoderNode {
        protected PyTruffleUnicodeAsASCIIStringNode() {
            super(StandardCharsets.US_ASCII);
        }
    }

    @Builtin(name = "PyTruffle_Unicode_AsUnicodeAndSize", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyTruffleUnicodeAsUnicodeAndSizeNode extends NativeBuiltin {
        @Specialization
        @TruffleBoundary
        Object doUnicode(PString s,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode) {
            char[] charArray = toJavaStringNode.execute(s.getValueUncached()).toCharArray();
            // stuff into byte[]
            ByteBuffer allocate = ByteBuffer.allocate(charArray.length * 2);
            for (int i = 0; i < charArray.length; i++) {
                allocate.putChar(charArray[i]);
            }
            return getContext().getEnv().asGuestValue(allocate.array());
        }
    }

    // directly called without landing function
    @Builtin(name = "PyTruffle_Unicode_DecodeUTF32", minNumOfPositionalArgs = 5)
    @GenerateNodeFactory
    abstract static class PyTruffleUnicodeDecodeUTF32Node extends NativeUnicodeBuiltin {

        @Specialization
        Object doUnicodeStringErrors(VirtualFrame frame, Object o, long size, TruffleString errors, int byteorder, Object errorMarker,
                        @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Cached GetByteArrayNode getByteArrayNode) {
            try {
                return toSulongNode.execute(decodeUTF32(getByteArrayNode.execute(o, size), (int) size, errors, byteorder));
            } catch (CharacterCodingException e) {
                return raiseNative(frame, errorMarker, PythonErrorType.UnicodeEncodeError, ErrorMessages.M, e);
            } catch (IllegalArgumentException e) {
                TruffleString csName = Charsets.getUTF32Name(byteorder);
                return raiseNative(frame, errorMarker, PythonErrorType.LookupError, ErrorMessages.UNKNOWN_ENCODING, csName);
            } catch (InteropException e) {
                return raiseNative(frame, errorMarker, PythonErrorType.TypeError, ErrorMessages.M, e);
            } catch (OverflowException e) {
                return raiseNative(frame, errorMarker, OverflowError, ErrorMessages.INPUT_TOO_LONG);
            }
        }

        @TruffleBoundary
        private TruffleString decodeUTF32(byte[] data, int size, TruffleString errors, int byteorder) throws CharacterCodingException {
            CharsetDecoder decoder = Charsets.getUTF32Charset(byteorder).newDecoder();
            CodingErrorAction action = BytesBuiltins.toCodingErrorAction(errors, this, TruffleString.EqualNode.getUncached());
            CharBuffer decode = decoder.onMalformedInput(action).onUnmappableCharacter(action).decode(wrap(data, 0, size));
            return toTruffleStringUncached(decode.toString());
        }
    }

    @Builtin(name = "PyTruffle_Unicode_AsWideChar", minNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class PyTruffleUnicodeAsWideCharNode extends NativeUnicodeBuiltin {
        @Specialization
        Object doUnicode(VirtualFrame frame, Object s, long elementSize, Object errorMarker,
                        @Cached UnicodeAsWideCharNode asWideCharNode,
                        @Cached CastToTruffleStringNode castStr) {
            try {
                PBytes wchars = asWideCharNode.executeLittleEndian(castStr.execute(s), elementSize);
                if (wchars != null) {
                    return wchars;
                } else {
                    return raiseNative(frame, errorMarker, PythonErrorType.ValueError, ErrorMessages.UNSUPPORTED_SIZE_WAS, "wchar", elementSize);
                }
            } catch (IllegalArgumentException e) {
                // TODO
                return raiseNative(frame, errorMarker, PythonErrorType.LookupError, ErrorMessages.M, e);
            }
        }
    }

    @Builtin(name = "PyTruffle_Bytes_AsString", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTruffleBytesAsStringNode extends NativeBuiltin {
        @Specialization
        static Object doBytes(PBytes bytes, @SuppressWarnings("unused") Object errorMarker) {
            return new PySequenceArrayWrapper(bytes, 1);
        }

        @Specialization
        static Object doUnicode(PString str, @SuppressWarnings("unused") Object errorMarker,
                        @Cached CastToTruffleStringNode castToStringNode) {
            return new CStringWrapper(castToStringNode.execute(str));
        }

        @Fallback
        Object doUnicode(VirtualFrame frame, Object o, Object errorMarker) {
            return raiseNative(frame, errorMarker, PythonErrorType.TypeError, ErrorMessages.EXPECTED_S_P_FOUND, "bytes", o);
        }
    }

    @Builtin(name = "PyTruffleHash_InitSecret", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffleHashGetSecret extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object get(Object secretPtr) {
            try {
                InteropLibrary lib = InteropLibrary.getUncached(secretPtr);
                byte[] secret = getContext().getHashSecret();
                int len = (int) lib.getArraySize(secretPtr);
                for (int i = 0; i < len; i++) {
                    lib.writeArrayElement(secretPtr, i, secret[i]);
                }
                return 0;
            } catch (UnsupportedMessageException | UnsupportedTypeException | InvalidArrayIndexException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @Builtin(name = "PyTruffleFrame_New", minNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class PyTruffleFrameNewNode extends PythonBuiltinNode {
        @Specialization
        Object newFrame(Object threadState, PCode code, PythonObject globals, Object locals) {
            Object frameLocals;
            if (locals == null || PGuards.isPNone(locals)) {
                frameLocals = factory().createDict();
            } else {
                frameLocals = locals;
            }
            return factory().createPFrame(threadState, code, globals, frameLocals);
        }
    }

    @Builtin(name = "PyTruffle_Set_SulongType", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTruffleSetSulongTypeNode extends NativeBuiltin {

        @Specialization(limit = "1")
        static Object doPythonObject(PythonClassNativeWrapper klass, Object ptr,
                        @CachedLibrary("klass") PythonNativeWrapperLibrary lib) {
            ((PythonManagedClass) lib.getDelegate(klass)).setSulongType(ptr);
            return ptr;
        }
    }

    // Called without landing node
    @Builtin(name = "PyTruffle_MemoryViewFromBuffer", minNumOfPositionalArgs = 11)
    @GenerateNodeFactory
    abstract static class PyTrufflMemoryViewFromBuffer extends NativeBuiltin {

        @Specialization
        Object wrap(VirtualFrame frame, Object bufferStructPointer, Object ownerObj, Object lenObj,
                        Object readonlyObj, Object itemsizeObj, Object formatObj,
                        Object ndimObj, Object bufPointer, Object shapePointer, Object stridesPointer, Object suboffsetsPointer,
                        @Cached ConditionProfile zeroDimProfile,
                        @Cached MemoryViewNodes.InitFlagsNode initFlagsNode,
                        @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached CastToJavaIntExactNode castToIntNode,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached TruffleString.CodePointLengthNode lengthNode,
                        @Cached TruffleString.CodePointAtIndexNode atIndexNode) {
            try {
                int ndim = castToIntNode.execute(ndimObj);
                int itemsize = castToIntNode.execute(itemsizeObj);
                int len = castToIntNode.execute(lenObj);
                boolean readonly = castToIntNode.execute(readonlyObj) != 0;
                TruffleString format = (TruffleString) asPythonObjectNode.execute(formatObj);
                Object owner = lib.isNull(ownerObj) ? null : asPythonObjectNode.execute(ownerObj);
                int[] shape = null;
                int[] strides = null;
                int[] suboffsets = null;
                if (zeroDimProfile.profile(ndim > 0)) {
                    if (!lib.isNull(shapePointer)) {
                        shape = new int[ndim];
                        for (int i = 0; i < ndim; i++) {
                            shape[i] = castToIntNode.execute(lib.readArrayElement(shapePointer, i));
                        }
                    } else {
                        assert ndim == 1;
                        shape = new int[1];
                        shape[0] = len / itemsize;
                    }
                    if (!lib.isNull(stridesPointer)) {
                        strides = new int[ndim];
                        for (int i = 0; i < ndim; i++) {
                            strides[i] = castToIntNode.execute(lib.readArrayElement(stridesPointer, i));
                        }
                    } else {
                        strides = PMemoryView.initStridesFromShape(ndim, itemsize, shape);
                    }
                    if (!lib.isNull(suboffsetsPointer)) {
                        suboffsets = new int[ndim];
                        for (int i = 0; i < ndim; i++) {
                            suboffsets[i] = castToIntNode.execute(lib.readArrayElement(suboffsetsPointer, i));
                        }
                    }
                }
                Object buffer = new NativeSequenceStorage(bufPointer, len, len, SequenceStorage.ListStorageType.Byte);
                int flags = initFlagsNode.execute(ndim, itemsize, shape, strides, suboffsets);
                BufferLifecycleManager bufferLifecycleManager = null;
                if (!lib.isNull(bufferStructPointer)) {
                    bufferLifecycleManager = new NativeBufferLifecycleManager.NativeBufferLifecycleManagerFromType(bufferStructPointer);
                }
                PMemoryView memoryview = factory().createMemoryView(getContext(), bufferLifecycleManager, buffer, owner, len, readonly, itemsize,
                                BufferFormat.forMemoryView(format, lengthNode, atIndexNode),
                                format, ndim, bufPointer, 0, shape, strides, suboffsets, flags);
                return toNewRefNode.execute(memoryview);
            } catch (PException e) {
                transformToNative(frame, e);
                return toNewRefNode.execute(getContext().getNativeNull());
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @Builtin(name = "PyTruffle_Bytes_EmptyWithCapacity", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffleBytesEmptyWithCapacityNode extends PythonUnaryBuiltinNode {

        @Specialization
        PBytes doInt(int size) {
            return factory().createBytes(new byte[size]);
        }

        @Specialization(rewriteOn = OverflowException.class)
        PBytes doLong(long size) throws OverflowException {
            return doInt(PInt.intValueExact(size));
        }

        @Specialization(replaces = "doLong")
        PBytes doLongOvf(long size,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            try {
                return doInt(PInt.intValueExact(size));
            } catch (OverflowException e) {
                throw raiseNode.raiseNumberTooLarge(IndexError, size);
            }
        }

        @Specialization(rewriteOn = OverflowException.class)
        PBytes doPInt(PInt size) throws OverflowException {
            return doInt(size.intValueExact());
        }

        @Specialization(replaces = "doPInt")
        PBytes doPIntOvf(PInt size,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            try {
                return doInt(size.intValueExact());
            } catch (OverflowException e) {
                throw raiseNode.raiseNumberTooLarge(IndexError, size);
            }
        }
    }

    private abstract static class UpcallLandingNode extends PythonVarargsBuiltinNode {
        @Override
        public Object varArgExecute(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            return execute(frame, self, arguments, PKeyword.EMPTY_KEYWORDS);
        }
    }

    @Builtin(name = "PyTruffle_Upcall_Borrowed", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    abstract static class UpcallBorrowedNode extends UpcallLandingNode {

        @Specialization
        Object upcall(VirtualFrame frame, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Cached ObjectUpcallNode upcallNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return toSulongNode.execute(upcallNode.execute(frame, args));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return toSulongNode.execute(getContext().getNativeNull());
            }
        }
    }

    @Builtin(name = "PyTruffle_Upcall_NewRef", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class UpcallNewRefNode extends UpcallLandingNode {

        @Specialization
        Object upcall(VirtualFrame frame, @SuppressWarnings("unused") Object self, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached ObjectUpcallNode upcallNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return toNewRefNode.execute(upcallNode.execute(frame, args));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return toNewRefNode.execute(getContext().getNativeNull());
            }
        }
    }

    @Builtin(name = "PyTruffle_Upcall_l", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class UpcallLNode extends UpcallLandingNode {

        @Specialization
        static Object upcall(VirtualFrame frame, @SuppressWarnings("unused") Object self, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached CastToNativeLongNode asLongNode,
                        @Cached ObjectUpcallNode upcallNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object result = asLongNode.execute(upcallNode.execute(frame, args));
                assert result instanceof Long || result instanceof PythonNativeVoidPtr;
                return result;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
        }
    }

    @Builtin(name = "PyTruffle_Upcall_d", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class UpcallDNode extends UpcallLandingNode {

        @Specialization
        static double upcall(VirtualFrame frame, @SuppressWarnings("unused") Object self, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached CastToJavaDoubleNode castToDoubleNode,
                        @Cached ObjectUpcallNode upcallNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return castToDoubleNode.execute(upcallNode.execute(frame, args));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1.0;
            }
        }
    }

    @Builtin(name = "PyTruffle_Upcall_ptr", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class UpcallPtrNode extends UpcallLandingNode {

        @Specialization
        Object upcall(VirtualFrame frame, @SuppressWarnings("unused") PythonModule cextModule, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached ObjectUpcallNode upcallNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return upcallNode.execute(frame, args);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return getContext().getNativeNull();
            }
        }
    }

    @Builtin(name = "PyTruffle_Cext_Upcall_Borrowed", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class UpcallCextBorrowedNode extends UpcallLandingNode {

        @Specialization(guards = "isStringCallee(args)")
        static Object upcall(VirtualFrame frame, PythonModule cextModule, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached CextUpcallNode upcallNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(upcallNode.execute(frame, cextModule, args));
        }

        @Specialization(guards = "!isStringCallee(args)")
        static Object doDirect(VirtualFrame frame, @SuppressWarnings("unused") PythonModule cextModule, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached DirectUpcallNode upcallNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(upcallNode.execute(frame, args));
        }

        public static boolean isStringCallee(Object[] args) {
            return PGuards.isString(args[0]);
        }
    }

    @Builtin(name = "PyTruffle_Cext_Upcall_NewRef", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    @ImportStatic(UpcallCextBorrowedNode.class)
    abstract static class UpcallCextNewRefNode extends UpcallLandingNode {

        @Specialization(guards = "isStringCallee(args)")
        static Object upcall(VirtualFrame frame, PythonModule cextModule, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached CextUpcallNode upcallNode,
                        @Shared("toNewRefNode") @Cached ToNewRefNode toNewRefNode) {
            return toNewRefNode.execute(upcallNode.execute(frame, cextModule, args));
        }

        @Specialization(guards = "!isStringCallee(args)")
        static Object doDirect(VirtualFrame frame, @SuppressWarnings("unused") PythonModule cextModule, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached DirectUpcallNode upcallNode,
                        @Shared("toNewRefNode") @Cached ToNewRefNode toNewRefNode) {
            return toNewRefNode.execute(upcallNode.execute(frame, args));
        }
    }

    @Builtin(name = "PyTruffle_Cext_Upcall_d", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    @ImportStatic(UpcallCextBorrowedNode.class)
    abstract static class UpcallCextDNode extends UpcallLandingNode {

        @Specialization(guards = "isStringCallee(args)")
        static double upcall(VirtualFrame frame, PythonModule cextModule, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached CextUpcallNode upcallNode,
                        @Shared("castToDoubleNode") @Cached CastToJavaDoubleNode castToDoubleNode) {
            return castToDoubleNode.execute(upcallNode.execute(frame, cextModule, args));
        }

        @Specialization(guards = "!isStringCallee(args)")
        static double doDirect(VirtualFrame frame, @SuppressWarnings("unused") PythonModule cextModule, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached DirectUpcallNode upcallNode,
                        @Shared("castToDoubleNode") @Cached CastToJavaDoubleNode castToDoubleNode) {
            return castToDoubleNode.execute(upcallNode.execute(frame, args));
        }
    }

    @Builtin(name = "PyTruffle_Cext_Upcall_l", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    @ImportStatic(UpcallCextBorrowedNode.class)
    abstract static class UpcallCextLNode extends UpcallLandingNode {

        @Specialization(guards = "isStringCallee(args)")
        static Object upcall(VirtualFrame frame, PythonModule cextModule, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached CextUpcallNode upcallNode,
                        @Shared("asLong") @Cached CastToNativeLongNode asLongNode) {
            return asLongNode.execute(upcallNode.execute(frame, cextModule, args));
        }

        @Specialization(guards = "!isStringCallee(args)")
        static Object doDirect(VirtualFrame frame, @SuppressWarnings("unused") PythonModule cextModule, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached DirectUpcallNode upcallNode,
                        @Shared("asLong") @Cached CastToNativeLongNode asLongNode) {
            return asLongNode.execute(upcallNode.execute(frame, args));
        }
    }

    @Builtin(name = "PyTruffle_Cext_Upcall_ptr", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    @ImportStatic(UpcallCextBorrowedNode.class)
    abstract static class UpcallCextPtrNode extends UpcallLandingNode {

        @Specialization(guards = "isStringCallee(args)")
        static Object upcall(VirtualFrame frame, PythonModule cextModule, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached CextUpcallNode upcallNode) {
            return upcallNode.execute(frame, cextModule, args);
        }

        @Specialization(guards = "!isStringCallee(args)")
        static Object doDirect(VirtualFrame frame, @SuppressWarnings("unused") PythonModule cextModule, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached DirectUpcallNode upcallNode) {
            return upcallNode.execute(frame, args);
        }
    }

    @Builtin(name = "to_long", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AsLong extends PythonUnaryBuiltinNode {
        @Specialization
        Object doIt(VirtualFrame frame, Object object,
                        @Cached CastToNativeLongNode asLongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object result = asLongNode.execute(object);
                assert result instanceof Long || result instanceof PythonNativeVoidPtr;
                return result;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
        }
    }

    @Builtin(name = "to_double", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AsDouble extends PythonUnaryBuiltinNode {
        @Specialization
        static double doIt(Object object,
                        @Cached CastToJavaDoubleNode castToDoubleNode) {
            return castToDoubleNode.execute(object);
        }
    }

    @Builtin(name = "PyTruffle_Register_NULL", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffleRegisterNULLNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doIt(Object object) {
            PythonNativeNull nn = getContext().getNativeNull();
            nn.setPtr(object);
            return nn;
        }
    }

    @Builtin(name = "PyTruffle_HandleCache_Create", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffleHandleCacheCreate extends PythonUnaryBuiltinNode {
        @Specialization
        static Object createCache(Object ptrToResolveHandle) {
            return new HandleCache(ptrToResolveHandle);
        }
    }

    @Builtin(name = "PyTruffle_PtrCache_Create", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTrufflePtrCacheCreate extends PythonUnaryBuiltinNode {
        @Specialization
        static Object createCache(int steal) {
            return new NativeReferenceCache(steal != 0);
        }
    }

    @Builtin(name = "PyTruffle_Decorate_Function", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTruffleDecorateFunction extends PythonBinaryBuiltinNode {
        @Specialization
        static PyCFunctionDecorator decorate(Object fun0, Object fun1) {
            return new PyCFunctionDecorator(fun0, fun1);
        }
    }

    @Builtin(name = "PyTruffle_Compute_Mro", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonTypes.class)
    public abstract static class PyTruffleComputeMroNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNativeObject(self)")
        Object doIt(Object self, TruffleString className) {
            PythonAbstractClass[] doSlowPath = TypeNodes.ComputeMroNode.doSlowPath(PythonNativeClass.cast(self));
            return factory().createTuple(new MroSequenceStorage(className, doSlowPath));
        }
    }

    @Builtin(name = "PyTruffle_NewTypeDict", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonTypes.class)
    public abstract static class PyTruffleNewTypeDict extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static PDict doGeneric(PythonNativeClass nativeClass) {
            PythonLanguage language = PythonLanguage.get(null);
            Store nativeTypeStore = new Store(language.getEmptyShape());
            DynamicObjectLibrary.getUncached().put(nativeTypeStore, PythonNativeClass.INSTANCESHAPE, language.getShapeForClass(nativeClass));
            return PythonObjectFactory.getUncached().createDict(new DynamicObjectStorage(nativeTypeStore));
        }
    }

    @Builtin(name = "PyTruffle_Type_Modified", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @TypeSystemReference(PythonTypes.class)
    public abstract static class PyTruffleTypeModifiedNode extends PythonTernaryBuiltinNode {

        @TruffleBoundary
        @Specialization(guards = {"isNativeClass(clazz)", "isNoValue(mroTuple)"})
        Object doIt(Object clazz, TruffleString name, @SuppressWarnings("unused") PNone mroTuple) {
            CyclicAssumption nativeClassStableAssumption = getContext().getNativeClassStableAssumption((PythonNativeClass) clazz, false);
            if (nativeClassStableAssumption != null) {
                nativeClassStableAssumption.invalidate("PyType_Modified(\"" + name.toJavaStringUncached() + "\") (without MRO) called");
            }
            SpecialMethodSlot.reinitializeSpecialMethodSlots(PythonNativeClass.cast(clazz), getLanguage());
            return PNone.NONE;
        }

        @TruffleBoundary
        @Specialization(guards = "isNativeClass(clazz)")
        Object doIt(Object clazz, TruffleString name, PTuple mroTuple,
                        @Cached("createClassProfile()") ValueProfile profile) {
            CyclicAssumption nativeClassStableAssumption = getContext().getNativeClassStableAssumption((PythonNativeClass) clazz, false);
            if (nativeClassStableAssumption != null) {
                nativeClassStableAssumption.invalidate("PyType_Modified(\"" + name.toJavaStringUncached() + "\") called");
            }
            SequenceStorage sequenceStorage = profile.profile(mroTuple.getSequenceStorage());
            if (sequenceStorage instanceof MroSequenceStorage) {
                ((MroSequenceStorage) sequenceStorage).lookupChanged();
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("invalid MRO object for native type \"" + name.toJavaStringUncached() + "\"");
            }
            SpecialMethodSlot.reinitializeSpecialMethodSlots(PythonNativeClass.cast(clazz), getLanguage());
            return PNone.NONE;
        }
    }

    @Builtin(name = "PyTruffle_FatalErrorFunc", parameterNames = {"func", "msg", "status"})
    @GenerateNodeFactory
    @TypeSystemReference(PythonTypes.class)
    public abstract static class PyTruffleFatalErrorFuncNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        Object doStrings(TruffleString func, TruffleString msg, int status) {
            CExtCommonNodes.fatalError(this, PythonContext.get(this), func, msg, status);
            return PNone.NONE;
        }

        @Specialization
        @TruffleBoundary
        Object doGeneric(Object funcObj, Object msgObj, int status) {
            TruffleString func = funcObj == PNone.NO_VALUE ? null : (TruffleString) funcObj;
            TruffleString msg = msgObj == PNone.NO_VALUE ? null : (TruffleString) msgObj;
            return doStrings(func, msg, status);
        }
    }

    @Builtin(name = "PyTruffle_OS_StringToDouble", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTruffleOSStringToDoubleNode extends NativeBuiltin {

        @Specialization
        Object doGeneric(VirtualFrame frame, TruffleString source, int reportPos,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode) {
            if (reportPos != 0) {
                ParsePosition pp = new ParsePosition(0);
                Number parse = parse(toJavaStringNode.execute(source), pp);
                if (parse != null) {
                    return factory().createTuple(new Object[]{doubleValue(parse), pp.getIndex()});
                }
            } else {
                try {
                    Number parse = parse(toJavaStringNode.execute(source));
                    return factory().createTuple(new Object[]{doubleValue(parse)});
                } catch (ParseException e) {
                    // ignore
                }
            }
            return raiseNative(frame, getContext().getNativeNull(), PythonBuiltinClassType.ValueError, ErrorMessages.COULD_NOT_CONVERT_STRING_TO_FLOAT, source);
        }

        @TruffleBoundary
        private static double doubleValue(Number number) {
            return number.doubleValue();
        }

        @TruffleBoundary
        private static Number parse(String source, ParsePosition pp) {
            // TODO TruffleString does not return BigDecimal [GR-38106]
            return DecimalFormat.getInstance().parse(source, pp);
        }

        @TruffleBoundary
        private static Number parse(String source) throws ParseException {
            // TODO TruffleString does not return BigDecimal
            return DecimalFormat.getInstance().parse(source);
        }
    }

    @Builtin(name = "PyTruffle_OS_DoubleToString", minNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    @ImportStatic(SpecialMethodNames.class)
    abstract static class PyTruffleOSDoubleToStringNode extends NativeBuiltin {

        /* keep in sync with macro 'TRANSLATE_TYPE' in 'pystrtod.c' */
        private static final int Py_DTST_FINITE = 0;
        private static final int Py_DTST_INFINITE = 1;
        private static final int Py_DTST_NAN = 2;

        @Specialization(guards = "isReprFormatCode(formatCode)")
        PTuple doRepr(VirtualFrame frame, double val, @SuppressWarnings("unused") int formatCode, @SuppressWarnings("unused") int precision, @SuppressWarnings("unused") int flags,
                        @Cached("create(Repr)") LookupAndCallUnaryNode callReprNode,
                        @Cached CastToTruffleStringNode castToStringNode) {
            Object reprString = callReprNode.executeObject(frame, val);
            try {
                return createResult(new CStringWrapper(castToStringNode.execute(reprString)), val);
            } catch (CannotCastException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException();
            }
        }

        @Specialization(guards = "!isReprFormatCode(formatCode)")
        Object doGeneric(VirtualFrame frame, double val, int formatCode, int precision, @SuppressWarnings("unused") int flags,
                        @Cached("create(Format)") LookupAndCallBinaryNode callReprNode,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached TruffleStringBuilder.AppendIntNumberNode appendIntNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.AppendCodePointNode appendCodepointNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            try {
                Object reprString = callReprNode.executeObject(frame, val,
                                joinFormatCode(formatCode, precision, appendIntNode, appendStringNode, appendCodepointNode, toStringNode));
                try {
                    return createResult(new CStringWrapper(castToStringNode.execute(reprString)), val);
                } catch (CannotCastException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException();
                }
            } catch (PException e) {
                transformToNative(frame, e);
                return getContext().getNativeNull();
            }
        }

        private static TruffleString joinFormatCode(int formatCode, int precision, TruffleStringBuilder.AppendIntNumberNode appendIntNode, TruffleStringBuilder.AppendStringNode appendStringNode,
                        TruffleStringBuilder.AppendCodePointNode appendCodepointNode, TruffleStringBuilder.ToStringNode toStringNode) {
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
            appendStringNode.execute(sb, T_DOT);
            appendIntNode.execute(sb, precision);
            appendCodepointNode.execute(sb, formatCode, 1, true);
            return toStringNode.execute(sb);
        }

        private PTuple createResult(Object str, double val) {
            return factory().createTuple(new Object[]{str, getTypeCode(val)});
        }

        private static int getTypeCode(double val) {
            if (Double.isInfinite(val)) {
                return Py_DTST_INFINITE;
            } else if (Double.isNaN(val)) {
                return Py_DTST_NAN;
            }
            assert Double.isFinite(val);
            return Py_DTST_FINITE;
        }

        protected static boolean isReprFormatCode(int formatCode) {
            return (char) formatCode == 'r';
        }
    }

    @ReportPolymorphism
    abstract static class CastArgsNode extends Node {

        public abstract Object[] execute(VirtualFrame frame, Object argsObj);

        @Specialization(guards = "lib.isNull(argsObj)")
        @SuppressWarnings("unused")
        static Object[] doNull(VirtualFrame frame, Object argsObj,
                        @Shared("lib") @CachedLibrary(limit = "3") InteropLibrary lib) {
            return EMPTY_OBJECT_ARRAY;
        }

        @Specialization(guards = "!lib.isNull(argsObj)")
        static Object[] doNotNull(VirtualFrame frame, Object argsObj,
                        @Cached ExecutePositionalStarargsNode expandArgsNode,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Shared("lib") @CachedLibrary(limit = "3") @SuppressWarnings("unused") InteropLibrary lib) {
            return expandArgsNode.executeWith(frame, asPythonObjectNode.execute(argsObj));
        }
    }

    @ReportPolymorphism
    abstract static class CastKwargsNode extends Node {

        public abstract PKeyword[] execute(Object kwargsObj);

        @Specialization(guards = "lib.isNull(kwargsObj) || isEmptyDict(kwargsToJavaNode, lenNode, kwargsObj)", limit = "1")
        @SuppressWarnings("unused")
        static PKeyword[] doNoKeywords(Object kwargsObj,
                        @Shared("lenNode") @Cached HashingCollectionNodes.LenNode lenNode,
                        @Shared("kwargsToJavaNode") @Cached AsPythonObjectNode kwargsToJavaNode,
                        @Shared("lib") @CachedLibrary(limit = "3") InteropLibrary lib) {
            return PKeyword.EMPTY_KEYWORDS;
        }

        @Specialization(guards = {"!lib.isNull(kwargsObj)", "!isEmptyDict(kwargsToJavaNode, lenNode, kwargsObj)"}, limit = "1")
        static PKeyword[] doKeywords(Object kwargsObj,
                        @Shared("lenNode") @Cached @SuppressWarnings("unused") HashingCollectionNodes.LenNode lenNode,
                        @Shared("kwargsToJavaNode") @Cached AsPythonObjectNode kwargsToJavaNode,
                        @Shared("lib") @CachedLibrary(limit = "3") @SuppressWarnings("unused") InteropLibrary lib,
                        @Cached ExpandKeywordStarargsNode expandKwargsNode) {
            return expandKwargsNode.execute(kwargsToJavaNode.execute(kwargsObj));
        }

        static boolean isEmptyDict(AsPythonObjectNode asPythonObjectNode, HashingCollectionNodes.LenNode lenNode, Object kwargsObj) {
            Object unwrapped = asPythonObjectNode.execute(kwargsObj);
            if (unwrapped instanceof PDict) {
                return lenNode.execute((PDict) unwrapped) == 0;
            }
            return false;
        }
    }

    public abstract static class ParseTupleAndKeywordsBaseNode extends PythonVarargsBuiltinNode {

        @Override
        public Object varArgExecute(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            return execute(frame, self, arguments, PKeyword.EMPTY_KEYWORDS);
        }

        public static int doConvert(CExtContext nativeContext, Object argv, Object nativeKwds, Object nativeFormat, Object nativeKwdnames, Object nativeVarargs,
                        SplitFormatStringNode splitFormatStringNode,
                        InteropLibrary kwdsRefLib,
                        InteropLibrary kwdnamesRefLib,
                        ConditionProfile kwdsProfile,
                        ConditionProfile kwdnamesProfile,
                        CExtAsPythonObjectNode kwdsToJavaNode,
                        CastToTruffleStringNode castToStringNode,
                        CExtParseArgumentsNode.ParseTupleAndKeywordsNode parseTupleAndKeywordsNode) {

            // force 'format' to be a String
            TruffleString[] split;
            try {
                split = splitFormatStringNode.execute(castToStringNode.execute(nativeFormat));
                assert split.length == 2;
            } catch (CannotCastException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException();
            }

            TruffleString format = split[0];
            TruffleString functionName = split[1];

            // sort out if kwds is native NULL
            Object kwds;
            if (kwdsProfile.profile(kwdsRefLib.isNull(nativeKwds))) {
                kwds = null;
            } else {
                kwds = kwdsToJavaNode.execute(nativeContext, nativeKwds);
            }

            // sort out if kwdnames is native NULL
            Object kwdnames = kwdnamesProfile.profile(kwdnamesRefLib.isNull(nativeKwdnames)) ? null : nativeKwdnames;

            return parseTupleAndKeywordsNode.execute(functionName, argv, kwds, format, kwdnames, nativeVarargs, nativeContext);
        }

        static Object getKwds(Object[] arguments) {
            return arguments[1];
        }

        static Object getKwdnames(Object[] arguments) {
            return arguments[3];
        }
    }

    @Builtin(name = "PyTruffle_Arg_ParseTupleAndKeywords", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class ParseTupleAndKeywordsNode extends ParseTupleAndKeywordsBaseNode {

        @Specialization(guards = "arguments.length == 5", limit = "2")
        static int doConvert(@SuppressWarnings("unused") Object self, Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                        @Cached SplitFormatStringNode splitFormatStringNode,
                        @CachedLibrary("getKwds(arguments)") InteropLibrary kwdsInteropLib,
                        @CachedLibrary("getKwdnames(arguments)") InteropLibrary kwdnamesRefLib,
                        @Cached ConditionProfile kwdsProfile,
                        @Cached ConditionProfile kwdnamesProfile,
                        @Cached AsPythonObjectNode argvToJavaNode,
                        @Cached AsPythonObjectNode kwdsToJavaNode,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached CExtParseArgumentsNode.ParseTupleAndKeywordsNode parseTupleAndKeywordsNode) {
            CExtContext nativeContext = PythonContext.get(splitFormatStringNode).getCApiContext();
            Object argv = argvToJavaNode.execute(arguments[0]);
            return ParseTupleAndKeywordsBaseNode.doConvert(nativeContext, argv, arguments[1], arguments[2], arguments[3], arguments[4], splitFormatStringNode, kwdsInteropLib, kwdnamesRefLib,
                            kwdsProfile, kwdnamesProfile, kwdsToJavaNode, castToStringNode, parseTupleAndKeywordsNode);
        }

    }

    @Builtin(name = "PyTruffle_Create_Lightweight_Upcall", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffleCreateLightweightUpcall extends PythonUnaryBuiltinNode {
        private static final TruffleString T_PY_TRUFFLE_OBJECT_ALLOC = tsLiteral("PyTruffle_Object_Alloc");
        private static final TruffleString T_PY_TRUFFLE_OBJECT_FREE = tsLiteral("PyTruffle_Object_Free");

        @Specialization
        static Object doGeneric(TruffleString key,
                        @Cached TruffleString.EqualNode eqNode) {
            if (eqNode.execute(key, T_PY_TRUFFLE_OBJECT_ALLOC, TS_ENCODING)) {
                return new PyTruffleObjectAlloc();
            } else if (eqNode.execute(key, T_PY_TRUFFLE_OBJECT_FREE, TS_ENCODING)) {
                return new PyTruffleObjectFree();
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalArgumentException("");
        }
    }

    @Builtin(name = "PyTruffle_TraceMalloc_Track", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @ImportStatic(CApiGuards.class)
    abstract static class PyTruffleTraceMallocTrack extends PythonBuiltinNode {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(PyTruffleTraceMallocTrack.class);

        @Specialization(guards = {"isSingleContext()", "domain == cachedDomain"}, limit = "3")
        int doCachedDomainIdx(VirtualFrame frame, @SuppressWarnings("unused") long domain, Object pointerObject, long size,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached("domain") @SuppressWarnings("unused") long cachedDomain,
                        @Cached("lookupDomain(domain)") int cachedDomainIdx) {

            CApiContext cApiContext = getContext().getCApiContext();
            cApiContext.getTraceMallocDomain(cachedDomainIdx).track(pointerObject, size);
            cApiContext.increaseMemoryPressure(frame, getThreadStateNode, this, size);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(() -> PythonUtils.formatJString("Tracking memory (size: %d): %s", size, CApiContext.asHex(pointerObject)));
            }
            return 0;
        }

        @Specialization(replaces = "doCachedDomainIdx")
        int doGeneric(VirtualFrame frame, long domain, Object pointerObject, long size,
                        @Cached GetThreadStateNode getThreadStateNode) {
            return doCachedDomainIdx(frame, domain, pointerObject, size, getThreadStateNode, domain, lookupDomain(domain));
        }

        int lookupDomain(long domain) {
            return getContext().getCApiContext().findOrCreateTraceMallocDomain(domain);
        }
    }

    @Builtin(name = "PyTruffle_TraceMalloc_Untrack", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(CApiGuards.class)
    abstract static class PyTruffleTraceMallocUntrack extends PythonBinaryBuiltinNode {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(PyTruffleTraceMallocUntrack.class);

        @Specialization(guards = {"isSingleContext()", "domain == cachedDomain"}, limit = "3")
        int doCachedDomainIdx(@SuppressWarnings("unused") long domain, Object pointerObject,
                        @Cached("domain") @SuppressWarnings("unused") long cachedDomain,
                        @Cached("lookupDomain(domain)") int cachedDomainIdx) {

            CApiContext cApiContext = getContext().getCApiContext();
            long trackedMemorySize = cApiContext.getTraceMallocDomain(cachedDomainIdx).untrack(pointerObject);
            cApiContext.reduceMemoryPressure(trackedMemorySize);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(() -> PythonUtils.formatJString("Untracking memory (size: %d): %s", trackedMemorySize, CApiContext.asHex(pointerObject)));
            }
            return 0;
        }

        @Specialization(replaces = "doCachedDomainIdx")
        int doGeneric(long domain, Object pointerObject) {
            return doCachedDomainIdx(domain, pointerObject, domain, lookupDomain(domain));
        }

        int lookupDomain(long domain) {
            return getContext().getCApiContext().findOrCreateTraceMallocDomain(domain);
        }
    }

    @Builtin(name = "PyTruffle_TraceMalloc_NewReference", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffleTraceMallocNewReference extends PythonUnaryBuiltinNode {

        @Specialization
        @SuppressWarnings("unused")
        static int doCachedDomainIdx(Object pointerObject) {
            // TODO(fa): implement; capture tracebacks in PyTraceMalloc_Track and update them
            // here
            return 0;
        }
    }

    abstract static class PyTruffleGcTracingNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = {"!traceCalls(getContext())", "traceMem(getContext())"})
        int doNativeWrapper(Object ptr,
                        @Shared("lib") @CachedLibrary(limit = "3") InteropLibrary lib) {
            trace(getContext(), CApiContext.asPointer(ptr, lib), null, null);
            return 0;
        }

        @Specialization(guards = {"traceCalls(getContext())", "traceMem(getContext())"})
        int doNativeWrapperTraceCall(VirtualFrame frame, Object ptr,
                        @Cached GetCurrentFrameRef getCurrentFrameRef,
                        @Shared("lib") @CachedLibrary(limit = "3") InteropLibrary lib) {

            PFrame.Reference ref = getCurrentFrameRef.execute(frame);
            trace(getContext(), CApiContext.asPointer(ptr, lib), ref, null);
            return 0;
        }

        @Specialization(guards = "!traceMem(getContext())")
        static int doNothing(@SuppressWarnings("unused") Object ptr) {
            // do nothing
            return 0;
        }

        static boolean traceMem(PythonContext context) {
            return context.getOption(PythonOptions.TraceNativeMemory);
        }

        static boolean traceCalls(PythonContext context) {
            return context.getOption(PythonOptions.TraceNativeMemoryCalls);
        }

        @SuppressWarnings("unused")
        protected void trace(PythonContext context, Object ptr, Reference ref, TruffleString className) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("should not reach");
        }
    }

    @Builtin(name = "PyTruffle_GC_Untrack", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffleGcUntrack extends PyTruffleGcTracingNode {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(PyTruffleGcUntrack.class);

        @Override
        protected void trace(PythonContext context, Object ptr, Reference ref, TruffleString className) {
            LOGGER.fine(() -> PythonUtils.formatJString("Untracking container object at %s", CApiContext.asHex(ptr)));
            context.getCApiContext().untrackObject(ptr, ref, className);
        }
    }

    @Builtin(name = "PyTruffle_GC_Track", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffleGcTrack extends PyTruffleGcTracingNode {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(PyTruffleGcTrack.class);

        @Override
        protected void trace(PythonContext context, Object ptr, Reference ref, TruffleString className) {
            LOGGER.fine(() -> PythonUtils.formatJString("Tracking container object at %s", CApiContext.asHex(ptr)));
            context.getCApiContext().trackObject(ptr, ref, className);
        }
    }

    @Builtin(name = "PyTruffle_Native_Options")
    @GenerateNodeFactory
    abstract static class PyTruffleNativeOptions extends PythonBuiltinNode {
        private static final int TRACE_MEM = 0x1;

        @Specialization
        int getNativeOptions() {
            int options = 0;
            if (getContext().getOption(PythonOptions.TraceNativeMemory)) {
                options |= TRACE_MEM;
            }
            return options;
        }
    }

    /**
     * This will be called right before the call to stdlib's {@code free} function.
     */
    @Builtin(name = "PyTruffle_Trace_Free", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTruffleTraceFree extends PythonBinaryBuiltinNode {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(PyTruffleTraceFree.class);

        @Specialization(limit = "2")
        static int doNativeWrapperLong(Object ptr, long size,
                        @CachedLibrary("ptr") InteropLibrary lib,
                        @Cached GetCurrentFrameRef getCurrentFrameRef) {

            PythonContext context = PythonContext.get(lib);
            CApiContext cApiContext = context.getCApiContext();
            cApiContext.reduceMemoryPressure(size);

            boolean isLoggable = LOGGER.isLoggable(Level.FINER);
            boolean traceNativeMemory = context.getOption(PythonOptions.TraceNativeMemory);
            if ((isLoggable || traceNativeMemory) && !lib.isNull(ptr)) {
                boolean traceNativeMemoryCalls = context.getOption(PythonOptions.TraceNativeMemoryCalls);
                if (traceNativeMemory) {
                    PFrame.Reference ref = null;
                    if (traceNativeMemoryCalls) {
                        ref = getCurrentFrameRef.execute(null);
                    }
                    AllocInfo allocLocation = cApiContext.traceFree(CApiContext.asPointer(ptr, lib), ref, null);
                    if (allocLocation != null) {
                        LOGGER.finer(() -> PythonUtils.formatJString("Freeing pointer (size: %d): %s", allocLocation.size, CApiContext.asHex(ptr)));

                        if (traceNativeMemoryCalls) {
                            Reference left = allocLocation.allocationSite;
                            PFrame pyFrame = null;
                            while (pyFrame == null && left != null) {
                                pyFrame = left.getPyFrame();
                                left = left.getCallerInfo();
                            }
                            if (pyFrame != null) {
                                final PFrame f = pyFrame;
                                LOGGER.finer(() -> PythonUtils.formatJString("Free'd pointer was allocated at: %s", f.getTarget()));
                            }
                        }
                    }
                } else {
                    assert isLoggable;
                    LOGGER.finer(() -> PythonUtils.formatJString("Freeing pointer: %s", CApiContext.asHex(ptr)));
                }
            }
            return 0;
        }

        @Specialization(limit = "2", replaces = "doNativeWrapperLong")
        static int doNativeWrapper(Object ptr, Object sizeObject,
                        @Cached CastToJavaLongLossyNode castToJavaLongNode,
                        @CachedLibrary("ptr") InteropLibrary lib,
                        @Cached GetCurrentFrameRef getCurrentFrameRef) {
            long size;
            try {
                size = castToJavaLongNode.execute(sizeObject);
            } catch (CannotCastException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalArgumentException("invalid type for second argument 'objectSize'");
            }
            return doNativeWrapperLong(ptr, size, lib, getCurrentFrameRef);
        }

    }

    @Builtin(name = "PyTruffle_Trace_Type", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTruffleTraceType extends PythonBinaryBuiltinNode {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(PyTruffleTraceType.class);

        @Specialization(limit = "3")
        int trace(Object ptr, Object classNameObj,
                        @CachedLibrary("ptr") InteropLibrary ptrLib,
                        @CachedLibrary("classNameObj") InteropLibrary nameLib,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
            final TruffleString className;
            if (nameLib.isString(classNameObj)) {
                try {
                    className = switchEncodingNode.execute(nameLib.asTruffleString(classNameObj), TS_ENCODING);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw new IllegalStateException();
                }
            } else {
                className = null;
            }
            PythonContext context = getContext();
            Object primitivePtr = CApiContext.asPointer(ptr, ptrLib);
            context.getCApiContext().traceStaticMemory(primitivePtr, null, className);
            LOGGER.fine(() -> PythonUtils.formatJString("Initializing native type %s (ptr = %s)", className, CApiContext.asHex(primitivePtr)));
            return 0;
        }
    }

    @ImportStatic(CExtContext.class)
    abstract static class NewClassMethodNode extends Node {

        abstract Object execute(Object methodDefPtr, TruffleString name, Object methObj, Object flags, Object wrapper, Object type, Object doc,
                        PythonObjectFactory factory);

        @Specialization(guards = "isClassOrStaticMethod(flags)")
        static Object classOrStatic(Object methodDefPtr, TruffleString name, Object methObj, int flags, int wrapper, Object type,
                        Object doc, PythonObjectFactory factory,
                        @CachedLibrary(limit = "1") DynamicObjectLibrary dylib,
                        @Shared("cf") @Cached CreateFunctionNode createFunctionNode,
                        @Shared("cstr") @Cached CharPtrToJavaObjectNode cstrPtr) {
            Object func = createFunctionNode.execute(name, methObj, wrapper, type, flags, factory);
            PythonObject function;
            if ((flags & METH_CLASS) != 0) {
                function = factory.createClassmethodFromCallableObj(func);
            } else {
                function = factory.createStaticmethodFromCallableObj(func);
            }
            dylib.put(function, T___NAME__, name);
            dylib.put(function, T___DOC__, cstrPtr.execute(doc));
            dylib.put(function, METHOD_DEF_PTR, methodDefPtr);
            return function;
        }

        @Specialization(guards = "!isClassOrStaticMethod(flags)")
        static Object doNativeCallable(Object methodDefPtr, TruffleString name, Object methObj, int flags, int wrapper, Object type,
                        Object doc, PythonObjectFactory factory,
                        @Cached PyObjectSetAttrNode setattr,
                        @Cached WriteAttributeToObjectNode write,
                        @Shared("cf") @Cached CreateFunctionNode createFunctionNode,
                        @Shared("cstr") @Cached CharPtrToJavaObjectNode cstrPtr) {
            Object func = createFunctionNode.execute(name, methObj, wrapper, type, flags, factory);
            setattr.execute(func, T___NAME__, name);
            setattr.execute(func, T___DOC__, cstrPtr.execute(doc));
            write.execute(func, METHOD_DEF_PTR, methodDefPtr);
            return func;
        }
    }

    // directly called without landing function
    @Builtin(name = "AddFunctionToType", parameterNames = {"method_def_ptr", "primary", "tpDict", "name", "cfunc", "flags", "wrapper", "doc"})
    @ArgumentClinic(name = "name", conversion = ClinicConversion.TString)
    @ArgumentClinic(name = "flags", conversion = ClinicConversion.Int)
    @ArgumentClinic(name = "wrapper", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class AddFunctionToTypeNode extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PythonCextBuiltinsClinicProviders.AddFunctionToTypeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object classMethod(VirtualFrame frame, Object methodDefPtr, Object primary,
                        Object tpDict, TruffleString name, Object cfunc, int flags, int wrapper, Object doc,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Cached NewClassMethodNode newClassMethodNode,
                        @Cached DictBuiltins.SetItemNode setItemNode) {
            Object type = asPythonObjectNode.execute(primary);
            Object func = newClassMethodNode.execute(methodDefPtr, name, cfunc, flags, wrapper, type, doc, factory());
            Object dict = asPythonObjectNode.execute(tpDict);
            setItemNode.execute(frame, dict, name, func);
            return 0;
        }
    }

    // directly called without landing function
    @Builtin(name = "AddFunctionToModule", parameterNames = {"method_def_ptr", "primary", "name", "cfunc", "flags", "wrapper", "doc"})
    @ArgumentClinic(name = "name", conversion = ClinicConversion.TString)
    @ArgumentClinic(name = "flags", conversion = ClinicConversion.Int)
    @ArgumentClinic(name = "wrapper", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class AddFunctionToModuleNode extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PythonCextBuiltinsClinicProviders.AddFunctionToModuleNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object moduleFunction(VirtualFrame frame, Object methodDefPtr, Object primary,
                        TruffleString name, Object cfunc, int flags, int wrapper, Object doc,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Cached ObjectBuiltins.SetattrNode setattrNode,
                        @CachedLibrary(limit = "1") DynamicObjectLibrary dylib,
                        @Cached CFunctionNewExMethodNode cFunctionNewExMethodNode) {
            PythonModule mod = (PythonModule) asPythonObjectNode.execute(primary);
            Object modName = dylib.getOrDefault(mod.getStorage(), T___NAME__, null);
            assert modName != null : "module name is missing!";
            Object func = cFunctionNewExMethodNode.execute(methodDefPtr, name, cfunc, flags, wrapper, mod, modName, doc, factory());
            setattrNode.execute(frame, mod, name, func);
            return 0;
        }
    }

    /**
     * Signature: {@code add_slot(primary, tpDict, name", cfunc, flags, wrapper, doc)}
     */
    // directly called without landing function
    @Builtin(name = "add_slot", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class AddSlotNode extends PythonVarargsBuiltinNode {

        @Override
        public final Object varArgExecute(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords) {
            return execute(frame, self, arguments, keywords);
        }

        @Specialization
        int doWithPrimitives(@SuppressWarnings("unused") Object self, Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                if (arguments.length != 7) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw PRaiseNode.raiseUncached(this, TypeError, ErrorMessages.TAKES_EXACTLY_D_ARGUMENTS_D_GIVEN, "add_slot", 7, arguments.length);
                }
                addSlot(arguments[0], arguments[1], arguments[2], arguments[3], castInt(arguments[4]), castInt(arguments[5]), arguments[6],
                                AsPythonObjectNodeGen.getUncached(), CastToTruffleStringNode.getUncached(), FromCharPointerNodeGen.getUncached(), InteropLibrary.getUncached(),
                                CreateFunctionNodeGen.getUncached(), WriteAttributeToDynamicObjectNode.getUncached(), HashingStorageLibrary.getUncached());
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @TruffleBoundary
        private static void addSlot(Object clsPtr, Object tpDictPtr, Object namePtr, Object cfunc, int flags, int wrapper, Object docPtr,
                        AsPythonObjectNode asPythonObjectNode,
                        CastToTruffleStringNode castToJavaStringNode,
                        FromCharPointerNode fromCharPointerNode,
                        InteropLibrary docPtrLib,
                        CreateFunctionNode createFunctionNode,
                        WriteAttributeToDynamicObjectNode writeDocNode,
                        HashingStorageLibrary dictStorageLib) {
            Object clazz = asPythonObjectNode.execute(clsPtr);
            PDict tpDict = castPDict(asPythonObjectNode.execute(tpDictPtr));

            TruffleString memberName;
            try {
                memberName = castToJavaStringNode.execute(asPythonObjectNode.execute(namePtr));
            } catch (CannotCastException e) {
                throw CompilerDirectives.shouldNotReachHere("Cannot cast member name to string");
            }
            // note: 'doc' may be NULL; in this case, we would store 'None'
            Object memberDoc = CharPtrToJavaObjectNode.run(docPtr, fromCharPointerNode, docPtrLib);

            // create wrapper descriptor
            Object wrapperDescriptor = createFunctionNode.execute(memberName, cfunc, wrapper, clazz, flags, PythonObjectFactory.getUncached());
            writeDocNode.execute(wrapperDescriptor, SpecialAttributeNames.T___DOC__, memberDoc);

            // add wrapper descriptor to tp_dict
            HashingStorage dictStorage = tpDict.getDictStorage();
            HashingStorage updatedStorage = dictStorageLib.setItem(dictStorage, memberName, wrapperDescriptor);
            if (dictStorage != updatedStorage) {
                tpDict.setDictStorage(updatedStorage);
            }
        }
    }

    abstract static class CFunctionNewExMethodNode extends Node {

        abstract Object execute(Object methodDefPtr, TruffleString name, Object methObj, Object flags, Object wrapper, Object self, Object module, Object cls, Object doc,
                        PythonObjectFactory factory);

        final Object execute(Object methodDefPtr, TruffleString name, Object methObj, Object flags, Object wrapper, Object self, Object module, Object doc,
                        PythonObjectFactory factory) {
            return execute(methodDefPtr, name, methObj, flags, wrapper, self, module, PNone.NO_VALUE, doc, factory);
        }

        @Specialization
        static Object doNativeCallable(Object methodDefPtr, TruffleString name, Object methObj, Object flags, Object wrapper, Object self, Object module, Object cls, Object doc,
                        PythonObjectFactory factory,
                        @SuppressWarnings("unused") @Cached AsPythonObjectNode asPythonObjectNode,
                        @Cached CreateFunctionNode createFunctionNode,
                        @CachedLibrary(limit = "1") DynamicObjectLibrary dylib,
                        @Cached CharPtrToJavaObjectNode charPtrToJavaObjectNode) {
            Object f = createFunctionNode.execute(name, methObj, wrapper, PNone.NO_VALUE, flags, factory);
            assert f instanceof PBuiltinFunction;
            PBuiltinFunction func = (PBuiltinFunction) f;
            dylib.put(func.getStorage(), T___NAME__, name);
            Object strDoc = charPtrToJavaObjectNode.execute(doc);
            dylib.put(func.getStorage(), T___DOC__, strDoc);
            PBuiltinMethod method;
            if (cls != PNone.NO_VALUE) {
                method = factory.createBuiltinMethod(self, func, cls);
            } else {
                method = factory.createBuiltinMethod(self, func);
            }
            dylib.put(method.getStorage(), T___MODULE__, module);
            dylib.put(method.getStorage(), METHOD_DEF_PTR, methodDefPtr);
            return method;
        }
    }

    // directly called without landing function
    @Builtin(name = "PyTruffle_Unicode_FromFormat", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTruffleUnicodeFromFromat extends PythonBuiltinNode {
        @Specialization
        Object doGeneric(VirtualFrame frame, TruffleString format, Object vaList,
                        @Cached UnicodeFromFormatNode unicodeFromFormatNode,
                        @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return toSulongNode.execute(unicodeFromFormatNode.execute(format, vaList));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return getContext().getNativeNull();
            }
        }
    }

    @Builtin(name = "PyTruffle_Bytes_CheckEmbeddedNull", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffleBytesCheckEmbeddedNull extends PythonUnaryBuiltinNode {

        @Specialization
        static int doBytes(PBytes bytes,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached GetItemScalarNode getItemScalarNode) {

            SequenceStorage sequenceStorage = bytes.getSequenceStorage();
            int len = lenNode.execute(sequenceStorage);
            try {
                for (int i = 0; i < len; i++) {
                    if (getItemScalarNode.executeInt(sequenceStorage, i) == 0) {
                        return -1;
                    }
                }
            } catch (UnexpectedResultException e) {
                throw CompilerDirectives.shouldNotReachHere("bytes object contains non-int value");
            }
            return 0;
        }
    }

    // directly called without landing function
    @Builtin(name = "AddMember", takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class AddMemberNode extends PythonVarargsBuiltinNode {

        @Override
        public final Object varArgExecute(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords) {
            return doWithPrimitives(self, arguments, keywords);
        }

        @TruffleBoundary
        @Specialization
        int doWithPrimitives(@SuppressWarnings("unused") Object self, Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords) {
            try {
                if (arguments.length != 7) {
                    PRaiseNode.raiseUncached(this, TypeError, ErrorMessages.TAKES_EXACTLY_D_ARGUMENTS_D_GIVEN, "AddMember", 7, arguments.length);
                }
                Object clazz = AsPythonObjectNodeGen.getUncached().execute(arguments[0]);
                Object tpDict = AsPythonObjectNodeGen.getUncached().execute(arguments[1]);
                Object nameObj = AsPythonObjectNodeGen.getUncached().execute(arguments[2]);
                addMember(getLanguage(), clazz, tpDict, nameObj, castInt(arguments[3]), castInt(arguments[4]), castInt(arguments[5]), arguments[6],
                                CastToTruffleStringNode.getUncached(), FromCharPointerNodeGen.getUncached(), InteropLibrary.getUncached(),
                                PythonObjectFactory.getUncached(), WriteAttributeToDynamicObjectNode.getUncached(), HashingStorageLibrary.getUncached());
                return 0;
            } catch (PException e) {
                TransformExceptionToNativeNodeGen.getUncached().execute(e);
                return -1;
            }
        }

        @TruffleBoundary
        public static void addMember(PythonLanguage language, Object clazz, Object tpDictObj, Object nameObj, int memberType, int offset, int canSet, Object docPtr,
                        CastToTruffleStringNode castToTruffleStringNode,
                        FromCharPointerNode fromCharPointerNode,
                        InteropLibrary docPtrLib,
                        PythonObjectFactory factory,
                        WriteAttributeToDynamicObjectNode writeDocNode,
                        HashingStorageLibrary dictStorageLib) {

            PDict tpDict = castPDict(tpDictObj);
            TruffleString memberName;
            try {
                memberName = castToTruffleStringNode.execute(nameObj);
            } catch (CannotCastException e) {
                throw CompilerDirectives.shouldNotReachHere("Cannot cast member name to string");
            }
            // note: 'doc' may be NULL; in this case, we would store 'None'
            Object memberDoc = CharPtrToJavaObjectNode.run(docPtr, fromCharPointerNode, docPtrLib);
            PBuiltinFunction getterObject = ReadMemberNode.createBuiltinFunction(language, clazz, memberName, memberType, offset);

            Object setterObject = null;
            if (canSet != 0) {
                setterObject = WriteMemberNode.createBuiltinFunction(language, clazz, memberName, memberType, offset);
            }

            // create member descriptor
            GetSetDescriptor memberDescriptor = factory.createMemberDescriptor(getterObject, setterObject, memberName, clazz);
            writeDocNode.execute(memberDescriptor, SpecialAttributeNames.T___DOC__, memberDoc);

            // add member descriptor to tp_dict
            HashingStorage dictStorage = tpDict.getDictStorage();
            HashingStorage updatedStorage = dictStorageLib.setItem(dictStorage, memberName, memberDescriptor);
            if (dictStorage != updatedStorage) {
                tpDict.setDictStorage(updatedStorage);
            }
        }
    }

    static PDict castPDict(Object tpDictObj) {
        if (tpDictObj instanceof PDict) {
            return (PDict) tpDictObj;
        }
        throw CompilerDirectives.shouldNotReachHere("tp_dict object must be a Python dict");
    }

    static int castInt(Object object) {
        if (object instanceof Integer) {
            return (int) object;
        } else if (object instanceof Long) {
            long lval = (long) object;
            if (PInt.isIntRange(lval)) {
                return (int) lval;
            }
        }
        throw CompilerDirectives.shouldNotReachHere("expected Java int");
    }

    abstract static class CreateGetSetNode extends Node {

        abstract GetSetDescriptor execute(TruffleString name, Object cls, Object getter, Object setter, Object doc, Object closure,
                        PythonLanguage language,
                        PythonObjectFactory factory);

        @Specialization
        static GetSetDescriptor createGetSet(TruffleString name, Object clazz, Object getter, Object setter, Object doc, Object closure,
                        PythonLanguage language,
                        PythonObjectFactory factory,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @CachedLibrary(limit = "1") DynamicObjectLibrary dylib,
                        @Cached CharPtrToJavaObjectNode fromCharPointerNode,
                        @CachedLibrary(limit = "2") InteropLibrary interopLibrary) {
            // note: 'doc' may be NULL; in this case, we would store 'None'
            Object cls = asPythonObjectNode.execute(clazz);
            PBuiltinFunction get = null;
            if (!interopLibrary.isNull(getter)) {
                RootCallTarget getterCT = getterCallTarget(name, language);
                get = factory.createGetSetBuiltinFunction(name, cls, EMPTY_OBJECT_ARRAY, ExternalFunctionNodes.createKwDefaults(getter, closure), getterCT);
            }

            PBuiltinFunction set = null;
            boolean hasSetter = !interopLibrary.isNull(setter);
            if (hasSetter) {
                RootCallTarget setterCT = setterCallTarget(name, language);
                set = factory.createGetSetBuiltinFunction(name, cls, EMPTY_OBJECT_ARRAY, ExternalFunctionNodes.createKwDefaults(setter, closure), setterCT);
            }

            // create get-set descriptor
            GetSetDescriptor descriptor = factory.createGetSetDescriptor(get, set, name, cls, hasSetter);
            Object memberDoc = fromCharPointerNode.execute(doc);
            dylib.put(descriptor.getStorage(), T___DOC__, memberDoc);
            return descriptor;
        }

        @TruffleBoundary
        private static RootCallTarget getterCallTarget(TruffleString name, PythonLanguage lang) {
            Function<PythonLanguage, RootNode> rootNodeFunction = l -> new GetterRoot(l, name, PExternalFunctionWrapper.GETTER);
            return lang.createCachedCallTarget(rootNodeFunction, GetterRoot.class, PExternalFunctionWrapper.GETTER, name, true);
        }

        @TruffleBoundary
        private static RootCallTarget setterCallTarget(TruffleString name, PythonLanguage lang) {
            Function<PythonLanguage, RootNode> rootNodeFunction = l -> new SetterRoot(l, name, PExternalFunctionWrapper.SETTER);
            return lang.createCachedCallTarget(rootNodeFunction, SetterRoot.class, PExternalFunctionWrapper.SETTER, name, true);
        }
    }

    // directly called without landing function
    @Builtin(name = "AddGetSet", minNumOfPositionalArgs = 7, parameterNames = {"cls", "tpDict", "name", "getter", "setter", "doc", "closure"})
    @ArgumentClinic(name = "name", conversion = ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class AddGetSetNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PythonCextBuiltinsClinicProviders.AddGetSetNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        int doGeneric(Object cls, Object tpDict, TruffleString name, Object getter, Object setter, Object doc, Object closure,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Cached CreateGetSetNode createGetSetNode,
                        @CachedLibrary(limit = "1") HashingStorageLibrary dictStorageLib,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                GetSetDescriptor descr = createGetSetNode.execute(name, cls, getter, setter, doc, closure, getLanguage(), factory());
                PDict dict = PythonCextBuiltins.castPDict(asPythonObjectNode.execute(tpDict));
                HashingStorage dictStorage = dict.getDictStorage();
                HashingStorage updatedStorage = dictStorageLib.setItem(dictStorage, name, descr);
                if (dictStorage != updatedStorage) {
                    dict.setDictStorage(updatedStorage);
                }
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }
    }

    @Builtin(name = "PyTruffle_tss_create")
    @GenerateNodeFactory
    abstract static class PyTruffleTssCreate extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        long tssCreate() {
            return getContext().getCApiContext().nextTssKey();
        }
    }

    @Builtin(name = "PyTruffle_tss_get", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffleTssGet extends PythonUnaryBuiltinNode {
        @Specialization
        Object tssGet(Object key,
                        @Cached CastToJavaLongLossyNode cast) {
            Object value = getContext().getCApiContext().tssGet(cast.execute(key));
            if (value == null) {
                return getContext().getNativeNull();
            }
            return value;
        }
    }

    @Builtin(name = "PyTruffle_tss_set", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTruffleTssSet extends PythonBinaryBuiltinNode {
        @Specialization
        Object tssSet(Object key, Object value,
                        @Cached CastToJavaLongLossyNode cast) {
            getContext().getCApiContext().tssSet(cast.execute(key), value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "PyTruffle_tss_delete", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffleTssDelete extends PythonUnaryBuiltinNode {
        @Specialization
        Object tssDelete(Object key,
                        @Cached CastToJavaLongLossyNode cast) {
            getContext().getCApiContext().tssDelete(cast.execute(key));
            return PNone.NONE;
        }
    }

    // directly called without landing function
    @Builtin(name = "PyTruffle_Debug", takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class PyTruffleDebugNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        static Object doIt(Object[] args,
                        @Cached DebugNode debugNode) {
            debugNode.execute(args);
            return PNone.NONE;
        }
    }

    // directly called without landing function
    @Builtin(name = "PyTruffle_ToNative", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyTruffleToNativeNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object doIt(Object object) {
            Env env = getContext().getEnv();
            LanguageInfo llvmInfo = env.getInternalLanguages().get(J_LLVM_LANGUAGE);
            Toolchain toolchain = env.lookup(llvmInfo, Toolchain.class);
            if ("native".equals(toolchain.getIdentifier())) {
                InteropLibrary.getUncached().toNative(object);
            }
            return PNone.NONE;
        }
    }
}
