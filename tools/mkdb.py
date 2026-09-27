#!/usr/bin/env python3
"""Build mozhi.db (SQLite) from the mozhi repo's enml.json.

Schema:
    entries(word TEXT PRIMARY KEY, lword TEXT, meanings TEXT)
    - word: original spelling (e.g. "A bad patch")
    - lword: lowercased word, indexed for case-insensitive prefix search
    - meanings: JSON array of Malayalam meaning strings
"""
import json
import os
import sqlite3
import sys

def main(src, dst):
    print('loading', src)
    with open(src, encoding='utf-8') as f:
        data = json.load(f)
    print('words:', len(data))

    if os.path.exists(dst):
        os.remove(dst)
    con = sqlite3.connect(dst)
    cur = con.cursor()
    cur.execute('CREATE TABLE entries(word TEXT PRIMARY KEY, lword TEXT, meanings TEXT)')
    rows = [(w, w.lower(), json.dumps(m, ensure_ascii=False))
            for w, m in data.items()]
    cur.executemany('INSERT INTO entries(word, lword, meanings) VALUES (?,?,?)', rows)
    cur.execute('CREATE INDEX idx_lword ON entries(lword)')
    con.commit()
    cur.execute('VACUUM')
    con.commit()

    n = cur.execute('SELECT COUNT(*) FROM entries').fetchone()[0]
    print('rows in db:', n)
    # sanity: prefix search like the app does
    r = cur.execute(
        "SELECT word FROM entries WHERE lword LIKE ? ESCAPE '\\' "
        "ORDER BY lword LIMIT 5", ('malayala%',)).fetchall()
    print('prefix "malayala":', [x[0] for x in r])
    r = cur.execute('SELECT meanings FROM entries WHERE word = ?',
                    ('apple',)).fetchone()
    print('apple meanings:', r[0][:120] if r else None)
    con.close()
    print('wrote', dst, os.path.getsize(dst), 'bytes')

if __name__ == '__main__':
    src = sys.argv[1] if len(sys.argv) > 1 else '/tmp/mozhi/enml.json'
    dst = sys.argv[2] if len(sys.argv) > 2 else 'mozhi.db'
    main(src, dst)
