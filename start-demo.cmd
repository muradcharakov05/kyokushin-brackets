@echo off
cd /d "%~dp0"
call mvn spring-boot:run -Dspring-boot.run.profiles=demo
pause
