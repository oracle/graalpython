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
package com.oracle.graal.python.nodes.object;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;

import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

@GenerateUncached
@GenerateInline(inlineByDefault = true)
@GenerateCached
@ImportStatic(PGuards.class)
public abstract class GetOrCreateDictNode extends PNodeWithContext {
    public abstract PDict execute(Node inliningTarget, Object object);

    public final PDict executeCached(Object object) {
        return execute(this, object);
    }

    public static PDict executeUncached(Object object) {
        return GetOrCreateDictNodeGen.getUncached().execute(null, object);
    }

    @Specialization
    static PDict doPythonObject(Node inliningTarget, PythonObject object,
                    @Shared("getDict") @Cached(inline = false) GetDictIfExistsNode getDictIfExistsNode,
                    @Cached SetDictNode setDictNode,
                    @Cached InlinedBranchProfile createDict,
                    @Cached(inline = false) PythonObjectFactory factory) {
        PDict dict = getDictIfExistsNode.execute(object);
        if (dict == null) {
            createDict.enter(inliningTarget);
            dict = factory.createDictFixedStorage(object);
            setDictNode.execute(inliningTarget, object, dict);
        }
        return dict;
    }

    @Specialization(guards = "!isPythonObject(object)")
    static PDict doOther(Node inliningTarget, Object object,
                    @Shared("getDict") @Cached(inline = false) GetDictIfExistsNode getDict) {
        PDict dict = getDict.execute(object);
        if (dict == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw PRaiseNode.raiseUncached(inliningTarget, SystemError, ErrorMessages.UNABLE_SET_DICT_OF_OBJ, object);
        }
        return dict;
    }

    @NeverDefault
    public static GetOrCreateDictNode create() {
        return GetOrCreateDictNodeGen.create();
    }
}
