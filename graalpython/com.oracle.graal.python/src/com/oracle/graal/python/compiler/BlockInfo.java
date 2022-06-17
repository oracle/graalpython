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
package com.oracle.graal.python.compiler;

import com.oracle.graal.python.pegparser.sst.StmtTy;

/**
 * Linked stack of block-related information needed to unwind execution before {@code break},
 * {@code continue} or {@code return}.
 */
class BlockInfo {
    BlockInfo outer;

    AbstractExceptionHandler findExceptionHandler() {
        if (outer != null) {
            return outer.findExceptionHandler();
        }
        return null;
    }

    abstract static class Loop extends BlockInfo {
        final Block start;
        final Block after;

        public Loop(Block start, Block after) {
            this.start = start;
            this.after = after;
        }
    }

    static class While extends Loop {
        public While(Block start, Block after) {
            super(start, after);
        }
    }

    static class For extends Loop {
        public For(Block start, Block after) {
            super(start, after);
        }
    }

    static class PopValue extends BlockInfo {
    }

    static class AbstractExceptionHandler extends BlockInfo {
        final Block tryBlock;
        final Block exceptionHandler;

        public AbstractExceptionHandler(Block tryBlock, Block exceptionHandler) {
            this.tryBlock = tryBlock;
            this.exceptionHandler = exceptionHandler;
        }

        @Override
        AbstractExceptionHandler findExceptionHandler() {
            return this;
        }
    }

    static class ExceptHandler extends AbstractExceptionHandler {
        public ExceptHandler(Block tryBlock, Block exceptionHandler) {
            super(tryBlock, exceptionHandler);
        }
    }

    static class TryExcept extends AbstractExceptionHandler {
        public TryExcept(Block tryBlock, Block exceptionHandler) {
            super(tryBlock, exceptionHandler);
        }
    }

    static class FinallyHandler extends AbstractExceptionHandler {
        public FinallyHandler(Block tryBlock, Block exceptionHandler) {
            super(tryBlock, exceptionHandler);
        }
    }

    static class HandlerBindingCleanup extends AbstractExceptionHandler {
        final String bindingName;

        public HandlerBindingCleanup(Block tryBlock, Block exceptionHandler, String bindingName) {
            super(tryBlock, exceptionHandler);
            this.bindingName = bindingName;
        }
    }

    static class With extends AbstractExceptionHandler {
        final StmtTy.With node;

        public With(Block tryBlock, Block exceptionHandler, StmtTy.With node) {
            super(tryBlock, exceptionHandler);
            this.node = node;
        }
    }

    static class TryFinally extends AbstractExceptionHandler {
        final StmtTy[] body;

        public TryFinally(Block tryBlock, Block exceptionHandler, StmtTy[] body) {
            super(tryBlock, exceptionHandler);
            this.body = body;
        }
    }
}
