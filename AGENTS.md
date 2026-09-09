# Qinghe Marketing Platform Repository Rules

## Scope and objective

- These rules apply to the whole repository.
- The repository is evolving an existing HM Dianping Plus codebase into the Qinghe Tea marketing-rights platform.
- Reuse existing technical foundations when they fit, but do not rename legacy behavior and present it as a new implementation.
- Qinghe is a simulated business case backed by real code and tests. Never claim a real client, real vendor integration, production deployment, production traffic, or unmeasured performance.

## Sources of truth

Before changing a business rule, read the relevant documents in this order:

1. `docs/product/青禾茶饮_优惠券权益与门店履约模块_PRD_v1.0.md`
2. `docs/contracts/青禾茶饮_一期接口设计说明_v1.0.md` and machine-readable contracts in `docs/contracts/`
3. `docs/technical/青禾茶饮_优惠券权益与门店履约模块技术方案_v0.1.md`
4. `docs/management/青禾茶饮_一期开发任务拆分与估时_v0.1.md`
5. The newest applicable checkpoint in `docs/management/`

When documents disagree, do not silently invent a compromise. Preserve the latest explicit decision, record the discrepancy, and stop only when the choice changes the agreed business boundary.

## Work-package discipline

- Work serially on one primary WP unless the plan explicitly allows a safe supporting task.
- Do not start a dependent WP before its predecessor passes its Gate.
- A WP is `DONE` only when its scoped code is committed, relevant success/repeat/failure tests pass, changed contracts and state documentation are updated, and the result provides a stable input to the next WP.
- A design, Mock, disabled test, generated skeleton, or unexecuted scenario is not an implemented business capability.
- Update the management plan and leave a checkpoint with exact commands, results, remaining risks, and the next safe action.

## Git workflow

- Keep `main` stable. Do not develop or commit directly on `main`.
- Use `qinghe/integration` as the Qinghe integration branch and `qinghe/wpNN-short-name` for work-package branches.
- Make scoped commits that explain one coherent change. Do not use `git add .` or mix unrelated legacy, generated, documentation, and business changes in one commit.
- Prefer commit subjects such as `feat(scope): ...`, `fix(scope): ...`, `test(scope): ...`, `docs(scope): ...`, and `chore(scope): ...`.
- Merge a WP only after its Gate passes. Never rewrite shared history, force-push, delete remote branches, or discard user changes without explicit authorization.
- Do not commit secrets, local credentials, temporary databases, logs, generated review files, IDE state, or `.worktrees/`.

## Code and domain boundaries

- Put new production business code under `com.qinghe.marketing`.
- Reuse `com.hmdp` only through an explicit adapter or stable shared infrastructure boundary; do not add new Qinghe business semantics to legacy community modules.
- Keep ClaimRequest (the asynchronous application) distinct from MemberEntitlement (the issued right).
- Keep Redemption (the fulfillment fact), SubsidyCandidate (the financial candidate), ReconciliationDifference, and SettlementBatch as separate concepts.
- Direct stores create no subsidy candidate. Franchise stores create at most one candidate after a successful redemption in the same business transaction.
- Store money as integer fen, inject business time through `BusinessClock`, and make external/request identifiers idempotent and auditable.

## Verification and environment safety

- Run the narrowest relevant tests first, followed by the WP Gate suite.
- Do not use the legacy full-context test as the default Qinghe smoke test: it can connect to local Redis, MySQL, and RabbitMQ and consume legacy queue messages.
- Use isolated test data and disposable databases for migrations. Verify both forward migration and rollback.
- Inject credentials through environment or untracked local configuration; never print or commit them.
- Do not run destructive database or filesystem operations against an unresolved or broad path.

## Delivery and resume rules

- Resume from the newest verified checkpoint and do not redo passing work without evidence that it is stale.
- Report implemented behavior separately from simulated contracts and future work.
- Resume bullets and project claims may be written only from code and verification evidence that currently exists in the repository.
