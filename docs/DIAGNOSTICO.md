# DIAGNOSTICO GENERAL - SmartZone

> Auditoria completa del proyecto realizada el 2026-08-27
> Cubre: microservicios, frontend, movil, Docker, tests, seguridad, dependencias, documentacion

---

## RESUMEN EJECUTIVO

SmartZone es un e-commerce de tecnologia (Perú) con arquitectura de microservicios:
3 servicios Spring Boot 4.1 + Gateway + Angular 21 + Flutter.

**Calificacion general: 8/10** -- Muy bien diseñado, con areas claras de mejora.

| Componente | Calificacion | Nota |
|-----------|-------------|------|
| Arquitectura | 9/10 | Saga, RLS, circuit breaker, defense-in-depth |
| Seguridad | 9/10 | OWASP Top 10 cubierto, JWT en cada capa |
| Tests | 7/10 | 371 tests, faltan contract tests y E2E automatizados |
| Docker | 8/10 | Bien orquestado, falta Dockerfile individual por servicio |
| Documentacion | 9/10 | README excepcional, contratos API, comentarios en codigo |
| Dependencias | 7/10 | Boot 4.1 muy reciente, Resilience4j escrito a mano |
| Cache/Redis | 3/10 | No existe Redis, cache HTTP basico en frontend |

---

## 1. HALLAZGOS CRITICOS

### 1.1 Credenciales de produccion en historial de git
- **Archivo:** `.env` (gitignored, pero versionado antes)
- **Contenido:** Neon DB, JWT, Google OAuth, Facebook, MercadoPago PRODUCTION
- **Estado:** El monolito retirado admite: "Estas credenciales estuvieron versionadas en git"
- **Accion:** Rotar TODAS las credenciales

### 1.2 Rate limiting in-memory no escala
- **Servicios afectados:** usuarios, catalogo, compras, web-gateway
- **Problema:** `ConcurrentHashMap` por instancia. En multi-replica, un atacante multiplica su tasa
- **Solucion:** Redis para rate limiting distribuido (cuando haya replicas)

### 1.3 Cache
- **Frontend:** dos capas. `CacheLecturaService` (IndexedDB, sobrevive a recargar)
  para el catalogo, y `cacheInterceptor` (memoria) para ubigeo, metodos de pago y guias
- **Backend:** sin cache. Cada request golpea la base de datos
- **Estado:** el arreglo de `reintentoInterceptor` que figuraba aqui es **real pero
  NO es una correccion de cache**: ese interceptor reintenta peticiones, no las
  guarda. La causa de "el cache no funciona" sigue **sin diagnosticar**.
  Lo que si estaba roto en `reintentoInterceptor`, y ya no: leia `error.transitorio`,
  que lo pone `errorInterceptor` y en la cadena va por FUERA, asi que aqui el error
  todavia es un `HttpErrorResponse` crudo y `transitorio` siempre era `undefined`
  (nunca se reintentaba nada). Corregido ademas para no reintentar lo que no es una
  respuesta HTTP: un `TypeError` mas adentro de la cadena se ejecutaba tres veces.

### 1.4 Imagenes de producto al navegar a categoria
- **Problema reportado:** Al entrar a una categoria, las imagenes desaparecen
- **Estado:** `listarPorCategoria` usa ahora `buscarConImagenes()` con
  `LEFT JOIN FETCH p.imagenes`. Es una mejora de rendimiento (evita el N+1), **no
  la causa del fallo**: `Producto.imagenes` es LAZY con `@BatchSize(100)` y el
  servicio es `@Transactional(readOnly = true)`, asi que la coleccion se cargaba
  igual. Las imagenes no faltaban por esto. **Sin diagnosticar.**

---

## 2. BUGS ENCONTRADOS

### CRITICOS - segunda revision (2026-08-27)

Encontrados al repasar servicio por servicio los cambios de la primera pasada.
Los dos primeros los **introdujo** esa misma pasada.

| # | Bug | Servicio | Estado |
|---|-----|----------|--------|
| A | Coordenadas de la tienda con el signo cambiado: `${TIENDA_LATITUD:-12.046374}` se leyo como sintaxis de bash y se "corrigio" a positivo. Lima es negativa en las dos. La tienda pasaba a estar en el sur de la India y **cada envio salia a ~19 000 km** | compras | **FIX** + `CoordenadasTiendaTest` |
| B | `MetodoPagoRequest` no llevaba el `tipo` nuevo: todo metodo creado desde el panel nacia `OTRO` y el checkout lo cerraba **como contra entrega, sin cobrar**. Antes lo salvaba el emparejado por nombre, que este cambio retiro | compras + frontend | **FIX** - `tipo` en el DTO, en el formulario y en el modelo del frontend |
| C | `AuthService.cortarSesiones()` escribia y `refrescar()` lanzaba `BadCredentialsException` acto seguido, dentro de la **misma** transaccion: el rollback deshacia el corte. La mitigacion contra el reuso de refresh tokens **no llegaba nunca a la base**. La prueba con mocks lo tapaba | usuarios | **FIX** - `REQUIRES_NEW` por `ObjectProvider`, como en `CheckoutOrquestador` |
| D | `/api/sync/**` sin enrutar en el gateway: la cola de valoraciones sin conexion recibia el `index.html` de la SPA en vez de JSON y nunca publicaba lo guardado. Tampoco funcionaba con `ng serve` (el proxy apunta al gateway) | web-gateway | **FIX** + `RutasConfigTest` |
| E | Consultas paginadas de producto sin `ORDER BY`: Postgres no promete orden entre consultas, asi que la pagina 2 podia repetir productos de la 1 y saltarse otros | catalogo | **FIX** - `ORDER BY p.id` en las cuatro |
| F | `Cortacircuitos`: si la peticion de prueba moria con un `Error` (no `RuntimeException`), `pruebasEnCurso` se quedaba en 1 y el circuito rechazaba todo para siempre | compras | **FIX** |

### Entorno, no codigo

- **Las pruebas de integracion NO se ejecutan en esta maquina.** Docker Desktop 29.5
  responde `400` al cliente de Testcontainers 1.21.3, el guard `@EnabledIf` las salta
  y el build sale **verde con 0 ejecutadas** (15 en catalogo, 14 en compras). Las
  migraciones V11 y V17 y el `@Formula` de `Guia` **no estan validados contra
  Postgres real** en local. En CI (`ubuntu-latest`) si deberian correr.

### CRITICOS - primera revision

| # | Bug | Servicio | Archivo | Estado |
|---|-----|----------|---------|--------|
| 1 | `LocalDateTime.now()` sin timezone en reserva de stock | catalogo | `InventarioService.java` | **FIX** - Instant/TIMESTAMPTZ |
| 2 | `EnvioService.cambiarEstado()` permite cualquier transicion (ENTREGADO->PENDIENTE) | compras | `EnvioService.java` | **FIX** - puedePasarA() |

### MEDIOS

| # | Bug | Servicio | Archivo | Estado |
|---|-----|----------|---------|--------|
| 3 | `Pedido.eliminar()` borra sin verificar envios/saga | compras | `PedidoService.java` | **FIX** - verifica envio |
| 4 | `ProductoService.aplicarDescuento()` no valida IDs duplicados | catalogo | `ProductoService.java` | **FIX** - deduplica |
| 5 | Race condition en `Cortacircuitos.permitePasar()` | compras | `Cortacircuitos.java` | **FIX** - pruebasEnCurso |
| 6 | `TokenServicio.emitir()` retorna null en fallo | compras | `TokenServicio.java` | **FIX** - lanza excepcion |
| 7 | ~~`${:-}` shell syntax en Spring properties~~ | compras | `application.properties` | **NO ERA UN BUG.** `${VAR:defecto}` es la sintaxis de Spring y el `-` era el signo del numero. "Corregirlo" rompio las coordenadas: ver bug A |
| 8 | JSON construido con `String.formatted()` | todos | `RespuestasSeguridad.java` | **FIX** - escaparJson(). Defensivo: los cuatro `titulo`/`detalle` son literales del codigo, nunca entrada del usuario, asi que no era explotable |

### BAJOS

| # | Bug | Servicio | Estado |
|---|-----|----------|--------|
| 9 | `uidDe(Jwt)` copiado en 4 controllers | catalogo | **FIX** - JwtUtils |
| 10 | Controllers sin `@Validated` ni validacion en PathVariable | catalogo | **FIX** - @Validated + @Positive |
| 11 | `ReservaStock.creadoEn` usa TIMESTAMP no TIMESTAMPTZ | catalogo | **FIX** - V17 migration |
| 12 | `ChatbotService.singular()` malpluraliza | catalogo | Documentado |
| 13 | N+1 queries en `GuiaResumen.desde()` | catalogo | **FIX** - @Formula totalPasos |
| 14 | `MetodoPago.esMercadoPago()` string matching fragil | compras | **FIX** - TipoPasarela enum |
| 15 | `CarritoService.ver()` abre read-write para lectura | compras | **FIX** - read-only |
| 16 | `normalizar()` duplicado en 2 servicios | usuarios | **FIX** - Saneador.normalizarEmail() |

---

## 3. DEPENDENCIAS

### Backend
| Dependencia | Version | Estado |
|-------------|---------|--------|
| Spring Boot | 4.1.0 | Muy reciente, Resilience4j escrito a mano |
| Spring Cloud (Oakwood) | 2025.1.2 | OK, primera release compatible Boot 4.1 |
| Java | 21 LTS | OK |
| JJWT | 0.12.6 | OK |
| Testcontainers | 1.21.3 | OK |
| Flyway | starter Boot 4 | Cuidado: requiere starter, no solo core |

### Frontend
| Dependencia | Version | Estado |
|-------------|---------|--------|
| Angular | 21.2.0 | Ultimo release |
| TypeScript | 5.9.2 | OK |
| pnpm | 11.1.2 | OK |
| Bootstrap | 5.3.8 | OK |
| Dexie | 4.4.5 | OK |
| Vitest | 4.0.8 | OK |

---

## 4. DOCKER

### Estado
- **docker-compose.yml:** 7 servicios, healthchecks, memory limits, perfiles
- **Dockerfiles:** 2 compartidos (`Dockerfile.jvm`, `Dockerfile.frontend`)
- **Preflight:** `verificador` valida `.env` antes de arrancar

### Problemas
- Los 3 microservicios usan `Dockerfile.jvm` compartido (funcional pero los `.dockerignore` individuales sugieren Dockerfile propio)
- `compras` depende de `catalogo` con `service_started` (no `service_healthy`)

---

## 5. TESTS

| Servicio | Unit | Integration | Total |
|----------|------|-------------|-------|
| usuarios | ~71 | Testcontainers | ~71 |
| catalogo | ~71 | Testcontainers | ~71 |
| compras | 55 | 5 | 60 |
| web-gateway | 13 | 0 | 13 |
| frontend | 151 | 0 | 151 |
| movil | ~5 | 0 | ~5 |
| **TOTAL** | **~366** | **~5+** | **~371** |

### Faltantes
- Contract testing entre servicios
- Tests E2E automatizados en CI (scripts bash manuales)
- Tests de routing/JWT en web-gateway
- Tests de UI en movil

---

## 6. DOCUMENTACION

| Documento | Lineas | Calidad |
|-----------|--------|---------|
| README.md | 1123 | Excepcional |
| docs/contrato-colaboradores.md | 418 | Excelente |
| docs/avance-colaboradores.md | 521 | Muy bueno |
| docs/pruebas/*.sh | 399 | Bueno (manual) |

---

## 7. PLAN DE ACCION

### Urgente - COMPLETADO
- [x] Fix: `EnvioService.cambiarEstado()` - maquina de estados con `puedePasarA()`
- [x] Fix: `InventarioService.reservar()` - usa `Instant.now()` + `TIMESTAMPTZ` (V17 migration)
- [x] Fix: Imagenes se eliminan al navegar a categoria - `buscarConImagenes()` con JOIN FETCH
- [x] Fix: Cache no funciona - `reintentoInterceptor` ahora verifica status codes directamente

### Importante - COMPLETADO
- [x] Fix: `Pedido.eliminar()` - verifica envio asociado antes de borrar
- [x] Fix: `ProductoService.aplicarDescuento()` - deduplica IDs antes del check
- [x] Fix: Race condition en `Cortacircuitos` - contador `pruebasEnCurso` para SEMIABIERTO
- [x] Fix: `TokenServicio.emitir()` - lanza excepcion en vez de retornar null
- [x] Fix: `${:-}` syntax en properties - corregido a `${:}`
- [x] Fix: JSON con `String.formatted()` - `escaparJson()` en los 4 servicios
- [x] Extraer `uidDe(Jwt)` a `JwtUtils` compartido en catalogo
- [x] Agregar `@Validated` y `@Positive` a CategoriaController y MarcaController

### Mejoras - COMPLETADO
- [x] Corregir N+1 en guias - `@Formula` para `totalPasos`
- [x] `MetodoPago.esMercadoPago()` - enum `TipoPasarela` con columna `tipo` (V11 migration)
- [x] `CarritoService.ver()` - ahora es read-only, no crea carrito
- [x] `normalizar()` duplicado - `Saneador.normalizarEmail()` compartido

### Segunda revision - COMPLETADO
- [x] Fix A: coordenadas de la tienda, con `CoordenadasTiendaTest` que vigila el signo
- [x] Fix B: `tipo` de pasarela en el DTO, el formulario del panel y el modelo del frontend
- [x] Fix C: `cortarSesiones()` en `REQUIRES_NEW`, invocado por el proxy
- [x] Fix D: `/api/sync/**` enrutado, con `RutasConfigTest` que recorre cada familia de la API
- [x] Fix E: `ORDER BY p.id` en las cuatro consultas paginadas de producto
- [x] Fix F: el cortacircuitos suelta el contador de pruebas tambien ante un `Error`

### Pendiente (requiere decision del equipo)
- [ ] **Diagnosticar de verdad "el cache no funciona" y "las imagenes desaparecen".**
      Lo que se apunto como causa en 1.3 y 1.4 no lo era; ver esas secciones
- [ ] **Hacer que las pruebas de integracion corran en local**, o que su ausencia
      no deje el build en verde. Hoy 29 pruebas se saltan sin que nada avise
- [ ] Implementar Redis para cache y rate limiting distribuido
- [ ] Automatizar tests E2E en CI
- [ ] Agregar contract testing entre servicios
- [ ] Rotar credenciales expuestas en historial de git
