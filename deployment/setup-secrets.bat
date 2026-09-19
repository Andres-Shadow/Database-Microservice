@echo off
setlocal enabledelayedexpansion

set SECRET_NAME=ms-db/db-credentials
set CONTAINER=ms-localstack
set SECRET_FILE=%TEMP%\ms-db-secret.json

if not exist "%SECRET_FILE%" (
    > "%SECRET_FILE%" (
        echo {
        echo   "host": "localhost",
        echo   "port": 5432,
        echo   "database": "sql_execution",
        echo   "username": "postgres",
        echo   "password": "postgres"
        echo }
    )
)

:MENU
echo.
echo ============================================
echo  Configuracion de secreto en Secrets Manager
echo ============================================
echo  Secreto: %SECRET_NAME%
echo.
echo  1. Ver contenido actual
echo  2. Editar contenido (abre Bloc de notas)
echo  3. Crear/actualizar secreto en LocalStack
echo  4. Verificar secreto en LocalStack
echo  5. Salir
echo.
set /p OPTION=Seleccione una opcion: 

if "%OPTION%"=="1" goto VIEW
if "%OPTION%"=="2" goto EDIT
if "%OPTION%"=="3" goto CREATE
if "%OPTION%"=="4" goto VERIFY
if "%OPTION%"=="5" goto EXIT
echo Opcion no valida.
goto MENU

:VIEW
echo.
echo Contenido actual del secreto:
echo -------------------------------------------
type "%SECRET_FILE%"
echo.
echo -------------------------------------------
goto MENU

:EDIT
echo.
echo Abriendo editor...
notepad "%SECRET_FILE%"
echo.
echo Contenido actualizado:
type "%SECRET_FILE%"
echo.
goto MENU

:CREATE
echo.
echo Creando secreto "%SECRET_NAME%" en LocalStack...

docker cp "%SECRET_FILE%" %CONTAINER%:/tmp/secret.json

docker exec %CONTAINER% awslocal secretsmanager create-secret ^
    --name "%SECRET_NAME%" ^
    --secret-string file:///tmp/secret.json 2>nul

if errorlevel 1 (
    echo El secreto ya existe, actualizando...
    docker exec %CONTAINER% awslocal secretsmanager update-secret ^
        --secret-id "%SECRET_NAME%" ^
        --secret-string file:///tmp/secret.json 2>nul
)

docker exec %CONTAINER% rm -f /tmp/secret.json 2>nul

if errorlevel 1 (
    echo [ERROR] No se pudo crear ni actualizar el secreto.
) else (
    echo Secreto creado/actualizado exitosamente.
)
goto MENU

:VERIFY
echo.
echo Consultando secreto "%SECRET_NAME%" en LocalStack...
docker exec %CONTAINER% awslocal secretsmanager get-secret-value --secret-id "%SECRET_NAME%" 2>nul
if errorlevel 1 (
    echo [ERROR] El secreto no existe o no se pudo consultar.
)
goto MENU

:EXIT
endlocal
