/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.util.PythonUtils.tsArray;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.PCallHPyFunction;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.PCallHPyFunctionNodeGen;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadVarArgsNode;
import com.oracle.graal.python.nodes.argument.ReadVarKeywordsNode;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;

public abstract class GraalHPyObjectBuiltins {

    public static final class HPyObjectNewNode extends PRootNode {
        private static final TruffleString KW_SUPERCONS = tsLiteral("$supercons");
        private static final TruffleString[] KEYWORDS_HIDDEN_SUPERCONS = {KW_SUPERCONS};

        private static final Signature SIGNATURE = new Signature(-1, true, 1, false, tsArray("self"), KEYWORDS_HIDDEN_SUPERCONS, false);

        private static final TruffleLogger LOGGER = GraalHPyContext.getLogger(HPyObjectNewNode.class);

        private static PKeyword[] createKwDefaults(Object superConstructor) {
            if (superConstructor != null) {
                return new PKeyword[]{new PKeyword(KW_SUPERCONS, superConstructor)};
            }
            return PKeyword.EMPTY_KEYWORDS;
        }

        @Child private CalleeContext calleeContext;
        @Child private ReadIndexedArgumentNode readSelfNode;
        @Child private ReadVarArgsNode readVarargsNode;
        @Child private ReadVarKeywordsNode readKwargsNode;
        @Child private ReadIndexedArgumentNode readCallableNode;
        @Child private PCallHPyFunction callHPyFunctionNode;
        @Child private CallVarargsMethodNode callNewNode;

        private final int builtinShape;

        private HPyObjectNewNode(PythonLanguage language, int builtinShape) {
            super(language);
            this.builtinShape = builtinShape;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            getCalleeContext().enter(frame);
            try {
                return doCall(frame, getSuperConstructor(frame), getSelf(frame), getVarargs(frame), getKwargs(frame));
            } finally {
                getCalleeContext().exit(frame, this);
            }
        }

        private Object doCall(VirtualFrame frame, Object superConstructor, Object explicitSelf, Object[] arguments, PKeyword[] keywords) {
            assert explicitSelf != null;

            // create the managed Python object

            // delegate to the best base's constructor
            Object self;
            Object[] argsWithSelf;
            if (explicitSelf == PNone.NO_VALUE) {
                argsWithSelf = arguments;
                self = argsWithSelf[0];
            } else {
                argsWithSelf = new Object[arguments.length + 1];
                argsWithSelf[0] = explicitSelf;
                PythonUtils.arraycopy(arguments, 0, argsWithSelf, 1, arguments.length);
                self = explicitSelf;
            }
            PythonContext context = PythonContext.get(this);
            Object dataPtr = null;
            Object defaultCallFunction = null;
            if (self instanceof PythonClass pythonClass) {
                // allocate native space
                long basicSize = pythonClass.getBasicSize();
                if (basicSize > 0) {
                    /*
                     * This is just calling 'calloc' which is a pure helper function. Therefore, we
                     * can take any HPy context and don't need to attach a context to this __new__
                     * function for that since the helper function won't deal with handles.
                     */
                    dataPtr = ensureCallHPyFunctionNode().call(context.getHPyContext(), GraalHPyNativeSymbol.GRAAL_HPY_CALLOC, basicSize, 1L);

                    if (LOGGER.isLoggable(Level.FINEST)) {
                        LOGGER.finest(PythonUtils.formatJString("Allocated HPy object with native space of size %d at %s", basicSize, dataPtr));
                    }
                }

                defaultCallFunction = pythonClass.getHPyDefaultCallFunc();
            }

            Object result = ensureCallNewNode().execute(frame, superConstructor, argsWithSelf, keywords);
            assert validateSuperConstructorResult(result, builtinShape);

            /*
             * Since we are creating an object with an unknown constructor, the Java type may be
             * anything (e.g. PInt, etc). However, we require it to be a PythonObject otherwise we
             * don't know where to store the native data pointer.
             */
            if (result instanceof PythonObject pythonObject) {
                if (dataPtr != null) {
                    GraalHPyData.setHPyNativeSpace(pythonObject, dataPtr);
                }
                if (defaultCallFunction != null) {
                    GraalHPyData.setHPyCallFunction(pythonObject, defaultCallFunction);
                }
            } else {
                assert false : "inherited constructor of HPy type did not create a managed Python object";
            }
            return result;
        }

        private static boolean validateSuperConstructorResult(Object result, int builtinShape) {
            return switch (builtinShape) {
                case GraalHPyDef.HPyType_BUILTIN_SHAPE_LEGACY, GraalHPyDef.HPyType_BUILTIN_SHAPE_OBJECT -> result instanceof PythonObject;
                case GraalHPyDef.HPyType_BUILTIN_SHAPE_TYPE -> result instanceof PythonClass;
                case GraalHPyDef.HPyType_BUILTIN_SHAPE_LONG -> result instanceof PInt;
                case GraalHPyDef.HPyType_BUILTIN_SHAPE_FLOAT -> result instanceof PFloat;
                case GraalHPyDef.HPyType_BUILTIN_SHAPE_UNICODE -> result instanceof PString;
                case GraalHPyDef.HPyType_BUILTIN_SHAPE_TUPLE -> result instanceof PTuple;
                case GraalHPyDef.HPyType_BUILTIN_SHAPE_LIST -> result instanceof PList;
                default -> false;
            };
        }

        private Object getSelf(VirtualFrame frame) {
            if (readSelfNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readSelfNode = insert(ReadIndexedArgumentNode.create(0));
            }
            return readSelfNode.execute(frame);
        }

        private Object[] getVarargs(VirtualFrame frame) {
            if (readVarargsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readVarargsNode = insert(ReadVarArgsNode.create(true));
            }
            return readVarargsNode.executeObjectArray(frame);
        }

        private PKeyword[] getKwargs(VirtualFrame frame) {
            if (PArguments.getKeywordArguments(frame).length == 0) {
                return PKeyword.EMPTY_KEYWORDS;
            }
            if (readKwargsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readKwargsNode = insert(ReadVarKeywordsNode.create());
            }
            return (PKeyword[]) readKwargsNode.execute(frame);
        }

        private Object getSuperConstructor(VirtualFrame frame) {
            if (readCallableNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                // we insert a hidden argument at the end of the positional arguments
                int hiddenArg = getSignature().getParameterIds().length;
                readCallableNode = insert(ReadIndexedArgumentNode.create(hiddenArg));
            }
            return readCallableNode.execute(frame);
        }

        private CalleeContext getCalleeContext() {
            if (calleeContext == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                calleeContext = insert(CalleeContext.create());
            }
            return calleeContext;
        }

        private static Object extractInheritedConstructor(PythonContext context, PKeyword[] keywords) {
            for (int i = 0; i < keywords.length; i++) {
                if (keywords[i].getName() == KW_SUPERCONS) {
                    return keywords[i].getValue();
                }
            }
            return context.lookupType(PythonBuiltinClassType.PythonObject).getAttribute(SpecialMethodNames.T___NEW__);
        }

        private PCallHPyFunction ensureCallHPyFunctionNode() {
            if (callHPyFunctionNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callHPyFunctionNode = insert(PCallHPyFunctionNodeGen.create());
            }
            return callHPyFunctionNode;
        }

        private CallVarargsMethodNode ensureCallNewNode() {
            if (callNewNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNewNode = insert(CallVarargsMethodNode.create());
            }
            return callNewNode;
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }

        @Override
        public boolean isPythonInternal() {
            return true;
        }

        @Override
        public boolean setsUpCalleeContext() {
            return true;
        }

        @TruffleBoundary
        public static PBuiltinFunction createBuiltinFunction(PythonLanguage language, Object superConstructor, int builtinShape) {
            // do not decorate the decorator
            if (superConstructor instanceof PBuiltinFunction builtinFunction && isHPyObjectNewDecorator(builtinFunction)) {
                return builtinFunction;
            }
            RootCallTarget callTarget = language.createCachedCallTarget(l -> new HPyObjectNewNode(language, builtinShape), HPyObjectNewNode.class, builtinShape);
            int flags = CExtContext.METH_KEYWORDS | CExtContext.METH_VARARGS;
            return PythonObjectFactory.getUncached().createBuiltinFunction(SpecialMethodNames.T___NEW__, null, PythonUtils.EMPTY_OBJECT_ARRAY, createKwDefaults(superConstructor), flags, callTarget);
        }

        public static Object getDecoratedSuperConstructor(PBuiltinFunction builtinFunction) {
            if (isHPyObjectNewDecorator(builtinFunction)) {
                return extractInheritedConstructor(PythonContext.get(null), builtinFunction.getKwDefaults());
            }
            return null;
        }

        private static boolean isHPyObjectNewDecorator(PBuiltinFunction builtinFunction) {
            return builtinFunction.getFunctionRootNode() instanceof HPyObjectNewNode;
        }
    }
}
