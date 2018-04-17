# Copyright (c) 2018, Oracle and/or its affiliates.
#
# All rights reserved.
from . import CPyExtTestCase, GRAALPYTHON


class TestWiki(CPyExtTestCase):
    def test_noddy(self):
        from . import noddy
        assert type(noddy.Noddy) is type
        assert type(noddy.Noddy()) is noddy.Noddy, str(type(noddy.Noddy()))
        assert str(noddy.Noddy()).startswith("<noddy.Noddy object at"), str(noddy.Noddy())

    def test_noddy2(self):
        from . import noddy2
        nd = noddy2.Noddy2("First", "Last", 42)
        assert nd.name() == "'First' 'Last' '42'"
        assert nd.first == "First", ("%s != First" % nd.first)
        assert nd.last == "Last"
        assert nd.number == 42

    def test_noddy3(self):
        from . import noddy3
        nd = noddy3.Noddy(b"First", b"Last", 42)
        assert nd.name() == "b'First' b'Last'", nd.name()
        assert nd.first == b"First", ("%s != First" % nd.first)
        assert nd.last == b"Last"
        assert nd.number == 42
