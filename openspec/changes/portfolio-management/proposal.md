## Why

Currently, trading plans are managed as a flat list with no grouping capability. Users cannot compare performance across different trading strategies or styles. This change introduces a **Portfolio** concept — a higher-dimensional grouping layer — so users can organize plans by strategy type, compare performance across portfolios, and manage multiple concurrent plan groups.

## What Changes

- **New Portfolio entity**: A portfolio groups multiple trading plans. Each portfolio has name, description, style tag, and userId (reserved for future multi-user isolation).
- **Plan-to-Portfolio association**: Each trading plan belongs to exactly one portfolio via `portfolioId` foreign key.
- **Portfolio CRUD API**: Endpoints to create, read, update, and delete portfolios.
- **Portfolio-scoped plan queries**: Filter plans by portfolio when listing or creating.
- **Portfolio statistics**: Compute aggregate statistics (total return, holding period, trade count) per portfolio using existing calculation logic.
- **Data migration**: Existing plans are migrated into a default portfolio named "预案组合A".
- **userId field reservation**: Add `userId` to Portfolio, Plan, ActualTrade, and DailySnapshot entities for future multi-user data isolation.

## Capabilities

### New Capabilities

- `portfolio`: Manage portfolios that group trading plans. Each portfolio has name, description, style, userId (reserved), and createdAt. Provides CRUD operations and statistics aggregation.
- `portfolio-plan-association`: Associate trading plans with portfolios. When creating or listing plans, portfolioId can be specified or filtered.
- `user-isolation-reservation`: Add userId field to Portfolio, Plan, ActualTrade, and DailySnapshot entities. Currently nullable (single-user mode), reserved for future authentication-based data filtering.

### Modified Capabilities

- `trading-plan`: Trading plans now belong to a portfolio. The plan creation and listing APIs accept/return portfolioId. Existing plans are migrated to a default portfolio on migration.
- `actual-trade`: ActualTrade records now include userId field for future isolation. No functional changes in current single-user mode.

## Impact

- **New entities**: `Portfolio`
- **Modified entities**: `Plan` (add portfolioId), `ActualTrade` (add userId), `DailySnapshot` (add userId)
- **New API endpoints**: `/api/portfolios` CRUD operations
- **Modified API endpoints**: `/api/plans` accepts/returns portfolioId
- **Database migrations**: V3__portfolio.sql (create portfolio table, add portfolioId to plan, add userId to actual_trade and daily_snapshot)
- **New frontend pages**: Portfolio list, portfolio create/edit
- **Modified frontend pages**: Plan list/plan create/plan edit with portfolio selector
