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

package com.oracle.graal.python.test.parser;

import org.junit.Test;

public class ImportTests extends ParserTestBase {
    
    @Test
    public void basic01() throws Exception {
        checkTreeResult("import sys");
    }
    
    @Test
    public void basic02() throws Exception {
        checkTreeResult("import sys as system");
    }

    @Test
    public void basic03() throws Exception {
        checkTreeResult("import sys, math");
    }
    
    @Test
    public void basic04() throws Exception {
        checkTreeResult("import sys as system, math");
    }
    
    @Test
    public void basic05() throws Exception {
        checkTreeResult("import sys, math as my_math");
    }
    
    @Test
    public void basic06() throws Exception {
        checkTreeResult("import encodings.aliases");
    }
    
    @Test
    public void basic07() throws Exception {
        checkTreeResult("import encodings.aliases as a");
    }
    
    @Test
    public void basic08() throws Exception {
        checkTreeResult("import encodings.aliases.something");
    }
    
    @Test
    public void basic09() throws Exception {
        checkTreeResult("import encodings.aliases.something as a");
    }
    
    @Test
    public void basicInFn01() throws Exception {
        checkScopeAndTree("def fn():\n  import sys");
    }
    
    @Test
    public void basicInFn02() throws Exception {
        checkScopeAndTree("def fn():\n  import sys as system");
    }

    @Test
    public void basicInFn03() throws Exception {
        checkScopeAndTree("def fn():\n  import sys, math");
    }
    
    @Test
    public void basicInFn04() throws Exception {
        checkScopeAndTree("def fn():\n  import sys as system, math");
    }
    
    @Test
    public void basicInFn05() throws Exception {
        checkScopeAndTree("def fn():\n  import sys, math as my_math");
    }
    
    @Test
    public void fromImport01() throws Exception {
        checkTreeResult("from sys.path import *");
    }
    
    @Test
    public void fromImport02() throws Exception {
        checkTreeResult("from sys.path import dirname");
    }
    
    @Test
    public void fromImport03() throws Exception {
        checkTreeResult("from sys.path import (dirname)");
    }

    @Test
    public void fromImport04() throws Exception {
        checkTreeResult("from sys.path import (dirname,)");
    }

    @Test
    public void fromImport05() throws Exception {
        checkTreeResult("from sys.path import dirname as my_dirname");
    }

    @Test
    public void fromImport06() throws Exception {
        checkTreeResult("from sys.path import (dirname as my_dirname)");
    }

    @Test
    public void fromImport07() throws Exception {
        checkTreeResult("from sys.path import (dirname as my_dirname,)");
    }

    @Test
    public void fromImport08() throws Exception {
        checkTreeResult("from sys.path import dirname, basename");
    }

    @Test
    public void fromImport09() throws Exception {
        checkTreeResult("from sys.path import (dirname, basename)");
    }

    @Test
    public void fromImport10() throws Exception {
        checkTreeResult("from sys.path import (dirname, basename,)");
    }

    @Test
    public void fromImport11() throws Exception {
        checkTreeResult("from sys.path import dirname as my_dirname, basename");
    }

    @Test
    public void fromImport13() throws Exception {
        checkTreeResult("from sys.path import (dirname as my_dirname, basename)");
    }

    @Test
    public void fromImport14() throws Exception {
        checkTreeResult("from sys.path import (dirname as my_dirname, basename,)");
    }

    @Test
    public void fromImport15() throws Exception {
        checkTreeResult("from sys.path import dirname, basename as my_basename");
    }
    @Test
    public void fromImport16() throws Exception {
        checkTreeResult("from sys.path import (dirname, basename as my_basename)");
    }
    @Test
    public void fromImport17() throws Exception {
        checkTreeResult("from sys.path import (dirname, basename as my_basename,)");
    }

    @Test
    public void fromImport18() throws Exception {
        checkTreeResult("from .bogus import x");
    }
    
    @Test
    public void fromImportInFn01() throws Exception {
        checkSyntaxError("def fn():\n  from sys.path import *");
    }
    
    @Test
    public void fromImportInFn02() throws Exception {
        checkScopeAndTree("def fn():\n  from sys.path import dirname");
    }
    
    @Test
    public void fromImportInFn03() throws Exception {
        checkScopeAndTree("def fn():\n  from sys.path import dirname as my_dirname");
    }
    
    @Test
    public void fromImportInFn04() throws Exception {
        checkScopeAndTree("def fn():\n  from sys.path import (dirname as my_dirname, basename)");
    }
    
    @Test
    public void relativeImport01() throws Exception {
        checkTreeResult("from . import name");
    }
    
    @Test
    public void relativeImport02() throws Exception {
        checkTreeResult("from .. import name");
    }

    @Test
    public void relativeImport03() throws Exception {
        checkTreeResult("from ... import name");
    }

    @Test
    public void relativeImport04() throws Exception {
        checkTreeResult("from .... import name");
    }

    @Test
    public void relativeImport05() throws Exception {
        checkTreeResult("from .pkg import name");
    }

    @Test
    public void relativeImport06() throws Exception {
        checkTreeResult("from ..pkg import name");
    }

    @Test
    public void relativeImport07() throws Exception {
        checkTreeResult("from ...pkg import name");
    }

    @Test
    public void relativeImport08() throws Exception {
        checkTreeResult("from ....pkg import name");
    }
}
