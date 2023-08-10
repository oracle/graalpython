/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.method;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___FUNC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___WRAPPED__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ANNOTATIONS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ISABSTRACTMETHOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ISABSTRACTMETHOD__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.object.SetDictNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PStaticmethod, PythonBuiltinClassType.PClassmethod})
public final class DecoratedMethodBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return DecoratedMethodBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class InitNode extends PythonBinaryBuiltinNode {
        @Specialization
        protected PNone init(VirtualFrame frame, PDecoratedMethod self, Object callable,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectLookupAttr lookupModule,
                        @Cached PyObjectSetAttr setModule,
                        @Cached PyObjectLookupAttr lookupName,
                        @Cached PyObjectSetAttr setName,
                        @Cached PyObjectLookupAttr lookupQualname,
                        @Cached PyObjectSetAttr setQualname,
                        @Cached PyObjectLookupAttr lookupDoc,
                        @Cached PyObjectSetAttr setDoc,
                        @Cached PyObjectLookupAttr lookupAnnotations,
                        @Cached PyObjectSetAttr setAnnotations) {
            self.setCallable(callable);
            copyAttr(frame, inliningTarget, callable, self, T___MODULE__, lookupModule, setModule);
            copyAttr(frame, inliningTarget, callable, self, T___NAME__, lookupName, setName);
            copyAttr(frame, inliningTarget, callable, self, T___QUALNAME__, lookupQualname, setQualname);
            copyAttr(frame, inliningTarget, callable, self, T___DOC__, lookupDoc, setDoc);
            copyAttr(frame, inliningTarget, callable, self, T___ANNOTATIONS__, lookupAnnotations, setAnnotations);
            return PNone.NONE;
        }

        private static void copyAttr(VirtualFrame frame, Node inliningTarget, Object wrapped, Object wrapper, TruffleString name, PyObjectLookupAttr lookup, PyObjectSetAttr set) {
            Object attr = lookup.execute(frame, inliningTarget, wrapped, name);
            if (attr != PNone.NO_VALUE) {
                set.execute(frame, inliningTarget, wrapper, name, attr);
            }
        }
    }

    @Builtin(name = J___FUNC__, minNumOfPositionalArgs = 1, isGetter = true)
    @Builtin(name = J___WRAPPED__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class FuncNode extends PythonUnaryBuiltinNode {
        @Specialization
        protected Object func(PDecoratedMethod self) {
            return self.getCallable();
        }
    }

    @Builtin(name = J___DICT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    public abstract static class DictNode extends PythonBinaryBuiltinNode {
        @Specialization
        protected Object getDict(PDecoratedMethod self, @SuppressWarnings("unused") PNone mapping,
                        @Bind("this") Node inliningTarget,
                        @Cached GetOrCreateDictNode getDict) {
            return getDict.execute(inliningTarget, self);
        }

        @Specialization
        protected Object setDict(PDecoratedMethod self, PDict mapping,
                        @Bind("this") Node inliningTarget,
                        @Cached SetDictNode setDict) {
            setDict.execute(inliningTarget, self, mapping);
            return PNone.NONE;
        }

        @Specialization(guards = {"!isNoValue(mapping)", "!isDict(mapping)"})
        protected Object setDict(@SuppressWarnings("unused") PDecoratedMethod self, Object mapping) {
            throw raise(TypeError, ErrorMessages.DICT_MUST_BE_SET_TO_DICT, mapping);
        }
    }

    @Builtin(name = J___ISABSTRACTMETHOD__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class IsAbstractMethodNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean isAbstract(VirtualFrame frame, PDecoratedMethod self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectLookupAttr lookup,
                        @Cached PyObjectIsTrueNode isTrue,
                        @Cached InlinedConditionProfile hasAttrProfile) {
            Object result = lookup.execute(frame, inliningTarget, self.getCallable(), T___ISABSTRACTMETHOD__);
            if (hasAttrProfile.profile(inliningTarget, result != PNone.NO_VALUE)) {
                return isTrue.execute(frame, inliningTarget, result);
            }
            return false;
        }
    }
}
