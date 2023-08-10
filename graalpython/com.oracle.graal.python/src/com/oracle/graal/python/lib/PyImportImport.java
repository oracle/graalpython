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
package com.oracle.graal.python.lib;

import static com.oracle.graal.python.nodes.BuiltinNames.T_BUILTINS;
import static com.oracle.graal.python.nodes.BuiltinNames.T_GLOBALS;
import static com.oracle.graal.python.nodes.BuiltinNames.T_LOCALS;
import static com.oracle.graal.python.nodes.BuiltinNames.T___BUILTINS__;
import static com.oracle.graal.python.nodes.BuiltinNames.T___IMPORT__;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.GetDictFromGlobalsNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Equivalent of CPython's {@code PyImport_Import}.
 */
@GenerateInline
@GenerateCached(false)
public abstract class PyImportImport extends Node {

    public static final TruffleString T_LEVEL = tsLiteral("level");
    public static final TruffleString T_FROMLIST = tsLiteral("fromlist");

    public abstract Object execute(VirtualFrame frame, Node inliningTarget, TruffleString name);

    @Specialization
    static Object doGeneric(VirtualFrame frame, Node inliningTarget, TruffleString moduleName,
                    @Cached InlinedConditionProfile noGlobalsProfile,
                    @Cached InlinedConditionProfile dictBuiltinsProfile,
                    @Cached PyImportGetModule importGetModule,
                    @Cached PyObjectGetItem getItemNode,
                    @Cached PyObjectGetAttr getAttrNode,
                    @Cached(inline = false) CallNode callNode,
                    @Cached(inline = false) AbstractImportNode.PyImportImportModuleLevelObject importModuleLevelObject,
                    @Cached PyEvalGetGlobals getGlobals,
                    @Cached(inline = false) GetDictFromGlobalsNode getDictFromGlobals,
                    @Cached(inline = false) PythonObjectFactory factory) {
        // Get the builtins from current globals
        Object globals = getGlobals.execute(frame, inliningTarget);
        Object builtins;
        if (noGlobalsProfile.profile(inliningTarget, globals != null)) {
            builtins = getItemNode.execute(frame, inliningTarget, getDictFromGlobals.execute(inliningTarget, globals), T___BUILTINS__);
        } else {
            // No globals -- use standard builtins, and fake globals
            builtins = importModuleLevelObject.execute(frame, PythonContext.get(inliningTarget), T_BUILTINS, null, null, 0);
            globals = factory.createDict(new PKeyword[]{new PKeyword(T___BUILTINS__, builtins)});
        }

        // Get the __import__ function from the builtins
        Object importFunc;
        if (dictBuiltinsProfile.profile(inliningTarget, builtins instanceof PDict)) {
            importFunc = getItemNode.execute(frame, inliningTarget, builtins, T___IMPORT__);
        } else {
            importFunc = getAttrNode.execute(frame, inliningTarget, builtins, T___IMPORT__);
        }

        // Call the __import__ function with the proper argument list Always use absolute import
        // here. Calling for side-effect of import.
        callNode.execute(importFunc, new Object[]{moduleName}, new PKeyword[]{
                        new PKeyword(T_GLOBALS, globals), new PKeyword(T_LOCALS, globals),
                        new PKeyword(T_FROMLIST, factory.createList()), new PKeyword(T_LEVEL, 0)
        });
        return importGetModule.execute(frame, inliningTarget, moduleName);
    }
}
