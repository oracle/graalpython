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

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEW__;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;
import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.PCallHPyFunction;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.PCallHPyFunctionNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyObjectBuiltinsFactory.HPyObjectNewNodeGen;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.GeneratedBy;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.strings.TruffleString;

public abstract class GraalHPyObjectBuiltins {
    public static class HPyObjectNewNodeFactory<T extends PythonBuiltinBaseNode> implements NodeFactory<T> {
        private final T node;

        public HPyObjectNewNodeFactory(T node) {
            this.node = node;
        }

        @Override
        public T createNode(Object... arguments) {
            return NodeUtil.cloneNode(node);
        }

        @Override
        public Class<T> getNodeClass() {
            return determineNodeClass(node);
        }

        @SuppressWarnings("unchecked")
        private static <T> Class<T> determineNodeClass(T node) {
            CompilerAsserts.neverPartOfCompilation();
            Class<T> nodeClass = (Class<T>) node.getClass();
            GeneratedBy genBy = nodeClass.getAnnotation(GeneratedBy.class);
            if (genBy != null) {
                nodeClass = (Class<T>) genBy.value();
                assert nodeClass.isAssignableFrom(node.getClass());
            }
            return nodeClass;
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

    @Builtin(name = J___NEW__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    public abstract static class HPyObjectNewNode extends PythonVarargsBuiltinNode {
        private static final TruffleString KW_SUPER_CONSTRUCTOR = tsLiteral("$supercons");

        private static PKeyword[] createKwDefaults(Object superConstructor) {
            if (superConstructor != null) {
                return new PKeyword[]{new PKeyword(KW_SUPER_CONSTRUCTOR, superConstructor)};
            }
            return PKeyword.EMPTY_KEYWORDS;
        }

        private static final Builtin BUILTIN = HPyObjectNewNode.class.getAnnotation(Builtin.class);
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(HPyObjectNewNode.class);

        @Child private PCallHPyFunction callHPyFunctionNode;
        @Child private CallVarargsMethodNode callNewNode;

        @Override
        public Object varArgExecute(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            if (arguments.length >= 1) {
                return doGeneric(frame, self, arguments, keywords);
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw VarargsBuiltinDirectInvocationNotSupported.INSTANCE;
        }

        @Specialization
        Object doGeneric(VirtualFrame frame, Object explicitSelf, Object[] arguments, PKeyword[] keywords) {
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
            Object dataPtr = null;
            if (self instanceof PythonClass) {
                // allocate native space
                long basicSize = ((PythonClass) self).basicSize;
                if (basicSize > 0) {
                    /*
                     * This is just calling 'calloc' which is a pure helper function. Therefore, we
                     * can take any HPy context and don't need to attach a context to this __new__
                     * function for that since the helper function won't deal with handles.
                     */
                    dataPtr = ensureCallHPyFunctionNode().call(getContext().getHPyContext(), GraalHPyNativeSymbol.GRAAL_HPY_CALLOC, basicSize, 1L);

                    if (LOGGER.isLoggable(Level.FINEST)) {
                        LOGGER.finest(PythonUtils.formatJString("Allocated HPy object with native space of size %d at %s", basicSize, dataPtr));
                    }
                    // TODO(fa): add memory tracing
                }
            }

            Object inheritedConstructor = extractInheritedConstructor(keywords);
            Object pythonObject;
            if (inheritedConstructor == null) {
                // fast-path if the super class is 'object'
                pythonObject = factory().createPythonHPyObject(self, dataPtr);
            } else {
                pythonObject = ensureCallNewNode().execute(frame, inheritedConstructor, argsWithSelf, keywords);

                /*
                 * Since we are creating an object with an unknown constructor, the Java type may be
                 * anything (e.g. PInt, etc). However, we require it to be a PythonObject otherwise
                 * we don't know where to store the native data pointer.
                 */
                if (dataPtr != null && pythonObject instanceof PythonObject) {
                    ((PythonObject) pythonObject).setHPyNativeSpace(dataPtr);
                }
            }
            return pythonObject;
        }

        private static Object extractInheritedConstructor(PKeyword[] keywords) {
            for (int i = 0; i < keywords.length; i++) {
                if (keywords[i].getName() == KW_SUPER_CONSTRUCTOR) {
                    return keywords[i].getValue();
                }
            }
            return PythonContext.get(null).lookupType(PythonBuiltinClassType.PythonObject).getAttribute(SpecialMethodNames.T___NEW__);
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
        public static PBuiltinFunction createBuiltinFunction(PythonLanguage language, Object superConstructor) {
            // do not decorate the decorator
            if (superConstructor instanceof PBuiltinFunction builtinFunction && isHPyObjectNewDecorator(builtinFunction)) {
                return builtinFunction;
            }

            RootCallTarget callTarget = language.createCachedCallTarget(l -> new BuiltinFunctionRootNode(l, BUILTIN, new HPyObjectNewNodeFactory<>(HPyObjectNewNodeGen.create()), true),
                            HPyObjectNewNode.class, BUILTIN.name());
            int flags = PBuiltinFunction.getFlags(BUILTIN, callTarget);
            return PythonObjectFactory.getUncached().createBuiltinFunction(SpecialMethodNames.T___NEW__, null, PythonUtils.EMPTY_OBJECT_ARRAY, createKwDefaults(superConstructor), flags, callTarget);
        }

        private static boolean isHPyObjectNewDecorator(PBuiltinFunction builtinFunction) {
            return builtinFunction.getFunctionRootNode() instanceof BuiltinFunctionRootNode rootNode && rootNode.getBuiltin() == BUILTIN;
        }

        public static Object getDecoratedSuperConstructor(PBuiltinFunction builtinFunction) {
            if (isHPyObjectNewDecorator(builtinFunction)) {
                return extractInheritedConstructor(builtinFunction.getKwDefaults());
            }
            return null;
        }
    }
}
