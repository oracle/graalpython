/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.common;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.KeyError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRaiseNode.Lazy;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.object.IsForeignObjectNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.StopIterationException;
import com.oracle.truffle.api.interop.UnknownKeyException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

/*
 * NOTE: We are not using IndirectCallContext here in this file because it seems unlikely that these interop messages
 * would call back to Python and that we would also need precise frame info for that case.
 * Adding it shouldn't hurt peak, but might be a non-trivial overhead in interpreter.
 */
public final class ForeignHashingStorage extends HashingStorage {

    public final Object foreignDict;

    public ForeignHashingStorage(Object foreignDict) {
        assert IsForeignObjectNode.executeUncached(foreignDict);
        assert InteropLibrary.getUncached().hasHashEntries(foreignDict);
        this.foreignDict = foreignDict;
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class LengthNode extends PNodeWithContext {
        public abstract int execute(Node inliningTarget, ForeignHashingStorage storage);

        @InliningCutoff
        @Specialization
        static int length(Node inliningTarget, ForeignHashingStorage storage,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") InteropLibrary interop,
                        @Cached(inline = false) GilNode gil,
                        @Cached InlinedBranchProfile errorProfile) {
            long size;
            gil.release(true);
            try {
                size = interop.getHashSize(storage.foreignDict);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            } finally {
                gil.acquire();
            }

            return PInt.long2int(inliningTarget, size, errorProfile);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetNode extends PNodeWithContext {
        public abstract Object execute(Node inliningTarget, ForeignHashingStorage storage, Object key);

        @InliningCutoff
        @Specialization
        static Object get(Node inliningTarget, ForeignHashingStorage storage, Object key,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") InteropLibrary interop,
                        @Cached(inline = false) GilNode gil,
                        @Cached(inline = false) PForeignToPTypeNode toPythonNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            var dict = storage.foreignDict;
            Object value;
            gil.release(true);
            try {
                value = interop.readHashValue(dict, key);
            } catch (UnknownKeyException e) {
                return null;
            } catch (UnsupportedMessageException e) {
                throw raiseNode.get(inliningTarget).raise(AttributeError, ErrorMessages.ATTR_S_OF_S_OBJ_IS_NOT_READABLE, key, dict);
            } finally {
                gil.acquire();
            }

            return toPythonNode.executeConvert(value);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class PutNode extends PNodeWithContext {
        public abstract void execute(Node inliningTarget, ForeignHashingStorage storage, Object key, Object value);

        @InliningCutoff
        @Specialization
        static void put(Node inliningTarget, ForeignHashingStorage storage, Object key, Object value,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") InteropLibrary interop,
                        @Cached(inline = false) GilNode gil,
                        @Cached PRaiseNode.Lazy raiseNode) {
            var dict = storage.foreignDict;
            gil.release(true);
            try {
                interop.writeHashEntry(dict, key, value);
            } catch (UnknownKeyException e) {
                throw raiseNode.get(inliningTarget).raise(KeyError, new Object[]{key});
            } catch (UnsupportedMessageException e) {
                if (interop.isHashEntryExisting(dict, key)) {
                    throw raiseNode.get(inliningTarget).raise(AttributeError, ErrorMessages.ATTR_S_OF_S_OBJ_IS_NOT_WRITABLE, key, dict);
                } else {
                    throw raiseNode.get(inliningTarget).raise(AttributeError, ErrorMessages.ATTR_S_OF_S_OBJ_IS_NOT_INSERTABLE, key, dict);
                }
            } catch (UnsupportedTypeException e) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.TYPE_P_NOT_SUPPORTED_BY_FOREIGN_OBJ, value);
            } finally {
                gil.acquire();
            }
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class RemoveNode extends PNodeWithContext {
        public abstract boolean execute(Node inliningTarget, ForeignHashingStorage storage, Object key);

        @InliningCutoff
        @Specialization
        static boolean remove(Node inliningTarget, ForeignHashingStorage storage, Object key,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") InteropLibrary interop,
                        @Cached(inline = false) GilNode gil,
                        @Cached PRaiseNode.Lazy raiseNode) {
            var dict = storage.foreignDict;

            gil.release(true);
            try {
                interop.removeHashEntry(dict, key);
            } catch (UnknownKeyException e) {
                return false;
            } catch (UnsupportedMessageException e) {
                throw raiseNode.get(inliningTarget).raise(AttributeError, ErrorMessages.ATTR_S_OF_S_OBJ_IS_NOT_REMOVABLE, key, dict);
            } finally {
                gil.acquire();
            }

            return true;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class PopNode extends PNodeWithContext {
        public abstract Object execute(Node inliningTarget, ForeignHashingStorage storage, Object key);

        @InliningCutoff
        @Specialization
        static Object pop(Node inliningTarget, ForeignHashingStorage storage, Object key,
                        @Cached GetNode getNode,
                        @Cached RemoveNode removeNode,
                        @Cached(inline = false) PForeignToPTypeNode toPythonNode) {
            Object value = getNode.execute(inliningTarget, storage, key);

            if (!removeNode.execute(inliningTarget, storage, key)) {
                return null;
            }

            return toPythonNode.executeConvert(value);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class ClearNode extends PNodeWithContext {
        public abstract void execute(Node inliningTarget, ForeignHashingStorage storage);

        @Specialization
        static void clear(Node inliningTarget, ForeignHashingStorage storage,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") InteropLibrary interop,
                        @Cached(inline = false) GilNode gil,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") InteropLibrary iteratorInterop,
                        @Cached PRaiseNode.Lazy raiseNode) {
            // We cannot just remove while iterating otherwise we get e.g.
            // ConcurrentModificationException with java.util.HashMap
            // So we remove keys by batch of 32 keys.
            gil.release(true);
            try {
                Object[] keys = new Object[32];
                int i;
                do {
                    i = 0;
                    var iterator = interop.getHashKeysIterator(storage.foreignDict);
                    while (i < keys.length && iteratorInterop.hasIteratorNextElement(iterator)) {
                        var key = iteratorInterop.getIteratorNextElement(iterator);
                        keys[i++] = key;
                    }

                    for (int k = 0; k < i; k++) {
                        remove(inliningTarget, storage.foreignDict, keys[k], interop, raiseNode);
                    }
                } while (i == keys.length);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            } catch (StopIterationException e) {
                // continue
            } finally {
                gil.acquire();
            }
        }

        private static void remove(Node inliningTarget, Object dict, Object key, InteropLibrary interop, Lazy raiseNode) {
            try {
                interop.removeHashEntry(dict, key);
            } catch (UnknownKeyException e) {
                // already removed concurrently
            } catch (UnsupportedMessageException e) {
                throw raiseNode.get(inliningTarget).raise(AttributeError, ErrorMessages.ATTR_S_OF_S_OBJ_IS_NOT_REMOVABLE, key, dict);
            }
        }
    }

}
