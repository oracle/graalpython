/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
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
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.builtins.ListNodesFactory.ConstructListNodeGen;
import com.oracle.graal.python.nodes.builtins.ListNodesFactory.FastConstructListNodeGen;
import com.oracle.graal.python.nodes.control.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;

@GenerateNodeFactory
public abstract class ListNodes {

    @ImportStatic({PGuards.class, SpecialMethodNames.class})
    public abstract static class CreateListFromIteratorNode extends PBaseNode {

        public abstract PList execute(PythonClass cls, Object iterable);

        @Specialization
        public PList executeGeneric(PythonClass cls, Object iterator,
                        @Cached("create()") GetNextNode next,
                        @Cached("createBinaryProfile()") ConditionProfile errorProfile) {
            PList list = factory().createList(cls);
            while (true) {
                Object value;
                try {
                    value = next.execute(iterator);
                } catch (PException e) {
                    e.expectStopIteration(getCore(), errorProfile);
                    return list;
                }
                list.append(value);
            }
        }

        public static CreateListFromIteratorNode create() {
            return ListNodesFactory.CreateListFromIteratorNodeGen.create();
        }
    }

    @ImportStatic({PGuards.class, SpecialMethodNames.class})
    public abstract static class ConstructListNode extends PBaseNode {

        public final PList execute(Object value, PythonClass valueClass) {
            return execute(lookupClass(PythonBuiltinClassType.PList), value, valueClass);
        }

        public abstract PList execute(Object cls, Object value, PythonClass valueClass);

        @Specialization
        public PList listString(PythonClass cls, String arg, @SuppressWarnings("unused") PythonClass valueClass) {
            char[] chars = arg.toCharArray();
            PList list = factory().createList(cls);

            for (char c : chars) {
                list.append(Character.toString(c));
            }

            return list;
        }

        @Specialization(guards = "isNoValue(none)")
        public PList listIterable(PythonClass cls, @SuppressWarnings("unused") PNone none, @SuppressWarnings("unused") PythonClass valueClass) {
            return factory().createList(cls);
        }

        @Specialization(guards = "!isNoValue(iterable)")
        public PList listIterable(PythonClass cls, Object iterable, @SuppressWarnings("unused") PythonClass valueClass,
                        @Cached("create()") GetIteratorNode getIteratorNode,
                        @Cached("create()") CreateListFromIteratorNode createListFromIteratorNode) {

            Object iterObj = getIteratorNode.executeWith(iterable);
            return createListFromIteratorNode.execute(cls, iterObj);
        }

        @Fallback
        public PList listObject(@SuppressWarnings("unused") Object cls, Object value, @SuppressWarnings("unused") PythonClass valueClass) {
            CompilerDirectives.transferToInterpreter();
            throw new RuntimeException("list does not support iterable object " + value);
        }

        public static ConstructListNode create() {
            return ConstructListNodeGen.create();
        }
    }

    @ImportStatic(PGuards.class)
    public abstract static class FastConstructListNode extends PBaseNode {

        @Child private ConstructListNode constructListNode;

        public final PSequence execute(Object value, PythonClass valueClass) {
            return execute(lookupClass(PythonBuiltinClassType.PList), value, valueClass);
        }

        public abstract PSequence execute(Object cls, Object value, PythonClass valueClass);

        @Specialization(guards = "cannotBeOverridden(valueClass)")
        protected PSequence doPList(@SuppressWarnings("unused") Object cls, PSequence value, @SuppressWarnings("unused") PythonClass valueClass) {
            return value;
        }

        @Fallback
        protected PSequence doGeneric(Object cls, Object value, PythonClass valueClass) {
            if (constructListNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                constructListNode = insert(ConstructListNode.create());
            }
            return constructListNode.execute(cls, value, valueClass);
        }

        public static FastConstructListNode create() {
            return FastConstructListNodeGen.create();
        }
    }
}
