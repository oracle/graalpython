/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.itertools;

import static com.oracle.graal.python.builtins.objects.itertools.TeeDataObjectBuiltins.LINKCELLS;
import static com.oracle.graal.python.nodes.ErrorMessages.CANNOT_REENTER_TEE_ITERATOR;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;

public final class PTeeDataObject extends PythonBuiltinObject {
    private Object it;
    private Object[] values;
    private int numread;
    private boolean running;
    private PTeeDataObject nextlink;

    public PTeeDataObject(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
    }

    public PTeeDataObject(Object it, Object cls, Shape instanceShape) {
        super(cls, instanceShape);
        this.it = it;
        this.values = new Object[LINKCELLS];
        this.numread = 0;
        this.running = false;
        this.nextlink = null;
    }

    public Object getIt() {
        return it;
    }

    public void setIt(Object it) {
        this.it = it;
    }

    public Object[] getValues() {
        return values;
    }

    public void setValues(Object[] values) {
        this.values = values;
    }

    public int getNumread() {
        return numread;
    }

    public void setNumread(int numread) {
        this.numread = numread;
    }

    public boolean getRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public PTeeDataObject getNextlink() {
        return nextlink;
    }

    public void setNextlink(PTeeDataObject nextlink) {
        this.nextlink = nextlink;
    }

    PTeeDataObject jumplink(PythonObjectFactory factory) {
        if (getNextlink() == null) {
            PTeeDataObject dataObj = factory.createTeeDataObject(getIt());
            nextlink = dataObj;
        }
        return nextlink;
    }

    Object getItem(VirtualFrame frame, Node inliningTarget, int i, BuiltinFunctions.NextNode nextNode, PRaiseNode.Lazy raiseNode) {
        assert i < TeeDataObjectBuiltins.LINKCELLS;
        if (i < numread) {
            return values[i];
        } else {
            assert i == numread;
            if (running) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.RuntimeError, CANNOT_REENTER_TEE_ITERATOR);
            }

            running = true;
            Object value;
            try {
                value = nextNode.execute(frame, it, PNone.NO_VALUE);
            } finally {
                running = false;
            }
            values[numread++] = value;
            return value;
        }
    }
}
