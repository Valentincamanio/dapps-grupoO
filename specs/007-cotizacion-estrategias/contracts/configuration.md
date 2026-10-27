# Configuración de la feature 007

Propiedades, valores por perfil, reglas de seguridad y caché que suma la feature. No se
reemplazan `build.gradle` ni los `application*.yml`: solo se agregan claves.

## 1. Propiedades

Se enlazan a `QuoteRecalculationProperties` (`config/`), registrada en `ApplicationConfig`
(`@EnableConfigurationProperties`), con el mismo estilo de `FootballDataProperties.Sync`. Se validan
al arrancar: si alguna es inválida, la aplicación no levanta.

| Propiedad | Tipo | Por defecto | Qué hace |
|---|---|---|---|
| `futbolmarket.quotes.recalculation.cron` | texto | `0 0 6 * * MON` | Cron de Spring (6 campos) de la corrida programada: los lunes a las 06:00, dos horas después de la sincronización de las 04:00. **`-` deshabilita** la corrida programada. Una expresión inválida impide el arranque |
| `futbolmarket.quotes.recalculation.zone` | texto | `America/Argentina/Buenos_Aires` | Zona en que se interpreta el cron. Un id inválido impide el arranque |
| `futbolmarket.quotes.recalculation.on-startup` | booleano | `true` | Si al arrancar se dispara un recálculo en segundo plano cuando hay semanas cerradas sin cotizar |

Lo que **no** es configurable, a propósito, porque es una definición del dominio: la semana (lunes
00:00 a lunes 00:00 en hora de Argentina), los 7 días de espera por estadísticas faltantes, el
factor de escala por defecto (10), el mínimo por defecto (10,00) y las escalas de los números.

### `application.yaml` (agrega)

```yaml
futbolmarket:
  quotes:
    recalculation:
      cron: "0 0 6 * * MON"
      zone: America/Argentina/Buenos_Aires
      on-startup: true
```

### `application-test.yml` (agrega)

```yaml
futbolmarket:
  quotes:
    recalculation:
      cron: "-"
      on-startup: false
```

Con `cron: "-"` y `on-startup: false`, ningún test compite con una corrida en segundo plano ni
depende del reloj. Los tests disparan el recálculo a mano, o llaman al servicio con un `Clock`
fijo.

### Perfiles

| Perfil | Comportamiento |
|---|---|
| `local` | Recálculo programado los lunes a las 06:00 (hora de Argentina) y al arrancar si hay semanas pendientes. Base H2 en archivo con `ddl-auto: update`: las tablas `strategies`, `strategy_weights` y `quotes` se crean solas |
| `test` | Sin corrida programada ni de arranque. H2 en memoria con `create-drop`. **La semilla de estrategias corre igual**: cada contexto de test parte con A y B cargadas |

## 2. Qué dispara un recálculo

| Disparador | Origen en el informe | Cuándo | Si hay otro recálculo o una sincronización en curso |
|---|---|---|---|
| Cron | `WEEKLY` | lunes 06:00 hora de Argentina | se omite, con un `WARN` que dice el motivo |
| Arranque | `STARTUP` | al terminar de arrancar, en segundo plano, si hay semanas cerradas sin cotizar | se omite, con un `WARN`; la semana queda para el cron |
| Manual | `MANUAL` | `POST /quotes/recalculate` (solo `ADMIN`) | responde 409 |
| Cambio de estrategia con `AHORA` | `STRATEGY_CHANGE` | `POST /strategies/{id}/activate` o `/versions` | responde 409 y **no cambia la activa** |
| Fin de la cadena de sincronización | `CHAIN` | **todavía no existe** (punto P-3 de `research.md`, D18) | n/a |

Mientras el disparo al terminar la cadena no exista, valen el cron, el arranque y el disparo manual.

## 3. Seguridad (`SecurityConfig`)

Reglas nuevas, antes de `anyRequest().authenticated()`:

| Regla | Efecto |
|---|---|
| `pathPattern(POST, "/quotes/**")` → `hasRole(ADMIN)` | recálculo y simulación solo para administradores |
| `pathPattern("/strategies/**")` (todos los métodos) → `hasRole(ADMIN)` | crear, editar, activar, listar y consultar estrategias solo para administradores |

- Sin credencial: 401. Con un usuario común: 403. Los dos antes del controller, así que no se
  cotiza, no se simula ni se cambia nada.
- **No se toca `publicEndpoints()`.** Las lecturas de cotizaciones y el ranking viven bajo
  `GET /players/**`, que ya es público por la regla marcada `TEMPORAL` ("la protección de estos
  endpoints se activa en un PR posterior"). **Queda señalado**: cuando esa regla se active, estas
  rutas cambian junto con el catálogo, y el spec (FR-040) exige que sean públicas.
- Los endpoints de administrador aceptan las dos credenciales de la feature 001 (token de sesión y
  clave de API). En Swagger llevan los dos `@SecurityRequirement` como alternativas, igual que
  `SyncController`.

| Método y ruta | Acceso |
|---|---|
| `GET /players/{id}/quotes`, `/quotes/current`, `/quotes/at` | público |
| `GET /players/quotes/current?ids=` | público |
| `GET /players/ranking` | público |
| `POST /quotes/recalculate`, `POST /quotes/simulate` | `ADMIN` |
| `POST /strategies`, `GET /strategies`, `GET /strategies/{id}` | `ADMIN` |
| `POST /strategies/{id}/versions`, `POST /strategies/{id}/activate` | `ADMIN` |

## 4. Caché

`CacheConfig` (`config/`): `@Configuration @EnableCaching` y un bean `CacheManager`:

```java
new ConcurrentMapCacheManager("quotes-current", "quotes-ranking")
```

Viene en `spring-context` (verificado en `spring-context-7.0.9.jar`): **no hay dependencia nueva**.

| Caché | Clave | Se llena en | Se invalida |
|---|---|---|---|
| `quotes-current` | id del jugador | `QuoteQueryService.getCurrent` | al terminar un recálculo, al terminar una recotización y al cambiar la estrategia activa |
| `quotes-ranking` | liga, equipo, posición, hoja y tamaño | `QuoteQueryService.getRanking` | ídem |

- La invalidación borra **todas las entradas** de los dos cachés y se hace **después del commit**, en
  el servicio que no es transaccional (`QuoteRecalculationService`, `StrategyChangeService`).
- No hay vencimiento por tiempo. Una sincronización que cambie jugadores activos o equipos deja el
  ranking desactualizado hasta el próximo recálculo (riesgo R4 de `research.md`).
- El historial, la consulta a una fecha, el lote de la pizarra y la simulación no se cachean.

## 5. Registro (SLF4J)

| Nivel | Mensaje |
|---|---|
| `INFO` | inicio y fin de cada recálculo; una línea por semana cotizada (jugadores con inicial, calculada y recotizada); una línea con las semanas pendientes y su motivo; la línea final con la duración y la estrategia usada |
| `WARN` | recálculo omitido (cron o arranque) por otro en curso o por una sincronización; semana que falló al guardarse |
| `INFO` | semilla de estrategias: cuáles se crearon y cuáles ya existían |

El informe completo se escribe en el registro y, en el disparo manual, va a la respuesta. **No se
guarda.** El registro no incluye datos de usuarios ni credenciales.

## 6. Swagger

Los endpoints nuevos se documentan con `@Operation`, `@ApiResponse` y `@Tag` (`Cotizaciones`,
`Ranking`, `Recálculo`, `Simulación`, `Estrategias`) y se pueden ejecutar desde `/swagger-ui.html`
(definición de terminado de la constitución). Los de administrador llevan
`@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)` y
`@SecurityRequirement(name = OpenApiConfig.API_KEY_AUTH)`.

## 7. Sin cambios

`build.gradle` (ninguna dependencia nueva), `spring.jpa.open-in-view: false`, los perfiles `local`
y `test` y las propiedades de `futbolmarket.football-data.*`.
