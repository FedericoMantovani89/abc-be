@echo off
REM ABC Musical — avvio frontend in locale (sviluppo ibrido).
REM Prerequisiti: Node 22+ installato, backend avviato (deploy\backend.bat o Docker).
REM Legge le variabili da abc-website\running\.env e genera abc-fe\.env.local.

set ROOT=%~dp0..\..
set ENV_FILE=%ROOT%\running\.env
set FRONTEND_DIR=%ROOT%\abc-fe

if not exist "%ENV_FILE%" (
    echo ERRORE: file %ENV_FILE% non trovato. Copia deploy\.env.example in running\.env e configuralo.
    exit /b 1
)

for /f "usebackq eol=# tokens=1,* delims==" %%a in ("%ENV_FILE%") do set "%%a=%%b"

(
    echo SPRING_API_URL=http://localhost:8080
    echo NEXTAUTH_URL=http://localhost:3000
    echo AUTH_URL=http://localhost:3000
    echo NEXTAUTH_SECRET=%NEXTAUTH_SECRET%
    echo AUTH_SECRET=%NEXTAUTH_SECRET%
    echo BACKEND_HOSTNAME=localhost:8080
) > "%FRONTEND_DIR%\.env.local"

cd /d "%FRONTEND_DIR%"
if not exist "node_modules" (
    echo Prima installazione dipendenze...
    call npm install
)

npm run dev
