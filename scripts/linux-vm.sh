#!/usr/bin/env bash
# Linux test VM for Fontsys (PRD D27), driven by Canonical Multipass.
# Works the same from Git Bash on Windows (Hyper-V) and from macOS (Apple Virtualization).
#
#   scripts/linux-vm.sh up       create the VM if needed (first boot installs Java, Mesa, fonts), and start it
#   scripts/linux-vm.sh test     copy this working tree in, run the build, unit tests and both
#                                client game-test runs (vanilla, Sodium + Iris) and the NeoForge
#                                smoke test, fetch the results
#   scripts/linux-vm.sh sync     copy this working tree into the VM only
#   scripts/linux-vm.sh fetch    copy the last results out of the VM
#   scripts/linux-vm.sh shell    open a shell in the VM
#   scripts/linux-vm.sh status   show the VM
#   scripts/linux-vm.sh stop     shut the VM down (keeps its disk and Gradle caches)
#   scripts/linux-vm.sh down     delete the VM completely
#
# Results land in build/reports/linux-vm/<timestamp>/ (kept by the worktree cleanup).
# Settings: FONTSYS_VM (name), FONTSYS_VM_IMAGE, FONTSYS_VM_CPUS, FONTSYS_VM_MEMORY, FONTSYS_VM_DISK.
set -euo pipefail

VM="${FONTSYS_VM:-fontsys-linux}"
IMAGE="${FONTSYS_VM_IMAGE:-26.04}"
CPUS="${FONTSYS_VM_CPUS:-8}"
MEMORY="${FONTSYS_VM_MEMORY:-12G}"
DISK="${FONTSYS_VM_DISK:-40G}"
GUEST_DIR=/home/ubuntu/fontsys

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
# Git Bash would rewrite guest paths such as /home/ubuntu into Windows paths.
export MSYS_NO_PATHCONV=1

MP="$(command -v multipass || true)"
if [ -z "$MP" ] && [ -x "/c/Program Files/Multipass/bin/multipass.exe" ]; then
	MP="/c/Program Files/Multipass/bin/multipass.exe"
fi
[ -n "$MP" ] || { echo "Multipass is not installed: https://canonical.com/multipass/install" >&2; exit 1; }
mp() { "$MP" "$@"; }
guest() { mp exec "$VM" -- bash -lc "$1"; }

state() { mp info "$VM" --format csv 2>/dev/null | awk -F, 'NR==2 {print $2}'; }

# macOS has shasum, not sha256sum.
sha256() { if command -v sha256sum >/dev/null; then sha256sum "$1"; else shasum -a 256 "$1"; fi | cut -c1-64; }

up() {
	case "$(state)" in
		"")
			echo "Creating $VM (Ubuntu $IMAGE, $CPUS CPUs, $MEMORY RAM, $DISK disk). First boot installs packages; this takes several minutes."
			mp launch "$IMAGE" --name "$VM" --cpus "$CPUS" --memory "$MEMORY" --disk "$DISK" \
				--cloud-init scripts/linux-vm/cloud-init.yaml --timeout 1800
			;;
		Running) ;;
		*) mp start "$VM" ;;
	esac
	guest "cloud-init status --wait >/dev/null; test -f /var/lib/fontsys-vm-ready" \
		|| { echo "First-boot setup failed; see: $0 shell, then: sudo cat /var/log/cloud-init-output.log" >&2; exit 1; }
	guest "java -version 2>&1 | head -1; fc-list | wc -l | xargs echo font faces:"
}

sync() {
	local tarball=build/linux-vm/src.tgz
	mkdir -p build/linux-vm
	# Tracked and new (not ignored) files, so uncommitted work is tested too.
	git ls-files -z --cached --others --exclude-standard | tar --null -czf "$tarball" -T -
	mp transfer "$tarball" "$VM:/home/ubuntu/src.tgz"
	# Keep the VM's Gradle and Loom caches between runs; replace everything else.
	guest "mkdir -p $GUEST_DIR && cd $GUEST_DIR \
		&& find . -mindepth 1 -maxdepth 1 ! -name .gradle ! -name build -exec rm -rf {} + \
		&& tar -xzf ~/src.tgz && rm ~/src.tgz && chmod +x gradlew"
	echo "Synced $(git rev-parse --short HEAD) plus local changes into $VM:$GUEST_DIR"
}

run_tests() {
	local result=0
	guest "bash $GUEST_DIR/scripts/linux-vm/run-tests.sh" || result=$?
	fetch
	return $result
}

fetch() {
	local stamp out
	stamp="$(date +%Y%m%d-%H%M%S)"
	out="build/reports/linux-vm/$stamp"
	mkdir -p "$out"
	# On Windows, `multipass transfer` to a file leaves it without delete permission, and
	# `multipass exec` never delivers the end of a large output. Transfer to stdout works, but
	# stdout there turns LF into CRLF, so the archive travels as base64 and is checksummed.
	local sum
	sum="$(guest "cd ~ && tar -czf results.tgz linux-vm-results && base64 results.tgz > results.b64 && sha256sum results.tgz | cut -c1-64")"
	mp transfer "$VM:/home/ubuntu/results.b64" - | tr -d '\r' | base64 -d > "$out/results.tgz"
	if [ "$(sha256 "$out/results.tgz")" != "$(echo "$sum" | tr -d '\r')" ]; then
		echo "Results archive arrived damaged: $out/results.tgz" >&2
		return 1
	fi
	tar -xzf "$out/results.tgz" -C "$out" --strip-components=1 && rm "$out/results.tgz"
	guest "rm -f ~/results.tgz ~/results.b64"
	echo "Results: $out"
}

case "${1:-}" in
	up) up ;;
	sync) sync ;;
	test) up && sync && run_tests ;;
	fetch) fetch ;;
	shell) mp shell "$VM" ;;
	status) mp info "$VM" ;;
	stop) mp stop "$VM" ;;
	down) mp delete --purge "$VM" ;;
	*) sed -n '2,17p' "$0"; exit 2 ;;
esac
