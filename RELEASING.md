# Release Management

Phonon versions the mod independently from Minecraft and identifies the exact
game target with SemVer build metadata. Release maturity records test evidence,
not how urgently a build is needed.

## Release identity

- Source version: `0.3.0-beta.1`
- Minecraft target: `26.2`
- Git tag: `v0.3.0-beta.1+mc26.2`
- Platform version: `0.3.0-beta.1+mc26.2`
- Published artifact: `phonon-neoforge-26.2-0.3.0-beta.1+mc26.2.jar`
- Local development artifact: `phonon-neoforge-26.2-0.3.0-beta.1.jar`

The pre-release identifier controls the release channel. The `+mc26.2` build
metadata makes releases for different game versions unique without changing
SemVer precedence. The release workflow supplies the platform version to
Gradle, which is why published and local artifact names differ.

## Promotion gates

| Stage | Minimum evidence | Version example | Platform channel |
| --- | --- | --- | --- |
| Beta | Clean build, automated tests, and the scripted checklist in the real client and on a dedicated server | `0.3.0-beta.1` | Beta |
| Stable | The functionally unchanged beta survives the recorded soak window without a release-blocking defect | `0.3.0` | Release |

New releases start at beta. Every Minecraft target needs its own scripted run:
evidence from one game version doesn't carry over to another.

The scripted checklist runs with the dev-only driver in `neoforge/driver`. It
checks playback state and game logs, so nobody hears the result. Release notes
say what was run, on which operating system, and what wasn't covered, including
listening by ear. A human tester is optional for beta. Stable requires the same
functional code as the tested beta, a named human tester who completed the
checklist by ear, at least one multi-hour real-server session, and an observation
window stated in the release notes. Any functional change invalidates prior
evidence and must be tested again.

## Choosing the next version

- Start every new feature line and every new Minecraft port at `beta.1`.
- Increment `beta.N` for each replacement artifact on that release branch. Two
  Minecraft targets may use the same `beta.N` because `+mc...` keeps their
  release identities distinct.
- Reset the counter at promotion: `beta.N` becomes the stable version with no
  pre-release suffix.
- After a stable release, compatible fixes and performance work begin a new
  patch line such as `0.3.1-beta.1`. New features or incompatible changes before
  1.0 begin a new minor line such as `0.4.0-beta.1`.
- Never overwrite an artifact, move a published tag, or reuse a published
  platform version.

This gives the normal progression `0.3.0-beta.1` -> `0.3.0-beta.2` -> `0.3.0`,
while a post-release fix starts at `0.3.1-beta.1`. Versions `0.3.0-alpha.1`
through `0.3.0-alpha.3` were published before alphas were retired.

## Branches

- `main` targets the newest supported Minecraft release.
- `release/minecraft-<version>` permanently tracks each older supported game
  release, for example `release/minecraft-1.21.1`.
- Fixes begin on the newest applicable branch and are backported deliberately.
  Do not claim one jar supports multiple Minecraft releases unless the same jar
  has actually passed the full test suite on every declared release.

Each release branch pins one exact Minecraft version in `gradle.properties` and
produces a version-qualified jar. The default branch must be merged normally;
release branches are not force-updated after publication.

## Promotion workflow

1. Update `version` in `gradle.properties` and the current entry in
   `CHANGELOG.md`.
2. Run `./gradlew clean build`.
3. Record the evidence listed below in the release notes, explicitly marking
   any check that wasn't run.
4. Commit the version change with a Conventional Commit.
5. Create an annotated tag, for example:

   ```bash
   git tag -a 'v0.3.0-beta.1+mc26.2' -m 'Phonon 0.3.0-beta.1 for Minecraft 26.2'
   ```

6. Push the branch before the tag. The release workflow validates that the tag,
   declared mod version, Java version, and Minecraft version agree.

Push release tags individually. GitHub does not create `push` events when more
than three tags are pushed at once. If an immutable tag already exists but its
workflow event was not created, or a platform upload needs to be retried, replay
that exact tag through the guarded manual entry point:

```bash
gh workflow run release.yml --ref main \
  -f 'release_tag=v0.3.0-beta.1+mc26.2'
```

The recovery workflow checks out the existing tag and applies the same version
and target validation as a tag push. Never delete or move the tag to retrigger a
release.

Configure the GitHub environments `publish-beta` and `publish-release`. Both
should require a maintainer approval. The workflow maps the channels explicitly,
marks beta GitHub releases as pre-releases, and emits a SHA-256 checksum.

## Required release evidence

Record these fields in every release entry:

- Phonon version, Minecraft version, NeoForge version, Java version, and commit.
- CI build/test result and artifact SHA-256.
- Dedicated-server startup result.
- Client startup and resource reload result.
- Scripted checklist date, operating system, and result, and any human tester.
- Soak duration for stable releases.
- Known issues and any deliberately untested paths.

## Checklist

- Start a dedicated server, join with a matching client, and verify a clean
  disconnect and reconnect.
- Load both a direct OGG URL and a yt-dlp-supported URL.
- Play one track through several speakers, then play different tracks
  concurrently.
- Exercise play, pause, resume, seek forward, seek backward, stop, and volume.
- Verify late join synchronization and automatic stop at the track duration.
- Reload client resources and confirm active speakers recover.
- Restart the integrated server and the dedicated server in the same process
  lifecycle where applicable.
- Confirm cache persistence and eviction with a deliberately small cache limit.
- Inspect client and server logs for decoder, packet-order, native-memory, and
  asynchronous I/O failures.

Record the tested Minecraft version, NeoForge version, Java runtime, operating
system, and result in the release notes.

## Store listings

`Sync Modrinth listing` publishes `README.md`, the project summary and
`docs/gallery.json` on relevant changes to `main`, or by manual dispatch. The
gallery manifest is authoritative: images absent from it are removed.
Non-alpha releases use the current listing source from `main`. CurseForge's
description and gallery are maintained through its author dashboard.
