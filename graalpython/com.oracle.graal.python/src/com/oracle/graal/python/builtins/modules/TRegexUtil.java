/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public final class TRegexUtil {

    private TRegexUtil() {
        // should not be constructed
    }

    public static final class Props {
        private Props() {
            // should not be constructed
        }

        public static final class CompiledRegex {
            private CompiledRegex() {
                // should not be constructed
            }

            public static final String PATTERN = "pattern";
            public static final String FLAGS = "flags";
            public static final String EXEC = "exec";
            public static final String GROUP_COUNT = "groupCount";
            public static final String GROUPS = "groups";
        }

        public static final class RegexResult {
            private RegexResult() {
                // should not be constructed
            }

            public static final String IS_MATCH = "isMatch";
            public static final String GET_START = "getStart";
            public static final String GET_END = "getEnd";
        }
    }

    private static final String NUMBER_OF_REGEX_RESULT_TYPES = "1";

    @GenerateCached
    @GenerateInline(inlineByDefault = true)
    @GenerateUncached
    public abstract static class InteropReadMemberNode extends Node {

        public abstract Object execute(Node node, Object obj, String key);

        @Specialization(limit = NUMBER_OF_REGEX_RESULT_TYPES)
        static Object read(Object obj, String key,
                        @CachedLibrary("obj") InteropLibrary objs) {
            try {
                return objs.readMember(obj, key);
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @GenerateCached(false)
    @GenerateInline
    @GenerateUncached
    public abstract static class InvokeExecMethodNode extends Node {

        public abstract Object execute(Node inliningTarget, Object compiledRegex, TruffleString input, int fromIndex);

        @Specialization(limit = NUMBER_OF_REGEX_RESULT_TYPES)
        static Object exec(Object compiledRegex, TruffleString input, int fromIndex,
                        @CachedLibrary("compiledRegex") InteropLibrary objs) {
            try {
                return objs.invokeMember(compiledRegex, Props.CompiledRegex.EXEC, input, fromIndex);
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException | UnknownIdentifierException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @GenerateCached(false)
    @GenerateInline
    @GenerateUncached
    public abstract static class InvokeExecMethodWithMaxIndexNode extends Node {

        public abstract Object execute(Node inliningTarget, Object compiledRegex, TruffleString input, int fromIndex, int toIndex);

        @Specialization(limit = NUMBER_OF_REGEX_RESULT_TYPES)
        static Object exec(Object compiledRegex, TruffleString input, int fromIndex, int toIndex,
                        @CachedLibrary("compiledRegex") InteropLibrary objs) {
            try {
                return objs.invokeMember(compiledRegex, Props.CompiledRegex.EXEC, input, fromIndex, toIndex, 0, toIndex);
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException | UnknownIdentifierException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @GenerateCached(false)
    @GenerateInline
    @GenerateUncached
    public abstract static class ReadIsMatchNode extends Node {
        static final String IS_MATCH = "isMatch";

        public abstract boolean execute(Node inliningTarget, Object regexResult);

        @Specialization(limit = NUMBER_OF_REGEX_RESULT_TYPES)
        static boolean read(Object regexResult, @CachedLibrary("regexResult") InteropLibrary objs) {
            try {
                return (boolean) objs.readMember(regexResult, IS_MATCH);
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @GenerateCached(false)
    @GenerateInline
    @GenerateUncached
    public abstract static class InvokeGetGroupBoundariesMethodNode extends Node {

        public abstract int execute(Node inliningTarget, Object regexResult, Object method, int groupNumber);

        @Specialization(limit = NUMBER_OF_REGEX_RESULT_TYPES)
        static int exec(Object regexResult, String method, int groupNumber,
                        @CachedLibrary("regexResult") InteropLibrary objs) {
            try {
                return (int) objs.invokeMember(regexResult, method, groupNumber);
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException | UnknownIdentifierException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    public static final class TRegexCompiledRegexAccessor {

        private TRegexCompiledRegexAccessor() {
        }

        public static String pattern(Object compiledRegexObject, Node node, InteropReadMemberNode readPattern) {
            return (String) readPattern.execute(node, compiledRegexObject, Props.CompiledRegex.PATTERN);
        }

        public static Object flags(Object compiledRegexObject, Node node, InteropReadMemberNode readFlags) {
            return readFlags.execute(node, compiledRegexObject, Props.CompiledRegex.FLAGS);
        }

        public static int groupCount(Object compiledRegexObject, Node node, InteropReadMemberNode readGroupCount) {
            return (int) readGroupCount.execute(node, compiledRegexObject, Props.CompiledRegex.GROUP_COUNT);
        }

        public static Object namedCaptureGroups(Object compiledRegexObject, Node node, InteropReadMemberNode readGroups) {
            return readGroups.execute(node, compiledRegexObject, Props.CompiledRegex.GROUPS);
        }
    }

    public static final class TRegexNamedCaptureGroupsAccessor {

        private TRegexNamedCaptureGroupsAccessor() {
        }

        public static boolean hasGroup(Object namedCaptureGroupsMap, TruffleString name, InteropLibrary interop) {
            return interop.isMemberReadable(namedCaptureGroupsMap, name.toJavaStringUncached());
        }

        public static int getGroupNumber(Object namedCaptureGroupsMap, TruffleString name, InteropLibrary libMap) {
            try {
                return (int) libMap.readMember(namedCaptureGroupsMap, name.toJavaStringUncached());
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    public static final class TRegexResultAccessor {

        private TRegexResultAccessor() {
        }

        public static boolean isMatch(Object result, Node node, InteropReadMemberNode readIsMatch) {
            return (boolean) readIsMatch.execute(node, result, Props.RegexResult.IS_MATCH);
        }

        public static int captureGroupStart(Object result, int groupNumber, Node node, InvokeGetGroupBoundariesMethodNode getStart) {
            return getStart.execute(node, result, Props.RegexResult.GET_START, groupNumber);
        }

        public static int captureGroupEnd(Object result, int groupNumber, Node node, InvokeGetGroupBoundariesMethodNode getEnd) {
            return getEnd.execute(node, result, Props.RegexResult.GET_END, groupNumber);
        }
    }
}
