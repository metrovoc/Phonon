# Release Management

Phonon versions the mod independently from Minecraft and identifies the exact
game target with SemVer build metadata.

## Version format

- Source version: `0.3.0-alpha.1`
- Minecraft target: `1.21.1`
- Git tag and platform version: `v0.3.0-alpha.1+mc1.21.1`
- Artifact name: `phonon-neoforge-1.21.1-0.3.0-alpha.1+mc1.21.1.jar`

The pre-release identifier controls the release channel. Build metadata keeps
artifacts for different Minecraft versions unique without changing SemVer
precedence.

| Stage | Required evidence | Version example | Platform channel |
| --- | --- | --- | --- |
| Alpha | Automated build and unit tests pass | `0.3.0-alpha.1` | Alpha |
| Beta | A human completes the smoke-test checklist | `0.3.0-beta.1` | Beta |
| Stable | The beta completes a real-server soak without a release-blocking defect | `0.3.0` | Release |

Every directly published, newly ported, or otherwise insufficiently tested
Minecraft target must remain alpha. A beta NeoForge dependency also keeps the
corresponding Phonon build in alpha unless that toolchain has received explicit
project-level validation.

Increment `alpha.N` or `beta.N` for every replacement artifact. Never move or
reuse a published version tag. Promotion changes only the version and release
notes unless testing found a defect.

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
3. Commit the version change with a Conventional Commit.
4. Create an annotated tag, for example:

   ```bash
   git tag -a 'v0.3.0-alpha.1+mc1.21.1' -m 'Phonon 0.3.0-alpha.1 for Minecraft 1.21.1'
   ```

5. Push the branch before the tag. The release workflow validates that the tag,
   declared mod version, Java version, and Minecraft version agree.

Configure the GitHub environments `publish-alpha`, `publish-beta`, and
`publish-release`. Alpha may publish automatically after CI; beta and release
should require a maintainer approval. The workflow maps all three channels
explicitly, marks alpha and beta GitHub releases as pre-releases, and emits a
SHA-256 checksum.

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
