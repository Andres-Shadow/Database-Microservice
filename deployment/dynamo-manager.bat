@echo off
setlocal

set TABLE_NAME=user_backups
set CONTAINER=ms-localstack

:menu
cls
echo ==========================================
echo   Administrador DynamoDB - %TABLE_NAME%
echo ==========================================
echo 1. Listar backups
echo 2. Ver detalle de un backup
echo 3. Eliminar un backup
echo 4. Contar registros
echo 5. Crear tabla (setup inicial)
echo 6. Eliminar tabla
echo 7. Salir
echo ==========================================
set /p opcion=Elija una opcion: 

if "%opcion%"=="1" goto listar
if "%opcion%"=="2" goto detalle
if "%opcion%"=="3" goto eliminar
if "%opcion%"=="4" goto contar
if "%opcion%"=="5" goto crear_tabla
if "%opcion%"=="6" goto eliminar_tabla
if "%opcion%"=="7" goto salir
goto menu

:listar
echo.
echo Backups en tabla %TABLE_NAME%:
docker exec %CONTAINER% awslocal dynamodb scan --table-name %TABLE_NAME% --query "Items[*].{BackupId:backup_id.S,Nombre:nombre.S,Email:email.S,DeletedAt:deleted_at.S}" --output table 2>nul
if errorlevel 1 (
    echo [ERROR] No se pudo listar. Verifique que la tabla exista.
)
echo.
pause
goto menu

:detalle
echo.
set /p backupId=Ingrese el backupId: 
if "%backupId%"=="" goto menu
echo.
echo --- Detalle del backup ---
docker exec %CONTAINER% awslocal dynamodb get-item --table-name %TABLE_NAME% --key "{\"backup_id\":{\"S\":\"%backupId%\"}}" --output json 2>nul
if errorlevel 1 (
    echo [ERROR] No se encontro el backup o la tabla no existe.
)
echo.
pause
goto menu

:eliminar
echo.
set /p backupId=Ingrese el backupId a eliminar: 
if "%backupId%"=="" goto menu
echo.
echo Eliminando backup %backupId%...
docker exec %CONTAINER% awslocal dynamodb delete-item --table-name %TABLE_NAME% --key "{\"backup_id\":{\"S\":\"%backupId%\"}}" 2>nul
if errorlevel 1 (
    echo [ERROR] No se pudo eliminar el backup.
) else (
    echo Backup eliminado exitosamente.
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

:crear_tabla
echo.
echo Creando tabla %TABLE_NAME%...
docker exec %CONTAINER% awslocal dynamodb create-table ^
    --table-name %TABLE_NAME% ^
    --attribute-definitions AttributeName=backup_id,AttributeType=S ^
    --key-schema AttributeName=backup_id,KeyType=HASH ^
    --billing-mode PAY_PER_REQUEST 2>nul
if errorlevel 1 (
    echo [ERROR] No se pudo crear la tabla. Puede que ya exista.
) else (
    echo Tabla creada exitosamente.
)
echo.
pause
goto menu

:eliminar_tabla
echo.
echo ADVERTENCIA: Esto eliminara TODOS los backups.
set /p confirm=Esta seguro? (S/N): 
if /i not "%confirm%"=="S" goto menu
echo Eliminando tabla %TABLE_NAME%...
docker exec %CONTAINER% awslocal dynamodb delete-table --table-name %TABLE_NAME% 2>nul
if errorlevel 1 (
    echo [ERROR] No se pudo eliminar la tabla.
) else (
    echo Tabla eliminada exitosamente.
)
echo.
pause
goto menu

:salir
echo.
echo Saliendo...
endlocal
exit /b 0
