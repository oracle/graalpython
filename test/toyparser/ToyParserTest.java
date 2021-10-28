/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package toyparser;

import org.junit.Test;
import static org.junit.Assert.*;
import com.oracle.graal.python.pegparser.ParserTokenizer;
import com.oracle.graal.python.pegparser.tokenizer.Token;


public class ToyParserTest {

    public ToyParserTest() {
    }

    @Test
    public void testBasic() {
        String code = "f(42)";
        ParserTokenizer tokenizer = new ParserTokenizer(code);
        ToyParser tParser = new ToyParser(tokenizer);
        Token token = tParser.expect(Token.Kind.NAME);
        assertNotNull(token);
        assertEquals("f", tokenizer.getText(token));
        int pos = tParser.mark();
        token = tParser.expect(Token.Kind.LPAR);
        assertNotNull(token);
        assertEquals("(", tokenizer.getText(token));
        token = tParser.expect(Token.Kind.NUMBER);
        assertNotNull(token);
        assertEquals("42", tokenizer.getText(token));
        token = tParser.expect(Token.Kind.RPAR);
        assertNotNull(token);
        assertEquals(")", tokenizer.getText(token));
        int pos2 = tParser.mark();

        tParser.reset(pos);
        token = tParser.expect(Token.Kind.LPAR);
        assertNotNull(token);
        assertEquals("(", tokenizer.getText(token));
        token = tParser.expect(Token.Kind.NUMBER);
        assertNotNull(token);
        assertEquals("42", tokenizer.getText(token));
        token = tParser.expect(Token.Kind.RPAR);
        assertNotNull(token);
        assertEquals(")", tokenizer.getText(token));

        tParser.reset(pos);
        token = tParser.expect(Token.Kind.LPAR);
        assertNotNull(token);
        assertEquals("(", tokenizer.getText(token));

        tParser.reset(pos2);
//        assertNotNull(tParser.expect(Token.Kind.ENDMARKER));
    }


    @Test
    public void testToy() {
        String code = "x - (y +z)";
        ParserTokenizer tokenizer = new ParserTokenizer(code);
        ToyParser tParser = new ToyParser(tokenizer);
        Node result = tParser.statement();
        assertNotNull(result);
        assertEquals("sub", result.type);
        assertEquals("name", result.children.get(0).type);
        assertEquals("add", result.children.get(1).type);
    }

}
