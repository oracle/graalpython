/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.pickle;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

public class PData {
    Object[] data;
    // is MARK set?
    boolean mark;
    // position of top MARK or 0
    int fence;
    int size;

    public PData() {
        this.mark = false;
        this.fence = 0;
        this.data = new Object[8];
        this.size = 0;
    }

    public int getSize() {
        return size;
    }

    public void clear(int clearTo) {
        assert clearTo >= this.fence;
        int i = this.size;
        if (clearTo >= i) {
            return;
        }
        while (--i >= clearTo) {
            this.data[i] = null;
        }
        this.size = clearTo;
    }

    public void grow() throws OverflowException {
        int newAllocated = this.data.length << 1;
        if (newAllocated <= 0) {
            throw OverflowException.INSTANCE;
        }
        this.data = PythonUtils.arrayCopyOf(this.data, newAllocated);
    }

    public abstract static class PDataBaseNode extends PNodeWithContext {
        static PException raiseUnderflow(PData self, PRaiseNode raiseNode) {
            return raiseNode.raise(PythonBuiltinClassType.UnpicklingError,
                            self.mark ? ErrorMessages.PDATA_UNEXPECTED_MARK_FOUND : ErrorMessages.PDATA_UNPICKLING_STACK_UNDERFLOW);
        }
    }

    @GenerateNodeFactory
    @GenerateInline(false)
    public abstract static class PDataPopNode extends PDataBaseNode {
        public abstract Object execute(PData self);

        @Specialization
        static Object pop(PData self,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (self.size <= self.fence) {
                throw raiseUnderflow(self, raiseNode.get(inliningTarget));
            }
            return self.data[--self.size];
        }

        public static PDataPopNode create() {
            return PDataFactory.PDataPopNodeFactory.create();
        }
    }

    @GenerateNodeFactory
    @GenerateInline(false)
    public abstract static class PDataPushNode extends PDataBaseNode {
        public abstract void execute(PData self, Object obj);

        @Specialization
        static void push(PData self, Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (self.size == self.data.length) {
                try {
                    self.grow();
                } catch (OverflowException e) {
                    throw raiseNode.get(inliningTarget).raiseOverflow();
                }
            }
            self.data[self.size++] = obj;
        }

        public static PDataPushNode create() {
            return PDataFactory.PDataPushNodeFactory.create();
        }
    }

    @GenerateNodeFactory
    @GenerateInline(false)
    public abstract static class PDataPopTupleNode extends PDataBaseNode {
        public abstract PTuple execute(PData self, int start);

        @Specialization
        static PTuple popTuple(PData self, int start,
                        @Bind("this") Node inliningTarget,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            int len, i, j;

            if (start < self.fence) {
                throw raiseUnderflow(self, raiseNode.get(inliningTarget));
            }
            len = self.size - start;
            Object[] items = new Object[len];
            for (i = start, j = 0; j < len; i++, j++) {
                items[j] = self.data[i];
            }
            self.size = start;
            return factory.createTuple(items);
        }

        public static PDataPopTupleNode create() {
            return PDataFactory.PDataPopTupleNodeFactory.create();
        }
    }

    @GenerateNodeFactory
    @GenerateInline(false)
    public abstract static class PDataPopListNode extends PDataBaseNode {
        public abstract PList execute(PData self, int start);

        @Specialization
        public PList popList(PData self, int start,
                        @Cached PythonObjectFactory factory) {
            int len, i, j;

            len = self.size - start;
            Object[] items = new Object[len];
            for (i = start, j = 0; j < len; i++, j++) {
                items[j] = self.data[i];
            }
            self.size = start;
            return factory.createList(items);
        }

        public static PDataPopListNode create() {
            return PDataFactory.PDataPopListNodeFactory.create();
        }
    }
}
