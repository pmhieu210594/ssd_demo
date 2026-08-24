# Feature: Track Standard Template Usage Rate by Phase

## Objective

Currently, the system does not provide any metrics to measure how closely ticket documents follow the standard templates.

The following functionality should be added:

- Automatically validate documents when processing GitHub Pull Requests.
- Determine whether documents comply with the standard templates.
- Store statistical results in the database.
- Display template usage rates on the PM Dashboard.

## 1. Processing in GithubWebhookController

### Trigger

When `GithubWebhookController` processes a `pull_request` event.

### Validation Scope

Only validate files located under:

```text
documents/changes/{ticket-id}/
```

Examples:

```text
documents/changes/ABC-123/spec-pack.md
documents/changes/ABC-123/context.md
```

## 2. Determine the Corresponding Template

When a file is detected for validation, the system must look for its template in the following priority order:

```text
documents/standards/templates/{file-name}
documents/standards/templates/_ticket-template/{file-name}
documents/standards/templates/_light-ticket-template/{file-name}
```

### Rules

- Use the first template found.
- If no template is found in any location, skip validation for that file.
- No error should be raised.

## 3. Comparison Rules

The objective is to verify that the document follows the template structure.

### Do Not Compare

- Detailed content.
- Descriptive text.
- Bullet lists.
- User-entered values.

### Compare Only

- Document structure.
- Header names.
- Header order.
- Header levels (`#`, `##`, `###`).

### Result

- PASS: Header structure matches the template.
- FAIL: Header names, order, or levels differ from the template.

## 4. Statistics by Phase

Statistics must be tracked by phase.

### Phase 1 - Spec Pack

Files:

- spec-pack.md
- sources.md
- open-issues.md

### Phase 2 - Working Files Initialization

Files:

- context.md

### Phase 3 - Implementation Plan

Files:

- impact-analysis.md
- impl_plan_template.md

### Phase 4 - Review Checklist

Files:

- review-checklist.md

### Phase 5 - Implementation and Review

Files:

- self-review.md
- codex-review.md

### Phase 6 - Test Plan and Results

Files:

- test-plan.md
- test-results.md

### Phase 7 - Blackbox Test

Files:

- blackbox-testcases.md
- test-data.md

### Phase 8 - Report

Files:

- report.md

## 5. Extensibility Design

Do not hard-code the logic for the current 8 phases.

Design the code so that the following can be done without modifying core business logic:

- Add a new phase.
- Remove a phase.
- Add a file to a phase.
- Remove a file from a phase.

## 6. Database Design

Identify a suitable existing table to store the following metrics:

- `total_check_count`
- `template_match_count`

If no suitable table exists, propose a new table.

The table name should be generic enough to support future metric tracking requirements.

### Data Update Logic

For each Pull Request processing:

```text
total_check_count += 1
```

If the validation result is PASS:

```text
template_match_count += 1
```

## 7. API for Frontend

Example:

```http
GET /api/template-usage/statistics
```

Response:

```json
[
  {
    "phaseCode": "SPEC_PACK",
    "phaseName": "Spec Pack",
    "totalCheckCount": 120,
    "templateMatchCount": 96,
    "usageRate": 80
  }
]
```

Calculation:

```text
usageRate = templateMatchCount / totalCheckCount * 100
```

## 8. Display on PM Dashboard

Screen:

```text
EDCAP_FE/src/pages/pm-dashboard/components/TicketDetailDrawer.tsx
```

### UI Requirements

Display the template usage rate for each phase.

Suggested layout:

```text
Phase Name
(Usage Count / Total Count)
Usage Rate (%)
```

If the total count is 0, display `-` instead of a percentage.

### Displayed Data

- Phase Name
- Total Check Count
- Template Match Count
- Usage Rate (%)

## Acceptance Criteria

### Backend

- Automatically validate ticket files when a Pull Request is processed.
- Search templates according to the specified priority order.
- Compare only the document header structure, not the content.
- Skip validation when a template cannot be found.
- Record statistics in the database by phase.
- Support future phase/file configuration changes with minimal code changes.

### Frontend

- Display statistics in `TicketDetailDrawer`.
- Show template usage percentages by phase.
- Retrieve data from the backend API.
- Display:
  - Total Check Count
  - Template Match Count
  - Usage Rate (%)
