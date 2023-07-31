/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.hpy;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPySlot.HPY_TP_CALL;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPySlot.HPY_TP_DESTROY;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPySlot.HPY_TP_NEW;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPySlot.HPY_TP_TRAVERSE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyHandle.NULL_HANDLE_DELEGATE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_CALL_FUNCTION_GET_IMPL;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_DEF_GET_GETSET;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_DEF_GET_KIND;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_DEF_GET_MEMBER;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_DEF_GET_METH;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_DEF_GET_SLOT;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_FROM_HPY_MODULE_DEF;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_MEMBER_GET_TYPE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_METH_GET_SIGNATURE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_MODULE_GET_DEFINES;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_MODULE_GET_LEGACY_METHODS;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_MODULE_INIT_GLOBALS;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_SLOT_GET_SLOT;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_TYPE_SPEC_GET_BUILTIN_SHAPE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_TYPE_SPEC_PARAM_GET_KIND;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_TYPE_SPEC_PARAM_GET_OBJECT;
import static com.oracle.graal.python.nodes.StringLiterals.T_DOT;
import static com.oracle.graal.python.nodes.StringLiterals.T_EXEC;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject.PInteropSubscriptNode;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.CreateMethodNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.GetTypeMemberNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.SubRefCntNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeMember;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CByteArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.AsNativePrimitiveNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.ConvertPIntToPrimitiveNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.EncodeNativeStringNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.EnsureTruffleStringNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.ImportCExtSymbolNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.LLVMType;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPyFuncSignature;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPySlot;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPySlotWrapper;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyLegacyDef.HPyLegacySlot;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyMemberAccessNodes.HPyReadMemberNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyMemberAccessNodes.HPyWriteMemberNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAllHandleCloseNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAttachJNIFunctionTypeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAttachNFIFunctionTypeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyGetSetSetterHandleCloseNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyKeywordsHandleCloseNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyRaiseNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyRichcmptFuncArgsCloseNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPySSizeObjArgProcCloseNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPySelfHandleCloseNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyTransformExceptionToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyVarargsHandleCloseNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyObjectBuiltins.HPyObjectNewNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodes.HPyCheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodes.HPyCheckHandleResultNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodes.HPyCheckPrimitiveResultNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodes.HPyGetSetDescriptorGetterRootNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodes.HPyGetSetDescriptorSetterRootNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodes.HPyLegacyGetSetDescriptorGetterRoot;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodes.HPyLegacyGetSetDescriptorSetterRoot;
import com.oracle.graal.python.builtins.objects.cext.hpy.jni.GraalHPyJNIFunctionPointer;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetSuperClassNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.InlinedIsSameTypeNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyTupleSizeNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.frame.GetCurrentFrameRef;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntLossyNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongExactNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.PythonOptions.HPyBackendMode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedExactClassProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.nfi.api.SignatureLibrary;

public abstract class GraalHPyNodes {

    /**
     * A node interface for calling (native) helper functions. The implementation depends on the HPy
     * backend. This is the reason why this node takes the HPy context as construction parameter.
     * The recommended usage of this node is
     * 
     * <pre>
     * &#064;Specialization
     * Object doSomething(GraalHPyContext hpyContext,
     *                 &#064;Cached(parameters = "hpyContext") HPyCallHelperFunctionNode callHelperNode) {
     *     // ...
     * }
     * </pre>
     */
    public abstract static class HPyCallHelperFunctionNode extends Node {
        public final Object call(GraalHPyContext context, GraalHPyNativeSymbol name, Object... args) {
            return execute(context, name, args);
        }

        protected abstract Object execute(GraalHPyContext context, GraalHPyNativeSymbol name, Object[] args);

        public static HPyCallHelperFunctionNode create(GraalHPyContext context) {
            return context.getBackend().createCallHelperFunctionNode();
        }

        public static HPyCallHelperFunctionNode getUncached(GraalHPyContext context) {
            return context.getBackend().getUncachedCallHelperFunctionNode();
        }
    }

    @GenerateUncached
    public abstract static class PCallHPyFunction extends PNodeWithContext {

        public final Object call(GraalHPyContext context, GraalHPyNativeSymbol name, Object... args) {
            return execute(context, name, args);
        }

        public abstract Object execute(GraalHPyContext context, GraalHPyNativeSymbol name, Object[] args);

        @Specialization
        static Object doIt(GraalHPyContext context, GraalHPyNativeSymbol name, Object[] args,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @Cached ImportCExtSymbolNode importCExtSymbolNode,
                        @Cached EnsureTruffleStringNode ensureTruffleStringNode,
                        @Cached PRaiseNode raiseNode) {
            try {
                return ensureTruffleStringNode.execute(interopLibrary.execute(importCExtSymbolNode.execute(context, name), args));
            } catch (UnsupportedTypeException | ArityException e) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, e);
            } catch (UnsupportedMessageException e) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.HPY_CAPI_SYM_NOT_CALLABLE, name);
            }
        }
    }

    /**
     * Use this node to transform an exception to native if a Python exception was thrown during an
     * upcall and before returning to native code. This node will correctly link to the current
     * frame using the frame reference and tries to avoid any materialization of the frame. The
     * exception is then registered in the native context as the current exception.
     */
    @GenerateUncached
    public abstract static class HPyTransformExceptionToNativeNode extends Node {

        public abstract void execute(Frame frame, GraalHPyContext nativeContext, PException e);

        public final void execute(GraalHPyContext nativeContext, PException e) {
            execute(null, nativeContext, e);
        }

        public final void execute(PException e) {
            execute(null, PythonContext.get(this).getHPyContext(), e);
        }

        public static void executeUncached(GraalHPyContext nativeContext, PException e) {
            HPyTransformExceptionToNativeNodeGen.getUncached().execute(nativeContext, e);
        }

        public static void executeUncached(PException e) {
            HPyTransformExceptionToNativeNodeGen.getUncached().execute(PythonContext.get(null).getHPyContext(), e);
        }

        @Specialization
        static void setCurrentException(Frame frame, GraalHPyContext nativeContext, PException e,
                        @Cached GetCurrentFrameRef getCurrentFrameRef,
                        @Cached GetThreadStateNode getThreadStateNode) {
            // TODO connect f_back
            getCurrentFrameRef.execute(frame).markAsEscaped();
            getThreadStateNode.setCurrentException(nativeContext.getContext(), e);
        }
    }

    @GenerateUncached
    public abstract static class HPyRaiseNode extends Node {

        public final int raiseInt(Frame frame, GraalHPyContext nativeContext, int errorValue, PythonBuiltinClassType errType, TruffleString format, Object... arguments) {
            return executeInt(frame, nativeContext, errorValue, errType, format, arguments);
        }

        public final Object raise(Frame frame, GraalHPyContext nativeContext, Object errorValue, PythonBuiltinClassType errType, TruffleString format, Object... arguments) {
            return execute(frame, nativeContext, errorValue, errType, format, arguments);
        }

        public final int raiseIntWithoutFrame(GraalHPyContext nativeContext, int errorValue, PythonBuiltinClassType errType, TruffleString format, Object... arguments) {
            return executeInt(null, nativeContext, errorValue, errType, format, arguments);
        }

        public final Object raiseWithoutFrame(GraalHPyContext nativeContext, Object errorValue, PythonBuiltinClassType errType, TruffleString format, Object... arguments) {
            return execute(null, nativeContext, errorValue, errType, format, arguments);
        }

        public static int raiseIntUncached(GraalHPyContext nativeContext, int errorValue, PythonBuiltinClassType errType, TruffleString format, Object... arguments) {
            return HPyRaiseNodeGen.getUncached().raiseIntWithoutFrame(nativeContext, errorValue, errType, format, arguments);
        }

        public abstract Object execute(Frame frame, GraalHPyContext nativeContext, Object errorValue, PythonBuiltinClassType errType, TruffleString format, Object[] arguments);

        public abstract int executeInt(Frame frame, GraalHPyContext nativeContext, int errorValue, PythonBuiltinClassType errType, TruffleString format, Object[] arguments);

        @Specialization
        static int doInt(Frame frame, GraalHPyContext nativeContext, int errorValue, PythonBuiltinClassType errType, TruffleString format, Object[] arguments,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("transformExceptionToNativeNode") @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                throw raiseNode.execute(raiseNode, errType, PNone.NO_VALUE, format, arguments);
            } catch (PException p) {
                transformExceptionToNativeNode.execute(frame, nativeContext, p);
            }
            return errorValue;
        }

        @Specialization
        static Object doObject(Frame frame, GraalHPyContext nativeContext, Object errorValue, PythonBuiltinClassType errType, TruffleString format, Object[] arguments,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("transformExceptionToNativeNode") @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                throw raiseNode.execute(raiseNode, errType, PNone.NO_VALUE, format, arguments);
            } catch (PException p) {
                transformExceptionToNativeNode.execute(frame, nativeContext, p);
            }
            return errorValue;
        }
    }

    /**
     * A node interface for creating a TruffleString from a {@code char *}. The implementation
     * depends on the HPy backend. This is the reason why this node takes the HPy context as
     * construction parameter. The recommended usage of this node is
     *
     * <pre>
     * &#064;Specialization
     * Object doSomething(GraalHPyContext hpyContext,
     *                 &#064;Cached(parameters = "hpyContext") HPyFromCharPointerNode fromCharPointerNode) {
     *     // ...
     * }
     * </pre>
     */
    public abstract static class HPyFromCharPointerNode extends Node {

        public final TruffleString execute(GraalHPyContext hpyContext, Object charPtr, boolean copy) {
            return execute(hpyContext, charPtr, -1, Encoding.UTF_8, copy);
        }

        public final TruffleString execute(GraalHPyContext hpyContext, Object charPtr, Encoding encoding) {
            return execute(hpyContext, charPtr, -1, encoding, true);
        }

        public abstract TruffleString execute(GraalHPyContext hpyContext, Object charPtr, int n, Encoding encoding, boolean copy);

        public abstract TruffleString execute(GraalHPyContext hpyContext, long charPtr, int n, Encoding encoding, boolean copy);

        public static HPyFromCharPointerNode create(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().createFromCharPointerNode();
        }

        public static HPyFromCharPointerNode getUncached(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().getUncachedFromCharPointerNode();
        }
    }

    /**
     * Creates an HPy module from a module definition structure:
     *
     * <pre>
     * typedef struct {
     *     const char* doc;
     *     HPy_ssize_t size;
     *     cpy_PyMethodDef *legacy_methods;
     *     HPyDef **defines;
     * } HPyModuleDef;
     * </pre>
     */
    @GenerateUncached
    public abstract static class GraalHPyModuleCreate extends Node {

        public abstract Object execute(GraalHPyContext hpyContext, TruffleString mName, Object spec, Object moduleDefPtr);

        @Specialization
        static Object doGeneric(GraalHPyContext hpyContext, TruffleString mName, Object spec, Object moduleDefPtr,
                        @Bind("this") Node inliningTarget,
                        @Cached PythonObjectFactory factory,
                        @Cached PCallHPyFunction callFromHPyModuleDefNode,
                        @Cached PCallHPyFunction callGetterNode,
                        @CachedLibrary(limit = "3") InteropLibrary ptrLib,
                        @CachedLibrary(limit = "3") InteropLibrary valueLib,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached CastToJavaIntLossyNode castToJavaIntNode,
                        @Cached WriteAttributeToObjectNode writeAttrNode,
                        @Cached WriteAttributeToDynamicObjectNode writeAttrToMethodNode,
                        @Cached HPyCreateFunctionNode addFunctionNode,
                        @Cached CreateMethodNode addLegacyMethodNode,
                        @Cached HPyReadSlotNode readSlotNode,
                        @Cached HPyAttachFunctionTypeNode attachFunctionTypeNode,
                        @Cached HPyCheckHandleResultNode checkFunctionResultNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @CachedLibrary(limit = "1") InteropLibrary createLib,
                        @Cached PRaiseNode raiseNode) {

            // call to type the pointer
            Object moduleDef = callFromHPyModuleDefNode.call(hpyContext, GRAAL_HPY_FROM_HPY_MODULE_DEF, moduleDefPtr);

            assert checkLayout(moduleDef);

            TruffleString mDoc;
            try {
                Object docPtr = ptrLib.readMember(moduleDef, "doc");
                if (!ptrLib.isNull(docPtr)) {
                    mDoc = fromCharPointerNode.execute(docPtr);
                } else {
                    mDoc = null;
                }

                Object sizeObj = ptrLib.readMember(moduleDef, "size");
                long size = valueLib.asLong(sizeObj);
                if (size < 0) {
                    throw raiseNode.raise(PythonBuiltinClassType.SystemError, tsLiteral("HPy does not permit HPyModuleDef.size < 0"));
                } else if (size > 0) {
                    throw raiseNode.raise(PythonBuiltinClassType.SystemError, tsLiteral("Module state is not supported yet in HPy, set HPyModuleDef.size = 0 if module state is not needed"));
                }
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, ErrorMessages.CANNOT_CREATE_MODULE_FROM_DEFINITION, e);
            }

            // process HPy module slots
            Object moduleDefinesPtr = callGetterNode.call(hpyContext, GRAAL_HPY_MODULE_GET_DEFINES, moduleDef);
            int nModuleDefines;
            try {
                if (ptrLib.isNull(moduleDefinesPtr)) {
                    nModuleDefines = 0;
                } else if (!ptrLib.hasArrayElements(moduleDefinesPtr)) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw raiseNode.raise(PythonBuiltinClassType.SystemError, ErrorMessages.FIELD_DID_NOT_RETURN_ARRAY, "defines");
                } else {
                    nModuleDefines = PInt.intValueExact(ptrLib.getArraySize(moduleDefinesPtr));
                }
            } catch (UnsupportedMessageException e) {
                // should not happen since we check if 'moduleDefines' has array elements
                throw CompilerDirectives.shouldNotReachHere();
            } catch (OverflowException ex) {
                throw CompilerDirectives.shouldNotReachHere("Count of HPy module defines exceeds maximum");
            }

            int nMethodDefs = 0;
            Object[] methodDefs = new Object[nModuleDefines];

            boolean hasExecutionSlots = false;
            List<Object> executeSlots = new LinkedList<>();
            Object createFunction = null;

            try {
                for (int i = 0; i < nModuleDefines; i++) {
                    Object moduleDefine = ptrLib.readArrayElement(moduleDefinesPtr, i);
                    int kind = castToJavaIntNode.execute(callGetterNode.call(hpyContext, GRAAL_HPY_DEF_GET_KIND, moduleDefine));
                    switch (kind) {
                        case GraalHPyDef.HPY_DEF_KIND_METH:
                            methodDefs[nMethodDefs++] = moduleDefine;
                            break;
                        case GraalHPyDef.HPY_DEF_KIND_SLOT:
                            Object slotDef = callGetterNode.call(hpyContext, GRAAL_HPY_DEF_GET_SLOT, moduleDefine);
                            HPySlotData slotData = readSlotNode.execute(inliningTarget, hpyContext, slotDef);
                            switch (slotData.slot) {
                                case HPY_MOD_CREATE -> {
                                    if (createFunction != null) {
                                        throw raiseNode.raise(PythonErrorType.SystemError, ErrorMessages.MODULE_HAS_MULTIPLE_CREATE_SLOTS, mName);
                                    }
                                    createFunction = slotData.impl;
                                    assert createLib.isExecutable(createFunction);
                                }
                                case HPY_MOD_EXEC -> {
                                    /*
                                     * In contrast to CPython, we already parse and store the
                                     * HPy_mod_exec slots here since parsing is a bit more expensive
                                     * in our case.
                                     */
                                    Object execFunction = slotData.impl;
                                    assert InteropLibrary.getUncached().isExecutable(execFunction);
                                    executeSlots.add(execFunction);
                                }
                                default -> throw raiseNode.raise(PythonErrorType.SystemError, ErrorMessages.MODULE_USES_UNKNOW_SLOT_ID, mName, slotData.slot);
                            }
                            break;
                        case GraalHPyDef.HPY_DEF_KIND_MEMBER:
                        case GraalHPyDef.HPY_DEF_KIND_GETSET:
                            // silently ignore
                            // TODO(fa): maybe we should log a warning
                            break;
                        default:
                            assert false : "unknown definition kind";
                    }
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                // should not happen since we check if 'moduleDefines' has array elements
                throw CompilerDirectives.shouldNotReachHere();
            }
            // create the module object
            Object module;
            if (createFunction != null) {
                module = callCreate(inliningTarget, createFunction, hpyContext, spec, checkFunctionResultNode, asHandleNode, createLib);
                if (module instanceof PythonModule) {
                    throw raiseNode.raise(SystemError, ErrorMessages.HPY_MOD_CREATE_RETURNED_BUILTIN_MOD);
                }
            } else {
                PythonModule pmodule = factory.createPythonModule(mName);
                pmodule.setNativeModuleDef(executeSlots);
                module = pmodule;
            }

            // process HPy methods
            for (int i = 0; i < nMethodDefs; i++) {
                Object methodDef = callGetterNode.call(hpyContext, GRAAL_HPY_DEF_GET_METH, methodDefs[i]);
                PBuiltinFunction fun = addFunctionNode.execute(hpyContext, null, methodDef);
                PBuiltinMethod method = factory.createBuiltinMethod(module, fun);
                writeAttrToMethodNode.execute(method, SpecialAttributeNames.T___MODULE__, mName);
                writeAttrNode.execute(module, fun.getName(), method);
            }

            // process legacy methods
            Object legacyMethods = callGetterNode.call(hpyContext, GRAAL_HPY_MODULE_GET_LEGACY_METHODS, moduleDef);
            // the field 'legacy_methods' may be 'NULL'
            if (!ptrLib.isNull(legacyMethods)) {
                if (!ptrLib.hasArrayElements(legacyMethods)) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw raiseNode.raise(PythonBuiltinClassType.SystemError, ErrorMessages.FIELD_DID_NOT_RETURN_ARRAY, "legacyMethods");
                }

                try {
                    long nLegacyMethods = ptrLib.getArraySize(legacyMethods);
                    CApiContext capiContext = nLegacyMethods > 0 ? PythonContext.get(ptrLib).getCApiContext() : null;
                    for (long i = 0; i < nLegacyMethods; i++) {
                        Object legacyMethod = ptrLib.readArrayElement(legacyMethods, i);

                        PBuiltinFunction fun = addLegacyMethodNode.execute(capiContext, legacyMethod);
                        PBuiltinMethod method = factory.createBuiltinMethod(module, fun);
                        writeAttrToMethodNode.execute(method.getStorage(), SpecialAttributeNames.T___MODULE__, mName);
                        writeAttrNode.execute(module, fun.getName(), method);
                    }
                } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                    // should not happen since we check if 'legacyMethods' has array
                    // elements
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }

            // allocate module's HPyGlobals
            try {
                int globalStartIdx = hpyContext.getEndIndexOfGlobalTable();
                int nModuleGlobals = ptrLib.asInt(callGetterNode.call(hpyContext, GRAAL_HPY_MODULE_INIT_GLOBALS, moduleDef, globalStartIdx));
                hpyContext.initBatchGlobals(globalStartIdx, nModuleGlobals);
            } catch (UnsupportedMessageException e) {
                // should not happen unless the number of module global is larger than an `int`
                throw CompilerDirectives.shouldNotReachHere();
            }

            if (mDoc != null) {
                writeAttrNode.execute(module, SpecialAttributeNames.T___DOC__, mDoc);
            }

            return module;
        }

        @TruffleBoundary
        private static boolean checkLayout(Object moduleDef) {
            String[] members = new String[]{"doc", "size", "legacy_methods", "defines"};
            InteropLibrary lib = InteropLibrary.getUncached(moduleDef);
            for (String member : members) {
                if (!lib.isMemberReadable(moduleDef, member)) {
                    return false;
                }
            }
            return true;
        }

        private static final TruffleString CREATE = tsLiteral("create");

        /**
         * Call the create slot function.
         * 
         * TODO(fa): This method shares some logic with
         * {@link com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodes.HPyExternalFunctionInvokeNode}.
         * We should refactor the node such that we can use it here.
         */
        static Object callCreate(Node inliningTarget, Object callable, GraalHPyContext hPyContext, Object spec,
                        HPyCheckFunctionResultNode checkFunctionResultNode, HPyAsHandleNode asHandleNode, InteropLibrary lib) {

            PythonLanguage language = PythonLanguage.get(inliningTarget);
            PythonContext ctx = hPyContext.getContext();
            PythonThreadState pythonThreadState = ctx.getThreadState(language);

            GraalHPyHandle hSpec = asHandleNode.execute(spec);
            try {
                return checkFunctionResultNode.execute(pythonThreadState, CREATE, lib.execute(callable, hPyContext.getBackend(), hSpec));
            } catch (UnsupportedTypeException | UnsupportedMessageException e) {
                throw PRaiseNode.raiseUncached(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.CALLING_NATIVE_FUNC_FAILED, CREATE, e);
            } catch (ArityException e) {
                throw PRaiseNode.raiseUncached(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.CALLING_NATIVE_FUNC_EXPECTED_ARGS, CREATE, e.getExpectedMinArity(), e.getActualArity());
            } finally {
                // close all handles (if necessary)
                hSpec.closeAndInvalidate(hPyContext);
            }
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GraalHPyModuleExecNode extends Node {

        public abstract void execute(Node node, GraalHPyContext hpyContext, PythonModule module);

        @Specialization
        static void doGeneric(Node node, GraalHPyContext hpyContext, PythonModule module,
                        @Cached HPyCheckPrimitiveResultNode checkFunctionResultNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @CachedLibrary(limit = "1") InteropLibrary lib) {
            // TODO(fa): once we support HPy module state, we need to allocate it here
            Object execSlotsObj = module.getNativeModuleDef();
            if (execSlotsObj instanceof LinkedList<?> execSlots) {
                for (Object execSlot : execSlots) {
                    callExec(node, hpyContext, execSlot, module, checkFunctionResultNode, asHandleNode, lib);
                }
            }
        }

        /**
         * Call the exec slot function.
         * <p>
         * TODO(fa): This method shares some logic with
         * {@link com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodes.HPyExternalFunctionInvokeNode}.
         * We should refactor the node such that we can use it here.
         * </p>
         */
        static void callExec(Node node, GraalHPyContext hPyContext, Object callable, PythonModule module,
                        HPyCheckPrimitiveResultNode checkFunctionResultNode, HPyAsHandleNode asHandleNode, InteropLibrary lib) {

            PythonLanguage language = PythonLanguage.get(node);
            PythonContext ctx = hPyContext.getContext();
            PythonThreadState pythonThreadState = ctx.getThreadState(language);

            GraalHPyHandle hModule = asHandleNode.execute(module);
            try {
                checkFunctionResultNode.execute(pythonThreadState, T_EXEC, lib.execute(callable, hPyContext.getBackend(), hModule));
            } catch (UnsupportedTypeException | UnsupportedMessageException e) {
                throw PRaiseNode.raiseUncached(node, PythonBuiltinClassType.TypeError, ErrorMessages.CALLING_NATIVE_FUNC_FAILED, T_EXEC, e);
            } catch (ArityException e) {
                throw PRaiseNode.raiseUncached(node, PythonBuiltinClassType.TypeError, ErrorMessages.CALLING_NATIVE_FUNC_EXPECTED_ARGS, T_EXEC, e.getExpectedMinArity(), e.getActualArity());
            } finally {
                // close all handles (if necessary)
                hModule.closeAndInvalidate(hPyContext);
            }
        }
    }

    /**
     * <pre>
     *     typedef struct {
     *         const char *name;             // The name of the built-in function/method
     *         const char *doc;              // The __doc__ attribute, or NULL
     *         void *impl;                   // Function pointer to the implementation
     *         void *cpy_trampoline;         // Used by CPython to call impl
     *         HPyFunc_Signature signature;  // Indicates impl's expected the signature
     *     } HPyMeth;
     * </pre>
     */
    @GenerateUncached
    public abstract static class HPyCreateFunctionNode extends PNodeWithContext {

        public abstract PBuiltinFunction execute(GraalHPyContext context, Object enclosingType, Object methodDef);

        @Specialization(limit = "1")
        static PBuiltinFunction doIt(GraalHPyContext context, Object enclosingType, Object methodDef,
                        @CachedLibrary("methodDef") InteropLibrary interopLibrary,
                        @CachedLibrary(limit = "2") InteropLibrary resultLib,
                        @Cached PCallHPyFunction callHelperFunctionNode,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached HPyAttachFunctionTypeNode attachFunctionTypeNode,
                        @Cached PythonObjectFactory factory,
                        @Cached WriteAttributeToDynamicObjectNode writeAttributeToDynamicObjectNode,
                        @Cached PRaiseNode raiseNode) {
            assert checkLayout(methodDef);

            TruffleString methodName = castToTruffleStringNode.execute(callHelperFunctionNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_GET_ML_NAME, methodDef));

            // note: 'ml_doc' may be NULL; in this case, we would store 'None'
            Object methodDoc = PNone.NONE;
            try {
                Object doc = interopLibrary.readMember(methodDef, "doc");
                if (!resultLib.isNull(doc)) {
                    methodDoc = fromCharPointerNode.execute(doc, false);
                }
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                // fall through
            }

            Object methodSignatureObj;
            HPyFuncSignature signature;
            Object methodFunctionPointer;
            try {
                methodSignatureObj = callHelperFunctionNode.call(context, GRAAL_HPY_METH_GET_SIGNATURE, methodDef);
                if (!resultLib.fitsInInt(methodSignatureObj)) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw raiseNode.raise(PythonBuiltinClassType.SystemError, ErrorMessages.SIG_OF_S_IS_NOT_INT, methodName);
                }
                signature = HPyFuncSignature.fromValue(resultLib.asInt(methodSignatureObj));
                if (signature == null) {
                    throw raiseNode.raise(PythonBuiltinClassType.ValueError, ErrorMessages.UNSUPPORTED_HYPMETH_SIG);
                }

                methodFunctionPointer = interopLibrary.readMember(methodDef, "impl");
                if (context.isDebugMode() || !resultLib.isExecutable(methodFunctionPointer)) {
                    methodFunctionPointer = attachFunctionTypeNode.execute(context, methodFunctionPointer, signature.getLLVMFunctionType());
                }
            } catch (UnknownIdentifierException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, ErrorMessages.INVALID_STRUCT_MEMBER, e.getUnknownIdentifier());
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_ACCESS_STRUCT_MEMBER_FLAGS_OR_METH);
            }

            PBuiltinFunction function = HPyExternalFunctionNodes.createWrapperFunction(PythonLanguage.get(raiseNode), context, signature, methodName, methodFunctionPointer, enclosingType, factory);

            // write doc string; we need to directly write to the storage otherwise it is
            // disallowed writing to builtin types.
            writeAttributeToDynamicObjectNode.execute(function.getStorage(), SpecialAttributeNames.T___DOC__, methodDoc);

            return function;
        }

        @TruffleBoundary
        private static boolean checkLayout(Object methodDef) {
            String[] members = new String[]{"name", "doc", "impl", "cpy_trampoline", "signature"};
            InteropLibrary lib = InteropLibrary.getUncached(methodDef);
            for (String member : members) {
                if (!lib.isMemberReadable(methodDef, member)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Parses a pointer to a {@code PyGetSetDef} struct and creates the corresponding property.
     *
     * <pre>
     *     typedef struct PyGetSetDef {
     *         const char *name;
     *         getter get;
     *         setter set;
     *         const char *doc;
     *         void *closure;
     * } PyGetSetDef;
     * </pre>
     */
    @GenerateUncached
    public abstract static class HPyAddLegacyGetSetDefNode extends PNodeWithContext {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(HPyAddLegacyGetSetDefNode.class);

        public abstract GetSetDescriptor execute(GraalHPyContext context, Object owner, Object legacyGetSetDef);

        @Specialization(limit = "1")
        static GetSetDescriptor doGeneric(GraalHPyContext context, Object owner, Object legacyGetSetDef,
                        @CachedLibrary("legacyGetSetDef") InteropLibrary interopLibrary,
                        @CachedLibrary(limit = "2") InteropLibrary resultLib,
                        @Cached PCallHPyFunction callGetNameNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached PythonObjectFactory factory,
                        @Cached WriteAttributeToDynamicObjectNode writeDocNode,
                        @Cached PRaiseNode raiseNode) {

            assert checkLayout(legacyGetSetDef) : "provided pointer has unexpected structure";

            TruffleString getSetDescrName = castToTruffleStringNode.execute(callGetNameNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_LEGACY_GETSETDEF_GET_NAME, legacyGetSetDef));

            // note: 'doc' may be NULL; in this case, we would store 'None'
            Object getSetDescrDoc = PNone.NONE;
            try {
                Object getSetDocPtr = interopLibrary.readMember(legacyGetSetDef, "doc");
                if (!resultLib.isNull(getSetDocPtr)) {
                    getSetDescrDoc = fromCharPointerNode.execute(getSetDocPtr, false);
                }
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                // fall through
            }

            Object getterFunPtr;
            Object setterFunPtr;
            Object closurePtr;
            boolean hasGetter;
            boolean hasSetter;
            try {
                getterFunPtr = interopLibrary.readMember(legacyGetSetDef, "get");
                hasGetter = !resultLib.isNull(getterFunPtr);
                if (hasGetter && !resultLib.isExecutable(getterFunPtr)) {
                    LOGGER.warning(() -> String.format("get of %s is not callable", getSetDescrName));
                }
                setterFunPtr = interopLibrary.readMember(legacyGetSetDef, "set");
                hasSetter = !resultLib.isNull(setterFunPtr);
                if (hasSetter && !resultLib.isExecutable(setterFunPtr)) {
                    LOGGER.warning(() -> String.format("set of %s is not callable", getSetDescrName));
                }
                closurePtr = interopLibrary.readMember(legacyGetSetDef, "closure");
            } catch (UnknownIdentifierException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, ErrorMessages.INVALID_STRUCT_MEMBER, e.getUnknownIdentifier());
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_ACCESS_STRUCT_MEMBER_FLAGS_OR_METH);
            }

            PythonLanguage lang = PythonLanguage.get(raiseNode);
            PBuiltinFunction getterObject;
            if (hasGetter) {
                getterObject = HPyLegacyGetSetDescriptorGetterRoot.createLegacyFunction(context, lang, owner, getSetDescrName, getterFunPtr, closurePtr);
            } else {
                getterObject = null;
            }

            PBuiltinFunction setterObject;
            if (hasSetter) {
                setterObject = HPyLegacyGetSetDescriptorSetterRoot.createLegacyFunction(context, lang, owner, getSetDescrName, setterFunPtr, closurePtr);
            } else {
                setterObject = null;
            }

            GetSetDescriptor getSetDescriptor = factory.createGetSetDescriptor(getterObject, setterObject, getSetDescrName, owner, hasSetter);
            writeDocNode.execute(getSetDescriptor, SpecialAttributeNames.T___DOC__, getSetDescrDoc);
            return getSetDescriptor;
        }

        @TruffleBoundary
        private static boolean checkLayout(Object methodDef) {
            String[] members = new String[]{"name", "get", "set", "doc", "closure"};
            InteropLibrary lib = InteropLibrary.getUncached(methodDef);
            for (String member : members) {
                if (!lib.isMemberReadable(methodDef, member)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * A simple helper class to return the property and its name separately.
     */
    @ValueType
    static final class HPyProperty {
        final Object key;
        final Object value;

        /**
         * In a very few cases, a single definition can define several properties. For example, slot
         * {@link HPySlot#HPY_SQ_ASS_ITEM} defines properties
         * {@link com.oracle.graal.python.nodes.SpecialMethodNames#T___SETITEM__} and
         * {@link com.oracle.graal.python.nodes.SpecialMethodNames#T___DELITEM__}. Therefore, we use
         * this field to create a linked list of such related properties.
         */
        final HPyProperty next;

        HPyProperty(Object key, Object value, HPyProperty next) {
            this.key = key;
            this.value = value;
            this.next = next;
        }

        HPyProperty(Object key, Object value) {
            this(key, value, null);
        }

        void write(WriteAttributeToObjectNode writeAttributeToObjectNode, ReadAttributeFromObjectNode readAttributeFromObjectNode, Object enclosingType) {
            for (HPyProperty prop = this; prop != null; prop = prop.next) {
                /*
                 * Do not overwrite existing attributes. Reason: Different slots may map to the same
                 * magic method. For example: 'nb_add' and 'sq_concat' are both mapped to '__add__'.
                 * For now, we will always use the first mapping. However, that is not fully
                 * correct. CPython has a fixed order for slots defined by array 'static slotdef
                 * slotdefs[]' in 'typeobject.c'. They iterate over this array and check if the new
                 * type provides the slot. The first mapping will then be install. The problem is
                 * that we cannot easily do the same since we have two separate sets of slots: HPy
                 * slots and legacy slots. Right now, the HPy slots have precedence.
                 */
                if (!keyExists(readAttributeFromObjectNode, enclosingType, prop.key)) {
                    writeAttributeToObjectNode.execute(enclosingType, prop.key, prop.value);
                }
            }
        }

        static boolean keyExists(ReadAttributeFromObjectNode readAttributeFromObjectNode, Object enclosingType, Object key) {
            return readAttributeFromObjectNode.execute(enclosingType, key) != PNone.NO_VALUE;
        }

    }

    /**
     * A simple helper class to return the parsed data of an {@code HPySlot} structure.
     *
     * <pre>
     * typedef struct {
     *     HPySlot_Slot slot;     // The slot to fill
     *     void *impl;            // Function pointer to the implementation
     *     void *cpy_trampoline;  // Used by CPython to call impl
     * } HPySlot;
     * </pre>
     */
    @ValueType
    record HPySlotData(HPySlot slot, Object impl) {
    }

    @GenerateUncached
    public abstract static class HPyCreateLegacyMemberNode extends PNodeWithContext {

        public abstract HPyProperty execute(Object enclosingType, Object memberDef);

        /**
         * <pre>
         * typedef struct PyMemberDef {
         *     const char *name;
         *     int type;
         *     Py_ssize_t offset;
         *     int flags;
         *     const char *doc;
         * } PyMemberDef;
         * </pre>
         */
        @Specialization(limit = "1")
        static HPyProperty doIt(Object enclosingType, Object memberDef,
                        @CachedLibrary("memberDef") InteropLibrary interopLibrary,
                        @CachedLibrary(limit = "2") InteropLibrary valueLib,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached PythonObjectFactory factory,
                        @Cached WriteAttributeToDynamicObjectNode writeDocNode,
                        @Cached PRaiseNode raiseNode) {

            assert interopLibrary.hasMembers(memberDef);
            assert interopLibrary.isMemberReadable(memberDef, "name");
            assert interopLibrary.isMemberReadable(memberDef, "type");
            assert interopLibrary.isMemberReadable(memberDef, "offset");
            assert interopLibrary.isMemberReadable(memberDef, "flags");
            assert interopLibrary.isMemberReadable(memberDef, "doc");

            try {
                TruffleString name = fromCharPointerNode.execute(interopLibrary.readMember(memberDef, "name"));

                // note: 'doc' may be NULL; in this case, we would store 'None'
                Object memberDoc = PNone.NONE;
                Object doc = interopLibrary.readMember(memberDef, "doc");
                if (!valueLib.isNull(doc)) {
                    memberDoc = fromCharPointerNode.execute(doc, false);
                }

                int flags = valueLib.asInt(interopLibrary.readMember(memberDef, "flags"));
                int type = valueLib.asInt(interopLibrary.readMember(memberDef, "type"));
                int offset = valueLib.asInt(interopLibrary.readMember(memberDef, "offset"));

                PythonLanguage language = PythonLanguage.get(raiseNode);
                PBuiltinFunction getterObject = HPyReadMemberNode.createBuiltinFunction(language, name, type, offset);

                Object setterObject = null;
                if ((flags & GraalHPyLegacyDef.MEMBER_FLAG_READONLY) == 0) {
                    setterObject = HPyWriteMemberNode.createBuiltinFunction(language, name, type, offset);
                }

                // create a property
                GetSetDescriptor memberDescriptor = factory.createMemberDescriptor(getterObject, setterObject, name, enclosingType);
                writeDocNode.execute(memberDescriptor, SpecialAttributeNames.T___DOC__, memberDoc);
                return new HPyProperty(name, memberDescriptor);
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, ErrorMessages.CANNOT_READ_FIELD_NAME_FROM_MEMBER_DEF);
            }
        }
    }

    @GenerateUncached
    public abstract static class HPyAddMemberNode extends PNodeWithContext {

        public abstract HPyProperty execute(GraalHPyContext context, Object enclosingType, Object memberDef);

        /**
         * <pre>
         * typedef struct {
         *     const char *name;
         *     HPyMember_FieldType type;
         *     HPy_ssize_t offset;
         *     int readonly;
         *     const char *doc;
         * } HPyMember;
         * </pre>
         */
        @Specialization(limit = "1")
        static HPyProperty doIt(GraalHPyContext context, Object enclosingType, Object memberDef,
                        @CachedLibrary("memberDef") InteropLibrary interopLibrary,
                        @CachedLibrary(limit = "2") InteropLibrary valueLib,
                        @Cached PCallHPyFunction callHelperNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached PythonObjectFactory factory,
                        @Cached WriteAttributeToDynamicObjectNode writeDocNode,
                        @Cached PRaiseNode raiseNode) {

            assert interopLibrary.hasMembers(memberDef);
            assert interopLibrary.isMemberReadable(memberDef, "name");
            assert interopLibrary.isMemberReadable(memberDef, "type");
            assert interopLibrary.isMemberReadable(memberDef, "offset");
            assert interopLibrary.isMemberReadable(memberDef, "readonly");
            assert interopLibrary.isMemberReadable(memberDef, "doc");

            try {
                TruffleString name = fromCharPointerNode.execute(interopLibrary.readMember(memberDef, "name"));

                // note: 'doc' may be NULL; in this case, we would store 'None'
                Object memberDoc = PNone.NONE;
                Object doc = interopLibrary.readMember(memberDef, "doc");
                if (!valueLib.isNull(doc)) {
                    memberDoc = fromCharPointerNode.execute(doc, false);
                }

                int type = valueLib.asInt(callHelperNode.call(context, GRAAL_HPY_MEMBER_GET_TYPE, memberDef));
                boolean readOnly = valueLib.asInt(interopLibrary.readMember(memberDef, "readonly")) != 0;
                int offset = valueLib.asInt(interopLibrary.readMember(memberDef, "offset"));

                PythonLanguage language = PythonLanguage.get(raiseNode);
                PBuiltinFunction getterObject = HPyReadMemberNode.createBuiltinFunction(language, name, type, offset);

                Object setterObject = null;
                if (!readOnly) {
                    setterObject = HPyWriteMemberNode.createBuiltinFunction(language, name, type, offset);
                }

                // create member descriptor
                GetSetDescriptor memberDescriptor = factory.createMemberDescriptor(getterObject, setterObject, name, enclosingType);
                writeDocNode.execute(memberDescriptor, SpecialAttributeNames.T___DOC__, memberDoc);
                return new HPyProperty(name, memberDescriptor);
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, ErrorMessages.CANNOT_READ_FIELD_NAME_FROM_MEMBER_DEF);
            }
        }

    }

    /**
     * Creates a get/set descriptor from an HPy get/set descriptor specification.
     *
     * <pre>
     * typedef struct {
     *     const char *name;
     *     void *getter_impl;            // Function pointer to the implementation
     *     void *setter_impl;            // Same; this may be NULL
     *     void *getter_cpy_trampoline;  // Used by CPython to call getter_impl
     *     void *setter_cpy_trampoline;  // Same; this may be NULL
     *     const char *doc;
     *     void *closure;
     * } HPyGetSet;
     * </pre>
     */
    @GenerateUncached
    public abstract static class HPyCreateGetSetDescriptorNode extends PNodeWithContext {

        public abstract GetSetDescriptor execute(GraalHPyContext context, Object type, Object memberDef);

        @Specialization(limit = "1")
        static GetSetDescriptor doIt(GraalHPyContext context, Object type, Object memberDef,
                        @CachedLibrary("memberDef") InteropLibrary memberDefLib,
                        @CachedLibrary(limit = "2") InteropLibrary valueLib,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached HPyAttachFunctionTypeNode attachFunctionTypeNode,
                        @Cached PythonObjectFactory factory,
                        @Cached WriteAttributeToDynamicObjectNode writeDocNode,
                        @Cached PRaiseNode raiseNode) {

            assert memberDefLib.hasMembers(memberDef);
            assert memberDefLib.isMemberReadable(memberDef, "name");
            assert memberDefLib.isMemberReadable(memberDef, "getter_impl");
            assert memberDefLib.isMemberReadable(memberDef, "setter_impl");
            assert memberDefLib.isMemberReadable(memberDef, "doc");
            assert memberDefLib.isMemberReadable(memberDef, "closure");

            try {
                TruffleString name = fromCharPointerNode.execute(memberDefLib.readMember(memberDef, "name"));

                // note: 'doc' may be NULL; in this case, we would store 'None'
                Object memberDoc = PNone.NONE;
                Object docCharPtr = memberDefLib.readMember(memberDef, "doc");
                if (!valueLib.isNull(docCharPtr)) {
                    memberDoc = fromCharPointerNode.execute(docCharPtr, false);
                }

                Object closurePtr = memberDefLib.readMember(memberDef, "closure");

                // signature: self, closure
                Object getterFunctionPtr = memberDefLib.readMember(memberDef, "getter_impl");
                boolean hasGetter = !valueLib.isNull(getterFunctionPtr);
                if (hasGetter && (context.isDebugMode() || !valueLib.isExecutable(getterFunctionPtr))) {
                    getterFunctionPtr = attachFunctionTypeNode.execute(context, getterFunctionPtr, LLVMType.HPyFunc_getter);
                }

                // signature: self, value, closure
                Object setterFunctionPtr = memberDefLib.readMember(memberDef, "setter_impl");
                boolean hasSetter = !valueLib.isNull(setterFunctionPtr);
                if (hasSetter && (context.isDebugMode() || !valueLib.isExecutable(setterFunctionPtr))) {
                    setterFunctionPtr = attachFunctionTypeNode.execute(context, setterFunctionPtr, LLVMType.HPyFunc_setter);
                }

                PBuiltinFunction getterObject;
                if (hasGetter) {
                    getterObject = HPyGetSetDescriptorGetterRootNode.createFunction(context, type, name, getterFunctionPtr, closurePtr);
                } else {
                    getterObject = null;
                }

                PBuiltinFunction setterObject;
                if (hasSetter) {
                    setterObject = HPyGetSetDescriptorSetterRootNode.createFunction(context, type, name, setterFunctionPtr, closurePtr);
                } else {
                    setterObject = null;
                }

                GetSetDescriptor getSetDescriptor = factory.createGetSetDescriptor(getterObject, setterObject, name, type, !hasSetter);
                writeDocNode.execute(getSetDescriptor, SpecialAttributeNames.T___DOC__, memberDoc);
                return getSetDescriptor;
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, ErrorMessages.CANNOT_READ_FIELD_NAME_FROM_MEMBER_DEF);
            }
        }
    }

    /**
     * Parser an {@code HPySlot} structure, creates and adds the appropriate function as magic
     * method. Returns either an HPyProperty if created, or the HPySlot itself.
     *
     * <pre>
     * typedef struct {
     *     HPySlot_Slot slot;     // The slot to fill
     *     void *impl;            // Function pointer to the implementation
     *     void *cpy_trampoline;  // Used by CPython to call impl
     * } HPySlot;
     * </pre>
     */
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class HPyReadSlotNode extends PNodeWithContext {

        public abstract HPySlotData execute(Node inliningTarget, GraalHPyContext context, Object slotDef);

        @Specialization(limit = "1")
        static HPySlotData doIt(Node inliningTarget, GraalHPyContext context, Object slotDef,
                        @CachedLibrary("slotDef") InteropLibrary interopLibrary,
                        @CachedLibrary(limit = "2") InteropLibrary resultLib,
                        @Cached PCallHPyFunction callHelperFunctionNode,
                        @Cached HPyAttachFunctionTypeNode attachFunctionTypeNode) {
            assert checkLayout(slotDef);

            int slotNr;
            Object slotObj = callHelperFunctionNode.call(context, GRAAL_HPY_SLOT_GET_SLOT, slotDef);
            if (resultLib.fitsInInt(slotObj)) {
                try {
                    slotNr = resultLib.asInt(slotObj);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseUncached(inliningTarget, PythonBuiltinClassType.SystemError, ErrorMessages.FIELD_SLOT_S_ISNT_INT, slotDef);
            }

            HPySlot slot = HPySlot.fromValue(slotNr);
            if (slot == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseUncached(inliningTarget, PythonBuiltinClassType.SystemError, ErrorMessages.INVALID_SLOT_VALUE, slotNr);
            }

            // read and check the function pointer
            Object methodFunctionPointer;
            try {
                methodFunctionPointer = interopLibrary.readMember(slotDef, "impl");
                if (context.isDebugMode() || !resultLib.isExecutable(methodFunctionPointer)) {
                    methodFunctionPointer = attachFunctionTypeNode.execute(context, methodFunctionPointer, slot.getSignatures()[0].getLLVMFunctionType());
                }
            } catch (UnknownIdentifierException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseUncached(inliningTarget, PythonBuiltinClassType.SystemError, ErrorMessages.INVALID_STRUCT_MEMBER, e.getUnknownIdentifier());
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseUncached(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_ACCESS_STRUCT_MEMBER_FLAGS_OR_METH);
            }

            return new HPySlotData(slot, methodFunctionPointer);
        }

        @TruffleBoundary
        private static boolean checkLayout(Object slotDef) {
            String[] members = new String[]{"slot", "impl", "cpy_trampoline"};
            InteropLibrary lib = InteropLibrary.getUncached(slotDef);
            for (String member : members) {
                if (!lib.isMemberReadable(slotDef, member)) {
                    return false;
                }
            }
            return true;
        }
    }

    @GenerateUncached
    public abstract static class HPyCreateSlotNode extends PNodeWithContext {

        public abstract Object execute(GraalHPyContext context, PythonClass enclosingType, Object slotDef);

        @Specialization
        static Object doIt(GraalHPyContext context, PythonClass enclosingType, Object slotDef,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyReadSlotNode readSlotNode,
                        @Cached PythonObjectFactory factory,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                        @Cached PRaiseNode raiseNode) {

            assert enclosingType.isHPyType();
            HPySlotData slotData = readSlotNode.execute(inliningTarget, context, slotDef);
            HPySlot slot = slotData.slot;
            HPyProperty property = null;
            Object[] methodNames = slot.getAttributeKeys();
            HPySlotWrapper[] slotWrappers = slot.getSignatures();

            /*
             * Special case: DESTROYFUNC. This won't be usable from Python, so we just store the
             * bare pointer object into Java field.
             */
            if (HPY_TP_DESTROY.equals(slot)) {
                enclosingType.setHPyDestroyFunc(slotData.impl());
            } else if (HPY_TP_TRAVERSE.equals(slot)) {
                assert methodNames.length == 0;
                return HPY_TP_TRAVERSE;
            } else {
                // create properties
                for (int i = 0; i < methodNames.length; i++) {
                    Object methodName;
                    TruffleString methodNameStr;
                    if (methodNames[i] instanceof HiddenKey) {
                        methodNameStr = fromJavaStringNode.execute(((HiddenKey) methodNames[i]).getName(), TS_ENCODING);
                        methodName = methodNames[i];
                    } else {
                        methodNameStr = (TruffleString) methodNames[i];
                        methodName = methodNameStr;
                    }
                    HPySlotWrapper slotWrapper = slotWrappers[i];

                    Object enclosingTypeForFun = HPY_TP_NEW.equals(slot) ? null : enclosingType;
                    PythonLanguage language = PythonLanguage.get(raiseNode);
                    Object function = HPyExternalFunctionNodes.createWrapperFunction(language, context, slotWrapper, methodNameStr, slotData.impl(), enclosingTypeForFun, factory);
                    property = new HPyProperty(methodName, function, property);
                }
            }

            /*
             * Special case: HPy_tp_call. The installed attributed __call__ will be just a
             * dispatcher. The actual function pointer given by the HPy definition is just the
             * default call function that we need to remember and set on every freshly created
             * instance of this type.
             */
            if (HPY_TP_CALL.equals(slot)) {
                enclosingType.setHPyDefaultCallFunc(slotData.impl());
            }
            return property;
        }
    }

    /**
     * Parses a {@code PyType_Slot} structure
     *
     * <pre>
     * typedef struct{
     *     int slot;
     *     void *pfunc;
     * } PyType_Slot;
     * </pre>
     */
    @GenerateUncached
    public abstract static class HPyCreateLegacySlotNode extends PNodeWithContext {

        public abstract HPyProperty execute(GraalHPyContext context, Object enclosingType, Object slotDef);

        @Specialization
        static HPyProperty doIt(GraalHPyContext context, Object enclosingType, Object slotDef,
                        @CachedLibrary(limit = "3") InteropLibrary resultLib,
                        @Cached CreateMethodNode legacyMethodNode,
                        @Cached HPyCreateLegacyMemberNode createLegacyMemberNode,
                        @Cached HPyAddLegacyGetSetDefNode legacyGetSetNode,
                        @Cached WriteAttributeToObjectNode writeAttributeToObjectNode,
                        @Cached ReadAttributeFromObjectNode readAttributeToObjectNode,
                        @Cached PCallHPyFunction callHelperFunctionNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached PythonObjectFactory factory) {
            assert checkLayout(slotDef) : "invalid layout of legacy slot definition";

            int slotId;
            Object slotObj = callHelperFunctionNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_LEGACY_SLOT_GET_SLOT, slotDef);
            if (resultLib.fitsInInt(slotObj)) {
                try {
                    slotId = resultLib.asInt(slotObj);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, ErrorMessages.FIELD_SLOT_S_ISNT_INT, slotDef);
            }

            HPyLegacySlot slot = HPyLegacySlot.fromValue(slotId);
            if (slot == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, ErrorMessages.INVALID_SLOT_VALUE, slotId);
            }

            // treatment for special slots 'Py_tp_members', 'Py_tp_getset', 'Py_tp_methods'
            switch (slot) {
                case Py_tp_members:
                    Object memberDefArrayPtr = callHelperFunctionNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_LEGACY_SLOT_GET_MEMBERS, slotDef);
                    try {
                        int nLegacyMemberDefs = PInt.intValueExact(resultLib.getArraySize(memberDefArrayPtr));
                        for (int i = 0; i < nLegacyMemberDefs; i++) {
                            Object legacyMemberDef = resultLib.readArrayElement(memberDefArrayPtr, i);
                            HPyProperty property = createLegacyMemberNode.execute(enclosingType, legacyMemberDef);
                            property.write(writeAttributeToObjectNode, readAttributeToObjectNode, enclosingType);
                        }
                    } catch (InteropException | OverflowException e) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw raiseNode.raise(PythonBuiltinClassType.SystemError, ErrorMessages.ERR_WHEN_READING_LEGACY_MEYHOD_FOR_TYPE, enclosingType);
                    }
                    break;
                case Py_tp_methods:
                    Object methodDefArrayPtr = callHelperFunctionNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_LEGACY_SLOT_GET_METHODS, slotDef);
                    try {
                        int nLegacyMemberDefs = PInt.intValueExact(resultLib.getArraySize(methodDefArrayPtr));
                        CApiContext capiContext = nLegacyMemberDefs > 0 ? PythonContext.get(raiseNode).getCApiContext() : null;
                        for (int i = 0; i < nLegacyMemberDefs; i++) {
                            Object legacyMethodDef = resultLib.readArrayElement(methodDefArrayPtr, i);
                            PBuiltinFunction method = legacyMethodNode.execute(capiContext, legacyMethodDef);
                            writeAttributeToObjectNode.execute(enclosingType, method.getName(), method);
                        }
                    } catch (InteropException | OverflowException e) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw raiseNode.raise(PythonBuiltinClassType.SystemError, ErrorMessages.ERR_WHEN_READING_LEGACY_MEYHOD_FOR_TYPE, enclosingType);
                    }
                    break;
                case Py_tp_getset:
                    Object getSetDefArrayPtr = callHelperFunctionNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_LEGACY_SLOT_GET_DESCRS, slotDef);
                    try {
                        int nLegacyMemberDefs = PInt.intValueExact(resultLib.getArraySize(getSetDefArrayPtr));
                        for (int i = 0; i < nLegacyMemberDefs; i++) {
                            Object legacyMethodDef = resultLib.readArrayElement(getSetDefArrayPtr, i);
                            GetSetDescriptor getSetDescriptor = legacyGetSetNode.execute(context, enclosingType, legacyMethodDef);
                            writeAttributeToObjectNode.execute(enclosingType, getSetDescriptor.getName(), getSetDescriptor);
                        }
                    } catch (InteropException | OverflowException e) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw raiseNode.raise(PythonBuiltinClassType.SystemError, ErrorMessages.ERR_WHEN_READING_LEGACY_MEYHOD_FOR_TYPE, enclosingType);
                    }
                    break;
                default:
                    // this is the generic slot case
                    TruffleString attributeKey = slot.getAttributeKey();
                    if (attributeKey != null) {
                        if (!HPyProperty.keyExists(readAttributeToObjectNode, enclosingType, attributeKey)) {
                            Object pfuncPtr = callHelperFunctionNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_LEGACY_SLOT_GET_PFUNC, slotDef);
                            /*
                             * TODO(fa): Properly determine if 'pfuncPtr' is a native function
                             * pointer and thus if we need to do result and argument conversion.
                             */
                            PBuiltinFunction method = PExternalFunctionWrapper.createWrapperFunction(attributeKey, pfuncPtr, enclosingType, 0,
                                            slot.getSignature(), PythonLanguage.get(raiseNode), factory, true);
                            writeAttributeToObjectNode.execute(enclosingType, attributeKey, method);
                        }
                    } else {
                        // TODO(fa): implement support for remaining legacy slot kinds
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw CompilerDirectives.shouldNotReachHere(PythonUtils.formatJString("support for legacy slot %s not yet implemented", slot.name()));
                    }
            }
            return null;
        }

        @TruffleBoundary
        private static boolean checkLayout(Object slotDef) {
            String[] members = new String[]{"slot", "pfunc"};
            InteropLibrary lib = InteropLibrary.getUncached(slotDef);
            for (String member : members) {
                if (!lib.isMemberReadable(slotDef, member)) {
                    return false;
                }
            }
            return true;
        }
    }

    @GenerateUncached
    public abstract static class HPyAsContextNode extends CExtToJavaNode {

        @Specialization
        static GraalHPyContext doHandle(GraalHPyNativeContext hpyContext) {
            return hpyContext.context;
        }

        /*
         * n.b. we could actually accept anything else, but we have specializations to be more *
         * strict about what we expect
         */

        @Specialization
        GraalHPyContext doInt(@SuppressWarnings("unused") int handle) {
            return getContext().getHPyContext();
        }

        @Specialization
        GraalHPyContext doLong(@SuppressWarnings("unused") long handle) {
            return getContext().getHPyContext();
        }

        @Specialization(guards = "interopLibrary.isPointer(handle)", limit = "2")
        static GraalHPyContext doPointer(@SuppressWarnings("unused") Object handle,
                        @CachedLibrary("handle") InteropLibrary interopLibrary) {
            return PythonContext.get(interopLibrary).getHPyContext();
        }
    }

    @ImportStatic(GraalHPyBoxing.class)
    public abstract static class HPyWithContextNode extends PNodeWithContext {

        static long asPointer(Object handle, InteropLibrary lib) {
            try {
                return lib.asPointer(handle);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @GenerateUncached
    @ImportStatic(GraalHPyBoxing.class)
    public abstract static class HPyEnsureHandleNode extends HPyWithContextNode {

        public abstract GraalHPyHandle execute(Object object);

        @Specialization
        static GraalHPyHandle doHandle(GraalHPyHandle handle) {
            return handle;
        }

        @Specialization(guards = {"!isLong(value)", "!isHPyHandle(value)", "!isBoxedNullHandle(bits)", "isBoxedHandle(bits)"})
        GraalHPyHandle doOtherBoxedHandle(@SuppressWarnings("unused") Object value,
                        @Shared("lib") @CachedLibrary(limit = "2") @SuppressWarnings("unused") InteropLibrary lib,
                        @Bind("asPointer(value, lib)") long bits) {
            return doLong(bits);
        }

        @Specialization(guards = {"!isLong(value)", "!isHPyHandle(value)", "isBoxedNullHandle(bits)"})
        @SuppressWarnings("unused")
        static GraalHPyHandle doOtherNull(Object value,
                        @Shared("lib") @CachedLibrary(limit = "2") InteropLibrary lib,
                        @Bind("asPointer(value, lib)") long bits) {
            return GraalHPyHandle.NULL_HANDLE;
        }

        @Specialization(guards = {"!isLong(value)", "!isHPyHandle(value)", "isBoxedInt(bits) || isBoxedDouble(bits)"})
        GraalHPyHandle doOtherBoxedPrimitive(@SuppressWarnings("unused") Object value,
                        @Shared("lib") @CachedLibrary(limit = "2") @SuppressWarnings("unused") InteropLibrary lib,
                        @Bind("asPointer(value, lib)") long bits) {
            return doBoxedPrimitive(bits);
        }

        @Specialization(guards = "isBoxedNullHandle(bits)")
        @SuppressWarnings("unused")
        static GraalHPyHandle doLongNull(long bits) {
            return GraalHPyHandle.NULL_HANDLE;
        }

        @Specialization(guards = {"isBoxedHandle(bits)"}, replaces = "doLongNull")
        GraalHPyHandle doLong(long bits) {
            GraalHPyContext context = getContext().getHPyContext();
            return context.createHandle(context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(bits)));
        }

        @Specialization(guards = "isBoxedInt(bits) || isBoxedDouble(bits)")
        @SuppressWarnings("unused")
        GraalHPyHandle doBoxedPrimitive(long bits) {
            /*
             * In this case, the long value is a boxed primitive and we cannot resolve it to a
             * GraalHPyHandle instance (because no instance has ever been created). We create a
             * fresh GaalHPyHandle instance here.
             */
            Object delegate;
            if (GraalHPyBoxing.isBoxedInt(bits)) {
                delegate = GraalHPyBoxing.unboxInt(bits);
            } else if (GraalHPyBoxing.isBoxedDouble(bits)) {
                delegate = GraalHPyBoxing.unboxDouble(bits);
            } else {
                throw CompilerDirectives.shouldNotReachHere();
            }
            return getContext().getHPyContext().createHandle(delegate);
        }
    }

    @GenerateUncached
    @ImportStatic(GraalHPyBoxing.class)
    public abstract static class HPyCloseHandleNode extends HPyWithContextNode {

        public abstract void execute(Object object);

        @Specialization(guards = "!handle.isAllocated()")
        @SuppressWarnings("unused")
        static void doHandle(GraalHPyHandle handle) {
            // nothing to do
        }

        @Specialization(guards = "handle.isAllocated()")
        void doHandleAllocated(GraalHPyHandle handle) {
            handle.closeAndInvalidate(getContext().getHPyContext());
        }

        @Specialization(guards = "isBoxedNullHandle(bits)")
        @SuppressWarnings("unused")
        static void doNullLong(long bits) {
            // nothing to do
        }

        @Specialization(guards = {"!isBoxedNullHandle(bits)", "isBoxedHandle(bits)"})
        void doLong(long bits) {
            /*
             * Since we have a long and it is in the "boxed handle" range, we know that the handle
             * *MUST* be allocated.
             */
            int id = GraalHPyBoxing.unboxHandle(bits);
            assert GraalHPyHandle.isAllocated(id);
            getContext().getHPyContext().releaseHPyHandleForObject(id);
        }

        @Specialization(guards = "!isBoxedHandle(bits)")
        @SuppressWarnings("unused")
        static void doLongDouble(long bits) {
            // nothing to do
        }

        @Specialization(guards = {"!isLong(value)", "!isHPyHandle(value)", "isBoxedNullHandle(bits)"})
        @SuppressWarnings("unused")
        static void doNullOther(Object value,
                        @Shared("lib") @CachedLibrary(limit = "2") InteropLibrary lib,
                        @Bind("asPointer(value, lib)") long bits) {
            // nothing to do
        }

        @Specialization(guards = {"!isLong(value)", "!isHPyHandle(value)", "!isBoxedNullHandle(bits)", "isBoxedHandle(bits)"})
        void doOther(@SuppressWarnings("unused") Object value,
                        @Shared("lib") @CachedLibrary(limit = "2") @SuppressWarnings("unused") InteropLibrary lib,
                        @Bind("asPointer(value, lib)") long bits) {
            doLong(bits);
        }

        @Specialization(guards = {"!isLong(value)", "!isHPyHandle(value)", "!isBoxedHandle(bits)"})
        @SuppressWarnings("unused")
        static void doOtherDouble(Object value,
                        @Shared("lib") @CachedLibrary(limit = "2") InteropLibrary lib,
                        @Bind("asPointer(value, lib)") long bits) {
            // nothing to do
        }
    }

    @GenerateUncached
    public abstract static class HPyCloseAndGetHandleNode extends HPyWithContextNode {

        public abstract Object execute(Object object);

        public abstract Object execute(long object);

        @Specialization(guards = "!handle.isAllocated()")
        static Object doHandle(GraalHPyHandle handle) {
            return handle.getDelegate();
        }

        @Specialization(guards = "handle.isAllocated()")
        Object doHandleAllocated(GraalHPyHandle handle) {
            handle.closeAndInvalidate(getContext().getHPyContext());
            return handle.getDelegate();
        }

        @Specialization(guards = "isBoxedNullHandle(bits)")
        @SuppressWarnings("unused")
        static Object doNullLong(long bits) {
            return GraalHPyHandle.NULL_HANDLE_DELEGATE;
        }

        @Specialization(guards = {"!isBoxedNullHandle(bits)", "isBoxedHandle(bits)"})
        Object doLong(long bits) {
            /*
             * Since we have a long and it is in the "boxed handle" range, we know that the handle
             * *MUST* be allocated.
             */
            int id = GraalHPyBoxing.unboxHandle(bits);
            assert GraalHPyHandle.isAllocated(id);
            GraalHPyContext context = getContext().getHPyContext();
            Object delegate = context.getObjectForHPyHandle(id);
            context.releaseHPyHandleForObject(id);
            return delegate;
        }

        @Specialization(guards = "isBoxedDouble(bits)")
        static double doLongDouble(long bits) {
            return GraalHPyBoxing.unboxDouble(bits);
        }

        @Specialization(guards = "isBoxedInt(bits)")
        static int doLongInt(long bits) {
            return GraalHPyBoxing.unboxInt(bits);
        }

        static long asPointer(Object handle, InteropLibrary lib) {
            try {
                return lib.asPointer(handle);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Specialization(guards = {"!isLong(value)", "!isHPyHandle(value)", "isBoxedNullHandle(bits)"})
        @SuppressWarnings("unused")
        static Object doNullOther(Object value,
                        @Shared("lib") @CachedLibrary(limit = "2") InteropLibrary lib,
                        @Bind("asPointer(value, lib)") long bits) {
            return GraalHPyHandle.NULL_HANDLE_DELEGATE;
        }

        @Specialization(guards = {"!isLong(value)", "!isHPyHandle(value)", "!isBoxedNullHandle(bits)", "isBoxedHandle(bits)"})
        Object doOther(@SuppressWarnings("unused") Object value,
                        @Shared("lib") @CachedLibrary(limit = "2") @SuppressWarnings("unused") InteropLibrary lib,
                        @Bind("asPointer(value, lib)") long bits) {
            return doLong(bits);
        }

        @Specialization(guards = {"!isLong(value)", "!isHPyHandle(value)", "isBoxedDouble(bits)"})
        static double doOtherDouble(@SuppressWarnings("unused") Object value,
                        @Shared("lib") @CachedLibrary(limit = "2") @SuppressWarnings("unused") InteropLibrary lib,
                        @Bind("asPointer(value, lib)") long bits) {
            return GraalHPyBoxing.unboxDouble(bits);
        }

        @Specialization(guards = {"!isLong(value)", "!isHPyHandle(value)", "isBoxedInt(bits)"})
        static int doOtherInt(@SuppressWarnings("unused") Object value,
                        @Shared("lib") @CachedLibrary(limit = "2") @SuppressWarnings("unused") InteropLibrary lib,
                        @Bind("asPointer(value, lib)") long bits) {
            return GraalHPyBoxing.unboxInt(bits);
        }
    }

    @GenerateUncached
    @ImportStatic(GraalHPyBoxing.class)
    public abstract static class HPyAsPythonObjectNode extends CExtToJavaNode {

        public abstract Object execute(long bits);

        @Specialization
        static Object doHandle(GraalHPyHandle handle) {
            return handle.getDelegate();
        }

        @Specialization(guards = "isBoxedNullHandle(bits)")
        @SuppressWarnings("unused")
        static Object doNullLong(long bits) {
            return GraalHPyHandle.NULL_HANDLE_DELEGATE;
        }

        @Specialization(guards = {"!isBoxedNullHandle(bits)", "isBoxedHandle(bits)"})
        Object doLong(long bits) {
            return getContext().getHPyContext().getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(bits));
        }

        @Specialization(guards = "isBoxedDouble(bits)")
        static double doLongDouble(long bits) {
            return GraalHPyBoxing.unboxDouble(bits);
        }

        @Specialization(guards = "isBoxedInt(bits)")
        static int doLongInt(long bits) {
            return GraalHPyBoxing.unboxInt(bits);
        }

        static long asPointer(Object handle, InteropLibrary lib) {
            try {
                return lib.asPointer(handle);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Specialization(guards = {"!isLong(value)", "!isHPyHandle(value)", "isBoxedNullHandle(bits)"})
        static Object doNullOther(@SuppressWarnings("unused") Object value,
                        @Shared("lib") @CachedLibrary(limit = "2") @SuppressWarnings("unused") InteropLibrary lib,
                        @Bind("asPointer(value, lib)") @SuppressWarnings("unused") long bits) {
            return GraalHPyHandle.NULL_HANDLE_DELEGATE;
        }

        @Specialization(guards = {"!isLong(value)", "!isHPyHandle(value)", "!isBoxedNullHandle(bits)", "isBoxedHandle(bits)"})
        Object doOther(@SuppressWarnings("unused") Object value,
                        @Shared("lib") @CachedLibrary(limit = "2") @SuppressWarnings("unused") InteropLibrary lib,
                        @Bind("asPointer(value, lib)") long bits) {
            return getContext().getHPyContext().getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(bits));
        }

        @Specialization(guards = {"!isLong(value)", "!isHPyHandle(value)", "isBoxedDouble(bits)"})
        static double doOtherDouble(@SuppressWarnings("unused") Object value,
                        @Shared("lib") @CachedLibrary(limit = "2") @SuppressWarnings("unused") InteropLibrary lib,
                        @Bind("asPointer(value, lib)") long bits) {
            return GraalHPyBoxing.unboxDouble(bits);
        }

        @Specialization(guards = {"!isLong(value)", "!isHPyHandle(value)", "isBoxedInt(bits)"})
        static int doOtherInt(@SuppressWarnings("unused") Object value,
                        @Shared("lib") @CachedLibrary(limit = "2") @SuppressWarnings("unused") InteropLibrary lib,
                        @Bind("asPointer(value, lib)") long bits) {
            return GraalHPyBoxing.unboxInt(bits);
        }

        @Specialization(replaces = {"doHandle", //
                        "doNullLong", "doLong", "doLongDouble", "doLongInt", //
                        "doNullOther", "doOther", "doOtherDouble", "doOtherInt" //
        })
        Object doGeneric(Object value,
                        @Shared("lib") @CachedLibrary(limit = "2") InteropLibrary lib) {
            if (value instanceof GraalHPyHandle) {
                return ((GraalHPyHandle) value).getDelegate();
            }
            long bits;
            if (value instanceof Long) {
                bits = (Long) value;
            } else {
                lib.toNative(value);
                try {
                    bits = lib.asPointer(value);
                } catch (UnsupportedMessageException ex) {
                    throw CompilerDirectives.shouldNotReachHere(ex);
                }
            }
            if (GraalHPyBoxing.isBoxedNullHandle(bits)) {
                return GraalHPyHandle.NULL_HANDLE_DELEGATE;
            } else if (GraalHPyBoxing.isBoxedInt(bits)) {
                return GraalHPyBoxing.unboxInt(bits);
            } else if (GraalHPyBoxing.isBoxedDouble(bits)) {
                return GraalHPyBoxing.unboxDouble(bits);
            } else {
                assert GraalHPyBoxing.isBoxedHandle(bits);
                return getContext().getHPyContext().getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(bits));
            }
        }
    }

    public static final class HPyDummyToJavaNode extends CExtToJavaNode {
        private static final HPyDummyToJavaNode UNCACHED = new HPyDummyToJavaNode();

        public static HPyDummyToJavaNode create() {
            return new HPyDummyToJavaNode();
        }

        public static HPyDummyToJavaNode getUncached() {
            return UNCACHED;
        }

        @Override
        public Object execute(Object object) {
            return object;
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.NONE;
        }

        @Override
        public boolean isAdoptable() {
            return this != UNCACHED;
        }
    }

    @GenerateUncached
    @ImportStatic(PGuards.class)
    public abstract static class HPyAsHandleNode extends CExtToNativeNode {
        protected static final byte HANDLE = 0;
        protected static final byte GLOBAL = 1;
        protected static final byte FIELD = 2;

        @Override
        public final GraalHPyHandle execute(Object object) {
            return execute(object, 0, HANDLE);
        }

        public final GraalHPyHandle executeGlobal(Object object, int id) {
            return execute(object, id, GLOBAL);
        }

        public final GraalHPyHandle executeField(Object object, int id) {
            return execute(object, id, FIELD);
        }

        protected abstract GraalHPyHandle execute(Object object, int id, int type);

        /*
         * NOTE: We *MUST NOT* box values here because we don't know where the handle will be given
         * to. In case we give it to LLVM code, we must still have an object that emulates the HPy
         * struct.
         */

        @Specialization(guards = "isNoValue(object)")
        @SuppressWarnings("unused")
        static GraalHPyHandle doNoValue(PNone object, int id, int type) {
            return GraalHPyHandle.NULL_HANDLE;
        }

        @Specialization(guards = {"!isNoValue(object)", "type == HANDLE"})
        GraalHPyHandle doObject(Object object, @SuppressWarnings("unused") int id, @SuppressWarnings("unused") int type) {
            return getContext().getHPyContext().createHandle(object);
        }

        @Specialization(guards = {"!isNoValue(object)", "type == GLOBAL"})
        static GraalHPyHandle doGlobal(Object object, int id, @SuppressWarnings("unused") int type) {
            return GraalHPyHandle.createGlobal(object, id);
        }

        @Specialization(guards = {"!isNoValue(object)", "type == FIELD"})
        GraalHPyHandle doField(Object object, int id, @SuppressWarnings("unused") int type) {
            return getContext().getHPyContext().createField(object, id);
        }
    }

    /**
     * Converts a Python object to a native {@code int64_t} compatible value.
     */
    @GenerateUncached
    public abstract static class HPyAsNativeInt64Node extends CExtToNativeNode {

        // Adding specializations for primitives does not make a lot of sense just to avoid
        // un-/boxing in the interpreter since interop will force un-/boxing anyway.
        @Specialization
        Object doGeneric(Object value,
                        @Cached ConvertPIntToPrimitiveNode asNativePrimitiveNode) {
            return asNativePrimitiveNode.execute(value, 1, Long.BYTES);
        }
    }

    public abstract static class HPyConvertArgsToSulongNode extends PNodeWithContext {

        public abstract void executeInto(VirtualFrame frame, Object[] args, int argsOffset, Object[] dest, int destOffset);

        abstract HPyCloseArgHandlesNode createCloseHandleNode();
    }

    public abstract static class HPyCloseArgHandlesNode extends PNodeWithContext {

        public abstract void executeInto(VirtualFrame frame, Object[] args, int argsOffset);
    }

    public abstract static class HPyVarargsToSulongNode extends HPyConvertArgsToSulongNode {

        @Specialization
        static void doConvert(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached HPyAsHandleNode selfAsHandleNode) {
            dest[destOffset] = selfAsHandleNode.execute(args[argsOffset]);
            dest[destOffset + 1] = args[argsOffset + 1];
            dest[destOffset + 2] = args[argsOffset + 2];
        }

        @Override
        HPyCloseArgHandlesNode createCloseHandleNode() {
            return HPyVarargsHandleCloseNodeGen.create();
        }
    }

    /**
     * The counter part of {@link HPyVarargsToSulongNode}.
     */
    public abstract static class HPyVarargsHandleCloseNode extends HPyCloseArgHandlesNode {

        @Specialization
        static void doConvert(Object[] dest, int destOffset,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyCloseHandleNode closeHandleNode) {
            closeHandleNode.execute(dest[destOffset]);
        }
    }

    /**
     * Always closes parameter at position {@code destOffset} (assuming that it is a handle).
     */
    public abstract static class HPySelfHandleCloseNode extends HPyCloseArgHandlesNode {

        @Specialization
        static void doConvert(Object[] dest, int destOffset,
                        @Cached HPyCloseHandleNode closeHandleNode) {
            closeHandleNode.execute(dest[destOffset]);
        }
    }

    public abstract static class HPyKeywordsToSulongNode extends HPyConvertArgsToSulongNode {

        @Specialization
        static void doConvert(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached HPyAsHandleNode selfAsHandleNode,
                        @Cached HPyAsHandleNode kwAsHandleNode) {
            dest[destOffset] = selfAsHandleNode.execute(args[argsOffset]);
            dest[destOffset + 1] = args[argsOffset + 1];
            dest[destOffset + 2] = args[argsOffset + 2];
            dest[destOffset + 3] = kwAsHandleNode.execute(args[argsOffset + 3]);
        }

        @Override
        HPyCloseArgHandlesNode createCloseHandleNode() {
            return HPyKeywordsHandleCloseNodeGen.create();
        }
    }

    /**
     * The counter part of {@link HPyKeywordsToSulongNode}.
     */
    public abstract static class HPyKeywordsHandleCloseNode extends HPyCloseArgHandlesNode {

        @Specialization
        static void doConvert(Object[] dest, int destOffset,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyCloseHandleNode closeFirstHandleNode,
                        @Cached HPyCloseHandleNode closeSecondHandleNode) {
            closeFirstHandleNode.execute(dest[destOffset]);
            closeSecondHandleNode.execute(dest[destOffset + 3]);
        }
    }

    public abstract static class HPyAllAsHandleNode extends HPyConvertArgsToSulongNode {

        static boolean isArgsOffsetPlus(int len, int off, int plus) {
            return len == off + plus;
        }

        static boolean isLeArgsOffsetPlus(int len, int off, int plus) {
            return len < plus + off;
        }

        @Specialization(guards = {"args.length == argsOffset"})
        @SuppressWarnings("unused")
        static void cached0(Object[] args, int argsOffset, Object[] dest, int destOffset) {
        }

        @Specialization(guards = {"args.length == cachedLength", "isLeArgsOffsetPlus(cachedLength, argsOffset, 8)"}, limit = "1", replaces = "cached0")
        @ExplodeLoop
        static void cachedLoop(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached("args.length") int cachedLength,
                        @Cached HPyAsHandleNode toSulongNode) {
            CompilerAsserts.partialEvaluationConstant(destOffset);
            for (int i = 0; i < cachedLength - argsOffset; i++) {
                dest[destOffset + i] = toSulongNode.execute(args[argsOffset + i]);
            }
        }

        @Specialization(replaces = {"cached0", "cachedLoop"})
        static void uncached(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached HPyAsHandleNode toSulongNode) {
            int len = args.length;
            for (int i = 0; i < len - argsOffset; i++) {
                dest[destOffset + i] = toSulongNode.execute(args[argsOffset + i]);
            }
        }

        @Override
        HPyCloseArgHandlesNode createCloseHandleNode() {
            return HPyAllHandleCloseNodeGen.create();
        }
    }

    /**
     * The counter part of {@link HPyAllAsHandleNode}.
     */
    public abstract static class HPyAllHandleCloseNode extends HPyCloseArgHandlesNode {

        @Specialization(guards = {"dest.length == destOffset"})
        @SuppressWarnings("unused")
        static void cached0(Object[] dest, int destOffset) {
        }

        @Specialization(guards = {"dest.length == cachedLength", "isLeArgsOffsetPlus(cachedLength, destOffset, 8)"}, limit = "1", replaces = "cached0")
        @ExplodeLoop
        static void cachedLoop(Object[] dest, int destOffset,
                        @Cached("dest.length") int cachedLength,
                        @Cached HPyCloseHandleNode closeHandleNode) {
            CompilerAsserts.partialEvaluationConstant(destOffset);
            for (int i = 0; i < cachedLength - destOffset; i++) {
                closeHandleNode.execute(dest[destOffset + i]);
            }
        }

        @Specialization(replaces = {"cached0", "cachedLoop"})
        static void uncached(Object[] dest, int destOffset,
                        @Cached HPyCloseHandleNode closeHandleNode) {
            int len = dest.length;
            for (int i = 0; i < len - destOffset; i++) {
                closeHandleNode.execute(dest[destOffset + i]);
            }
        }

        static boolean isLeArgsOffsetPlus(int len, int off, int plus) {
            return len < plus + off;
        }
    }

    /**
     * Argument converter for calling a native get/set descriptor getter function. The native
     * signature is: {@code HPy getter(HPyContext ctx, HPy self, void* closure)}.
     */
    public abstract static class HPyGetSetGetterToSulongNode extends HPyConvertArgsToSulongNode {

        @Specialization
        static void doConvert(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached HPyAsHandleNode selfAsHandleNode) {
            dest[destOffset] = selfAsHandleNode.execute(args[argsOffset]);
            dest[destOffset + 1] = args[argsOffset + 1];
        }

        @Override
        HPyCloseArgHandlesNode createCloseHandleNode() {
            return HPySelfHandleCloseNodeGen.create();
        }
    }

    /**
     * Argument converter for calling a native get/set descriptor setter function. The native
     * signature is: {@code HPy setter(HPyContext ctx, HPy self, HPy value, void* closure)}.
     */
    public abstract static class HPyGetSetSetterToSulongNode extends HPyConvertArgsToSulongNode {

        @Specialization
        static void doConvert(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached HPyAsHandleNode asHandleNode) {
            dest[destOffset] = asHandleNode.execute(args[argsOffset]);
            dest[destOffset + 1] = asHandleNode.execute(args[argsOffset + 1]);
            dest[destOffset + 2] = args[argsOffset + 2];
        }

        @Override
        HPyCloseArgHandlesNode createCloseHandleNode() {
            return HPyGetSetSetterHandleCloseNodeGen.create();
        }
    }

    /**
     * The counter part of {@link HPyGetSetSetterToSulongNode}.
     */
    public abstract static class HPyGetSetSetterHandleCloseNode extends HPyCloseArgHandlesNode {

        @Specialization
        static void doConvert(Object[] dest, int destOffset,
                        @Cached HPyCloseHandleNode closeFirstHandleNode,
                        @Cached HPyCloseHandleNode closeSecondHandleNode) {
            closeFirstHandleNode.execute(dest[destOffset]);
            closeSecondHandleNode.execute(dest[destOffset + 1]);
        }
    }

    /**
     * The counter part of {@link HPyGetSetSetterToSulongNode}.
     */
    public abstract static class HPyLegacyGetSetSetterDecrefNode extends HPyCloseArgHandlesNode {

        @Specialization
        static void doConvert(Object[] dest, int destOffset,
                        @Cached SubRefCntNode subRefCntNode) {
            subRefCntNode.dec(dest[destOffset + 1]);
        }
    }

    /**
     * Converts {@code self} to an HPy handle and any other argument to {@code HPy_ssize_t}.
     */
    public abstract static class HPySSizeArgFuncToSulongNode extends HPyConvertArgsToSulongNode {

        @Specialization(guards = {"isArity(args.length, argsOffset, 2)"})
        static void doHandleSsizeT(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached ConvertPIntToPrimitiveNode asSsizeTNode) {
            CompilerAsserts.partialEvaluationConstant(argsOffset);
            dest[destOffset] = asHandleNode.execute(args[argsOffset]);
            dest[destOffset + 1] = asSsizeTNode.execute(args[argsOffset + 1], 1, Long.BYTES);
        }

        @Specialization(guards = {"isArity(args.length, argsOffset, 3)"})
        static void doHandleSsizeTSsizeT(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached ConvertPIntToPrimitiveNode asSsizeTNode) {
            CompilerAsserts.partialEvaluationConstant(argsOffset);
            dest[destOffset] = asHandleNode.execute(args[argsOffset]);
            dest[destOffset + 1] = asSsizeTNode.execute(args[argsOffset + 1], 1, Long.BYTES);
            dest[destOffset + 2] = asSsizeTNode.execute(args[argsOffset + 2], 1, Long.BYTES);
        }

        @Specialization(replaces = {"doHandleSsizeT", "doHandleSsizeTSsizeT"})
        static void doGeneric(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached ConvertPIntToPrimitiveNode asSsizeTNode) {
            dest[destOffset] = asHandleNode.execute(args[argsOffset]);
            for (int i = 1; i < args.length - argsOffset; i++) {
                dest[destOffset + i] = asSsizeTNode.execute(args[argsOffset + i], 1, Long.BYTES);
            }
        }

        @Override
        HPyCloseArgHandlesNode createCloseHandleNode() {
            return HPySelfHandleCloseNodeGen.create();
        }

        static boolean isArity(int len, int off, int expected) {
            return len - off == expected;
        }
    }

    /**
     * Converts arguments for C function signature
     * {@code int (*HPyFunc_ssizeobjargproc)(HPyContext ctx, HPy, HPy_ssize_t, HPy)}.
     */
    public abstract static class HPySSizeObjArgProcToSulongNode extends HPyConvertArgsToSulongNode {

        @Specialization
        static void doConvert(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached ConvertPIntToPrimitiveNode asSsizeTNode) {
            CompilerAsserts.partialEvaluationConstant(argsOffset);
            dest[destOffset] = asHandleNode.execute(args[argsOffset]);
            dest[destOffset + 1] = asSsizeTNode.execute(args[argsOffset + 1], 1, Long.BYTES);
            dest[destOffset + 2] = asHandleNode.execute(args[argsOffset + 2]);
        }

        @Override
        HPyCloseArgHandlesNode createCloseHandleNode() {
            return HPySSizeObjArgProcCloseNodeGen.create();
        }
    }

    /**
     * Always closes handle parameter at position {@code destOffset} and also closes parameter at
     * position {@code destOffset + 2} if it is not a {@code NULL} handle.
     */
    public abstract static class HPySSizeObjArgProcCloseNode extends HPyCloseArgHandlesNode {

        @Specialization
        static void doConvert(Object[] dest, int destOffset,
                        @Cached HPyCloseHandleNode closeFirstHandleNode,
                        @Cached HPyCloseHandleNode closeSecondHandleNode) {
            closeFirstHandleNode.execute(dest[destOffset]);
            closeSecondHandleNode.execute(dest[destOffset + 2]);
        }
    }

    /**
     * Converts arguments for C function signature
     * {@code HPy (*HPyFunc_richcmpfunc)(HPyContext ctx, HPy, HPy, HPy_RichCmpOp);}.
     */
    public abstract static class HPyRichcmpFuncArgsToSulongNode extends HPyConvertArgsToSulongNode {

        @Specialization
        static void doConvert(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached HPyAsHandleNode asHandleNode) {
            CompilerAsserts.partialEvaluationConstant(argsOffset);
            dest[destOffset] = asHandleNode.execute(args[argsOffset]);
            dest[destOffset + 1] = asHandleNode.execute(args[argsOffset + 1]);
            dest[destOffset + 2] = args[argsOffset + 2];
        }

        @Override
        HPyCloseArgHandlesNode createCloseHandleNode() {
            return HPyRichcmptFuncArgsCloseNodeGen.create();
        }
    }

    /**
     * Always closes handle parameter at positions {@code destOffset} and {@code destOffset + 1}.
     */
    public abstract static class HPyRichcmptFuncArgsCloseNode extends HPyCloseArgHandlesNode {

        @Specialization
        static void doConvert(Object[] dest, int destOffset,
                        @Cached HPyCloseHandleNode closeFirstHandleNode,
                        @Cached HPyCloseHandleNode closeSecondHandleNode) {
            closeFirstHandleNode.execute(dest[destOffset]);
            closeSecondHandleNode.execute(dest[destOffset + 1]);
        }
    }

    /**
     * Converts for C function signature
     * {@code int (*HPyFunc_getbufferproc)(HPyContext ctx, HPy self, HPy_buffer *buffer, int flags)}
     * .
     */
    public abstract static class HPyGetBufferProcToSulongNode extends HPyConvertArgsToSulongNode {

        @Specialization
        static void doConversion(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached AsNativePrimitiveNode asIntNode) {
            CompilerAsserts.partialEvaluationConstant(argsOffset);
            dest[destOffset] = asHandleNode.execute(args[argsOffset]);
            dest[destOffset + 1] = args[argsOffset + 1];
            dest[destOffset + 2] = asIntNode.execute(args[argsOffset + 2], 1, Integer.BYTES, true);
        }

        @Override
        HPyCloseArgHandlesNode createCloseHandleNode() {
            return HPySelfHandleCloseNodeGen.create();
        }
    }

    /**
     * Converts for C function signature
     * {@code void (*HPyFunc_releasebufferproc)(HPyContext ctx, HPy self, HPy_buffer *buffer)}.
     */
    public abstract static class HPyReleaseBufferProcToSulongNode extends HPyConvertArgsToSulongNode {

        @Specialization
        static void doConversion(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached HPyAsHandleNode asHandleNode) {
            CompilerAsserts.partialEvaluationConstant(argsOffset);
            dest[destOffset] = asHandleNode.execute(args[argsOffset]);
            dest[destOffset + 1] = args[argsOffset + 1];
        }

        @Override
        HPyCloseArgHandlesNode createCloseHandleNode() {
            return HPySelfHandleCloseNodeGen.create();
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    abstract static class HPyLongFromLong extends Node {
        public abstract Object execute(Node inliningTarget, int value, boolean signed);

        public abstract Object execute(Node inliningTarget, long value, boolean signed);

        public abstract Object execute(Node inliningTarget, Object value, boolean signed);

        @Specialization(guards = "signed")
        static int doSignedInt(int n, @SuppressWarnings("unused") boolean signed) {
            return n;
        }

        @Specialization(guards = "!signed")
        static long doUnsignedInt(int n, @SuppressWarnings("unused") boolean signed) {
            if (n < 0) {
                return n & 0xFFFFFFFFL;
            }
            return n;
        }

        @Specialization(guards = "signed")
        static long doSignedLong(long n, @SuppressWarnings("unused") boolean signed) {
            return n;
        }

        @Specialization(guards = {"!signed", "n >= 0"})
        static long doUnsignedLongPositive(long n, @SuppressWarnings("unused") boolean signed) {
            return n;
        }

        @Specialization(guards = {"!signed", "n < 0"})
        static Object doUnsignedLongNegative(long n, @SuppressWarnings("unused") boolean signed,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createInt(convertToBigInteger(n));
        }

        @TruffleBoundary
        private static BigInteger convertToBigInteger(long n) {
            return BigInteger.valueOf(n).add(BigInteger.ONE.shiftLeft(Long.SIZE));
        }

        @Specialization
        static Object doPointer(PythonNativeObject n, @SuppressWarnings("unused") boolean signed,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createNativeVoidPtr(n.getPtr());
        }
    }

    /**
     * <pre>
     *     typedef struct {
     *         const char* name;
     *         int basicsize;
     *         int itemsize;
     *         unsigned int flags;
     *         int legacy;
     *         void *legacy_slots;
     *         HPyDef **defines;
     *         const char *doc;
     *     } HPyType_Spec;
     * </pre>
     */
    @ImportStatic(SpecialMethodSlot.class)
    @GenerateUncached
    abstract static class HPyCreateTypeFromSpecNode extends Node {

        abstract Object execute(GraalHPyContext context, Object typeSpec, Object typeSpecParamArray);

        static final TruffleString T_PYTRUFFLE_CREATETYPE = tsLiteral("PyTruffle_CreateType");

        @Specialization
        Object doGeneric(GraalHPyContext context, Object typeSpec, Object typeSpecParamArray,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "3") InteropLibrary ptrLib,
                        @CachedLibrary(limit = "3") InteropLibrary valueLib,
                        @CachedLibrary(limit = "1") DynamicObjectLibrary dylib,
                        @Cached HPyTypeSplitNameNode splitName,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PCallHPyFunction callHelperFunctionNode,
                        @Cached PCallHPyFunction callMallocNode,
                        @Cached GetTypeMemberNode getMetaSizeNode,
                        @Cached ReadAttributeFromObjectNode readAttributeFromObjectNode,
                        @Cached WriteAttributeToObjectNode writeAttributeToObjectNode,
                        @Cached PyObjectCallMethodObjArgs callCreateTypeNode,
                        @Cached CastToJavaIntLossyNode castToJavaIntNode,
                        @Cached HPyCreateFunctionNode addFunctionNode,
                        @Cached HPyAddMemberNode addMemberNode,
                        @Cached HPyCreateSlotNode addSlotNode,
                        @Cached HPyCreateLegacySlotNode createLegacySlotNode,
                        @Cached HPyCreateGetSetDescriptorNode createGetSetDescriptorNode,
                        @Cached GetSuperClassNode getSuperClassNode,
                        @Cached InlinedIsSameTypeNode isSameTypeNode,
                        @Cached ReadAttributeFromObjectNode readHPyTypeFlagsNode,
                        @Cached(parameters = "New") LookupCallableSlotInMRONode lookupNewNode,
                        @Cached HPyAsPythonObjectNode hPyAsPythonObjectNode,
                        @Cached PRaiseNode raiseNode) {

            try {
                // the name as given by the specification
                Object specNamePtr = ptrLib.readMember(typeSpec, "name");

                // TODO(fa): function 'graal_hpy_type_name' returns a new string (created with
                // 'strdup'). We need to free it.
                Object tpName = callHelperFunctionNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_TYPE_NAME, specNamePtr);
                TruffleString specName = fromCharPointerNode.execute(specNamePtr, false);

                // extract module and type name
                TruffleString[] names = splitName.execute(inliningTarget, specName);
                assert names.length == 2;

                PDict namespace;
                Object doc = ptrLib.readMember(typeSpec, "doc");
                if (!ptrLib.isNull(doc)) {
                    TruffleString docString = castToTruffleStringNode.execute(fromCharPointerNode.execute(doc));
                    namespace = factory.createDict(new PKeyword[]{new PKeyword(SpecialAttributeNames.T___DOC__, docString)});
                } else {
                    namespace = factory.createDict();
                }

                // extract bases from type spec params

                PTuple bases;
                try {
                    bases = extractBases(context, typeSpecParamArray, ptrLib, castToJavaIntNode, callHelperFunctionNode, hPyAsPythonObjectNode, factory);
                } catch (CannotCastException | InteropException e) {
                    throw raiseNode.raise(SystemError, ErrorMessages.FAILED_TO_EXTRACT_BASES_FROM_TYPE_SPEC_PARAMS, specName);
                }

                // create the type object
                Object metatype = getMetatype(context, typeSpecParamArray, ptrLib, castToJavaIntNode, callHelperFunctionNode, hPyAsPythonObjectNode, raiseNode);
                PythonModule pythonCextModule = PythonContext.get(this).lookupBuiltinModule(BuiltinNames.T___GRAALPYTHON__);
                PythonClass newType = (PythonClass) callCreateTypeNode.execute(null, pythonCextModule, T_PYTRUFFLE_CREATETYPE,
                                names[1], bases, namespace, metatype != null ? metatype : PythonBuiltinClassType.PythonClass);
                // allocate additional memory for the metatype and set it
                long metaBasicSize = 0;
                Object destroyFunc = null;
                if (metatype instanceof PythonClass metaclass) {
                    // get basicsize of metatype and allocate it into
                    // GraalHPyDef.OBJECT_HPY_NATIVE_SPACE
                    metaBasicSize = metaclass.getBasicSize();
                    destroyFunc = metaclass.getHPyDestroyFunc();
                } else if (metatype instanceof PythonNativeClass) {
                    // This path is implemented only for completeness,
                    // but is not expected to happen often, hence the
                    // uncached nodes, no profiling and potential leak
                    Object sizeObj = getMetaSizeNode.execute(metatype, NativeMember.TP_BASICSIZE);
                    metaBasicSize = CastToJavaLongExactNode.getUncached().execute(sizeObj);
                }
                if (metaBasicSize > 0) {
                    Object dataPtr = callMallocNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_CALLOC, metaBasicSize, 1L);
                    GraalHPyData.setHPyNativeSpace(newType, dataPtr);
                    if (destroyFunc != null) {
                        context.createHandleReference(newType, dataPtr, destroyFunc != PNone.NO_VALUE ? destroyFunc : null);
                    }
                }

                // determine and set the correct module attribute
                TruffleString value = names[0];
                if (value != null) {
                    writeAttributeToObjectNode.execute(newType, SpecialAttributeNames.T___MODULE__, value);
                } else {
                    // TODO(fa): issue deprecation warning with message "builtin type %.200s has no
                    // __module__ attribute"
                }

                // store flags, basicsize, and itemsize to type
                long flags = castToLong(valueLib, ptrLib.readMember(typeSpec, "flags"));
                int builtinShape = castToInt(valueLib, callHelperFunctionNode.call(context, GRAAL_HPY_TYPE_SPEC_GET_BUILTIN_SHAPE, typeSpec));

                long basicSize = castToLong(valueLib, ptrLib.readMember(typeSpec, "basicsize"));
                long itemSize = castToLong(valueLib, ptrLib.readMember(typeSpec, "itemsize"));
                newType.setHPyTypeExtra(new HPyTypeExtra(flags, basicSize, itemSize, tpName, builtinShape));
                newType.makeStaticBase(dylib);

                boolean seenNew = false;
                boolean needsTpTraverse = ((flags & GraalHPyDef.HPy_TPFLAGS_HAVE_GC) != 0);

                // process defines
                Object defines = callHelperFunctionNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_TYPE_SPEC_GET_DEFINES, typeSpec);
                // field 'defines' may be 'NULL'
                if (!ptrLib.isNull(defines)) {
                    if (!ptrLib.hasArrayElements(defines)) {
                        return raiseNode.raise(SystemError, ErrorMessages.FIELD_DEFINES_DID_NOT_RETURN_ARRAY, specName);
                    }

                    int nDefines = PInt.intValueExact(ptrLib.getArraySize(defines));
                    for (long i = 0; i < nDefines; i++) {
                        Object moduleDefine = ptrLib.readArrayElement(defines, i);
                        HPyProperty property = null;
                        int kind = castToJavaIntNode.execute(callHelperFunctionNode.call(context, GRAAL_HPY_DEF_GET_KIND, moduleDefine));
                        switch (kind) {
                            case GraalHPyDef.HPY_DEF_KIND_METH:
                                Object methodDef = callHelperFunctionNode.call(context, GRAAL_HPY_DEF_GET_METH, moduleDefine);
                                PBuiltinFunction fun = addFunctionNode.execute(context, newType, methodDef);
                                property = new HPyProperty(fun.getName(), fun);
                                break;
                            case GraalHPyDef.HPY_DEF_KIND_SLOT:
                                Object slotDef = callHelperFunctionNode.call(context, GRAAL_HPY_DEF_GET_SLOT, moduleDefine);
                                Object addSlotResult = addSlotNode.execute(context, newType, slotDef);
                                if (HPY_TP_TRAVERSE.equals(addSlotResult)) {
                                    needsTpTraverse = false;
                                } else if (addSlotResult instanceof HPyProperty) {
                                    property = (HPyProperty) addSlotResult;
                                }
                                if (property != null && SpecialMethodNames.T___NEW__.equals(property.key)) {
                                    seenNew = true;
                                }
                                break;
                            case GraalHPyDef.HPY_DEF_KIND_MEMBER:
                                Object memberDef = callHelperFunctionNode.call(context, GRAAL_HPY_DEF_GET_MEMBER, moduleDefine);
                                property = addMemberNode.execute(context, newType, memberDef);
                                break;
                            case GraalHPyDef.HPY_DEF_KIND_GETSET:
                                Object getsetDef = callHelperFunctionNode.call(context, GRAAL_HPY_DEF_GET_GETSET, moduleDefine);
                                GetSetDescriptor getSetDescriptor = createGetSetDescriptorNode.execute(context, newType, getsetDef);
                                property = new HPyProperty(getSetDescriptor.getName(), getSetDescriptor);
                                break;
                            default:
                                assert false : "unknown definition kind";
                        }

                        if (property != null) {
                            property.write(writeAttributeToObjectNode, readAttributeFromObjectNode, newType);
                        }
                    }
                }

                if (needsTpTraverse) {
                    throw raiseNode.raise(ValueError, ErrorMessages.TRAVERSE_FUNCTION_NEEDED);
                }

                // process legacy slots; this is of type 'cpy_PyTypeSlot legacy_slots[]'
                Object legacySlots = callHelperFunctionNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_TYPE_SPEC_GET_LEGECY_SLOTS, typeSpec);
                if (!ptrLib.isNull(legacySlots)) {
                    if (builtinShape != GraalHPyDef.HPyType_BUILTIN_SHAPE_LEGACY) {
                        throw raiseNode.raise(TypeError, ErrorMessages.CANNOT_SPECIFY_LEG_SLOTS_WO_SETTING_LEG);
                    }
                    int nLegacySlots = PInt.intValueExact(ptrLib.getArraySize(legacySlots));
                    for (int i = 0; i < nLegacySlots; i++) {
                        Object legacySlotDef = ptrLib.readArrayElement(legacySlots, i);
                        HPyProperty property = createLegacySlotNode.execute(context, newType, legacySlotDef);
                        if (property != null) {
                            property.write(writeAttributeToObjectNode, readAttributeFromObjectNode, newType);
                        }
                    }
                }

                /*
                 * If 'basicsize > 0' and no explicit constructor is given, the constructor of the
                 * object needs to allocate the native space for the object. However, the inherited
                 * constructors won't do that. Also, if the default call function needs to be set
                 * (i.e. 'HPy_tp_call' was defined), an inherited constructor won't do it.
                 *
                 * The built-in shape determines the "native" shape of the object which means that
                 * it determines which Java object we need to allocate (e.g. PInt, PythonObject,
                 * PFloat, etc.).
                 */
                if (!seenNew && (basicSize > 0 || newType.getHPyDefaultCallFunc() != null)) {
                    PBuiltinFunction constructorDecorator = HPyObjectNewNode.createBuiltinFunction(PythonLanguage.get(raiseNode), builtinShape);
                    writeAttributeToObjectNode.execute(newType, SpecialMethodNames.T___NEW__, constructorDecorator);
                }

                long baseFlags;
                Object baseClass = getSuperClassNode.execute(newType);
                if (baseClass instanceof PythonClass pythonBaseClass) {
                    baseFlags = pythonBaseClass.getFlags();
                } else {
                    Object baseFlagsObj = readHPyTypeFlagsNode.execute(baseClass, GraalHPyDef.TYPE_HPY_FLAGS);
                    baseFlags = baseFlagsObj != PNone.NO_VALUE ? (long) baseFlagsObj : 0;
                }
                int baseBuiltinShape = GraalHPyDef.getBuiltinShapeFromHiddenAttribute(baseClass, readHPyTypeFlagsNode);
                checkInheritanceConstraints(flags, baseFlags, builtinShape, baseBuiltinShape > GraalHPyDef.HPyType_BUILTIN_SHAPE_LEGACY, raiseNode);
                return newType;
            } catch (CannotCastException | InteropException e) {
                throw raiseNode.raise(SystemError, ErrorMessages.COULD_NOT_CREATE_TYPE_FROM_SPEC_BECAUSE, e);
            } catch (OverflowException e) {
                throw raiseNode.raise(SystemError, ErrorMessages.COULD_NOT_CREATE_TYPE_FROM_SPEC_TOO_MANY);
            }
        }

        /**
         * Extract bases from an array consisting of elements with the following C struct.
         *
         * <pre>
         *     typedef struct {
         *         HPyType_SpecParam_Kind kind;
         *         HPy object;
         *     } HPyType_SpecParam;
         * </pre>
         *
         * Reference implementation can be found in {@code ctx_type.c:build_bases_from_params}.
         *
         * @return The bases tuple or {@code null} in case of an error.
         */
        @TruffleBoundary
        private static PTuple extractBases(GraalHPyContext context, Object typeSpecParamArray,
                        InteropLibrary ptrLib,
                        CastToJavaIntLossyNode castToJavaIntNode,
                        PCallHPyFunction callHelperFunctionNode,
                        HPyAsPythonObjectNode asPythonObjectNode,
                        PythonObjectFactory factory) throws InteropException {

            // if the pointer is NULL, no bases have been explicitly specified
            if (ptrLib.isNull(typeSpecParamArray)) {
                return factory.createTuple(PythonUtils.EMPTY_OBJECT_ARRAY);
            }

            long nSpecParam = ptrLib.getArraySize(typeSpecParamArray);
            ArrayList<Object> basesList = new ArrayList<>();
            for (long i = 0; i < nSpecParam; i++) {
                Object specParam = ptrLib.readArrayElement(typeSpecParamArray, i);
                // TODO(fa): directly read member as soon as this is supported by Sulong.
                // Currently, we cannot pass struct-by-value via interop.
                int specParamKind = castToJavaIntNode.execute(callHelperFunctionNode.call(context, GRAAL_HPY_TYPE_SPEC_PARAM_GET_KIND, specParam));
                Object specParamObject = asPythonObjectNode.execute(callHelperFunctionNode.call(context, GRAAL_HPY_TYPE_SPEC_PARAM_GET_OBJECT, specParam));

                switch (specParamKind) {
                    case GraalHPyDef.HPyType_SPEC_PARAM_BASE:
                        // In this case, the 'specParamObject' is a single handle. We add it to
                        // the list of bases.
                        assert PGuards.isClass(specParamObject, IsTypeNode.getUncached()) : "base object is not a Python class";
                        basesList.add(specParamObject);
                        break;
                    case GraalHPyDef.HPyType_SPEC_PARAM_BASES_TUPLE:
                        // In this case, the 'specParamObject' is tuple. According to the
                        // reference implementation, we immediately use this tuple and throw
                        // away any other single base classes or subsequent params.
                        assert PGuards.isPTuple(specParamObject) : "type spec param claims to be a tuple but isn't";
                        return (PTuple) specParamObject;
                    case GraalHPyDef.HPyType_SPEC_PARAM_METACLASS:
                        // intentionally ignored
                        break;
                    default:
                        assert false : "unknown type spec param kind";
                }
            }
            return factory.createTuple(basesList.toArray());
        }

        /**
         * Reference implementation can be found in {@code ctx_type.c:get_metatype}
         */
        @TruffleBoundary
        private static Object getMetatype(GraalHPyContext context, Object typeSpecParamArray,
                        InteropLibrary ptrLib,
                        CastToJavaIntLossyNode castToJavaIntNode,
                        PCallHPyFunction callHelperFunctionNode,
                        HPyAsPythonObjectNode asPythonObjectNode,
                        PRaiseNode raiseNode) throws InteropException {
            if (!ptrLib.isNull(typeSpecParamArray)) {
                long nSpecParam = ptrLib.getArraySize(typeSpecParamArray);
                for (long i = 0; i < nSpecParam; i++) {
                    Object specParam = ptrLib.readArrayElement(typeSpecParamArray, i);
                    // TODO(fa): directly read member as soon as this is supported by Sulong.
                    // Currently, we cannot pass struct-by-value via interop.
                    int specParamKind = castToJavaIntNode.execute(callHelperFunctionNode.call(context, GRAAL_HPY_TYPE_SPEC_PARAM_GET_KIND, specParam));

                    switch (specParamKind) {
                        case GraalHPyDef.HPyType_SPEC_PARAM_METACLASS:
                            Object object = asPythonObjectNode.execute(callHelperFunctionNode.call(context, GRAAL_HPY_TYPE_SPEC_PARAM_GET_OBJECT, specParam));
                            if (!IsTypeNode.getUncached().execute(object)) {
                                throw raiseNode.raise(TypeError, ErrorMessages.HPY_METACLASS_IS_NOT_A_TYPE, object);
                            }
                            return object;
                        default:
                            // intentionally ignored
                            break;
                    }
                }
            }
            return null;
        }

        private static void checkInheritanceConstraints(long flags, long baseFlags, int builtinShape, boolean baseIsPure, PRaiseNode raiseNode) {
            // Pure types may inherit from:
            //
            // * pure types, or
            // * PyBaseObject_Type, or
            // * other builtin or legacy types as long as as they do not
            // access the struct layout (e.g. by using HPy_AsStruct or defining
            // a deallocator with HPy_tp_destroy).
            //
            // It would be nice to relax these restrictions or check them here.
            // See https://github.com/hpyproject/hpy/issues/169 for details.
            assert GraalHPyDef.isValidBuiltinShape(builtinShape);
            if (builtinShape == GraalHPyDef.HPyType_BUILTIN_SHAPE_LEGACY && baseIsPure) {
                throw raiseNode.raise(TypeError, ErrorMessages.LEG_TYPE_SHOULDNT_INHERIT_MEM_LAYOUT_FROM_PURE_TYPE);
            }
        }

        private static long castToLong(InteropLibrary lib, Object value) throws OverflowException {
            if (lib.fitsInLong(value)) {
                try {
                    return lib.asLong(value);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }
            throw OverflowException.INSTANCE;
        }

        private static int castToInt(InteropLibrary lib, Object value) {
            if (lib.fitsInInt(value)) {
                try {
                    return lib.asInt(value);
                } catch (UnsupportedMessageException e) {
                    // fall through
                }
            }
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    /**
     * Extract the heap type's and the module's name from the name given by the type
     * specification.<br/>
     * According to CPython, we need to look for the last {@code '.'} and everything before it
     * (which may also contain more dots) is the module name. Everything after it is the type name.
     * See also: {@code typeobject.c: PyType_FromSpecWithBases}
     */
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    abstract static class HPyTypeSplitNameNode extends Node {

        public abstract TruffleString[] execute(Node inliningTarget, TruffleString tpName);

        @Specialization
        static TruffleString[] doGeneric(TruffleString specNameUtf8,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached TruffleString.LastIndexOfCodePointNode indexOfCodepointNode,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Cached TruffleString.CodePointLengthNode lengthNode) {
            TruffleString specName = switchEncodingNode.execute(specNameUtf8, TS_ENCODING);
            int length = lengthNode.execute(specName, TS_ENCODING);
            int firstDotIdx = indexOfCodepointNode.execute(specName, '.', length, 0, TS_ENCODING);
            if (firstDotIdx > -1) {
                TruffleString left = substringNode.execute(specName, 0, firstDotIdx, TS_ENCODING, false);
                TruffleString right = substringNode.execute(specName, firstDotIdx + 1, length - firstDotIdx - 1, TS_ENCODING, false);
                return new TruffleString[]{left, right};
            }
            return new TruffleString[]{null, specName};
        }
    }

    @GenerateUncached
    @ImportStatic(PGuards.class)
    public abstract static class HPyTypeGetNameNode extends Node {

        public abstract Object execute(Object object);

        @Specialization(guards = "tpName != null")
        static Object doPythonClass(PythonClass clazz,
                        @Bind("clazz.getTpName()") Object tpName) {
            return tpName;
        }

        @Fallback
        static Object doOther(Object type,
                        @Cached GetNameNode getName,
                        @Cached ReadAttributeFromObjectNode readModuleNameNode,
                        @Cached EncodeNativeStringNode encodeNativeStringNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            TruffleString baseName = getName.execute(type);
            TruffleString name;
            if (type instanceof PythonClass pythonClass && pythonClass.isHPyType()) {
                // Types that originated from HPy: although they are ordinary managed
                // PythonClasses, the name should have "cext semantics", i.e., contain the
                // module if it was specified in the HPyType_Spec
                Object moduleName = readModuleNameNode.execute(type, SpecialAttributeNames.T___MODULE__);
                if (moduleName instanceof TruffleString) {
                    TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
                    appendNode.execute(sb, (TruffleString) moduleName);
                    appendNode.execute(sb, T_DOT);
                    appendNode.execute(sb, baseName);
                    name = toStringNode.execute(sb);
                } else {
                    name = baseName;
                }
            } else {
                name = baseName;
            }
            byte[] result = encodeNativeStringNode.execute(StandardCharsets.UTF_8, name, T_STRICT);
            return new CByteArrayWrapper(result);
        }
    }

    @GenerateUncached
    @ImportStatic(PGuards.class)
    public abstract static class HPyGetNativeSpacePointerNode extends Node {

        public abstract Object execute(Object object);

        @Specialization
        static Object doPythonObject(PythonObject object) {
            return GraalHPyData.getHPyNativeSpace(object);
        }

        @Fallback
        Object doOther(@SuppressWarnings("unused") Object object) {
            // TODO(fa): this should be a backend-specific value
            return PythonContext.get(this).getNativeNull();
        }
    }

    abstract static class HPyAttachFunctionTypeNode extends PNodeWithContext {
        public abstract Object execute(GraalHPyContext hpyContext, Object pointerObject, LLVMType llvmFunctionType);

        @NeverDefault
        public static HPyAttachFunctionTypeNode create() {
            PythonLanguage language = PythonLanguage.get(null);
            if (language.getEngineOption(PythonOptions.HPyBackend) == HPyBackendMode.JNI) {
                return HPyAttachJNIFunctionTypeNodeGen.create();
            }
            assert language.getEngineOption(PythonOptions.HPyBackend) == HPyBackendMode.NFI;
            return HPyAttachNFIFunctionTypeNodeGen.create();
        }

        public static HPyAttachFunctionTypeNode getUncached() {
            PythonLanguage language = PythonLanguage.get(null);
            if (language.getEngineOption(PythonOptions.HPyBackend) == HPyBackendMode.JNI) {
                return HPyAttachJNIFunctionTypeNodeGen.getUncached();
            }
            assert language.getEngineOption(PythonOptions.HPyBackend) == HPyBackendMode.NFI;
            return HPyAttachNFIFunctionTypeNodeGen.getUncached();
        }
    }

    /**
     * This node can be used to attach a function type to a function pointer if the function pointer
     * is not executable, i.e., if
     * {@code InteropLibrary.getUncached().isExecutable(functionPointer) == false}. This should not
     * be necessary if running bitcode because Sulong should then know if a pointer is a function
     * pointer but it might be necessary if a library was loaded with NFI since no bitcode is
     * available. The node will return a typed function pointer that is then executable.
     */
    @GenerateUncached
    public abstract static class HPyAttachNFIFunctionTypeNode extends HPyAttachFunctionTypeNode {
        private static final String J_NFI_LANGUAGE = "nfi";

        @Specialization(guards = {"isSingleContext()", "llvmFunctionType == cachedType"}, limit = "3")
        static Object doCachedSingleContext(@SuppressWarnings("unused") GraalHPyContext hpyContext, Object pointerObject, @SuppressWarnings("unused") LLVMType llvmFunctionType,
                        @Cached("llvmFunctionType") @SuppressWarnings("unused") LLVMType cachedType,
                        @Cached("getNFISignature(hpyContext, llvmFunctionType)") Object nfiSignature,
                        @CachedLibrary("nfiSignature") SignatureLibrary signatureLibrary) {
            return signatureLibrary.bind(nfiSignature, pointerObject);
        }

        @Specialization(guards = "llvmFunctionType == cachedType", limit = "3", replaces = "doCachedSingleContext")
        static Object doCached(@SuppressWarnings("unused") GraalHPyContext hpyContext, Object pointerObject, @SuppressWarnings("unused") LLVMType llvmFunctionType,
                        @Cached("llvmFunctionType") @SuppressWarnings("unused") LLVMType cachedType,
                        @Cached("getNFISignatureCallTarget(hpyContext, llvmFunctionType)") CallTarget nfiSignatureCt,
                        @CachedLibrary(limit = "1") SignatureLibrary signatureLibrary) {
            return signatureLibrary.bind(nfiSignatureCt.call(), pointerObject);
        }

        @Specialization(replaces = {"doCachedSingleContext", "doCached"})
        static Object doGeneric(GraalHPyContext hpyContext, Object pointerObject, LLVMType llvmFunctionType,
                        @CachedLibrary(limit = "1") SignatureLibrary signatureLibrary) {
            return signatureLibrary.bind(getNFISignature(hpyContext, llvmFunctionType), pointerObject);
        }

        @TruffleBoundary
        static Object getNFISignature(GraalHPyContext hpyContext, LLVMType llvmFunctionType) {
            return hpyContext.getContext().getEnv().parseInternal(getNFISignatureSource(llvmFunctionType)).call();
        }

        @TruffleBoundary
        static CallTarget getNFISignatureCallTarget(GraalHPyContext hpyContext, LLVMType llvmFunctionType) {
            return hpyContext.getContext().getEnv().parseInternal(getNFISignatureSource(llvmFunctionType));
        }

        @TruffleBoundary
        static Source getNFISignatureSource(LLVMType llvmFunctionType) {
            return Source.newBuilder(J_NFI_LANGUAGE, getNFISignatureSourceString(llvmFunctionType), llvmFunctionType.name()).build();
        }

        private static String getNFISignatureSourceString(LLVMType llvmFunctionType) {
            switch (llvmFunctionType) {
                case HPyModule_init:
                    return "(POINTER): POINTER";
                case HPyFunc_noargs:
                case HPyFunc_unaryfunc:
                case HPyFunc_getiterfunc:
                case HPyFunc_iternextfunc:
                case HPyFunc_reprfunc:
                    return "(POINTER, POINTER): POINTER";
                case HPyFunc_binaryfunc:
                case HPyFunc_o:
                case HPyFunc_getter:
                case HPyFunc_getattrfunc:
                case HPyFunc_getattrofunc:
                    return "(POINTER, POINTER, POINTER): POINTER";
                case HPyFunc_varargs:
                    return "(POINTER, POINTER, POINTER, SINT64): POINTER";
                case HPyFunc_keywords:
                    return "(POINTER, POINTER, POINTER, SINT64, POINTER): POINTER";
                case HPyFunc_ternaryfunc:
                case HPyFunc_descrgetfunc:
                    return "(POINTER, POINTER, POINTER, POINTER): POINTER";
                case HPyFunc_inquiry:
                    return "(POINTER, POINTER): SINT32";
                case HPyFunc_lenfunc:
                case HPyFunc_hashfunc:
                    return "(POINTER, POINTER): SINT64";
                case HPyFunc_ssizeargfunc:
                    return "(POINTER, POINTER, SINT64): POINTER";
                case HPyFunc_ssizessizeargfunc:
                    return "(POINTER, POINTER, SINT64, SINT64): POINTER";
                case HPyFunc_ssizeobjargproc:
                    return "(POINTER, POINTER, SINT64, POINTER): SINT32";
                case HPyFunc_initproc:
                    return "(POINTER, POINTER, POINTER, SINT64, POINTER): SINT32";
                case HPyFunc_ssizessizeobjargproc:
                    return "(POINTER, POINTER, SINT64, SINT64, POINTER): SINT32";
                case HPyFunc_objobjargproc:
                case HPyFunc_setter:
                case HPyFunc_descrsetfunc:
                case HPyFunc_setattrfunc:
                case HPyFunc_setattrofunc:
                    return "(POINTER, POINTER, POINTER, POINTER): SINT32";
                case HPyFunc_freefunc:
                    return "(POINTER, POINTER): VOID";
                case HPyFunc_richcmpfunc:
                    return "(POINTER, POINTER, POINTER, SINT32): POINTER";
                case HPyFunc_objobjproc:
                    return "(POINTER, POINTER, POINTER): SINT32";
                case HPyFunc_getbufferproc:
                    return "(POINTER, POINTER, POINTER, SINT32): SINT32";
                case HPyFunc_releasebufferproc:
                    return "(POINTER, POINTER, POINTER): VOID";
                case HPyFunc_destroyfunc:
                    return "(POINTER): VOID";
            }
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    /**
     */
    @GenerateUncached
    public abstract static class HPyAttachJNIFunctionTypeNode extends HPyAttachFunctionTypeNode {

        @Specialization
        static GraalHPyJNIFunctionPointer doGeneric(GraalHPyContext hpyContext, Object pointerObject, LLVMType llvmFunctionType,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary) {
            if (!interopLibrary.isPointer(pointerObject)) {
                interopLibrary.toNative(pointerObject);
            }
            try {
                return new GraalHPyJNIFunctionPointer(interopLibrary.asPointer(pointerObject), llvmFunctionType, hpyContext.isDebugMode());
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }

    protected static Object callBuiltinFunction(GraalHPyContext graalHPyContext, TruffleString func, Object[] pythonArguments,
                    ReadAttributeFromObjectNode readAttr,
                    CallNode callNode) {
        Object builtinFunction = readAttr.execute(graalHPyContext.getContext().getBuiltins(), func);
        return callNode.execute(builtinFunction, pythonArguments, PKeyword.EMPTY_KEYWORDS);
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class RecursiveExceptionMatches extends Node {
        abstract int execute(GraalHPyContext context, Object err, Object exc);

        @Specialization
        int tuple(GraalHPyContext context, Object err, PTuple exc,
                        @Cached RecursiveExceptionMatches recExcMatch,
                        @Cached PInteropSubscriptNode getItemNode,
                        @Cached LoopConditionProfile loopProfile) {
            int len = exc.getSequenceStorage().length();
            for (int i = 0; loopProfile.profile(i < len); i++) {
                Object e = getItemNode.execute(exc, i);
                if (recExcMatch.execute(context, err, e) != 0) {
                    return 1;
                }
            }
            return 0;
        }

        @Specialization(guards = {"!isPTuple(exc)", "isTupleSubtype(exc, getClassNode, isSubtypeNode)"})
        int subtuple(GraalHPyContext context, Object err, Object exc,
                        @Cached RecursiveExceptionMatches recExcMatch,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached ReadAttributeFromObjectNode readAttr,
                        @Cached CallNode callNode,
                        @Cached CastToJavaIntExactNode cast,
                        @Cached PInteropSubscriptNode getItemNode,
                        @Cached LoopConditionProfile loopProfile) {
            int len = cast.execute(callBuiltinFunction(context, BuiltinNames.T_LEN, new Object[]{exc}, readAttr, callNode));
            for (int i = 0; loopProfile.profile(i < len); i++) {
                Object e = getItemNode.execute(exc, i);
                if (recExcMatch.execute(context, err, e) != 0) {
                    return 1;
                }
            }
            return 0;
        }

        @Specialization(guards = {"!isPTuple(exc)", "!isTupleSubtype(exc, getClassNode, isSubtypeNode)"})
        int others(GraalHPyContext context, Object err, Object exc,
                        @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached ReadAttributeFromObjectNode readAttr,
                        @Cached CallNode callNode,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached IsTypeNode isTypeNode,
                        @Cached IsNode isNode,
                        @Cached BranchProfile isBaseExceptionProfile,
                        @Cached ConditionProfile isExceptionProfile) {
            Object isInstance = callBuiltinFunction(context,
                            BuiltinNames.T_ISINSTANCE,
                            new Object[]{err, PythonBuiltinClassType.PBaseException},
                            readAttr, callNode);
            Object e = err;
            if (isTrueNode.execute(null, isInstance)) {
                isBaseExceptionProfile.enter();
                e = getClassNode.execute(err);
            }
            if (isExceptionProfile.profile(
                            isExceptionClass(context, e, isTypeNode, readAttr, callNode, isTrueNode) &&
                                            isExceptionClass(context, exc, isTypeNode, readAttr, callNode, isTrueNode))) {
                return isSubClass(context, e, exc, readAttr, callNode, isTrueNode) ? 1 : 0;
            } else {
                return isNode.execute(exc, e) ? 1 : 0;
            }
        }

        protected boolean isTupleSubtype(Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(getClassNode.execute(obj), PythonBuiltinClassType.PTuple);
        }

        static boolean isSubClass(GraalHPyContext graalHPyContext, Object derived, Object cls,
                        ReadAttributeFromObjectNode readAttr,
                        CallNode callNode,
                        PyObjectIsTrueNode isTrueNode) {
            return isTrueNode.execute(null, callBuiltinFunction(graalHPyContext,
                            BuiltinNames.T_ISSUBCLASS,
                            new Object[]{derived, cls}, readAttr, callNode));

        }

        private static boolean isExceptionClass(GraalHPyContext nativeContext, Object obj,
                        IsTypeNode isTypeNode,
                        ReadAttributeFromObjectNode readAttr,
                        CallNode callNode,
                        PyObjectIsTrueNode isTrueNode) {
            return isTypeNode.execute(obj) && isSubClass(nativeContext, obj, PythonBuiltinClassType.PBaseException, readAttr, callNode, isTrueNode);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class HPyPackKeywordArgsNode extends Node {

        public abstract PKeyword[] execute(Node inliningTarget, Object[] kwvalues, PTuple kwnames);

        @Specialization
        static PKeyword[] doPTuple(Node inliningTarget, Object[] kwvalues, PTuple kwnames,
                        @Cached PyTupleSizeNode sizeNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Cached InlinedLoopConditionProfile loopProfile) {
            int nkw = sizeNode.execute(kwnames);
            loopProfile.profileCounted(inliningTarget, nkw);
            if (nkw == 0) {
                return PKeyword.EMPTY_KEYWORDS;
            }
            PKeyword[] result = new PKeyword[nkw];
            SequenceStorage storage = kwnames.getSequenceStorage();
            for (int i = 0; loopProfile.inject(inliningTarget, i < nkw); i++) {
                TruffleString name = (TruffleString) getItemNode.execute(storage, i);
                result[i] = new PKeyword(name, kwvalues[i]);
            }
            return result;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(true)
    public abstract static class HPyFieldLoadNode extends Node {

        public abstract Object execute(Node inliningTarget, PythonObject owner, Object hpyFieldPtr);

        @Specialization
        static Object doHandle(@SuppressWarnings("unused") PythonObject owner, GraalHPyHandle handle) {
            return handle.getDelegate();
        }

        @Specialization(replaces = "doHandle")
        static Object doGeneric(Node inliningTarget, PythonObject owner, Object hpyFieldPtr,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached InlinedExactClassProfile fieldTypeProfile) {
            Object hpyFieldObject = fieldTypeProfile.profile(inliningTarget, hpyFieldPtr);
            Object referent;
            // avoid `asPointer` message dispatch
            if (hpyFieldObject instanceof GraalHPyHandle) {
                referent = ((GraalHPyHandle) hpyFieldObject).getDelegate();
            } else {
                int idx;
                if (hpyFieldObject instanceof Long) {
                    // branch profile in lib.asPointer
                    try {
                        idx = PInt.intValueExact((Long) hpyFieldObject);
                    } catch (OverflowException e) {
                        throw CompilerDirectives.shouldNotReachHere(e);
                    }
                } else {
                    try {
                        idx = PInt.intValueExact(lib.asPointer(hpyFieldObject));
                    } catch (InteropException | OverflowException e) {
                        throw CompilerDirectives.shouldNotReachHere(e);
                    }
                }
                if (idx == 0) {
                    return NULL_HANDLE_DELEGATE;
                }
                referent = GraalHPyData.getHPyField(owner, idx);
            }
            return referent;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(true)
    public abstract static class HPyFieldStoreNode extends Node {

        public abstract int execute(Node inliningTarget, PythonObject owner, Object hpyFieldObject, Object referent);

        @Specialization
        static int doHandle(Node inliningTarget, @SuppressWarnings("unused") PythonObject owner, GraalHPyHandle hpyFieldObject, Object referent,
                        @Shared @Cached InlinedConditionProfile nullHandleProfile) {
            int idx = hpyFieldObject.getFieldId();
            if (nullHandleProfile.profile(inliningTarget, referent == NULL_HANDLE_DELEGATE && idx == 0)) {
                // assigning HPy_NULL to a field that already holds HPy_NULL, nothing to do
                return 0;
            } else {
                return GraalHPyData.setHPyField(owner, referent, idx);
            }
        }

        @Specialization(replaces = "doHandle")
        static int doGeneric(Node inliningTarget, PythonObject owner, Object hpyFieldObject, Object referent,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Shared @Cached InlinedConditionProfile nullHandleProfile) {
            int idx;
            if (lib.isNull(hpyFieldObject)) { // uninitialized
                idx = 0;
            } else if (hpyFieldObject instanceof GraalHPyHandle) {
                // avoid `asPointer` message dispatch
                idx = ((GraalHPyHandle) hpyFieldObject).getFieldId();
            } else {
                if (hpyFieldObject instanceof Long) {
                    // branch profile in lib.asPointer
                    try {
                        idx = PInt.intValueExact((Long) hpyFieldObject);
                    } catch (OverflowException e) {
                        throw CompilerDirectives.shouldNotReachHere(e);
                    }
                } else {
                    try {
                        idx = PInt.intValueExact(lib.asPointer(hpyFieldObject));
                    } catch (InteropException | OverflowException e) {
                        throw CompilerDirectives.shouldNotReachHere(e);
                    }
                }
            }
            // TODO: (tfel) do not actually allocate the index / free the existing one when
            // value can be stored as tagged handle
            if (nullHandleProfile.profile(inliningTarget, referent == NULL_HANDLE_DELEGATE && idx == 0)) {
                // assigning HPy_NULL to a field that already holds HPy_NULL, nothing to do
            } else {
                idx = GraalHPyData.setHPyField(owner, referent, idx);
            }
            return idx;
        }
    }

    /**
     * Parses an {@code HPyCallFunction} structure and returns the {@code impl} function pointer. A
     * {@code NULL} pointer will be translated to Java {@code null}.
     *
     * <pre>
     * typedef struct {
     *     cpy_vectorcallfunc cpy_trampoline;
     *     HPyFunc_keywords impl;
     * } HPyCallFunction;
     * </pre>
     */
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class HPyReadCallFunctionNode extends PNodeWithContext {

        public abstract Object execute(Node inliningTarget, GraalHPyContext context, Object def);

        @Specialization
        static Object doIt(GraalHPyContext context, Object def,
                        @CachedLibrary(limit = "2") InteropLibrary resultLib,
                        @Cached PCallHPyFunction callHelperFunctionNode,
                        @Cached HPyAttachFunctionTypeNode attachFunctionTypeNode) {
            // read and check the function pointer
            Object methodFunctionPointer = callHelperFunctionNode.call(context, GRAAL_HPY_CALL_FUNCTION_GET_IMPL, def);
            if (resultLib.isNull(methodFunctionPointer)) {
                return null;
            }
            if (context.isDebugMode() || !resultLib.isExecutable(methodFunctionPointer)) {
                HPySlotWrapper slotWrapper = HPY_TP_CALL.getSignatures()[0];
                methodFunctionPointer = attachFunctionTypeNode.execute(context, methodFunctionPointer, slotWrapper.getLLVMFunctionType());
            }
            return methodFunctionPointer;
        }
    }
}
