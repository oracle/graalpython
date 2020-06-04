/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.builtins;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.CreateStorageFromIteratorNode;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNode.GetIteratorNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

@GenerateNodeFactory
public abstract class TupleNodes {

    @ImportStatic({PGuards.class, SpecialMethodNames.class})
    public abstract static class ConstructTupleNode extends PNodeWithContext {
        @Child private PythonObjectFactory factory = PythonObjectFactory.create();

        public final PTuple execute(VirtualFrame frame, Object value) {
            return execute(frame, PythonBuiltinClassType.PTuple, value);
        }

        public abstract PTuple execute(VirtualFrame frame, Object cls, Object value);

        @Specialization(guards = "isNoValue(none)")
        PTuple tuple(Object cls, @SuppressWarnings("unused") PNone none) {
            return factory.createEmptyTuple(cls);
        }

        @Specialization
        PTuple tuple(Object cls, String arg) {
            Object[] values = new Object[arg.length()];
            for (int i = 0; i < arg.length(); i++) {
                values[i] = String.valueOf(arg.charAt(i));
            }
            return factory.createTuple(cls, values);
        }

        @Specialization(guards = {"cannotBeOverridden(cls)", "cannotBeOverridden(plib.getLazyPythonClass(iterable))"}, limit = "2")
        PTuple tuple(@SuppressWarnings("unused") Object cls, PTuple iterable,
                        @SuppressWarnings("unused") @CachedLibrary("iterable") PythonObjectLibrary plib) {
            return iterable;
        }

        @Specialization(guards = {"!isNoValue(iterable)", "createNewTuple(cls, iterable, plib)"}, limit = "2")
        PTuple tuple(VirtualFrame frame, Object cls, Object iterable,
                        @Cached("create()") GetIteratorNode getIteratorNode,
                        @Cached("create()") CreateStorageFromIteratorNode storageNode,
                        @SuppressWarnings("unused") @CachedLibrary("iterable") PythonObjectLibrary plib) {
            Object iterObj = getIteratorNode.executeWith(frame, iterable);
            return factory.createTuple(cls, storageNode.execute(frame, iterObj));
        }

        @Fallback
        public PTuple tuple(@SuppressWarnings("unused") Object cls, Object value) {
            CompilerDirectives.transferToInterpreter();
            throw new RuntimeException("tuple does not support iterable object " + value);
        }

        protected boolean createNewTuple(Object cls, Object iterable, PythonObjectLibrary plib) {
            if (iterable instanceof PTuple) {
                return !(PGuards.cannotBeOverridden(cls) && PGuards.cannotBeOverridden(plib.getLazyPythonClass(iterable)));
            }
            return true;
        }

        public static ConstructTupleNode create() {
            return TupleNodesFactory.ConstructTupleNodeGen.create();
        }
    }
}
