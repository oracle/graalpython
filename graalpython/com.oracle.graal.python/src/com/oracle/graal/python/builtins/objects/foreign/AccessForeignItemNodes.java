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
package com.oracle.graal.python.builtins.objects.foreign;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.KeyError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.foreign.AccessForeignItemNodesFactory.RemoveForeignItemNodeGen;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownKeyException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.strings.TruffleString;

abstract class AccessForeignItemNodes {

    @ImportStatic(PythonOptions.class)
    @TypeSystemReference(PythonArithmeticTypes.class)
    protected abstract static class AccessForeignItemBaseNode extends PNodeWithContext {
        @Child PRaiseNode raiseNode;

        protected PException raise(PythonBuiltinClassType type, TruffleString msg, Object... arguments) {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            return raiseNode.raise(type, msg, arguments);
        }
    }

    protected abstract static class GetForeignItemNode extends AccessForeignItemBaseNode {

        public abstract Object execute(VirtualFrame frame, Object object, Object idx);

        @Specialization(guards = {"lib.isString(object)"})
        Object doString(VirtualFrame frame, Object object, Object idx,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") InteropLibrary lib,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached StringBuiltins.StrGetItemNode getItemNode,
                        @Cached GilNode gil) {
            TruffleString string;
            gil.release(true);
            try {
                string = switchEncodingNode.execute(lib.asTruffleString(object), TS_ENCODING);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            } finally {
                gil.acquire();
            }
            return getItemNode.execute(frame, string, idx);
        }

        @Fallback
        @SuppressWarnings("unused")
        Object doFail(Object object, Object key) {
            throw raise(TypeError, ErrorMessages.OBJ_NOT_SUBSCRIPTABLE, object);
        }
    }

    protected abstract static class RemoveForeignItemNode extends AccessForeignItemBaseNode {

        public abstract Object execute(VirtualFrame frame, Object object, Object idx);

        @Specialization(guards = {"lib.hasHashEntries(object)"})
        Object doHashKey(Object object, Object key,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") InteropLibrary lib,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached GilNode gil) {
            if (lib.isHashEntryRemovable(object, key)) {
                gil.release(true);
                try {
                    lib.removeHashEntry(object, key);
                    return PNone.NONE;
                } catch (UnknownKeyException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                } catch (UnsupportedMessageException e) {
                    return raise(AttributeError, ErrorMessages.ATTR_S_OF_S_OBJ_IS_NOT_REMOVABLE, key, object);
                } finally {
                    gil.acquire();
                }
            }
            throw keyError(this, key, lib, switchEncodingNode);
        }

        @Fallback
        @SuppressWarnings("unused")
        Object doFail(Object object, Object key) {
            throw raise(TypeError, ErrorMessages.OBJ_DOESNT_SUPPORT_DELETION, object);
        }

        public static RemoveForeignItemNode create() {
            return RemoveForeignItemNodeGen.create();
        }
    }

    private static PException keyError(AccessForeignItemBaseNode node, Object key, InteropLibrary lib, TruffleString.SwitchEncodingNode switchEncodingNode) {
        try {
            return node.raise(KeyError, switchEncodingNode.execute(lib.asTruffleString(lib.toDisplayString(key, true)), TS_ENCODING));
        } catch (UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }
}
