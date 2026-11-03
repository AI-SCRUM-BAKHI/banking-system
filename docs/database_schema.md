# Database Schema Report - Deployed Schema

> DBML diagram for dbdiagram.io: [`database_schema.dbml`](./database_schema.dbml)

## 1. Overview

This report describes the application schema in Neon PostgreSQL and the target schema managed by Flyway. All application objects are located in the `public` schema.

The database supports a basic internal banking fraud-review application.

The database contains four tables:

1. `users`
2. `fraud_transactions`
3. `fraud_rule_hits`
4. `transaction_chain`

The live database inspection found no Flyway history table and no views. Migration `V2` creates the three query views used by the backend and graph frontend. Flyway baselines an existing non-empty database at version 1 before applying `V2`.

The earlier `V1` migration creates a `schema_metadata` setup table. It is retained to preserve migration history and is not part of the business model; it is absent from the inspected Neon database. A fresh local database will therefore have this additional legacy table.

**Application database role:** `neondb_owner`

The other roles shown by Neon, such as `cloud_admin`, `neon_service`, `neon_superuser`, and PostgreSQL predefined roles, are platform/database infrastructure roles. They are not application users and are not part of the application data model.

## 2. Table: `users`

Stores bank employees who can log in to the application.

| Column | Data type | Key/constraint | Description |
|---|---|---|---|
| `id` | UUID | Primary Key | Internal user identifier |
| `username` | VARCHAR(50) | Unique, Not Null | Login username |
| `password_hash` | VARCHAR(100) | Not Null | BCrypt password hash; plaintext passwords are not stored |
| `full_name` | VARCHAR(120) | Not Null | Employee display name |
| `email` | VARCHAR(254) | Unique, Not Null | Employee email address |
| `role` | VARCHAR(20) | Not Null, Check | `ADMIN` or `BANK_USER` |
| `status` | VARCHAR(20) | Not Null, Check | `ACTIVE` or `DISABLED` |
| `created_at` | TIMESTAMPTZ | Not Null | Record creation time |
| `updated_at` | TIMESTAMPTZ | Not Null | Last update time |

**Primary Key:** `id`

**Foreign Keys:** None

**Deployed constraints:**

- `users_pkey`: primary key on `id`.
- `users_username_key`: unique `username`.
- `users_email_key`: unique `email`.
- `users_role_check`: allows `ADMIN` or `BANK_USER`.
- `users_status_check`: allows `ACTIVE` or `DISABLED`.

## 3. Table: `fraud_transactions`

Stores only transactions that have already been marked as suspicious or fraudulent by the fraud-detection system.

| Column | Data type | Key/constraint | Description |
|---|---|---|---|
| `id` | UUID | Primary Key | Internal fraud transaction identifier |
| `transaction_ref` | VARCHAR(80) | Unique, Not Null | Transaction reference from the source system |
| `from_account_ref` | VARCHAR(80) | Not Null | Source account reference used as a graph node ID |
| `from_account_masked` | VARCHAR(40) | Not Null | Masked source account number |
| `from_customer_name` | VARCHAR(120) | Nullable | Generated or masked source customer name |
| `to_account_ref` | VARCHAR(80) | Not Null | Destination account reference used as a graph node ID |
| `to_account_masked` | VARCHAR(40) | Not Null | Masked destination account number |
| `to_customer_name` | VARCHAR(120) | Nullable | Generated or masked destination customer name |
| `amount` | NUMERIC(19,4) | Not Null, Check > 0 | Transaction amount |
| `currency` | CHAR(3) | Not Null | Currency code; default is `VND` |
| `transaction_time` | TIMESTAMPTZ | Not Null | Time the transaction occurred |
| `risk_score` | NUMERIC(5,2) | Not Null, Check 0–100 | Fraud risk score |
| `risk_level` | VARCHAR(10) | Not Null, Check | `LOW`, `MEDIUM`, `HIGH`, or `CRITICAL` |
| `fraud_reason` | VARCHAR(500) | Not Null | General reason the transaction was flagged |
| `review_status` | VARCHAR(30) | Not Null, Check | `NEW`, `REVIEWING`, `CONFIRMED_FRAUD`, or `FALSE_POSITIVE` |
| `created_at` | TIMESTAMPTZ | Not Null | Time the record was inserted into this database |

**Primary Key:** `id`

**Foreign Keys:** None

**Deployed constraints:**

- `fraud_transactions_pkey`: primary key on `id`.
- `fraud_transactions_transaction_ref_key`: unique `transaction_ref`.
- `fraud_transactions_amount_check`: requires `amount > 0`.
- `fraud_transactions_risk_score_check`: requires a score from 0 to 100.
- `fraud_transactions_risk_level_check`: validates the risk level.
- `fraud_transactions_review_status_check`: validates the review status.

## 4. Table: `fraud_rule_hits`

Stores the individual rules that contributed to the risk score of a fraud transaction.

| Column | Data type | Key/constraint | Description |
|---|---|---|---|
| `id` | UUID | Primary Key | Rule-hit identifier |
| `fraud_transaction_id` | UUID | Foreign Key, Not Null | Fraud transaction evaluated by the rule |
| `rule_code` | VARCHAR(50) | Not Null | Stable rule code |
| `rule_name` | VARCHAR(160) | Not Null | Human-readable rule name |
| `score_contribution` | NUMERIC(5,2) | Not Null, Check 0–100 | Points contributed by the rule |
| `explanation` | VARCHAR(500) | Not Null | Explanation shown to the bank employee |
| `created_at` | TIMESTAMPTZ | Not Null | Rule-hit creation time |

**Primary Key:** `id`

**Foreign Key:**

```text
fraud_rule_hits.fraud_transaction_id
    -> fraud_transactions.id
```

The foreign key uses `ON DELETE CASCADE`. Deleting a fraud transaction also deletes its rule hits.

**Unique constraint:** `(fraud_transaction_id, rule_code)`

This prevents the same rule from being stored twice for one transaction.

**Deployed constraints:**

- `fraud_rule_hits_pkey`: primary key on `id`.
- `fraud_rule_hits_fraud_transaction_id_fkey`: foreign key to `fraud_transactions.id` with `ON DELETE CASCADE`.
- `fraud_rule_hits_fraud_transaction_id_rule_code_key`: unique transaction/rule pair.
- `fraud_rule_hits_score_contribution_check`: requires a score from 0 to 100.

## 5. Table: `transaction_chain`

Stores the root fraud transaction and any following transactions discovered during money-flow tracing.

| Column | Data type | Key/constraint | Description |
|---|---|---|---|
| `id` | UUID | Primary Key | Chain record and graph edge identifier |
| `root_fraud_transaction_id` | UUID | Foreign Key, Not Null | Fraud transaction being traced |
| `transaction_ref` | VARCHAR(80) | Not Null | Reference of this transaction in the chain |
| `from_account_ref` | VARCHAR(80) | Not Null | Source graph node ID |
| `from_account_masked` | VARCHAR(40) | Not Null | Source node label |
| `from_customer_name` | VARCHAR(120) | Nullable | Generated or masked source customer name |
| `to_account_ref` | VARCHAR(80) | Not Null | Target graph node ID |
| `to_account_masked` | VARCHAR(40) | Not Null | Target node label |
| `to_customer_name` | VARCHAR(120) | Nullable | Generated or masked destination customer name |
| `amount` | NUMERIC(19,4) | Not Null, Check > 0 | Transferred amount |
| `currency` | CHAR(3) | Not Null | Currency code; default is `VND` |
| `transaction_time` | TIMESTAMPTZ | Not Null | Time of this transfer |
| `hop_level` | INTEGER | Not Null, Check >= 0 | Position in the chain |
| `is_root` | BOOLEAN | Not Null | Identifies the original fraud transaction |
| `created_at` | TIMESTAMPTZ | Not Null | Chain record creation time |

**Primary Key:** `id`

**Foreign Key:**

```text
transaction_chain.root_fraud_transaction_id
    -> fraud_transactions.id
```

The foreign key uses `ON DELETE CASCADE`. Deleting a fraud transaction also deletes its transaction chain.

**Unique constraint:** `(root_fraud_transaction_id, transaction_ref)`

**Chain constraint:**

- The root transaction must have `is_root = true` and `hop_level = 0`.
- Following transactions must have `is_root = false` and `hop_level > 0`.

**Deployed constraints:**

- `transaction_chain_pkey`: primary key on `id`.
- `transaction_chain_root_fraud_transaction_id_fkey`: foreign key to `fraud_transactions.id` with `ON DELETE CASCADE`.
- `transaction_chain_root_fraud_transaction_id_transaction_ref_key`: unique root transaction/chain transaction pair.
- `transaction_chain_amount_check`: requires `amount > 0`.
- `transaction_chain_hop_level_check`: requires `hop_level >= 0`.
- `transaction_chain_check`: validates the relationship between `is_root` and `hop_level`.

## 6. Table Relationships

```text
users
  No foreign-key relationship in the basic version

fraud_transactions (1)
  |
  +----< fraud_rule_hits (many)
  |
  +----< transaction_chain (many)
```

One fraud transaction can have multiple triggered rules and multiple transaction-chain records.

## 7. Database Views (created by Flyway `V2`)

### `v_fraud_details`

Returns fraud transaction details together with its rule hits as a JSON array. Each JSON item contains `id`, `ruleCode`, `ruleName`, `scoreContribution`, `explanation` and `createdAt`. This view is created by `V2`; it was not present during inspection of the current Neon database.

### `v_fraud_graph_nodes`

Creates unique graph nodes from the source and destination accounts in `transaction_chain`. This view is created by `V2`.

Main output columns:

- `root_fraud_transaction_id`
- `node_id`
- `account_label`
- `customer_name`

### `v_fraud_graph_edges`

Returns transaction-chain records in a graph-edge format. This view is created by `V2`.

Main output columns:

- `edge_id`
- `root_fraud_transaction_id`
- `transaction_ref`
- `source_node_id`
- `target_node_id`
- `amount`
- `currency`
- `transaction_time`
- `hop_level`
- `is_root`

## 8. Neon Roles and Permissions

Neon created and manages the database/platform roles shown in the schema export. The application tables grant `neondb_owner` the following privileges with grant option:

- `SELECT`
- `INSERT`
- `UPDATE`
- `DELETE`
- `TRUNCATE`
- `REFERENCES`
- `TRIGGER`

These grants control database access and do not represent application-level roles. Application roles remain `ADMIN` and `BANK_USER` in the `users.role` column.

The team should not manually recreate, modify, or delete Neon system roles such as `cloud_admin`, `neon_service`, or `neon_superuser`.

## 9. Indexes

| Index | Table | Purpose |
|---|---|---|
| `idx_fraud_transactions_created` | `fraud_transactions` | Sort/filter recently inserted fraud transactions |
| `idx_fraud_transactions_risk` | `fraud_transactions` | Sort/filter by risk score and transaction time |
| `idx_rule_hits_transaction` | `fraud_rule_hits` | Find all rule hits for a fraud transaction |
| `idx_transaction_chain_root_hop` | `transaction_chain` | Load a transaction chain in hop order |

PostgreSQL also creates unique indexes automatically for primary-key and unique constraints, including:

- `users_pkey`, `users_username_key`, `users_email_key`
- `fraud_transactions_pkey`, `fraud_transactions_transaction_ref_key`
- `fraud_rule_hits_pkey`, `fraud_rule_hits_fraud_transaction_id_rule_code_key`
- `transaction_chain_pkey`, `transaction_chain_root_fraud_transaction_id_transaction_ref_key`

## 10. Backend Connection and Interaction

### 10.1 Connection requirements

The Java backend connects directly to Neon PostgreSQL. The React frontend must never connect directly to the database.

```text
React Frontend
      |
      | HTTP/JSON
      v
Java Spring Boot Backend
      |
      | PostgreSQL JDBC over TLS
      v
Neon PostgreSQL
      └── Database schema: public
```

The backend requires the following Neon connection information:

- Database host
- Port `5432`
- Database name, normally `neondb`
- Database username, currently `neondb_owner`
- Database password
- TLS setting `sslmode=require`

The project uses the default PostgreSQL schema `public`. No additional application schema is required.

For the backend application, the pooled Neon connection is recommended. Store the PostgreSQL connection URI in the ignored `backend/.env` file as `DATABASE_URL`. The URI must include the username and password, so this is the only database connection variable. Database credentials must not be committed to GitHub.

### 10.2 Spring Boot dependencies

Spring Data JPA and PostgreSQL JDBC are managed in the Gradle backend modules. Business entities and repositories live in `identity` or `fraud`; Flyway migrations live in `database/src/main/resources/db/migration`.

### 10.3 Spring Boot configuration

`application.yml` imports the optional `backend/.env` file and maps the connection URI into the PostgreSQL datasource. Flyway remains enabled. `baseline-on-migrate` at version 1 supports the existing manually-created Neon schema: Flyway records the baseline, then applies `V2` idempotently. On an empty local database, Flyway runs `V1` and `V2`. This automatic baseline assumes the existing tables match this report; Flyway does not repair a different schema just because `V2` uses `IF NOT EXISTS`.

```env
DATABASE_URL=postgresql://USER:PASSWORD@HOST:5432/DATABASE?sslmode=require
```

For local PostgreSQL, use a URI with local credentials as shown in `backend/.env.example`:

```env
DATABASE_URL=postgresql://postgres:postgres@localhost:5432/scrum_ai
```

The real connection string and password must not be included in this report, committed to Git, exposed in frontend code, or sent to a browser.

Hibernate uses `ddl-auto=validate`; Flyway owns schema creation and upgrades. The backend does not run Hibernate schema generation.

### 10.4 Backend database usage

| Backend function | Database object | Operation |
|---|---|---|
| Employee login | `users` | Read user by `username` and validate the BCrypt password hash |
| Receive flagged transaction | `fraud_transactions` | Insert a transaction marked by the fraud-detection system |
| Store fraud explanations | `fraud_rule_hits` | Insert the triggered rules and their score contributions |
| Store traced transfers | `transaction_chain` | Insert the root transaction and optional following transactions |
| Fraud transaction list/detail | `v_fraud_details` | Read transaction data together with rule hits |
| Money-flow graph nodes | `v_fraud_graph_nodes` | Read unique account nodes |
| Money-flow graph edges | `v_fraud_graph_edges` | Read directed transfer edges in hop order |
| Review a transaction | `fraud_transactions.review_status` | Update the employee review result |

When the backend receives a fraud transaction together with its rule hits and chain, all inserts should be executed in one database transaction. If one insert fails, the complete operation should be rolled back.

## 11. Summary

| Table | Primary Key | Foreign Key |
|---|---|---|
| `users` | `id` | None |
| `fraud_transactions` | `id` | None |
| `fraud_rule_hits` | `id` | `fraud_transaction_id -> fraud_transactions.id` |
| `transaction_chain` | `id` | `root_fraud_transaction_id -> fraud_transactions.id` |
