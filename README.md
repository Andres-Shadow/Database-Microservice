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
- El caso de uso solo depende de abstracciones (`SqlScriptStorage`, `SqlStatementParser`, `SqlExecutor`).
- El entry point no contiene lógica de negocio.
- El plugin de Clean Architecture (`validateStructure`) y un `ArchitectureTest` auto-generado validan
  estas reglas en cada build.

### Puertos del dominio

| Puerto               | Responsabilidad                                              | Implementación            |
|----------------------|--------------------------------------------------------------|---------------------------|
| `SqlScriptStorage`   | Leer script y guardar resultado                              | `S3SqlScriptStorage`      |
| `SqlStatementParser` | Dividir el contenido en sentencias                           | `DefaultSqlStatementParser` |
| `SqlExecutor`        | Ejecutar una sentencia y devolver su resultado               | `PostgresSqlExecutor`     |

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
- `ms-localstack` → LocalStack con S3, puerto `4566`, credenciales `test`/`test`.

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
