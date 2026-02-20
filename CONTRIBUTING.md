# Contributing

## Branch Strategy
- `main`: always releasable state.
- Work on a feature/fix branch and merge by Pull Request.
- Use small, focused commits.

## Commit Message
- Recommended format: `type: summary`
- Examples:
  - `feat: improve excel import header detection`
  - `fix: reconnect notification listener after permission grant`
  - `chore: add android ci workflow`

## Local Validation
- Run before pushing:
  - `./gradlew testSafeDebugUnitTest`
  - `./gradlew assembleSafeDebug`

## Play Upload Notes
- Keep `versionCode` increasing.
- Upload mapping/native symbol files for Play diagnostics.

