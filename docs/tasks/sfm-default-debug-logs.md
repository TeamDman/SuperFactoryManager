# Persistent CLI diagnostics

Ordinary parsed SFM commands allocate a DEBUG NDJSON run file under the existing
application state home: `$SFM_PROPAGATE_CHANGES_HOME/logs`, or the platform SFM
configuration home’s `logs` directory when that override is absent. The terminal
keeps its existing INFO default. Existing `--debug`, `--log-filter` and `RUST_LOG`
precedence is retained. Explicit `--log-file` keeps its selected location and
existing INFO default unless a level is selected.

Each run writes directly to a unique UTC timestamp/PID/random `run-*.ndjson`.
Historical files are never renamed or pruned. `latest.json` is a small discovery
pointer, not NDJSON: its `file` field names the most recently allocated run.
Concurrent starts may select either run as latest; every run retains its own
stable path. For example, join `logs` with `jq -r .file logs/latest.json`, then
use `tail -f` or parse each line as JSON. Reading latest while another command
starts returns either complete pointer. A pointer does not imply the command
finished successfully.

An explicit existing directory receives the same unique files and latest
pointer. An explicit filename is appended, never truncated. Recognized SFM latest-pointer
metadata is rejected as an explicit destination so it cannot lose discovery or
be renamed while serving as an active log. Shared filename
writers serialize complete records with bounded cooperative OS locks. Every writer uses thread serialization and the same bounded OS record lock,
including an explicit writer joining an active automatic run file.
No environment dump or unredacted startup argument event is introduced. Existing
startup argument redaction and JSON formatting remain in use. Returned command
errors add a generic failure marker; detailed error bodies remain on stderr.
Typed stdout and the existing terminal/progress logger remain separate.

Run-file creation, pointer initialization and subscriber initialization errors
stop the command before command work. For cooperating SFM writers, unknown existing pointer data, links and
nonregular pointer/lock paths fail without replacement. The discovery lock does
not exclude a same-account program that bypasses this protocol; such a program
can race a pointer update. Run files are never renamed by discovery updates.
The application-home path must be trusted. New automatic Unix log directories
are created as 0700; existing directories must be user-owned without group/world
write permission, and unsafe permissions are rejected without modification.
Windows inherits the application-home ACL; its permission/locking behavior has
not yet been qualified. A failed initialization
may leave an allocated empty run and pointer staging metadata; these are retained.
Runtime formatter/write failures continue to use tracing’s stderr diagnostics.
Writes are synchronous to the OS; there is no per-event fsync or promise of
power-loss durability. Normal errors and cancellation retain prior records;
panic/forced exit may leave the final record incomplete. Explicit append keeps
those bytes and adds a newline under the record lock before starting new events;
readers may still need to skip the preserved invalid historical line. There is no retention
limit or automatic cleanup.

Help/version, parse failures and internal workers that return before logging
initialization do not allocate a run log. Candidate-only `source legacy
frozen-preset-stage` suppresses the default file destination to preserve its
existing pre-admission no-write boundary; global `--log-file` remains rejected
there before initialization. These exceptions do not log raw unparsed argv.
