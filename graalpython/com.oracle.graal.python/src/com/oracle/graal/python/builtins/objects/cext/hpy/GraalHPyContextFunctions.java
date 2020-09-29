/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBaseException;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.FunctionMode.CHAR_PTR;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.FunctionMode.INT32;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.FunctionMode.OBJECT;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.OBJECT_HPY_NATIVE_SPACE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.TYPE_HPY_BASICSIZE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbols.GRAAL_HPY_DEF_GET_KIND;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbols.GRAAL_HPY_DEF_GET_METH;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbols.GRAAL_HPY_FROM_HPY_MODULE_DEF;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbols.GRAAL_HPY_FROM_HPY_TYPE_SPEC;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbols.GRAAL_HPY_FROM_HPY_TYPE_SPEC_PARAM_ARRAY;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbols.GRAAL_HPY_MODULE_GET_DEFINES;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbols.GRAAL_HPY_MODULE_GET_LEGACY_METHODS;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbols.GRAAL_HPY_WRITE_PTR;

import java.nio.charset.StandardCharsets;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject.PInteropGetAttributeNode;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject.PInteropSubscriptAssignNode;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject.PInteropSubscriptNode;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.AsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.CastToJavaDoubleNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.FromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ResolveHandleNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeReferenceCache.ResolveNativeReferenceNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.AsNativeDoubleNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.ConvertPIntToPrimitiveNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.EncodeNativeStringNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.UnicodeFromWcharNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.ConversionNodeSupplier;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyAddLegacyMethodNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyAsContextNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyAsHandleNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyAsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyCreateFunctionNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyCreateTypeFromSpecNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyEnsureHandleNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyLongFromLong;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyRaiseNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyTransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.PCallHPyFunction;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntLossyNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

@SuppressWarnings("static-method")
public abstract class GraalHPyContextFunctions {

    enum FunctionMode {
        OBJECT,
        CHAR_PTR,
        INT32
    }

    @ExportLibrary(InteropLibrary.class)
    abstract static class GraalHPyContextFunction implements TruffleObject {

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(@SuppressWarnings("unused") Object[] arguments) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("should not reach");
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
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyDup extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyEnsureHandleNode ensureHandleNode) throws ArityException {
            if (arguments.length != 2) {
                throw ArityException.create(2, arguments.length);
            }
            GraalHPyContext hpyContext = asContextNode.execute(arguments[0]);
            GraalHPyHandle handle = ensureHandleNode.execute(hpyContext, arguments[1]);
            return handle.copy();
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyClose extends GraalHPyContextFunction {
        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyEnsureHandleNode ensureHandleNode) throws ArityException {
            if (arguments.length != 2) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(2, arguments.length);
            }
            GraalHPyContext hpyContext = asContextNode.execute(arguments[0]);
            GraalHPyHandle handle = ensureHandleNode.execute(hpyContext, arguments[1]);
            if (handle.isNative()) {
                try {
                    hpyContext.releaseHPyHandleForObject((int) handle.asPointer());
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException("trying to release non-native handle that claims to be native");
                }
            }
            // nothing to do if the handle never got 'toNative'
            return 0;
        }
    }

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
                        @Cached HPyAddLegacyMethodNode addLegacyMethodNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached PRaiseNode raiseNode) throws ArityException {
            if (arguments.length != 2) {
                throw ArityException.create(2, arguments.length);
            }
            GraalHPyContext context = asContextNode.execute(arguments[0]);

            // call to type the pointer
            Object moduleDef = callFromHPyModuleDefNode.call(context, GRAAL_HPY_FROM_HPY_MODULE_DEF, arguments[1]);

            assert ptrLib.hasMembers(moduleDef);

            try {
                String mName = castToJavaStringNode.execute(fromCharPointerNode.execute(ptrLib.readMember(moduleDef, "m_name")));

                // do not eagerly read the doc string; this turned out to be unnecessarily expensive
                Object mDoc = fromCharPointerNode.execute(ptrLib.readMember(moduleDef, "m_doc"));

                // create the module object
                PythonModule module = factory.createPythonModule(mName);

                // process HPy methods
                {
                    Object moduleDefines = callGetterNode.call(context, GRAAL_HPY_MODULE_GET_DEFINES, moduleDef);
                    if (!ptrLib.hasArrayElements(moduleDefines)) {
                        throw raiseNode.raise(PythonBuiltinClassType.SystemError, "field 'defines' did not return an array");
                    }

                    long nModuleDefines = ptrLib.getArraySize(moduleDefines);
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
                }

                // process legacy methods
                {
                    Object legacyMethods = callGetterNode.call(context, GRAAL_HPY_MODULE_GET_LEGACY_METHODS, moduleDef);
                    // the field 'legacy_methods' may be 'NULL'
                    if (!ptrLib.isNull(legacyMethods)) {
                        if (!ptrLib.hasArrayElements(legacyMethods)) {
                            throw raiseNode.raise(PythonBuiltinClassType.SystemError, "field 'legacyMethods' did not return an array");
                        }

                        long nLegacyMethods = ptrLib.getArraySize(legacyMethods);
                        for (long i = 0; i < nLegacyMethods; i++) {
                            Object legacyMethod = ptrLib.readArrayElement(legacyMethods, i);

                            PBuiltinFunction fun = addLegacyMethodNode.execute(context, legacyMethod);
                            PBuiltinMethod method = factory.createBuiltinMethod(module, fun);
                            writeAttrToMethodNode.execute(method.getStorage(), SpecialAttributeNames.__MODULE__, mName);
                            writeAttrNode.execute(module, fun.getName(), method);
                        }
                    }
                }

                writeAttrNode.execute(module, SpecialAttributeNames.__DOC__, mDoc);

                return asHandleNode.execute(context, module);
            } catch (InteropException e) {
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, "");
            }
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
                        @Cached CastToJavaLongExactNode castToJavaLongNode,
                        @Cached HPyLongFromLong fromLongNode) throws ArityException {
            if (arguments.length != 2) {
                throw ArityException.create(2, arguments.length);
            }
            long left = castToJavaLongNode.execute(arguments[1]);

            return fromLongNode.execute(left, signed);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyLongAsLong extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached CastToJavaLongExactNode castToJavaLongNode,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached ConvertPIntToPrimitiveNode convertPIntToPrimitiveNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            if (arguments.length != 2) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(2, arguments.length);
            }
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object object = asPythonObjectNode.execute(context, arguments[1]);
            try {
                return castToJavaLongNode.execute(convertPIntToPrimitiveNode.execute(null, object, 1, Long.BYTES));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(context, e);
                return -1L;
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyNumberAdd extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode leftAsPythonObjectNode,
                        @Cached HPyAsPythonObjectNode rightAsPythonObjectNode,
                        @Cached HPyAsHandleNode resultAsHandleNode,
                        @Cached LookupInheritedAttributeNode.Dynamic lookupAddNode,
                        @Cached CallBinaryMethodNode callBinaryMethodNode,
                        @Cached PRaiseNode raiseNode) throws ArityException {
            if (arguments.length != 3) {
                throw ArityException.create(3, arguments.length);
            }
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object left = leftAsPythonObjectNode.execute(context, arguments[1]);
            Object right = rightAsPythonObjectNode.execute(context, arguments[2]);

            Object addAttr = lookupAddNode.execute(left, SpecialMethodNames.__ADD__);
            if (addAttr != PNone.NO_VALUE) {
                return resultAsHandleNode.execute(context, callBinaryMethodNode.executeObject(null, addAttr, left, right));
            }
            throw raiseNode.raise(TypeError, "object of class %p has no __add__", left);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyDictNew extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached PythonObjectFactory factory,
                        @Cached HPyAsHandleNode asHandleNode) throws ArityException {
            if (arguments.length != 1) {
                throw ArityException.create(1, arguments.length);
            }
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
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            if (arguments.length != 4) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(4, arguments.length);
            }
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
                        @Cached HPyAsHandleNode asHandleNode) throws ArityException {
            if (arguments.length != 3) {
                throw ArityException.create(3, arguments.length);
            }
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object left = profile.profile(dictAsPythonObjectNode.execute(context, arguments[1]));
            if (!PGuards.isDict(left)) {
                return raiseNode.raiseWithoutFrame(context, context.getNullHandle(), SystemError, "bad internal call");
            }
            PDict dict = (PDict) left;
            Object key = keyAsPythonObjectNode.execute(context, arguments[2]);
            try {
                Object item = hashingStorageLibrary.getItem(dict.getDictStorage(), key);
                if (item != null) {
                    return asHandleNode.execute(item);
                }
                return context.getNullHandle();
            } catch (PException e) {
                // This function has the same (odd) error behavior as PyDict_GetItem: If an error
                // occurred, the error is cleared and NULL is returned.
                return context.getNullHandle();
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
            if (arguments.length != 2) {
                throw ArityException.create(2, arguments.length);
            }
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
                        @Cached HPyRaiseNode raiseNode) throws ArityException {
            if (arguments.length != 3) {
                throw ArityException.create(3, arguments.length);
            }
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
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyFloatFromDouble extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached CastToJavaDoubleNode castToJavaDoubleNode,
                        @Cached HPyAsHandleNode asHandleNode) throws ArityException {
            if (arguments.length != 2) {
                throw ArityException.create(2, arguments.length);
            }
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
                        @CachedLibrary(limit = "3") PythonObjectLibrary lib) throws ArityException {
            if (arguments.length != 2) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(2, arguments.length);
            }
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            return lib.asJavaDouble(asPythonObjectNode.execute(context, arguments[1]));
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
                        @CachedLibrary(limit = "1") PythonObjectLibrary lib,
                        @Cached IsSubtypeNode isSubtypeNode) throws ArityException {
            if (arguments.length != 2) {
                throw ArityException.create(2, arguments.length);
            }
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object object = asPythonObjectNode.execute(context, arguments[1]);
            return isSubtypeNode.execute(lib.getLazyPythonClass(object), expectedType);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyErrSetString extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @CachedLibrary(limit = "1") InteropLibrary interopLib,
                        @Cached HPyRaiseNode raiseNode) throws ArityException {
            if (arguments.length != 3) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(3, arguments.length);
            }
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object errTypeObj = asPythonObjectNode.execute(context, arguments[1]);
            if (!(PGuards.isClass(errTypeObj, interopLib) && isSubtypeNode.execute(errTypeObj, PBaseException))) {
                return raiseNode.raiseIntWithoutFrame(context, -1, SystemError, "exception %s not a BaseException subclass", errTypeObj);
            }
            // the cast is now guaranteed because it is a subtype of PBaseException and there it is
            // a type
            Object valueObj = fromCharPointerNode.execute(arguments[2]);
            try {
                String errorMessage = castToJavaStringNode.execute(valueObj);
                return raiseNode.raiseIntWithoutFrame(context, 0, errTypeObj, errorMessage);
            } catch (CannotCastException e) {
                return raiseNode.raiseIntWithoutFrame(context, -1, TypeError, "exception value is not a valid string");
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyErrOccurred extends GraalHPyContextFunction {

        @ExportMessage
        int execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode) throws ArityException {
            if (arguments.length != 1) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(1, arguments.length);
            }
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            return context.getContext().getCurrentException() != null ? 1 : 0;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyUnicodeAsUTF8String extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached HPyAsHandleNode resultAsHandleNode,
                        @Cached EncodeNativeStringNode encodeNativeStringNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            if (arguments.length != 2) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(2, arguments.length);
            }
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object unicodeObject = asPythonObjectNode.execute(context, arguments[1]);
            try {
                Object result = encodeNativeStringNode.execute(StandardCharsets.UTF_8, unicodeObject, "strict");
                return resultAsHandleNode.execute(context, result);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(context, e);
                return context.getNullHandle();
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyUnicodeFromString extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached HPyAsHandleNode asHandleNode) throws ArityException {
            if (arguments.length != 2) {
                throw ArityException.create(2, arguments.length);
            }
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            try {
                // TODO(fa) provide encoding (utf8)
                Object str = fromCharPointerNode.execute(arguments[1]);
                return asHandleNode.execute(context, str);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(context, e);
                return context.getNullHandle();
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
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            if (arguments.length != 3) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(3, arguments.length);
            }
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            long len = castToJavaLongNode.execute(arguments[2]);
            // Note: 'len' may be -1; in this case, function GRAAL_HPY_I8_FROM_WCHAR_ARRAY will
            // use 'wcslen' to determine the C array's length.
            long byteLen = len == -1 ? -1 : len * context.getWcharSize();
            Object dataArray = callFromWcharArrayNode.call(context, GraalHPyNativeSymbols.GRAAL_HPY_I8_FROM_WCHAR_ARRAY, arguments[1], byteLen);
            try {
                // UnicodeFromWcharNode always expects an i8 array
                return resultAsHandleNode.execute(context, unicodeFromWcharNode.execute(dataArray, context.getWcharSize()));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(context, e);
                return context.getNullHandle();
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyAsPyObject extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode handleAsPythonObjectNode,
                        @Cached ToNewRefNode toPyObjectPointerNode) throws ArityException {
            if (arguments.length != 2) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(2, arguments.length);
            }
            GraalHPyContext hPyContext = asContextNode.execute(arguments[0]);
            Object object = handleAsPythonObjectNode.execute(hPyContext, arguments[1]);
            return toPyObjectPointerNode.execute(hPyContext.getContext().getCApiContext(), object);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyBytesAsString extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached HPyRaiseNode raiseNode) throws ArityException {
            if (arguments.length != 2) {
                throw ArityException.create(2, arguments.length);
            }
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
                        @Cached HPyRaiseNode raiseNode) throws ArityException {
            if (arguments.length != 2) {
                throw ArityException.create(2, arguments.length);
            }
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object object = asPythonObjectNode.execute(context, arguments[1]);
            if (object instanceof PBytes) {
                return lenNode.execute((PSequence) object);
            }
            return raiseNode.raiseIntWithoutFrame(context, -1, TypeError, "expected bytes, %p found", object);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyIsTrue extends GraalHPyContextFunction {

        @ExportMessage
        int execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @CachedLibrary(limit = "3") PythonObjectLibrary lib) throws ArityException {
            if (arguments.length != 2) {
                throw ArityException.create(2, arguments.length);
            }
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object object = asPythonObjectNode.execute(context, arguments[1]);
            return lib.isTrue(object) ? 1 : 0;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyGetAttr extends GraalHPyContextFunction {

        private final FunctionMode mode;

        public GraalHPyGetAttr(FunctionMode mode) {
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
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            if (arguments.length != 3) {
                throw ArityException.create(3, arguments.length);
            }
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
                return context.getNullHandle();
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
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            if (arguments.length != 3) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(3, arguments.length);
            }

            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object typeSpec = callHelperFunctionNode.call(context, GRAAL_HPY_FROM_HPY_TYPE_SPEC, arguments[1]);
            Object typeSpecParamArray = callHelperFunctionNode.call(context, GRAAL_HPY_FROM_HPY_TYPE_SPEC_PARAM_ARRAY, arguments[2]);

            try {
                Object newType = createTypeFromSpecNode.execute(context, typeSpec, typeSpecParamArray);
                assert PGuards.isClass(newType, InteropLibrary.getUncached()) : "Object created from type spec is not a type";
                return asHandleNode.execute(context, newType);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(context, e);
                return context.getNullHandle();
            }
        }

    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyHasAttr extends GraalHPyContextFunction {

        private final FunctionMode mode;

        public GraalHPyHasAttr(FunctionMode mode) {
            checkMode(mode, OBJECT, CHAR_PTR);
            this.mode = mode;
        }

        @ExportMessage
        int execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode receiverAsPythonObjectNode,
                        @Cached HPyAsPythonObjectNode keyAsPythonObjectNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached PInteropGetAttributeNode getAttributeNode) throws ArityException {
            if (arguments.length != 3) {
                throw ArityException.create(3, arguments.length);
            }
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
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPySetAttr extends GraalHPyContextFunction {

        private final FunctionMode mode;

        public GraalHPySetAttr(FunctionMode mode) {
            checkMode(mode, OBJECT, CHAR_PTR);
            this.mode = mode;
        }

        @ExportMessage
        int execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode receiverAsPythonObjectNode,
                        @Cached HPyAsPythonObjectNode keyAsPythonObjectNode,
                        @Cached HPyAsPythonObjectNode valueAsPythonObjectNode,
                        @CachedLibrary(limit = "1") PythonObjectLibrary lib,
                        @Cached IsBuiltinClassProfile isPStringProfile,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached LookupInheritedAttributeNode.Dynamic lookupSetAttrNode,
                        @Cached CallTernaryMethodNode callSetAttrNode,
                        @Cached("createBinaryProfile()") ConditionProfile profile,
                        @Cached HPyRaiseNode raiseNativeNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            if (arguments.length != 4) {
                throw ArityException.create(4, arguments.length);
            }

            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object receiver = receiverAsPythonObjectNode.execute(context, arguments[1]);
            Object key;
            switch (mode) {
                case OBJECT:
                    key = keyAsPythonObjectNode.execute(context, arguments[2]);
                    if (!isPStringProfile.profileClass(lib.getLazyPythonClass(key), PythonBuiltinClassType.PString)) {
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
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyGetItem extends GraalHPyContextFunction {

        private final FunctionMode mode;

        public GraalHPyGetItem(FunctionMode mode) {
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
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            if (arguments.length != 3) {
                throw ArityException.create(3, arguments.length);
            }
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
                return context.getNullHandle();
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPySetItem extends GraalHPyContextFunction {

        private final FunctionMode mode;

        public GraalHPySetItem(FunctionMode mode) {
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
                        @Cached HPyRaiseNode raiseNativeNode) throws ArityException {
            if (arguments.length != 4) {
                throw ArityException.create(4, arguments.length);
            }
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
                        @Cached HPyAsHandleNode asHandleNode) throws ArityException {
            if (arguments.length != 2) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(2, arguments.length);
            }
            // IMPORTANT: this is not stealing the reference. The CPython implementation actually
            // increases the reference count by 1.
            Object resolvedPyObject = asPythonObjectNode.execute(resolveNativeReferenceNode.execute(resolveHandleNode.execute(arguments[1]), false));
            return asHandleNode.execute(asContextNode.execute(arguments[0]), resolvedPyObject);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyNew extends GraalHPyContextFunction {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(GraalHPyNew.class);

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached ReadAttributeFromObjectNode readBasicsizeNode,
                        @Cached IsTypeNode isTypeNode,
                        @Cached HPyRaiseNode raiseNode,
                        @Cached PythonObjectFactory factory,
                        @Cached WriteAttributeToObjectNode writeNativeSpaceNode,
                        @Cached PCallHPyFunction callMallocNode,
                        @Cached PCallHPyFunction callWriteDataNode,
                        @Cached HPyAsHandleNode asHandleNode) throws ArityException {
            if (arguments.length != 3) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(3, arguments.length);
            }
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object type = asPythonObjectNode.execute(context, arguments[1]);
            Object dataOutVar = arguments[2];

            // check if agrument is actually a type
            if (!isTypeNode.execute(type)) {
                return raiseNode.raiseWithoutFrame(context, context.getNullHandle(), TypeError, "HPy_New arg 1 must be a type");
            }

            // create the managed Python object
            PythonObject pythonObject = factory.createPythonObject(type);

            // allocate native space
            Object attrObj = readBasicsizeNode.execute(type, TYPE_HPY_BASICSIZE);
            if (attrObj != PNone.NO_VALUE) {
                // we fully control this attribute; if it is there, it's always a long
                long basicsize = (long) attrObj;
                Object dataPtr = callMallocNode.call(context, GraalHPyNativeSymbols.GRAAL_HPY_CALLOC, basicsize, 1L);
                writeNativeSpaceNode.execute(pythonObject, OBJECT_HPY_NATIVE_SPACE, dataPtr);

                // write data pointer to out var
                callWriteDataNode.call(context, GRAAL_HPY_WRITE_PTR, dataOutVar, 0L, dataPtr);

                LOGGER.fine(() -> String.format("Allocated HPy object with native space of size %d at %s", basicsize, dataPtr));
                // TODO(fa): add memory tracing

                // TODO(fa): Here we allocated some native memory but we do never free it. We need
                // to create a weakref to the Python object and if it dies, we need to free the
                // native space.
            }
            return asHandleNode.execute(pythonObject);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyCast extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached ReadAttributeFromObjectNode readAttributeFromObjectNode) throws ArityException {
            if (arguments.length != 2) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(2, arguments.length);
            }
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object receiver = asPythonObjectNode.execute(context, arguments[1]);

            // we can also just return NO_VALUE since that will be interpreter as NULL
            return readAttributeFromObjectNode.execute(receiver, OBJECT_HPY_NATIVE_SPACE);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyTypeGenericNew extends GraalHPyContextFunction {

        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(GraalHPyTypeGenericNew.class);

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached ReadAttributeFromObjectNode readBasicsizeNode,
                        @Cached PythonObjectFactory factory,
                        @Cached WriteAttributeToObjectNode writeNativeSpaceNode,
                        @Cached PCallHPyFunction callMallocNode,
                        @Cached HPyAsHandleNode asHandleNode) throws ArityException {
            if (arguments.length != 5) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(5, arguments.length);
            }
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object type = asPythonObjectNode.execute(context, arguments[1]);

            // create the managed Python object
            PythonObject pythonObject = factory.createPythonObject(type);

            // allocate native space
            Object attrObj = readBasicsizeNode.execute(type, TYPE_HPY_BASICSIZE);
            if (attrObj != PNone.NO_VALUE) {
                // we fully control this attribute; if it is there, it's always a long
                long basicsize = (long) attrObj;
                Object dataPtr = callMallocNode.call(context, GraalHPyNativeSymbols.GRAAL_HPY_CALLOC, basicsize, 1L);
                writeNativeSpaceNode.execute(pythonObject, OBJECT_HPY_NATIVE_SPACE, dataPtr);

                LOGGER.fine(() -> String.format("Allocated HPy object with native space of size %d at %s", basicsize, dataPtr));
                // TODO(fa): add memory tracing
            }
            return asHandleNode.execute(pythonObject);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyCallBuiltinFunction extends GraalHPyContextFunction {

        private final String key;
        private final int nPythonArguments;

        private ConversionNodeSupplier toNativeNodeSupplier;

        public GraalHPyCallBuiltinFunction(String key, int nPythonArguments) {
            this.key = key;
            assert nPythonArguments >= 0 : "number of arguments cannot be negative";
            this.nPythonArguments = nPythonArguments;
            this.toNativeNodeSupplier = GraalHPyConversionNodeSupplier.HANDLE;
        }

        public GraalHPyCallBuiltinFunction(String key, int nPythonArguments, ConversionNodeSupplier toNativeNodeSupplier) {
            this.key = key;
            assert nPythonArguments >= 0 : "number of arguments cannot be negative";
            this.nPythonArguments = nPythonArguments;
            this.toNativeNodeSupplier = toNativeNodeSupplier != null ? toNativeNodeSupplier : GraalHPyConversionNodeSupplier.HANDLE;
        }

        @ExportMessage
        static class Execute {

            @Specialization
            static Object execute(GraalHPyCallBuiltinFunction receiver, Object[] arguments,
                            @Cached HPyAsContextNode asContextNode,
                            @Cached HPyAsPythonObjectNode asPythonObjectNode,
                            @Cached ReadAttributeFromObjectNode readAttributeFromObjectNode,
                            @CachedLibrary(limit = "1") PythonObjectLibrary lib,
                            @Cached(value = "createToNativeNode(receiver)", uncached = "getUncachedToNativeNode(receiver)") CExtToNativeNode toNativeNode,
                            @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
                if (arguments.length != receiver.nPythonArguments + 1) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw ArityException.create(receiver.nPythonArguments + 1, arguments.length);
                }
                GraalHPyContext nativeContext = asContextNode.execute(arguments[0]);
                Object[] pythonArguments = new Object[receiver.nPythonArguments];
                for (int i = 0; i < pythonArguments.length; i++) {
                    pythonArguments[i] = asPythonObjectNode.execute(nativeContext, arguments[i + 1]);
                }
                try {
                    Object builtinFunction = readAttributeFromObjectNode.execute(nativeContext.getContext().getBuiltins(), receiver.key);
                    return toNativeNode.execute(nativeContext, lib.callObjectWithState(builtinFunction, null, pythonArguments));
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(nativeContext, e);
                    return nativeContext.getNullHandle();
                }
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
                        @CachedLibrary(limit = "1") PythonObjectLibrary lib,
                        @CachedLibrary(limit = "1") PythonObjectLibrary resultLib,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException, UnsupportedTypeException {
            if (arguments.length != 4) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(4, arguments.length);
            }
            GraalHPyContext nativeContext = asContextNode.execute(arguments[0]);
            Object receiver = asPythonObjectNode.execute(nativeContext, arguments[1]);
            Object[] pythonArguments = new Object[2];
            pythonArguments[0] = asPythonObjectNode.execute(nativeContext, arguments[2]);
            try {
                pythonArguments[1] = SpecialMethodNames.getCompareOpString(castToJavaIntExactNode.execute(arguments[3]));
            } catch (CannotCastException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw UnsupportedTypeException.create(arguments, "4th argument must fit into Java int");
            }
            try {
                Object result = lib.lookupAndCallSpecialMethodWithState(receiver, null, SpecialMethodNames.RICHCMP, pythonArguments);
                return returnPrimitive ? PInt.intValue(resultLib.isTrue(result)) : asHandleNode.execute(nativeContext, result);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(nativeContext, e);
                return returnPrimitive ? 0 : nativeContext.getNullHandle();
            }
        }
    }
}
