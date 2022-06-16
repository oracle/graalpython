/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.KeyError;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.lib.PyDictCheckExactNode;
import com.oracle.graal.python.lib.PyDictGetItem;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;

@GenerateUncached
@ImportStatic(SpecialMethodSlot.class)
public abstract class GetNameFromLocalsNode extends PNodeWithContext {
    public abstract Object execute(Frame frame, Object locals, TruffleString name, boolean cellvar);

    @Specialization
    Object getValue(VirtualFrame frame, Object locals, TruffleString name, boolean isCellVar,
                    @Cached PyDictCheckExactNode checkDictNode,
                    @Cached PyDictGetItem getDictItemNode,
                    @Cached PyObjectGetItem getItemNode,
                    @Cached IsBuiltinClassProfile classProfile,
                    @Cached PRaiseNode raiseNode) {
        if (checkDictNode.execute(locals)) {
            return getDictItemNode.execute(frame, (PDict) locals, name);
        } else {
            try {
                return getItemNode.execute(frame, locals, name);
            } catch (PException pe) {
                if (!classProfile.profileClass(pe, KeyError)) {
                    if (isCellVar) {
                        throw raiseNode.raise(PythonBuiltinClassType.UnboundLocalError, ErrorMessages.LOCAL_VAR_REFERENCED_BEFORE_ASSIGMENT, name);
                    } else {
                        throw raiseNode.raise(PythonBuiltinClassType.NameError, ErrorMessages.UNBOUNDFREEVAR, name);
                    }
                }
            }
        }
        return null;
    }

    public static GetNameFromLocalsNode create() {
        return GetNameFromLocalsNodeGen.create();
    }

    public static GetNameFromLocalsNode getUncached() {
        return GetNameFromLocalsNodeGen.getUncached();
    }
}
