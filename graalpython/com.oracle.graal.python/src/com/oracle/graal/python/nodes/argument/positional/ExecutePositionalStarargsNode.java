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
package com.oracle.graal.python.nodes.argument.positional;

import java.util.Iterator;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@NodeChildren({@NodeChild(value = "splat", type = PNode.class)})
public abstract class ExecutePositionalStarargsNode extends Node {
    public abstract Object[] execute(VirtualFrame frame);

    public abstract Object[] executeWith(Object starargs);

    @Specialization
    Object[] starargs(Object[] starargs) {
        return starargs;
    }

    @Specialization
    Object[] starargs(PTuple starargs) {
        return starargs.getArray();
    }

    @Specialization
    Object[] starargs(PList starargs) {
        int length = starargs.getSequenceStorage().length();
        Object[] internalArray = starargs.getSequenceStorage().getInternalArray();
        if (internalArray.length != length) {
            return starargs.getSequenceStorage().getCopyOfInternalArray();
        }
        return internalArray;
    }

    @Specialization
    Object[] starargs(PDict starargs) {
        int length = starargs.size();
        Object[] args = new Object[length];
        Iterator<Object> iterator = starargs.getDictStorage().keys().iterator();
        for (int i = 0; i < args.length; i++) {
            assert iterator.hasNext();
            args[i] = iterator.next();
        }
        return args;
    }

    @Specialization
    Object[] starargs(PSet starargs) {
        int length = starargs.size();
        Object[] args = new Object[length];
        Iterator<Object> iterator = starargs.getDictStorage().keys().iterator();
        for (int i = 0; i < args.length; i++) {
            assert iterator.hasNext();
            args[i] = iterator.next();
        }
        return args;
    }

    @SuppressWarnings("unused")
    @Specialization
    Object[] starargs(PNone none) {
        return new Object[0];
    }

    public static ExecutePositionalStarargsNode create() {
        return ExecutePositionalStarargsNodeGen.create(null);
    }

    public static ExecutePositionalStarargsNode create(PNode splat) {
        return ExecutePositionalStarargsNodeGen.create(splat);
    }
}
