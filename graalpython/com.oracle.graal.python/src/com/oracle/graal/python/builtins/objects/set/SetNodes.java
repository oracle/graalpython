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
package com.oracle.graal.python.builtins.objects.set;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes.SetItemNode;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNode.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@GenerateNodeFactory
public abstract class SetNodes {

    @ImportStatic({PGuards.class, SpecialMethodNames.class})
    public abstract static class ConstructSetNode extends PNodeWithContext {
        @Child private PRaiseNode raise;
        @Child private SetItemNode setItemNode;

        public abstract PSet execute(VirtualFrame frame, LazyPythonClass cls, Object value);

        public final PSet executeWith(VirtualFrame frame, Object value) {
            return this.execute(frame, PythonBuiltinClassType.PSet, value);
        }

        @Specialization
        PSet setString(VirtualFrame frame, LazyPythonClass cls, String arg,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            PSet set = factory.createSet(cls);
            for (int i = 0; i < PString.length(arg); i++) {
                getSetItemNode().execute(frame, set, PString.valueOf(PString.charAt(arg, i)), PNone.NONE);
            }
            return set;
        }

        @Specialization(guards = "emptyArguments(none)")
        @SuppressWarnings("unused")
        PSet set(LazyPythonClass cls, PNone none,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createSet(cls);
        }

        @Specialization(guards = "!isNoValue(iterable)")
        PSet setIterable(VirtualFrame frame, LazyPythonClass cls, Object iterable,
                        @Shared("factory") @Cached PythonObjectFactory factory,
                        @Cached("create()") GetIteratorNode getIterator,
                        @Cached("create()") GetNextNode next,
                        @Cached("create()") IsBuiltinClassProfile errorProfile) {

            PSet set = factory.createSet(cls);
            Object iterator = getIterator.executeWith(frame, iterable);
            while (true) {
                try {
                    getSetItemNode().execute(frame, set, next.execute(frame, iterator), PNone.NONE);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    return set;
                }
            }
        }

        @Fallback
        PSet setObject(@SuppressWarnings("unused") LazyPythonClass cls, Object value) {
            if (raise == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raise = insert(PRaiseNode.create());
            }
            throw raise.raise(TypeError, ErrorMessages.OBJ_NOT_ITERABLE, value);
        }

        private SetItemNode getSetItemNode() {
            if (setItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setItemNode = insert(SetItemNode.create());
            }
            return setItemNode;
        }

        public static ConstructSetNode create() {
            return SetNodesFactory.ConstructSetNodeGen.create();
        }
    }
}
