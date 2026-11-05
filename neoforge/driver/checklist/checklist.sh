#!/bin/bash
# Runs the release checklist in the real client and on a dedicated server. Run it from the repository root.
# Needs ffmpeg and yt-dlp. It replaces neoforge/run, writes eula=true into that throwaway folder, and leaves
# its results in neoforge/run/checklist.txt. Set MONITOR to the name of a virtual display to use one.
set -u
PORT=${PORT:-25611}; WWW=${WWW:-51990}; MONITOR=${MONITOR:-}
C=neoforge/driver/checklist; RD=$(pwd)/neoforge/run; O=$RD/checklist.txt
L=$RD/driver/logs/latest.log; L2=$RD/driver2/logs/latest.log; A=$RD/checklist-audio; T=$RD/checklist-tmp
PATH=$PATH:/usr/sbin
cap() { local t=$1; shift; perl -e 'alarm shift; exec @ARGV' "$t" "$@"; }
rm -rf "${RD:?}"; mkdir -p "$RD/driver" "$RD/driver2" "$A" "$T"; : > "$O"
ffmpeg -v error -y -f lavfi -i "sine=frequency=330:duration=120" "$A/long.wav"
ffmpeg -v error -y -f lavfi -i "sine=frequency=440:duration=5" -c:a libvorbis "$A/short.ogg"
for n in n1 n2; do ffmpeg -v error -y -f lavfi -i "anoisesrc=d=60:c=pink" -ac 2 -c:a libvorbis -q:a 4 "$A/$n.ogg"; done
(cd "$A"; exec python3 -m http.server "$WWW" --bind 127.0.0.1 >/dev/null 2>&1) & HS=$!
trap 'kill $HS 2>/dev/null' EXIT
script() { sed -e "s/PORT/$PORT/g" -e "s/51990/$WWW/g" "$C/$1" > "$T/$1"; echo "$T/$1"; }
gradle() { local cache=$1 limit=$2; shift 2; cap "$limit" ./gradlew --no-daemon --console=plain --project-cache-dir "$RD/gradle-$cache" "$@" -q; }
client() { gradle client 480 :neoforge:runDriver -Pscript="$(script "$1")" ${MONITOR:+-Pmonitor="$MONITOR"} > "$RD/client-$1.log" 2>&1; }
filter() { grep -E '\[check\]|\[CHAT\] (Added|Couldn|That|Tracks|Unknown|  )|\[driver\] done|step failed' "$1" | sed -E 's/^.*(\[check\]|\[CHAT\]|\[driver\] done|step failed)/\1/' | cut -c1-120; }
errors() { grep -E '/ERROR\]' "$1" | grep -iE 'phonon|audio|openal|decod' | cut -c1-190 | head -8; }
server() {
  JAVA_TOOL_OPTIONS=-Djava.awt.headless=true gradle server 900 :neoforge:runServer > "$RD/server-$1.log" 2>&1 & G=$!
  for _ in $(seq 1 80); do sleep 3; lsof -nP -iTCP:"$PORT" -sTCP:LISTEN >/dev/null 2>&1 && break; done
  sleep 3; echo "server $1 up: $(grep -m1 -o 'Done ([^)]*)' "$RD/logs/latest.log")" >> "$O"
}
stopserver() {
  for _ in $(seq 1 30); do kill -0 "$G" 2>/dev/null || break; sleep 2; done
  lsof -nP -t -iTCP:"$PORT" -sTCP:LISTEN | xargs kill 2>/dev/null; sleep 3; wait "$G" 2>/dev/null
  errors "$RD/logs/latest.log" >> "$O"; grep -E 'Loaded [0-9]+ audio' "$RD/logs/latest.log" | cut -c30-110 >> "$O"
}

printf 'soundCategory_master:0.01\nonboardAccessibility:false\n' | tee "$RD/driver/options.txt" > "$RD/driver2/options.txt"
echo "=== singleplayer" >> "$O"; client check_sp.txt; filter "$L" >> "$O"; errors "$L" >> "$O"
echo "=== stations and boombox" >> "$O"
client stations.txt; filter "$L" >> "$O"; errors "$L" >> "$O"
echo "=== cache eviction (1 MB)" >> "$O"
perl -pi -e 's/^(\s*maxSizeMB\s*=).*/$1 1/' "$RD/driver/config/phonon-client.toml"; rm -rf "$RD/driver/phonon_cache"
client evict.txt; filter "$L" >> "$O"; errors "$L" >> "$O"
echo "cache: $(du -sk "$RD/driver/phonon_cache" | cut -f1) KB" >> "$O"
perl -pi -e 's/^(\s*maxSizeMB\s*=).*/$1 512/' "$RD/driver/config/phonon-client.toml"

echo 'eula=true' > "$RD/eula.txt"
printf "online-mode=false\ngamemode=creative\nforce-gamemode=true\nlevel-seed=12345\nspawn-protection=0\nallow-flight=true\nserver-port=%s\nspawn-monsters=false\nmax-tick-time=-1\n" "$PORT" > "$RD/server.properties"
echo '[{"uuid":"380df991-f603-344c-a090-369bad2a924a","name":"Dev","level":4,"bypassesPlayerLimit":false}]' > "$RD/ops.json"
for phase in 1 2; do
  echo "=== dedicated server, phase $phase" >> "$O"; server "$phase"; client "mp$phase.txt"; filter "$L" >> "$O"; errors "$L" >> "$O"; stopserver
done

echo "=== two clients" >> "$O"; rm -rf "${RD:?}/world"; server 3; rm -f "$L"
client s1.txt & C1=$!
for _ in $(seq 1 120); do sleep 2; grep -q s1_playing "$L" 2>/dev/null && break; done
gradle second 300 :neoforge:runDriver -Pscript="$(script s2.txt)" -Pgdir=run/driver2 -Puname=Dev2 ${MONITOR:+-Pmonitor="$MONITOR"} > "$RD/client-s2.log" 2>&1 & C2=$!
wait "$C1"; wait "$C2"
grep -E '\[check\] s1_(playing|pause|after|stopped) |\[driver\] done|step failed' "$L" | sed -E 's/^.*(\[check\]|\[driver\] done|step failed)/\1/' | cut -c1-110 >> "$O"; errors "$L" >> "$O"
errors "$L2" >> "$O"; grep -E 'step\(s\)' "$L2" | sed 's/^.*\[driver\]/client2 /' >> "$O"
python3 "$C/sync.py" "$L" "$L2" >> "$O" 2>&1; stopserver
echo "=== finished" >> "$O"; cat "$O"
