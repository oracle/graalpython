/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.runtime;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.nodes.Node;

public abstract class GilNode extends Node {

    private static final class Cached extends GilNode {
        @CompilationFinal ContextReference<PythonContext> contextRef;
        @CompilationFinal LanguageReference<PythonLanguage> languageRef;

        @Override
        public boolean isAdoptable() {
            return true;
        }

        @Override
        public GilNode release() {
            if (!getLanguage().singleThreadedAssumption.isValid()) {
                getContext().releaseGil();
            }
            return this;
        }

        @Override
        public GilNode acquire() {
            if (!getLanguage().singleThreadedAssumption.isValid()) {
                getContext().acquireGil();
            }
            return this;
        }

        private final PythonLanguage getLanguage() {
            if (languageRef == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                languageRef = lookupLanguageReference(PythonLanguage.class);
            }
            return languageRef.get();
        }

        private final PythonContext getContext() {
            if (contextRef == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                contextRef = lookupContextReference(PythonLanguage.class);
            }
            return contextRef.get();
        }
    }

    private abstract static class Uncached extends GilNode implements AutoCloseable {
        @Override
        public boolean isAdoptable() {
            return false;
        }

        @Override
        @TruffleBoundary
        public Uncached acquire() {
            if (!PythonLanguage.getCurrent().singleThreadedAssumption.isValid()) {
                PythonLanguage.getContext().acquireGil();
            }
            return this;
        }

        @Override
        @TruffleBoundary
        public Uncached release() {
            if (!PythonLanguage.getCurrent().singleThreadedAssumption.isValid()) {
                PythonLanguage.getContext().releaseGil();
            }
            return this;
        }

        public abstract void close();
    }

    public static final class UncachedRelease extends Uncached {
        private UncachedRelease() {
        }

        private static final UncachedRelease INSTANCE = new UncachedRelease();

        @Override
        public final void close() {
            acquire();
        }
    }

    public static final class UncachedAcquire extends Uncached {
        private UncachedAcquire() {
        }

        private static final UncachedAcquire INSTANCE = new UncachedAcquire();

        @Override
        public final void close() {
            release();
        }
    }

    public abstract GilNode acquire();

    public abstract GilNode release();

    public static GilNode create() {
        return new Cached();
    }

    public static UncachedRelease uncachedRelease() {
        return (UncachedRelease) UncachedRelease.INSTANCE.release();
    }

    public static UncachedAcquire uncachedAcquire() {
        return (UncachedAcquire) UncachedAcquire.INSTANCE.acquire();
    }

    public static GilNode getUncached() {
        // it doesn't matter which is used for the default uncached case
        return UncachedRelease.INSTANCE;
    }
}
