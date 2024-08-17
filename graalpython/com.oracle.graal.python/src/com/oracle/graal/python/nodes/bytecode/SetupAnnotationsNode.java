/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.function.PArguments.getSpecialArgument;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ANNOTATIONS__;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.lib.PyDictGetItem;
import com.oracle.graal.python.lib.PyDictSetItem;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectSetItem;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

@GenerateUncached
@ImportStatic(PArguments.class)
@GenerateInline(false) // used in BCI root node
@OperationProxy.Proxyable
public abstract class SetupAnnotationsNode extends PNodeWithContext {
    public abstract void execute(Frame frame);

    @Specialization
    public static void doLocals(VirtualFrame frame,
                    @Bind("this") Node inliningTarget,
                    @Cached InlinedConditionProfile hasLocals,
                    @Cached SetupAnnotationsFromDictOrModuleNode setup) {
        Object locals = getSpecialArgument(frame);
        if (hasLocals.profile(inliningTarget, locals != null)) {
            setup.execute(frame, inliningTarget, locals);
        } else {
            setup.execute(frame, inliningTarget, PArguments.getGlobals(frame));
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class SetupAnnotationsFromDictOrModuleNode extends PNodeWithContext {
        public abstract void execute(Frame frame, Node inliningTarget, Object locals);

        @Specialization
        static void doModule(PythonModule locals,
                        @Cached(inline = false) ReadAttributeFromObjectNode read,
                        @Cached(inline = false) WriteAttributeToObjectNode write,
                        @Shared("factory") @Cached(inline = false) PythonObjectFactory factory) {
            Object annotations = read.execute(locals, T___ANNOTATIONS__);
            if (annotations == PNone.NO_VALUE) {
                write.execute(locals, T___ANNOTATIONS__, factory.createDict());
            }
        }

        @Specialization(guards = "isBuiltinDict(locals)")
        static void doBuiltinDict(VirtualFrame frame, Node inliningTarget, PDict locals,
                        @Cached PyDictGetItem getItem,
                        @Cached PyDictSetItem setItem,
                        @Shared("factory") @Cached(inline = false) PythonObjectFactory factory) {
            Object annotations = getItem.execute(frame, inliningTarget, locals, T___ANNOTATIONS__);
            if (annotations == null) {
                setItem.execute(frame, inliningTarget, locals, T___ANNOTATIONS__, factory.createDict());
            }
        }

        @Fallback
        static void doOther(VirtualFrame frame, Node inliningTarget, Object locals,
                        @Cached PyObjectGetItem getItem,
                        @Cached PyObjectSetItem setItem,
                        @Cached IsBuiltinObjectProfile errorProfile,
                        @Shared("factory") @Cached(inline = false) PythonObjectFactory factory) {
            try {
                getItem.execute(frame, inliningTarget, locals, T___ANNOTATIONS__);
            } catch (PException e) {
                e.expect(inliningTarget, PythonBuiltinClassType.KeyError, errorProfile);
                setItem.execute(frame, inliningTarget, locals, T___ANNOTATIONS__, factory.createDict());
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
