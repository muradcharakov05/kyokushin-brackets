@echo off
cd /d "%~dp0"
".tools\postgres\bin\pg_ctl.exe" -D data\postgres -m fast -w stop
pause
