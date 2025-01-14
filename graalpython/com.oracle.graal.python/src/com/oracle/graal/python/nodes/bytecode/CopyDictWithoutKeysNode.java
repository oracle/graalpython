/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.dict.DictNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.lib.PyDictDelItem;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;

@GenerateInline(false) // used in BCI root node
public abstract class CopyDictWithoutKeysNode extends PNodeWithContext {
    public abstract PDict execute(Frame frame, Object subject, Object[] keys);

    @Specialization(guards = {"keys.length == keysLength", "keysLength <= 32"}, limit = "1")
    static PDict copy(VirtualFrame frame, Object subject, @NeverDefault @SuppressWarnings("unused") Object[] keys,
                    @Bind("this") Node inliningTarget,
                    @Cached("keys.length") int keysLength,
                    @Shared @Cached PythonObjectFactory factory,
                    @Shared @Cached DictNodes.UpdateNode updateNode,
                    @Shared @Cached PyDictDelItem delItem) {
        PDict rest = factory.createDict();
        updateNode.execute(frame, rest, subject);
        deleteKeys(frame, inliningTarget, keys, keysLength, delItem, rest);
        return rest;
    }

    @ExplodeLoop
    private static void deleteKeys(VirtualFrame frame, Node inliningTarget, Object[] keys, int keysLen, PyDictDelItem delItem, PDict rest) {
        CompilerAsserts.partialEvaluationConstant(keysLen);
        for (int i = 0; i < keysLen; i++) {
            delItem.execute(frame, inliningTarget, rest, keys[i]);
        }
    }

    @Specialization(guards = "keys.length > 32")
    static PDict copy(VirtualFrame frame, Object subject, Object[] keys,
                    @Bind("this") Node inliningTarget,
                    @Shared @Cached PythonObjectFactory factory,
                    @Shared @Cached DictNodes.UpdateNode updateNode,
                    @Shared @Cached PyDictDelItem delItem) {
        PDict rest = factory.createDict();
        updateNode.execute(frame, rest, subject);
        for (int i = 0; i < keys.length; i++) {
            delItem.execute(frame, inliningTarget, rest, keys[i]);
        }
        return rest;
    }

    public static CopyDictWithoutKeysNode create() {
        return CopyDictWithoutKeysNodeGen.create();
    }

}
