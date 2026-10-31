# Publishing the reviewed wiki pages

The files in `docs/wiki/` are the reviewed 7.1 development reference. They cover all 27 previously listed wiki pages, three added feature pages, and the sidebar. The main README identifies the difference between the main 7.0 code line and the 7.1 development branches.

GitHub stores a repository wiki in a separate Git repository and does not support pull requests against that wiki. A documentation PR against the ordinary repository therefore prepares reviewable wiki changes; merging it does not publish them into the live wiki automatically.

The connector used to prepare this PR supports repository files and PRs but has no wiki write operation. These source pages have **not** been published to the live wiki by that connector. A maintainer with authenticated Git access to the wiki can publish the reviewed pages after the documentation and relevant code changes are accepted.

From a checkout containing this documentation:

```sh
python3 docs/export_wiki.py
git clone https://github.com/Luis-GameDev/Minecraft-Better-Horses-Plugin.wiki.git work/wiki-publish
python3 docs/export_wiki.py --destination work/wiki-publish
git -C work/wiki-publish diff
```

The exporter writes the Markdown pages locally and converts relative `.md` links into GitHub wiki page routes. It preserves anchors and external URLs. It does not commit, push, alter authentication, or delete unrelated wiki pages. Review existing wiki changes before replacing them; rerun against a fresh wiki checkout when another maintainer has edited the live wiki.

After reviewing the resulting diff, commit and push it with the maintainer's ordinary authenticated Git workflow. The wiki's existing default branch should be retained. If another working branch is created, use the project's `fix/` or `feature/` naming convention.

Verify the live Home page, sidebar, API examples, configuration examples, permissions, shears-neutering, individual size and optional-null command documentation after publication. The reference describes the fixes and features in the 7.1 development PRs; it must not be presented as functionality already available in an older released 7.0 JAR.
