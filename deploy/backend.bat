@echo off
REM ABC Musical — avvio backend in locale (sviluppo ibrido).
REM Prerequisiti:
REM   - JDK 21 + Maven installati
REM   - PostgreSQL condiviso (nibius-db, stack nibius-infra) avviato e
REM     raggiungibile dall'host su localhost:5432.
REM     Per usare un host/porta diversi imposta DB_HOST/DB_PORT nel .env.
REM Legge le variabili da abc-website\running\.env (unica fonte di verita').

cd /d %~dp0..

set ENV_FILE=..\running\.env
if not exist "%ENV_FILE%" (
    echo ERRORE: file %ENV_FILE% non trovato. Copia deploy\.env.example in running\.env e configuralo.
    exit /b 1
)

for /f "usebackq eol=# tokens=1,* delims==" %%a in ("%ENV_FILE%") do set "%%a=%%b"

set SPRING_PROFILES_ACTIVE=dev
if not defined DB_HOST set DB_HOST=localhost
if not defined DB_PORT set DB_PORT=5432
set APP_UPLOAD_DIR=%CD%\uploads

echo ==========================================
echo ABC Musical - Backend (profilo dev)
echo DB: localhost:5432/%DB_NAME%  -  http://localhost:8080
echo ==========================================

powershell -Command "& { mvn spring-boot:run 2>&1 | Tee-Object -FilePath server.log }"
