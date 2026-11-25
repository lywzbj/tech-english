@echo off
setlocal
REM pack-commits-to-one-folder.bat -- Merge N commits' changed files into ONE folder (Windows wrapper)
REM   Same file in multiple commits: newest version wins. Includes one combined description md.
REM   Usage: pack-commits-to-one-folder.bat [-n N] [-M] [-o OUTDIR] [-z] [commit1 commit2 ...]
REM   -M : include merge commits (diff vs first parent; files taken from merge result)
REM   Example: pack-commits-to-one-folder.bat -n 5 -z
REM            pack-commits-to-one-folder.bat -r 94f05c5a..HEAD -z
REM            pack-commits-to-one-folder.bat -M 3e3632b6 -z
python "%~dp0pack-commits-to-one-folder.py" %*
exit /b %ERRORLEVEL%
