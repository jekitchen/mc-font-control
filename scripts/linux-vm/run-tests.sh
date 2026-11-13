#!/usr/bin/env bash
# Runs inside the Linux test VM (see scripts/linux-vm.sh): the build with unit tests, then the
# client game tests twice (vanilla, Sodium + Iris) and the NeoForge smoke test, headless with the
# same display setup as CI.
# Everything worth keeping is collected in ~/linux-vm-results/ (outside build/, which `clean` deletes).
set -uo pipefail
cd "$(dirname "$0")/../.."
R="$HOME/linux-vm-results"
rm -rf "$R" && mkdir -p "$R"

{ lsb_release -ds; uname -srm; java -version 2>&1 | head -1; } > "$R/system.txt"
fc-list : family | sort -u > "$R/fc-families.txt"

status=0
./gradlew clean build --console=plain > "$R/build.log" 2>&1 || status=1
cp -r build/test-results build/reports "$R/" 2>/dev/null

for mods in vanilla sodium-iris; do
	flag=()
	[ "$mods" = sodium-iris ] && flag=(-PwithSodiumIris)
	# 26.3 asks SDL for an sRGB-capable OpenGL framebuffer; Xvfb's GLX has none, EGL does.
	SDL_VIDEO_FORCE_EGL=1 timeout 30m xvfb-run -a -s "-screen 0 1920x1080x24" \
		./gradlew runClientGameTest "${flag[@]}" --console=plain > "$R/gametest-$mods.log" 2>&1 || status=1
	out="$R/gametest-$mods"
	mkdir -p "$out/config"
	for d in screenshots logs crash-reports; do
		[ -e "build/run/clientGameTest/$d" ] && cp -r "build/run/clientGameTest/$d" "$out/"
	done
	[ -e build/run/clientGameTest/config/fontsys ] && cp -r build/run/clientGameTest/config/fontsys "$out/config/"
	[ -e build/reports/measurements ] && cp -r build/reports/measurements "$out/"
done

# NeoForge build (PRD D51): boots the NeoForge client, applies a font, checks it and quits.
SDL_VIDEO_FORCE_EGL=1 timeout 30m xvfb-run -a -s "-screen 0 1920x1080x24" \
	./gradlew :neoforge:neoforgeSmokeTest --console=plain > "$R/neoforge-smoketest.log" 2>&1 || status=1
out="$R/neoforge-smoketest"
mkdir -p "$out"
for d in screenshots logs crash-reports fontsys-smoketest.txt; do
	[ -e "neoforge/build/run/smokeTest/$d" ] && cp -r "neoforge/build/run/smokeTest/$d" "$out/"
done

for log in "$R/build.log" "$R"/gametest-*.log "$R/neoforge-smoketest.log"; do
	printf '%-28s %s\n' "$(basename "$log")" "$(grep -h -oE 'BUILD (SUCCESSFUL|FAILED)( in .*)?' "$log" | tail -1)"
done
exit $status
