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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RuntimeWarning;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyHandle.NULL_HANDLE_DELEGATE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_DEF_GET_KIND;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_DEF_GET_METH;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_DEF_GET_SLOT;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_FROM_HPY_MODULE_DEF;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_FROM_HPY_TYPE_SPEC;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_FROM_HPY_TYPE_SPEC_PARAM_ARRAY;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_MODULE_GET_DEFINES;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_MODULE_GET_LEGACY_METHODS;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_MODULE_INIT_GLOBALS;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_SLOT_GET_SLOT;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_WRITE_HPY;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_WRITE_PTR;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_WRITE_UL;
import static com.oracle.graal.python.nodes.BuiltinNames.T_APPEND;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins.WarnNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.capsule.PyCapsule;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext.LLVMType;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.CastToJavaDoubleNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.CreateMethodNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.GetLLVMType;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.PThreadState;
import com.oracle.graal.python.builtins.objects.cext.capi.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CByteArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.AsNativePrimitiveNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.EncodeNativeStringNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.GetByteArrayNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.SizeofWCharNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.UnicodeFromWcharNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPySlot;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyAsContextNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyAsHandleNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyAsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyCloseAndGetHandleNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyCloseHandleNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyCreateFunctionNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyCreateTypeFromSpecNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyEnsureHandleNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyGetNativeSpacePointerNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyLongFromLong;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyRaiseNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyTransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyTypeGetNameNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.PCallHPyFunction;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.RecursiveExceptionMatches;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsContextNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsHandleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsNativeInt64NodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.NoGeneralizationNode;
import com.oracle.graal.python.builtins.objects.contextvars.PContextVar;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.StrGetItemNodeWithSlice;
import com.oracle.graal.python.builtins.objects.str.StringNodes.InternStringNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.lib.CanBeDoubleNode;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyDictKeys;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyLongAsDoubleNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectSetItem;
import com.oracle.graal.python.lib.PySequenceCheckNode;
import com.oracle.graal.python.lib.PySequenceContainsNode;
import com.oracle.graal.python.lib.PyUnicodeFromEncodedObject;
import com.oracle.graal.python.lib.PyUnicodeReadCharNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.WriteUnraisableNode;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNode;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.InlinedGetClassNode;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntLossyNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongExactNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.util.CharsetMapping;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.strings.TruffleString;

@SuppressWarnings("static-method")
public abstract class GraalHPyContextFunctions {

    public enum FunctionMode {
        OBJECT,
        CHAR_PTR,
        INT32
    }

    public enum ReturnType {
        OBJECT {
            @Override
            public CExtToNativeNode createToNativeNode() {
                return HPyAsHandleNodeGen.create();
            }

            @Override
            public CExtToNativeNode getUncachedToNativeNode() {
                return HPyAsHandleNodeGen.getUncached();
            }

            @Override
            public CExtToJavaNode createToJavaNode() {
                return HPyAsPythonObjectNodeGen.create();
            }

            @Override
            public CExtToJavaNode getUncachedToJavaNode() {
                return HPyAsPythonObjectNodeGen.getUncached();
            }
        },
        INT {
            @Override
            public CExtToNativeNode createToNativeNode() {
                return HPyAsNativeInt64NodeGen.create();
            }

            @Override
            public CExtToNativeNode getUncachedToNativeNode() {
                return HPyAsNativeInt64NodeGen.getUncached();
            }
        };

        public abstract CExtToNativeNode createToNativeNode();

        public abstract CExtToNativeNode getUncachedToNativeNode();

        public CExtToJavaNode createToJavaNode() {
            throw CompilerDirectives.shouldNotReachHere("unsupported");
        }

        public CExtToJavaNode getUncachedToJavaNode() {
            throw CompilerDirectives.shouldNotReachHere("unsupported");
        }
    }

    protected static void checkArity(Object[] arguments, int expectedArity) throws ArityException {
        if (arguments.length != expectedArity) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw ArityException.create(expectedArity, expectedArity, arguments.length);
        }
    }

    public abstract static class GraalHPyContextFunction extends Node {

        public abstract Object execute(Object[] arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException;

    }

    public abstract static class HPyContextFunctionWithFlag extends Node {

        public abstract Object execute(Object[] arguments, boolean flag) throws UnsupportedTypeException, ArityException, UnsupportedMessageException;
    }

    public abstract static class HPyContextFunctionWithMode extends Node {

        public abstract Object execute(Object[] arguments, FunctionMode mode) throws UnsupportedTypeException, ArityException, UnsupportedMessageException;
    }

    public abstract static class HPyContextFunctionWithBuiltinType extends Node {

        public abstract Object execute(Object[] arguments, PythonBuiltinClassType type) throws UnsupportedTypeException, ArityException, UnsupportedMessageException;
    }

    public abstract static class HPyContextFunctionWithObject extends Node {

        public abstract Object execute(Object[] arguments, Object obj) throws UnsupportedTypeException, ArityException, UnsupportedMessageException;
    }

    @GenerateUncached
    public abstract static class GraalHPyDup extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached HPyAsHandleNode asHandleNode) throws ArityException {
            checkArity(arguments, 2);
            return asHandleNode.execute(asPythonObjectNode.execute(arguments[1]));
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyClose extends GraalHPyContextFunction {
        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyCloseHandleNode closeHandleNode) throws ArityException {
            checkArity(arguments, 2);
            closeHandleNode.execute(arguments[1]);
            return 0;
        }
    }

    /**
     * Creates an HPy module from a module definition structure:
     *
     * <pre>
     * typedef struct {
     *     const char* name;
     *     const char* doc;
     *     HPy_ssize_t size;
     *     cpy_PyMethodDef *legacy_methods;
     *     HPyDef **defines;
     * } HPyModuleDef;
     * </pre>
     */
    @GenerateUncached
    public abstract static class GraalHPyModuleCreate extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PCallHPyFunction callFromHPyModuleDefNode,
                        @Cached PCallHPyFunction callGetterNode,
                        @CachedLibrary(limit = "3") InteropLibrary ptrLib,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached CastToJavaIntLossyNode castToJavaIntNode,
                        @Cached WriteAttributeToObjectNode writeAttrNode,
                        @Cached WriteAttributeToDynamicObjectNode writeAttrToMethodNode,
                        @Cached HPyCreateFunctionNode addFunctionNode,
                        @Cached CreateMethodNode addLegacyMethodNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, 2);
            GraalHPyContext context = asContextNode.execute(arguments[0]);

            // call to type the pointer
            Object moduleDef = callFromHPyModuleDefNode.call(context, GRAAL_HPY_FROM_HPY_MODULE_DEF, arguments[1]);

            assert checkLayout(moduleDef);

            try {
                TruffleString mName;
                Object mDoc;
                try {
                    mName = fromCharPointerNode.execute(ptrLib.readMember(moduleDef, "name"));

                    // do not eagerly read the doc string; this turned out to be unnecessarily
                    // expensive
                    mDoc = fromCharPointerNode.execute(ptrLib.readMember(moduleDef, "doc"), false);
                } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw raiseNode.raise(PythonBuiltinClassType.SystemError, ErrorMessages.CANNOT_CREATE_MODULE_FROM_DEFINITION, e);
                }

                // create the module object
                PythonModule module = factory.createPythonModule(mName);

                // process HPy methods
                Object moduleDefines = callGetterNode.call(context, GRAAL_HPY_MODULE_GET_DEFINES, moduleDef);
                try {
                    long nModuleDefines;
                    if (ptrLib.isNull(moduleDefines)) {
                        nModuleDefines = 0;
                    } else if (!ptrLib.hasArrayElements(moduleDefines)) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw raiseNode.raise(PythonBuiltinClassType.SystemError, ErrorMessages.FIELD_DID_NOT_RETURN_ARRAY, "defines");
                    } else {
                        nModuleDefines = ptrLib.getArraySize(moduleDefines);
                    }

                    for (long i = 0; i < nModuleDefines; i++) {
                        Object moduleDefine = ptrLib.readArrayElement(moduleDefines, i);
                        int kind = castToJavaIntNode.execute(callGetterNode.call(context, GRAAL_HPY_DEF_GET_KIND, moduleDefine));
                        switch (kind) {
                            case GraalHPyDef.HPY_DEF_KIND_METH:
                                Object methodDef = callGetterNode.call(context, GRAAL_HPY_DEF_GET_METH, moduleDefine);
                                PBuiltinFunction fun = addFunctionNode.execute(context, null, methodDef);
                                PBuiltinMethod method = factory.createBuiltinMethod(module, fun);
                                writeAttrToMethodNode.execute(method, SpecialAttributeNames.T___MODULE__, mName);
                                writeAttrNode.execute(module, fun.getName(), method);
                                break;
                            case GraalHPyDef.HPY_DEF_KIND_SLOT:
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

                // process legacy methods
                Object legacyMethods = callGetterNode.call(context, GRAAL_HPY_MODULE_GET_LEGACY_METHODS, moduleDef);
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
                    int globalStartIdx = context.getEndIndexOfGlobalTable();
                    int nModuleGlobals = ptrLib.asInt(callGetterNode.call(context, GRAAL_HPY_MODULE_INIT_GLOBALS, moduleDef, globalStartIdx));
                    context.initBatchGlobals(globalStartIdx, nModuleGlobals);
                } catch (UnsupportedMessageException e) {
                    // should not happen unless the number of module global is larger than an `int`
                    throw CompilerDirectives.shouldNotReachHere();
                }

                writeAttrNode.execute(module, SpecialAttributeNames.T___DOC__, mDoc);

                return asHandleNode.execute(module);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(context, e);
                return GraalHPyHandle.NULL_HANDLE;
            }
        }

        @TruffleBoundary
        private static boolean checkLayout(Object moduleDef) {
            String[] members = new String[]{"name", "doc", "size", "legacy_methods", "defines"};
            InteropLibrary lib = InteropLibrary.getUncached(moduleDef);
            for (String member : members) {
                if (!lib.isMemberReadable(moduleDef, member)) {
                    return false;
                }
            }
            return true;
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyBoolFromLong extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached CastToJavaLongExactNode castToJavaLongNode) throws ArityException {
            checkArity(arguments, 2);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            long left = castToJavaLongNode.execute(arguments[1]);
            Python3Core core = context.getContext();
            return asHandleNode.execute(left != 0 ? core.getTrue() : core.getFalse());
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyLongFromLong extends HPyContextFunctionWithFlag {

        @Specialization
        static Object doGeneric(Object[] arguments, boolean signed,
                        @Cached CastToJavaLongExactNode castToJavaLongNode,
                        @Cached HPyLongFromLong fromLongNode) throws ArityException {
            checkArity(arguments, 2);
            long left = castToJavaLongNode.execute(arguments[1]);
            return fromLongNode.execute(left, signed);
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyLongAsPrimitive extends Node {

        public abstract Object execute(Object[] arguments, int signed, int targetSize, boolean exact, boolean requiresPInt) throws ArityException;

        @Specialization
        static Object doGeneric(Object[] arguments, int signed, int targetSize, boolean exact, boolean requiresPInt,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached InlinedGetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, 2);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object object = asPythonObjectNode.execute(arguments[1]);
            try {
                if (requiresPInt && !isSubtypeNode.execute(getClassNode.execute(inliningTarget, object), PythonBuiltinClassType.PInt)) {
                    throw raiseNode.raise(TypeError, ErrorMessages.INTEGER_REQUIRED);
                }
                return asNativePrimitiveNode.execute(object, signed, targetSize, exact);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(context, e);
                return -1L;
            }
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyLongAsDouble extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached PyLongAsDoubleNode asDoubleNode) throws ArityException {
            checkArity(arguments, 2);
            return asDoubleNode.execute(asPythonObjectNode.execute(arguments[1]));
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyDictNew extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached PythonObjectFactory factory,
                        @Cached HPyAsHandleNode asHandleNode) throws ArityException {
            checkArity(arguments, 1);
            return asHandleNode.execute(factory.createDict());
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyDictSetItem extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode dictAsPythonObjectNode,
                        @Cached HPyAsPythonObjectNode keyAsPythonObjectNode,
                        @Cached HPyAsPythonObjectNode valueAsPythonObjectNode,
                        @Cached HashingStorageSetItem setItem,
                        @Cached("createClassProfile()") ValueProfile profile,
                        @Cached("createCountingProfile()") ConditionProfile updateStorageProfile,
                        @Cached HPyRaiseNode raiseNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, 4);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object left = profile.profile(dictAsPythonObjectNode.execute(arguments[1]));
            if (!PGuards.isDict(left)) {
                return raiseNode.raiseIntWithoutFrame(context, -1, SystemError, ErrorMessages.BAD_INTERNAL_CALL);
            }
            PDict dict = (PDict) left;
            Object key = keyAsPythonObjectNode.execute(arguments[2]);
            Object value = valueAsPythonObjectNode.execute(arguments[3]);
            try {
                HashingStorage dictStorage = dict.getDictStorage();
                HashingStorage updatedStorage = setItem.execute(null, dictStorage, key, value);
                if (updateStorageProfile.profile(updatedStorage != dictStorage)) {
                    dict.setDictStorage(updatedStorage);
                }
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(context, e);
                return -1;
            }
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyDictGetItem extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode dictAsPythonObjectNode,
                        @Cached HPyAsPythonObjectNode keyAsPythonObjectNode,
                        @Cached HashingStorageGetItem getItem,
                        @Cached("createClassProfile()") ValueProfile profile,
                        @Cached HPyRaiseNode raiseNode,
                        @Cached HPyAsHandleNode asHandleNode) throws ArityException {
            checkArity(arguments, 3);
            Object left = profile.profile(dictAsPythonObjectNode.execute(arguments[1]));
            if (!PGuards.isDict(left)) {
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                return raiseNode.raiseWithoutFrame(context, GraalHPyHandle.NULL_HANDLE, SystemError, ErrorMessages.BAD_INTERNAL_CALL);
            }
            PDict dict = (PDict) left;
            Object key = keyAsPythonObjectNode.execute(arguments[2]);
            try {
                Object item = getItem.execute(null, dict.getDictStorage(), key);
                if (item != null) {
                    return asHandleNode.execute(item);
                }
                return GraalHPyHandle.NULL_HANDLE;
            } catch (PException e) {
                /*
                 * This function has the same (odd) error behavior as PyDict_GetItem: If an error
                 * occurred, the error is cleared and NULL is returned.
                 */
                return GraalHPyHandle.NULL_HANDLE;
            }
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyListNew extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached CastToJavaIntExactNode castToJavaIntNode,
                        @Cached PythonObjectFactory factory,
                        @Cached HPyAsHandleNode asHandleNode) throws ArityException {
            checkArity(arguments, 2);
            int len = castToJavaIntNode.execute(arguments[1]);
            Object[] data = new Object[len];
            for (int i = 0; i < len; i++) {
                // TODO(fa) maybe this should be NO_VALUE (representing native 'NULL')
                data[i] = PNone.NONE;
            }
            return asHandleNode.execute(factory.createList(data));
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyListAppend extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode listAsPythonObjectNode,
                        @Cached HPyAsPythonObjectNode valueAsPythonObjectNode,
                        @Cached LookupInheritedAttributeNode.Dynamic lookupAppendNode,
                        @Cached CallBinaryMethodNode callAppendNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached HPyRaiseNode raiseNode) throws ArityException {
            checkArity(arguments, 3);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object left = listAsPythonObjectNode.execute(arguments[1]);
            if (!PGuards.isList(left)) {
                return raiseNode.raiseIntWithoutFrame(context, -1, SystemError, ErrorMessages.BAD_INTERNAL_CALL);
            }
            PList list = (PList) left;
            Object value = valueAsPythonObjectNode.execute(arguments[2]);
            Object attrAppend = lookupAppendNode.execute(list, T_APPEND);
            if (attrAppend == PNone.NO_VALUE) {
                return raiseNode.raiseIntWithoutFrame(context, -1, SystemError, ErrorMessages.LIST_DOES_NOT_ATTR_APPEND);
            }
            try {
                callAppendNode.executeObject(null, attrAppend, list, value);
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(context, e);
                return -1;
            }
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyFloatFromDouble extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached CastToJavaDoubleNode castToJavaDoubleNode,
                        @Cached HPyAsHandleNode asHandleNode) throws ArityException {
            checkArity(arguments, 2);
            // note: node 'CastToJavaDoubleNode' cannot throw a PException
            double value = castToJavaDoubleNode.execute(arguments[1]);
            return asHandleNode.execute(value);
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyFloatAsDouble extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached PyFloatAsDoubleNode asDoubleNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, 2);
            try {
                return asDoubleNode.execute(null, asPythonObjectNode.execute(arguments[1]));
            } catch (PException e) {
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                transformExceptionToNativeNode.execute(context, e);
                return -1.0;
            }
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyCheckBuiltinType extends HPyContextFunctionWithBuiltinType {

        @Specialization
        static Object doGeneric(Object[] arguments, PythonBuiltinClassType expectedType,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached InlinedGetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode) throws ArityException {
            checkArity(arguments, 2);
            Object object = asPythonObjectNode.execute(arguments[1]);
            return PInt.intValue(isSubtypeNode.execute(getClassNode.execute(inliningTarget, object), expectedType));
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyErrRaisePredefined extends Node {

        public abstract Object execute(Object[] arguments, PythonBuiltinClassType errType, TruffleString errorMessage, boolean primitiveErrorValue) throws ArityException;

        @Specialization
        static Object doGeneric(Object[] arguments, PythonBuiltinClassType errType, TruffleString errorMessage, boolean primitiveErrorValue,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, 1);
            GraalHPyContext context = asContextNode.execute(arguments[0]);

            // Unfortunately, the HPyRaiseNode is not suitable because it expects a String
            // message.
            try {
                if (errorMessage != null) {
                    throw raiseNode.raise(errType, errorMessage);
                } else {
                    throw raiseNode.raise(errType, new Object[]{PNone.NO_VALUE});
                }
            } catch (PException p) {
                transformExceptionToNativeNode.execute(context, p);
            }
            return primitiveErrorValue ? 0 : GraalHPyHandle.NULL_HANDLE;
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyErrSetString extends HPyContextFunctionWithFlag {

        @Specialization
        static Object doGeneric(Object[] arguments, boolean stringMode,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached IsSubtypeNode isExcValueSubtypeNode,
                        @Cached InlinedGetClassNode getClassNode,
                        @Cached PCallHPyFunction callFromStringNode,
                        @Cached CallNode callExceptionConstructorNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, 3);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object errTypeObj = asPythonObjectNode.execute(arguments[1]);
            if (!(PGuards.isPythonClass(errTypeObj) && isSubtypeNode.execute(errTypeObj, PythonBuiltinClassType.PBaseException))) {
                return raiseNode.raise(SystemError, ErrorMessages.EXCEPTION_NOT_BASEEXCEPTION, errTypeObj);
            }
            try {
                Object exception;
                if (stringMode) {
                    /*
                     * We need to eagerly convert the C string into a Python string because the
                     * given buffer may die right after this call returns.
                     */
                    Object valueObj = callFromStringNode.call(context, GraalHPyNativeSymbol.POLYGLOT_FROM_STRING, arguments[2], "utf-8");
                    exception = callExceptionConstructorNode.execute(errTypeObj, valueObj);
                } else {
                    Object valueObj = asPythonObjectNode.execute(arguments[2]);
                    // If the exception value is already an exception object, just take it.
                    if (isExcValueSubtypeNode.execute(getClassNode.execute(inliningTarget, valueObj), PythonBuiltinClassType.PBaseException)) {
                        exception = valueObj;
                    } else {
                        exception = callExceptionConstructorNode.execute(errTypeObj, valueObj);
                    }
                }

                if (PGuards.isPBaseException(exception)) {
                    throw raiseNode.raiseExceptionObject((PBaseException) exception);
                }
                // This should really not happen since we did a type check above but in theory,
                // the constructor could be broken.
                throw CompilerDirectives.shouldNotReachHere();
            } catch (PException p) {
                transformExceptionToNativeNode.execute(context, p);
                return GraalHPyHandle.NULL_HANDLE;
            }
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyErrSetFromErrnoWithFilenameObjects extends HPyContextFunctionWithFlag {

        @Specialization
        static Object doGeneric(Object[] arguments, boolean withObjects,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PCallHPyFunction callFromStringNode,
                        @Cached CallNode callExceptionConstructorNode,
                        @CachedLibrary(limit = "2") InteropLibrary lib,
                        @Cached PRaiseNode raiseNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, withObjects ? 4 : 3);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object errTypeObj = asPythonObjectNode.execute(arguments[1]);
            Object i = callFromStringNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_GET_ERRNO);
            Object message = callFromStringNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_GET_STRERROR, i);
            try {
                if (!isSubtypeNode.execute(errTypeObj, PythonBuiltinClassType.PBaseException)) {
                    return raiseNode.raise(SystemError, ErrorMessages.EXCEPTION_NOT_BASEEXCEPTION, errTypeObj);
                }
                Object exception = null;
                if (!withObjects) {
                    if (!lib.isNull(arguments[2])) {
                        Object filename_fsencoded = callFromStringNode.call(context, GraalHPyNativeSymbol.POLYGLOT_FROM_STRING, arguments[2], "utf-8");
                        exception = callExceptionConstructorNode.execute(errTypeObj, i, message, filename_fsencoded);
                    }
                } else {
                    Object filenameObject = asPythonObjectNode.execute(arguments[2]);
                    if (filenameObject != NULL_HANDLE_DELEGATE) {
                        Object filenameObject2 = asPythonObjectNode.execute(arguments[3]);
                        if (filenameObject2 != NULL_HANDLE_DELEGATE) {
                            exception = callExceptionConstructorNode.execute(errTypeObj, i, message, filenameObject, 0, filenameObject2);
                        } else {
                            exception = callExceptionConstructorNode.execute(errTypeObj, i, message, filenameObject);
                        }
                    }
                }

                if (exception == null) {
                    exception = callExceptionConstructorNode.execute(errTypeObj, i, message);
                }

                if (PGuards.isPBaseException(exception)) {
                    throw raiseNode.raiseExceptionObject((PBaseException) exception);
                }
                // This should really not happen since we did a type check above but in theory,
                // the constructor could be broken.
                throw CompilerDirectives.shouldNotReachHere();
            } catch (PException p) {
                transformExceptionToNativeNode.execute(context, p);
                return GraalHPyHandle.NULL_HANDLE;
            }
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyFatalError extends GraalHPyContextFunction {
        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @CachedLibrary(limit = "1") InteropLibrary interopLib) throws ArityException {
            checkArity(arguments, 2);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            TruffleString errorMessage = ErrorMessages.MSG_NOT_SET;
            if (!interopLib.isNull(arguments[1])) {
                errorMessage = fromCharPointerNode.execute(arguments[1], false);
            }
            CExtCommonNodes.fatalError(asContextNode, context.getContext(), null, errorMessage, -1);
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyErrOccurred extends GraalHPyContextFunction {

        @Specialization
        static int doGeneric(Object[] arguments,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached HPyAsContextNode asContextNode) throws ArityException {
            checkArity(arguments, 1);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            return getThreadStateNode.getCurrentException(context.getContext()) != null ? 1 : 0;
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyErrExceptionMatches extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached RecursiveExceptionMatches exceptionMatches) throws ArityException {
            checkArity(arguments, 2);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            PException err = getThreadStateNode.getCurrentException(context.getContext());
            if (err == null) {
                return 0;
            }
            Object exc = asPythonObjectNode.execute(arguments[1]);
            if (exc == NULL_HANDLE_DELEGATE) {
                return 0;
            }
            return exceptionMatches.execute(context, err.getUnreifiedException(), exc);
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyErrClear extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached HPyAsContextNode asContextNode) throws ArityException {
            checkArity(arguments, 1);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            getThreadStateNode.setCurrentException(context.getContext(), null);
            return PNone.NO_VALUE;
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyErrWarnEx extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached CastToJavaIntExactNode castToJavaIntNode,
                        @Cached WarnNode warnNode,
                        @CachedLibrary(limit = "2") InteropLibrary interopLib) throws ArityException {
            checkArity(arguments, 4);
            Object category;
            if (interopLib.isNull(arguments[1])) {
                category = RuntimeWarning;
            } else {
                category = asPythonObjectNode.execute(arguments[1]);
            }
            TruffleString message = T_EMPTY_STRING;
            if (!interopLib.isNull(arguments[2])) {
                message = fromCharPointerNode.execute(arguments[2]);
            }
            int stackLevel = castToJavaIntNode.execute(arguments[3]);
            warnNode.warnEx(null, category, message, stackLevel);
            return 0;
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyErrWriteUnraisable extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached WriteUnraisableNode writeUnraisableNode) throws ArityException {
            checkArity(arguments, 2);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            PException exception = getThreadStateNode.getCurrentException(context.getContext());
            getThreadStateNode.setCurrentException(context.getContext(), null);
            Object object = asPythonObjectNode.execute(arguments[1]);
            writeUnraisableNode.execute(null, exception.getUnreifiedException(), null, (object instanceof PNone) ? PNone.NONE : object);
            return 0; // void
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyUnicodeAsCharsetString extends HPyContextFunctionWithObject {

        @Specialization
        static Object doGeneric(Object[] arguments, Charset charset,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached HPyAsHandleNode resultAsHandleNode,
                        @Cached EncodeNativeStringNode encodeNativeStringNode,
                        @Cached PythonObjectFactory factory,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, 2);
            Object unicodeObject = asPythonObjectNode.execute(arguments[1]);
            try {
                byte[] result = encodeNativeStringNode.execute(charset, unicodeObject, T_STRICT);
                return resultAsHandleNode.execute(factory.createBytes(result));
            } catch (PException e) {
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                transformExceptionToNativeNode.execute(context, e);
                return GraalHPyHandle.NULL_HANDLE;
            }
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyUnicodeAsUTF8AndSize extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached PCallHPyFunction callFromTyped,
                        @Cached GetLLVMType getLLVMType,
                        @Cached EncodeNativeStringNode encodeNativeStringNode,
                        @CachedLibrary(limit = "3") InteropLibrary ptrLib,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, 3);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object unicodeObject = asPythonObjectNode.execute(arguments[1]);
            Object sizePtr = arguments[2];
            try {
                byte[] result = encodeNativeStringNode.execute(StandardCharsets.UTF_8, unicodeObject, T_STRICT);
                if (!ptrLib.isNull(sizePtr)) {
                    sizePtr = callFromTyped.call(context, GraalHPyNativeSymbol.POLYGLOT_FROM_TYPED, sizePtr, getLLVMType.execute(LLVMType.Py_ssize_ptr_t));
                    try {
                        ptrLib.writeArrayElement(sizePtr, 0, (long) result.length);
                    } catch (InteropException e) {
                        throw CompilerDirectives.shouldNotReachHere();
                    }
                }
                return new CByteArrayWrapper(result);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(context, e);
                return GraalHPyHandle.NULL_HANDLE;
            }
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyUnicodeFromString extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached CastToTruffleStringNode toString,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached HPyAsHandleNode asHandleNode) throws ArityException {
            checkArity(arguments, 2);
            try {
                // FromCharPointerNode uses UTF-8 encoding by default
                TruffleString str = toString.execute(fromCharPointerNode.execute(arguments[1]));
                return asHandleNode.execute(str);
            } catch (PException e) {
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                transformExceptionToNativeNode.execute(context, e);
                return GraalHPyHandle.NULL_HANDLE;
            }
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyUnicodeFromWchar extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsHandleNode resultAsHandleNode,
                        @Cached CastToJavaLongExactNode castToJavaLongNode,
                        @Cached PCallHPyFunction callFromWcharArrayNode,
                        @Cached UnicodeFromWcharNode unicodeFromWcharNode,
                        @Cached SizeofWCharNode sizeofWCharNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, 3);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            long len = castToJavaLongNode.execute(arguments[2]);
            // Note: 'len' may be -1; in this case, function GRAAL_HPY_I8_FROM_WCHAR_ARRAY will
            // use 'wcslen' to determine the C array's length.
            Object dataArray = callFromWcharArrayNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_FROM_WCHAR_ARRAY, arguments[1], len);
            try {
                return resultAsHandleNode.execute(unicodeFromWcharNode.execute(dataArray, PInt.intValueExact(context.getWcharSize())));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(context, e);
                return GraalHPyHandle.NULL_HANDLE;
            } catch (OverflowException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyUnicodeDecodeCharset extends HPyContextFunctionWithObject {

        @Specialization
        static Object doGeneric(Object[] arguments, TruffleString charset,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached PCallHPyFunction callHPyFunction,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode) throws ArityException {
            checkArity(arguments, 2);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            // TODO GR-37896 - use polyglot from tstring
            Object result = callHPyFunction.call(context, GraalHPyNativeSymbol.POLYGLOT_FROM_STRING, arguments[1], toJavaStringNode.execute(charset));
            return asHandleNode.execute(result);
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyUnicodeDecodeCharsetAndSize extends HPyContextFunctionWithObject {

        @Specialization
        static Object doGeneric(Object[] arguments, TruffleString charset,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached PCallHPyFunction callHPyFunction,
                        @CachedLibrary(limit = "2") InteropLibrary interopLib,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached CastToJavaLongExactNode castToJavaLongNode,
                        @Cached GetByteArrayNode getByteArrayNode,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode) throws ArityException {
            checkArity(arguments, 3);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object charPtr = arguments[1];
            long size = castToJavaLongNode.execute(arguments[2]);
            if (!interopLib.hasArrayElements(charPtr)) {
                charPtr = callHPyFunction.call(context, GraalHPyNativeSymbol.GRAAL_HPY_FROM_I8_ARRAY, charPtr, size);
            }
            byte[] bytes;
            try {
                bytes = getByteArrayNode.execute(charPtr, size);
            } catch (OverflowException | InteropException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
            TruffleString result = fromJavaStringNode.execute(decode(charset, CodingErrorAction.IGNORE, bytes), TS_ENCODING);
            return asHandleNode.execute(result);
        }

        @TruffleBoundary
        static String decode(TruffleString charset, CodingErrorAction errorAction, byte[] bytes) {
            try {
                TruffleString normalizedCharset = CharsetMapping.normalizeUncached(charset);
                return CharsetMapping.getCharsetNormalized(normalizedCharset).newDecoder().onMalformedInput(errorAction).onUnmappableCharacter(errorAction).decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyUnicodeDecodeCharsetAndSizeAndErrors extends HPyContextFunctionWithObject {

        @Specialization
        static Object doGeneric(Object[] arguments, TruffleString charset,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached PCallHPyFunction callHPyFunction,
                        @CachedLibrary(limit = "2") InteropLibrary interopLib,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached CastToJavaLongExactNode castToJavaLongNode,
                        @Cached CastToTruffleStringNode castToJavaStringNode,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                        @Cached GetByteArrayNode getByteArrayNode,
                        @Cached HPyRaiseNode raiseNode,
                        @Cached TruffleString.EqualNode equalNode) throws ArityException {
            checkArity(arguments, 4);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object charPtr = arguments[1];
            long size = castToJavaLongNode.execute(arguments[2]);
            if (!interopLib.hasArrayElements(charPtr)) {
                charPtr = callHPyFunction.call(context, GraalHPyNativeSymbol.GRAAL_HPY_FROM_I8_ARRAY, charPtr, size);
            }
            byte[] bytes;
            try {
                bytes = getByteArrayNode.execute(charPtr, size);
            } catch (OverflowException | InteropException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
            // TODO: TruffleString - when we have ISO-8859-1, we can just force the encoding and
            // short-circuit the error reading etc
            TruffleString errors = castToJavaStringNode.execute(callHPyFunction.call(context, GraalHPyNativeSymbol.POLYGLOT_FROM_STRING, arguments[3], "ascii"));
            CodingErrorAction errorAction = CodecsModuleBuiltins.convertCodingErrorAction(errors, equalNode);
            TruffleString result = fromJavaStringNode.execute(GraalHPyUnicodeDecodeCharsetAndSize.decode(charset, errorAction, bytes), TS_ENCODING);
            if (result == null) {
                // TODO: refactor helper nodes for CodecsModuleBuiltins to use them here
                return raiseNode.raiseWithoutFrame(context, GraalHPyHandle.NULL_HANDLE, PythonBuiltinClassType.UnicodeDecodeError, ErrorMessages.MALFORMED_INPUT);
            } else {
                return asHandleNode.execute(result);
            }
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyUnicodeReadChar extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached CastToJavaLongExactNode castToJavaLongNode,
                        @Cached PyUnicodeReadCharNode unicodeReadChar) throws ArityException {
            checkArity(arguments, 3);
            long index = castToJavaLongNode.execute(arguments[2]);
            return unicodeReadChar.execute(asPythonObjectNode.execute(arguments[1]), index);
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyAsPyObject extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsPythonObjectNode handleAsPythonObjectNode,
                        @Cached PythonToNativeNewRefNode toPyObjectPointerNode) throws ArityException {
            checkArity(arguments, 2);
            Object object = handleAsPythonObjectNode.execute(arguments[1]);
            return toPyObjectPointerNode.execute(object);
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyBytesAsString extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached HPyRaiseNode raiseNode) throws ArityException {
            checkArity(arguments, 2);
            Object object = asPythonObjectNode.execute(arguments[1]);
            if (object instanceof PBytes) {
                return new PySequenceArrayWrapper(object, 1);
            }
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            return raiseNode.raiseIntWithoutFrame(context, -1, TypeError, ErrorMessages.EXPECTED_BYTES_P_FOUND, object);
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyBytesGetSize extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached SequenceNodes.LenNode lenNode,
                        @Cached HPyRaiseNode raiseNode) throws ArityException {
            checkArity(arguments, 2);
            Object object = asPythonObjectNode.execute(arguments[1]);
            if (object instanceof PBytes) {
                return lenNode.execute(inliningTarget, (PSequence) object);
            }
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            return raiseNode.raiseIntWithoutFrame(context, -1, TypeError, ErrorMessages.EXPECTED_BYTES_P_FOUND, object);
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyBytesFromStringAndSize extends HPyContextFunctionWithFlag {

        @Specialization
        static Object doGeneric(Object[] arguments, boolean withSize,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached CastToJavaIntExactNode castToJavaIntNode,
                        @Cached PCallHPyFunction callHelperNode,
                        @Cached GetByteArrayNode getByteArrayNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @CachedLibrary(limit = "2") InteropLibrary interopLib,
                        @Cached HPyRaiseNode raiseNode,
                        @Cached PythonObjectFactory factory) throws ArityException {
            int expectedArity = withSize ? 3 : 2;
            if (arguments.length != expectedArity) {
                throw ArityException.create(expectedArity, expectedArity, arguments.length);
            }
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object charPtr = arguments[1];

            int size;
            try {
                if (withSize) {
                    if (interopLib.isNull(charPtr)) {
                        return raiseNode.raiseWithoutFrame(context, GraalHPyHandle.NULL_HANDLE, ValueError, ErrorMessages.NULL_CHAR_PASSED);
                    }
                    size = castToJavaIntNode.execute(arguments[2]);
                    if (size == 0) {
                        return asHandleNode.execute(factory.createBytes(new byte[size]));
                    }
                    if (size < 0) {
                        return raiseNode.raiseWithoutFrame(context, GraalHPyHandle.NULL_HANDLE, SystemError, ErrorMessages.NEGATIVE_SIZE_PASSED);
                    }
                } else {
                    size = castToJavaIntNode.execute(callHelperNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_STRLEN, charPtr));
                }
            } catch (PException e) {
                return raiseNode.raiseWithoutFrame(context, GraalHPyHandle.NULL_HANDLE, OverflowError, ErrorMessages.BYTE_STR_IS_TOO_LARGE);
            }

            if (!interopLib.hasArrayElements(charPtr)) {
                charPtr = callHelperNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_FROM_I8_ARRAY, charPtr, (long) size);
            }

            try {
                return asHandleNode.execute(factory.createBytes(getByteArrayNode.execute(charPtr, size)));
            } catch (InteropException e) {
                return raiseNode.raiseWithoutFrame(context, GraalHPyHandle.NULL_HANDLE, TypeError, ErrorMessages.M, e);
            } catch (OverflowException e) {
                return raiseNode.raiseWithoutFrame(context, GraalHPyHandle.NULL_HANDLE, OverflowError, ErrorMessages.BYTE_STR_IS_TOO_LARGE);
            }
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyIsTrue extends GraalHPyContextFunction {

        @Specialization
        static int doGeneric(Object[] arguments,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached PyObjectIsTrueNode isTrueNode) throws ArityException {
            checkArity(arguments, 2);
            Object object = asPythonObjectNode.execute(arguments[1]);
            return PInt.intValue(isTrueNode.execute(null, object));
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyGetAttr extends HPyContextFunctionWithMode {

        @Specialization
        static Object doGeneric(Object[] arguments, FunctionMode mode,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode receiverAsPythonObjectNode,
                        @Cached HPyAsPythonObjectNode keyAsPythonObjectNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached PyObjectGetAttr getAttributeNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, 3);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object receiver = receiverAsPythonObjectNode.execute(arguments[1]);
            Object key;
            switch (mode) {
                case OBJECT:
                    key = keyAsPythonObjectNode.execute(arguments[2]);
                    break;
                case CHAR_PTR:
                    key = fromCharPointerNode.execute(arguments[2]);
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
            try {
                return asHandleNode.execute(getAttributeNode.execute(receiver, key));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(context, e);
                return GraalHPyHandle.NULL_HANDLE;
            }
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyMaybeGetAttrS extends GraalHPyContextFunction {
        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsPythonObjectNode receiverAsPythonObjectNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached PyObjectLookupAttr lookupAttr) throws ArityException {
            checkArity(arguments, 3);
            Object receiver = receiverAsPythonObjectNode.execute(arguments[1]);
            Object key = fromCharPointerNode.execute(arguments[2]);
            return asHandleNode.execute(lookupAttr.execute(null, receiver, key));
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyTypeFromSpec extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached PCallHPyFunction callHelperFunctionNode,
                        @Cached HPyCreateTypeFromSpecNode createTypeFromSpecNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, 3);

            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object typeSpec = callHelperFunctionNode.call(context, GRAAL_HPY_FROM_HPY_TYPE_SPEC, arguments[1]);
            Object typeSpecParamArray = callHelperFunctionNode.call(context, GRAAL_HPY_FROM_HPY_TYPE_SPEC_PARAM_ARRAY, arguments[2]);

            try {
                Object newType = createTypeFromSpecNode.execute(context, typeSpec, typeSpecParamArray);
                assert PGuards.isClass(newType, IsTypeNode.getUncached()) : "Object created from type spec is not a type";
                return asHandleNode.execute(newType);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(context, e);
                return GraalHPyHandle.NULL_HANDLE;
            }
        }

    }

    @GenerateUncached
    public abstract static class GraalHPyHasAttr extends HPyContextFunctionWithMode {

        @Specialization
        static int doGeneric(Object[] arguments, FunctionMode mode,
                        @Cached HPyAsPythonObjectNode receiverAsPythonObjectNode,
                        @Cached HPyAsPythonObjectNode keyAsPythonObjectNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached PyObjectGetAttr getAttributeNode) throws ArityException {
            checkArity(arguments, 3);
            Object receiver = receiverAsPythonObjectNode.execute(arguments[1]);
            Object key;
            switch (mode) {
                case OBJECT:
                    key = keyAsPythonObjectNode.execute(arguments[2]);
                    break;
                case CHAR_PTR:
                    key = fromCharPointerNode.execute(arguments[2]);
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
            try {
                Object attr = getAttributeNode.execute(receiver, key);
                return attr != PNone.NO_VALUE ? 1 : 0;
            } catch (PException e) {
                return 0;
            }
        }
    }

    @GenerateUncached
    public abstract static class GraalHPySetAttr extends HPyContextFunctionWithMode {

        @Specialization
        static int doGeneric(Object[] arguments, FunctionMode mode,
                        @Bind("$node") Node inliningTarget,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode receiverAsPythonObjectNode,
                        @Cached HPyAsPythonObjectNode keyAsPythonObjectNode,
                        @Cached HPyAsPythonObjectNode valueAsPythonObjectNode,
                        @Cached IsBuiltinObjectProfile isStringProfile,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached LookupInheritedAttributeNode.Dynamic lookupSetAttrNode,
                        @Cached CallTernaryMethodNode callSetAttrNode,
                        @Cached ConditionProfile profile,
                        @Cached HPyRaiseNode raiseNativeNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, 4);

            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object receiver = receiverAsPythonObjectNode.execute(arguments[1]);
            Object key;
            switch (mode) {
                case OBJECT:
                    key = keyAsPythonObjectNode.execute(arguments[2]);
                    if (!isStringProfile.profileObject(inliningTarget, key, PythonBuiltinClassType.PString)) {
                        return raiseNativeNode.raiseIntWithoutFrame(context, -1, TypeError, ErrorMessages.ATTR_NAME_MUST_BE_STRING, key);
                    }
                    break;
                case CHAR_PTR:
                    key = fromCharPointerNode.execute(arguments[2]);
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
            Object value = valueAsPythonObjectNode.execute(arguments[3]);

            Object attrGetattribute = lookupSetAttrNode.execute(receiver, SpecialMethodNames.T___SETATTR__);
            if (profile.profile(attrGetattribute != PNone.NO_VALUE)) {
                try {
                    callSetAttrNode.execute(null, attrGetattribute, receiver, key, value);
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(context, e);
                    return -1;
                }
            } else {
                return raiseNativeNode.raiseIntWithoutFrame(context, -1, TypeError, ErrorMessages.P_OBJ_HAS_NO_ATTRS, receiver);
            }
            return 0;
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyGetItem extends HPyContextFunctionWithMode {

        @Specialization
        static Object doGeneric(Object[] arguments, FunctionMode mode,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode receiverAsPythonObjectNode,
                        @Cached HPyAsPythonObjectNode keyAsPythonObjectNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached PyObjectGetItem getItemNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, 3);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object receiver = receiverAsPythonObjectNode.execute(arguments[1]);
            Object key;
            switch (mode) {
                case OBJECT:
                    key = keyAsPythonObjectNode.execute(arguments[2]);
                    break;
                case CHAR_PTR:
                    key = fromCharPointerNode.execute(arguments[2]);
                    break;
                case INT32:
                    key = arguments[2];
                    assert key instanceof Number;
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
            try {
                return asHandleNode.execute(getItemNode.execute(null, receiver, key));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(context, e);
                return GraalHPyHandle.NULL_HANDLE;
            }
        }
    }

    @GenerateUncached
    public abstract static class GraalHPySetItem extends HPyContextFunctionWithMode {

        @Specialization
        static Object doGeneric(Object[] arguments, FunctionMode mode,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode receiverAsPythonObjectNode,
                        @Cached HPyAsPythonObjectNode keyAsPythonObjectNode,
                        @Cached HPyAsPythonObjectNode valueAsPythonObjectNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached PyObjectSetItem setItemNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, 4);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object receiver = receiverAsPythonObjectNode.execute(arguments[1]);
            Object key;
            switch (mode) {
                case OBJECT:
                    key = keyAsPythonObjectNode.execute(arguments[2]);
                    break;
                case CHAR_PTR:
                    key = fromCharPointerNode.execute(arguments[2]);
                    break;
                case INT32:
                    key = arguments[2];
                    assert key instanceof Number;
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
            Object value = valueAsPythonObjectNode.execute(arguments[3]);
            try {
                setItemNode.execute(null, receiver, key, value);
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(context, e);
            }
            return -1;
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyFromPyObject extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached NativeToPythonNode toJavaNode,
                        @Cached HPyAsHandleNode asHandleNode) throws ArityException {
            checkArity(arguments, 2);
            // IMPORTANT: this is not stealing the reference. The CPython implementation
            // actually increases the reference count by 1.
            Object resolvedPyObject = toJavaNode.execute(arguments[1]);
            return asHandleNode.execute(resolvedPyObject);
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyNew extends GraalHPyContextFunction {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(GraalHPyNew.class);

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached IsTypeNode isTypeNode,
                        @Cached HPyRaiseNode raiseNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PCallHPyFunction callMallocNode,
                        @Cached PCallHPyFunction callWriteDataNode,
                        @Cached HPyAsHandleNode asHandleNode) throws ArityException {
            checkArity(arguments, 3);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object type = asPythonObjectNode.execute(arguments[1]);
            Object dataOutVar = arguments[2];

            // check if argument is actually a type
            if (!isTypeNode.execute(type)) {
                return raiseNode.raiseWithoutFrame(context, GraalHPyHandle.NULL_HANDLE, TypeError, ErrorMessages.HPY_NEW_ARG_1_MUST_BE_A_TYPE);
            }

            // create the managed Python object
            PythonObject pythonObject = null;

            if (type instanceof PythonClass clazz) {
                // allocate native space
                long basicSize = clazz.basicSize;
                if (basicSize != -1) {
                    Object dataPtr = callMallocNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_CALLOC, basicSize, 1L);
                    pythonObject = factory.createPythonHPyObject(type, dataPtr);
                    Object destroyFunc = clazz.hpyDestroyFunc;
                    context.createHandleReference(pythonObject, dataPtr, destroyFunc != PNone.NO_VALUE ? destroyFunc : null);

                    // write data pointer to out var
                    callWriteDataNode.call(context, GRAAL_HPY_WRITE_PTR, dataOutVar, 0L, dataPtr);

                    if (LOGGER.isLoggable(Level.FINEST)) {
                        LOGGER.finest(() -> PythonUtils.formatJString("Allocated HPy object with native space of size %d at %s", basicSize, dataPtr));
                    }
                    // TODO(fa): add memory tracing
                }
            }
            if (pythonObject == null) {
                pythonObject = factory.createPythonObject(type);
            }
            return asHandleNode.execute(pythonObject);
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyCast extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached HPyGetNativeSpacePointerNode getNativeSpacePointerNode) throws ArityException {
            checkArity(arguments, 2);
            Object receiver = asPythonObjectNode.execute(arguments[1]);

            // we can also just return NO_VALUE since that will be interpreter as NULL
            return getNativeSpacePointerNode.execute(receiver);
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyTypeGenericNew extends GraalHPyContextFunction {

        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(GraalHPyTypeGenericNew.class);

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PCallHPyFunction callMallocNode,
                        @Cached HPyAsHandleNode asHandleNode) throws ArityException {
            checkArity(arguments, 5);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object type = asPythonObjectNode.execute(arguments[1]);

            // create the managed Python object
            PythonObject pythonObject = null;

            // allocate native space
            if (type instanceof PythonClass) {
                PythonClass clazz = (PythonClass) type;
                long basicSize = clazz.basicSize;
                if (basicSize != -1) {
                    // we fully control this attribute; if it is there, it's always a long
                    Object dataPtr = callMallocNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_CALLOC, basicSize, 1L);
                    pythonObject = factory.createPythonHPyObject(type, dataPtr);

                    if (LOGGER.isLoggable(Level.FINEST)) {
                        LOGGER.finest(() -> PythonUtils.formatJString("Allocated HPy object with native space of size %d at %s", basicSize, dataPtr));
                    }
                    // TODO(fa): add memory tracing
                }
            }
            if (pythonObject == null) {
                pythonObject = factory.createPythonObject(type);
            }
            return asHandleNode.execute(pythonObject);
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyCallBuiltinFunction extends Node {

        public abstract Object execute(Object[] arguments, TruffleString key, int nPythonArguments, ReturnType returnType) throws ArityException;

        @Specialization
        static Object doGeneric(Object[] arguments, TruffleString key, int nPythonArguments, ReturnType returnType,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached ReadAttributeFromObjectNode readAttributeFromObjectNode,
                        @Cached CallNode callNode,
                        @Cached(value = "createToNativeNode(returnType)", uncached = "getUncachedToNativeNode(returnType)") CExtToNativeNode toNativeNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            if (arguments.length != nPythonArguments + 1) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(nPythonArguments + 1, nPythonArguments + 1, arguments.length);
            }
            GraalHPyContext nativeContext = asContextNode.execute(arguments[0]);
            Object[] pythonArguments = new Object[nPythonArguments];
            for (int i = 0; i < pythonArguments.length; i++) {
                pythonArguments[i] = asPythonObjectNode.execute(arguments[i + 1]);
            }
            try {
                Object builtinFunction = readAttributeFromObjectNode.execute(nativeContext.getContext().getBuiltins(), key);
                return toNativeNode.execute(callNode.execute(builtinFunction, pythonArguments, PKeyword.EMPTY_KEYWORDS));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(nativeContext, e);
                return switch (returnType) {
                    case OBJECT -> GraalHPyHandle.NULL_HANDLE;
                    case INT -> -1;
                };
            }
        }

        static CExtToNativeNode createToNativeNode(ReturnType type) {
            return type.createToNativeNode();
        }

        static CExtToNativeNode getUncachedToNativeNode(ReturnType type) {
            return type.getUncachedToNativeNode();
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyRichcompare extends HPyContextFunctionWithFlag {

        @Specialization
        static Object doGeneric(Object[] arguments, boolean returnPrimitive,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached CastToJavaIntExactNode castToJavaIntExactNode,
                        @Cached InlinedGetClassNode getClassNode,
                        @Cached LookupSpecialMethodNode.Dynamic lookupRichcmp,
                        @Cached CallTernaryMethodNode callRichcmp,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException, UnsupportedTypeException {
            checkArity(arguments, 4);
            Object receiver = asPythonObjectNode.execute(arguments[1]);
            Object arg1 = asPythonObjectNode.execute(arguments[2]);
            int arg2;
            try {
                arg2 = castToJavaIntExactNode.execute(arguments[3]);
            } catch (CannotCastException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw UnsupportedTypeException.create(arguments, "4th argument must fit into Java int");
            }
            try {
                Object richcmp = lookupRichcmp.execute(null, getClassNode.execute(inliningTarget, receiver), SpecialMethodNames.T_RICHCMP, receiver);
                Object result = callRichcmp.execute(null, richcmp, receiver, arg1, arg2);
                return returnPrimitive ? PInt.intValue(isTrueNode.execute(null, result)) : asHandleNode.execute(result);
            } catch (PException e) {
                GraalHPyContext nativeContext = asContextNode.execute(arguments[0]);
                transformExceptionToNativeNode.execute(nativeContext, e);
                return returnPrimitive ? 0 : GraalHPyHandle.NULL_HANDLE;
            }
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyAsIndex extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, 2);
            Object receiver = asPythonObjectNode.execute(arguments[1]);
            try {
                return asHandleNode.execute(indexNode.execute(null, receiver));
            } catch (PException e) {
                GraalHPyContext nativeContext = asContextNode.execute(arguments[0]);
                transformExceptionToNativeNode.execute(nativeContext, e);
                return GraalHPyHandle.NULL_HANDLE;
            }
        }
    }

    @GenerateUncached
    @ImportStatic(SpecialMethodSlot.class)
    public abstract static class GraalHPyIsNumber extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached CanBeDoubleNode canBeDoubleNode,
                        @Cached InlinedGetClassNode getClassNode,
                        @Cached(parameters = "Int") LookupCallableSlotInMRONode lookup,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, 2);
            Object receiver = asPythonObjectNode.execute(arguments[1]);
            try {
                if (indexCheckNode.execute(receiver) || canBeDoubleNode.execute(receiver)) {
                    return 1;
                }
                Object receiverType = getClassNode.execute(inliningTarget, receiver);
                return PInt.intValue(lookup.execute(receiverType) != PNone.NO_VALUE);
            } catch (PException e) {
                GraalHPyContext nativeContext = asContextNode.execute(arguments[0]);
                transformExceptionToNativeNode.execute(nativeContext, e);
                return GraalHPyHandle.NULL_HANDLE;
            }
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyTupleFromArray extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached CastToJavaIntExactNode castToJavaIntExactNode,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached PCallHPyFunction callHelperNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached PythonObjectFactory factory,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException, UnsupportedTypeException {
            checkArity(arguments, 3);
            GraalHPyContext nativeContext = asContextNode.execute(arguments[0]);
            Object arrayPtr = arguments[1];
            int n;
            try {
                n = castToJavaIntExactNode.execute(arguments[2]);
            } catch (CannotCastException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw UnsupportedTypeException.create(arguments, "third argument must fit into int");
            }

            Object typedArrayPtr = callHelperNode.call(nativeContext, GraalHPyNativeSymbol.GRAAL_HPY_FROM_HPY_ARRAY, arrayPtr, n);
            if (!lib.hasArrayElements(typedArrayPtr)) {
                throw CompilerDirectives.shouldNotReachHere("returned pointer object must have array type");
            }

            try {
                Object[] elements = new Object[n];
                try {
                    for (int i = 0; i < elements.length; i++) {
                        // This will read an element of a 'HPy arr[]' and the returned value
                        // will be
                        // an HPy "structure". So, we also need to read element "_i" to get
                        // the
                        // internal handle value.
                        Object hpyStructPtr = lib.readArrayElement(typedArrayPtr, i);
                        elements[i] = asPythonObjectNode.execute(lib.readMember(hpyStructPtr, GraalHPyHandle.J_I));
                    }
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                } catch (InvalidArrayIndexException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw raiseNode.raise(SystemError, ErrorMessages.CANNOT_ACCESS_IDX, e.getInvalidIndex(), n);
                } catch (UnknownIdentifierException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw raiseNode.raise(SystemError, ErrorMessages.CANNOT_READ_HANDLE_VAL);
                }

                return asHandleNode.execute(factory.createTuple(elements));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(nativeContext, e);
                return GraalHPyHandle.NULL_HANDLE;
            }
        }
    }

    abstract static class GraalHPyBuilderNewBase extends GraalHPyContextFunction {

        protected abstract Object createObject(int capacity);

        protected final Object doExecute(Object[] arguments,
                        CastToJavaIntExactNode castToJavaIntExactNode,
                        HPyAsHandleNode asHandleNode) throws ArityException {
            checkArity(arguments, 2);
            try {
                int capacity = castToJavaIntExactNode.execute(arguments[1]);
                if (capacity >= 0) {
                    return asHandleNode.execute(createObject(capacity));
                }
            } catch (CannotCastException e) {
                // fall through
            }
            return GraalHPyHandle.NULL_HANDLE;
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyBuilderNew extends GraalHPyBuilderNewBase {

        @Specialization
        Object doGeneric(Object[] arguments,
                        @Cached CastToJavaIntExactNode castToJavaIntExactNode,
                        @Cached HPyAsHandleNode asHandleNode) throws ArityException {
            return doExecute(arguments, castToJavaIntExactNode, asHandleNode);
        }

        @Override
        protected Object createObject(int capacity) {
            Object[] data = new Object[capacity];
            for (int i = 0; i < data.length; i++) {
                data[i] = PNone.NONE;
            }
            return new ObjectSequenceStorage(data);
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyBuilderSet extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached CastToJavaIntExactNode castToJavaIntExactNode,
                        @Cached SequenceStorageNodes.SetItemDynamicNode setItemNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException, UnsupportedTypeException {
            checkArity(arguments, 4);
            Object builder = asPythonObjectNode.execute(arguments[1]);
            if (builder instanceof ObjectSequenceStorage storage) {
                try {
                    int idx = castToJavaIntExactNode.execute(arguments[2]);
                    Object value = asPythonObjectNode.execute(arguments[3]);
                    setItemNode.execute(null, NoGeneralizationNode.DEFAULT, storage, idx, value);
                } catch (CannotCastException e) {
                    // fall through
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(e);
                }
                return 0;
            }
                /*
                 * that's really unexpected since the C signature should enforce a valid builder but
                 * someone could have messed it up
                 */
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw UnsupportedTypeException.create(arguments, "invalid builder object");
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyBuilderBuild extends HPyContextFunctionWithBuiltinType {

        @Specialization
        static Object doGeneric(Object[] arguments, PythonBuiltinClassType type,
                        @Cached HPyCloseAndGetHandleNode closeAndGetHandleNode,
                        @Cached PythonObjectFactory factory,
                        @Cached HPyAsHandleNode asHandleNode) throws ArityException, UnsupportedTypeException {
            checkArity(arguments, 2);
            ObjectSequenceStorage builder = cast(closeAndGetHandleNode.execute(arguments[1]));
            if (builder == null) {
                /*
                 * that's really unexpected since the C signature should enforce a valid builder but
                 * someone could have messed it up
                 */
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw UnsupportedTypeException.create(arguments, "invalid builder object");
            }

            Object sequence;
            switch (type) {
                case PTuple:
                    sequence = factory.createTuple(builder);
                    break;
                case PList:
                    sequence = factory.createList(builder);
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
            return asHandleNode.execute(sequence);
        }

        private static ObjectSequenceStorage cast(Object object) {
            if (object instanceof ObjectSequenceStorage) {
                return (ObjectSequenceStorage) object;
            }
            return null;
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyBuilderCancel extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyCloseAndGetHandleNode closeAndGetHandleNode) throws ArityException, UnsupportedTypeException {
            checkArity(arguments, 2);

            // be pedantic and also check what we are cancelling
            ObjectSequenceStorage builder = cast(closeAndGetHandleNode.execute(arguments[1]));
            if (builder == null) {
                /*
                 * that's really unexpected since the C signature should enforce a valid builder but
                 * someone could have messed it up
                 */
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw UnsupportedTypeException.create(arguments, "invalid builder object");
            }

            return 0;
        }

        private static ObjectSequenceStorage cast(Object object) {
            if (object instanceof ObjectSequenceStorage) {
                return (ObjectSequenceStorage) object;
            }
            return null;
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyTrackerNew extends GraalHPyBuilderNewBase {
        @Specialization
        Object doGeneric(Object[] arguments,
                        @Cached CastToJavaIntExactNode castToJavaIntExactNode,
                        @Cached HPyAsHandleNode asHandleNode) throws ArityException {
            return doExecute(arguments, castToJavaIntExactNode, asHandleNode);
        }

        @Override
        protected Object createObject(int capacity) {
            return new GraalHPyTracker(capacity);
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyTrackerAdd extends GraalHPyContextFunction {
        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached HPyEnsureHandleNode ensureHandleNode) throws ArityException, UnsupportedTypeException {
            checkArity(arguments, 3);
            GraalHPyTracker builder = cast(asPythonObjectNode.execute(arguments[1]));
            if (builder == null) {
                // that's really unexpected since the C signature should enforce a valid builder
                // but someone could have messed it up
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw UnsupportedTypeException.create(arguments, "invalid builder object");
            }
            try {
                GraalHPyHandle handle = ensureHandleNode.execute(arguments[2]);
                if (handle != null) {
                    builder.add(handle);
                }
            } catch (OverflowException | OutOfMemoryError e) {
                return -1;
            }
            return 0;
        }

        private static GraalHPyTracker cast(Object object) {
            if (object instanceof GraalHPyTracker) {
                return (GraalHPyTracker) object;
            }
            return null;
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyTrackerCleanup extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyCloseAndGetHandleNode closeAndGetHandleNode,
                        @Cached HPyCloseHandleNode closeHandleNode) throws ArityException, UnsupportedTypeException {
            checkArity(arguments, 2);
            GraalHPyTracker builder = cast(closeAndGetHandleNode.execute(arguments[1]));
            if (builder == null) {
                // that's really unexpected since the C signature should enforce a valid builder
                // but someone could have messed it up
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw UnsupportedTypeException.create(arguments, "invalid builder object");
            }
            builder.free(closeHandleNode);
            return 0;
        }

        static GraalHPyTracker cast(Object object) {
            if (object instanceof GraalHPyTracker) {
                return (GraalHPyTracker) object;
            }
            return null;
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyTrackerForgetAll extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode) throws ArityException, UnsupportedTypeException {
            checkArity(arguments, 2);
            GraalHPyTracker builder = GraalHPyTrackerCleanup.cast(asPythonObjectNode.execute(arguments[1]));
            if (builder == null) {
                // that's really unexpected since the C signature should enforce a valid builder
                // but someone could have messed it up
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw UnsupportedTypeException.create(arguments, "invalid builder object");
            }
            builder.removeAll();
            return 0;
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyIsCallable extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached PyCallableCheckNode callableCheck) throws ArityException {
            checkArity(arguments, 2);
            Object object = asPythonObjectNode.execute(arguments[1]);
            return PInt.intValue(callableCheck.execute(object));
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyIsSequence extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached PySequenceCheckNode sequenceCheck) throws ArityException {
            checkArity(arguments, 2);
            Object object = asPythonObjectNode.execute(arguments[1]);
            return PInt.intValue(sequenceCheck.execute(object));
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyCallTupleDict extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached ExecutePositionalStarargsNode expandArgsNode,
                        @Cached HashingStorageLen lenNode,
                        @Cached ExpandKeywordStarargsNode expandKwargsNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached CallNode callNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached PRaiseNode raiseNode) throws ArityException {
            checkArity(arguments, 4);
            try {
                // check and expand args
                Object argsObject = asPythonObjectNode.execute(arguments[2]);
                Object[] args = castArgs(argsObject, expandArgsNode, raiseNode);

                // check and expand kwargs
                Object kwargsObject = asPythonObjectNode.execute(arguments[3]);
                PKeyword[] keywords = castKwargs(kwargsObject, lenNode, expandKwargsNode, raiseNode);

                Object callable = asPythonObjectNode.execute(arguments[1]);
                return asHandleNode.execute(callNode.execute(callable, args, keywords));
            } catch (PException e) {
                GraalHPyContext nativeContext = asContextNode.execute(arguments[0]);
                // transformExceptionToNativeNode acts as a branch profile
                transformExceptionToNativeNode.execute(nativeContext, e);
                return GraalHPyHandle.NULL_HANDLE;
            }
        }

        private static Object[] castArgs(Object args,
                        ExecutePositionalStarargsNode expandArgsNode,
                        PRaiseNode raiseNode) {
            // this indicates that a NULL handle was passed (which is valid)
            if (args == PNone.NO_VALUE) {
                return PythonUtils.EMPTY_OBJECT_ARRAY;
            }
            if (PGuards.isPTuple(args)) {
                return expandArgsNode.executeWith(null, args);
            }
            throw raiseNode.raise(TypeError, ErrorMessages.HPY_CALLTUPLEDICT_REQUIRES_ARGS_TUPLE_OR_NULL);
        }

        private static PKeyword[] castKwargs(Object kwargs,
                        HashingStorageLen lenNode,
                        ExpandKeywordStarargsNode expandKwargsNode,
                        PRaiseNode raiseNode) {
            // this indicates that a NULL handle was passed (which is valid)
            if (kwargs == PNone.NO_VALUE || isEmptyDict(kwargs, lenNode)) {
                return PKeyword.EMPTY_KEYWORDS;
            }
            if (PGuards.isDict(kwargs)) {
                return expandKwargsNode.execute(kwargs);
            }
            throw raiseNode.raise(TypeError, ErrorMessages.HPY_CALLTUPLEDICT_REQUIRES_KW_DICT_OR_NULL);
        }

        private static boolean isEmptyDict(Object delegate, HashingStorageLen lenNode) {
            return delegate instanceof PDict && lenNode.execute(((PDict) delegate).getDictStorage()) == 0;
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyDump extends GraalHPyContextFunction {

        @Specialization
        @TruffleBoundary
        Object doGeneric(Object[] arguments) throws ArityException {
            checkArity(arguments, 2);
            GraalHPyContext nativeContext = HPyAsContextNodeGen.getUncached().execute(arguments[0]);
            PythonContext context = nativeContext.getContext();
            Object pythonObject = HPyAsPythonObjectNodeGen.getUncached().execute(arguments[1]);
            Object type = InlinedGetClassNode.executeUncached(pythonObject);
            PrintWriter stderr = new PrintWriter(context.getStandardErr());
            stderr.println("object type     : " + type);
            stderr.println("object type name: " + GetNameNode.getUncached().execute(type));

            // the most dangerous part
            stderr.println("object repr     : ");
            stderr.flush();
            try {
                stderr.println(PyObjectReprAsTruffleStringNode.getUncached().execute(null, pythonObject).toJavaStringUncached());
                stderr.flush();
            } catch (PException | CannotCastException e) {
                // errors are ignored at this point
            }
            return 0;
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyType extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached InlinedGetClassNode getClassNode) throws ArityException {
            checkArity(arguments, 2);
            Object object = asPythonObjectNode.execute(arguments[1]);
            return asHandleNode.execute(getClassNode.execute(inliningTarget, object));
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyTypeCheck extends HPyContextFunctionWithFlag {

        @Specialization
        static Object doGeneric(Object[] arguments, boolean withGlobal,
                        @Bind("this") Node inliningTarget,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached InlinedGetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode) throws ArityException {
            checkArity(arguments, 3);
            Object object = asPythonObjectNode.execute(arguments[1]);
            Object expectedType;
            if (withGlobal) {
                throw CompilerDirectives.shouldNotReachHere("not yet implemented");
            } else {
                expectedType = asPythonObjectNode.execute(arguments[2]);
            }
            return PInt.intValue(isSubtypeNode.execute(getClassNode.execute(inliningTarget, object), expectedType));
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyNewException extends HPyContextFunctionWithFlag {

        @Specialization
        static Object doGeneric(Object[] arguments, boolean withDoc,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached PCallHPyFunction callFromStringNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached TruffleString.IndexOfCodePointNode indexOfCodepointNode,
                        @Cached TruffleString.CodePointLengthNode codepointLengthNode,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Cached HashingStorageGetItem getHashingStorageItem,
                        @Cached HashingStorageSetItem setHashingStorageItem,
                        @Cached CallNode callTypeConstructorNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached PythonObjectFactory factory,
                        @Cached HPyAsHandleNode asHandleNode) throws ArityException {
            int docExtra = withDoc ? 1 : 0;
            checkArity(arguments, 4 + docExtra);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object nameObj = callFromStringNode.call(context, GraalHPyNativeSymbol.POLYGLOT_FROM_STRING, arguments[1], "utf-8");
            TruffleString doc = withDoc ? fromCharPointerNode.execute(arguments[2]) : null;
            Object base = asPythonObjectNode.execute(arguments[2 + docExtra]);
            Object dictObj = asPythonObjectNode.execute(arguments[3 + docExtra]);

            TruffleString name;
            try {
                name = castToTruffleStringNode.execute(nameObj);
            } catch (CannotCastException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }

            try {
                int len = codepointLengthNode.execute(name, TS_ENCODING);
                int dotIdx = indexOfCodepointNode.execute(name, '.', 0, len, TS_ENCODING);
                if (dotIdx < 0) {
                    throw raiseNode.raise(SystemError, ErrorMessages.NAME_MUST_BE_MOD_CLS);
                }

                if (base == PNone.NO_VALUE) {
                    base = PythonBuiltinClassType.Exception;
                }
                PDict dict;
                HashingStorage dictStorage;
                if (dictObj == PNone.NO_VALUE) {
                    dictStorage = new DynamicObjectStorage(PythonLanguage.get(asContextNode));
                    dict = factory.createDict(dictStorage);
                } else {
                    if (!(dictObj instanceof PDict)) {
                        /*
                         * CPython expects a PyDictObject and if not, it raises a
                         * ErrorMessages.BAD_INTERNAL_CALL.
                         */
                        throw raiseNode.raise(SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC);
                    }
                    dict = (PDict) dictObj;
                    dictStorage = dict.getDictStorage();
                }

                if (!getHashingStorageItem.hasKey(dictStorage, SpecialAttributeNames.T___MODULE__)) {
                    dictStorage = setHashingStorageItem.execute(dictStorage, SpecialAttributeNames.T___MODULE__, substringNode.execute(name, 0, dotIdx, TS_ENCODING, false));
                }

                if (withDoc) {
                    assert doc != null;
                    dictStorage = setHashingStorageItem.execute(dictStorage, SpecialAttributeNames.T___DOC__, doc);
                }

                dict.setDictStorage(dictStorage);

                PTuple bases;
                if (base instanceof PTuple) {
                    bases = (PTuple) base;
                } else {
                    bases = factory.createTuple(new Object[]{base});
                }

                Object newExceptionType = callTypeConstructorNode.execute(PythonBuiltinClassType.PythonClass, substringNode.execute(name, dotIdx + 1, len - dotIdx - 1, TS_ENCODING, false), bases,
                                dict);
                return asHandleNode.execute(newExceptionType);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(context, e);
                return GraalHPyHandle.NULL_HANDLE;
            }
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyIs extends HPyContextFunctionWithFlag {

        @Specialization
        static Object doGeneric(Object[] arguments, boolean withGlobal,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached IsNode isNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, 3);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object left = asPythonObjectNode.execute(arguments[1]);
            Object right;
            if (withGlobal) {
                throw CompilerDirectives.shouldNotReachHere("not yet implemented");
            } else {
                right = asPythonObjectNode.execute(arguments[2]);
            }
            try {
                return PInt.intValue(isNode.execute(left, right));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(context, e);
                return -1;
            }
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyImportModule extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, 2);
            try {
                TruffleString name = fromCharPointerNode.execute(arguments[1]);
                return asHandleNode.execute(AbstractImportNode.importModule(name));
            } catch (CannotCastException e) {
                throw CompilerDirectives.shouldNotReachHere();
            } catch (PException e) {
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                transformExceptionToNativeNode.execute(context, e);
                return GraalHPyHandle.NULL_HANDLE;
            }
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyFieldStore extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached PCallHPyFunction callHelperFunctionNode,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached ConditionProfile nullHandleProfile) throws ArityException {
            checkArity(arguments, 4);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object ownerObject = asPythonObjectNode.execute(arguments[1]);
            Object hpyFieldPtr = arguments[2];
            Object referent = asPythonObjectNode.execute(arguments[3]);
            Object hpyFieldObject = callHelperFunctionNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_GET_FIELD_I, hpyFieldPtr);

            if (!(ownerObject instanceof PythonObject)) {
                throw CompilerDirectives.shouldNotReachHere("HPyField owner is not a PythonObject!");
            }
            PythonObject owner = (PythonObject) ownerObject;

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
            if (nullHandleProfile.profile(referent == GraalHPyHandle.NULL_HANDLE_DELEGATE && idx == 0)) {
                // assigning HPy_NULL to a field that already holds HPy_NULL, nothing to do
            } else {
                idx = assign(owner, referent, idx);
            }
            GraalHPyHandle newHandle = asHandleNode.executeField(referent, idx);
            callHelperFunctionNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_SET_FIELD_I, hpyFieldPtr, newHandle);
            return 0;
        }

        public static int assign(PythonObject owner, Object referent, int location) {
            Object[] hpyFields = owner.getHPyData();
            if (location != 0) {
                assert hpyFields != null;
                hpyFields[location] = referent;
                return location;
            } else {
                int newLocation;
                if (hpyFields == null) {
                    newLocation = 1;
                    hpyFields = new Object[]{0, referent};
                } else {
                    newLocation = hpyFields.length;
                    hpyFields = PythonUtils.arrayCopyOf(hpyFields, newLocation + 1);
                    hpyFields[newLocation] = referent;
                }
                owner.setHPyData(hpyFields);
                return newLocation;
            }
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyFieldLoad extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached("createClassProfile()") ValueProfile fieldTypeProfile) throws ArityException {
            checkArity(arguments, 3);
            Object hpyFieldObject = fieldTypeProfile.profile(arguments[2]);
            if (lib.isNull(hpyFieldObject)) { // fast track in case field is not
                                              // initialized.
                return GraalHPyHandle.NULL_HANDLE;
            }
            Object referent;
            if (hpyFieldObject instanceof GraalHPyHandle) { // avoid `asPointer` message
                                                            // dispatch
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
                    return GraalHPyHandle.NULL_HANDLE;
                }
                Object owner = asPythonObjectNode.execute(arguments[1]);
                if (owner instanceof PythonObject) {
                    referent = ((PythonObject) owner).getHPyData()[idx];
                } else {
                    throw CompilerDirectives.shouldNotReachHere("HPyField owner is not a PythonObject!");
                }
            }
            return asHandleNode.execute(referent);
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyGlobalStore extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached PCallHPyFunction callHelperFunctionNode,
                        @Cached("createClassProfile()") ValueProfile typeProfile,
                        @CachedLibrary(limit = "3") InteropLibrary lib) throws ArityException {
            checkArity(arguments, 3);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object hpyGlobalPtr = arguments[1];
            Object value = asPythonObjectNode.execute(arguments[2]);
            Object hpyGlobal = typeProfile.profile(callHelperFunctionNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_GET_GLOBAL_I, hpyGlobalPtr));

            int idx = -1;
            if (hpyGlobal instanceof GraalHPyHandle) {
                // branch profiling with typeProfile
                idx = ((GraalHPyHandle) hpyGlobal).getGlobalId();
            } else if (!(hpyGlobal instanceof Long) && lib.isNull(hpyGlobal)) {
                // nothing to do
            } else {
                long bits;
                if (hpyGlobal instanceof Long) {
                    // branch profile due to lib.asPointer usage in else branch
                    // and typeProfile
                    bits = (Long) hpyGlobal;
                } else {
                    try {
                        bits = lib.asPointer(hpyGlobal);
                    } catch (UnsupportedMessageException e) {
                        throw CompilerDirectives.shouldNotReachHere();
                    }
                }
                if (GraalHPyBoxing.isBoxedHandle(bits)) {
                    idx = GraalHPyBoxing.unboxHandle(bits);
                    // idx =
                    // context.getObjectForHPyGlobal(GraalHPyBoxing.unboxHandle(bits)).getGlobalId();
                }
            }

            // TODO: (tfel) do not actually allocate the index / free the existing one when
            // value can be stored as tagged handle
            idx = context.createGlobal(value, idx);
            GraalHPyHandle newHandle = GraalHPyHandle.createGlobal(value, idx);
            callHelperFunctionNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_SET_GLOBAL_I, hpyGlobalPtr, newHandle);
            return 0;
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyGlobalLoad extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached("createClassProfile()") ValueProfile typeProfile) throws ArityException {
            checkArity(arguments, 2);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object hpyGlobal = typeProfile.profile(arguments[1]);
            if (hpyGlobal instanceof GraalHPyHandle) {
                // branch profiling with typeProfile
                GraalHPyHandle h = (GraalHPyHandle) hpyGlobal;
                return asHandleNode.execute(h.getDelegate());
            } else if (!(hpyGlobal instanceof Long) && lib.isNull(hpyGlobal)) {
                // type profile influences first test
                return GraalHPyHandle.NULL_HANDLE;
            } else {
                long bits;
                if (hpyGlobal instanceof Long) {
                    // branch profile due to lib.asPointer usage in else branch
                    // and typeProfile
                    bits = (Long) hpyGlobal;
                } else {
                    try {
                        bits = lib.asPointer(hpyGlobal);
                    } catch (UnsupportedMessageException e) {
                        throw CompilerDirectives.shouldNotReachHere();
                    }
                }
                if (GraalHPyBoxing.isBoxedHandle(bits)) {
                    // if asHandleNode wasn't used above, it acts as a branch profile
                    // here. otherwise we're probably already pulling in a lot of code
                    // and are a bit too polymorphic
                    return asHandleNode.execute(context.getObjectForHPyGlobal(GraalHPyBoxing.unboxHandle(bits)));
                } else {
                    // tagged handles can be returned directly
                    return bits;
                }
            }
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyLeavePythonExecution extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached GilNode gil) throws ArityException {
            checkArity(arguments, 1);
            PythonContext context = asContextNode.execute(arguments[0]).getContext();
            PThreadState threadState = PThreadState.getThreadState(PythonLanguage.get(gil), context);
            gil.release(context, true);
            return threadState;
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyReenterPythonExecution extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached GilNode gil) throws ArityException {
            checkArity(arguments, 2);
            PythonContext context = asContextNode.execute(arguments[0]).getContext();
            // nothing to do with PThreadState in arguments[1]
            gil.acquire(context);
            return 0;
        }
    }

    @ImportStatic(SpecialMethodSlot.class)
    @GenerateUncached
    public abstract static class GraalHPyContains extends GraalHPyContextFunction {

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached PySequenceContainsNode containsNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, 3);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object container = asPythonObjectNode.execute(arguments[1]);
            Object key = asPythonObjectNode.execute(arguments[2]);
            try {
                return containsNode.execute(container, key) ? 1 : 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(context, e);
                return -1;
            }
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyTypeIsSubtype extends GraalHPyContextFunction {
        @Specialization
        static int doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asSubtype,
                        @Cached HPyAsPythonObjectNode asBasetype,
                        @Cached IsSubtypeNode isSubtype,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, 3);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            try {
                return isSubtype.execute(asSubtype.execute(arguments[1]),
                                asBasetype.execute(arguments[2])) ? 1 : 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(context, e);
                return -1;
            }
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyTypeGetName extends GraalHPyContextFunction {
        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsPythonObjectNode asType,
                        @Cached HPyTypeGetNameNode getName) throws ArityException {
            checkArity(arguments, 2);
            Object type = asType.execute(arguments[1]);
            return getName.execute(type);
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyDictKeys extends GraalHPyContextFunction {
        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsPythonObjectNode asDict,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached PyDictKeys keysNode) throws ArityException {
            checkArity(arguments, 2);
            Object dict = asDict.execute(arguments[1]);
            return asHandleNode.execute(keysNode.execute((PDict) dict));
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyUnicodeInternFromString extends GraalHPyContextFunction {
        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached InternStringNode internStringNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, 2);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object string = fromCharPointerNode.execute(arguments[1]);
            PString interned = internStringNode.execute(string);
            if (interned == null) {
                try {
                    throw raiseNode.raise(TypeError, ErrorMessages.CANNOT_INTERN_P, string);
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(context, e);
                    return GraalHPyHandle.NULL_HANDLE;
                }
            }
            return asHandleNode.execute(interned);
        }
    }

    // see _HPyCapsule_key in the HPy API
    public static final class CapsuleKey {
        public static final byte Pointer = 0;
        public static final byte Name = 1;
        public static final byte Context = 2;
        public static final byte Destructor = 3;
    }

    @GenerateUncached
    public abstract static class GraalHPyCapsuleNew extends GraalHPyContextFunction {
        public static final TruffleString NULL_PTR_ERROR = tsLiteral("HPyCapsule_New called with null pointer");

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached PythonObjectFactory factory,
                        @CachedLibrary(limit = "1") InteropLibrary interopLib,
                        @Cached HPyRaiseNode raiseNode) throws ArityException {
            checkArity(arguments, 4);
            GraalHPyContext context = asContextNode.execute(arguments[0]);

            if (interopLib.isNull(arguments[1])) {
                return raiseNode.raiseWithoutFrame(context, GraalHPyHandle.NULL_HANDLE, ValueError, NULL_PTR_ERROR);
            }
            PyCapsule result = factory.createCapsule(arguments[1], arguments[2], arguments[3]);
            return asHandleNode.execute(result);
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyCapsuleGet extends GraalHPyContextFunction {
        public static final TruffleString INCORRECT_NAME = tsLiteral("HPyCapsule_GetPointer called with incorrect name");

        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asCapsule,
                        @CachedLibrary(limit = "1") InteropLibrary interopLib,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached TruffleString.EqualNode equalNode,
                        @Cached CastToJavaIntExactNode castInt,
                        @Cached PRaiseNode raiseNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, 4);
            GraalHPyContext context = null;
            try {
                context = asContextNode.execute(arguments[0]);
                Object capsule = asCapsule.execute(arguments[1]);
                int key = castInt.execute(arguments[2]);
                isLegalCapsule(capsule, key, raiseNode);
                PyCapsule pyCapsule = (PyCapsule) capsule;
                Object result;
                switch (key) {
                    case CapsuleKey.Pointer:
                        if (!nameMatches(pyCapsule, arguments[3], interopLib, fromCharPointerNode, equalNode)) {
                            throw raiseNode.raise(ValueError, INCORRECT_NAME);
                        }
                        result = pyCapsule.getPointer();
                        break;
                    case CapsuleKey.Context:
                        result = pyCapsule.getContext();
                        break;
                    case CapsuleKey.Name:
                        result = pyCapsule.getName();
                        break;
                    case CapsuleKey.Destructor:
                        result = pyCapsule.getDestructor();
                        break;
                    default:
                        throw CompilerDirectives.shouldNotReachHere("invalid key");
                }
                // never allow Java 'null' to be returned
                if (result == null) {
                    return context.getContext().getNativeNull().getPtr();
                }
                return result;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(context, e);
                return GraalHPyHandle.NULL_HANDLE;
            }
        }

        private static boolean nameMatches(PyCapsule capsule, Object namePtr, InteropLibrary interopLib, FromCharPointerNode fromCharPointerNode, TruffleString.EqualNode equalNode) {
            boolean isCapsuleNameNull = capsule.getName() == null;
            boolean isNamePtrNull = interopLib.isNull(namePtr);

            // if one of them is NULL, then both need to be NULL
            if (isCapsuleNameNull || isNamePtrNull) {
                return isCapsuleNameNull && isNamePtrNull;
            }

            TruffleString name = fromCharPointerNode.execute(namePtr);
            TruffleString capsuleName = fromCharPointerNode.execute(capsule.getName());
            return equalNode.execute(capsuleName, name, TS_ENCODING);
        }

        public static void isLegalCapsule(Object object, int key, PRaiseNode raiseNode) {
            if (!(object instanceof PyCapsule) || ((PyCapsule) object).getPointer() == null) {
                throw raiseNode.raise(ValueError, getErrorMessage(key));
            }
        }

        @TruffleBoundary
        public static TruffleString getErrorMessage(int key) {
            switch (key) {
                case CapsuleKey.Pointer:
                    return ErrorMessages.CAPSULE_GETPOINTER_WITH_INVALID_CAPSULE;
                case CapsuleKey.Context:
                    return ErrorMessages.CAPSULE_GETCONTEXT_WITH_INVALID_CAPSULE;
                case CapsuleKey.Name:
                    return ErrorMessages.CAPSULE_GETNAME_WITH_INVALID_CAPSULE;
                case CapsuleKey.Destructor:
                    return ErrorMessages.CAPSULE_GETDESTRUCTOR_WITH_INVALID_CAPSULE;
                default:
                    throw CompilerDirectives.shouldNotReachHere("invalid key");
            }
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyCapsuleSet extends GraalHPyContextFunction {
        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asCapsule,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached CastToTruffleStringNode castStr,
                        @Cached CastToJavaIntExactNode castInt,
                        @Cached PRaiseNode raiseNode,
                        @CachedLibrary(limit = "1") InteropLibrary interopLib,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, 4);
            GraalHPyContext context = null;
            try {
                context = asContextNode.execute(arguments[0]);
                Object capsule = asCapsule.execute(arguments[1]);
                int key = castInt.execute(arguments[2]);
                GraalHPyCapsuleGet.isLegalCapsule(capsule, key, raiseNode);
                PyCapsule pyCapsule = (PyCapsule) capsule;
                switch (key) {
                    case CapsuleKey.Pointer:
                        if (interopLib.isNull(arguments[3])) {
                            throw raiseNode.raise(ValueError, ErrorMessages.CAPSULE_SETPOINTER_CALLED_WITH_NULL_POINTER);
                        }
                        pyCapsule.setPointer(arguments[3]);
                        break;
                    case CapsuleKey.Context:
                        pyCapsule.setContext(arguments[3]);
                        break;
                    case CapsuleKey.Name:
                        // we may assume that the pointer is owned
                        pyCapsule.setName(fromCharPointerNode.execute(arguments[3], false));
                        break;
                    case CapsuleKey.Destructor:
                        pyCapsule.setDestructor(arguments[3]);
                        break;
                    default:
                        throw CompilerDirectives.shouldNotReachHere("invalid key");
                }
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(context, e);
                return -1;
            }
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyCapsuleIsValid extends GraalHPyContextFunction {
        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsPythonObjectNode asCapsule,
                        @CachedLibrary(limit = "1") InteropLibrary interopLib,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached TruffleString.EqualNode equalNode) throws ArityException {
            checkArity(arguments, 3);
            Object capsule = asCapsule.execute(arguments[1]);
            if (!(capsule instanceof PyCapsule)) {
                return 0;
            }
            PyCapsule pyCapsule = (PyCapsule) capsule;
            if (!GraalHPyCapsuleGet.nameMatches(pyCapsule, arguments[2], interopLib, fromCharPointerNode, equalNode)) {
                return 0;
            }
            return 1;
        }
    }

    @GenerateUncached
    public abstract static class GraalHPySetType extends GraalHPyContextFunction {
        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsPythonObjectNode asObject,
                        @Cached HPyAsPythonObjectNode asType,
                        @CachedLibrary(limit = "3") DynamicObjectLibrary dylib) throws ArityException {
            checkArity(arguments, 3);
            Object object = asObject.execute(arguments[1]);
            if (!(object instanceof PythonObject)) {
                return -1;
            }
            Object type = asType.execute(arguments[2]);
            if (!(type instanceof PythonAbstractClass)) {
                return -1;
            }
            ((PythonObject) object).setPythonClass(type, dylib);
            return 0;
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyContextVarNew extends GraalHPyContextFunction {
        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached HPyAsPythonObjectNode asObject,
                        @Cached CallNode callContextvar,
                        @Cached HPyAsHandleNode asHandleNode) throws ArityException {
            checkArity(arguments, 3);
            TruffleString name = fromCharPointerNode.execute(arguments[1]);
            Object def = asObject.execute(arguments[2]);
            return asHandleNode.execute(callContextvar.execute(PythonBuiltinClassType.ContextVar, name, def));
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyContextVarGet extends GraalHPyContextFunction {
        @Specialization
        static int doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asVar,
                        @Cached HPyAsPythonObjectNode asDef,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached PCallHPyFunction callWriteHPyNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, 4);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object var = asVar.execute(arguments[1]);
            Object def = asDef.execute(arguments[2]);
            Object outPtr = arguments[3];
            try {
                if (!(var instanceof PContextVar)) {
                    throw raiseNode.raise(TypeError, ErrorMessages.INSTANCE_OF_CONTEXTVAR_EXPECTED);
                }
                PythonThreadState threadState = context.getContext().getThreadState(PythonLanguage.get(asContextNode));
                Object result = getObject(threadState, (PContextVar) var, def);
                callWriteHPyNode.call(context, GRAAL_HPY_WRITE_HPY, outPtr, 0L, asHandleNode.execute(result));
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(context, e);
                return -1;
            }
        }

        public static Object getObject(PythonThreadState threadState, PContextVar var, Object def) {
            Object result = var.getValue(threadState);
            if (result == null) {
                if (def == NULL_HANDLE_DELEGATE) {
                    def = var.getDefault();
                    if (def == PContextVar.NO_DEFAULT) {
                        def = NULL_HANDLE_DELEGATE;
                    }
                }
                result = def;
            }
            return result;
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyContextVarSet extends GraalHPyContextFunction {
        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asVar,
                        @Cached HPyAsPythonObjectNode asVal,
                        @Cached PRaiseNode raiseNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, 3);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object var = asVar.execute(arguments[1]);
            Object val = asVal.execute(arguments[2]);
            try {
                if (!(var instanceof PContextVar)) {
                    throw raiseNode.raise(TypeError, ErrorMessages.INSTANCE_OF_CONTEXTVAR_EXPECTED);
                }
                PythonThreadState threadState = context.getContext().getThreadState(PythonLanguage.get(asContextNode));
                ((PContextVar) var).setValue(threadState, val);
                return asHandleNode.execute(PNone.NONE);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(context, e);
                return -1;
            }
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyUnicodeFromEncodedObject extends GraalHPyContextFunction {
        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode objNode,
                        @Cached FromCharPointerNode fromNativeCharPointerNode,
                        @Cached PyUnicodeFromEncodedObject libNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, 4);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object obj = objNode.execute(arguments[1]);
            TruffleString encoding = fromNativeCharPointerNode.execute(arguments[2]);
            TruffleString errors = fromNativeCharPointerNode.execute(arguments[3]);
            try {
                Object result = libNode.execute(null, obj, encoding, errors);
                return asHandleNode.execute(result);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(context, e);
                return GraalHPyHandle.NULL_HANDLE;
            }
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyUnicodeSubstring extends GraalHPyContextFunction {
        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode objNode,
                        @Cached CastToTruffleStringNode castStr,
                        @Cached CastToJavaIntExactNode castStart,
                        @Cached CastToJavaIntExactNode castEnd,
                        @Cached StrGetItemNodeWithSlice getSlice,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, 4);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            TruffleString value = castStr.execute(objNode.execute(arguments[1]));
            int start = castStart.execute(arguments[2]);
            int end = castEnd.execute(arguments[3]);
            try {
                Object result = getSlice.execute(value, new SliceInfo(start, end, 1));
                return asHandleNode.execute(result);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(context, e);
                return GraalHPyHandle.NULL_HANDLE;
            }
        }
    }

    @GenerateUncached
    public abstract static class GraalHPySliceUnpack extends GraalHPyContextFunction {
        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode objNode,
                        @Cached PCallHPyFunction callWriteDataNode,
                        @Cached SliceNodes.SliceUnpack sliceUnpack) throws ArityException {
            checkArity(arguments, 5);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object obj = objNode.execute(arguments[1]);
            if (!(obj instanceof PSlice)) {
                return -1;
            }
            PSlice slice = (PSlice) obj;
            SliceInfo info = sliceUnpack.execute(slice);
            callWriteDataNode.call(context, GRAAL_HPY_WRITE_UL, arguments[2], 0L, info.start);
            callWriteDataNode.call(context, GRAAL_HPY_WRITE_UL, arguments[3], 0L, info.stop);
            callWriteDataNode.call(context, GRAAL_HPY_WRITE_UL, arguments[4], 0L, info.step);
            return 0;
        }
    }

    @GenerateUncached
    public abstract static class GraalHPyTypeCheckSlot extends GraalHPyContextFunction {
        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode objNode,
                        @Cached PCallHPyFunction callHelperFunctionNode,
                        @CachedLibrary(limit = "2") InteropLibrary slotLib,
                        @CachedLibrary(limit = "2") InteropLibrary slotDefLib,
                        @Cached ReadAttributeFromObjectNode readFunction) throws ArityException {
            checkArity(arguments, 3);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object type = objNode.execute(arguments[1]);
            Object slotDef = callHelperFunctionNode.call(context, GRAAL_HPY_DEF_GET_SLOT, arguments[2]);
            Object slotObj = callHelperFunctionNode.call(context, GRAAL_HPY_SLOT_GET_SLOT, slotDef);
            HPySlot slot;
            if (slotLib.fitsInInt(slotObj)) {
                try {
                    slot = HPySlot.fromValue(slotLib.asInt(slotObj));
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return 0;
            }
            if (slot == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return 0;
            }
            Object methodName = slot.getAttributeKeys()[0];
            Object methodFunctionPointer;
            try {
                methodFunctionPointer = slotDefLib.readMember(slotDef, "impl");
            } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
            Object slotFunc = readFunction.execute(type, methodName);
            if (slotFunc instanceof PBuiltinFunction) {
                PKeyword[] kwDefaults = ((PBuiltinFunction) slotFunc).getKwDefaults();
                if (kwDefaults.length > 1 && kwDefaults[0].getName() == HPyExternalFunctionNodes.KW_CALLABLE) {
                    if (kwDefaults[1].getValue() == methodFunctionPointer) {
                        return 1;
                    }
                }
            }
            return 0;
        }
    }

    @GenerateUncached
    public abstract static class GraalHPySeqIterNew extends GraalHPyContextFunction {
        @Specialization
        static Object doGeneric(Object[] arguments,
                        @Cached HPyAsPythonObjectNode asSeqNode,
                        @Cached HPyAsHandleNode asHandle,
                        @Cached PythonObjectFactory factory) throws ArityException {
            checkArity(arguments, 2);
            Object seq = asSeqNode.execute(arguments[1]);
            return asHandle.execute(factory.createSequenceIterator(seq));
        }
    }
}
