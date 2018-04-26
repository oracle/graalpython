
import unittest

class AssertionsTest(unittest.TestCase):
    
    def test_assert(self):
        try:
            assert False
        except:
            pass
        else:
            raise Exception("Assertions doesn't work!")
    
