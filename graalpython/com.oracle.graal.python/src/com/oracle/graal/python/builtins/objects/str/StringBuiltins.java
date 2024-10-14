/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.objects.str;

import static com.oracle.graal.python.nodes.BuiltinNames.J_ENCODE;
import static com.oracle.graal.python.nodes.BuiltinNames.J_ENDSWITH;
import static com.oracle.graal.python.nodes.BuiltinNames.J_FORMAT;
import static com.oracle.graal.python.nodes.BuiltinNames.J_FORMAT_MAP;
import static com.oracle.graal.python.nodes.BuiltinNames.J_REMOVEPREFIX;
import static com.oracle.graal.python.nodes.BuiltinNames.J_REMOVESUFFIX;
import static com.oracle.graal.python.nodes.BuiltinNames.J_STARTSWITH;
import static com.oracle.graal.python.nodes.BuiltinNames.T_ENDSWITH;
import static com.oracle.graal.python.nodes.BuiltinNames.T_FORMAT;
import static com.oracle.graal.python.nodes.BuiltinNames.T_STARTSWITH;
import static com.oracle.graal.python.nodes.ErrorMessages.FILL_CHAR_MUST_BE_UNICODE_CHAR_NOT_P;
import static com.oracle.graal.python.nodes.ErrorMessages.S_ENCODER_RETURNED_P_INSTEAD_OF_BYTES;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___FORMAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETNEWARGS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___MOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___STR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___TRUFFLE_RICHCOMPARE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETNEWARGS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___STR__;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.assertNoJavaString;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.IndexError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsbCapacity;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oracle.truffle.api.dsl.Fallback;
import org.graalvm.shadowed.com.ibm.icu.lang.UCharacter;
import org.graalvm.shadowed.com.ibm.icu.lang.UProperty;
import org.graalvm.shadowed.com.ibm.icu.text.CaseMap;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.FormatNodeBase;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorValue;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.iterator.PStringIterator;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins.ListReverseNode;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.range.RangeNodes.LenOfRangeNode;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes.CoerceToIntSlice;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes.ComputeIndices;
import com.oracle.graal.python.builtins.objects.str.StringBuiltinsClinicProviders.FormatNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.str.StringBuiltinsClinicProviders.SplitNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.str.StringNodes.CastToJavaStringCheckedNode;
import com.oracle.graal.python.builtins.objects.str.StringNodes.CastToTruffleStringCheckedNode;
import com.oracle.graal.python.builtins.objects.str.StringNodes.JoinInternalNode;
import com.oracle.graal.python.builtins.objects.str.StringNodes.SpliceNode;
import com.oracle.graal.python.builtins.objects.str.StringNodes.StringLenNode;
import com.oracle.graal.python.builtins.objects.str.StringNodes.StringReplaceNode;
import com.oracle.graal.python.builtins.objects.str.StringUtils.StripKind;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.MpSubscriptBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.SqConcatBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen.LenBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.SqItemBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.SqRepeatBuiltinNode;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.builtins.ListNodes.AppendNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.formatting.InternalFormat;
import com.oracle.graal.python.runtime.formatting.InternalFormat.Spec;
import com.oracle.graal.python.runtime.formatting.StringFormatProcessor;
import com.oracle.graal.python.runtime.formatting.TextFormatter;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.ComparisonOp;
import com.oracle.graal.python.util.IntPredicate;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;
import com.oracle.truffle.api.strings.InternalByteArray;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.CodePointLengthNode;
import com.oracle.truffle.api.strings.TruffleString.CodeRange;
import com.oracle.truffle.api.strings.TruffleString.Encoding;
import com.oracle.truffle.api.strings.TruffleString.IndexOfStringNode;
import com.oracle.truffle.api.strings.TruffleString.LastIndexOfStringNode;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.strings.TruffleStringIterator;

/**
 * NOTE: self can either be a TruffleString, PString, PythonNativeObject string or a foreign string
 * (isString()). Use {@link CastToTruffleStringCheckedNode} or {@link CastToTruffleStringNode} to
 * convert to TruffleString.
 */
@CoreFunctions(extendClasses = PythonBuiltinClassType.PString)
public final class StringBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = StringBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return StringBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___STR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class StrNode extends PythonUnaryBuiltinNode {

        @Specialization
        static TruffleString doString(TruffleString self) {
            return self;
        }

        @Specialization(guards = "!isTruffleString(self)")
        static TruffleString doGeneric(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castToTruffleStringNode) {
            return castToTruffleStringNode.cast(inliningTarget, self, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, T___STR__, self);
        }
    }

    @Builtin(name = J___FORMAT__, minNumOfPositionalArgs = 2, parameterNames = {"$self", "format_spec"})
    @ArgumentClinic(name = "format_spec", conversion = ClinicConversion.TString)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class FormatNode extends FormatNodeBase {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return FormatNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "!formatString.isEmpty()")
        static TruffleString format(Object self, TruffleString formatString,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToJavaStringCheckedNode castToJavaStringNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            // We cannot cast self via argument clinic, because we need to keep it as-is for the
            // empty format string case, which should call __str__, which may be overridden
            String str = castToJavaStringNode.cast(inliningTarget, self, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, T___STR__, self);
            return formatString(inliningTarget, getAndValidateSpec(inliningTarget, formatString, raiseNode), str);
        }

        @TruffleBoundary
        private static TruffleString formatString(Node raisingNode, Spec spec, String str) {
            TextFormatter formatter = new TextFormatter(spec, raisingNode);
            formatter.format(str);
            return formatter.pad().getResult();
        }

        private static Spec getAndValidateSpec(Node inliningTarget, TruffleString formatString, PRaiseNode.Lazy raiseNode) {
            Spec spec = InternalFormat.fromText(formatString, 's', '<', inliningTarget);
            if (Spec.specified(spec.type) && spec.type != 's') {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.UNKNOWN_FORMAT_CODE, spec.type, "str");
            }
            if (Spec.specified(spec.sign)) {
                if (spec.sign == ' ') {
                    throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.SPACE_NOT_ALLOWED_IN_STRING_FORMAT_SPECIFIER);
                } else {
                    throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.SIGN_NOT_ALLOWED_FOR_STRING_FMT);
                }
            }
            if (spec.alternate) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.ALTERNATE_NOT_ALLOWED_WITH_STRING_FMT);
            }
            if (Spec.specified(spec.align) && spec.align == '=') {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.EQUALS_ALIGNMENT_FLAG_NOT_ALLOWED_FOR_STRING_FMT);
            }
            return spec;
        }
    }

    @Builtin(name = J_FORMAT, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class StrFormatNode extends PythonBuiltinNode {

        @Specialization
        static TruffleString format(VirtualFrame frame, Object self, Object[] args, PKeyword[] kwargs,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @Cached BuiltinFunctions.FormatNode format,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached PRaiseNode raiseNode) {
            TruffleString string;
            try {
                string = castToStringNode.execute(inliningTarget, self);
            } catch (CannotCastException e) {
                throw raiseNode.raise(TypeError, ErrorMessages.DESCRIPTOR_S_REQUIRES_S_OBJ_RECEIVED_P, T_FORMAT, "str", self);
            }
            TemplateFormatter template = new TemplateFormatter(string);
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            PythonContext context = PythonContext.get(inliningTarget);
            Object state = IndirectCallContext.enter(frame, language, context, indirectCallData);
            try {
                return template.build(inliningTarget, args, kwargs, format);
            } finally {
                IndirectCallContext.exit(frame, language, context, state);
            }
        }
    }

    @Builtin(name = J_FORMAT_MAP, minNumOfPositionalArgs = 2, declaresExplicitSelf = true, parameterNames = {"self", "mapping"})
    @ArgumentClinic(name = "self", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    @ImportStatic(SpecialMethodNames.class)
    abstract static class FormatMapNode extends PythonBinaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StringBuiltinsClinicProviders.FormatMapNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        TruffleString format(VirtualFrame frame, TruffleString self, Object mapping,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @Cached BuiltinFunctions.FormatNode format) {

            TemplateFormatter template = new TemplateFormatter(self);

            PythonLanguage language = PythonLanguage.get(this);
            PythonContext context = PythonContext.get(this);
            Object state = IndirectCallContext.enter(frame, language, context, indirectCallData);
            try {
                return template.build(this, null, mapping, format);
            } finally {
                IndirectCallContext.exit(frame, language, context, state);
            }
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString doGeneric(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castToStringNode,
                        @Cached StringNodes.StringReprNode reprNode) {
            return reprNode.execute(castToStringNode.cast(inliningTarget, self, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, T___REPR__, self));
        }
    }

    @Builtin(name = J___GETNEWARGS__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class GetNewargsNode extends PythonUnaryBuiltinNode {
        @Specialization
        PTuple doGeneric(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode cast,
                        @Cached PythonObjectFactory factory) {
            TruffleString selfStr = cast.cast(inliningTarget, self, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, T___GETNEWARGS__, self);
            // CPython requires the resulting string not to be the same object as the original for
            // some reason
            PString copy = factory.createString(selfStr);
            return factory.createTuple(new Object[]{copy});
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class StringEqOpHelperNode extends Node {

        abstract Object execute(Node inliningTarget, Object self, Object other, boolean negate);

        @Specialization
        static boolean doStrings(TruffleString self, TruffleString other, boolean negate,
                        @Shared @Cached(inline = false) TruffleString.EqualNode equalNode) {
            return equalNode.execute(self, other, TS_ENCODING) != negate;
        }

        @Specialization
        static Object doGeneric(Node inliningTarget, Object self, Object other, boolean negate,
                        @Cached CastToTruffleStringCheckedNode castSelfNode,
                        @Cached CastToTruffleStringNode castOtherNode,
                        @Shared @Cached(inline = false) TruffleString.EqualNode equalNode,
                        @Cached InlinedBranchProfile noStringBranch) {
            TruffleString selfStr = castSelfNode.cast(inliningTarget, self, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, T___EQ__, self);
            TruffleString otherStr;
            try {
                otherStr = castOtherNode.execute(inliningTarget, other);
            } catch (CannotCastException e) {
                noStringBranch.enter(inliningTarget);
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            return doStrings(selfStr, otherStr, negate, equalNode);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class StringCmpOpHelperNode extends Node {

        abstract Object execute(Node inliningTarget, Object self, Object other, IntPredicate resultProcessor);

        @Specialization
        static boolean doStrings(TruffleString self, TruffleString other, IntPredicate resultProcessor,
                        @Shared @Cached(inline = false) TruffleString.CompareIntsUTF32Node compareIntsUTF32Node) {
            return resultProcessor.test(StringUtils.compareStrings(self, other, compareIntsUTF32Node));
        }

        @Specialization
        static Object doGeneric(Node inliningTarget, Object self, Object other, IntPredicate resultProcessor,
                        @Cached CastToTruffleStringCheckedNode castSelfNode,
                        @Cached CastToTruffleStringNode castOtherNode,
                        @Shared @Cached(inline = false) TruffleString.CompareIntsUTF32Node compareIntsUTF32Node,
                        @Cached InlinedBranchProfile noStringBranch) {
            TruffleString selfStr = castSelfNode.cast(inliningTarget, self, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, T___EQ__, self);
            TruffleString otherStr;
            try {
                otherStr = castOtherNode.execute(inliningTarget, other);
            } catch (CannotCastException e) {
                noStringBranch.enter(inliningTarget);
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            return doStrings(selfStr, otherStr, resultProcessor, compareIntsUTF32Node);
        }
    }

    @Builtin(name = J___CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        public abstract boolean executeBool(Object self, Object left);

        @Specialization
        static boolean doit(Object self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castStr,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.IndexOfStringNode indexOfStringNode) {
            TruffleString selfStr = castStr.cast(inliningTarget, self, ErrorMessages.REQUIRES_STRING_AS_LEFT_OPERAND, other);
            TruffleString otherStr = castStr.cast(inliningTarget, other, ErrorMessages.REQUIRES_STRING_AS_LEFT_OPERAND, other);
            return indexOfStringNode.execute(selfStr, otherStr, 0, codePointLengthNode.execute(selfStr, TS_ENCODING), TS_ENCODING) >= 0;
        }
    }

    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object doIt(Object self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached StringEqOpHelperNode stringEqOpHelperNode) {
            return stringEqOpHelperNode.execute(inliningTarget, self, other, false);
        }
    }

    @Builtin(name = J___NE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class NeNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object doIt(Object self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached StringEqOpHelperNode stringEqOpHelperNode) {
            return stringEqOpHelperNode.execute(inliningTarget, self, other, true);
        }
    }

    @Builtin(name = J___LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class LtNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object doIt(Object self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached StringCmpOpHelperNode stringCmpOpHelperNode) {
            return stringCmpOpHelperNode.execute(inliningTarget, self, other, r -> r < 0);
        }
    }

    @Builtin(name = J___LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class LeNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object doIt(Object self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached StringCmpOpHelperNode stringCmpOpHelperNode) {
            return stringCmpOpHelperNode.execute(inliningTarget, self, other, r -> r <= 0);
        }
    }

    @Builtin(name = J___GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GtNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object doIt(Object self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached StringCmpOpHelperNode stringCmpOpHelperNode) {
            return stringCmpOpHelperNode.execute(inliningTarget, self, other, r -> r > 0);
        }
    }

    @Builtin(name = J___GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GeNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object doIt(Object self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached StringCmpOpHelperNode stringCmpOpHelperNode) {
            return stringCmpOpHelperNode.execute(inliningTarget, self, other, r -> r >= 0);
        }
    }

    @Builtin(name = J___TRUFFLE_RICHCOMPARE__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @ImportStatic(ComparisonOp.class)
    abstract static class RichCompareNode extends PythonTernaryBuiltinNode {

        @Specialization(guards = "isEqualityOpCode(opCode)")
        static Object doEqNeOp(Object left, Object right, int opCode,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached PyUnicodeCheckNode checkLeft,
                        @Exclusive @Cached PyUnicodeCheckNode checkRight,
                        @Cached StringEqOpHelperNode stringEqOpHelperNode) {
            if (!checkLeft.execute(inliningTarget, left) || !checkRight.execute(inliningTarget, right)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            return stringEqOpHelperNode.execute(inliningTarget, left, right, opCode == ComparisonOp.NE.opCode);
        }

        @Specialization(guards = {"opCode == cachedOp.opCode", "!isEqualityOpCode(opCode)"}, limit = "4")
        static Object doRelOp(Object left, Object right, @SuppressWarnings("unused") int opCode,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached PyUnicodeCheckNode checkLeft,
                        @Exclusive @Cached PyUnicodeCheckNode checkRight,
                        @Cached("fromOpCode(opCode)") ComparisonOp cachedOp,
                        @Cached StringCmpOpHelperNode stringCmpOpHelperNode) {
            if (!checkLeft.execute(inliningTarget, left) || !checkRight.execute(inliningTarget, right)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            return stringCmpOpHelperNode.execute(inliningTarget, left, right, cachedOp.intPredicate);
        }
    }

    @Slot(value = SlotKind.sq_concat, isComplex = true)
    @GenerateNodeFactory
    public abstract static class ConcatNode extends SqConcatBuiltinNode {
        @Specialization
        static TruffleString doIt(TruffleString self, TruffleString other,
                        @Shared @Cached TruffleString.ConcatNode concatNode) {
            return concatNode.execute(self, other, TS_ENCODING, false);
        }

        @Fallback
        static TruffleString doSS(Object self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringNode castToStringLeftNode,
                        @Cached CastToTruffleStringNode castToStringRightNode,
                        @Shared @Cached TruffleString.ConcatNode concatNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            TruffleString left;
            TruffleString right;
            try {
                left = castToStringLeftNode.execute(inliningTarget, self);
            } catch (CannotCastException e) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.DESCRIPTOR_S_REQUIRES_S_OBJ_RECEIVED_P, T___ADD__, "str", self);
            }
            try {
                right = castToStringRightNode.execute(inliningTarget, other);
            } catch (CannotCastException e) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.CAN_ONLY_CONCAT_S_NOT_P_TO_S, "str", other, "str");
            }
            return doIt(left, right, concatNode);
        }
    }

    @GenerateInline(false) // footprint reduction 56 -> 39
    @ImportStatic(PGuards.class)
    public abstract static class PrefixSuffixNode extends Node {

        enum Op {
            PREFIX,
            SUFFIX;

            TruffleString methodName() {
                return this == PREFIX ? T_STARTSWITH : T_ENDSWITH;
            }
        }

        abstract boolean execute(Object self, Object subStr, int start, int end, Op op);

        public final boolean startsWith(Object self, Object subStr, int start, int end) {
            return execute(self, subStr, start, end, Op.PREFIX);
        }

        public final boolean endsWith(Object self, Object subStr, int start, int end) {
            return execute(self, subStr, start, end, Op.SUFFIX);
        }

        @Specialization(guards = "!isPTuple(subStrObj)")
        static boolean doString(Object selfObj, Object subStrObj, int start, int end, Op op,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached CastToTruffleStringCheckedNode castSelfNode,
                        @Exclusive @Cached CastToTruffleStringCheckedNode castPrefixNode,
                        @Shared @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared @Cached TruffleString.RegionEqualNode regionEqualNode) {
            TruffleString self = castSelfNode.cast(inliningTarget, selfObj, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, op.methodName(), selfObj);
            TruffleString subStr = castPrefixNode.cast(inliningTarget, subStrObj, ErrorMessages.FIRST_ARG_MUST_BE_S_OR_TUPLE_NOT_P, op.methodName(), "str", subStrObj);
            int selfLen = codePointLengthNode.execute(self, TS_ENCODING);
            int subStrLen = codePointLengthNode.execute(subStr, TS_ENCODING);
            return doIt(self, subStr, adjustStartIndex(start, selfLen), adjustEndIndex(end, selfLen), selfLen, subStrLen, regionEqualNode, op);
        }

        @Specialization
        static boolean doTuple(Object selfObj, PTuple subStrs, int start, int end, Op op,
                        @Bind("this") Node inliningTarget,
                        @Cached GetObjectArrayNode getObjectArrayNode,
                        @Exclusive @Cached CastToTruffleStringCheckedNode castSelfNode,
                        @Exclusive @Cached CastToTruffleStringCheckedNode castPrefixNode,
                        @Shared @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared @Cached TruffleString.RegionEqualNode regionEqualNode) {
            TruffleString self = castSelfNode.cast(inliningTarget, selfObj, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, op.methodName(), selfObj);

            int selfLen = codePointLengthNode.execute(self, TS_ENCODING);
            int cpStart = adjustStartIndex(start, selfLen);
            int cpEnd = adjustEndIndex(end, selfLen);

            for (Object element : getObjectArrayNode.execute(inliningTarget, subStrs)) {
                TruffleString subStr = castPrefixNode.cast(inliningTarget, element, ErrorMessages.INVALID_ELEMENT_TYPE, op.methodName(), element);
                int subStrLen = codePointLengthNode.execute(subStr, TS_ENCODING);
                if (doIt(self, subStr, cpStart, cpEnd, selfLen, subStrLen, regionEqualNode, op)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean doIt(TruffleString text, TruffleString subStr, int start, int end, int textLen, int subStrLen, TruffleString.RegionEqualNode regionEqualNode, Op op) {
            // start and end must be normalized indices for 'text'
            assert start >= 0;
            assert end >= 0 && end <= textLen;

            if (end - start < subStrLen) {
                return false;
            }
            int fromIndex = op == Op.PREFIX ? start : end - subStrLen;
            return regionEqualNode.execute(text, fromIndex, subStr, 0, subStrLen, TS_ENCODING);
        }
    }

    // str.startswith(prefix[, start[, end]])
    @Builtin(name = J_STARTSWITH, minNumOfPositionalArgs = 2, parameterNames = {"self", "prefix", "start", "end"})
    @ArgumentClinic(name = "start", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "0", useDefaultForNone = true)
    @ArgumentClinic(name = "end", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "Integer.MAX_VALUE", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class StartsWithNode extends PythonQuaternaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StringBuiltinsClinicProviders.StartsWithNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static boolean doStartsWith(Object self, Object prefix, int start, int end,
                        @Cached PrefixSuffixNode prefixSuffixNode) {
            return prefixSuffixNode.startsWith(self, prefix, start, end);
        }
    }

    // str.endswith(suffix[, start[, end]])
    @Builtin(name = J_ENDSWITH, minNumOfPositionalArgs = 2, parameterNames = {"self", "suffix", "start", "end"})
    @ArgumentClinic(name = "start", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "0", useDefaultForNone = true)
    @ArgumentClinic(name = "end", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "Integer.MAX_VALUE", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class EndsWithNode extends PythonQuaternaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StringBuiltinsClinicProviders.EndsWithNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static boolean doEndsWith(Object self, Object prefix, int start, int end,
                        @Cached PrefixSuffixNode prefixSuffixNode) {
            return prefixSuffixNode.endsWith(self, prefix, start, end);
        }
    }

    // str.rfind(str[, start[, end]])
    @Builtin(name = "rfind", minNumOfPositionalArgs = 2, parameterNames = {"$self", "sub", "start", "end"})
    @ArgumentClinic(name = "start", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "0", useDefaultForNone = true)
    @ArgumentClinic(name = "end", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "Integer.MAX_VALUE", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class RFindNode extends PythonQuaternaryClinicBuiltinNode {

        public abstract int execute(Object self, Object sub, int start, int end);

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StringBuiltinsClinicProviders.RFindNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static int rfind(TruffleString self, TruffleString sub, int start, int end,
                        @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared("lastIndexOf") @Cached TruffleString.LastIndexOfStringNode lastIndexOfStringNode) {
            return lastIndexOf(self, sub, start, end, codePointLengthNode, lastIndexOfStringNode);
        }

        @Specialization
        static int rfind(Object self, Object sub, int start, int end,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castNode,
                        @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared("lastIndexOf") @Cached TruffleString.LastIndexOfStringNode lastIndexOfStringNode) {
            TruffleString selfStr = castNode.cast(inliningTarget, self, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, "rfind", self);
            TruffleString subStr = castNode.cast(inliningTarget, sub, ErrorMessages.MUST_BE_STR_NOT_P, sub);
            return rfind(selfStr, subStr, start, end, codePointLengthNode, lastIndexOfStringNode);
        }
    }

    // str.find(str[, start[, end]])
    @Builtin(name = "find", minNumOfPositionalArgs = 2, parameterNames = {"$self", "sub", "start", "end"})
    @ArgumentClinic(name = "start", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "0", useDefaultForNone = true)
    @ArgumentClinic(name = "end", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "Integer.MAX_VALUE", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class FindNode extends PythonQuaternaryClinicBuiltinNode {
        public abstract int execute(Object self, Object sub, int start, int end);

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StringBuiltinsClinicProviders.FindNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static int find(TruffleString self, TruffleString sub, int start, int end,
                        @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared("indexOf") @Cached TruffleString.IndexOfStringNode indexOfStringNode) {
            return indexOf(self, sub, start, end, codePointLengthNode, indexOfStringNode);
        }

        @Specialization
        static int find(Object self, Object sub, int start, int end,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castNode,
                        @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared("indexOf") @Cached TruffleString.IndexOfStringNode indexOfStringNode) {
            TruffleString selfStr = castNode.cast(inliningTarget, self, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, "find", self);
            TruffleString subStr = castNode.cast(inliningTarget, sub, ErrorMessages.MUST_BE_STR_NOT_P, sub);
            return find(selfStr, subStr, start, end, codePointLengthNode, indexOfStringNode);
        }
    }

    // str.count(str[, start[, end]])
    @Builtin(name = "count", minNumOfPositionalArgs = 2, parameterNames = {"$self", "sub", "start", "end"})
    @ArgumentClinic(name = "start", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "0", useDefaultForNone = true)
    @ArgumentClinic(name = "end", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "Integer.MAX_VALUE", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class CountNode extends PythonQuaternaryClinicBuiltinNode {

        public abstract int execute(Object self, Object sub, int start, int end);

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StringBuiltinsClinicProviders.FindNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static int count(TruffleString self, TruffleString sub, int start, int end,
                        @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared("indexOf") @Cached TruffleString.IndexOfStringNode indexOfStringNode) {
            int cpLen = codePointLengthNode.execute(self, TS_ENCODING);
            int cpStart = adjustStartIndex(start, cpLen);
            int cpEnd = adjustEndIndex(end, cpLen);

            if (self.isEmpty()) {
                return (sub.isEmpty() && cpStart <= 0) ? 1 : 0;
            }
            if (sub.isEmpty()) {
                return (cpStart <= cpLen) ? (cpEnd - cpStart) + 1 : 0;
            }
            if (cpStart >= cpLen) {
                return 0;
            }

            int subLen = codePointLengthNode.execute(sub, TS_ENCODING);
            int pos = cpStart;
            int cnt = 0;
            while (pos <= cpEnd - subLen) {
                int i = indexOfStringNode.execute(self, sub, pos, cpEnd, TS_ENCODING);
                if (i < 0) {
                    break;
                }
                cnt++;
                pos = i + subLen;
            }
            return cnt;
        }

        @Specialization
        static int count(Object self, Object sub, int start, int end,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castNode,
                        @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared("indexOf") @Cached TruffleString.IndexOfStringNode indexOfStringNode) {
            TruffleString selfStr = castNode.cast(inliningTarget, self, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, "count", self);
            TruffleString subStr = castNode.cast(inliningTarget, sub, ErrorMessages.MUST_BE_STR_NOT_P, sub);
            return count(selfStr, subStr, start, end, codePointLengthNode, indexOfStringNode);
        }
    }

    // str.join(iterable)
    @Builtin(name = "join", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class JoinNode extends PythonBinaryBuiltinNode {

        @Specialization
        static TruffleString join(VirtualFrame frame, Object self, Object iterable,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castToStringNode,
                        @Cached JoinInternalNode join) {
            return join.execute(frame, castToStringNode.cast(inliningTarget, self, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, "join", self), iterable);
        }
    }

    // str.lower()
    @Builtin(name = "lower", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LowerNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "isAscii(self, getCodeRangeNode)")
        static TruffleString lowerAscii(TruffleString self,
                        @Shared("getCodeRange") @Cached @SuppressWarnings("unused") TruffleString.GetCodeRangeNode getCodeRangeNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached TruffleString.GetInternalByteArrayNode getInternalByteArrayNode,
                        @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode,
                        @Cached TruffleString.FromByteArrayNode fromByteArrayNode) {
            TruffleString ascii = switchEncodingNode.execute(self, Encoding.US_ASCII);
            int i = findFirstUpperCase(ascii, getInternalByteArrayNode);
            if (i < 0) {
                return self;
            }
            byte[] buf = new byte[ascii.byteLength(Encoding.US_ASCII)];
            copyToByteArrayNode.execute(ascii, 0, buf, 0, buf.length, Encoding.US_ASCII);
            for (; i < buf.length; ++i) {
                if (buf[i] >= 'A' && buf[i] <= 'Z') {
                    buf[i] = (byte) (buf[i] - 'A' + 'a');
                }
            }
            return switchEncodingNode.execute(fromByteArrayNode.execute(buf, Encoding.US_ASCII, false), TS_ENCODING);
        }

        @Specialization(guards = "!isAscii(self, getCodeRangeNode)")
        static TruffleString lower(TruffleString self,
                        @Shared("getCodeRange") @Cached @SuppressWarnings("unused") TruffleString.GetCodeRangeNode getCodeRangeNode,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            return fromJavaStringNode.execute(StringUtils.toLowerCase(toJavaStringNode.execute(self)), TS_ENCODING);
        }

        @Specialization
        static Object doGeneric(VirtualFrame frame, Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castToStringNode,
                        @Cached LowerNode lowerNode) {
            return lowerNode.execute(frame, castToStringNode.cast(inliningTarget, self, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, "lower", self));
        }

        private static int findFirstUpperCase(TruffleString s, TruffleString.GetInternalByteArrayNode getInternalByteArrayNode) {
            InternalByteArray iba = getInternalByteArrayNode.execute(s, Encoding.US_ASCII);
            byte[] bytes = iba.getArray();
            int end = iba.getEnd();
            for (int i = iba.getOffset(); i < end; ++i) {
                if (bytes[i] >= 'A' && bytes[i] <= 'Z') {
                    return i;
                }
            }
            return -1;
        }
    }

    // str.upper()
    @Builtin(name = "upper", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class UpperNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "isAscii(self, getCodeRangeNode)")
        static TruffleString upperAscii(TruffleString self,
                        @Shared("getCodeRange") @Cached @SuppressWarnings("unused") TruffleString.GetCodeRangeNode getCodeRangeNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached TruffleString.GetInternalByteArrayNode getInternalByteArrayNode,
                        @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode,
                        @Cached TruffleString.FromByteArrayNode fromByteArrayNode) {
            TruffleString ascii = switchEncodingNode.execute(self, Encoding.US_ASCII);
            int i = findFirstLowerCase(ascii, getInternalByteArrayNode);
            if (i < 0) {
                return self;
            }
            byte[] buf = new byte[ascii.byteLength(Encoding.US_ASCII)];
            copyToByteArrayNode.execute(ascii, 0, buf, 0, buf.length, Encoding.US_ASCII);
            for (; i < buf.length; ++i) {
                if (buf[i] >= 'a' && buf[i] <= 'z') {
                    buf[i] = (byte) (buf[i] - 'a' + 'A');
                }
            }
            return switchEncodingNode.execute(fromByteArrayNode.execute(buf, Encoding.US_ASCII, false), TS_ENCODING);
        }

        @Specialization(guards = "!isAscii(self, getCodeRangeNode)")
        static TruffleString upper(TruffleString self,
                        @Shared("getCodeRange") @Cached @SuppressWarnings("unused") TruffleString.GetCodeRangeNode getCodeRangeNode,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            return fromJavaStringNode.execute(StringUtils.toUpperCase(toJavaStringNode.execute(self)), TS_ENCODING);
        }

        @Specialization
        static Object doGeneric(VirtualFrame frame, Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castToStringNode,
                        @Cached UpperNode upperNode) {
            return upperNode.execute(frame, castToStringNode.cast(inliningTarget, self, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, "upper", self));
        }

        private static int findFirstLowerCase(TruffleString s, TruffleString.GetInternalByteArrayNode getInternalByteArrayNode) {
            InternalByteArray iba = getInternalByteArrayNode.execute(s, Encoding.US_ASCII);
            byte[] bytes = iba.getArray();
            int end = iba.getEnd();
            for (int i = iba.getOffset(); i < end; ++i) {
                if (bytes[i] >= 'a' && bytes[i] <= 'z') {
                    return i;
                }
            }
            return -1;
        }
    }

    // str.maketrans()
    @Builtin(name = "maketrans", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4, isStaticmethod = true)
    @GenerateNodeFactory
    public abstract static class MakeTransNode extends PythonQuaternaryBuiltinNode {

        @Specialization(guards = "!isNoValue(to)")
        @SuppressWarnings("unused")
        static PDict doString(VirtualFrame frame, Object cls, Object from, Object to, Object z,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached CastToTruffleStringCheckedNode castFromNode,
                        @Exclusive @Cached CastToTruffleStringCheckedNode castToNode,
                        @Exclusive @Cached CastToTruffleStringCheckedNode castZNode,
                        @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Cached TruffleStringIterator.NextNode nextNode,
                        @Cached InlinedConditionProfile hasZProfile,
                        @Exclusive @Cached HashingStorageSetItem setHashingStorageItem,
                        @Shared @Cached PythonObjectFactory factory,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {

            TruffleString toStr = castToNode.cast(inliningTarget, to, ErrorMessages.ARG_S_MUST_BE_S_NOT_P, "2", "str", to);
            TruffleString fromStr = castFromNode.cast(inliningTarget, from, ErrorMessages.FIRST_MAKETRANS_ARGS_MUST_BE_A_STR);
            boolean hasZ = hasZProfile.profile(inliningTarget, z != PNone.NO_VALUE);
            TruffleString zString = null;
            if (hasZ) {
                zString = castZNode.cast(inliningTarget, z, ErrorMessages.ARG_D_MUST_BE_S_NOT_P, "maketrans()", 3, "str", z);
            }
            int toLen = codePointLengthNode.execute(toStr, TS_ENCODING);
            int fromLen = codePointLengthNode.execute(fromStr, TS_ENCODING);
            if (toLen != fromLen) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.FIRST_TWO_MAKETRANS_ARGS_MUST_HAVE_EQ_LENGTH);
            }
            HashingStorage storage = PDict.createNewStorage(fromLen);
            TruffleStringIterator fromIt = createCodePointIteratorNode.execute(fromStr, TS_ENCODING);
            TruffleStringIterator toIt = createCodePointIteratorNode.execute(toStr, TS_ENCODING);
            while (fromIt.hasNext()) {
                assert toIt.hasNext();
                int key = nextNode.execute(fromIt);
                int value = nextNode.execute(toIt);
                storage = setHashingStorageItem.execute(frame, inliningTarget, storage, key, value);
            }
            assert !toIt.hasNext();
            if (hasZ) {
                TruffleStringIterator zIt = createCodePointIteratorNode.execute(zString, TS_ENCODING);
                while (zIt.hasNext()) {
                    int key = nextNode.execute(zIt);
                    storage = setHashingStorageItem.execute(frame, inliningTarget, storage, key, PNone.NONE);
                }
            }
            return factory.createDict(storage);
        }

        @Specialization(guards = {"isNoValue(to)", "isNoValue(z)"})
        @SuppressWarnings("unused")
        static PDict doDict(VirtualFrame frame, Object cls, PDict from, Object to, Object z,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached CastToTruffleStringCheckedNode cast,
                        @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Exclusive @Cached HashingStorageSetItem setHashingStorageItem,
                        @Cached HashingStorageLen lenNode,
                        @Cached HashingStorageGetIterator getIter,
                        @Cached HashingStorageIteratorNext iterHasNext,
                        @Cached HashingStorageIteratorKey iterKey,
                        @Cached HashingStorageIteratorValue iterValue,
                        @Shared @Cached PythonObjectFactory factory,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            HashingStorage srcStorage = from.getDictStorage();
            HashingStorage destStorage = PDict.createNewStorage(lenNode.execute(inliningTarget, srcStorage));
            HashingStorageIterator it = getIter.execute(inliningTarget, srcStorage);
            while (iterHasNext.execute(inliningTarget, srcStorage, it)) {
                Object currentKey = iterKey.execute(inliningTarget, srcStorage, it);
                Object currentValue = iterValue.execute(inliningTarget, srcStorage, it);
                if (PGuards.isInteger(currentKey) || PGuards.isPInt(currentKey)) {
                    destStorage = setHashingStorageItem.execute(frame, inliningTarget, destStorage, currentKey, currentValue);
                } else {
                    TruffleString strKey = cast.cast(inliningTarget, currentKey, ErrorMessages.KEYS_IN_TRANSLATE_TABLE_MUST_BE_STRINGS_OR_INTEGERS);
                    if (codePointLengthNode.execute(strKey, TS_ENCODING) != 1) {
                        throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.STRING_KEYS_MUST_BE_LENGTH_1);
                    }
                    int codePoint = codePointAtIndexNode.execute(strKey, 0, TS_ENCODING);
                    destStorage = setHashingStorageItem.execute(frame, inliningTarget, destStorage, codePoint, currentValue);
                }
            }
            return factory.createDict(destStorage);
        }

        @Specialization(guards = {"!isDict(from)", "isNoValue(to)"})
        @SuppressWarnings("unused")
        static PDict doFail(Object cls, Object from, Object to, Object z,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.IF_YOU_GIVE_ONLY_ONE_ARG_TO_DICT);
        }
    }

    // str.translate()
    @Builtin(name = "translate", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class TranslateNode extends PythonBuiltinNode {
        @Specialization
        static TruffleString doStringString(TruffleString self, TruffleString table,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Shared("createCpIterator") @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Shared("next") @Cached TruffleStringIterator.NextNode nextNode,
                        @Shared("appendCp") @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Shared("toString") @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            int tableLen = codePointLengthNode.execute(table, TS_ENCODING);
            TruffleStringIterator it = createCodePointIteratorNode.execute(self, TS_ENCODING);
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING, self.byteLength(TS_ENCODING));
            while (it.hasNext()) {
                int cp = nextNode.execute(it);
                if (cp >= 0 && cp < tableLen) {
                    cp = codePointAtIndexNode.execute(table, cp, TS_ENCODING);
                }
                appendCodePointNode.execute(sb, cp, 1, true);
            }
            return toStringNode.execute(sb);
        }

        @Specialization
        static TruffleString doGeneric(VirtualFrame frame, Object self, Object table,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castSelfNode,
                        @Cached PyObjectGetItem getItemNode,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached SpliceNode spliceNode,
                        @Shared("createCpIterator") @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Shared("next") @Cached TruffleStringIterator.NextNode nextNode,
                        @Shared("appendCp") @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Shared("toString") @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            TruffleString selfStr = castSelfNode.cast(inliningTarget, self, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, "translate", self);
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING, selfStr.byteLength(TS_ENCODING));
            TruffleStringIterator it = createCodePointIteratorNode.execute(selfStr, TS_ENCODING);
            while (it.hasNext()) {
                int original = nextNode.execute(it);
                Object translated = null;
                try {
                    translated = getItemNode.execute(frame, inliningTarget, table, original);
                } catch (PException e) {
                    if (!isSubtypeNode.execute(null, getClassNode.execute(inliningTarget, e.getUnreifiedException()), PythonBuiltinClassType.LookupError)) {
                        throw e;
                    }
                }
                if (translated != null) {
                    spliceNode.execute(sb, translated);
                } else {
                    appendCodePointNode.execute(sb, original, 1, true);
                }
            }

            return toStringNode.execute(sb);
        }
    }

    // str.capitalize()
    @Builtin(name = "capitalize", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CapitalizeNode extends PythonUnaryBuiltinNode {

        @CompilationFinal private static CaseMap.Title titlecaser;

        @Specialization
        static TruffleString capitalize(TruffleString self,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            if (self.isEmpty()) {
                return T_EMPTY_STRING;
            } else {
                return fromJavaStringNode.execute(capitalizeImpl(toJavaStringNode.execute(self)), TS_ENCODING);
            }
        }

        @Specialization
        static TruffleString doGeneric(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToJavaStringCheckedNode castToJavaStringNode,
                        @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            String s = castToJavaStringNode.cast(inliningTarget, self, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, "capitalize", self);
            if (s.isEmpty()) {
                return T_EMPTY_STRING;
            }
            return fromJavaStringNode.execute(capitalizeImpl(s), TS_ENCODING);
        }

        private static String capitalizeImpl(String str) {
            if (titlecaser == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                titlecaser = CaseMap.toTitle().wholeString().noBreakAdjustment();
            }
            return apply(str);
        }

        @TruffleBoundary
        private static String apply(String str) {
            return titlecaser.apply(Locale.ROOT, null, str);
        }
    }

    // str.partition
    @Builtin(name = "partition", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PartitionNode extends PythonBinaryBuiltinNode {

        @Specialization
        static PTuple doGeneric(Object self, Object sep,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castSelfNode,
                        @Cached CastToTruffleStringCheckedNode castSepNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.IndexOfStringNode indexOfStringNode,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            TruffleString selfStr = castSelfNode.cast(inliningTarget, self, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, "partition", self);
            TruffleString sepStr = castSepNode.cast(inliningTarget, sep, ErrorMessages.MUST_BE_STR_NOT_P, sep);
            if (sepStr.isEmpty()) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.EMPTY_SEPARATOR);
            }
            int selfLen = codePointLengthNode.execute(selfStr, TS_ENCODING);
            int indexOf = indexOfStringNode.execute(selfStr, sepStr, 0, selfLen, TS_ENCODING);
            TruffleString[] partitioned = new TruffleString[3];
            if (indexOf < 0) {
                partitioned[0] = selfStr;
                partitioned[1] = T_EMPTY_STRING;
                partitioned[2] = T_EMPTY_STRING;
            } else {
                int o = indexOf + codePointLengthNode.execute(sepStr, TS_ENCODING);
                partitioned[0] = substringNode.execute(selfStr, 0, indexOf, TS_ENCODING, false);
                partitioned[1] = sepStr;
                partitioned[2] = substringNode.execute(selfStr, o, selfLen - o, TS_ENCODING, false);
            }
            return factory.createTuple(partitioned);
        }
    }

    // str.rpartition
    @Builtin(name = "rpartition", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class RPartitionNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object self, Object sep,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castSelfNode,
                        @Cached CastToTruffleStringCheckedNode castSepNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.LastIndexOfStringNode lastIndexOfStringNode,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            TruffleString selfStr = castSelfNode.cast(inliningTarget, self, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, "rpartition", self);
            TruffleString sepStr = castSepNode.cast(inliningTarget, sep, ErrorMessages.MUST_BE_STR_NOT_P, sep);
            if (sepStr.isEmpty()) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.EMPTY_SEPARATOR);
            }
            int selfLen = codePointLengthNode.execute(selfStr, TS_ENCODING);
            int lastIndexOf = lastIndexOfStringNode.execute(selfStr, sepStr, selfLen, 0, TS_ENCODING);
            TruffleString[] partitioned = new TruffleString[3];
            if (lastIndexOf < 0) {
                partitioned[0] = T_EMPTY_STRING;
                partitioned[1] = T_EMPTY_STRING;
                partitioned[2] = selfStr;
            } else {
                int o = lastIndexOf + codePointLengthNode.execute(sepStr, TS_ENCODING);
                partitioned[0] = substringNode.execute(selfStr, 0, lastIndexOf, TS_ENCODING, false);
                partitioned[1] = sepStr;
                partitioned[2] = substringNode.execute(selfStr, o, selfLen - o, TS_ENCODING, false);
            }
            return factory.createTuple(partitioned);
        }
    }

    // str.split
    @Builtin(name = "split", minNumOfPositionalArgs = 1, parameterNames = {"$self", "sep", "maxsplit"}, needsFrame = true)
    @ArgumentClinic(name = "$self", conversion = ClinicConversion.TString)
    @ArgumentClinic(name = "sep", conversion = ClinicConversion.TString, defaultValue = "PNone.NONE", useDefaultForNone = true)
    @ArgumentClinic(name = "maxsplit", conversion = ClinicConversion.Index, defaultValue = "-1")
    @GenerateNodeFactory
    public abstract static class SplitNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SplitNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        @SuppressWarnings("unused")
        static PList doStringNoSep(TruffleString self, PNone sep, int maxsplit,
                        @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Shared("substring") @Cached TruffleString.SubstringNode substringNode,
                        @Shared("appendNode") @Cached AppendNode appendNode,
                        @Shared @Cached PythonObjectFactory factory) {
            return splitfields(self, maxsplit, appendNode, codePointLengthNode, codePointAtIndexNode, substringNode, factory);
        }

        @Specialization
        static PList doStringSep(TruffleString self, TruffleString sep, int maxsplit,
                        @Bind("this") Node inliningTarget,
                        @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.IndexOfStringNode indexOfStringNode,
                        @Shared("substring") @Cached TruffleString.SubstringNode substringNode,
                        @Shared("appendNode") @Cached AppendNode appendNode,
                        @Shared @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (sep.isEmpty()) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.EMPTY_SEPARATOR);
            }
            int splits = maxsplit == -1 ? Integer.MAX_VALUE : maxsplit;

            PList list = factory.createList();
            int lastEnd = 0;
            int selfLen = codePointLengthNode.execute(self, TS_ENCODING);
            int sepLen = codePointLengthNode.execute(sep, TS_ENCODING);
            while (splits > 0 && lastEnd < selfLen) {
                int nextIndex = indexOfStringNode.execute(self, sep, lastEnd, selfLen, TS_ENCODING);
                if (nextIndex < 0) {
                    break;
                }
                splits--;
                appendNode.execute(list, substringNode.execute(self, lastEnd, nextIndex - lastEnd, TS_ENCODING, false));
                lastEnd = nextIndex + sepLen;
            }
            appendNode.execute(list, substringNode.execute(self, lastEnd, selfLen - lastEnd, TS_ENCODING, false));
            return list;
        }

        // See {@link PyString}
        private static PList splitfields(TruffleString s, int maxsplit, AppendNode appendNode, TruffleString.CodePointLengthNode codePointLengthNode,
                        TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        TruffleString.SubstringNode substringNode, PythonObjectFactory factory) {
            /*
             * Result built here is a list of split parts, exactly as required for s.split(None,
             * maxsplit). If there are to be n splits, there will be n+1 elements in L.
             */
            PList list = factory.createList();
            int length = codePointLengthNode.execute(s, TS_ENCODING);
            int start = 0;
            int splits = 0;
            int index;

            int maxsplit2 = maxsplit;
            if (maxsplit2 < 0) {
                // Make all possible splits: there can't be more than:
                maxsplit2 = length;
            }

            // start is always the first character not consumed into a piece on the list
            while (start < length) {
                // Find the next occurrence of non-whitespace
                while (start < length) {
                    if (!StringUtils.isSpace(codePointAtIndexNode.execute(s, start, TS_ENCODING))) {
                        // Break leaving start pointing at non-whitespace
                        break;
                    }
                    start++;
                }

                if (start >= length) {
                    // Only found whitespace so there is no next segment
                    break;

                } else if (splits >= maxsplit2) {
                    // The next segment is the last and contains all characters up to the end
                    index = length;

                } else {
                    // The next segment runs up to the next next whitespace or end
                    for (index = start; index < length; index++) {
                        if (StringUtils.isSpace(codePointAtIndexNode.execute(s, index, TS_ENCODING))) {
                            // Break leaving index pointing at whitespace
                            break;
                        }
                    }
                }

                // Make a piece from start up to index
                appendNode.execute(list, substringNode.execute(s, start, index - start, TS_ENCODING, false));
                splits++;

                // Start next segment search at that point
                start = index;
            }

            return list;
        }
    }

    // str.rsplit
    @Builtin(name = "rsplit", minNumOfPositionalArgs = 1, parameterNames = {"$self", "sep", "maxsplit"}, needsFrame = true)
    @ArgumentClinic(name = "$self", conversion = ClinicConversion.TString)
    @ArgumentClinic(name = "sep", conversion = ClinicConversion.TString, defaultValue = "PNone.NONE", useDefaultForNone = true)
    @ArgumentClinic(name = "maxsplit", conversion = ClinicConversion.Index, defaultValue = "-1")
    @GenerateNodeFactory
    public abstract static class RSplitNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StringBuiltinsClinicProviders.RSplitNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PList doStringSepMaxsplit(VirtualFrame frame, TruffleString self, TruffleString sep, int maxsplitInput,
                        @Bind("this") Node inliningTarget,
                        @Shared("appendNode") @Cached AppendNode appendNode,
                        @Shared("reverseNode") @Cached ListReverseNode reverseNode,
                        @Shared("cpLen") @Cached CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.LastIndexOfStringNode lastIndexOfStringNode,
                        @Shared @Cached TruffleString.SubstringNode substringNode,
                        @Shared @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (sep.isEmpty()) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.EMPTY_SEPARATOR);
            }
            int maxsplit = maxsplitInput;
            if (maxsplitInput < 0) {
                maxsplit = Integer.MAX_VALUE;
            }
            PList list = factory.createList();
            int splits = 0;
            int end = codePointLengthNode.execute(self, TS_ENCODING);
            int sepLength = codePointLengthNode.execute(sep, TS_ENCODING);
            while (splits < maxsplit && end > 0) {
                int idx = lastIndexOfStringNode.execute(self, sep, end, 0, TS_ENCODING);

                if (idx < 0) {
                    break;
                }

                appendNode.execute(list, substringNode.execute(self, idx + sepLength, end - (idx + sepLength), TS_ENCODING, false));
                end = idx;
                splits++;
            }

            appendNode.execute(list, substringNode.execute(self, 0, end, TS_ENCODING, true));
            reverseNode.execute(frame, list);
            return list;
        }

        @Specialization
        static PList doStringMaxsplit(VirtualFrame frame, TruffleString s, @SuppressWarnings("unused") PNone sep, int maxsplit,
                        @Shared("appendNode") @Cached AppendNode appendNode,
                        @Shared("reverseNode") @Cached ListReverseNode reverseNode,
                        @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Shared @Cached TruffleString.SubstringNode substringNode,
                        @Shared @Cached PythonObjectFactory factory) {
            /*
             * Result built here is a list of split parts, exactly as required for s.split(None,
             * maxsplit). If there are to be n splits, there will be n+1 elements in L.
             */
            PList list = factory.createList();
            int length = codePointLengthNode.execute(s, TS_ENCODING);

            int maxsplit2 = maxsplit;
            if (maxsplit2 < 0) {
                // Make all possible splits: there can't be more than:
                maxsplit2 = length;
            }

            // 2 state machine - we're either reading whitespace or non-whitespace, segments are
            // emitted in ws->non-ws transition and at the end
            boolean hasSegment = false;
            int start = 0, end = length, splits = 0;

            for (int i = length - 1; i >= 0; i--) {
                if (StringUtils.isSpace(codePointAtIndexNode.execute(s, i, TS_ENCODING))) {
                    if (hasSegment) {
                        appendNode.execute(list, substringNode.execute(s, start, end - start, TS_ENCODING, false));
                        hasSegment = false;
                        splits++;
                    }
                    end = i;
                } else {
                    hasSegment = true;
                    if (splits >= maxsplit2) {
                        break;
                    }
                    start = i;
                }
            }
            if (hasSegment) {
                appendNode.execute(list, substringNode.execute(s, 0, end, TS_ENCODING, false));
            }

            reverseNode.execute(frame, list);
            return list;
        }
    }

    // str.splitlines([keepends])
    @Builtin(name = "splitlines", minNumOfPositionalArgs = 1, parameterNames = {"self", "keepends"})
    @GenerateNodeFactory
    public abstract static class SplitLinesNode extends PythonBinaryBuiltinNode {
        private static final Pattern LINEBREAK_PATTERN = Pattern.compile("\\R");

        @Specialization
        static PList doString(TruffleString self, @SuppressWarnings("unused") PNone keepends,
                        @Shared("ts2js") @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                        @Shared @Cached AppendNode appendNode,
                        @Shared @Cached PythonObjectFactory factory) {
            return doStringKeepends(self, false, toJavaStringNode, fromJavaStringNode, appendNode, factory);
        }

        @Specialization
        static PList doStringKeepends(TruffleString selfTs, boolean keepends,
                        @Shared("ts2js") @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                        @Shared @Cached AppendNode appendNode,
                        @Shared @Cached PythonObjectFactory factory) {
            // TODO GR-37218: use TRegex or codepoint iterator + hand-written state machine
            PList list = factory.createList();
            int lastEnd = 0;
            String self = toJavaStringNode.execute(selfTs);
            Matcher matcher = getMatcher(self);
            while (matcherFind(matcher)) {
                int end = matcherEnd(matcher);
                String line;
                if (keepends) {
                    line = substring(self, lastEnd, end);
                } else {
                    line = substring(self, lastEnd, matcherStart(matcher));
                }
                appendNode.execute(list, fromJavaStringNode.execute(line, TS_ENCODING));
                lastEnd = end;
            }
            String remainder = substring(self, lastEnd);
            if (!remainder.isEmpty()) {
                appendNode.execute(list, fromJavaStringNode.execute(remainder, TS_ENCODING));
            }
            return list;
        }

        @TruffleBoundary(allowInlining = true)
        private static String substring(String str, int start, int end) {
            return str.substring(start, end);
        }

        @TruffleBoundary(allowInlining = true)
        private static String substring(String str, int start) {
            return str.substring(start);
        }

        @TruffleBoundary
        private static int matcherStart(Matcher matcher) {
            return matcher.start();
        }

        @TruffleBoundary
        private static int matcherEnd(Matcher matcher) {
            return matcher.end();
        }

        @TruffleBoundary
        private static boolean matcherFind(Matcher matcher) {
            return matcher.find();
        }

        @TruffleBoundary
        private static Matcher getMatcher(String self) {
            return LINEBREAK_PATTERN.matcher(self);
        }

        @Specialization(replaces = {"doString", "doStringKeepends"})
        static PList doGeneric(Object self, Object keepends,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castSelfNode,
                        @Cached CastToJavaIntExactNode castToJavaIntNode,
                        @Shared("ts2js") @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                        @Shared @Cached AppendNode appendNode,
                        @Shared @Cached PythonObjectFactory factory) {
            TruffleString selfStr = castSelfNode.cast(inliningTarget, self, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, "splitlines", self);
            boolean bKeepends = !PGuards.isPNone(keepends) && castToJavaIntNode.execute(inliningTarget, keepends) != 0;
            return doStringKeepends(selfStr, bKeepends, toJavaStringNode, fromJavaStringNode, appendNode, factory);
        }
    }

    // str.replace
    @Builtin(name = "replace", minNumOfPositionalArgs = 3, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    public abstract static class ReplaceNode extends PythonQuaternaryBuiltinNode {

        @Specialization
        static TruffleString doReplace(TruffleString self, TruffleString old, TruffleString with, @SuppressWarnings("unused") PNone maxCount,
                        @Shared("replace") @Cached StringReplaceNode replaceNode) {
            return replaceNode.execute(self, old, with, -1);
        }

        @Specialization
        static TruffleString doReplace(TruffleString self, TruffleString old, TruffleString with, int maxCountArg,
                        @Shared("replace") @Cached StringReplaceNode replaceNode) {
            return replaceNode.execute(self, old, with, maxCountArg);
        }

        @Specialization
        static TruffleString doGeneric(VirtualFrame frame, Object self, Object old, Object with, Object maxCount,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castSelfNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Shared("replace") @Cached StringReplaceNode replaceNode) {
            TruffleString selfStr = castSelfNode.cast(inliningTarget, self, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, "replace", self);
            TruffleString oldStr = castSelfNode.cast(inliningTarget, old, ErrorMessages.S_BRACKETS_ARG_S_MUST_BE_S_NOT_P, "replace", "1", "str", old);
            TruffleString withStr = castSelfNode.cast(inliningTarget, with, ErrorMessages.S_BRACKETS_ARG_S_MUST_BE_S_NOT_P, "replace", "2", "str", with);
            int iMaxCount;
            if (PGuards.isPNone(maxCount)) {
                iMaxCount = -1;
            } else {
                iMaxCount = asSizeNode.executeExact(frame, inliningTarget, maxCount);
            }
            return replaceNode.execute(selfStr, oldStr, withStr, iMaxCount);
        }
    }

    @Builtin(name = "strip", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class StripNode extends PythonBinaryBuiltinNode {
        @Specialization
        static TruffleString doStringString(TruffleString self, TruffleString chars,
                        @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared("cpAtIndex") @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Shared("indexOf") @Cached TruffleString.IndexOfCodePointNode indexOfCodePointNode,
                        @Shared("substring") @Cached TruffleString.SubstringNode substringNode) {
            return StringUtils.strip(self, chars, StripKind.BOTH, codePointLengthNode, codePointAtIndexNode, indexOfCodePointNode, substringNode);
        }

        @Specialization
        static TruffleString doStringNone(TruffleString self, @SuppressWarnings("unused") PNone chars,
                        @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared("cpAtIndex") @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Shared("substring") @Cached TruffleString.SubstringNode substringNode) {
            return StringUtils.strip(self, StripKind.BOTH, codePointLengthNode, codePointAtIndexNode, substringNode);
        }

        @Specialization(replaces = {"doStringString", "doStringNone"})
        static TruffleString doGeneric(Object self, Object chars,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castSelfNode,
                        @Cached CastToTruffleStringCheckedNode castCharsNode,
                        @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared("cpAtIndex") @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Shared("indexOf") @Cached TruffleString.IndexOfCodePointNode indexOfCodePointNode,
                        @Shared("substring") @Cached TruffleString.SubstringNode substringNode) {
            TruffleString selfStr = castSelfNode.cast(inliningTarget, self, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, "strip", self);
            if (PGuards.isPNone(chars)) {
                return doStringNone(selfStr, PNone.NO_VALUE, codePointLengthNode, codePointAtIndexNode, substringNode);
            }
            TruffleString charsStr = castCharsNode.cast(inliningTarget, chars, ErrorMessages.S_ARG_1_MUST_BE_STR_NOT_P, "strip", chars);
            return doStringString(selfStr, charsStr, codePointLengthNode, codePointAtIndexNode, indexOfCodePointNode, substringNode);
        }
    }

    @Builtin(name = "rstrip", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class RStripNode extends PythonBinaryBuiltinNode {
        @Specialization
        static TruffleString doStringString(TruffleString self, TruffleString chars,
                        @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared("cpAtIndex") @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Shared("indexOf") @Cached TruffleString.IndexOfCodePointNode indexOfCodePointNode,
                        @Shared("substring") @Cached TruffleString.SubstringNode substringNode) {
            return StringUtils.strip(self, chars, StripKind.RIGHT, codePointLengthNode, codePointAtIndexNode, indexOfCodePointNode, substringNode);
        }

        @Specialization
        static TruffleString doStringNone(TruffleString self, @SuppressWarnings("unused") PNone chars,
                        @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared("cpAtIndex") @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Shared("substring") @Cached TruffleString.SubstringNode substringNode) {
            return StringUtils.strip(self, StripKind.RIGHT, codePointLengthNode, codePointAtIndexNode, substringNode);
        }

        @Specialization(replaces = {"doStringString", "doStringNone"})
        static TruffleString doGeneric(Object self, Object chars,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castSelfNode,
                        @Cached CastToTruffleStringCheckedNode castCharsNode,
                        @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared("cpAtIndex") @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Shared("indexOf") @Cached TruffleString.IndexOfCodePointNode indexOfCodePointNode,
                        @Shared("substring") @Cached TruffleString.SubstringNode substringNode) {
            TruffleString selfStr = castSelfNode.cast(inliningTarget, self, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, "rstrip", self);
            if (PGuards.isPNone(chars)) {
                return doStringNone(selfStr, PNone.NO_VALUE, codePointLengthNode, codePointAtIndexNode, substringNode);
            }
            TruffleString charsStr = castCharsNode.cast(inliningTarget, chars, ErrorMessages.S_ARG_1_MUST_BE_STR_NOT_P, "rstrip", chars);
            return doStringString(selfStr, charsStr, codePointLengthNode, codePointAtIndexNode, indexOfCodePointNode, substringNode);
        }
    }

    @Builtin(name = "lstrip", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class LStripNode extends PythonBuiltinNode {
        @Specialization
        static TruffleString doStringString(TruffleString self, TruffleString chars,
                        @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared("cpAtIndex") @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Shared("indexOf") @Cached TruffleString.IndexOfCodePointNode indexOfCodePointNode,
                        @Shared("substring") @Cached TruffleString.SubstringNode substringNode) {
            return StringUtils.strip(self, chars, StripKind.LEFT, codePointLengthNode, codePointAtIndexNode, indexOfCodePointNode, substringNode);
        }

        @Specialization
        static TruffleString doStringNone(TruffleString self, @SuppressWarnings("unused") PNone chars,
                        @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared("cpAtIndex") @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Shared("substring") @Cached TruffleString.SubstringNode substringNode) {
            return StringUtils.strip(self, StripKind.LEFT, codePointLengthNode, codePointAtIndexNode, substringNode);
        }

        @Specialization(replaces = {"doStringString", "doStringNone"})
        static TruffleString doGeneric(Object self, Object chars,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castSelfNode,
                        @Cached CastToTruffleStringCheckedNode castCharsNode,
                        @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared("cpAtIndex") @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Shared("indexOf") @Cached TruffleString.IndexOfCodePointNode indexOfCodePointNode,
                        @Shared("substring") @Cached TruffleString.SubstringNode substringNode) {
            TruffleString selfStr = castSelfNode.cast(inliningTarget, self, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, "lstrip", self);
            if (PGuards.isPNone(chars)) {
                return doStringNone(selfStr, PNone.NO_VALUE, codePointLengthNode, codePointAtIndexNode, substringNode);
            }
            TruffleString charsStr = castCharsNode.cast(inliningTarget, chars, ErrorMessages.S_ARG_1_MUST_BE_STR_NOT_P, "lstrip", chars);
            return doStringString(selfStr, charsStr, codePointLengthNode, codePointAtIndexNode, indexOfCodePointNode, substringNode);
        }
    }

    @Slot(SlotKind.sq_length)
    @Slot(SlotKind.mp_length)
    @GenerateUncached
    @GenerateNodeFactory
    public abstract static class LenNode extends LenBuiltinNode {
        @Specialization
        static int len(Object self,
                        @Cached StringLenNode stringLenNode) {
            return stringLenNode.execute(self);
        }
    }

    private static int indexOf(TruffleString self, TruffleString sub, int start, int end, CodePointLengthNode codePointLengthNode, IndexOfStringNode indexOfStringNode) {
        int cpLen = codePointLengthNode.execute(self, TS_ENCODING);
        int cpStart = adjustStartIndex(start, cpLen);
        int cpEnd = adjustEndIndex(end, cpLen);
        if (cpStart < cpEnd) {
            return indexOfStringNode.execute(self, sub, cpStart, cpEnd, TS_ENCODING);
        } else if (sub.isEmpty() && cpStart == cpEnd && cpStart <= cpLen) {
            return cpStart;
        }
        return -1;
    }

    private static int lastIndexOf(TruffleString self, TruffleString sub, int start, int end, CodePointLengthNode codePointLengthNode, LastIndexOfStringNode lastIndexOfStringNode) {
        int cpLen = codePointLengthNode.execute(self, TS_ENCODING);
        int cpStart = adjustStartIndex(start, cpLen);
        int cpEnd = adjustEndIndex(end, cpLen);
        if (cpStart < cpEnd) {
            return lastIndexOfStringNode.execute(self, sub, cpEnd, cpStart, TS_ENCODING);
        } else if (sub.isEmpty() && cpStart == cpEnd && cpStart <= cpLen) {
            return cpStart;
        }
        return -1;
    }

    @Builtin(name = "index", minNumOfPositionalArgs = 2, parameterNames = {"$self", "sub", "start", "end"})
    @ArgumentClinic(name = "start", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "0", useDefaultForNone = true)
    @ArgumentClinic(name = "end", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "Integer.MAX_VALUE", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class IndexNode extends PythonQuaternaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StringBuiltinsClinicProviders.IndexNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static int index(Object selfObj, Object subObj, int start, int end,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.IndexOfStringNode indexOfStringNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            TruffleString self = castNode.cast(inliningTarget, selfObj, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, "index", selfObj);
            TruffleString sub = castNode.cast(inliningTarget, subObj, ErrorMessages.MUST_BE_STR_NOT_P, subObj);

            int idx = indexOf(self, sub, start, end, codePointLengthNode, indexOfStringNode);
            if (idx < 0) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.SUBSTRING_NOT_FOUND);
            }
            return idx;
        }
    }

    @Builtin(name = "rindex", minNumOfPositionalArgs = 2, parameterNames = {"$self", "sub", "start", "end"})
    @ArgumentClinic(name = "start", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "0", useDefaultForNone = true)
    @ArgumentClinic(name = "end", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "Integer.MAX_VALUE", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class RIndexNode extends PythonQuaternaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StringBuiltinsClinicProviders.RIndexNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static int rindex(Object selfObj, Object subObj, int start, int end,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.LastIndexOfStringNode lastIndexOfStringNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            TruffleString self = castNode.cast(inliningTarget, selfObj, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, "rindex", selfObj);
            TruffleString sub = castNode.cast(inliningTarget, subObj, ErrorMessages.MUST_BE_STR_NOT_P, subObj);
            int idx = lastIndexOf(self, sub, start, end, codePointLengthNode, lastIndexOfStringNode);
            if (idx < 0) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.SUBSTRING_NOT_FOUND);
            }
            return idx;
        }
    }

    @Builtin(name = J_ENCODE, minNumOfPositionalArgs = 1, parameterNames = {"self", "encoding", "errors"}, doc = "Decode the bytes using the codec registered for encoding.\n\n" +
                    "    encoding\n" +
                    "      The encoding with which to decode the bytes.\n" +
                    "    errors\n" +
                    "      The error handling scheme to use for the handling of decoding errors.\n" +
                    "      The default is 'strict' meaning that decoding errors raise a\n" +
                    "      UnicodeDecodeError. Other possible values are 'ignore' and 'replace'\n" +
                    "      as well as any other name registered with codecs.register_error that\n" +
                    "      can handle UnicodeDecodeErrors.")
    @ArgumentClinic(name = "encoding", conversion = ClinicConversion.TString, defaultValue = "T_UTF8", useDefaultForNone = true)
    @ArgumentClinic(name = "errors", conversion = ClinicConversion.TString, defaultValue = "T_STRICT", useDefaultForNone = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class EncodeNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StringBuiltinsClinicProviders.EncodeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object doIt(VirtualFrame frame, Object selfObj, TruffleString encoding, TruffleString errors,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castSelfNode,
                        @Cached CodecsModuleBuiltins.EncodeNode encodeNode,
                        @Cached SequenceStorageNodes.CopyNode copyNode,
                        @Cached PythonObjectFactory.Lazy factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            TruffleString self = castSelfNode.cast(inliningTarget, selfObj, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, "index", selfObj);
            Object result = encodeNode.execute(frame, self, encoding, errors);
            if (!(result instanceof PBytes)) {
                if (result instanceof PByteArray) {
                    return factory.get(inliningTarget).createBytes(copyNode.execute(inliningTarget, ((PByteArray) result).getSequenceStorage()));
                }
                throw raiseNode.get(inliningTarget).raise(TypeError, S_ENCODER_RETURNED_P_INSTEAD_OF_BYTES, encoding, result);
            }
            return result;
        }
    }

    @Slot(value = SlotKind.sq_repeat, isComplex = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class MulNode extends SqRepeatBuiltinNode {

        @Specialization(guards = "right <= 0")
        static TruffleString doStringIntNonPositive(@SuppressWarnings("unused") Object left, @SuppressWarnings("unused") int right) {
            return T_EMPTY_STRING;
        }

        @Specialization(guards = "right > 0")
        static TruffleString doStringIntPositive(TruffleString left, int right,
                        @Shared("repeat") @Cached TruffleString.RepeatNode repeatNode) {
            return repeatNode.execute(left, right, TS_ENCODING);
        }

        @Specialization
        static TruffleString doGeneric(Object self, int right,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castSelfNode,
                        @Cached InlinedConditionProfile isNegativeProfile,
                        @Shared("repeat") @Cached TruffleString.RepeatNode repeatNode) {
            TruffleString selfStr = castSelfNode.cast(inliningTarget, self, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, "index", self);
            if (isNegativeProfile.profile(inliningTarget, right <= 0)) {
                return T_EMPTY_STRING;
            }
            return doStringIntPositive(selfStr, right, repeatNode);
        }
    }

    @Builtin(name = J___MOD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ModNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doGeneric(VirtualFrame frame, Object self, Object right,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @Cached CastToJavaStringCheckedNode castSelfNode,
                        @Cached TupleBuiltins.GetItemNode getTupleItemNode,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            String selfStr = castSelfNode.cast(inliningTarget, self, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, T___MOD__, self);
            PythonContext context = PythonContext.get(inliningTarget);
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            Object state = IndirectCallContext.enter(frame, language, context, indirectCallData);
            try {
                return fromJavaStringNode.execute(new StringFormatProcessor(context, getTupleItemNode, selfStr, inliningTarget).format(assertNoJavaString(right)), TS_ENCODING);
            } finally {
                IndirectCallContext.exit(frame, language, context, state);
            }
        }
    }

    @Builtin(name = J___RMOD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RModNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object mod(Object self, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = "isascii", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsAsciiNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean doString(TruffleString self,
                        @Shared("getCodeRange") @Cached TruffleString.GetCodeRangeNode getCodeRangeNode) {
            return getCodeRangeNode.execute(self, TS_ENCODING) == CodeRange.ASCII;
        }

        @Specialization(replaces = "doString")
        boolean doGeneric(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castSelfNode,
                        @Shared("getCodeRange") @Cached TruffleString.GetCodeRangeNode getCodeRangeNode) {
            return doString(castSelfNode.cast(inliningTarget, self, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, "isascii", self), getCodeRangeNode);
        }
    }

    abstract static class IsCategoryBaseNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean doGeneric(Object selfObj,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castSelfNode,
                        @Cached InlinedConditionProfile isEmptyProfile,
                        @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Cached TruffleStringIterator.NextNode nextNode) {
            TruffleString self = castSelfNode.cast(inliningTarget, selfObj, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, getName(), selfObj);
            if (isEmptyProfile.profile(inliningTarget, self.isEmpty())) {
                return resultForEmpty();
            }
            TruffleStringIterator it = createCodePointIteratorNode.execute(self, TS_ENCODING);
            while (it.hasNext()) {
                int codePoint = nextNode.execute(it);
                if (!isCategory(codePoint)) {
                    return false;
                }
            }
            return true;
        }

        @SuppressWarnings("unused")
        protected boolean isCategory(int codePoint) {
            CompilerAsserts.neverPartOfCompilation();
            throw new IllegalStateException("should not be reached");
        }

        protected boolean resultForEmpty() {
            return false;
        }

        protected String getName() {
            CompilerAsserts.neverPartOfCompilation();
            throw new IllegalStateException("should not be reached");
        }
    }

    @Builtin(name = "isalnum", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsAlnumNode extends IsCategoryBaseNode {
        @Override
        protected boolean isCategory(int codePoint) {
            return StringUtils.isAlnum(codePoint);
        }

        @Override
        protected String getName() {
            return "isalnum";
        }
    }

    @Builtin(name = "isalpha", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsAlphaNode extends IsCategoryBaseNode {
        @Override
        @TruffleBoundary
        protected boolean isCategory(int codePoint) {
            return UCharacter.isLetter(codePoint);
        }

        @Override
        protected String getName() {
            return "isalpha";
        }
    }

    @Builtin(name = "isdecimal", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsDecimalNode extends IsCategoryBaseNode {
        @Override
        @TruffleBoundary
        protected boolean isCategory(int codePoint) {
            return UCharacter.isDigit(codePoint);
        }

        @Override
        protected String getName() {
            return "isdecimal";
        }
    }

    @Builtin(name = "isdigit", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsDigitNode extends IsCategoryBaseNode {
        @Override
        @TruffleBoundary
        protected boolean isCategory(int codePoint) {
            int numericType = UCharacter.getIntPropertyValue(codePoint, UProperty.NUMERIC_TYPE);
            return numericType == UCharacter.NumericType.DECIMAL || numericType == UCharacter.NumericType.DIGIT;
        }

        @Override
        protected String getName() {
            return "isdigit";
        }
    }

    @Builtin(name = "isnumeric", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsNumericNode extends IsCategoryBaseNode {
        @Override
        @TruffleBoundary
        protected boolean isCategory(int codePoint) {
            int numericType = UCharacter.getIntPropertyValue(codePoint, UProperty.NUMERIC_TYPE);
            return numericType == UCharacter.NumericType.DECIMAL || numericType == UCharacter.NumericType.DIGIT || numericType == UCharacter.NumericType.NUMERIC;
        }

        @Override
        protected String getName() {
            return "isnumeric";
        }
    }

    @Builtin(name = "isidentifier", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IsIdentifierNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean doGeneric(Object selfObj,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castSelfNode,
                        @Cached StringUtils.IsIdentifierNode isIdentifierNode) {
            TruffleString self = castSelfNode.cast(inliningTarget, selfObj, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, "isidentifier", selfObj);
            return isIdentifierNode.execute(inliningTarget, self);
        }
    }

    @Builtin(name = "islower", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsLowerNode extends PythonUnaryBuiltinNode {
        @TruffleBoundary
        private static boolean isUpper(int codePoint) {
            return UCharacter.isUUppercase(codePoint) || UCharacter.isTitleCase(codePoint);
        }

        @TruffleBoundary
        private static boolean isLower(int codePoint) {
            return UCharacter.isULowercase(codePoint);
        }

        @Specialization
        static boolean doIt(Object selfObj,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castSelfNode,
                        @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Cached TruffleStringIterator.NextNode nextNode) {
            TruffleString self = castSelfNode.cast(inliningTarget, selfObj, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, "islower", selfObj);
            boolean hasLower = false;
            TruffleStringIterator it = createCodePointIteratorNode.execute(self, TS_ENCODING);
            while (it.hasNext()) {
                int codePoint = nextNode.execute(it);
                if (isUpper(codePoint)) {
                    return false;
                }
                if (!hasLower && isLower(codePoint)) {
                    hasLower = true;
                }
            }
            return hasLower;
        }
    }

    @Builtin(name = "isprintable", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsPrintableNode extends IsCategoryBaseNode {
        @Override
        @TruffleBoundary
        protected boolean isCategory(int codePoint) {
            return StringUtils.isPrintable(codePoint);
        }

        @Override
        protected boolean resultForEmpty() {
            return true;
        }

        @Override
        protected String getName() {
            return "isprintable";
        }
    }

    @Builtin(name = "isspace", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsSpaceNode extends IsCategoryBaseNode {
        @Override
        protected boolean isCategory(int codePoint) {
            return StringUtils.isSpace(codePoint);
        }

        @Override
        protected String getName() {
            return "isspace";
        }
    }

    @Builtin(name = "istitle", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsTitleNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean doString(TruffleString self,
                        @Shared("createCpIterator") @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Shared("next") @Cached TruffleStringIterator.NextNode nextNode) {
            boolean cased = false;
            boolean previousIsCased = false;
            TruffleStringIterator it = createCodePointIteratorNode.execute(self, TS_ENCODING);
            while (it.hasNext()) {
                int codePoint = nextNode.execute(it);
                if (isUpper(codePoint)) {
                    if (previousIsCased) {
                        return false;
                    }
                    previousIsCased = true;
                    cased = true;
                } else if (isLower(codePoint)) {
                    if (!previousIsCased) {
                        return false;
                    }
                    previousIsCased = true;
                    cased = true;
                } else {
                    previousIsCased = false;
                }
            }
            return cased;
        }

        @TruffleBoundary
        private static boolean isUpper(int codePoint) {
            return UCharacter.isUUppercase(codePoint) || UCharacter.isTitleCase(codePoint);
        }

        @TruffleBoundary
        private static boolean isLower(int codePoint) {
            return UCharacter.isULowercase(codePoint);
        }

        @Specialization(replaces = "doString")
        static boolean doGeneric(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castSelfNode,
                        @Shared("createCpIterator") @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Shared("next") @Cached TruffleStringIterator.NextNode nextNode) {
            return doString(castSelfNode.cast(inliningTarget, self, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, "istitle", self), createCodePointIteratorNode, nextNode);
        }
    }

    @Builtin(name = "isupper", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsUpperNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean doString(TruffleString self,
                        @Shared("createCpIterator") @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Shared("next") @Cached TruffleStringIterator.NextNode nextNode) {
            boolean hasUpper = false;
            TruffleStringIterator it = createCodePointIteratorNode.execute(self, TS_ENCODING);
            while (it.hasNext()) {
                int codePoint = nextNode.execute(it);
                if (isLower(codePoint)) {
                    return false;
                }
                if (!hasUpper && isUpper(codePoint)) {
                    hasUpper = true;
                }
            }
            return hasUpper;
        }

        @TruffleBoundary
        private static boolean isLower(int codePoint) {
            return UCharacter.isULowercase(codePoint) || UCharacter.isTitleCase(codePoint);
        }

        @TruffleBoundary
        private static boolean isUpper(int codePoint) {
            return UCharacter.isUUppercase(codePoint);
        }

        @Specialization(replaces = "doString")
        static boolean doGeneric(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castSelfNode,
                        @Shared("createCpIterator") @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Shared("next") @Cached TruffleStringIterator.NextNode nextNode) {
            return doString(castSelfNode.cast(inliningTarget, self, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, "isupper", self), createCodePointIteratorNode, nextNode);
        }
    }

    @Builtin(name = "zfill", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ZFillNode extends PythonBinaryBuiltinNode {

        @Specialization
        static TruffleString doGeneric(VirtualFrame frame, Object selfObj, Object widthObj,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castSelfNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            TruffleString self = castSelfNode.cast(inliningTarget, selfObj, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, "zfill", selfObj);
            int width = asSizeNode.executeExact(frame, inliningTarget, widthObj);
            int len = codePointLengthNode.execute(self, TS_ENCODING);
            if (len >= width) {
                return self;
            }
            int nzeros = width - len;
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING, tsbCapacity(nzeros + len));
            if (len > 0) {
                int start = codePointAtIndexNode.execute(self, 0, TS_ENCODING);
                if (start == '+' || start == '-') {
                    appendCodePointNode.execute(sb, start, 1, true);
                    if (nzeros > 0) {
                        appendCodePointNode.execute(sb, '0', nzeros, true);
                    }
                    TruffleString digits = substringNode.execute(self, 1, len - 1, TS_ENCODING, true);
                    appendStringNode.execute(sb, digits);
                } else {
                    if (nzeros > 0) {
                        appendCodePointNode.execute(sb, '0', nzeros, true);
                    }
                    appendStringNode.execute(sb, self);
                }
            } else {
                if (nzeros > 0) {
                    appendCodePointNode.execute(sb, '0', nzeros, true);
                }
            }
            return toStringNode.execute(sb);
        }
    }

    @Builtin(name = "title", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @ArgumentClinic(name = "$self", conversion = ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class TitleNode extends PythonUnaryClinicBuiltinNode {

        @Specialization
        @TruffleBoundary
        static TruffleString doString(TruffleString self,
                        @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Cached TruffleStringIterator.NextNode nextNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING, self.byteLength(TS_ENCODING));
            TruffleStringIterator it = createCodePointIteratorNode.execute(self, TS_ENCODING);
            int start = 0;
            int end = 0;
            while (it.hasNext()) {
                final int cp = nextNode.execute(it);
                if (!UCharacter.isLowerCase(cp) && !UCharacter.isUpperCase(cp)) {
                    if (start == end) {
                        appendCodePointNode.execute(sb, cp, 1, true);
                    } else {
                        appendSegment(self, appendStringNode, substringNode, toJavaStringNode, fromJavaStringNode, sb, start, end);
                    }
                    start = end + 1;
                }
                end++;
            }
            if (start != end) {
                appendSegment(self, appendStringNode, substringNode, toJavaStringNode, fromJavaStringNode, sb, start, end - 1);
            }
            return toStringNode.execute(sb);
        }

        private static void appendSegment(TruffleString self, TruffleStringBuilder.AppendStringNode appendStringNode, TruffleString.SubstringNode substringNode,
                        TruffleString.ToJavaStringNode toJavaStringNode, TruffleString.FromJavaStringNode fromJavaStringNode, TruffleStringBuilder sb, int start, int end) {
            TruffleString segment = substringNode.execute(self, start, end - start + 1, TS_ENCODING, true);
            String titleSegment = UCharacter.toTitleCase(Locale.ROOT, toJavaStringNode.execute(segment), null);
            appendStringNode.execute(sb, fromJavaStringNode.execute(titleSegment, TS_ENCODING));
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StringBuiltinsClinicProviders.TitleNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "center", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @ImportStatic(PString.class)
    abstract static class CenterNode extends PythonBuiltinNode {

        @Specialization
        TruffleString doIt(VirtualFrame frame, Object selfObj, Object width, Object fill,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castSelfNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached CastToTruffleStringCheckedNode castFillNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode,
                        @Cached InlinedConditionProfile errorProfile,
                        @Cached PRaiseNode.Lazy raiseNode) {
            TruffleString self = castSelfNode.cast(inliningTarget, selfObj, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, T___ITER__, selfObj);
            int fillChar;
            if (PGuards.isNoValue(fill)) {
                fillChar = ' ';
            } else {
                TruffleString fillStr = castFillNode.cast(inliningTarget, fill, FILL_CHAR_MUST_BE_UNICODE_CHAR_NOT_P, fill);
                if (errorProfile.profile(inliningTarget, codePointLengthNode.execute(fillStr, TS_ENCODING) != 1)) {
                    throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.FILL_CHAR_MUST_BE_LENGTH_1);
                }
                fillChar = codePointAtIndexNode.execute(fillStr, 0, TS_ENCODING);
            }
            return make(self, asSizeNode.executeExact(frame, inliningTarget, width), fillChar, codePointLengthNode, appendCodePointNode, appendStringNode, toStringNode);
        }

        private TruffleString make(TruffleString self, int width, int fillChar, TruffleString.CodePointLengthNode codePointLengthNode, TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        TruffleStringBuilder.AppendStringNode appendStringNode, TruffleStringBuilder.ToStringNode toStringNode) {
            int len = codePointLengthNode.execute(self, TS_ENCODING);
            if (width <= len) {
                return self;
            }
            int left = getLeftPaddingWidth(len, width);
            int right = width - len - left;
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING, tsbCapacity(len + left + right));
            if (left > 0) {
                appendCodePointNode.execute(sb, fillChar, left, true);
            }
            appendStringNode.execute(sb, self);
            if (right > 0) {
                appendCodePointNode.execute(sb, fillChar, right, true);
            }
            return toStringNode.execute(sb);
        }

        protected int getLeftPaddingWidth(int len, int width) {
            int pad = width - len;
            int half = pad / 2;
            if (pad % 2 > 0 && width % 2 > 0) {
                half += 1;
            }
            return half;
        }
    }

    @Builtin(name = "ljust", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class LJustNode extends CenterNode {

        @Override
        protected int getLeftPaddingWidth(int len, int width) {
            return 0;
        }
    }

    @Builtin(name = "rjust", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class RJustNode extends CenterNode {
        @Override
        protected int getLeftPaddingWidth(int len, int width) {
            return width - len;
        }
    }

    @GenerateUncached
    @SuppressWarnings("truffle-inlining")       // footprint reduction 52 -> 34
    public abstract static class StrGetItemNodeWithSlice extends Node {

        public abstract TruffleString execute(TruffleString value, SliceInfo info);

        static boolean isEmptySlice(SliceInfo s) {
            int step = s.step;
            int start = s.start;
            int stop = s.stop;
            return (step >= 0 && stop <= start) || (step <= 0 && stop >= start);
        }

        static boolean isSimpleSlice(SliceInfo s) {
            return s.step == 1 && s.stop > s.start;
        }

        @Specialization(guards = "isSimpleSlice(slice)")
        static TruffleString doStepOneStopGtStart(TruffleString value, SliceInfo slice,
                        @Cached TruffleString.SubstringNode substringNode) {
            // TODO GR-37219: consider lazy substring when slice.stop - slice.start is close
            // to value.codePointLength
            return substringNode.execute(value, slice.start, slice.stop - slice.start, TS_ENCODING, false);
        }

        @Specialization(guards = "isEmptySlice(slice)")
        static TruffleString doEmptySlice(@SuppressWarnings("unused") TruffleString value, @SuppressWarnings("unused") SliceInfo slice) {
            return T_EMPTY_STRING;
        }

        @Specialization(guards = {"step == slice.step", "!isSimpleSlice(slice)", "!isEmptySlice(slice)"}, limit = "1")
        static TruffleString doGenericCachedStep(TruffleString value, SliceInfo slice,
                        @Bind("this") Node inliningTarget,
                        @Cached(value = "slice.step") int step,
                        @Shared("loop") @Cached InlinedLoopConditionProfile loopProfile,
                        @Shared("len") @Cached LenOfRangeNode sliceLen,
                        @Shared("appendCP") @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Shared("toStr") @Cached TruffleStringBuilder.ToStringNode toStringNode,
                        @Shared("cpAtIndex") @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
            int len = sliceLen.len(inliningTarget, slice);
            int start = slice.start;
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING, tsbCapacity(len));
            int j = 0;
            loopProfile.profileCounted(inliningTarget, len);
            for (int i = start; loopProfile.inject(inliningTarget, j < len); i += step) {
                appendCodePointNode.execute(sb, codePointAtIndexNode.execute(value, i, TS_ENCODING), 1, true);
                j++;
            }
            return toStringNode.execute(sb);
        }

        @Specialization(replaces = "doGenericCachedStep", guards = {"!isSimpleSlice(slice)", "!isEmptySlice(slice)"})
        static TruffleString doGeneric(TruffleString value, SliceInfo slice,
                        @Bind("this") Node inliningTarget,
                        @Shared("loop") @Cached InlinedLoopConditionProfile loopProfile,
                        @Shared("len") @Cached LenOfRangeNode sliceLen,
                        @Shared("appendCP") @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Shared("toStr") @Cached TruffleStringBuilder.ToStringNode toStringNode,
                        @Shared("cpAtIndex") @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
            return doGenericCachedStep(value, slice, inliningTarget, slice.step, loopProfile, sliceLen, appendCodePointNode, toStringNode, codePointAtIndexNode);
        }
    }

    @Slot(value = SlotKind.sq_item, isComplex = true)
    @GenerateNodeFactory
    public abstract static class StrSqItemNode extends SqItemBuiltinNode {
        @Specialization
        static Object doIt(VirtualFrame frame, Object self, int index,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castToString,
                        @Cached TruffleString.SubstringNode substringNode) {
            TruffleString str = castToString.cast(inliningTarget, self, ErrorMessages.DESCRIPTOR_S_REQUIRES_S_OBJ_RECEIVED_P, T___GETITEM__, "str", self);
            return substringNode.execute(str, index, 1, TS_ENCODING, false);
        }
    }

    @Slot(value = SlotKind.mp_subscript, isComplex = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class StrGetItemNode extends MpSubscriptBuiltinNode {

        @Specialization
        static TruffleString doString(VirtualFrame frame, Object self, PSlice slice,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached CastToTruffleStringCheckedNode castToString,
                        @Cached CoerceToIntSlice sliceCast,
                        @Cached ComputeIndices compute,
                        @Cached StrGetItemNodeWithSlice getItemNodeWithSlice,
                        @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode) {
            TruffleString str = castToString.cast(inliningTarget, self, ErrorMessages.DESCRIPTOR_S_REQUIRES_S_OBJ_RECEIVED_P, T___GETITEM__, "str", self);
            SliceInfo info = compute.execute(frame, sliceCast.execute(inliningTarget, slice), codePointLengthNode.execute(str, TS_ENCODING));
            return getItemNodeWithSlice.execute(str, info);
        }

        @Specialization(guards = "!isPSlice(idx)")
        static TruffleString doString(VirtualFrame frame, Object self, Object idx,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached CastToTruffleStringCheckedNode castToString,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            TruffleString str = castToString.cast(inliningTarget, self, ErrorMessages.DESCRIPTOR_S_REQUIRES_S_OBJ_RECEIVED_P, T___GETITEM__, "str", self);
            int len = codePointLengthNode.execute(str, TS_ENCODING);
            int index;
            try {
                index = asSizeNode.executeExact(frame, inliningTarget, idx);
            } catch (PException e) {
                if (!indexCheckNode.execute(inliningTarget, idx)) {
                    throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.STRING_INDICES_MUST_BE_INTEGERS_NOT_P, idx);
                }
                throw e;
            }
            if (index < 0) {
                index += len;
            }
            if (index < 0 || index >= len) {
                throw raiseNode.get(inliningTarget).raise(IndexError, ErrorMessages.STRING_INDEX_OUT_OF_RANGE);
            }
            return substringNode.execute(str, index, 1, TS_ENCODING, false);
        }
    }

    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PStringIterator doGeneric(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castSelfNode,
                        @Cached PythonObjectFactory factory) {
            TruffleString string = castSelfNode.cast(inliningTarget, self, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, T___ITER__, self);
            return factory.createStringIterator(string);
        }
    }

    @Builtin(name = "casefold", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CasefoldNode extends PythonUnaryBuiltinNode {

        @TruffleBoundary
        private static String doJavaString(String self) {
            return UCharacter.foldCase(self, true);
        }

        @Specialization
        static TruffleString doString(TruffleString self,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            return fromJavaStringNode.execute(doJavaString(toJavaStringNode.execute(self)), TS_ENCODING);
        }

        @Specialization(replaces = "doString")
        static TruffleString doGeneric(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToJavaStringCheckedNode castSelfNode,
                        @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            return fromJavaStringNode.execute(doJavaString(castSelfNode.cast(inliningTarget, self, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, "casefold", self)), TS_ENCODING);
        }
    }

    @Builtin(name = "swapcase", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class SwapCaseNode extends PythonUnaryBuiltinNode {

        private static final int CAPITAL_SIGMA = 0x3A3;

        @TruffleBoundary
        private static String doJavaString(String self) {
            StringBuilder sb = new StringBuilder(self.length());
            for (int i = 0; i < self.length();) {
                int codePoint = self.codePointAt(i);
                int charCount = Character.charCount(codePoint);
                String substr = self.substring(i, i + charCount);
                if (UCharacter.isUUppercase(codePoint)) {
                    // Special case for capital sigma, needed because ICU4J doesn't have the context
                    // of the whole string
                    if (codePoint == CAPITAL_SIGMA) {
                        handleCapitalSigma(self, sb, i, codePoint);
                    } else {
                        sb.append(UCharacter.toLowerCase(Locale.ROOT, substr));
                    }
                } else if (UCharacter.isULowercase(codePoint)) {
                    sb.append(UCharacter.toUpperCase(Locale.ROOT, substr));
                } else {
                    sb.append(substr);
                }
                i += charCount;
            }
            return sb.toString();
        }

        // Adapted from unicodeobject.c:handle_capital_sigma
        private static void handleCapitalSigma(String self, StringBuilder sb, int i, int codePoint) {
            int j;
            for (j = i - 1; j >= 0; j--) {
                if (!Character.isLowSurrogate(self.charAt(j))) {
                    int ch = self.codePointAt(j);
                    if (!UCharacter.hasBinaryProperty(ch, UProperty.CASE_IGNORABLE)) {
                        break;
                    }
                }
            }
            boolean finalSigma = j >= 0 && UCharacter.hasBinaryProperty(codePoint, UProperty.CASED);
            if (finalSigma) {
                for (j = i + 1; j < self.length();) {
                    int ch = self.codePointAt(j);
                    if (!UCharacter.hasBinaryProperty(ch, UProperty.CASE_IGNORABLE)) {
                        break;
                    }
                    j += Character.charCount(ch);
                }
                finalSigma = j == self.length() || !UCharacter.hasBinaryProperty(codePoint, UProperty.CASED);
            }
            sb.appendCodePoint(finalSigma ? 0x3C2 : 0x3C3);
        }

        @Specialization
        static TruffleString doString(TruffleString self,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            return fromJavaStringNode.execute(doJavaString(toJavaStringNode.execute(self)), TS_ENCODING);
        }

        @Specialization(replaces = "doString")
        static TruffleString doGeneric(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToJavaStringCheckedNode castSelfNode,
                        @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            return fromJavaStringNode.execute(doJavaString(castSelfNode.cast(inliningTarget, self, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, "swapcase", self)), TS_ENCODING);
        }
    }

    @Builtin(name = "expandtabs", minNumOfPositionalArgs = 1, parameterNames = {"$self", "tabsize"})
    @ArgumentClinic(name = "$self", conversion = ClinicConversion.TString)
    @ArgumentClinic(name = "tabsize", conversion = ClinicConversion.Int, defaultValue = "8", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class ExpandTabsNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        static TruffleString doString(TruffleString self, int tabsize,
                        @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Cached TruffleStringIterator.NextNode nextNode,
                        @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING, self.byteLength(TS_ENCODING));
            int linePos = 0;
            TruffleStringIterator it = createCodePointIteratorNode.execute(self, TS_ENCODING);
            // It's ok to iterate with charAt, we just pass surrogates through
            while (it.hasNext()) {
                int cp = nextNode.execute(it);
                if (cp == '\t') {
                    int incr = tabsize - (linePos % tabsize);
                    if (incr > 0) {
                        appendCodePointNode.execute(sb, ' ', incr, true);
                    }
                    linePos += incr;
                } else {
                    if (cp == '\n' || cp == '\r') {
                        linePos = 0;
                    } else {
                        linePos++;
                    }
                    appendCodePointNode.execute(sb, cp, 1, true);
                }
            }
            return toStringNode.execute(sb);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StringBuiltinsClinicProviders.ExpandTabsNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = J___HASH__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class HashNode extends PythonUnaryBuiltinNode {

        @Specialization
        static long doString(TruffleString self,
                        @Shared("hashCode") @Cached TruffleString.HashCodeNode hashCodeNode) {
            return PyObjectHashNode.hash(self, hashCodeNode);
        }

        @Specialization(replaces = "doString")
        static long doGeneric(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringNode cast,
                        @Shared("hashCode") @Cached TruffleString.HashCodeNode hashCodeNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                return doString(cast.execute(inliningTarget, self), hashCodeNode);
            } catch (CannotCastException e) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.REQUIRES_STR_OBJECT_BUT_RECEIVED_P, T___HASH__, self);
            }
        }
    }

    static int adjustStartIndex(int startIn, int len) {
        if (startIn < 0) {
            int start = startIn + len;
            return start < 0 ? 0 : start;
        }
        return startIn;
    }

    static int adjustEndIndex(int endIn, int len) {
        if (endIn > len) {
            return len;
        } else if (endIn < 0) {
            int end = endIn + len;
            return end < 0 ? 0 : end;
        }
        return endIn;
    }

    @Builtin(name = J_REMOVEPREFIX, minNumOfPositionalArgs = 2, declaresExplicitSelf = true, parameterNames = {"self", "prefix"})
    @ArgumentClinic(name = "self", conversion = ArgumentClinic.ClinicConversion.TString)
    @ArgumentClinic(name = "prefix", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    @ImportStatic(SpecialMethodNames.class)
    abstract static class RemovePrefixNode extends PythonBinaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StringBuiltinsClinicProviders.RemovePrefixNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static TruffleString remove(TruffleString self, TruffleString prefix,
                        @Cached PrefixSuffixNode prefixSuffixNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.SubstringNode substringNode) {
            int prefixLen = codePointLengthNode.execute(prefix, TS_ENCODING);
            if (prefixSuffixNode.startsWith(self, prefix, 0, prefixLen)) {
                int selfLen = codePointLengthNode.execute(self, TS_ENCODING);
                return substringNode.execute(self, prefixLen, selfLen - prefixLen, TS_ENCODING, false);
            }
            return self;
        }
    }

    @Builtin(name = J_REMOVESUFFIX, minNumOfPositionalArgs = 2, declaresExplicitSelf = true, parameterNames = {"self", "suffix"})
    @ArgumentClinic(name = "self", conversion = ArgumentClinic.ClinicConversion.TString)
    @ArgumentClinic(name = "suffix", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    @ImportStatic(SpecialMethodNames.class)
    abstract static class RemoveSuffixNode extends PythonBinaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StringBuiltinsClinicProviders.RemovePrefixNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static TruffleString remove(TruffleString self, TruffleString suffix,
                        @Bind("this") Node inliningTarget,
                        @Cached PrefixSuffixNode prefixSuffixNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Cached InlinedConditionProfile profile) {
            int selfLen = codePointLengthNode.execute(self, TS_ENCODING);
            if (profile.profile(inliningTarget, prefixSuffixNode.endsWith(self, suffix, 0, selfLen))) {
                int suffixLen = codePointLengthNode.execute(suffix, TS_ENCODING);
                return substringNode.execute(self, 0, selfLen - suffixLen, TS_ENCODING, false);
            }
            return self;
        }
    }
}
