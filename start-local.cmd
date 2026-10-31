@echo off
cd /d "%~dp0"
if exist ".env" for /f "usebackq eol=# tokens=1,* delims==" %%A in (".env") do set "%%A=%%B"
if not exist data\postgres\PG_VERSION (
 echo Local PostgreSQL data directory not found. Use start-postgres.cmd or start-demo.cmd.
 pause
 exit /b 1
)
".tools\postgres\bin\pg_ctl.exe" -D data\postgres status >nul 2>&1
if errorlevel 1 ".tools\postgres\bin\pg_ctl.exe" -D data\postgres -l data\postgres.log -o "-h 127.0.0.1 -p 55432" -w start
set DB_URL=jdbc:postgresql://127.0.0.1:55432/kyokushin
echo Open http://localhost:8080 in your browser.
java -jar target\brackets-1.0.0.jar
pause
