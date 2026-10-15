# 2026-10-02 targeted fixes

See `FIX_REPORT.md` for this delivery: employee reactivation, atomic basic account creation by HR, HR attendance restriction, optional shifts, own profile/photos, tasks, salary confirmation, employee scope protections, professional wording/fonts and encoding. Existing implementation and data are retained; migration 002 is additive except for widening employee name/address columns to Unicode.

# Changelog

## 1.0.0 - Viva Release

- Added seven-role authentication and authorization.
- Added EP01-EP05 employee profile workflows.
- Added AT01-AT05 attendance and shift workflows.
- Added RA01-RA05 account security and audit workflows.
- Added LA01-LA05 multi-level leave workflow.
- Added PE01-PE04 reviews and KPI tracking.
- Added PR01-PR05 configurable payroll processing.
- Added live dashboards, notifications, CSV reports, responsive UI, database setup, sample accounts, tests and team documentation.

## 2026-09-30 — targeted scope and validation upgrade

Preserved the supplied architecture, source paths and UI assets. Added attendance absence/summary/correction workflows, supervisor balances and pending leave lifecycle, Operations Manager review/KPI ownership and reports, account updates/session revocation, and payroll drafts/salary setup. Added a non-destructive migration and a reproducible regression suite. See `docs/UPGRADE_REPORT.md` for details and limitations: database workflows are not verified in the delivery environment.
