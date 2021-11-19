/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.pegparser;

import org.junit.jupiter.api.Test;


public class AtomsTests extends ParserTestBase {

    @Test
    public void variableName() throws Exception {
        checkTreeResult("foo");
    }

    @Test
    public void atomTrue() throws Exception {
        checkTreeResult("True");
    }

    @Test
    public void atomFalse() throws Exception {
        checkTreeResult("False");
    }

    @Test
    public void atomNone() throws Exception {
        checkTreeResult("None");
    }

    @Test
    public void atomString1() throws Exception {
        checkTreeResult("'a String'");
    }

    @Test
    public void atomString2() throws Exception {
        checkTreeResult("\"a String\"");
    }

    @Test
    public void atomString3() throws Exception {
        checkTreeResult("'''a String'''");
    }

    @Test
    public void atomString4() throws Exception {
        checkTreeResult("\"\"\"a String\"\"\"");
    }

    @Test
    public void atomString5() throws Exception {
        checkTreeResult("'a' ' String'");
    }

    @Test
    public void atomString6() throws Exception {
        checkTreeResult("'''a''' ' String'");
    }

    @Test
    public void atomString7() throws Exception {
        checkTreeResult("\"a\" ' String'");
    }

    @Test
    public void atomFString() throws Exception {
        checkTreeResult("f'a{b!r}'");
        checkError("f'a{b!g}'", "Generic[0:4]:f-string: invalid conversion character: expected 's', 'r', or 'a'");
    }

    @Test
    public void atomByte() throws Exception {
        checkTreeResult("b\"a\"");
    }

    @Test
    public void atomMixedBytesString() throws Exception {
        checkError("b\"a\" f'aa'", "Generic[0:10]:cannot mix bytes and nonbytes literals");
    }

    @Test
    public void atomTuple() throws Exception {
        checkTreeResult("(a,)");
    }

    @Test
    public void atomGroup() throws Exception {
        checkTreeResult("(a)");
    }

    @Test
    public void atomList() throws Exception {
        checkTreeResult("[2]");
    }

    @Test
    public void atomListcomp() throws Exception {
        checkTreeResult("[[i] for i in a]");
    }

    @Test
    public void atomListcomp2() throws Exception {
        checkTreeResult("[[i] for i in a for [j] in b if 12]");
    }

    @Test
    public void atomDict() throws Exception {
        checkTreeResult("{1: 2}");
    }

    @Test
    public void atomSet() throws Exception {
        checkTreeResult("{a, 2}");
    }

    @Test
    public void atomDictcomp() throws Exception {
        checkTreeResult("{(a,):(b) for a,b in y}");
    }

    @Test
    public void atomSetcomp() throws Exception {
        checkTreeResult("{(a,) for a in b}");
    }

    @Test
    public void atomGenerator() throws Exception {
        checkTreeResult("((a,) for a in b)");
    }

    @Test
    public void atomEllipsis() throws Exception {
        checkTreeResult("...");
    }
}
