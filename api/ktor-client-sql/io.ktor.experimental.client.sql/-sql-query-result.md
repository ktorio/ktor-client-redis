[ktor-client-sql](../index.md) / [io.ktor.experimental.client.sql](index.md) / [SqlQueryResult](./-sql-query-result.md)

# SqlQueryResult

`interface SqlQueryResult`

### Inheritors

| Name | Summary |
|---|---|
| [SqlBatchResult](-sql-batch-result/index.md) | `class SqlBatchResult<T : `[`SqlQueryResult`](./-sql-query-result.md)`> : `[`SqlQueryResult`](./-sql-query-result.md) |
| [SqlEmptyResult](-sql-empty-result.md) | `object SqlEmptyResult : `[`SqlQueryResult`](./-sql-query-result.md) |
| [SqlMessage](-sql-message/index.md) | `class SqlMessage : `[`SqlQueryResult`](./-sql-query-result.md) |
| [SqlStatement](-sql-statement/index.md) | `abstract class SqlStatement : `[`SqlQueryResult`](./-sql-query-result.md) |
| [SqlTables](-sql-tables/index.md) | `abstract class SqlTables : `[`SqlQueryResult`](./-sql-query-result.md)`, ReceiveChannel<`[`SqlTable`](-sql-table/index.md)`>` |