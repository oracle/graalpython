#!/usr/bin/env python3
# encoding: UTF-8
# Copyright (c) 2021, Oracle and/or its affiliates.
# Copyright (C) 1996-2021 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2

"""Takes two grammar files and diffs them"""

import sys
import re
import tokenize

from pegen.grammar import Alt, Grammar, GrammarVisitor, Rule
from pegen.grammar_parser import GeneratedParser as GrammarParser
from pegen.tokenizer import Tokenizer


class DiffVisitor(GrammarVisitor):
    OLD_ESCAPE = "\033[9m\033[31m"
    NEW_ESCAPE = "\033[32m"
    NORMAL_ESCAPE = "\033[0m"

    FUNCCALL_REGEXP = re.compile(r"(?:\( [a-zA-Z0-9_]+ \*\))?((?:new )?[a-zA-Z0-9_\.]+(?:\[\])?)(?: {|\().*")

    def __init__(self, grammar1: Grammar, grammar2: Grammar):
        self.grammar1, self.grammar2 = grammar1, grammar2

    @classmethod
    def old(cls, text: str):
        return f"{cls.OLD_ESCAPE}{text}{cls.NORMAL_ESCAPE}"

    @classmethod
    def new(cls, text: str):
        return f"{cls.NEW_ESCAPE}{text}{cls.NORMAL_ESCAPE}"

    def diff(self):
        self.rules = rules1 = {}
        self.visit(self.grammar1)
        del self.rules

        self.rules = rules2 = {}
        self.visit(self.grammar2)
        del self.rules

        rules_added = []
        rules_removed = []

        for rulename in rules1:
            if rulename not in rules2:
                rules_removed.append(rulename)
        for rulename in rules2:
            if rulename not in rules1:
                rules_added.append(rulename)

        rules_diff = []
        replacement_functions = {}

        for rulename, (type, actions) in rules1.items():
            if rulename in rules2:
                new_type, new_actions = rules2[rulename]
                if type != new_type or actions != new_actions:
                    rules_diff.append(rulename)
                    for pattern,old_action in actions.items():
                        new_action = new_actions.get(pattern)
                        if new_action and new_action != old_action and (m1 := self.FUNCCALL_REGEXP.match(old_action)) and (m2 := self.FUNCCALL_REGEXP.match(new_action)):
                            replacement_functions.setdefault(m1.group(1), set()).add(m2.group(1))

        if rules_added:
            print(f"== Rules added [{len(rules_added)}]\n")
            for rulename in rules_added:
                new_type,new_actions = rules2[rulename]
                print(f"\t{rulename}[{new_type}]")
                for pattern,action in new_actions.items():
                    print(f"\t\t| {pattern} {{{action}}}")
                print()

        if rules_removed:
            print(f"== Rules removed [{len(rules_removed)}]\n")
            for rulename in rules_removed:
                old_type,old_actions = rules1[rulename]
                print(f"\t{rulename}[{old_type}]")
                for pattern,action in old_actions.items():
                    print(f"\t\t| {pattern} {{{action}}}")
                print()

        if rules_diff:
            print(f"== Rule differences [{len(rules_diff)}]\n")
            for rulename in rules_diff:
                old_type,old_actions = rules1[rulename]
                new_type,new_actions = rules2[rulename]
                print(f"\t{rulename}", end="")
                if old_type != new_type:
                    print(f"[{self.old(old_type)}{self.new(new_type)}]")
                else:
                    print(f"[{old_type}]")
                for pattern,old_action in old_actions.items():
                    print(f"\t\t| ", end="")
                    if pattern in new_actions:
                        print(pattern, end=" ")
                        new_action = new_actions[pattern]
                        if old_action != new_action:
                            print(f"{{{self.old(old_action)}{self.new(new_action)}}}")
                        else:
                            print(f"{{{old_action}}}")
                    else:
                        print(self.old(f"{pattern} {{{old_action}}}"))
                for pattern,new_action in new_actions.items():
                    if pattern not in old_actions:
                        print(self.new(f"\t\t| {pattern} {{{new_action}}}"))
                print()

        unchanged_rules = set(rules1.keys()) - set(rules_diff) - set(rules_removed)
        if unchanged_rules:
            print(f"== Unchanged rules [{len(unchanged_rules)}]")
            print("\n\t", "\n\t".join(sorted(unchanged_rules)), "\n", sep="")

        if replacement_functions:
            print(f"== Typical replacement functions\n")
            for old,new in sorted(replacement_functions.items()):
                print(f"\t{old}", "->", self.new(", ".join(new)))
                print()

    def visit_Rule(self, node: Rule):
        self.actions = {}
        self.visit(node.rhs)
        self.rules[node.name] = (node.type, self.actions)
        del self.actions

    def visit_Alt(self, node: Alt):
        action = re.sub(r" ([\.,\(\)\[\]]) ", r"\1", str(node.action)) # shorten action string
        self.actions[" ".join(str(item) for item in node.items)] = action


def main():
    if len(sys.argv) == 3:
        grammar_files = map(lambda f: open(f), sys.argv[1:])
    elif len(sys.argv) == 2 and not sys.stdin.isatty():
        grammar_files = [sys.stdin, open(sys.argv[1])]
    else:
        sys.exit("\n".join([
            "Usage:",
            f"\t\t{sys.argv[0]} GRAMMAR_FILE_OLD GRAMMAR_FILE_NEW",
            "\tor",
            f"\t\tcat GRAMMAR_FILE_OLD | {sys.argv[0]} GRAMMAR_FILE_NEW"
        ]))

    grammars = []
    for grammar_file in grammar_files:
        with grammar_file as file:
            tokenizer = Tokenizer(tokenize.generate_tokens(file.readline))
            parser = GrammarParser(tokenizer)
            grammar = parser.start()
            if not grammar:
                sys.exit(f"Failed to parse {grammar_file}")
            grammars.append(grammar)

    DiffVisitor(*grammars).diff()


if __name__ == "__main__":
    main()
