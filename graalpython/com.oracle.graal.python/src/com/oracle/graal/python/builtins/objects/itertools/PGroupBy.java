/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

public final class PGroupBy extends PythonBuiltinObject {

    private Object tgtKey;
    private PGrouper currGrouper;
    private Object currValue;
    private Object currKey;
    private Object keyFunc;
    private Object it;

    public PGroupBy(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
    }

    public PGrouper getCurrGrouper() {
        return currGrouper;
    }

    public void setCurrGrouper(PGrouper currGrouper) {
        this.currGrouper = currGrouper;
    }

    public Object getTgtKey() {
        return tgtKey;
    }

    public void setTgtKey(Object tgtKey) {
        this.tgtKey = tgtKey;
    }

    public Object getKeyFunc() {
        return keyFunc;
    }

    public void setKeyFunc(Object keyFunc) {
        this.keyFunc = keyFunc;
    }

    public Object getIt() {
        return it;
    }

    public void setIt(Object it) {
        this.it = it;
    }

    public Object getCurrValue() {
        return currValue;
    }

    public void setCurrValue(Object currValue) {
        this.currValue = currValue;
    }

    public Object getCurrKey() {
        return currKey;
    }

    public void setCurrKey(Object currKey) {
        this.currKey = currKey;
    }

    void groupByStep(VirtualFrame frame, Node inliningTarget, BuiltinFunctions.NextNode nextNode, CallNode callNode, InlinedConditionProfile hasFuncProfile) {
        Object newValue = nextNode.execute(frame, it, PNone.NO_VALUE);
        Object newKey;
        if (hasFuncProfile.profile(inliningTarget, keyFunc == null)) {
            newKey = newValue;
        } else {
            newKey = callNode.execute(frame, keyFunc, newValue);
        }
        currValue = newValue;
        currKey = newKey;
    }
}
