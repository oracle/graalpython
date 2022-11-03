/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.bytecode;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

import static com.oracle.graal.python.nodes.SpecialMethodNames.T_GET;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

public abstract class MatchKeysNode extends PNodeWithContext {
    public abstract Object execute(Frame frame, Object map, Object keys);

    @Specialization
    static Object match(VirtualFrame frame, Object map, Object keys,
                    @Cached TupleBuiltins.LenNode lenNode,
                    @Cached TupleBuiltins.GetItemNode getItemNode,
                    @Cached PyObjectRichCompareBool.EqNode compareNode,
                    @Cached PyObjectCallMethodObjArgs callMethod,
                    @Cached PythonObjectFactory factory,
                    @Cached PRaiseNode raise) {
        int nkeys = (int) lenNode.execute(frame, keys);
        if (nkeys == 0) {
            return factory.createTuple(PythonUtils.EMPTY_OBJECT_ARRAY);
        }
        Object[] values = getValues(frame, map, keys, nkeys, getItemNode, compareNode, callMethod, raise);
        return values != null ? factory.createTuple(values) : PNone.NONE;
    }

    @ExplodeLoop
    private static Object[] getValues(VirtualFrame frame, Object map, Object keys, int nkeys, TupleBuiltins.GetItemNode getItemNode, PyObjectRichCompareBool.EqNode compareNode,
                    PyObjectCallMethodObjArgs callMethod, PRaiseNode raise) {
        Object[] values = new Object[nkeys];
        Object dummy = new Object();
        Object[] seen = new Object[nkeys];
        for (int i = 0; i < nkeys; i++) {
            Object key = getItemNode.execute(frame, keys, i);
            checkSeen(frame, raise, seen, key, compareNode);
            seen[i] = key;
            Object value = callMethod.execute(frame, map, T_GET, key, dummy);
            if (value == dummy) {
                return null;
            }
            values[i] = value;
        }
        return values;
    }

    @ExplodeLoop
    private static void checkSeen(VirtualFrame frame, PRaiseNode raise, Object[] seen, Object key, PyObjectRichCompareBool.EqNode compareNode) {
        for (int i = 0; i < seen.length; i++) {
            if (seen[i] != null && compareNode.execute(frame, seen[i], key)) {
                raise.raise(ValueError, ErrorMessages.MAPPING_PATTERN_CHECKS_DUPE_KEY_S, key);
            }
        }
    }

    public static MatchKeysNode create() {
        return MatchKeysNodeGen.create();
    }
}
