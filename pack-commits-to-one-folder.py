#!/usr/bin/env python3
"""
限定提交数量,把这些提交改动的全部代码合并打包到一个文件夹(保留仓库相对路径,
同名文件取最新提交版本),并生成一份总的「变更说明.md」。

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

关于 merge 提交(-M):
  默认跳过 merge 提交。加 -M 后 merge 会被纳入:差异按其相对第一父(合入前的本地侧)
  计算,文件内容取 merge 落盘版本——两侧都改过的文件,合并结果只存在于 merge 树中,
  用被合入分支的普通提交打包会丢失本地侧改动,此时必须用 -M 打包 merge 本身。
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


_MERGE_CACHE: dict[str, bool] = {}


def is_merge_commit(commit: str) -> bool:
    """存在第二父提交即为 merge。"""
    if commit not in _MERGE_CACHE:
        _MERGE_CACHE[commit] = git_text("rev-parse", "--verify", "--quiet", f"{commit}^2") is not None
    return _MERGE_CACHE[commit]


def resolve_commits(args) -> list[str]:
    """返回按提交时间倒序(最新在前)的提交哈希列表;默认排除 merge 提交,-M 可纳入。"""
    no_merges = [] if args.include_merges else ["--no-merges"]
    if args.range:
        raw = git_text("rev-list", *no_merges, args.range)
    elif args.commits:
        raw = git_text("rev-list", "--no-walk", "--date-order", *no_merges, *args.commits)
    else:
        raw = git_text("rev-list", *no_merges, f"-n{args.n}", "HEAD")
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


def main() -> None:
    parser = argparse.ArgumentParser(description="把 N 个提交合并打包到一个文件夹(含变更说明.md)")
    parser.add_argument("commits", nargs="*", help="提交哈希列表")
    parser.add_argument("--range", "-r", help="提交区间,如 A..B")
    parser.add_argument("-n", type=int, default=2, help="未指定提交时打包最近 N 个非 merge 提交(默认 2)")
    parser.add_argument("-M", "--include-merges", action="store_true",
                        help="纳入 merge 提交(差异按相对第一父计,文件内容取 merge 落盘版本)")
    parser.add_argument("-o", "--output", help="输出目录(默认 部署输出/合并_<分支>_<时间戳>)")
    parser.add_argument("-z", "--zip", action="store_true", help="生成后压缩为同名 zip")
    args = parser.parse_args()

    for c in list(args.commits) + ([args.range] if args.range else []):
        token = c.split("..")[0] or None
        if token and git_text("cat-file", "-e", f"{token}^{{commit}}") is None:
            print(f"错误: {c} 不是有效提交", file=sys.stderr)
            sys.exit(1)

    commits = resolve_commits(args)  # 新→旧
    if not commits:
        print("错误: 未解析到任何提交" + ("" if args.include_merges else "(merge 提交默认跳过,可用 -M 纳入)"), file=sys.stderr)
        sys.exit(1)

    branch = (git_text("rev-parse", "--abbrev-ref", "HEAD") or "detached").replace("/", "-")
    ts = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    out_dir = Path(args.output) if args.output else Path(f"部署输出/合并_{branch}_{ts}")
    out_dir.mkdir(parents=True, exist_ok=True)

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

    # 2) 落盘文件:每个取"最后一次改动它的提交"版本
    copied = 0
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

    # 4) 生成变更说明.md
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
    L.append("1. 本目录已合并全部提交的最终版本并保持仓库相对路径,可与现场仓库逐目录对照。\n")
    L.append("2. 新增/修改类文件用本目录内版本整文件覆盖现场对应文件(覆盖前建议先比对差异)。\n")
    L.append("3. 「需要在现场删除的文件」小节列出的文件请在现场手工删除。\n")
    L.append("4. 数据库脚本类变更(*.sql)请按第二节中提交的先后顺序(从最早提交开始)在现场库执行并回归验证。\n")
    (out_dir / "变更说明.md").write_text("".join(L), encoding="utf-8")

    print(f"  ✓ 共 {len(final_state)} 个文件(落盘 {copied} 个), 需现场删除 {len(deleted_paths) + len(renamed_old)} 个")
    print(f"  ✓ 变更说明.md 已生成")

    if args.zip:
        zip_path = shutil.make_archive(str(out_dir), "zip", root_dir=out_dir.parent, base_dir=out_dir.name)
        print(f"→ 已压缩: {zip_path} ({Path(zip_path).stat().st_size / 1048576:.2f} MB)")
    print("完成。")


if __name__ == "__main__":
    main()
