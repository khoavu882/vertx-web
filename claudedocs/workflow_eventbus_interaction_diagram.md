# Workflow Plan: Add Missing Diagram to `eventbus_interaction.md`

**Source Request:** Missing workflow diagram in `claudedocs/eventbus_interaction.md`  
**Created:** February 12, 2026  
**Mode:** Planning only (no implementation changes in this step)

## Goal
Add a clear workflow diagram that visualizes HTTP-to-EventBus-to-Consumer request/reply paths, including failure mapping and health-check flow.

## Scope
- In scope:
  - Add one primary workflow diagram section to `claudedocs/eventbus_interaction.md`
  - Ensure diagram covers analytics flow, batch flow, and health-check probe flow
  - Align diagram nodes with existing addresses and class/file names
- Out of scope:
  - Refactoring Java source code
  - Changing EventBus addresses or runtime behavior
  - Rewriting full document structure

## Implementation Phases

### Phase 1: Baseline Extraction
- Task 1.1: Extract current interaction steps from `claudedocs/eventbus_interaction.md`
- Task 1.2: Cross-check publisher/consumer endpoints and addresses in source files
- Task 1.3: Freeze canonical terms for diagram labels (endpoint names, addresses, error mapping)
- Deliverable: Approved diagram content inventory

### Phase 2: Diagram Design
- Task 2.1: Choose diagram format (Mermaid sequence diagram recommended)
- Task 2.2: Define participants:
  - Client
  - AppVerticle HTTP Router layer
  - EventBus
  - Worker Consumer layer (`AnalyticsConsumer`, `BatchOperationConsumer`, `HealthCheckConsumer`)
- Task 2.3: Define branches:
  - success reply path
  - failure path (`message.fail` -> `ReplyException.failureCode`)
  - health probe path
- Deliverable: Final diagram draft text

### Phase 3: Documentation Integration
- Task 3.1: Add new section `## Workflow Diagram` after context/address sections
- Task 3.2: Insert Mermaid block and brief legend
- Task 3.3: Add one short note linking diagram steps to existing sections (analytics, batch, health)
- Deliverable: Updated markdown with embedded workflow diagram

### Phase 4: Validation and Quality Gate
- Task 4.1: Verify diagram labels match actual addresses:
  - `app.worker.analytics-report`
  - `app.worker.batch-operation`
  - `app.health.check`
- Task 4.2: Verify failure mapping notes match current router behavior
- Task 4.3: Render check in markdown viewer (diagram compiles without syntax errors)
- Deliverable: Verified documentation update ready for commit

## Dependency Map
- `Phase 1` -> required before `Phase 2`
- `Phase 2` -> required before `Phase 3`
- `Phase 3` -> required before `Phase 4`

## Checkpoints
- Checkpoint A (after Phase 1): Interaction inventory approved
- Checkpoint B (after Phase 2): Diagram draft approved
- Checkpoint C (after Phase 4): Render + consistency validation passed

## Risks and Mitigations
- Risk: Diagram diverges from code reality
  - Mitigation: source-level cross-check before finalization
- Risk: Mermaid syntax incompatibility in target viewer
  - Mitigation: keep syntax minimal and run render verification
- Risk: Overly dense diagram
  - Mitigation: use one primary sequence diagram with concise labels

## Execution Order (Linear)
1. Baseline extraction
2. Diagram design draft
3. Markdown insertion
4. Validation gate

## Handoff
Use `/sc:implement` next to execute this plan and patch `claudedocs/eventbus_interaction.md`.
