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

import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_OBJECT_GENERIC_SET_DICT;

import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.CheckPrimitiveFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

@GenerateUncached
@GenerateInline
@GenerateCached(false)
public abstract class SetDictNode extends PNodeWithContext {
    public abstract void execute(Node inliningTarget, Object object, PDict dict);

    public static void executeUncached(Object object, PDict dict) {
        SetDictNodeGen.getUncached().execute(null, object, dict);
    }

    @Specialization
    static void doPythonClass(Node inliningTarget, PythonClass object, PDict dict,
                    @Shared @Cached HiddenAttr.WriteNode writeHiddenAttrNode,
                    @Cached InlinedBranchProfile hasMroShapeProfile) {
        object.setDictHiddenProp(inliningTarget, writeHiddenAttrNode, hasMroShapeProfile, dict);
    }

    @Specialization(guards = "!isPythonClass(object)")
    static void doPythonObjectNotClass(Node inliningTarget, PythonObject object, PDict dict,
                    @Shared @Cached HiddenAttr.WriteNode writeHiddenAttrNode) {
        object.setDict(inliningTarget, writeHiddenAttrNode, dict);
    }

    @Specialization
    void doNativeObject(PythonAbstractNativeObject object, PDict dict,
                    @Cached(inline = false) PythonToNativeNode objectToSulong,
                    @Cached(inline = false) PythonToNativeNode dictToSulong,
                    @Cached(inline = false) CExtNodes.PCallCapiFunction callGetDictNode,
                    @Cached(inline = false) CheckPrimitiveFunctionResultNode checkResult) {
        assert !IsTypeNode.executeUncached(object);
        PythonContext context = getContext();
        Object result = callGetDictNode.call(FUN_PY_OBJECT_GENERIC_SET_DICT, objectToSulong.execute(object), dictToSulong.execute(dict), context.getNativeNull());
        checkResult.execute(context, FUN_PY_OBJECT_GENERIC_SET_DICT.getTsName(), result);
    }

    protected static boolean isPythonClass(Object object) {
        return object instanceof PythonClass;
    }
}
