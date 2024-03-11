/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_basicsize;
import static com.oracle.graal.python.nodes.StringLiterals.T_EXEC;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject.PInteropSubscriptNode;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.CreateFunctionNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.CreateMethodNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.AsNativePrimitiveNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.ConvertPIntToPrimitiveNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.ReadGenericNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyCAccess.ReadHPyNode;
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
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyCloseHandleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyGetNativeSpacePointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyGetSetSetterHandleCloseNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyKeywordsHandleCloseNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyRaiseNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyRichcmptFuncArgsCloseNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPySSizeObjArgProcCloseNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPySelfHandleCloseNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyTransformExceptionToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyTypeGetNameNodeGen;
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
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor;
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
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBaseClassNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.HasSameConstructorNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToPythonObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.frame.GetCurrentFrameRef;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.PythonOptions;
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
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
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
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedExactClassProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;
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

        @NeverDefault
        public static HPyCallHelperFunctionNode create(GraalHPyContext context) {
            return context.getBackend().createCallHelperFunctionNode();
        }

        public static HPyCallHelperFunctionNode getUncached(GraalHPyContext context) {
            return context.getBackend().getUncachedCallHelperFunctionNode();
        }
    }

    /**
     * Use this node to transform an exception to native if a Python exception was thrown during an
     * upcall and before returning to native code. This node will correctly link to the current
     * frame using the frame reference and tries to avoid any materialization of the frame. The
     * exception is then registered in the native context as the current exception.
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class HPyTransformExceptionToNativeNode extends Node {

        public abstract void execute(Frame frame, Node inliningTarget, GraalHPyContext nativeContext, PException e);

        public final void execute(Node inliningTarget, GraalHPyContext nativeContext, PException e) {
            execute(null, inliningTarget, nativeContext, e);
        }

        public final void execute(Node inliningTarget, PException e) {
            execute(null, inliningTarget, PythonContext.get(this).getHPyContext(), e);
        }

        public static void executeUncached(GraalHPyContext nativeContext, PException e) {
            HPyTransformExceptionToNativeNodeGen.getUncached().execute(null, nativeContext, e);
        }

        public static void executeUncached(PException e) {
            HPyTransformExceptionToNativeNodeGen.getUncached().execute(null, PythonContext.get(null).getHPyContext(), e);
        }

        @Specialization
        static void setCurrentException(Frame frame, Node inliningTarget, GraalHPyContext nativeContext, PException e,
                        @Cached GetCurrentFrameRef getCurrentFrameRef,
                        @Cached GetThreadStateNode getThreadStateNode) {
            // TODO connect f_back
            getCurrentFrameRef.execute(frame, inliningTarget).markAsEscaped();
            PythonThreadState threadState = getThreadStateNode.execute(inliningTarget, nativeContext.getContext());
            threadState.setCurrentException(e);
        }
    }

    @GenerateUncached
    @GenerateInline(false)
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
                        @Bind("this") Node inliningTarget,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("transformExceptionToNativeNode") @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                throw raiseNode.execute(raiseNode, errType, PNone.NO_VALUE, format, arguments);
            } catch (PException p) {
                transformExceptionToNativeNode.execute(frame, inliningTarget, nativeContext, p);
            }
            return errorValue;
        }

        @Specialization
        static Object doObject(Frame frame, GraalHPyContext nativeContext, Object errorValue, PythonBuiltinClassType errType, TruffleString format, Object[] arguments,
                        @Bind("this") Node inliningTarget,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("transformExceptionToNativeNode") @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                throw raiseNode.execute(raiseNode, errType, PNone.NO_VALUE, format, arguments);
            } catch (PException p) {
                transformExceptionToNativeNode.execute(frame, inliningTarget, nativeContext, p);
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

        @NeverDefault
        public static HPyFromCharPointerNode create(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().createFromCharPointerNode();
        }

        public static HPyFromCharPointerNode getUncached(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().getUncachedFromCharPointerNode();
        }
    }

    public abstract static class HPyAsCharPointerNode extends Node {

        public abstract Object execute(GraalHPyContext hpyContext, TruffleString string, Encoding encoding);

        @NeverDefault
        public static HPyAsCharPointerNode create(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().createAsCharPointerNode();
        }

        public static HPyAsCharPointerNode getUncached(GraalHPyContext hpyContext) {
            return hpyContext.getBackend().getUncachedAsCharPointerNode();
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
     *     HPyGlobal **globals;
     * } HPyModuleDef;
     * </pre>
     */
    @GenerateUncached
    @GenerateCached(false)
    @GenerateInline(false) // footprint reduction 108 -> 89
    public abstract static class GraalHPyModuleCreate extends Node {

        private static final TruffleLogger LOGGER = GraalHPyContext.getLogger(GraalHPyModuleCreate.class);

        public abstract Object execute(GraalHPyContext hpyContext, TruffleString mName, Object spec, Object moduleDefPtr);

        @Specialization
        static Object doGeneric(GraalHPyContext context, TruffleString mName, Object spec, Object moduleDefPtr,
                        @Bind("this") Node inliningTarget,
                        @Cached PythonObjectFactory factory,
                        @Cached(parameters = "context") GraalHPyCAccess.ReadPointerNode readPointerNode,
                        @Cached(parameters = "context") GraalHPyCAccess.IsNullNode isNullNode,
                        @Cached(parameters = "context") GraalHPyCAccess.ReadGenericNode readGenericNode,
                        @Cached(parameters = "context") GraalHPyCAccess.WriteSizeTNode writeSizeTNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached WriteAttributeToObjectNode writeAttrNode,
                        @Cached WriteAttributeToPythonObjectNode writeAttrToMethodNode,
                        @Cached HPyCreateFunctionNode addFunctionNode,
                        @Cached CreateMethodNode addLegacyMethodNode,
                        @Cached HPyReadSlotNode readSlotNode,
                        @Cached HPyCheckHandleResultNode checkFunctionResultNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @CachedLibrary(limit = "1") InteropLibrary createLib,
                        @Cached PRaiseNode raiseNode) {

            TruffleString mDoc;
            long size;
            Object docPtr = readPointerNode.read(context, moduleDefPtr, GraalHPyCField.HPyModuleDef__doc);
            if (!isNullNode.execute(context, docPtr)) {
                mDoc = fromCharPointerNode.execute(docPtr);
            } else {
                mDoc = null;
            }

            size = readGenericNode.readLong(context, moduleDefPtr, GraalHPyCField.HPyModuleDef__size);
            if (size < 0) {
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, tsLiteral("HPy does not permit HPyModuleDef.size < 0"));
            } else if (size > 0) {
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, tsLiteral("Module state is not supported yet in HPy, set HPyModuleDef.size = 0 if module state is not needed"));
            }

            // process HPy module slots
            Object moduleDefinesPtr = readPointerNode.read(context, moduleDefPtr, GraalHPyCField.HPyModuleDef__defines);

            List<Object> executeSlots = new LinkedList<>();
            List<Object> methodDefs = new LinkedList<>();
            Object createFunction = null;

            if (!isNullNode.execute(context, moduleDefinesPtr)) {
                for (int i = 0;; i++) {
                    Object def = readPointerNode.readArrayElement(context, moduleDefinesPtr, i);
                    if (isNullNode.execute(context, def)) {
                        break;
                    }
                    int kind = readGenericNode.readInt(context, def, GraalHPyCField.HPyDef__kind);
                    switch (kind) {
                        case GraalHPyDef.HPY_DEF_KIND_METH:
                            methodDefs.add(def);
                            break;
                        case GraalHPyDef.HPY_DEF_KIND_SLOT:
                            HPySlotData slotData = readSlotNode.execute(inliningTarget, context, def);
                            switch (slotData.slot) {
                                case HPY_MOD_CREATE -> {
                                    if (createFunction != null) {
                                        throw raiseNode.raise(PythonErrorType.SystemError, ErrorMessages.MODULE_HAS_MULTIPLE_CREATE_SLOTS, mName);
                                    }
                                    createFunction = slotData.impl;
                                }
                                case HPY_MOD_EXEC -> {
                                    if (createFunction != null) {
                                        throw raiseNode.raise(PythonErrorType.SystemError, ErrorMessages.HPY_DEFINES_CREATE_AND_OTHER_SLOTS, mName);
                                    }
                                    /*
                                     * In contrast to CPython, we already parse and store the
                                     * HPy_mod_exec slots here since parsing is a bit more expensive
                                     * in our case.
                                     */
                                    executeSlots.add(slotData.impl);
                                }
                                default -> throw raiseNode.raise(PythonErrorType.SystemError, ErrorMessages.MODULE_USES_UNKNOW_SLOT_ID, mName, slotData.slot);
                            }
                            break;
                        case GraalHPyDef.HPY_DEF_KIND_MEMBER:
                        case GraalHPyDef.HPY_DEF_KIND_GETSET:
                            // silently ignore
                            LOGGER.warning("get/set definitions are not supported for modules");
                            break;
                        default:
                            if (LOGGER.isLoggable(Level.SEVERE)) {
                                LOGGER.severe(PythonUtils.formatJString("unknown definition kind: %d", kind));
                            }
                            assert false;
                    }
                }
            }

            // determine of 'legacy_methods' is NULL upfront (required for a consistency check)
            Object legacyMethods = readPointerNode.read(context, moduleDefPtr, GraalHPyCField.HPyModuleDef__legacy_methods);
            // the field 'legacy_methods' may be 'NULL'
            boolean hasLegacyMethods = !isNullNode.execute(context, legacyMethods);

            // allocate module's HPyGlobals
            int globalStartIdx = context.getEndIndexOfGlobalTable();
            int nModuleGlobals = initModuleGlobals(context, moduleDefPtr, globalStartIdx, isNullNode, readPointerNode, writeSizeTNode);
            context.initBatchGlobals(globalStartIdx, nModuleGlobals);

            // create the module object
            Object module;
            if (createFunction != null) {
                /*
                 * TODO(fa): this check should be before any other check (and the also test for
                 * 'size > 0')
                 */
                if (hasLegacyMethods || mDoc != null || nModuleGlobals != 0) {
                    throw raiseNode.raise(SystemError, ErrorMessages.HPY_DEFINES_CREATE_AND_NON_DEFAULT);
                }
                module = callCreate(inliningTarget, createFunction, context, spec, checkFunctionResultNode, asHandleNode, createLib);
                if (module instanceof PythonModule) {
                    throw raiseNode.raise(SystemError, ErrorMessages.HPY_MOD_CREATE_RETURNED_BUILTIN_MOD);
                }
            } else {
                PythonModule pmodule = factory.createPythonModule(mName);
                pmodule.setNativeModuleDef(executeSlots);
                module = pmodule;
            }

            // process HPy methods
            for (Object methodDef : methodDefs) {
                PBuiltinFunction fun = addFunctionNode.execute(context, null, methodDef);
                PBuiltinMethod method = factory.createBuiltinMethod(module, fun);
                writeAttrToMethodNode.execute(method, SpecialAttributeNames.T___MODULE__, mName);
                writeAttrNode.execute(module, fun.getName(), method);
            }

            // process legacy methods
            if (hasLegacyMethods) {
                for (int i = 0;; i++) {
                    PBuiltinFunction fun = addLegacyMethodNode.execute(inliningTarget, legacyMethods, i);
                    if (fun == null) {
                        break;
                    }
                    PBuiltinMethod method = factory.createBuiltinMethod(module, fun);
                    writeAttrToMethodNode.execute(method, SpecialAttributeNames.T___MODULE__, mName);
                    writeAttrNode.execute(module, fun.getName(), method);
                }
            }

            if (mDoc != null) {
                writeAttrNode.execute(module, SpecialAttributeNames.T___DOC__, mDoc);
            }

            return module;
        }

        /**
         * Initializes all HPy globals of the currently created module.
         */
        private static int initModuleGlobals(GraalHPyContext hpyContext, Object moduleDefPtr, int startID,
                        GraalHPyCAccess.IsNullNode isNullNode,
                        GraalHPyCAccess.ReadPointerNode readPointerNode,
                        GraalHPyCAccess.WriteSizeTNode writeSizeTNode) {
            Object globalsPtrArr = readPointerNode.read(hpyContext, moduleDefPtr, GraalHPyCField.HPyModuleDef__globals);
            if (!isNullNode.execute(hpyContext, globalsPtrArr)) {
                for (int i = 0;; i++) {
                    Object globalPtr = readPointerNode.readArrayElement(hpyContext, globalsPtrArr, i);
                    if (isNullNode.execute(hpyContext, globalPtr)) {
                        return i;
                    }
                    writeSizeTNode.execute(hpyContext, globalPtr, 0, startID + i);
                }
            }
            return 0;
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
                if (hSpec.isAllocated()) {
                    hSpec.closeAndInvalidate(hPyContext);
                }
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
                        @Cached(inline = false) HPyCheckPrimitiveResultNode checkFunctionResultNode,
                        @Cached(inline = false) HPyAsHandleNode asHandleNode,
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
                if (hModule.isAllocated()) {
                    hModule.closeAndInvalidate(hPyContext);
                }
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
    @GenerateInline(false) // footprint reduction 52 -> 33
    public abstract static class HPyCreateFunctionNode extends PNodeWithContext {

        public abstract PBuiltinFunction execute(GraalHPyContext context, Object enclosingType, Object methodDef);

        @Specialization
        static PBuiltinFunction doIt(GraalHPyContext context, Object enclosingType, Object methodDef,
                        @Cached(parameters = "context") GraalHPyCAccess.ReadPointerNode readPointerNode,
                        @Cached(parameters = "context") GraalHPyCAccess.IsNullNode isNullNode,
                        @Cached(parameters = "context") GraalHPyCAccess.ReadGenericNode readGenericNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached HPyAttachFunctionTypeNode attachFunctionTypeNode,
                        @Cached PythonObjectFactory factory,
                        @Cached WriteAttributeToPythonObjectNode writeAttributeToPythonObjectNode,
                        @Cached PRaiseNode raiseNode) {

            TruffleString methodName = fromCharPointerNode.execute(readPointerNode.read(context, methodDef, GraalHPyCField.HPyDef__meth__name));

            // note: 'ml_doc' may be NULL; in this case, we would store 'None'
            Object methodDoc = PNone.NONE;
            Object doc = readPointerNode.read(context, methodDef, GraalHPyCField.HPyDef__meth__doc);
            if (!isNullNode.execute(context, doc)) {
                methodDoc = fromCharPointerNode.execute(doc, false);
            }

            HPyFuncSignature signature;
            Object methodFunctionPointer;
            signature = HPyFuncSignature.fromValue(readGenericNode.readInt(context, methodDef, GraalHPyCField.HPyDef__meth__signature));
            if (signature == null) {
                throw raiseNode.raise(PythonBuiltinClassType.ValueError, ErrorMessages.UNSUPPORTED_HYPMETH_SIG);
            }

            methodFunctionPointer = readPointerNode.read(context, methodDef, GraalHPyCField.HPyDef__meth__impl);
            methodFunctionPointer = attachFunctionTypeNode.execute(context, methodFunctionPointer, signature.getLLVMFunctionType());

            PBuiltinFunction function = HPyExternalFunctionNodes.createWrapperFunction(PythonLanguage.get(raiseNode), context, signature, methodName, methodFunctionPointer, enclosingType, factory);

            // write doc string; we need to directly write to the storage otherwise it is
            // disallowed writing to builtin types.
            writeAttributeToPythonObjectNode.execute(function, SpecialAttributeNames.T___DOC__, methodDoc);

            return function;
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
    @GenerateInline(false) // footprint reduction 44 -> 25
    public abstract static class HPyAddLegacyGetSetDefNode extends PNodeWithContext {

        public abstract GetSetDescriptor execute(GraalHPyContext context, Object owner, Object legacyGetSetDefArrPtr, int i);

        @Specialization
        static GetSetDescriptor doGeneric(GraalHPyContext context, Object owner, Object legacyGetSetDef, int i,
                        @Cached(parameters = "context") GraalHPyCAccess.ReadPointerNode readPointerNode,
                        @Cached(parameters = "context") GraalHPyCAccess.IsNullNode isNullNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached PythonObjectFactory factory,
                        @Cached WriteAttributeToPythonObjectNode writeDocNode,
                        @Cached PRaiseNode raiseNode) {

            // compute offset of name and read name pointer
            long nameOffset = GraalHPyCAccess.ReadPointerNode.getElementPtr(context, i, HPyContextSignatureType.PyGetSetDef, GraalHPyCField.PyGetSetDef__name);
            Object namePtr = readPointerNode.execute(context, legacyGetSetDef, nameOffset);

            // if the name pointer is null, this is the sentinel
            if (isNullNode.execute(context, namePtr)) {
                return null;
            }
            TruffleString getSetDescrName = fromCharPointerNode.execute(namePtr);

            // compute remaining offsets
            long docOffset = GraalHPyCAccess.ReadPointerNode.getElementPtr(context, i, HPyContextSignatureType.PyGetSetDef, GraalHPyCField.PyGetSetDef__doc);
            long getOffset = GraalHPyCAccess.ReadPointerNode.getElementPtr(context, i, HPyContextSignatureType.PyGetSetDef, GraalHPyCField.PyGetSetDef__get);
            long setOffset = GraalHPyCAccess.ReadPointerNode.getElementPtr(context, i, HPyContextSignatureType.PyGetSetDef, GraalHPyCField.PyGetSetDef__set);
            long closureOffset = GraalHPyCAccess.ReadPointerNode.getElementPtr(context, i, HPyContextSignatureType.PyGetSetDef, GraalHPyCField.PyGetSetDef__closure);

            // note: 'doc' may be NULL; in this case, we would store 'None'
            Object getSetDescrDoc = PNone.NONE;
            Object docPtr = readPointerNode.execute(context, legacyGetSetDef, docOffset);
            if (!isNullNode.execute(context, docPtr)) {
                getSetDescrDoc = fromCharPointerNode.execute(docPtr);
            }

            Object getterFunPtr = readPointerNode.execute(context, legacyGetSetDef, getOffset);
            Object setterFunPtr = readPointerNode.execute(context, legacyGetSetDef, setOffset);
            /*
             * Note: we need to convert the native closure pointer to an interop pointer because it
             * will be handed to a C API root which expects that.
             */
            Object closurePtr = context.nativeToInteropPointer(readPointerNode.execute(context, legacyGetSetDef, closureOffset));

            PythonLanguage lang = PythonLanguage.get(raiseNode);
            PBuiltinFunction getterObject = null;
            if (!isNullNode.execute(context, getterFunPtr)) {
                Object getterFunInteropPtr = CExtContext.ensureExecutable(context.nativeToInteropPointer(getterFunPtr), PExternalFunctionWrapper.GETTER);
                getterObject = HPyLegacyGetSetDescriptorGetterRoot.createLegacyFunction(context, lang, owner, getSetDescrName, getterFunInteropPtr, closurePtr);
            }

            PBuiltinFunction setterObject = null;
            boolean hasSetter = !isNullNode.execute(context, setterFunPtr);
            if (hasSetter) {
                Object setterFunInteropPtr = CExtContext.ensureExecutable(context.nativeToInteropPointer(setterFunPtr), PExternalFunctionWrapper.SETTER);
                setterObject = HPyLegacyGetSetDescriptorSetterRoot.createLegacyFunction(context, lang, owner, getSetDescrName, setterFunInteropPtr, closurePtr);
            }

            GetSetDescriptor getSetDescriptor = factory.createGetSetDescriptor(getterObject, setterObject, getSetDescrName, owner, hasSetter);
            writeDocNode.execute(getSetDescriptor, SpecialAttributeNames.T___DOC__, getSetDescrDoc);
            return getSetDescriptor;
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
            assert key instanceof TruffleString || key instanceof HiddenAttr;
            this.key = key;
            this.value = value;
            this.next = next;
        }

        HPyProperty(TruffleString key, Object value) {
            this(key, value, null);
        }

        void write(Node inliningTarget, WritePropertyNode writePropertyNode, ReadPropertyNode readPropertyNode, Object enclosingType) {
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
                if (!keyExists(inliningTarget, readPropertyNode, enclosingType, prop.key)) {
                    writePropertyNode.execute(inliningTarget, enclosingType, prop.key, prop.value);
                }
            }
        }

        static boolean keyExists(Node inliningTarget, ReadPropertyNode readPropertyNode, Object enclosingType, Object key) {
            return readPropertyNode.execute(inliningTarget, enclosingType, key) != PNone.NO_VALUE;
        }

        static boolean keyExists(ReadAttributeFromObjectNode readAttributeFromObjectNode, Object enclosingType, TruffleString key) {
            return readAttributeFromObjectNode.execute(enclosingType, key) != PNone.NO_VALUE;
        }

    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    abstract static class ReadPropertyNode extends Node {
        abstract Object execute(Node inliningTarget, Object receiver, Object key);

        @Specialization
        static Object doHiddenAttr(Node inliningTarget, PythonAbstractObject receiver, HiddenAttr key,
                        @Cached HiddenAttr.ReadNode readNode) {
            return readNode.execute(inliningTarget, receiver, key, PNone.NO_VALUE);
        }

        @Specialization
        static Object doOther(Object receiver, TruffleString key,
                        @Cached(inline = false) ReadAttributeFromObjectNode readAttributeFromObjectNode) {
            return readAttributeFromObjectNode.execute(receiver, key);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    abstract static class WritePropertyNode extends Node {
        // key comes from HPyProperty#key which is either TruffleString or HiddenAttr
        abstract void execute(Node inliningTarget, Object receiver, Object key, Object value);

        @Specialization
        static void doHiddenAttr(Node inliningTarget, PythonAbstractObject receiver, HiddenAttr key, Object value,
                        @Cached HiddenAttr.WriteNode writeNode) {
            writeNode.execute(inliningTarget, receiver, key, value);
        }

        @Specialization
        static void doString(Object receiver, TruffleString key, Object value,
                        @Cached(inline = false) WriteAttributeToObjectNode writeAttributeToObjectNode) {
            writeAttributeToObjectNode.execute(receiver, key, value);
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
    @GenerateInline(false) // footprint reduction 48 -> 29
    public abstract static class HPyCreateLegacyMemberNode extends PNodeWithContext {

        public abstract HPyProperty execute(GraalHPyContext context, Object enclosingType, Object memberDefArrPtr, int i);

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
        @Specialization
        static HPyProperty doIt(GraalHPyContext context, Object enclosingType, Object memberDefArrPtr, int i,
                        @Cached(parameters = "context") GraalHPyCAccess.ReadPointerNode readPointerNode,
                        @Cached(parameters = "context") GraalHPyCAccess.IsNullNode isNullNode,
                        @Cached(parameters = "context") GraalHPyCAccess.ReadGenericNode readGenericNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached PythonObjectFactory factory,
                        @Cached WriteAttributeToPythonObjectNode writeDocNode,
                        @Cached PRaiseNode raiseNode) {

            // computes offsets like '&(memberDefArrPtr[i].name)'
            int pyMemberDefSize = context.getCTypeSize(HPyContextSignatureType.PyMemberDef);
            long nameOffset = ReadGenericNode.getElementPtr(context, i, pyMemberDefSize, GraalHPyCField.PyMemberDef__name);
            long typeOffset = ReadGenericNode.getElementPtr(context, i, pyMemberDefSize, GraalHPyCField.PyMemberDef__type);
            long offsetOffset = ReadGenericNode.getElementPtr(context, i, pyMemberDefSize, GraalHPyCField.PyMemberDef__offset);
            long flagsOffset = ReadGenericNode.getElementPtr(context, i, pyMemberDefSize, GraalHPyCField.PyMemberDef__flags);
            long docOffset = ReadGenericNode.getElementPtr(context, i, pyMemberDefSize, GraalHPyCField.PyMemberDef__doc);

            Object namePtr = readPointerNode.execute(context, memberDefArrPtr, nameOffset);
            if (isNullNode.execute(context, namePtr)) {
                return null;
            }

            TruffleString name = fromCharPointerNode.execute(namePtr);

            // note: 'doc' may be NULL; in this case, we would store 'None'
            Object memberDoc = PNone.NONE;
            Object doc = readPointerNode.execute(context, memberDefArrPtr, docOffset);
            if (!isNullNode.execute(context, doc)) {
                memberDoc = fromCharPointerNode.execute(doc, false);
            }

            int flags = readGenericNode.executeInt(context, memberDefArrPtr, flagsOffset, HPyContextSignatureType.Int);
            int type = readGenericNode.executeInt(context, memberDefArrPtr, typeOffset, HPyContextSignatureType.Int);
            int offset = readGenericNode.executeInt(context, memberDefArrPtr, offsetOffset, HPyContextSignatureType.Int);

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
        }
    }

    @GenerateUncached
    @GenerateInline(false) // footprint reduction 52 -> 33
    public abstract static class HPyAddMemberNode extends PNodeWithContext {

        public abstract HPyProperty execute(GraalHPyContext context, PythonClass enclosingType, Object memberDef);

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
        @Specialization
        static HPyProperty doIt(GraalHPyContext context, PythonClass enclosingType, Object memberDef,
                        @Cached(parameters = "context") GraalHPyCAccess.ReadPointerNode readPointerNode,
                        @Cached(parameters = "context") GraalHPyCAccess.IsNullNode isNullNode,
                        @Cached(parameters = "context") GraalHPyCAccess.ReadGenericNode readGenericNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached TruffleString.EqualNode equalNode,
                        @Cached PythonObjectFactory factory,
                        @Cached WriteAttributeToPythonObjectNode writeDocNode,
                        @Cached PRaiseNode raiseNode) {

            TruffleString name = fromCharPointerNode.execute(readPointerNode.read(context, memberDef, GraalHPyCField.HPyDef__member__name));

            // note: 'doc' may be NULL; in this case, we would store 'None'
            Object memberDoc = PNone.NONE;
            Object doc = readPointerNode.read(context, memberDef, GraalHPyCField.HPyDef__member__doc);
            if (!isNullNode.execute(context, doc)) {
                memberDoc = fromCharPointerNode.execute(doc, false);
            }

            int type = readGenericNode.readInt(context, memberDef, GraalHPyCField.HPyDef__member__type);
            boolean readOnly = readGenericNode.readInt(context, memberDef, GraalHPyCField.HPyDef__member__readonly) != 0;
            int offset = readGenericNode.readInt(context, memberDef, GraalHPyCField.HPyDef__member__offset);

            if (equalNode.execute(SpecialAttributeNames.T___VECTORCALLOFFSET__, name, TS_ENCODING)) {
                enclosingType.setHPyVectorcallOffset(offset);
            }

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
    @GenerateInline(false) // footprint reduction 44 -> 25
    public abstract static class HPyCreateGetSetDescriptorNode extends PNodeWithContext {

        public abstract GetSetDescriptor execute(GraalHPyContext context, Object type, Object memberDef);

        @Specialization
        static GetSetDescriptor doIt(GraalHPyContext context, Object type, Object memberDef,
                        @Cached(parameters = "context") GraalHPyCAccess.ReadPointerNode readPointerNode,
                        @Cached(parameters = "context") GraalHPyCAccess.IsNullNode isNullNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached HPyAttachFunctionTypeNode attachFunctionTypeNode,
                        @Cached PythonObjectFactory factory,
                        @Cached WriteAttributeToPythonObjectNode writeDocNode) {

            TruffleString name = fromCharPointerNode.execute(readPointerNode.read(context, memberDef, GraalHPyCField.HPyDef__getset__name));

            // note: 'doc' may be NULL; in this case, we would store 'None'
            Object memberDoc = PNone.NONE;
            Object docCharPtr = readPointerNode.read(context, memberDef, GraalHPyCField.HPyDef__getset__doc);
            if (!isNullNode.execute(context, docCharPtr)) {
                memberDoc = fromCharPointerNode.execute(docCharPtr, false);
            }

            Object closurePtr = readPointerNode.read(context, memberDef, GraalHPyCField.HPyDef__getset__closure);

            // signature: self, closure
            Object getterFunctionPtr = readPointerNode.read(context, memberDef, GraalHPyCField.HPyDef__getset__getter_impl);
            boolean hasGetter = !isNullNode.execute(context, getterFunctionPtr);
            if (hasGetter) {
                getterFunctionPtr = attachFunctionTypeNode.execute(context, getterFunctionPtr, LLVMType.HPyFunc_getter);
            }

            // signature: self, value, closure
            Object setterFunctionPtr = readPointerNode.read(context, memberDef, GraalHPyCField.HPyDef__getset__setter_impl);
            boolean hasSetter = !isNullNode.execute(context, setterFunctionPtr);
            if (hasSetter) {
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

        @Specialization
        static HPySlotData doIt(Node inliningTarget, GraalHPyContext context, Object slotDef,
                        @Cached(parameters = "context", inline = false) GraalHPyCAccess.ReadGenericNode readGenericNode,
                        @Cached(parameters = "context", inline = false) GraalHPyCAccess.ReadPointerNode readPointerNode,
                        @Cached(inline = false) HPyAttachFunctionTypeNode attachFunctionTypeNode) {

            int slotNr = readGenericNode.readInt(context, slotDef, GraalHPyCField.HPyDef__slot__slot);
            HPySlot slot = HPySlot.fromValue(slotNr);
            if (slot == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseUncached(inliningTarget, PythonBuiltinClassType.SystemError, ErrorMessages.INVALID_SLOT_VALUE, slotNr);
            }

            // read and check the function pointer
            Object methodFunctionPointer = readPointerNode.read(context, slotDef, GraalHPyCField.HPyDef__slot__impl);
            methodFunctionPointer = attachFunctionTypeNode.execute(context, methodFunctionPointer, slot.getSignatures()[0].getLLVMFunctionType());
            return new HPySlotData(slot, methodFunctionPointer);
        }
    }

    @GenerateUncached
    @GenerateInline(false) // footprint reduction 44 -> 25
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
                    if (methodNames[i] instanceof HiddenAttr ha) {
                        methodNameStr = fromJavaStringNode.execute(ha.getName(), TS_ENCODING);
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
                if (enclosingType.getItemSize() > 0) {
                    throw raiseNode.raise(TypeError, ErrorMessages.HPY_CANNOT_USE_CALL_WITH_VAR_OBJECTS);
                }
                if (enclosingType.getBuiltinShape() == GraalHPyDef.HPyType_BUILTIN_SHAPE_LEGACY && enclosingType.getBasicSize() == 0) {
                    throw raiseNode.raise(TypeError, ErrorMessages.HPY_CANNOT_USE_CALL_WITH_LEGACY);
                }
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
    @GenerateInline(false) // footprint reduction 80 -> 61
    public abstract static class HPyCreateLegacySlotNode extends PNodeWithContext {

        public abstract boolean execute(GraalHPyContext context, Object enclosingType, Object slotDefArrPtr, int i);

        @Specialization
        static boolean doIt(GraalHPyContext context, Object enclosingType, Object slotDefArrPtr, int i,
                        @Bind("this") Node inliningTarget,
                        @Cached(parameters = "context") GraalHPyCAccess.ReadGenericNode readGenericNode,
                        @Cached(parameters = "context") GraalHPyCAccess.ReadPointerNode readPointerNode,
                        @Cached CreateMethodNode legacyMethodNode,
                        @Cached HPyCreateLegacyMemberNode createLegacyMemberNode,
                        @Cached HPyAddLegacyGetSetDefNode legacyGetSetNode,
                        @Cached WriteAttributeToObjectNode writeAttributeToObjectNode,
                        @Cached ReadAttributeFromObjectNode readAttributeToObjectNode,
                        @Cached ReadPropertyNode readPropertyNode,
                        @Cached WritePropertyNode writePropertyNode,
                        @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {

            // computes '&(slotDefArrPtr[i].slot)'
            long slotIdOffset = ReadGenericNode.getElementPtr(context, i, context.getCTypeSize(HPyContextSignatureType.PyType_Slot), GraalHPyCField.PyType_Slot__slot);
            int slotId = readGenericNode.executeInt(context, slotDefArrPtr, slotIdOffset, HPyContextSignatureType.Int);
            if (slotId == 0) {
                return false;
            }

            HPyLegacySlot slot = HPyLegacySlot.fromValue(slotId);
            if (slot == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.SystemError, ErrorMessages.INVALID_SLOT_VALUE, slotId);
            }

            // computes '&(slotDefArrPtr[i].pfunc)'
            long pfuncOffset = ReadGenericNode.getElementPtr(context, i, context.getCTypeSize(HPyContextSignatureType.PyType_Slot), GraalHPyCField.PyType_Slot__pfunc);
            Object pfuncPtr = readPointerNode.execute(context, slotDefArrPtr, pfuncOffset);

            // treatment for special slots 'Py_tp_members', 'Py_tp_getset', 'Py_tp_methods'
            switch (slot) {
                case Py_tp_members:
                    for (int j = 0;; j++) {
                        HPyProperty property = createLegacyMemberNode.execute(context, enclosingType, pfuncPtr, j);
                        if (property == null) {
                            break;
                        }
                        property.write(inliningTarget, writePropertyNode, readPropertyNode, enclosingType);
                    }
                    break;
                case Py_tp_methods:
                    for (int j = 0;; j++) {
                        PBuiltinFunction method = legacyMethodNode.execute(inliningTarget, pfuncPtr, j);
                        if (method == null) {
                            break;
                        }
                        writeAttributeToObjectNode.execute(enclosingType, method.getName(), method);
                    }
                    break;
                case Py_tp_getset:
                    for (int j = 0;; j++) {
                        GetSetDescriptor getSetDescriptor = legacyGetSetNode.execute(context, enclosingType, pfuncPtr, j);
                        if (getSetDescriptor == null) {
                            break;
                        }
                        writeAttributeToObjectNode.execute(enclosingType, getSetDescriptor.getName(), getSetDescriptor);
                    }
                    break;
                default:
                    // this is the generic slot case
                    TruffleString attributeKey = slot.getAttributeKey();
                    if (attributeKey != null) {
                        if (!HPyProperty.keyExists(readAttributeToObjectNode, enclosingType, attributeKey)) {
                            Object interopPFuncPtr = context.nativeToInteropPointer(pfuncPtr);
                            PBuiltinFunction method;
                            Object resolved = CreateFunctionNode.resolveClosurePointer(context.getContext(), interopPFuncPtr, lib);
                            if (resolved instanceof PBuiltinFunction builtinFunction) {
                                method = builtinFunction;
                            } else {
                                Object callable;
                                if (resolved instanceof RootCallTarget || resolved instanceof BuiltinMethodDescriptor) {
                                    callable = resolved;
                                } else {
                                    assert resolved == null;
                                    // the pointer is not a closure pointer, so we assume it is a
                                    // native function pointer
                                    callable = interopPFuncPtr;
                                }
                                PythonLanguage lang = PythonLanguage.get(raiseNode);
                                method = PExternalFunctionWrapper.createWrapperFunction(attributeKey, callable, enclosingType, 0, slot.getSignature(), lang, factory, true);
                            }
                            writeAttributeToObjectNode.execute(enclosingType, attributeKey, method);
                        } else {
                            // TODO(fa): implement support for remaining legacy slot kinds
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            throw CompilerDirectives.shouldNotReachHere(PythonUtils.formatJString("support for legacy slot %s not yet implemented", slot.name()));
                        }
                    }
                    break;
            }
            return true;
        }
    }

    @GenerateUncached
    @GenerateInline(false)
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

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    @ImportStatic(GraalHPyBoxing.class)
    public abstract static class HPyEnsureHandleNode extends HPyWithContextNode {

        public abstract GraalHPyHandle execute(Node inliningTarget, Object object);

        @Specialization
        static GraalHPyHandle doHandle(GraalHPyHandle handle) {
            return handle;
        }

        @Specialization(guards = {"!isLong(value)", "!isHPyHandle(value)", "!isBoxedNullHandle(bits)", "isBoxedHandle(bits)"})
        static GraalHPyHandle doOtherBoxedHandle(Node inliningTarget, @SuppressWarnings("unused") Object value,
                        @Shared("lib") @CachedLibrary(limit = "2") @SuppressWarnings("unused") InteropLibrary lib,
                        @Bind("asPointer(value, lib)") long bits) {
            return doLong(inliningTarget, bits);
        }

        @Specialization(guards = {"!isLong(value)", "!isHPyHandle(value)", "isBoxedNullHandle(bits)"})
        @SuppressWarnings("unused")
        static GraalHPyHandle doOtherNull(Object value,
                        @Shared("lib") @CachedLibrary(limit = "2") InteropLibrary lib,
                        @Bind("asPointer(value, lib)") long bits) {
            return GraalHPyHandle.NULL_HANDLE;
        }

        @Specialization(guards = {"!isLong(value)", "!isHPyHandle(value)", "isBoxedInt(bits) || isBoxedDouble(bits)"})
        static GraalHPyHandle doOtherBoxedPrimitive(Node inliningTarget, @SuppressWarnings("unused") Object value,
                        @Shared("lib") @CachedLibrary(limit = "2") @SuppressWarnings("unused") InteropLibrary lib,
                        @Bind("asPointer(value, lib)") long bits) {
            return doBoxedPrimitive(inliningTarget, bits);
        }

        @Specialization(guards = "isBoxedNullHandle(bits)")
        @SuppressWarnings("unused")
        static GraalHPyHandle doLongNull(long bits) {
            return GraalHPyHandle.NULL_HANDLE;
        }

        @Specialization(guards = {"isBoxedHandle(bits)"}, replaces = "doLongNull")
        static GraalHPyHandle doLong(Node inliningTarget, long bits) {
            GraalHPyContext context = PythonContext.get(inliningTarget).getHPyContext();
            return context.createHandle(context.getObjectForHPyHandle(GraalHPyBoxing.unboxHandle(bits)));
        }

        @Specialization(guards = "isBoxedInt(bits) || isBoxedDouble(bits)")
        @SuppressWarnings("unused")
        static GraalHPyHandle doBoxedPrimitive(Node inliningTarget, long bits) {
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
            return PythonContext.get(inliningTarget).getHPyContext().createHandle(delegate);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    @ImportStatic(GraalHPyBoxing.class)
    public abstract static class HPyCloseHandleNode extends HPyWithContextNode {

        public abstract void execute(Node inliningTarget, Object object);

        public static void executeUncached(Object object) {
            HPyCloseHandleNodeGen.getUncached().execute(null, object);
        }

        @Specialization(guards = "!handle.isAllocated()")
        @SuppressWarnings("unused")
        static void doHandle(GraalHPyHandle handle) {
            // nothing to do
        }

        @Specialization(guards = "handle.isAllocated()")
        static void doHandleAllocated(Node inliningTarget, GraalHPyHandle handle) {
            handle.closeAndInvalidate(PythonContext.get(inliningTarget).getHPyContext());
        }

        @Specialization(guards = "isBoxedNullHandle(bits)")
        @SuppressWarnings("unused")
        static void doNullLong(long bits) {
            // nothing to do
        }

        @Specialization(guards = {"!isBoxedNullHandle(bits)", "isBoxedHandle(bits)"})
        static void doLong(Node inliningTarget, long bits) {
            /*
             * Since we have a long and it is in the "boxed handle" range, we know that the handle
             * *MUST* be allocated.
             */
            int id = GraalHPyBoxing.unboxHandle(bits);
            assert GraalHPyHandle.isAllocated(id);
            PythonContext.get(inliningTarget).getHPyContext().releaseHPyHandleForObject(id);
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
        static void doOther(Node inliningTarget, @SuppressWarnings("unused") Object value,
                        @Shared("lib") @CachedLibrary(limit = "2") @SuppressWarnings("unused") InteropLibrary lib,
                        @Bind("asPointer(value, lib)") long bits) {
            doLong(inliningTarget, bits);
        }

        @Specialization(guards = {"!isLong(value)", "!isHPyHandle(value)", "!isBoxedHandle(bits)"})
        @SuppressWarnings("unused")
        static void doOtherDouble(Object value,
                        @Shared("lib") @CachedLibrary(limit = "2") InteropLibrary lib,
                        @Bind("asPointer(value, lib)") long bits) {
            // nothing to do
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class HPyCloseAndGetHandleNode extends HPyWithContextNode {

        public abstract Object execute(Node inliningTarget, Object object);

        public abstract Object execute(Node inliningTarget, long object);

        @Specialization(guards = "!handle.isAllocated()")
        static Object doHandle(GraalHPyHandle handle) {
            return handle.getDelegate();
        }

        @Specialization(guards = "handle.isAllocated()")
        static Object doHandleAllocated(Node inliningTarget, GraalHPyHandle handle) {
            handle.closeAndInvalidate(PythonContext.get(inliningTarget).getHPyContext());
            return handle.getDelegate();
        }

        @Specialization(guards = "isBoxedNullHandle(bits)")
        @SuppressWarnings("unused")
        static Object doNullLong(long bits) {
            return GraalHPyHandle.NULL_HANDLE_DELEGATE;
        }

        @Specialization(guards = {"!isBoxedNullHandle(bits)", "isBoxedHandle(bits)"})
        static Object doLong(Node inliningTarget, long bits) {
            /*
             * Since we have a long and it is in the "boxed handle" range, we know that the handle
             * *MUST* be allocated.
             */
            int id = GraalHPyBoxing.unboxHandle(bits);
            assert GraalHPyHandle.isAllocated(id);
            GraalHPyContext context = PythonContext.get(inliningTarget).getHPyContext();
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
        static Object doOther(Node inliningTarget, @SuppressWarnings("unused") Object value,
                        @Shared("lib") @CachedLibrary(limit = "2") @SuppressWarnings("unused") InteropLibrary lib,
                        @Bind("asPointer(value, lib)") long bits) {
            return doLong(inliningTarget, bits);
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
    @GenerateInline(false)
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
    @GenerateInline(false)
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
    @GenerateInline(false)
    public abstract static class HPyAsNativeInt64Node extends CExtToNativeNode {

        // Adding specializations for primitives does not make a lot of sense just to avoid
        // un-/boxing in the interpreter since interop will force un-/boxing anyway.
        @Specialization
        static Object doGeneric(Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached ConvertPIntToPrimitiveNode asNativePrimitiveNode) {
            return asNativePrimitiveNode.execute(inliningTarget, value, 1, Long.BYTES);
        }
    }

    public abstract static class HPyConvertArgsToSulongNode extends PNodeWithContext {

        public abstract void executeInto(VirtualFrame frame, Object[] args, int argsOffset, Object[] dest, int destOffset);

        abstract HPyCloseArgHandlesNode createCloseHandleNode();
    }

    public abstract static class HPyCloseArgHandlesNode extends PNodeWithContext {

        public abstract void executeInto(VirtualFrame frame, Object[] args, int argsOffset);
    }

    @GenerateInline(false)
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
    @GenerateInline(false)
    public abstract static class HPyVarargsHandleCloseNode extends HPyCloseArgHandlesNode {

        @Specialization
        static void doConvert(Object[] dest, int destOffset,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyCloseHandleNode closeHandleNode) {
            closeHandleNode.execute(inliningTarget, dest[destOffset]);
        }
    }

    /**
     * Always closes parameter at position {@code destOffset} (assuming that it is a handle).
     */
    @GenerateInline(false)
    public abstract static class HPySelfHandleCloseNode extends HPyCloseArgHandlesNode {

        @Specialization
        static void doConvert(Object[] dest, int destOffset,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyCloseHandleNode closeHandleNode) {
            closeHandleNode.execute(inliningTarget, dest[destOffset]);
        }
    }

    @GenerateInline(false)
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
    @GenerateInline(false)
    public abstract static class HPyKeywordsHandleCloseNode extends HPyCloseArgHandlesNode {

        @Specialization
        static void doConvert(Object[] dest, int destOffset,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyCloseHandleNode closeFirstHandleNode,
                        @Cached HPyCloseHandleNode closeSecondHandleNode) {
            closeFirstHandleNode.execute(inliningTarget, dest[destOffset]);
            closeSecondHandleNode.execute(inliningTarget, dest[destOffset + 3]);
        }
    }

    @GenerateInline(false)
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
                        @Shared @Cached HPyAsHandleNode toSulongNode) {
            CompilerAsserts.partialEvaluationConstant(destOffset);
            for (int i = 0; i < cachedLength - argsOffset; i++) {
                dest[destOffset + i] = toSulongNode.execute(args[argsOffset + i]);
            }
        }

        @Specialization(replaces = {"cached0", "cachedLoop"})
        static void uncached(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Shared @Cached HPyAsHandleNode toSulongNode) {
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
    @GenerateInline(false)
    public abstract static class HPyAllHandleCloseNode extends HPyCloseArgHandlesNode {

        @Specialization(guards = {"dest.length == destOffset"})
        @SuppressWarnings("unused")
        static void cached0(Object[] dest, int destOffset) {
        }

        @Specialization(guards = {"dest.length == cachedLength", "isLeArgsOffsetPlus(cachedLength, destOffset, 8)"}, limit = "1", replaces = "cached0")
        @ExplodeLoop
        static void cachedLoop(Object[] dest, int destOffset,
                        @Bind("this") Node inliningTarget,
                        @Cached("dest.length") int cachedLength,
                        @Shared @Cached HPyCloseHandleNode closeHandleNode) {
            CompilerAsserts.partialEvaluationConstant(destOffset);
            for (int i = 0; i < cachedLength - destOffset; i++) {
                closeHandleNode.execute(inliningTarget, dest[destOffset + i]);
            }
        }

        @Specialization(replaces = {"cached0", "cachedLoop"})
        static void uncached(Object[] dest, int destOffset,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached HPyCloseHandleNode closeHandleNode) {
            int len = dest.length;
            for (int i = 0; i < len - destOffset; i++) {
                closeHandleNode.execute(inliningTarget, dest[destOffset + i]);
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
    @GenerateInline(false)
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
    @GenerateInline(false)
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
    @GenerateInline(false)
    public abstract static class HPyGetSetSetterHandleCloseNode extends HPyCloseArgHandlesNode {

        @Specialization
        static void doConvert(Object[] dest, int destOffset,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyCloseHandleNode closeFirstHandleNode,
                        @Cached HPyCloseHandleNode closeSecondHandleNode) {
            closeFirstHandleNode.execute(inliningTarget, dest[destOffset]);
            closeSecondHandleNode.execute(inliningTarget, dest[destOffset + 1]);
        }
    }

    /**
     * Converts {@code self} to an HPy handle and any other argument to {@code HPy_ssize_t}.
     */
    @GenerateInline(false)
    public abstract static class HPySSizeArgFuncToSulongNode extends HPyConvertArgsToSulongNode {

        @Specialization(guards = {"isArity(args.length, argsOffset, 2)"})
        static void doHandleSsizeT(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached HPyAsHandleNode asHandleNode,
                        @Shared @Cached ConvertPIntToPrimitiveNode asSsizeTNode) {
            CompilerAsserts.partialEvaluationConstant(argsOffset);
            dest[destOffset] = asHandleNode.execute(args[argsOffset]);
            dest[destOffset + 1] = asSsizeTNode.execute(inliningTarget, args[argsOffset + 1], 1, Long.BYTES);
        }

        @Specialization(guards = {"isArity(args.length, argsOffset, 3)"})
        static void doHandleSsizeTSsizeT(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached HPyAsHandleNode asHandleNode,
                        @Shared @Cached ConvertPIntToPrimitiveNode asSsizeTNode) {
            CompilerAsserts.partialEvaluationConstant(argsOffset);
            dest[destOffset] = asHandleNode.execute(args[argsOffset]);
            dest[destOffset + 1] = asSsizeTNode.execute(inliningTarget, args[argsOffset + 1], 1, Long.BYTES);
            dest[destOffset + 2] = asSsizeTNode.execute(inliningTarget, args[argsOffset + 2], 1, Long.BYTES);
        }

        @Specialization(replaces = {"doHandleSsizeT", "doHandleSsizeTSsizeT"})
        static void doGeneric(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached HPyAsHandleNode asHandleNode,
                        @Shared @Cached ConvertPIntToPrimitiveNode asSsizeTNode) {
            dest[destOffset] = asHandleNode.execute(args[argsOffset]);
            for (int i = 1; i < args.length - argsOffset; i++) {
                dest[destOffset + i] = asSsizeTNode.execute(inliningTarget, args[argsOffset + i], 1, Long.BYTES);
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
    @GenerateInline(false)
    public abstract static class HPySSizeObjArgProcToSulongNode extends HPyConvertArgsToSulongNode {

        @Specialization
        static void doConvert(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached ConvertPIntToPrimitiveNode asSsizeTNode) {
            CompilerAsserts.partialEvaluationConstant(argsOffset);
            dest[destOffset] = asHandleNode.execute(args[argsOffset]);
            dest[destOffset + 1] = asSsizeTNode.execute(inliningTarget, args[argsOffset + 1], 1, Long.BYTES);
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
    @GenerateInline(false)
    public abstract static class HPySSizeObjArgProcCloseNode extends HPyCloseArgHandlesNode {

        @Specialization
        static void doConvert(Object[] dest, int destOffset,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyCloseHandleNode closeFirstHandleNode,
                        @Cached HPyCloseHandleNode closeSecondHandleNode) {
            closeFirstHandleNode.execute(inliningTarget, dest[destOffset]);
            closeSecondHandleNode.execute(inliningTarget, dest[destOffset + 2]);
        }
    }

    /**
     * Converts arguments for C function signature
     * {@code HPy (*HPyFunc_richcmpfunc)(HPyContext ctx, HPy, HPy, HPy_RichCmpOp);}.
     */
    @GenerateInline(false)
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
    @GenerateInline(false)
    public abstract static class HPyRichcmptFuncArgsCloseNode extends HPyCloseArgHandlesNode {

        @Specialization
        static void doConvert(Object[] dest, int destOffset,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyCloseHandleNode closeFirstHandleNode,
                        @Cached HPyCloseHandleNode closeSecondHandleNode) {
            closeFirstHandleNode.execute(inliningTarget, dest[destOffset]);
            closeSecondHandleNode.execute(inliningTarget, dest[destOffset + 1]);
        }
    }

    /**
     * Converts for C function signature
     * {@code int (*HPyFunc_getbufferproc)(HPyContext ctx, HPy self, HPy_buffer *buffer, int flags)}
     * .
     */
    @GenerateInline(false)
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
    @GenerateInline(false)
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
                        @Shared("factory") @Cached(inline = false) PythonObjectFactory factory) {
            return factory.createInt(convertToBigInteger(n));
        }

        @TruffleBoundary
        private static BigInteger convertToBigInteger(long n) {
            return BigInteger.valueOf(n).add(BigInteger.ONE.shiftLeft(Long.SIZE));
        }

        @Specialization
        static Object doPointer(PythonNativeObject n, @SuppressWarnings("unused") boolean signed,
                        @Shared("factory") @Cached(inline = false) PythonObjectFactory factory) {
            return factory.createNativeVoidPtr(n.getPtr());
        }
    }

    /**
     * Represents {@code HPyType_SpecParam}.
     */
    @ValueType
    record HPyTypeSpecParam(int kind, Object object) {
    };

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
    @GenerateInline(false) // footprint reduction 196 -> 180
    abstract static class HPyCreateTypeFromSpecNode extends Node {

        private static final TruffleLogger LOGGER = GraalHPyContext.getLogger(HPyCreateTypeFromSpecNode.class);
        static final TruffleString T_PYTRUFFLE_CREATETYPE = tsLiteral("PyTruffle_CreateType");

        abstract Object execute(GraalHPyContext context, Object typeSpec, Object typeSpecParamArray);

        @Specialization
        static Object doGeneric(GraalHPyContext context, Object typeSpec, Object typeSpecParamArray,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") DynamicObjectLibrary dylib,
                        @Cached(parameters = "context") GraalHPyCAccess.ReadPointerNode readPointerNode,
                        @Cached(parameters = "context") GraalHPyCAccess.AllocateNode allocateNode,
                        @Cached(parameters = "context") GraalHPyCAccess.ReadI32Node readI32Node,
                        @Cached(parameters = "context") GraalHPyCAccess.IsNullNode isNullNode,
                        @Cached(parameters = "context") HPyAsCharPointerNode asCharPointerNode,
                        @Cached HPyTypeSplitNameNode splitName,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached PythonObjectFactory factory,
                        @Cached IsTypeNode isTypeNode,
                        @Cached HasSameConstructorNode hasSameConstructorNode,
                        @Cached CStructAccess.ReadI64Node getMetaSizeNode,
                        @Cached WriteAttributeToObjectNode writeAttributeToObjectNode,
                        @Cached ReadPropertyNode readPropertyNode,
                        @Cached WritePropertyNode writePropertyNode,
                        @Cached PyObjectCallMethodObjArgs callCreateTypeNode,
                        @Cached HPyCreateFunctionNode addFunctionNode,
                        @Cached HPyAddMemberNode addMemberNode,
                        @Cached HPyCreateSlotNode addSlotNode,
                        @Cached HPyCreateLegacySlotNode createLegacySlotNode,
                        @Cached HPyCreateGetSetDescriptorNode createGetSetDescriptorNode,
                        @Cached GetBaseClassNode getBaseClassNode,
                        @Cached(parameters = "New") LookupCallableSlotInMRONode lookupNewNode,
                        @Cached PRaiseNode raiseNode) {

            try {
                // the name as given by the specification
                TruffleString specName = fromCharPointerNode.execute(readPointerNode.read(context, typeSpec, GraalHPyCField.HPyType_Spec__name), false);

                // extract module and type name
                TruffleString[] names = splitName.execute(inliningTarget, specName);
                assert names.length == 2;

                Object tpName = asCharPointerNode.execute(context, names[1], Encoding.UTF_8);

                PDict namespace;
                Object doc = readPointerNode.read(context, typeSpec, GraalHPyCField.HPyType_Spec__doc);
                if (!isNullNode.execute(context, doc)) {
                    TruffleString docString = fromCharPointerNode.execute(doc);
                    namespace = factory.createDict(new PKeyword[]{new PKeyword(SpecialAttributeNames.T___DOC__, docString)});
                } else {
                    namespace = factory.createDict();
                }

                HPyTypeSpecParam[] typeSpecParams = extractTypeSpecParams(context, typeSpecParamArray);

                // extract bases from type spec params
                PTuple bases = extractBases(typeSpecParams, factory);
                // extract metaclass from type spec params
                Object metatype = getMetatype(typeSpecParams, raiseNode);

                if (metatype != null) {
                    if (!isTypeNode.execute(inliningTarget, metatype)) {
                        throw raiseNode.raise(TypeError, ErrorMessages.HPY_METACLASS_IS_NOT_A_TYPE, metatype);
                    }
                    if (!hasSameConstructorNode.execute(inliningTarget, metatype, PythonBuiltinClassType.PythonClass)) {
                        throw raiseNode.raise(TypeError, ErrorMessages.HPY_METACLASS_WITH_CUSTOM_CONS_NOT_SUPPORTED);
                    }
                }

                // create the type object
                PythonModule pythonCextModule = PythonContext.get(inliningTarget).lookupBuiltinModule(BuiltinNames.T___GRAALPYTHON__);
                PythonClass newType = (PythonClass) callCreateTypeNode.execute(null, inliningTarget, pythonCextModule, T_PYTRUFFLE_CREATETYPE,
                                names[1], bases, namespace, metatype != null ? metatype : PythonBuiltinClassType.PythonClass);
                // allocate additional memory for the metatype and set it
                long metaBasicSize = 0;
                Object destroyFunc = null;
                if (metatype instanceof PythonClass metaclass) {
                    // get basicsize of metatype and allocate it into
                    // GraalHPyDef.OBJECT_HPY_NATIVE_SPACE
                    metaBasicSize = metaclass.getBasicSize();
                    destroyFunc = metaclass.getHPyDestroyFunc();
                } else if (metatype instanceof PythonAbstractNativeObject nativeObject) {
                    // This path is implemented only for completeness,
                    // but is not expected to happen often, hence the
                    // uncached nodes, no profiling and potential leak
                    metaBasicSize = getMetaSizeNode.readFromObj(nativeObject, PyTypeObject__tp_basicsize);
                }
                if (metaBasicSize > 0) {
                    Object dataPtr = allocateNode.calloc(context, 1, metaBasicSize);
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
                long flags = readI32Node.readUnsigned(context, typeSpec, GraalHPyCField.HPyType_Spec__flags);
                int builtinShape = readI32Node.read(context, typeSpec, GraalHPyCField.HPyType_Spec__builtin_shape);
                if (!GraalHPyDef.isValidBuiltinShape(builtinShape)) {
                    throw raiseNode.raise(ValueError, ErrorMessages.HPY_INVALID_BUILTIN_SHAPE, builtinShape);
                }

                long basicSize = readI32Node.read(context, typeSpec, GraalHPyCField.HPyType_Spec__basicsize);
                long itemSize = readI32Node.read(context, typeSpec, GraalHPyCField.HPyType_Spec__itemsize);
                newType.setHPyTypeExtra(new HPyTypeExtra(flags, basicSize, itemSize, tpName, builtinShape));
                newType.makeStaticBase(dylib);

                boolean seenNew = false;
                boolean needsTpTraverse = ((flags & GraalHPyDef.HPy_TPFLAGS_HAVE_GC) != 0);

                // process defines
                Object defines = readPointerNode.read(context, typeSpec, GraalHPyCField.HPyType_Spec__defines);
                // field 'defines' may be 'NULL'
                if (!isNullNode.execute(context, defines)) {
                    for (long i = 0;; i++) {
                        Object def = readPointerNode.readArrayElement(context, defines, i);
                        if (isNullNode.execute(context, def)) {
                            break;
                        }
                        HPyProperty property = null;
                        int kind = readI32Node.read(context, def, GraalHPyCField.HPyDef__kind);
                        switch (kind) {
                            case GraalHPyDef.HPY_DEF_KIND_METH:
                                PBuiltinFunction fun = addFunctionNode.execute(context, newType, def);
                                property = new HPyProperty(fun.getName(), fun);
                                break;
                            case GraalHPyDef.HPY_DEF_KIND_SLOT:
                                Object addSlotResult = addSlotNode.execute(context, newType, def);
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
                                property = addMemberNode.execute(context, newType, def);
                                break;
                            case GraalHPyDef.HPY_DEF_KIND_GETSET:
                                GetSetDescriptor getSetDescriptor = createGetSetDescriptorNode.execute(context, newType, def);
                                property = new HPyProperty(getSetDescriptor.getName(), getSetDescriptor);
                                break;
                            default:
                                if (LOGGER.isLoggable(Level.SEVERE)) {
                                    LOGGER.severe(PythonUtils.formatJString("unknown definition kind: %d", kind));
                                }
                                assert false;
                        }

                        if (property != null) {
                            property.write(inliningTarget, writePropertyNode, readPropertyNode, newType);
                        }
                    }
                }

                /*
                 * Enforce constraint that we cannot have slot 'HPy_tp_call' and an explicit member
                 * '__vectorcalloffset__'.
                 */
                if (newType.getHPyVectorcallOffset() != Long.MIN_VALUE && newType.getHPyDefaultCallFunc() != null) {
                    throw raiseNode.raise(TypeError, ErrorMessages.HPY_CANNOT_HAVE_CALL_AND_VECTORCALLOFFSET);
                }

                if (needsTpTraverse) {
                    throw raiseNode.raise(ValueError, ErrorMessages.TRAVERSE_FUNCTION_NEEDED);
                }

                // process legacy slots; this is of type 'cpy_PyTypeSlot legacy_slots[]'
                Object legacySlotsArrPtr = readPointerNode.read(context, typeSpec, GraalHPyCField.HPyType_Spec__legacy_slots);
                if (!isNullNode.execute(context, legacySlotsArrPtr)) {
                    if (builtinShape != GraalHPyDef.HPyType_BUILTIN_SHAPE_LEGACY) {
                        throw raiseNode.raise(TypeError, ErrorMessages.HPY_CANNOT_SPECIFY_LEG_SLOTS_WO_SETTING_LEG);
                    }
                    for (int i = 0;; i++) {
                        if (!createLegacySlotNode.execute(context, newType, legacySlotsArrPtr, i)) {
                            break;
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
                Object baseClass = getBaseClassNode.execute(inliningTarget, newType);
                if (!seenNew && (basicSize > 0 || newType.getHPyDefaultCallFunc() != null)) {

                    /*
                     * TODO(fa): we could do some shortcut if 'baseClass == PythonObject' and use
                     * 'inheritedConstruct = null' but that needs to be considered in the decorating
                     * new as well
                     */
                    // Lookup the inherited constructor and pass it to the HPy decorator.
                    Object inheritedConstructor = lookupNewNode.execute(baseClass);
                    PBuiltinFunction constructorDecorator = HPyObjectNewNode.createBuiltinFunction(PythonLanguage.get(raiseNode), inheritedConstructor, builtinShape);
                    writeAttributeToObjectNode.execute(newType, SpecialMethodNames.T___NEW__, constructorDecorator);
                }

                long baseFlags;
                if (baseClass instanceof PythonClass pythonBaseClass) {
                    baseFlags = pythonBaseClass.getFlags();
                } else {
                    baseFlags = 0;
                }
                int baseBuiltinShape = GraalHPyDef.getBuiltinShapeFromHiddenAttribute(baseClass);
                checkInheritanceConstraints(flags, baseFlags, builtinShape, baseBuiltinShape > GraalHPyDef.HPyType_BUILTIN_SHAPE_LEGACY, raiseNode);
                return newType;
            } catch (CannotCastException e) {
                throw raiseNode.raise(SystemError, ErrorMessages.COULD_NOT_CREATE_TYPE_FROM_SPEC_BECAUSE, e);
            }
        }

        /**
         * Read the array of {@code HPyType_SpecParam} and convert to a Java array of
         * {@link HPyTypeSpecParam}.
         *
         * <pre>
         *     typedef struct {
         *         HPyType_SpecParam_Kind kind;
         *         HPy object;
         *     } HPyType_SpecParam;
         * </pre>
         */
        @TruffleBoundary
        private static HPyTypeSpecParam[] extractTypeSpecParams(GraalHPyContext context, Object typeSpecParamArray) {

            // if the pointer is NULL, no bases have been explicitly specified
            if (GraalHPyCAccess.IsNullNode.executeUncached(context, typeSpecParamArray)) {
                return null;
            }

            GraalHPyCAccess.ReadI32Node readI32Node = GraalHPyCAccess.ReadI32Node.getUncached(context);
            GraalHPyCAccess.ReadHPyNode readHPyNode = GraalHPyCAccess.ReadHPyNode.getUncached(context);

            long specParamSize = context.getCTypeSize(HPyContextSignatureType.HPyType_SpecParam);

            List<HPyTypeSpecParam> specParams = new LinkedList<>();
            for (int i = 0;; i++) {
                long specParamKindOffset = ReadHPyNode.getElementPtr(context, i, specParamSize, GraalHPyCField.HPyType_SpecParam__kind);
                int specParamKind = readI32Node.readOffset(context, typeSpecParamArray, specParamKindOffset);
                if (specParamKind == 0) {
                    break;
                }
                long specParamObjectOffset = ReadHPyNode.getElementPtr(context, i, specParamSize, GraalHPyCField.HPyType_SpecParam__object);
                Object specParamObject = readHPyNode.read(context, typeSpecParamArray, specParamObjectOffset);

                specParams.add(new HPyTypeSpecParam(specParamKind, specParamObject));
            }
            return specParams.toArray(new HPyTypeSpecParam[0]);
        }

        /**
         * Extract bases from the array of type spec params. Reference implementation can be found
         * in {@code ctx_type.c:build_bases_from_params}.
         *
         * @return The bases tuple or {@code null} in case of an error.
         */
        @TruffleBoundary
        private static PTuple extractBases(HPyTypeSpecParam[] typeSpecParams, PythonObjectFactory factory) {

            // if there are no type spec params, no bases have been explicitly specified
            if (typeSpecParams == null) {
                return factory.createEmptyTuple();
            }

            ArrayList<Object> basesList = new ArrayList<>();
            for (HPyTypeSpecParam typeSpecParam : typeSpecParams) {
                switch (typeSpecParam.kind()) {
                    case GraalHPyDef.HPyType_SPEC_PARAM_BASE:
                        // In this case, the 'specParamObject' is a single handle. We add it to
                        // the list of bases.
                        assert PGuards.isClassUncached(typeSpecParam.object()) : "base object is not a Python class";
                        basesList.add(typeSpecParam.object());
                        break;
                    case GraalHPyDef.HPyType_SPEC_PARAM_BASES_TUPLE:
                        // In this case, the 'specParamObject' is tuple. According to the
                        // reference implementation, we immediately use this tuple and throw
                        // away any other single base classes or subsequent params.
                        assert PGuards.isPTuple(typeSpecParam.object()) : "type spec param claims to be a tuple but isn't";
                        return (PTuple) typeSpecParam.object();
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
        private static Object getMetatype(HPyTypeSpecParam[] typeSpecParams, PRaiseNode raiseNode) {
            Object result = null;
            if (typeSpecParams != null) {
                for (HPyTypeSpecParam typeSpecParam : typeSpecParams) {
                    if (typeSpecParam.kind() == GraalHPyDef.HPyType_SPEC_PARAM_METACLASS) {
                        if (result != null) {
                            throw raiseNode.raise(ValueError, ErrorMessages.HPY_METACLASS_SPECIFIED_MULTIPLE_TIMES);
                        }
                        result = typeSpecParam.object();
                        if (!IsTypeNode.executeUncached(result)) {
                            throw raiseNode.raise(TypeError, ErrorMessages.HPY_METACLASS_IS_NOT_A_TYPE, result);
                        }
                    }
                }
            }
            return result;
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
                        @Cached(inline = false) TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached(inline = false) TruffleString.LastIndexOfCodePointNode indexOfCodepointNode,
                        @Cached(inline = false) TruffleString.SubstringNode substringNode,
                        @Cached(inline = false) TruffleString.CodePointLengthNode lengthNode) {
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
    @GenerateInline
    @GenerateCached(false)
    public abstract static class HPyTypeGetNameNode extends Node {

        public static Object executeUncached(GraalHPyContext ctx, Object object) {
            return HPyTypeGetNameNodeGen.getUncached().execute(null, ctx, object);
        }

        public abstract Object execute(Node inliningTarget, GraalHPyContext ctx, Object object);

        @Specialization(guards = "tpName != null")
        static Object doTpName(@SuppressWarnings("unused") GraalHPyContext ctx, @SuppressWarnings("unused") PythonClass clazz,
                        @Bind("clazz.getTpName()") Object tpName) {
            return tpName;
        }

        @Specialization(replaces = "doTpName")
        static Object doGeneric(Node inliningTarget, GraalHPyContext ctx, Object type,
                        @Cached GetNameNode getName,
                        @Cached(parameters = "ctx", inline = false) HPyAsCharPointerNode asCharPointerNode) {
            if (type instanceof PythonClass pythonClass && pythonClass.getTpName() != null) {
                return pythonClass.getTpName();
            }
            TruffleString baseName = getName.execute(inliningTarget, type);
            return asCharPointerNode.execute(ctx, baseName, Encoding.UTF_8);
        }
    }

    @GenerateInline(inlineByDefault = true)
    @GenerateCached
    @GenerateUncached
    @ImportStatic(PGuards.class)
    public abstract static class HPyGetNativeSpacePointerNode extends Node {

        public abstract Object execute(Node inliningTarget, Object object);

        public final Object executeCached(Object object) {
            return execute(this, object);
        }

        public static Object executeUncached(Object object) {
            return HPyGetNativeSpacePointerNodeGen.getUncached().execute(null, object);
        }

        @Specialization
        static Object doPythonObject(PythonObject object) {
            return GraalHPyData.getHPyNativeSpace(object);
        }

        @Fallback
        static Object doOther(Node inliningTarget, @SuppressWarnings("unused") Object object) {
            // TODO(fa): this should be a backend-specific value
            return PythonContext.get(inliningTarget).getNativeNull();
        }
    }

    public abstract static class HPyAttachFunctionTypeNode extends PNodeWithContext {
        public abstract Object execute(GraalHPyContext hpyContext, Object pointerObject, LLVMType llvmFunctionType);

        @NeverDefault
        public static HPyAttachFunctionTypeNode create() {
            PythonLanguage language = PythonLanguage.get(null);
            switch (language.getEngineOption(PythonOptions.HPyBackend)) {
                case JNI:
                    if (!PythonOptions.WITHOUT_JNI) {
                        return HPyAttachJNIFunctionTypeNodeGen.create();
                    }
                    throw CompilerDirectives.shouldNotReachHere();
                case LLVM:
                    return HPyLLVMAttachFunctionTypeNode.UNCACHED;
                case NFI:
                    return HPyAttachNFIFunctionTypeNodeGen.create();
            }
            throw CompilerDirectives.shouldNotReachHere();
        }

        public static HPyAttachFunctionTypeNode getUncached() {
            PythonLanguage language = PythonLanguage.get(null);
            switch (language.getEngineOption(PythonOptions.HPyBackend)) {
                case JNI:
                    if (!PythonOptions.WITHOUT_JNI) {
                        return HPyAttachJNIFunctionTypeNodeGen.getUncached();
                    }
                    throw CompilerDirectives.shouldNotReachHere();
                case LLVM:
                    return HPyLLVMAttachFunctionTypeNode.UNCACHED;
                case NFI:
                    return HPyAttachNFIFunctionTypeNodeGen.getUncached();
            }
            throw CompilerDirectives.shouldNotReachHere();
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
    @GenerateInline(false)
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
                        @Shared @CachedLibrary(limit = "1") SignatureLibrary signatureLibrary) {
            return signatureLibrary.bind(nfiSignatureCt.call(), pointerObject);
        }

        @Specialization(replaces = {"doCachedSingleContext", "doCached"})
        static Object doGeneric(GraalHPyContext hpyContext, Object pointerObject, LLVMType llvmFunctionType,
                        @Shared @CachedLibrary(limit = "1") SignatureLibrary signatureLibrary) {
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
                case HPyFunc_traverseproc:
                    return "(POINTER, POINTER, POINTER): SINT32";
                case HPyFunc_destroyfunc:
                    return "(POINTER): VOID";
            }
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    /**
     */
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class HPyAttachJNIFunctionTypeNode extends HPyAttachFunctionTypeNode {

        @Specialization
        static GraalHPyJNIFunctionPointer doLong(GraalHPyContext hpyContext, long pointer, LLVMType llvmFunctionType) {
            return new GraalHPyJNIFunctionPointer(pointer, llvmFunctionType, hpyContext.getCurrentMode());
        }

        @Specialization
        static GraalHPyJNIFunctionPointer doGeneric(GraalHPyContext hpyContext, Object pointerObject, LLVMType llvmFunctionType,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary) {
            long pointer;
            if (pointerObject instanceof Long pointerLong) {
                pointer = pointerLong;
            } else {
                if (!interopLibrary.isPointer(pointerObject)) {
                    interopLibrary.toNative(pointerObject);
                }
                try {
                    pointer = interopLibrary.asPointer(pointerObject);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }
            return new GraalHPyJNIFunctionPointer(pointer, llvmFunctionType, hpyContext.getCurrentMode());
        }
    }

    public static final class HPyLLVMAttachFunctionTypeNode extends HPyAttachFunctionTypeNode {

        private static final HPyLLVMAttachFunctionTypeNode UNCACHED = new HPyLLVMAttachFunctionTypeNode();

        @Override
        public Object execute(GraalHPyContext hpyContext, Object pointerObject, LLVMType llvmFunctionType) {
            assert InteropLibrary.getUncached().isExecutable(pointerObject);
            return pointerObject;
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.MONOMORPHIC;
        }

        @Override
        public boolean isAdoptable() {
            return false;
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
    @GenerateInline(false) // footprint reduction 60 -> 41
    public abstract static class RecursiveExceptionMatches extends Node {
        abstract int execute(GraalHPyContext context, Object err, Object exc);

        @Specialization
        static int tuple(GraalHPyContext context, Object err, PTuple exc,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached RecursiveExceptionMatches recExcMatch,
                        @Exclusive @Cached PInteropSubscriptNode getItemNode,
                        @Exclusive @Cached InlinedLoopConditionProfile loopProfile) {
            int len = exc.getSequenceStorage().length();
            for (int i = 0; loopProfile.profile(inliningTarget, i < len); i++) {
                Object e = getItemNode.execute(exc, i);
                if (recExcMatch.execute(context, err, e) != 0) {
                    return 1;
                }
            }
            return 0;
        }

        @Specialization(guards = {"!isPTuple(exc)", "isTupleSubtype(inliningTarget, exc, getClassNode, isSubtypeNode)"}, limit = "1")
        static int subtuple(GraalHPyContext context, Object err, Object exc,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached RecursiveExceptionMatches recExcMatch,
                        @SuppressWarnings("unused") @Exclusive @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Shared @Cached ReadAttributeFromObjectNode readAttr,
                        @Shared @Cached CallNode callNode,
                        @Cached CastToJavaIntExactNode cast,
                        @Exclusive @Cached PInteropSubscriptNode getItemNode,
                        @Exclusive @Cached InlinedLoopConditionProfile loopProfile) {
            int len = cast.execute(inliningTarget, callBuiltinFunction(context, BuiltinNames.T_LEN, new Object[]{exc}, readAttr, callNode));
            for (int i = 0; loopProfile.profile(inliningTarget, i < len); i++) {
                Object e = getItemNode.execute(exc, i);
                if (recExcMatch.execute(context, err, e) != 0) {
                    return 1;
                }
            }
            return 0;
        }

        @Specialization(guards = {"!isPTuple(exc)", "!isTupleSubtype(inliningTarget, exc, getClassNode, isSubtypeNode)"}, limit = "1")
        static int others(GraalHPyContext context, Object err, Object exc,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Shared @Cached ReadAttributeFromObjectNode readAttr,
                        @Shared @Cached CallNode callNode,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached IsTypeNode isTypeNode,
                        @Cached IsNode isNode,
                        @Cached InlinedBranchProfile isBaseExceptionProfile,
                        @Cached InlinedConditionProfile isExceptionProfile) {
            Object isInstance = callBuiltinFunction(context,
                            BuiltinNames.T_ISINSTANCE,
                            new Object[]{err, PythonBuiltinClassType.PBaseException},
                            readAttr, callNode);
            Object e = err;
            if (isTrueNode.execute(null, inliningTarget, isInstance)) {
                isBaseExceptionProfile.enter(inliningTarget);
                e = getClassNode.execute(inliningTarget, err);
            }
            if (isExceptionProfile.profile(inliningTarget,
                            isExceptionClass(context, inliningTarget, e, isTypeNode, readAttr, callNode, isTrueNode) &&
                                            isExceptionClass(context, inliningTarget, exc, isTypeNode, readAttr, callNode, isTrueNode))) {
                return isSubClass(context, inliningTarget, e, exc, readAttr, callNode, isTrueNode) ? 1 : 0;
            } else {
                return isNode.execute(exc, e) ? 1 : 0;
            }
        }

        protected boolean isTupleSubtype(Node inliningTarget, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(getClassNode.execute(inliningTarget, obj), PythonBuiltinClassType.PTuple);
        }

        static boolean isSubClass(GraalHPyContext graalHPyContext, Node inliningTarget, Object derived, Object cls,
                        ReadAttributeFromObjectNode readAttr,
                        CallNode callNode,
                        PyObjectIsTrueNode isTrueNode) {
            return isTrueNode.execute(null, inliningTarget, callBuiltinFunction(graalHPyContext,
                            BuiltinNames.T_ISSUBCLASS,
                            new Object[]{derived, cls}, readAttr, callNode));

        }

        private static boolean isExceptionClass(GraalHPyContext nativeContext, Node inliningTarget, Object obj,
                        IsTypeNode isTypeNode,
                        ReadAttributeFromObjectNode readAttr,
                        CallNode callNode,
                        PyObjectIsTrueNode isTrueNode) {
            return isTypeNode.execute(inliningTarget, obj) && isSubClass(nativeContext, inliningTarget, obj, PythonBuiltinClassType.PBaseException, readAttr, callNode, isTrueNode);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class HPyPackKeywordArgsNode extends Node {

        public abstract PKeyword[] execute(Node inliningTarget, Object[] kwvalues, PTuple kwnames, int nkw);

        @Specialization
        static PKeyword[] doPTuple(Node inliningTarget, Object[] kwvalues, PTuple kwnames, int nkw,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Cached InlinedLoopConditionProfile loopProfile) {
            loopProfile.profileCounted(inliningTarget, nkw);
            if (nkw == 0) {
                return PKeyword.EMPTY_KEYWORDS;
            }
            PKeyword[] result = new PKeyword[nkw];
            SequenceStorage storage = kwnames.getSequenceStorage();
            for (int i = 0; loopProfile.inject(inliningTarget, i < nkw); i++) {
                TruffleString name = (TruffleString) getItemNode.execute(inliningTarget, storage, i);
                result[i] = new PKeyword(name, kwvalues[i]);
            }
            return result;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
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
    @GenerateCached(false)
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
                        @Cached(parameters = "context", inline = false) GraalHPyCAccess.ReadPointerNode readPointerNode,
                        @Cached(parameters = "context", inline = false) GraalHPyCAccess.IsNullNode isNullNode,
                        @Cached(inline = false) HPyAttachFunctionTypeNode attachFunctionTypeNode) {
            // read and check the function pointer
            Object methodFunctionPointer = readPointerNode.read(context, def, GraalHPyCField.HPyCallFunction__impl);
            if (isNullNode.execute(context, methodFunctionPointer)) {
                return null;
            }
            HPySlotWrapper slotWrapper = HPY_TP_CALL.getSignatures()[0];
            return attachFunctionTypeNode.execute(context, methodFunctionPointer, slotWrapper.getLLVMFunctionType());
        }
    }
}
