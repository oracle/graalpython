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
package com.oracle.graal.python.builtins.modules.functools;

import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageCopy;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;

public final class PPartial extends PythonBuiltinObject {
    private Object fn;
    private Object[] args;
    private PTuple argsTuple;
    private PDict kw;

    public PPartial(Object cls, Shape instanceShape, Object fn, Object[] args, PDict kw) {
        super(cls, instanceShape);
        this.fn = fn;
        this.args = args;
        this.kw = kw;
    }

    public Object getFn() {
        return fn;
    }

    public void setFn(Object fn) {
        this.fn = fn;
    }

    public Object[] getArgs() {
        return args;
    }

    public PTuple getArgsTuple(PythonObjectFactory factory) {
        if (argsTuple == null) {
            this.argsTuple = factory.createTuple(args);
        }
        return argsTuple;
    }

    public void setArgs(Node inliningTarget, PTuple args, SequenceNodes.GetSequenceStorageNode storageNode, SequenceStorageNodes.ToArrayNode arrayNode) {
        this.argsTuple = args;
        this.args = arrayNode.execute(inliningTarget, storageNode.execute(inliningTarget, args));
    }

    public PDict getKw() {
        return kw;
    }

    public PDict getOrCreateKw(PythonObjectFactory factory) {
        if (kw == null) {
            kw = factory.createDict();
        }
        return kw;
    }

    public PDict getKwCopy(Node inliningTarget, PythonObjectFactory factory, HashingStorageCopy copyNode) {
        assert kw != null;
        return factory.createDict(copyNode.execute(inliningTarget, kw.getDictStorage()));
    }

    public boolean hasKw(Node inliningTarget, HashingStorageLen lenNode) {
        return lenNode.execute(inliningTarget, kw.getDictStorage()) > 0;
    }

    public void setKw(PDict kwArgs) {
        this.kw = kwArgs;
    }
}
