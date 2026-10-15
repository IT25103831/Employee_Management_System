# GitHub Collaboration Guide

## One-time repository setup

1. Create one empty GitHub repository.
2. Push only this integrated `EmployeeManagementSystem` folder.
3. Add all six members as collaborators.
4. Protect `main`: require a pull request, one approval, and the Maven CI check.
5. Copy `.github/CODEOWNERS.example` to `.github/CODEOWNERS` only after replacing every sample owner with the members' real GitHub usernames.

## Every member starts correctly

```bash
git clone REPOSITORY_URL
cd EmployeeManagementSystem
git switch main
git pull origin main
git switch -c feature/MEMBER-MODULE
```

Use the exact recommended branch in `MEMBER_FILE_OWNERSHIP.md`.

## Before every work session

```bash
git switch main
git pull origin main
git switch feature/MEMBER-MODULE
git merge main
```

Resolve any issue before starting new edits. Never use `git push --force` on shared branches.

## Commit and push

```bash
git status
git add PATHS-YOU-OWN
git commit -m "feat(module): describe the completed operation"
git push -u origin feature/MEMBER-MODULE
```

Open a pull request into `main`, complete the checklist, request another member's review, and merge only after CI succeeds.

## Conflict prevention rules

1. One member owns one module branch and its module-specific files.
2. Do not use “Reformat Code” on the whole project.
3. Do not commit `.idea`, `target`, logs or real database credentials.
4. Announce shared-file work before editing it.
5. Keep pull requests small and merge them frequently.
6. After any pull request is merged, every other member updates their branch from `main`.
7. The integration coordinator handles release tags and final ZIP/WAR creation.

## Viva evidence

Show the repository Code page, Issues, member branches, pull requests with reviews, Actions build results, commit history, Insights/Contributors, module ownership table, and a live demonstration of each member's operations.
