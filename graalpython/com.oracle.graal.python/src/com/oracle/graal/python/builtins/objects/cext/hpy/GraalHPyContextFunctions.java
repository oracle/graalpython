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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbols.GRAAL_HPY_ALLOCATE_OUTVAR;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbols.GRAAL_HPY_FROM_HPY_ARRAY;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbols.GRAAL_HPY_FROM_STRING;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbols.GRAAL_HPY_GET_M_DOC;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbols.GRAAL_HPY_GET_M_METHODS;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbols.GRAAL_HPY_GET_M_NAME;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject.PInteropGetAttributeNode;
import com.oracle.graal.python.builtins.objects.cext.common.VaListWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtParseArgumentsNode.ParseTupleAndKeywordsNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyContextMembers;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyAddFunctionNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyAsContextNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyAsHandleNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyAsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyEnsureHandleNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyLongFromLong;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.PCallHPyFunction;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
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
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class GraalHPyContextFunctions {

    @ExportLibrary(InteropLibrary.class)
    abstract static class GraalHPyContextFunction implements TruffleObject {

        private HPyContextMembers fun;

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(@SuppressWarnings("unused") Object[] arguments) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("should not reach");
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
        @ExportMessage(limit = "1")
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
    public static final class GraalHPyParseArgs extends GraalHPyContextFunction {

        /**
         * {@code int (*ctx_Arg_Parse)(HPyContext ctx, HPy *args, HPy_ssize_t nargs, const char *fmt, va_list _vl);}
         */
        @ExportMessage(limit = "1")
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached PCallHPyFunction callFromHPyArrayNode,
                        @Cached PCallHPyFunction callFromStringNode,
                        @Cached PCallHPyFunction callMallocOutVarPtr,
                        @CachedLibrary(limit = "1") InteropLibrary argsArrayLib,
                        @CachedLibrary(limit = "1") InteropLibrary arrayWrapperLib,
                        @Cached HPyAsPythonObjectNode argAsHandleNode,
                        @Cached("createBinaryProfile()") ConditionProfile functionNameProfile,
                        @Cached CastToJavaStringNode castToStringNode,
                        @Cached CastToJavaIntNode castToJavaLongNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode raiseNode,
                        @Cached ParseTupleAndKeywordsNode parseTupleAndKeywordsNode) throws ArityException {
            if (arguments.length != 5) {
                throw ArityException.create(5, arguments.length);
            }
            try {
                GraalHPyContext nativeContext = asContextNode.execute(arguments[0]);

                // convert 'HPy *args' array to a PTuple
                Object argsArray;
                int n = castToJavaLongNode.execute(arguments[2]);
                if (!arrayWrapperLib.hasArrayElements(arguments[1])) {
                    argsArray = callFromHPyArrayNode.call(nativeContext, GRAAL_HPY_FROM_HPY_ARRAY, arguments[1], (long) n);
                } else {
                    argsArray = arguments[1];
                    assert arrayWrapperLib.getArraySize(argsArray) >= n;
                }
                Object[] args = new Object[n];
                for (int i = 0; i < args.length; i++) {
                    args[i] = argAsHandleNode.execute(nativeContext, argsArrayLib.readArrayElement(argsArray, i));
                }
                PTuple argv = factory.createTuple(args);

                // read format string from a 'const char *'
                String format = castToStringNode.execute(callFromStringNode.call(nativeContext, GRAAL_HPY_FROM_STRING, arguments[3]));
                String functionName = null;

                // separate format string from function name
                int colonIdx = format.indexOf(":");
                if (functionNameProfile.profile(colonIdx != -1)) {
                    // extract function name
                    // use 'colonIdx+1' because we do not want to include the colon
                    functionName = format.substring(colonIdx + 1);

                    // trim off function name
                    format = format.substring(0, colonIdx);
                }

                VaListWrapper varargs = new VaListWrapper(nativeContext, arguments[4], callMallocOutVarPtr.call(nativeContext, GRAAL_HPY_ALLOCATE_OUTVAR));
                return parseTupleAndKeywordsNode.execute(functionName, argv, null, format, null, varargs, nativeContext);
            } catch (ArithmeticException | InteropException e) {
                CompilerDirectives.transferToInterpreter();
                return raiseNode.raise(SystemError, "error when reading native argument stack: %m", e);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyLongFromLong extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                       @Cached HPyAsContextNode asContextNode,
                       @Cached CastToJavaLongNode castToJavaLongNode,
                       @Cached HPyLongFromLong fromLongNode) throws ArityException {
            if (arguments.length != 2) {
                throw ArityException.create(2, arguments.length);
            }
            GraalHPyContext context = asContextNode.execute(arguments[0]);
            long left = castToJavaLongNode.execute(arguments[1]);

            return fromLongNode.execute(left, true);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class GraalHPyLongFromUnsignedLongLong extends GraalHPyContextFunction {

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached HPyAsContextNode asContextNode,
                        @Cached CastToJavaLongNode castToJavaLongNode,
                        @Cached HPyLongFromLong fromLongNode) throws ArityException {
            if (arguments.length != 2) {
                throw ArityException.create(2, arguments.length);
            }
            GraalHPyContext context = asContextNode.execute(arguments[0]);
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
            if(addAttr != PNone.NO_VALUE) {
                return resultAsHandleNode.execute(context, callBinaryMethodNode.executeObject(null, addAttr, left, right));
            }
            throw raiseNode.raise(TypeError, "object of class %p has no __add__", left);
        }
    }
}
