/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.datamodel;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.iterator.PZip;
import com.oracle.graal.python.builtins.objects.range.PRange;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;

@GenerateUncached
public abstract class IsIterableNode extends PDataModelEmulationNode {

    @Specialization
    public boolean isIterable(@SuppressWarnings("unused") PRange range) {
        return true;
    }

    @Specialization
    public boolean isIterable(@SuppressWarnings("unused") PArray array) {
        return true;
    }

    @Specialization
    public boolean isIterable(@SuppressWarnings("unused") PSequence sequence) {
        return true;
    }

    @Specialization
    public boolean isIterable(@SuppressWarnings("unused") String str) {
        return true;
    }

    @Specialization
    public boolean isIterable(@SuppressWarnings("unused") PZip zip) {
        return true;
    }

    @Specialization
    public boolean isIterable(Object object,
                    @Cached GetLazyClassNode getClassNode,
                    @Cached LookupAttributeInMRONode.Dynamic getIterNode,
                    @Cached LookupAttributeInMRONode.Dynamic getGetItemNode,
                    @Cached LookupAttributeInMRONode.Dynamic hasNextNode,
                    @Cached IsCallableNode isCallableNode,
                    @Cached("createBinaryProfile()") ConditionProfile profileIter,
                    @Cached("createBinaryProfile()") ConditionProfile profileGetItem,
                    @Cached("createBinaryProfile()") ConditionProfile profileNext) {
        LazyPythonClass klass = getClassNode.execute(object);
        Object iterMethod = getIterNode.execute(klass, __ITER__);
        if (profileIter.profile(iterMethod != PNone.NO_VALUE && iterMethod != PNone.NONE)) {
            return true;
        } else {
            Object getItemMethod = getGetItemNode.execute(klass, __GETITEM__);
            if (profileGetItem.profile(getItemMethod != PNone.NO_VALUE)) {
                return true;
            } else if (isCallableNode.execute(object)) {
                return profileNext.profile(hasNextNode.execute(klass, __NEXT__) != PNone.NO_VALUE);
            }
        }
        return false;
    }

    public static IsIterableNode create() {
        return IsIterableNodeGen.create();
    }

    public static IsIterableNode getUncached() {
        return IsIterableNodeGen.getUncached();
    }
}
