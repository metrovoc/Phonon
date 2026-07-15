# Changelog

## 0.3.0-alpha.1

This is an alpha build. Automated tests and a dedicated-server startup smoke
test have passed, but each published Minecraft target still requires manual
in-game validation before promotion to beta.

### Performance

- Move OGG page indexing from whole-file reads to bounded sequential channel
  reads.
- Move server audio reads off the tick thread, keep file channels open per
  transfer, prefetch two chunks, and schedule players fairly under global and
  per-player byte budgets.
- Remove repeated packet splitting and full-buffer cache assembly from client
  streaming.
- Decode PCM directly into OpenAL-bound buffers and retain partial Vorbis frames
  without reallocating native scratch memory on every fill.
- Share compressed cached OGG bytes across speakers while keeping independent
  decoder state.
- Replace repeated collision ray clips with staggered block traversal for
  acoustic occlusion.
- Cache sorted audio metadata snapshots and normalized client search keys.

### Reliability and security

- Add explicit stream IDs, ordered chunks, cancellation, reference-counted
  shared downloads, and safe forward-only stream reuse.
- Use monotonic local playback clocks and latency-adjusted state snapshots
  instead of comparing wall clocks across machines.
- Validate speaker interaction distance, resource IDs, actions, seek bounds,
  volume, packet collection sizes, strings, headers, chunks, and sample rates.
- Validate OGG Vorbis files before registration and bound direct downloads and
  converted file sizes.
- Save metadata and cached streams through temporary files with atomic
  replacement where supported.
- Enforce the configured client cache limit and clean incomplete cache files.
- Reset server and client streaming state across disconnects, resource reloads,
  and repeated integrated-server lifecycles.

### Build and release management

- Target Minecraft 1.21.1 exactly with NeoForge 21.1.235, Java 21,
  ModDevGradle 2.0.141, and Gradle 9.2.1 for this branch.
- Remove unused Mixin declarations and replace the optional Cloth Config screen
  with a dependency-free vanilla screen.
- Add unit tests for OGG indexing, seek lookup, segmented buffering, playback
  clocks, metadata indexes, persistence, and stream sharing.
- Add explicit alpha, beta, and release channel mapping with version-qualified
  Minecraft tags and guarded GitHub publishing environments.
