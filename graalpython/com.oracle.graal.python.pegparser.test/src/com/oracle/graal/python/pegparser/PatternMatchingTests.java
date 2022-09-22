package com.oracle.graal.python.pegparser;

import java.io.File;

import org.junit.Test;

public class PatternMatchingTests extends ParserTestBase {

    @Test
    public void singletons() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void missingColon() {
        checkSyntaxErrorMessage("match a\n    case None: pass", "expected ':'");
    }

    @Test
    public void missingIndent() {
        checkIndentationError("match a:\ncase None: pass");
    }

    private void checkScopeAndTree() throws Exception {
        File testFile = getTestFileFromTestAndTestMethod();
        checkScopeFromFile(testFile, false);
        checkTreeFromFile(testFile, false);
    }
}
