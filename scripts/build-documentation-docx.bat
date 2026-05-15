@echo off
REM Generate docs\DOCUMENTATION_TECHNIQUE_SYSCO_FR.docx from the Markdown source.
cd /d "%~dp0.."
pip install python-docx -q
python scripts\md_to_docx.py
if %ERRORLEVEL% equ 0 (
  start "" "docs\DOCUMENTATION_TECHNIQUE_SYSCO_FR.docx"
) else (
  echo Failed. Install Python 3 and run: pip install python-docx
  pause
)
