package com.oracle.graal.python.pegparser;

import java.io.File;

import org.junit.Test;

public class PatternMatchingTests extends ParserTestBase {

    @Test
    public void singletons() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void tupleSubject() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void guard() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void asPattern() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void literals() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void attr() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void sequence() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void missingColonInMatch() {
        checkSyntaxErrorMessage("match a\n    case None: pass", "expected ':'");
    }

    @Test
    public void missingColonInCase() {
        checkSyntaxErrorMessage("match a:\n    case None pass", "expected ':'");
    }

    @Test
    public void missingIndentAfterMatch() {
        checkIndentationError("match a:\ncase None: pass");
    }

    @Test
    public void missingIndentAfterCase() {
        checkIndentationError("match a:\n  case None:\n  pass");
    }

    @Test
    public void underscoreAsTarget() {
        checkSyntaxErrorMessage("match a:\n  case None as _:\n  pass", "cannot use '_' as a target");
    }

    @Test
    public void exprAsTarget() {
        checkSyntaxErrorMessage("match a:\n  case None as 4:\n  pass", "invalid pattern target");
    }

    @Test
    public void realNumberRequired() {
        checkSyntaxErrorMessage("match a:\n    case 1j+2j: pass", "real number required in complex literal");
    }

    @Test
    public void imaginaryNumberRequired() {
        checkSyntaxErrorMessage("match a:\n    case 1+2: pass", "imaginary number required in complex literal");
    }

    private void checkScopeAndTree() throws Exception {
        File testFile = getTestFileFromTestAndTestMethod();
        checkScopeFromFile(testFile, false);
        checkTreeFromFile(testFile, false);
    }
}
