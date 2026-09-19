# ms-db — Ejecutor de Scripts SQL desde S3

Microservicio **reactivo** construido con **Java 25**, **Spring Boot 4**, **Spring WebFlux**,
**R2DBC/PostgreSQL** y **AWS SDK v2 (S3)**. Sigue **Clean Architecture / Arquitectura Hexagonal**
con estructura modular de Gradle.

Recibe por HTTP el nombre de un archivo `.sql`, lo lee desde un bucket de **S3**, ejecuta sus
sentencias contra **PostgreSQL** en orden y genera un archivo de resultados que vuelve a subir al
mismo bucket.

---

## Tabla de contenidos

- [Flujo general](#flujo-general)
- [Arquitectura](#arquitectura)
- [Procesamiento de la request](#procesamiento-de-la-request)
- [Recuperación del archivo (S3)](#recuperación-del-archivo-s3)
- [Parsing de SQL](#parsing-de-sql)
- [Ejecución de las sentencias](#ejecución-de-las-sentencias)
- [Manejo de errores](#manejo-de-errores)
- [Generación del archivo de resultados](#generación-del-archivo-de-resultados)
- [Configuración](#configuración)
- [Requisitos](#requisitos)
- [Levantar la infraestructura local](#levantar-la-infraestructura-local)
- [Crear el bucket y subir archivos](#crear-el-bucket-y-subir-archivos)
- [Levantar el microservicio](#levantar-el-microservicio)
- [API](#api)
- [Funcionalidades de gestión de usuarios](#funcionalidades-de-gestión-de-usuarios)
- [Endpoints de administración](#endpoints-de-administración)
- [Seguridad](#seguridad)
- [Testing](#testing)
- [Build](#build)

---

## Flujo general

```text
HTTP POST /api/v1/sql/execute
        │
        ▼
   entrypoint (WebFlux functional)
        │  valida fileName
        ▼
   ExecuteSqlScriptUseCase
        │
        ├── 1. SqlScriptStorage.get(fileName)  ──► S3 (bucket configurado)
        │
        ├── 2. SqlStatementParser.parse(content) ──► List<SqlStatement>
        │
        ├── 3. SqlExecutor.execute(statement)   ──► PostgreSQL (R2DBC)
        │        (secuencial, STOP ON ERROR)
        │
        ├── 4. SqlExecutionReportFormatter ──► texto del reporte
        │
        └── 5. SqlScriptStorage.saveResult(...) ──► S3 (mismo bucket)
        │
        ▼
   HTTP 200 con resultado general + ruta del archivo de resultados
```

---

## Arquitectura

El proyecto es **multi-módulo** y respeta la dirección de dependencias hacia el dominio:

```text
applications/app-service          (composición raíz, main, wiring)
        │
        ├──► infrastructure/entry-points/reactive-web      (adaptadores de entrada)
        │
        ├──► infrastructure/driven-adapters/s3-repository  (adaptador S3)
        │
        ├──► infrastructure/driven-adapters/r2dbc-postgresql (adaptador PostgreSQL)
        │
        ├──► infrastructure/driven-adapters/dynamo-db      (adaptador DynamoDB - backups)
        │
        └──► infrastructure/helpers/metrics                (publicador de métricas AWS)
        │
        ▼
domain/usecase                    (casos de uso y lógica de aplicación)
        │
        ▼
domain/model                      (modelos de dominio y puertos)
```

Reglas que se respetan:

- `domain/model` y `domain/usecase` **no** dependen de Spring, AWS SDK, S3, R2DBC, PostgreSQL, HTTP
  ni de `application.yaml`.
- Los adaptadores implementan **puertos** definidos en el dominio (inversión de dependencias).
- Los casos de uso solo dependen de abstracciones (puertos definidos en `model`).
- El entry point no contiene lógica de negocio.
- El plugin de Clean Architecture (`validateStructure`) y un `ArchitectureTest` auto-generado validan
  estas reglas en cada build.

### Puertos del dominio

| Puerto                  | Responsabilidad                                              | Implementación              |
|-------------------------|--------------------------------------------------------------|-----------------------------|
| `SqlScriptStorage`      | Leer script SQL y guardar resultado                          | `S3SqlScriptStorage`        |
| `SqlStatementParser`    | Dividir el contenido en sentencias SQL                       | `DefaultSqlStatementParser` |
| `SqlExecutor`           | Ejecutar una sentencia SQL y devolver su resultado           | `PostgresSqlExecutor`       |
| `BatchFileStorage`      | Leer/eliminar archivos de lote y guardar resultados          | `S3BatchFileStorage`        |
| `UserRepository`        | CRUD de usuarios en PostgreSQL                               | `PostgresUserRepository`    |
| `UserBackupRepository`  | Guardar/buscar backups de usuarios en DynamoDB               | `DynamoDBUserBackupRepository` |
| `ChangeNameParser`      | Parsear archivo `id;nombre_nuevo`                            | `DefaultChangeNameParser`   |
| `DeleteUserParser`      | Parsear archivo `id;nombre;email`                            | `DefaultDeleteUserParser`   |

---

## Procesamiento de la request

La entrada es un **functional endpoint** de WebFlux (sin `@RestController`).

1. `SqlExecutionRouter` registra `POST /api/v1/sql/execute`.
2. `SqlExecutionHandler` deserializa el body en `SqlExecutionRequest`.
3. Se valida `fileName`:
   - no puede ser nulo o vacío;
   - debe terminar en `.sql`;
   - se rechaza path traversal (`..`, `\`, ruta absoluta, letra de unidad).
4. Se llama a `ExecuteSqlScriptUseCase.execute(fileName)`.
5. El resultado se serializa como `SqlExecutionOutcome`.

> El bucket **nunca** llega en el request: siempre se obtiene de configuración.

---

## Recuperación del archivo (S3)

`S3SqlScriptStorage` usa `S3AsyncClient` (cliente **asíncrono**, obligatorio por reglas de
arquitectura reactiva):

- `get(fileName)`: ejecuta `GetObjectRequest` con `bucket` (de configuración) y `key = fileName`,
  lee la respuesta como `String` y devuelve un `SqlScript(fileName, content)`.
- Si el objeto no existe (`NoSuchBucketException`/`NoSuchKeyException`), lanza
  `SqlScriptNotFoundException` (mapeado a **404**).
- Cualquier otro error de S3 se traduce a `SqlExecutionInfrastructureException` (mapeado a **500**).

---

## Parsing de SQL

`DefaultSqlStatementParser` divide el contenido en sentencias usando una **máquina de estados**,
**no** un `split(";")` ingenuo.

Se respetan correctamente los `;` que aparecen dentro de:

- strings con comilla simple `'...'` (con escape `''`);
- identificadores con comilla doble `"..."` (con escape `""`);
- bloques dollar-quoted de PostgreSQL `$$...$$` o `$tag$...$tag$`;
- comentarios de línea `-- ...`;
- comentarios de bloque `/* ... */` (con anidamiento).

Solo un `;` fuera de esos contextos se considera separador de sentencia. Cada sentencia resultante
se recorta, se descartan las vacías y se numeran secuencialmente (`sequence` empezando en 1).

- Contenido vacío o sin sentencias → `InvalidSqlScriptException` (**400**).
- String, comentario o dollar-quote sin cerrar → `InvalidSqlScriptException` (**400**).

---

## Ejecución de las sentencias

`PostgresSqlExecutor` ejecuta cada `SqlStatement` de forma individual con `DatabaseClient`
(Spring Data R2DBC), midiendo el tiempo y capturando filas afectadas:

```java
databaseClient.sql(statement.sql()).fetch().rowsUpdated()
```

El caso de uso `ExecuteSqlScriptUseCase` ejecuta las sentencias **secuencialmente** y aplica la
política **STOP ON ERROR**:

```text
statement 1 → SUCCESS
statement 2 → SUCCESS
statement 3 → FAILED
statement 4 → NOT_EXECUTED   (no se ejecuta; el error anterior detuvo el flujo)
```

Esto se implementa con `Flux.concatMap(...).takeUntil(FAILED)`. Si una sentencia falla, las
restantes se completan con `NOT_EXECUTED` y el estado global queda `FAILED`.

Errores de infraestructura (conexión a la base de datos, por ejemplo) **no** se convierten en un
`FAILED`: se propagan como `SqlExecutionInfrastructureException` (**500**), de modo que no se
devuelve `200 OK` ante un fallo inesperado.

---

## Manejo de errores

El manejo es **centralizado** en `SqlExecutionHandler`:

| Excepción / condición                | HTTP |
|--------------------------------------|------|
| `InvalidSqlScriptException`          | 400  |
| Body inválido (`ServerWebInputException`) | 400  |
| `SqlScriptNotFoundException`         | 404  |
| `SqlExecutionInfrastructureException`| 500  |
| Cualquier otro error inesperado      | 500  |

El body de error es siempre un JSON `{ "message": "..." }`.

---

## Generación del archivo de resultados

`SqlExecutionReportFormatter` produce un reporte de texto legible y auditable:

```text
SQL EXECUTION RESULT
====================

Source file:
approved/update-customers.sql

Overall status:
FAILED

----------------------------------------
Statement #1
----------------------------------------

SQL:
UPDATE customers SET status = 'ACTIVE' WHERE id = 10

Status:
SUCCESS

Rows affected:
1

Execution time:
15 ms

----------------------------------------
Statement #2
----------------------------------------

...
```

El nombre del archivo generado se calcula así:

```text
{result-prefix}/{nombre-base}-result.txt
```

Por ejemplo `approved/update-customers.sql` → `results/update-customers-result.txt`. El prefijo se
configura con `adapters.aws.s3.result-prefix`.

El reporte se sube al **mismo bucket** desde el que se leyó el script.

---

## Configuración

Toda la configuración externa está en `application.yaml` y se enlaza con `@ConfigurationProperties`.

### PostgreSQL (R2DBC)

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/sql_execution
    username: postgres
    password: postgres
```

### S3 / LocalStack

```yaml
adapters:
  aws:
    s3:
      endpoint: http://localhost:4566
      region: us-east-1
      bucket-name: sql-execution
      access-key: test
      secret-key: test
      result-prefix: results
```

`S3Config` construye un `S3AsyncClient` con:

- credenciales estáticas (`access-key` / `secret-key`);
- `pathStyleAccessEnabled(true)` para LocalStack;
- `endpointOverride` cuando `endpoint` está configurado (en AWS real se deja vacío).

---

## Requisitos

- Java 25 (el proyecto usa toolchain de Java 25)
- Docker y Docker Compose
- Gradle (se usa el wrapper `./gradlew` / `gradlew.bat`)

---

## Levantar la infraestructura local

```bash
docker compose -f deployment/docker-compose.yml up -d
```

Esto levanta:

- `ms-postgres` → PostgreSQL 16, base `sql_execution`, usuario `postgres`, contraseña `postgres`, puerto `5432`.
- `ms-localstack` → LocalStack con S3, Secrets Manager y DynamoDB, puerto `4566`, credenciales `test`/`test`.

### Crear la tabla de DynamoDB (backups)

```bash
deployment\setup-dynamo.bat
```

---

## Crear el bucket y subir archivos

### Bucket

```bash
docker exec ms-localstack awslocal s3 mb s3://sql-execution
```

### Script de setup automático

```bash
deployment\setup-s3.bat
```

Crea el bucket y sube `approved/test.sql` con:

```sql
SELECT COUNT(1); SELECT 2;
```

### Administrador interactivo de S3

```bash
deployment\s3-manager.bat
```

Permite listar, ver detalle, **subir archivos locales**, eliminar y salir.

---

## Levantar el microservicio

```bash
./gradlew bootRun
```

El servicio queda en `http://localhost:8080`.

---

## API

### Ejecutar un script SQL

```http
POST /api/v1/sql/execute
Content-Type: application/json
```

**Request**

```json
{
  "fileName": "approved/test.sql"
}
```

**Response (200)**

```json
{
  "sourceFile": "approved/test.sql",
  "status": "SUCCESS",
  "totalStatements": 3,
  "successfulStatements": 3,
  "failedStatements": 0,
  "totalExecutionTimeMs": 42,
  "resultFile": "results/test-result.txt"
}
```

**Ejemplo con curl**

```bash
curl -X POST http://localhost:8080/api/v1/sql/execute \
  -H "Content-Type: application/json" \
  -d '{"fileName":"approved/test.sql"}'
```

### Respuestas de error

```json
{ "message": "fileName must have the .sql extension" }
```

---

## Funcionalidades de gestión de usuarios

Además de la ejecución de scripts SQL, el microservicio expone tres endpoints para gestionar
operaciones en lote sobre la tabla `usuarios`, con archivos fijos en S3 y backup automático en
DynamoDB.

### Requisitos previos

Antes de usar los nuevos endpoints, la tabla de DynamoDB debe existir en LocalStack:

```bash
deployment\setup-dynamo.bat
```

Esto crea la tabla `user_backups` con `backupId` como partition key.

Para administrar los backups manualmente:

```bash
deployment\dynamo-manager.bat
```

### Cambiar nombres en lote

Lee `change_name.txt` desde S3. Cada línea tiene el formato `id;nombre_nuevo` y genera un
`UPDATE usuarios SET nombre = ? WHERE id = ?`. El archivo original se elimina y se sube
`change_name_result.txt` con el reporte.

**Archivo `change_name.txt` de ejemplo:**

```text
1;Juan Perez
2;Maria Lopez
3;Carlos Ruiz
```

**Subir el archivo a S3:**

```bash
docker exec -i ms-localstack awslocal s3 cp - s3://sql-execution/change_name.txt < change_name.txt
```

**Request**

```http
POST /api/v1/users/change-name
```

**Ejemplo con curl**

```bash
curl -X POST http://localhost:8080/api/v1/users/change-name
```

**Response (200)**

```json
{
  "sourceFile": "change_name.txt",
  "status": "SUCCESS",
  "totalEntries": 3,
  "successfulEntries": 3,
  "failedEntries": 0,
  "totalExecutionTimeMs": 85,
  "resultFile": "change_name_result.txt"
}
```

Si alguna entrada falla, el estado será `FAILED` y el reporte `_result.txt` detalla cuáles
entradas tuvieron éxito y cuáles no.

---

### Eliminar usuarios con backup

Lee `delete_user.txt` desde S3. Cada línea tiene el formato `id;nombre;email`. **Antes** de cada
DELETE, se ejecuta un `SELECT *` con las mismas cláusulas y el registro se guarda en la tabla
`user_backups` de DynamoDB como backup. Solo si el backup es exitoso se ejecuta el DELETE. El
archivo original se elimina y se sube `delete_user_result.txt` con el reporte.

**Archivo `delete_user.txt` de ejemplo:**

```text
1;Juan Perez;juan@example.com
2;Maria Lopez;maria@example.com
3;Carlos Ruiz;carlos@example.com
```

**Subir el archivo a S3:**

```bash
docker exec -i ms-localstack awslocal s3 cp - s3://sql-execution/delete_user.txt < delete_user.txt
```

**Request**

```http
POST /api/v1/users/delete
```

**Ejemplo con curl**

```bash
curl -X POST http://localhost:8080/api/v1/users/delete
```

**Response (200)**

```json
{
  "sourceFile": "delete_user.txt",
  "status": "SUCCESS",
  "totalEntries": 3,
  "successfulEntries": 3,
  "failedEntries": 0,
  "totalExecutionTimeMs": 120,
  "resultFile": "delete_user_result.txt"
}
```

Cada backup en DynamoDB genera un `backupId` (UUID) que aparece en los logs y permite restaurar
el registro.

**Flujo por cada entrada:**

```text
1. SELECT * FROM usuarios WHERE id = X AND nombre = Y AND email = Z
2. Guardar snapshot en DynamoDB (user_backups) con backupId único
3. DELETE FROM usuarios WHERE id = X AND nombre = Y AND email = Z
   └── Si el paso 2 falla, el DELETE NO se ejecuta
```

---

### Restaurar usuario desde backup

Recibe un `backupId` y restaura el registro guardado en DynamoDB de vuelta a PostgreSQL. Como el
`id` es autogenerado (`BIGSERIAL`), el INSERT genera un nuevo `id`.

**Request**

```http
POST /api/v1/users/restore
Content-Type: application/json
```

```json
{
  "backupId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

**Ejemplo con curl**

```bash
curl -X POST http://localhost:8080/api/v1/users/restore \
  -H "Content-Type: application/json" \
  -d '{"backupId":"a1b2c3d4-e5f6-7890-abcd-ef1234567890"}'
```

**Response (200)**

```json
{
  "message": "User restored successfully from backup a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

**Para obtener el backupId**, listar los backups con el script interactivo:

```bash
deployment\dynamo-manager.bat
```

O directamente por CLI:

```bash
docker exec ms-localstack awslocal dynamodb scan --table-name user_backups --query "Items[*].{BackupId:backup_id.S,Nombre:nombre.S,Email:email.S}" --output table
```

---

### Ejemplo completo de flujo de contingencia

```bash
# 1. Crear la tabla DynamoDB
deployment\setup-dynamo.bat

# 2. Subir archivo de eliminación a S3
docker exec -i ms-localstack awslocal s3 cp - s3://sql-execution/delete_user.txt < delete_user.txt

# 3. Ejecutar eliminaciones (con backup automático en DynamoDB)
curl -X POST http://localhost:8080/api/v1/users/delete

# 4. Ver los backups disponibles
docker exec ms-localstack awslocal dynamodb scan --table-name user_backups --output json

# 5. Restaurar un usuario específico si hubo un error
curl -X POST http://localhost:8080/api/v1/users/restore \
  -H "Content-Type: application/json" \
  -d '{"backupId":"<backup-id-del-paso-4>"}'
```

---

## Endpoints de administración

Endpoints utilitarios para consultar y administrar los recursos de S3 y DynamoDB.

### S3 — Listar archivos

```bash
curl http://localhost:8080/api/v1/manage/s3/files
```

**Response (200)**

```json
[
  {
    "key": "approved/test.sql",
    "size": 245,
    "lastModified": "2026-09-18T15:30:00Z"
  },
  {
    "key": "change_name.txt",
    "size": 42,
    "lastModified": "2026-09-18T16:00:00Z"
  }
]
```

### S3 — Ver detalle de un archivo

```bash
curl "http://localhost:8080/api/v1/manage/s3/files/detail?key=approved/test.sql"
```

**Response (200)**

```json
{
  "key": "approved/test.sql",
  "size": 245,
  "lastModified": "2026-09-18T15:30:00Z",
  "content": "CREATE TABLE usuarios (...);"
}
```

### S3 — Eliminar un archivo

```bash
curl -X DELETE "http://localhost:8080/api/v1/manage/s3/files?key=change_name.txt"
```

**Response (200)**

```json
{
  "message": "File deleted: change_name.txt"
}
```

### DynamoDB — Listar backups

```bash
curl http://localhost:8080/api/v1/manage/dynamo/backups
```

**Response (200)**

```json
[
  {
    "backupId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "user": {
      "nombre": "Juan Perez",
      "email": "juan@example.com",
      "edad": 30,
      "activo": true,
      "salario": 5000000.00,
      "fechaRegistro": "2026-01-15T10:30:00"
    },
    "deletedAt": "2026-09-18T22:00:00"
  }
]
```

### DynamoDB — Ver detalle de un backup

```bash
curl http://localhost:8080/api/v1/manage/dynamo/backups/a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

**Response (200)**

```json
{
  "backupId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "user": {
    "nombre": "Juan Perez",
    "email": "juan@example.com",
    "edad": 30,
    "activo": true,
    "salario": 5000000.00,
    "fechaRegistro": "2026-01-15T10:30:00"
  },
  "deletedAt": "2026-09-18T22:00:00"
}
```

### DynamoDB — Eliminar un backup

```bash
curl -X DELETE http://localhost:8080/api/v1/manage/dynamo/backups/a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

**Response (200)**

```json
{
  "message": "Backup deleted: a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

---

## Seguridad

- `fileName` se valida (no vacío, extensión `.sql`, sin path traversal).
- El bucket nunca llega en el request; siempre se lee de configuración.
- No se acepta SQL directo en el request: el SQL siempre proviene de S3.
- No se exponen credenciales; el logging no imprime el contenido completo del SQL ni credenciales.

---

## Testing

### Unit tests

- `DefaultSqlStatementParserTest`: múltiples sentencias, `;` dentro de strings/comentarios/dollar-quotes,
  archivos vacíos, strings sin cerrar.
- `SqlExecutionReportFormatterTest`: generación del reporte.
- `ExecuteSqlScriptUseCaseTest`: éxito total, STOP ON ERROR, `NOT_EXECUTED` y propagación de errores.
- `SqlExecutionRouterTest`: validación del endpoint y mapeo de errores a HTTP.

### Integration test

`SqlExecutionIntegrationTest` usa **Testcontainers** (PostgreSQL + LocalStack) para validar el flujo
real completo:

```text
S3 → aplicación → PostgreSQL → S3
```

Verifica que el `CREATE TABLE`, `INSERT` y `UPDATE` se ejecutan y que el archivo de resultados queda
en S3.

Para ejecutar los tests:

```bash
./gradlew test
```

---

## Build

```bash
./gradlew clean build
```

El build ejecuta `validateStructure`, `ArchitectureTest`, tests unitarios, test de integración,
`pitest` (mutation testing) y genera el `bootJar`.
