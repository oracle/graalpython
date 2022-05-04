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
import argparse
import os
import sys
import tokenize

from pegen.build import generate_token_definitions
from pegen.grammar_parser import GeneratedParser as GrammarParser
from pegen.tokenizer import Tokenizer
from pegjava.java_generator import JavaParserGenerator


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("grammar_file")
    parser.add_argument("tokens_file")
    parser.add_argument("output_file")
    parser.add_argument("--verbose", action="store_true")
    parser.add_argument("--debug", action="store_true")

    args = parser.parse_args()

    with open(args.grammar_file) as file:
        tokenizer = Tokenizer(tokenize.generate_tokens(file.readline), verbose=args.verbose)
        parser = GrammarParser(tokenizer, verbose=args.verbose)
        grammar = parser.start()

    if not grammar:
        sys.exit("Failed to generate grammar")

    with open(args.tokens_file, "r") as tok_file:
        all_tokens, exact_tokens, non_exact_tokens = generate_token_definitions(tok_file)

    with open(args.output_file, "w") as file:
        gen = JavaParserGenerator(grammar, all_tokens, exact_tokens, non_exact_tokens, file, debug=args.debug)
        gen.generate(os.path.relpath(args.grammar_file, os.path.dirname(args.output_file)))


if __name__ == '__main__':
    main()
