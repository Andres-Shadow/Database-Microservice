@echo off
setlocal

set CONTAINER=ms-localstack

:select_table
cls
echo ==========================================
echo   Administrador DynamoDB - LocalStack
echo ==========================================
echo 1. Tabla: user_backups (backups de usuarios)
echo 2. Tabla: execution_history (historial de ejecuciones)
echo 3. Salir
echo ==========================================
set /p tabla=Elija una tabla: 

if "%tabla%"=="1" (
    set TABLE_NAME=user_backups
    set PK_NAME=backup_id
    set LIST_QUERY=Items[*].{Id:backup_id.S,Nombre:nombre.S,Email:email.S,DeletedAt:deleted_at.S}
    goto menu
)
if "%tabla%"=="2" (
    set TABLE_NAME=execution_history
    set PK_NAME=execution_id
    set LIST_QUERY=Items[*].{Id:execution_id.S,Type:operation_type.S,File:source_file.S,Status:status.S,Time:executed_at.S}
    goto menu
)
if "%tabla%"=="3" goto salir
goto select_table

:menu
cls
echo ==========================================
echo   Tabla: %TABLE_NAME%
echo ==========================================
echo 1. Listar registros
echo 2. Ver detalle de un registro
echo 3. Eliminar un registro
echo 4. Contar registros
echo 5. Cambiar de tabla
echo 6. Salir
echo ==========================================
set /p opcion=Elija una opcion: 

if "%opcion%"=="1" goto listar
if "%opcion%"=="2" goto detalle
if "%opcion%"=="3" goto eliminar
if "%opcion%"=="4" goto contar
if "%opcion%"=="5" goto select_table
if "%opcion%"=="6" goto salir
goto menu

:listar
echo.
echo Registros en %TABLE_NAME%:
docker exec %CONTAINER% awslocal dynamodb scan --table-name %TABLE_NAME% --query "%LIST_QUERY%" --output table 2>nul
if errorlevel 1 (
    echo [ERROR] No se pudo listar. Verifique que la tabla exista.
)
echo.
pause
goto menu

:detalle
echo.
set /p recordId=Ingrese el ID del registro: 
if "%recordId%"=="" goto menu
echo.
echo --- Detalle ---
docker exec %CONTAINER% awslocal dynamodb get-item --table-name %TABLE_NAME% --key "{\"%PK_NAME%\":{\"S\":\"%recordId%\"}}" --output json 2>nul
if errorlevel 1 (
    echo [ERROR] No se encontro el registro o la tabla no existe.
)
echo.
pause
goto menu

:eliminar
echo.
set /p recordId=Ingrese el ID a eliminar: 
if "%recordId%"=="" goto menu
echo.
echo Eliminando registro %recordId%...
docker exec %CONTAINER% awslocal dynamodb delete-item --table-name %TABLE_NAME% --key "{\"%PK_NAME%\":{\"S\":\"%recordId%\"}}" 2>nul
if errorlevel 1 (
    echo [ERROR] No se pudo eliminar el registro.
) else (
    echo Registro eliminado exitosamente.
)
echo.
pause
goto menu

:contar
echo.
echo Conteo de registros en %TABLE_NAME%:
docker exec %CONTAINER% awslocal dynamodb scan --table-name %TABLE_NAME% --select COUNT 2>nul
if errorlevel 1 (
    echo [ERROR] No se pudo contar. Verifique que la tabla exista.
)
echo.
pause
goto menu

:salir
echo.
echo Saliendo...
endlocal
exit /b 0