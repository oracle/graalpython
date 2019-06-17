/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.test.parser;

import org.junit.Test;

/**
 *
 * @author petr
 */
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
