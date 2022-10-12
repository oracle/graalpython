/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.generator;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.HashingStorageGetItemNodeGen;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public abstract class DictConcatNode extends ExpressionNode {

    @Children final ExpressionNode[] mappables;
    @Children final HashingStorageGetItem[] getDictItemNodes;

    @Child private PRaiseNode raiseNode;

    protected DictConcatNode(ExpressionNode... mappablesNodes) {
        this.mappables = mappablesNodes;
        if (mappablesNodes.length > 0) {
            this.getDictItemNodes = new HashingStorageGetItem[mappablesNodes.length - 1];
        } else {
            this.getDictItemNodes = null;
        }
    }

    @ExplodeLoop
    @Specialization
    public Object concat(VirtualFrame frame,
                    @Cached HashingStorageSetItem setItem,
                    @CachedLibrary(limit = "3") HashingStorageLibrary hlib) {
        // TODO support mappings in general
        PDict first = null;
        PDict other;
        for (int i = 0; i < mappables.length; i++) {
            ExpressionNode n = mappables[i];
            if (first == null) {
                first = expectDict(n.execute(frame));
            } else {
                other = expectDict(n.execute(frame));
                if (getDictItemNodes[i] == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    getDictItemNodes[i] = insert(HashingStorageGetItemNodeGen.create());
                }
                addAllToDict(frame, first, other, getDictItemNodes[i], setItem, hlib);
            }
        }
        return first;
    }

    private static void addAllToDict(VirtualFrame frame, PDict dict, PDict other,
                    HashingStorageGetItem getItem, HashingStorageSetItem setItem, HashingStorageLibrary hlib) {
        HashingStorage dictStorage = dict.getDictStorage();
        HashingStorage otherStorage = other.getDictStorage();
        for (Object key : hlib.keys(otherStorage)) {
            Object value = getItem.execute(frame, otherStorage, key);
            dictStorage = setItem.execute(frame, dictStorage, key, value);
        }
        dict.setDictStorage(dictStorage);
    }

    private PDict expectDict(Object first) {
        if (!(first instanceof PDict)) {
            throw getRaiseNode().raise(TypeError, ErrorMessages.OBJ_ISNT_MAPPING, first);
        }
        return (PDict) first;
    }

    protected final PRaiseNode getRaiseNode() {
        if (raiseNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            raiseNode = insert(PRaiseNode.create());
        }
        return raiseNode;
    }
}
