/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.frame;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.NameError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@GenerateUncached
@GenerateInline(false)       // footprint reduction 40 -> 21
public abstract class ReadBuiltinNode extends PNodeWithContext {
    public abstract Object execute(TruffleString attributeId);

    // TODO: (tfel) Think about how we can globally catch writes to the builtin
    // module so we can treat anything read from it as constant here.
    @Specialization(guards = "isSingleContext(this)")
    Object returnBuiltinFromConstantModule(TruffleString attributeId,
                    @Bind Node inliningTarget,
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Exclusive @Cached InlinedConditionProfile isBuiltinProfile,
                    @Shared @Cached ReadAttributeFromObjectNode readFromBuiltinsNode,
                    @Cached(value = "getBuiltins()", allowUncached = true) PythonModule builtins) {
        return readBuiltinFromModule(attributeId, raiseNode, inliningTarget, isBuiltinProfile, builtins, readFromBuiltinsNode);
    }

    @InliningCutoff
    private static PException raiseNameNotDefined(Node inliningTarget, PRaiseNode raiseNode, TruffleString attributeId) {
        throw raiseNode.raise(inliningTarget, NameError, ErrorMessages.NAME_NOT_DEFINED, attributeId);
    }

    @InliningCutoff
    @Specialization(replaces = "returnBuiltinFromConstantModule")
    Object returnBuiltin(TruffleString attributeId,
                    @Bind Node inliningTarget,
                    @Exclusive @Cached PRaiseNode raiseNode,
                    @Exclusive @Cached InlinedConditionProfile isBuiltinProfile,
                    @Shared @Cached ReadAttributeFromObjectNode readFromBuiltinsNode,
                    @Exclusive @Cached InlinedConditionProfile ctxInitializedProfile) {
        PythonModule builtins = getBuiltins(inliningTarget, ctxInitializedProfile);
        return returnBuiltinFromConstantModule(attributeId, inliningTarget, raiseNode, isBuiltinProfile, readFromBuiltinsNode, builtins);
    }

    private static Object readBuiltinFromModule(TruffleString attributeId, PRaiseNode raiseNode, Node inliningTarget,
                    InlinedConditionProfile isBuiltinProfile, PythonModule builtins,
                    ReadAttributeFromObjectNode readFromBuiltinsNode) {
        Object builtin = readFromBuiltinsNode.execute(builtins, attributeId);
        if (isBuiltinProfile.profile(inliningTarget, builtin != PNone.NO_VALUE)) {
            return builtin;
        } else {
            throw raiseNameNotDefined(inliningTarget, raiseNode, attributeId);
        }
    }

    @NeverDefault
    protected PythonModule getBuiltins() {
        CompilerAsserts.neverPartOfCompilation();
        return getBuiltins(null, InlinedConditionProfile.getUncached());
    }

    protected PythonModule getBuiltins(Node inliningTarget, InlinedConditionProfile ctxInitializedProfile) {
        PythonContext context = PythonContext.get(this);
        if (ctxInitializedProfile.profile(inliningTarget, context.isInitialized())) {
            return context.getBuiltins();
        } else {
            return context.lookupBuiltinModule(BuiltinNames.T_BUILTINS);
        }
    }

    @NeverDefault
    public static ReadBuiltinNode create() {
        return ReadBuiltinNodeGen.create();
    }

    public static ReadBuiltinNode getUncached() {
        return ReadBuiltinNodeGen.getUncached();
    }
}
