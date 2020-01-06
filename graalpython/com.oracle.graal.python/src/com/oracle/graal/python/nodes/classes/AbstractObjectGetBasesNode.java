/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.classes;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__BASES__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTRIBUTE__;

import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;

@NodeInfo(shortName = "cpython://Objects/abstract.c/abstract_get_bases")
@GenerateUncached
@ImportStatic({SpecialMethodNames.class})
public abstract class AbstractObjectGetBasesNode extends PNodeWithContext {
    public static AbstractObjectGetBasesNode create() {
        return AbstractObjectGetBasesNodeGen.create();
    }

    protected abstract PTuple executeInternal(Frame frame, Object cls);

    public final PTuple execute(VirtualFrame frame, Object cls) {
        return executeInternal(frame, cls);
    }

    public final PTuple execute(Object cls) {
        return executeInternal(null, cls);
    }

    @Specialization(guards = "!isUncached()")
    PTuple getBasesCached(VirtualFrame frame, Object cls,
                    @Cached("create(__GETATTRIBUTE__)") LookupAndCallBinaryNode getAttributeNode,
                    @Shared("exceptionMaskProfile") @Cached IsBuiltinClassProfile exceptionMaskProfile) {
        try {
            Object bases = getAttributeNode.executeObject(frame, cls, __BASES__);
            if (bases instanceof PTuple) {
                return (PTuple) bases;
            }
        } catch (PException pe) {
            pe.expectAttributeError(exceptionMaskProfile);
        }
        return null;
    }

    @Specialization(replaces = "getBasesCached")
    PTuple getBasesUncached(VirtualFrame frame, Object cls,
                    @Cached LookupInheritedAttributeNode.Dynamic lookupGetattributeNode,
                    @Cached CallNode callGetattributeNode,
                    @Shared("exceptionMaskProfile") @Cached IsBuiltinClassProfile exceptionMaskProfile) {
        Object getattr = lookupGetattributeNode.execute(cls, __GETATTRIBUTE__);
        try {
            Object bases = callGetattributeNode.execute(frame, getattr, cls, __BASES__);
            if (bases instanceof PTuple) {
                return (PTuple) bases;
            }
        } catch (PException pe) {
            pe.expectAttributeError(exceptionMaskProfile);
        }
        return null;
    }
}
