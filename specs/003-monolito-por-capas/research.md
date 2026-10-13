# Investigación: Monolito por capas

**Rama**: `003-monolito-por-capas` | **Fecha**: 2026-10-03 | **Plan**: [plan.md](./plan.md)

El contexto técnico no dejó puntos `NEEDS CLARIFICATION`: el stack, la build y la
configuración no cambian. Esta investigación registra las decisiones de ejecución del
movimiento y los riesgos encontrados al recorrer el código en el commit `a922df4`.

## D1. Árbol destino: `<capa>/<contexto>/`, no paquetes planos

- **Decisión**: la tabla de movimientos del pedido de planificación se lee como el mapeo de
  **capas** (`auth/controller` → `controller`, `*/modelo` → `modelo`, etc.). Dentro de cada
  capa se agrega el nivel de contexto según FR-007 y FR-008. Por ejemplo,
  `auth/modelo/AppUser` → `modelo/user/AppUser` y `catalog/modelo/PlayerNotFoundException` →
  `modelo/player/exception/PlayerNotFoundException`.
- **Motivo**: el Principio I (NO NEGOCIABLE) define el árbol `<capa>/<contexto>/` como el
  único válido, y FR-005, FR-007, FR-008 y FR-009 lo exigen. Confirmado por el usuario el
  2026-10-03.
- **Alternativa descartada**: paquetes planos sin contexto (`controller/`, `modelo/`,
  `modelo/exception/`). Viola el Principio I y requeriría enmendar la constitución y el spec.

## D2. Mover con `git mv`, un archivo por vez

- **Decisión**: cada archivo se mueve con `git mv origen destino`, después de crear la carpeta
  destino. El cambio de `package` e imports va en el mismo commit que el movimiento.
- **Motivo**: conserva el historial. Git detecta un renombre cuando el archivo nuevo se parece
  al viejo en al menos un 50 %. Como solo cambian la línea `package` y algunos imports, la
  similitud queda muy por encima de ese umbral, y `git log --follow` y `git blame` siguen la
  historia.
- **Alternativas descartadas**:
  - Mover con el IDE (refactor de paquete): reescribe imports de todo el proyecto y puede
    reordenarlos o tocar comentarios. Es más difícil de revisar contra FR-013 y FR-016.
  - Borrar y crear de nuevo: pierde el historial.
- **Nota**: `git mv` no crea carpetas intermedias. Hay que crear cada carpeta destino antes
  (`mkdir -p`).

## D3. Orden: por capa, de abajo hacia arriba, compilando en cada paso

- **Decisión**: se mueve primero `modelo`, después `persistence`, `service` y `controller`, y
  al final los tests. Después de cada capa se corrigen los imports de todo el árbol y se corre
  `./gradlew compileJava compileTestJava`.
- **Motivo**: cada paso deja el proyecto compilando, así que un error se detecta en la capa que
  lo causó y cada commit intermedio se puede bisecar. El modelo va primero porque todas las
  capas lo importan.
- **Alternativa descartada**: mover todo y compilar al final. Los errores de compilación se
  mezclan y no queda claro qué movimiento los causó.

## D4. Reemplazo de imports: solo nombres totalmente calificados

- **Decisión**: los `package` e imports se reescriben buscando el nombre totalmente calificado
  `ar.edu.unq.desapp.futbolmarket.auth.` y `ar.edu.unq.desapp.futbolmarket.catalog.`, nunca
  `futbolmarket.auth` a secas.
- **Motivo**: `futbolmarket.auth` también es el prefijo de propiedades de configuración
  (`@ConfigurationProperties("futbolmarket.auth")` en `config/AuthProperties` y
  `registry.add("futbolmarket.auth.admin.*")` en `AdminAccountInitializerIT`, `AdminAccountIT`
  y `AuthErrorsIT`). Si se tocaran esas cadenas cambiarían datos de los tests (prohibido por
  FR-016) y la clave de `application.yaml` dejaría de coincidir (prohibido por FR-014).
- **Alternativa descartada**: reemplazo global de `futbolmarket.auth`.

## D5. Referencias que hoy no necesitan import y después sí

Clases que hoy comparten paquete y quedan en contextos distintos. El compilador las detecta,
pero se listan para que la tarea las prevea. Todas se resuelven agregando un import
(FR-013 lo permite) y las relaciones entre contextos están permitidas por las reglas de
monolito.

| Clase (destino) | Usa (destino) |
|---|---|
| `modelo/user/AppUser` | `modelo/auth/CredentialPolicy`, `modelo/auth/PasswordHasher` |
| `modelo/auth/SessionTokenIssuer` | `modelo/user/AppUser` |
| `modelo/player/Player` | `modelo/team/Team`, `modelo/league/League`, `modelo/position/Position`, `modelo/player/exception/CatalogInvariantException` |
| `modelo/player/PlayerFilter` | `League`, `Position`, `CatalogInvariantException` |
| `modelo/player/PlayerPage` | `CatalogInvariantException` |
| `modelo/team/Team` | `League`, `CatalogInvariantException` |
| `persistence/mapper/player/PlayerMapper` | `persistence/mapper/team/TeamMapper` |
| `persistence/sql/entity/player/PlayerSQL` | `persistence/sql/entity/team/TeamSQL` |
| test `modelo/user/AppUserTest` | test `modelo/auth/FakePasswordHasher` |

Riesgo de visibilidad revisado: todas las clases y miembros de producción que se mueven son
`public` (los únicos `protected` son los constructores sin argumentos de `PlayerSQL` y
`TeamSQL`, que solo usa Hibernate). Los tests ya estaban en paquetes distintos de las clases
que prueban (`auth.persistence` vs `auth.persistence.repository`), así que ninguno depende de
acceso de paquete. No hace falta cambiar ningún modificador.

No se forman ciclos entre clases: `AppUser` usa `CredentialPolicy` y `PasswordHasher`, que no
usan a `AppUser` en código. La única mención de `AppUser` en `CredentialPolicy` está en el
Javadoc (ver D6).

## D6. Javadoc y comentarios

Se recorrieron todos los comentarios de `src/main` y `src/test` buscando `auth`, `catalog`,
`catálogo` y rutas de paquete.

| Archivo | Comentario | Acción |
|---|---|---|
| `modelo/player/exception/CatalogInvariantException` | "contraparte de InvalidUserDataException de auth" | Cambiar "de auth" por "del contexto `user`", que es donde queda esa excepción. |
| `modelo/auth/CredentialPolicy` | `{@link AppUser}` | Deja de resolverse porque `AppUser` pasa a `modelo/user`. Se usa el nombre calificado en el link, `{@link ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser}`, en lugar de un import usado solo por el Javadoc. |
| `shared/ApiError` | "spec de auth" | Sin cambio: nombra el spec 001, no un paquete. |
| `security/SecurityConfig` | `{@code /auth/**}`, `{@code /auth/me}` | Sin cambio: son rutas HTTP, que no cambian (FR-001). |

Los demás `{@link}` de las clases movidas (`AppUserMapper`, `AppUserRepository`,
`AdminAccountInitializer`, `FakePasswordHasher`) apuntan a clases que ya importan o que quedan
en su mismo paquete. En los tests no hay comentarios que nombren paquetes viejos, así que FR-016
se cumple sin excepciones.

**Desvío menor de FR-013**: FR-013 dice que en producción solo cambian `package`, imports y
ubicación. Los dos comentarios de la tabla son un cambio adicional, pedido de forma explícita en
la planificación, que no toca lógica. Se registra en Seguimiento de complejidad del plan.

## D7. Descubrimiento de componentes, entidades y repositorios

- **Decisión**: no se toca ninguna configuración de escaneo.
- **Motivo**: `FutbolMarketApplication` sigue en la raíz `ar.edu.unq.desapp.futbolmarket` y no
  declara `scanBasePackages`, `@EntityScan` ni `@EnableJpaRepositories`, así que Spring
  descubre componentes, entidades y repositorios en todo el árbol nuevo. Los tests usan
  `@SpringBootTest`, `@DataJpaTest` y un `@Import` por clase (`AppUserRepositoryIT`), sin
  nombres de paquete. `application*.yml` no menciona paquetes. No hay exclusiones de Sonar ni
  pasos de CI por paquete.

## D8. Por qué importa que no haya nombres de clase repetidos

Verificado por el usuario: ninguna clase de `auth/` comparte nombre simple con una de
`catalog/`. Eso garantiza que no cambien tres cosas que dependen del nombre simple:

- **Beans de Spring**: el nombre por defecto es el de la clase. Dos `@Service` homónimos en
  paquetes distintos romperían el arranque.
- **Entidades JPA**: el nombre de entidad por defecto es el de la clase, y las consultas JPQL
  de `PlayerSQLDAO` (`FROM PlayerSQL ... JOIN player.team`) lo usan. Como no cambia, no hay
  que tocar consultas.
- **Esquemas de OpenAPI**: springdoc nombra los esquemas por el nombre simple de la clase. Si
  el nombre se mantiene, `/v3/api-docs` no cambia (SC-006).

## D9. Base local persistida

`application-local.yml` usa `ddl-auto: update` sobre `jdbc:h2:file`. Las tablas y columnas
salen de `@Table` y `@Column` explícitos, y el nombre de entidad no cambia (D8), así que
Hibernate no ve diferencias de esquema y los datos existentes siguen accesibles (FR-004).

## D10. Línea base y comparación

- **Decisión**: antes de mover nada se miden, sobre la punta de `develop` en la que se rebasea
  la rama, la cantidad de tests (XML de `build/test-results/test`) y el JSON de
  `/v3/api-docs`. Al terminar se repiten y se comparan. El procedimiento está en
  [quickstart.md](./quickstart.md).
- **Motivo**: la línea base del spec (212 tests en 25 clases, commit `dc0be4d`) vale mientras
  `develop` no cambie. Si cambia, el spec indica medir de nuevo.

## D11. SonarCloud

- Solo se analiza `main` (plan Free), así que SC-007 se confirma después del merge (clarificación
  del spec).
- Mover archivos cambia su ruta. Sonar puede mostrar como nuevos issues que ya existían. Si el
  total supera 9, se revisa si alguno surge del movimiento (por ejemplo, un import sin usar) y se
  corrige en un PR de seguimiento. Los demás quedan fuera de alcance según el spec.
- La regla de Sonar sobre ciclos entre paquetes no debería dispararse: D5 muestra que no hay
  ciclos entre clases.
