@echo off
setlocal

set TABLE_NAME=user_backups
set CONTAINER=ms-localstack

echo Creando tabla DynamoDB "%TABLE_NAME%" en LocalStack...
docker exec %CONTAINER% awslocal dynamodb create-table ^
    --table-name %TABLE_NAME% ^
    --attribute-definitions AttributeName=backup_id,AttributeType=S ^
    --key-schema AttributeName=backup_id,KeyType=HASH ^
    --billing-mode PAY_PER_REQUEST

if errorlevel 1 (
    echo [ERROR] No se pudo crear la tabla. Verifique que LocalStack este ejecutandose.
) else (
    echo.
    echo Tabla "%TABLE_NAME%" creada exitosamente.
    echo.
    echo Verificando...
    docker exec %CONTAINER% awslocal dynamodb describe-table --table-name %TABLE_NAME% --query "Table.{Status:TableStatus,ItemCount:ItemCount}" 2>nul
)

echo.
endlocal
