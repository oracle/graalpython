# Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import os
import sys
import tokenize

from os import path

from pegen.build import generate_token_definitions
from pegen.grammar import Grammar
from pegen.grammar_parser import GeneratedParser as GrammarParser
from pegen.tokenizer import Tokenizer
from pegjava.java_generator import JavaParserGenerator

verbose_tokenizer = False
verbose_parser = False

def main():
    __dir__ = path.dirname(__file__)
    grammar_file = path.relpath(path.join(__dir__, "pegjava", "python.gram"), os.getcwd())
    print("Reading", grammar_file)
    with open(grammar_file) as file:
        tokenizer = Tokenizer(tokenize.generate_tokens(file.readline), verbose=verbose_tokenizer)
        parser = GrammarParser(tokenizer, verbose=verbose_parser)
        grammar = parser.start()

    if not grammar:
        sys.exit("Fail")

    tokens_file = path.join(__dir__, "pegjava", "Tokens")
    with open(tokens_file, "r") as tok_file:
        all_tokens, exact_tokens, non_exact_tokens = generate_token_definitions(tok_file)
#    print("all_tokens")
#    print(all_tokens)

#    print("exact_tokens")
#    print(exact_tok)

#    print("non_exact_tokens")
#    print(non_exact_tok)
    output_file = path.join(__dir__, "..", "java", "com", "oracle", "graal", "python", "pegparser", "Parser.java")
    with open(output_file, "w") as file:
        gen: ParserGenerator = JavaParserGenerator(grammar, all_tokens, exact_tokens, non_exact_tokens, file, debug=False)
        gen.generate(grammar_file)
#    print("[")
#    for rule in rules:
#        print(f"  {rule},")
#    print("]")
#    for rule in rules:
#        print(rule.name, end=": ", file=sys.stderr)
#        print(*(" ".join(alt) for alt in rule.alts), sep=" | ", file=sys.stderr)
#
#
#
#    outfile = "../src/genPythonParser/GenParser.java"
#    print("Updating", outfile, file=sys.stderr)
#    with open(outfile, "w") as stream:
#        generate(rules, stream)

if __name__ == '__main__':
    main()
