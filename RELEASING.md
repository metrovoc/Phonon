# Release Management

Phonon versions the mod independently from Minecraft and identifies the exact
game target with SemVer build metadata. Release maturity records test evidence,
not how urgently a build is needed.

## Release identity

- Source version: `0.3.0-alpha.1`
- Minecraft target: `26.2`
- Git tag: `v0.3.0-alpha.1+mc26.2`
- Platform version: `0.3.0-alpha.1+mc26.2`
- Published artifact: `phonon-neoforge-26.2-0.3.0-alpha.1+mc26.2.jar`
- Local development artifact: `phonon-neoforge-26.2-0.3.0-alpha.1.jar`

The pre-release identifier controls the release channel. The `+mc26.2` build
metadata makes releases for different game versions unique without changing
SemVer precedence. The release workflow supplies the platform version to
Gradle, which is why published and local artifact names differ.

## Promotion gates

| Stage | Minimum evidence | Version example | Platform channel |
| --- | --- | --- | --- |
| Alpha | Clean build and automated tests | `0.3.0-alpha.1` | Alpha |
| Beta | Full manual checklist on a matching client and dedicated server | `0.3.0-beta.1` | Beta |
| Stable | The functionally unchanged beta survives the recorded soak window without a release-blocking defect | `0.3.0` | Release |

Every directly published, newly ported, or otherwise insufficiently tested
Minecraft target must remain alpha, even when the same code was tested on a
different Minecraft release. A pre-release NeoForge dependency also keeps the
corresponding Phonon build in alpha unless that exact toolchain receives
explicit project-level validation.

Beta requires a named human tester, the exact commit and dependency versions,
and a recorded result for every checklist item. Stable requires the same
functional code as the tested beta, at least one multi-hour real-server session,
and an observation window stated in the release notes. Any functional change
invalidates prior evidence and must be tested again.

## Choosing the next version

- Start every new feature line and every new Minecraft port at `alpha.1`.
- Increment `alpha.N` for each replacement alpha artifact on that release
  branch. Two Minecraft targets may use the same `alpha.N` because `+mc...`
  keeps their release identities distinct.
- Reset the counter at promotion: `alpha.N` becomes `beta.1`; `beta.N` becomes
  the stable version with no pre-release suffix.
- Increment `beta.N` only after the replacement candidate completes the manual
  checklist. Keep untested beta fixes as unpublished CI artifacts.
- After a stable release, compatible fixes and performance work begin a new
  patch line such as `0.3.1-alpha.1`. New features or incompatible changes
  before 1.0 begin a new minor line such as `0.4.0-alpha.1`.
- Never overwrite an artifact, move a published tag, or reuse a published
  platform version.

This gives the normal progression
`0.3.0-alpha.1` -> `0.3.0-alpha.2` -> `0.3.0-beta.1` -> `0.3.0`, while a
post-release fix starts at `0.3.1-alpha.1`.

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
3. Record the evidence listed below in the release notes. Alpha entries must
   explicitly mark all manual checks as not run when applicable.
4. Commit the version change with a Conventional Commit.
5. Create an annotated tag, for example:

   ```bash
   git tag -a 'v0.3.0-alpha.1+mc26.2' -m 'Phonon 0.3.0-alpha.1 for Minecraft 26.2'
   ```

6. Push the branch before the tag. The release workflow validates that the tag,
   declared mod version, Java version, and Minecraft version agree.

Push release tags individually. GitHub does not create `push` events when more
than three tags are pushed at once. If an immutable tag already exists but its
workflow event was not created, or a platform upload needs to be retried, replay
that exact tag through the guarded manual entry point:

```bash
gh workflow run release.yml --ref main \
  -f 'release_tag=v0.3.0-alpha.2+mc26.2'
```

The recovery workflow checks out the existing tag and applies the same version
and target validation as a tag push. Never delete or move the tag to retrigger a
release.

Configure the GitHub environments `publish-alpha`, `publish-beta`, and
`publish-release`. Alpha may publish automatically after CI; beta and release
should require a maintainer approval. The workflow maps all three channels
explicitly, marks alpha and beta GitHub releases as pre-releases, and emits a
SHA-256 checksum.

## Required release evidence

Record these fields in every release entry:

- Phonon version, Minecraft version, NeoForge version, Java version, and commit.
- CI build/test result and artifact SHA-256.
- Dedicated-server startup result.
- Client startup and resource reload result.
- Manual checklist tester, date, operating system, and result, or `not run`.
- Soak duration for stable releases.
- Known issues and any deliberately untested paths.

## Manual smoke-test checklist

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
system, and result in the release notes before promotion to beta.
