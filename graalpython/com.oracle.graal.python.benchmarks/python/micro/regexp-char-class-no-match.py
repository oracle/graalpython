import re

subject = 'abc'

char_class_no_match = re.compile('[0-9]')

def search_char_class_no_match(runs):
    for i in range(runs):
        match = char_class_no_match.search(subject) is not None
    return match

def __benchmark__(runs=100000000):
    match = search_char_class_no_match(runs)
    print("match", match)
