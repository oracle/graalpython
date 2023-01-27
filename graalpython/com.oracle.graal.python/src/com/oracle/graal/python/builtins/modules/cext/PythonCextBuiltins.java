/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PY_GIL_STATE_STATE;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyCodeObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyFrameObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyThreadState;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyTypeObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.SIZE_T;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.UINTPTR_T;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.UNSIGNED_INT;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.VA_LIST_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Void;
import static com.oracle.graal.python.builtins.objects.cext.common.CExtContext.METH_CLASS;
import static com.oracle.graal.python.nodes.BuiltinNames.T_BUILTINS;
import static com.oracle.graal.python.nodes.BuiltinNames.T__WEAKREF;
import static com.oracle.graal.python.nodes.ErrorMessages.INDEX_OUT_OF_RANGE;
import static com.oracle.graal.python.nodes.ErrorMessages.NATIVE_S_SUBTYPES_NOT_IMPLEMENTED;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.StringLiterals.J_LLVM_LANGUAGE;
import static com.oracle.graal.python.nodes.StringLiterals.T_DOT;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_OBJECT_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.GraalPythonModuleBuiltins.DebugNode;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins.GetFileSystemEncodingNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.CreateFunctionNodeGen;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext.AllocInfo;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiGuards;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodes.ReadMemberNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodes.WriteMemberNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.CharPtrToJavaObjectNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ClearNativeWrapperNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.UnicodeFromFormatNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.FromCharPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.TransformExceptionToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.GetterRoot;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.SetterRoot;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeMember;
import com.oracle.graal.python.builtins.objects.cext.capi.PThreadState;
import com.oracle.graal.python.builtins.objects.cext.capi.PyCFunctionDecorator;
import com.oracle.graal.python.builtins.objects.cext.capi.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativePointer;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.HandleReleaser;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.HandleResolver;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.HandleResolverStealing;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.HandleTester;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.JavaStringToTruffleString;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeTransfer;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext.Store;
import com.oracle.graal.python.builtins.objects.cext.common.CExtParseArgumentsNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtParseArgumentsNode.SplitFormatStringNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToNativeNode;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
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
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.CreateTypeNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.lib.PyDictSetItem;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PNodeWithRaiseAndIndirectCall;
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
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.frame.GetCurrentFrameRef;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongLossyNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.GilNode;
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
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
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
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.utilities.CyclicAssumption;
import com.oracle.truffle.llvm.api.Toolchain;
import com.oracle.truffle.nfi.api.SignatureLibrary;

@CoreFunctions(defineModule = PythonCextBuiltins.PYTHON_CEXT)
@GenerateNodeFactory
public final class PythonCextBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return filterFactories(PythonCextBuiltinsFactory.getFactories(), PythonBuiltinBaseNode.class);
    }

    private static final TruffleLogger LOGGER = CApiContext.getLogger(PythonCextBuiltins.class);

    public static final String PYTHON_CEXT = "python_cext";
    public static final TruffleString T_PYTHON_CEXT = tsLiteral(PYTHON_CEXT);

    @GenerateUncached
    public abstract static class PromoteBorrowedValue extends Node {

        public abstract Object execute(Object value);

        @Specialization
        static PythonBuiltinObject doString(TruffleString str,
                        @Cached PythonObjectFactory factory) {
            return factory.createString(str);
        }

        @Specialization
        static PythonBuiltinObject doInteger(int i,
                        @Cached PythonObjectFactory factory) {
            return factory.createInt(i);
        }

        @Specialization
        static PythonBuiltinObject doLong(long i,
                        @Cached PythonObjectFactory factory) {
            return factory.createInt(i);
        }

        @Specialization(guards = "!isNaN(d)")
        static PythonBuiltinObject doDouble(double d,
                        @Cached PythonObjectFactory factory) {
            return factory.createFloat(d);
        }

        static boolean isNaN(double d) {
            return Double.isNaN(d);
        }

        @Fallback
        static PythonBuiltinObject doOther(@SuppressWarnings("unused") Object value) {
            return null;
        }
    }

    /*
     * Native pointer to the PyMethodDef struct for functions created in C. We need to keep it
     * because the C program may expect to get its pointer back when accessing m_ml member of
     * methods.
     */
    public static final HiddenKey METHOD_DEF_PTR = new HiddenKey("method_def_ptr");

    public static PException checkThrowableBeforeNative(Throwable t, String where1, Object where2) {
        if (t instanceof PException) {
            // this is ok, and will be handled correctly
            throw (PException) t;
        }
        // everything else: log and convert to PException (SystemError)
        CompilerDirectives.transferToInterpreter();
        PNodeWithContext.printStack();
        PrintStream out = new PrintStream(PythonContext.get(null).getEnv().err());
        out.println("while executing " + where1 + " " + where2);
        out.println("should not throw exceptions apart from PException");
        t.printStackTrace(out);
        out.flush();
        throw PRaiseNode.raiseUncached(null, SystemError, ErrorMessages.INTERNAL_EXCEPTION_OCCURED);
    }

    @CApiBuiltin(ret = PyThreadState, args = {}, acquiresGIL = false, call = Direct)
    @GenerateNodeFactory
    public abstract static class PyEval_SaveThread extends CApiNullaryBuiltinNode {
        private static final TruffleLogger LOGGER = CApiContext.getLogger(PyEval_SaveThread.class);

        @Specialization
        Object save(@Cached GilNode gil) {
            PythonContext context = PythonContext.get(gil);
            PThreadState threadState = PThreadState.getThreadState(PythonLanguage.get(gil), context);
            LOGGER.fine("C extension releases GIL");
            gil.release(context, true);
            return threadState;
        }
    }

    @CApiBuiltin(ret = Void, args = {PyThreadState}, acquiresGIL = false, call = Direct)
    @GenerateNodeFactory
    public abstract static class PyEval_RestoreThread extends CApiUnaryBuiltinNode {
        private static final TruffleLogger LOGGER = CApiContext.getLogger(PyEval_RestoreThread.class);

        @Specialization
        Object restore(@SuppressWarnings("unused") Object ptr,
                        @Cached GilNode gil) {
            PythonContext context = PythonContext.get(gil);
            PThreadState threadState = PThreadState.getThreadState(PythonLanguage.get(gil), context);
            LOGGER.fine("C extension acquires GIL");
            gil.acquire(context);
            return threadState;
        }
    }

    @CApiBuiltin(ret = PY_GIL_STATE_STATE, args = {}, acquiresGIL = false, call = Direct)
    @GenerateNodeFactory
    public abstract static class PyGILState_Ensure extends CApiNullaryBuiltinNode {

        @Specialization
        Object save(@Cached GilNode gil) {
            // TODO allow acquiring from foreign thread
            boolean acquired = gil.acquire();
            return acquired ? 1 : 0;
        }
    }

    @CApiBuiltin(ret = Void, args = {PY_GIL_STATE_STATE}, acquiresGIL = false, call = Direct)
    @GenerateNodeFactory
    public abstract static class PyGILState_Release extends CApiUnaryBuiltinNode {

        @Specialization
        Object restore(int state,
                        @Cached GilNode gil) {
            gil.release(state == 1);
            return 0;
        }
    }

    /**
     * Called from C when they actually want a {@code const char*} for a Python string
     */
    @CApiBuiltin(ret = Pointer, args = {PyObject}, call = Ignored)
    @GenerateNodeFactory
    abstract static class PyTruffleToCharPointer extends CApiUnaryBuiltinNode {

        @Specialization(guards = "isString(str)")
        static Object run(Object str,
                        @Cached AsCharPointerNode asCharPointerNode) {
            return asCharPointerNode.execute(str);
        }

        @Fallback
        Object run(Object o) {
            throw raise(PythonErrorType.SystemError, ErrorMessages.CANNOT_CONVERT_OBJ_TO_C_STRING, o, o.getClass().getName());
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, call = Ignored)
    @GenerateNodeFactory
    public abstract static class PyTruffle_FileSystemDefaultEncoding extends CApiNullaryBuiltinNode {
        @Specialization
        static TruffleString encoding() {
            return GetFileSystemEncodingNode.getFileSystemEncoding();
        }
    }

    public abstract static class CApiBuiltinNode extends PNodeWithRaiseAndIndirectCall {

        public abstract Object execute(Object[] args);

        protected final PythonNativePointer getNativeNull() {
            return getContext().getNativeNull();
        }

        /**
         * Returns the "NULL" pointer retrieved from the native backend, e.g., an LLVMPointer
         * instance. This is not wrapped, i.e., it cannot be passed through a PyObject
         * Python-To-Native transition (because it would be treated as a foreign Truffle object at
         * that point).
         */
        protected final Object getNULL() {
            return getContext().getNativeNull().getPtr();
        }

        @TruffleBoundary(allowInlining = true)
        protected static ByteBuffer wrap(byte[] data) {
            return ByteBuffer.wrap(data);
        }

        @TruffleBoundary(allowInlining = true)
        protected static ByteBuffer wrap(byte[] data, int offset, int length) {
            return ByteBuffer.wrap(data, offset, length);
        }

        @Child private PythonObjectFactory objectFactory;

        @CompilationFinal private ArgDescriptor ret;

        protected final PythonObjectFactory factory() {
            if (objectFactory == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (isAdoptable()) {
                    objectFactory = insert(PythonObjectFactory.create());
                } else {
                    objectFactory = factory();
                }
            }
            return objectFactory;
        }

        public final Python3Core getCore() {
            return getContext();
        }

        protected final PException badInternalCall(String argName) {
            CompilerDirectives.transferToInterpreter();
            throw raise(SystemError, ErrorMessages.S_S_BAD_ARG_TO_INTERNAL_FUNC, getName(), argName);
        }

        private String getName() {
            Class<?> c = getClass();
            while (c.getSimpleName().endsWith("NodeGen")) {
                c = c.getSuperclass();
            }
            String name = c.getSimpleName();
            return name;
        }

        @TruffleBoundary
        protected PException raiseFallback(Object obj, PythonBuiltinClassType type) {
            if (obj == PNone.NO_VALUE) {
                throw raise(SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC_S, getName());
            }
            if (IsSubtypeNode.getUncached().execute(GetClassNode.getUncached().execute(obj), type)) {
                throw raise(NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, type.getName());
            } else {
                throw raise(SystemError, ErrorMessages.EXPECTED_S_NOT_P, type.getName(), obj);
            }
        }

        @TruffleBoundary
        protected PException raiseFallback(Object obj, PythonBuiltinClassType type1, PythonBuiltinClassType type2) {
            if (obj == PNone.NO_VALUE) {
                throw raise(SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC_S, getName());
            }
            if (IsSubtypeNode.getUncached().execute(GetClassNode.getUncached().execute(obj), type1) || IsSubtypeNode.getUncached().execute(GetClassNode.getUncached().execute(obj), type2)) {
                throw raise(NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, type1.getName());
            } else {
                throw raise(SystemError, ErrorMessages.EXPECTED_S_NOT_P, type1.getName(), obj);
            }
        }

        protected final ArgDescriptor getRetDescriptor() {
            return ret;
        }

        protected final int castToInt(long elementSize) {
            if (elementSize == (int) elementSize) {
                return (int) elementSize;
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw raise(SystemError, INDEX_OUT_OF_RANGE);
        }

        public final Object getPosixSupport() {
            return getContext().getPosixSupport();
        }

        protected void checkNonNullArg(Object obj) {
            if (obj == PNone.NO_VALUE) {
                throw raise(SystemError, ErrorMessages.NULL_ARG_INTERNAL);
            }
        }

        protected void checkNonNullArg(Object obj1, Object obj2) {
            if (obj1 == PNone.NO_VALUE || obj2 == PNone.NO_VALUE) {
                throw raise(SystemError, ErrorMessages.NULL_ARG_INTERNAL);
            }
        }
    }

    public abstract static class CApiNullaryBuiltinNode extends CApiBuiltinNode {
        public abstract Object execute();

        @Override
        public final Object execute(Object[] args) {
            return execute();
        }
    }

    public abstract static class CApiUnaryBuiltinNode extends CApiBuiltinNode {
        public abstract Object execute(Object arg0);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0]);
        }
    }

    public abstract static class CApiBinaryBuiltinNode extends CApiBuiltinNode {
        public abstract Object execute(Object arg0, Object arg1);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0], args[1]);
        }
    }

    public abstract static class CApiTernaryBuiltinNode extends CApiBuiltinNode {
        public abstract Object execute(Object arg0, Object arg1, Object arg2);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0], args[1], args[2]);
        }
    }

    public abstract static class CApiQuaternaryBuiltinNode extends CApiBuiltinNode {
        public abstract Object execute(Object arg0, Object arg1, Object arg2, Object arg3);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0], args[1], args[2], args[3]);
        }
    }

    public abstract static class CApi5BuiltinNode extends CApiBuiltinNode {
        public abstract Object execute(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0], args[1], args[2], args[3], args[4]);
        }
    }

    public abstract static class CApi6BuiltinNode extends CApiBuiltinNode {
        public abstract Object execute(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0], args[1], args[2], args[3], args[4], args[5]);
        }
    }

    public abstract static class CApi7BuiltinNode extends CApiBuiltinNode {
        public abstract Object execute(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0], args[1], args[2], args[3], args[4], args[5], args[6]);
        }
    }

    public abstract static class CApi8BuiltinNode extends CApiBuiltinNode {
        public abstract Object execute(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7]);
        }
    }

    public abstract static class CApi9BuiltinNode extends CApiBuiltinNode {
        public abstract Object execute(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8]);
        }
    }

    public abstract static class CApi10BuiltinNode extends CApiBuiltinNode {
        public abstract Object execute(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9]);
        }
    }

    public abstract static class CApi11BuiltinNode extends CApiBuiltinNode {
        public abstract Object execute(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9], args[10]);
        }
    }

    public abstract static class CApi15BuiltinNode extends CApiBuiltinNode {
        public abstract Object execute(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11,
                        Object arg12, Object arg13, Object arg14);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9], args[10], args[11], args[12], args[13], args[14]);
        }
    }

    public abstract static class CApi16BuiltinNode extends CApiBuiltinNode {
        public abstract Object execute(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11,
                        Object arg12, Object arg13, Object arg14, Object arg15);

        @Override
        public final Object execute(Object[] args) {
            return execute(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9], args[10], args[11], args[12], args[13], args[14], args[15]);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class CApiBuiltinExecutable implements TruffleObject {

        final ArgDescriptor ret;
        final ArgDescriptor[] args;
        final NodeFactory<? extends CApiBuiltinNode> factory;
        @CompilationFinal private CallTarget callTarget;
        private final CApiBuiltin annotation;

        public CApiBuiltinExecutable(CApiBuiltin annotation, ArgDescriptor ret, ArgDescriptor[] args, NodeFactory<? extends CApiBuiltinNode> factory) {
            this.annotation = annotation;
            this.ret = ret;
            this.args = args;
            this.factory = factory;
        }

        CallTarget getCallTarget() {
            if (callTarget == null) {
                CompilerDirectives.transferToInterpreter();
                CompilerDirectives.shouldNotReachHere("call target slow path not implemented");
            }
            return callTarget;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        public boolean isExecutable() {
            return true;
        }

        public CApiBuiltin getAnnotation() {
            return annotation;
        }

        CExtToNativeNode createRetNode() {
            return ret.createPythonToNativeNode();
        }

        ArgDescriptor getRetDescriptor() {
            return ret;
        }

        CExtToJavaNode[] createArgNodes() {
            return ArgDescriptor.createNativeToPython(args);
        }

        public CApiBuiltinNode createBuiltinNode() {
            CApiBuiltinNode node = factory.createNode();
            node.ret = ret;
            return node;
        }

        public CApiBuiltinNode getUncachedNode() {
            // TODO: how to set "node.ret"?
            return factory.getUncachedInstance();
        }

        @ExportMessage
        static final class Execute {
            @Specialization(guards = "self == cachedSelf")
            public static Object doExecute(@SuppressWarnings("unused") CApiBuiltinExecutable self, Object[] arguments,
                            @Cached("self") CApiBuiltinExecutable cachedSelf,
                            @Cached(parameters = "cachedSelf") ExecuteCApiBuiltinNode call) {
                try {
                    return call.execute(cachedSelf, arguments);
                } catch (Throwable t) {
                    CompilerDirectives.transferToInterpreter();
                    t.printStackTrace();
                    throw CompilerDirectives.shouldNotReachHere(t);
                }
            }
        }

        @ExportMessage
        @TruffleBoundary
        boolean isPointer() {
            long pointer = PythonContext.get(null).getCApiContext().getClosurePointer(this);
            return pointer != -1;
        }

        @ExportMessage
        @TruffleBoundary
        long asPointer() throws UnsupportedMessageException {
            long pointer = PythonContext.get(null).getCApiContext().getClosurePointer(this);
            if (pointer == -1) {
                throw UnsupportedMessageException.create();
            }
            return pointer;
        }

        private static final class SignatureContainerRootNode extends RootNode {

            final HashMap<Class<?>, SignatureLibrary> libs = new HashMap<>();

            protected SignatureContainerRootNode() {
                super(null);
            }

            @Override
            public Object execute(VirtualFrame frame) {
                throw CompilerDirectives.shouldNotReachHere("not meant to be executed");
            }

            public SignatureLibrary getLibrary(Class<?> clazz) {
                return libs.computeIfAbsent(clazz, c -> {
                    SignatureLibrary lib = SignatureLibrary.getFactory().createDispatched(3);
                    SignatureContainerRootNode.this.insert(lib);
                    return lib;
                });
            }
        }

        @ExportMessage
        @TruffleBoundary
        void toNative() {
            CApiContext context = PythonContext.get(null).getCApiContext();
            long pointer = context.getClosurePointer(this);
            if (pointer == -1) {
                if (context.signatureContainer == null) {
                    context.signatureContainer = new SignatureContainerRootNode().getCallTarget();
                }
                SignatureContainerRootNode container = (SignatureContainerRootNode) context.signatureContainer.getRootNode();
                // create NFI closure and get its address
                StringBuilder signature = new StringBuilder("(");
                for (int i = 0; i < args.length; i++) {
                    signature.append(i == 0 ? "" : ",");
                    signature.append(args[i].getNFISignature());
                }
                signature.append("):").append(ret.getNFISignature());
                Object nfiSignature = PythonContext.get(null).getEnv().parseInternal(Source.newBuilder("nfi", signature.toString(), "exec").build()).call();
                Object closure = container.getLibrary(factory.getNodeClass()).createClosure(nfiSignature, this);
                InteropLibrary.getUncached().toNative(closure);
                try {
                    pointer = InteropLibrary.getUncached().asPointer(closure);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
                context.setClosurePointer(this, closure, pointer);
            }
        }

        @Override
        public String toString() {
            return "CApiBuiltin(" + factory.getNodeClass().getSimpleName() + ")";
        }
    }

    @GenerateUncached
    abstract static class ExecuteCApiBuiltinNode extends Node {
        abstract Object execute(CApiBuiltinExecutable self, Object[] arguments);

        public static ExecuteCApiBuiltinNode create(CApiBuiltinExecutable self) {
            try {
                return new CachedExecuteCApiBuiltinNode(self);
            } catch (Throwable t) {
                PNodeWithContext.printStack();
                System.out.println("while creating CApiBuiltin " + self.factory.getNodeClass());
                throw t;
            }
        }

        public static ExecuteCApiBuiltinNode getUncached(@SuppressWarnings("unused") CApiBuiltinExecutable self) {
            return UncachedExecuteCApiBuiltinNode.INSTANCE;
        }
    }

    static final class CachedExecuteCApiBuiltinNode extends ExecuteCApiBuiltinNode {
        private final CApiBuiltinExecutable cachedSelf;
        @Child private CExtToNativeNode retNode;
        @Children private final CExtToJavaNode[] argNodes;
        @Child private CApiBuiltinNode builtinNode;
        @Child private TransformExceptionToNativeNode transformExceptionToNativeNode;

        CachedExecuteCApiBuiltinNode(CApiBuiltinExecutable cachedSelf) {
            assert cachedSelf.ret.createCheckResultNode() == null : "primitive result check types are only intended for ExternalFunctionInvokeNode";
            this.cachedSelf = cachedSelf;
            this.retNode = cachedSelf.createRetNode();
            this.argNodes = cachedSelf.createArgNodes();
            this.builtinNode = cachedSelf.createBuiltinNode();
        }

        @Override
        Object execute(CApiBuiltinExecutable self, Object[] arguments) {
            try {
                try {
                    // System.out.println("executing CApiBuiltin " + self.factory.getNodeClass());
                    assert cachedSelf == self;
                    assert arguments.length == argNodes.length;

                    Object[] argCast = new Object[argNodes.length];
                    for (int i = 0; i < argNodes.length; i++) {
                        argCast[i] = argNodes[i] == null ? arguments[i] : argNodes[i].execute(arguments[i]);
                    }
                    Object result = builtinNode.execute(argCast);
                    if (retNode != null) {
                        result = retNode.execute(result);
                    }
                    assert !(result instanceof PythonNativePointer);
                    return result;
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "CApiBuiltin", self.factory);
                }
            } catch (PException e) {
                if (transformExceptionToNativeNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    transformExceptionToNativeNode = insert(TransformExceptionToNativeNodeGen.create());
                }
                transformExceptionToNativeNode.execute(e);
                if (cachedSelf.getRetDescriptor().isIntType()) {
                    return -1;
                } else if (cachedSelf.getRetDescriptor().isPyObjectOrPointer()) {
                    return PythonContext.get(this).getNativeNull().getPtr();
                } else if (cachedSelf.getRetDescriptor().isFloatType()) {
                    return -1.0;
                } else if (cachedSelf.getRetDescriptor().isVoid()) {
                    return PNone.NO_VALUE;
                } else {
                    CompilerDirectives.transferToInterpreter();
                    throw CompilerDirectives.shouldNotReachHere("return type while handling PException: " + cachedSelf.getRetDescriptor() + " in " + cachedSelf.factory.getNodeClass());
                }
            }
        }
    }

    static final class UncachedExecuteCApiBuiltinNode extends ExecuteCApiBuiltinNode {

        static final UncachedExecuteCApiBuiltinNode INSTANCE = new UncachedExecuteCApiBuiltinNode();

        @Override
        public boolean isAdoptable() {
            return false;
        }

        @Override
        Object execute(CApiBuiltinExecutable self, Object[] arguments) {
            return IndirectCallNode.getUncached().call(self.getCallTarget(), arguments);
        }
    }

    // This appears to be used only from HPy
    @Builtin(name = "PyTruffle_CreateType", minNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class PyTruffle_CreateType extends PythonQuaternaryBuiltinNode {
        @Specialization
        static PythonClass createType(VirtualFrame frame, TruffleString name, PTuple bases, PDict namespaceOrig, Object metaclass,
                        @Cached CreateTypeNode createType) {
            return createType.execute(frame, namespaceOrig, name, bases, metaclass, PKeyword.EMPTY_KEYWORDS);
        }
    }

    @Builtin(name = "PyTruffle_GetBuiltin", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffleGetBuiltin extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        Object doI(Object builtinNameObject,
                        @Cached CastToJavaStringNode castToStringNode) {
            String builtinName = castToStringNode.execute(builtinNameObject);
            CApiBuiltinExecutable builtin = capiBuiltins.get(builtinName);
            if (builtin != null) {
                return builtin;
            }
            throw raise(PythonErrorType.KeyError, ErrorMessages.APOSTROPHE_S, builtinName);
        }
    }

    /**
     * How the call is routed from native code to the Java CApiBuiltin implementation, i.e., whether
     * there needs to be some intermediate C code.
     */
    public enum CApiCallPath {
        /**
         * This builtin can be called without any intermediate code - a call stub will be generated
         * as appropriate.
         */
        Direct,
        /**
         * This builtin has an explicit C implementation that can be executed both from native and
         * from Sulong - no automatic stub will be generated.
         */
        CImpl,
        /**
         * This builtin has an explicit C implementation that needs to be executed in Sulong - an
         * automatic stub will be generated for native calls.
         */
        PolyglotImpl,
        /**
         * This builtin is not implemented - create an empty stub that raises an error.
         */
        NotImplemented,
        /**
         * This builtin is not part of the Python C API, no proxy is generated.
         */
        Ignored,
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface CApiBuiltins {
        CApiBuiltin[] value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Repeatable(value = CApiBuiltins.class)
    public @interface CApiBuiltin {

        String name() default "";

        ArgDescriptor ret();

        ArgDescriptor[] args() default {};

        boolean inlined() default false;

        boolean acquiresGIL() default true;

        CApiCallPath call();

        /**
         * Specifies a va_list function that this builtin can be forwarded to, e.g., "PyErr_FormatV"
         * for "PyErr_Format".
         */
        String forwardsTo() default "";

        /**
         * Comment to explain, e.g., why a builtin is ignored.
         */
        String comment() default "";
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface CApiSymbols {
        CApiSymbol[] value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Repeatable(value = CApiSymbols.class)
    public @interface CApiSymbol {

        String name() default "";

        ArgDescriptor type();
    }

    @CApiBuiltin(ret = PyTypeObject, args = {ConstCharPtrAsTruffleString}, call = Ignored)
    @GenerateNodeFactory
    abstract static class PyTruffle_Type extends CApiUnaryBuiltinNode {

        private static final TruffleString[] LOOKUP_MODULES = new TruffleString[]{
                        PythonCextBuiltins.T_PYTHON_CEXT,
                        T__WEAKREF,
                        T_BUILTINS
        };

        @Specialization
        Object doI(TruffleString typeName,
                        @Cached TruffleString.EqualNode eqNode) {
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

    @GenerateUncached
    abstract static class CreateFunctionNode extends PNodeWithContext {

        abstract Object execute(TruffleString name, Object callable, Object wrapper, Object type, Object flags, PythonObjectFactory factory);

        @Specialization(guards = {"!isNoValue(type)", "isNoValue(wrapper)"})
        static Object doPythonCallableWithoutWrapper(@SuppressWarnings("unused") TruffleString name, PythonNativeWrapper callable,
                        @SuppressWarnings("unused") PNone wrapper,
                        @SuppressWarnings("unused") Object type,
                        @SuppressWarnings("unused") Object flags,
                        @SuppressWarnings("unused") PythonObjectFactory factory) {
            // This can happen if a native type inherits slots from a managed type. Therefore,
            // something like 'base->tp_new' will be a wrapper of the managed '__new__'. So, in this
            // case, we assume that the object is already callable.
            return callable.getDelegate();
        }

        @Specialization(guards = "!isNoValue(type)")
        @TruffleBoundary
        Object doPythonCallable(TruffleString name, PythonNativeWrapper callable, int signature, Object type, int flags, PythonObjectFactory factory) {
            // This can happen if a native type inherits slots from a managed type. Therefore,
            // something like 'base->tp_new' will be a wrapper of the managed '__new__'. So, in this
            // case, we assume that the object is already callable.
            Object managedCallable = callable.getDelegate();
            PBuiltinFunction function = PExternalFunctionWrapper.createWrapperFunction(name, managedCallable, type, flags, signature, getLanguage(), factory, false);
            return function != null ? function : managedCallable;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isNoValue(type)", "isDecoratedManagedFunction(callable)", "isNoValue(wrapper)"})
        static Object doDecoratedManagedWithoutWrapper(@SuppressWarnings("unused") TruffleString name, PyCFunctionDecorator callable, PNone wrapper, Object type, Object flags,
                        @SuppressWarnings("unused") PythonObjectFactory factory) {
            // This can happen if a native type inherits slots from a managed type. Therefore,
            // something like 'base->tp_new' will be a wrapper of the managed '__new__'. So, in this
            // case, we assume that the object is already callable.
            // Note, that this will also drop the 'native-to-java' conversion which is usually done
            // by 'callable.getFun1()'.
            return ((PythonNativeWrapper) callable.getNativeFunction()).getDelegate();
        }

        @Specialization(guards = "isDecoratedManagedFunction(callable)")
        @TruffleBoundary
        Object doDecoratedManaged(TruffleString name, PyCFunctionDecorator callable, int signature, Object type, int flags, PythonObjectFactory factory) {
            // This can happen if a native type inherits slots from a managed type. Therefore,
            // something like 'base->tp_new' will be a wrapper of the managed '__new__'. So, in this
            // case, we assume that the object is already callable.
            // Note, that this will also drop the 'native-to-java' conversion which is usually done
            // by 'callable.getFun1()'.
            Object managedCallable = ((PythonNativeWrapper) callable.getNativeFunction()).getDelegate();
            PBuiltinFunction function = PExternalFunctionWrapper.createWrapperFunction(name, managedCallable, type, flags, signature, PythonLanguage.get(this), factory, false);
            if (function != null) {
                return function;
            }

            // Special case: if the returned 'wrappedCallTarget' is null, this indicates we want to
            // call a Python callable without wrapping and arguments conversion. So, directly use
            // the callable.
            return managedCallable;
        }

        @Specialization(guards = {"!isNoValue(type)", "!isNativeWrapper(callable)"})
        @TruffleBoundary
        PBuiltinFunction doNativeCallableWithType(TruffleString name, Object callable, int signature, Object type, int flags, PythonObjectFactory factory) {
            return PExternalFunctionWrapper.createWrapperFunction(name, callable, type, flags, signature, getLanguage(), factory, true);
        }

        @Specialization(guards = {"isNoValue(type)", "!isNativeWrapper(callable)"})
        @TruffleBoundary
        PBuiltinFunction doNativeCallableWithoutType(TruffleString name, Object callable, int signature, @SuppressWarnings("unused") PNone type, int flags, PythonObjectFactory factory) {
            return doNativeCallableWithType(name, callable, signature, null, flags, factory);
        }

        @Specialization(guards = {"!isNoValue(type)", "isNoValue(wrapper)", "!isNativeWrapper(callable)"})
        @TruffleBoundary
        PBuiltinFunction doNativeCallableWithoutWrapper(TruffleString name, Object callable, Object type,
                        @SuppressWarnings("unused") PNone wrapper,
                        @SuppressWarnings("unused") Object flags, PythonObjectFactory factory) {
            return PExternalFunctionWrapper.createWrapperFunction(name, callable, type, 0, PExternalFunctionWrapper.DIRECT, getLanguage(), factory, true);
        }

        @Specialization(guards = {"isNoValue(wrapper)", "isNoValue(type)", "!isNativeWrapper(callable)"})
        @TruffleBoundary
        PBuiltinFunction doNativeCallableWithoutWrapperAndType(TruffleString name, Object callable, PNone wrapper, @SuppressWarnings("unused") PNone type, Object flags, PythonObjectFactory factory) {
            return doNativeCallableWithoutWrapper(name, callable, null, wrapper, flags, factory);
        }

        static boolean isNativeWrapper(Object obj) {
            return CApiGuards.isNativeWrapper(obj) || isDecoratedManagedFunction(obj);
        }

        static boolean isDecoratedManagedFunction(Object obj) {
            return obj instanceof PyCFunctionDecorator && CApiGuards.isNativeWrapper(((PyCFunctionDecorator) obj).getNativeFunction());
        }
    }

    abstract static class PyObjectSetAttrNode extends PNodeWithContext {

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

    @CApiBuiltin(ret = Int, args = {PyTypeObject, Pointer, Pointer}, call = Ignored)
    @GenerateNodeFactory
    abstract static class PyTruffle_Set_Native_Slots extends CApiTernaryBuiltinNode {
        static final HiddenKey NATIVE_SLOTS = new HiddenKey("__native_slots__");

        @Specialization
        static int doPythonClass(PythonClass pythonClass, Object nativeGetSets, Object nativeMembers,
                        @Cached WriteAttributeToObjectNode writeAttrNode) {
            writeAttrNode.execute(pythonClass, NATIVE_SLOTS, new Object[]{nativeGetSets, nativeMembers});
            return 0;
        }
    }

    @CApiBuiltin(ret = Pointer, args = {PyTypeObject, ConstCharPtrAsTruffleString}, call = Ignored)
    @GenerateNodeFactory
    abstract static class PyTruffle_Get_Inherited_Native_Slots extends CApiBinaryBuiltinNode {
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
                return getNULL();
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
                Object value = ReadAttributeFromObjectNode.getUncachedForceType().execute(kls, PyTruffle_Set_Native_Slots.NATIVE_SLOTS);
                if (value != PNone.NO_VALUE) {
                    Object[] tuple = (Object[]) value;
                    assert tuple.length == 2;
                    l.add(new PythonNativePointer(tuple[idx]));
                }
            }
            return l.toArray();
        }
    }

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

    @CApiBuiltin(ret = Pointer, args = {PyObject}, call = Ignored)
    @GenerateNodeFactory
    abstract static class PyTruffle_Bytes_AsString extends CApiUnaryBuiltinNode {
        @Specialization
        static Object doBytes(PBytes bytes) {
            return new PySequenceArrayWrapper(bytes, 1);
        }

        @Specialization
        static Object doUnicode(PString str,
                        @Cached CastToTruffleStringNode castToStringNode) {
            return new CStringWrapper(castToStringNode.execute(str));
        }

        @Specialization
        static Object doNative(PythonAbstractNativeObject obj,
                        @Cached ToSulongNode toSulong,
                        @Cached PCallCapiFunction callMemberGetterNode) {
            return callMemberGetterNode.call(NativeMember.OB_SVAL.getGetterFunctionName(), toSulong.execute(obj));
        }

        @Fallback
        Object doUnicode(Object o) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.EXPECTED_S_P_FOUND, "bytes", o);
        }
    }

    @CApiBuiltin(ret = PyFrameObjectTransfer, args = {PyThreadState, PyCodeObject, PyObject, PyObject}, call = Direct)
    @GenerateNodeFactory
    abstract static class PyFrame_New extends CApiQuaternaryBuiltinNode {
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

    @CApiBuiltin(ret = Pointer, args = {PyTypeObject, Pointer}, call = Ignored)
    @GenerateNodeFactory
    abstract static class PyTruffle_Set_SulongType extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doPythonObject(PythonManagedClass klass, Object ptr) {
            klass.setSulongType(ptr);
            return ptr;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {Pointer, PyObject, Py_ssize_t, Int, Py_ssize_t, ConstCharPtrAsTruffleString, Int, Pointer, Pointer, Pointer, Pointer}, call = Ignored)
    @GenerateNodeFactory
    abstract static class PyTruffle_MemoryViewFromBuffer extends CApi11BuiltinNode {

        @Specialization
        Object wrap(Object bufferStructPointer, Object ownerObj, Object lenObj,
                        Object readonlyObj, Object itemsizeObj, TruffleString format,
                        Object ndimObj, Object bufPointer, Object shapePointer, Object stridesPointer, Object suboffsetsPointer,
                        @Cached ConditionProfile zeroDimProfile,
                        @Cached MemoryViewNodes.InitFlagsNode initFlagsNode,
                        @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached CastToJavaIntExactNode castToIntNode,
                        @Cached TruffleString.CodePointLengthNode lengthNode,
                        @Cached TruffleString.CodePointAtIndexNode atIndexNode) {
            try {
                int ndim = castToIntNode.execute(ndimObj);
                int itemsize = castToIntNode.execute(itemsizeObj);
                int len = castToIntNode.execute(lenObj);
                boolean readonly = castToIntNode.execute(readonlyObj) != 0;
                Object owner = ownerObj instanceof PythonNativePointer ? null : ownerObj;
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
                return factory().createMemoryView(getContext(), bufferLifecycleManager, buffer, owner, len, readonly, itemsize,
                                BufferFormat.forMemoryView(format, lengthNode, atIndexNode), format, ndim, bufPointer, 0, shape, strides, suboffsets, flags);
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {ArgDescriptor.Long}, call = Ignored)
    @GenerateNodeFactory
    abstract static class PyTruffle_Bytes_EmptyWithCapacity extends CApiUnaryBuiltinNode {

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

    @CApiBuiltin(ret = PyObjectTransfer, args = {Py_ssize_t}, call = Ignored)
    @GenerateNodeFactory
    abstract static class PyTruffle_ByteArray_EmptyWithCapacity extends CApiUnaryBuiltinNode {

        @Specialization
        PByteArray doInt(int size) {
            return factory().createByteArray(new byte[size]);
        }

        @Specialization(rewriteOn = OverflowException.class)
        PByteArray doLong(long size) throws OverflowException {
            return doInt(PInt.intValueExact(size));
        }

        @Specialization(replaces = "doLong")
        PByteArray doLongOvf(long size,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            try {
                return doInt(PInt.intValueExact(size));
            } catch (OverflowException e) {
                throw raiseNode.raiseNumberTooLarge(IndexError, size);
            }
        }

        @Specialization(rewriteOn = OverflowException.class)
        PByteArray doPInt(PInt size) throws OverflowException {
            return doInt(size.intValueExact());
        }

        @Specialization(replaces = "doPInt")
        PByteArray doPIntOvf(PInt size,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            try {
                return doInt(size.intValueExact());
            } catch (OverflowException e) {
                throw raiseNode.raiseNumberTooLarge(IndexError, size);
            }
        }
    }

    @CApiBuiltin(ret = Void, args = {Pointer}, call = Ignored)
    @GenerateNodeFactory
    abstract static class PyTruffle_Register_NULL extends CApiUnaryBuiltinNode {
        @Specialization
        Object doIt(Object object) {
            PythonNativePointer nn = getContext().getNativeNull();
            nn.setPtr(object);
            return PNone.NO_VALUE;
        }
    }

    @Builtin(name = "PyTruffle_HandleResolver_Create", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffleHandleResolverCreate extends PythonUnaryBuiltinNode {
        @Specialization
        static Object create(int which) {
            switch (which) {
                case 0:
                    return new HandleResolver();
                case 1:
                    return new HandleResolverStealing();
                case 2:
                    return new HandleTester();
                case 12: // pythonToNative
                    return new PythonToNativeTransfer();
                case 20: // javaStringToTruffleString
                    return new JavaStringToTruffleString();
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyTypeObject, ConstCharPtrAsTruffleString}, call = Ignored)
    @GenerateNodeFactory
    public abstract static class PyTruffle_Compute_Mro extends CApiBinaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        Object doIt(PythonNativeClass self, TruffleString className) {
            PythonAbstractClass[] doSlowPath = TypeNodes.ComputeMroNode.doSlowPath(self);
            return factory().createTuple(new MroSequenceStorage(className, doSlowPath));
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyTypeObject}, call = Ignored)
    @GenerateNodeFactory
    public abstract static class PyTruffle_NewTypeDict extends CApiUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static PDict doGeneric(PythonNativeClass nativeClass) {
            PythonLanguage language = PythonLanguage.get(null);
            Store nativeTypeStore = new Store(language.getEmptyShape());
            DynamicObjectLibrary.getUncached().put(nativeTypeStore, PythonNativeClass.INSTANCESHAPE, language.getShapeForClass(nativeClass));
            return PythonObjectFactory.getUncached().createDict(new DynamicObjectStorage(nativeTypeStore));
        }
    }

    @CApiBuiltin(ret = Int, args = {PyTypeObject, ConstCharPtrAsTruffleString, PyObject}, call = Ignored)
    @GenerateNodeFactory
    public abstract static class PyTruffle_Type_Modified extends CApiTernaryBuiltinNode {

        @TruffleBoundary
        @Specialization(guards = "isNoValue(mroTuple)")
        int doIt(PythonNativeClass clazz, TruffleString name, @SuppressWarnings("unused") PNone mroTuple) {
            CyclicAssumption nativeClassStableAssumption = getContext().getNativeClassStableAssumption(clazz, false);
            if (nativeClassStableAssumption != null) {
                nativeClassStableAssumption.invalidate("PyType_Modified(\"" + name.toJavaStringUncached() + "\") (without MRO) called");
            }
            SpecialMethodSlot.reinitializeSpecialMethodSlots(PythonNativeClass.cast(clazz), getLanguage());
            return 0;
        }

        @TruffleBoundary
        @Specialization
        int doIt(PythonNativeClass clazz, TruffleString name, PTuple mroTuple,
                        @Cached("createClassProfile()") ValueProfile profile) {
            CyclicAssumption nativeClassStableAssumption = getContext().getNativeClassStableAssumption(clazz, false);
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
            return 0;
        }
    }

    @CApiBuiltin(ret = Void, args = {ConstCharPtrAsTruffleString, ConstCharPtrAsTruffleString, Int}, call = Ignored)
    @GenerateNodeFactory
    public abstract static class PyTruffle_FatalErrorFunc extends CApiTernaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        Object doStrings(TruffleString func, TruffleString msg, int status) {
            CExtCommonNodes.fatalError(this, getContext(), func, msg, status);
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

    @CApiBuiltin(ret = PyObjectTransfer, args = {ConstCharPtrAsTruffleString, Int}, call = Ignored)
    @GenerateNodeFactory
    abstract static class PyTruffle_OS_StringToDouble extends CApiBinaryBuiltinNode {

        @Specialization
        Object doGeneric(TruffleString source, int reportPos,
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
            throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.COULD_NOT_CONVERT_STRING_TO_FLOAT, source);
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

    @CApiBuiltin(ret = PyObjectTransfer, args = {ArgDescriptor.Double, Int, Int, Int}, call = Ignored)
    @GenerateNodeFactory
    @ImportStatic(SpecialMethodNames.class)
    abstract static class PyTruffle_OS_DoubleToString extends CApiQuaternaryBuiltinNode {

        /* keep in sync with macro 'TRANSLATE_TYPE' in 'pystrtod.c' */
        private static final int Py_DTST_FINITE = 0;
        private static final int Py_DTST_INFINITE = 1;
        private static final int Py_DTST_NAN = 2;

        @Specialization(guards = "isReprFormatCode(formatCode)")
        PTuple doRepr(double val, @SuppressWarnings("unused") int formatCode, @SuppressWarnings("unused") int precision, @SuppressWarnings("unused") int flags,
                        @Cached("create(Repr)") LookupAndCallUnaryNode callReprNode,
                        @Cached CastToTruffleStringNode castToStringNode) {
            Object reprString = callReprNode.executeObject(null, val);
            try {
                return createResult(new CStringWrapper(castToStringNode.execute(reprString)), val);
            } catch (CannotCastException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException();
            }
        }

        @Specialization(guards = "!isReprFormatCode(formatCode)")
        Object doGeneric(double val, int formatCode, int precision, @SuppressWarnings("unused") int flags,
                        @Cached("create(Format)") LookupAndCallBinaryNode callReprNode,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached TruffleStringBuilder.AppendIntNumberNode appendIntNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.AppendCodePointNode appendCodepointNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            Object reprString = callReprNode.executeObject(null, val, joinFormatCode(formatCode, precision, appendIntNode, appendStringNode, appendCodepointNode, toStringNode));
            try {
                return createResult(new CStringWrapper(castToStringNode.execute(reprString)), val);
            } catch (CannotCastException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException();
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
    abstract static class CastArgsNode extends PNodeWithContext {

        public abstract Object[] execute(VirtualFrame frame, Object argsObj);

        @Specialization(guards = "isNoValue(args)")
        @SuppressWarnings("unused")
        static Object[] doNull(PNone args) {
            return EMPTY_OBJECT_ARRAY;
        }

        @Specialization(guards = "!isNoValue(args)")
        static Object[] doNotNull(VirtualFrame frame, Object args,
                        @Cached ExecutePositionalStarargsNode expandArgsNode) {
            return expandArgsNode.executeWith(frame, args);
        }
    }

    @ReportPolymorphism
    abstract static class CastKwargsNode extends PNodeWithContext {

        public abstract PKeyword[] execute(Object kwargsObj);

        @Specialization(guards = "isNoValue(kwargs)")
        @SuppressWarnings("unused")
        static PKeyword[] doNoKeywords(Object kwargs) {
            return PKeyword.EMPTY_KEYWORDS;
        }

        @Specialization(guards = "!isNoValue(kwargs)")
        static PKeyword[] doKeywords(Object kwargs,
                        @Cached ExpandKeywordStarargsNode expandKwargsNode) {
            return expandKwargsNode.execute(kwargs);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PyObject, ConstCharPtrAsTruffleString, Pointer, Pointer}, call = Ignored)
    @GenerateNodeFactory
    abstract static class PyTruffle_Arg_ParseTupleAndKeywords extends CApi5BuiltinNode {

        @Specialization
        static int doConvert(Object argv, Object nativeKwds, TruffleString formatString, Object nativeKwdnames, Object varargs,
                        @Cached SplitFormatStringNode splitFormatStringNode,
                        @CachedLibrary(limit = "2") InteropLibrary kwdnamesRefLib,
                        @Cached ConditionProfile kwdsProfile,
                        @Cached ConditionProfile kwdnamesProfile,
                        @Cached CExtParseArgumentsNode.ParseTupleAndKeywordsNode parseTupleAndKeywordsNode) {
            // force 'format' to be a String
            TruffleString[] split;
            try {
                split = splitFormatStringNode.execute(formatString);
                assert split.length == 2;
            } catch (CannotCastException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException();
            }

            TruffleString format = split[0];
            TruffleString functionName = split[1];

            // sort out if kwds is native NULL
            Object kwds;
            if (kwdsProfile.profile(PGuards.isNoValue(nativeKwds))) {
                kwds = null;
            } else {
                kwds = nativeKwds;
            }

            // sort out if kwdnames is native NULL
            Object kwdnames = kwdnamesProfile.profile(kwdnamesRefLib.isNull(nativeKwdnames)) ? null : nativeKwdnames;

            return parseTupleAndKeywordsNode.execute(functionName, argv, kwds, format, kwdnames, varargs);
        }
    }

    @CApiBuiltin(ret = Int, args = {Pointer, ArgDescriptor.Long}, call = Ignored)
    @GenerateNodeFactory
    public abstract static class PyTruffle_Object_Alloc extends CApiBinaryBuiltinNode {
        private static final TruffleLogger LOGGER = CApiContext.getLogger(PyTruffle_Object_Alloc.class);

        @Specialization
        Object alloc(Object allocatedObject, long objectSize,
                        @Cached GetCurrentFrameRef getCurrentFrameRef,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached(value = "getAllocationReporter(getContext())", allowUncached = true) AllocationReporter reporter) {
            // memory management
            PythonContext context = getContext();
            CApiContext cApiContext = context.getCApiContext();
            cApiContext.increaseMemoryPressure(objectSize, lib);

            boolean isLoggable = LOGGER.isLoggable(Level.FINER);
            boolean traceNativeMemory = context.getOption(PythonOptions.TraceNativeMemory);
            boolean reportAllocation = reporter.isActive();
            if (isLoggable || traceNativeMemory || reportAllocation) {
                if (isLoggable) {
                    LOGGER.finer(() -> PythonUtils.formatJString("Allocated memory at %s (size: %d bytes)", CApiContext.asHex(allocatedObject), objectSize));
                }
                if (traceNativeMemory) {
                    PFrame.Reference ref = null;
                    if (context.getOption(PythonOptions.TraceNativeMemoryCalls)) {
                        ref = getCurrentFrameRef.execute(null);
                        ref.markAsEscaped();
                    }
                    cApiContext.traceAlloc(CApiContext.asPointer(allocatedObject, lib), ref, null, objectSize);
                }
                if (reportAllocation) {
                    reporter.onEnter(null, 0, objectSize);
                    reporter.onReturnValue(allocatedObject, 0, objectSize);
                }
                return 0;
            }
            return -2;
        }

        static AllocationReporter getAllocationReporter(PythonContext context) {
            return context.getEnv().lookup(AllocationReporter.class);
        }

    }

    @CApiBuiltin(ret = Int, args = {Pointer}, call = Ignored)
    @GenerateNodeFactory
    @ImportStatic(CApiGuards.class)
    public abstract static class PyTruffle_Object_Free extends CApiUnaryBuiltinNode {
        private static final TruffleLogger LOGGER = CApiContext.getLogger(PyTruffle_Object_Free.class);

        @Specialization(guards = "!isCArrayWrapper(nativeWrapper)")
        static int doNativeWrapper(PythonNativeWrapper nativeWrapper,
                        @Cached ClearNativeWrapperNode clearNativeWrapperNode,
                        @Cached PCallCapiFunction callReleaseHandleNode) {
            // if (nativeWrapper.getRefCount() > 0) {
            // CompilerDirectives.transferToInterpreterAndInvalidate();
            // throw new IllegalStateException("deallocating native object with refcnt > 0");
            // }

            // clear native wrapper
            Object delegate = nativeWrapper.getDelegate();
            clearNativeWrapperNode.execute(delegate, nativeWrapper);

            doNativeWrapper(nativeWrapper, callReleaseHandleNode);
            return 1;
        }

        @Specialization
        static int arrayWrapper(@SuppressWarnings("unused") CArrayWrapper object) {
            // It's a pointer to a managed object but doesn't need special handling, so we just
            // ignore it.
            return 1;
        }

        @Specialization(guards = "!isNativeWrapper(object)")
        static int doOther(@SuppressWarnings("unused") Object object) {
            // It's a pointer to a managed object but none of our wrappers, so we just ignore
            // it.
            return 0;
        }

        protected static boolean isCArrayWrapper(Object obj) {
            return obj instanceof CArrayWrapper;
        }

        static void doNativeWrapper(PythonNativeWrapper nativeWrapper,
                        @Cached PCallCapiFunction callReleaseHandleNode) {

            // If wrapper already received toNative, release the handle or free the native
            // memory.
            if (nativeWrapper.isNative()) {
                // We do not call 'truffle_release_handle' directly because we still want to
                // support
                // native wrappers that have a real native pointer. 'PyTruffle_Free' does the
                // necessary distinction.
                long nativePointer = nativeWrapper.getNativePointer();
                if (LOGGER.isLoggable(Level.FINER)) {
                    LOGGER.finer(() -> PythonUtils.formatJString("Releasing handle: %x (object: %s)", nativePointer, nativeWrapper));
                }
                if (HandleTester.pointsToPyHandleSpace(nativePointer)) {
                    HandleReleaser.release(nativePointer);
                } else {
                    callReleaseHandleNode.call(NativeCAPISymbol.FUN_PY_TRUFFLE_FREE, nativePointer);
                }
            }
        }
    }

    @CApiBuiltin(ret = Int, args = {UNSIGNED_INT, UINTPTR_T, SIZE_T}, call = Direct)
    @GenerateNodeFactory
    @ImportStatic(CApiGuards.class)
    abstract static class PyTraceMalloc_Track extends CApiTernaryBuiltinNode {
        private static final TruffleLogger LOGGER = CApiContext.getLogger(PyTraceMalloc_Track.class);

        @Specialization(guards = {"isSingleContext()", "domain == cachedDomain"}, limit = "3")
        int doCachedDomainIdx(@SuppressWarnings("unused") int domain, Object pointerObject, long size,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @CachedLibrary("pointerObject") InteropLibrary lib,
                        @Cached("domain") @SuppressWarnings("unused") long cachedDomain,
                        @Cached("lookupDomain(domain)") int cachedDomainIdx) {

            // this will also be called if the allocation failed
            if (!lib.isNull(pointerObject)) {
                CApiContext cApiContext = getContext().getCApiContext();
                cApiContext.getTraceMallocDomain(cachedDomainIdx).track(pointerObject, size);
                cApiContext.increaseMemoryPressure(null, getThreadStateNode, this, size);
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine(() -> PythonUtils.formatJString("Tracking memory (size: %d): %s", size, CApiContext.asHex(pointerObject)));
                }
            }
            return 0;
        }

        @Specialization(replaces = "doCachedDomainIdx", limit = "3")
        int doGeneric(int domain, Object pointerObject, long size,
                        @CachedLibrary("pointerObject") InteropLibrary lib,
                        @Cached GetThreadStateNode getThreadStateNode) {
            return doCachedDomainIdx(domain, pointerObject, size, getThreadStateNode, lib, domain, lookupDomain(domain));
        }

        int lookupDomain(int domain) {
            return getContext().getCApiContext().findOrCreateTraceMallocDomain(domain);
        }
    }

    @CApiBuiltin(ret = Int, args = {UNSIGNED_INT, UINTPTR_T}, call = Direct)
    @GenerateNodeFactory
    @ImportStatic(CApiGuards.class)
    abstract static class PyTraceMalloc_Untrack extends CApiBinaryBuiltinNode {
        private static final TruffleLogger LOGGER = CApiContext.getLogger(PyTraceMalloc_Untrack.class);

        @Specialization(guards = {"isSingleContext()", "domain == cachedDomain"}, limit = "3")
        int doCachedDomainIdx(@SuppressWarnings("unused") int domain, Object pointerObject,
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
        int doGeneric(int domain, Object pointerObject) {
            return doCachedDomainIdx(domain, pointerObject, domain, lookupDomain(domain));
        }

        int lookupDomain(int domain) {
            return getContext().getCApiContext().findOrCreateTraceMallocDomain(domain);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject}, call = Direct)
    @GenerateNodeFactory
    abstract static class _PyTraceMalloc_NewReference extends CApiUnaryBuiltinNode {

        @Specialization
        @SuppressWarnings("unused")
        static int doCachedDomainIdx(Object pointerObject) {
            // TODO(fa): implement; capture tracebacks in PyTraceMalloc_Track and update them
            // here
            return 0;
        }
    }

    abstract static class PyTruffleGcTracingNode extends CApiUnaryBuiltinNode {

        @Specialization(guards = {"!traceCalls(getContext())", "traceMem(getContext())"})
        Object doNativeWrapper(Object ptr,
                        @Shared("lib") @CachedLibrary(limit = "3") InteropLibrary lib) {
            trace(getContext(), CApiContext.asPointer(ptr, lib), null, null);
            return PNone.NO_VALUE;
        }

        @Specialization(guards = {"traceCalls(getContext())", "traceMem(getContext())"})
        Object doNativeWrapperTraceCall(Object ptr,
                        @Cached GetCurrentFrameRef getCurrentFrameRef,
                        @Shared("lib") @CachedLibrary(limit = "3") InteropLibrary lib) {

            PFrame.Reference ref = getCurrentFrameRef.execute(null);
            trace(getContext(), CApiContext.asPointer(ptr, lib), ref, null);
            return PNone.NO_VALUE;
        }

        @Specialization(guards = "!traceMem(getContext())")
        static Object doNothing(@SuppressWarnings("unused") Object ptr) {
            // do nothing
            return PNone.NO_VALUE;
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

    @CApiBuiltin(ret = Void, args = {Pointer}, call = Direct)
    @GenerateNodeFactory
    abstract static class PyObject_GC_UnTrack extends PyTruffleGcTracingNode {
        private static final TruffleLogger LOGGER = CApiContext.getLogger(PyObject_GC_UnTrack.class);

        @Override
        protected void trace(PythonContext context, Object ptr, Reference ref, TruffleString className) {
            LOGGER.fine(() -> PythonUtils.formatJString("Untracking container object at %s", CApiContext.asHex(ptr)));
            context.getCApiContext().untrackObject(ptr, ref, className);
        }
    }

    @CApiBuiltin(ret = Void, args = {Pointer}, call = Direct)
    @GenerateNodeFactory
    abstract static class PyObject_GC_Track extends PyTruffleGcTracingNode {
        private static final TruffleLogger LOGGER = CApiContext.getLogger(PyObject_GC_Track.class);

        @Override
        protected void trace(PythonContext context, Object ptr, Reference ref, TruffleString className) {
            LOGGER.fine(() -> PythonUtils.formatJString("Tracking container object at %s", CApiContext.asHex(ptr)));
            context.getCApiContext().trackObject(ptr, ref, className);
        }
    }

    private static final int TRACE_MEM = 0x1;
    private static final int LOG_INFO = 0x2;
    private static final int LOG_CONFIG = 0x4;
    private static final int LOG_FINE = 0x8;
    private static final int LOG_FINER = 0x10;
    private static final int LOG_FINEST = 0x20;

    @CApiBuiltin(ret = Int, call = Ignored)
    @GenerateNodeFactory
    abstract static class PyTruffle_Native_Options extends CApiNullaryBuiltinNode {

        @Specialization
        int getNativeOptions() {
            int options = 0;
            if (getContext().getOption(PythonOptions.TraceNativeMemory)) {
                options |= TRACE_MEM;
            }
            if (LOGGER.isLoggable(Level.INFO)) {
                options |= LOG_INFO;
            }
            if (LOGGER.isLoggable(Level.CONFIG)) {
                options |= LOG_CONFIG;
            }
            if (LOGGER.isLoggable(Level.FINE)) {
                options |= LOG_FINE;
            }
            if (LOGGER.isLoggable(Level.FINER)) {
                options |= LOG_FINER;
            }
            if (LOGGER.isLoggable(Level.FINEST)) {
                options |= LOG_FINEST;
            }
            return options;
        }
    }

    @CApiBuiltin(ret = Void, args = {Int, ConstCharPtrAsTruffleString}, call = Ignored)
    @GenerateNodeFactory
    abstract static class PyTruffle_LogString extends CApiBinaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static Object log(int level, TruffleString message) {
            String msg = message.toJavaStringUncached();
            switch (level) {
                case LOG_INFO:
                    LOGGER.info(msg);
                    break;
                case LOG_CONFIG:
                    LOGGER.config(msg);
                    break;
                case LOG_FINE:
                    LOGGER.fine(msg);
                    break;
                case LOG_FINER:
                    LOGGER.finer(msg);
                    break;
                case LOG_FINEST:
                    LOGGER.finest(msg);
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere("unknown log level: " + level);
            }
            return PNone.NO_VALUE;
        }
    }

    /**
     * This will be called right before the call to stdlib's {@code free} function.
     */
    @CApiBuiltin(ret = Int, args = {Pointer, Py_ssize_t}, call = Ignored)
    @GenerateNodeFactory
    abstract static class _PyTruffle_Trace_Free extends CApiBinaryBuiltinNode {
        private static final TruffleLogger LOGGER = CApiContext.getLogger(_PyTruffle_Trace_Free.class);

        @Specialization(limit = "2")
        static int doNativeWrapperLong(Object ptr, long size,
                        @CachedLibrary("ptr") InteropLibrary lib,
                        @Cached GetCurrentFrameRef getCurrentFrameRef) {

            PythonContext context = PythonContext.get(lib);
            CApiContext cApiContext = context.getCApiContext();
            cApiContext.reduceMemoryPressure(size);

            boolean isLoggable = LOGGER.isLoggable(Level.FINER);
            boolean traceNativeMemory = context.getOption(PythonOptions.TraceNativeMemory);
            if (!lib.isNull(ptr)) {
                Object maybeLong = CApiContext.asPointer(ptr, lib);
                if (maybeLong instanceof Long) {
                    assert !context.nativeContext.nativeLookup.containsKey(maybeLong);
                }
                if (isLoggable || traceNativeMemory) {
                    boolean traceNativeMemoryCalls = context.getOption(PythonOptions.TraceNativeMemoryCalls);
                    if (traceNativeMemory) {
                        PFrame.Reference ref = null;
                        if (traceNativeMemoryCalls) {
                            ref = getCurrentFrameRef.execute(null);
                        }
                        AllocInfo allocLocation = cApiContext.traceFree(maybeLong, ref, null);
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

    @CApiBuiltin(ret = Int, args = {Pointer, Pointer}, call = Ignored)
    @GenerateNodeFactory
    abstract static class PyTruffle_Trace_Type extends CApiBinaryBuiltinNode {
        private static final TruffleLogger LOGGER = CApiContext.getLogger(PyTruffle_Trace_Type.class);

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

    @CApiBuiltin(ret = Void, args = {}, call = Direct)
    @GenerateNodeFactory
    abstract static class PyTruffle_DebugTrace extends CApiNullaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        Object trace() {
            PrintStream out = new PrintStream(getContext().getEnv().out());
            if (getContext().getOption(PythonOptions.EnableDebuggingBuiltins)) {
                out.println("\n\nJava Stacktrace:");
                new RuntimeException().printStackTrace(out);
                out.println("\n\nTruffle Stacktrace:");
                printStack();
                out.println("\n\nFrames:");
                Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Void>() {

                    public Void visitFrame(FrameInstance frame) {
                        out.println("  ===========================");
                        out.println("  call: " + frame.getCallNode());
                        out.println("  target: " + frame.getCallTarget());
                        Frame f = frame.getFrame(FrameInstance.FrameAccess.READ_ONLY);
                        out.println("  args: " + Arrays.asList(f.getArguments()));
                        return null;
                    }
                });
            } else {
                out.println("\n\nDEBUG TRACE (enable details via --python.EnableDebuggingBuiltins)");
            }
            return PNone.NO_VALUE;
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

    @CApiBuiltin(ret = Int, args = {Pointer, PyTypeObject, PyObject, ConstCharPtrAsTruffleString, Pointer, Int, Int, Pointer}, call = Ignored)
    @GenerateNodeFactory
    abstract static class PyTruffleType_AddFunctionToType extends CApi8BuiltinNode {

        @Specialization
        int classMethod(Object methodDefPtr, Object type, Object dict, TruffleString name, Object cfunc, int flags, int wrapper, Object doc,
                        @Cached NewClassMethodNode newClassMethodNode,
                        @Cached DictBuiltins.SetItemNode setItemNode) {
            Object func = newClassMethodNode.execute(methodDefPtr, name, cfunc, flags, wrapper, type, doc, factory());
            setItemNode.execute(null, dict, name, func);
            return 0;
        }
    }

    @CApiBuiltin(ret = Int, args = {Pointer, PyObject, ConstCharPtrAsTruffleString, Pointer, Int, Int, Pointer}, call = Ignored)
    @GenerateNodeFactory
    abstract static class PyTruffleModule_AddFunctionToModule extends CApi7BuiltinNode {

        @Specialization
        Object moduleFunction(Object methodDefPtr, PythonModule mod, TruffleString name, Object cfunc, int flags, int wrapper, Object doc,
                        @Cached ObjectBuiltins.SetattrNode setattrNode,
                        @CachedLibrary(limit = "1") DynamicObjectLibrary dylib,
                        @Cached CFunctionNewExMethodNode cFunctionNewExMethodNode) {
            Object modName = dylib.getOrDefault(mod.getStorage(), T___NAME__, null);
            assert modName != null : "module name is missing!";
            Object func = cFunctionNewExMethodNode.execute(methodDefPtr, name, cfunc, flags, wrapper, mod, modName, doc, factory());
            setattrNode.execute(null, mod, name, func);
            return 0;
        }
    }

    /**
     * Signature: {@code (primary, tpDict, name", cfunc, flags, wrapper, doc)}
     */
    @CApiBuiltin(ret = Int, args = {PyTypeObject, PyObject, ConstCharPtrAsTruffleString, Pointer, Int, Int, Pointer}, call = Ignored)
    @GenerateNodeFactory
    abstract static class PyTruffleType_AddSlot extends CApi7BuiltinNode {

        @Specialization
        @TruffleBoundary
        static int addSlot(Object clazz, PDict tpDict, TruffleString memberName, Object cfunc, int flags, int wrapper, Object docPtr) {
            // note: 'doc' may be NULL; in this case, we would store 'None'
            Object memberDoc = CharPtrToJavaObjectNode.run(docPtr, FromCharPointerNodeGen.getUncached(), InteropLibrary.getUncached());

            // create wrapper descriptor
            Object wrapperDescriptor = CreateFunctionNodeGen.getUncached().execute(memberName, cfunc, wrapper, clazz, flags, PythonObjectFactory.getUncached());
            WriteAttributeToDynamicObjectNode.getUncached().execute(wrapperDescriptor, SpecialAttributeNames.T___DOC__, memberDoc);

            // add wrapper descriptor to tp_dict
            PyDictSetItem.executeUncached(tpDict, memberName, wrapperDescriptor);
            return 0;
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

    @CApiBuiltin(ret = PyObjectTransfer, args = {ConstCharPtrAsTruffleString, VA_LIST_PTR}, call = CApiCallPath.Ignored)
    @GenerateNodeFactory
    abstract static class PyTruffle_Unicode_FromFormat extends CApiBinaryBuiltinNode {
        @Specialization
        Object doGeneric(TruffleString format, Object vaList,
                        @Cached UnicodeFromFormatNode unicodeFromFormatNode) {
            return unicodeFromFormatNode.execute(format, vaList);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject}, call = CApiCallPath.Ignored)
    @GenerateNodeFactory
    abstract static class PyTruffle_Bytes_CheckEmbeddedNull extends CApiUnaryBuiltinNode {

        @Specialization
        static int doBytes(PBytes bytes,
                        @Cached GetItemScalarNode getItemScalarNode) {

            SequenceStorage sequenceStorage = bytes.getSequenceStorage();
            int len = sequenceStorage.length();
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

    @CApiBuiltin(ret = Int, args = {PyTypeObject, PyObject, ConstCharPtrAsTruffleString, Int, Py_ssize_t, Int, Pointer}, call = CApiCallPath.Ignored)
    @GenerateNodeFactory
    public abstract static class PyTruffleType_AddMember extends CApi7BuiltinNode {

        @Specialization
        @TruffleBoundary
        public static int addMember(Object clazz, PDict tpDict, TruffleString memberName, int memberType, long offset, int canSet, Object docPtr) {
            // note: 'doc' may be NULL; in this case, we would store 'None'
            Object memberDoc = CharPtrToJavaObjectNode.run(docPtr, FromCharPointerNodeGen.getUncached(), InteropLibrary.getUncached());
            PythonLanguage language = PythonLanguage.get(null);
            PBuiltinFunction getterObject = ReadMemberNode.createBuiltinFunction(language, clazz, memberName, memberType, (int) offset);

            Object setterObject = null;
            if (canSet != 0) {
                setterObject = WriteMemberNode.createBuiltinFunction(language, clazz, memberName, memberType, (int) offset);
            }

            // create member descriptor
            GetSetDescriptor memberDescriptor = PythonObjectFactory.getUncached().createMemberDescriptor(getterObject, setterObject, memberName, clazz);
            WriteAttributeToDynamicObjectNode.getUncached().execute(memberDescriptor, SpecialAttributeNames.T___DOC__, memberDoc);

            // add member descriptor to tp_dict
            PyDictSetItem.executeUncached(tpDict, memberName, memberDescriptor);
            return 0;
        }
    }

    abstract static class CreateGetSetNode extends Node {

        abstract GetSetDescriptor execute(TruffleString name, Object cls, Object getter, Object setter, Object doc, Object closure,
                        PythonLanguage language,
                        PythonObjectFactory factory);

        @Specialization
        static GetSetDescriptor createGetSet(TruffleString name, Object cls, Object getter, Object setter, Object doc, Object closure,
                        PythonLanguage language,
                        PythonObjectFactory factory,
                        @CachedLibrary(limit = "1") DynamicObjectLibrary dylib,
                        @Cached CharPtrToJavaObjectNode fromCharPointerNode,
                        @CachedLibrary(limit = "2") InteropLibrary interopLibrary) {
            // note: 'doc' may be NULL; in this case, we would store 'None'
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

    @CApiBuiltin(ret = Int, args = {PyTypeObject, PyObject, ConstCharPtrAsTruffleString, Pointer, Pointer, Pointer, Pointer}, call = Ignored)
    @GenerateNodeFactory
    abstract static class PyTruffleType_AddGetSet extends CApi7BuiltinNode {

        @Specialization
        int doGeneric(Object cls, PDict dict, TruffleString name, Object getter, Object setter, Object doc, Object closure,
                        @Cached CreateGetSetNode createGetSetNode,
                        @Cached PyDictSetItem dictSetItem) {
            GetSetDescriptor descr = createGetSetNode.execute(name, cls, getter, setter, doc, closure, getLanguage(), factory());
            dictSetItem.execute(null, dict, name, descr);
            return 0;
        }
    }

    @CApiBuiltin(ret = ArgDescriptor.Long, args = {}, call = Ignored)
    @GenerateNodeFactory
    abstract static class PyTruffle_tss_create extends CApiNullaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        long tssCreate() {
            return getContext().getCApiContext().nextTssKey();
        }
    }

    @CApiBuiltin(ret = Pointer, args = {ArgDescriptor.Long}, call = Ignored)
    @GenerateNodeFactory
    abstract static class PyTruffle_tss_get extends CApiUnaryBuiltinNode {
        @Specialization
        Object tssGet(long key) {
            Object value = getContext().getCApiContext().tssGet(key);
            if (value == null) {
                return getNULL();
            }
            return value;
        }
    }

    @CApiBuiltin(ret = Int, args = {ArgDescriptor.Long, Pointer}, call = Ignored)
    @GenerateNodeFactory
    abstract static class PyTruffle_tss_set extends CApiBinaryBuiltinNode {
        @Specialization
        int tssSet(long key, Object value) {
            getContext().getCApiContext().tssSet(key, value);
            return 0;
        }
    }

    @CApiBuiltin(ret = Void, args = {ArgDescriptor.Long}, call = Ignored)
    @GenerateNodeFactory
    abstract static class PyTruffle_tss_delete extends CApiUnaryBuiltinNode {
        @Specialization
        Object tssDelete(long key) {
            getContext().getCApiContext().tssDelete(key);
            return PNone.NONE;
        }
    }

    @CApiBuiltin(ret = Int, args = {Pointer}, call = Direct)
    @GenerateNodeFactory
    public abstract static class PyTruffle_Debug extends CApiUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static Object doIt(Object[] args,
                        @Cached DebugNode debugNode) {
            debugNode.execute(args);
            return 0;
        }
    }

    @CApiBuiltin(ret = Int, args = {Pointer}, call = Direct)
    @GenerateNodeFactory
    public abstract static class PyTruffle_ToNative extends CApiUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        int doIt(Object object) {
            if (!PythonOptions.EnableDebuggingBuiltins.getValue(getContext().getEnv().getOptions())) {
                String message = "PyTruffle_ToNative is not enabled - enable with --python.EnableDebuggingBuiltins\n";
                try {
                    getContext().getEnv().out().write(message.getBytes());
                } catch (IOException e) {
                    System.out.print(message);
                }
                return 1;
            }
            Env env = getContext().getEnv();
            LanguageInfo llvmInfo = env.getInternalLanguages().get(J_LLVM_LANGUAGE);
            Toolchain toolchain = env.lookup(llvmInfo, Toolchain.class);
            if ("native".equals(toolchain.getIdentifier())) {
                InteropLibrary.getUncached().toNative(object);
            }
            return 0;
        }
    }

    public static final HashMap<String, CApiBuiltinExecutable> capiBuiltins = new HashMap<>();

    static {
        // List<NodeFactory<? extends PNodeWithRaise>>
        addCApiBuiltins(PythonCextAbstractBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextBoolBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextBytesBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextCapsuleBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextCEvalBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextClassBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextCodeBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextComplexBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextContextBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextDescrBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextDictBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextErrBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextFileBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextFloatBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextFuncBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextGenericAliasBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextHashBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextImportBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextIterBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextListBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextLongBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextMemoryViewBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextMethodBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextModuleBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextNamespaceBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextObjectBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextPosixmoduleBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextPyLifecycleBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextPyStateBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextPythonRunBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextSetBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextSliceBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextSlotBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextStructSeqBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextSysBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextTracebackBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextTupleBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextTypeBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextUnicodeBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextWarnBuiltinsFactory.getFactories());
        addCApiBuiltins(PythonCextWeakrefBuiltinsFactory.getFactories());
    }

    private static void addCApiBuiltins(List<?> list) {
        List<? extends NodeFactory<? extends CApiBuiltinNode>> factories = filterFactories(list, CApiBuiltinNode.class);

        for (var factory : factories) {
            CApiBuiltins builtins = factory.getNodeClass().getAnnotation(CApiBuiltins.class);
            CApiBuiltin[] annotations;
            if (builtins == null) {
                annotations = new CApiBuiltin[]{factory.getNodeClass().getAnnotation(CApiBuiltin.class)};
            } else {
                annotations = builtins.value();
            }
            try {
                for (var annotation : annotations) {
                    CApiBuiltinExecutable result = new CApiBuiltinExecutable(annotation, annotation.ret(), annotation.args(), factory);
                    String name = factory.getNodeClass().getSimpleName();
                    if (!annotation.name().isEmpty()) {
                        name = annotation.name();
                    }
                    assert !capiBuiltins.containsKey(name);
                    capiBuiltins.put(name, result);
                }
            } catch (Throwable t) {
                System.out.println("when processing " + factory.getNodeClass());
                throw t;
            }
        }
    }

    public static void main(String[] args) throws IOException {
        TreeSet<String> duplicates = new TreeSet<>();
        TreeSet<String> set = new TreeSet<>();
        for (var entry : new TreeMap<>(capiBuiltins).entrySet()) {
            String name = entry.getKey();
            CApiBuiltinExecutable value = entry.getValue();
            String line = "    BUILTIN(" + name + ", " + value.ret.cSignature;
            for (var arg : value.args) {
                line += ", " + arg.cSignature;
            }
            line += ") \\";
            set.add(line);

            CApiFunction apiFunction = CApiFunction.valueOf(name);
            if (apiFunction != null) {
                String mismatch = "";
                if (apiFunction.returnType != value.ret) {
                    mismatch += ", returns " + apiFunction.returnType + " vs. " + value.ret;
                }
                if (apiFunction.arguments.length != value.args.length) {
                    mismatch += ", mismatching arg length";
                } else {
                    for (int i = 0; i < value.args.length; i++) {
                        if (apiFunction.arguments[i] != value.args[i]) {
                            mismatch += ", arg " + i + " " + apiFunction.arguments[i] + " vs. " + value.args[i];
                        }
                    }
                }
                if (mismatch.isEmpty()) {
                    duplicates.add(name);
                } else {
                    duplicates.add(name + mismatch);
                }
            }
        }

        if (!duplicates.isEmpty()) {
            System.out.println("Duplicate CApiBuiltin specifications:");
            duplicates.forEach(System.out::println);
            System.out.println();
        }

        ArrayList<String> defines = new ArrayList<>();

        for (var entry : new TreeMap<>(capiBuiltins).entrySet()) {
            String name = entry.getKey();
            CApiBuiltinExecutable value = entry.getValue();
            if (!name.endsWith("_dummy")) {
                if (name.startsWith("Py_get_")) {
                    assert value.args.length == 1;
                    String type = value.args[0].name().replace("Wrapper", "");
                    StringBuilder macro = new StringBuilder();
                    assert name.charAt(7 + type.length()) == '_';
                    String field = name.substring(7 + type.length() + 1); // after "_"
                    macro.append("#define " + name.substring(7) + "(OBJ) ( points_to_py_handle_space(OBJ) ? Graal" + name + "((" + type + "*) (OBJ)) : ((" + type + "*) (OBJ))->" + field + " )");
                    defines.add(macro.toString());
                } else if (name.startsWith("Py_set_")) {
                    assert value.args.length == 2;
                    String type = value.args[0].name().replace("Wrapper", "");
                    StringBuilder macro = new StringBuilder();
                    assert name.charAt(7 + type.length()) == '_';
                    String field = name.substring(7 + type.length() + 1); // after "_"
                    macro.append("#define set_" + name.substring(7) + "(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) Graal" + name + "((" + type + "*) (OBJ), (VALUE)); else  ((" + type +
                                    "*) (OBJ))->" + field + " = (VALUE); }");
                    defines.add(macro.toString());
                }
            }
        }

        List<String> result = new ArrayList<>();
        result.add("#define CAPI_BUILTINS \\");
        result.addAll(set);
        result.add("");
        result.addAll(defines);

        CApiFunction.writeGenerated(Path.of("com.oracle.graal.python.cext", "src", "capi.h"), result);

        ArrayList<String> stubs = new ArrayList<>();
        for (var entry : new TreeMap<>(capiBuiltins).entrySet()) {
            String name = entry.getKey();
            CApiBuiltinExecutable value = entry.getValue();
            if (value.annotation.call() == Direct) {
                stubs.add("#undef " + name);
                String line = "PyAPI_FUNC(" + value.ret.cSignature + ") " + name + "(";
                for (int i = 0; i < value.args.length; i++) {
                    line += (i == 0 ? "" : ", ") + CApiFunction.getArgSignatureWithName(value.args[i], i);
                }
                line += ") {";
                stubs.add(line);
                line = "    " + (value.ret == ArgDescriptor.Void ? "" : "return ") + "Graal" + name + "(";
                for (int i = 0; i < value.args.length; i++) {
                    line += (i == 0 ? "" : ", ");
                    if (value.args[i] == ConstCharPtrAsTruffleString) {
                        line += "truffleString(" + CApiFunction.argName(i) + ")";
                    } else {
                        line += CApiFunction.argName(i);
                    }
                }
                line += ");";
                stubs.add(line);
                stubs.add("}");
            }
        }

        CApiFunction.writeGenerated(Path.of("com.oracle.graal.python.cext", "src", "capi.c"), stubs);

        CApiFunction.generateCForwards();
    }

    @SuppressWarnings("unchecked")
    public static <T> List<? extends NodeFactory<? extends T>> filterFactories(List<?> factories, Class<T> clazz) {
        List<NodeFactory<? extends T>> result = new ArrayList<>();
        for (Object entry : factories) {
            NodeFactory<?> factory = (NodeFactory<?>) entry;
            if (clazz.isAssignableFrom(factory.getNodeClass())) {
                result.add((NodeFactory<? extends T>) factory);
            }
        }
        return result;
    }
}
