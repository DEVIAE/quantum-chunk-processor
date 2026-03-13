@echo off
title Quantum Chunk Processor - LOCAL
echo ============================================
echo   Quantum Chunk Processor - Modo LOCAL
echo   Broker : format-normalizer tcp://localhost:61616
echo   Redis  : opcional (fallback a in-memory si no disponible)
echo ============================================
echo.
set JAVA_HOME=C:\Program Files\Java\jdk-21
set MAVEN_HOME=E:\Projects\Java\Maven\apache-maven-3.8.6
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%
set BACKEND_PORT=8082

cd /d %~dp0

echo [INFO] Creando directorios de datos...
call :mkdir_if_missing E:\data\processing
call :mkdir_if_missing E:\data\processed
call :mkdir_if_missing E:\data\failed
call :mkdir_if_missing E:\data\output
echo.

echo [INFO] Verificando quantum-common 1.1.0...
if exist "%USERPROFILE%\.m2\repository\com\quantum\quantum-common\1.1.0\quantum-common-1.1.0.jar" (
    echo [OK] quantum-common 1.1.0 encontrado.
) else (
    echo [ERROR] quantum-common 1.1.0 NO encontrado. Ejecuta primero: cd quantum-common ^&^& start-backend.bat
    pause
    exit /b 1
)
echo.

echo [INFO] Verificando broker (format-normalizer en :61616)...
powershell -NoProfile -Command "try { $t = New-Object Net.Sockets.TcpClient; $t.Connect('localhost',61616); $t.Close(); Write-Host '[OK] Broker detectado en :61616' } catch { Write-Host '[WARN] Broker no disponible - inicia format-normalizer primero'; exit 1 }"
if errorlevel 1 ( pause & exit /b 1 )
echo.

echo [INFO] Verificando Redis en :6379 (opcional)...
powershell -NoProfile -Command "try { $t = New-Object Net.Sockets.TcpClient; $t.Connect('localhost',6379); $t.Close(); Write-Host '[OK] Redis disponible en :6379' } catch { Write-Host '[INFO] Redis no disponible - se usara idempotencia en memoria' }"
echo.

echo [INFO] Verificando puerto %BACKEND_PORT%...
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr /C:":%BACKEND_PORT% "') do (
    echo [WARN] Puerto %BACKEND_PORT% en uso por PID %%a, liberando...
    taskkill /T /F /PID %%a >nul 2^>^&1
)
echo.

if not exist "logs" mkdir logs
set LOGTS=%DATE:~6,4%%DATE:~3,2%%DATE:~0,2%%TIME:~0,2%%TIME:~3,2%
set LOGTS=%LOGTS: =0%
set LOG_FILE=logs\%LOGTS%.log

echo [INFO] Arrancando quantum-chunk-processor con perfil LOCAL...
echo [INFO] Puerto  : %BACKEND_PORT%
echo [INFO] Broker  : tcp://localhost:61616  (embebido en format-normalizer)
echo [INFO] Redis   : localhost:6379  (opcional)
echo [INFO] ELK     : DESHABILITADO
echo [INFO] Log     : %LOG_FILE%
echo.
echo [READY] Health: http://localhost:%BACKEND_PORT%/actuator/health
echo.

echo [INFO] Verificando agente Elastic APM...
set APM_AGENT_DIR=%USERPROFILE%\.quantum-apm
set APM_AGENT_JAR=%APM_AGENT_DIR%\elastic-apm-agent-1.51.0.jar
if not exist "%APM_AGENT_DIR%" mkdir "%APM_AGENT_DIR%"
if not exist "%APM_AGENT_JAR%" (
    echo [INFO] Descargando Elastic APM agent 1.51.0...
    %MAVEN_HOME%\bin\mvn.cmd dependency:copy -Dartifact=co.elastic.apm:elastic-apm-agent:1.51.0:jar -DoutputDirectory="%APM_AGENT_DIR%" -Dmdep.stripVersion=false -q
)
set "APM_JVM_ARGS=-javaagent:%APM_AGENT_JAR% -Delastic.apm.service_name=quantum-chunk-processor -Delastic.apm.server_url=http://localhost:8200 -Delastic.apm.secret_token=quantum-apm-token -Delastic.apm.environment=local -Delastic.apm.application_packages=com.quantum"
echo [INFO] APM activo: quantum-chunk-processor → http://localhost:8200
echo.

mvn spring-boot:run -Dspring-boot.run.profiles=local "-Dspring-boot.run.jvmArguments=%APM_JVM_ARGS%" 2>&1 | powershell -NoProfile -Command "$input | Tee-Object -FilePath '%LOG_FILE%'"
goto :eof

:mkdir_if_missing
if not exist "%~1" (
    mkdir "%~1"
    echo [OK] Creado: %~1
) else (
    echo [OK] Ya existe: %~1
)
goto :eof
