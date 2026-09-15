/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.HiddenAttr.ReadNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;

/**
 * Like {@link GetDictIfExistsNode}, but gets the dict only if it is materialized and reads/writes must be wired through it.
 */
@GenerateUncached
@GenerateInline(false)       // footprint reduction 36 -> 17
@ImportStatic({GetDictIfExistsNode.class, PGuards.class})
public abstract class GetDictIfMaterializedNode extends PNodeWithContext {

    public abstract PDict execute(PythonObject object);

    @Specialization(guards = {"object.getShape() == cachedShape", "!hasMaterializedDict(cachedShape)"}, limit = "1")
    static PDict getNoDictCachedShape(@SuppressWarnings("unused") PythonObject object,
                    @SuppressWarnings("unused") @Cached("object.getShape()") Shape cachedShape) {
        assert object.checkDictFlags();
        return null;
    }

    @Specialization(guards = "!hasMaterializedDict(object.getShape())", replaces = "getNoDictCachedShape")
    static PDict getNoDict(@SuppressWarnings("unused") PythonObject object) {
        assert object.checkDictFlags();
        return null;
    }

    @Specialization(guards = {"isSingleContext()", "hasMaterializedDict(object.getShape())", "object == cached", "dictIsConstant(cached)", "dict != null"}, limit = "1")
    static PDict getConstant(@SuppressWarnings("unused") PythonObject object,
                    @SuppressWarnings("unused") @Cached(value = "object", weak = true) PythonObject cached,
                    @Cached(value = "getDictUncached(object)", weak = true) PDict dict) {
        return dict;
    }

    @Specialization(guards = "hasMaterializedDict(object.getShape())", replaces = "getConstant")
    @InliningCutoff
    static PDict doPythonObject(PythonObject object,
                    @Bind Node inliningTarget,
                    @Cached ReadNode readHiddenAttrNode) {
        return (PDict) readHiddenAttrNode.execute(inliningTarget, object, HiddenAttr.DICT, null);
    }
}
