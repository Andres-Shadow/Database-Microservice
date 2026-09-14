@echo off
setlocal

set BUCKET=sql-execution
set CONTAINER=ms-localstack

:menu
cls
echo ==========================================
echo   Administrador de archivos S3 - %BUCKET%
echo ==========================================
echo 1. Listar archivos
echo 2. Ver detalle de un archivo
echo 3. Subir archivo local
echo 4. Eliminar un archivo
echo 5. Salir
echo ==========================================
set /p opcion=Elija una opcion: 

if "%opcion%"=="1" goto listar
if "%opcion%"=="2" goto detalle
if "%opcion%"=="3" goto subir
if "%opcion%"=="4" goto eliminar
if "%opcion%"=="5" goto salir
goto menu

:listar
echo.
echo Archivos en s3://%BUCKET%:
docker exec %CONTAINER% awslocal s3 ls "s3://%BUCKET%/" --recursive
echo.
pause
goto menu

:detalle
echo.
set /p key=Ingrese la clave del archivo (ej: approved/test.sql): 
if "%key%"=="" goto menu
echo.
echo --- Metadatos ---
docker exec %CONTAINER% awslocal s3api head-object --bucket %BUCKET% --key "%key%"
echo.
echo --- Contenido ---
docker exec %CONTAINER% awslocal s3 cp "s3://%BUCKET%/%key%" -
echo.
pause
goto menu

:subir
echo.
set /p local=Ingrese la ruta del archivo local (ej: example.sql): 
if "%local%"=="" goto menu
if not exist "%local%" (
  echo El archivo no existe: "%local%"
  pause
  goto menu
)
set /p key=Ingrese la clave destino en S3 (ej: approved/example.sql): 
if "%key%"=="" set key=%local%
docker exec -i %CONTAINER% awslocal s3 cp - "s3://%BUCKET%/%key%" < "%local%"
echo.
echo Archivo subido a s3://%BUCKET%/%key%
pause
goto menu

:eliminar
echo.
set /p key=Ingrese la clave del archivo a eliminar: 
if "%key%"=="" goto menu
docker exec %CONTAINER% awslocal s3 rm "s3://%BUCKET%/%key%"
echo.
pause
goto menu

:salir
echo.
echo Saliendo...
endlocal
exit /b 0
