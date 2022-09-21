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

import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ANNOTATIONS__;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.lib.PyDictGetItem;
import com.oracle.graal.python.lib.PyDictSetItem;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectSetItem;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;

@GenerateUncached
@ImportStatic(PArguments.class)
public abstract class SetupAnnotationsNode extends PNodeWithContext {
    public abstract void execute(Frame frame);

    @Specialization(guards = "locals != null")
    void doLocals(VirtualFrame frame,
                    @Bind("getSpecialArgument(frame)") Object locals,
                    @Shared("setup") @Cached SetupAnnotationsFromDictOrModuleNode setup) {
        setup.execute(frame, locals);
    }

    @Fallback
    void doGlobals(VirtualFrame frame,
                    @Shared("setup") @Cached SetupAnnotationsFromDictOrModuleNode setup) {
        setup.execute(frame, PArguments.getGlobals(frame));
    }

    @GenerateUncached
    abstract static class SetupAnnotationsFromDictOrModuleNode extends PNodeWithContext {
        public abstract void execute(Frame frame, Object locals);

        @Specialization(guards = "isBuiltinDict(locals)")
        static void doBuiltinDict(VirtualFrame frame, PDict locals,
                        @Cached PyDictGetItem getItem,
                        @Cached PyDictSetItem setItem,
                        @Cached PythonObjectFactory factory) {
            Object annotations = getItem.execute(frame, locals, T___ANNOTATIONS__);
            if (annotations == null) {
                setItem.execute(frame, locals, T___ANNOTATIONS__, factory.createDict());
            }
        }

        @Fallback
        void doOther(VirtualFrame frame, Object locals,
                        @Cached PyObjectGetItem getItem,
                        @Cached PyObjectSetItem setItem,
                        @Cached IsBuiltinClassProfile errorProfile,
                        @Cached PythonObjectFactory factory) {
            try {
                getItem.execute(frame, locals, T___ANNOTATIONS__);
            } catch (PException e) {
                e.expect(PythonBuiltinClassType.KeyError, errorProfile);
                setItem.execute(frame, locals, T___ANNOTATIONS__, factory.createDict());
            }
        }
    }

    public static SetupAnnotationsNode create() {
        return SetupAnnotationsNodeGen.create();
    }

    public static SetupAnnotationsNode getUncached() {
        return SetupAnnotationsNodeGen.getUncached();
    }
}
