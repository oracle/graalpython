/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;

import java.io.PrintWriter;
import java.math.BigInteger;
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
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.IntNode;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.AllocFuncRootNode;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.GetAttrFuncRootNode;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.MethDirectRoot;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.MethFastcallRoot;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.MethFastcallWithKeywordsRoot;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.MethKeywordsRoot;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.MethNoargsRoot;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.MethORoot;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.MethPowRootNode;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.MethRPowRootNode;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.MethReverseRootNode;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.MethRichcmpOpRootNode;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.MethVarargsRoot;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.RichCmpFuncRootNode;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.SSizeObjArgProcRootNode;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.SetAttrFuncRootNode;
import com.oracle.graal.python.builtins.modules.PythonCextBuiltinsFactory.DefaultCheckFunctionResultNodeGen;
import com.oracle.graal.python.builtins.modules.PythonCextBuiltinsFactory.GetByteArrayNodeGen;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.CApiGuards;
import com.oracle.graal.python.builtins.objects.cext.CArrayWrappers.CByteArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.AddRefCntNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.AllToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.AsCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.AsNativeDoubleNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.AsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.AsPythonObjectStealingNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.BinaryFirstToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.CastToJavaDoubleNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.CastToNativeLongNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.CextUpcallNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ConvertArgsToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.DirectUpcallNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.FastCallArgsToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.FastCallWithKeywordsArgsToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.GetNativeNullNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.MayRaiseBinaryNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.MayRaiseNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.MayRaiseNodeFactory;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.MayRaiseTernaryNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.MayRaiseUnaryNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ObjectUpcallNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.PRaiseNativeNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ResolveHandleNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.TernaryFirstSecondToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.TernaryFirstThirdToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.VoidPtrToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.DynamicObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.DynamicObjectNativeWrapper.PrimitiveNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.HandleCache;
import com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols;
import com.oracle.graal.python.builtins.objects.cext.PThreadState;
import com.oracle.graal.python.builtins.objects.cext.PyCFunctionDecorator;
import com.oracle.graal.python.builtins.objects.cext.PyDateTimeCAPIWrapper;
import com.oracle.graal.python.builtins.objects.cext.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonClassNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeNull;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeWrapperLibrary;
import com.oracle.graal.python.builtins.objects.cext.UnicodeObjectNodes.UnicodeAsWideCharNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.CastToNativeLongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.MayRaiseBinaryNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.MayRaiseTernaryNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.MayRaiseUnaryNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.PRaiseNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.ToJavaNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.TransformExceptionToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext.AllocInfo;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeReferenceCache;
import com.oracle.graal.python.builtins.objects.cext.capi.PyTruffleObjectAlloc;
import com.oracle.graal.python.builtins.objects.cext.capi.PyTruffleObjectFree;
import com.oracle.graal.python.builtins.objects.cext.common.CExtAsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.Charsets;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.ConvertPIntToPrimitiveNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.EncodeNativeStringNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.PCallCExtFunction;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.UnicodeFromWcharNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.common.CExtParseArgumentsNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtParseArgumentsNode.SplitFormatStringNode;
import com.oracle.graal.python.builtins.objects.cext.common.VaListWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.ConvertPIntToPrimitiveNodeGen;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.frame.PFrame.Reference;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.set.PBaseSet;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.traceback.GetTracebackNode;
import com.oracle.graal.python.builtins.objects.traceback.LazyTraceback;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetTypeFlagsNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.WriteUnraisableNode;
import com.oracle.graal.python.nodes.argument.keywords.ExecuteKeywordStarargsNode.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNode;
import com.oracle.graal.python.nodes.attributes.HasInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.frame.GetCurrentFrameRef;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToByteNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongLossyNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
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
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.HiddenKey;
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
    abstract static class TruffleString_AsString extends NativeBuiltin {

        @Specialization(guards = "isString(str)")
        Object run(Object str,
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
        Object run(Object obj,
                        @Cached CExtNodes.ToSulongNode toSulongNode) {
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
        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        PTuple doGeneric(VirtualFrame frame, Object size,
                        @Cached("createBinaryProfile()") ConditionProfile gotFrame,
                        @CachedLibrary("size") PythonObjectLibrary lib) {
            int index;
            if (gotFrame.profile(frame != null)) {
                index = lib.asSizeWithState(size, PArguments.getThreadState(frame));
            } else {
                index = lib.asSize(size);
            }
            return factory().createTuple(new Object[index]);
        }
    }

    @Builtin(name = "PyTuple_SetItem", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @ImportStatic(CApiGuards.class)
    abstract static class PyTuple_SetItem extends PythonTernaryBuiltinNode {
        @Specialization
        static int doManaged(VirtualFrame frame, PythonNativeWrapper selfWrapper, Object position, Object elementWrapper,
                        @Cached AsPythonObjectNode selfAsPythonObjectNode,
                        @Cached AsPythonObjectStealingNode elementAsPythonObjectNode,
                        @Cached("createSetItem()") SequenceStorageNodes.SetItemNode setItemNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object self = selfAsPythonObjectNode.execute(selfWrapper);
                if (!PGuards.isPTuple(self) || selfWrapper.getRefCount() != 1) {
                    throw raiseNode.raise(SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC_P, "PTuple_SetItem");
                }
                PTuple tuple = (PTuple) self;
                Object element = elementAsPythonObjectNode.execute(elementWrapper);
                setItemNode.execute(frame, tuple.getSequenceStorage(), position, element);
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
        }

        @Specialization(guards = "!isNativeWrapper(tuple)")
        static int doNative(Object tuple, long position, Object element,
                        @Cached PCallCapiFunction callSetItem) {
            // TODO(fa): This path should be avoided since this is called from native code to do a
            // native operation.
            callSetItem.call(NativeCAPISymbols.FUN_PY_TRUFFLE_TUPLE_SET_ITEM, tuple, position, element);
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

        @Specialization(guards = {"lib.isLazyPythonClass(type)", "isNoValue(wrapper)"}, limit = "3")
        static Object doPythonCallableWithoutWrapper(@SuppressWarnings("unused") String name, PythonNativeWrapper callable, @SuppressWarnings("unused") PNone wrapper,
                        @SuppressWarnings("unused") Object type,
                        @CachedLibrary("callable") PythonNativeWrapperLibrary nativeWrapperLibrary,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
            // This can happen if a native type inherits slots from a managed type. Therefore,
            // something like 'base->tp_new' will be a wrapper of the managed '__new__'. So, in this
            // case, we assume that the object is already callable.
            return nativeWrapperLibrary.getDelegate(callable);
        }

        @Specialization(guards = "lib.isLazyPythonClass(type)", limit = "3")
        Object doPythonCallable(String name, PythonNativeWrapper callable, PExternalFunctionWrapper wrapper, Object type,
                        @Shared("lang") @CachedLanguage PythonLanguage lang,
                        @CachedLibrary("callable") PythonNativeWrapperLibrary nativeWrapperLibrary,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
            // This can happen if a native type inherits slots from a managed type. Therefore,
            // something like 'base->tp_new' will be a wrapper of the managed '__new__'. So, in this
            // case, we assume that the object is already callable.

            Object managedCallable = nativeWrapperLibrary.getDelegate(callable);
            RootCallTarget wrappedCallTarget = wrapper.createCallTarget(lang, name, managedCallable, false);
            if (wrappedCallTarget != null) {
                return factory().createBuiltinFunction(name, type, 0, wrappedCallTarget);
            }
            return managedCallable;
        }

        @Specialization(guards = {"lib.isLazyPythonClass(type)", "isDecoratedManagedFunction(callable)", "isNoValue(wrapper)"})
        static Object doDecoratedManagedWithoutWrapper(@SuppressWarnings("unused") String name, PyCFunctionDecorator callable, @SuppressWarnings("unused") PNone wrapper,
                        @SuppressWarnings("unused") Object type,
                        @CachedLibrary(limit = "3") PythonNativeWrapperLibrary nativeWrapperLibrary,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
            // This can happen if a native type inherits slots from a managed type. Therefore,
            // something like 'base->tp_new' will be a wrapper of the managed '__new__'. So, in this
            // case, we assume that the object is already callable.
            // Note, that this will also drop the 'native-to-java' conversion which is usually done
            // by 'callable.getFun1()'.
            return nativeWrapperLibrary.getDelegate(callable.getNativeFunction());
        }

        @Specialization(guards = "isDecoratedManagedFunction(callable)")
        Object doDecoratedManaged(String name, PyCFunctionDecorator callable, PExternalFunctionWrapper wrapper, Object type,
                        @Shared("lang") @CachedLanguage PythonLanguage lang,
                        @CachedLibrary(limit = "3") PythonNativeWrapperLibrary nativeWrapperLibrary) {
            // This can happen if a native type inherits slots from a managed type. Therefore,
            // something like 'base->tp_new' will be a wrapper of the managed '__new__'. So, in this
            // case, we assume that the object is already callable.
            // Note, that this will also drop the 'native-to-java' conversion which is usually done
            // by 'callable.getFun1()'.
            Object managedCallable = nativeWrapperLibrary.getDelegate(callable.getNativeFunction());
            RootCallTarget wrappedCallTarget = wrapper.createCallTarget(lang, name, managedCallable, false);
            if (wrappedCallTarget != null) {
                return factory().createBuiltinFunction(name, type, 0, wrappedCallTarget);
            }

            // Special case: if the returned 'wrappedCallTarget' is null, this indicates we want to
            // call a Python callable without wrapping and arguments conversion. So, directly use
            // the callable.
            return managedCallable;
        }

        @Specialization(guards = {"lib.isLazyPythonClass(type)", "!isNativeWrapper(callable)"})
        PBuiltinFunction doNativeCallableWithType(String name, Object callable, PExternalFunctionWrapper wrapper, Object type,
                        @Shared("lang") @CachedLanguage PythonLanguage lang,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
            RootCallTarget wrappedCallTarget = wrapper.createCallTarget(lang, name, callable, true);
            return factory().createBuiltinFunction(name, type, 0, wrappedCallTarget);
        }

        @Specialization(guards = {"isNoValue(type)", "!isNativeWrapper(callable)"})
        PBuiltinFunction doNativeCallableWithoutType(String name, Object callable, PExternalFunctionWrapper wrapper, @SuppressWarnings("unused") PNone type,
                        @Shared("lang") @CachedLanguage PythonLanguage lang) {
            return doNativeCallableWithType(name, callable, wrapper, null, lang, null);
        }

        @Specialization(guards = {"lib.isLazyPythonClass(type)", "isNoValue(wrapper)", "!isNativeWrapper(callable)"})
        PBuiltinFunction doNativeCallableWithoutWrapper(String name, Object callable, Object type, @SuppressWarnings("unused") PNone wrapper,
                        @Shared("lang") @CachedLanguage PythonLanguage lang,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
            RootCallTarget callTarget = PExternalFunctionWrapper.createCallTarget(MethDirectRoot.create(lang, name, callable));
            return factory().createBuiltinFunction(name, type, 0, callTarget);
        }

        @Specialization(guards = {"isNoValue(wrapper)", "isNoValue(type)", "!isNativeWrapper(callable)"})
        PBuiltinFunction doNativeCallableWithoutWrapperAndType(String name, Object callable, PNone wrapper, @SuppressWarnings("unused") PNone type,
                        @Shared("lang") @CachedLanguage PythonLanguage lang) {
            return doNativeCallableWithoutWrapper(name, callable, null, wrapper, lang, null);
        }

        static boolean isNativeWrapper(Object obj) {
            return CApiGuards.isNativeWrapper(obj) || isDecoratedManagedFunction(obj);
        }

        static boolean isDecoratedManagedFunction(Object obj) {
            return obj instanceof PyCFunctionDecorator && CApiGuards.isNativeWrapper(((PyCFunctionDecorator) obj).getNativeFunction());
        }
    }

    @Builtin(name = "PyErr_Restore", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyErrRestoreNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object run(PNone typ, PNone val, PNone tb) {
            getContext().setCurrentException(null);
            return PNone.NONE;
        }

        @Specialization
        Object run(@SuppressWarnings("unused") Object typ, PBaseException val, @SuppressWarnings("unused") PNone tb,
                        @Shared("language") @CachedLanguage PythonLanguage language) {
            PythonContext context = getContext();
            context.setCurrentException(PException.fromExceptionInfo(val, (LazyTraceback) null, PythonOptions.isPExceptionWithJavaStacktrace(language)));
            return PNone.NONE;
        }

        @Specialization
        Object run(@SuppressWarnings("unused") Object typ, PBaseException val, PTraceback tb,
                        @Shared("language") @CachedLanguage PythonLanguage language) {
            PythonContext context = getContext();
            context.setCurrentException(PException.fromExceptionInfo(val, tb, PythonOptions.isPExceptionWithJavaStacktrace(language)));
            return PNone.NONE;
        }
    }

    @Builtin(name = "PyErr_Fetch", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class PyErrFetchNode extends NativeBuiltin {
        @Specialization
        public Object run(Object module,
                        @Exclusive @Cached GetClassNode getClassNode,
                        @Exclusive @Cached GetTracebackNode getTracebackNode,
                        @Exclusive @Cached GetNativeNullNode getNativeNullNode) {
            PythonContext context = getContext();
            PException currentException = context.getCurrentException();
            Object result;
            if (currentException == null) {
                result = getNativeNullNode.execute(module);
            } else {
                PBaseException exception = currentException.getEscapedException();
                Object traceback = null;
                if (currentException.getTraceback() != null) {
                    traceback = getTracebackNode.execute(currentException.getTraceback());
                }
                if (traceback == null) {
                    traceback = getNativeNullNode.execute(module);
                }
                result = factory().createTuple(new Object[]{getClassNode.execute(exception), exception, traceback});
                context.setCurrentException(null);
            }
            return result;
        }
    }

    @Builtin(name = "PyErr_Occurred", maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyErrOccurred extends PythonUnaryBuiltinNode {
        @Specialization
        Object run(Object errorMarker,
                        @Cached GetClassNode getClassNode) {
            PException currentException = getContext().getCurrentException();
            if (currentException != null) {
                // getClassNode acts as a branch profile
                return getClassNode.execute(currentException.getExceptionObject());
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
        Object doFull(@SuppressWarnings("unused") Object typ, PBaseException val, PTraceback tb,
                        @Shared("language") @CachedLanguage PythonLanguage language) {
            PythonContext context = getContext();
            context.setCaughtException(PException.fromExceptionInfo(val, tb, PythonOptions.isPExceptionWithJavaStacktrace(language)));
            return PNone.NONE;
        }

        @Specialization
        Object doWithoutTraceback(@SuppressWarnings("unused") Object typ, PBaseException val, @SuppressWarnings("unused") PNone tb,
                        @Shared("language") @CachedLanguage PythonLanguage language) {
            return doFull(typ, val, null, language);
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
        Object run(Object typ, PBaseException val, Object tb) {
            if (val.getException() != null) {
                ExceptionUtils.printPythonLikeStackTrace(val.getException());
            }
            return PNone.NO_VALUE;
        }
    }

    @Builtin(name = "PyTruffle_WriteUnraisable", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTruffleWriteUnraisable extends PythonBuiltinNode {

        @Specialization
        Object run(PBaseException exception, Object object,
                        @Cached WriteUnraisableNode writeUnraisableNode) {
            writeUnraisableNode.execute(null, exception, null, (object instanceof PNone) ? PNone.NONE : object);
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

    @Builtin(name = "PyTruffle_Get_Inherited_Native_Slots", minNumOfPositionalArgs = 3, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class PyTruffleGetInheritedNativeSlots extends NativeBuiltin {
        private static final int INDEX_GETSETS = 0;
        private static final int INDEX_MEMBERS = 1;

        /**
         * A native class may inherit from a managed class. However, the managed class may define
         * custom slots at a time where the C API is not yet loaded. So we need to check if any of
         * the base classes defines custom slots and adapt the basicsize to allocate space for the
         * slots and add the native member slot descriptors.
         *
         */
        @Specialization
        Object slots(Object module, Object pythonClass, String subKey,
                        @Cached GetMroStorageNode getMroStorageNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            int idx;
            if ("getsets".equals(subKey)) {
                idx = INDEX_GETSETS;
            } else if ("members".equals(subKey)) {
                idx = INDEX_MEMBERS;
            } else {
                return getNativeNullNode.execute(module);
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
                    l.add(new PythonAbstractNativeObject((TruffleObject) tuple[idx]));
                }
            }
            return l.toArray();
        }
    }

    abstract static class CheckFunctionResultNode extends PNodeWithContext {
        public abstract Object execute(String name, Object result);
    }

    // roughly equivalent to _Py_CheckFunctionResult in Objects/call.c
    @ImportStatic(PGuards.class)
    abstract static class DefaultCheckFunctionResultNode extends CheckFunctionResultNode {

        @Specialization(limit = "1")
        static Object doNativeWrapper(String name, DynamicObjectNativeWrapper.PythonObjectNativeWrapper result,
                        @CachedLibrary(value = "result") PythonNativeWrapperLibrary lib,
                        @Cached DefaultCheckFunctionResultNode recursive) {
            return recursive.execute(name, lib.getDelegate(result));
        }

        @Specialization(guards = "!isPythonObjectNativeWrapper(result)")
        Object doPrimitiveWrapper(String name, @SuppressWarnings("unused") PythonNativeWrapper result,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("ctxt") @CachedContext(PythonLanguage.class) PythonContext context,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raise") @Cached PRaiseNode raise) {
            checkFunctionResult(name, false, false, language, context, raise, factory);
            return result;
        }

        @Specialization(guards = "isNoValue(result)")
        Object doNoValue(String name, @SuppressWarnings("unused") PNone result,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("ctxt") @CachedContext(PythonLanguage.class) PythonContext context,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raise") @Cached PRaiseNode raise) {
            checkFunctionResult(name, true, false, language, context, raise, factory);
            return PNone.NO_VALUE;
        }

        @Specialization(guards = "!isNoValue(result)")
        Object doPythonObject(String name, @SuppressWarnings("unused") PythonAbstractObject result,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("ctxt") @CachedContext(PythonLanguage.class) PythonContext context,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raise") @Cached PRaiseNode raise) {
            checkFunctionResult(name, false, false, language, context, raise, factory);
            return result;
        }

        @Specialization
        Object doPythonNativeNull(String name, @SuppressWarnings("unused") PythonNativeNull result,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("ctxt") @CachedContext(PythonLanguage.class) PythonContext context,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raise") @Cached PRaiseNode raise) {
            checkFunctionResult(name, true, false, language, context, raise, factory);
            return result;
        }

        @Specialization
        int doInteger(String name, int result,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("ctxt") @CachedContext(PythonLanguage.class) PythonContext context,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raise") @Cached PRaiseNode raise) {
            // If the native functions returns a primitive int, only a value '-1' indicates an
            // error.
            checkFunctionResult(name, result == -1, true, language, context, raise, factory);
            return result;
        }

        @Specialization
        long doLong(String name, long result,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("ctxt") @CachedContext(PythonLanguage.class) PythonContext context,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raise") @Cached PRaiseNode raise) {
            // If the native functions returns a primitive int, only a value '-1' indicates an
            // error.
            checkFunctionResult(name, result == -1, true, language, context, raise, factory);
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
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("ctxt") @CachedContext(PythonLanguage.class) PythonContext context,
                        @Shared("fact") @Cached PythonObjectFactory factory,
                        @Shared("raise") @Cached PRaiseNode raise) {
            checkFunctionResult(name, isNullProfile.profile(lib.isNull(result)), false, language, context, raise, factory);
            return result;
        }

        private void checkFunctionResult(String name, boolean indicatesError, boolean isPrimitiveResult, PythonLanguage language, PythonContext context, PRaiseNode raise,
                        PythonObjectFactory factory) {
            PException currentException = context.getCurrentException();
            boolean errOccurred = currentException != null;
            if (indicatesError) {
                // consume exception
                context.setCurrentException(null);
                if (!errOccurred && !isPrimitiveResult) {
                    throw raise.raise(PythonErrorType.SystemError, ErrorMessages.RETURNED_NULL_WO_SETTING_ERROR, name);
                } else {
                    throw currentException.getExceptionForReraise();
                }
            } else if (errOccurred) {
                // consume exception
                context.setCurrentException(null);
                PBaseException sysExc = factory.createBaseException(PythonErrorType.SystemError, ErrorMessages.RETURNED_RESULT_WITH_ERROR_SET, new Object[]{name});
                sysExc.setCause(currentException.getEscapedException());
                throw PException.fromObject(sysExc, this, PythonOptions.isPExceptionWithJavaStacktrace(language));
            }
        }

        protected static boolean isNativeNull(TruffleObject object) {
            return object instanceof PythonNativeNull;
        }

        protected static boolean isPythonObjectNativeWrapper(Object object) {
            return object instanceof DynamicObjectNativeWrapper.PythonObjectNativeWrapper;
        }
    }

    @Builtin(name = "Py_NoValue")
    @GenerateNodeFactory
    abstract static class Py_NoValue extends PythonBuiltinNode {
        @Specialization
        PNone doNoValue() {
            return PNone.NO_VALUE;
        }
    }

    @Builtin(name = "Py_None")
    @GenerateNodeFactory
    abstract static class PyNoneNode extends PythonBuiltinNode {
        @Specialization
        PNone doNativeNone() {
            return PNone.NONE;
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

        protected Object raiseNative(VirtualFrame frame, Object defaultValue, PythonBuiltinClassType errType, String fmt, Object... args) {
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
        protected static String toString(CharBuffer cb) {
            return cb.toString();
        }

        @TruffleBoundary
        protected static int remaining(ByteBuffer cb) {
            return cb.remaining();
        }
    }

    // directly called without landing function
    @Builtin(name = "PyLong_AsPrimitive", minNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonTypes.class)
    @GenerateNodeFactory
    abstract static class PyLongAsPrimitive extends PythonTernaryBuiltinNode {

        public abstract Object executeWith(VirtualFrame frame, Object object, int signed, long targetTypeSize);

        public abstract long executeLong(VirtualFrame frame, Object object, int signed, long targetTypeSize);

        public abstract int executeInt(VirtualFrame frame, Object object, int signed, long targetTypeSize);

        @Specialization(rewriteOn = {UnexpectedWrapperException.class, UnexpectedResultException.class})
        static long doPrimitiveNativeWrapperToLong(VirtualFrame frame, Object object, int signed, long targetTypeSize,
                        @Shared("resolveHandleNode") @Cached ResolveHandleNode resolveHandleNode,
                        @Shared("converPIntToPrimitiveNode") @Cached ConvertPIntToPrimitiveNode convertPIntToPrimitiveNode,
                        @Shared("transformExceptionToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) throws UnexpectedWrapperException, UnexpectedResultException {
            Object resolvedPointer = resolveHandleNode.execute(object);
            try {
                if (resolvedPointer instanceof PrimitiveNativeWrapper) {
                    return convertPIntToPrimitiveNode.executeLong(frame, resolvedPointer, signed, targetTypeSize);
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw UnexpectedWrapperException.INSTANCE;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
        }

        @Specialization(replaces = "doPrimitiveNativeWrapperToLong", rewriteOn = UnexpectedResultException.class)
        static long doGenericToLong(VirtualFrame frame, Object object, int signed, long targetTypeSize,
                        @Shared("resolveHandleNode") @Cached ResolveHandleNode resolveHandleNode,
                        @Shared("toJavaNode") @Cached ToJavaNode toJavaNode,
                        @Cached("createClassProfile()") ValueProfile pointerClassProfile,
                        @Shared("converPIntToPrimitiveNode") @Cached ConvertPIntToPrimitiveNode convertPIntToPrimitiveNode,
                        @Shared("transformExceptionToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) throws UnexpectedResultException {
            Object resolvedPointer = pointerClassProfile.profile(resolveHandleNode.execute(object));
            try {
                if (resolvedPointer instanceof PrimitiveNativeWrapper) {
                    return convertPIntToPrimitiveNode.executeLong(frame, resolvedPointer, signed, targetTypeSize);
                }
                return convertPIntToPrimitiveNode.executeLong(frame, toJavaNode.execute(resolvedPointer), signed, targetTypeSize);
            } catch (UnexpectedResultException e) {
                CompilerAsserts.neverPartOfCompilation();
                throw new UnexpectedResultException(CastToNativeLongNodeGen.getUncached().execute(e.getResult()));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
        }

        @Specialization(replaces = {"doPrimitiveNativeWrapperToLong", "doGenericToLong"})
        static Object doGeneric(VirtualFrame frame, Object object, int signed, long targetTypeSize,
                        @Shared("resolveHandleNode") @Cached ResolveHandleNode resolveHandleNode,
                        @Shared("toJavaNode") @Cached ToJavaNode toJavaNode,
                        @Cached CastToNativeLongNode castToNativeLongNode,
                        @Cached("createClassProfile()") ValueProfile pointerClassProfile,
                        @Cached IntNode constructIntNode,
                        @Shared("converPIntToPrimitiveNode") @Cached ConvertPIntToPrimitiveNode convertPIntToPrimitiveNode,
                        @Shared("transformExceptionToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            Object resolvedPointer = pointerClassProfile.profile(resolveHandleNode.execute(object));
            try {
                if (resolvedPointer instanceof PrimitiveNativeWrapper) {
                    return convertPIntToPrimitiveNode.execute(frame, resolvedPointer, signed, targetTypeSize);
                }
                Object coerced = constructIntNode.execute(frame, PythonBuiltinClassType.PInt, toJavaNode.execute(resolvedPointer), PNone.NO_VALUE);
                return castToNativeLongNode.execute(convertPIntToPrimitiveNode.execute(frame, coerced, signed, targetTypeSize));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
        }

        static final class UnexpectedWrapperException extends ControlFlowException {
            private static final long serialVersionUID = 1L;
            static final UnexpectedWrapperException INSTANCE = new UnexpectedWrapperException();
        }
    }

    @Builtin(name = "PyTruffle_Unicode_FromWchar", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyTruffle_Unicode_FromWchar extends NativeUnicodeBuiltin {
        @Specialization
        static Object doNativeWrapper(VirtualFrame frame, PythonNativeWrapper arr, long elementSize, Object errorMarker,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Shared("unicodeFromWcharNode") @Cached UnicodeFromWcharNode unicodeFromWcharNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Shared("excToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return toSulongNode.execute(unicodeFromWcharNode.execute(asPythonObjectNode.execute(arr), elementSize));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return errorMarker;
            }
        }

        @Specialization
        static Object doPointer(VirtualFrame frame, Object arr, long elementSize, Object errorMarker,
                        @Shared("unicodeFromWcharNode") @Cached UnicodeFromWcharNode unicodeFromWcharNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Shared("excToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return toSulongNode.execute(unicodeFromWcharNode.execute(arr, elementSize));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return errorMarker;
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
        Object doUnicode(VirtualFrame frame, PString s, @SuppressWarnings("unused") PNone errors, Object error_marker,
                        @Shared("encodeNode") @Cached EncodeNativeStringNode encodeNativeStringNode) {
            return doUnicode(frame, s, "strict", error_marker, encodeNativeStringNode);
        }

        @Specialization
        Object doUnicode(VirtualFrame frame, PString s, String errors, Object error_marker,
                        @Shared("encodeNode") @Cached EncodeNativeStringNode encodeNativeStringNode) {
            try {
                return encodeNativeStringNode.execute(charset, s, errors);
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

    // directly called without landing function
    @Builtin(name = "PyTruffle_Unicode_DecodeUTF32", minNumOfPositionalArgs = 5)
    @GenerateNodeFactory
    abstract static class PyTruffle_Unicode_DecodeUTF32 extends NativeUnicodeBuiltin {

        @Specialization
        Object doUnicodeStringErrors(VirtualFrame frame, TruffleObject o, long size, String errors, int byteorder, Object errorMarker,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Shared("getByteArrayNode") @Cached GetByteArrayNode getByteArrayNode) {
            try {
                return toSulongNode.execute(decodeUTF32(getByteArrayNode.execute(frame, o, size), (int) size, errors, byteorder));
            } catch (CharacterCodingException e) {
                return raiseNative(frame, errorMarker, PythonErrorType.UnicodeEncodeError, "%m", e);
            } catch (IllegalArgumentException e) {
                String csName = Charsets.getUTF32Name(byteorder);
                return raiseNative(frame, errorMarker, PythonErrorType.LookupError, ErrorMessages.UNKNOWN_ENCODING, csName);
            } catch (InteropException e) {
                return raiseNative(frame, errorMarker, PythonErrorType.TypeError, "%m", e);
            }
        }

        @Specialization(replaces = "doUnicodeStringErrors")
        Object doUnicode(VirtualFrame frame, TruffleObject o, long size, Object errors, int byteorder, Object errorMarker,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Shared("getByteArrayNode") @Cached GetByteArrayNode getByteArrayNode) {
            Object perrors = asPythonObjectNode.execute(errors);
            assert perrors == PNone.NO_VALUE || perrors instanceof String;
            return doUnicodeStringErrors(frame, o, size, perrors == PNone.NO_VALUE ? "strict" : (String) perrors, byteorder, errorMarker, toSulongNode, getByteArrayNode);
        }

        @TruffleBoundary
        private String decodeUTF32(byte[] data, int size, String errors, int byteorder) throws CharacterCodingException {
            CharsetDecoder decoder = Charsets.getUTF32Charset(byteorder).newDecoder();
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
            if (ary.length > n && n >= 0) {
                // cast to int is guaranteed because of 'ary.length > n'
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
                        @Cached BytesNodes.ToBytesNode toBytesNode) {
            return subRangeIfNeeded(toBytesNode.execute(frame, lib.getDelegate(obj)), n);
        }

        @Specialization(limit = "5")
        byte[] doForeign(VirtualFrame frame, Object obj, long n,
                        @Cached("createBinaryProfile()") ConditionProfile profile,
                        @CachedLibrary("obj") InteropLibrary interopLib,
                        @Cached CastToByteNode castToByteNode) throws InteropException {
            long size;
            if (profile.profile(n < 0)) {
                size = interopLib.getArraySize(obj);
            } else {
                size = n;
            }
            return readWithSize(frame, interopLib, castToByteNode, obj, size);
        }

        private static byte[] readWithSize(VirtualFrame frame, InteropLibrary interopLib, CastToByteNode castToByteNode, Object o, long size)
                        throws UnsupportedMessageException, InvalidArrayIndexException {
            byte[] bytes = new byte[(int) size];
            for (long i = 0; i < size; i++) {
                Object elem = interopLib.readArrayElement(o, i);
                bytes[(int) i] = castToByteNode.execute(frame, elem);
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
                    return raiseNative(frame, errorMarker, PythonErrorType.ValueError, ErrorMessages.UNSUPPORTED_SIZE_WAS, "wchar", elementSize);
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
                return raiseNative(frame, errorMarker, PythonErrorType.ValueError, ErrorMessages.INVALID_PARAMS);
            }
        }

        @Specialization
        Object doUnicode(VirtualFrame frame, String s, PInt elementSize, PInt elements, Object errorMarker) {
            try {
                return doUnicode(frame, s, elementSize.longValueExact(), elements.longValueExact(), errorMarker);
            } catch (ArithmeticException e) {
                return raiseNative(frame, errorMarker, PythonErrorType.ValueError, ErrorMessages.INVALID_PARAMS);
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
            return raiseNative(frame, errorMarker, PythonErrorType.TypeError, ErrorMessages.EXPECTED_S_P_FOUND, "bytes", o);
        }
    }

    @Builtin(name = "PyHash_Imag")
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

    @Builtin(name = "PyTraceBack_Here", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTraceBackHereNode extends PythonUnaryBuiltinNode {
        @Specialization
        int tbHere(PFrame frame,
                        @Cached GetTracebackNode getTracebackNode,
                        @CachedLanguage PythonLanguage language) {
            PythonContext context = getContext();
            PException currentException = context.getCurrentException();
            if (currentException != null) {
                PTraceback traceback = null;
                if (currentException.getTraceback() != null) {
                    traceback = getTracebackNode.execute(currentException.getTraceback());
                }
                PTraceback newTraceback = factory().createTraceback(frame, frame.getLine(), traceback);
                boolean withJavaStacktrace = PythonOptions.isPExceptionWithJavaStacktrace(language);
                context.setCurrentException(PException.fromExceptionInfo(currentException.getExceptionObject(), newTraceback, withJavaStacktrace));
            }

            return 0;
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
            Object pclass = getGetClassNode().execute(lib.getDelegate(nativeWrapper));
            return getGetTypeFlagsNode().execute(pclass);
        }

        @Specialization
        long doPythonObject(PythonAbstractObject object) {
            Object pclass = getGetClassNode().execute(object);
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

    @Builtin(name = "PyThreadState_Get")
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
        Object call(Object get, Object set, String name, Object owner) {
            return factory().createGetSetDescriptor(get, set, name, owner, true);
        }

        @Specialization(guards = {"!isNoValue(get)", "isNoValue(set)"})
        Object call(Object get, @SuppressWarnings("unused") PNone set, String name, Object owner) {
            return factory().createGetSetDescriptor(get, null, name, owner);
        }

        @Specialization(guards = {"isNoValue(get)", "!isNoValue(set)"})
        Object call(@SuppressWarnings("unused") PNone get, Object set, String name, Object owner) {
            return factory().createGetSetDescriptor(null, set, name, owner, true);
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
        private final Supplier<CheckFunctionResultNode> checkFunctionResultNodeSupplier;

        public PExternalFunctionWrapper(Supplier<ConvertArgsToSulongNode> convertArgsNodeSupplier) {
            super(PythonBuiltinClassType.PythonObject, PythonBuiltinClassType.PythonObject.getInstanceShape());
            this.convertArgsNodeSupplier = convertArgsNodeSupplier;
            this.checkFunctionResultNodeSupplier = DefaultCheckFunctionResultNodeGen::create;
        }

        public PExternalFunctionWrapper(Supplier<ConvertArgsToSulongNode> convertArgsNodeSupplier, Supplier<CheckFunctionResultNode> checkFunctionResultNodeSupplier) {
            super(PythonBuiltinClassType.PythonObject, PythonBuiltinClassType.PythonObject.getInstanceShape());
            this.convertArgsNodeSupplier = convertArgsNodeSupplier;
            this.checkFunctionResultNodeSupplier = checkFunctionResultNodeSupplier;
        }

        protected abstract RootCallTarget createCallTarget(PythonLanguage language, String name, Object callable, boolean doArgAndResultConversion);

        @TruffleBoundary
        public static RootCallTarget createCallTarget(RootNode n) {
            return Truffle.getRuntime().createCallTarget(n);
        }

        @TruffleBoundary
        protected ConvertArgsToSulongNode createConvertArgsToSulongNode() {
            return convertArgsNodeSupplier.get();
        }

        @TruffleBoundary
        public CheckFunctionResultNode getCheckFunctionResultNode() {
            return checkFunctionResultNodeSupplier.get();
        }

        @Override
        public int compareTo(Object o) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @Builtin(name = "METH_DIRECT")
    @GenerateNodeFactory
    public abstract static class MethDirectNode extends PythonBuiltinNode {
        public static final PExternalFunctionWrapper METH_DIRECT_CONVERTER = new PExternalFunctionWrapper(AllToSulongNode::create) {

            @Override
            @TruffleBoundary
            protected RootCallTarget createCallTarget(PythonLanguage language, String name, Object callable, boolean doArgAndResultConversion) {
                if (!doArgAndResultConversion) {
                    // this should directly (== without argument conversion) call a managed
                    // function; so directly use the function. null indicates this
                    return null;
                } else {
                    return createCallTarget(MethDirectRoot.create(language, name, callable));
                }
            }
        };

        @Specialization
        static PExternalFunctionWrapper call() {
            return METH_DIRECT_CONVERTER;
        }
    }

    @Builtin(name = "METH_KEYWORDS")
    @GenerateNodeFactory
    public abstract static class MethKeywordsNode extends PythonBuiltinNode {
        public static final PExternalFunctionWrapper METH_KEYWORDS_CONVERTER = new PExternalFunctionWrapper(AllToSulongNode::create) {

            @Override
            @TruffleBoundary
            protected RootCallTarget createCallTarget(PythonLanguage language, String name, Object callable, boolean doArgAndResultConversion) {
                if (!doArgAndResultConversion) {
                    return createCallTarget(new MethKeywordsRoot(language, name, callable));
                } else {
                    return createCallTarget(new MethKeywordsRoot(language, name, callable, this));
                }
            }
        };

        @Specialization
        static PExternalFunctionWrapper call() {
            return METH_KEYWORDS_CONVERTER;
        }
    }

    @Builtin(name = "METH_VARARGS")
    @GenerateNodeFactory
    public abstract static class MethVarargsNode extends PythonBuiltinNode {
        public static final PExternalFunctionWrapper METH_VARARGS_CONVERTER = new PExternalFunctionWrapper(AllToSulongNode::create) {

            @Override
            @TruffleBoundary
            protected RootCallTarget createCallTarget(PythonLanguage language, String name, Object callable, boolean doArgAndResultConversion) {
                if (!doArgAndResultConversion) {
                    return createCallTarget(new MethVarargsRoot(language, name, callable));
                } else {
                    return createCallTarget(new MethVarargsRoot(language, name, callable, this));
                }
            }
        };

        @Specialization
        static PExternalFunctionWrapper call() {
            return METH_VARARGS_CONVERTER;
        }
    }

    @Builtin(name = "METH_NOARGS")
    @GenerateNodeFactory
    public abstract static class MethNoargsNode extends PythonBuiltinNode {
        public static final PExternalFunctionWrapper METH_NOARGS_CONVERTER = new PExternalFunctionWrapper(AllToSulongNode::create) {

            @Override
            @TruffleBoundary
            protected RootCallTarget createCallTarget(PythonLanguage language, String name, Object callable, boolean doArgAndResultConversion) {
                if (!doArgAndResultConversion) {
                    return createCallTarget(new MethNoargsRoot(language, name, callable));
                } else {
                    return createCallTarget(new MethNoargsRoot(language, name, callable, this));
                }
            }
        };

        @Specialization
        static PExternalFunctionWrapper call() {
            return METH_NOARGS_CONVERTER;
        }
    }

    @Builtin(name = "METH_O")
    @GenerateNodeFactory
    public abstract static class MethONode extends PythonBuiltinNode {
        public static final PExternalFunctionWrapper METH_O_CONVERTER = new PExternalFunctionWrapper(AllToSulongNode::create) {

            @Override
            @TruffleBoundary
            protected RootCallTarget createCallTarget(PythonLanguage language, String name, Object callable, boolean doArgAndResultConversion) {
                if (!doArgAndResultConversion) {
                    return createCallTarget(new MethORoot(language, name, callable));
                } else {
                    return createCallTarget(new MethORoot(language, name, callable, this));
                }
            }
        };

        @Specialization
        static PExternalFunctionWrapper call() {
            return METH_O_CONVERTER;
        }
    }

    @Builtin(name = "METH_FASTCALL")
    @GenerateNodeFactory
    public abstract static class MethFastcallNode extends PythonBuiltinNode {
        public static final PExternalFunctionWrapper METH_FASTCALL_CONVERTER = new PExternalFunctionWrapper(FastCallArgsToSulongNode::create) {

            @Override
            @TruffleBoundary
            protected RootCallTarget createCallTarget(PythonLanguage language, String name, Object callable, boolean doArgAndResultConversion) {
                if (!doArgAndResultConversion) {
                    return createCallTarget(new MethFastcallRoot(language, name, callable));
                } else {
                    return createCallTarget(new MethFastcallRoot(language, name, callable, this));
                }
            }
        };

        @Specialization
        static PExternalFunctionWrapper call() {
            return METH_FASTCALL_CONVERTER;
        }
    }

    @Builtin(name = "METH_FASTCALL_WITH_KEYWORDS")
    @GenerateNodeFactory
    public abstract static class MethFastcallWithKeywordsNode extends PythonBuiltinNode {
        public static final PExternalFunctionWrapper METH_FASTCALL_WITH_KEYWORDS_CONVERTER = new PExternalFunctionWrapper(FastCallWithKeywordsArgsToSulongNode::create) {

            @Override
            @TruffleBoundary
            protected RootCallTarget createCallTarget(PythonLanguage language, String name, Object callable, boolean doArgAndResultConversion) {
                if (!doArgAndResultConversion) {
                    return createCallTarget(new MethFastcallWithKeywordsRoot(language, name, callable));
                } else {
                    return createCallTarget(new MethFastcallWithKeywordsRoot(language, name, callable, this));
                }
            }
        };

        @Specialization
        static PExternalFunctionWrapper call() {
            return METH_FASTCALL_WITH_KEYWORDS_CONVERTER;
        }
    }

    @Builtin(name = "METH_ALLOC")
    @GenerateNodeFactory
    public abstract static class MethAllocNode extends PythonBuiltinNode {
        public static final PExternalFunctionWrapper METH_ALLOC_CONVERTER = new PExternalFunctionWrapper(BinaryFirstToSulongNode::create) {

            @Override
            @TruffleBoundary
            protected RootCallTarget createCallTarget(PythonLanguage language, String name, Object callable, boolean doArgAndResultConversion) {
                if (!doArgAndResultConversion) {
                    return createCallTarget(new AllocFuncRootNode(language, name, callable));
                } else {
                    return createCallTarget(new AllocFuncRootNode(language, name, callable, this));
                }
            }
        };

        @Specialization
        static PExternalFunctionWrapper call() {
            return METH_ALLOC_CONVERTER;
        }
    }

    @Builtin(name = "METH_GETATTR")
    @GenerateNodeFactory
    public abstract static class MethGetattrNode extends PythonBuiltinNode {
        public static final PExternalFunctionWrapper METH_GETATTR_CONVERTER = new PExternalFunctionWrapper(BinaryFirstToSulongNode::create) {

            @Override
            @TruffleBoundary
            protected RootCallTarget createCallTarget(PythonLanguage language, String name, Object callable, boolean doArgAndResultConversion) {
                if (!doArgAndResultConversion) {
                    return createCallTarget(new GetAttrFuncRootNode(language, name, callable));
                } else {
                    return createCallTarget(new GetAttrFuncRootNode(language, name, callable, this));
                }
            }
        };

        @Specialization
        static PExternalFunctionWrapper call() {
            return METH_GETATTR_CONVERTER;
        }
    }

    @Builtin(name = "METH_SETATTR")
    @GenerateNodeFactory
    public abstract static class MethSetattrNode extends PythonBuiltinNode {
        public static final PExternalFunctionWrapper METH_SETATTR_CONVERTER = new PExternalFunctionWrapper(TernaryFirstThirdToSulongNode::create) {

            @Override
            @TruffleBoundary
            protected RootCallTarget createCallTarget(PythonLanguage language, String name, Object callable, boolean doArgAndResultConversion) {
                if (!doArgAndResultConversion) {
                    return createCallTarget(new SetAttrFuncRootNode(language, name, callable));
                } else {
                    return createCallTarget(new SetAttrFuncRootNode(language, name, callable, this));
                }
            }
        };

        @Specialization
        static PExternalFunctionWrapper call() {
            return METH_SETATTR_CONVERTER;
        }
    }

    @Builtin(name = "METH_RICHCMP")
    @GenerateNodeFactory
    public abstract static class MethRichcmpNode extends PythonBuiltinNode {
        public static final PExternalFunctionWrapper METH_RICHCMP_CONVERTER = new PExternalFunctionWrapper(TernaryFirstSecondToSulongNode::create) {

            @Override
            @TruffleBoundary
            protected RootCallTarget createCallTarget(PythonLanguage language, String name, Object callable, boolean doArgAndResultConversion) {
                if (!doArgAndResultConversion) {
                    return createCallTarget(new RichCmpFuncRootNode(language, name, callable));
                } else {
                    return createCallTarget(new RichCmpFuncRootNode(language, name, callable, this));
                }
            }
        };

        @Specialization
        static PExternalFunctionWrapper call() {
            return METH_RICHCMP_CONVERTER;
        }
    }

    @Builtin(name = "METH_SSIZE_OBJ_ARG")
    @GenerateNodeFactory
    public abstract static class MethSSizeObjArgNode extends PythonBuiltinNode {
        public static final PExternalFunctionWrapper METH_SSIZE_OBJ_ARG_CONVERTER = new PExternalFunctionWrapper(TernaryFirstThirdToSulongNode::create) {

            @Override
            @TruffleBoundary
            protected RootCallTarget createCallTarget(PythonLanguage language, String name, Object callable, boolean doArgAndResultConversion) {
                if (!doArgAndResultConversion) {
                    return createCallTarget(new SSizeObjArgProcRootNode(language, name, callable));
                } else {
                    return createCallTarget(new SSizeObjArgProcRootNode(language, name, callable, this));
                }
            }
        };

        @Specialization
        static PExternalFunctionWrapper call() {
            return METH_SSIZE_OBJ_ARG_CONVERTER;
        }
    }

    @Builtin(name = "METH_REVERSE")
    @GenerateNodeFactory
    public abstract static class MethReverseNode extends PythonBuiltinNode {
        public static final PExternalFunctionWrapper METH_REVERSE_CONVERTER = new PExternalFunctionWrapper(AllToSulongNode::create) {

            @Override
            @TruffleBoundary
            protected RootCallTarget createCallTarget(PythonLanguage language, String name, Object callable, boolean doArgAndResultConversion) {
                if (!doArgAndResultConversion) {
                    return createCallTarget(new MethReverseRootNode(language, name, callable));
                } else {
                    return createCallTarget(new MethReverseRootNode(language, name, callable, this));
                }
            }
        };

        @Specialization
        static PExternalFunctionWrapper call() {
            return METH_REVERSE_CONVERTER;
        }
    }

    @Builtin(name = "METH_POW")
    @GenerateNodeFactory
    public abstract static class MethPowNode extends PythonBuiltinNode {
        public static final PExternalFunctionWrapper METH_POW_CONVERTER = new PExternalFunctionWrapper(AllToSulongNode::create) {

            @Override
            @TruffleBoundary
            protected RootCallTarget createCallTarget(PythonLanguage language, String name, Object callable, boolean doArgAndResultConversion) {
                if (!doArgAndResultConversion) {
                    return createCallTarget(new MethPowRootNode(language, name, callable));
                } else {
                    return createCallTarget(new MethPowRootNode(language, name, callable, this));
                }
            }
        };

        @Specialization
        static PExternalFunctionWrapper call() {
            return METH_POW_CONVERTER;
        }
    }

    @Builtin(name = "METH_REVERSE_POW")
    @GenerateNodeFactory
    public abstract static class MethRPowNode extends PythonBuiltinNode {
        public static final PExternalFunctionWrapper METH_REVERSE_POW_CONVERTER = new PExternalFunctionWrapper(AllToSulongNode::create) {

            @Override
            @TruffleBoundary
            protected RootCallTarget createCallTarget(PythonLanguage language, String name, Object callable, boolean doArgAndResultConversion) {
                if (!doArgAndResultConversion) {
                    return createCallTarget(new MethRPowRootNode(language, name, callable));
                } else {
                    return createCallTarget(new MethRPowRootNode(language, name, callable, this));
                }
            }
        };

        @Specialization
        static PExternalFunctionWrapper call() {
            return METH_REVERSE_POW_CONVERTER;
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

        private static final PExternalFunctionWrapper[] METH_RICHCMP_OP_CONVERTERS = new PExternalFunctionWrapper[PY_GE + 1];

        private static PExternalFunctionWrapper createConverter(int op) {
            return new PExternalFunctionWrapper(TernaryFirstSecondToSulongNode::create) {
                @Override
                @TruffleBoundary
                protected RootCallTarget createCallTarget(PythonLanguage language, String name, Object callable, boolean doArgAndResultConversion) {
                    if (!doArgAndResultConversion) {
                        return createCallTarget(new MethRichcmpOpRootNode(language, name, callable, op));
                    } else {
                        return createCallTarget(new MethRichcmpOpRootNode(language, name, callable, this, op));
                    }
                }
            };
        }

        private final int op;

        MethRichcmpOpBaseNode(int op) {
            assert PY_LT <= op && op <= PY_GE;
            this.op = op;
        }

        @Specialization
        PExternalFunctionWrapper call() {
            PExternalFunctionWrapper converter = METH_RICHCMP_OP_CONVERTERS[op];
            if (converter == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                synchronized (METH_RICHCMP_OP_CONVERTERS) {
                    // check again
                    if (METH_RICHCMP_OP_CONVERTERS[op] == null) {
                        converter = MethRichcmpOpBaseNode.createConverter(op);
                        METH_RICHCMP_OP_CONVERTERS[op] = converter;
                    } else {
                        // if another thread created it in the meantime; load it
                        converter = METH_RICHCMP_OP_CONVERTERS[op];
                    }
                }
            }
            return converter;
        }
    }

    @Builtin(name = "METH_LT")
    @GenerateNodeFactory
    public abstract static class MethLtNode extends MethRichcmpOpBaseNode {
        protected MethLtNode() {
            super(PY_LT);
        }
    }

    @Builtin(name = "METH_LE")
    @GenerateNodeFactory
    public abstract static class MethLeNode extends MethRichcmpOpBaseNode {
        protected MethLeNode() {
            super(PY_LE);
        }
    }

    @Builtin(name = "METH_EQ")
    @GenerateNodeFactory
    public abstract static class MethEqNode extends MethRichcmpOpBaseNode {
        protected MethEqNode() {
            super(PY_EQ);
        }
    }

    @Builtin(name = "METH_NE")
    @GenerateNodeFactory
    public abstract static class MethNeNode extends MethRichcmpOpBaseNode {
        protected MethNeNode() {
            super(PY_NE);
        }
    }

    @Builtin(name = "METH_GT")
    @GenerateNodeFactory
    public abstract static class MethGtNode extends MethRichcmpOpBaseNode {
        protected MethGtNode() {
            super(PY_GT);
        }
    }

    @Builtin(name = "METH_GE")
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

        @Specialization(rewriteOn = ArithmeticException.class)
        PBytes doPInt(PInt size) {
            return doInt(size.intValueExact());
        }

        @Specialization(replaces = "doPInt")
        PBytes doPIntOvf(PInt size,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            try {
                return doInt(size.intValueExact());
            } catch (ArithmeticException e) {
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

    @Builtin(name = "PyTruffle_Upcall_Borrowed", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class UpcallBorrowedNode extends UpcallLandingNode {

        @Specialization
        Object upcall(VirtualFrame frame, PythonModule cextModule, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Cached ObjectUpcallNode upcallNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return toSulongNode.execute(upcallNode.execute(frame, args));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return toSulongNode.execute(getNativeNullNode.execute(cextModule));
            }
        }
    }

    @Builtin(name = "PyTruffle_Upcall_NewRef", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class UpcallNewRefNode extends UpcallLandingNode {

        @Specialization
        static Object upcall(VirtualFrame frame, PythonModule cextModule, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached ObjectUpcallNode upcallNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return toNewRefNode.execute(upcallNode.execute(frame, args));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return toNewRefNode.execute(getNativeNullNode.execute(cextModule));
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
        double upcall(VirtualFrame frame, @SuppressWarnings("unused") Object self, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
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
        Object upcall(VirtualFrame frame, PythonModule cextModule, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached ObjectUpcallNode upcallNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return upcallNode.execute(frame, args);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return getNativeNullNode.execute(cextModule);
            }
        }
    }

    @Builtin(name = "PyTruffle_Cext_Upcall_Borrowed", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class UpcallCextBorrowedNode extends UpcallLandingNode {

        @Specialization(guards = "isStringCallee(args)")
        Object upcall(VirtualFrame frame, PythonModule cextModule, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached CextUpcallNode upcallNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(upcallNode.execute(frame, cextModule, args));
        }

        @Specialization(guards = "!isStringCallee(args)")
        Object doDirect(VirtualFrame frame, @SuppressWarnings("unused") PythonModule cextModule, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
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
        double upcall(VirtualFrame frame, PythonModule cextModule, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached CextUpcallNode upcallNode,
                        @Shared("castToDoubleNode") @Cached CastToJavaDoubleNode castToDoubleNode) {
            return castToDoubleNode.execute(upcallNode.execute(frame, cextModule, args));
        }

        @Specialization(guards = "!isStringCallee(args)")
        double doDirect(VirtualFrame frame, @SuppressWarnings("unused") PythonModule cextModule, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
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
        double doIt(Object object,
                        @Cached CastToJavaDoubleNode castToDoubleNode) {
            return castToDoubleNode.execute(object);
        }
    }

    @Builtin(name = "PyTruffle_Register_NULL", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffle_Register_NULL extends PythonUnaryBuiltinNode {
        @Specialization
        Object doIt(Object object,
                        @Cached ReadAttributeFromObjectNode readAttrNode) {
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
        static Object createCache(TruffleObject ptrToResolveHandle) {
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

    // directly called without landing function
    @Builtin(name = "PyLong_FromLongLong", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyLongFromLongLong extends PythonBinaryBuiltinNode {
        @Specialization(guards = "signed != 0")
        static Object doSignedInt(int n, @SuppressWarnings("unused") int signed,
                        @Shared("toNewRefNode") @Cached ToNewRefNode toNewRefNode) {
            return toNewRefNode.executeInt(n);
        }

        @Specialization(guards = "signed == 0")
        static Object doUnsignedInt(int n, @SuppressWarnings("unused") int signed,
                        @Shared("toNewRefNode") @Cached ToNewRefNode toNewRefNode) {
            if (n < 0) {
                return toNewRefNode.executeLong(n & 0xFFFFFFFFL);
            }
            return toNewRefNode.executeInt(n);
        }

        @Specialization(guards = "signed != 0")
        static Object doSignedLong(long n, @SuppressWarnings("unused") int signed,
                        @Shared("toNewRefNode") @Cached ToNewRefNode toNewRefNode) {
            return toNewRefNode.executeLong(n);
        }

        @Specialization(guards = {"signed == 0", "n >= 0"})
        static Object doUnsignedLongPositive(long n, @SuppressWarnings("unused") int signed,
                        @Shared("toNewRefNode") @Cached ToNewRefNode toNewRefNode) {
            return toNewRefNode.executeLong(n);
        }

        @Specialization(guards = {"signed == 0", "n < 0"})
        Object doUnsignedLongNegative(long n, @SuppressWarnings("unused") int signed,
                        @Shared("toNewRefNode") @Cached ToNewRefNode toNewRefNode) {
            return toNewRefNode.execute(factory().createInt(convertToBigInteger(n)));
        }

        @TruffleBoundary
        private static BigInteger convertToBigInteger(long n) {
            return BigInteger.valueOf(n).add(BigInteger.ONE.shiftLeft(Long.SIZE));
        }
    }

    @Builtin(name = "PyLong_FromVoidPtr", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyLongFromVoidPtr extends PythonUnaryBuiltinNode {
        @Specialization
        Object doPointer(TruffleObject pointer,
                        @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(factory().createNativeVoidPtr(pointer));
        }
    }

    @Builtin(name = "PyLong_AsVoidPtr", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyLongAsVoidPtr extends PythonUnaryBuiltinNode {
        @Child private ConvertPIntToPrimitiveNode asPrimitiveNode;

        @Specialization
        static long doPointer(int n) {
            return n;
        }

        @Specialization
        static long doPointer(long n) {
            return n;
        }

        @Specialization
        long doPointer(PInt n,
                        @Cached BranchProfile overflowProfile) {
            try {
                return n.longValueExact();
            } catch (ArithmeticException e) {
                overflowProfile.enter();
                throw raise(OverflowError);
            }
        }

        @Specialization
        static TruffleObject doPointer(PythonNativeVoidPtr n) {
            return n.object;
        }

        @Fallback
        long doGeneric(VirtualFrame frame, Object n) {
            if (asPrimitiveNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                asPrimitiveNode = insert(ConvertPIntToPrimitiveNodeGen.create());
            }
            try {
                return asPrimitiveNode.executeLong(frame, n, 0, Long.BYTES);
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("should not be reached");
            }
        }
    }

    @Builtin(name = "PyType_IsSubtype", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(PythonOptions.class)
    abstract static class PyType_IsSubtype extends PythonBinaryBuiltinNode {

        @Specialization(guards = {"a == cachedA", "b == cachedB"})
        static int doCached(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") PythonNativeWrapper a, @SuppressWarnings("unused") PythonNativeWrapper b,
                        @Cached("a") @SuppressWarnings("unused") PythonNativeWrapper cachedA,
                        @Cached("b") @SuppressWarnings("unused") PythonNativeWrapper cachedB,
                        @Cached("doSlow(frame, a, b)") int result) {
            return result;
        }

        protected static Class<?> getClazz(Object v) {
            return v.getClass();
        }

        @Specialization(replaces = "doCached", guards = {"cachedClassA == getClazz(a)", "cachedClassB == getClazz(b)"}, limit = "getVariableArgumentInlineCacheLimit()")
        int doCachedClass(VirtualFrame frame, Object a, Object b,
                        @Cached("getClazz(a)") Class<?> cachedClassA,
                        @Cached("getClazz(b)") Class<?> cachedClassB,
                        @Cached ToJavaNode leftToJavaNode,
                        @Cached ToJavaNode rightToJavaNode,
                        @Cached IsSubtypeNode isSubtypeNode) {
            Object ua = leftToJavaNode.execute(cachedClassA.cast(a));
            Object ub = rightToJavaNode.execute(cachedClassB.cast(b));
            return isSubtypeNode.execute(frame, ua, ub) ? 1 : 0;
        }

        @Specialization(replaces = {"doCached", "doCachedClass"})
        int doGeneric(VirtualFrame frame, Object a, Object b,
                        @Cached ToJavaNode leftToJavaNode,
                        @Cached ToJavaNode rightToJavaNode,
                        @Cached IsSubtypeNode isSubtypeNode) {
            Object ua = leftToJavaNode.execute(a);
            Object ub = rightToJavaNode.execute(b);
            return isSubtypeNode.execute(frame, ua, ub) ? 1 : 0;
        }

        int doSlow(VirtualFrame frame, Object derived, Object cls) {
            return doGeneric(frame, derived, cls, ToJavaNodeGen.getUncached(), ToJavaNodeGen.getUncached(), IsSubtypeNodeGen.getUncached());
        }
    }

    @Builtin(name = "PyTuple_GetItem", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTuple_GetItem extends PythonBinaryBuiltinNode {

        @Specialization
        Object doPTuple(VirtualFrame frame, PTuple tuple, long key,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode) {
            SequenceStorage sequenceStorage = tuple.getSequenceStorage();
            // we must do a bounds-check but we must not normalize the index
            if (key < 0 || key >= lenNode.execute(sequenceStorage)) {
                throw raise(IndexError, ErrorMessages.TUPLE_OUT_OF_BOUNDS);
            }
            return getItemNode.execute(frame, sequenceStorage, key);
        }

        @Fallback
        Object doPTuple(Object tuple, @SuppressWarnings("unused") Object key) {
            // TODO(fa) To be absolutely correct, we need to do a 'isinstance' check on the object.
            throw raise(SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, tuple, tuple);
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
    @ImportStatic(CApiGuards.class)
    abstract static class PyBytes_FromStringAndSize extends NativeBuiltin {
        // n.b.: the specializations for PIBytesLike are quite common on
        // managed, when the PySequenceArrayWrapper that we used never went
        // native, and during the upcall to here it was simply unwrapped again
        // with the ToJava (rather than mapped from a native pointer back into a
        // PythonNativeObject)

        @Specialization
        Object doGeneric(VirtualFrame frame, @SuppressWarnings("unused") Object module, PythonNativeWrapper object, long size,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Exclusive @Cached BytesNodes.ToBytesNode getByteArrayNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            byte[] ary = getByteArrayNode.execute(frame, asPythonObjectNode.execute(object));
            PBytes result;
            if (size >= 0 && size < ary.length) {
                // cast to int is guaranteed because of 'size < ary.length'
                result = factory().createBytes(Arrays.copyOf(ary, (int) size));
            } else {
                result = factory().createBytes(ary);
            }
            return toSulongNode.execute(result);
        }

        @Specialization(guards = "!isNativeWrapper(nativePointer)")
        Object doNativePointer(VirtualFrame frame, Object module, Object nativePointer, long size,
                        @Exclusive @Cached GetNativeNullNode getNativeNullNode,
                        @Exclusive @Cached GetByteArrayNode getByteArrayNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            try {
                return toSulongNode.execute(factory().createBytes(getByteArrayNode.execute(frame, nativePointer, size)));
            } catch (InteropException e) {
                return raiseNative(frame, getNativeNullNode.execute(module), PythonErrorType.TypeError, "%m", e);
            }
        }
    }

    @Builtin(name = "PyFloat_AsDouble", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyFloat_AsDouble extends PythonUnaryBuiltinNode {

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
                        @Shared("asPythonObjectNode") @Cached AsPythonObjectNode asPythonObjectNode,
                        @Shared("asDoubleNode") @Cached AsNativeDoubleNode asDoubleNode,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return asDoubleNode.execute(asPythonObjectNode.execute(object));
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        @Specialization(replaces = "doGeneric")
        double doGenericErr(VirtualFrame frame, Object object,
                        @Shared("asPythonObjectNode") @Cached AsPythonObjectNode asPythonObjectNode,
                        @Shared("asDoubleNode") @Cached AsNativeDoubleNode asDoubleNode,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return doGeneric(frame, object, asPythonObjectNode, asDoubleNode, context);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1.0;
            }
        }
    }

    // directly called without landing function
    @Builtin(name = "PyNumber_Float", minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class PyNumberFloat extends NativeBuiltin {

        @Child private BuiltinConstructors.FloatNode floatNode;

        @Specialization(guards = "object.isDouble()")
        static Object doDoubleNativeWrapper(@SuppressWarnings("unused") Object module, PrimitiveNativeWrapper object,
                        @Cached AddRefCntNode refCntNode) {
            return refCntNode.inc(object);
        }

        @Specialization(guards = "!object.isDouble()")
        static Object doLongNativeWrapper(@SuppressWarnings("unused") Object module, PrimitiveNativeWrapper object,
                        @Cached ToNewRefNode primitiveToSulongNode) {
            return primitiveToSulongNode.execute((double) object.getLong());
        }

        @Specialization(rewriteOn = PException.class)
        Object doGeneric(VirtualFrame frame, @SuppressWarnings("unused") Object module, Object object,
                        @Shared("toNewRefNode") @Cached ToNewRefNode toNewRefNode,
                        @Shared("asPythonObjectNode") @Cached AsPythonObjectNode asPythonObjectNode) {
            if (floatNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                floatNode = insert(BuiltinConstructorsFactory.FloatNodeFactory.create());
            }
            return toNewRefNode.execute(floatNode.executeWith(frame, PythonBuiltinClassType.PFloat, asPythonObjectNode.execute(object)));
        }

        @Specialization(replaces = "doGeneric")
        Object doGenericErr(VirtualFrame frame, Object module, Object object,
                        @Exclusive @Cached GetNativeNullNode getNativeNullNode,
                        @Shared("toNewRefNode") @Cached ToNewRefNode toNewRefNode,
                        @Shared("asPythonObjectNode") @Cached AsPythonObjectNode asPythonObjectNode) {
            try {
                return doGeneric(frame, module, object, toNewRefNode, asPythonObjectNode);
            } catch (PException e) {
                transformToNative(frame, e);
                return toNewRefNode.execute(getNativeNullNode.execute(module));
            }
        }
    }

    @Builtin(name = "PySet_Add", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PySet_Add extends PythonBinaryBuiltinNode {

        @Specialization
        int add(VirtualFrame frame, PBaseSet self, Object o,
                        @Cached HashingCollectionNodes.SetItemNode setItemNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                setItemNode.execute(frame, self, o, PNone.NO_VALUE);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
            return 0;
        }

        @Specialization(guards = "!isAnySet(self)")
        int add(VirtualFrame frame, Object self, @SuppressWarnings("unused") Object o,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, SystemError, ErrorMessages.EXPECTED_S_NOT_P, "a set object", self);
        }
    }

    @Builtin(name = "_PyBytes_Resize", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PyBytes_Resize extends PythonBinaryBuiltinNode {

        @Specialization(limit = "1")
        int resize(VirtualFrame frame, PBytes self, long newSizeL,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached SequenceStorageNodes.GetItemNode getItemNode,
                        @CachedLibrary("newSizeL") PythonObjectLibrary lib,
                        @Cached CastToByteNode castToByteNode) {

            SequenceStorage storage = self.getSequenceStorage();
            int newSize = lib.asSize(newSizeL);
            int len = lenNode.execute(storage);
            byte[] smaller = new byte[newSize];
            for (int i = 0; i < newSize && i < len; i++) {
                smaller[i] = castToByteNode.execute(frame, getItemNode.execute(frame, storage, i));
            }
            self.setSequenceStorage(new ByteSequenceStorage(smaller));
            return 0;
        }

        @Specialization(guards = "!isBytes(self)")
        int add(VirtualFrame frame, Object self, @SuppressWarnings("unused") Object o,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, SystemError, ErrorMessages.EXPECTED_S_NOT_P, "a set object", self);
        }

    }

    @Builtin(name = "PyTruffle_Compute_Mro", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonTypes.class)
    public abstract static class PyTruffle_Compute_Mro extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNativeObject(self)")
        Object doIt(Object self, String className) {
            PythonAbstractClass[] doSlowPath = TypeNodes.ComputeMroNode.doSlowPath(PythonNativeClass.cast(self));
            return factory().createTuple(new MroSequenceStorage(className, doSlowPath));
        }
    }

    @Builtin(name = "PyTruffle_Type_Modified", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @TypeSystemReference(PythonTypes.class)
    public abstract static class PyTruffle_Type_Modified extends PythonTernaryBuiltinNode {

        @Specialization(guards = {"isNativeClass(clazz)", "isNoValue(mroTuple)"})
        Object doIt(Object clazz, String name, @SuppressWarnings("unused") PNone mroTuple) {
            CyclicAssumption nativeClassStableAssumption = getContext().getNativeClassStableAssumption((PythonNativeClass) clazz, false);
            if (nativeClassStableAssumption != null) {
                nativeClassStableAssumption.invalidate("PyType_Modified(\"" + name + "\") (without MRO) called");
            }
            return PNone.NONE;
        }

        @Specialization(guards = "isNativeClass(clazz)")
        Object doIt(Object clazz, String name, PTuple mroTuple,
                        @Cached("createClassProfile()") ValueProfile profile) {
            CyclicAssumption nativeClassStableAssumption = getContext().getNativeClassStableAssumption((PythonNativeClass) clazz, false);
            if (nativeClassStableAssumption != null) {
                nativeClassStableAssumption.invalidate("PyType_Modified(\"" + name + "\") called");
            }
            SequenceStorage sequenceStorage = profile.profile(mroTuple.getSequenceStorage());
            if (sequenceStorage instanceof MroSequenceStorage) {
                ((MroSequenceStorage) sequenceStorage).lookupChanged();
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
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
        private void decodeUTF8(CharBuffer resultBuffer, ByteBuffer inputBuffer, String errors) {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
            CodingErrorAction action = BytesBuiltins.toCodingErrorAction(errors, this);
            decoder.onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(action).decode(inputBuffer, resultBuffer, true);
        }
    }

    @Builtin(name = "PyTruffle_IsSequence", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffle_IsSequence extends PythonUnaryBuiltinNode {

        @Specialization(limit = "1")
        boolean doGeneric(VirtualFrame frame, Object object,
                        @CachedLibrary("object") PythonObjectLibrary dataModelLibrary,
                        @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            PythonContext context = contextRef.get();
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return dataModelLibrary.isSequence(object);
            } finally {
                IndirectCallContext.exit(frame, context, state);
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
                    return factory().createTuple(new Object[]{doubleValue(parse), pp.getIndex()});
                }
            } else {
                try {
                    Number parse = parse(source);
                    return factory().createTuple(new Object[]{doubleValue(parse)});
                } catch (ParseException e) {
                    // ignore
                }
            }
            return raiseNative(frame, getNativeNullNode.execute(module), PythonBuiltinClassType.ValueError, ErrorMessages.COULD_NOT_CONVERT_STRING_TO_FLOAT, source);
        }

        @TruffleBoundary
        private static double doubleValue(Number parse) {
            return parse.doubleValue();
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

    @Builtin(name = "PyTruffle_OS_DoubleToString", minNumOfPositionalArgs = 5, declaresExplicitSelf = true)
    @GenerateNodeFactory
    @ImportStatic(SpecialMethodNames.class)
    abstract static class PyTruffle_OS_DoubleToString extends NativeBuiltin {

        /* keep in sync with macro 'TRANSLATE_TYPE' in 'pystrtod.c' */
        private static final int Py_DTST_FINITE = 0;
        private static final int Py_DTST_INFINITE = 1;
        private static final int Py_DTST_NAN = 2;

        @Specialization(guards = "isReprFormatCode(formatCode)")
        @SuppressWarnings("unused")
        PTuple doRepr(VirtualFrame frame, Object module, double val, int formatCode, int precision, int flags,
                        @Cached("create(__REPR__)") LookupAndCallUnaryNode callReprNode,
                        @Cached CastToJavaStringNode castToStringNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            Object reprString = callReprNode.executeObject(frame, val);
            try {
                return createResult(new CStringWrapper(castToStringNode.execute(reprString)), val);
            } catch (CannotCastException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException();
            }
        }

        @Specialization(guards = "!isReprFormatCode(formatCode)")
        Object doGeneric(VirtualFrame frame, Object module, double val, int formatCode, int precision, @SuppressWarnings("unused") int flags,
                        @Cached("create(__FORMAT__)") LookupAndCallBinaryNode callReprNode,
                        @Cached CastToJavaStringNode castToStringNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                Object reprString = callReprNode.executeObject(frame, val, joinFormatCode(formatCode, precision));
                try {
                    return createResult(new CStringWrapper(castToStringNode.execute(reprString)), val);
                } catch (CannotCastException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException();
                }
            } catch (PException e) {
                transformToNative(frame, e);
                return getNativeNullNode.execute(module);
            }
        }

        @TruffleBoundary
        private static String joinFormatCode(int formatCode, int precision) {
            return "." + precision + (char) formatCode;
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
                return raiseNative(frame, getNativeNullNode.execute(module), PythonErrorType.LookupError, ErrorMessages.UNKNOWN_ENCODING, encoding);
            } catch (InteropException e) {
                return raiseNative(frame, getNativeNullNode.execute(module), PythonErrorType.TypeError, "%m", e);
            }
        }

        @TruffleBoundary
        private void decode(CharBuffer resultBuffer, ByteBuffer inputBuffer, String encoding, String errors) {
            CharsetDecoder decoder = Charset.forName(encoding).newDecoder();
            CodingErrorAction action = BytesBuiltins.toCodingErrorAction(errors, this);
            decoder.onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(action).decode(inputBuffer, resultBuffer, true);
        }
    }

    @Builtin(name = "PyObject_Size", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @ImportStatic(SpecialMethodNames.class)
    abstract static class PyObject_Size extends PythonUnaryBuiltinNode {

        // n.b.: specializations 'doSequence' and 'doMapping' are not just shortcuts but also
        // required for correctness because CPython's implementation uses
        // 'type->tp_as_sequence->sq_length', 'type->tp_as_mapping->mp_length' which will bypass any
        // user implementation of '__len__'.
        @Specialization
        static int doSequence(PSequence sequence,
                        @Cached SequenceNodes.LenNode seqLenNode) {
            return seqLenNode.execute(sequence);
        }

        @Specialization
        static int doMapping(PHashingCollection container,
                        @Cached HashingCollectionNodes.LenNode seqLenNode) {
            return seqLenNode.execute(container);
        }

        @Specialization(guards = "!isMappingOrSequence(obj)")
        static Object doGenericUnboxed(VirtualFrame frame, Object obj,
                        @Cached("create(__LEN__)") LookupAndCallUnaryNode callLenNode,
                        @Cached("createBinaryProfile()") ConditionProfile noLenProfile,
                        @Cached CastToNativeLongNode castToLongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object result = callLenNode.executeObject(frame, obj);
                if (noLenProfile.profile(result == PNone.NO_VALUE)) {
                    return -1;
                }
                Object lresult = castToLongNode.execute(result);
                assert lresult instanceof Long || lresult instanceof PythonNativeVoidPtr;
                return lresult;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
        }

        protected static boolean isMappingOrSequence(Object obj) {
            return obj instanceof PSequence || obj instanceof PHashingCollection;
        }
    }

    @Builtin(name = "PyObject_Call", parameterNames = {"callee", "args", "kwargs"})
    @GenerateNodeFactory
    @ReportPolymorphism
    abstract static class PyObjectCallNode extends PythonTernaryBuiltinNode {

        @Specialization
        static Object doGeneric(VirtualFrame frame, Object callableObj, Object argsObj, Object kwargsObj,
                        @Cached AsPythonObjectNode callableToJavaNode,
                        @Cached CastArgsNode castArgsNode,
                        @Cached CastKwargsNode castKwargsNode,
                        @Cached CallNode callNode,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached GetNativeNullNode getNativeNullNode,
                        @Cached CExtNodes.ToSulongNode nullToSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {

            try {
                Object callable = callableToJavaNode.execute(callableObj);
                Object[] args = castArgsNode.execute(frame, argsObj);
                PKeyword[] keywords = castKwargsNode.execute(kwargsObj);
                return toNewRefNode.execute(callNode.execute(frame, callable, args, keywords));
            } catch (PException e) {
                // transformExceptionToNativeNode acts as a branch profile
                transformExceptionToNativeNode.execute(frame, e);
                return nullToSulongNode.execute(getNativeNullNode.execute());
            }
        }

    }

    @ReportPolymorphism
    abstract static class CastArgsNode extends Node {

        public abstract Object[] execute(VirtualFrame frame, Object argsObj);

        @Specialization(guards = "lib.isNull(argsObj)")
        @SuppressWarnings("unused")
        static Object[] doNull(VirtualFrame frame, Object argsObj,
                        @Shared("lib") @CachedLibrary(limit = "3") InteropLibrary lib) {
            return new Object[0];
        }

        @Specialization(guards = "!lib.isNull(argsObj)")
        static Object[] doNotNull(VirtualFrame frame, Object argsObj,
                        @Cached ExecutePositionalStarargsNode expandArgsNode,
                        @Cached AsPythonObjectNode argsToJavaNode,
                        @Shared("lib") @CachedLibrary(limit = "3") @SuppressWarnings("unused") InteropLibrary lib) {
            return expandArgsNode.executeWith(frame, argsToJavaNode.execute(argsObj));
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
            return expandKwargsNode.executeWith(kwargsToJavaNode.execute(kwargsObj));
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
                        CastToJavaStringNode castToStringNode,
                        CExtParseArgumentsNode.ParseTupleAndKeywordsNode parseTupleAndKeywordsNode) {

            // force 'format' to be a String
            String[] split;
            try {
                split = splitFormatStringNode.execute(castToStringNode.execute(nativeFormat));
                assert split.length == 2;
            } catch (CannotCastException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException();
            }

            String format = split[0];
            String functionName = split[1];

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
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached SplitFormatStringNode splitFormatStringNode,
                        @CachedLibrary("getKwds(arguments)") InteropLibrary kwdsInteropLib,
                        @CachedLibrary("getKwdnames(arguments)") InteropLibrary kwdnamesRefLib,
                        @Cached("createBinaryProfile()") ConditionProfile kwdsProfile,
                        @Cached("createBinaryProfile()") ConditionProfile kwdnamesProfile,
                        @Cached AsPythonObjectNode argvToJavaNode,
                        @Cached AsPythonObjectNode kwdsToJavaNode,
                        @Cached CastToJavaStringNode castToStringNode,
                        @Cached CExtParseArgumentsNode.ParseTupleAndKeywordsNode parseTupleAndKeywordsNode) {
            CExtContext nativeContext = context.getCApiContext();
            Object argv = argvToJavaNode.execute(arguments[0]);
            return ParseTupleAndKeywordsBaseNode.doConvert(nativeContext, argv, arguments[1], arguments[2], arguments[3], arguments[4], splitFormatStringNode, kwdsInteropLib, kwdnamesRefLib,
                            kwdsProfile, kwdnamesProfile, kwdsToJavaNode, castToStringNode, parseTupleAndKeywordsNode);
        }

    }

    @Builtin(name = "PyTruffle_Arg_ParseTupleAndKeywords_VaList", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class ParseTupleAndKeywordsVaListNode extends ParseTupleAndKeywordsBaseNode {

        @Specialization(guards = "arguments.length == 5", limit = "2")
        static int doConvert(@SuppressWarnings("unused") Object self, Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached SplitFormatStringNode splitFormatStringNode,
                        @CachedLibrary("getKwds(arguments)") InteropLibrary kwdsRefLib,
                        @CachedLibrary("getKwdnames(arguments)") InteropLibrary kwdnamesRefLib,
                        @Cached("createBinaryProfile()") ConditionProfile kwdsProfile,
                        @Cached("createBinaryProfile()") ConditionProfile kwdnamesProfile,
                        @Cached PCallCExtFunction callMallocOutVarPtr,
                        @Cached AsPythonObjectNode argvToJavaNode,
                        @Cached AsPythonObjectNode kwdsToJavaNode,
                        @Cached CastToJavaStringNode castToStringNode,
                        @Cached CExtParseArgumentsNode.ParseTupleAndKeywordsNode parseTupleAndKeywordsNode) {
            CExtContext nativeContext = context.getCApiContext();
            Object argv = argvToJavaNode.execute(arguments[0]);
            VaListWrapper varargs = new VaListWrapper(nativeContext, arguments[4], callMallocOutVarPtr.call(nativeContext, NativeCAPISymbols.FUN_ALLOCATE_OUTVAR));
            return ParseTupleAndKeywordsBaseNode.doConvert(nativeContext, argv, arguments[1], arguments[2], arguments[3], varargs, splitFormatStringNode, kwdsRefLib, kwdnamesRefLib, kwdsProfile,
                            kwdnamesProfile, kwdsToJavaNode, castToStringNode, parseTupleAndKeywordsNode);
        }
    }

    @Builtin(name = "PyTruffle_Create_Lightweight_Upcall", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffleCreateLightweightUpcall extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doGeneric(String key) {
            switch (key) {
                case "PyTruffle_Object_Alloc":
                    return new PyTruffleObjectAlloc();
                case "PyTruffle_Object_Free":
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

        @Specialization(guards = {"domain == cachedDomain"}, limit = "3")
        int doCachedDomainIdx(VirtualFrame frame, @SuppressWarnings("unused") long domain, Object pointerObject, long size,
                        @Cached("domain") @SuppressWarnings("unused") long cachedDomain,
                        @Cached("lookupDomain(domain)") int cachedDomainIdx,
                        @Cached BranchProfile profile) {

            CApiContext cApiContext = getContext().getCApiContext();
            cApiContext.getTraceMallocDomain(cachedDomainIdx).track(pointerObject, size);
            cApiContext.increaseMemoryPressure(frame, size, profile);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(() -> String.format("Tracking memory (size: %d): %s", size, CApiContext.asHex(pointerObject)));
            }
            return 0;
        }

        @Specialization(replaces = "doCachedDomainIdx")
        int doGeneric(VirtualFrame frame, int domain, Object pointerObject, long size,
                        @Cached BranchProfile profile) {
            return doCachedDomainIdx(frame, domain, pointerObject, size, domain, lookupDomain(domain), profile);
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

        @Specialization(guards = {"domain == cachedDomain"}, limit = "3")
        int doCachedDomainIdx(@SuppressWarnings("unused") long domain, Object pointerObject,
                        @Cached("domain") @SuppressWarnings("unused") long cachedDomain,
                        @Cached("lookupDomain(domain)") int cachedDomainIdx) {

            CApiContext cApiContext = getContext().getCApiContext();
            long trackedMemorySize = cApiContext.getTraceMallocDomain(cachedDomainIdx).untrack(pointerObject);
            cApiContext.reduceMemoryPressure(trackedMemorySize);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(() -> String.format("Untracking memory (size: %d): %s", trackedMemorySize, CApiContext.asHex(pointerObject)));
            }
            return 0;
        }

        @Specialization(replaces = "doCachedDomainIdx")
        int doGeneric(int domain, Object pointerObject) {
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
            // TODO(fa): implement; capture tracebacks in PyTraceMalloc_Track and update them here
            return 0;
        }
    }

    abstract static class PyTruffleGcTracingNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = {"!traceCalls(context)", "traceMem(context)"})
        int doNativeWrapper(Object ptr,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context,
                        @Shared("lib") @CachedLibrary(limit = "3") InteropLibrary lib) {
            trace(context, CApiContext.asPointer(ptr, lib), null, null);
            return 0;
        }

        @Specialization(guards = {"traceCalls(context)", "traceMem(context)"})
        int doNativeWrapperTraceCall(VirtualFrame frame, Object ptr,
                        @Cached GetCurrentFrameRef getCurrentFrameRef,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context,
                        @Shared("lib") @CachedLibrary(limit = "3") InteropLibrary lib) {

            PFrame.Reference ref = getCurrentFrameRef.execute(frame);
            trace(context, CApiContext.asPointer(ptr, lib), ref, null);
            return 0;
        }

        @Specialization(guards = "!traceMem(context)")
        static int doNothing(@SuppressWarnings("unused") Object ptr,
                        @Shared("context") @CachedContext(PythonLanguage.class) @SuppressWarnings("unused") PythonContext context) {
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
        protected void trace(PythonContext context, Object ptr, Reference ref, String className) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("should not reach");
        }
    }

    @Builtin(name = "PyTruffle_GC_Untrack", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffleGcUntrack extends PyTruffleGcTracingNode {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(PyTruffleGcUntrack.class);

        @Override
        protected void trace(PythonContext context, Object ptr, Reference ref, String className) {
            LOGGER.fine(() -> String.format("Untracking container object at %s", CApiContext.asHex(ptr)));
            context.getCApiContext().untrackObject(ptr, ref, className);
        }
    }

    @Builtin(name = "PyTruffle_GC_Track", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTruffleGcTrack extends PyTruffleGcTracingNode {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(PyTruffleGcTrack.class);

        @Override
        protected void trace(PythonContext context, Object ptr, Reference ref, String className) {
            LOGGER.fine(() -> String.format("Tracking container object at %s", CApiContext.asHex(ptr)));
            context.getCApiContext().trackObject(ptr, ref, className);
        }
    }

    @Builtin(name = "PyTruffle_Native_Options")
    @GenerateNodeFactory
    abstract static class PyTruffleNativeOptions extends PythonBuiltinNode {
        private static final int TRACE_MEM = 0x1;

        @Specialization
        static int getNativeOptions(
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            int options = 0;
            if (context.getOption(PythonOptions.TraceNativeMemory)) {
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
                        @Cached GetCurrentFrameRef getCurrentFrameRef,
                        @CachedContext(PythonLanguage.class) PythonContext context) {

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
                        LOGGER.finer(() -> String.format("Freeing pointer (size: %d): %s", allocLocation.size, CApiContext.asHex(ptr)));

                        if (traceNativeMemoryCalls) {
                            Reference left = allocLocation.allocationSite;
                            PFrame pyFrame = null;
                            while (pyFrame == null && left != null) {
                                pyFrame = left.getPyFrame();
                                left = left.getCallerInfo();
                            }
                            if (pyFrame != null) {
                                final PFrame f = pyFrame;
                                LOGGER.finer(() -> String.format("Free'd pointer was allocated at: %s", f.getTarget()));
                            }
                        }
                    }
                } else {
                    assert isLoggable;
                    LOGGER.finer(() -> String.format("Freeing pointer: %s", CApiContext.asHex(ptr)));
                }
            }
            return 0;
        }

        @Specialization(limit = "2", replaces = "doNativeWrapperLong")
        static int doNativeWrapper(Object ptr, Object sizeObject,
                        @Cached CastToJavaLongLossyNode castToJavaLongNode,
                        @CachedLibrary("ptr") InteropLibrary lib,
                        @Cached GetCurrentFrameRef getCurrentFrameRef,
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            long size;
            try {
                size = castToJavaLongNode.execute(sizeObject);
            } catch (CannotCastException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalArgumentException("invalid type for second argument 'objectSize'");
            }
            return doNativeWrapperLong(ptr, size, lib, getCurrentFrameRef, context);
        }

    }

    @Builtin(name = "PyTruffle_Trace_Type", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTruffleTraceType extends PythonBinaryBuiltinNode {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(PyTruffleTraceType.class);

        @Specialization(limit = "3")
        int trace(Object ptr, Object classNameObj,
                        @CachedLibrary("ptr") InteropLibrary ptrLib,
                        @CachedLibrary("classNameObj") InteropLibrary nameLib) {
            final String className;
            if (nameLib.isString(classNameObj)) {
                try {
                    className = nameLib.asString(classNameObj);
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
            LOGGER.fine(() -> String.format("Initializing native type %s (ptr = %s)", className, CApiContext.asHex(primitivePtr)));
            return 0;
        }
    }

    @Builtin(name = "PyList_SetItem", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @ImportStatic(CApiGuards.class)
    abstract static class PyListSetItem extends PythonTernaryBuiltinNode {
        @Specialization
        int doManaged(VirtualFrame frame, PythonNativeWrapper listWrapper, Object position, Object elementWrapper,
                        @Cached AsPythonObjectNode listWrapperAsPythonObjectNode,
                        @Cached AsPythonObjectStealingNode elementAsPythonObjectNode,
                        @Cached("createSetItem()") SequenceStorageNodes.SetItemNode setItemNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object delegate = listWrapperAsPythonObjectNode.execute(listWrapper);
                if (!PGuards.isList(delegate)) {
                    throw raise(SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, delegate, delegate);
                }
                PList list = (PList) delegate;
                Object element = elementAsPythonObjectNode.execute(elementWrapper);
                setItemNode.execute(frame, list.getSequenceStorage(), position, element);
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
        }

        protected static SequenceStorageNodes.SetItemNode createSetItem() {
            return SequenceStorageNodes.SetItemNode.create(NormalizeIndexNode.forListAssign(), "invalid item for assignment");
        }
    }

    @Builtin(name = "PySequence_GetItem", minNumOfPositionalArgs = 3, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class PySequenceGetItem extends PythonTernaryBuiltinNode {

        @Specialization
        Object doManaged(VirtualFrame frame, Object module, Object listWrapper, Object position,
                        @Cached LookupInheritedAttributeNode.Dynamic lookupGetItemNode,
                        @Cached CallBinaryMethodNode callGetItemNode,
                        @Cached AsPythonObjectNode listWrapperAsPythonObjectNode,
                        @Cached AsPythonObjectNode positionAsPythonObjectNode,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached GetNativeNullNode getNativeNullNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object delegate = listWrapperAsPythonObjectNode.execute(listWrapper);
                Object attrGetItem = lookupGetItemNode.execute(delegate, __GETITEM__);
                if (attrGetItem == PNone.NO_VALUE) {
                    throw raise(TypeError, ErrorMessages.OBJ_DOES_NOT_SUPPORT_INDEXING, delegate);
                }
                Object item = callGetItemNode.executeObject(frame, attrGetItem, delegate, positionAsPythonObjectNode.execute(position));
                return toNewRefNode.execute(item);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return toNewRefNode.execute(getNativeNullNode.execute(module));
            }
        }
    }

    @Builtin(name = "PyObject_GetItem", minNumOfPositionalArgs = 3, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class PyObjectGetItem extends PythonTernaryBuiltinNode {
        @Specialization
        Object doManaged(VirtualFrame frame, Object module, Object listWrapper, Object position,
                        @Cached LookupInheritedAttributeNode.Dynamic lookupGetItemNode,
                        @Cached CallBinaryMethodNode callGetItemNode,
                        @Cached AsPythonObjectNode listWrapperAsPythonObjectNode,
                        @Cached AsPythonObjectNode positionAsPythonObjectNode,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached GetNativeNullNode getNativeNullNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object delegate = listWrapperAsPythonObjectNode.execute(listWrapper);
                Object attrGetItem = lookupGetItemNode.execute(delegate, __GETITEM__);
                if (attrGetItem == PNone.NO_VALUE) {
                    throw raise(TypeError, ErrorMessages.OBJ_NOT_SUBSCRIPTABLE, delegate);
                }
                Object item = callGetItemNode.executeObject(frame, attrGetItem, delegate, positionAsPythonObjectNode.execute(position));
                return toNewRefNode.execute(item);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return toNewRefNode.execute(getNativeNullNode.execute(module));
            }
        }
    }

    @Builtin(name = "wrap_PyDateTime_CAPI", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class WrapPyDateTimeCAPI extends PythonBuiltinNode {
        @Specialization
        static Object doGeneric(Object object) {
            return new PyDateTimeCAPIWrapper(object);
        }
    }
}
