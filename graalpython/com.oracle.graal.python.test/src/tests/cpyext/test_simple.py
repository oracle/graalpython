# Copyright (c) 2018, Oracle and/or its affiliates.
#
# All rights reserved.
from . import CPyExtTestCase, GRAALPYTHON


class TestSimple(CPyExtTestCase):
    def test_demo(self):
        from . import demo
        assert demo.system("echo 1") == 0
        assert demo.system("arrrr") != 0

    def test_demo2(self):
        from . import demo2
        assert demo2.system("Hello World") == "Hello World from C", demo2.system("Hello World")
