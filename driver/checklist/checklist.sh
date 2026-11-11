#!/bin/bash
# Runs the release checklist in the real client and on a dedicated server. Run it from the repository root.
# Needs ffmpeg and yt-dlp. MC picks the Stonecutter node's game version (default: the active one) and LOADER
# neoforge (default) or fabric. MODE=boot runs only the short boot test: one speaker in singleplayer, then the
# built jar on the loader's real dedicated server. It replaces versions/MC-LOADER/run, writes eula=true into the
# throwaway server folders, and leaves its results in versions/MC-LOADER/run/checklist.txt. Set MONITOR to the
# name of a virtual display to use one, JAR to test another jar, and JAVA to the server's Java. MODE=server
# only boots the jar on a production server of GAME, another release the node covers (default: MC).
set -u
PORT=${PORT:-25611}; WWW=${WWW:-51990}; MONITOR=${MONITOR:-}; LOADER=${LOADER:-neoforge}; MODE=${MODE:-full}
MC=${MC:-$(sed -nE 's/^stonecutter active "([^-]+)-.*/\1/p' stonecutter.gradle.kts)}; NODE=$MC-$LOADER; P=$(pwd)/versions/$NODE
# A property of this node from stonecutter.properties.toml: [loader."mc"], then ["mc"], then the top level.
prop() { python3 -c 'import sys,tomllib; t=tomllib.load(open("stonecutter.properties.toml","rb")); mc,l,k=sys.argv[1:]
for d in (t.get(l,{}).get(mc,{}), t.get(mc,{}), t):
    v=d
    for part in k.split("."): v=v.get(part,{}) if isinstance(v,dict) else {}
    if v!={}: print(v if not isinstance(v,list) else " ".join(v)); break' "${GAME:-$MC}" "$LOADER" "$1"; }
JV=$([ "${MC%%.*}" -ge 26 ] && echo 25 || echo 21)
JAVA=${JAVA:-$(ls -d ~/.gradle/jdks/*-"$JV"-*/jdk*/Contents/Home/bin/java 2>/dev/null | head -1)}
[ -n "$JAVA" ] || { JAVA=$(/usr/libexec/java_home -v "$JV" 2>/dev/null)/bin/java; [ -x "$JAVA" ] || JAVA=java; }
JAR=${JAR:-$(ls "$P"/build/libs/phonon-*.jar 2>/dev/null | grep -vE -- '-(sources|javadoc)\.jar$' | head -1)}
C=driver/checklist; RD=$P/run; O=$RD/checklist.txt
L=$RD/driver/logs/latest.log; L2=$RD/driver2/logs/latest.log; A=$RD/checklist-audio; T=$RD/checklist-tmp
PATH=$PATH:/usr/sbin
[ -f "${JAR:-}" ] || { echo "No built jar for $NODE; run ./gradlew :$NODE:build first, or set JAR"; exit 1; }
cap() { local t=$1; shift; perl -e 'alarm shift; exec @ARGV' "$t" "$@"; }
if [ "$MODE" = server ]; then  # keeps the node's other results
  O=$RD/prodserver-${GAME:-$MC}.txt; mkdir -p "$RD"; : > "$O"
else
  rm -rf "${RD:?}"; mkdir -p "$RD/driver" "$RD/driver2" "$A" "$T"; : > "$O"
  ffmpeg -v error -y -f lavfi -i "sine=frequency=330:duration=120" "$A/long.wav"
  ffmpeg -v error -y -f lavfi -i "sine=frequency=440:duration=5" -c:a libvorbis "$A/short.ogg"
  for n in n1 n2; do ffmpeg -v error -y -f lavfi -i "anoisesrc=d=60:c=pink" -ac 2 -c:a libvorbis -q:a 4 "$A/$n.ogg"; done
  (cd "$A"; exec python3 -m http.server "$WWW" --bind 127.0.0.1 >/dev/null 2>&1) & HS=$!
  trap 'kill $HS 2>/dev/null' EXIT
fi
script() { sed -e "s/PORT/$PORT/g" -e "s/51990/$WWW/g" "$C/$1" > "$T/$1"; echo "$T/$1"; }
gradle() { local cache=$1 limit=$2; shift 2; cap "$limit" ./gradlew --no-daemon --console=plain --project-cache-dir "$RD/gradle-$cache" "$@" -q; }
client() {
  gradle client 480 :$NODE:runDriver -Pscript="$(script "$1")" ${MONITOR:+-Pmonitor="$MONITOR"} > "$RD/client-$1.log" 2>&1; local e=$?
  grep -q '\[driver\] done' "$L" 2>/dev/null || echo "FAILED: client $1 never finished (exit $e, see client-$1.log)" >> "$O"
  ls "$RD/driver/crash-reports" 2>/dev/null | sed 's/^/crash report: /' >> "$O"
}
filter() { grep -E '\[check\]|\[CHAT\] (Added|Couldn|That|Tracks|Unknown|Unlocked|No recipe|  )|names in phonon|\[driver\] done|step failed' "$1" | sed -E 's/^.*(\[check\]|\[CHAT\]|\[driver\] done|\[driver\] [0-9]+ names|step failed)/\1/' | cut -c1-120; }
errors() { grep -E '/ERROR\]' "$1" | grep -iE 'phonon|audio|openal|decod' | cut -c1-190 | head -8; }
warnings() { grep -E '\/WARN\]' "$1" | grep -i phonon | grep -v 'FMLConfig' | cut -c1-190 | head -8; }
prodserver() {
  local mc=${GAME:-$MC} S start
  if [ "$LOADER" = fabric ]; then
    local fl fa inst; fl=$(prop deps.fabric_loader); fa=$(prop deps.fabric_api)
    S=$P/build/prodserver/$mc-$fl; mkdir -p "$S/mods"
    if [ ! -f "$S/fabric-server-launch.jar" ]; then
      inst=$(curl -fsS https://meta.fabricmc.net/v2/versions/installer | python3 -c 'import json,sys; print(next(i["version"] for i in json.load(sys.stdin) if i["stable"]))')
      curl -fsSLo "$S/launch.part" "https://meta.fabricmc.net/v2/versions/loader/$mc/$fl/$inst/server/jar" \
        && mv "$S/launch.part" "$S/fabric-server-launch.jar" || echo "FAILED: Fabric server launcher download (installer '$inst')" >> "$O"
    fi
    rm -f "$S"/mods/*.jar
    curl -fsSLo "$S/mods/fabric-api-$fa.jar" "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$fa/fabric-api-$fa.jar" \
      || echo "FAILED: Fabric API $fa download" >> "$O"
    start=("$JAVA" -Xmx2G -jar fabric-server-launch.jar nogui)
  else
    local v; v=$(prop deps.neoforge); S=$P/build/prodserver/$v; mkdir -p "$S/mods"
    if [ ! -f "$S/libraries/net/neoforged/neoforge/$v/unix_args.txt" ]; then
      curl -fsSLo "$S/installer.jar" "https://maven.neoforged.net/releases/net/neoforged/neoforge/$v/neoforge-$v-installer.jar"
      (cd "$S" && "$JAVA" -jar installer.jar --install-server . > install.log 2>&1)
      [ -f "$S/libraries/net/neoforged/neoforge/$v/unix_args.txt" ] || { echo "FAILED: NeoForge $v server install"; tail -5 "$S/install.log"; } >> "$O"
    fi
    rm -f "$S"/mods/*.jar; start=("$JAVA" -Xmx2G "@libraries/net/neoforged/neoforge/$v/unix_args.txt" nogui)
  fi
  cp "$JAR" "$S/mods/"; rm -rf "$S/world" "$S/logs" "$S/crash-reports"; echo 'eula=true' > "$S/eula.txt"
  printf "online-mode=false\nwhite-list=false\nserver-port=%s\nlevel-seed=12345\n" "$PORT" > "$S/server.properties"
  echo "=== production dedicated server, Minecraft $mc ($(basename "$JAR"))" >> "$O"
  (cd "$S" && { for _ in $(seq 1 150); do sleep 2; grep -q 'Done (' logs/latest.log 2>/dev/null && break; done
    echo 'phonon reload'; sleep 8; echo stop; } | cap 400 "${start[@]}" > "$RD/prodserver.log" 2>&1)
  echo "exit $?, $(grep -m1 -o 'Done ([^)]*)' "$S/logs/latest.log" 2>/dev/null || echo 'never finished starting')" >> "$O"
  grep -E "Loaded [0-9]+ audio|Reloaded the config|Couldn't reload|Unknown or incomplete command|phonon\\.reload\\.|Stopping the server|Exception|Crash report|crashed" "$S/logs/latest.log" 2>/dev/null | cut -c1-160 | head -8 >> "$O"
  grep -q 'Reloaded the config' "$S/logs/latest.log" 2>/dev/null || echo 'FAILED: /phonon reload never replied' >> "$O"
  errors "$S/logs/latest.log" >> "$O"; warnings "$S/logs/latest.log" >> "$O"
  ls "$S/crash-reports" 2>/dev/null | sed 's/^/crash report: /' >> "$O"
}
server() {
  JAVA_TOOL_OPTIONS=-Djava.awt.headless=true gradle server 900 :$NODE:runServer > "$RD/server-$1.log" 2>&1 & G=$!
  for _ in $(seq 1 80); do sleep 3; lsof -nP -iTCP:"$PORT" -sTCP:LISTEN >/dev/null 2>&1 && break; done
  sleep 3; echo "server $1 up: $(grep -m1 -o 'Done ([^)]*)' "$RD/logs/latest.log")" >> "$O"
}
stopserver() {
  for _ in $(seq 1 30); do kill -0 "$G" 2>/dev/null || break; sleep 2; done
  lsof -nP -t -iTCP:"$PORT" -sTCP:LISTEN | xargs kill 2>/dev/null; sleep 3; wait "$G" 2>/dev/null
  errors "$RD/logs/latest.log" >> "$O"; grep -E 'Loaded [0-9]+ audio' "$RD/logs/latest.log" | cut -c30-110 >> "$O"
}

if [ "$MODE" = server ]; then
  prodserver; echo "=== finished" >> "$O"; cat "$O"; exit
fi

# From 26.3 the game draws through SDL, whose buffer swap waits for a vsync that never comes while the window is
# hidden; so test clients run without vsync, and NeoForge without its separate early loading window.
printf 'soundCategory_master:0.01\nonboardAccessibility:false\nenableVsync:false\n' | tee "$RD/driver/options.txt" > "$RD/driver2/options.txt"
mkdir -p "$RD/driver/config" "$RD/driver2/config"
echo 'earlyWindowControl = false' | tee "$RD/driver/config/fml.toml" > "$RD/driver2/config/fml.toml"
if [ "$MODE" = boot ]; then
  echo "=== boot test" >> "$O"; client boot.txt; filter "$L" >> "$O"; errors "$L" >> "$O"; warnings "$L" >> "$O"
  prodserver; echo "=== finished" >> "$O"; cat "$O"; exit
fi
echo "=== singleplayer" >> "$O"; client check_sp.txt; filter "$L" >> "$O"; errors "$L" >> "$O"
echo "=== stations and boombox" >> "$O"
client stations.txt; filter "$L" >> "$O"; errors "$L" >> "$O"
echo "=== cache eviction (1 MB)" >> "$O"
cachesize() { perl -pi -e "s/(\"?maxSizeMB\"?\\s*[=:]\\s*)\\d+/\${1}$1/" "$RD"/driver/config/phonon-client.*; }
cachesize 1; rm -rf "$RD/driver/phonon_cache"
client evict.txt; filter "$L" >> "$O"; errors "$L" >> "$O"
echo "cache: $(du -sk "$RD/driver/phonon_cache" | cut -f1) KB" >> "$O"
cachesize 512

echo 'eula=true' > "$RD/eula.txt"
printf "online-mode=false\nwhite-list=false\ngamemode=creative\nforce-gamemode=true\nlevel-seed=12345\nspawn-protection=0\nallow-flight=true\nserver-port=%s\nspawn-monsters=false\nmax-tick-time=-1\n" "$PORT" > "$RD/server.properties"
echo '[{"uuid":"380df991-f603-344c-a090-369bad2a924a","name":"Dev","level":4,"bypassesPlayerLimit":false}]' > "$RD/ops.json"
for phase in 1 2; do
  echo "=== dedicated server, phase $phase" >> "$O"; server "$phase"; client "mp$phase.txt"; filter "$L" >> "$O"; errors "$L" >> "$O"; stopserver
done

echo "=== two clients" >> "$O"; rm -rf "${RD:?}/world"; server 3; rm -f "$L"
client s1.txt & C1=$!
for _ in $(seq 1 120); do sleep 2; grep -q s1_playing "$L" 2>/dev/null && break; done
gradle second 300 :$NODE:runDriver -Pscript="$(script s2.txt)" -Pgdir=run/driver2 -Puname=Dev2 ${MONITOR:+-Pmonitor="$MONITOR"} > "$RD/client-s2.log" 2>&1 & C2=$!
wait "$C1"; wait "$C2"
grep -E '\[check\] s1_(playing|pause|after|stopped) |\[driver\] done|step failed' "$L" | sed -E 's/^.*(\[check\]|\[driver\] done|step failed)/\1/' | cut -c1-110 >> "$O"; errors "$L" >> "$O"
errors "$L2" >> "$O"; grep -E 'step\(s\)' "$L2" | sed 's/^.*\[driver\]/client2 /' >> "$O"
grep -E 'step failed' "$L2" | sed 's/^.*\[driver\] /FAILED: client2 /' | cut -c1-190 >> "$O"
python3 "$C/sync.py" "$L" "$L2" >> "$O" 2>&1; stopserver
prodserver
echo "=== finished" >> "$O"; cat "$O"
