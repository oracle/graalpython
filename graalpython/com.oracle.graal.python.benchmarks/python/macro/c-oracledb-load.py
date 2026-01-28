# Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

# blog_load.py
#
# This is the code used for the blog:
#  https://cjones-oracle.medium.com/direct-path-loads-fast-data-ingestion-with-python-and-oracle-database-c681fb60384f
#
# christopher.jones@oracle.com, 2025
#
# Compare end-to-end times for reading a CSV file (number, date, string) in
# chunks and inserting into the DB. Pandas, executemany() and
# direct_path_load() are compared.
#
# The CSV file can be created using blog_create.py found at
#   https://gist.github.com/cjbj/f4b605cb7db9acdf10f4ff3a2ba58d4d, inlined below into the generate_csv function
#
# Create a CSV file with 1,000,000 rows:
#   python blog_create.py 1000000
#
# Benchmark loading the CSV file in batches of 1,000,000 rows
#  python blog_load.py 1000000
#
# Benchmark loading the CSV file in batches of 500,000 rows
#  python blog_load.py 500000
#
# Install:
#   python -m pip install oracledb pyarrow sqlalchemy pandas
# Requires python-oracledb 3.4+


ensure_packages(oracledb="3.4.1", pandas="2.2.3", pyarrow="20.0.0", sqlalchemy="2.0.45")

import csv
from datetime import datetime
import getpass
import os
import sys
import time

import pyarrow.csv
from sqlalchemy import create_engine
import pandas

import oracledb

# startup database with
# $ podman run --detach --replace --name oracledb -p 1521:1521 -e ORACLE_PWD=graalpy  container-registry.oracle.com/database/free:latest
USERNAME = 'system'
CONNECTSTRING = 'localhost:1521/freepdb1'
PASSWORD = "graalpy"

# -----------------------------------------------------------------------------

FILE_NAME = os.path.join(os.path.dirname(__file__), "sample.csv")

if (len(sys.argv) > 1):
    BATCH_SIZE = int(sys.argv[1])
else:
    BATCH_SIZE = 2_000_000

# -----------------------------------------------------------------------------

def createtab(connection, tab):
    with connection.cursor() as cursor:
        cursor.execute(f"""drop table if exists {tab} purge""") # 23ai syntax
        cursor.execute(f"""create table {tab} (
                                        id   number,
                                        dt   date,
                                        name varchar2(50))""")

# -----------------------------------------------------------------------------

def droptabs(connection, tabs):
    with connection.cursor() as cursor:
        for tab in tabs:
            cursor.execute(f"drop table if exists {tab} purge") # 23ai syntax

# -----------------------------------------------------------------------------

def checkrowcount(connection, tab):
    r, = connection.cursor().execute(f"select count(*) from {tab}").fetchone()
    print(f"{r} rows were inserted")

# -----------------------------------------------------------------------------

# Compare tables
def compare(connection, t1, t2):
    print(f"\nChecking '{t1}' and '{t2}'")

    sql = f"""(
      select * from {t1}
      minus
      select * from {t2}
    ) union all (
      select * from {t2}
      minus
      select * from {t1}
    )"""

    with connection.cursor() as cursor:
        for r in cursor.execute(sql):
            print(f"Tables '{t1}' and '{t2}' differ")
            print(r)
            exit()

    print(f"Tables are the same")

# -----------------------------------------------------------------------------
# Using Pandas to read and insert

def pd(tab):
    print("\nPandas read_csv() - Pandas to_sql()")

    engine = create_engine(
        "oracle+oracledb://@",
        connect_args={
            "user": USERNAME,
            "password": PASSWORD,
            "dsn": CONNECTSTRING,
        },
    #   echo=True
    )

    start = time.perf_counter_ns()

    csv_reader = pandas.read_csv(
        FILE_NAME,
        header=None,
        names=["id", "dt", "name"],
        parse_dates=['dt'],
        chunksize=BATCH_SIZE)
    for df in csv_reader:
        df.to_sql(tab, engine, if_exists='append', index=False)

    elapsed = (time.perf_counter_ns() - start) / 1_000_000
    print(f"Loaded in batches of size {BATCH_SIZE}")
    print(f"Total elapsed time: {elapsed:,.1f} ms")

# -----------------------------------------------------------------------------
#  Using Python's CSV package to read into a list and then calling executemany()

def em(connection, tab):
    print("\nPython CSV loader to list - executemany()")

    start = time.perf_counter_ns()

    cursor = connection.cursor()

    cursor.setinputsizes(None, None, 50)

    sql = f"insert into {tab} (id, dt, name) values (:1, :2, :3)"
    data = []
    batch_number = 0
    csv_reader = csv.reader(open(FILE_NAME, "r"), delimiter=",")
    for line in csv_reader:
        data.append((float(line[0]), datetime.strptime(line[1], "%d-%b-%Y"), line[2]))
        if len(data) % BATCH_SIZE == 0:
            cursor.executemany(sql, data)
            data = []
            batch_number += 1
    if data:
        cursor.executemany(sql, data)
        batch_number += 1

    connection.commit()

    elapsed = (time.perf_counter_ns() - start) / 1_000_000
    print(f"Loaded in {batch_number} batches of size {BATCH_SIZE}")
    print(f"Total elapsed time: {elapsed:,.1f} ms")

# -----------------------------------------------------------------------------
# Using PyArrow to read into Dataframe and then calling executemany()

def pyaem(connection, tab):
    print("\nPyArrow CSV loader to DataFrame - executemany()")

    start = time.perf_counter_ns()

    sql = f"insert into {tab} (id, dt, name) values (:1, :2, :3)"
    colnames=["id", "dt", "name"]

    read_options = pyarrow.csv.ReadOptions(
        column_names=colnames,
        block_size=BLOCK_SIZE
    )

    convert_options = pyarrow.csv.ConvertOptions(
        timestamp_parsers=["%d-%b-%Y"],
        column_types={
            "id": pyarrow.int64(),
            "dt": pyarrow.timestamp("us"),
            "name": pyarrow.string()
        }
    )

    cursor = connection.cursor()

    batch_number = 0
    csv_reader = pyarrow.csv.open_csv(FILE_NAME, read_options=read_options, convert_options=convert_options)
    for df in csv_reader:
        if df is None:
            break
        batch_number += 1
        cursor.executemany(sql, df)

    connection.commit()

    elapsed = (time.perf_counter_ns() - start) / 1_000_000
    print(f"Loaded in {batch_number} batches with block size {BLOCK_SIZE}")
    print(f"Total elapsed time: {elapsed:,.1f} ms")

# -----------------------------------------------------------------------------
# Using Python's CSV package to read into a list and then a Direct Path Load
#

def dpl(connection, tab):
    print("\nPython CSV loader to list - Direct Path Load")

    start = time.perf_counter_ns()

    column_names = ["id", "dt", "name"]
    data = []
    batch_number = 0
    csv_reader = csv.reader(open(FILE_NAME, "r"), delimiter=",")
    for line in csv_reader:
        data.append((float(line[0]), datetime.strptime(line[1], "%d-%b-%Y"), line[2]))
        if len(data) % BATCH_SIZE == 0:
            connection.direct_path_load(
                schema_name=USERNAME,
                table_name=tab,
                column_names=column_names,
                data=data)
            batch_number += 1
            data = []
    if data:
        connection.direct_path_load(
            schema_name=USERNAME,
            table_name=tab,
            column_names=column_names,
            data=data)
        batch_number += 1

    print(f"Loaded in {batch_number} batches of size {BATCH_SIZE}")
    elapsed = (time.perf_counter_ns() - start) / 1_000_000
    print(f"Total elapsed time: {elapsed:,.1f} ms")

# -----------------------------------------------------------------------------
# Using PyArrow to read into Dataframe and then a Direct Path Load

def pya(connection, tab):
    print("\nPyArrow CSV loader to DataFrame - Direct Path Load ")

    start = time.perf_counter_ns()

    column_names=["id", "dt", "name"]

    read_options = pyarrow.csv.ReadOptions(
        column_names=column_names,
        block_size=BLOCK_SIZE
    )

    convert_options = pyarrow.csv.ConvertOptions(
        timestamp_parsers=["%d-%b-%Y"],
        column_types={
            "id": pyarrow.int64(),
            "dt": pyarrow.timestamp("us"),
            "name": pyarrow.string()
        }
    )

    csv_reader = pyarrow.csv.open_csv(FILE_NAME, read_options=read_options, convert_options=convert_options)
    batch_number = 0
    for df in csv_reader:
        if df is None:
            break
        batch_number += 1
        connection.direct_path_load(
            schema_name=USERNAME,
            table_name=tab,
            column_names=column_names,
            data=df)

    elapsed = (time.perf_counter_ns() - start) / 1_000_000
    print(f"Loaded in {batch_number} batches with block size {BLOCK_SIZE}")
    print(f"Total elapsed time: {elapsed:,.1f} ms")

# -----------------------------------------------------------------------------

BLOCK_SIZE = 0
CONNECTION = None

def __setup__(*args):
    # blog_create.py
    #
    # christopher.jones@oracle.com, 2025
    #
    # Create a CSV file (number, date, string)
    import csv
    import sys
    from datetime import datetime


    num_records = BATCH_SIZE
    data = [
        (
            i + 1,
            datetime.now().strftime("%d-%b-%Y"),
            f"String for row {i + 1}"
        ) for i in range(num_records)
    ]

    with open(FILE_NAME, "w") as f:
        writer = csv.writer(
            f, lineterminator="\n", quoting=csv.QUOTE_NONNUMERIC
        )
        writer.writerows(data)

    print(f"Created {FILE_NAME} with {num_records} records")

    global BLOCK_SIZE, CONNECTION
    BLOCK_SIZE = len(max(open(FILE_NAME, 'r'), key=len)) * BATCH_SIZE
    CONNECTION = oracledb.connect(user=USERNAME, password=PASSWORD, dsn=CONNECTSTRING)


def __benchmark__(num=1):
    assert num == 1
    t1 = "mytabpya"
    createtab(CONNECTION, t1)
    pya(CONNECTION, t1)
    checkrowcount(CONNECTION, t1)

    t2 = "mytabdpl"
    # createtab(CONNECTION, t2)
    # dpl(CONNECTION, t2)
    # checkrowcount(CONNECTION, t2)

    t3 = "mytabpyaem"
    # createtab(CONNECTION, t3)
    # pyaem(CONNECTION, t3)
    # checkrowcount(CONNECTION, t3)

    t4 = "mytabem"
    # createtab(CONNECTION, t4)
    # em(CONNECTION, t4)
    # checkrowcount(CONNECTION, t4)

    t5 = "mytabpd"
    # createtab(CONNECTION, t5)
    # pd(t5)
    # checkrowcount(CONNECTION, t5)

    # Check all the tables are the same
    #compare(CONNECTION, t1, t2); compare(CONNECTION, t2, t3); compare(CONNECTION, t3, t4); compare(CONNECTION, t4, t5)


def __cleanup__(*args):
    droptabs(CONNECTION, [t1, t2, t3, t4, t5])


if __name__ == "__main__":
    __setup__()
    print("\nCompare end-to-end times for reading a "
          "CSV file (number, date, string) in chunks and inserting into the Database")
    __benchmark__(1)
    __cleanup__()
