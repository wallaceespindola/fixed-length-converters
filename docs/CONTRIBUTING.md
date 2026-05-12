# Contributing

## Branching Strategy

- `main` — stable, always deployable
- `feature/<name>` — new features or enhancements
- `fix/<name>` — bug fixes

## Workflow

1. Fork the repository
2. Create a branch: `git checkout -b feature/my-feature`
3. Make changes following the code standards below
4. Run tests: `make test`
5. Commit following conventional commits format
6. Push and open a pull request against `main`

## Commit Message Format

```
<type>: <short description>

<optional body>
```

Types: `feat`, `fix`, `refactor`, `test`, `docs`, `ci`, `chore`

Examples:
```
feat: add fixedformat4j 2.0 strategy for CODA
fix: ensure CODA output lines are exactly 128 chars
test: add symmetry tests for all 14 strategy combinations
```

## Code Standards

- Java 21+, Spring Boot 3.4.x
- Max line length: 120 chars
- All packages: `com.wtechitsolutions.*`
- New strategies MUST implement `FileGenerationStrategy` interface
- CODA output lines MUST be exactly 128 characters
- SWIFT output MUST contain `:20:`, `:25:`, `:28C:`, `:60F:`, `:62F:` tags
- All SWIFT formatters MUST use `---\n` as the inter-message separator
- All new code must have JUnit 5 tests
- Test coverage must remain above 40% (JaCoCo enforced)

## Frontend Changes

The pre-built frontend bundle is committed to `src/main/resources/static/`. When you change any frontend source code under `src/main/frontend/src/`, you must rebuild the bundle before committing:

```bash
cd src/main/frontend
npm install          # only needed if package.json changed
npm run build        # outputs to src/main/resources/static/
```

Then stage and commit the rebuilt bundle alongside your source changes:

```bash
git add src/main/frontend/src/ src/main/resources/static/
git commit -m "feat: <describe your frontend change>"
```

This ensures that `mvn spring-boot:run -Pskip-frontend` always serves the latest UI without requiring a local Node.js install.

## Adding a New Parser Library

1. Create `parser/<LibraryName>Formatter.java`
2. Create annotated model classes in `parser/model/` — no XML files (except Camel BeanIO which uses XML stream mapping by design)
3. Create `strategy/Coda<LibraryName>Strategy.java` extending `AbstractCodaStrategy`
4. Create `strategy/Swift<LibraryName>Strategy.java` extending `AbstractSwiftStrategy`
5. Add the Maven dependency to `pom.xml`
6. Add the new `Library` enum value
7. Write tests covering both CODA and SWIFT generation + parsing
8. Update `CLAUDE.md` and `README.md`
9. Add a `CHANGELOG.md` entry under `[Unreleased]`
