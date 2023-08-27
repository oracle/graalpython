/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.lib;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.HiddenKey;

/**
 * Partial equivalent of CPython's {@code PyObject_Dir}. Only supports listing attributes of an
 * object, not local variables like the {@code dir} builtin when called with no arguments.
 */
@GenerateInline
@GenerateCached(false)
public abstract class PyObjectDir extends PNodeWithContext {
    public abstract PList execute(VirtualFrame frame, Node inliningTarget, Object object);

    @Specialization
    static PList dir(VirtualFrame frame, Node inliningTarget, Object object,
                    @Cached(inline = false) ListBuiltins.ListSortNode sortNode,
                    @Cached(inline = false) ListNodes.ConstructListNode constructListNode,
                    @Cached(value = "create(T___DIR__)", inline = false) LookupAndCallUnaryNode callDir,
                    @Cached PRaiseNode.Lazy raiseNode) {
        Object result = callDir.executeObject(frame, object);
        if (result == PNone.NO_VALUE) {
            throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.OBJ_DOES_NOT_PROVIDE_DIR);
        }
        PList list = constructListNode.execute(frame, result);
        filterHiddenKeys(list.getSequenceStorage());
        sortNode.execute(frame, list);
        return list;
    }

    static void filterHiddenKeys(SequenceStorage s) {
        if (s instanceof EmptySequenceStorage) {
            // noting to do.
        } else if (s instanceof ObjectSequenceStorage storage) {
            // String do not have a special storage
            Object[] oldarray = storage.getInternalArray();
            Object[] newarray = new Object[storage.length()];
            int j = 0;
            for (int i = 0; i < storage.length(); i++) {
                Object o = oldarray[i];
                if (o instanceof HiddenKey) {
                    continue;
                }
                newarray[j++] = o;
            }
            storage.setInternalArrayObject(newarray);
            storage.setNewLength(j);
        } else {
            assert false : "Unexpected storage type!";
        }
    }
}
