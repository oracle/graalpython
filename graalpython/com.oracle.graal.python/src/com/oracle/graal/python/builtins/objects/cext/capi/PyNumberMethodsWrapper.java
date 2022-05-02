/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi;

import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_ABSOLUTE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_ADD;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_AND;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_BOOL;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_DIVMOD;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_FLOAT;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_FLOOR_DIVIDE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_INDEX;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_INPLACE_ADD;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_INPLACE_AND;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_INPLACE_FLOOR_DIVIDE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_INPLACE_LSHIFT;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_INPLACE_MULTIPLY;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_INPLACE_OR;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_INPLACE_POWER;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_INPLACE_REMAINDER;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_INPLACE_RSHIFT;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_INPLACE_SUBTRACT;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_INPLACE_TRUE_DIVIDE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_INPLACE_XOR;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_INT;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_INVERT;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_LSHIFT;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_MULTIPLY;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_NEGATIVE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_OR;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_POSITIVE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_POWER;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_REMAINDER;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_RSHIFT;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_SUBTRACT;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_TRUE_DIVIDE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.NB_XOR;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ABS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___AND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___DIVMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___FLOAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___FLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IAND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IFLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ILSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INDEX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INVERT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IPOW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IRSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ISUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ITRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IXOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEG__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___OR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___POS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___POW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___TRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___XOR__;

import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

/**
 * Wraps a PythonObject to provide a native view with a shape like {@code PyNumberMethods}.
 */
@ExportLibrary(InteropLibrary.class)
@ExportLibrary(value = NativeTypeLibrary.class, useForAOT = false)
@ImportStatic(SpecialMethodNames.class)
public final class PyNumberMethodsWrapper extends PythonNativeWrapper {

    @CompilationFinal(dimensions = 1) private static final NativeMember[] NUMBER_METHODS = new NativeMember[]{
                    NB_ABSOLUTE,
                    NB_ADD,
                    NB_AND,
                    NB_BOOL,
                    NB_DIVMOD,
                    NB_FLOAT,
                    NB_FLOOR_DIVIDE,
                    NB_INDEX,
                    NB_INPLACE_ADD,
                    NB_INPLACE_AND,
                    NB_INPLACE_FLOOR_DIVIDE,
                    NB_INPLACE_LSHIFT,
                    NB_INPLACE_MULTIPLY,
                    NB_INPLACE_OR,
                    NB_INPLACE_POWER,
                    NB_INPLACE_REMAINDER,
                    NB_INPLACE_RSHIFT,
                    NB_INPLACE_SUBTRACT,
                    NB_INPLACE_TRUE_DIVIDE,
                    NB_INPLACE_XOR,
                    NB_INT,
                    NB_INVERT,
                    NB_LSHIFT,
                    NB_MULTIPLY,
                    NB_NEGATIVE,
                    NB_OR,
                    NB_POSITIVE,
                    NB_POWER,
                    NB_REMAINDER,
                    NB_RSHIFT,
                    NB_SUBTRACT,
                    NB_TRUE_DIVIDE,
                    NB_XOR,
    };

    @CompilationFinal(dimensions = 1) private static final TruffleString[] NUMBER_METHODS_MAPPING = new TruffleString[]{
                    T___ABS__,
                    T___ADD__,
                    T___AND__,
                    T___BOOL__,
                    T___DIVMOD__,
                    T___FLOAT__,
                    T___FLOORDIV__,
                    T___INDEX__,
                    T___IADD__,
                    T___IAND__,
                    T___IFLOORDIV__,
                    T___ILSHIFT__,
                    T___IMUL__,
                    T___IOR__,
                    T___IPOW__,
                    T___IMOD__,
                    T___IRSHIFT__,
                    T___ISUB__,
                    T___ITRUEDIV__,
                    T___IXOR__,
                    T___INT__,
                    T___INVERT__,
                    T___LSHIFT__,
                    T___MUL__,
                    T___NEG__,
                    T___OR__,
                    T___POS__,
                    T___POW__,
                    T___MOD__,
                    T___RSHIFT__,
                    T___SUB__,
                    T___TRUEDIV__,
                    T___XOR__
    };

    public PyNumberMethodsWrapper(PythonManagedClass delegate) {
        super(delegate);
    }

    public PythonManagedClass getPythonClass() {
        return (PythonManagedClass) getDelegate();
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    protected boolean hasMembers() {
        return true;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    protected boolean isMemberReadable(String member) {
        return isValidMember(member);
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    protected Object getMembers(@SuppressWarnings("unused") boolean includeInternal) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected Object readMember(String member,
                    @Exclusive @Cached ReadMethodNode readMethodNode,
                    @Exclusive @Cached ToSulongNode toSulongNode,
                    @Exclusive @Cached GilNode gil) throws UnknownIdentifierException {
        boolean mustRelease = gil.acquire();
        try {
            // translate key to attribute name
            return toSulongNode.execute(readMethodNode.execute(getPythonClass(), member));
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    protected boolean hasNativeType() {
        // TODO implement native type
        return false;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public Object getNativeType() {
        // TODO implement native type
        return null;
    }

    @GenerateUncached
    @ImportStatic(PyNumberMethodsWrapper.class)
    abstract static class ReadMethodNode extends Node {

        public abstract Object execute(PythonManagedClass clazz, String key) throws UnknownIdentifierException;

        @Specialization(guards = {"isValidMember(key)", "eq(cachedKey, key)"})
        static Object getMethodCached(PythonManagedClass clazz, @SuppressWarnings("unused") String key,
                        @Cached("key") @SuppressWarnings("unused") String cachedKey,
                        @Exclusive @Cached LookupAttributeInMRONode.Dynamic lookupNode) throws UnknownIdentifierException {
            return getMethod(clazz, cachedKey, lookupNode);
        }

        @Specialization(replaces = "getMethodCached")
        static Object getMethod(PythonManagedClass clazz, @SuppressWarnings("unused") String key,
                        @Exclusive @Cached LookupAttributeInMRONode.Dynamic lookupNode) throws UnknownIdentifierException {
            TruffleString translate = translate(key);
            if (translate != null) {
                return lookupNode.execute(clazz, translate);
            }
            throw UnknownIdentifierException.create(key);
        }

        protected static boolean eq(String expected, String actual) {
            return expected.equals(actual);
        }
    }

    @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
    private static TruffleString translate(String key) {
        for (int i = 0; i < NUMBER_METHODS.length; i++) {
            if (NUMBER_METHODS[i].getMemberNameJavaString().equals(key)) {
                return NUMBER_METHODS_MAPPING[i];
            }
        }
        return null;
    }

    protected static boolean isValidMember(String member) {
        return translate(member) != null;
    }
}
