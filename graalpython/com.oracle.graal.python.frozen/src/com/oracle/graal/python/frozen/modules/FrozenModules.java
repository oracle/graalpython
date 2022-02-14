/* Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.frozen.modules;

import com.oracle.graal.python.frozen.PythonFrozenModule;

public final class FrozenModules {

    private static final class Map {
        private static final PythonFrozenModule Abc = new PythonFrozenModule("Abc", "abc");
        private static final PythonFrozenModule Codecs = new PythonFrozenModule("Codecs", "codecs");
        private static final PythonFrozenModule Io = new PythonFrozenModule("Io", "io");
        private static final PythonFrozenModule CollectionsAbc = new PythonFrozenModule("CollectionsAbc", "_collections_abc");
        private static final PythonFrozenModule Sitebuiltins = new PythonFrozenModule("Sitebuiltins", "_sitebuiltins");
        private static final PythonFrozenModule Genericpath = new PythonFrozenModule("Genericpath", "genericpath");
        private static final PythonFrozenModule Ntpath = new PythonFrozenModule("Ntpath", "ntpath");
        private static final PythonFrozenModule Posixpath = new PythonFrozenModule("Posixpath", "posixpath");
        private static final PythonFrozenModule Os = new PythonFrozenModule("Os", "os");
        private static final PythonFrozenModule Site = new PythonFrozenModule("Site", "site");
        private static final PythonFrozenModule Stat = new PythonFrozenModule("Stat", "stat");
        private static final PythonFrozenModule Hello = new PythonFrozenModule("Hello", "__hello__");
    }

    public static final PythonFrozenModule lookup(String name) {
        switch (name) {
            case "abc": return Map.Abc;
            case "codecs": return Map.Codecs;
            case "io": return Map.Io;
            case "_collections_abc": return Map.CollectionsAbc;
            case "_sitebuiltins": return Map.Sitebuiltins;
            case "genericpath": return Map.Genericpath;
            case "ntpath": return Map.Ntpath;
            case "posixpath": return Map.Posixpath;
            case "os": return Map.Os;
            case "site": return Map.Site;
            case "stat": return Map.Stat;
            case "__hello__": return Map.Hello;
            default: return null;
        }
    }
}
