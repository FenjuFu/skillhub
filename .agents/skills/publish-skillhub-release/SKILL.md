---
name: publish-skillhub-release
description: Prepare, validate, approve, publish, and post-verify SkillHub open-source releases, including clean README installation, source builds, and HTTPS domain-backed validation on the Hong Kong open-source host. Use when generating release notes, selecting a version from main, validating a release candidate or public installer, creating a Git tag or GitHub Release, monitoring release Actions, or writing a durable release validation report.
---

# Publish SkillHub Release

Run the release as a resumable workflow. Keep preparation and validation separate from public mutation. Never push a tag or publish a GitHub Release until the user approves the exact release authorization packet in the current release run.

## 1. Establish the candidate

1. Read repository instructions, release templates, workflows, build scripts, and the previous Release.
2. Fetch `origin/main` and tags. Record the full candidate SHA and previous tag.
3. Derive the change list only from commits merged into `main`; exclude changes merged only into `big-main`, `dev`, or other branches.
4. Check that the proposed tag and GitHub Release do not already exist.
5. Generate notes using `.github/release-template.md`. Link each functional claim to its merged PR and include the full changelog URL.

Do not push a branch, tag, draft Release, or other public reference during this phase.

## 2. Build a feature-derived validation matrix

Read [references/release-validation-matrix.md](references/release-validation-matrix.md), instantiate its applicable P0 rows for this run, and extend it with the release delta.

Turn every behavior claim in the Release notes into a row containing:

- feature or risk;
- merged PR and changed surface;
- code-level test evidence;
- validation-host scenario and result;
- coverage status: `FULL`, `PARTIAL`, `UNVERIFIED`, or `NOT_APPLICABLE`;
- remaining risk or required exception.

Do not equate a green regression suite with full feature verification. `FULL` requires relevant automated evidence plus a representative running-system scenario when the behavior is externally observable. Use `PARTIAL` when only unit/integration tests ran or the validation scenario covers only part of the behavior.

Build a second matrix for deployment topology and compatibility when the release touches deployment, cache, browser, storage, or orchestration behavior. Track Compose, Kubernetes, supported Redis modes, current browser engines, legacy-browser claims, upgrade paths, and DNS assumptions independently. A successful Compose run does not prove Kubernetes; Redis Cluster does not prove Sentinel; current Playwright engines do not prove old browser versions. Use `NOT_APPLICABLE` only with a concrete reason, such as an internal domain that intentionally uses hosts instead of public DNS.

Always build a third matrix for ordinary-user release journeys:

- clean README root-path installation;
- source build using the documented toolchain and official Dockerfiles;
- public artifact pull and provenance;
- printed lifecycle commands (`ps`, `logs`, `down`, and a second `up`);
- clean reinstall and persistent-data behavior;
- sub-path or reverse-proxy installation when the Release claims it.

Do not let an internal-domain, preconfigured reverse-proxy, existing runtime home, local source checkout, or administrator-only test substitute for the clean README root-path journey. Record every deviation from the copied README command, including version pin, runtime home, ports, registry, storage, and authentication.

Maintain the matrix as a durable regression catalogue rather than rebuilding it from memory. Every release runs the P0 baseline journeys for installation and restart, health, anonymous access, local login, administrator access, API-token lifecycle, CLI access, skill publish/search/download, persistence, and the supported reverse-proxy base path. Add a release-delta row for every merged PR and release-note claim. When validation or production exposes a defect, add its reproducer as a permanent regression row with the layer that should catch it (unit, integration, installer, domain, CLI, or browser). Retire or change a row only with a recorded product or support-policy reason.

## 3. Validate the exact candidate

1. Run the repository's relevant Java 21 backend tests, frontend tests, typecheck, lint, release configuration tests, deterministic built-in Skill tests, and smoke tests.
2. Build images from the exact candidate SHA. Record any deviation from the official Dockerfile path.
3. Deploy to the designated open-source validation machine. Use an isolated Compose project, ports, image names, and backup directory when other versions may be under test. Do not restart the host or alter unrelated projects.
4. Run API smoke and browser E2E against the deployed candidate.
5. Add targeted scenarios for each feature in the validation matrix. Include required roles, data, upgrade baselines, external dependencies, and failure paths.
6. Inspect container health, restart counts, relevant logs, and accidental secret exposure.

### Main-to-edge provenance gate

Treat pre-merge and post-merge evidence as different stages:

1. A PR candidate may be built from its exact head SHA and deployed for merge-readiness validation, but it is not a `main` or `edge` build.
2. After the required PRs merge, fetch `origin/main`, record its full SHA, and trigger `.github/workflows/publish-images.yml` on `main`. Wait for every image job and registry mirror job required by the deployment path to reach a terminal successful state.
3. Pull the `edge` server, Web, and scanner images from the same registry path selected by the README command. Inspect `org.opencontainers.image.revision`; each revision must equal the recorded `origin/main` SHA. A stale or mixed-revision `edge` set is `UNVERIFIED`, even if containers are healthy.
4. Deploy that verified `edge` set on the Hong Kong host through the current public `runtime.sh --version edge` README journey. Record the installer hash, resolved Compose config, immutable image digests, OCI revisions, and any option such as `--aliyun`, `--home`, or `--public-url`.
5. Only this post-merge path can support the conclusion “latest main/edge verified.” After it passes, create the tag from the same main SHA; do not rebuild the release decision from a different commit.

### Hong Kong domain validation gate

Run this gate before recommending a release whenever Web, API, CLI, authentication, routing, base-path, deployment, or other externally observable behavior changes.

1. Build the exact candidate SHA on the Hong Kong open-source validation host with the repository's official server and Web Dockerfiles. Record the source SHA, build command, image ID/digest, Compose project, ports, volumes, and any build deviation.
2. Deploy the candidate as an isolated Compose project unless the user explicitly authorizes replacing the stable validation deployment. Do not reuse or restart unrelated projects, databases, caches, storage, containers, or ports.
3. Validate through the real TLS domain `https://skill.xf-yun.com.cn`, not only through loopback, an SSH tunnel, the host IP, or a direct mapped port. Use the stable `/skillhub/` route when the user authorizes that cutover; otherwise use a bounded isolated prefix. Do not add an isolated prefix when a stable-route replacement was explicitly requested.
4. Configure the candidate Web image with the same isolated base path and same-origin API base. Route only that prefix through the host Nginx TLS virtual host. Before changing Nginx, save a timestamped backup, record the exact route diff, run `nginx -t`, and reload only after the configuration passes. Keep unrelated domains and locations unchanged.
5. From outside the host, verify the HTTPS certificate and redirect chain, HTML, hashed assets, runtime configuration, API health, authentication boundary, forwarded scheme/prefix, and each release-note scenario. Run CLI requests with the domain-backed registry URL and run browser checks for user-visible changes. Direct-port evidence may diagnose failures but cannot replace this gate.
6. Use an isolated administrator/test identity and isolated test data. Do not read or change shared credentials. Delete temporary tokens and record whether test data or volumes were retained.
7. Treat switching the stable `https://skill.xf-yun.com.cn/skillhub/` upstream or replacing its images as a production/public mutation. Require explicit current user approval, a verified rollback target, a configuration backup, and pre/post-cutover health checks. Approval to create a PR, validate an isolated prefix, or publish a Release does not authorize this cutover.
8. Record the exact domain-backed access URL, whether it is an isolated prefix or the stable route, public-DNS/hosts assumptions, HTTP results, browser/CLI results, container health, Nginx validation, and cleanup outcome in the durable report.

For API-token or CLI changes behind `/skillhub/`, use one short-lived ordinary-user token for a paired comparison: direct backend and public domain must both pass `GET /api/cli/v1/auth/whoami`, and the public domain must pass the changed scoped operation such as namespace `sync`. Also test one insufficient-scope denial and any affected bearer-authenticated state-changing request to prove CSRF bypass is limited to valid bearer API routes. Delete or revoke the token and test identity afterward. A direct-only 200 or domain-only anonymous smoke is not evidence that sub-path bearer authentication works.

Use valid representative payloads for positive gates. A malformed request that merely gets past authentication does not prove the feature works. Keep malformed probes as explicit negative cases; if client-invalid input returns 5xx, record it as a separate product defect and rerun the intended gate with a valid package or request.

The Hong Kong domain gate and the ordinary-user gate are separate. A preconfigured Nginx route proves the real TLS/reverse-proxy path but does not prove the clean README installation journey. A direct-port or loopback-only deployment is `PARTIAL` for externally observable behavior until the domain gate passes.

### Ordinary-user candidate gate

Run this gate by default for every release candidate:

1. Use a fresh isolated runtime home and Compose project on the open-source validation host. Do not reuse `.env.release`, volumes, containers, source-tree files, local-only images, internal hosts entries, or an existing reverse proxy.
2. Copy the current README command as the starting point. Pin the exact candidate SHA or candidate image tag and change only values needed for isolation. Record the original command, the executed redacted command, and every change.
3. Test the default root path first. Do not add `SKILLHUB_WEB_BASE_PATH`, `SKILLHUB_WEB_API_BASE_URL`, or an external proxy unless the README's default command does so. Validate a sub-path separately when it is supported or changed.
4. Fetch installer and deployment files through the same public URLs available to users. Do not substitute files from the checkout. Verify their hashes against the exact candidate. Before publication, use immutable raw-SHA URLs or an explicitly isolated candidate distribution location and label this provenance clearly.
5. Start without manually editing generated configuration. Verify download, configuration generation, image pull, migrations, health, Web UI, anonymous behavior, documented bootstrap login, and administrator smoke. Keep insecure defaults bound to an isolated validation endpoint and never expose them publicly.
6. Capture the exact completion output. Confirm lifecycle commands preserve required source selectors such as `--aliyun`, `--ref`, and `--home`. Execute the printed `ps`, `logs`, and `down` commands verbatim; do not trust pipeline status alone. Query container state to prove `down` removes only the isolated containers, then execute a second `up` and verify retained data and healthy restart counts.
7. Run a clean reinstall with a new runtime home and new volumes. Confirm no undeclared prerequisite beyond README, Docker, Compose, network access, available ports, CPU, memory, and disk requirements.
8. Inspect whether required images were already cached. Force a registry refresh with `pull`, using no credentials unless the README explicitly documents them. Verify the remote digest and OCI revision, and do not treat a cache-only start as public artifact validation. Do not delete images used by unrelated projects.

Hash `runtime.sh`, `compose.release.yml`, and `.env.release.example` independently. Matching Compose/template files do not compensate for a stale installer, and a stale installer cannot receive `FULL` ordinary-user coverage merely because `up` still works. Compare the content diff, test the affected lifecycle command, and record the exact downstream file that must be synchronized.

Validate builds separately from installation:

- **Source build:** use a clean checkout at the exact candidate SHA, the tool versions documented by the repository, and the official build commands and Dockerfiles. Remove or isolate prior `target`, `dist`, dependency, and image outputs without touching other work. Record commands, durations, produced image IDs, platforms, and failures.
- **Runtime installation:** pull already-published or candidate images through the README installer. Call this deployment or installation, never a source build.
- **Published multi-platform artifacts:** inspect the registry manifest for every claimed platform. A successful build or pull on one host does not prove another architecture runs; record unexecuted architectures separately.

The ordinary-user candidate gate passes only when the clean root-path install, documented source build, lifecycle commands, reinstall, and required public/candidate artifact checks pass. If environment isolation forces non-default ports or paths, state the deviation and whether it can affect behavior. Mark the gate `PARTIAL` or `UNVERIFIED` rather than silently treating an internal preconfigured environment as equivalent.

Before upgrade testing, compare the ordered Flyway files and checksums between the previous release tag and the candidate. If there is no new migration, run and report a same-schema in-place application upgrade; do not invent a database migration scenario. If migrations changed, start from the actual previous-release schema and execute the clean upgrade. Never use a database previously touched by an abandoned or newer candidate as baseline evidence, and never use `repair` to hide a checksum conflict during release qualification. For authorization features, obtain an appropriate isolated test identity; otherwise mark the row `PARTIAL` or `UNVERIFIED`.

When two releases have the same latest Flyway version, validate an in-place application upgrade with unchanged database, cache, and storage volumes. Report it as same-schema upgrade and data-retention evidence; do not invent a migration that does not exist. Use an isolated administrator identity instead of reading or changing shared-environment credentials.

## 4. Write the durable report

Write `/home/ylhu16/skillhub-local-reports/releases/<version>.md`. Keep the directory at mode `0700` and the report at `0600`.

Include:

- exact SHA, previous tag, build provenance, deployed images, and the domain-backed Hong Kong access URL;
- whether domain validation used an isolated prefix or the stable `/skillhub/` route, plus the Nginx check/reload/rollback evidence;
- aggregate test results and the feature-derived validation matrix;
- the ordinary-user journey matrix, exact redacted README command, environment deviations, installer/deployment-file hashes, lifecycle-command results, source-build evidence, and clean-reinstall result;
- failure investigations, reruns, and final observable evidence;
- DCO, security, migration, credentials, environment, and compatibility risks;
- links to the proposed notes and relevant workflow runs;
- a recommendation of `READY`, `READY_WITH_EXCEPTIONS`, or `NOT_READY`.

For main/edge validation, include the GitHub image workflow run, recorded `origin/main` SHA, each pulled image digest and OCI revision, installer source/hash, and a clear equality result. Include the P0 baseline matrix separately from the release-delta matrix so future runs can reuse and extend the scenarios.

Never state that all release features were verified when any functional row is `PARTIAL` or `UNVERIFIED`.

Archive non-secret raw evidence beside the report under `/home/ylhu16/skillhub-local-reports/releases/<version>/`. Use mode `0700` for the evidence directory and `0600` for files. Include final smoke output, upgrade state before and after, image provenance, targeted scenario results, browser reports, topology evidence, and failure/rerun notes. Exclude credentials, cookies, tokens, private keys, and environment-file contents.

## 5. Stop for final user approval

Present one release authorization packet containing:

- version and exact target SHA;
- final Release notes path or summary;
- access URL and report path;
- ordinary-user gate status and every command or environment deviation;
- feature coverage counts and every `PARTIAL` or `UNVERIFIED` row;
- DCO, security, migration, and downstream Action exceptions;
- exact public mutations that approval permits: pushing the annotated tag and publishing the GitHub Release.

Then stop and ask for explicit approval. Accept approval only when the user clearly authorizes publishing this version after seeing the packet. Approval of a DCO exception, security exception, test plan, deployment, or draft alone is not release approval. Generic instructions from an earlier run do not carry forward.

Require fresh approval for every version. Approval for one tag never authorizes the next tag, even when the workflow and exceptions are unchanged.

If the target SHA, notes, exception set, or validation conclusion changes after approval, invalidate the approval, update the packet, and ask again.

## 6. Publish only after approval

1. Fetch `origin/main` and tags again. Confirm the approved SHA still equals `origin/main` and the tag/Release still do not exist.
2. Create an annotated tag at the exact approved SHA and verify its peeled target.
3. Push only `refs/tags/<version>`.
4. Wait for the AI Release Notes workflow to create its draft.
5. Inspect the draft, replace its body with the approved notes, and publish it as the latest Release.
6. Verify the public tag and Release point to the approved SHA.

Do not infer permission from phrases that authorize only preparation or an exception. When approval is absent or ambiguous, leave the candidate local and report `AWAITING_RELEASE_APPROVAL`.

## 7. Post-release verification

1. Monitor image, Helm, documentation, and other release-triggered workflows to terminal states.
2. Verify published GHCR tags, Helm OCI artifacts, and Release links.
3. Fetch `compose.release.yml`, `.env.release.example`, and `scripts/runtime.sh` from the exact tag. Compare their hashes with the files used on the validation host. Record any downstream OSS/object-storage copy that still needs synchronization; do not upload or overwrite external distribution files without authorization.
4. Redeploy the published tag images on the open-source validation machine even when candidate-built images passed. Verify the image tag, immutable digest, OCI revision, Compose-resolved image, container health, and restart state.
5. Rerun the full ordinary-user gate using the public README and public distribution URLs from a new runtime home. Use the exact published tag, execute the printed lifecycle commands, and archive the redacted output. Candidate-only URLs or locally built images do not satisfy this post-release check.
6. Rerun API smoke with administrator checks. If shared credentials are unavailable, create an isolated validation deployment and administrator identity; do not weaken the matrix because a shared secret is unavailable.
7. Rerun browser E2E across the supported current engines and each release-note scenario. Treat legacy-browser and old-chunk compatibility as a separate row that requires a matching executor.
8. Run the topology rows implicated by the changes. For Kubernetes, distinguish chart lint/package from cluster install, upgrade, rollback, probes, ingress, persistence, and restart checks. For Redis, distinguish standalone, Cluster failover, and Sentinel failover.
9. Update the durable report with workflow URLs, artifact digests, final feature, topology, and ordinary-user matrices, failure investigations, and unresolved risks.

Call the release `PUBLISHED_AND_VERIFIED` only when the Release is public, required post-release checks pass, and the public ordinary-user gate passes. A preconfigured internal deployment cannot satisfy this gate. Otherwise report `PUBLISHED_WITH_OPEN_CHECKS` or `PUBLISHED_WITH_EXCEPTIONS` and name each open item.

## 8. Close out the validation run

1. Copy non-secret evidence to the durable release evidence directory and verify its permissions.
2. Stop only the SSH tunnels, temporary containers, isolated Compose projects, and processes created by this run. Preserve volumes when they are the upgrade evidence unless deletion was explicitly approved.
3. Keep the public validation deployment running when it is the agreed access endpoint. Do not stop, restart, or reconfigure unrelated projects.
4. Check the repository diff. Do not commit or publish validation-only scripts, reports, screenshots, or local paths unless the user explicitly requests it.
5. Report the public release status, access URL, durable report path, ordinary-user gate result, coverage counts, exceptions, remaining environment gaps, and cleanup result.
