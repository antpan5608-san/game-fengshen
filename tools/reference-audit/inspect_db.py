"""Read-only SQLite structure/count inspection; no game data is generated."""
import json
import sqlite3
import sys
from pathlib import Path

path = Path(sys.argv[1]).resolve()
db = sqlite3.connect(path.as_uri() + '?mode=ro', uri=True)
db.row_factory = sqlite3.Row
result = {'integrity': [list(r) for r in db.execute('PRAGMA integrity_check')], 'tables': {}}
for name, sql in db.execute("SELECT name, sql FROM sqlite_master WHERE type='table' ORDER BY name"):
    quoted = '"' + name.replace('"', '""') + '"'
    rows = [dict(r) for r in db.execute('SELECT * FROM ' + quoted)]
    result['tables'][name] = {'sql': sql, 'count': len(rows), 'columns': [dict(r) for r in db.execute('PRAGMA table_info(' + quoted + ')')], 'sample': rows[:2]}
print(json.dumps(result, ensure_ascii=False, indent=2))
