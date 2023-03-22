/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.InlinedIntValueProfile;
import com.oracle.truffle.api.source.SourceSection;

@ExportLibrary(InteropLibrary.class)
public final class PythonScopes implements TruffleObject {

    static final int LIMIT = 2;

    public final Object[] scopes;
    private final int scopeIndex;

    public PythonScopes(Object[] scopes, int scopeIndex) {
        this.scopes = scopes;
        this.scopeIndex = scopeIndex;
    }

    private static InteropMap scopeFromObject(PythonObject globals) {
        if (globals instanceof PDict) {
            return InteropMap.fromPDict((PDict) globals);
        } else {
            return InteropMap.fromPythonObject(globals);
        }
    }

    public static Object create(Node node, Frame frame) {
        RootNode root = node.getRootNode();
        PythonLocalScope localScope = PythonLocalScope.createLocalScope(root, frame != null ? frame.materialize() : null);
        Object[] scopes;
        if (frame != null) {
            PythonObject globals = PArguments.getGlobalsSafe(frame);
            MaterializedFrame generatorFrame = PArguments.getGeneratorFrameSafe(frame);
            Object globalsScope = null;
            if (globals != null) {
                globalsScope = new PythonMapScope(new Object[]{scopeFromObject(globals)}, new String[]{"globals()"});
            }
            if (globals != null && generatorFrame != null) {
                scopes = new Object[]{localScope, globalsScope, PythonLocalScope.createLocalScope(root, generatorFrame)};
            } else if (globals != null) {
                scopes = new Object[]{localScope, globalsScope};
            } else if (generatorFrame != null) {
                scopes = new Object[]{localScope, PythonLocalScope.createLocalScope(root, generatorFrame)};
            } else {
                return localScope;
            }
        } else {
            return localScope;
        }
        return new PythonScopes(scopes, 0);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isScope() {
        return true;
    }

    @ExportMessage
    boolean hasScopeParent() {
        return scopeIndex < (scopes.length - 1);
    }

    @ExportMessage
    Object getScopeParent() throws UnsupportedMessageException {
        if (scopeIndex < (scopes.length - 1)) {
            return new PythonScopes(scopes, scopeIndex + 1);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    public boolean hasSourceLocation(
                    @Shared("sourceInterop") @CachedLibrary(limit = "1") InteropLibrary lib) {
        return lib.hasSourceLocation(scopes[scopeIndex]);
    }

    @ExportMessage
    public SourceSection getSourceLocation(
                    @Shared("sourceInterop") @CachedLibrary(limit = "1") InteropLibrary lib) throws UnsupportedMessageException {
        return lib.getSourceLocation(scopes[scopeIndex]);
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
    boolean hasMembers(@Bind("$node") Node inliningTarget,
                    @Shared("interop") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Shared("lenghtProfile") @Cached InlinedIntValueProfile lengthProfile) {
        int length = lengthProfile.profile(inliningTarget, scopes.length);
        for (int i = scopeIndex; i < length; i++) {
            Object vars = this.scopes[i];
            if (interop.hasMembers(vars)) {
                return true;
            }
        }
        return false;
    }

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal,
                    @Bind("$node") Node inliningTarget,
                    @Shared("interop") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Shared("lenghtProfile") @Cached InlinedIntValueProfile lengthProfile) throws UnsupportedMessageException {
        int length = lengthProfile.profile(inliningTarget, scopes.length);
        Object[] keys = new Object[length - scopeIndex];
        for (int i = scopeIndex; i < length; i++) {
            keys[i - scopeIndex] = interop.getMembers(scopes[i]);
        }
        return new MergedPropertyNames(keys);
    }

    @ExportMessage
    boolean isMemberReadable(String member,
                    @Bind("$node") Node inliningTarget,
                    @Shared("interop") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Shared("lenghtProfile") @Cached InlinedIntValueProfile lengthProfile) {
        int length = lengthProfile.profile(inliningTarget, scopes.length);
        for (int i = scopeIndex; i < length; i++) {
            Object scope = this.scopes[i];
            if (interop.isMemberReadable(scope, member)) {
                return true;
            } else if (interop.isMemberExisting(scope, member)) {
                break;
            }
        }
        return false;
    }

    @ExportMessage
    Object readMember(String member,
                    @Bind("$node") Node inliningTarget,
                    @Shared("interop") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Shared("lenghtProfile") @Cached InlinedIntValueProfile lengthProfile) throws UnknownIdentifierException, UnsupportedMessageException {
        int length = lengthProfile.profile(inliningTarget, scopes.length);
        for (int i = scopeIndex; i < length; i++) {
            Object scope = this.scopes[i];
            if (interop.isMemberExisting(scope, member)) {
                return interop.readMember(scope, member);
            }
        }
        throw UnknownIdentifierException.create(member);
    }

    @ExportMessage
    void writeMember(String member, Object value,
                    @Bind("$node") Node inliningTarget,
                    @Shared("interop") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Shared("lenghtProfile") @Cached InlinedIntValueProfile lengthProfile)
                    throws UnknownIdentifierException, UnsupportedMessageException, UnsupportedTypeException {
        int length = lengthProfile.profile(inliningTarget, scopes.length);
        for (int i = scopeIndex; i < length; i++) {
            Object scope = this.scopes[i];
            if (interop.isMemberExisting(scope, member)) {
                interop.writeMember(scope, member, value);
                return;
            }
        }
        throw UnknownIdentifierException.create(member);
    }

    @ExportMessage
    boolean isMemberModifiable(String member,
                    @Bind("$node") Node inliningTarget,
                    @Shared("interop") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Shared("lenghtProfile") @Cached InlinedIntValueProfile lengthProfile) {
        int length = lengthProfile.profile(inliningTarget, scopes.length);
        for (int i = scopeIndex; i < length; i++) {
            Object scope = this.scopes[i];
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
        int length = lengthProfile.profile(inliningTarget, scopes.length);
        boolean wasInsertable = false;
        for (int i = scopeIndex; i < length; i++) {
            Object scope = this.scopes[i];
            if (interop.isMemberExisting(scope, member)) {
                return false;
            }
            if (interop.isMemberInsertable(scope, member)) {
                wasInsertable = true;
            }
        }
        return wasInsertable;
    }

    @ExportMessage
    Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects,
                    @Exclusive @CachedLibrary(limit = "1") InteropLibrary lib) {
        return lib.toDisplayString(scopes[scopeIndex]);
    }
}
