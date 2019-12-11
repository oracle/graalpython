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
package com.oracle.graal.python.builtins.objects.cext.hpy;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;

public class GraalHPyNodes {

    @GenerateUncached
    abstract static class ImportHPySymbolNode extends PNodeWithContext {

        public abstract Object execute(GraalHPyContext context, String name);

        @Specialization(guards = "cachedName == name", limit = "1", assumptions = "singleContextAssumption()")
        Object doReceiverCachedIdentity(@SuppressWarnings("unused") GraalHPyContext context, @SuppressWarnings("unused") String name,
                        @Cached("name") @SuppressWarnings("unused") String cachedName,
                        @Cached("importHPySymbolUncached(context, name)") Object sym) {
            return sym;
        }

        @Specialization(guards = "cachedName.equals(name)", limit = "1", assumptions = "singleContextAssumption()", replaces = "doReceiverCachedIdentity")
        Object doReceiverCached(GraalHPyContext context, @SuppressWarnings("unused") String name,
                        @Cached("name") @SuppressWarnings("unused") String cachedName,
                        @Cached("importHPySymbolUncached(context, name)") Object sym) {
            return sym;
        }

        @Specialization(replaces = {"doReceiverCached", "doReceiverCachedIdentity"})
        Object doGeneric(GraalHPyContext context, String name,
                        @CachedLibrary(limit = "1") @SuppressWarnings("unused") InteropLibrary interopLib,
                        @Cached PRaiseNode raiseNode) {
            return importHPySymbol(raiseNode, interopLib, context.getHPyLibrary(), name);
        }

        protected static Object importHPySymbolUncached(GraalHPyContext context, String name) {
            Object hpyLibrary = context.getHPyLibrary();
            InteropLibrary uncached = InteropLibrary.getFactory().getUncached(hpyLibrary);
            return importHPySymbol(PRaiseNode.getUncached(), uncached, hpyLibrary, name);
        }

        private static Object importHPySymbol(PRaiseNode raiseNode, InteropLibrary library, Object capiLibrary, String name) {
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
    public abstract static class PCallHPyFunction extends PNodeWithContext {

        public final Object call(GraalHPyContext context, String name, Object... args) {
            return execute(context, name, args);
        }

        public abstract Object execute(GraalHPyContext context, String name, Object[] args);

        @Specialization
        Object doIt(GraalHPyContext context, String name, Object[] args,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @Cached ImportHPySymbolNode importCAPISymbolNode,
                        @Cached BranchProfile profile,
                        @Cached PRaiseNode raiseNode) {
            try {
                return interopLibrary.execute(importCAPISymbolNode.execute(context, name), args);
            } catch (UnsupportedTypeException | ArityException e) {
                profile.enter();
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, e);
            } catch (UnsupportedMessageException e) {
                profile.enter();
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, "HPy C API symbol %s is not callable", name);
            }
        }
    }

    @GenerateUncached
    public abstract static class HPyAsPythonObjectNode extends PNodeWithContext {

        public abstract Object execute(GraalHPyContext hpyContext, Object object);

        public abstract Object executeInt(GraalHPyContext hpyContext, int l);

        public abstract Object executeLong(GraalHPyContext hpyContext, long l);

        @Specialization
        static Object doHandle(@SuppressWarnings("unused") GraalHPyContext hpyContext, GraalHPyHandle handle) {
            return handle.getDelegate();
        }

        @Specialization(guards = "hpyContext == null")
        static Object doInt(@SuppressWarnings("unused") GraalHPyContext hpyContext, int handle,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            return context.getHPyContext().getObjectForHPyHandle(handle).getDelegate();
        }

        @Specialization(guards = "hpyContext == null", rewriteOn = ArithmeticException.class)
        static Object doLong(@SuppressWarnings("unused") GraalHPyContext hpyContext, long handle,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            return context.getHPyContext().getObjectForHPyHandle(PInt.intValueExact(handle)).getDelegate();
        }

        @Specialization(guards = "hpyContext == null", replaces = "doLong")
        static Object doLongOvf(@SuppressWarnings("unused") GraalHPyContext hpyContext, long handle,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            return doLongOvfWithContext(context.getHPyContext(), handle, raiseNode);
        }

        @Specialization(guards = "hpyContext != null")
        static Object doIntWithContext(GraalHPyContext hpyContext, int handle) {
            return hpyContext.getObjectForHPyHandle(handle).getDelegate();
        }

        @Specialization(guards = "hpyContext != null", rewriteOn = ArithmeticException.class)
        static Object doLongWithContext(GraalHPyContext hpyContext, long handle) {
            return hpyContext.getObjectForHPyHandle(PInt.intValueExact(handle)).getDelegate();
        }

        @Specialization(guards = "hpyContext != null", replaces = "doLongWithContext")
        static Object doLongOvfWithContext(GraalHPyContext hpyContext, long handle,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            try {
                return hpyContext.getObjectForHPyHandle(PInt.intValueExact(handle)).getDelegate();
            } catch (ArithmeticException e) {
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, "unknown handle: %d", handle);
            }
        }
    }

}
