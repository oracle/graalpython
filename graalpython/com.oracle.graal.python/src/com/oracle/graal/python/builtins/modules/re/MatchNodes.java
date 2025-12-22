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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.TRegexUtil;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

import java.util.LinkedHashMap;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IndexError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

public class MatchNodes {

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class NewNode extends Node {

        public abstract Object execute(VirtualFrame frame, Node inliningTarget, PPattern pattern, Object regexResult, Object stringObject, int pos, int endPos);

        @Specialization
        public static Object getSlice(Node inliningTarget, PPattern pattern, Object regexResult, Object stringObject, int pos, int endPos,
                        @Bind PythonLanguage language,
                        @Cached PyUnicodeCheckNode unicodeCheckNode) {
            boolean isBytesLike = !unicodeCheckNode.execute(inliningTarget, stringObject);
            PythonBuiltinClassType cls = PythonBuiltinClassType.PMatch;
            Shape shape = cls.getInstanceShape(language);

            return new PMatch(cls, shape, regexResult, pattern, stringObject, isBytesLike, pos, endPos);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetSliceNode extends Node {
        static final int INVALID_RESULT_INDEX = -1;

        public abstract Object execute(VirtualFrame frame, Node inliningTarget, PMatch match, Object indexObject);

        @Specialization
        public static Object getSlice(VirtualFrame frame, Node inliningTarget, PMatch match, Object groupIndexObject,
                        @Bind PythonLanguage language,
                        @Cached GetGroupIndexNode getGroupIndexNode,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readStartNode,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readEndNode,
                        @Cached BytesNodes.BytesRangeFromObject bytesRangeFromObject,
                        @Cached CastToTruffleStringNode castToTruffleString,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached TruffleString.SubstringNode substringNode) {
            int groupIndex = getGroupIndexNode.execute(frame, inliningTarget, match, groupIndexObject);

            int start = TRegexUtil.TRegexResultAccessor.captureGroupStart(match.regexResult, groupIndex, inliningTarget, readStartNode);
            if (start == INVALID_RESULT_INDEX) {
                // it's an optional group that didn't match
                return PNone.NONE;
            }

            int end = TRegexUtil.TRegexResultAccessor.captureGroupEnd(match.regexResult, groupIndex, inliningTarget, readEndNode);
            if (end == INVALID_RESULT_INDEX) {
                // a group did match but cannot get a correct end position of a corresponding
                // substring
                throw CompilerDirectives.shouldNotReachHere();
            }

            // source can be modifiable (e.g. bytearray) so ensure start/end indices correctness
            int length = sizeNode.execute(frame, inliningTarget, match.string);
            int startAdjusted = Math.min(start, length);
            int endAdjusted = Math.min(end, length);

            if (match.isBytesLike) {
                byte[] bytes = bytesRangeFromObject.execute(frame, match.string, startAdjusted, endAdjusted);
                return PFactory.createBytes(language, bytes);
            } else {
                TruffleString tstring = castToTruffleString.execute(inliningTarget, match.string);
                return substringNode.execute(tstring, startAdjusted, endAdjusted - startAdjusted, TS_ENCODING, true);
            }
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetGroupIndexNode extends Node {
        public abstract int execute(VirtualFrame frame, Node inliningTarget, PMatch match, Object indexObject);

        @Specialization
        public static int getGroupIndex(VirtualFrame frame, Node inliningTarget, PMatch match, Object groupIndexObject,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode numberAsSizeNode,
                        @Cached PyUnicodeCheckNode unicodeCheckNode,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached PRaiseNode raiseNode) {
            final int groupIndex;

            if (indexCheckNode.execute(inliningTarget, groupIndexObject)) {
                groupIndex = numberAsSizeNode.execute(frame, inliningTarget, groupIndexObject, PNone.NONE);

                // index is 1-based, 0 - refers to the whole matched substring
                if (groupIndex < 0 || groupIndex > match.pattern.groupsCount) {
                    throw raiseNode.raise(inliningTarget, IndexError, ErrorMessages.NO_SUCH_GROUP);
                }
            } else if (unicodeCheckNode.execute(inliningTarget, groupIndexObject)) {
                String name = castToJavaStringNode.execute(groupIndexObject);
                Object value = getMapElement(match.pattern.groupToIndexMap, name);

                if (value == null) {
                    // no group with the given name
                    throw raiseNode.raise(inliningTarget, IndexError, ErrorMessages.NO_SUCH_GROUP);
                }

                if (!(value instanceof Integer valueInteger)) {
                    // expect that value is always Integer, that's RegexObject always provides
                    // Integer group indices
                    throw CompilerDirectives.shouldNotReachHere();
                }

                groupIndex = valueInteger;

                // index is 1-based
                if (groupIndex <= 0 || groupIndex > match.pattern.groupsCount) {
                    // the mapping contains invalid index value
                    throw CompilerDirectives.shouldNotReachHere();
                }
            } else {
                throw raiseNode.raise(inliningTarget, IndexError, ErrorMessages.NO_SUCH_GROUP);
            }

            return groupIndex;
        }

        @TruffleBoundary
        private static Object getMapElement(LinkedHashMap<String, Object> map, String key) {
            return map.get(key);
        }
    }
}
