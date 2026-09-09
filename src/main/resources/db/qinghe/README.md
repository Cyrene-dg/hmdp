# Qinghe database migrations

`migration/` contains additive, forward migrations for tables owned by the Qinghe module.
They deliberately do not alter the legacy `tb_*` schema. The application does not run them
automatically yet, so starting the inherited application cannot unexpectedly mutate a local
database.

`rollback/` is only for disposable development databases before Qinghe business data exists.
After a migration carries business facts, rollback is forward-only: disable new writes, deploy
a compatible application version, and use a new corrective migration. Issued entitlements,
redemptions, reconciliation imports and confirmed settlement amounts must never be deleted by
a rollback script.

`QingheSchemaMigrationIT` proves V001 against a uniquely named temporary MySQL database and
then proves the development rollback before dropping that temporary database. V002 adds the
WP-02 platform session, versioned store import, POS credential-reference and nonce replay-guard
tables; R002 drops only those additions in dependency-safe order.

V003 adds the WP-03 campaign approval fact, immutable publication snapshot and the fields
needed to audit campaign and inventory state transitions. R003 removes only those WP-03
additions from a disposable database, before any campaign facts exist.

V004 adds the WP-04 Outbox lease owner, terminal publication fields and an append-only
delivery-attempt table. R004 is development-only and may be used only before claim and
Outbox facts exist; environments containing business facts must use a forward correction.
