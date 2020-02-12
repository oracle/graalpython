/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext;

import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.MP_ASS_SUBSCRIPT;
import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.MP_LENGTH;
import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.MP_SUBSCRIPT;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;

import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
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
public class PyMappingMethodsWrapper extends PythonNativeWrapper {

    @CompilationFinal(dimensions = 1) private static final String[] MAPPING_METHODS = new String[]{
                    MP_LENGTH.getMemberName(),
                    MP_SUBSCRIPT.getMemberName(),
                    MP_ASS_SUBSCRIPT.getMemberName(),
    };

    @CompilationFinal(dimensions = 1) private static final String[] MAPPING_METHODS_MAPPING = new String[]{
                    __LEN__,
                    __GETITEM__,
                    __SETITEM__,
    };

    public PyMappingMethodsWrapper(PythonManagedClass delegate) {
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
                    @Exclusive @Cached CExtNodes.ToSulongNode toSulongNode) throws UnknownIdentifierException {
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
    @ImportStatic(PyMappingMethodsWrapper.class)
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
        for (int i = 0; i < MAPPING_METHODS.length; i++) {
            if (MAPPING_METHODS[i].equals(key)) {
                return MAPPING_METHODS_MAPPING[i];
            }
        }
        return null;
    }

    protected static boolean isValidMember(String member) {
        return translate(member) != null;
    }
}
