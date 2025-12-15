/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.object.Shape;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = "_sre")
public final class SREModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SREModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        addBuiltinConstant("CODESIZE", 4);
        addBuiltinConstant("MAGIC", 20221023);
        addBuiltinConstant("MAXREPEAT", 4294967295L);
        addBuiltinConstant("MAXGROUPS", 2147483647);

        super.initialize(core);
    }

    @Builtin(name = "getcodesize", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    abstract static class GetCodeSizeNode extends PythonBuiltinNode {
        private static final TruffleString TS_GETCODESIZE_NOT_YET_IMPLEMENTED = tsLiteral("_sre.getcodesize is not yet implemented");

        @Specialization
        static Object getCodeSize(Object self, Object[] args, PKeyword[] keywords,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(inliningTarget, NotImplementedError, TS_GETCODESIZE_NOT_YET_IMPLEMENTED);
        }
    }

    @Builtin(name = "unicode_iscased", minNumOfPositionalArgs = 2, parameterNames = {"$module", "codepoint"}, doc = "unicode_iscased($module, character, /)\n--\n\n")
    @GenerateNodeFactory
    @ArgumentClinic(name = "codepoint", conversion = ArgumentClinic.ClinicConversion.Int)
    abstract static class UnicodeIsCasedNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SREModuleBuiltinsClinicProviders.UnicodeIsCasedNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        @TruffleBoundary
        static boolean isCased(Object module, int codepoint) {
            if (Character.isLetter(codepoint)) {
                return false;
            }

            return Character.toLowerCase(codepoint) != Character.toUpperCase(codepoint);
        }
    }

    @Builtin(name = "unicode_tolower", minNumOfPositionalArgs = 2, parameterNames = {"$module", "codepoint"}, doc = "unicode_tolower($module, character, /)\n--\n\n")
    @GenerateNodeFactory
    @ArgumentClinic(name = "codepoint", conversion = ArgumentClinic.ClinicConversion.Int)
    abstract static class UnicodeToLowerNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SREModuleBuiltinsClinicProviders.UnicodeToLowerNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        @TruffleBoundary
        static int toLower(Object module, int codepoint) {
            return Character.toLowerCase(codepoint);
        }
    }

    @Builtin(name = "ascii_iscased", minNumOfPositionalArgs = 2, parameterNames = {"$module", "codepoint"}, doc = "unicode_iscased($module, character, /)\n--\n\n")
    @GenerateNodeFactory
    @ArgumentClinic(name = "codepoint", conversion = ArgumentClinic.ClinicConversion.Int)
    abstract static class AsciiIsCasedNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SREModuleBuiltinsClinicProviders.AsciiIsCasedNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        @TruffleBoundary
        static boolean isCased(Object module, int codepoint) {
            return codepoint < 128 && Character.isLetter(codepoint);
        }
    }

    @Builtin(name = "ascii_tolower", minNumOfPositionalArgs = 2, parameterNames = {"$module", "codepoint"}, doc = "unicode_tolower($module, character, /)\n--\n\n")
    @GenerateNodeFactory
    @ArgumentClinic(name = "codepoint", conversion = ArgumentClinic.ClinicConversion.Int)
    abstract static class AsciiToLowerNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SREModuleBuiltinsClinicProviders.AsciiToLowerNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        @TruffleBoundary
        static int toLower(Object module, int codepoint) {
            if (codepoint >= 128) {
                return codepoint;
            }

            return Character.toLowerCase(codepoint);
        }
    }

    @Builtin(name = "template", minNumOfPositionalArgs = 2, parameterNames = {"pattern", "template"})
    @GenerateNodeFactory
    abstract static class TemplateNode extends PythonBuiltinNode {
        private static final TruffleString INVALID_TEMPLATE = tsLiteral("invalid template");

        @Specialization
        PTemplate template(VirtualFrame frame, PPattern pattern, PList template,
                        @Bind Node inliningTarget,
                        @Cached @Shared PRaiseNode raiseNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItem,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            // template is a list containing interleaved literal strings (str or bytes)
            // and group indices (int), as returned by _parser.parse_template:
            // [str 1, int 1, str 2, int 2, ..., str N-1, int N-1, str N].

            SequenceStorage storage = template.getSequenceStorage();
            int length = storage.length();

            if ((length & 1) == 0 || length < 1) {
                throw raiseNode.raise(inliningTarget, TypeError, INVALID_TEMPLATE);
            }

            Object[] literals = new Object[length / 2 + 1]; // there is an extra trailing literal
            int[] indices = new int[length / 2];
            for (int i = 0; i < length; i++) {
                Object item = getItem.execute(inliningTarget, storage, i);

                if ((i & 1) == 1) {
                    // group index
                    int index = asSizeNode.executeExact(frame, inliningTarget, item);
                    if (index < 0) {
                        throw raiseNode.raise(inliningTarget, TypeError, INVALID_TEMPLATE);
                    }

                    indices[i / 2] = index;
                } else {
                    // string (or bytes) literal
                    literals[i / 2] = item;
                }
            }

            Object cls = PythonBuiltinClassType.SRETemplate;
            Shape shape = getInstanceShape.execute(cls);
            return new PTemplate(cls, shape, literals, indices);
        }

        @Fallback
        PTemplate template(Object pattern, Object template,
                        @Bind Node inliningTarget,
                        @Cached @Shared PRaiseNode raiseNode) {
            throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.ARG_D_MUST_BE_S_NOT_P, "template()", 2, "list", template);
        }
    }
}
