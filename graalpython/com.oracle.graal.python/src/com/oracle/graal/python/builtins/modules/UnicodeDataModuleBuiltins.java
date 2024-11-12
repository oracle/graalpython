/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import org.graalvm.shadowed.com.ibm.icu.lang.UCharacter;
import org.graalvm.shadowed.com.ibm.icu.lang.UProperty;
import org.graalvm.shadowed.com.ibm.icu.text.Normalizer2;
import org.graalvm.shadowed.com.ibm.icu.util.VersionInfo;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = "unicodedata")
public final class UnicodeDataModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return UnicodeDataModuleBuiltinsFactory.getFactories();
    }

    public static String getUnicodeVersion() {
        VersionInfo version = UCharacter.getUnicodeVersion();
        return Integer.toString(version.getMajor()) + '.' +
                        version.getMinor() + '.' +
                        version.getMicro();
    }

    /**
     * Returns the name of a Unicode codepoint or null if the name is unknown. Unlike CPython we
     * skip all codepoint in the private use areas.
     */
    public static String getUnicodeName(int cp) {
        if ((0xe000 <= cp && cp <= 0xf8ff) || (0xF0000 <= cp && cp <= 0xFFFFD) || (0x100000 <= cp && cp <= 0x10FFFD)) {
            return null;
        }
        return getUnicodeNameTB(cp);
    }

    @TruffleBoundary
    private static String getUnicodeNameTB(int cp) {
        return UCharacter.getName(cp);
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        addBuiltinConstant("unidata_version", getUnicodeVersion());
    }

    static final int NORMALIZER_FORM_COUNT = 4;

    @TruffleBoundary
    static Normalizer2 getNormalizer(TruffleString form) {
        return switch (form.toJavaStringUncached()) {
            case "NFC" -> Normalizer2.getNFCInstance();
            case "NFKC" -> Normalizer2.getNFKCInstance();
            case "NFD" -> Normalizer2.getNFDInstance();
            case "NFKD" -> Normalizer2.getNFKDInstance();
            default -> null;
        };
    }

    // unicodedata.normalize(form, unistr)
    @Builtin(name = "normalize", minNumOfPositionalArgs = 2, parameterNames = {"form", "unistr"})
    @ArgumentClinic(name = "form", conversion = ArgumentClinic.ClinicConversion.TString)
    @ArgumentClinic(name = "unistr", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    @ImportStatic(UnicodeDataModuleBuiltins.class)
    public abstract static class NormalizeNode extends PythonBinaryClinicBuiltinNode {
        @Specialization(guards = {"cachedNormalizer != null", "stringEquals(form, cachedForm, equalNode)"}, limit = "NORMALIZER_FORM_COUNT")
        static TruffleString normalize(@SuppressWarnings("unused") TruffleString form, TruffleString unistr,
                        @SuppressWarnings("unused") @Cached("form") TruffleString cachedForm,
                        @Cached("getNormalizer(cachedForm)") Normalizer2 cachedNormalizer,
                        @SuppressWarnings("unused") @Cached TruffleString.EqualNode equalNode,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @Exclusive @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            return fromJavaStringNode.execute(normalize(toJavaStringNode.execute(unistr), cachedNormalizer), TS_ENCODING);
        }

        @Specialization(guards = "getNormalizer(form) == null")
        TruffleString invalidForm(@SuppressWarnings("unused") TruffleString form, @SuppressWarnings("unused") TruffleString unistr) {
            throw PRaiseNode.raiseUncached(this, ValueError, ErrorMessages.INVALID_NORMALIZATION_FORM);
        }

        @TruffleBoundary
        private static String normalize(String str, Normalizer2 normalizer) {
            return normalizer.normalize(str);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return UnicodeDataModuleBuiltinsClinicProviders.NormalizeNodeClinicProviderGen.INSTANCE;
        }
    }

    // unicodedata.is_normalized(form, unistr)
    @Builtin(name = "is_normalized", minNumOfPositionalArgs = 2, parameterNames = {"form", "unistr"})
    @ArgumentClinic(name = "form", conversion = ArgumentClinic.ClinicConversion.TString)
    @ArgumentClinic(name = "unistr", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    @ImportStatic(UnicodeDataModuleBuiltins.class)
    public abstract static class IsNormalizedNode extends PythonBinaryClinicBuiltinNode {
        @Specialization(guards = {"cachedNormalizer != null", "stringEquals(form, cachedForm, equalNode)"}, limit = "NORMALIZER_FORM_COUNT")
        @TruffleBoundary
        boolean isNormalized(@SuppressWarnings("unused") TruffleString form, TruffleString unistr,
                        @SuppressWarnings("unused") @Cached("form") TruffleString cachedForm,
                        @Cached("getNormalizer(cachedForm)") Normalizer2 cachedNormalizer,
                        @SuppressWarnings("unused") @Cached TruffleString.EqualNode equalNode) {
            return cachedNormalizer.isNormalized(unistr.toJavaStringUncached());
        }

        @Specialization(guards = "getNormalizer(form) == null")
        TruffleString invalidForm(@SuppressWarnings("unused") TruffleString form, @SuppressWarnings("unused") TruffleString unistr) {
            throw PRaiseNode.raiseUncached(this, ValueError, ErrorMessages.INVALID_NORMALIZATION_FORM);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return UnicodeDataModuleBuiltinsClinicProviders.IsNormalizedNodeClinicProviderGen.INSTANCE;
        }
    }

    // unicodedata.name(chr, default)
    @Builtin(name = "name", minNumOfPositionalArgs = 1, parameterNames = {"chr", "default"})
    @ArgumentClinic(name = "chr", conversion = ArgumentClinic.ClinicConversion.CodePoint)
    @GenerateNodeFactory
    public abstract static class NameNode extends PythonBinaryClinicBuiltinNode {

        @Specialization
        static Object name(int cp, Object defaultValue,
                        @Bind("this") Node inliningTarget,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            String result = getUnicodeName(cp);
            if (result == null) {
                if (defaultValue == PNone.NO_VALUE) {
                    throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.NO_SUCH_NAME);
                }
                return defaultValue;
            }
            return fromJavaStringNode.execute(result, TS_ENCODING);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return UnicodeDataModuleBuiltinsClinicProviders.NameNodeClinicProviderGen.INSTANCE;
        }
    }

    // unicodedata.bidirectional(char)
    @Builtin(name = "bidirectional", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"chr"})
    @ArgumentClinic(name = "chr", conversion = ArgumentClinic.ClinicConversion.CodePoint)
    @GenerateNodeFactory
    public abstract static class BidirectionalNode extends PythonUnaryClinicBuiltinNode {
        @Specialization
        static TruffleString bidirectional(int chr,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            return fromJavaStringNode.execute(getBidiClassName(chr), TS_ENCODING);
        }

        @TruffleBoundary
        private static String getBidiClassName(int chr) {
            return UCharacter.getPropertyValueName(UProperty.BIDI_CLASS, UCharacter.getDirection(chr), UProperty.NameChoice.SHORT);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return UnicodeDataModuleBuiltinsClinicProviders.BidirectionalNodeClinicProviderGen.INSTANCE;
        }
    }

    // unicodedata.category(char)
    @Builtin(name = "category", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"chr"})
    @ArgumentClinic(name = "chr", conversion = ArgumentClinic.ClinicConversion.CodePoint)
    @GenerateNodeFactory
    public abstract static class CategoryNode extends PythonUnaryClinicBuiltinNode {
        @Specialization
        static TruffleString category(int chr,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            return fromJavaStringNode.execute(getCategoryName(chr), TS_ENCODING);
        }

        @TruffleBoundary
        private static String getCategoryName(int chr) {
            return UCharacter.getPropertyValueName(UProperty.GENERAL_CATEGORY, UCharacter.getType(chr), UProperty.NameChoice.SHORT);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return UnicodeDataModuleBuiltinsClinicProviders.CategoryNodeClinicProviderGen.INSTANCE;
        }
    }
}
