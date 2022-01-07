from __future__ import annotations


def b(x: int | str) -> int | str:
    return 10

print(b.__annotations__)

x: int | str = 10
