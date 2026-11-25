#!/usr/bin/env python3
"""
把指定范围内的提交改动合并打包到一个文件夹(保留仓库相对路径,同名文件取最新提交版本),
并生成一份总的「变更说明.md」;可同时导出 git bundle,现场用 git 直接取到提交。

与 pack-commits-by-folder.py 的区别:那个按提交逐一分文件夹;本脚本只出一个目录,
现场拿整个目录对照仓库覆盖即可,适合一次性交付多个提交。

用法:
  python pack-commits-to-one-folder.py                # 默认最近 2 个非 merge 提交
  python pack-commits-to-one-folder.py -n 5           # 最近 5 个非 merge 提交
  python pack-commits-to-one-folder.py c1 c2 c3       # 指定多个提交
  python pack-commits-to-one-folder.py -r A..B        # 指定提交区间(不含 A,含 B)
  python pack-commits-to-one-folder.py -M <merge哈希> # 打包合并提交(按其相对第一父的差异计变更)
  python pack-commits-to-one-folder.py -n 5 -M -z     # 允许 merge 提交纳入统计并压缩
  python pack-commits-to-one-folder.py -n 5 -z        # 生成后再压缩为 zip
  python pack-commits-to-one-folder.py -o 目录 ...    # 自定义输出目录

打包指定分支 + 指定区间(含 bundle):
  python pack-commits-to-one-folder.py -b dev -r v1.0..origin/dev --bundle
  python pack-commits-to-one-folder.py -b dev -n 3 --bundle --bundle-full
  python pack-commits-to-one-folder.py -b dev -r v1.0..dev --bundle-only

关于 merge 提交(-M):
  默认跳过 merge 提交。加 -M 后 merge 会被纳入:差异按其相对第一父(合入前的本地侧)
  计算,文件内容取 merge 落盘版本——两侧都改过的文件,合并结果只存在于 merge 树中,
  用被合入分支的普通提交打包会丢失本地侧改动,此时必须用 -M 打包 merge 本身。

关于 git bundle(--bundle):
  文件夹靠「整文件覆盖」交付,bundle 则把所选提交连同树和 blob 打成一个文件,现场
  `git fetch` 即可拿到提交历史、二进制文件、重命名与删除,不会漏掉无法用文本比对的改动。
  - bundle 只含所选提交所必需的对象;被剪掉的父提交记为「前置提交」,现场仓库必须已有,
    否则 fetch 会报 Repository lacks these prerequisite commits。
  - --bundle-full 改为打包整条分支历史(无前置提交,自包含,可直接 clone)。
  - git bundle 要求至少一个 ref 名,所选提交的最新端不是分支尖端时,脚本会临时创建
    refs/heads/incoming-<分支>-<时间戳> 再删除。
"""

import argparse
import datetime
import re
import shutil
import subprocess
import sys
from pathlib import Path

MAX_DIFF_LINES = 400

STATUS_CN = {"A": "新增", "M": "修改", "D": "删除", "R": "重命名", "C": "复制", "T": "类型变更", "U": "未合并"}


def git_bytes(*args: str) -> bytes | None:
    r = subprocess.run(["git", "-c", "core.quotepath=false", *args], capture_output=True)
    return r.stdout if r.returncode == 0 else None


def git_text(*args: str) -> str | None:
    out = git_bytes(*args)
    return out.decode("utf-8", "replace").strip() if out is not None else None


def git_run(*args: str) -> tuple[int, str, str]:
    """执行 git 命令,返回 (退出码, stdout, stderr)。bundle 系列的进度与结论多写在 stderr。"""
    r = subprocess.run(["git", "-c", "core.quotepath=false", *args], capture_output=True)
    return (
        r.returncode,
        r.stdout.decode("utf-8", "replace").strip(),
        r.stderr.decode("utf-8", "replace").strip(),
    )


_MERGE_CACHE: dict[str, bool] = {}


def is_merge_commit(commit: str) -> bool:
    """存在第二父提交即为 merge。"""
    if commit not in _MERGE_CACHE:
        _MERGE_CACHE[commit] = git_text("rev-parse", "--verify", "--quiet", f"{commit}^2") is not None
    return _MERGE_CACHE[commit]


def validate_revs(args) -> str | None:
    """校验 --branch / 提交参数 / 区间两端,返回错误信息;None 表示通过。"""
    if args.branch and git_text("cat-file", "-e", f"{args.branch}^{{commit}}") is None:
        return f"分支/版本 `{args.branch}` 不存在"
    revs = list(args.commits)
    if args.range:
        revs += [t for t in re.split(r"\.\.\.?", args.range) if t]
    for rev in revs:
        if git_text("cat-file", "-e", f"{rev}^{{commit}}") is None:
            return f"`{rev}` 不是有效提交"
    return None


def resolve_branch(args) -> tuple[str, str | None]:
    """返回 (分支名, 分支尖端哈希)。未指定 -b 时取当前分支。"""
    name = args.branch or git_text("rev-parse", "--abbrev-ref", "HEAD")
    if not name:
        return "detached", git_text("rev-parse", "--verify", "HEAD")
    return name, git_text("rev-parse", "--verify", f"{name}^{{commit}}")


def resolve_commits(args, tip_rev: str) -> list[str]:
    """返回按提交时间倒序(最新在前)的提交哈希列表;默认排除 merge 提交,-M 可纳入。"""
    no_merges = [] if args.include_merges else ["--no-merges"]
    if args.range:
        raw = git_text("rev-list", *no_merges, args.range)
    elif args.commits:
        raw = git_text("rev-list", "--no-walk", "--date-order", *no_merges, *args.commits)
    else:
        raw = git_text("rev-list", *no_merges, f"-n{args.n}", tip_rev)
    if raw is None or not raw.strip():
        return []
    return [c.strip() for c in raw.splitlines() if c.strip()]


def change_entries(commit: str) -> list[dict]:
    """解析提交的变更文件(name-status + numstat,重命名做合并)。
    merge 提交按其相对第一父的差异计算(-M 模式),内容以 merge 落盘版本为准。"""
    if is_merge_commit(commit):
        base = ["diff-tree", "-r", "-M", "-C", "--no-commit-id"]
        revs = [f"{commit}^1", commit]
    else:
        is_root = git_text("rev-parse", "--verify", f"{commit}^") is None
        base = ["diff-tree", "-r", "-M", "-C", "--no-commit-id"] + (["--root"] if is_root else [])
        revs = [commit]

    ns_raw = git_text(*base, "--name-status", *revs) or ""
    num_raw = git_text(*base, "--numstat", *revs) or ""
    adds, dels = {}, {}
    for line in num_raw.splitlines():
        parts = line.split("\t")
        if len(parts) >= 3:
            a, d, path = parts[0], parts[1], "\t".join(parts[2:])
            adds[path] = int(a) if a.isdigit() else 0
            dels[path] = int(d) if d.isdigit() else 0

    entries = []
    for line in ns_raw.splitlines():
        parts = line.split("\t")
        status = parts[0][0]
        if status in ("R", "C") and len(parts) >= 3:
            old_path, new_path = parts[1], parts[2]
        elif len(parts) >= 2:
            old_path = new_path = parts[1]
        else:
            continue
        entries.append(
            {
                "status": status,
                "path": new_path,
                "old_path": old_path,
                "add": adds.get(new_path, adds.get(old_path, 0)),
                "del": dels.get(new_path, dels.get(old_path, 0)),
            }
        )
    return entries


def commit_patch(commit: str) -> str:
    if is_merge_commit(commit):
        out = git_bytes("diff", "-M", "-C", f"{commit}^1", commit)
        return out.decode("utf-8", "replace") if out else ""
    is_root = git_text("rev-parse", "--verify", f"{commit}^") is None
    cmd = ["show", "-M", "-C", "--format=", "--patch", commit]
    if is_root:
        cmd.insert(1, "--root")
    out = git_bytes(*cmd)
    return out.decode("utf-8", "replace") if out else ""


def split_patch_by_file(patch: str) -> dict[str, str]:
    per_file: dict[str, str] = {}
    current: str | None = None
    buf: list[str] = []
    for line in patch.splitlines():
        m = re.match(r"^diff --git a/(.*?) b/(.*)$", line)
        if m:
            if current is not None:
                per_file[current] = "\n".join(buf)
            current = m.group(2)
            per_file.setdefault(m.group(1), "")
            buf = [line]
        else:
            buf.append(line)
    if current is not None:
        per_file[current] = "\n".join(buf)
    for old, new in re.findall(r"^diff --git a/(.*?) b/(.*)$", patch, re.M):
        if old != new and new in per_file:
            per_file[old] = per_file[new]
    return per_file


def format_diff_section(path: str, diff_text: str) -> str:
    if not diff_text.strip():
        return f"#### `{path}`\n\n*(二进制文件或无文本差异,完整内容见目录内文件)*\n"
    lines = diff_text.splitlines()
    note = ""
    if len(lines) > MAX_DIFF_LINES:
        note = f"\n\n> ⚠️ 差异过长,此处截断(共 {len(lines)} 行),完整变更请查看目录内文件。"
        lines = lines[:MAX_DIFF_LINES]
    return f"#### `{path}`\n\n```diff\n" + "\n".join(lines) + "\n```" + note + "\n"


def ref_slug(name: str) -> str:
    """把分支名收敛成可放进 ref 的 ASCII 片段。"""
    return re.sub(r"[^0-9A-Za-z._-]+", "-", name).strip("-./") or "delivery"


def is_ancestor(anc: str, desc: str) -> bool:
    return subprocess.run(["git", "merge-base", "--is-ancestor", anc, desc],
                          capture_output=True).returncode == 0


def bundle_refs(commits: list[str]) -> tuple[list[str], list[str]]:
    """把所选提交拆成 (需要 ref 的前沿提交, 可剪枝的父提交)。

    前沿:没有被其它所选提交当作父提交的提交。连续区间只会有 1 个(最新提交),
    但显式挑不连续的提交时可能有多个,每个都得有一个 ref,现场 fetch 才取得到。
    边界:所选提交的父提交,但必须是「不被任何所选提交包含」的,否则会把别的所选
    提交一起剪掉(例如只选 c1、c3 时,c3 的父提交 c2 不能当边界)。"""
    selected = set(commits)
    children: set[str] = set()
    candidates: list[str] = []
    for commit in commits:
        for p in (git_text("rev-list", "--parents", "-n1", commit) or "").split()[1:]:
            if p in selected:
                children.add(p)
            elif p not in candidates:
                candidates.append(p)
    frontier = [c for c in commits if c not in children]
    negatives = [p for p in candidates if not any(is_ancestor(f, p) for f in frontier)]
    return frontier, negatives


def parse_bundle_prereqs(verify_stdout: str) -> list[str]:
    """从 `git bundle verify` 的 stdout 里取「前置提交」清单。"""
    prereqs: list[str] = []
    in_requires = False
    for raw in verify_stdout.splitlines():
        line = raw.strip()
        if line.startswith("The bundle requires"):
            in_requires = True
            continue
        if line.startswith(("The bundle contains", "The bundle uses", "The bundle records")):
            in_requires = False
            continue
        if in_requires and re.fullmatch(r"[0-9a-f]{40,64}", line):
            prereqs.append(line)
    return prereqs


def make_bundle(path: Path, commits: list[str], branch: str, branch_sha: str | None,
                full: bool, stamp: str) -> dict:
    """生成 bundle 并自检。返回 {path,size,heads,prereqs,verify_ok,full}。

    git bundle 至少需要一个 ref 名;所选提交的最新端不是本地分支尖端时,临时建
    refs/heads/incoming-<分支>-<时间戳> 指向它,打包后立即删除。放在 refs/heads
    下而非自定义命名空间,是为了让现场 `git clone <bundle>` / `git fetch <bundle>`
    都能正常取到该分支(bundle 只有非 heads ref 时 clone 会得到空仓库)。"""
    path.parent.mkdir(parents=True, exist_ok=True)
    negatives: list[str] = []
    temp_refs: list[str] = []
    if full:
        rev_args = [branch or "HEAD"]
    else:
        frontier, negatives = bundle_refs(commits)
        positives: list[str] = []
        for i, commit in enumerate(frontier):
            if branch_sha == commit and git_text("rev-parse", "--verify", "--quiet", f"refs/heads/{branch}"):
                positives.append(f"refs/heads/{branch}")
                continue
            temp_ref = f"refs/heads/incoming-{ref_slug(branch)}-{stamp}" + ("" if i == 0 else f"-{i + 1}")
            rc, _, err = git_run("update-ref", temp_ref, commit)
            if rc != 0:
                print(f"错误: 无法临时创建 {temp_ref}:{err}", file=sys.stderr)
                sys.exit(1)
            temp_refs.append(temp_ref)
            positives.append(temp_ref)
        rev_args = [*positives, *[f"^{n}" for n in negatives]]

    try:
        rc, _, err = git_run("bundle", "create", str(path), *rev_args)
    finally:
        for temp_ref in temp_refs:
            git_run("update-ref", "-d", temp_ref)

    if rc != 0:
        path.unlink(missing_ok=True)
        print(f"错误: git bundle create 失败:{err}", file=sys.stderr)
        sys.exit(1)

    heads: list[tuple[str, str]] = []
    for line in (git_text("bundle", "list-heads", str(path)) or "").splitlines():
        parts = line.split()
        if len(parts) >= 2:
            heads.append((parts[0], parts[1]))
    v_rc, v_out, v_err = git_run("bundle", "verify", str(path))
    return {
        "path": path,
        "size": path.stat().st_size,
        "heads": heads,
        "prereqs": parse_bundle_prereqs(v_out) or negatives,
        "verify_ok": v_rc == 0,
        "verify_err": v_err,
        "full": full,
    }


def bundle_section(info: dict) -> list[str]:
    """变更说明.md 里的 bundle 使用章节。"""
    name = info["path"].name
    incoming = "incoming-" + ref_slug(info["branch"])
    heads = info["heads"] or []
    refs = ", ".join(f"`{ref}` → `{sha[:7]}`" for sha, ref in heads) or "*(无 ref)*"
    # 多个前沿提交(bundle 里有多个 ref)时逐个映射到现场分支
    specs = " ".join(
        f"{ref}:refs/heads/{incoming}" + ("" if i == 0 else f"-{i + 1}")
        for i, (_sha, ref) in enumerate(heads)
    ) or f"HEAD:refs/heads/{incoming}"
    L = ["## 四、git bundle 说明\n\n"]
    L.append(f"- 文件: `{name}`({info['size'] / 1024:.1f} KB),与本说明同目录\n")
    L.append(f"- 内容: {refs}\n")
    L.append("- 打包方式: " + ("整条分支历史(自包含)" if info["full"] else "仅所选提交,范围外的父提交作为前置提交") + "\n")
    if info["prereqs"]:
        L.append("- 前置提交(现场仓库必须已存在): " + ", ".join(f"`{s[:7]}`" for s in info["prereqs"]) + "\n")
    else:
        L.append("- 前置提交: 无\n")
    L.append(f"- 自检: `git bundle verify` {'通过' if info['verify_ok'] else '未通过'}\n")
    if not info["verify_ok"] and info.get("verify_err"):
        L.append(f"\n> ⚠️ 自检失败,请先在生成端检查:{info['verify_err']}\n")
    L.append("\n现场用法(在目标仓库根目录执行):\n\n```bash\n")
    L.append(f"git bundle verify ./{name}\n")
    L.append(f"git fetch ./{name} {specs}\n")
    L.append(f"git log --oneline HEAD..{incoming}        # 先确认将引入哪些提交\n")
    L.append(f"git merge {incoming}                      # 或按需 git cherry-pick\n```\n\n")
    if info["prereqs"]:
        L.append("> 若报 `error: Repository lacks these prerequisite commits`,说明现场缺少上面的前置提交:"
                 "改用 `--bundle-full` 生成自包含 bundle,或先在现场补齐这些提交。\n")
    else:
        first_ref = heads[0][1] if heads else "HEAD"
        clone_ref = first_ref.removeprefix("refs/heads/")
        L.append(f"> 该 bundle 自包含,也可 `git clone -b {clone_ref} ./{name} <目录>` 得到完整仓库。\n")
    return L


def main() -> None:
    parser = argparse.ArgumentParser(description="把 N 个提交合并打包到一个文件夹(含变更说明.md)")
    parser.add_argument("commits", nargs="*", help="提交哈希列表")
    parser.add_argument("--range", "-r", help="提交区间,如 A..B")
    parser.add_argument("-n", type=int, default=2, help="未指定提交时打包最近 N 个非 merge 提交(默认 2)")
    parser.add_argument("-M", "--include-merges", action="store_true",
                        help="纳入 merge 提交(差异按相对第一父计,文件内容取 merge 落盘版本)")
    parser.add_argument("-b", "--branch",
                        help="指定分支(默认当前分支);决定 -n 的起点、bundle 的 ref 与输出命名")
    parser.add_argument("-o", "--output", help="输出目录(默认 部署输出/合并_<分支>_<时间戳>)")
    parser.add_argument("-z", "--zip", action="store_true", help="生成后压缩为同名 zip")
    parser.add_argument("--bundle", action="store_true", help="同时在输出目录内生成 git bundle(仅含所选提交)")
    parser.add_argument("--bundle-full", action="store_true", help="bundle 改为打包整条分支历史(自包含,无前置提交)")
    parser.add_argument("--bundle-path", help="自定义 bundle 文件路径(隐含 --bundle)")
    parser.add_argument("--bundle-only", action="store_true", help="只生成 bundle 与变更说明.md,不导出文件副本")
    args = parser.parse_args()

    error = validate_revs(args)
    if error:
        print(f"错误: {error}", file=sys.stderr)
        sys.exit(1)

    branch, branch_sha = resolve_branch(args)
    commits = resolve_commits(args, args.branch or "HEAD")  # 新→旧
    if not commits:
        print("错误: 未解析到任何提交" + ("" if args.include_merges else "(merge 提交默认跳过,可用 -M 纳入)"), file=sys.stderr)
        sys.exit(1)

    if args.bundle_full and args.range:
        print("  注意: --bundle-full 打包整条分支历史,bundle 不受 -r 区间限制(目录内文件仍按区间导出)",
              file=sys.stderr)
    if args.branch and args.range and branch_sha:
        right = re.split(r"\.\.\.?", args.range, maxsplit=1)[1].strip() or "HEAD"
        right_sha = git_text("rev-parse", "--verify", "--quiet", f"{right}^{{commit}}")
        if right_sha and not is_ancestor(right_sha, branch_sha):
            print(f"  注意: 区间终点 `{right}` 不在分支 `{branch}` 上,打包内容以区间为准", file=sys.stderr)

    ts = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    out_dir = Path(args.output) if args.output else Path(f"部署输出/合并_{branch.replace('/', '-')}_{ts}")
    out_dir.mkdir(parents=True, exist_ok=True)
    want_bundle = args.bundle or args.bundle_full or bool(args.bundle_path) or args.bundle_only

    oldest_first = list(reversed(commits))
    print(f"→ 分支 {branch},合并 {len(commits)} 个提交 → {out_dir}")

    # 1) 按 旧→新 顺序累积:path -> (最终提交, 状态, old_path)。
    #    后处理的(更新的)覆盖先处理的;最新状态为 D 的文件不落盘。
    final_state: dict[str, tuple[str, str, str]] = {}
    per_commit: dict[str, list[dict]] = {}
    for commit in oldest_first:
        entries = change_entries(commit)
        per_commit[commit] = entries
        for e in entries:
            if e["status"] == "D":
                final_state.pop(e["path"], None)
            else:
                final_state[e["path"]] = (commit, e["status"], e["old_path"])

    # 2) 落盘文件:每个取"最后一次改动它的提交"版本(--bundle-only 时跳过)
    copied = 0
    if args.bundle_only:
        print("→ --bundle-only:跳过文件副本导出")
    else:
        for path, (commit, status, _old) in sorted(final_state.items()):
            content = git_bytes("show", f"{commit}:{path}")
            if content is None:
                print(f"  警告: 无法提取 {path}")
                continue
            target = out_dir / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(content)
            copied += 1

    # 3) 汇总"需要在现场删除"的文件:被删除且此前未在本包内提供过(仍可能被现场仓库存在)
    deleted_paths = sorted(
        {e["path"] for c in commits for e in per_commit[c] if e["status"] == "D"}
    )
    # 重命名旧路径也要删
    renamed_old = sorted(
        {e["old_path"] for c in commits for e in per_commit[c] if e["status"] == "R"}
        - set(final_state)
    )

    # 4) 生成 bundle(可选):bundle 与目录同源,现场既能整文件覆盖也能直接 fetch 提交
    bundle_info = None
    if want_bundle:
        bundle_path = Path(args.bundle_path) if args.bundle_path else out_dir / f"{out_dir.name}.bundle"
        bundle_info = make_bundle(bundle_path, commits, branch, branch_sha, args.bundle_full, ts)
        bundle_info["branch"] = branch
        heads = ", ".join(f"{ref} → {sha[:7]}" for sha, ref in bundle_info["heads"]) or "无 ref"
        prereq = f"前置提交 {len(bundle_info['prereqs'])} 个" if bundle_info["prereqs"] else "自包含"
        state = "自检通过" if bundle_info["verify_ok"] else "自检未通过"
        print(f"→ bundle: {bundle_path} ({bundle_info['size'] / 1024:.1f} KB, {heads}, {prereq}, {state})")

    # 5) 生成变更说明.md
    L: list[str] = []
    now = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    L.append(f"# 合并部署包变更说明({len(commits)} 个提交)\n\n")
    L.append(f"- 分支: `{branch}`\n- 生成时间: {now}\n")
    L.append(f"- 提交列表(新→旧):\n")
    for c in commits:
        short = git_text("rev-parse", "--short", c)
        subj = git_text("log", "--format=%s", "-n1", c)
        note = ""
        if is_merge_commit(c):
            p1 = git_text("rev-parse", "--short", f"{c}^1")
            note = f" —— *(merge 提交:差异按相对第一父 `{p1}` 计,文件内容取合并落盘版本)*"
        L.append(f"  - `{short}` {subj}{note}\n")
    L.append("\n## 一、最终文件清单(同名文件已取最新提交版本)\n\n")
    if args.bundle_only:
        L.append("*(本次以 --bundle-only 生成,未导出文件副本;文件内容见 bundle。下表仅供核对范围)*\n\n")
    if not final_state:
        L.append("*(本包无文件)*\n")
    else:
        L.append("| # | 文件 | 最终状态 | 版本来源提交 |\n|---|---|---|---|\n")
        for i, (path, (commit, status, _o)) in enumerate(sorted(final_state.items()), 1):
            short = git_text("rev-parse", "--short", commit)
            L.append(f"| {i} | `{path}` | {STATUS_CN.get(status, status)} | `{short}` |\n")
        L.append("\n")
    if deleted_paths or renamed_old:
        L.append("### 需要在现场删除的文件\n\n")
        for p in deleted_paths + renamed_old:
            L.append(f"- `{p}`\n")
        L.append("\n")

    L.append("## 二、按提交的逐文件差异\n\n")
    for commit in commits:
        short = git_text("rev-parse", "--short", commit)
        subj = git_text("log", "--format=%s", "-n1", commit)
        author = git_text("log", "--format=%an", "-n1", commit)
        date = git_text("log", "--format=%ad", "--date=format:%Y-%m-%d %H:%M", "-n1", commit)
        entries = per_commit[commit]
        patches = split_patch_by_file(commit_patch(commit)) if entries else {}
        tag = ",差异为 merge 相对第一父的合并结果" if is_merge_commit(commit) else ""
        L.append(f"### {short} — {subj}\n\n*{author} @ {date},共 {len(entries)} 个文件{tag}*\n\n")
        if not entries:
            L.append("*(空提交)*\n\n")
            continue
        L.append("| 文件 | 变更类型 | +行 | -行 |\n|---|---|---|---|\n")
        for e in entries:
            L.append(f"| `{e['path']}` | {STATUS_CN.get(e['status'], e['status'])} | +{e['add']} | -{e['del']} |\n")
        L.append("\n")
        for e in entries:
            if e["status"] == "D":
                L.append(f"#### `{e['path']}`\n\n*(该文件在此提交中被删除)*\n\n")
                continue
            L.append(format_diff_section(e["path"], patches.get(e["path"]) or patches.get(e["old_path"]) or ""))
            L.append("\n")

    L.append("## 三、现场合并指引\n\n")
    if args.bundle_only:
        L.append("1. 本包未导出文件副本,请按第四节用 git bundle 取提交与内容。\n")
    else:
        L.append("1. 本目录已合并全部提交的最终版本并保持仓库相对路径,可与现场仓库逐目录对照。\n")
        L.append("2. 新增/修改类文件用本目录内版本整文件覆盖现场对应文件(覆盖前建议先比对差异)。\n")
        L.append("3. 「需要在现场删除的文件」小节列出的文件请在现场手工删除。\n")
    L.append("4. 数据库脚本类变更(*.sql)请按第二节中提交的先后顺序(从最早提交开始)在现场库执行并回归验证。\n")
    if bundle_info:
        L.append("5. 有 git bundle 时优先用 bundle 取提交(见第四节),再按本目录核对文件,避免漏掉二进制或重命名改动。\n")
    else:
        L.append("5. 如需保留提交历史与删除/重命名语义,可加 `--bundle` 重新生成带 git bundle 的包。\n")
    if bundle_info:
        L.append("\n")
        L.extend(bundle_section(bundle_info))
    (out_dir / "变更说明.md").write_text("".join(L), encoding="utf-8")

    print(f"  ✓ 共 {len(final_state)} 个文件(落盘 {copied} 个), 需现场删除 {len(deleted_paths) + len(renamed_old)} 个")
    print(f"  ✓ 变更说明.md 已生成")

    if args.zip:
        zip_path = shutil.make_archive(str(out_dir), "zip", root_dir=out_dir.parent, base_dir=out_dir.name)
        print(f"→ 已压缩: {zip_path} ({Path(zip_path).stat().st_size / 1048576:.2f} MB)")
    print("完成。")


if __name__ == "__main__":
    main()
