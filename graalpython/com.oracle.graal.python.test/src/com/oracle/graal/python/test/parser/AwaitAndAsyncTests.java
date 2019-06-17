/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.test.parser;

import org.junit.Test;

public class AwaitAndAsyncTests extends ParserTestBase {
      
    @Test
    public void await01() throws Exception {
        checkScopeAndTree("async def f():\n await smth()");
    }

    @Test
    public void await02() throws Exception {
        checkScopeAndTree("async def f():\n foo = await smth()");
    }

    @Test
    public void await03() throws Exception {
        checkScopeAndTree("async def f():\n foo, bar = await smth()");
    }

    @Test
    public void await04() throws Exception {
        checkScopeAndTree("async def f():\n (await smth())");
    }

    @Test
    public void await05() throws Exception {
        checkScopeAndTree("async def f():\n foo((await smth()))");
    }

    @Test
    public void await06() throws Exception {
        checkScopeAndTree("async def f():\n await foo(); return 42");
    }
    
    @Test
    public void asyncWith01() throws Exception {
        checkScopeAndTree("async def f():\n async with 1: pass");
    }

    @Test
    public void asyncWith02() throws Exception {
        checkScopeAndTree("async def f():\n async with a as b, c as d: pass");
    }
        
    @Test
    public void asyncFor01() throws Exception {
        checkScopeAndTree("async def f():\n async for i in (): pass");
    }

    @Test
    public void asyncFor02() throws Exception {
        checkScopeAndTree("async def f():\n async for i, b in (): pass");
    }
}
