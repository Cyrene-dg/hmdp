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
then proves the development rollback before dropping that temporary database.
