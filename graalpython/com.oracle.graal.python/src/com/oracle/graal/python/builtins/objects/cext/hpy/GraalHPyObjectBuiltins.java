/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPyType_BUILTIN_SHAPE_FLOAT;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPyType_BUILTIN_SHAPE_LEGACY;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPyType_BUILTIN_SHAPE_LIST;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPyType_BUILTIN_SHAPE_LONG;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPyType_BUILTIN_SHAPE_OBJECT;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPyType_BUILTIN_SHAPE_TUPLE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPyType_BUILTIN_SHAPE_TYPE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPyType_BUILTIN_SHAPE_UNICODE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEW__;

import java.util.List;
import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFunctions.GraalHPyNew;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.PCallHPyFunction;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.PCallHPyFunctionNodeGen;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

public abstract class GraalHPyObjectBuiltins {

    @Builtin(name = J___NEW__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    public static final class HPyObjectNewNode extends PythonVarargsBuiltinNode {

        private static final Builtin BUILTIN = HPyObjectNewNode.class.getAnnotation(Builtin.class);
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(HPyObjectNewNode.class);

        @Child private PCallHPyFunction callHPyFunctionNode;
        @Child private CallVarargsMethodNode callNewNode;

        private final PythonBuiltinClassType builtinClassType;

        protected HPyObjectNewNode(int builtinShape) {
            this.builtinClassType = getBuiltinClassType(builtinShape);
        }

        @Override
        public Object varArgExecute(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            if (arguments.length >= 1) {
                return execute(frame, self, arguments, keywords);
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw VarargsBuiltinDirectInvocationNotSupported.INSTANCE;
        }

        @Override
        public Object execute(VirtualFrame frame, Object explicitSelf, Object[] arguments, PKeyword[] keywords) {
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
            if (self instanceof PythonClass pythonClass) {
                // allocate native space
                long basicSize = pythonClass.basicSize;
                assert basicSize > 0;
                /*
                 * This is just calling 'calloc' which is a pure helper function. Therefore, we can
                 * take any HPy context and don't need to attach a context to this __new__ function
                 * for that since the helper function won't deal with handles.
                 */
                dataPtr = ensureCallHPyFunctionNode().call(context.getHPyContext(), GraalHPyNativeSymbol.GRAAL_HPY_CALLOC, basicSize, 1L);

                if (LOGGER.isLoggable(Level.FINEST)) {
                    LOGGER.finest(PythonUtils.formatJString("Allocated HPy object with native space of size %d at %s", basicSize, dataPtr));
                }
                // TODO(fa): add memory tracing
            }

            Object builtinConstructor = extractInheritedConstructor(context, builtinClassType);
            Object pythonObject = ensureCallNewNode().execute(frame, builtinConstructor, argsWithSelf, keywords);

            /*
             * Since we are creating an object with an unknown constructor, the Java type may be
             * anything (e.g. PInt, etc). However, we require it to be a PythonObject otherwise we
             * don't know where to store the native data pointer.
             */
            if (dataPtr != null && pythonObject instanceof PythonObject) {
                ((PythonObject) pythonObject).setHPyNativeSpace(dataPtr);
            }
            return pythonObject;
        }

        private static PythonBuiltinClassType getBuiltinClassType(int builtinShape) {
            return switch (builtinShape) {
                case HPyType_BUILTIN_SHAPE_LEGACY, HPyType_BUILTIN_SHAPE_OBJECT -> PythonBuiltinClassType.PythonObject;
                case HPyType_BUILTIN_SHAPE_TYPE -> PythonBuiltinClassType.PythonClass;
                case HPyType_BUILTIN_SHAPE_LONG -> PythonBuiltinClassType.PInt;
                case HPyType_BUILTIN_SHAPE_FLOAT -> PythonBuiltinClassType.PFloat;
                case HPyType_BUILTIN_SHAPE_UNICODE -> PythonBuiltinClassType.PString;
                case HPyType_BUILTIN_SHAPE_TUPLE -> PythonBuiltinClassType.PTuple;
                case HPyType_BUILTIN_SHAPE_LIST -> PythonBuiltinClassType.PList;
                default -> throw CompilerDirectives.shouldNotReachHere(GraalHPyNew.INVALID_BUILT_IN_SHAPE);
            };
        }

        private static Object extractInheritedConstructor(PythonContext context, PythonBuiltinClassType builtinClassType) {
            PythonBuiltinClass pythonBuiltinClass = context.lookupType(builtinClassType);
            Object builtinConstructor = SpecialMethodSlot.New.getValue(pythonBuiltinClass);
            assert builtinConstructor instanceof PythonBuiltinObject;
            return builtinConstructor;
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

        @TruffleBoundary
        public static PBuiltinFunction createBuiltinFunction(PythonLanguage language, int builtinShape) {
            RootCallTarget callTarget = language.createCachedCallTarget(l -> new BuiltinFunctionRootNode(l, BUILTIN, new HPyObjectNewNodeFactory(builtinShape), true), HPyObjectNewNode.class,
                            builtinShape);
            int flags = PBuiltinFunction.getFlags(BUILTIN, callTarget);
            return PythonObjectFactory.getUncached().createBuiltinFunction(SpecialMethodNames.T___NEW__, null, PythonUtils.EMPTY_OBJECT_ARRAY, PKeyword.EMPTY_KEYWORDS, flags, callTarget);
        }

        public static Object getDecoratedSuperConstructor(PBuiltinFunction builtinFunction) {
            if (builtinFunction.getFunctionRootNode() instanceof BuiltinFunctionRootNode rootNode && rootNode.getBuiltin() == BUILTIN) {
                HPyObjectNewNodeFactory nodeFactory = (HPyObjectNewNodeFactory) rootNode.getFactory();
                return extractInheritedConstructor(PythonContext.get(null), getBuiltinClassType(nodeFactory.builtinShape));
            }
            return null;
        }

        public static HPyObjectNewNode create(int builtinShape) {
            return new HPyObjectNewNode(builtinShape);
        }
    }

    public static class HPyObjectNewNodeFactory implements NodeFactory<HPyObjectNewNode> {
        private final int builtinShape;

        public HPyObjectNewNodeFactory(int builtinShape) {
            this.builtinShape = builtinShape;
        }

        @Override
        public HPyObjectNewNode createNode(Object... arguments) {
            return HPyObjectNewNode.create(builtinShape);
        }

        @Override
        public Class<HPyObjectNewNode> getNodeClass() {
            return HPyObjectNewNode.class;
        }

        @Override
        public List<List<Class<?>>> getNodeSignatures() {
            throw new IllegalAccessError();
        }

        @Override
        public List<Class<? extends Node>> getExecutionSignature() {
            throw new IllegalAccessError();
        }

    }
}
