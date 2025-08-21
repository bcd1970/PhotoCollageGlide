# General Development Rules - Claude Code Configuration

## 1. General CLAUDE.md (Place in project root)

```markdown
# General Development Standards

## Core Development Rules
- Research first: Always analyze existing codebase before implementing
- Test first: Write tests before implementation
- No mocking: Use real services and data in tests
- Update existing files, never create duplicates on the same topic
- Follow established patterns in the codebase
- Document changes in existing files only

## File Management Rules
- ALWAYS update existing documentation files, never create new ones on the same topic
- ALWAYS update existing README files instead of creating additional ones
- Update existing test files rather than creating duplicate test classes
- Modify existing configuration files instead of creating new variants
- Refactor existing code instead of duplicating functionality

## Code Quality Rules
- Write unit tests for all business logic (NO MOCK DATA - use real data/services)
- Write integration tests for critical user flows
- Follow existing naming conventions in the project
- Implement proper error handling
- Use dependency injection consistently
- Follow the established architecture pattern

## Implementation Rules
- Research existing implementations before starting
- Understand current architecture and patterns
- Maintain consistency with existing code style
- Update existing documentation with changes
- Test with real data and real scenarios
- Focus on maintainability and readability
```

## 2. General .claude/settings.json

```json
{
  "rules": {
    "general": {
      "updateExistingFiles": true,
      "noMockTesting": true,
      "researchFirst": true,
      "testFirst": true,
      "followExistingPatterns": true,
      "maintainConsistency": true
    },
    "allowedShellCommands": [
      "git",
      "npm",
      "yarn",
      "docker",
      "make"
    ],
    "blockedShellCommands": [
      "rm -rf",
      "sudo",
      "chmod 777",
      "format",
      "dd"
    ],
    "fileAccess": {
      "allowWrite": [
        "src/**/*",
        "test/**/*",
        "tests/**/*",
        "*.md",
        "*.json",
        "*.yml",
        "*.yaml"
      ],
      "blockWrite": [
        ".git/**",
        "node_modules/**",
        "*.env",
        "*.key",
        "*.pem"
      ]
    }
  }
}
```

## 3. General Commands

### .claude/commands/implement-feature.md
```markdown
# Implement Feature: $ARGUMENTS

## Research Phase
1. Analyze existing codebase for similar patterns
2. Review current architecture and identify where feature fits
3. Check existing implementations to understand patterns
4. Review existing tests to understand testing approach

## Implementation Phase
1. Write tests first (NO MOCK DATA - use real services)
2. Follow existing architecture pattern
3. Use existing coding conventions
4. Update existing documentation files
5. Implement proper error handling
6. Ensure compatibility with existing features

## Testing Rules
- Use real data sources, never mocked data
- Test edge cases and error scenarios
- Update existing test classes, don't create duplicates
- Test integration with existing features
- Follow existing test patterns
```

### .claude/commands/refactor-code.md
```markdown
# Refactor Code: $ARGUMENTS

## Analysis
1. Research current implementation thoroughly
2. Identify improvement opportunities
3. Check existing tests and ensure they cover functionality
4. Verify current architecture pattern compliance

## Refactoring Rules
1. Maintain existing functionality
2. Follow established architecture pattern
3. Update existing tests, don't create new test files
4. Update existing documentation to reflect changes
5. Improve code quality while maintaining consistency
6. Use existing patterns and conventions

## Documentation
- Update existing documentation with changes made
- Never create new documentation files for existing topics
- Document architectural decisions in existing files
- Maintain consistency with existing documentation style
```

### .claude/commands/test-implementation.md
```markdown
# Test Implementation: $ARGUMENTS

## Testing Rules
- NO MOCK DATA: Use real services and data sources
- Test with actual data where appropriate
- Test edge cases with real scenarios
- Update existing test classes, don't create new ones

## Test Structure
1. Follow existing test patterns in codebase
2. Write descriptive test names
3. Test both success and failure scenarios
4. Include integration testing where relevant
5. Test error handling

## Coverage Areas
- Business logic with real data
- Integration between components
- Error handling scenarios
- Edge cases and boundary conditions
```

### .claude/commands/update-docs.md
```markdown
# Update Documentation: $ARGUMENTS

## Documentation Rules
- ALWAYS update existing documentation files
- NEVER create new files for existing topics
- Update README.md with new information
- Modify existing architecture docs
- Maintain consistency with existing style

## Process
1. Find existing documentation on the topic
2. Update the relevant sections
3. Maintain consistency with existing style
4. Ensure all changes are reflected in existing files
5. Update any existing table of contents
6. Keep documentation current with code changes
```

### .claude/commands/code-review.md
```markdown
# Code Review: $ARGUMENTS

## Review Checklist
1. Architecture compliance with existing patterns
2. Proper error handling implementation
3. Test coverage with real data (no mocks)
4. Code style and naming convention consistency
5. Performance considerations
6. Documentation updates

## Quality Check
- Ensure existing patterns are followed
- Verify no duplicate files created
- Check that existing docs are updated
- Confirm real data usage in tests
- Validate integration with existing code
```

## Usage Instructions

### Commands Usage
```bash
# Implement new features
/implement-feature "User authentication"

# Refactor existing code
/refactor-code "Improve error handling"

# Test implementations
/test-implementation "Login flow"

# Update documentation
/update-docs "API changes"

# Review code
/code-review "New feature implementation"
```

## Key General Rules Summary

### Development Philosophy
- ✅ **Research existing codebase first**
- ✅ **Update existing files, never create duplicates**
- ✅ **Write tests before implementation**
- ✅ **Use real data, no mocks**
- ✅ **Follow established patterns**
- ✅ **Maintain consistency**

### File Management
- ✅ **Update existing documentation only**
- ✅ **Modify existing test files**
- ✅ **Refactor instead of duplicating**
- ✅ **Follow existing project structure**

### Quality Standards
- ✅ **Test with real data**
- ✅ **Proper error handling**
- ✅ **Follow naming conventions**
- ✅ **Maintain architecture consistency**