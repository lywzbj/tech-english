@echo off
setlocal
REM pack-commits-to-one-folder.bat -- Merge N commits' changed files into ONE folder (Windows wrapper)
REM   Same file in multiple commits: newest version wins. Includes one combined description md.
REM   Usage: pack-commits-to-one-folder.bat [-n N] [-M] [-o OUTDIR] [-z] [-b BRANCH] [--bundle]
REM          [--bundle-full] [--bundle-path FILE] [--bundle-only] [commit1 commit2 ...]
REM   -M : include merge commits (diff vs first parent; files taken from merge result)
REM   -b : branch to pack (default: current branch); also used for -n and the bundle ref
REM   --bundle : also write a git bundle (selected commits only) into the output folder
REM   --bundle-full : bundle the whole branch history instead (self-contained, no prerequisites)
REM   --bundle-only : only write the bundle + description md, no file copies
REM   Example: pack-commits-to-one-folder.bat -n 5 -z
REM            pack-commits-to-one-folder.bat -r 94f05c5a..HEAD -z
REM            pack-commits-to-one-folder.bat -M 3e3632b6 -z
REM            pack-commits-to-one-folder.bat -b dev -r v1.0..origin/dev --bundle -z
python "%~dp0pack-commits-to-one-folder.py" %*
exit /b %ERRORLEVEL%
