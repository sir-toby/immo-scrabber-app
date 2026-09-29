# Issue tracker: GitHub

Issues and specs for this repo live as GitHub issues. Use the `gh` CLI, but **only `gh api` (the REST API)**: Claude Code cloud sessions block GitHub's GraphQL API, and the high-level commands (`gh issue list/view/create/edit/comment/close`, `gh pr view/list`) are built on it, so they fail there with `HTTP 403: GitHub GraphQL is not available`. `gh api` works both in the cloud and locally.

`{owner}/{repo}` is filled in by `gh api` from the clone's `git remote`, so run these from inside the repo.

## Conventions

- **Create an issue**:
  ```bash
  gh api repos/{owner}/{repo}/issues -f title="..." -f body="$(cat <<'EOF'
  multi-line body
  EOF
  )" -f 'labels[]=needs-triage' --jq '.number'
  ```
- **Read an issue**: `gh api repos/{owner}/{repo}/issues/<number> --jq '{number, title, state, body, labels: [.labels[].name], assignees: [.assignees[].login]}'`, then its comments with `gh api --paginate repos/{owner}/{repo}/issues/<number>/comments --jq '.[] | {user: .user.login, body}'`.
- **List issues**: `gh api --paginate 'repos/{owner}/{repo}/issues?state=open&per_page=100' --jq '.[] | select(.pull_request | not) | {number, title, body, labels: [.labels[].name]}'`. Filter by label with `&labels=a,b` (comma = AND) and by state with `state=open|closed|all`. The endpoint also returns PRs, so keep the `select(.pull_request | not)`.
- **Comment on an issue**: `gh api repos/{owner}/{repo}/issues/<number>/comments -f body="..."`
- **Apply a label**: `gh api repos/{owner}/{repo}/issues/<number>/labels -f 'labels[]=...'`
- **Remove a label**: `gh api -X DELETE repos/{owner}/{repo}/issues/<number>/labels/<label>` (URL-encode the label name, e.g. `wayfinder%3Amap`).
- **Create a missing label**: `gh api repos/{owner}/{repo}/labels -f name="..." -f color="ededed"`. A `422 already_exists` means it is already there; ignore it.
- **Close**: comment first (above), then `gh api -X PATCH repos/{owner}/{repo}/issues/<number> -f state=closed -f state_reason=completed` (`not_planned` for won't-fix/duplicates).

## Pull requests as a triage surface

**PRs as a request surface: no.** _(Set to `yes` if this repo treats external PRs as feature requests; `/triage` reads this flag.)_

When set to `yes`, PRs run through the same labels and states as issues. PR comments, labels and close go through the same `issues/<number>` endpoints above, since GitHub treats every PR as an issue too.

- **Read a PR**: `gh api repos/{owner}/{repo}/pulls/<number> --jq '{number, title, body, state, user: .user.login, author_association}'`, and the diff with `gh api -H 'Accept: application/vnd.github.diff' repos/{owner}/{repo}/pulls/<number>`.
- **List external PRs for triage**: `gh api --paginate 'repos/{owner}/{repo}/pulls?state=open&per_page=100' --jq '.[] | select(.author_association == "CONTRIBUTOR" or .author_association == "FIRST_TIME_CONTRIBUTOR" or .author_association == "NONE") | {number, title, body, labels: [.labels[].name], user: .user.login}'`
- **Close a PR**: `gh api -X PATCH repos/{owner}/{repo}/pulls/<number> -f state=closed`

GitHub shares one number space across issues and PRs, so a bare `#42` may be either: `gh api repos/{owner}/{repo}/issues/42` answers for both, and a `pull_request` key in the result means it is a PR.

## When a skill says "publish to the issue tracker"

Create a GitHub issue (see above).

## When a skill says "fetch the relevant ticket"

Read the issue and its comments (see above).

## Wayfinding operations

Used by `/wayfinder`. The **map** is a single issue with **child** issues as tickets.

- **Map**: a single issue labelled `wayfinder:map`, holding the Notes / Decisions-so-far / Fog body. Create it as above with `-f 'labels[]=wayfinder:map'`.
- **Child ticket**: an issue linked to the map as a GitHub sub-issue: `gh api repos/{owner}/{repo}/issues/<map>/sub_issues -F sub_issue_id=<child-db-id>`, where `<child-db-id>` is the child's numeric **database id** (`gh api repos/{owner}/{repo}/issues/<n> --jq .id`, _not_ the `#number` or `node_id`). List them with `gh api repos/{owner}/{repo}/issues/<map>/sub_issues --jq '.[] | {number, state, title}'`. Where sub-issues aren't enabled, add the child to a task list in the map body and put `Part of #<map>` at the top of the child body. Labels: `wayfinder:<type>` (`research`/`prototype`/`grilling`/`task`). Once claimed, the ticket is assigned to the driving dev.
- **Blocking**: GitHub's **native issue dependencies**, the canonical, UI-visible representation. Add an edge with `gh api --method POST repos/{owner}/{repo}/issues/<child>/dependencies/blocked_by -F issue_id=<blocker-db-id>` (database id as above). GitHub reports `issue_dependencies_summary.blocked_by` on the issue (open blockers only, the live gate). Where dependencies aren't available, fall back to a `Blocked by: #<n>, #<n>` line at the top of the child body. A ticket is unblocked when every blocker is closed.
- **Frontier query**: list the map's open sub-issues (or its task list), drop any with an open blocker (`issue_dependencies_summary.blocked_by > 0`, or an open issue in the `Blocked by` line) or an assignee; first in map order wins.
- **Claim**: `gh api repos/{owner}/{repo}/issues/<n>/assignees -f "assignees[]=$(gh api user --jq .login)"`, the session's first write.
- **Resolve**: comment the answer on the issue, close it (see above), then append a context pointer (gist + link) to the map's Decisions-so-far by editing the map body: `gh api -X PATCH repos/{owner}/{repo}/issues/<map> -f body="..."`.
