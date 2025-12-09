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
import com.oracle.graal.python.builtins.modules.TRegexUtil;
import com.oracle.graal.python.builtins.modules.re.SREModuleBuiltins.PythonMethod;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

public class PatternNodes {

    @GenerateInline
    @GenerateCached(false)
    public abstract static class SearchNode extends Node {
        public static final int PYTHON_METHOD_COUNT = SREModuleBuiltins.PythonMethod.PYTHON_METHOD_COUNT;

        public abstract Object execute(VirtualFrame frame, Node inliningTarget, PPattern self, Object stringObject, int pos, int endPos, PythonMethod method, boolean mustAdvance);

        @Specialization(guards = {"isSingleContext()", "pattern == cachedPattern", "method == cachedMethod", "mustAdvance == cachedMustAdvance", "!cache.isLocaleSensitive()"}, limit = "1")
        @SuppressWarnings({"truffle-static-method", "unused"})
        public static Object getSliceCached(VirtualFrame frame, Node inliningTarget, PPattern pattern, Object stringObject, int pos, int endPos, PythonMethod method, boolean mustAdvance,
                        @CachedLibrary(limit = "1") @Shared InteropLibrary interop,
                        @Cached(value = "pattern", weak = true) PPattern cachedPattern,
                        @Cached("method") PythonMethod cachedMethod,
                        @Cached("mustAdvance") boolean cachedMustAdvance,
                        @Cached(value = "pattern.cache", weak = true) SREModuleBuiltins.TRegexCache cache,
                        @Cached(inline = false) @Shared SREModuleBuiltins.TRegexCompileInner tRegexCompileInnerNode,
                        @Cached(value = "tRegexCompileInnerNode.execute(frame, pattern.cache, method, mustAdvance)") Object regexObject,
                        @Cached @Shared SREModuleBuiltins.RECheckInputTypeNode reCheckInputTypeNode,
                        @Cached @Shared PyObjectSizeNode objectSizeNode,
                        @Cached(inline = false) @Shared SREModuleBuiltins.TRegexCallExec tRegexCallExecNode,
                        @Cached @Shared TRegexUtil.InteropReadMemberNode interopReadMemberNode,
                        @Cached @Shared MatchNodes.NewNode newMatchNode,
                        @Cached @Shared InlinedConditionProfile matchProfile) {
            reCheckInputTypeNode.execute(frame, stringObject, pattern.cache.isBinary());

            if (pos >= endPos) {
                return PNone.NONE;
            }

            int length = objectSizeNode.execute(frame, inliningTarget, stringObject);
            if (pos < 0) {
                pos = 0;
            } else if (pos > length) {
                pos = length;
            }
            if (endPos < 0) {
                endPos = 0;
            } else if (endPos > length) {
                endPos = length;
            }

            if (interop.isNull(regexObject)) {
                SREModuleBuiltins.bailoutUnsupportedRegex(pattern.cache);
            }

            Object regexResult = tRegexCallExecNode.execute(frame, regexObject, stringObject, pos, endPos);
            boolean isMatch = TRegexUtil.TRegexResultAccessor.isMatch(regexResult, inliningTarget, interopReadMemberNode);

            if (matchProfile.profile(inliningTarget, isMatch)) {
                return newMatchNode.execute(frame, inliningTarget, pattern, regexResult, stringObject, pos, endPos);
            } else {
                return PNone.NONE;
            }
        }

        @Specialization(guards = {"tRegexCompileInnerNode.execute(frame, pattern.cache, method, mustAdvance) == regexObject", "method == cachedMethod",
                "mustAdvance == cachedMustAdvance", "!cache.isLocaleSensitive()"}, limit = "1", replaces = "getSliceCached")
        @SuppressWarnings("truffle-static-method")
        public static Object getSliceCachedRegex(VirtualFrame frame, Node inliningTarget, PPattern pattern, Object stringObject, int pos, int endPos, PythonMethod method, boolean mustAdvance,
                        @CachedLibrary(limit = "1") @Shared InteropLibrary interop,
                        @Cached("method") PythonMethod cachedMethod,
                        @Cached("mustAdvance") @SuppressWarnings("unused") boolean cachedMustAdvance,
                        @Cached(inline = false) @Shared SREModuleBuiltins.TRegexCompileInner tRegexCompileInnerNode,
                        @Cached(value = "pattern.cache", weak = true) SREModuleBuiltins.TRegexCache cache,
                        @Cached(value = "tRegexCompileInnerNode.execute(frame, pattern.cache, method, mustAdvance)") Object regexObject,
                        @Cached @Shared SREModuleBuiltins.RECheckInputTypeNode reCheckInputTypeNode,
                        @Cached @Shared PyObjectSizeNode objectSizeNode,
                        @Cached(inline = false) @Shared SREModuleBuiltins.TRegexCallExec tRegexCallExecNode,
                        @Cached @Shared TRegexUtil.InteropReadMemberNode interopReadMemberNode,
                        @Cached @Shared MatchNodes.NewNode newMatchNode,
                        @Cached @Shared InlinedConditionProfile matchProfile) {
            return getSliceCached(frame, inliningTarget, pattern, stringObject, pos, endPos, method, mustAdvance,
                    interop, pattern, method, mustAdvance, cache, tRegexCompileInnerNode, regexObject, reCheckInputTypeNode, objectSizeNode, tRegexCallExecNode, interopReadMemberNode, newMatchNode, matchProfile);
        }

        @Specialization(guards = "method == cachedMethod", limit = "PYTHON_METHOD_COUNT", replaces = {"getSliceCached", "getSliceCachedRegex"})
        @SuppressWarnings("truffle-static-method")
        @ReportPolymorphism.Megamorphic
        public static Object getSliceDynamic(VirtualFrame frame, Node inliningTarget, PPattern pattern, Object stringObject, int pos, int endPos, PythonMethod method, boolean mustAdvance,
                        @Cached("method") PythonMethod cachedMethod,
                        @CachedLibrary(limit = "1") @Shared InteropLibrary interop,
                        @Cached(inline = false) @Shared SREModuleBuiltins.TRegexCompileInner tRegexCompileInnerNode,
                        @Cached @Shared SREModuleBuiltins.RECheckInputTypeNode reCheckInputTypeNode,
                        @Cached @Shared PyObjectSizeNode objectSizeNode,
                        @Cached(inline = false) @Shared SREModuleBuiltins.TRegexCallExec tRegexCallExecNode,
                        @Cached @Shared TRegexUtil.InteropReadMemberNode interopReadMemberNode,
                        @Cached @Shared MatchNodes.NewNode newMatchNode,
                        @Cached @Shared InlinedConditionProfile matchProfile) {
            Object regexObject = tRegexCompileInnerNode.execute(frame, pattern.cache, method, mustAdvance);

            return getSliceCached(frame, inliningTarget, pattern, stringObject, pos, endPos, method, mustAdvance,
                    interop, pattern, method, mustAdvance, pattern.cache, tRegexCompileInnerNode, regexObject, reCheckInputTypeNode, objectSizeNode, tRegexCallExecNode, interopReadMemberNode, newMatchNode, matchProfile);
        }

        @Idempotent
        public final boolean isSingleContext() {
            return PythonLanguage.get(this).isSingleContext();
        }
    }
}
