# SkillHub release validation matrix

Use this catalogue as the minimum release baseline. Copy the applicable rows into the durable release report, add release-delta rows derived from merged PRs, and attach observable evidence. Do not mark a row `PASS` from an unrelated broad test suite.

Status values:

- `PASS`: the stated scenario and assertions passed on the exact candidate.
- `FAIL`: observed behavior contradicted an assertion.
- `BLOCKED`: a named prerequisite is unavailable.
- `NOT_APPLICABLE`: the release or support policy excludes the scenario, with a reason.

Coverage values remain `FULL`, `PARTIAL`, `UNVERIFIED`, and `NOT_APPLICABLE`. A row may have a passing unit test yet remain `PARTIAL` when its required deployed scenario was not run.

## P0 baseline catalogue

| ID | Area | Required scenario | Observable assertions |
|---|---|---|---|
| PROV-01 | Source | Resolve candidate from `origin/main` | Full SHA recorded; release changes come only from commits reachable from main |
| PROV-02 | Images | Build/pull server, Web, scanner | OCI revisions all equal candidate SHA; immutable digests recorded; no mixed or cached stale image |
| INST-01 | Public assets | Compare README installer assets | Public `runtime.sh`, Compose, and env-template hashes each match intended revision |
| INST-02 | Clean install | Run the exact README root-path command in a fresh home | Configuration generated without manual edits; dependencies pulled; all containers healthy |
| INST-03 | Lifecycle | Execute printed `ps`, `logs`, and `down`, then a second `up` | Printed commands retain source options; only target project stops; second start is healthy |
| INST-04 | Persistence | Restart using unchanged volumes | Created marker data remains; migrations do not rerun destructively; restart count is understood |
| INST-05 | Clean reinstall | Install into a second fresh home and volume set | No undeclared prerequisite or dependency on first installation/cache |
| UPG-01 | Schema diff | Compare Flyway names and checksums to previous release | Correctly classified as migration, same-schema upgrade, or incompatible baseline |
| UPG-02 | Upgrade | Upgrade an actual previous-release database or run same-schema in-place upgrade | Data retained; Flyway succeeds; health and core reads/writes pass; no `repair` used to hide conflict |
| WEB-01 | Root Web | Load HTML and hashed assets | 200 responses; runtime config points to expected API/base path; no console bootstrap failure |
| WEB-02 | Sub-path Web | Load `/skillhub/`, assets, refreshable routes, and API health | Redirect, base path, forwarded prefix/proto, and same-origin API behavior are correct |
| AUTH-01 | Anonymous boundary | Call public and protected endpoints without credentials | Public calls pass; protected calls return the expected 401/403 envelope |
| AUTH-02 | Local session | CSRF bootstrap, register/login, current user, logout | Cookie/CSRF behavior and session identity are correct through the public domain |
| AUTH-03 | API token | Create one short-lived ordinary-user token | Direct backend and domain `whoami` both return 200 for the same token |
| AUTH-04 | Token scopes | Call allowed and insufficient-scope endpoints | Allowed operation passes; missing scope returns 403 with the required scope; token is revoked afterward |
| AUTH-05 | Bearer POST | Send a valid bearer-authenticated state-changing or dry-run request | Valid scoped request reaches the controller without session CSRF; malformed payloads are separate negative cases |
| CLI-01 | Identity | Run the exact candidate CLI `whoami` against the domain | Exit 0 and correct registry/identity, with token supplied outside argv when possible |
| CLI-02 | Discovery | Run search/resolve/install against representative public content | Results, archive download, extraction, and install manifest are correct |
| CLI-03 | Namespace sync | Run `sync status`, pull/check, diff, and affected push/dry-run paths | Server manifest contract, fingerprints, local change detection, pagination, and scopes behave correctly |
| SKILL-01 | Publish | Publish or dry-run a valid minimal package | Package validation, visibility, version, and response envelope are correct |
| SKILL-02 | Scan/review | Exercise scan completion and required review transition | Task reaches the expected terminal state; retry/failure evidence captured when changed |
| SKILL-03 | Search/download | Search, resolve, and download the published version | Indexed metadata and downloaded content match the published artifact |
| SKILL-04 | Lifecycle | Exercise yank/hide/archive behavior implicated by changes | Listing, installability, latest-version pointer, and authorization match lifecycle policy |
| ADMIN-01 | Administrator | Login with an isolated administrator and run admin smoke | Admin-only API/UI access passes without using shared credentials |
| OBS-01 | Runtime | Inspect health, logs, and restart counts after scenarios | All required containers healthy; no unexpected restart, migration, auth, or secret-leak errors |
| CLEAN-01 | Cleanup | Remove temporary tokens, identities, routes, and projects | Stable endpoint remains healthy; unrelated projects unchanged; retained evidence is intentional |

## Release-delta rows

For each merged PR or release-note claim, add at least one row:

| ID | PR/claim | Changed surface | Risk | Automated evidence | HK/domain scenario | Expected result | Status | Coverage | Evidence link |
|---|---|---|---|---|---|---|---|---|---|
| DELTA-001 | `#<pr>` | API/Web/CLI/deploy/data | Describe user-visible failure | Exact test | Representative real-domain action | Observable assertion | Pending | UNVERIFIED | Pending |

Split one PR into multiple rows when it changes independent behaviors or topologies. Merge multiple PRs into one scenario only when the same observable action proves all claims.

## Defect-to-regression rule

Every defect found during validation creates or strengthens a durable row before the release is closed:

1. Record the exact failing path, topology, identity/scope, input, expected result, actual result, candidate SHA, and evidence.
2. Add the narrowest deterministic automated test that would have caught the defect.
3. Add a deployed scenario when proxying, packaging, browser, registry, persistence, or external integration contributed.
4. Rerun both the reproducer and the positive user journey after the fix.
5. Keep the row in future P0 runs when recurrence would break installation, authentication, data integrity, CLI compatibility, or the stable domain. Otherwise retain it as a risk-selected regression row with its selection rule.

Known durable regressions include:

- Aliyun installer lifecycle output must reference OSS-root `/runtime.sh`, not `/scripts/runtime.sh`.
- `edge` image OCI revision must equal the selected `origin/main` SHA.
- `/skillhub/` bearer authentication must pass the same-token direct/domain comparison plus CLI namespace sync.
- Bare `/skillhub` must redirect with relative `Location: /skillhub/` so an upstream TLS deployment cannot downgrade to HTTP.
- A database touched by a newer or abandoned migration set cannot serve as previous-release upgrade evidence.
