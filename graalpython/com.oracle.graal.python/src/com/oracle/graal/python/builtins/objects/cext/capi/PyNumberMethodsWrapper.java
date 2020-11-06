/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ABS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__AND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DIVMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FLOAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IAND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IFLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ILSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INDEX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INVERT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IPOW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IRSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ISUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IXOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEG__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__OR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__POS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__POW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__TRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__XOR__;

import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

/**
 * Wraps a PythonObject to provide a native view with a shape like {@code PyNumberMethods}.
 */
@ExportLibrary(InteropLibrary.class)
@ExportLibrary(NativeTypeLibrary.class)
@ImportStatic(SpecialMethodNames.class)
public class PyNumberMethodsWrapper extends PythonNativeWrapper {

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

    @CompilationFinal(dimensions = 1) private static final String[] NUMBER_METHODS_MAPPING = new String[]{
                    __ABS__,
                    __ADD__,
                    __AND__,
                    __BOOL__,
                    __DIVMOD__,
                    __FLOAT__,
                    __FLOORDIV__,
                    __INDEX__,
                    __IADD__,
                    __IAND__,
                    __IFLOORDIV__,
                    __ILSHIFT__,
                    __IMUL__,
                    __IOR__,
                    __IPOW__,
                    __IMOD__,
                    __IRSHIFT__,
                    __ISUB__,
                    __ITRUEDIV__,
                    __IXOR__,
                    __INT__,
                    __INVERT__,
                    __LSHIFT__,
                    __MUL__,
                    __NEG__,
                    __OR__,
                    __POS__,
                    __POW__,
                    __MOD__,
                    __RSHIFT__,
                    __SUB__,
                    __TRUEDIV__,
                    __XOR__
    };

    public PyNumberMethodsWrapper(PythonManagedClass delegate) {
        super(delegate);
    }

    public PythonManagedClass getPythonClass(PythonNativeWrapperLibrary lib) {
        return (PythonManagedClass) lib.getDelegate(this);
    }

    @ExportMessage
    protected boolean hasMembers() {
        return true;
    }

    @ExportMessage
    protected boolean isMemberReadable(String member) {
        return isValidMember(member);
    }

    @ExportMessage
    protected Object getMembers(@SuppressWarnings("unused") boolean includeInternal) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected Object readMember(String member,
                    @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                    @Exclusive @Cached ReadMethodNode readMethodNode,
                    @Exclusive @Cached ToSulongNode toSulongNode) throws UnknownIdentifierException {
        // translate key to attribute name
        return toSulongNode.execute(readMethodNode.execute(getPythonClass(lib), member));
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
            String translate = translate(key);
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
    private static String translate(String key) {
        for (int i = 0; i < NUMBER_METHODS.length; i++) {
            if (NUMBER_METHODS[i].getMemberName().equals(key)) {
                return NUMBER_METHODS_MAPPING[i];
            }
        }
        return null;
    }

    protected static boolean isValidMember(String member) {
        return translate(member) != null;
    }
}
