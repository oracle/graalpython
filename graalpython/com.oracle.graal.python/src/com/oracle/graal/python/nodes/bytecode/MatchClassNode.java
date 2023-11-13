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

import static com.oracle.graal.python.builtins.objects.type.TypeFlags.MATCH_SELF;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MATCH_ARGS;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyObjectGetAttrO;
import com.oracle.graal.python.lib.PyTupleCheckExactNode;
import com.oracle.graal.python.lib.PyTupleSizeNode;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@GenerateInline(false) // used in BCI root node
public abstract class MatchClassNode extends PNodeWithContext {
    public abstract Object execute(Frame frame, Object subject, Object type, int nargs, TruffleString[] kwArgs);

    @Specialization
    Object match(VirtualFrame frame, Object subject, Object type, int nargs, @NeverDefault @SuppressWarnings("unused") TruffleString[] kwArgsArg,
                    @Bind("this") Node inliningTarget,
                    @Cached(value = "kwArgsArg", dimensions = 1) TruffleString[] kwArgs,
                    @Cached TypeNodes.IsTypeNode isTypeNode,
                    @Cached BuiltinFunctions.IsInstanceNode isInstanceNode,
                    @Cached PyObjectGetAttrO getAttr,
                    @Cached TypeNodes.GetTypeFlagsNode getTypeFlagsNode,
                    @Cached IsBuiltinObjectProfile isClassProfile,
                    @Cached StringBuiltins.EqNode eqStrNode,
                    @Cached PyTupleCheckExactNode tupleCheckExactNode,
                    @Cached PythonObjectFactory factory,
                    @Cached PyTupleSizeNode tupleSizeNode,
                    @Cached TupleBuiltins.GetItemNode getItemNode,
                    @Cached PyUnicodeCheckNode unicodeCheckNode,
                    @Cached PRaiseNode raise) {

        if (!isTypeNode.execute(inliningTarget, type)) {
            throw raise.raise(TypeError, ErrorMessages.CALLED_MATCH_PAT_MUST_BE_TYPE);
        }

        if (!isInstanceNode.executeWith(frame, subject, type)) {
            return null;
        }

        Object[] seen = new Object[nargs + kwArgs.length];
        int[] seenLength = {0};
        Object[] attrs;
        int[] attrsLength = {0};
        // First, the positional subpatterns:
        if (nargs > 0) {
            boolean matchSelf = false;
            Object matchArgs;
            try {
                matchArgs = getAttr.execute(frame, inliningTarget, type, T___MATCH_ARGS);
                if (!tupleCheckExactNode.execute(inliningTarget, matchArgs)) {
                    throw raise.raise(TypeError, ErrorMessages.P_MATCH_ARGS_MUST_BE_A_TUPLE_GOT_P, type, matchArgs);
                }
            } catch (PException e) {
                // _Py_TPFLAGS_MATCH_SELF is only acknowledged if the type does not
                // define __match_args__. This is natural behavior for subclasses:
                // it's as if __match_args__ is some "magic" value that is lost as
                // soon as they redefine it.
                e.expectAttributeError(inliningTarget, isClassProfile);
                matchArgs = factory.createEmptyTuple();
                matchSelf = (getTypeFlagsNode.execute(type) & MATCH_SELF) != 0;
            }
            int allowed = matchSelf ? 1 : tupleSizeNode.execute(inliningTarget, matchArgs);
            if (allowed < nargs) {
                throw raise.raise(TypeError, ErrorMessages.P_ACCEPTS_D_POS_SUBARG_S_D_GIVEN, type, allowed, (allowed == 1) ? "" : "s", nargs);
            }
            if (matchSelf) {
                // Easy. Copy the subject itself, and move on to kwargs.
                attrs = new Object[1 + kwArgs.length];
                attrs[attrsLength[0]++] = subject;
            } else {
                attrs = new Object[nargs + kwArgs.length];
                getArgs(frame, inliningTarget, subject, type, nargs, seen, seenLength, attrs, attrsLength, matchArgs, getAttr, eqStrNode, getItemNode, unicodeCheckNode, raise);
            }
        } else {
            attrs = new Object[kwArgs.length];
        }
        // Finally, the keyword subpatterns:
        getKwArgs(frame, inliningTarget, subject, type, kwArgs, seen, seenLength, attrs, attrsLength, getAttr, eqStrNode, raise);
        return factory.createList(attrs);
    }

    @ExplodeLoop
    private static void getArgs(VirtualFrame frame, Node inliningTarget, Object subject, Object type, int nargs, Object[] seen, int[] seenLength, Object[] attrs, int[] attrsLength, Object matchArgs,
                    PyObjectGetAttrO getAttr,
                    StringBuiltins.EqNode eqStrNode, TupleBuiltins.GetItemNode getItemNode, PyUnicodeCheckNode unicodeCheckNode, PRaiseNode raise) {
        CompilerAsserts.partialEvaluationConstant(nargs);
        for (int i = 0; i < nargs; i++) {
            Object name = getItemNode.execute(frame, matchArgs, i);
            if (!unicodeCheckNode.execute(inliningTarget, name)) {
                throw raise.raise(TypeError, ErrorMessages.MATCH_ARGS_ELEMENTS_MUST_BE_STRINGS_GOT_P, name);
            }
            setName(frame, type, name, seen, seenLength, eqStrNode, raise);
            attrs[attrsLength[0]++] = getAttr.execute(frame, inliningTarget, subject, name);
        }
    }

    @ExplodeLoop
    private static void getKwArgs(VirtualFrame frame, Node inliningTarget, Object subject, Object type, TruffleString[] kwArgs, Object[] seen, int[] seenLength, Object[] attrs, int[] attrsLength,
                    PyObjectGetAttrO getAttr,
                    StringBuiltins.EqNode eqStrNode, PRaiseNode raise) {
        CompilerAsserts.partialEvaluationConstant(kwArgs);
        for (int i = 0; i < kwArgs.length; i++) {
            TruffleString name = kwArgs[i];
            CompilerAsserts.partialEvaluationConstant(name);
            setName(frame, type, name, seen, seenLength, eqStrNode, raise);
            attrs[attrsLength[0]++] = getAttr.execute(frame, inliningTarget, subject, name);
        }
    }

    private static void setName(VirtualFrame frame, Object type, Object name, Object[] seen, int[] seenLength, StringBuiltins.EqNode eqNode, PRaiseNode raise) {
        if (seenLength[0] > 0 && contains(frame, seen, name, eqNode)) {
            throw raise.raise(TypeError, ErrorMessages.S_GOT_MULTIPLE_SUBPATTERNS_FOR_ATTR_S, type, name);
        }
        seen[seenLength[0]++] = name;
    }

    @ExplodeLoop
    private static boolean contains(VirtualFrame frame, Object[] seen, Object name, StringBuiltins.EqNode eqNode) {
        for (int i = 0; i < seen.length; i++) {
            if (seen[i] != null && (boolean) eqNode.execute(frame, seen[i], name)) {
                return true;
            }
        }
        return false;
    }

    public static MatchClassNode create() {
        return MatchClassNodeGen.create();
    }
}
