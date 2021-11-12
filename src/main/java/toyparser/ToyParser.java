/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package toyparser;

import com.oracle.graal.python.pegparser.NodeFactory;
import java.util.Arrays;
import com.oracle.graal.python.pegparser.AbstractParser;
import com.oracle.graal.python.pegparser.ParserTokenizer;
import com.oracle.graal.python.pegparser.tokenizer.Token;

/**
 *
 * @author petr
 */
public class ToyParser extends AbstractParser {



    public ToyParser(ParserTokenizer tokenizer) {
        super(tokenizer, null);
    }

    public Node statement() {
        Node result = null;
        if ((result = assignment()) != null) {
            return result;
        }
        if ((result = expr()) != null) {
            return result;
        }
        if ((result = ifStatement()) != null) {
            return result;
        }
        return null;
    }

    public Node assignment() {
        int pos = mark();
        Node target = target();
        if (target != null) {
            Token token = expect(Token.Kind.EQUAL);
            if (token != null) {
                Node expr;
                if ((expr = expr()) != null) {
                    return new Node("assignment", Arrays.asList(new Node[]{target, expr}));
                }
            }
        }
        reset(pos);
        return null;
    }

    public Node expr() {
        Node term;
        int pos = 0;
        if ((term = term()) != null) {
            pos = mark();
            Token token = expect(Token.Kind.PLUS);
            if (token != null) {
                Node expr;
                if ((expr = expr()) != null) {
                    return new Node("add", Arrays.asList(new Node[]{term, expr}));
                }
            }
            reset(pos);
            token = expect(Token.Kind.MINUS);
            if (token != null) {
                Node expr;
                if ((expr = expr()) != null) {
                    return new Node("sub", Arrays.asList(new Node[]{term, expr}));
                }
            }
            reset(pos);
            return term;
        }
        return null;
    }

    public Node ifStatement() {
        int pos = mark();
        Token token = expect("if");
        if (token != null) {
            Node expr;
            if((expr = expr()) != null) {
                token = expect(Token.Kind.COLON);
                if (token != null) {
                    Node statement;
                    if ((statement = statement()) != null) {
                        return new Node("if", Arrays.asList(new Node[]{expr, statement}));
                    }
                }
            }
        }
        reset(pos);
        return null;
    }

    public Node term() {
        Node atom;
        if ((atom = atom()) != null) {
            int pos = mark();
            Token token = expect(Token.Kind.STAR);
            if (token != null) {
                Node term;
                if ((term = term()) != null) {
                    return new Node("mul", Arrays.asList(new Node[]{atom, term}));
                }
            }
            reset(pos);
            token = expect(Token.Kind.SLASH);
            if (token != null) {
                Node term;
                if ((term = term()) != null) {
                    return new Node("div", Arrays.asList(new Node[]{atom, term}));
                }
            }
            reset(pos);
            return atom;
        }
        return null;
    }

    public Node atom() {
        Token token = expect(Token.Kind.NAME);
        if(token != null && Token.Kind.NAME == token.type) {
            return new Node("name", null);
        }
        token = expect(Token.Kind.NUMBER);
        if(token != null) {
            return new Node("number", null);
        }
        int pos = mark();
        token = expect(Token.Kind.LPAR);
        if (token != null) {
            Node expr;
            if ((expr = expr()) != null) {
                token = expect(Token.Kind.RPAR);
                if (token != null) {
                    return expr;
                }
            }
        }
        reset(pos);
        return null;
    }

    public Node target() {
        Token token = expect (Token.Kind.NAME);
        if (token != null) {
            return new Node("target", null);
        }
        return null;
    }

    @Override
    protected Object[][][] getReservedKeywords() {
        return null;
    }

    @Override
    protected String[] getSoftKeywords() {
        return null;
    }

}
