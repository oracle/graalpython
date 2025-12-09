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
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.TRegexUtil;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.MpSubscriptBuiltinNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.oracle.graal.python.nodes.SpecialMethodNames.T___REPR__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING_BINARY;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PMatch})
public final class MatchBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = MatchBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MatchBuiltinsFactory.getFactories();
    }

    @Slot(value = Slot.SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {

        static final int INVALID_RESULT_INDEX = -1;
        static final int NOT_MATCHING_GROUP_START_INDEX = -1;
        static final int NOT_MATCHING_GROUP_END_INDEX = -1;

        @Specialization
        static TruffleString repr(VirtualFrame frame, PMatch self,
                        @Bind Node inliningTarget,
                        @Cached MatchNodes.GetSliceNode getSliceNode,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readStartNode,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readEndNode,
                        @Cached PyObjectCallMethodObjArgs callMethodObjArgs) {
            int groupIndex = 0; // points at the whole matching substring

            int start = TRegexUtil.TRegexResultAccessor.captureGroupStart(self.regexResult, groupIndex, inliningTarget, readStartNode);
            if (start == INVALID_RESULT_INDEX) {
                // it's an optional group that didn't match
                start = NOT_MATCHING_GROUP_START_INDEX;
            }

            int end = TRegexUtil.TRegexResultAccessor.captureGroupEnd(self.regexResult, groupIndex, inliningTarget, readEndNode);
            if (end == INVALID_RESULT_INDEX) {
                // it's an optional group that didn't match
                end = NOT_MATCHING_GROUP_END_INDEX;
            }

            if ((start == NOT_MATCHING_GROUP_START_INDEX) != (end == NOT_MATCHING_GROUP_END_INDEX)) {
                // either start or end index isn't obtained
                throw CompilerDirectives.shouldNotReachHere();
            }

            // either Python str or bytes
            Object substring = getSliceNode.execute(frame, inliningTarget, self, groupIndex);

            // matching substring as a String literal
            Object substringReprObject = callMethodObjArgs.execute(frame, inliningTarget, substring, T___REPR__);
            if (!(substringReprObject instanceof TruffleString substringRepr)) {
                throw CompilerDirectives.shouldNotReachHere();
            }

            return format(start, end, substringRepr);
        }

        @TruffleBoundary
        private static TruffleString format(int start, int end, TruffleString match) {
            String string = "<re.Match object; span=(" + start + ", " + end + "), match=" + match + ">";
            return TruffleString.FromJavaStringNode.getUncached().execute(string, TS_ENCODING);
        }
    }

    @Slot(value = Slot.SlotKind.mp_subscript, isComplex = true)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends MpSubscriptBuiltinNode {

        @Specialization
        public static Object getItem(VirtualFrame frame, PMatch self, Object indexObject,
                        @Bind Node inliningTarget,
                        @Cached MatchNodes.GetSliceNode getSliceNode) {
            return getSliceNode.execute(frame, inliningTarget, self, indexObject);
        }
    }

    @Builtin(name = "pos", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class PosNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int getPos(PMatch self) {
            return self.pos;
        }
    }

    @Builtin(name = "endpos", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class EndPosNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int getEndPos(PMatch self) {
            return self.endPos;
        }
    }

    @Builtin(name = "re", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class ReNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PPattern getRe(PMatch self) {
            return self.pattern;
        }
    }

    @Builtin(name = "string", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class StringNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object getString(PMatch self) {
            return self.string;
        }
    }

    @Builtin(name = "expand", minNumOfPositionalArgs = 2, parameterNames = {"$self", "template"})
    @GenerateNodeFactory
    public abstract static class ExpandNode extends PythonBuiltinNode {

        public static final TruffleString T_RE = tsLiteral("re");
        public static final TruffleString T__COMPILE_TEMPLATE = tsLiteral("_compile_template");

        @Specialization
        static Object expand(VirtualFrame frame, PMatch self, Object templateString,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached("createFor($node)") IndirectCallData.BoundaryCallData boundaryCallData,
                        @Cached PyObjectCallMethodObjArgs callMethodObjArgs,
                        @Cached PyUnicodeCheckNode unicodeCheckNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached SREModuleBuiltins.TRegexCallExec.BufferToTruffleStringNode bufferToTruffleStringNode,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached MatchNodes.GetSliceNode getSliceNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode,
                        @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            // Parse the given template string into a sequence of group indices and substring
            // literals. It's equivalent to the following Python code:
            // filter = re._compile_template(self.__re, template)
            PythonModule re = AbstractImportNode.importModule(frame, boundaryCallData, T_RE);
            Object templateObject = callMethodObjArgs.execute(frame, inliningTarget, re, T__COMPILE_TEMPLATE, self.pattern, templateString);
            if (!(templateObject instanceof PTemplate template)) {
                throw CompilerDirectives.shouldNotReachHere();
            }

            assert template.literals.length >= 1;
            boolean isBytesLikeTemplate = !unicodeCheckNode.execute(inliningTarget, template.literals[0]);

            final TruffleStringBuilder builder;
            if (isBytesLikeTemplate) {
                builder = TruffleStringBuilder.create(TS_ENCODING_BINARY);
            } else {
                assert TS_ENCODING == TruffleString.Encoding.UTF_32 : "use TruffleStringBuilderUTF8 after switching to UTF-8";
                builder = TruffleStringBuilder.createUTF32();
            }

            assert template.indices.length + 1 == template.literals.length;
            for (int i = 0; i < template.indices.length; i++) {
                // add literal
                Object literal = template.literals[i];

                TruffleString substring;
                if (isBytesLikeTemplate) {
                    substring = bufferToTruffleStringNode.execute(frame, literal);
                } else {
                    substring = castToTruffleStringNode.castKnownString(inliningTarget, literal);
                }

                appendStringNode.execute(builder, substring);

                // add captured group
                int groupIndex = template.indices[i];
                Object group = getSliceNode.execute(frame, inliningTarget, self, groupIndex);

                if (group != PNone.NONE) {
                    // it isn't an optional not matching group
                    if (isBytesLikeTemplate) {
                        substring = bufferToTruffleStringNode.execute(frame, group);
                    } else {
                        substring = castToTruffleStringNode.castKnownString(inliningTarget, group);
                    }

                    appendStringNode.execute(builder, substring);
                }
            }

            // add the extra literal
            Object literal = template.literals[template.literals.length - 1];
            TruffleString substring;
            if (isBytesLikeTemplate) {
                substring = bufferToTruffleStringNode.execute(frame, literal);
            } else {
                substring = castToTruffleStringNode.castKnownString(inliningTarget, literal);
            }
            appendStringNode.execute(builder, substring);

            TruffleString string = toStringNode.execute(builder);
            if (isBytesLikeTemplate) {
                byte[] bytes = copyToByteArrayNode.execute(string, TS_ENCODING_BINARY);
                return PFactory.createBytes(language, bytes);
            } else {
                return string;
            }
        }
    }

    @Builtin(name = "group", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class GroupNode extends PythonVarargsBuiltinNode {

        @Specialization
        static Object group(VirtualFrame frame, PMatch self, Object[] args, PKeyword[] keywords,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached MatchNodes.GetSliceNode getSliceNode) {
            if (args.length == 0) {
                // return the whole matching substring if no arguments given
                return getSliceNode.execute(frame, inliningTarget, self, 0);
            } else if (args.length == 1) {
                // return a single group if a single argument given
                return getSliceNode.execute(frame, inliningTarget, self, args[0]);
            } else {
                // return a tuple of groups
                Object[] groups = new Object[args.length];
                for (int i = 0; i < args.length; i++) {
                    Object group = getSliceNode.execute(frame, inliningTarget, self, args[i]);
                    groups[i] = group;
                }

                return PFactory.createTuple(language, groups);
            }
        }
    }

    @Builtin(name = "groups", minNumOfPositionalArgs = 1, parameterNames = {"self", "default"})
    @GenerateNodeFactory
    public abstract static class GroupsNode extends PythonBuiltinNode {

        @Specialization
        static Object groups(VirtualFrame frame, PMatch self, Object defaultValueArg,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached MatchNodes.GetSliceNode getSliceNode) {
            final Object defaultValue;
            if (defaultValueArg == PNone.NO_VALUE) {
                defaultValue = PNone.NONE;
            } else {
                defaultValue = defaultValueArg;
            }

            Object[] groups = new Object[self.pattern.groupsCount];
            for (int i = 0; i < self.pattern.groupsCount; i++) {
                int groupIndex = i + 1;
                Object group = getSliceNode.execute(frame, inliningTarget, self, groupIndex);

                if (group == PNone.NONE) {
                    groups[i] = defaultValue;
                } else {
                    groups[i] = group;
                }
            }

            return PFactory.createTuple(language, groups);
        }
    }

    @Builtin(name = "groupdict", minNumOfPositionalArgs = 1, parameterNames = {"self", "default"})
    @GenerateNodeFactory
    public abstract static class GroupDictNode extends PythonBuiltinNode {

        @Specialization
        static Object groupDict(VirtualFrame frame, PMatch self, Object defaultValueArg,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached MatchNodes.GetSliceNode getSliceNode) {
            final Object defaultValue;
            if (defaultValueArg == PNone.NO_VALUE) {
                defaultValue = PNone.NONE;
            } else {
                defaultValue = defaultValueArg;
            }

            Object[] keysAndValues = mapToArray(self.pattern.groupToIndexMap);

            for (int i = 0; i < keysAndValues.length; i += 2) {
                Object index = keysAndValues[i + 1];
                Object group = getSliceNode.execute(frame, inliningTarget, self, index);

                if (group == PNone.NONE) {
                    keysAndValues[i + 1] = defaultValue;
                } else {
                    keysAndValues[i + 1] = group;
                }
            }

            LinkedHashMap<String, Object> groupNameToGroupMap = arrayToMap(keysAndValues);
            return PFactory.createDictFromMap(language, groupNameToGroupMap);
        }

        @TruffleBoundary
        static Object[] mapToArray(LinkedHashMap<String, Object> map) {
            Object[] array = new Object[map.size() * 2];

            int i = 0;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                array[i++] = key;
                array[i++] = value;
            }

            return array;
        }

        @TruffleBoundary
        static LinkedHashMap<String, Object> arrayToMap(Object[] array) {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();

            for (int i = 0; i < array.length; i += 2) {
                String key = (String) array[i];
                Object value = array[i + 1];

                map.put(key, value);
            }

            return map;
        }
    }

    @Builtin(name = "start", minNumOfPositionalArgs = 1, parameterNames = {"self", "group"})
    @GenerateNodeFactory
    public abstract static class StartNode extends PythonBuiltinNode {
        static final int INVALID_RESULT_INDEX = -1;
        static final int NOT_MATCHING_GROUP_START_INDEX = -1;

        @Specialization
        static int start(VirtualFrame frame, PMatch self, Object groupIndexObject,
                        @Bind Node inliningTarget,
                        @Cached MatchNodes.GetGroupIndexNode getGroupIndexNode,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readStartNode) {
            final int groupIndex;

            if (groupIndexObject == PNone.NO_VALUE) {
                groupIndex = 0;
            } else {
                groupIndex = getGroupIndexNode.execute(frame, inliningTarget, self, groupIndexObject);
            }

            int start = TRegexUtil.TRegexResultAccessor.captureGroupStart(self.regexResult, groupIndex, inliningTarget, readStartNode);
            if (start == INVALID_RESULT_INDEX) {
                // it's an optional group that didn't match
                return NOT_MATCHING_GROUP_START_INDEX;
            }

            return start;
        }
    }

    @Builtin(name = "end", minNumOfPositionalArgs = 1, parameterNames = {"self", "group"})
    @GenerateNodeFactory
    public abstract static class EndNode extends PythonBuiltinNode {
        static final int INVALID_RESULT_INDEX = -1;
        static final int NOT_MATCHING_GROUP_END_INDEX = -1;

        @Specialization
        static int end(VirtualFrame frame, PMatch self, Object groupIndexObject,
                        @Bind Node inliningTarget,
                        @Cached MatchNodes.GetGroupIndexNode getGroupIndexNode,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readEndNode) {
            final int groupIndex;

            if (groupIndexObject == PNone.NO_VALUE) {
                groupIndex = 0;
            } else {
                groupIndex = getGroupIndexNode.execute(frame, inliningTarget, self, groupIndexObject);
            }

            int end = TRegexUtil.TRegexResultAccessor.captureGroupEnd(self.regexResult, groupIndex, inliningTarget, readEndNode);
            if (end == INVALID_RESULT_INDEX) {
                // it's an optional group that didn't match
                return NOT_MATCHING_GROUP_END_INDEX;
            }

            return end;
        }
    }

    @Builtin(name = "span", minNumOfPositionalArgs = 1, parameterNames = {"self", "group"})
    @GenerateNodeFactory
    public abstract static class SpanNode extends PythonBuiltinNode {
        static final int INVALID_RESULT_INDEX = -1;
        static final int NOT_MATCHING_GROUP_START_INDEX = -1;
        static final int NOT_MATCHING_GROUP_END_INDEX = -1;

        @Specialization
        static PTuple span(VirtualFrame frame, PMatch self, Object groupIndexObject,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached MatchNodes.GetGroupIndexNode getGroupIndexNode,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readStartNode,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readEndNode) {
            final int groupIndex;

            if (groupIndexObject == PNone.NO_VALUE) {
                groupIndex = 0;
            } else {
                groupIndex = getGroupIndexNode.execute(frame, inliningTarget, self, groupIndexObject);
            }

            int start = TRegexUtil.TRegexResultAccessor.captureGroupStart(self.regexResult, groupIndex, inliningTarget, readStartNode);
            if (start == INVALID_RESULT_INDEX) {
                // it's an optional group that didn't match
                start = NOT_MATCHING_GROUP_START_INDEX;
            }

            int end = TRegexUtil.TRegexResultAccessor.captureGroupEnd(self.regexResult, groupIndex, inliningTarget, readEndNode);
            if (end == INVALID_RESULT_INDEX) {
                // it's an optional group that didn't match
                end = NOT_MATCHING_GROUP_END_INDEX;
            }

            if ((start == NOT_MATCHING_GROUP_START_INDEX) != (end == NOT_MATCHING_GROUP_END_INDEX)) {
                // either start or end index isn't obtained
                throw CompilerDirectives.shouldNotReachHere();
            }

            return PFactory.createTuple(language, new int[]{start, end});
        }
    }

    /**
     * Match.regs is intentionally not documented and will be deprecated. @see
     * <a href="https://https://github.com/python/cpython/issues/62243"> related GitHub issue for
     * details</a>
     */
    @Builtin(name = "regs", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class RegsNode extends PythonBuiltinNode {
        static final int INVALID_RESULT_INDEX = -1;
        static final int NOT_MATCHING_GROUP_START_INDEX = -1;
        static final int NOT_MATCHING_GROUP_END_INDEX = -1;

        @Specialization
        static PTuple spans(PMatch self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readStartNode,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readEndNode) {
            PTuple[] spans = new PTuple[self.pattern.groupsCount + 1];

            // start from 0 - 0th group is the whole matching substring
            for (int groupIndex = 0; groupIndex < spans.length; groupIndex++) {
                int start = TRegexUtil.TRegexResultAccessor.captureGroupStart(self.regexResult, groupIndex, inliningTarget, readStartNode);
                if (start == INVALID_RESULT_INDEX) {
                    // it's an optional group that didn't match
                    start = NOT_MATCHING_GROUP_START_INDEX;
                }

                int end = TRegexUtil.TRegexResultAccessor.captureGroupEnd(self.regexResult, groupIndex, inliningTarget, readEndNode);
                if (end == INVALID_RESULT_INDEX) {
                    // it's an optional group that didn't match
                    end = NOT_MATCHING_GROUP_END_INDEX;
                }

                if ((start == NOT_MATCHING_GROUP_START_INDEX) != (end == NOT_MATCHING_GROUP_END_INDEX)) {
                    // either start or end index isn't obtained
                    throw CompilerDirectives.shouldNotReachHere();
                }

                spans[groupIndex] = PFactory.createTuple(language, new int[]{start, end});
            }

            return PFactory.createTuple(language, spans);
        }
    }

    @Builtin(name = "lastindex", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class LastIndexNode extends PythonUnaryBuiltinNode {
        static final int NO_MATCHING_GROUPS_CODE = -1;

        @Specialization
        static Object lastIndex(PMatch self,
                        @Bind Node inliningTarget,
                        @Cached TRegexUtil.InteropReadMemberNode interopReadMemberNode) {
            int groupIndex = TRegexUtil.TRegexResultAccessor.lastGroup(self.regexResult, inliningTarget, interopReadMemberNode);

            if (groupIndex == NO_MATCHING_GROUPS_CODE) {
                // no matching groups
                return PNone.NONE;
            }

            return groupIndex;
        }
    }

    @Builtin(name = "lastgroup", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class LastGroupNode extends PythonUnaryBuiltinNode {
        static final int NO_MATCHING_GROUPS_CODE = -1;

        @Specialization
        static Object lastGroup(PMatch self,
                        @Bind Node inliningTarget,
                        @Cached TRegexUtil.InteropReadMemberNode interopReadMemberNode,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            int groupIndex = TRegexUtil.TRegexResultAccessor.lastGroup(self.regexResult, inliningTarget, interopReadMemberNode);

            if (groupIndex == NO_MATCHING_GROUPS_CODE) {
                // no matching groups
                return PNone.NONE;
            }

            for (Map.Entry<String, Object> entry : self.pattern.groupToIndexMap.entrySet()) {
                Object value = entry.getValue();

                if (!(value instanceof Integer valueInteger)) {
                    // expect that value is always Integer, that's RegexObject always provides
                    // Integer group indices
                    throw CompilerDirectives.shouldNotReachHere();
                }

                if (valueInteger == groupIndex) {
                    // a named group found
                    String name = entry.getKey();
                    return fromJavaStringNode.execute(name, TS_ENCODING);
                }
            }

            // the last matched group does not have a name
            return PNone.NONE;
        }
    }

    @Builtin(name = "__copy__", minNumOfPositionalArgs = 1, parameterNames = {"$self"}, doc = "__copy__($self, /)\n--\n\n")
    @GenerateNodeFactory
    public abstract static class CopyNode extends PythonBuiltinNode {

        @Specialization
        static PMatch copy(PMatch self) {
            return self;
        }
    }

    @Builtin(name = "__deepcopy__", minNumOfPositionalArgs = 2, parameterNames = {"$self", "memo"}, doc = "__deepcopy__($self, memo, /)\n--\n\n")
    @GenerateNodeFactory
    public abstract static class DeepCopyNode extends PythonBuiltinNode {

        @Specialization
        static PMatch deepCopy(PMatch self, Object memo) {
            return self;
        }
    }
}
