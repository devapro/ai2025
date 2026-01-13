# Code Quality Rules for PR Review

This file defines code quality rules and best practices for code reviews. The AI agent uses these rules when reviewing pull requests.

## General Code Quality

### Code Style and Formatting
- ✅ Code must follow Kotlin coding conventions
- ✅ Consistent indentation (4 spaces, not tabs)
- ✅ Line length should not exceed 120 characters
- ✅ No trailing whitespace
- ✅ Blank line at end of file

### Naming Conventions
- ✅ Classes: PascalCase (e.g., `UserRepository`, `AiAgent`)
- ✅ Functions: camelCase (e.g., `processMessage`, `executeGitCommand`)
- ✅ Constants: UPPER_SNAKE_CASE (e.g., `MAX_MESSAGE_LENGTH`)
- ✅ Private members: start with lowercase (e.g., `private val logger`)
- ✅ Boolean variables: start with `is`, `has`, `should` (e.g., `isEnabled`, `hasError`)

### Code Organization
- ✅ One class per file (exceptions: small data classes, sealed classes)
- ✅ Organize imports (remove unused imports)
- ✅ Group related functions together
- ✅ Keep functions small and focused (max 50 lines ideally)
- ✅ Use meaningful variable names (avoid single letters except for loop counters)

## Security

### Critical Security Rules
- ❌ **NEVER** commit API keys, tokens, or passwords
- ❌ **NEVER** hardcode secrets in source code
- ❌ **NEVER** log sensitive data (passwords, tokens, personal info)
- ✅ Use environment variables for configuration
- ✅ Validate all user input
- ✅ Sanitize data before output (prevent XSS, injection attacks)
- ✅ Use HTTPS for external API calls
- ✅ Implement proper error handling (don't expose internal errors to users)

### Dependency Security
- ✅ Keep dependencies up to date
- ✅ Review dependencies for known vulnerabilities
- ✅ Use official packages from trusted sources

## Error Handling

### Exception Handling
- ✅ Catch specific exceptions, not generic `Exception`
- ✅ Log errors with context (what failed, why, relevant data)
- ✅ Provide user-friendly error messages
- ✅ Clean up resources in `finally` blocks or use `use {}`
- ✅ Don't swallow exceptions silently
- ❌ Avoid empty catch blocks

### Resource Management
- ✅ Close resources properly (files, connections, streams)
- ✅ Use Kotlin's `use {}` for automatic resource management
- ✅ Handle timeouts for external operations
- ✅ Implement proper retry logic for transient failures

## Testing

### Test Coverage
- ✅ Write unit tests for new functionality
- ✅ Aim for at least 70% code coverage
- ✅ Test edge cases and error conditions
- ✅ Mock external dependencies in tests
- ✅ Keep tests fast (unit tests should run in milliseconds)

### Test Quality
- ✅ Test names should describe what they test: `testUserAuthenticationWithValidCredentials()`
- ✅ Use Given-When-Then or Arrange-Act-Assert pattern
- ✅ One assertion per test (or closely related assertions)
- ✅ Tests should be independent (no shared state)
- ❌ Don't test framework or library code

## Documentation

### Code Comments
- ✅ Add KDoc comments for public APIs
- ✅ Explain *why*, not *what* (code should be self-explanatory)
- ✅ Document complex algorithms or business logic
- ✅ Keep comments up to date with code changes
- ❌ Avoid obvious comments (e.g., `// Get user name` above `val name = user.name`)

### README and Documentation
- ✅ Update README.md when adding new features
- ✅ Document configuration options in .env.example
- ✅ Add usage examples for new APIs
- ✅ Document breaking changes clearly

## Performance

### Efficiency Guidelines
- ✅ Use appropriate data structures (List vs Set vs Map)
- ✅ Avoid nested loops where possible (watch for O(n²) complexity)
- ✅ Use lazy evaluation when appropriate (`lazy {}`, `sequence {}`)
- ✅ Close resources promptly (don't keep connections open unnecessarily)
- ✅ Consider caching for expensive operations
- ⚠️ Profile before optimizing (don't optimize prematurely)

### Kotlin-Specific Performance
- ✅ Use `lateinit` instead of nullable types when appropriate
- ✅ Use `inline` functions for higher-order functions with lambdas
- ✅ Use `data class` for simple data containers
- ✅ Use coroutines for async operations (not threads)
- ✅ Use `Flow` for reactive streams

## Kotlin Best Practices

### Nullability
- ✅ Use non-nullable types by default
- ✅ Use safe call operator `?.` instead of null checks
- ✅ Use elvis operator `?:` for default values
- ✅ Avoid `!!` operator (use only when absolutely sure it's non-null)
- ✅ Use `let {}` for null-safe operations

### Collections
- ✅ Use immutable collections by default (`listOf`, `setOf`, `mapOf`)
- ✅ Use mutable collections only when needed (`mutableListOf`, etc.)
- ✅ Use collection operators (`map`, `filter`, `reduce`) instead of loops
- ✅ Use `asSequence()` for large collections to avoid intermediate lists

### Functions
- ✅ Use expression body for simple functions: `fun double(x: Int) = x * 2`
- ✅ Use named arguments for readability when calling functions with many parameters
- ✅ Use default parameter values instead of overloads
- ✅ Mark functions as `suspend` if they perform async operations
- ✅ Use extension functions to add functionality to existing classes

## Dependency Injection (Koin)

### DI Best Practices
- ✅ Define all dependencies in `AppDi.kt`
- ✅ Use constructor injection (not property injection)
- ✅ Use `single {}` for singletons (most app components)
- ✅ Use `factory {}` for instances that should be created each time
- ✅ Use `named()` qualifier for multiple implementations of same type
- ✅ Validate DI configuration at startup

## Git and Version Control

### Commit Messages
- ✅ Use meaningful commit messages (not "fix", "update", "WIP")
- ✅ Start with verb: "Add feature X", "Fix bug in Y", "Refactor Z"
- ✅ Keep first line under 72 characters
- ✅ Add detailed description in commit body if needed
- ✅ Reference JIRA ticket in commit message if applicable

### Branch Strategy
- ✅ Create feature branches from develop: `feature/user-authentication`
- ✅ Use descriptive branch names
- ✅ Keep branches short-lived (merge quickly)
- ✅ Delete branches after merging
- ❌ Don't commit directly to main/master

## PR Review Checklist

When reviewing a PR, check for:

### Must Have (Critical)
- [ ] No hardcoded secrets or credentials
- [ ] No security vulnerabilities
- [ ] All tests pass
- [ ] No breaking changes (or properly documented)
- [ ] Error handling implemented
- [ ] Resources properly closed

### Should Have (Major)
- [ ] Tests for new functionality
- [ ] Documentation updated
- [ ] Code follows project conventions
- [ ] No code duplication
- [ ] Efficient algorithms used
- [ ] Proper logging added

### Nice to Have (Minor)
- [ ] Code comments for complex logic
- [ ] Refactoring opportunities addressed
- [ ] Performance optimizations considered
- [ ] Accessibility improvements

## Issue Severity Levels

When reporting issues in PR reviews, use these severity levels:

### Critical Issues (Must Fix Before Merge)
- Security vulnerabilities
- Data loss risks
- Breaking changes without documentation
- Hardcoded secrets
- Major bugs that crash the application

### Major Issues (Should Fix)
- Missing error handling
- Performance problems
- Code that violates project conventions
- Missing tests for critical functionality
- Resource leaks

### Minor Issues (Nice to Have)
- Code style inconsistencies
- Unused imports
- Missing documentation
- Refactoring opportunities
- Non-critical performance improvements

## Example Issue Report Format

```markdown
**Issue Title**
- File: `path/to/file.kt`
- Line: 123
- Code: `val password = "hardcoded123"`
- Issue: Hardcoded password in source code (CRITICAL SECURITY VIOLATION)
- Recommendation: Move to environment variable and use `get(named("password"))`
```

---

**Note:** These rules are guidelines, not absolute laws. Use judgment and consider the context. If you need to break a rule, document why in code comments or PR description.
