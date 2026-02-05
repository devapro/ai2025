👋 **Hello! I'm your AI Project Manager.**

I'm here to help you manage software development projects. I track tasks, review pull requests, monitor progress, and ensure deliverables meet requirements and quality standards.

## What I Can Help With

**📋 Task Management:**
• Track JIRA task status and progress
• Monitor assignees, story points, and due dates
• Identify dependencies and blockers
• Generate sprint progress reports

**🔍 Pull Request Reviews:**
• Comprehensive code reviews against requirements
• Verify JIRA acceptance criteria compliance
• Check code quality against standards (promts/rules.md)
• Security vulnerability detection
• Provide clear APPROVE/REQUEST CHANGES/REJECT decisions

**📊 Progress Tracking:**
• Calculate sprint completion percentages
• Track story points burned vs remaining
• Monitor PR merge status
• Identify risks and blockers

**✅ Requirements Verification:**
• Compare implementation against JIRA requirements
• Ensure acceptance criteria are met
• Validate feature completeness
• Check test coverage adequacy

## Common Commands

**Task Status:**
• "What's the status of AND-123?"
• "Show me details for JIRA task AND-456"
• "Check if AND-789 has any blockers"

**Pull Request Reviews:**
• `/review-pr https://github.com/owner/repo/pull/123`
• "Review PR #456 against JIRA requirements"
• "Does PR #789 meet code quality standards?"

**Progress Reports:**
• "Sprint progress for AND-123, AND-124, AND-125"
• "How many tasks are completed this sprint?"
• "Show me blocked tasks"

**Requirements Verification:**
• "Does PR #456 meet AND-123 requirements?"
• "Verify feature completeness for AND-789"
• "Check if implementation matches specifications"

## My Tools & Capabilities

✅ **JIRA Integration** - Fetch tasks, requirements, acceptance criteria
✅ **GitHub Integration** - Monitor PRs, reviews, merge status
✅ **Git Operations** - Review code diffs for PR assessment
✅ **RAG Documentation Search** - Access project specs and docs
✅ **Code Quality Checks** - Apply standards from promts/rules.md

## How I Work

1. **Fetch requirements** from JIRA automatically
2. **Get PR details** from GitHub automatically
3. **Review code changes** against requirements
4. **Check quality standards** per promts/rules.md
5. **Identify issues** with severity levels (Critical/Major/Minor)
6. **Provide decisions** with clear rationale and next steps

**I'm autonomous** - I automatically fetch all needed data without asking permission!

## Review Criteria

Every PR review includes:
• ✅ Requirements compliance (JIRA acceptance criteria)
• ✅ Code quality (Kotlin conventions, naming, structure)
• ✅ Security assessment (no secrets, input validation, error handling)
• ✅ Testing coverage (unit tests, edge cases)
• ✅ Documentation updates
• ✅ Performance considerations

## Configuration Status

Your environment:
• ✅ JIRA: Connected to inv.atlassian.net
• ✅ GitHub: Token configured (private repos access)
• ✅ RAG: OpenAI embeddings enabled
• ✅ Code Standards: promts/rules.md available
• ⚠️ Git Ops: Requires PROJECT_SOURCE_DIR in .env

Ready to help manage your project!

---

*Try these:*
• `/review-pr [GitHub PR URL]`
• "Status of AND-123"
• "Sprint progress report"
