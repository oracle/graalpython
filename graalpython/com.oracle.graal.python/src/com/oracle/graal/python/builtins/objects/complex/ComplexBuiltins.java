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
package com.oracle.graal.python.builtins.objects.complex;

import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyComplexObject__cval__imag;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyComplexObject__cval__real;
import static com.oracle.graal.python.nodes.BuiltinNames.J_COMPLEX;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___COMPLEX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___FORMAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETNEWARGS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___COMPLEX__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ZeroDivisionError;
import static com.oracle.graal.python.runtime.formatting.FormattingUtils.validateForFloat;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.common.FormatNodeBase;
import com.oracle.graal.python.builtins.objects.complex.ComplexBuiltinsClinicProviders.FormatNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.floats.FloatBuiltins;
import com.oracle.graal.python.builtins.objects.floats.FloatUtils;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun.HashBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotInquiry.NbBoolBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare;
import com.oracle.graal.python.lib.CanBeDoubleNode;
import com.oracle.graal.python.lib.PyComplexCheckExactNode;
import com.oracle.graal.python.lib.PyComplexCheckNode;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.lib.PyFloatCheckNode;
import com.oracle.graal.python.lib.PyLongAsDoubleNode;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectReprAsObjectNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles;
import com.oracle.graal.python.nodes.truffle.PythonIntegerAndFloatTypes;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.formatting.ComplexFormatter;
import com.oracle.graal.python.runtime.formatting.InternalFormat;
import com.oracle.graal.python.runtime.formatting.InternalFormat.Spec;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PComplex)
public final class ComplexBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = ComplexBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ComplexBuiltinsFactory.getFactories();
    }

    @ValueType
    static final class ComplexValue {
        private final double real;
        private final double imag;

        ComplexValue(double real, double imag) {
            this.real = real;
            this.imag = imag;
        }

        public double getReal() {
            return real;
        }

        public double getImag() {
            return imag;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    abstract static class ToComplexValueNode extends Node {
        public abstract ComplexValue execute(Node inliningTarget, Object v);

        @Specialization
        static ComplexValue doComplex(PComplex v) {
            return new ComplexValue(v.getReal(), v.getImag());
        }

        @Specialization(guards = "check.execute(inliningTarget, v)", limit = "1")
        @InliningCutoff
        static ComplexValue doNative(@SuppressWarnings("unused") Node inliningTarget, PythonAbstractNativeObject v,
                        @SuppressWarnings("unused") @Cached PyComplexCheckNode check,
                        @Cached(inline = false) CStructAccess.ReadDoubleNode read) {
            double real = read.readFromObj(v, PyComplexObject__cval__real);
            double imag = read.readFromObj(v, PyComplexObject__cval__imag);
            return new ComplexValue(real, imag);
        }

        @Specialization
        static ComplexValue doInt(int v) {
            return new ComplexValue(v, 0);
        }

        @Specialization
        static ComplexValue doDouble(double v) {
            return new ComplexValue(v, 0);
        }

        @Specialization(guards = "check.execute(inliningTarget, v)", limit = "1")
        @InliningCutoff
        static ComplexValue doIntGeneric(Node inliningTarget, Object v,
                        @SuppressWarnings("unused") @Cached PyLongCheckNode check,
                        @Cached PyLongAsDoubleNode longAsDoubleNode) {
            return new ComplexValue(longAsDoubleNode.execute(inliningTarget, v), 0);
        }

        @Specialization(guards = "check.execute(inliningTarget, v)", limit = "1")
        @InliningCutoff
        static ComplexValue doFloatGeneric(Node inliningTarget, Object v,
                        @SuppressWarnings("unused") @Cached PyFloatCheckNode check,
                        @Cached PyFloatAsDoubleNode floatAsDoubleNode) {
            return new ComplexValue(floatAsDoubleNode.execute(null, inliningTarget, v), 0);
        }

        @Fallback
        @SuppressWarnings("unused")
        static ComplexValue doOther(Node inliningTarget, Object v) {
            return null;
        }
    }

    // complex([real[, imag]])
    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = J_COMPLEX, minNumOfPositionalArgs = 1, parameterNames = {"$cls", "real", "imag"})
    @GenerateNodeFactory
    public abstract static class ComplexNewNode extends PythonTernaryBuiltinNode {
        @Child private PyObjectReprAsObjectNode reprNode;
        @Child private LookupAndCallUnaryNode callComplexNode;
        @Child private WarningsModuleBuiltins.WarnNode warnNode;

        @GenerateInline
        @GenerateCached(false)
        @GenerateUncached
        abstract static class CreateComplexNode extends Node {
            public abstract Object execute(Node inliningTarget, Object cls, double real, double imaginary);

            public static Object executeUncached(Object cls, double real, double imaginary) {
                return ComplexBuiltinsFactory.ComplexNewNodeFactory.CreateComplexNodeGen.getUncached().execute(null, cls, real, imaginary);
            }

            @Specialization(guards = "!needsNativeAllocationNode.execute(inliningTarget, cls)", limit = "1")
            static PComplex doManaged(@SuppressWarnings("unused") Node inliningTarget, Object cls, double real, double imaginary,
                            @SuppressWarnings("unused") @Cached TypeNodes.NeedsNativeAllocationNode needsNativeAllocationNode,
                            @Bind PythonLanguage language,
                            @Cached TypeNodes.GetInstanceShape getInstanceShape) {
                return PFactory.createComplex(language, cls, getInstanceShape.execute(cls), real, imaginary);
            }

            @Fallback
            static Object doNative(Node inliningTarget, Object cls, double real, double imaginary,
                            @Cached(inline = false) CExtNodes.PCallCapiFunction callCapiFunction,
                            @Cached(inline = false) CApiTransitions.PythonToNativeNode toNativeNode,
                            @Cached(inline = false) CApiTransitions.NativeToPythonTransferNode toPythonNode,
                            @Cached(inline = false) ExternalFunctionNodes.DefaultCheckFunctionResultNode checkFunctionResultNode) {
                NativeCAPISymbol symbol = NativeCAPISymbol.FUN_COMPLEX_SUBTYPE_FROM_DOUBLES;
                Object nativeResult = callCapiFunction.call(symbol, toNativeNode.execute(cls), real, imaginary);
                return toPythonNode.execute(checkFunctionResultNode.execute(PythonContext.get(inliningTarget), symbol.getTsName(), nativeResult));
            }
        }

        @Specialization(guards = {"isNoValue(real)", "isNoValue(imag)"})
        @SuppressWarnings("unused")
        static Object complexFromNone(Object cls, PNone real, PNone imag,
                        @Bind Node inliningTarget,
                        @Cached.Shared @Cached CreateComplexNode createComplexNode) {
            return createComplexNode.execute(inliningTarget, cls, 0, 0);
        }

        @Specialization
        static Object complexFromIntInt(Object cls, int real, int imaginary,
                        @Bind Node inliningTarget,
                        @Cached.Shared @Cached CreateComplexNode createComplexNode) {
            return createComplexNode.execute(inliningTarget, cls, real, imaginary);
        }

        @Specialization
        static Object complexFromLongLong(Object cls, long real, long imaginary,
                        @Bind Node inliningTarget,
                        @Cached.Shared @Cached CreateComplexNode createComplexNode) {
            return createComplexNode.execute(inliningTarget, cls, real, imaginary);
        }

        @Specialization
        static Object complexFromLongLong(Object cls, PInt real, PInt imaginary,
                        @Bind Node inliningTarget,
                        @Cached.Shared @Cached CreateComplexNode createComplexNode) {
            return createComplexNode.execute(inliningTarget, cls, real.doubleValueWithOverflow(inliningTarget),
                            imaginary.doubleValueWithOverflow(inliningTarget));
        }

        @Specialization
        static Object complexFromDoubleDouble(Object cls, double real, double imaginary,
                        @Bind Node inliningTarget,
                        @Cached.Shared @Cached CreateComplexNode createComplexNode) {
            return createComplexNode.execute(inliningTarget, cls, real, imaginary);
        }

        @Specialization(guards = "isNoValue(imag)")
        static Object complexFromDouble(Object cls, double real, @SuppressWarnings("unused") PNone imag,
                        @Bind Node inliningTarget,
                        @Cached.Shared @Cached CreateComplexNode createComplexNode) {
            return createComplexNode.execute(inliningTarget, cls, real, 0);
        }

        @Specialization(guards = "isNoValue(real)")
        static Object complexFromDoubleImag(Object cls, @SuppressWarnings("unused") PNone real, double imag,
                        @Bind Node inliningTarget,
                        @Cached.Shared @Cached CreateComplexNode createComplexNode) {
            return createComplexNode.execute(inliningTarget, cls, 0, imag);
        }

        @Specialization(guards = "isNoValue(imag)")
        Object complexFromDouble(VirtualFrame frame, Object cls, PFloat real, @SuppressWarnings("unused") PNone imag,
                        @Bind Node inliningTarget,
                        @Cached.Shared @Cached CreateComplexNode createComplexNode,
                        @Cached.Shared @Cached CanBeDoubleNode canBeDoubleNode,
                        @Cached.Shared("floatAsDouble") @Cached PyFloatAsDoubleNode asDoubleNode,
                        @Cached.Shared("isComplex") @Cached PyComplexCheckExactNode isComplexType,
                        @Cached.Shared("isComplexResult") @Cached PyComplexCheckExactNode isResultComplexType,
                        @Cached.Shared("isPrimitive") @Cached BuiltinClassProfiles.IsBuiltinClassExactProfile isPrimitiveProfile,
                        @Cached.Shared("isBuiltinObj") @Cached PyComplexCheckExactNode isBuiltinObjectProfile,
                        @Cached.Shared @Cached PRaiseNode raiseNode) {
            return complexFromObject(frame, cls, real, imag, inliningTarget, createComplexNode, canBeDoubleNode, asDoubleNode, isComplexType, isResultComplexType, isPrimitiveProfile,
                            isBuiltinObjectProfile,
                            raiseNode);
        }

        @Specialization(guards = "isNoValue(real)")
        Object complexFromDouble(VirtualFrame frame, Object cls, @SuppressWarnings("unused") PNone real, PFloat imag,
                        @Bind Node inliningTarget,
                        @Cached.Shared @Cached CreateComplexNode createComplexNode,
                        @Cached.Shared @Cached CanBeDoubleNode canBeDoubleNode,
                        @Cached.Shared("floatAsDouble") @Cached PyFloatAsDoubleNode asDoubleNode,
                        @Cached.Shared("isComplex") @Cached PyComplexCheckExactNode isComplexType,
                        @Cached.Shared("isComplexResult") @Cached PyComplexCheckExactNode isResultComplexType,
                        @Cached.Shared("isPrimitive") @Cached BuiltinClassProfiles.IsBuiltinClassExactProfile isPrimitiveProfile,
                        @Cached.Shared("isBuiltinObj") @Cached PyComplexCheckExactNode isBuiltinObjectProfile,
                        @Cached.Shared @Cached PRaiseNode raiseNode) {
            return complexFromObject(frame, cls, real, imag, inliningTarget, createComplexNode, canBeDoubleNode, asDoubleNode, isComplexType, isResultComplexType, isPrimitiveProfile,
                            isBuiltinObjectProfile,
                            raiseNode);
        }

        @Specialization(guards = "isNoValue(imag)")
        static Object complexFromInt(Object cls, int real, @SuppressWarnings("unused") PNone imag,
                        @Bind Node inliningTarget,
                        @Cached.Shared @Cached CreateComplexNode createComplexNode) {
            return createComplexNode.execute(inliningTarget, cls, real, 0);
        }

        @Specialization(guards = "isNoValue(real)")
        static Object complexFromIntImag(Object cls, @SuppressWarnings("unused") PNone real, int imag,
                        @Bind Node inliningTarget,
                        @Cached.Shared @Cached CreateComplexNode createComplexNode) {
            return createComplexNode.execute(inliningTarget, cls, 0, imag);
        }

        @Specialization(guards = "isNoValue(imag)")
        static Object complexFromLong(Object cls, long real, @SuppressWarnings("unused") PNone imag,
                        @Bind Node inliningTarget,
                        @Cached.Shared @Cached CreateComplexNode createComplexNode) {
            return createComplexNode.execute(inliningTarget, cls, real, 0);
        }

        @Specialization(guards = "isNoValue(real)")
        static Object complexFromLongImag(Object cls, @SuppressWarnings("unused") PNone real, long imag,
                        @Bind Node inliningTarget,
                        @Cached.Shared @Cached CreateComplexNode createComplexNode) {
            return createComplexNode.execute(inliningTarget, cls, 0, imag);
        }

        @Specialization(guards = "isNoValue(imag)")
        Object complexFromLong(VirtualFrame frame, Object cls, PInt real, @SuppressWarnings("unused") PNone imag,
                        @Bind Node inliningTarget,
                        @Cached.Shared @Cached CreateComplexNode createComplexNode,
                        @Cached.Shared @Cached CanBeDoubleNode canBeDoubleNode,
                        @Cached.Shared("floatAsDouble") @Cached PyFloatAsDoubleNode asDoubleNode,
                        @Cached.Shared("isComplex") @Cached PyComplexCheckExactNode isComplexType,
                        @Cached.Shared("isComplexResult") @Cached PyComplexCheckExactNode isResultComplexType,
                        @Cached.Shared("isPrimitive") @Cached BuiltinClassProfiles.IsBuiltinClassExactProfile isPrimitiveProfile,
                        @Cached.Shared("isBuiltinObj") @Cached PyComplexCheckExactNode complexCheck,
                        @Cached.Shared @Cached PRaiseNode raiseNode) {
            return complexFromObject(frame, cls, real, imag, inliningTarget, createComplexNode, canBeDoubleNode, asDoubleNode, isComplexType, isResultComplexType, isPrimitiveProfile, complexCheck,
                            raiseNode);
        }

        @Specialization(guards = "isNoValue(real)")
        Object complexFromLong(VirtualFrame frame, Object cls, @SuppressWarnings("unused") PNone real, PInt imag,
                        @Bind Node inliningTarget,
                        @Cached.Shared @Cached CreateComplexNode createComplexNode,
                        @Cached.Shared @Cached CanBeDoubleNode canBeDoubleNode,
                        @Cached.Shared("floatAsDouble") @Cached PyFloatAsDoubleNode asDoubleNode,
                        @Cached.Shared("isComplex") @Cached PyComplexCheckExactNode isComplexType,
                        @Cached.Shared("isComplexResult") @Cached PyComplexCheckExactNode isResultComplexType,
                        @Cached.Shared("isPrimitive") @Cached BuiltinClassProfiles.IsBuiltinClassExactProfile isPrimitiveProfile,
                        @Cached.Shared("isBuiltinObj") @Cached PyComplexCheckExactNode complexCheck,
                        @Cached.Shared @Cached PRaiseNode raiseNode) {
            return complexFromObject(frame, cls, real, imag, inliningTarget, createComplexNode, canBeDoubleNode, asDoubleNode, isComplexType, isResultComplexType, isPrimitiveProfile, complexCheck,
                            raiseNode);
        }

        @Specialization(guards = {"isNoValue(imag)", "!isNoValue(number)", "!isString(number)"})
        Object complexFromObject(VirtualFrame frame, Object cls, Object number, @SuppressWarnings("unused") PNone imag,
                        @Bind Node inliningTarget,
                        @Cached.Shared @Cached CreateComplexNode createComplexNode,
                        @Cached.Shared @Cached CanBeDoubleNode canBeDoubleNode,
                        @Cached.Shared("floatAsDouble") @Cached PyFloatAsDoubleNode asDoubleNode,
                        @Cached.Shared("isComplex") @Cached PyComplexCheckExactNode isComplexType,
                        @Cached.Shared("isComplexResult") @Cached PyComplexCheckExactNode isResultComplexType,
                        @Cached.Shared("isPrimitive") @Cached BuiltinClassProfiles.IsBuiltinClassExactProfile isPrimitiveProfile,
                        @Cached.Shared("isBuiltinObj") @Cached PyComplexCheckExactNode complexCheck,
                        @Cached.Shared @Cached PRaiseNode raiseNode) {
            PComplex value = getComplexNumberFromObject(frame, number, inliningTarget, isComplexType, isResultComplexType, raiseNode);
            if (value == null) {
                if (canBeDoubleNode.execute(inliningTarget, number)) {
                    return createComplexNode.execute(inliningTarget, cls, asDoubleNode.execute(frame, inliningTarget, number), 0.0);
                } else {
                    throw raiseFirstArgError(number, inliningTarget, raiseNode);
                }
            }
            if (isPrimitiveProfile.profileClass(inliningTarget, cls, PythonBuiltinClassType.PComplex)) {
                if (complexCheck.execute(inliningTarget, value)) {
                    return value;
                }
                return PFactory.createComplex(PythonLanguage.get(inliningTarget), value.getReal(), value.getImag());
            }
            return createComplexNode.execute(inliningTarget, cls, value.getReal(), value.getImag());
        }

        @Specialization(guards = {"!isNoValue(imag)", "isNoValue(number)"})
        Object complexFromObject(VirtualFrame frame, Object cls, @SuppressWarnings("unused") PNone number, Object imag,
                        @Bind Node inliningTarget,
                        @Cached.Shared @Cached CreateComplexNode createComplexNode,
                        @Cached.Shared @Cached CanBeDoubleNode canBeDoubleNode,
                        @Cached.Shared("floatAsDouble") @Cached PyFloatAsDoubleNode asDoubleNode,
                        @Cached.Shared("isComplex") @Cached PyComplexCheckExactNode isComplexType,
                        @Cached.Shared("isComplexResult") @Cached PyComplexCheckExactNode isResultComplexType,
                        @Cached.Shared("isPrimitive") @Cached BuiltinClassProfiles.IsBuiltinClassExactProfile isPrimitiveProfile,
                        @Cached.Shared("isBuiltinObj") @Cached PyComplexCheckExactNode complexCheck,
                        @Cached.Shared @Cached PRaiseNode raiseNode) {
            PComplex value = getComplexNumberFromObject(frame, imag, inliningTarget, isComplexType, isResultComplexType, raiseNode);
            if (value == null) {
                if (canBeDoubleNode.execute(inliningTarget, imag)) {
                    return createComplexNode.execute(inliningTarget, cls, 0.0, asDoubleNode.execute(frame, inliningTarget, imag));
                } else {
                    throw raiseSecondArgError(imag, inliningTarget, raiseNode);
                }
            }
            if (isPrimitiveProfile.profileClass(inliningTarget, cls, PythonBuiltinClassType.PComplex)) {
                return PFactory.createComplex(PythonLanguage.get(inliningTarget), -value.getImag(), 0.0);
            }
            return createComplexNode.execute(inliningTarget, cls, -value.getImag(), 0.0);
        }

        @Specialization
        static Object complexFromLongComplex(Object cls, long one, PComplex two,
                        @Bind Node inliningTarget,
                        @Cached.Shared @Cached CreateComplexNode createComplexNode) {
            return createComplexNode.execute(inliningTarget, cls, one - two.getImag(), two.getReal());
        }

        @Specialization
        static Object complexFromPIntComplex(Object cls, PInt one, PComplex two,
                        @Bind Node inliningTarget,
                        @Cached.Shared @Cached CreateComplexNode createComplexNode) {
            return createComplexNode.execute(inliningTarget, cls, one.doubleValueWithOverflow(inliningTarget) - two.getImag(), two.getReal());
        }

        @Specialization
        static Object complexFromDoubleComplex(Object cls, double one, PComplex two,
                        @Bind Node inliningTarget,
                        @Cached.Shared @Cached CreateComplexNode createComplexNode) {
            return createComplexNode.execute(inliningTarget, cls, one - two.getImag(), two.getReal());
        }

        @Specialization(guards = {"!isString(one)", "!isNoValue(one)"})
        Object complexFromComplexLong(VirtualFrame frame, Object cls, Object one, long two,
                        @Bind Node inliningTarget,
                        @Cached.Shared @Cached CreateComplexNode createComplexNode,
                        @Cached.Shared @Cached CanBeDoubleNode canBeDoubleNode,
                        @Cached.Shared("floatAsDouble") @Cached PyFloatAsDoubleNode asDoubleNode,
                        @Cached.Shared("isComplex") @Cached PyComplexCheckExactNode isComplexType,
                        @Cached.Shared("isComplexResult") @Cached PyComplexCheckExactNode isResultComplexType,
                        @Cached.Shared @Cached PRaiseNode raiseNode) {
            PComplex value = getComplexNumberFromObject(frame, one, inliningTarget, isComplexType, isResultComplexType, raiseNode);
            if (value == null) {
                if (canBeDoubleNode.execute(inliningTarget, one)) {
                    return createComplexNode.execute(inliningTarget, cls, asDoubleNode.execute(frame, inliningTarget, one), two);
                } else {
                    throw raiseFirstArgError(one, inliningTarget, raiseNode);
                }
            }
            return createComplexNode.execute(inliningTarget, cls, value.getReal(), value.getImag() + two);
        }

        @Specialization(guards = {"!isString(one)", "!isNoValue(one)"})
        Object complexFromComplexDouble(VirtualFrame frame, Object cls, Object one, double two,
                        @Bind Node inliningTarget,
                        @Cached.Shared @Cached CreateComplexNode createComplexNode,
                        @Cached.Shared @Cached CanBeDoubleNode canBeDoubleNode,
                        @Cached.Shared("floatAsDouble") @Cached PyFloatAsDoubleNode asDoubleNode,
                        @Cached.Shared("isComplex") @Cached PyComplexCheckExactNode isComplexType,
                        @Cached.Shared("isComplexResult") @Cached PyComplexCheckExactNode isResultComplexType,
                        @Cached.Shared @Cached PRaiseNode raiseNode) {
            PComplex value = getComplexNumberFromObject(frame, one, inliningTarget, isComplexType, isResultComplexType, raiseNode);
            if (value == null) {
                if (canBeDoubleNode.execute(inliningTarget, one)) {
                    return createComplexNode.execute(inliningTarget, cls, asDoubleNode.execute(frame, inliningTarget, one), two);
                } else {
                    throw raiseFirstArgError(one, inliningTarget, raiseNode);
                }
            }
            return createComplexNode.execute(inliningTarget, cls, value.getReal(), value.getImag() + two);
        }

        @Specialization(guards = {"!isString(one)", "!isNoValue(one)"})
        Object complexFromComplexPInt(VirtualFrame frame, Object cls, Object one, PInt two,
                        @Bind Node inliningTarget,
                        @Cached.Shared @Cached CreateComplexNode createComplexNode,
                        @Cached.Shared @Cached CanBeDoubleNode canBeDoubleNode,
                        @Cached.Shared("floatAsDouble") @Cached PyFloatAsDoubleNode asDoubleNode,
                        @Cached.Shared("isComplex") @Cached PyComplexCheckExactNode isComplexType,
                        @Cached.Shared("isComplexResult") @Cached PyComplexCheckExactNode isResultComplexType,
                        @Cached.Shared @Cached PRaiseNode raiseNode) {
            PComplex value = getComplexNumberFromObject(frame, one, inliningTarget, isComplexType, isResultComplexType, raiseNode);
            if (value == null) {
                if (canBeDoubleNode.execute(inliningTarget, one)) {
                    return createComplexNode.execute(inliningTarget, cls, asDoubleNode.execute(frame, inliningTarget, one), two.doubleValueWithOverflow(this));
                } else {
                    throw raiseFirstArgError(one, inliningTarget, raiseNode);
                }
            }
            return createComplexNode.execute(inliningTarget, cls, value.getReal(), value.getImag() + two.doubleValueWithOverflow(this));
        }

        @Specialization(guards = {"!isString(one)", "!isNoValue(one)"})
        Object complexFromComplexComplex(VirtualFrame frame, Object cls, Object one, PComplex two,
                        @Bind Node inliningTarget,
                        @Cached.Shared @Cached CreateComplexNode createComplexNode,
                        @Cached.Shared @Cached CanBeDoubleNode canBeDoubleNode,
                        @Cached.Shared("floatAsDouble") @Cached PyFloatAsDoubleNode asDoubleNode,
                        @Cached.Shared("isComplex") @Cached PyComplexCheckExactNode isComplexType,
                        @Cached.Shared("isComplexResult") @Cached PyComplexCheckExactNode isResultComplexType,
                        @Cached.Shared @Cached PRaiseNode raiseNode) {
            PComplex value = getComplexNumberFromObject(frame, one, inliningTarget, isComplexType, isResultComplexType, raiseNode);
            if (value == null) {
                if (canBeDoubleNode.execute(inliningTarget, one)) {
                    return createComplexNode.execute(inliningTarget, cls, asDoubleNode.execute(frame, inliningTarget, one) - two.getImag(), two.getReal());
                } else {
                    throw raiseFirstArgError(one, inliningTarget, raiseNode);
                }
            }
            return createComplexNode.execute(inliningTarget, cls, value.getReal() - two.getImag(), value.getImag() + two.getReal());
        }

        @Specialization(guards = {"isString(two)"})
        Object secondArgString(VirtualFrame frame, Object cls, Object one, Object two,
                        @Bind Node inliningTarget,
                        @Cached.Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.COMPLEX_SECOND_ARG_CANT_BE_STRING);
        }

        @Specialization(guards = {"!isString(one)", "!isNoValue(two)", "!isPComplex(two)", "!isString(two)"})
        @SuppressWarnings("truffle-static-method")
        Object complexFromComplexObject(VirtualFrame frame, Object cls, Object one, Object two,
                        @Bind Node inliningTarget,
                        @Cached.Shared @Cached CreateComplexNode createComplexNode,
                        @Cached.Shared @Cached CanBeDoubleNode canBeDoubleNode,
                        @Cached.Shared("floatAsDouble") @Cached PyFloatAsDoubleNode asDoubleNode,
                        @Cached.Shared("isComplex") @Cached PyComplexCheckExactNode isComplexType,
                        @Cached.Shared("isComplexResult") @Cached PyComplexCheckExactNode isResultComplexType,
                        @Cached.Shared @Cached PRaiseNode raiseNode) {
            PComplex oneValue = getComplexNumberFromObject(frame, one, inliningTarget, isComplexType, isResultComplexType, raiseNode);
            if (canBeDoubleNode.execute(inliningTarget, two)) {
                double twoValue = asDoubleNode.execute(frame, inliningTarget, two);
                if (oneValue == null) {
                    if (one == PNone.NO_VALUE) {
                        return createComplexNode.execute(inliningTarget, cls, 0, twoValue);
                    } else if (canBeDoubleNode.execute(inliningTarget, one)) {
                        return createComplexNode.execute(inliningTarget, cls, asDoubleNode.execute(frame, inliningTarget, one), twoValue);
                    } else {
                        throw raiseFirstArgError(one, inliningTarget, raiseNode);
                    }
                }
                return createComplexNode.execute(inliningTarget, cls, oneValue.getReal(), oneValue.getImag() + twoValue);
            } else {
                throw raiseSecondArgError(two, inliningTarget, raiseNode);
            }
        }

        @Specialization
        Object complexFromString(VirtualFrame frame, Object cls, TruffleString real, Object imaginary,
                        @Bind Node inliningTarget,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @Cached.Shared @Cached PRaiseNode raiseNode) {
            if (imaginary != PNone.NO_VALUE) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.COMPLEX_CANT_TAKE_ARG);
            }
            return convertStringToComplex(frame, inliningTarget, toJavaStringNode.execute(real), cls, real, raiseNode);
        }

        @Specialization
        Object complexFromString(VirtualFrame frame, Object cls, PString real, Object imaginary,
                        @Bind Node inliningTarget,
                        @Cached CastToJavaStringNode castToStringNode,
                        @Cached.Shared @Cached PRaiseNode raiseNode) {
            if (imaginary != PNone.NO_VALUE) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.COMPLEX_CANT_TAKE_ARG);
            }
            return convertStringToComplex(frame, inliningTarget, castToStringNode.execute(real), cls, real, raiseNode);
        }

        private Object callComplex(VirtualFrame frame, Object object) {
            if (callComplexNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callComplexNode = insert(LookupAndCallUnaryNode.create(T___COMPLEX__));
            }
            return callComplexNode.executeObject(frame, object);
        }

        private WarningsModuleBuiltins.WarnNode getWarnNode() {
            if (warnNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                warnNode = insert(WarningsModuleBuiltins.WarnNode.create());
            }
            return warnNode;
        }

        private static PException raiseFirstArgError(Object x, Node inliningTarget, PRaiseNode raiseNode) {
            throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.ARG_MUST_BE_STRING_OR_NUMBER, "complex() first", x);
        }

        private static PException raiseSecondArgError(Object x, Node inliningTarget, PRaiseNode raiseNode) {
            throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.ARG_MUST_BE_NUMBER, "complex() second", x);
        }

        private PComplex getComplexNumberFromObject(VirtualFrame frame, Object object, Node inliningTarget,
                        PyComplexCheckExactNode isComplexType, PyComplexCheckExactNode isResultComplexType, PRaiseNode raiseNode) {
            if (isComplexType.execute(inliningTarget, object)) {
                return (PComplex) object;
            } else {
                Object result = callComplex(frame, object);
                if (result instanceof PComplex) {
                    if (!isResultComplexType.execute(inliningTarget, result)) {
                        getWarnNode().warnFormat(frame, null, PythonBuiltinClassType.DeprecationWarning, 1,
                                        ErrorMessages.WARN_P_RETURNED_NON_P,
                                        object, "__complex__", "complex", result, "complex");
                    }
                    return (PComplex) result;
                } else if (result != PNone.NO_VALUE) {
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.COMPLEX_RETURNED_NON_COMPLEX, result);
                }
                if (object instanceof PComplex) {
                    // the class extending PComplex but doesn't have __complex__ method
                    return (PComplex) object;
                }
                return null;
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object complexGeneric(Object cls, Object realObj, Object imaginaryObj,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "complex.__new__(X): X", cls);
        }

        // Adapted from CPython's complex_subtype_from_string
        private Object convertStringToComplex(VirtualFrame frame, Node inliningTarget, String src, Object cls, Object origObj, PRaiseNode raiseNode) {
            String str = FloatUtils.removeUnicodeAndUnderscores(src);
            if (str == null) {
                if (reprNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    reprNode = insert(PyObjectReprAsObjectNode.create());
                }
                Object strStr = reprNode.executeCached(frame, origObj);
                if (PGuards.isString(strStr)) {
                    throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.COULD_NOT_CONVERT_STRING_TO_COMPLEX, strStr);
                } else {
                    // During the formatting of "ValueError: invalid literal ..." exception,
                    // CPython attempts to raise "TypeError: __repr__ returned non-string",
                    // which gets later overwitten with the original "ValueError",
                    // but without any message (since the message formatting failed)
                    throw raiseNode.raise(inliningTarget, ValueError);
                }
            }
            Object c = convertStringToComplexOrNull(str, cls);
            if (c == null) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.COMPLEX_ARG_IS_MALFORMED_STR);
            }
            return c;
        }

        // Adapted from CPython's complex_from_string_inner
        @TruffleBoundary
        private Object convertStringToComplexOrNull(String str, Object cls) {
            int len = str.length();

            // position on first nonblank
            int i = FloatUtils.skipAsciiWhitespace(str, 0, len);

            boolean gotBracket;
            if (i < len && str.charAt(i) == '(') {
                // Skip over possible bracket from repr().
                gotBracket = true;
                i = FloatUtils.skipAsciiWhitespace(str, i + 1, len);
            } else {
                gotBracket = false;
            }

            double x, y;
            boolean expectJ;

            // first look for forms starting with <float>
            FloatUtils.StringToDoubleResult res1 = FloatUtils.stringToDouble(str, i, len);
            if (res1 != null) {
                // all 4 forms starting with <float> land here
                i = res1.position;
                char ch = i < len ? str.charAt(i) : '\0';
                if (ch == '+' || ch == '-') {
                    // <float><signed-float>j | <float><sign>j
                    x = res1.value;
                    FloatUtils.StringToDoubleResult res2 = FloatUtils.stringToDouble(str, i, len);
                    if (res2 != null) {
                        // <float><signed-float>j
                        y = res2.value;
                        i = res2.position;
                    } else {
                        // <float><sign>j
                        y = ch == '+' ? 1.0 : -1.0;
                        i++;
                    }
                    expectJ = true;
                } else if (ch == 'j' || ch == 'J') {
                    // <float>j
                    i++;
                    y = res1.value;
                    x = 0;
                    expectJ = false;
                } else {
                    // <float>
                    x = res1.value;
                    y = 0;
                    expectJ = false;
                }
            } else {
                // not starting with <float>; must be <sign>j or j
                char ch = i < len ? str.charAt(i) : '\0';
                if (ch == '+' || ch == '-') {
                    // <sign>j
                    y = ch == '+' ? 1.0 : -1.0;
                    i++;
                } else {
                    // j
                    y = 1.0;
                }
                x = 0;
                expectJ = true;
            }

            if (expectJ) {
                char ch = i < len ? str.charAt(i) : '\0';
                if (!(ch == 'j' || ch == 'J')) {
                    return null;
                }
                i++;
            }

            // trailing whitespace and closing bracket
            i = FloatUtils.skipAsciiWhitespace(str, i, len);
            if (gotBracket) {
                // if there was an opening parenthesis, then the corresponding
                // closing parenthesis should be right here
                if (i >= len || str.charAt(i) != ')') {
                    return null;
                }
                i = FloatUtils.skipAsciiWhitespace(str, i + 1, len);
            }

            // we should now be at the end of the string
            if (i != len) {
                return null;
            }
            return CreateComplexNode.executeUncached(cls, x, y);
        }
    }

    @Builtin(name = J___COMPLEX__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ComplexNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object complex(Object self,
                        @Bind Node inliningTarget,
                        @Cached PyComplexCheckExactNode check,
                        @Cached ToComplexValueNode toComplexValueNode) {
            if (check.execute(inliningTarget, self)) {
                return self;
            } else {
                ComplexValue c = toComplexValueNode.execute(inliningTarget, self);
                return PFactory.createComplex(PythonLanguage.get(inliningTarget), c.real, c.imag);
            }
        }
    }

    @Slot(value = SlotKind.nb_absolute, isComplex = true)
    @GenerateNodeFactory
    public abstract static class AbsNode extends PythonUnaryBuiltinNode {

        public abstract double executeDouble(Object arg);

        @Specialization
        @InliningCutoff
        static double abs(Object self,
                        @Bind Node inliningTarget,
                        @Cached ToComplexValueNode toComplexValueNode,
                        @Cached PRaiseNode raiseNode) {
            ComplexValue c = toComplexValueNode.execute(inliningTarget, self);
            double x = c.getReal();
            double y = c.getImag();
            if (Double.isInfinite(x) || Double.isInfinite(y)) {
                return Double.POSITIVE_INFINITY;
            } else if (Double.isNaN(x) || Double.isNaN(y)) {
                return Double.NaN;
            } else {

                final int expX = getExponent(x);
                final int expY = getExponent(y);
                if (expX > expY + 27) {
                    // y is neglectible with respect to x
                    return abs(x);
                } else if (expY > expX + 27) {
                    // x is neglectible with respect to y
                    return abs(y);
                } else {

                    // find an intermediate scale to avoid both overflow and
                    // underflow
                    final int middleExp = (expX + expY) / 2;

                    // scale parameters without losing precision
                    final double scaledX = scalb(x, -middleExp);
                    final double scaledY = scalb(y, -middleExp);

                    // compute scaled hypotenuse
                    final double scaledH = Math.sqrt(scaledX * scaledX + scaledY * scaledY);

                    // remove scaling
                    double r = scalb(scaledH, middleExp);
                    if (Double.isInfinite(r)) {
                        throw raiseNode.raise(inliningTarget, PythonErrorType.OverflowError, ErrorMessages.ABSOLUTE_VALUE_TOO_LARGE);
                    }
                    return r;
                }
            }
        }

        private static final long MASK_NON_SIGN_LONG = 0x7fffffffffffffffL;

        static double abs(double x) {
            return Double.longBitsToDouble(MASK_NON_SIGN_LONG & Double.doubleToRawLongBits(x));
        }

        static double scalb(final double d, final int n) {

            // first simple and fast handling when 2^n can be represented using
            // normal numbers
            if ((n > -1023) && (n < 1024)) {
                return d * Double.longBitsToDouble(((long) (n + 1023)) << 52);
            }

            // handle special cases
            if (Double.isNaN(d) || Double.isInfinite(d) || (d == 0)) {
                return d;
            }
            if (n < -2098) {
                return (d > 0) ? 0.0 : -0.0;
            }
            if (n > 2097) {
                return (d > 0) ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
            }

            // decompose d
            final long bits = Double.doubleToRawLongBits(d);
            final long sign = bits & 0x8000000000000000L;
            int exponent = ((int) (bits >>> 52)) & 0x7ff;
            long mantissa = bits & 0x000fffffffffffffL;

            // compute scaled exponent
            int scaledExponent = exponent + n;

            if (n < 0) {
                // we are really in the case n <= -1023
                if (scaledExponent > 0) {
                    // both the input and the result are normal numbers, we only
                    // adjust the exponent
                    return Double.longBitsToDouble(sign | (((long) scaledExponent) << 52) | mantissa);
                } else if (scaledExponent > -53) {
                    // the input is a normal number and the result is a subnormal
                    // number

                    // recover the hidden mantissa bit
                    mantissa = mantissa | (1L << 52);

                    // scales down complete mantissa, hence losing least significant
                    // bits
                    final long mostSignificantLostBit = mantissa & (1L << (-scaledExponent));
                    mantissa = mantissa >>> (1 - scaledExponent);
                    if (mostSignificantLostBit != 0) {
                        // we need to add 1 bit to round up the result
                        mantissa++;
                    }
                    return Double.longBitsToDouble(sign | mantissa);

                } else {
                    // no need to compute the mantissa, the number scales down to 0
                    return (sign == 0L) ? 0.0 : -0.0;
                }
            } else {
                // we are really in the case n >= 1024
                if (exponent == 0) {

                    // the input number is subnormal, normalize it
                    while ((mantissa >>> 52) != 1) {
                        mantissa = mantissa << 1;
                        --scaledExponent;
                    }
                    ++scaledExponent;
                    mantissa = mantissa & 0x000fffffffffffffL;

                    if (scaledExponent < 2047) {
                        return Double.longBitsToDouble(sign | (((long) scaledExponent) << 52) | mantissa);
                    } else {
                        return (sign == 0L) ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
                    }

                } else if (scaledExponent < 2047) {
                    return Double.longBitsToDouble(sign | (((long) scaledExponent) << 52) | mantissa);
                } else {
                    return (sign == 0L) ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
                }
            }
        }

        static int getExponent(final double d) {
            // NaN and Infinite will return 1024 anywho so can use raw bits
            return (int) ((Double.doubleToRawLongBits(d) >>> 52) & 0x7ff) - 1023;
        }

        @NeverDefault
        public static AbsNode create() {
            return ComplexBuiltinsFactory.AbsNodeFactory.create();
        }

    }

    @Slot(value = SlotKind.nb_add, isComplex = true)
    @GenerateNodeFactory
    abstract static class AddNode extends BinaryOpBuiltinNode {
        @Specialization
        static PComplex doInt(PComplex left, int right,
                        @Bind PythonLanguage language) {
            return PFactory.createComplex(language, left.getReal() + right, left.getImag());
        }

        @Specialization
        static PComplex doDouble(PComplex left, double right,
                        @Bind PythonLanguage language) {
            return PFactory.createComplex(language, left.getReal() + right, left.getImag());
        }

        @Specialization
        static Object doGeneric(Object leftObj, Object rightObj,
                        @Bind Node inliningTarget,
                        @Cached ToComplexValueNode toComplexLeft,
                        @Cached ToComplexValueNode toComplexRight,
                        @Cached InlinedConditionProfile notImplementedProfile,
                        @Bind PythonLanguage language) {
            ComplexValue left = toComplexLeft.execute(inliningTarget, leftObj);
            ComplexValue right = toComplexRight.execute(inliningTarget, rightObj);
            if (notImplementedProfile.profile(inliningTarget, left == null || right == null)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            return PFactory.createComplex(language, left.getReal() + right.getReal(), left.getImag() + right.getImag());
        }
    }

    @Slot(value = SlotKind.nb_true_divide, isComplex = true)
    @GenerateNodeFactory
    public abstract static class DivNode extends BinaryOpBuiltinNode {

        public abstract PComplex executeComplex(VirtualFrame frame, Object left, Object right);

        @NeverDefault
        public static DivNode create() {
            return ComplexBuiltinsFactory.DivNodeFactory.create();
        }

        @Specialization
        static Object doComplex(Object leftObj, Object rightObj,
                        @Bind Node inliningTarget,
                        @Cached ToComplexValueNode toComplexLeft,
                        @Cached ToComplexValueNode toComplexRight,
                        @Cached InlinedConditionProfile notImplementedProfile,
                        @Cached InlinedConditionProfile topConditionProfile,
                        @Cached InlinedConditionProfile zeroDivisionProfile,
                        @Bind PythonLanguage language,
                        @Cached PRaiseNode raiseNode) {
            ComplexValue left = toComplexLeft.execute(inliningTarget, leftObj);
            ComplexValue right = toComplexRight.execute(inliningTarget, rightObj);
            if (notImplementedProfile.profile(inliningTarget, left == null || right == null)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            double absRightReal = right.getReal() < 0 ? -right.getReal() : right.getReal();
            double absRightImag = right.getImag() < 0 ? -right.getImag() : right.getImag();
            double real;
            double imag;
            if (topConditionProfile.profile(inliningTarget, absRightReal >= absRightImag)) {
                /* divide tops and bottom by right.real */
                if (zeroDivisionProfile.profile(inliningTarget, absRightReal == 0.0)) {
                    throw raiseNode.raise(inliningTarget, PythonErrorType.ZeroDivisionError, ErrorMessages.S_DIVISION_BY_ZERO, "complex");
                } else {
                    double ratio = right.getImag() / right.getReal();
                    double denom = right.getReal() + right.getImag() * ratio;
                    real = (left.getReal() + left.getImag() * ratio) / denom;
                    imag = (left.getImag() - left.getReal() * ratio) / denom;
                }
            } else {
                /* divide tops and bottom by right.imag */
                double ratio = right.getReal() / right.getImag();
                double denom = right.getReal() * ratio + right.getImag();
                real = (left.getReal() * ratio + left.getImag()) / denom;
                imag = (left.getImag() * ratio - left.getReal()) / denom;
            }
            return PFactory.createComplex(language, real, imag);
        }

        static PComplex doubleDivComplex(double left, PComplex right, PythonLanguage language) {
            double oprealSq = right.getReal() * right.getReal();
            double opimagSq = right.getImag() * right.getImag();
            double realPart = right.getReal() * left;
            double imagPart = right.getImag() * left;
            double denom = oprealSq + opimagSq;
            return PFactory.createComplex(language, realPart / denom, -imagPart / denom);
        }
    }

    @Slot(value = SlotKind.nb_multiply, isComplex = true)
    @GenerateNodeFactory
    abstract static class MulNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object doComplex(Object leftObj, Object rightObj,
                        @Bind Node inliningTarget,
                        @Cached ToComplexValueNode toComplexLeft,
                        @Cached ToComplexValueNode toComplexRight,
                        @Cached InlinedConditionProfile notImplementedProfile,
                        @Bind PythonLanguage language) {
            ComplexValue left = toComplexLeft.execute(inliningTarget, leftObj);
            ComplexValue right = toComplexRight.execute(inliningTarget, rightObj);
            if (notImplementedProfile.profile(inliningTarget, left == null || right == null)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            ComplexValue res = multiply(left, right);
            return PFactory.createComplex(language, res.getReal(), res.getImag());
        }

        static ComplexValue multiply(ComplexValue left, ComplexValue right) {
            double newReal = left.getReal() * right.getReal() - left.getImag() * right.getImag();
            double newImag = left.getReal() * right.getImag() + left.getImag() * right.getReal();
            return new ComplexValue(newReal, newImag);
        }
    }

    @GenerateNodeFactory
    @Slot(value = SlotKind.nb_subtract, isComplex = true)
    abstract static class SubNode extends BinaryOpBuiltinNode {
        static PComplex doComplex(PComplex left, double right,
                        @Bind PythonLanguage language) {
            return PFactory.createComplex(language, left.getReal() - right, left.getImag());
        }

        @Specialization
        static PComplex doComplex(PComplex left, int right,
                        @Bind PythonLanguage language) {
            return PFactory.createComplex(language, left.getReal() - right, left.getImag());
        }

        @Specialization
        static Object doComplex(Object leftObj, Object rightObj,
                        @Bind Node inliningTarget,
                        @Cached ToComplexValueNode toComplexLeft,
                        @Cached ToComplexValueNode toComplexRight,
                        @Cached InlinedConditionProfile notImplementedProfile,
                        @Bind PythonLanguage language) {
            ComplexValue left = toComplexLeft.execute(inliningTarget, leftObj);
            ComplexValue right = toComplexRight.execute(inliningTarget, rightObj);
            if (notImplementedProfile.profile(inliningTarget, left == null || right == null)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            return PFactory.createComplex(language, left.getReal() - right.getReal(), left.getImag() - right.getImag());
        }
    }

    @Slot(value = SlotKind.nb_power, isComplex = true)
    @GenerateNodeFactory
    abstract static class PowerNode extends PythonTernaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object leftObj, Object rightObj, @SuppressWarnings("unused") PNone mod,
                        @Bind Node inliningTarget,
                        @Cached ToComplexValueNode toComplexLeft,
                        @Cached ToComplexValueNode toComplexRight,
                        @Cached InlinedConditionProfile notImplementedProfile,
                        @Cached InlinedBranchProfile rightZeroProfile,
                        @Cached InlinedBranchProfile leftZeroProfile,
                        @Cached InlinedBranchProfile smallPositiveProfile,
                        @Cached InlinedBranchProfile smallNegativeProfile,
                        @Cached InlinedBranchProfile complexProfile,
                        @Bind PythonLanguage language,
                        @Cached PRaiseNode raiseNode) {
            ComplexValue left = toComplexLeft.execute(inliningTarget, leftObj);
            ComplexValue right = toComplexRight.execute(inliningTarget, rightObj);
            if (notImplementedProfile.profile(inliningTarget, left == null || right == null)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            PComplex result;
            if (right.getReal() == 0.0 && right.getImag() == 0.0) {
                rightZeroProfile.enter(inliningTarget);
                result = PFactory.createComplex(language, 1.0, 0.0);
            } else if (left.getReal() == 0.0 && left.getImag() == 0.0) {
                leftZeroProfile.enter(inliningTarget);
                if (right.getImag() != 0.0 || right.getReal() < 0.0) {
                    throw PRaiseNode.raiseStatic(inliningTarget, ZeroDivisionError, ErrorMessages.COMPLEX_ZERO_TO_NEGATIVE_POWER);
                }
                result = PFactory.createComplex(language, 0.0, 0.0);
            } else if (right.getImag() == 0.0 && right.getReal() == (int) right.getReal() && right.getReal() < 100 && right.getReal() > -100) {
                if (right.getReal() >= 0) {
                    smallPositiveProfile.enter(inliningTarget);
                    result = complexToSmallPositiveIntPower(left, (int) right.getReal(), language);
                } else {
                    smallNegativeProfile.enter(inliningTarget);
                    result = DivNode.doubleDivComplex(1.0, complexToSmallPositiveIntPower(left, -(int) right.getReal(), language), language);
                }
            } else {
                complexProfile.enter(inliningTarget);
                result = complexToComplexBoundary(left.getReal(), left.getImag(), right.getReal(), right.getImag(), language);
            }
            if (Double.isInfinite(result.getReal()) || Double.isInfinite(result.getImag())) {
                throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.COMPLEX_EXPONENTIATION);
            }
            return result;
        }

        @Specialization(guards = "!isPNone(mod)")
        @InliningCutoff
        @SuppressWarnings("unused")
        static Object error(Object left, Object right, Object mod,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.COMPLEX_MODULO);
        }

        private static PComplex complexToSmallPositiveIntPower(ComplexValue x, long n, PythonLanguage language) {
            long mask = 1;
            ComplexValue r = new ComplexValue(1.0, 0.0);
            ComplexValue p = x;
            while (mask > 0 && n >= mask) {
                if ((n & mask) != 0) {
                    r = MulNode.multiply(r, p);
                }
                mask <<= 1;
                p = MulNode.multiply(p, p);
            }
            return PFactory.createComplex(language, r.getReal(), r.getImag());
        }

        @TruffleBoundary
        private static PComplex complexToComplexBoundary(double leftRead, double leftImag, double rightReal, double rightImag, PythonLanguage language) {
            PComplex result;
            double vabs = Math.hypot(leftRead, leftImag);
            double len = Math.pow(vabs, rightReal);
            double at = Math.atan2(leftImag, leftRead);
            double phase = at * rightReal;
            if (rightImag != 0.0) {
                len /= Math.exp(at * rightImag);
                phase += rightImag * Math.log(vabs);
            }
            result = PFactory.createComplex(language, len * Math.cos(phase), len * Math.sin(phase));
            return result;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    @TypeSystemReference(PythonIntegerAndFloatTypes.class)
    abstract static class ComplexEqNode extends Node {
        public abstract Object execute(Node inliningTarget, Object left, Object right);

        @Specialization
        static Object doComplex(PComplex left, PComplex right) {
            return left.getReal() == right.getReal() && left.getImag() == right.getImag();
        }

        @Specialization(guards = "check.execute(inliningTarget, rightObj)", limit = "1")
        static Object doComplex(Node inliningTarget, Object leftObj, Object rightObj,
                        @SuppressWarnings("unused") @Cached PyComplexCheckNode check,
                        @Exclusive @Cached ToComplexValueNode toComplexLeft,
                        @Exclusive @Cached ToComplexValueNode toComplexRight) {
            ComplexValue left = toComplexLeft.execute(inliningTarget, leftObj);
            ComplexValue right = toComplexRight.execute(inliningTarget, rightObj);
            return left.getReal() == right.getReal() && left.getImag() == right.getImag();
        }

        @Specialization
        static boolean doComplexDouble(Node inliningTarget, Object leftObj, double right,
                        @Exclusive @Cached ToComplexValueNode toComplexLeft) {
            ComplexValue left = toComplexLeft.execute(inliningTarget, leftObj);
            return left.getImag() == 0 && left.getReal() == right;
        }

        @Specialization
        static boolean doComplexInt(Node inliningTarget, Object leftObj, long right,
                        @Exclusive @Cached ToComplexValueNode toComplexLeft,
                        @Cached InlinedConditionProfile longFitsToDoubleProfile) {
            ComplexValue left = toComplexLeft.execute(inliningTarget, leftObj);
            return left.getImag() == 0 && FloatBuiltins.ComparisonHelperNode.compareDoubleToLong(inliningTarget, left.getReal(), right, longFitsToDoubleProfile) == 0;
        }

        @Specialization
        static boolean doComplexInt(Node inliningTarget, Object leftObj, PInt right,
                        @Exclusive @Cached ToComplexValueNode toComplexLeft) {
            ComplexValue left = toComplexLeft.execute(inliningTarget, leftObj);
            return left.getImag() == 0 && FloatBuiltins.ComparisonHelperNode.compareDoubleToLargeInt(left.getReal(), right) == 0;
        }

        @SuppressWarnings("unused")
        @Fallback
        static PNotImplemented doNotImplemented(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Slot(SlotKind.tp_richcompare)
    @GenerateNodeFactory
    @GenerateUncached
    abstract static class ComplexRichCmpNode extends TpSlotRichCompare.RichCmpBuiltinNode {
        @Specialization
        static Object doComplex(Object left, Object right, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Cached ComplexEqNode complexEqNode,
                        @Cached InlinedConditionProfile isNotImplementedProfile) {
            if (!op.isEqOrNe()) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            Object result = complexEqNode.execute(inliningTarget, left, right);
            if (isNotImplementedProfile.profile(inliningTarget, result == PNotImplemented.NOT_IMPLEMENTED)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            return (boolean) result == (op == RichCmpOp.Py_EQ);
        }
    }

    @GenerateNodeFactory
    @Slot(value = SlotKind.tp_repr, isComplex = true)
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        @InliningCutoff
        static TruffleString repr(Object self,
                        @Bind Node inliningTarget,
                        @Cached ToComplexValueNode toComplexValueNode) {
            ComplexValue c = toComplexValueNode.execute(inliningTarget, self);
            return doFormat(inliningTarget, c.getReal(), c.getImag());
        }

        @TruffleBoundary
        private static TruffleString doFormat(Node inliningTarget, double real, double imag) {
            ComplexFormatter formatter = new ComplexFormatter(new Spec(-1, Spec.NONE), inliningTarget);
            formatter.format(real, imag);
            return formatter.pad().getResult();
        }
    }

    @Builtin(name = J___FORMAT__, minNumOfPositionalArgs = 2, parameterNames = {"$self", "format_spec"})
    @ArgumentClinic(name = "format_spec", conversion = ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class FormatNode extends FormatNodeBase {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return FormatNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        @InliningCutoff
        static TruffleString format(Object self, TruffleString formatString,
                        @Bind Node inliningTarget,
                        @Cached ToComplexValueNode toComplexValueNode,
                        @Cached PRaiseNode raiseNode) {
            ComplexValue c = toComplexValueNode.execute(inliningTarget, self);
            InternalFormat.Spec spec = InternalFormat.fromText(formatString, Spec.NONE, '>', inliningTarget);
            validateSpec(inliningTarget, spec, raiseNode);
            return doFormat(inliningTarget, c.getReal(), c.getImag(), spec);
        }

        @TruffleBoundary
        private static TruffleString doFormat(Node raisingNode, double real, double imag, Spec spec) {
            ComplexFormatter formatter = new ComplexFormatter(validateForFloat(spec, "complex", raisingNode), raisingNode);
            formatter.format(real, imag);
            return formatter.pad().getResult();
        }

        private static void validateSpec(Node inliningTarget, Spec spec, PRaiseNode raiseNode) {
            if (spec.getFill(' ') == '0') {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.ZERO_PADDING_NOT_ALLOWED_FOR_COMPLEX_FMT);
            }

            char align = spec.getAlign('>');
            if (align == '=') {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.S_ALIGNMENT_FLAG_NOT_ALLOWED_FOR_COMPLEX_FMT, align);
            }
        }
    }

    @Slot(SlotKind.nb_bool)
    @GenerateUncached
    @GenerateNodeFactory
    abstract static class BoolNode extends NbBoolBuiltinNode {
        @Specialization
        static boolean bool(Object self,
                        @Bind Node inliningTarget,
                        @Cached ToComplexValueNode toComplexValueNode) {
            ComplexValue c = toComplexValueNode.execute(inliningTarget, self);
            return c.getReal() != 0.0 || c.getImag() != 0.0;
        }
    }

    @Slot(value = SlotKind.nb_negative, isComplex = true)
    @GenerateNodeFactory
    abstract static class NegNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PComplex neg(Object self,
                        @Bind Node inliningTarget,
                        @Cached ToComplexValueNode toComplexValueNode,
                        @Bind PythonLanguage language) {
            ComplexValue c = toComplexValueNode.execute(inliningTarget, self);
            return PFactory.createComplex(language, -c.getReal(), -c.getImag());
        }
    }

    @Slot(value = SlotKind.nb_positive, isComplex = true)
    @GenerateNodeFactory
    abstract static class PosNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PComplex pos(Object self,
                        @Bind Node inliningTarget,
                        @Cached ToComplexValueNode toComplexValueNode,
                        @Bind PythonLanguage language) {
            ComplexValue c = toComplexValueNode.execute(inliningTarget, self);
            return PFactory.createComplex(language, c.getReal(), c.getImag());
        }
    }

    @GenerateNodeFactory
    @Builtin(name = J___GETNEWARGS__, minNumOfPositionalArgs = 1)
    abstract static class GetNewArgsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PTuple get(Object self,
                        @Bind Node inliningTarget,
                        @Cached ToComplexValueNode toComplexValueNode,
                        @Bind PythonLanguage language) {
            ComplexValue c = toComplexValueNode.execute(inliningTarget, self);
            return PFactory.createTuple(language, new Object[]{c.getReal(), c.getImag()});
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "real", minNumOfPositionalArgs = 1, isGetter = true, doc = "the real part of a complex number")
    abstract static class RealNode extends PythonBuiltinNode {
        @Specialization
        static double get(PComplex self) {
            return self.getReal();
        }

        @Specialization
        @InliningCutoff
        static double getNative(PythonAbstractNativeObject self,
                        @Cached CStructAccess.ReadDoubleNode read) {
            return read.readFromObj(self, PyComplexObject__cval__real);
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "imag", minNumOfPositionalArgs = 1, isGetter = true, doc = "the imaginary part of a complex number")
    abstract static class ImagNode extends PythonBuiltinNode {
        @Specialization
        static double get(PComplex self) {
            return self.getImag();
        }

        @Specialization
        @InliningCutoff
        static double getNative(PythonAbstractNativeObject self,
                        @Cached CStructAccess.ReadDoubleNode read) {
            return read.readFromObj(self, PyComplexObject__cval__imag);
        }
    }

    @Slot(value = SlotKind.tp_hash, isComplex = true)
    @GenerateNodeFactory
    abstract static class HashNode extends HashBuiltinNode {
        @Specialization
        static long doPComplex(Object self,
                        @Bind Node inliningTarget,
                        @Cached ToComplexValueNode toComplexValueNode) {
            ComplexValue c = toComplexValueNode.execute(inliningTarget, self);
            // just like CPython
            long realHash = PyObjectHashNode.hash(c.getReal());
            long imagHash = PyObjectHashNode.hash(c.getImag());
            long combined = realHash + SysModuleBuiltins.HASH_IMAG * imagHash;
            return combined == -1L ? -2L : combined;
        }

    }

    @GenerateNodeFactory
    @Builtin(name = "conjugate", minNumOfPositionalArgs = 1)
    abstract static class ConjugateNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PComplex hash(Object self,
                        @Bind Node inliningTarget,
                        @Cached ToComplexValueNode toComplexValueNode,
                        @Bind PythonLanguage language) {
            ComplexValue c = toComplexValueNode.execute(inliningTarget, self);
            return PFactory.createComplex(language, c.getReal(), -c.getImag());
        }
    }
}
