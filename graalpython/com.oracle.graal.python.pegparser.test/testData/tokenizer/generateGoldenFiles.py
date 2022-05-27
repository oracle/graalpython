# Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

from os import listdir
from os.path import isfile, join, splitext, basename, dirname
from io import StringIO
from tokenize import generate_tokens
import token as Token

inputDir = join(dirname(__file__), "testFiles")
outputDir = join(dirname(__file__), "goldenFiles")

files = [f for f in listdir(inputDir) if isfile(join(inputDir, f))]

for file in files:
    if file.endswith(".data"):
        dataFile = open(join(inputDir, file), 'r')
        lines = dataFile.readlines()

        outputFile = open(join(outputDir, splitext(file)[0] + ".token"), 'w')


        for line in lines:
            line = line.strip()
            if len(line) > 0 and not line.startswith('//'):
                outputFile.write(line + '\n')
                tokens = generate_tokens(StringIO(line).readline)
                for token in tokens:
                    end = token.end
                    start = token.start
                    text = token.string
                    if token.type == Token.COMMENT and token.string.startswith("# type: "):
                        # TODO this is hack. The python tokenizer doesn't recognize the TYPE_COMMENTs
                        outputFile.write("Token type:58 (TYPE_COMMENT)")
                        text = text.strip()[len("# type: "): len(text)]
                        start = list(start)
                        start[1] = start[1] + int(token.string.find(text))
                        start = tuple(start)
                        end = list(end)
                        end[1] = start[1] + len(text)
                        end = tuple(end)
                    else: 
                        if token.type == Token.NEWLINE:
                            # I'm not sure that this is the right fix. But at least for now it reflects changes in tokenizer. 
                            # On the other hadn this script is now limited only for one line tests
                            start = (2, -1)
                            end = (2, 0)
                        elif token.type == Token.ENDMARKER:
                            start = (3, 0)
                            end = (3, 0)
                        outputFile.write("Token type:%d (%s)" % (token.type, Token.tok_name[token.type]))
                        if token.type == Token.OP:
                            outputFile.write(" exact_type:%d (%s)" % (token.exact_type, Token.tok_name[token.exact_type]))
                    
                    outputFile.write(" start:[%d, %d] end:[%d, %d]" % (start + end))
                    outputFile.write(" string:'%s'" % (text))
                    outputFile.write('\n')
                outputFile.write('\n')
