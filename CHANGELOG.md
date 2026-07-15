# Changelog

## 0.3.0-alpha.2

This Minecraft 26.2 replacement build contains no runtime changes. It corrects
the GitHub Actions tag filter so the validated artifact can be published. The
`v0.3.0-alpha.1+mc26.2` tag produced no GitHub, Modrinth, or CurseForge release
and remains reserved rather than being moved or reused. Manual gameplay testing
is still outstanding, so this build remains alpha.

## 0.3.0-alpha.1

This is an alpha build. Every target passes compilation and automated tests,
and selected targets pass server or client startup smoke tests, but each
published Minecraft target still requires manual in-game validation before
promotion to beta.

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

- Add exact-target release branches for Minecraft 1.21.1 through 1.21.11, 26.1,
  26.1.1, 26.1.2, and 26.2; `main` targets 26.2.
- Pin each branch to its matching NeoForge release. Minecraft 1.21.x uses Java
  21, while Minecraft 26.x uses Java 25.
- Remove unused Mixin declarations and replace the optional Cloth Config screen
  with a dependency-free vanilla screen.
- Add unit tests for OGG indexing, seek lookup, segmented buffering, playback
  clocks, metadata indexes, persistence, and stream sharing.
- Add explicit alpha, beta, and release channel mapping with version-qualified
  Minecraft tags and guarded GitHub publishing environments.
- Pass dedicated-server startup smoke tests on 1.21.1, 1.21.2, 1.21.4, 1.21.9,
  26.1, and 26.2, plus a 26.2 client resource and audio-engine startup smoke
  test. The full multiplayer and playback checklist remains untested.
