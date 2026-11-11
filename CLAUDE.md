# MC Font Control — notes for Claude

- **The PRD lives at `docs/PRD.md` and is the single source of truth** for requirements, scope and decisions. Read it before planning or writing code.
- When work changes or settles anything the PRD describes, update `docs/PRD.md` in the same change:
  - add or update the decision log (section 10),
  - bump the version in the header,
  - add a change-log line at the bottom.
- Changes go through a branch and pull request, not direct pushes to `main`.
- If the PRD and other notes (project instructions, Claude Docs copies) disagree, the PRD wins unless the owner says otherwise; point out the conflict.
- Licence: MIT. Never copy GPL code (e.g. CustomFontFix) into this repo.
