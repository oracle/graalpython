# Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

from typing import Annotated

ensure_packages(pydantic="2.12.5")
from pydantic import BaseModel, BeforeValidator, TypeAdapter, field_validator, model_validator


def strip_text(value):
    return value.strip() if isinstance(value, str) else value


StrippedString = Annotated[str, BeforeValidator(strip_text)]


class Wine(BaseModel):
    id: int
    points: int
    title: StrippedString
    description: str | None
    price: float | None
    variety: str | None
    winery: str | None
    country: str
    designation: StrippedString | None

    @field_validator("description", "price", "variety", "winery", "designation", mode="before")
    @classmethod
    def convert_null(cls, value):
        return None if value == "null" else value

    @field_validator("country", mode="before")
    @classmethod
    def fill_country(cls, value):
        return "Unknown" if not value or value == "null" else value

    @model_validator(mode="after")
    def check_points(self):
        if not 0 <= self.points <= 100:
            raise ValueError("points must be between 0 and 100")
        return self


WINE_ADAPTER = TypeAdapter(list[Wine])
WINE_DATA = [
    {
        "id": str(i),
        "points": 80 + i % 20,
        "title": f"  Wine {i}  ",
        "description": "null" if i % 4 == 0 else f"Description for wine {i}",
        "price": "null" if i % 5 == 0 else str(10 + i % 40),
        "variety": "Merlot" if i % 3 else "null",
        "winery": f"Winery {i % 10}",
        "country": "null" if i % 7 == 0 else "US",
        "designation": f"  Vineyard {i % 12}  ",
    }
    for i in range(64)
]


def __benchmark__(iterations=1000):
    result = None
    for _ in range(iterations):
        result = WINE_ADAPTER.validate_python(WINE_DATA)
    return result
