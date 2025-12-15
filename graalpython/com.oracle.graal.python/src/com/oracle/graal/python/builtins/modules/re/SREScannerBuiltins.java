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
package com.oracle.graal.python.builtins.modules.re;

import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.TRegexUtil;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

import java.util.List;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.SREScanner})
public final class SREScannerBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SREScannerBuiltinsFactory.getFactories();
    }

    @Builtin(name = "pattern", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class PatternNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PPattern getPattern(PSREScanner self) {
            return self.pattern;
        }
    }

    @Builtin(name = "match", minNumOfPositionalArgs = 1, parameterNames = {"$self"}, forceSplitDirectCalls = true)
    @GenerateNodeFactory
    public abstract static class MatchNode extends PythonBuiltinNode {

        static final int INVALID_RESULT_INDEX = -1;

        @Specialization
        static Object match(VirtualFrame frame, PSREScanner self,
                        @Bind Node inliningTarget,
                        @Cached PatternNodes.SearchNode searchNode,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readEndNode) {
            Object matchObject = searchNode.execute(frame, inliningTarget, self.pattern, self.string, self.pos, self.endPos, PythonMethod.Match, self.mustAdvance);

            if (matchObject == PNone.NONE) {
                return PNone.NONE;
            } else if (matchObject instanceof PMatch match) {
                int groupIndex = 0; // points at the whole matching substring
                int endPos = TRegexUtil.TRegexResultAccessor.captureGroupEnd(match.regexResult, groupIndex, inliningTarget, readEndNode);

                if (endPos == INVALID_RESULT_INDEX) {
                    // end index isn't available
                    throw CompilerDirectives.shouldNotReachHere();
                }

                self.pos = endPos;
                return match;
            } else {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }

    @Builtin(name = "search", minNumOfPositionalArgs = 1, parameterNames = {"$self"}, forceSplitDirectCalls = true)
    @GenerateNodeFactory
    public abstract static class SearchNode extends PythonBuiltinNode {

        static final int INVALID_RESULT_INDEX = -1;

        @Specialization
        static Object search(VirtualFrame frame, PSREScanner self,
                        @Bind Node inliningTarget,
                        @Cached PatternNodes.SearchNode searchNode,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readStartNode,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readEndNode) {
            Object matchObject = searchNode.execute(frame, inliningTarget, self.pattern, self.string, self.pos, self.endPos, PythonMethod.Search, self.mustAdvance);

            if (matchObject == PNone.NONE) {
                return PNone.NONE;
            } else if (matchObject instanceof PMatch match) {
                int groupIndex = 0; // points at the whole matching substring

                int startPos = TRegexUtil.TRegexResultAccessor.captureGroupStart(match.regexResult, groupIndex, inliningTarget, readStartNode);
                if (startPos == INVALID_RESULT_INDEX) {
                    // start index isn't available
                    throw CompilerDirectives.shouldNotReachHere();
                }

                int endPos = TRegexUtil.TRegexResultAccessor.captureGroupEnd(match.regexResult, groupIndex, inliningTarget, readEndNode);
                if (endPos == INVALID_RESULT_INDEX) {
                    // end index isn't available
                    throw CompilerDirectives.shouldNotReachHere();
                }

                self.pos = endPos;

                // if empty match (and consequently searching returns '') then force the matcher to
                // advance by at least one character
                self.mustAdvance = startPos == endPos;
                return match;
            } else {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }
}
