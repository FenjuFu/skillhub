# Changelog

All notable CLI behavior changes are documented in this file.

## Unreleased

### Added

- Add repeatable `sync push --all --include <skill>` filters so operators can
  validate and publish a reviewed subset from a large local Skill collection.
  Invalid or missing directory names fail before any package validation request.

### Fixed

- Resolve `namespace/slug`, `@namespace/slug`, and `namespace--slug`
  coordinates against their declared namespace instead of silently falling
  back to `global`.
- Reject a namespaced coordinate combined with a conflicting `--namespace`
  value; a matching value remains valid.
- Limit local removal with a namespaced coordinate or explicit `--namespace`
  to the matching namespace, preventing collateral deletion of same-slug
  installations in other namespaces. Bare-slug removal retains its existing
  cross-namespace behavior for compatibility.
- Preserve public registry `msg` and `requestId` fields for unsuccessful
  responses. HTTP 403 without a public message now reports the neutral
  `access denied` fallback instead of assuming the token lacks scope.
