@echo off
setlocal

set CONTAINER=ms-localstack

echo Creando tablas DynamoDB en LocalStack...
echo.

echo [1/2] Creando tabla "user_backups"...
docker exec %CONTAINER% awslocal dynamodb create-table ^
    --table-name user_backups ^
    --attribute-definitions AttributeName=backup_id,AttributeType=S ^
    --key-schema AttributeName=backup_id,KeyType=HASH ^
    --billing-mode PAY_PER_REQUEST

if errorlevel 1 (
    echo [WARN] No se pudo crear user_backups (puede que ya exista).
) else (
    echo [OK] Tabla "user_backups" creada.
)

echo.
echo [2/2] Creando tabla "execution_history"...
docker exec %CONTAINER% awslocal dynamodb create-table ^
    --table-name execution_history ^
    --attribute-definitions AttributeName=execution_id,AttributeType=S ^
    --key-schema AttributeName=execution_id,KeyType=HASH ^
    --billing-mode PAY_PER_REQUEST

if errorlevel 1 (
    echo [WARN] No se pudo crear execution_history (puede que ya exista).
) else (
    echo [OK] Tabla "execution_history" creada.
)

echo.
echo Verificando tablas...
docker exec %CONTAINER% awslocal dynamodb list-tables 2>nul

echo.
endlocal