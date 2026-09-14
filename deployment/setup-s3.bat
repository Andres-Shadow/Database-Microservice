@echo off
setlocal

set BUCKET=sql-execution
set CONTAINER=ms-localstack
set S3_KEY=approved/test.sql
set SQL_FILE=%TEMP%\ms-test.sql

> "%SQL_FILE%" echo SELECT COUNT(1); SELECT 2;

echo Creando bucket s3://%BUCKET% ...
docker exec %CONTAINER% awslocal s3 mb s3://%BUCKET% 2>nul

echo Subiendo %S3_KEY% ...
docker exec -i %CONTAINER% awslocal s3 cp - s3://%BUCKET%/%S3_KEY% < "%SQL_FILE%"

echo.
echo Listo. Archivo subido a s3://%BUCKET%/%S3_KEY%
echo Probar con:
echo   curl -X POST http://localhost:8080/api/v1/sql/execute -H "Content-Type: application/json" -d "{\"fileName\":\"approved/test.sql\"}"

endlocal
