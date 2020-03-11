/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PString;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.FunctionMode.CHAR_PTR;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.FunctionMode.INT32;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.FunctionMode.OBJECT;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbols.GRAAL_HPY_GET_M_DOC;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbols.GRAAL_HPY_GET_M_METHODS;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbols.GRAAL_HPY_GET_M_NAME;

import java.nio.charset.StandardCharsets;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject.PInteropGetAttributeNode;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject.PInteropSubscriptAssignNode;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject.PInteropSubscriptNode;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.CastToJavaDoubleNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.FromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.PRaiseNativeNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.EncodeNativeStringNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyAddFunctionNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyAsContextNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyAsHandleNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyAsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyEnsureHandleNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyLongFromLong;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.PCallHPyFunction;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CastToJavaIntNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

public abstract class GraalHPyContextFunctions {

    enum FunctionMode {
        OBJECT,
        CHAR_PTR,
        INT32;
    }

    @ExportLibrary(InteropLibrary.class)
    abstract static class GraalHPyContextFunction implements TruffleObject {

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(@SuppressWarnings("unused") Object[] arguments) {
            CompilerDirectives.transferToInterpreter();
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
            CompilerDirectives.transferToInterpreter();
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
                throw ArityException.create(2, arguments.length);
            }
            GraalHPyContext hpyContext = asContextNode.execute(arguments[0]);
            GraalHPyHandle handle = ensureHandleNode.execute(hpyContext, arguments[1]);
            if (handle.isNative()) {
                try {
                    hpyContext.releaseHPyHandleForObject((int) handle.asPointer());
                } catch (UnsupportedMessageException e) {
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
                        @Cached PCallHPyFunction callGetMNameNode,
                        @Cached PCallHPyFunction callGetMDocNode,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached WriteAttributeToObjectNode writeAttrNode,
                        @Cached WriteAttributeToDynamicObjectNode writeAttrToMethodNode,
                        @Cached HPyAddFunctionNode addFunctionNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached PRaiseNode raiseNode) throws ArityException {
            if (arguments.length != 2) {
                throw ArityException.create(2, arguments.length);
            }
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object moduleDef = arguments[1];

            String mName = castToJavaStringNode.execute(callGetMNameNode.call(context, GRAAL_HPY_GET_M_NAME, moduleDef));
            String mDoc = castToJavaStringNode.execute(callGetMDocNode.call(context, GRAAL_HPY_GET_M_DOC, moduleDef));
            Object methodDefArray = callGetMNameNode.call(context, GRAAL_HPY_GET_M_METHODS, moduleDef);

            if (!interopLibrary.hasArrayElements(methodDefArray)) {
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, "");
            }

            PythonModule module = factory.createPythonModule(mName);

            try {
                long nMethodDef = interopLibrary.getArraySize(methodDefArray);
                for (long i = 0; i < nMethodDef; i++) {
                    Object methodDef = interopLibrary.readArrayElement(methodDefArray, i);
                    PBuiltinFunction fun = addFunctionNode.execute(context, methodDef);
                    PBuiltinMethod method = factory.createBuiltinMethod(module, fun);
                    writeAttrToMethodNode.execute(method.getStorage(), SpecialAttributeNames.__MODULE__, mName);
                    writeAttrNode.execute(module, fun.getName(), method);
                }
            } catch (InteropException e) {
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, "");
            }

            writeAttrNode.execute(module, SpecialAttributeNames.__DOC__, mDoc);

            return asHandleNode.execute(context, module);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyLongFromLong extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached CastToJavaLongNode castToJavaLongNode,
                        @Cached HPyLongFromLong fromLongNode) throws ArityException {
            if (arguments.length != 2) {
                throw ArityException.create(2, arguments.length);
            }
            long left = castToJavaLongNode.execute(arguments[1]);

            return fromLongNode.execute(left, true);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyLongFromUnsignedLongLong extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached CastToJavaLongNode castToJavaLongNode,
                        @Cached HPyLongFromLong fromLongNode) throws ArityException {
            if (arguments.length != 2) {
                throw ArityException.create(2, arguments.length);
            }
            long left = castToJavaLongNode.execute(arguments[1]);
            return fromLongNode.execute(left, false);
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
                        @Cached PInteropSubscriptAssignNode setItemNode,
                        @Cached("createClassProfile()") ValueProfile profile,
                        @Cached PRaiseNativeNode raiseNode) throws ArityException {
            if (arguments.length != 4) {
                throw ArityException.create(4, arguments.length);
            }
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object left = profile.profile(dictAsPythonObjectNode.execute(context, arguments[1]));
            if (!PGuards.isDict(left)) {
                return raiseNode.raiseIntWithoutFrame(-1, SystemError, "bad internal call");
            }
            PDict dict = (PDict) left;
            Object key = keyAsPythonObjectNode.execute(context, arguments[2]);
            Object value = valueAsPythonObjectNode.execute(context, arguments[3]);
            try {
                setItemNode.execute(dict, key, value);
                return 0;
            } catch (UnsupportedMessageException e) {
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
                        @Cached HashingStorageNodes.GetItemInteropNode getItemNode,
                        @Cached("createClassProfile()") ValueProfile profile,
                        @Cached PRaiseNativeNode raiseNode,
                        @Cached HPyAsHandleNode asHandleNode) throws ArityException {
            if (arguments.length != 3) {
                throw ArityException.create(3, arguments.length);
            }
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object left = profile.profile(dictAsPythonObjectNode.execute(context, arguments[1]));
            if (!PGuards.isDict(left)) {
                return raiseNode.raiseIntWithoutFrame(-1, SystemError, "bad internal call");
            }
            PDict dict = (PDict) left;
            Object key = keyAsPythonObjectNode.execute(context, arguments[2]);
            try {
                return asHandleNode.execute(getItemNode.executeWithGlobalState(dict.getDictStorage(), key));
            } catch (PException e) {
                return context.getNullHandle();
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyListNew extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached CastToJavaIntNode castToJavaIntNode,
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
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached PRaiseNativeNode raiseNode) throws ArityException {
            if (arguments.length != 3) {
                throw ArityException.create(3, arguments.length);
            }
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object left = listAsPythonObjectNode.execute(context, arguments[1]);
            if (!PGuards.isList(left)) {
                return raiseNode.raiseIntWithoutFrame(-1, SystemError, "bad internal call");
            }
            PList list = (PList) left;
            Object value = valueAsPythonObjectNode.execute(context, arguments[2]);
            Object attrAppend = lookupAppendNode.execute(list, "append");
            if (attrAppend == PNone.NO_VALUE) {
                return raiseNode.raiseIntWithoutFrame(-1, SystemError, "list does not have attribute 'append'");
            }
            try {
                callAppendNode.executeObject(null, attrAppend, list, value);
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(null, e);
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
                        @Cached IsSubtypeNode isSubtypeNode) throws ArityException {
            if (arguments.length != 2) {
                throw ArityException.create(2, arguments.length);
            }
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object object = asPythonObjectNode.execute(context, arguments[1]);
            PythonAbstractClass klass = getClassNode.execute(object);
            return isSubtypeNode.execute(klass, PythonBuiltinClassType.PBytes);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyErrSetString extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNode) throws ArityException {
            if (arguments.length != 3) {
                throw ArityException.create(3, arguments.length);
            }
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object errTypeObj = asPythonObjectNode.execute(context, arguments[1]);
            if (!(PGuards.isClass(errTypeObj) && isSubtypeNode.execute((PythonAbstractClass) errTypeObj, PBaseException))) {
                return raiseNode.raiseIntWithoutFrame(-1, SystemError, "exception %s not a BaseException subclass", errTypeObj);
            }
            // the cast is now guaranteed because it is a subtype of PBaseException and there it is
            // a type
            return raiseNode.raiseIntWithoutFrame(0, (LazyPythonClass) errTypeObj, "exception %s not a BaseException subclass", errTypeObj);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyErrOccurred extends GraalHPyContextFunction {

        @ExportMessage
        int execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNode) throws ArityException {
            if (arguments.length != 1) {
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
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            if (arguments.length != 2) {
                throw ArityException.create(2, arguments.length);
            }
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object object = asPythonObjectNode.execute(context, arguments[1]);
            try {
                Object result = encodeNativeStringNode.execute(StandardCharsets.UTF_8, object, "strict");
                return resultAsHandleNode.execute(context, result);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(null, e);
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
                        @Cached CastToJavaLongNode castToJavaLongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached("createBinaryProfile()") ConditionProfile profile) throws ArityException {
            if (arguments.length != 3) {
                throw ArityException.create(3, arguments.length);
            }
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            long length = castToJavaLongNode.execute(arguments[2]);
            try {
                // TODO(fa) provide element size, length, and encoding
                return resultAsHandleNode.execute(context, fromCharPointerNode.execute(arguments[1]));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(null, e);
                return context.getNullHandle();
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyAsPyObject extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsHandleNode asHandleNode,
                        @Cached ToNewRefNode toPyObjectPointerNode) throws ArityException {
            if (arguments.length != 2) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(2, arguments.length);
            }
            GraalHPyContext hPyContext = asContextNode.execute(arguments[0]);
            return toPyObjectPointerNode.execute(hPyContext.getContext().getCApiContext(), asHandleNode.execute(hPyContext, arguments[1]));
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyBytesAsString extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached PRaiseNativeNode raiseNode) throws ArityException {
            if (arguments.length != 2) {
                throw ArityException.create(2, arguments.length);
            }
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object object = asPythonObjectNode.execute(context, arguments[1]);
            if (object instanceof PBytes) {
                return new PySequenceArrayWrapper(object, 1);
            }
            return raiseNode.raiseIntWithoutFrame(-1, TypeError, "expected bytes, %p found", object);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyBytesGetSize extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached HPyAsPythonObjectNode asPythonObjectNode,
                        @Cached SequenceNodes.LenNode lenNode,
                        @Cached PRaiseNativeNode raiseNode) throws ArityException {
            if (arguments.length != 2) {
                throw ArityException.create(2, arguments.length);
            }
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object object = asPythonObjectNode.execute(context, arguments[1]);
            if (object instanceof PBytes) {
                return lenNode.execute((PSequence) object);
            }
            return raiseNode.raiseIntWithoutFrame(-1, TypeError, "expected bytes, %p found", object);
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
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
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
                return asHandleNode.execute(context, getAttributeNode.execute(receiver, key));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(null, e);
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
                        @Cached GetLazyClassNode getKeyClassNode,
                        @Cached IsBuiltinClassProfile isPStringProfile,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached LookupInheritedAttributeNode.Dynamic lookupSetAttrNode,
                        @Cached CallTernaryMethodNode callSetAttrNode,
                        @Cached("createBinaryProfile()") ConditionProfile profile,
                        @Cached PRaiseNativeNode raiseNativeNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
            if (arguments.length != 4) {
                throw ArityException.create(4, arguments.length);
            }

            GraalHPyContext context = asContextNode.execute(arguments[0]);
            Object receiver = receiverAsPythonObjectNode.execute(context, arguments[1]);
            Object key;
            switch (mode) {
                case OBJECT:
                    key = keyAsPythonObjectNode.execute(context, arguments[2]);
                    if (!isPStringProfile.profileClass(getKeyClassNode.execute(key), PString)) {
                        return raiseNativeNode.raiseInt(null, -1, TypeError, "attribute name must be string, not '%p'", key);
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
                    callSetAttrNode.execute(attrGetattribute, receiver, key, value);
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(null, e);
                    return -1;
                }
            } else {
                return raiseNativeNode.raiseInt(null, -1, TypeError, "'%p' object has no attributes", receiver);
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
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) throws ArityException {
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
                transformExceptionToNativeNode.execute(null, e);
                return context.getNullHandle();
            }
        }
    }

}
