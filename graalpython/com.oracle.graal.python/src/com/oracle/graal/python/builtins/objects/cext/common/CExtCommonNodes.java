/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.common;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.UnicodeEncodeError;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;

public abstract class CExtCommonNodes {

    @GenerateUncached
    public abstract static class ImportCExtSymbolNode extends PNodeWithContext {

        public abstract Object execute(CExtContext nativeContext, String name);

        // n.b. if 'singleContextAssumption' is valid, we may also cache the native context
        @Specialization(guards = {"nativeContext == cachedNativeContext", "cachedName == name"}, //
                        limit = "1", //
                        assumptions = "singleContextAssumption()")
        @SuppressWarnings("unused")
        static Object doReceiverCachedIdentity(CExtContext nativeContext, String name,
                        @Cached("nativeContext") CExtContext cachedNativeContext,
                        @Cached("name") String cachedName,
                        @Cached("importCAPISymbolUncached(nativeContext, name)") Object sym) {
            return sym;
        }

        // n.b. if 'singleContextAssumption' is valid, we may also cache the native context
        @Specialization(guards = {"nativeContext == cachedNativeContext", "cachedName.equals(name)"}, //
                        limit = "1", //
                        assumptions = "singleContextAssumption()", //
                        replaces = "doReceiverCachedIdentity")
        @SuppressWarnings("unused")
        static Object doReceiverCached(CExtContext nativeContext, String name,
                        @Cached("nativeContext") CExtContext cachedNativeContext,
                        @Cached("name") String cachedName,
                        @Cached("importCAPISymbolUncached(nativeContext, name)") Object sym) {
            return sym;
        }

        @Specialization(replaces = {"doReceiverCachedIdentity", "doReceiverCached"}, //
                        limit = "1")
        static Object doWithContext(CExtContext nativeContext, String name,
                        @CachedLibrary("nativeContext.getLLVMLibrary()") @SuppressWarnings("unused") InteropLibrary interopLib,
                        @Cached PRaiseNode raiseNode) {
            return importCAPISymbol(raiseNode, interopLib, nativeContext.getLLVMLibrary(), name);
        }

        protected static Object importCAPISymbolUncached(CExtContext nativeContext, String name) {
            Object capiLibrary = nativeContext.getLLVMLibrary();
            return importCAPISymbol(PRaiseNode.getUncached(), InteropLibrary.getFactory().getUncached(capiLibrary), capiLibrary, name);
        }

        private static Object importCAPISymbol(PRaiseNode raiseNode, InteropLibrary library, Object capiLibrary, String name) {
            try {
                return library.readMember(capiLibrary, name);
            } catch (UnknownIdentifierException e) {
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, "invalid C API function: %s", name);
            } catch (UnsupportedMessageException e) {
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, "corrupted C API library object: %s", capiLibrary);
            }
        }
    }

    @GenerateUncached
    public abstract static class PCallCExtFunction extends PNodeWithContext {

        public final Object call(CExtContext nativeContext, String name, Object... args) {
            return execute(nativeContext, name, args);
        }

        public abstract Object execute(CExtContext nativeContext, String name, Object[] args);

        @Specialization
        static Object doIt(CExtContext nativeContext, String name, Object[] args,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @Cached ImportCExtSymbolNode importCExtSymbolNode,
                        @Cached BranchProfile profile,
                        @Cached PRaiseNode raiseNode) {
            try {
                return interopLibrary.execute(importCExtSymbolNode.execute(nativeContext, name), args);
            } catch (UnsupportedTypeException | ArityException e) {
                profile.enter();
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, e);
            } catch (UnsupportedMessageException e) {
                profile.enter();
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, "C API symbol %s is not callable", name);
            }
        }
    }

    @GenerateUncached
    public abstract static class EncodeNativeStringNode extends PNodeWithContext {

        public abstract PBytes execute(Charset charset, Object string, String errors);

        @Specialization
        static PBytes doJavaString(Charset charset, String s, String errors,
                        @Shared("factory") @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            try {
                CodingErrorAction action = BytesBuiltins.toCodingErrorAction(errors, raiseNode);
                return factory.createBytes(doEncode(charset, s, action));
            } catch (CharacterCodingException e) {
                throw raiseNode.raise(UnicodeEncodeError, "%m", e);
            }
        }

        @Specialization(replaces = "doJavaString")
        static PBytes doGeneric(Charset charset, Object stringObj, String errors,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Shared("factory") @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            return doJavaString(charset, castToJavaStringNode.execute(stringObj), errors, factory, raiseNode);
        }

        @TruffleBoundary
        private static byte[] doEncode(Charset charset, String string, CodingErrorAction action) throws CharacterCodingException {
            CharsetEncoder encoder = charset.newEncoder();
            encoder.onMalformedInput(action).onUnmappableCharacter(action);
            CharBuffer buf = CharBuffer.allocate(string.length());
            buf.put(string);
            buf.flip();
            ByteBuffer encoded = encoder.encode(buf);
            byte[] barr = new byte[encoded.remaining()];
            encoded.get(barr);
            return barr;
        }
    }
}
