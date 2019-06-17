/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.test.parser;

import org.junit.Test;


public class DictAndSetTests extends ParserTestBase {
    
    @Test
    public void dict01() throws Exception {
        checkTreeResult("{}");
    }
    
    @Test
    public void dict02() throws Exception {
        checkTreeResult("{a:b}");
    }
    
    @Test
    public void dict03() throws Exception {
        checkTreeResult("{a:b,}");
    }

    @Test
    public void dict04() throws Exception {
        checkTreeResult("{a:b, c:d}");
    }
    
    @Test
    public void dict05() throws Exception {
        checkTreeResult("{a:b, c:d, }");
    }
    
    @Test
    public void dict06() throws Exception {
        checkTreeResult("{**{}}");
    }
    
    @Test
    public void dict07() throws Exception {
        checkTreeResult("{**{}, 3:4, **{5:6, 7:8}}");
    }
    
    @Test
    public void dict08() throws Exception {
        checkTreeResult("{1:2, **{}, 3:4, **{5:6, 7:8}}");
    }
    
    @Test
    public void dict09() throws Exception {
        checkTreeResult("{**{}, 3:4}");
    }
    
    @Test
    public void dict10() throws Exception {
        checkTreeResult("{**{\"a\": \"hello\", \"b\": \"world\"}, **{3:4, 5:6}}");
    }
    
    @Test
    public void dict11() throws Exception {
        checkTreeResult("{**{\"a\": \"hello\", \"b\": \"world\"}, 1:2,  **{3:4, 5:6}}");
    }
    
    @Test
    public void set01() throws Exception {
        checkTreeResult("{2}");
    }
    
    @Test
    public void set02() throws Exception {
        checkTreeResult("{2,}");
    }
    
    @Test
    public void set03() throws Exception {
        checkTreeResult("{2, 3}");
    }
    
    @Test
    public void set04() throws Exception {
        checkTreeResult("{2, 3,}");
    }
    
    @Test
    public void set05() throws Exception {
        checkTreeResult("{*{2}, 3, *[4]}");
    }    
}
