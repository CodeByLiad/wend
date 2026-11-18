# Companion probe

- Primary target: `tools/companion`.
- Mode: Operate; Android phone, portrait and landscape.
- Authority: Phase 0 PRD explicitly requires a plain chat screen and excludes
  design work/polish. The owner chose encrypted local prototype-file storage
  on 2026-11-18. This is a research tool beside `tools/benchmark`, not an
  extension of the editorial Today design or a new visual identity.
- Composition: a scrollable Material page with labelled Chat / Saved facts /
  Model selection, a bounded scrollable transcript, labelled inputs and native
  action buttons. Dark/light follow the system; text follows system scaling.
- First viewport: the probe title, privacy/AI limitation, section selection and
  a truthful empty state. Without a model there is a direct Choose model action.
- State: no model, import/loading, chat, cancellation, failure, unreadable
  storage and destructive confirmation must all be explicit.
- Signature interaction: only explicit user Save fact, Remove fact and confirmed Delete
  chats and facts actions change the visible memory list. No automatic or inferred memory.
- Motion: native Material interaction feedback and transcript scroll only.
- Preserve `DESIGN.md` and Today styles. No custom visual assets, design
  tournament, AI imagery, new branding or polishing scope is authorized here.
