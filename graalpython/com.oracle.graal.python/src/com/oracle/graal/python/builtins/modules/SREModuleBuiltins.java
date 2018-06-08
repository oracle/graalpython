/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(defineModule = "_sre")
public class SREModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
        return SREModuleBuiltinsFactory.getFactories();
    }

    /**
     * Called from C when they actually want a {@code const char*} for a Python string
     */
    @Builtin(name = "tregex_preprocess", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    abstract static class TregexPreprocessNode extends PythonUnaryBuiltinNode {
        @CompilationFinal private Pattern namedCaptGroupPattern;

        @Specialization
        Object run(PString str) {
            return run(str.getValue());
        }

        @Specialization
        Object run(String str) {
            if (namedCaptGroupPattern == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                namedCaptGroupPattern = Pattern.compile("\\?P\\<(?<GRPNAME>\\w*)\\>");
            }
            return replaceAll(str);
        }

        /**
         * replaces named capturing groups {@code ?P<name>} by {@code ?<name>}, removes comments and
         * whitespaces if they are not in a character class, and replaces end-of-string {@code \Z}
         * by {@code $}.
         */
        @TruffleBoundary(transferToInterpreterOnException = false, allowInlining = true)
        private String replaceAll(String r) {
            Matcher matcher0 = namedCaptGroupPattern.matcher(r);
            StringBuffer sb = new StringBuffer();
            while (matcher0.find()) {
                matcher0.appendReplacement(sb, "?<" + matcher0.group("GRPNAME") + ">");
            }
            matcher0.appendTail(sb);

            int charclassNestingLevel = 0;
            boolean inComment = false;
            for (int i = 0; i < sb.length();) {
                char c = sb.charAt(i);
                if (c == '[' && !inComment) {
                    charclassNestingLevel++;
                } else if (c == ']' && !inComment) {
                    charclassNestingLevel--;
                } else if (c == '#' && charclassNestingLevel == 0) {
                    inComment = true;
                } else if (c == '\n' && inComment) {
                    inComment = false;
                }
                if (inComment || (Character.isWhitespace(c) && charclassNestingLevel == 0)) {
                    sb.deleteCharAt(i);
                } else {
                    i++;
                }
            }

            for (int idx = sb.indexOf("\\Z"); idx != -1; idx = sb.indexOf("\\Z", idx + 2)) {
                sb.replace(idx, idx + 2, "$");
            }

            return sb.toString();
        }

        @Fallback
        Object run(Object o) {
            throw raise(PythonErrorType.TypeError, "expected string, not %p", o);
        }

    }

}
