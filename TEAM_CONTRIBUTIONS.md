# Team Contribution Workflow

Use one shared GitHub repository and protect `main`. Each member should implement, test, document, and review genuine work through a feature branch and pull request.

| Member | Recommended branch |
|---|---|
| IT25103826 - Dasanayake W. G. A. N. | `feature/employee-profiles` |
| IT25103827 - Medawala M. M. G. U. S. | `feature/attendance-shifts` |
| IT25103828 - Pradeesh M. | `feature/user-access` |
| IT25103829 - Nandasena M. B. | `feature/leave-workflow` |
| IT25103830 - Ineeshiya W. K. M. M. | `feature/performance-kpis` |
| IT25103831 - Wimalasara A. G. H. L. | `feature/payroll-payslips` |

For each module: create assigned GitHub issues, make focused commits, open a pull request, request another member's review, address review comments, run `mvn clean package`, and merge only after approval. Do not fabricate contribution history.

During the viva show Code, Commits, Pull Requests, Issues, Insights > Contributors, the role matrix, the running system, and each member's module.

## Conflict-resistant working agreement

Use `docs/MEMBER_FILE_OWNERSHIP.md` as the authoritative ownership map and `docs/GITHUB_COLLABORATION_GUIDE.md` for the exact Git workflow. Module-specific frontend styles are separated under `src/main/webapp/assets/css/modules/`. Shared backend and SQL files require integration-coordinator approval before editing.
