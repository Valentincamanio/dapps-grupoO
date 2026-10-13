# Quickstart: Frontend "Pizarra táctica"

**Rama**: `004-frontend-pizarra-tactica` | **Fecha**: 2026-10-07 | **Plan**: [plan.md](plan.md)

Guía para levantar backend y frontend juntos, recorrer a mano las historias de la spec y correr
en local lo mismo que corre el CI. Los tipos y la forma de la dirección están en
[data-model.md](data-model.md); los endpoints, en [contracts/README.md](contracts/README.md).

## Requisitos

- Java 21 (Temurin), para el backend. Sin Docker.
- Node 20 y npm (la misma versión que el CI).

## 1. Levantar el backend

En una terminal, desde `backend/`:

```bash
./gradlew bootRun
```

Queda escuchando en `http://localhost:8080` con el catálogo semilla. Comprobación:
`http://localhost:8080/players?size=1` devuelve un `PlayerPageResponse` con `totalElements`
entre 50 y 60.

## 2. Levantar el frontend

En otra terminal, desde `frontend/`:

```bash
npm ci
```

```bash
npm run dev
```

Abrir `http://localhost:5173`. Debe redirigir a `/pizarra`. El proxy de Vite manda `/api/*` al
backend: en las herramientas de desarrollo del navegador, los pedidos van a
`http://localhost:5173/api/players?...` y responden 200 (no hay errores de CORS).

## 3. Recorridos de validación

Cada recorrido cita la historia y los requisitos que valida.

### Pizarra sin sesión (historia 2, CE-002, CE-013)

1. Sin iniciar sesión, abrir `/pizarra`.
2. Se ven 12 post-its con nombre, posición en español, equipo y liga legible; el color depende
   de la posición; ninguno muestra precio ni botón de acción.
3. "hoja anterior" está deshabilitada; "hoja 1 de N" coincide con `ceil(totalElements / 12)`.
4. Recorrer con "hoja siguiente" hasta la última: "hoja siguiente" queda deshabilitada y ningún
   jugador se repite.
5. Activar un post-it con Tab + Enter: abre `/jugadores/<id>`.

### Filtros (historia 3, CE-003, CE-004)

1. Tocar la zona "defensa" de la cancha: la dirección pasa a `?position=DEFENDER`, solo hay
   defensores y la zona queda activa.
2. Tocar otra vez "defensa": se quita `position` y "todos" queda activo.
3. Elegir la pestaña "La Liga" y luego, en "equipo: todos ▾", un equipo: la lista solo ofrece
   equipos de La Liga, ordenados. La dirección queda como
   `?position=...&league=LA_LIGA&team=...` sin `page`.
4. Ir a la hoja 2 (si existe) y recargar (F5): se mantienen filtros y hoja. Copiar la dirección
   en una ventana privada (sin sesión): se ve lo mismo.
5. Elegir una combinación sin jugadores (por ejemplo, arco + un equipo sin arqueros en el
   catálogo, o `?team=Inexistente` a mano): aparece la nota que describe la combinación y
   "limpiar filtros" vuelve a `/pizarra`.
6. Casos límite a mano: `?position=XYZ&page=abc` muestra la pizarra completa sin error;
   `?page=40` muestra la nota de pizarra vacía con "volver a la primera hoja".

### Buscador (historia 4, CE-005, CE-006)

1. En cualquier pantalla, escribir una letra: no hay sugerencias.
2. Escribir `mbappe`: aparece "Kylian Mbappé" con posición y equipo (si está en el catálogo
   semilla; si no, probar con otro nombre acentuado).
3. Con flechas y Enter se abre su ficha; Escape cierra las sugerencias y deja el foco en el
   buscador.
4. Escribir `zzz`: "no hay nadie con ese nombre en la pizarra".
5. En la pestaña de red: el catálogo completo (`/api/players?size=50&page=...`) se pide solo la
   primera vez que se usa el buscador o el filtro de equipo, y no se vuelve a pedir.

### Ficha (historia 5)

1. Desde la pizarra filtrada en la hoja 2, abrir un post-it y elegir "volver a la pizarra":
   vuelve con los mismos filtros y hoja.
2. Abrir `/jugadores/999999` y `/jugadores/abc`: "jugador no encontrado" con opción de volver.

### Acceso y vestuario (historias 1 y 6, CE-010, CE-011)

1. Abrir `/vestuario` sin sesión: lleva a `/login` con "para entrar al vestuario tenés que
   iniciar sesión".
2. Ir a "registrarse", crear una cuenta: se muestra la clave de API con "copiar" y la advertencia.
   Intentar recargar: el navegador pide confirmación. "continuar": llega a la pizarra con la
   sesión iniciada y el saldo en la barra superior.
3. Abrir el vestuario: usuario, correo, rol "Usuario" y saldo con dos decimales, sin acciones.
4. Cambiar la contraseña con una actual incorrecta: nota junto al campo. Con la correcta: nota de
   confirmación y formulario vacío.
5. "regenerar clave": pide confirmación; "cancelar" no cambia nada; confirmar muestra la clave
   nueva una sola vez.
6. "cerrar sesión": vuelve a la pizarra sin sesión, que sigue funcionando completa.
7. Sesión vencida: con la sesión iniciada, en las herramientas de desarrollo editar en
   `sessionStorage` el valor de `futbolmarket.session` cambiando `expiresAt` a una fecha pasada
   y recargar `/vestuario`: lleva a `/login` con "tu sesión venció, volvé a entrar" y, al entrar,
   vuelve al vestuario.
8. Error de red: con el frontend abierto, detener el backend y cambiar de hoja: aparece la nota
   "no se pudo conectar" con "reintentar"; volver a levantar el backend y "reintentar" carga la
   hoja sin perder filtros.

### Presentación (CE-007, CE-008, CE-009)

- Recorrer todo solo con teclado: el foco se ve en cada elemento.
- Con el modo responsive del navegador a 360 px: la cancha compacta queda arriba, los post-its en
  una columna y no hay desplazamiento horizontal.
- En ninguna pantalla hay botones ni datos de compra, venta, precio, presupuesto o puntaje.

## 4. Correr lo mismo que el CI

Desde `frontend/`, en este orden (son los pasos de `.github/workflows/frontend-ci.yml`):

```bash
npm ci
```

```bash
npm run lint
```

```bash
npm test
```

```bash
npm run build
```

Resultado esperado: los cuatro terminan con código 0. `npm test` corre `vitest run` (no queda en
modo watch), no necesita el backend porque MSW simula la API, y deja el reporte JUnit en
`frontend/test-results/junit.xml`, que es lo que el CI publica como artifact durante 7 días.
`npm run build` hace el chequeo de tipos de TypeScript strict y genera `frontend/dist/`.

## 5. Definición de terminado del frontend

Se cumple cuando `npm test`, `npm run lint` y `npm run build` pasan (sección 4) y `npm run dev`
funciona contra `./gradlew bootRun` (secciones 1 a 3), según la sección Frontend de la
constitución 2.1.0.
