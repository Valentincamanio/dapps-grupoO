# Quickstart de validación: Monolito por capas

**Rama**: `003-monolito-por-capas` | **Fecha**: 2026-10-03 | **Plan**: [plan.md](./plan.md)

Guía para comprobar que la reestructuración cumple el spec. No describe cómo mover cada clase:
el mapa está en [data-model.md](./data-model.md) y el orden en [research.md](./research.md)
(D2 a D4).

Todos los comandos se corren desde `backend/` con Git Bash, salvo que se indique otra cosa.
`$BASE` es una carpeta temporal fuera del repo para guardar la línea base.

## Requisitos

- Java 21. Nada más: la base es embebida.
- El puerto 8080 libre.

## 1. Línea base (antes de mover nada)

Sobre la punta de `develop` donde se rebasea la rama:

```bash
./gradlew clean test
```

Contar clases y tests ejecutados, fallidos y omitidos a partir de los XML:

```bash
ls build/test-results/test/TEST-*.xml | wc -l
cat build/test-results/test/TEST-*.xml | grep -o '<testsuite [^>]*' | sed -E 's/.*tests="([0-9]+)".*skipped="([0-9]+)".*failures="([0-9]+)".*errors="([0-9]+)".*/\1 \2 \3 \4/' | awk '{t+=$1;s+=$2;f+=$3+$4} END {print "tests="t" skipped="s" failed="f}'
```

**Esperado** (commit `dc0be4d`): 25 archivos, `tests=212 skipped=0 failed=0`. Si `develop`
cambió, se anotan los valores nuevos y esos pasan a ser la línea base.

Guardar el documento OpenAPI, con la aplicación levantada en otra terminal (`./gradlew bootRun`):

```bash
curl -s http://localhost:8080/v3/api-docs > "$BASE/api-docs-antes.json"
```

## 2. Estructura (SC-008, FR-005, FR-006, FR-011)

Después de la reestructuración, desde `backend/src`:

```bash
ls main/java/ar/edu/unq/desapp/futbolmarket
```

**Esperado**: `config controller FutbolMarketApplication.java modelo persistence security service shared`.

```bash
find . -type d \( -path '*/futbolmarket/auth' -o -path '*/futbolmarket/catalog' \)
```

**Esperado**: sin salida. No queda ningún paquete `auth/` ni `catalog/` en `main` ni en `test`.

```bash
grep -rn "futbolmarket\.\(auth\|catalog\)\." --include=*.java . | grep -v '"futbolmarket\.auth\.'
```

**Esperado**: sin salida. Las únicas apariciones permitidas de `futbolmarket.auth.` son las
cadenas de propiedades de configuración (`"futbolmarket.auth..."`), que se filtran.

```bash
find . -type d -empty; find . -name .gitkeep
```

**Esperado**: sin salida.

Cada clase está donde indica [data-model.md](./data-model.md). Para revisarlo:

```bash
find main/java/ar/edu/unq/desapp/futbolmarket/{controller,service,modelo,persistence} -name '*.java' | sort
```

## 3. Historial conservado (D2)

Desde la raíz del repo, con los cambios en stage o commiteados:

```bash
git diff --cached -M --stat develop | grep '=>' | wc -l
```

**Esperado**: 65, es decir, las 51 clases de producción y los 14 archivos de test movidos (13
clases de test y `FakePasswordHasher`), cada uno detectado como renombre y no como borrado más
alta. Para un archivo puntual:

```bash
git log --follow --oneline -- backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/user/AppUser.java
```

**Esperado**: aparecen los commits de la feature 001.

## 4. Tests movidos sin cambios de contenido (SC-004, FR-016)

Desde la raíz del repo:

```bash
git diff -M develop -- backend/src/test | grep -E '^[+-][^+-]' | grep -vE '^[+-](package |import )'
```

**Esperado**: sin salida. En los tests solo cambian líneas `package` e `import`.

El mismo control en producción tiene que mostrar únicamente los dos comentarios de
[research.md](./research.md) D6:

```bash
git diff -M develop -- backend/src/main | grep -E '^[+-][^+-]' | grep -vE '^[+-](package |import )'
```

## 5. Build y suite completa (SC-002, SC-003)

```bash
./gradlew clean build
```

**Esperado**: `BUILD SUCCESSFUL`. Repetir el conteo del paso 1: los mismos valores que la
línea base (25 archivos, `tests=212 skipped=0 failed=0`).

## 6. Arranque con la base local existente (SC-005, FR-004)

Sin borrar `backend/data/` (la base H2 en archivo creada antes del cambio):

```bash
./gradlew bootRun
```

En otra terminal:

```bash
curl -s http://localhost:8080/actuator/health
curl -s "http://localhost:8080/players?size=1"
```

**Esperado**: arranca al primer intento, sin errores de Hibernate ni de beans, `{"status":"UP"}`
y una página con los jugadores ya cargados.

## 7. Documentación interactiva sin cambios (SC-006)

Con la aplicación levantada:

```bash
curl -s http://localhost:8080/v3/api-docs > "$BASE/api-docs-despues.json"
diff <(python -m json.tool --sort-keys "$BASE/api-docs-antes.json") <(python -m json.tool --sort-keys "$BASE/api-docs-despues.json")
```

**Esperado**: sin diferencias. Si no hay Python, se comparan los archivos a mano o con
cualquier herramienta de diff de JSON.

Después, abrir `http://localhost:8080/swagger-ui.html` y confirmar que aparecen las 7
operaciones de negocio bajo las mismas secciones ("Autenticación", "Cuenta" y la del catálogo
de jugadores).

## 8. Documentación del repo

- `README.md`: la sección **Estructura** muestra el árbol por capa.
- `specs/001-auth-usuarios/plan.md` y `specs/002-catalogo-jugadores/plan.md` empiezan con la
  nota de estructura histórica.
- `specs/001-auth-usuarios/contracts/shared-integration.md` está marcado como obsoleto.

## 9. SonarCloud (SC-007)

Después del merge a `main`: el análisis de SonarCloud del push a `main` reporta menos de 10
issues. Ver [research.md](./research.md) D11 si no se cumple.
