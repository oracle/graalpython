/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.OBJECT_HPY_NATIVE_SPACE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPySlot.HPY_TP_DESTROY;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPySlot.HPY_TP_NEW;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_DEF_GET_GETSET;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_DEF_GET_KIND;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_DEF_GET_MEMBER;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_DEF_GET_METH;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_DEF_GET_SLOT;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_MEMBER_GET_TYPE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_METH_GET_SIGNATURE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_SLOT_GET_SLOT;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_TYPE_SPEC_PARAM_GET_OBJECT;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.MethDirectRoot;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.MethKeywordsRoot;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.MethNoargsRoot;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.MethORoot;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.MethVarargsRoot;
import com.oracle.graal.python.builtins.modules.PythonCextBuiltins.MethKeywordsNode;
import com.oracle.graal.python.builtins.modules.PythonCextBuiltins.MethNoargsNode;
import com.oracle.graal.python.builtins.modules.PythonCextBuiltins.MethONode;
import com.oracle.graal.python.builtins.modules.PythonCextBuiltins.MethVarargsNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ConvertArgsToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.SubRefCntNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToBorrowedRefNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.ConvertPIntToPrimitiveNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.EncodeNativeStringNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.ImportCExtSymbolNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPyFuncSignature;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPySlot;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyLegacyDef.HPyLegacySlot;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyMemberAccessNodes.HPyDeleteMemberNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyMemberAccessNodes.HPyGetSetDescriptorGetterRootNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyMemberAccessNodes.HPyGetSetDescriptorNotWritableRootNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyMemberAccessNodes.HPyGetSetDescriptorSetterRootNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyMemberAccessNodes.HPyReadMemberNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyMemberAccessNodes.HPyWriteMemberNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAllHandleCloseNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyGetSetSetterHandleCloseNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyKeywordsHandleCloseNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPySelfHandleCloseNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyVarargsHandleCloseNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyArrayWrappers.HPyArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyArrayWrappers.HPyCloseArrayWrapperNode;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.frame.GetCurrentFrameRef;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaBooleanNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntLossyNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.profiles.ConditionProfile;

public class GraalHPyNodes {
    @GenerateUncached
    public abstract static class PCallHPyFunction extends PNodeWithContext {

        public final Object call(GraalHPyContext context, GraalHPyNativeSymbol name, Object... args) {
            return execute(context, name, args);
        }

        abstract Object execute(GraalHPyContext context, GraalHPyNativeSymbol name, Object[] args);

        @Specialization
        static Object doIt(GraalHPyContext context, GraalHPyNativeSymbol name, Object[] args,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @Cached ImportCExtSymbolNode importCExtSymbolNode,
                        @Cached PRaiseNode raiseNode) {
            try {
                return interopLibrary.execute(importCExtSymbolNode.execute(context, name), args);
            } catch (UnsupportedTypeException | ArityException e) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, e);
            } catch (UnsupportedMessageException e) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, "HPy C API symbol %s is not callable", name);
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

        @Specialization
        static void setCurrentException(Frame frame, GraalHPyContext nativeContext, PException e,
                        @Cached GetCurrentFrameRef getCurrentFrameRef) {
            // TODO connect f_back
            getCurrentFrameRef.execute(frame).markAsEscaped();
            nativeContext.setCurrentException(e);
        }
    }

    @GenerateUncached
    public abstract static class HPyRaiseNode extends Node {

        public final int raiseInt(Frame frame, GraalHPyContext nativeContext, int errorValue, Object errType, String format, Object... arguments) {
            return executeInt(frame, nativeContext, errorValue, errType, format, arguments);
        }

        public final Object raise(Frame frame, GraalHPyContext nativeContext, Object errorValue, Object errType, String format, Object... arguments) {
            return execute(frame, nativeContext, errorValue, errType, format, arguments);
        }

        public final int raiseIntWithoutFrame(GraalHPyContext nativeContext, int errorValue, Object errType, String format, Object... arguments) {
            return executeInt(null, nativeContext, errorValue, errType, format, arguments);
        }

        public final Object raiseWithoutFrame(GraalHPyContext nativeContext, Object errorValue, Object errType, String format, Object... arguments) {
            return execute(null, nativeContext, errorValue, errType, format, arguments);
        }

        public abstract Object execute(Frame frame, GraalHPyContext nativeContext, Object errorValue, Object errType, String format, Object[] arguments);

        public abstract int executeInt(Frame frame, GraalHPyContext nativeContext, int errorValue, Object errType, String format, Object[] arguments);

        @Specialization
        static int doInt(Frame frame, GraalHPyContext nativeContext, int errorValue, Object errType, String format, Object[] arguments,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("transformExceptionToNativeNode") @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                throw raiseNode.execute(errType, PNone.NO_VALUE, format, arguments);
            } catch (PException p) {
                transformExceptionToNativeNode.execute(frame, nativeContext, p);
            }
            return errorValue;
        }

        @Specialization
        static Object doObject(Frame frame, GraalHPyContext nativeContext, Object errorValue, Object errType, String format, Object[] arguments,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("transformExceptionToNativeNode") @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                throw raiseNode.execute(errType, PNone.NO_VALUE, format, arguments);
            } catch (PException p) {
                transformExceptionToNativeNode.execute(frame, nativeContext, p);
            }
            return errorValue;
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
                        @CachedLanguage PythonLanguage language,
                        @CachedLibrary("methodDef") InteropLibrary interopLibrary,
                        @CachedLibrary(limit = "2") InteropLibrary resultLib,
                        @Cached PCallHPyFunction callHelperFunctionNode,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached PythonObjectFactory factory,
                        @Cached WriteAttributeToDynamicObjectNode writeAttributeToDynamicObjectNode,
                        @Cached PRaiseNode raiseNode) {
            assert checkLayout(methodDef);

            String methodName = castToJavaStringNode.execute(callHelperFunctionNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_GET_ML_NAME, methodDef));

            // note: 'ml_doc' may be NULL; in this case, we would store 'None'
            Object methodDoc = PNone.NONE;
            try {
                Object doc = interopLibrary.readMember(methodDef, "doc");
                if (!resultLib.isNull(doc)) {
                    methodDoc = fromCharPointerNode.execute(doc);
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
                    throw raiseNode.raise(PythonBuiltinClassType.SystemError, "signature of %s is not an integer", methodName);
                }
                signature = HPyFuncSignature.fromValue(resultLib.asInt(methodSignatureObj));
                if (signature == null) {
                    throw raiseNode.raise(PythonBuiltinClassType.ValueError, "Unsupported HPyMeth signature");
                }

                methodFunctionPointer = interopLibrary.readMember(methodDef, "impl");
                if (!resultLib.isExecutable(methodFunctionPointer)) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw raiseNode.raise(PythonBuiltinClassType.SystemError, "meth of %s is not callable", methodName);
                }
            } catch (UnknownIdentifierException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, "Invalid struct member '%s'", e.getUnknownIdentifier());
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, "Cannot access struct member 'ml_flags' or 'ml_meth'.");
            }

            PBuiltinFunction function = HPyExternalFunctionNodes.createWrapperFunction(language, signature, methodName, methodFunctionPointer, enclosingType, factory);

            // write doc string; we need to directly write to the storage otherwise it is
            // disallowed writing to builtin types.
            writeAttributeToDynamicObjectNode.execute(function.getStorage(), SpecialAttributeNames.__DOC__, methodDoc);

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
     * <pre>
     *     struct PyMethodDef {
     *         const char * ml_name;
     *         PyCFunction  ml_meth;
     *         int          ml_flags;
     *         const char * ml_doc;
     *     };
     * </pre>
     */
    @GenerateUncached
    public abstract static class HPyAddLegacyMethodNode extends PNodeWithContext {

        public abstract PBuiltinFunction execute(GraalHPyContext context, Object legacyMethodDef);

        @Specialization(limit = "1")
        static PBuiltinFunction doIt(GraalHPyContext context, Object methodDef,
                        @CachedLanguage PythonLanguage language,
                        @CachedLibrary("methodDef") InteropLibrary interopLibrary,
                        @CachedLibrary(limit = "2") InteropLibrary resultLib,
                        @Cached PCallHPyFunction callGetNameNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached PythonObjectFactory factory,
                        @Cached WriteAttributeToDynamicObjectNode writeAttributeToDynamicObjectNode,
                        @Cached PRaiseNode raiseNode) {

            assert checkLayout(methodDef) : "provided pointer has unexpected structure";

            String methodName = castToJavaStringNode.execute(callGetNameNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_LEGACY_METHODDEF_GET_ML_NAME, methodDef));

            // note: 'ml_doc' may be NULL; in this case, we would store 'None'
            Object methodDoc = PNone.NONE;
            try {
                Object methodDocPtr = interopLibrary.readMember(methodDef, "ml_doc");
                if (!resultLib.isNull(methodDocPtr)) {
                    methodDoc = fromCharPointerNode.execute(methodDocPtr);
                }
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                // fall through
            }

            Object methodFlagsObj;
            int flags;
            Object mlMethObj;
            try {
                methodFlagsObj = interopLibrary.readMember(methodDef, "ml_flags");
                if (!resultLib.fitsInInt(methodFlagsObj)) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw raiseNode.raise(PythonBuiltinClassType.SystemError, "ml_flags of %s is not an integer", methodName);
                }
                flags = resultLib.asInt(methodFlagsObj);

                mlMethObj = interopLibrary.readMember(methodDef, "ml_meth");
                if (!resultLib.isExecutable(mlMethObj)) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw raiseNode.raise(PythonBuiltinClassType.SystemError, "ml_meth of %s is not callable", methodName);
                }
            } catch (UnknownIdentifierException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, "Invalid struct member '%s'", e.getUnknownIdentifier());
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, "Cannot access struct member 'ml_flags' or 'ml_meth'.");
            }

            // CPy-style methods
            // TODO(fa) support static and class methods
            PRootNode rootNode = createWrapperRootNode(language, flags, methodName);
            PKeyword[] kwDefaults = ExternalFunctionNodes.createKwDefaults(mlMethObj);
            PBuiltinFunction function = factory.createBuiltinFunction(methodName, null, PythonUtils.EMPTY_OBJECT_ARRAY, kwDefaults, PythonUtils.getOrCreateCallTarget(rootNode));

            // write doc string; we need to directly write to the storage otherwise it is disallowed
            // writing to builtin types.
            writeAttributeToDynamicObjectNode.execute(function.getStorage(), SpecialAttributeNames.__DOC__, methodDoc);

            return function;
        }

        @TruffleBoundary
        private static boolean checkLayout(Object methodDef) {
            String[] members = new String[]{"ml_name", "ml_meth", "ml_flags", "ml_doc"};
            InteropLibrary lib = InteropLibrary.getUncached(methodDef);
            for (String member : members) {
                if (!lib.isMemberReadable(methodDef, member)) {
                    return false;
                }
            }
            return true;
        }

        @TruffleBoundary
        private static PRootNode createWrapperRootNode(PythonLanguage language, int flags, String name) {
            if (CExtContext.isMethNoArgs(flags)) {
                return new MethNoargsRoot(language, name, MethNoargsNode.METH_NOARGS_CONVERTER);
            } else if (CExtContext.isMethO(flags)) {
                return new MethORoot(language, name, MethONode.METH_O_CONVERTER);
            } else if (CExtContext.isMethKeywords(flags)) {
                return new MethKeywordsRoot(language, name, MethKeywordsNode.METH_KEYWORDS_CONVERTER);
            } else if (CExtContext.isMethVarargs(flags)) {
                return new MethVarargsRoot(language, name, MethVarargsNode.METH_VARARGS_CONVERTER);
            }
            throw new IllegalStateException("illegal method flags");
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

        public abstract GetSetDescriptor execute(GraalHPyContext context, Object owner, Object legacyGetSetDef);

        @Specialization(limit = "1")
        static GetSetDescriptor doGeneric(GraalHPyContext context, Object owner, Object legacyGetSetDef,
                        @CachedLanguage PythonLanguage lang,
                        @Cached GetNameNode getNameNode,
                        @CachedLibrary("legacyGetSetDef") InteropLibrary interopLibrary,
                        @CachedLibrary(limit = "2") InteropLibrary resultLib,
                        @Cached PCallHPyFunction callGetNameNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached PythonObjectFactory factory,
                        @Cached WriteAttributeToDynamicObjectNode writeDocNode,
                        @Cached PRaiseNode raiseNode) {

            assert checkLayout(legacyGetSetDef) : "provided pointer has unexpected structure";

            String getSetDescrName = castToJavaStringNode.execute(callGetNameNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_LEGACY_GETSETDEF_GET_NAME, legacyGetSetDef));

            // note: 'doc' may be NULL; in this case, we would store 'None'
            Object getSetDescrDoc = PNone.NONE;
            try {
                Object getSetDocPtr = interopLibrary.readMember(legacyGetSetDef, "doc");
                if (!resultLib.isNull(getSetDocPtr)) {
                    getSetDescrDoc = fromCharPointerNode.execute(getSetDocPtr);
                }
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                // fall through
            }

            Object getterFunPtr;
            Object setterFunPtr;
            Object closurePtr;
            boolean readOnly;
            try {
                getterFunPtr = interopLibrary.readMember(legacyGetSetDef, "get");
                // TODO eagerly resolve function ptr
                // the pointer must either be NULL or a callable function pointer
                if (!(resultLib.isNull(getterFunPtr) || resultLib.isExecutable(getterFunPtr))) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw raiseNode.raise(PythonBuiltinClassType.SystemError, "get of %s is not callable", getSetDescrName);
                }

                setterFunPtr = interopLibrary.readMember(legacyGetSetDef, "set");
                // TODO eagerly resolve function ptr
                // the pointer must either be NULL or a callable function pointer
                if (!(resultLib.isNull(setterFunPtr) || resultLib.isExecutable(setterFunPtr))) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw raiseNode.raise(PythonBuiltinClassType.SystemError, "set of %s is not callable", getSetDescrName);
                }
                readOnly = resultLib.isNull(setterFunPtr);

                closurePtr = interopLibrary.readMember(legacyGetSetDef, "closure");
            } catch (UnknownIdentifierException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, "Invalid struct member '%s'", e.getUnknownIdentifier());
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, "Cannot access struct member 'ml_flags' or 'ml_meth'.");
            }

            PBuiltinFunction getterObject = HPyGetSetDescriptorGetterRootNode.createLegacyFunction(lang, owner, getSetDescrName, getterFunPtr, closurePtr);
            Object setterObject;
            if (readOnly) {
                setterObject = HPyGetSetDescriptorNotWritableRootNode.createFunction(context.getContext(), getNameNode.execute(owner), getSetDescrName);
            } else {
                setterObject = HPyGetSetDescriptorSetterRootNode.createLegacyFunction(lang, owner, getSetDescrName, setterFunPtr, closurePtr);
            }

            GetSetDescriptor getSetDescriptor = factory.createGetSetDescriptor(getterObject, setterObject, getSetDescrName, owner, !readOnly);
            writeDocNode.execute(getSetDescriptor, SpecialAttributeNames.__DOC__, getSetDescrDoc);
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

        HPyProperty(Object key, Object value) {
            this.key = key;
            this.value = value;
        }
    }

    @GenerateUncached
    public abstract static class HPyCreateLegacyMemberNode extends PNodeWithContext {

        public abstract HPyProperty execute(GraalHPyContext context, Object memberDef);

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
        static HPyProperty doIt(GraalHPyContext context, Object memberDef,
                        @CachedLanguage PythonLanguage language,
                        @CachedLibrary("memberDef") InteropLibrary interopLibrary,
                        @CachedLibrary(limit = "2") InteropLibrary valueLib,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached ReadAttributeFromObjectNode readAttributeNode,
                        @Cached CallNode callPropertyClassNode,
                        @Cached PRaiseNode raiseNode) {

            assert interopLibrary.hasMembers(memberDef);
            assert interopLibrary.isMemberReadable(memberDef, "name");
            assert interopLibrary.isMemberReadable(memberDef, "type");
            assert interopLibrary.isMemberReadable(memberDef, "offset");
            assert interopLibrary.isMemberReadable(memberDef, "flags");
            assert interopLibrary.isMemberReadable(memberDef, "doc");

            try {
                String name;
                try {
                    name = castToJavaStringNode.execute(fromCharPointerNode.execute(interopLibrary.readMember(memberDef, "name")));
                } catch (CannotCastException e) {
                    throw CompilerDirectives.shouldNotReachHere("Cannot cast member name to string");
                }

                // note: 'doc' may be NULL; in this case, we would store 'None'
                Object memberDoc = PNone.NONE;
                Object doc = interopLibrary.readMember(memberDef, "doc");
                if (!valueLib.isNull(doc)) {
                    memberDoc = fromCharPointerNode.execute(doc);
                }

                int flags = valueLib.asInt(interopLibrary.readMember(memberDef, "flags"));
                int type = valueLib.asInt(interopLibrary.readMember(memberDef, "type"));
                int offset = valueLib.asInt(interopLibrary.readMember(memberDef, "offset"));

                PBuiltinFunction getterObject = HPyReadMemberNode.createBuiltinFunction(language, name, type, offset);

                Object setterObject = PNone.NONE;
                if ((flags & GraalHPyLegacyDef.MEMBER_FLAG_READONLY) == 0) {
                    setterObject = HPyWriteMemberNode.createBuiltinFunction(language, name, type, offset);
                }

                // read class 'property' from 'builtins/property.py'
                Object property = readAttributeNode.execute(context.getContext().getBuiltins(), "property");
                Object propertyObject = callPropertyClassNode.execute(property, PythonUtils.EMPTY_OBJECT_ARRAY, new PKeyword[]{
                                new PKeyword("fget", getterObject),
                                new PKeyword("fset", setterObject),
                                new PKeyword("doc", memberDoc),
                                new PKeyword("name", name)
                });

                return new HPyProperty(name, propertyObject);
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, "Cannot read field 'name' from member definition");
            }
        }

    }

    @GenerateUncached
    public abstract static class HPyAddMemberNode extends PNodeWithContext {

        public abstract HPyProperty execute(GraalHPyContext context, Object memberDef);

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
        static HPyProperty doIt(GraalHPyContext context, Object memberDef,
                        @CachedLanguage PythonLanguage language,
                        @CachedLibrary("memberDef") InteropLibrary interopLibrary,
                        @CachedLibrary(limit = "2") InteropLibrary valueLib,
                        @Cached PCallHPyFunction callHelperNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached ReadAttributeFromObjectNode readAttributeNode,
                        @Cached CallNode callPropertyClassNode,
                        @Cached PRaiseNode raiseNode) {

            assert interopLibrary.hasMembers(memberDef);
            assert interopLibrary.isMemberReadable(memberDef, "name");
            assert interopLibrary.isMemberReadable(memberDef, "type");
            assert interopLibrary.isMemberReadable(memberDef, "offset");
            assert interopLibrary.isMemberReadable(memberDef, "readonly");
            assert interopLibrary.isMemberReadable(memberDef, "doc");

            try {
                String name;
                try {
                    name = castToJavaStringNode.execute(fromCharPointerNode.execute(interopLibrary.readMember(memberDef, "name")));
                } catch (CannotCastException e) {
                    throw CompilerDirectives.shouldNotReachHere("Cannot cast member name to string");
                }

                // note: 'doc' may be NULL; in this case, we would store 'None'
                Object memberDoc = PNone.NONE;
                Object doc = interopLibrary.readMember(memberDef, "doc");
                if (!valueLib.isNull(doc)) {
                    memberDoc = fromCharPointerNode.execute(doc);
                }

                int type = valueLib.asInt(callHelperNode.call(context, GRAAL_HPY_MEMBER_GET_TYPE, memberDef));
                boolean readOnly = valueLib.asInt(interopLibrary.readMember(memberDef, "readonly")) != 0;
                int offset = valueLib.asInt(interopLibrary.readMember(memberDef, "offset"));

                PBuiltinFunction getterObject = HPyReadMemberNode.createBuiltinFunction(language, name, type, offset);

                Object setterObject = PNone.NONE;
                Object deleterObject = PNone.NONE;
                if (!readOnly) {
                    setterObject = HPyWriteMemberNode.createBuiltinFunction(language, name, type, offset);
                    // Members are, of course, not deletable; this built-in function will throw a
                    // TypeError.
                    deleterObject = HPyDeleteMemberNode.createBuiltinFunction(language, name);
                }

                // read class 'property' from 'builtins/property.py'
                Object property = readAttributeNode.execute(context.getContext().getBuiltins(), "property");
                Object propertyObject = callPropertyClassNode.execute(property, PythonUtils.EMPTY_OBJECT_ARRAY, new PKeyword[]{
                                new PKeyword("fget", getterObject),
                                new PKeyword("fset", setterObject),
                                new PKeyword("fdel", deleterObject),
                                new PKeyword("doc", memberDoc),
                                new PKeyword("name", name)
                });

                return new HPyProperty(name, propertyObject);
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, "Cannot read field 'name' from member definition");
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
                        @CachedLibrary("memberDef") InteropLibrary interopLibrary,
                        @CachedLibrary(limit = "2") InteropLibrary valueLib,
                        @Cached GetNameNode getNameNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached PythonObjectFactory factory,
                        @Cached WriteAttributeToDynamicObjectNode writeDocNode,
                        @Cached PRaiseNode raiseNode) {

            assert interopLibrary.hasMembers(memberDef);
            assert interopLibrary.isMemberReadable(memberDef, "name");
            assert interopLibrary.isMemberReadable(memberDef, "getter_impl");
            assert interopLibrary.isMemberReadable(memberDef, "setter_impl");
            assert interopLibrary.isMemberReadable(memberDef, "doc");
            assert interopLibrary.isMemberReadable(memberDef, "closure");

            String enclosingClassName = getNameNode.execute(type);
            try {
                String name;
                try {
                    name = castToJavaStringNode.execute(fromCharPointerNode.execute(interopLibrary.readMember(memberDef, "name")));
                } catch (CannotCastException e) {
                    throw CompilerDirectives.shouldNotReachHere("Cannot cast member name to string");
                }

                // note: 'doc' may be NULL; in this case, we would store 'None'
                Object memberDoc = PNone.NONE;
                Object docCharPtr = interopLibrary.readMember(memberDef, "doc");
                if (!valueLib.isNull(docCharPtr)) {
                    memberDoc = fromCharPointerNode.execute(docCharPtr);
                }

                Object closurePtr = interopLibrary.readMember(memberDef, "closure");

                // signature: self, closure
                Object getterFunctionPtr = interopLibrary.readMember(memberDef, "getter_impl");

                // signature: self, value, closure
                Object setterFunctionPtr = interopLibrary.readMember(memberDef, "setter_impl");
                boolean readOnly = interopLibrary.isNull(setterFunctionPtr);

                PFunction getterObject = HPyGetSetDescriptorGetterRootNode.createFunction(context.getContext(), enclosingClassName, name, getterFunctionPtr, closurePtr);
                Object setterObject;
                if (readOnly) {
                    setterObject = HPyGetSetDescriptorNotWritableRootNode.createFunction(context.getContext(), enclosingClassName, name);
                } else {
                    setterObject = HPyGetSetDescriptorSetterRootNode.createFunction(context.getContext(), name, setterFunctionPtr, closurePtr);
                }

                GetSetDescriptor getSetDescriptor = factory.createGetSetDescriptor(getterObject, setterObject, name, type, !readOnly);
                writeDocNode.execute(getSetDescriptor, SpecialAttributeNames.__DOC__, memberDoc);
                return getSetDescriptor;
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, "Cannot read field 'name' from member definition");
            }
        }
    }

    /**
     * Parser an {@code HPySlot} structure, creates and adds the appropriate function as magic
     * method.
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
    public abstract static class HPyCreateSlotNode extends PNodeWithContext {

        public abstract HPyProperty execute(GraalHPyContext context, Object enclosingType, Object slotDef);

        @Specialization(limit = "1")
        static HPyProperty doIt(GraalHPyContext context, Object enclosingType, Object slotDef,
                        @CachedLanguage PythonLanguage language,
                        @CachedLibrary("slotDef") InteropLibrary interopLibrary,
                        @CachedLibrary(limit = "2") InteropLibrary resultLib,
                        @Cached PCallHPyFunction callHelperFunctionNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode raiseNode) {
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
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, "field 'slot' of %s is not an integer", slotDef);
            }

            HPySlot slot = HPySlot.fromValue(slotNr);
            if (slot == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, "invalid slot value %d", slotNr);
            }

            Object methodName = slot.getAttributeKey();

            if (methodName == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, "slot %s is not yet supported", slot.name());
            }

            Object methodFunctionPointer;
            try {

                methodFunctionPointer = interopLibrary.readMember(slotDef, "impl");
                if (!resultLib.isExecutable(methodFunctionPointer)) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw raiseNode.raise(PythonBuiltinClassType.SystemError, "meth of %s is not callable", methodName);
                }
            } catch (UnknownIdentifierException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, "Invalid struct member '%s'", e.getUnknownIdentifier());
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, "Cannot access struct member 'ml_flags' or 'ml_meth'.");
            }

            String methodNameStr = methodName instanceof HiddenKey ? ((HiddenKey) methodName).getName() : (String) methodName;

            Object function;
            if (HPY_TP_DESTROY.equals(slot)) {
                // special case: DESTROYFUNC
                // This won't be usable from Python, so we just store the bare pointer object into
                // the hidden attribute.
                function = methodFunctionPointer;
            } else {
                function = HPyExternalFunctionNodes.createWrapperFunction(language, slot.getSignature(), methodNameStr, methodFunctionPointer, HPY_TP_NEW.equals(slot) ? null : enclosingType, factory);
            }
            return new HPyProperty(methodName, function);
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
                        @CachedLanguage PythonLanguage lang,
                        @CachedLibrary(limit = "3") InteropLibrary resultLib,
                        @Cached HPyAddLegacyMethodNode legacyMethodNode,
                        @Cached HPyCreateLegacyMemberNode createLegacyMemberNode,
                        @Cached HPyAddLegacyGetSetDefNode legacyGetSetNode,
                        @Cached WriteAttributeToObjectNode writeAttributeToObjectNode,
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
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, "field 'slot' of %s is not an integer", slotDef);
            }

            HPyLegacySlot slot = HPyLegacySlot.fromValue(slotId);
            if (slot == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, "invalid slot value %d", slotId);
            }

            // treatment for special slots 'Py_tp_members', 'Py_tp_getset', 'Py_tp_methods'
            switch (slot) {
                case Py_tp_members:
                    Object memberDefArrayPtr = callHelperFunctionNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_LEGACY_SLOT_GET_MEMBERS, slotDef);
                    try {
                        int nLegacyMemberDefs = PInt.intValueExact(resultLib.getArraySize(memberDefArrayPtr));
                        for (int i = 0; i < nLegacyMemberDefs; i++) {
                            Object legacyMemberDef = resultLib.readArrayElement(memberDefArrayPtr, i);
                            HPyProperty property = createLegacyMemberNode.execute(context, legacyMemberDef);
                            writeAttributeToObjectNode.execute(enclosingType, property.key, property.value);
                        }
                    } catch (InteropException | OverflowException e) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw raiseNode.raise(PythonBuiltinClassType.SystemError, "error when reading legacy method definition for type %s", enclosingType);
                    }
                    break;
                case Py_tp_methods:
                    Object methodDefArrayPtr = callHelperFunctionNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_LEGACY_SLOT_GET_METHODS, slotDef);
                    try {
                        int nLegacyMemberDefs = PInt.intValueExact(resultLib.getArraySize(methodDefArrayPtr));
                        for (int i = 0; i < nLegacyMemberDefs; i++) {
                            Object legacyMethodDef = resultLib.readArrayElement(methodDefArrayPtr, i);
                            PBuiltinFunction method = legacyMethodNode.execute(context, legacyMethodDef);
                            writeAttributeToObjectNode.execute(enclosingType, method.getName(), method);
                        }
                    } catch (InteropException | OverflowException e) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw raiseNode.raise(PythonBuiltinClassType.SystemError, "error when reading legacy method definition for type %s", enclosingType);
                    }
                    break;
                case Py_tp_repr:
                    Object pfuncPtr = callHelperFunctionNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_LEGACY_SLOT_GET_PFUNC, slotDef);
                    RootCallTarget callTarget = PythonUtils.getOrCreateCallTarget(MethDirectRoot.create(lang, SpecialMethodNames.__REPR__));
                    PKeyword[] kwDefaults = ExternalFunctionNodes.createKwDefaults(pfuncPtr);
                    PBuiltinFunction method = factory.createBuiltinFunction(SpecialMethodNames.__REPR__, enclosingType, PythonUtils.EMPTY_OBJECT_ARRAY, kwDefaults, callTarget);
                    writeAttributeToObjectNode.execute(enclosingType, SpecialMethodNames.__REPR__, method);
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
                        throw raiseNode.raise(PythonBuiltinClassType.SystemError, "error when reading legacy method definition for type %s", enclosingType);
                    }
                    break;
                default:
                    // TODO(fa): implement support for remaining legacy slot kinds
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw CompilerDirectives.shouldNotReachHere(String.format("support for legacy slot %s not yet implemented", slot.name()));
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
    public abstract static class HPyAsContextNode extends PNodeWithContext {

        public abstract GraalHPyContext execute(Object object);

        public abstract GraalHPyContext executeInt(int l);

        public abstract GraalHPyContext executeLong(long l);

        @Specialization
        static GraalHPyContext doHandle(GraalHPyContext hpyContext) {
            return hpyContext;
        }

        // n.b. we could actually accept anything else but we have specializations to be more strict
        // about what we expect

        @Specialization
        static GraalHPyContext doInt(@SuppressWarnings("unused") int handle,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            return context.getHPyContext();
        }

        @Specialization
        static GraalHPyContext doLong(@SuppressWarnings("unused") long handle,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            return context.getHPyContext();
        }

        @Specialization(guards = "interopLibrary.isPointer(handle)", limit = "2")
        static GraalHPyContext doLong(@SuppressWarnings("unused") Object handle,
                        @CachedLibrary("handle") @SuppressWarnings("unused") InteropLibrary interopLibrary,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            return context.getHPyContext();
        }
    }

    @GenerateUncached
    public abstract static class HPyEnsureHandleNode extends PNodeWithContext {

        public abstract GraalHPyHandle execute(GraalHPyContext hpyContext, Object object);

        public abstract GraalHPyHandle executeInt(GraalHPyContext hpyContext, int l);

        public abstract GraalHPyHandle executeLong(GraalHPyContext hpyContext, long l);

        @Specialization
        static GraalHPyHandle doHandle(@SuppressWarnings("unused") GraalHPyContext hpyContext, GraalHPyHandle handle) {
            return handle;
        }

        @Specialization(guards = {"hpyContext != null", "interopLibrary.isPointer(handle)"}, limit = "2")
        static GraalHPyHandle doPointer(@SuppressWarnings("unused") GraalHPyContext hpyContext, Object handle,
                        @CachedLibrary("handle") InteropLibrary interopLibrary,
                        @Cached PRaiseNode raiseNode) {
            try {
                return doLongOvfWithContext(hpyContext, interopLibrary.asPointer(handle), raiseNode);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("");
            }
        }

        @Specialization(guards = "hpyContext == null")
        static GraalHPyHandle doInt(@SuppressWarnings("unused") GraalHPyContext hpyContext, int handle,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            return context.getHPyContext().getObjectForHPyHandle(handle);
        }

        @Specialization(guards = "hpyContext == null", rewriteOn = OverflowException.class)
        static GraalHPyHandle doLong(@SuppressWarnings("unused") GraalHPyContext hpyContext, long handle,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) throws OverflowException {
            return context.getHPyContext().getObjectForHPyHandle(PInt.intValueExact(handle));
        }

        @Specialization(guards = "hpyContext == null", replaces = "doLong")
        static GraalHPyHandle doLongOvf(@SuppressWarnings("unused") GraalHPyContext hpyContext, long handle,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            return doLongOvfWithContext(context.getHPyContext(), handle, raiseNode);
        }

        @Specialization(guards = "hpyContext != null")
        static GraalHPyHandle doIntWithContext(GraalHPyContext hpyContext, int handle) {
            return hpyContext.getObjectForHPyHandle(handle);
        }

        @Specialization(guards = "hpyContext != null", rewriteOn = OverflowException.class)
        static GraalHPyHandle doLongWithContext(GraalHPyContext hpyContext, long handle) throws OverflowException {
            return hpyContext.getObjectForHPyHandle(PInt.intValueExact(handle));
        }

        @Specialization(guards = "hpyContext != null", replaces = "doLongWithContext")
        static GraalHPyHandle doLongOvfWithContext(GraalHPyContext hpyContext, long handle,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            try {
                return hpyContext.getObjectForHPyHandle(PInt.intValueExact(handle));
            } catch (OverflowException e) {
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, "unknown handle: %d", handle);
            }
        }
    }

    @GenerateUncached
    public abstract static class HPyAsPythonObjectNode extends CExtToJavaNode {

        @Specialization
        static Object doHandle(@SuppressWarnings("unused") GraalHPyContext hpyContext, GraalHPyHandle handle) {
            return handle.getDelegate();
        }

        @Specialization
        static Object doInt(GraalHPyContext hpyContext, int handle,
                        @Shared("ensureHandleNode") @Cached HPyEnsureHandleNode ensureHandleNode) {
            return ensureHandleNode.executeInt(hpyContext, handle).getDelegate();
        }

        @Specialization
        static Object doLong(GraalHPyContext hpyContext, long handle,
                        @Shared("ensureHandleNode") @Cached HPyEnsureHandleNode ensureHandleNode) {
            return ensureHandleNode.executeLong(hpyContext, handle).getDelegate();
        }

        @Specialization(replaces = "doHandle")
        static Object doObject(GraalHPyContext hpyContext, Object object,
                        @Shared("ensureHandleNode") @Cached HPyEnsureHandleNode ensureHandleNode) {
            return ensureHandleNode.execute(hpyContext, object).getDelegate();
        }
    }

    /**
     * Similar to {@link HPyAsPythonObjectNode}, this node converts a native primitive value to an
     * appropriate Python value considering the native value as unsigned. For example, a negative
     * {@code int} value will be converted to a positive {@code long} value.
     */
    @GenerateUncached
    public abstract static class HPyUnsignedPrimitiveAsPythonObjectNode extends CExtToJavaNode {

        @Specialization(guards = "n >= 0")
        static int doUnsignedIntPositive(@SuppressWarnings("unused") GraalHPyContext hpyContext, int n) {
            return n;
        }

        @Specialization(replaces = "doUnsignedIntPositive")
        static long doUnsignedInt(@SuppressWarnings("unused") GraalHPyContext hpyContext, int n) {
            if (n < 0) {
                return n & 0xffffffffL;
            }
            return n;
        }

        @Specialization(guards = "n >= 0")
        static long doUnsignedLongPositive(@SuppressWarnings("unused") GraalHPyContext hpyContext, long n) {
            return n;
        }

        @Specialization(guards = "n < 0")
        static Object doUnsignedLongNegative(@SuppressWarnings("unused") GraalHPyContext hpyContext, long n,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createInt(PInt.longToUnsignedBigInteger(n));
        }

        @Specialization(replaces = {"doUnsignedIntPositive", "doUnsignedInt", "doUnsignedLongPositive", "doUnsignedLongNegative"})
        static Object doGeneric(GraalHPyContext hpyContext, Object n,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            if (n instanceof Integer) {
                int i = (int) n;
                if (i >= 0) {
                    return i;
                } else {
                    return doUnsignedInt(hpyContext, i);
                }
            } else if (n instanceof Long) {
                long l = (long) n;
                if (l >= 0) {
                    return l;
                } else {
                    return doUnsignedLongNegative(hpyContext, l, factory);
                }
            }
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    /**
     * Similar to {@link HPyAsPythonObjectNode}, this node converts a native primitive value to an
     * appropriate Python char value (a single-char Python string).
     */
    @GenerateUncached
    public abstract static class HPyPrimitiveAsPythonCharNode extends CExtToJavaNode {

        @Specialization
        static Object doByte(@SuppressWarnings("unused") GraalHPyContext hpyContext, byte b) {
            return PythonUtils.newString(new char[]{(char) b});
        }

        @Specialization
        static Object doShort(@SuppressWarnings("unused") GraalHPyContext hpyContext, short i) {
            return createString((char) i);
        }

        @Specialization
        static Object doLong(@SuppressWarnings("unused") GraalHPyContext hpyContext, long l) {
            // If the integer is out of byte range, we just to a lossy cast since that's the same
            // sematics as we should just read a single byte.
            return createString((char) l);
        }

        @Specialization(replaces = {"doByte", "doShort", "doLong"})
        static Object doGeneric(@SuppressWarnings("unused") GraalHPyContext hpyContext, Object n) {
            if (n instanceof Number) {
                return createString((char) ((Number) n).shortValue());
            }
            throw CompilerDirectives.shouldNotReachHere();
        }

        private static String createString(char c) {
            return PythonUtils.newString(new char[]{c});
        }
    }

    /**
     * Similar to {@link HPyAsPythonObjectNode}, this node converts a Boolean value to Python
     * Boolean.
     */
    @GenerateUncached
    public abstract static class HPyPrimitiveAsPythonBooleanNode extends CExtToJavaNode {

        @Specialization
        static Object doByte(@SuppressWarnings("unused") GraalHPyContext hpyContext, byte b) {
            return b != 0;
        }

        @Specialization
        static Object doShort(@SuppressWarnings("unused") GraalHPyContext hpyContext, short i) {
            return i != 0;
        }

        @Specialization
        static Object doLong(@SuppressWarnings("unused") GraalHPyContext hpyContext, long l) {
            // If the integer is out of byte range, we just to a lossy cast since that's the same
            // sematics as we should just read a single byte.
            return l != 0;
        }

        @Specialization(replaces = {"doByte", "doShort", "doLong"})
        static Object doGeneric(@SuppressWarnings("unused") GraalHPyContext hpyContext, Object n) {
            if (n instanceof Number) {
                return ((Number) n).longValue() != 0;
            }
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @GenerateUncached
    public abstract static class HPyAsHandleNode extends CExtToNativeNode {

        // TODO(fa) implement handles for primitives that avoid boxing

        @Specialization
        static GraalHPyHandle doObject(@SuppressWarnings("unused") CExtContext hpyContext, Object object) {
            return new GraalHPyHandle(object);
        }

    }

    /**
     * Converts a Python object to
     */
    public abstract static class HPyAsNativePrimitiveNode extends CExtToNativeNode {

        private final int targetTypeSize;
        private final boolean signed;

        protected HPyAsNativePrimitiveNode(int targetTypeSize, boolean signed) {
            this.targetTypeSize = targetTypeSize;
            this.signed = signed;
        }

        // Adding specializations for primitives does not make a lot of sense just to avoid
        // un-/boxing in the interpreter since interop will force un-/boxing anyway.
        @Specialization
        Object doGeneric(@SuppressWarnings("unused") CExtContext hpyContext, Object value,
                        @Cached ConvertPIntToPrimitiveNode asNativePrimitiveNode) {
            return asNativePrimitiveNode.execute(null, value, PInt.intValue(signed), targetTypeSize);
        }
    }

    /**
     * Converts a Python character (1-element Python string) into a UTF-8 encoded C {@code char}.
     * According to CPython, we need to encode the whole Python string before we access the first
     * byte (see also: {@code structmember.c:PyMember_SetOne} case {@code T_CHAR}).
     */
    @GenerateUncached
    public abstract static class HPyAsNativeCharNode extends CExtToNativeNode {

        @Specialization
        static byte doGeneric(@SuppressWarnings("unused") CExtContext hpyContext, Object value,
                        @Cached EncodeNativeStringNode encodeNativeStringNode,
                        @Cached PRaiseNode raiseNode) {
            byte[] encoded = encodeNativeStringNode.execute(StandardCharsets.UTF_8, value, CodecsModuleBuiltins.STRICT);
            if (encoded.length != 1) {
                throw raiseNode.raise(TypeError, ErrorMessages.BAD_ARG_TYPE_FOR_BUILTIN_OP);
            }
            return encoded[0];
        }
    }

    /**
     * Converts a Python Boolean into a C Boolean {@code char} (see also:
     * {@code structmember.c:PyMember_SetOne} case {@code T_BOOL}).
     */
    @GenerateUncached
    public abstract static class HPyAsNativeBooleanNode extends CExtToNativeNode {

        @Specialization
        static byte doGeneric(@SuppressWarnings("unused") CExtContext hpyContext, Object value,
                        @Cached CastToJavaBooleanNode castToJavaBooleanNode,
                        @Cached PRaiseNode raiseNode) {
            try {
                return (byte) PInt.intValue(castToJavaBooleanNode.execute(value));
            } catch (CannotCastException e) {
                throw raiseNode.raise(TypeError, ErrorMessages.ATTR_VALUE_MUST_BE_BOOL);
            }
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
        Object doGeneric(@SuppressWarnings("unused") CExtContext hpyContext, Object value,
                        @Cached ConvertPIntToPrimitiveNode asNativePrimitiveNode) {
            return asNativePrimitiveNode.execute(null, value, 1, Long.BYTES);
        }
    }

    @GenerateUncached
    public abstract static class HPyAsNativeDoubleNode extends CExtToNativeNode {

        // Adding specializations for primitives does not make a lot of sense just to avoid
        // un-/boxing in the interpreter since interop will force un-/boxing anyway.
        @Specialization(limit = "3")
        static Object doGeneric(@SuppressWarnings("unused") CExtContext hpyContext, Object value,
                        @CachedLibrary("value") PythonObjectLibrary lib) {
            return lib.asJavaDouble(value);
        }
    }

    public abstract static class HPyConvertArgsToSulongNode extends PNodeWithContext {

        public abstract void executeInto(VirtualFrame frame, GraalHPyContext hpyContext, Object[] args, int argsOffset, Object[] dest, int destOffset);

        abstract HPyCloseArgHandlesNode createCloseHandleNode();
    }

    public abstract static class HPyCloseArgHandlesNode extends PNodeWithContext {

        public abstract void executeInto(VirtualFrame frame, GraalHPyContext hpyContext, Object[] args, int argsOffset);
    }

    public abstract static class HPyVarargsToSulongNode extends HPyConvertArgsToSulongNode {

        @Specialization
        static void doConvert(GraalHPyContext hpyContext, Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached HPyAsHandleNode selfAsHandleNode) {
            dest[destOffset] = selfAsHandleNode.execute(hpyContext, args[argsOffset]);
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
        static void doConvert(GraalHPyContext hpyContext, Object[] dest, int destOffset,
                        @Cached HPyEnsureHandleNode ensureHandleNode,
                        @Cached ConditionProfile isAllocatedProfile,
                        @Cached HPyCloseArrayWrapperNode closeArrayWrapperNode) {
            ensureHandleNode.execute(hpyContext, dest[destOffset]).close(hpyContext, isAllocatedProfile);
            closeArrayWrapperNode.execute(hpyContext, (HPyArrayWrapper) dest[destOffset + 1]);
        }
    }

    /**
     * Always closes parameter at position {@code destOffset} (assuming that it is a handle).
     */
    public abstract static class HPySelfHandleCloseNode extends HPyCloseArgHandlesNode {

        @Specialization
        static void doConvert(GraalHPyContext hpyContext, Object[] dest, int destOffset,
                        @Cached ConditionProfile isAllocatedProfile,
                        @Cached HPyEnsureHandleNode ensureHandleNode) {
            ensureHandleNode.execute(hpyContext, dest[destOffset]).close(hpyContext, isAllocatedProfile);
        }
    }

    public abstract static class HPyKeywordsToSulongNode extends HPyConvertArgsToSulongNode {

        @Specialization
        static void doConvert(GraalHPyContext hpyContext, Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached HPyAsHandleNode selfAsHandleNode,
                        @Cached HPyAsHandleNode kwAsHandleNode) {
            dest[destOffset] = selfAsHandleNode.execute(hpyContext, args[argsOffset]);
            dest[destOffset + 1] = args[argsOffset + 1];
            dest[destOffset + 2] = args[argsOffset + 2];
            dest[destOffset + 3] = kwAsHandleNode.execute(hpyContext, args[argsOffset + 3]);
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
        static void doConvert(GraalHPyContext hpyContext, Object[] dest, int destOffset,
                        @Cached HPyEnsureHandleNode ensureHandleNode,
                        @Cached ConditionProfile isAllocatedProfile,
                        @Cached HPyCloseArrayWrapperNode closeArrayWrapperNode) {
            ensureHandleNode.execute(hpyContext, dest[destOffset]).close(hpyContext, isAllocatedProfile);
            closeArrayWrapperNode.execute(hpyContext, (HPyArrayWrapper) dest[destOffset + 1]);
            ensureHandleNode.execute(hpyContext, dest[destOffset + 3]).close(hpyContext, isAllocatedProfile);
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
        static void cached0(GraalHPyContext hpyContext, Object[] args, int argsOffset, Object[] dest, int destOffset) {
        }

        @Specialization(guards = {"args.length == cachedLength", "isLeArgsOffsetPlus(cachedLength, argsOffset, 8)"}, limit = "1", replaces = "cached0")
        @ExplodeLoop
        static void cachedLoop(GraalHPyContext hpyContext, Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached("args.length") int cachedLength,
                        @Cached HPyAsHandleNode toSulongNode) {
            CompilerAsserts.partialEvaluationConstant(destOffset);
            for (int i = 0; i < cachedLength - argsOffset; i++) {
                dest[destOffset + i] = toSulongNode.execute(hpyContext, args[argsOffset + i]);
            }
        }

        @Specialization(replaces = {"cached0", "cachedLoop"})
        static void uncached(GraalHPyContext hpyContext, Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached HPyAsHandleNode toSulongNode) {
            int len = args.length;
            for (int i = 0; i < len - argsOffset; i++) {
                dest[destOffset + i] = toSulongNode.execute(hpyContext, args[argsOffset + i]);
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
        static void cached0(GraalHPyContext hpyContext, Object[] dest, int destOffset) {
        }

        @Specialization(guards = {"dest.length == cachedLength", "isLeArgsOffsetPlus(cachedLength, destOffset, 8)"}, limit = "1", replaces = "cached0")
        @ExplodeLoop
        static void cachedLoop(GraalHPyContext hpyContext, Object[] dest, int destOffset,
                        @Cached("dest.length") int cachedLength,
                        @Cached ConditionProfile isAllocatedProfile,
                        @Cached HPyEnsureHandleNode ensureHandleNode) {
            CompilerAsserts.partialEvaluationConstant(destOffset);
            for (int i = 0; i < cachedLength - destOffset; i++) {
                ensureHandleNode.execute(hpyContext, dest[destOffset + i]).close(hpyContext, isAllocatedProfile);
            }
        }

        @Specialization(replaces = {"cached0", "cachedLoop"})
        static void uncached(GraalHPyContext hpyContext, Object[] dest, int destOffset,
                        @Cached ConditionProfile isAllocatedProfile,
                        @Cached HPyEnsureHandleNode ensureHandleNode) {
            int len = dest.length;
            for (int i = 0; i < len - destOffset; i++) {
                ensureHandleNode.execute(hpyContext, dest[destOffset + i]).close(hpyContext, isAllocatedProfile);
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
        static void doConvert(GraalHPyContext hpyContext, Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached HPyAsHandleNode selfAsHandleNode) {
            dest[destOffset] = selfAsHandleNode.execute(hpyContext, args[argsOffset]);
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
        static void doConvert(GraalHPyContext hpyContext, Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached HPyAsHandleNode asHandleNode) {
            dest[destOffset] = asHandleNode.execute(hpyContext, args[argsOffset]);
            dest[destOffset + 1] = asHandleNode.execute(hpyContext, args[argsOffset + 1]);
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
        static void doConvert(GraalHPyContext hpyContext, Object[] dest, int destOffset,
                        @Cached ConditionProfile isAllocatedProfile,
                        @Cached HPyEnsureHandleNode ensureHandleNode) {
            ensureHandleNode.execute(hpyContext, dest[destOffset]).close(hpyContext, isAllocatedProfile);
            ensureHandleNode.execute(hpyContext, dest[destOffset + 1]).close(hpyContext, isAllocatedProfile);
        }
    }

    /**
     * Argument converter for calling a native legacy get/set descriptor getter function. The native
     * signature is: {@code PyObject* getter(struct _HPyObject_head_s *self, void* closure)} whereas
     * {@code struct _HPyObject_head_s} is size-compatible to {@code PyObject}.
     */
    public abstract static class HPyLegacyGetSetGetterToSulongNode extends ConvertArgsToSulongNode {

        @Specialization
        static void doConvert(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached HPyGetNativeSpacePointerNode readNativeSpaceNode) {
            dest[destOffset] = readNativeSpaceNode.execute(args[argsOffset]);
            dest[destOffset + 1] = args[argsOffset + 1];
        }
    }

    /**
     * Argument converter for calling a native legacy get/set descriptor setter function. The native
     * signature is:
     * {@code int setter(struct _HPyObject_head_s *self, PyObject *value, void* closure)}.
     */
    public abstract static class HPyLegacyGetSetSetterToSulongNode extends ConvertArgsToSulongNode {

        @Specialization
        static void doConvert(Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached HPyGetNativeSpacePointerNode getNativeSpacePointerNode,
                        @Cached ToBorrowedRefNode toSulongNode) {
            dest[destOffset] = getNativeSpacePointerNode.execute(args[argsOffset]);
            dest[destOffset + 1] = toSulongNode.execute(args[argsOffset + 1]);
            dest[destOffset + 2] = args[argsOffset + 2];
        }
    }

    /**
     * The counter part of {@link HPyGetSetSetterToSulongNode}.
     */
    public abstract static class HPyLegacyGetSetSetterDecrefNode extends HPyCloseArgHandlesNode {

        @Specialization
        static void doConvert(@SuppressWarnings("unused") GraalHPyContext hpyContext, Object[] dest, int destOffset,
                        @Cached SubRefCntNode subRefCntNode) {
            subRefCntNode.dec(dest[destOffset + 1]);
        }
    }

    /**
     * Converts {@code self} to an HPy handle and any other argument to {@code HPy_ssize_t}.
     */
    public abstract static class HPySSizeArgFuncToSulongNode extends HPyConvertArgsToSulongNode {

        @Specialization(guards = {"isArity(args.length, argsOffset, 2)"})
        static void doHandleSsizeT(VirtualFrame frame, GraalHPyContext hpyContext, Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached ConvertPIntToPrimitiveNode asSsizeTNode) {
            CompilerAsserts.partialEvaluationConstant(argsOffset);
            dest[destOffset] = asHandleNode.execute(hpyContext, args[argsOffset]);
            dest[destOffset + 1] = asSsizeTNode.execute(frame, args[argsOffset + 1], 1, Long.BYTES);
        }

        @Specialization(guards = {"isArity(args.length, argsOffset, 3)"})
        static void doHandleSsizeTSsizeT(VirtualFrame frame, GraalHPyContext hpyContext, Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached ConvertPIntToPrimitiveNode asSsizeTNode) {
            CompilerAsserts.partialEvaluationConstant(argsOffset);
            dest[destOffset] = asHandleNode.execute(hpyContext, args[argsOffset]);
            dest[destOffset + 1] = asSsizeTNode.execute(frame, args[argsOffset + 1], 1, Long.BYTES);
            dest[destOffset + 2] = asSsizeTNode.execute(frame, args[argsOffset + 2], 1, Long.BYTES);
        }

        @Specialization(replaces = {"doHandleSsizeT", "doHandleSsizeTSsizeT"})
        static void doGeneric(VirtualFrame frame, @SuppressWarnings("unused") GraalHPyContext hpyContext, Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached ConvertPIntToPrimitiveNode asSsizeTNode) {
            dest[destOffset] = asHandleNode.execute(hpyContext, args[argsOffset]);
            for (int i = 1; i < args.length - argsOffset; i++) {
                dest[destOffset + i] = asSsizeTNode.execute(frame, args[argsOffset + i], 1, Long.BYTES);
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

    @GenerateUncached
    abstract static class HPyLongFromLong extends Node {
        public abstract Object execute(int value, boolean signed);

        public abstract Object execute(long value, boolean signed);

        public abstract Object execute(Object value, boolean signed);

        @Specialization(guards = "signed")
        Object doSignedInt(int n, @SuppressWarnings("unused") boolean signed,
                        @Cached HPyAsHandleNode toSulongNode) {
            return toSulongNode.execute(n);
        }

        @Specialization(guards = "!signed")
        Object doUnsignedInt(int n, @SuppressWarnings("unused") boolean signed,
                        @Cached HPyAsHandleNode toSulongNode) {
            if (n < 0) {
                return toSulongNode.execute(n & 0xFFFFFFFFL);
            }
            return toSulongNode.execute(n);
        }

        @Specialization(guards = "signed")
        Object doSignedLong(long n, @SuppressWarnings("unused") boolean signed,
                        @Cached HPyAsHandleNode toSulongNode) {
            return toSulongNode.execute(n);
        }

        @Specialization(guards = {"!signed", "n >= 0"})
        Object doUnsignedLongPositive(long n, @SuppressWarnings("unused") boolean signed,
                        @Cached HPyAsHandleNode toSulongNode) {
            return toSulongNode.execute(n);
        }

        @Specialization(guards = {"!signed", "n < 0"})
        Object doUnsignedLongNegative(long n, @SuppressWarnings("unused") boolean signed,
                        @Cached HPyAsHandleNode toSulongNode,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return toSulongNode.execute(factory.createInt(convertToBigInteger(n)));
        }

        @TruffleBoundary
        private static BigInteger convertToBigInteger(long n) {
            return BigInteger.valueOf(n).add(BigInteger.ONE.shiftLeft(Long.SIZE));
        }

        @Specialization
        Object doPointer(PythonNativeObject n, @SuppressWarnings("unused") boolean signed,
                        @Cached HPyAsHandleNode toSulongNode,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return toSulongNode.execute(factory.createNativeVoidPtr(n.getPtr()));
        }
    }

    /**
     * <pre>
     *     typedef struct {
     *         const char* name;
     *         int basicsize;
     *         int itemsize;
     *         unsigned int flags;
     *         void *legacy_slots;
     *         HPyDef **defines;
     *     } HPyType_Spec;
     * </pre>
     */
    @GenerateUncached
    abstract static class HPyCreateTypeFromSpecNode extends Node {

        abstract Object execute(GraalHPyContext context, Object typeSpec, Object typeSpecParamArray);

        @Specialization
        static Object doGeneric(GraalHPyContext context, Object typeSpec, Object typeSpecParamArray,
                        @CachedLibrary(limit = "3") InteropLibrary ptrLib,
                        @CachedLibrary(limit = "3") InteropLibrary valueLib,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PCallHPyFunction callHelperFunctionNode,
                        @Cached ReadAttributeFromObjectNode readAttributeFromObjectNode,
                        @Cached WriteAttributeToObjectNode writeAttributeToObjectNode,
                        @Cached CallNode callTypeNewNode,
                        @Cached CastToJavaIntLossyNode castToJavaIntNode,
                        @Cached HPyCreateFunctionNode addFunctionNode,
                        @Cached HPyAddMemberNode addMemberNode,
                        @Cached HPyCreateSlotNode addSlotNode,
                        @Cached HPyCreateLegacySlotNode createLegacySlotNode,
                        @Cached HPyCreateGetSetDescriptorNode createGetSetDescriptorNode,
                        @Cached HPyAsPythonObjectNode hPyAsPythonObjectNode,
                        @Cached PRaiseNode raiseNode) {

            try {
                // the name as given by the specification
                String specName = castToJavaStringNode.execute(fromCharPointerNode.execute(ptrLib.readMember(typeSpec, "name")));

                // extract module and type name
                String[] names = splitName(specName);
                assert names.length == 2;

                // extract bases from type spec params

                PTuple bases;
                try {
                    bases = extractBases(context, typeSpecParamArray, ptrLib, castToJavaIntNode, callHelperFunctionNode, hPyAsPythonObjectNode, factory);
                } catch (CannotCastException | InteropException e) {
                    throw raiseNode.raise(SystemError, "failed to extract bases from type spec params for type %s", specName);
                }

                // create the type object
                Object typeBuiltin = readAttributeFromObjectNode.execute(context.getContext().getBuiltins(), BuiltinNames.TYPE);
                Object newType = callTypeNewNode.execute(typeBuiltin, names[1], bases, factory.createDict());

                // determine and set the correct module attribute
                String value = names[0];
                if (value != null) {
                    writeAttributeToObjectNode.execute(newType, SpecialAttributeNames.__MODULE__, value);
                } else {
                    // TODO(fa): issue deprecation warning with message "builtin type %.200s has no
                    // __module__ attribute"
                }

                // store flags, basicsize, and itemsize to type
                long flags = castToLong(valueLib, ptrLib.readMember(typeSpec, "flags"));
                long basicSize = castToLong(valueLib, ptrLib.readMember(typeSpec, "basicsize"));
                long itemSize = castToLong(valueLib, ptrLib.readMember(typeSpec, "itemsize"));
                writeAttributeToObjectNode.execute(newType, GraalHPyDef.TYPE_HPY_BASICSIZE, basicSize);
                writeAttributeToObjectNode.execute(newType, GraalHPyDef.TYPE_HPY_ITEMSIZE, itemSize);
                writeAttributeToObjectNode.execute(newType, GraalHPyDef.TYPE_HPY_FLAGS, flags);

                // process defines
                Object defines = callHelperFunctionNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_TYPE_SPEC_GET_DEFINES, typeSpec);
                // field 'defines' may be 'NULL'
                if (!ptrLib.isNull(defines)) {
                    if (!ptrLib.hasArrayElements(defines)) {
                        return raiseNode.raise(SystemError, "field 'defines' did not return an array for type %s", specName);
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
                                property = addSlotNode.execute(context, newType, slotDef);
                                break;
                            case GraalHPyDef.HPY_DEF_KIND_MEMBER:
                                Object memberDef = callHelperFunctionNode.call(context, GRAAL_HPY_DEF_GET_MEMBER, moduleDefine);
                                property = addMemberNode.execute(context, memberDef);
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
                            writeAttributeToObjectNode.execute(newType, property.key, property.value);
                        }
                    }
                }

                // process legacy slots; this is of type 'cpy_PyTypeSlot legacy_slots[]'
                Object legacySlots = callHelperFunctionNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_TYPE_SPEC_GET_LEGECY_SLOTS, typeSpec);
                if (!ptrLib.isNull(legacySlots)) {
                    int nLegacySlots = PInt.intValueExact(ptrLib.getArraySize(legacySlots));
                    for (int i = 0; i < nLegacySlots; i++) {
                        Object legacySlotDef = ptrLib.readArrayElement(legacySlots, i);
                        HPyProperty property = createLegacySlotNode.execute(context, newType, legacySlotDef);
                        if (property != null) {
                            writeAttributeToObjectNode.execute(newType, property.key, property.value);
                        }
                    }
                }

                return newType;
            } catch (CannotCastException | InteropException e) {
                throw raiseNode.raise(SystemError, "Could not create type from spec because: %m", e);
            } catch (OverflowException e) {
                throw raiseNode.raise(SystemError, "Could not create type from spec: too many members");
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
                int specParamKind = castToJavaIntNode.execute(ptrLib.readMember(specParam, "kind"));
                Object specParamObject = asPythonObjectNode.execute(context, callHelperFunctionNode.call(context, GRAAL_HPY_TYPE_SPEC_PARAM_GET_OBJECT, specParam));

                switch (specParamKind) {
                    case GraalHPyDef.HPyType_SPEC_PARAM_BASE:
                        // In this case, the 'specParamObject' is a single handle. We add it to
                        // the list of bases.
                        assert PGuards.isClass(specParamObject, InteropLibrary.getUncached()) : "base object is not a Python class";
                        basesList.add(specParamObject);
                        break;
                    case GraalHPyDef.HPyType_SPEC_PARAM_BASES_TUPLE:
                        // In this case, the 'specParamObject' is tuple. According to the
                        // reference implementation, we immediately use this tuple and throw
                        // away any other single base classes or subsequent params.
                        assert PGuards.isPTuple(specParamObject) : "type spec param claims to be a tuple but isn't";
                        return (PTuple) specParamObject;
                    default:
                        assert false : "unknown type spec param kind";
                }
            }
            return factory.createTuple(basesList.toArray());
        }

        /**
         * Extract the heap type's and the module's name from the name given by the type
         * specification.<br/>
         * According to CPython, we need to look for the first {@code '.'} and everything before it
         * is the module name. Everything after it (which may also contain more dots) is the type
         * name. See also: {@code typeobject.c: PyType_FromSpecWithBases}
         */
        @TruffleBoundary
        private static String[] splitName(String specName) {
            int firstDotIdx = specName.indexOf('.');
            if (firstDotIdx != -1) {
                return new String[]{specName.substring(0, firstDotIdx), specName.substring(firstDotIdx + 1)};
            }
            return new String[]{null, specName};
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
    }

    @ImportStatic(PythonOptions.class)
    @GenerateUncached
    public abstract static class HPyGetNativeSpacePointerNode extends Node {

        public abstract Object execute(Object object);

        @Specialization(limit = "getVariableArgumentInlineCacheLimit()")
        static Object doDynamicObject(DynamicObject object,
                        @CachedLibrary("object") DynamicObjectLibrary lib) {
            return lib.getOrDefault(object, OBJECT_HPY_NATIVE_SPACE, PNone.NO_VALUE);
        }

        @Specialization(replaces = "doDynamicObject")
        static Object doOther(Object object,
                        @CachedLibrary(limit = "getVariableArgumentInlineCacheLimit()") DynamicObjectLibrary lib) {
            if (object instanceof DynamicObject) {
                return lib.getOrDefault((DynamicObject) object, OBJECT_HPY_NATIVE_SPACE, PNone.NO_VALUE);
            }
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

}
