import leftpad
import os

def test_leftpad_exact():
    assert leftpad.leftpad("hello", 5) == "hello"

def test_leftpad_shorter():
    assert leftpad.leftpad("hello", 15) == "          hello"

def test_leftpad_longer():
    assert leftpad.leftpad("hello world", 5) == "hello world"

def test_leftpad_failing():
    if 'GRAALPY_LEFTPAD_FAIL' in os.environ:
        assert leftpad.leftpad("hello", 5) == "hello world"
