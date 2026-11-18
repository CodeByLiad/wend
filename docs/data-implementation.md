# Production data layer

`:core:data` is the production Android Room/SQLCipher boundary. It adds no
networking dependency and leaves the pure engine unchanged. This is the first
production foundation; the app shell is not yet migrated in this change.

## Stored data

Schema 1 has days, ordered planned blocks, ChangeLog and versioned preferences.
Every row has a UUID and created/updated timestamps. Scheduling fields round-trip
through the spec's engine types, including overnight minutes and relative or
open window bounds. Snapshot JSON lives inside SQLCipher, not plaintext files.
Room schemas are committed; no destructive migration fallback is enabled.

The database sits in app-private no-backup storage. A random 32-byte database
key is wrapped using AES-256-GCM and an Android Keystore wrapping key. The
wrapped key is written atomically. An existing database with a missing or
unreadable key is not silently assigned a replacement.

Key loading/native opening is deferred to the first DAO connection, so Hilt
injection does not decrypt/open the database. The SQLCipher helper forwards
Room migration/lifecycle callbacks but installs an explicit non-destructive
corruption handler. Wrong keys/corrupt data must fail without deleting the file.
The vendor Java logger is disabled, and queries do not interpolate user text.

## Schedule writes

Only repositories can reach the internal DAO/entities. Create initially stores
an empty day; activities enter through engine AddBlock proposals.

`preview` loads the current Day and revision and calls the engine.
`apply` reloads inside a transaction, rejects stale revision/snapshot pairs
and recomputes the original command. It accepts OK or explicitly reviewed
INFEASIBLE attempts; REJECTED/CONFLICT/NO_HORIZON attempts are returned without
writing. The preview capsule has no public constructor or mutable copy API.

A save updates day/blocks and appends exact before/after snapshots, engine change
types/reasons/causes, operation, source and timestamps in one transaction.
No-op comparisons use the complete Day, so wake/bedtime-only edits are retained.
Unchanged block metadata is preserved.

Start/Done are validated status-only actions (owner delegated this choice on
2026-11-18), with no interval, duration, kind, window, priority or dependency edits.
Only one activity can be in progress. Dormant/skipped items require the engine's
retry path, not a status shortcut. Daily rollover marks unfinished past-day
items MISSED and is idempotent; planned times stay unchanged.

Undo fetches the last active undoable record and passes its exact previous Day
to the engine's Undo command. It writes its own ChangeLog record and marks the
original undone in the same transaction. Repeated undo walks persisted history,
including after restart. Arbitrary caller-supplied Undo snapshots are not a
public preview operation.

## Settings

Defaults include system theme/time format, balanced wording, approval before
rebuild changes, 07:00 wake, 23:00 bedtime, 22:00–07:00 quiet hours, Monday week
start and two reminder repeats ten minutes apart. These are implementation
defaults, not measured user preferences. Settings and Today card order persist
in the encrypted database. A global default-time edit does not rewrite today's
schedule; a current-day change must use an engine command.

## Dependency references

Versions were checked against primary sources on 2026-11-18:

- [Room stable/API setup](https://developer.android.com/jetpack/androidx/releases/room):
  Room 2.8.5, using its Room 2 API for SQLCipher integration.
- [SQLCipher Android integration](https://github.com/sqlcipher/sqlcipher-android):
  community artifact 4.20.0; explicit helper/corruption handling is used here.
- [Hilt KSP setup](https://dagger.dev/hilt/gradle-setup): Hilt 2.60.1.
- [KSP releases](https://github.com/google/ksp/releases): KSP 2.3.14.
- [SQLite helper configuration](https://developer.android.com/reference/kotlin/androidx/sqlite/db/SupportSQLiteOpenHelper.Configuration):
  never allow data loss on recovery.

## Verification contract

Unit tests check that status actions change no scheduling fields and validate
settings. Emulator instrumentation uses actual Room, SQLCipher and Keystore:
exact reopen/history/codec behavior, ciphertext inspection, stale approvals,
persistent repeated undo, transaction rollback, wrong/missing keys, lazy setup,
settings/card order and status-only rollover. These are software storage tests,
not phone power/performance or reminder reliability measurements.
