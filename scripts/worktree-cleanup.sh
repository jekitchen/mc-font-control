#!/usr/bin/env bash
# Removes finished git worktrees under .claude/worktrees/ and keeps their build,
# test and measurement output in artifacts/<worktree>-<timestamp>/ (gitignored).
#
# Usage:
#   scripts/worktree-cleanup.sh             Sweep: remove every finished worktree.
#   scripts/worktree-cleanup.sh --dry-run   Show what a sweep would do.
#   scripts/worktree-cleanup.sh --hook      Claude Code WorktreeRemove hook: reads the
#                                           hook JSON on stdin and removes that worktree.
#
# A worktree is "finished" when it has no uncommitted changes and either
#   - its pull request is merged or closed, or
#   - its branch is already contained in origin/main and nobody has touched it for 24 hours.
# Uncommitted work is never deleted, and a branch with commits that are not on
# origin/main is never deleted (only its worktree folder, and only in --hook mode).

set -uo pipefail

MODE=sweep
case "${1:-}" in
	--dry-run) MODE=dry-run ;;
	--hook) MODE=hook ;;
	"") ;;
	*) echo "usage: $0 [--dry-run|--hook]" >&2; exit 2 ;;
esac

# Paths that are worth keeping after a worktree is gone.
KEEP=(build/reports build/test-results build/libs run/logs run/crash-reports run/screenshots measurements)
IDLE_MINUTES=1440

log() { echo "[worktree-cleanup] $*" >&2; }

common_dir=$(git rev-parse --path-format=absolute --git-common-dir 2>/dev/null) || { log "not in a git repository"; exit 0; }
ROOT=$(dirname "$common_dir")
WT_DIR="$ROOT/.claude/worktrees"
ART_DIR="$ROOT/artifacts"
cd "$ROOT" || exit 0

save_artifacts() {
	local wt=$1 name dest found=0 p
	name=$(basename "$wt")
	dest="$ART_DIR/$name-$(date +%Y%m%d-%H%M%S)"
	for p in "${KEEP[@]}"; do
		if [ -e "$wt/$p" ]; then
			mkdir -p "$dest/$(dirname "$p")"
			cp -R "$wt/$p" "$dest/$p"
			found=1
		fi
	done
	[ "$found" = 1 ] && log "saved artifacts from $name to ${dest#"$ROOT"/}"
	return 0
}

pr_state() {
	command -v gh >/dev/null 2>&1 || return 0
	gh pr list --head "$1" --state all --json state --jq '.[0].state // empty' 2>/dev/null
}

# True if every commit on the branch is already on origin/main.
contained_in_main() {
	git rev-parse --verify -q origin/main >/dev/null && git merge-base --is-ancestor "$1" origin/main
}

remove_worktree() {
	local wt=$1 branch=$2 state=$3 force=${4:-}
	save_artifacts "$wt"
	git worktree unlock "$wt" >/dev/null 2>&1
	if ! git worktree remove $force "$wt"; then
		log "could not remove $wt (files in use?); try again later"
		return 1
	fi
	log "removed worktree $(basename "$wt")"
	[ -z "$branch" ] && return 0
	if contained_in_main "$branch"; then
		git branch -d "$branch" >/dev/null 2>&1 && log "deleted branch $branch"
	elif [ "$state" = MERGED ]; then
		# Squash or rebase merge: the PR is merged even though the commits differ.
		git branch -D "$branch" >/dev/null 2>&1 && log "deleted branch $branch (PR merged)"
	else
		log "kept branch $branch: it has commits that are not on origin/main"
	fi
}

branch_of() { git -C "$1" symbolic-ref --short -q HEAD; }
is_clean() { [ -z "$(git -C "$1" status --porcelain 2>/dev/null)" ]; }

if [ "$MODE" = hook ]; then
	input=$(cat)
	wt=$(printf '%s' "$input" | sed -n 's/.*"worktree_path"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | tr -s '\\' '/')
	[ -z "$wt" ] && { log "no worktree_path in hook input"; exit 1; }
	[ -d "$wt" ] || exit 0
	branch=$(branch_of "$wt")
	if ! is_clean "$wt"; then
		log "keeping $wt: it has uncommitted changes. Commit them or remove the worktree by hand."
		exit 1
	fi
	remove_worktree "$wt" "$branch" "$( [ -n "$branch" ] && pr_state "$branch")" || exit 1
	git worktree prune
	exit 0
fi

[ -d "$WT_DIR" ] || exit 0
git fetch -q --prune origin 2>/dev/null || log "fetch failed; using local refs"
here=$(pwd -P)

for wt in "$WT_DIR"/*/; do
	[ -d "$wt" ] || continue
	wt=${wt%/}
	name=$(basename "$wt")
	git -C "$wt" rev-parse --git-dir >/dev/null 2>&1 || continue
	case "$here/" in "$(cd "$wt" && pwd -P)/"*) continue ;; esac
	if ! is_clean "$wt"; then
		log "skip $name: uncommitted changes"
		continue
	fi
	branch=$(branch_of "$wt")
	state=""
	[ -n "$branch" ] && state=$(pr_state "$branch")
	reason=""
	if [ "$state" = MERGED ] || [ "$state" = CLOSED ]; then
		reason="PR $state"
	elif git worktree list --porcelain | grep -A5 "^worktree $(cd "$wt" && pwd)\$" | grep -q '^locked'; then
		continue
	elif contained_in_main "${branch:-HEAD}" && [ -n "$(find "$(git -C "$wt" rev-parse --absolute-git-dir)/index" -mmin +$IDLE_MINUTES 2>/dev/null)" ]; then
		reason="already on origin/main and idle"
	fi
	[ -z "$reason" ] && continue
	if [ "$MODE" = dry-run ]; then
		log "would remove $name ($reason)"
	else
		log "$name: $reason"
		remove_worktree "$wt" "$branch" "$state"
	fi
done
[ "$MODE" = sweep ] && git worktree prune
exit 0
