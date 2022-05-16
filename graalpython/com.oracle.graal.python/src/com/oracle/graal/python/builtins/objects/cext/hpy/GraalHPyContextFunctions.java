/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.FunctionMode.CHAR_PTR;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.FunctionMode.INT32;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.FunctionMode.OBJECT;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyHandle.NULL_HANDLE_DELEGATE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_DEF_GET_KIND;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_DEF_GET_METH;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_FROM_HPY_MODULE_DEF;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_FROM_HPY_TYPE_SPEC;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_FROM_HPY_TYPE_SPEC_PARAM_ARRAY;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_MODULE_GET_DEFINES;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_MODULE_GET_LEGACY_METHODS;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_WRITE_HPY;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol.GRAAL_HPY_WRITE_PTR;

import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins.GetFileSystemEncodingNode;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins.WarnNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject.PInteropGetAttributeNode;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject.PInteropSubscriptAssignNode;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject.PInteropSubscriptNode;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.capsule.PyCapsule;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext.LLVMType;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.CastToJavaDoubleNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.CreateMethodNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.GetLLVMType;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ResolveHandleNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeReferenceCache.ResolveNativeReferenceNode;
import com.oracle.graal.python.builtins.objects.cext.capi.PThreadState;
import com.oracle.graal.python.builtins.objects.cext.capi.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CByteArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.AsNativePrimitiveNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.EncodeNativeStringNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.GetByteArrayNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.SizeofWCharNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.UnicodeFromWcharNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.ConversionNodeSupplier;
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
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.PCallHPyFunction;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.RecursiveExceptionMatches;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsContextNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.NoGeneralizationNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
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
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PySequenceContainsNode;
import com.oracle.graal.python.lib.PyUnicodeFromEncodedObject;
import com.oracle.graal.python.lib.PyUnicodeReadCharNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.WriteUnraisableNode;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNode.ExecutePositionalStarargsInteropNode;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.InplaceArithmetic;
import com.oracle.graal.python.nodes.expression.TernaryArithmetic;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntLossyNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.util.CharsetMapping;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;

@SuppressWarnings("static-method")
public abstract class GraalHPyContextFunctions {

    enum FunctionMode {
        OBJECT,
        CHAR_PTR,
        INT32
    }

    enum ReturnType {
        OBJECT,
        INT,
        FLOAT
    }

    @ExportLibrary(InteropLibrary.class)
    abstract static class GraalHPyContextFunction implements TruffleObject {

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage(name = "execute")
        Object executeShouldNotReach(@SuppressWarnings("unused") Object[] arguments) {
            throw CompilerDirectives.shouldNotReachHere();
        }

        @ExplodeLoop
        static void checkMode(FunctionMode actualMode, FunctionMode... allowedModes) {
            CompilerAsserts.partialEvaluationConstant(actualMode);
            CompilerAsserts.partialEvaluationConstant(allowedModes);
            for (int i = 0; i < allowedModes.length; i++) {
                if (actualMode == allowedModes[i]) {
                    return;
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("invalid function mode used: " + actualMode);
        }

        protected static void checkArity(Object[] arguments, int expectedArity) throws ArityException {
            if (arguments.length != expectedArity) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(expectedArity, expectedArity, arguments.length);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyDup extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached HPyAsHandleNode asHandleNode) throws ArityException {
            checkArity(arguments, 2);
            GraalHPyContext hpyContext = asContextNode.execute(arguments[0]);

            return asHandleNode.execute(hpyContext, asPythonObjectNode.execute(hpyContext, arguments[1]));
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyClose extends GraalHPyContextFunction {
        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyCloseHandleNode closeHandleNode) throws ArityException {
            checkArity(arguments, 2);
            GraalHPyContext hpyContext = asContextNode.execute(arguments[0]);
            closeHandleNode.execute(hpyContext, arguments[1]);
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
    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyModuleCreate extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PCallHPyFunction callFromHPyModuleDefNode,
                        @Cached PCallHPyFunction callGetterNode,
                        @CachedLibrary(limit = "3") InteropLibrary ptrLib,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached CastToJavaIntLossyNode castToJavaIntNode,
                        @Cached WriteAttributeToObjectNode writeAttrNode,
                        @Cached WriteAttributeToDynamicObjectNode writeAttrToMethodNode,
                        @Cached HPyCreateFunctionNode addFunctionNode,
                        @Cached CreateMethodNode addLegacyMethodNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                checkArity(arguments, 2);
                GraalHPyContext context = asContextNode.execute(arguments[0]);

                // call to type the pointer
                Object moduleDef = callFromHPyModuleDefNode.call(context, GRAAL_HPY_FROM_HPY_MODULE_DEF, arguments[1]);

                assert checkLayout(moduleDef);

                try {
                    String mName;
                    Object mDoc;
                    try {
                        mName = castToJavaStringNode.execute(fromCharPointerNode.execute(ptrLib.readMember(moduleDef, "name")));

                        // do not eagerly read the doc string; this turned out to be unnecessarily
                        // expensive
                        mDoc = fromCharPointerNode.execute(ptrLib.readMember(moduleDef, "doc"));
                    } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw raiseNode.raise(PythonBuiltinClassType.SystemError, "Cannot create module from definition because: %m", e);
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
                            throw raiseNode.raise(PythonBuiltinClassType.SystemError, "field 'defines' did not return an array");
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
                                    writeAttrToMethodNode.execute(method, SpecialAttributeNames.__MODULE__, mName);
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
                            throw raiseNode.raise(PythonBuiltinClassType.SystemError, "field 'legacyMethods' did not return an array");
                        }

                        try {
                            long nLegacyMethods = ptrLib.getArraySize(legacyMethods);
                            CApiContext capiContext = nLegacyMethods > 0 ? PythonContext.get(ptrLib).getCApiContext() : null;
                            for (long i = 0; i < nLegacyMethods; i++) {
                                Object legacyMethod = ptrLib.readArrayElement(legacyMethods, i);

                                PBuiltinFunction fun = addLegacyMethodNode.execute(capiContext, legacyMethod);
                                PBuiltinMethod method = factory.createBuiltinMethod(module, fun);
                                writeAttrToMethodNode.execute(method.getStorage(), SpecialAttributeNames.__MODULE__, mName);
                                writeAttrNode.execute(module, fun.getName(), method);
                            }
                        } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                            // should not happen since we check if 'legacyMethods' has array
                            // elements
                            throw CompilerDirectives.shouldNotReachHere();
                        }
                    }

                    writeAttrNode.execute(module, SpecialAttributeNames.__DOC__, mDoc);

                    return asHandleNode.execute(context, module);
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(context, e);
                    return GraalHPyHandle.NULL_HANDLE;
                }
            } finally {
                gil.release(mustRelease);
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

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyBoolFromLong extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached CastToJavaLongExactNode castToJavaLongNode) throws ArityException {
            checkArity(arguments, 2);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            long left = castToJavaLongNode.execute(arguments[1]);
            Python3Core core = context.getContext();
            return asHandleNode.execute(context, left != 0 ? core.getTrue() : core.getFalse());
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyLongFromLong extends GraalHPyContextFunction {

        private final boolean signed;

        public GraalHPyLongFromLong() {
            this.signed = true;
        }

        public GraalHPyLongFromLong(boolean signed) {
            this.signed = signed;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached CastToJavaLongExactNode castToJavaLongNode,
                        @Cached HPyLongFromLong fromLongNode) throws ArityException {
            checkArity(arguments, 2);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            long left = castToJavaLongNode.execute(arguments[1]);
            return fromLongNode.execute(context, left, signed);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyLongAsPrimitive extends GraalHPyContextFunction {

        private final int targetSize;
        private final int signed;
        private final boolean exact;
        private final boolean requiresPInt;

        public GraalHPyLongAsPrimitive(int signed, int targetSize, boolean exact) {
            this(signed, targetSize, exact, false);
        }

        public GraalHPyLongAsPrimitive(int signed, int targetSize, boolean exact, boolean requiresPInt) {
            this.targetSize = targetSize;
            this.signed = signed;
            this.exact = exact;
            this.requiresPInt = requiresPInt;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                checkArity(arguments, 2);
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object object = asPythonObjectNode.execute(context, arguments[1]);
                try {
                    if (requiresPInt && !isSubtypeNode.execute(getClassNode.execute(object), PythonBuiltinClassType.PInt)) {
                        throw raiseNode.raise(TypeError, ErrorMessages.INTEGER_REQUIRED);
                    }
                    return asNativePrimitiveNode.execute(object, signed, targetSize, exact);
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(context, e);
                    return -1L;
                }
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyLongAsDouble extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached PyLongAsDoubleNode asDoubleNode) throws ArityException {
            checkArity(arguments, 2);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            return asDoubleNode.execute(asPythonObjectNode.execute(context, arguments[1]));
        }
    }

    @ExportLibrary(InteropLibrary.class)
    abstract static class GraalHPyArithmetic extends GraalHPyContextFunction {

        @CompilationFinal private RootCallTarget callTarget;

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached GenericInvokeNode invokeNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                /*
                 * We need to do argument checking at this position because our helper root node
                 * won't do it.
                 */
                checkArguments(arguments);

                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object[] pythonArguments = PArguments.create(arguments.length - 1);
                // TODO(fa): cache len and explode loop
                for (int i = 0; i < PArguments.getUserArgumentLength(pythonArguments); i++) {
                    PArguments.setArgument(pythonArguments, i, asPythonObjectNode.execute(context, arguments[i + 1]));
                }

                try {
                    Object result = invokeNode.execute(ensureCallTarget(), pythonArguments);
                    return asHandleNode.execute(context, result);
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(e);
                    return GraalHPyHandle.NULL_HANDLE;
                }
            } finally {
                gil.release(mustRelease);
            }
        }

        private RootCallTarget ensureCallTarget() {
            if (callTarget == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callTarget = createCallTarget(PythonLanguage.get(null));
            }
            return callTarget;
        }

        protected abstract void checkArguments(Object[] arguments) throws ArityException;

        protected abstract RootCallTarget createCallTarget(PythonLanguage language);

    }

    public static final class GraalHPyUnaryArithmetic extends GraalHPyArithmetic {
        private final UnaryArithmetic unaryOperator;

        public GraalHPyUnaryArithmetic(UnaryArithmetic unaryOperator) {
            this.unaryOperator = unaryOperator;
        }

        @Override
        protected void checkArguments(Object[] arguments) throws ArityException {
            // we also need to account for the HPy context
            checkArity(arguments, 2);
        }

        @Override
        protected RootCallTarget createCallTarget(PythonLanguage language) {
            return language.createCachedCallTarget(unaryOperator::createRootNode, unaryOperator);
        }
    }

    public static final class GraalHPyBinaryArithmetic extends GraalHPyArithmetic {
        private final BinaryArithmetic binaryOperator;

        public GraalHPyBinaryArithmetic(BinaryArithmetic unaryOperator) {
            this.binaryOperator = unaryOperator;
        }

        @Override
        protected void checkArguments(Object[] arguments) throws ArityException {
            // we also need to account for the HPy context
            checkArity(arguments, 3);
        }

        @Override
        protected RootCallTarget createCallTarget(PythonLanguage language) {
            return language.createCachedCallTarget(binaryOperator::createRootNode, binaryOperator);
        }
    }

    public static final class GraalHPyTernaryArithmetic extends GraalHPyArithmetic {
        private final TernaryArithmetic ternaryOperator;

        public GraalHPyTernaryArithmetic(TernaryArithmetic unaryOperator) {
            this.ternaryOperator = unaryOperator;
        }

        @Override
        protected void checkArguments(Object[] arguments) throws ArityException {
            // we also need to account for the HPy context
            checkArity(arguments, 4);
        }

        @Override
        protected RootCallTarget createCallTarget(PythonLanguage language) {
            return language.createCachedCallTarget(ternaryOperator::createRootNode, ternaryOperator);
        }
    }

    public static final class GraalHPyInplaceArithmetic extends GraalHPyArithmetic {
        private final InplaceArithmetic inplaceOperator;

        public GraalHPyInplaceArithmetic(InplaceArithmetic unaryOperator) {
            this.inplaceOperator = unaryOperator;
        }

        @Override
        protected void checkArguments(Object[] arguments) throws ArityException {
            // we also need to account for the HPy context
            if (inplaceOperator.isTernary() && arguments.length != 4) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(4, 4, arguments.length);
            }
            if (!inplaceOperator.isTernary() && arguments.length != 3) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(3, 3, arguments.length);
            }
        }

        @Override
        protected RootCallTarget createCallTarget(PythonLanguage language) {
            return language.createCachedCallTarget(inplaceOperator::createRootNode, inplaceOperator);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyDictNew extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached PythonObjectFactory factory,
                        @Cached HPyAsHandleNode asHandleNode) throws ArityException {
            checkArity(arguments, 1);
            return asHandleNode.execute(asContextNode.execute(arguments[0]), factory.createDict());
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyDictSetItem extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode dictAsPythonObjectNode,
                        @Cached HPyAsPythonObjectNode keyAsPythonObjectNode,
                        @Cached HPyAsPythonObjectNode valueAsPythonObjectNode,
                        @CachedLibrary(limit = "3") HashingStorageLibrary hashingStorageLibrary,
                        @Cached("createClassProfile()") ValueProfile profile,
                        @Cached("createCountingProfile()") ConditionProfile updateStorageProfile,
                        @Cached HPyRaiseNode raiseNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                checkArity(arguments, 4);
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object left = profile.profile(dictAsPythonObjectNode.execute(context, arguments[1]));
                if (!PGuards.isDict(left)) {
                    return raiseNode.raiseIntWithoutFrame(context, -1, SystemError, "bad internal call");
                }
                PDict dict = (PDict) left;
                Object key = keyAsPythonObjectNode.execute(context, arguments[2]);
                Object value = valueAsPythonObjectNode.execute(context, arguments[3]);
                try {
                    HashingStorage dictStorage = dict.getDictStorage();
                    HashingStorage updatedStorage = hashingStorageLibrary.setItem(dictStorage, key, value);
                    if (updateStorageProfile.profile(updatedStorage != dictStorage)) {
                        dict.setDictStorage(updatedStorage);
                    }
                    return 0;
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(context, e);
                    return -1;
                }
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyDictGetItem extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode dictAsPythonObjectNode,
                        @Cached HPyAsPythonObjectNode keyAsPythonObjectNode,
                        @CachedLibrary(limit = "2") HashingStorageLibrary hashingStorageLibrary,
                        @Cached("createClassProfile()") ValueProfile profile,
                        @Cached HPyRaiseNode raiseNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                checkArity(arguments, 3);
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object left = profile.profile(dictAsPythonObjectNode.execute(context, arguments[1]));
                if (!PGuards.isDict(left)) {
                    return raiseNode.raiseWithoutFrame(context, GraalHPyHandle.NULL_HANDLE, SystemError, "bad internal call");
                }
                PDict dict = (PDict) left;
                Object key = keyAsPythonObjectNode.execute(context, arguments[2]);
                try {
                    Object item = hashingStorageLibrary.getItem(dict.getDictStorage(), key);
                    if (item != null) {
                        return asHandleNode.execute(context, item);
                    }
                    return GraalHPyHandle.NULL_HANDLE;
                } catch (PException e) {
                    /*
                     * This function has the same (odd) error behavior as PyDict_GetItem: If an
                     * error occurred, the error is cleared and NULL is returned.
                     */
                    return GraalHPyHandle.NULL_HANDLE;
                }
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyListNew extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
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
            return asHandleNode.execute(asContextNode.execute(arguments[0]), factory.createList(data));
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyListAppend extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode listAsPythonObjectNode,
                        @Cached HPyAsPythonObjectNode valueAsPythonObjectNode,
                        @Cached LookupInheritedAttributeNode.Dynamic lookupAppendNode,
                        @Cached CallBinaryMethodNode callAppendNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached HPyRaiseNode raiseNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                checkArity(arguments, 3);
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object left = listAsPythonObjectNode.execute(context, arguments[1]);
                if (!PGuards.isList(left)) {
                    return raiseNode.raiseIntWithoutFrame(context, -1, SystemError, "bad internal call");
                }
                PList list = (PList) left;
                Object value = valueAsPythonObjectNode.execute(context, arguments[2]);
                Object attrAppend = lookupAppendNode.execute(list, "append");
                if (attrAppend == PNone.NO_VALUE) {
                    return raiseNode.raiseIntWithoutFrame(context, -1, SystemError, "list does not have attribute 'append'");
                }
                try {
                    callAppendNode.executeObject(null, attrAppend, list, value);
                    return 0;
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(context, e);
                    return -1;
                }
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyFloatFromDouble extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached CastToJavaDoubleNode castToJavaDoubleNode,
                        @Cached HPyAsHandleNode asHandleNode) throws ArityException {
            checkArity(arguments, 2);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            double value = castToJavaDoubleNode.execute(arguments[1]);
            return asHandleNode.execute(context, value);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyFloatAsDouble extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached PyFloatAsDoubleNode asDoubleNode) throws ArityException {
            checkArity(arguments, 2);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            return asDoubleNode.execute(null, asPythonObjectNode.execute(context, arguments[1]));
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyCheckBuiltinType extends GraalHPyContextFunction {

        private final PythonBuiltinClassType expectedType;

        public GraalHPyCheckBuiltinType(PythonBuiltinClassType expectedType) {
            this.expectedType = expectedType;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                checkArity(arguments, 2);
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object object = asPythonObjectNode.execute(context, arguments[1]);
                return PInt.intValue(isSubtypeNode.execute(getClassNode.execute(object), expectedType));
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyErrRaisePredefined extends GraalHPyContextFunction {

        private final PythonBuiltinClassType errType;
        private final Object errorMessage;
        private final boolean primitiveErrorValue;

        public GraalHPyErrRaisePredefined(PythonBuiltinClassType errType) {
            this(errType, null, false);
        }

        public GraalHPyErrRaisePredefined(PythonBuiltinClassType errType, String errorMessage) {
            this(errType, errorMessage, false);
        }

        public GraalHPyErrRaisePredefined(PythonBuiltinClassType errType, String errorMessage, boolean primitiveErrorValue) {
            this.errType = errType;
            this.errorMessage = errorMessage != null ? errorMessage : PNone.NO_VALUE;
            this.primitiveErrorValue = primitiveErrorValue;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, 1);
            GraalHPyContext context = asContextNode.execute(arguments[0]);

            // Unfortunately, the HPyRaiseNode is not suitable because it expects a String
            // message.
            try {
                throw raiseNode.raise(errType, errorMessage);
            } catch (PException p) {
                transformExceptionToNativeNode.execute(context, p);
            }
            return primitiveErrorValue ? 0 : GraalHPyHandle.NULL_HANDLE;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyErrSetString extends GraalHPyContextFunction {

        private final boolean stringMode;

        public GraalHPyErrSetString(boolean stringMode) {
            this.stringMode = stringMode;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached IsSubtypeNode isExcValueSubtypeNode,
                        @Cached GetClassNode getClassNode,
                        @Cached PCallHPyFunction callFromStringNode,
                        @Cached CallNode callExceptionConstructorNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                checkArity(arguments, 3);
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object errTypeObj = asPythonObjectNode.execute(context, arguments[1]);
                if (!(PGuards.isPythonClass(errTypeObj) && isSubtypeNode.execute(errTypeObj, PythonBuiltinClassType.PBaseException))) {
                    return raiseNode.raise(SystemError, "exception %s not a BaseException subclass", errTypeObj);
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
                        Object valueObj = asPythonObjectNode.execute(context, arguments[2]);
                        // If the exception value is already an exception object, just take it.
                        if (isExcValueSubtypeNode.execute(getClassNode.execute(valueObj), PythonBuiltinClassType.PBaseException)) {
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
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyErrSetFromErrnoWithFilenameObjects extends GraalHPyContextFunction {

        private final boolean withObjects;

        public GraalHPyErrSetFromErrnoWithFilenameObjects(boolean withObjects) {
            this.withObjects = withObjects;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PCallHPyFunction callFromStringNode,
                        @Cached CallNode callExceptionConstructorNode,
                        @CachedLibrary(limit = "2") InteropLibrary lib,
                        @Cached PRaiseNode raiseNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                checkArity(arguments, withObjects ? 4 : 3);
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object errTypeObj = asPythonObjectNode.execute(context, arguments[1]);
                Object i = callFromStringNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_GET_ERRNO);
                Object message = callFromStringNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_GET_STRERROR, i);
                try {
                    if (!isSubtypeNode.execute(errTypeObj, PythonBuiltinClassType.PBaseException)) {
                        return raiseNode.raise(SystemError, "exception %s not a BaseException subclass", errTypeObj);
                    }
                    Object exception = null;
                    if (!withObjects) {
                        if (!lib.isNull(arguments[2])) {
                            Object filename_fsencoded = callFromStringNode.call(context, GraalHPyNativeSymbol.POLYGLOT_FROM_STRING, arguments[2], "utf-8");
                            exception = callExceptionConstructorNode.execute(errTypeObj, i, message, filename_fsencoded);
                        }
                    } else {
                        Object filenameObject = asPythonObjectNode.execute(context, arguments[2]);
                        if (filenameObject != NULL_HANDLE_DELEGATE) {
                            Object filenameObject2 = asPythonObjectNode.execute(context, arguments[3]);
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
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyFatalError extends GraalHPyContextFunction {
        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @CachedLibrary(limit = "1") InteropLibrary interopLib) throws ArityException {
            checkArity(arguments, 2);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object valueObj = fromCharPointerNode.execute(arguments[1]);
            String errorMessage = "<message not set>";
            if (!interopLib.isNull(valueObj)) {
                try {
                    errorMessage = castToJavaStringNode.execute(valueObj);
                } catch (CannotCastException e) {
                    // ignore
                }
            }
            CExtCommonNodes.fatalError(asContextNode, context.getContext(), null, errorMessage, -1);
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyErrOccurred extends GraalHPyContextFunction {

        @ExportMessage
        int execute(Object[] arguments,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached HPyAsContextNode asContextNode) throws ArityException {
            checkArity(arguments, 1);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            return getThreadStateNode.getCurrentException(context.getContext()) != null ? 1 : 0;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyErrExceptionMatches extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
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
            Object exc = asPythonObjectNode.execute(context, arguments[1]);
            if (exc == NULL_HANDLE_DELEGATE) {
                return 0;
            }
            return exceptionMatches.execute(context, err.getUnreifiedException(), exc);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyErrClear extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached HPyAsContextNode asContextNode) throws ArityException {
            checkArity(arguments, 1);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            getThreadStateNode.setCurrentException(context.getContext(), null);
            return PNone.NO_VALUE;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyErrWarnEx extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached CastToJavaIntExactNode castToJavaIntNode,
                        @Cached WarnNode warnNode,
                        @CachedLibrary(limit = "2") InteropLibrary interopLib) throws ArityException {
            checkArity(arguments, 4);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object category;
            if (interopLib.isNull(arguments[1])) {
                category = RuntimeWarning;
            } else {
                category = asPythonObjectNode.execute(context, arguments[1]);
            }
            Object valueObj = fromCharPointerNode.execute(arguments[2]);
            String message = "";
            if (!interopLib.isNull(valueObj)) {
                try {
                    message = castToJavaStringNode.execute(valueObj);
                } catch (CannotCastException e) {
                    // ignore
                }
            }
            int stackLevel = castToJavaIntNode.execute(arguments[3]);
            warnNode.warnEx(null, category, message, stackLevel);
            return 0;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyErrWriteUnraisable extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached WriteUnraisableNode writeUnraisableNode) throws ArityException {
            checkArity(arguments, 2);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            PException exception = getThreadStateNode.getCurrentException(context.getContext());
            getThreadStateNode.setCurrentException(context.getContext(), null);
            Object object = asPythonObjectNode.execute(context, arguments[1]);
            writeUnraisableNode.execute(null, exception.getUnreifiedException(), null, (object instanceof PNone) ? PNone.NONE : object);
            return 0; // void
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyUnicodeAsCharsetString extends GraalHPyContextFunction {

        private final Charset charset;

        private GraalHPyUnicodeAsCharsetString(Charset charset) {
            this.charset = charset;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached HPyAsHandleNode resultAsHandleNode,
                        @Cached EncodeNativeStringNode encodeNativeStringNode,
                        @Cached PythonObjectFactory factory,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                checkArity(arguments, 2);
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object unicodeObject = asPythonObjectNode.execute(context, arguments[1]);
                try {
                    byte[] result = encodeNativeStringNode.execute(charset, unicodeObject, CodecsModuleBuiltins.STRICT);
                    return resultAsHandleNode.execute(context, factory.createBytes(result));
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(context, e);
                    return GraalHPyHandle.NULL_HANDLE;
                }
            } finally {
                gil.release(mustRelease);
            }
        }

        public static GraalHPyUnicodeAsCharsetString asUTF8() {
            return new GraalHPyUnicodeAsCharsetString(StandardCharsets.UTF_8);
        }

        public static GraalHPyUnicodeAsCharsetString asASCII() {
            return new GraalHPyUnicodeAsCharsetString(StandardCharsets.US_ASCII);
        }

        public static GraalHPyUnicodeAsCharsetString asLatin1() {
            return new GraalHPyUnicodeAsCharsetString(StandardCharsets.ISO_8859_1);
        }

        @TruffleBoundary
        public static GraalHPyUnicodeAsCharsetString asFSDefault() {
            return new GraalHPyUnicodeAsCharsetString(CharsetMapping.getCharset(GetFileSystemEncodingNode.getFileSystemEncoding()));
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyUnicodeAsUTF8AndSize extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached PCallHPyFunction callFromTyped,
                        @Cached GetLLVMType getLLVMType,
                        @Cached EncodeNativeStringNode encodeNativeStringNode,
                        @CachedLibrary(limit = "3") InteropLibrary ptrLib,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                checkArity(arguments, 3);
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object unicodeObject = asPythonObjectNode.execute(context, arguments[1]);
                Object sizePtr = arguments[2];
                try {
                    byte[] result = encodeNativeStringNode.execute(StandardCharsets.UTF_8, unicodeObject, CodecsModuleBuiltins.STRICT);
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
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyUnicodeFromString extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached CastToJavaStringNode toString,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached HPyAsHandleNode asHandleNode) throws ArityException {
            checkArity(arguments, 2);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            try {
                // TODO(fa) provide encoding (utf8)
                String str = toString.execute(fromCharPointerNode.execute(arguments[1]));
                return asHandleNode.execute(context, str);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(context, e);
                return GraalHPyHandle.NULL_HANDLE;
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyUnicodeFromWchar extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsHandleNode resultAsHandleNode,
                        @Cached CastToJavaLongExactNode castToJavaLongNode,
                        @Cached PCallHPyFunction callFromWcharArrayNode,
                        @Cached UnicodeFromWcharNode unicodeFromWcharNode,
                        @Cached SizeofWCharNode sizeofWCharNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                checkArity(arguments, 3);
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                long len = castToJavaLongNode.execute(arguments[2]);
                // Note: 'len' may be -1; in this case, function GRAAL_HPY_I8_FROM_WCHAR_ARRAY will
                // use 'wcslen' to determine the C array's length.
                Object dataArray = callFromWcharArrayNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_FROM_WCHAR_ARRAY, arguments[1], len);
                try {
                    return resultAsHandleNode.execute(context, unicodeFromWcharNode.execute(dataArray, PInt.intValueExact(sizeofWCharNode.execute(context))));
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(context, e);
                    return GraalHPyHandle.NULL_HANDLE;
                } catch (OverflowException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyUnicodeDecodeCharset extends GraalHPyContextFunction {

        private final String charset;

        public GraalHPyUnicodeDecodeCharset(String charset) {
            this.charset = charset;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached PCallHPyFunction callHPyFunction,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                checkArity(arguments, 2);
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object result = callHPyFunction.call(context, GraalHPyNativeSymbol.POLYGLOT_FROM_STRING, arguments[1], charset);
                return asHandleNode.execute(context, result);
            } finally {
                gil.release(mustRelease);
            }
        }

        public static GraalHPyUnicodeDecodeCharset decodeFSDefault() {
            return new GraalHPyUnicodeDecodeCharset(GetFileSystemEncodingNode.getFileSystemEncoding());
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyUnicodeDecodeCharsetAndSize extends GraalHPyContextFunction {

        private final String charset;

        public GraalHPyUnicodeDecodeCharsetAndSize(String charset) {
            this.charset = charset;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached PCallHPyFunction callHPyFunction,
                        @CachedLibrary(limit = "2") InteropLibrary interopLib,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached CastToJavaLongExactNode castToJavaLongNode,
                        @Cached GetByteArrayNode getByteArrayNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
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
                String result = decode(CodingErrorAction.IGNORE, bytes);
                return asHandleNode.execute(context, result);
            } finally {
                gil.release(mustRelease);
            }
        }

        public static GraalHPyUnicodeDecodeCharsetAndSize decodeFSDefault() {
            return new GraalHPyUnicodeDecodeCharsetAndSize(GetFileSystemEncodingNode.getFileSystemEncoding());
        }

        @TruffleBoundary
        private String decode(CodingErrorAction errorAction, byte[] bytes) {
            try {
                return CharsetMapping.getCharset(charset).newDecoder().onMalformedInput(errorAction).onUnmappableCharacter(errorAction).decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyUnicodeDecodeCharsetAndSizeAndErrors extends GraalHPyContextFunction {

        private final String charset;

        public GraalHPyUnicodeDecodeCharsetAndSizeAndErrors(String charset) {
            this.charset = charset;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached PCallHPyFunction callHPyFunction,
                        @CachedLibrary(limit = "2") InteropLibrary interopLib,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached CastToJavaLongExactNode castToJavaLongNode,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached GetByteArrayNode getByteArrayNode,
                        @Cached HPyRaiseNode raiseNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
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
                String errors = castToJavaStringNode.execute(callHPyFunction.call(context, GraalHPyNativeSymbol.POLYGLOT_FROM_STRING, arguments[3], "ascii"));
                CodingErrorAction errorAction = CodecsModuleBuiltins.convertCodingErrorAction(errors);
                String result = decode(errorAction, bytes);
                if (result == null) {
                    // TODO: refactor helper nodes for CodecsModuleBuiltins to use them here
                    return raiseNode.raiseWithoutFrame(context, GraalHPyHandle.NULL_HANDLE, PythonBuiltinClassType.UnicodeDecodeError, ErrorMessages.MALFORMED_INPUT);
                } else {
                    return asHandleNode.execute(context, result);
                }
            } finally {
                gil.release(mustRelease);
            }
        }

        public static GraalHPyUnicodeDecodeCharsetAndSizeAndErrors decodeASCII() {
            return new GraalHPyUnicodeDecodeCharsetAndSizeAndErrors("ASCII");
        }

        public static GraalHPyUnicodeDecodeCharsetAndSizeAndErrors decodeLatin1() {
            return new GraalHPyUnicodeDecodeCharsetAndSizeAndErrors("ISO-8859-1");
        }

        @TruffleBoundary
        private String decode(CodingErrorAction errorAction, byte[] bytes) {
            try {
                return CharsetMapping.getCharset(charset).newDecoder().onMalformedInput(errorAction).onUnmappableCharacter(errorAction).decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException ex) {
                return null;
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyUnicodeReadChar extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached CastToJavaLongExactNode castToJavaLongNode,
                        @Cached PyUnicodeReadCharNode unicodeReadChar,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                checkArity(arguments, 3);
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                long index = castToJavaLongNode.execute(arguments[2]);
                return unicodeReadChar.execute(asPythonObjectNode.execute(context, arguments[1]), index);
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyAsPyObject extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode handleAsPythonObjectNode,
                        @Cached ToNewRefNode toPyObjectPointerNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                checkArity(arguments, 2);
                GraalHPyContext hPyContext = asContextNode.execute(arguments[0]);
                Object object = handleAsPythonObjectNode.execute(hPyContext, arguments[1]);
                return toPyObjectPointerNode.execute(hPyContext.getContext().getCApiContext(), object);
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyBytesAsString extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached HPyRaiseNode raiseNode) throws ArityException {
            checkArity(arguments, 2);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object object = asPythonObjectNode.execute(context, arguments[1]);
            if (object instanceof PBytes) {
                return new PySequenceArrayWrapper(object, 1);
            }
            return raiseNode.raiseIntWithoutFrame(context, -1, TypeError, "expected bytes, %p found", object);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyBytesGetSize extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached SequenceNodes.LenNode lenNode,
                        @Cached HPyRaiseNode raiseNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                checkArity(arguments, 2);
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object object = asPythonObjectNode.execute(context, arguments[1]);
                if (object instanceof PBytes) {
                    return lenNode.execute((PSequence) object);
                }
                return raiseNode.raiseIntWithoutFrame(context, -1, TypeError, "expected bytes, %p found", object);
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyBytesFromStringAndSize extends GraalHPyContextFunction {

        private final boolean withSize;

        public GraalHPyBytesFromStringAndSize(boolean withSize) {
            this.withSize = withSize;
        }

        @ExportMessage
        Object execute(Object[] arguments,
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
                        return raiseNode.raiseWithoutFrame(context, GraalHPyHandle.NULL_HANDLE, ValueError, "NULL char * passed to HPyBytes_FromStringAndSize");
                    }
                    size = castToJavaIntNode.execute(arguments[2]);
                    if (size == 0) {
                        return asHandleNode.execute(context, factory.createBytes(new byte[size]));
                    }
                    if (size < 0) {
                        return raiseNode.raiseWithoutFrame(context, GraalHPyHandle.NULL_HANDLE, SystemError, "negative size passed");
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
                return asHandleNode.execute(context, factory.createBytes(getByteArrayNode.execute(charPtr, size)));
            } catch (InteropException e) {
                return raiseNode.raiseWithoutFrame(context, GraalHPyHandle.NULL_HANDLE, TypeError, "%m", e);
            } catch (OverflowException e) {
                return raiseNode.raiseWithoutFrame(context, GraalHPyHandle.NULL_HANDLE, OverflowError, ErrorMessages.BYTE_STR_IS_TOO_LARGE);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyIsTrue extends GraalHPyContextFunction {

        @ExportMessage
        int execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached PyObjectIsTrueNode isTrueNode) throws ArityException {
            checkArity(arguments, 2);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object object = asPythonObjectNode.execute(context, arguments[1]);
            return PInt.intValue(isTrueNode.execute(null, object));
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class GraalHPyGetAttr extends GraalHPyContextFunction {

        private final FunctionMode mode;

        GraalHPyGetAttr(FunctionMode mode) {
            checkMode(mode, OBJECT, CHAR_PTR);
            this.mode = mode;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode receiverAsPythonObjectNode,
                        @Cached HPyAsPythonObjectNode keyAsPythonObjectNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached PInteropGetAttributeNode getAttributeNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                checkArity(arguments, 3);
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object receiver = receiverAsPythonObjectNode.execute(context, arguments[1]);
                Object key;
                switch (mode) {
                    case OBJECT:
                        key = keyAsPythonObjectNode.execute(context, arguments[2]);
                        break;
                    case CHAR_PTR:
                        key = fromCharPointerNode.execute(arguments[2]);
                        break;
                    default:
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw new IllegalStateException("should not be reached");
                }
                try {
                    return asHandleNode.execute(context, getAttributeNode.execute(receiver, key));
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(context, e);
                    return GraalHPyHandle.NULL_HANDLE;
                }
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class GraalHPyMaybeGetAttrS extends GraalHPyContextFunction {
        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode receiverAsPythonObjectNode,
                        @Cached HPyAsPythonObjectNode keyAsPythonObjectNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached PyObjectLookupAttr lookupAttr,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                checkArity(arguments, 3);
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object receiver = receiverAsPythonObjectNode.execute(context, arguments[1]);
                Object key = fromCharPointerNode.execute(arguments[2]);
                return asHandleNode.execute(context, lookupAttr.execute(null, receiver, key));
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyTypeFromSpec extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached PCallHPyFunction callHelperFunctionNode,
                        @Cached HPyCreateTypeFromSpecNode createTypeFromSpecNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                checkArity(arguments, 3);

                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object typeSpec = callHelperFunctionNode.call(context, GRAAL_HPY_FROM_HPY_TYPE_SPEC, arguments[1]);
                Object typeSpecParamArray = callHelperFunctionNode.call(context, GRAAL_HPY_FROM_HPY_TYPE_SPEC_PARAM_ARRAY, arguments[2]);

                try {
                    Object newType = createTypeFromSpecNode.execute(context, typeSpec, typeSpecParamArray);
                    assert PGuards.isClass(newType, InteropLibrary.getUncached()) : "Object created from type spec is not a type";
                    return asHandleNode.execute(context, newType);
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(context, e);
                    return GraalHPyHandle.NULL_HANDLE;
                }
            } finally {
                gil.release(mustRelease);
            }
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static final class GraalHPyHasAttr extends GraalHPyContextFunction {

        private final FunctionMode mode;

        GraalHPyHasAttr(FunctionMode mode) {
            checkMode(mode, OBJECT, CHAR_PTR);
            this.mode = mode;
        }

        @ExportMessage
        int execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode receiverAsPythonObjectNode,
                        @Cached HPyAsPythonObjectNode keyAsPythonObjectNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached PInteropGetAttributeNode getAttributeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                checkArity(arguments, 3);
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object receiver = receiverAsPythonObjectNode.execute(context, arguments[1]);
                Object key;
                switch (mode) {
                    case OBJECT:
                        key = keyAsPythonObjectNode.execute(context, arguments[2]);
                        break;
                    case CHAR_PTR:
                        key = fromCharPointerNode.execute(arguments[2]);
                        break;
                    default:
                        CompilerDirectives.transferToInterpreter();
                        throw new IllegalStateException("should not be reached");
                }
                try {
                    Object attr = getAttributeNode.execute(receiver, key);
                    return attr != PNone.NO_VALUE ? 1 : 0;
                } catch (PException e) {
                    return 0;
                }
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class GraalHPySetAttr extends GraalHPyContextFunction {

        private final FunctionMode mode;

        GraalHPySetAttr(FunctionMode mode) {
            checkMode(mode, OBJECT, CHAR_PTR);
            this.mode = mode;
        }

        @ExportMessage
        int execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode receiverAsPythonObjectNode,
                        @Cached HPyAsPythonObjectNode keyAsPythonObjectNode,
                        @Cached HPyAsPythonObjectNode valueAsPythonObjectNode,
                        @Cached GetClassNode getClassNode,
                        @Cached IsBuiltinClassProfile isPStringProfile,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached LookupInheritedAttributeNode.Dynamic lookupSetAttrNode,
                        @Cached CallTernaryMethodNode callSetAttrNode,
                        @Cached ConditionProfile profile,
                        @Cached HPyRaiseNode raiseNativeNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                checkArity(arguments, 4);

                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object receiver = receiverAsPythonObjectNode.execute(context, arguments[1]);
                Object key;
                switch (mode) {
                    case OBJECT:
                        key = keyAsPythonObjectNode.execute(context, arguments[2]);
                        if (!isPStringProfile.profileClass(getClassNode.execute(key), PythonBuiltinClassType.PString)) {
                            return raiseNativeNode.raiseIntWithoutFrame(context, -1, TypeError, "attribute name must be string, not '%p'", key);
                        }
                        break;
                    case CHAR_PTR:
                        key = fromCharPointerNode.execute(arguments[2]);
                        break;
                    default:
                        CompilerDirectives.transferToInterpreter();
                        throw new IllegalStateException("should not be reached");
                }
                Object value = valueAsPythonObjectNode.execute(context, arguments[3]);

                Object attrGetattribute = lookupSetAttrNode.execute(receiver, SpecialMethodNames.__SETATTR__);
                if (profile.profile(attrGetattribute != PNone.NO_VALUE)) {
                    try {
                        callSetAttrNode.execute(null, attrGetattribute, receiver, key, value);
                    } catch (PException e) {
                        transformExceptionToNativeNode.execute(context, e);
                        return -1;
                    }
                } else {
                    return raiseNativeNode.raiseIntWithoutFrame(context, -1, TypeError, "'%p' object has no attributes", receiver);
                }
                return 0;
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class GraalHPyGetItem extends GraalHPyContextFunction {

        private final FunctionMode mode;

        GraalHPyGetItem(FunctionMode mode) {
            checkMode(mode, OBJECT, CHAR_PTR, INT32);
            this.mode = mode;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode receiverAsPythonObjectNode,
                        @Cached HPyAsPythonObjectNode keyAsPythonObjectNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached PInteropSubscriptNode getItemNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                checkArity(arguments, 3);
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object receiver = receiverAsPythonObjectNode.execute(context, arguments[1]);
                Object key;
                switch (mode) {
                    case OBJECT:
                        key = keyAsPythonObjectNode.execute(context, arguments[2]);
                        break;
                    case CHAR_PTR:
                        key = fromCharPointerNode.execute(arguments[2]);
                        break;
                    case INT32:
                        key = arguments[2];
                        assert key instanceof Number;
                        break;
                    default:
                        CompilerDirectives.transferToInterpreter();
                        throw new IllegalStateException("should not be reached");
                }
                try {
                    return asHandleNode.execute(context, getItemNode.execute(receiver, key));
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(context, e);
                    return GraalHPyHandle.NULL_HANDLE;
                }
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class GraalHPySetItem extends GraalHPyContextFunction {

        private final FunctionMode mode;

        GraalHPySetItem(FunctionMode mode) {
            checkMode(mode, OBJECT, CHAR_PTR, INT32);
            this.mode = mode;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode receiverAsPythonObjectNode,
                        @Cached HPyAsPythonObjectNode keyAsPythonObjectNode,
                        @Cached HPyAsPythonObjectNode valueAsPythonObjectNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached PInteropSubscriptAssignNode setItemNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached HPyRaiseNode raiseNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                checkArity(arguments, 4);
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object receiver = receiverAsPythonObjectNode.execute(context, arguments[1]);
                Object key;
                switch (mode) {
                    case OBJECT:
                        key = keyAsPythonObjectNode.execute(context, arguments[2]);
                        break;
                    case CHAR_PTR:
                        key = fromCharPointerNode.execute(arguments[2]);
                        break;
                    case INT32:
                        key = arguments[2];
                        assert key instanceof Number;
                        break;
                    default:
                        CompilerDirectives.transferToInterpreter();
                        throw new IllegalStateException("should not be reached");
                }
                Object value = valueAsPythonObjectNode.execute(context, arguments[3]);
                try {
                    setItemNode.execute(receiver, key, value);
                    return 0;
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(context, e);
                } catch (UnsupportedMessageException e) {
                    raiseNativeNode.raiseIntWithoutFrame(context, -1, TypeError, "%p object does not support item assignment", receiver);
                }
                return -1;
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyFromPyObject extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Cached ResolveHandleNode resolveHandleNode,
                        @Cached ResolveNativeReferenceNode resolveNativeReferenceNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                checkArity(arguments, 2);
                // IMPORTANT: this is not stealing the reference. The CPython implementation
                // actually
                // increases the reference count by 1.
                Object resolvedPyObject = asPythonObjectNode.execute(resolveNativeReferenceNode.execute(resolveHandleNode.execute(arguments[1]), false));
                return asHandleNode.execute(asContextNode.execute(arguments[0]), resolvedPyObject);
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyNew extends GraalHPyContextFunction {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(GraalHPyNew.class);

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached IsTypeNode isTypeNode,
                        @Cached HPyRaiseNode raiseNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PCallHPyFunction callMallocNode,
                        @Cached PCallHPyFunction callWriteDataNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                checkArity(arguments, 3);
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object type = asPythonObjectNode.execute(context, arguments[1]);
                Object dataOutVar = arguments[2];

                // check if argument is actually a type
                if (!isTypeNode.execute(type)) {
                    return raiseNode.raiseWithoutFrame(context, GraalHPyHandle.NULL_HANDLE, TypeError, "HPy_New arg 1 must be a type");
                }

                // create the managed Python object
                PythonObject pythonObject = null;

                if (type instanceof PythonClass) {
                    PythonClass clazz = (PythonClass) type;
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
                            LOGGER.finest(() -> String.format("Allocated HPy object with native space of size %d at %s", basicSize, dataPtr));
                        }
                        // TODO(fa): add memory tracing
                    }
                }
                if (pythonObject == null) {
                    pythonObject = factory.createPythonObject(type);
                }
                return asHandleNode.execute(context, pythonObject);
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyCast extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached HPyGetNativeSpacePointerNode getNativeSpacePointerNode) throws ArityException {
            checkArity(arguments, 2);
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object receiver = asPythonObjectNode.execute(context, arguments[1]);

            // we can also just return NO_VALUE since that will be interpreter as NULL
            return getNativeSpacePointerNode.execute(receiver);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyTypeGenericNew extends GraalHPyContextFunction {

        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(GraalHPyTypeGenericNew.class);

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PCallHPyFunction callMallocNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                checkArity(arguments, 5);
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object type = asPythonObjectNode.execute(context, arguments[1]);

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
                            LOGGER.finest(() -> String.format("Allocated HPy object with native space of size %d at %s", basicSize, dataPtr));
                        }
                        // TODO(fa): add memory tracing
                    }
                }
                if (pythonObject == null) {
                    pythonObject = factory.createPythonObject(type);
                }
                return asHandleNode.execute(context, pythonObject);
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class GraalHPyCallBuiltinFunction extends GraalHPyContextFunction {

        private final String key;
        private final int nPythonArguments;
        private final ReturnType returnType;

        private ConversionNodeSupplier toNativeNodeSupplier;

        GraalHPyCallBuiltinFunction(String key, int nPythonArguments) {
            this(key, nPythonArguments, ReturnType.OBJECT, null);
        }

        GraalHPyCallBuiltinFunction(String key, int nPythonArguments, ReturnType returnType, ConversionNodeSupplier toNativeNodeSupplier) {
            this.key = key;
            assert nPythonArguments >= 0 : "number of arguments cannot be negative";
            this.nPythonArguments = nPythonArguments;
            this.returnType = returnType;
            this.toNativeNodeSupplier = toNativeNodeSupplier != null ? toNativeNodeSupplier : GraalHPyConversionNodeSupplier.HANDLE;
        }

        @ExportMessage
        static class Execute {

            @Specialization
            static Object doGeneric(GraalHPyCallBuiltinFunction receiver, Object[] arguments,
                            @Cached HPyAsContextNode asContextNode,
                            @Cached HPyAsPythonObjectNode asPythonObjectNode,
                            @Cached ReadAttributeFromObjectNode readAttributeFromObjectNode,
                            @Cached CallNode callNode,
                            @Cached(value = "createToNativeNode(receiver)", uncached = "getUncachedToNativeNode(receiver)") CExtToNativeNode toNativeNode,
                            @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                            @Exclusive @Cached GilNode gil) throws ArityException {
                boolean mustRelease = gil.acquire();
                try {
                    if (arguments.length != receiver.nPythonArguments + 1) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw ArityException.create(receiver.nPythonArguments + 1, receiver.nPythonArguments + 1, arguments.length);
                    }
                    GraalHPyContext nativeContext = asContextNode.execute(arguments[0]);
                    Object[] pythonArguments = new Object[receiver.nPythonArguments];
                    for (int i = 0; i < pythonArguments.length; i++) {
                        pythonArguments[i] = asPythonObjectNode.execute(nativeContext, arguments[i + 1]);
                    }
                    try {
                        Object builtinFunction = readAttributeFromObjectNode.execute(nativeContext.getContext().getBuiltins(), receiver.key);
                        return toNativeNode.execute(nativeContext, callNode.execute(builtinFunction, pythonArguments, PKeyword.EMPTY_KEYWORDS));
                    } catch (PException e) {
                        transformExceptionToNativeNode.execute(nativeContext, e);
                        switch (receiver.returnType) {
                            case OBJECT:
                                return GraalHPyHandle.NULL_HANDLE;
                            case INT:
                                return -1;
                            case FLOAT:
                                return -1.0;

                        }
                    }
                } finally {
                    gil.release(mustRelease);
                }
                throw CompilerDirectives.shouldNotReachHere();
            }

            static CExtToNativeNode createToNativeNode(GraalHPyCallBuiltinFunction receiver) {
                return receiver.toNativeNodeSupplier.createToNativeNode();
            }

            static CExtToNativeNode getUncachedToNativeNode(GraalHPyCallBuiltinFunction receiver) {
                return receiver.toNativeNodeSupplier.getUncachedToNativeNode();
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyRichcompare extends GraalHPyContextFunction {

        private final boolean returnPrimitive;

        public GraalHPyRichcompare(boolean returnPrimitive) {
            this.returnPrimitive = returnPrimitive;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached CastToJavaIntExactNode castToJavaIntExactNode,
                        @Cached GetClassNode getClassNode,
                        @Cached LookupSpecialMethodNode.Dynamic lookupRichcmp,
                        @Cached CallTernaryMethodNode callRichcmp,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException, UnsupportedTypeException {
            boolean mustRelease = gil.acquire();
            try {
                checkArity(arguments, 4);
                GraalHPyContext nativeContext = asContextNode.execute(arguments[0]);
                Object receiver = asPythonObjectNode.execute(nativeContext, arguments[1]);
                Object arg1 = asPythonObjectNode.execute(nativeContext, arguments[2]);
                Object arg2;
                try {
                    arg2 = SpecialMethodNames.getCompareOpString(castToJavaIntExactNode.execute(arguments[3]));
                } catch (CannotCastException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw UnsupportedTypeException.create(arguments, "4th argument must fit into Java int");
                }
                try {
                    Object richcmp = lookupRichcmp.execute(null, getClassNode.execute(receiver), SpecialMethodNames.RICHCMP, receiver);
                    Object result = callRichcmp.execute(null, richcmp, receiver, arg1, arg2);
                    return returnPrimitive ? PInt.intValue(isTrueNode.execute(null, result)) : asHandleNode.execute(nativeContext, result);
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(nativeContext, e);
                    return returnPrimitive ? 0 : GraalHPyHandle.NULL_HANDLE;
                }
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyAsIndex extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, 2);
            GraalHPyContext nativeContext = asContextNode.execute(arguments[0]);
            Object receiver = asPythonObjectNode.execute(nativeContext, arguments[1]);
            try {
                return asHandleNode.execute(nativeContext, indexNode.execute(null, receiver));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(nativeContext, e);
                return GraalHPyHandle.NULL_HANDLE;
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @ImportStatic(SpecialMethodSlot.class)
    public static final class GraalHPyIsNumber extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached CanBeDoubleNode canBeDoubleNode,
                        @Cached GetClassNode getClassNode,
                        @Cached(parameters = "Int") LookupCallableSlotInMRONode lookup,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            checkArity(arguments, 2);
            GraalHPyContext nativeContext = asContextNode.execute(arguments[0]);
            Object receiver = asPythonObjectNode.execute(nativeContext, arguments[1]);
            try {
                if (indexCheckNode.execute(receiver) || canBeDoubleNode.execute(receiver)) {
                    return 1;
                }
                Object receiverType = getClassNode.execute(receiver);
                return PInt.intValue(lookup.execute(receiverType) != PNone.NO_VALUE);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(nativeContext, e);
                return GraalHPyHandle.NULL_HANDLE;
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyTupleFromArray extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached CastToJavaIntExactNode castToJavaIntExactNode,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached PCallHPyFunction callHelperNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached PythonObjectFactory factory,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException, UnsupportedTypeException {
            boolean mustRelease = gil.acquire();
            try {
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
                            elements[i] = asPythonObjectNode.execute(nativeContext, lib.readMember(hpyStructPtr, GraalHPyHandle.I));
                        }
                    } catch (UnsupportedMessageException e) {
                        throw CompilerDirectives.shouldNotReachHere(e);
                    } catch (InvalidArrayIndexException e) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw raiseNode.raise(SystemError, "Cannot access index %d although array should have size %d ", e.getInvalidIndex(), n);
                    } catch (UnknownIdentifierException e) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw raiseNode.raise(SystemError, "Cannot read handle value");
                    }

                    return asHandleNode.execute(nativeContext, factory.createTuple(elements));
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(nativeContext, e);
                    return GraalHPyHandle.NULL_HANDLE;
                }
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    abstract static class GraalHPyBuilderNewBase extends GraalHPyContextFunction {

        protected abstract Object createObject(int capacity);

        protected final Object doExecute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached CastToJavaIntExactNode castToJavaIntExactNode,
                        @Cached HPyAsHandleNode asHandleNode) throws ArityException {
            checkArity(arguments, 2);
            GraalHPyContext nativeContext = asContextNode.execute(arguments[0]);
            try {
                int capacity = castToJavaIntExactNode.execute(arguments[1]);
                if (capacity >= 0) {
                    return asHandleNode.execute(nativeContext, createObject(capacity));
                }
            } catch (CannotCastException e) {
                // fall through
            }
            return GraalHPyHandle.NULL_HANDLE;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyBuilderNew extends GraalHPyBuilderNewBase {
        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached CastToJavaIntExactNode castToJavaIntExactNode,
                        @Cached HPyAsHandleNode asHandleNode) throws ArityException {
            return doExecute(arguments, asContextNode, castToJavaIntExactNode, asHandleNode);
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

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyBuilderSet extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached CastToJavaIntExactNode castToJavaIntExactNode,
                        @Cached SequenceStorageNodes.SetItemDynamicNode setItemNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException, UnsupportedTypeException {
            checkArity(arguments, 4);
            GraalHPyContext nativeContext = asContextNode.execute(arguments[0]);
            Object builder = asPythonObjectNode.execute(nativeContext, arguments[1]);
            if (!isValid(builder)) {
                // that's really unexpected since the C signature should enforce a valid builder
                // but
                // someone could have messed it up
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw UnsupportedTypeException.create(arguments, "invalid builder object");
            }
            ObjectSequenceStorage storage = (ObjectSequenceStorage) builder;

            try {
                int idx = castToJavaIntExactNode.execute(arguments[2]);
                Object value = asPythonObjectNode.execute(nativeContext, arguments[3]);
                setItemNode.execute(null, NoGeneralizationNode.DEFAULT, storage, idx, value);
            } catch (CannotCastException e) {
                // fall through
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
            }
            return 0;
        }

        private boolean isValid(Object object) {
            return object instanceof ObjectSequenceStorage;
        }

    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyBuilderBuild extends GraalHPyContextFunction {

        private final PythonBuiltinClassType type;

        public GraalHPyBuilderBuild(PythonBuiltinClassType type) {
            assert type == PythonBuiltinClassType.PTuple || type == PythonBuiltinClassType.PList;
            this.type = type;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyCloseAndGetHandleNode closeAndGetHandleNode,
                        @Cached PythonObjectFactory factory,
                        @Cached HPyAsHandleNode asHandleNode) throws ArityException, UnsupportedTypeException {
            checkArity(arguments, 2);
            GraalHPyContext nativeContext = asContextNode.execute(arguments[0]);
            ObjectSequenceStorage builder = cast(closeAndGetHandleNode.execute(nativeContext, arguments[1]));
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
            return asHandleNode.execute(nativeContext, sequence);
        }

        private static ObjectSequenceStorage cast(Object object) {
            if (object instanceof ObjectSequenceStorage) {
                return (ObjectSequenceStorage) object;
            }
            return null;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyBuilderCancel extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyCloseAndGetHandleNode closeAndGetHandleNode) throws ArityException, UnsupportedTypeException {
            checkArity(arguments, 2);
            GraalHPyContext nativeContext = asContextNode.execute(arguments[0]);

            // be pedantic and also check what we are cancelling
            ObjectSequenceStorage builder = cast(closeAndGetHandleNode.execute(nativeContext, arguments[1]));
            if (builder == null) {
                // that's really unexpected since the C signature should enforce a valid builder
                // but
                // someone could have messed it up
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

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyTrackerNew extends GraalHPyBuilderNewBase {
        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached CastToJavaIntExactNode castToJavaIntExactNode,
                        @Cached HPyAsHandleNode asHandleNode) throws ArityException {
            return doExecute(arguments, asContextNode, castToJavaIntExactNode, asHandleNode);
        }

        @Override
        protected Object createObject(int capacity) {
            return new GraalHPyTracker(capacity);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyTrackerAdd extends GraalHPyContextFunction {
        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached HPyEnsureHandleNode ensureHandleNode) throws ArityException, UnsupportedTypeException {
            checkArity(arguments, 3);
            GraalHPyContext nativeContext = asContextNode.execute(arguments[0]);
            GraalHPyTracker builder = cast(asPythonObjectNode.execute(nativeContext, arguments[1]));
            if (builder == null) {
                // that's really unexpected since the C signature should enforce a valid builder
                // but someone could have messed it up
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw UnsupportedTypeException.create(arguments, "invalid builder object");
            }
            try {
                GraalHPyHandle handle = ensureHandleNode.execute(nativeContext, arguments[2]);
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

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyTrackerCleanup extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyCloseAndGetHandleNode closeAndGetHandleNode,
                        @Cached HPyCloseHandleNode closeHandleNode) throws ArityException, UnsupportedTypeException {
            checkArity(arguments, 2);
            GraalHPyContext nativeContext = asContextNode.execute(arguments[0]);
            GraalHPyTracker builder = cast(closeAndGetHandleNode.execute(nativeContext, arguments[1]));
            if (builder == null) {
                // that's really unexpected since the C signature should enforce a valid builder
                // but someone could have messed it up
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw UnsupportedTypeException.create(arguments, "invalid builder object");
            }
            builder.free(nativeContext, closeHandleNode);
            return 0;
        }

        static GraalHPyTracker cast(Object object) {
            if (object instanceof GraalHPyTracker) {
                return (GraalHPyTracker) object;
            }
            return null;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyTrackerForgetAll extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode) throws ArityException, UnsupportedTypeException {
            checkArity(arguments, 2);
            GraalHPyContext nativeContext = asContextNode.execute(arguments[0]);
            GraalHPyTracker builder = GraalHPyTrackerCleanup.cast(asPythonObjectNode.execute(nativeContext, arguments[1]));
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

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyIsCallable extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached PyCallableCheckNode callableCheck) throws ArityException {
            checkArity(arguments, 2);
            GraalHPyContext nativeContext = asContextNode.execute(arguments[0]);
            Object object = asPythonObjectNode.execute(nativeContext, arguments[1]);
            return PInt.intValue(callableCheck.execute(object));
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyCallTupleDict extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached ExecutePositionalStarargsInteropNode expandArgsNode,
                        @Cached HashingCollectionNodes.LenNode lenNode,
                        @Cached ExpandKeywordStarargsNode expandKwargsNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached CallNode callNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached PRaiseNode raiseNode) throws ArityException {
            checkArity(arguments, 4);
            GraalHPyContext nativeContext = asContextNode.execute(arguments[0]);
            try {
                // check and expand args
                Object argsObject = asPythonObjectNode.execute(nativeContext, arguments[2]);
                Object[] args = castArgs(argsObject, expandArgsNode, raiseNode);

                // check and expand kwargs
                Object kwargsObject = asPythonObjectNode.execute(nativeContext, arguments[3]);
                PKeyword[] keywords = castKwargs(kwargsObject, lenNode, expandKwargsNode, raiseNode);

                Object callable = asPythonObjectNode.execute(nativeContext, arguments[1]);
                return asHandleNode.execute(nativeContext, callNode.execute(callable, args, keywords));
            } catch (PException e) {
                // transformExceptionToNativeNode acts as a branch profile
                transformExceptionToNativeNode.execute(nativeContext, e);
                return GraalHPyHandle.NULL_HANDLE;
            }
        }

        private static Object[] castArgs(Object args,
                        ExecutePositionalStarargsInteropNode expandArgsNode,
                        PRaiseNode raiseNode) {
            // this indicates that a NULL handle was passed (which is valid)
            if (args == PNone.NO_VALUE) {
                return PythonUtils.EMPTY_OBJECT_ARRAY;
            }
            if (PGuards.isPTuple(args)) {
                return expandArgsNode.executeWithGlobalState(args);
            }
            throw raiseNode.raise(TypeError, "HPy_CallTupleDict requires args to be a tuple or null handle");
        }

        private static PKeyword[] castKwargs(Object kwargs,
                        @Cached HashingCollectionNodes.LenNode lenNode,
                        @Cached ExpandKeywordStarargsNode expandKwargsNode,
                        @Cached PRaiseNode raiseNode) {
            // this indicates that a NULL handle was passed (which is valid)
            if (kwargs == PNone.NO_VALUE || isEmptyDict(kwargs, lenNode)) {
                return PKeyword.EMPTY_KEYWORDS;
            }
            if (PGuards.isDict(kwargs)) {
                return expandKwargsNode.execute(kwargs);
            }
            throw raiseNode.raise(TypeError, "HPy_CallTupleDict requires kw to be a dict or null handle");
        }

        private static boolean isEmptyDict(Object delegate, HashingCollectionNodes.LenNode lenNode) {
            return delegate instanceof PDict && lenNode.execute((PDict) delegate) == 0;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyDump extends GraalHPyContextFunction {

        @ExportMessage
        @TruffleBoundary
        Object execute(Object[] arguments) throws ArityException {
            checkArity(arguments, 2);
            GraalHPyContext nativeContext = HPyAsContextNodeGen.getUncached().execute(arguments[0]);
            PythonContext context = nativeContext.getContext();
            Object pythonObject = HPyAsPythonObjectNodeGen.getUncached().execute(nativeContext, arguments[1]);
            Object type = GetClassNode.getUncached().execute(pythonObject);
            PrintWriter stderr = new PrintWriter(context.getStandardErr());
            stderr.println("object type     : " + type);
            stderr.println("object type name: " + GetNameNode.getUncached().execute(type));

            // the most dangerous part
            stderr.println("object repr     : ");
            stderr.flush();
            try {
                Object reprObj = PyObjectCallMethodObjArgs.getUncached().execute(null, context.getBuiltins(), BuiltinNames.REPR, pythonObject);
                stderr.println(CastToJavaStringNode.getUncached().execute(reprObj));
            } catch (PException | CannotCastException e) {
                // errors are ignored at this point
            }
            return 0;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyType extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached GetClassNode getClassNode) throws ArityException {
            checkArity(arguments, 2);
            GraalHPyContext nativeContext = asContextNode.execute(arguments[0]);
            Object object = asPythonObjectNode.execute(nativeContext, arguments[1]);
            return asHandleNode.execute(nativeContext, getClassNode.execute(object));
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyTypeCheck extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                checkArity(arguments, 3);
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object object = asPythonObjectNode.execute(context, arguments[1]);
                Object expectedType = asPythonObjectNode.execute(context, arguments[2]);
                return PInt.intValue(isSubtypeNode.execute(getClassNode.execute(object), expectedType));
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyNewException extends GraalHPyContextFunction {

        private final boolean withDoc;

        public GraalHPyNewException(boolean withDoc) {
            this.withDoc = withDoc;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached PCallHPyFunction callFromStringNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @CachedLibrary(limit = "3") HashingStorageLibrary storageLibrary,
                        @Cached CallNode callTypeConstructorNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached PythonObjectFactory factory,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                int docExtra = withDoc ? 1 : 0;
                checkArity(arguments, 4 + docExtra);
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object nameObj = callFromStringNode.call(context, GraalHPyNativeSymbol.POLYGLOT_FROM_STRING, arguments[1], "utf-8");
                Object doc = withDoc ? fromCharPointerNode.execute(arguments[2]) : null;
                Object base = asPythonObjectNode.execute(context, arguments[2 + docExtra]);
                Object dictObj = asPythonObjectNode.execute(context, arguments[3 + docExtra]);

                String name;
                try {
                    name = castToJavaStringNode.execute(nameObj);
                } catch (CannotCastException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }

                try {
                    int dotIdx = name.indexOf('.');
                    if (dotIdx == -1) {
                        throw raiseNode.raise(SystemError, "name must be module.class");
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
                             * "bad internal call".
                             */
                            throw raiseNode.raise(SystemError, "bad argument to internal function");
                        }
                        dict = (PDict) dictObj;
                        dictStorage = dict.getDictStorage();
                    }

                    if (!storageLibrary.hasKey(dictStorage, SpecialAttributeNames.__MODULE__)) {
                        dictStorage = storageLibrary.setItem(dictStorage, SpecialAttributeNames.__MODULE__, name.substring(0, dotIdx));
                    }

                    if (withDoc) {
                        assert doc != null;
                        dictStorage = storageLibrary.setItem(dictStorage, SpecialAttributeNames.__DOC__, doc);
                    }

                    dict.setDictStorage(dictStorage);

                    PTuple bases;
                    if (base instanceof PTuple) {
                        bases = (PTuple) base;
                    } else {
                        bases = factory.createTuple(new Object[]{base});
                    }

                    Object newExceptionType = callTypeConstructorNode.execute(PythonBuiltinClassType.PythonClass, name.substring(dotIdx + 1), bases, dict);
                    return asHandleNode.execute(context, newExceptionType);
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(context, e);
                    return GraalHPyHandle.NULL_HANDLE;
                }
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyIs extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached IsNode isNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                checkArity(arguments, 3);
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object left = asPythonObjectNode.execute(context, arguments[1]);
                Object right = asPythonObjectNode.execute(context, arguments[2]);
                try {
                    return PInt.intValue(isNode.execute(left, right));
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(context, e);
                    return -1;
                }
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyImportModule extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            checkArity(arguments, 2);
            boolean mustRelease = gil.acquire();
            try {
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                try {
                    String name = castToJavaStringNode.execute(fromCharPointerNode.execute(arguments[1]));
                    return asHandleNode.execute(context, AbstractImportNode.importModule(name));
                } catch (CannotCastException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(context, e);
                    return GraalHPyHandle.NULL_HANDLE;
                }
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyFieldStore extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached PCallHPyFunction callHelperFunctionNode,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached ConditionProfile nullHandleProfile,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            checkArity(arguments, 4);
            boolean mustRelease = gil.acquire();
            try {
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object ownerObject = asPythonObjectNode.execute(context, arguments[1]);
                Object hpyFieldPtr = arguments[2];
                Object referent = asPythonObjectNode.execute(context, arguments[3]);
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
                GraalHPyHandle newHandle = asHandleNode.executeField(context, referent, idx);
                callHelperFunctionNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_SET_FIELD_I, hpyFieldPtr, newHandle);
                return 0;
            } finally {
                gil.release(mustRelease);
            }
        }

        private int assign(PythonObject owner, Object referent, int location) {
            Object[] hpyFields = owner.getHpyFields();
            if (location != 0) {
                assert hpyFields != null;
                hpyFields[location - 1] = referent;
                return location;
            } else {
                int newLocation;
                if (hpyFields == null) {
                    newLocation = 1;
                    hpyFields = new Object[]{referent};
                } else {
                    newLocation = hpyFields.length + 1;
                    hpyFields = PythonUtils.arrayCopyOf(hpyFields, newLocation);
                    hpyFields[newLocation - 1] = referent;
                }
                owner.setHpyFields(hpyFields);
                return newLocation;
            }
        }

    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyFieldLoad extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached("createClassProfile()") ValueProfile fieldTypeProfile,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            checkArity(arguments, 3);
            boolean mustRelease = gil.acquire();
            try {
                GraalHPyContext context = asContextNode.execute(arguments[0]);
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
                    Object owner = asPythonObjectNode.execute(context, arguments[1]);
                    if (owner instanceof PythonObject) {
                        referent = ((PythonObject) owner).getHpyFields()[idx - 1];
                    } else {
                        throw CompilerDirectives.shouldNotReachHere("HPyField owner is not a PythonObject!");
                    }
                }
                return asHandleNode.execute(context, referent);
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyGlobalStore extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached PCallHPyFunction callHelperFunctionNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached("createClassProfile()") ValueProfile typeProfile,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            checkArity(arguments, 3);
            boolean mustRelease = gil.acquire();
            try {
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object hpyGlobalPtr = arguments[1];
                Object value = asPythonObjectNode.execute(context, arguments[2]);
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
                        idx = context.getObjectForHPyGlobal(GraalHPyBoxing.unboxHandle(bits)).getGlobalId();
                    }
                }

                // TODO: (tfel) do not actually allocate the index / free the existing one when
                // value can be stored as tagged handle
                GraalHPyHandle newHandle = asHandleNode.executeGlobal(context, value, idx);
                callHelperFunctionNode.call(context, GraalHPyNativeSymbol.GRAAL_HPY_SET_GLOBAL_I, hpyGlobalPtr, newHandle);
                return 0;
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyGlobalLoad extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached("createClassProfile()") ValueProfile typeProfile,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            checkArity(arguments, 2);
            boolean mustRelease = gil.acquire();
            try {
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object hpyGlobal = typeProfile.profile(arguments[1]);
                if (hpyGlobal instanceof GraalHPyHandle) {
                    // branch profiling with typeProfile
                    GraalHPyHandle h = (GraalHPyHandle) hpyGlobal;
                    return asHandleNode.execute(context, h.getDelegate());
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
                        return asHandleNode.execute(context, context.getObjectForHPyGlobal(GraalHPyBoxing.unboxHandle(bits)).getDelegate());
                    } else {
                        // tagged handles can be returned directly
                        return bits;
                    }
                }
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyLeavePythonExecution extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached GilNode gil) throws ArityException {
            checkArity(arguments, 1);
            PythonContext context = asContextNode.execute(arguments[0]).getContext();
            PThreadState threadState = PThreadState.getThreadState(PythonLanguage.get(gil), context);
            gil.release(context, true);
            return threadState;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyReenterPythonExecution extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
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
    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyContains extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached PySequenceContainsNode containsNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GilNode gil) throws ArityException {
            checkArity(arguments, 3);
            boolean mustRelease = gil.acquire();
            try {
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object container = asPythonObjectNode.execute(context, arguments[1]);
                Object key = asPythonObjectNode.execute(context, arguments[2]);
                try {
                    return containsNode.execute(container, key) ? 1 : 0;
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(context, e);
                    return -1;
                }
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyTypeIsSubtype extends GraalHPyContextFunction {
        @ExportMessage
        int execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asSubtype,
                        @Cached HPyAsPythonObjectNode asBasetype,
                        @Cached IsSubtypeNode isSubtype,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GilNode gil) throws ArityException {
            checkArity(arguments, 3);
            boolean mustRelease = gil.acquire();
            try {
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                try {
                    return isSubtype.execute(asSubtype.execute(context, arguments[1]),
                                    asBasetype.execute(context, arguments[2])) ? 1 : 0;
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(context, e);
                    return -1;
                }
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyTypeGetName extends GraalHPyContextFunction {
        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asType,
                        @Cached GetNameNode getName,
                        @Cached EncodeNativeStringNode encodeNativeStringNode,
                        @Cached GilNode gil) throws ArityException {
            checkArity(arguments, 2);
            boolean mustRelease = gil.acquire();
            try {
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object type = asType.execute(context, arguments[1]);
                String name = getName.execute(type);
                byte[] result = encodeNativeStringNode.execute(StandardCharsets.UTF_8, name, CodecsModuleBuiltins.STRICT);
                return new CByteArrayWrapper(result);
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyDictKeys extends GraalHPyContextFunction {
        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asDict,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached PyDictKeys keysNode,
                        @Cached GilNode gil) throws ArityException {
            checkArity(arguments, 2);
            boolean mustRelease = gil.acquire();
            try {
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object dict = asDict.execute(context, arguments[1]);
                return asHandleNode.execute(context, keysNode.execute((PDict) dict));
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyUnicodeInternFromString extends GraalHPyContextFunction {
        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached InternStringNode internStringNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GilNode gil) throws ArityException {
            checkArity(arguments, 2);
            boolean mustRelease = gil.acquire();
            try {
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
                return asHandleNode.execute(context, interned);
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    // see _HPyCapsule_key in the HPy API
    static final class CapsuleKey {
        private static final byte Pointer = 0;
        private static final byte Name = 1;
        private static final byte Context = 2;
        private static final byte Destructor = 3;
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyCapsuleNew extends GraalHPyContextFunction {
        protected static Object argument2(Object[] arguments) {
            return arguments[2];
        }

        @ExportMessage(limit = "3")
        Object execute(Object[] arguments,
                        @CachedLibrary("argument2(arguments)") InteropLibrary nameLib,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached CastToJavaStringNode castStr,
                        @Cached GilNode gil) throws ArityException {
            checkArity(arguments, 4);
            boolean mustRelease = gil.acquire();
            try {
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                PyCapsule result = new PyCapsule();
                result.setPointer(arguments[1]);
                try {
                    if (!nameLib.isPointer(arguments[2]) || nameLib.asPointer(arguments[2]) != 0) {
                        result.setName(castStr.execute(fromCharPointerNode.execute(arguments[2])));
                    }
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
                result.setDestructor(arguments[3]);
                return asHandleNode.execute(context, result);
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyCapsuleGet extends GraalHPyContextFunction {

        @TruffleBoundary
        private static byte[] getBytes(PyCapsule pyCapsule) {
            return pyCapsule.getName().getBytes();
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asCapsule,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached CastToJavaStringNode castStr,
                        @Cached CastToJavaIntExactNode castInt,
                        @Cached GilNode gil) throws ArityException {
            checkArity(arguments, 4);
            boolean mustRelease = gil.acquire();
            try {
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object capsule = asCapsule.execute(context, arguments[1]);
                if (!(capsule instanceof PyCapsule)) {
                    throw CompilerDirectives.shouldNotReachHere("TODO: raise ValueError");
                }
                PyCapsule pyCapsule = (PyCapsule) capsule;
                String name = castStr.execute(fromCharPointerNode.execute(arguments[3]));
                if (!pyCapsule.getName().equals(name)) {
                    throw CompilerDirectives.shouldNotReachHere("TODO: raise ValueError");
                }
                int key = castInt.execute(arguments[2]);
                Object result;
                switch (key) {
                    case CapsuleKey.Pointer:
                        result = pyCapsule.getPointer();
                        break;
                    case CapsuleKey.Context:
                        result = pyCapsule.getContext();
                        break;
                    case CapsuleKey.Name:
                        result = new CByteArrayWrapper(getBytes(pyCapsule));
                        break;
                    case CapsuleKey.Destructor:
                        result = pyCapsule.getDestructor();
                        break;
                    default:
                        throw CompilerDirectives.shouldNotReachHere("TODO: raise ValueError");
                }
                return result;
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyCapsuleSet extends GraalHPyContextFunction {
        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asCapsule,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached CastToJavaStringNode castStr,
                        @Cached CastToJavaIntExactNode castInt,
                        @Cached GilNode gil) throws ArityException {
            checkArity(arguments, 4);
            boolean mustRelease = gil.acquire();
            try {
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object capsule = asCapsule.execute(context, arguments[1]);
                if (!(capsule instanceof PyCapsule)) {
                    throw CompilerDirectives.shouldNotReachHere("TODO: raise ValueError");
                }
                PyCapsule pyCapsule = (PyCapsule) capsule;
                int key = castInt.execute(arguments[2]);
                switch (key) {
                    case CapsuleKey.Pointer:
                        pyCapsule.setPointer(arguments[3]);
                        break;
                    case CapsuleKey.Context:
                        pyCapsule.setContext(arguments[3]);
                        break;
                    case CapsuleKey.Name:
                        pyCapsule.setName(castStr.execute(fromCharPointerNode.execute(arguments[3])));
                        break;
                    case CapsuleKey.Destructor:
                        pyCapsule.setDestructor(arguments[3]);
                        break;
                    default:
                        throw CompilerDirectives.shouldNotReachHere("TODO: raise ValueError");
                }
                return 0;
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyCapsuleIsValid extends GraalHPyContextFunction {
        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asCapsule,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached CastToJavaStringNode castStr,
                        @Cached GilNode gil) throws ArityException {
            checkArity(arguments, 3);
            boolean mustRelease = gil.acquire();
            try {
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object capsule = asCapsule.execute(context, arguments[1]);
                if (!(capsule instanceof PyCapsule)) {
                    return 0;
                }
                PyCapsule pyCapsule = (PyCapsule) capsule;
                String name = castStr.execute(fromCharPointerNode.execute(arguments[3]));
                if (!pyCapsule.getName().equals(name)) {
                    return 0;
                }
                return 1;
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPySetType extends GraalHPyContextFunction {
        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asObject,
                        @Cached HPyAsPythonObjectNode asType,
                        @CachedLibrary(limit = "3") DynamicObjectLibrary dylib,
                        @Cached GilNode gil) throws ArityException {
            checkArity(arguments, 3);
            boolean mustRelease = gil.acquire();
            try {
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object object = asObject.execute(context, arguments[1]);
                if (!(object instanceof PythonObject)) {
                    return -1;
                }
                Object type = asType.execute(context, arguments[2]);
                if (!(type instanceof PythonAbstractClass)) {
                    return -1;
                }
                ((PythonObject) object).setPythonClass(type, dylib);
                return 0;
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyContextVarNew extends GraalHPyContextFunction {
        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached CastToJavaStringNode castStr,
                        @Cached HPyAsPythonObjectNode asObject,
                        @Cached CallNode callContextvar,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached GilNode gil) throws ArityException {
            checkArity(arguments, 3);
            boolean mustRelease = gil.acquire();
            try {
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                String name = castStr.execute(fromCharPointerNode.execute(arguments[1]));
                Object def = asObject.execute(context, arguments[2]);
                return asHandleNode.execute(context, callContextvar.execute(PythonBuiltinClassType.ContextVar, name, def));
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyContextVarGet extends GraalHPyContextFunction {
        @ExportMessage
        int execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asVar,
                        @Cached HPyAsPythonObjectNode asDef,
                        @Cached PyObjectCallMethodObjArgs callGet,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached PCallHPyFunction callWriteHPyNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GilNode gil) throws ArityException {
            checkArity(arguments, 4);
            boolean mustRelease = gil.acquire();
            try {
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object var = asVar.execute(context, arguments[1]);
                Object def = asDef.execute(context, arguments[2]);
                Object outPtr = arguments[3];
                try {
                    Object result = callGet.execute(null, var, "get", def);
                    callWriteHPyNode.call(context, GRAAL_HPY_WRITE_HPY, outPtr, 0L, asHandleNode.execute(context, result));
                    return 0;
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(context, e);
                    return -1;
                }
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyContextVarSet extends GraalHPyContextFunction {
        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asVar,
                        @Cached HPyAsPythonObjectNode asVal,
                        @Cached PyObjectCallMethodObjArgs callSet,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GilNode gil) throws ArityException {
            checkArity(arguments, 3);
            boolean mustRelease = gil.acquire();
            try {
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object var = asVar.execute(context, arguments[1]);
                Object val = asVal.execute(context, arguments[2]);
                try {
                    Object result = callSet.execute(null, var, "set", val);
                    return asHandleNode.execute(context, result);
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(context, e);
                    return -1;
                }
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyUnicodeFromEncodedObject extends GraalHPyContextFunction {
        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode objNode,
                        @Cached FromCharPointerNode encodingNode,
                        @Cached FromCharPointerNode errorsNode,
                        @Cached PyUnicodeFromEncodedObject libNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GilNode gil) throws ArityException {
            checkArity(arguments, 4);
            boolean mustRelease = gil.acquire();
            try {
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                Object obj = objNode.execute(context, arguments[1]);
                Object encoding = encodingNode.execute(arguments[2]);
                Object errors = errorsNode.execute(arguments[3]);
                try {
                    Object result = libNode.execute(null, obj, encoding, errors);
                    return asHandleNode.execute(context, result);
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(context, e);
                    return GraalHPyHandle.NULL_HANDLE;
                }
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyUnicodeSubstring extends GraalHPyContextFunction {
        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode objNode,
                        @Cached CastToJavaStringNode castToJavaString,
                        @Cached CastToJavaIntExactNode castStart,
                        @Cached CastToJavaIntExactNode castEnd,
                        @Cached StrGetItemNodeWithSlice getSlice,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GilNode gil) throws ArityException {
            checkArity(arguments, 4);
            boolean mustRelease = gil.acquire();
            try {
                GraalHPyContext context = asContextNode.execute(arguments[0]);
                String value = castToJavaString.execute(objNode.execute(context, arguments[1]));
                int start = castStart.execute(arguments[2]);
                int end = castEnd.execute(arguments[3]);
                try {
                    Object result = getSlice.execute(value, new SliceInfo(start, end, 1));
                    return asHandleNode.execute(context, result);
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(context, e);
                    return GraalHPyHandle.NULL_HANDLE;
                }
            } finally {
                gil.release(mustRelease);
            }
        }
    }
}
