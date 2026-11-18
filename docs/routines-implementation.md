# Saved routines and first-day generation

The production data layer now stores Routine and ActivityTemplate records in the
encrypted database. This is the F3 foundation, not a claim that the activity
editor, morning background job or onboarding UI is finished.

## Templates and recurrence

A routine has a UUID, name, day-type label, enabled flag, recurrence and ordered
activities. Each activity has its own UUID, category, icon key and complete
engine Block template. Template status is always PLANNED. UUIDs, nonblank display
fields, durations, windows and the complete dependency graph are validated.
A template belongs to one routine and is reused across its dated instances;
copying it to another routine requires a new UUID.

Daily, selected weekdays, every N days and monthly date rules are supported.
Implementation choices: every-N uses an explicit anchor and starts there (never
before it); a monthly date absent from a short month is skipped, not moved to the
last day. Matching returns all enabled matches, optionally filtered by day type;
the caller must choose if several match. No routine wins silently. An explicitly
selected routine can be used for a one-off date outside its recurrence.

Save/delete use revisions, so an old editor cannot overwrite newer changes.
Template replacement is transactional and preserves unchanged row metadata.
Routine edits and deletion leave existing dated plans untouched.

## Generation and history

A preview captures the saved routine revision, date, bounds and now parameter.
It materializes fresh UUIDs for the day and remaps every dependency. The pure
engine performs the rebuild. Applying rechecks the routine and recomputes the
engine result inside a transaction; caller-edited proposal fields are not trusted.
Only OK or explicitly accepted INFEASIBLE previews can create a day. Other
statuses write nothing. Existing dates always return their saved day unchanged,
even if another preview, routine edit or generation attempt follows.

Initial GENERATE_DAY history has no previous stored day, exact engine output and
its change metadata; it is not undoable into a nonexistent day. Later edits use
the normal persistent undo history. Instance category/icon/origin records retain
the template/routine UUID and their original metadata even after template edits
or routine deletion, so future apply-to-routine UI has an explicit origin.
They are linked to the day rather than cascading from block replacement, which
keeps metadata available when engine undo restores a removed block.

## Storage and verification

Room schema 2 adds routines, templates and instance origins. Schema 1 remains
committed; Room generates a non-destructive automatic migration. An emulator test
creates a real SQLCipher schema-1 file from the committed schema, inserts an old
day, preference and ChangeLog, then opens it through the production factory and
verifies schema 2 and the exact old data. See the
[official migration guide](https://developer.android.com/training/data-storage/room/migrating-db-versions).

Recurrence and validation unit tests cover calendar rules, anchors, short months,
invalid inputs, graph validation and dependency remapping. Actual encrypted
instrumentation covers reopen/Flow, matching, stale saves and previews, separate
instance IDs, idempotent creation, one-off isolation, routine deletion, rejection
without partial days and cross-routine template ownership.
