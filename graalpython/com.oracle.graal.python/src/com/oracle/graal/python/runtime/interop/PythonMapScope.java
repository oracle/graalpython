/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime.interop;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedIntValueProfile;

/**
 * represents global and builtins scopes, which variables values are stored in dict-like objects.
 */
@ExportLibrary(InteropLibrary.class)
public final class PythonMapScope implements TruffleObject {

    @CompilationFinal(dimensions = 1) private static final String[] TOP_SCOPE_NAMES = {
                    BuiltinNames.J___MAIN__,
                    BuiltinNames.J_BUILTINS,
    };

    static final int LIMIT = 2;

    private final String[] names;
    private final Object[] objects;
    private final int scopeIndex;

    public PythonMapScope(Object[] objects, String[] names) {
        this(objects, 0, names);
    }

    private PythonMapScope(Object[] objects, int index, String[] names) {
        assert objects.length == names.length;
        this.names = names;
        this.objects = objects;
        this.scopeIndex = index;
    }

    public static PythonMapScope createTopScope(PythonContext context) {
        Object[] objects = new Object[]{
                        context.getMainModule(),
                        InteropMap.fromPythonObject(context.getBuiltins())
        };
        return new PythonMapScope(objects, 0, TOP_SCOPE_NAMES);
    }

    @ExportMessage
    boolean hasScopeParent() {
        return scopeIndex < (names.length - 1);
    }

    @ExportMessage
    Object getScopeParent() throws UnsupportedMessageException {
        if (scopeIndex < (names.length - 1)) {
            return new PythonMapScope(objects, scopeIndex + 1, names);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal,
                    @Bind("$node") Node inliningTarget,
                    @Shared("interop") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Shared("lenghtProfile") @Cached InlinedIntValueProfile lengthProfile)
                    throws UnsupportedMessageException {
        int length = lengthProfile.profile(null, names.length);
        Object[] keys = new Object[length - scopeIndex];
        for (int i = scopeIndex; i < length; i++) {
            keys[i - scopeIndex] = interop.getMembers(objects[i]);
        }
        return new MergedPropertyNames(keys);
    }

    @ExportMessage
    boolean isMemberReadable(String member,
                    @Bind("$node") Node inliningTarget,
                    @Shared("interop") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Shared("lenghtProfile") @Cached InlinedIntValueProfile lengthProfile) {
        int length = lengthProfile.profile(inliningTarget, names.length);
        for (int i = scopeIndex; i < length; i++) {
            if (interop.isMemberReadable(objects[i], member)) {
                return true;
            } else if (interop.isMemberExisting(objects[i], member)) {
                break;
            }
        }
        return false;
    }

    @ExportMessage
    Object readMember(String member,
                    @Bind("$node") Node inliningTarget,
                    @Shared("interop") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Shared("lenghtProfile") @Cached InlinedIntValueProfile lengthProfile)
                    throws UnknownIdentifierException, UnsupportedMessageException {
        int length = lengthProfile.profile(inliningTarget, names.length);
        for (int i = scopeIndex; i < length; i++) {
            Object scope = this.objects[i];
            if (interop.isMemberExisting(scope, member)) {
                return interop.readMember(scope, member);
            }
        }
        throw UnknownIdentifierException.create(member);
    }

    @ExportMessage
    boolean isMemberModifiable(String member,
                    @Bind("$node") Node inliningTarget,
                    @Shared("interop") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Shared("lenghtProfile") @Cached InlinedIntValueProfile lengthProfile) {
        int length = lengthProfile.profile(inliningTarget, names.length);
        for (int i = scopeIndex; i < length; i++) {
            Object scope = this.objects[i];
            if (interop.isMemberModifiable(scope, member)) {
                return true;
            } else if (interop.isMemberExisting(scope, member)) {
                break;
            }
        }
        return false;
    }

    @ExportMessage
    boolean isMemberInsertable(String member,
                    @Bind("$node") Node inliningTarget,
                    @Shared("interop") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Shared("lenghtProfile") @Cached InlinedIntValueProfile lengthProfile) {
        int length = lengthProfile.profile(inliningTarget, names.length);
        for (int i = scopeIndex; i < length; i++) {
            Object scope = this.objects[i];
            if (interop.isMemberInsertable(scope, member)) {
                return true;
            } else if (interop.isMemberExisting(scope, member)) {
                return false; // saw existing member which would shadow the new member
            }
        }
        return false;
    }

    @ExportMessage
    boolean hasMemberReadSideEffects(String member,
                    @Bind("$node") Node inliningTarget,
                    @Shared("interop") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Shared("lenghtProfile") @Cached InlinedIntValueProfile lengthProfile) {
        int length = lengthProfile.profile(inliningTarget, names.length);
        for (int i = scopeIndex; i < length; i++) {
            Object scope = this.objects[i];
            if (interop.isMemberReadable(scope, member)) {
                return interop.hasMemberReadSideEffects(scope, member);
            }
        }
        return false;
    }

    @ExportMessage
    boolean hasMemberWriteSideEffects(String member,
                    @Bind("$node") Node inliningTarget,
                    @Shared("interop") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Shared("lenghtProfile") @Cached InlinedIntValueProfile lengthProfile) {
        int length = lengthProfile.profile(inliningTarget, names.length);
        for (int i = scopeIndex; i < length; i++) {
            Object scope = this.objects[i];
            if (interop.isMemberWritable(scope, member)) {
                return interop.hasMemberWriteSideEffects(scope, member);
            }
        }
        return false;
    }

    @ExportMessage
    void writeMember(String member, Object value,
                    @Bind("$node") Node inliningTarget,
                    @Shared("interop") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Shared("lenghtProfile") @Cached InlinedIntValueProfile lengthProfile)
                    throws UnknownIdentifierException, UnsupportedMessageException, UnsupportedTypeException {
        int length = lengthProfile.profile(inliningTarget, names.length);
        for (int i = scopeIndex; i < length; i++) {
            Object scope = this.objects[i];
            if (interop.isMemberModifiable(scope, member) || interop.isMemberInsertable(scope, member)) {
                interop.writeMember(scope, member, value);
                return;
            } else if (interop.isMemberExisting(scope, member)) {
                // saw existing member which would shadow the new member
                throw UnsupportedMessageException.create();
            }
        }

        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean isMemberRemovable(String member,
                    @Bind("$node") Node inliningTarget,
                    @Shared("interop") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Shared("lenghtProfile") @Cached InlinedIntValueProfile lengthProfile) {
        int length = lengthProfile.profile(inliningTarget, names.length);
        for (int i = scopeIndex; i < length; i++) {
            Object scope = this.objects[i];
            if (interop.isMemberRemovable(scope, member)) {
                return true;
            } else if (interop.isMemberExisting(scope, member)) {
                return false;
            }
        }
        return false;
    }

    @ExportMessage
    void removeMember(String member,
                    @Bind("$node") Node inliningTarget,
                    @Shared("interop") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Shared("lenghtProfile") @Cached InlinedIntValueProfile lengthProfile)
                    throws UnsupportedMessageException, UnknownIdentifierException {
        int length = lengthProfile.profile(inliningTarget, names.length);
        for (int i = scopeIndex; i < length; i++) {
            Object scope = this.objects[i];
            if (interop.isMemberRemovable(scope, member)) {
                interop.removeMember(scope, member);
                return;
            } else if (interop.isMemberExisting(scope, member)) {
                break;
            }
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Class<? extends TruffleLanguage<?>> getLanguage() {
        return PythonLanguage.class;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isScope() {
        return true;
    }

    @ExportMessage
    Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return names[scopeIndex];
    }
}
