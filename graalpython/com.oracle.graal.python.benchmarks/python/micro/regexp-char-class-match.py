import re

subject = 'abc'

char_class_match = re.compile('[a-z]')

def search_char_class_match(runs):
    for i in range(runs):
        match = char_class_match.search(subject) is not None
    return match

def __benchmark__(runs=100000000):
    match = search_char_class_match(runs)
    print("match", match)
