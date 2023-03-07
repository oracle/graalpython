import re

subject = 'abc'

literal_no_match = re.compile('def')
literal_match = re.compile('abc')
universal_match = re.compile('.')
char_class_match = re.compile('[a-z]')
char_class_no_match = re.compile('[0-9]')

def search_literal_no_match(runs):
    for i in range(runs):
        match = literal_no_match.search(subject) is not None
    return match

def search_literal_match(runs):
    for i in range(runs):
        match = literal_match.search(subject) is not None
    return match

def search_universal_match(runs):
    for i in range(runs):
        match = universal_match.search(subject) is not None
    return match

def search_char_class_match(runs):
    for i in range(runs):
        match = char_class_match.search(subject) is not None
    return match

def search_char_class_no_match(runs):
    for i in range(runs):
        match = char_class_no_match.search(subject) is not None
    return match

def search_all(runs):
    for fn in [search_literal_no_match, search_literal_match, search_universal_match, search_char_class_match, search_char_class_no_match]:
        match = fn(runs)
    return match

def __benchmark__(runs=20000000):
    match = search_all(runs)
    print("match", match)
