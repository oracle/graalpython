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
    output_file = path.join(__dir__, "..", "java", "com", "oracle", "graal", "python", "pegparser", "GenParser.java")
    with open(output_file, "w") as file:
        gen: ParserGenerator = JavaParserGenerator(grammar, all_tokens, exact_tokens, non_exact_tokens, file)
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
