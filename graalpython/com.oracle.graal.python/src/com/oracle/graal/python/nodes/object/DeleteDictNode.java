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
package com.oracle.graal.python.nodes.object;

import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageCopy;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

@GenerateUncached
@SuppressWarnings("truffle-inlining")       // footprint reduction 36 -> 17
public abstract class DeleteDictNode extends PNodeWithContext {
    public abstract void execute(PythonObject object);

    @Specialization
    static void doPythonObject(PythonObject object,
                    @Bind("this") Node inliningTarget,
                    @Cached HiddenAttr.ReadNode readHiddenAttrNode,
                    @Cached HiddenAttr.WriteNode writeHiddenAttrNode,
                    @Cached HashingStorageCopy copyNode,
                    @Cached PythonObjectFactory factory) {
        /* There is no special handling for class MROs because type.__dict__ cannot be deleted. */
        assert !PGuards.isPythonClass(object);
        PDict oldDict = (PDict) readHiddenAttrNode.execute(inliningTarget, object, HiddenAttr.DICT, null);
        if (oldDict != null) {
            HashingStorage storage = oldDict.getDictStorage();
            if (storage instanceof DynamicObjectStorage && ((DynamicObjectStorage) storage).getStore() == object) {
                /*
                 * We have to dissociate the dict from this DynamicObject so that changes to it no
                 * longer affect this object.
                 */
                oldDict.setDictStorage(copyNode.execute(inliningTarget, storage));
            }
        }
        /*
         * Ideally we would use resetShape, but that would lose all the hidden keys. Creating a new
         * empty dict dissociated from this object seems like the cleanest option. The disadvantage
         * is that the current values won't get garbage collected.
         */
        PDict newDict = factory.createDict();
        object.setDict(inliningTarget, writeHiddenAttrNode, newDict);
    }

    @NeverDefault
    public static DeleteDictNode create() {
        return DeleteDictNodeGen.create();
    }

    public static DeleteDictNode getUncached() {
        return DeleteDictNodeGen.getUncached();
    }
}
