## Summary

<!-- Describe what this PR does and why -->

## Type of Change

- [ ] Bug fix
- [ ] New feature
- [ ] Refactoring
- [ ] Documentation
- [ ] CI/CD / Infrastructure

## Checklist

- [ ] Code compiles with `mvn clean compile -Pskip-frontend`
- [ ] All tests pass: `mvn test -Pskip-frontend`
- [ ] Test coverage stays above 40% (JaCoCo check)
- [ ] New strategies implement `FileGenerationStrategy` interface correctly
- [ ] CODA output lines are exactly 128 characters
- [ ] SWIFT output contains required MT940 tags (:20:, :25:, :28C:, :60F:, :62F:)
- [ ] No secrets or credentials committed
- [ ] CLAUDE.md updated if architecture changed

## Test Evidence

<!-- Paste relevant test output or describe manual testing steps -->
