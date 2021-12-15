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
package com.oracle.graal.python.pegparser;


import com.oracle.graal.python.pegparser.ScopeInfo.ScopeKind;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 */
public final class ScopeInfo {
    public enum ScopeKind {
        Module,
        Class,
        Function,
        AsyncFunction,
        Lambda,
        Comprehension;
    }

    private final SymbolList symbols;
    private final ScopeKind kind;
    private final ScopeInfo[] childScopes;

    ScopeInfo(SymbolList symbols, ScopeKind kind, ScopeInfo[] childScopes) {
        this.symbols = symbols;
        this.kind = kind;
        this.childScopes = childScopes;
    }

    public void analyze() {
        HashSet<String> free = new HashSet<>();
        HashSet<String> global = new HashSet<>();
        analyzeBlock(null, free, global);
    }

    private void analyzeBlock(HashSet<String> bound, HashSet<String> free, HashSet<String> global) {
        HashSet<String> local = new HashSet<>();
        HashMap<String, ScopeInfo> scopes = new HashMap<>();

        HashSet<String> newglobal = new HashSet<>();
        HashSet<String> newfree = new HashSet<>();
        HashSet<String> newbound = new HashSet<>();

        if (kind == ScopeKind.Class) {
            newglobal.addAll(global);
            if (bound != null) {
                newbound.addAll(bound);
            }
        }

        for (int i = 0; i < symbols.size(); i++) {
            analzyeName(scopes,
                            symbols.getNode(i), // name
                            symbols.getContext(i), // flags
                            bound,
                            local,
                            free,
                            global);
        }

        // Populate global and bound sets to be passed to children.
        if (kind != ScopeKind.Class) {
            if (kind == ScopeKind.Function) {
                newbound.addAll(local);
            }
            if (bound != null) {
                newbound.addAll(bound);
            }
            newglobal.addAll(global);
        } else {
            //
        }


    }
}
