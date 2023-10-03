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
package com.oracle.graal.python.builtins.modules.ast;

import static com.oracle.graal.python.builtins.modules.ast.AstModuleBuiltins.T__FIELDS;
import static com.oracle.graal.python.nodes.ErrorMessages.IS_NOT_A_SEQUENCE;
import static com.oracle.graal.python.nodes.ErrorMessages.P_GOT_MULTIPLE_VALUES_FOR_ARGUMENT_S;
import static com.oracle.graal.python.nodes.ErrorMessages.S_CONSTRUCTOR_TAKES_AT_MOST_D_POSITIONAL_ARGUMENT_S;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DICT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_OBJECT_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.object.SetDictNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.AST)
public final class AstBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return AstBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    abstract static class InitNode extends PythonVarargsBuiltinNode {

        // TODO CPython uses PySequence_Size, PySequence_GetItem, PySequence_Contains and
        // PySequence_Contains. The code here works for the generated classes, but may
        // behave differently in some corner cases.

        @Specialization
        protected Object doIt(VirtualFrame frame, Object self, Object[] args, PKeyword[] kwArgs,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached SequenceNodes.GetObjectArrayNode getObjectArrayNode,
                        @Cached PyObjectSetAttr setAttrNode,
                        @Cached TruffleString.EqualNode equalNode) {
            Object fieldsObj = lookupAttrNode.execute(frame, inliningTarget, self, T__FIELDS);
            Object[] fields;
            if (fieldsObj == PNone.NO_VALUE) {
                fields = EMPTY_OBJECT_ARRAY;
            } else {
                if (!(fieldsObj instanceof PSequence)) {
                    throw raise(TypeError, IS_NOT_A_SEQUENCE, fieldsObj);
                }
                fields = getObjectArrayNode.execute(inliningTarget, fieldsObj);
            }
            if (fields.length < args.length) {
                throw raise(TypeError, S_CONSTRUCTOR_TAKES_AT_MOST_D_POSITIONAL_ARGUMENT_S, self, fields.length, fields.length == 1 ? "" : "s");
            }
            for (int i = 0; i < args.length; ++i) {
                setAttrNode.execute(frame, inliningTarget, self, fields[i], args[i]);
            }
            for (PKeyword kwArg : kwArgs) {
                if (contains(fields, args.length, kwArg.getName(), equalNode)) {
                    throw raise(TypeError, P_GOT_MULTIPLE_VALUES_FOR_ARGUMENT_S, self, kwArg.getName());
                }
                setAttrNode.execute(frame, inliningTarget, self, kwArg.getName(), kwArg.getValue());
            }
            return PNone.NONE;
        }

        @TruffleBoundary
        private static boolean contains(Object[] fields, int maxIndex, TruffleString name, TruffleString.EqualNode equalNode) {
            for (int i = 0; i < maxIndex; ++i) {
                if (fields[i] instanceof TruffleString && equalNode.execute(name, (TruffleString) fields[i], TS_ENCODING)) {
                    return true;
                }
            }
            return false;
        }
    }

    @Builtin(name = J___DICT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class DictNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNoValue(none)")
        static Object doit(PythonObject self, @SuppressWarnings("unused") PNone none,
                        @Bind("this") Node inliningTarget,
                        @Cached GetOrCreateDictNode getDict) {
            return getDict.execute(inliningTarget, self);
        }

        @Specialization
        static Object dict(PythonObject self, PDict dict,
                        @Bind("this") Node inliningTarget,
                        @Cached SetDictNode setDict) {
            setDict.execute(inliningTarget, self, dict);
            return PNone.NONE;
        }

        @Specialization(guards = {"!isNoValue(d)", "!isDict(d)"})
        @SuppressWarnings("unused")
        static Object setDict(PythonObject self, Object d,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.DICT_MUST_BE_SET_TO_DICT, d);
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        static Object doit(VirtualFrame frame, PythonObject self, Object ignored,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached PyObjectLookupAttr lookupAttr,
                        @Cached PythonObjectFactory factory) {
            Object clazz = getClassNode.execute(inliningTarget, self);
            Object dict = lookupAttr.execute(frame, inliningTarget, self, T___DICT__);
            return factory.createTuple(new Object[]{clazz, factory.createTuple(EMPTY_OBJECT_ARRAY), dict});
        }
    }
}
