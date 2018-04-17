# Copyright (c) 2018, Oracle and/or its affiliates.
# Copyright (c) 2013-2016, Regents of the University of California
#
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without modification, are
# permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this list of
# conditions and the following disclaimer.
# 2. Redistributions in binary form must reproduce the above copyright notice, this list of
# conditions and the following disclaimer in the documentation and/or other materials provided
# with the distribution.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
# OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
# COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
# GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
# AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
# OF THE POSSIBILITY OF SUCH DAMAGE.

import unittest

class ArihtmeticTests(unittest.TestCase):

    def testAddition(self):
        self.assertEquals(3 + 4, 7, "3 + 4 not equal to 7")

    def testMultiplication(self):
        self.assertEqual(3 * 4, 12, "3 * 4 not equal to 12")
            
    def testSubtraction(self):
        self.assertEqual(3 - 4, -1, "3 - 4 not equal to -1")
      
    def testSyntax(self):
        self.assertRaises(SyntaxError, compile, "lambda x: x = 2", '<test string>', 'exec')
    
    
class ComparisonTests(unittest.TestCase):
    
    def testLessThan(self):
        self.assertTrue(3 < 4, "3 < 4 is not true")
         
    def testGreaterThan(self):
        self.assertTrue(4 > 3, "4 > 3 is not true")
#             
    def testGreaterThanOrEqual(self):
        self.assertTrue(3 >= 4, "3 >= 4 is not true")
    
      
  
if __name__ == '__main__':
    unittest.main()
    