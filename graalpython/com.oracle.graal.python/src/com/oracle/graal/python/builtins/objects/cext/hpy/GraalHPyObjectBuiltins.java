/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.OBJECT_HPY_NATIVE_SPACE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.TYPE_HPY_BASICSIZE;

import java.util.List;
import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.PCallHPyFunction;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.PCallHPyFunctionNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyObjectBuiltinsFactory.HPyObjectNewNodeGen;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
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

    @Builtin(name = SpecialMethodNames.__NEW__, minNumOfPositionalArgs = 1, parameterNames = "$self", takesVarArgs = true, takesVarKeywordArgs = true)
    abstract static class HPyObjectNewNode extends PythonVarargsBuiltinNode {
        private static final Builtin BUILTIN = HPyObjectNewNode.class.getAnnotation(Builtin.class);
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(HPyObjectNewNode.class);

        @Child private PCallHPyFunction callHPyFunctionNode;
        @Child private ReadAttributeFromObjectNode readBasicsizeNode;
        @Child private WriteAttributeToObjectNode writeNativeSpaceNode;

        @Override
        public Object varArgExecute(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            if (arguments.length >= 1) {
                return doGeneric(frame, arguments[0], PythonUtils.EMPTY_OBJECT_ARRAY, PKeyword.EMPTY_KEYWORDS);
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new VarargsBuiltinDirectInvocationNotSupported();
        }

        @Specialization
        @SuppressWarnings("unused")
        PythonObject doGeneric(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords) {

            // create the managed Python object
            PythonObject pythonObject = factory().createPythonObject(self);

            // allocate native space
            Object attrObj = ensureReadBasicsizeNode().execute(self, TYPE_HPY_BASICSIZE);
            if (attrObj != PNone.NO_VALUE) {
                // we fully control this attribute; if it is there, it's always a long
                long basicsize = (long) attrObj;
                /*
                 * This is just calling 'calloc' which is a pure helper function. Therefore, we can
                 * take any HPy context and don't need to attach a context to this __new__ function
                 * for that since the helper function won't deal with handles.
                 */
                Object dataPtr = ensureCallHPyFunctionNode().call(getContext().getHPyContext(), GraalHPyNativeSymbol.GRAAL_HPY_CALLOC, basicsize, 1L);
                ensureWriteNativeSpaceNode().execute(pythonObject, OBJECT_HPY_NATIVE_SPACE, dataPtr);

                if (LOGGER.isLoggable(Level.FINEST)) {
                    LOGGER.finest(() -> String.format("Allocated HPy object with native space of size %d at %s", basicsize, dataPtr));
                }
                // TODO(fa): add memory tracing
            }
            return pythonObject;
        }

        private ReadAttributeFromObjectNode ensureReadBasicsizeNode() {
            if (readBasicsizeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readBasicsizeNode = insert(ReadAttributeFromObjectNode.createForceType());
            }
            return readBasicsizeNode;
        }

        private WriteAttributeToObjectNode ensureWriteNativeSpaceNode() {
            if (writeNativeSpaceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                writeNativeSpaceNode = insert(WriteAttributeToObjectNode.createForceType());
            }
            return writeNativeSpaceNode;
        }

        private PCallHPyFunction ensureCallHPyFunctionNode() {
            if (callHPyFunctionNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callHPyFunctionNode = insert(PCallHPyFunctionNodeGen.create());
            }
            return callHPyFunctionNode;
        }

        @TruffleBoundary
        public static PBuiltinFunction createBuiltinFunction(PythonLanguage language) {
            RootCallTarget callTarget = language.createCachedCallTarget(l -> new BuiltinFunctionRootNode(l, BUILTIN, new HPyObjectNewNodeFactory<>(HPyObjectNewNodeGen.create()), true),
                            HPyObjectNewNode.class, BUILTIN.name());
            int flags = PBuiltinFunction.getFlags(BUILTIN, callTarget);
            return PythonObjectFactory.getUncached().createBuiltinFunction(SpecialMethodNames.__NEW__, null, 0, flags, callTarget);
        }
    }
}
