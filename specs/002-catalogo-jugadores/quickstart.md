# Guía de validación: Catálogo de jugadores

## Precondiciones

- Java 21 instalado.
- No se necesita Docker, base externa ni otro servicio.
- Ejecutar desde `backend/`.

## Ejecutar la aplicación local

```powershell
./gradlew bootRun
```

El perfil predeterminado `local` crea/usa H2 persistente y carga `src/main/resources/data/players.json` una única vez. Detener y volver a iniciar no debe aumentar las cantidades de equipos ni jugadores.

## Ejecutar la suite

```powershell
./gradlew test
./gradlew build
```

Los tests usan el perfil `test` con H2 en memoria. Deben pasar unitarias de dominio, service y mappers; integración de repository/seeder; y pruebas HTTP MockMvc, sin depender del orden ni de datos de ejecuciones anteriores.

## Escenarios HTTP

Los formatos completos se definen en [players-api.yaml](./contracts/players-api.yaml) y las reglas de entidades en [data-model.md](./data-model.md).

```powershell
curl "http://localhost:8080/players?page=0&size=10"
curl "http://localhost:8080/players?league=PREMIER&position=FORWARD&page=0&size=5"
curl "http://localhost:8080/players/1"
```

Validar que el primer listado incluye contenido, metadatos de página y solo las cuatro propiedades visibles del jugador más su identificador. Recorrer todas las páginas confirma que el orden por identificador es estable y que cada jugador aparece una vez.

Comprobar además:

- filtros individuales y combinados devuelven únicamente jugadores que los satisfacen;
- filtros admitidos sin resultados devuelven `200` con `content: []`;
- `league=INVALID`, `position=INVALID`, `page=-1` o `size=0` devuelven `400` y el formato `ApiError`;
- `GET /players/{id}` de un identificador inexistente devuelve `404`, en español y sin datos de otro jugador;
- después de tres reinicios locales, el conteo y las relaciones siguen siendo consistentes.

## Verificación final registrada

El 2026-09-25 se ejecutaron `./gradlew test` y `./gradlew build` desde `backend/`: ambos finalizaron con `BUILD SUCCESSFUL`. La aplicación se inició con el perfil predeterminado `local` tres veces consecutivas. En cada inicio, `GET /players?page=0&size=50` devolvió 50 jugadores, 50 identificadores únicos y valores no vacíos de equipo, liga y posición para cada elemento; por lo tanto, el seeder no duplicó jugadores ni produjo relaciones incompletas.

También se verificaron las respuestas de error finales: `GET /players?league=INVALID` devolvió `400` con `ApiError` y `GET /players/999999` devolvió `404` con un mensaje en español. Los ejemplos de respuesta 200, 400 y 404 están documentados en [players-api.yaml](./contracts/players-api.yaml).
