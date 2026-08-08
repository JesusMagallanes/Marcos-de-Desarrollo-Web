# SmartZone — Tienda de tecnología (microservicios)

Migración del monolito Spring Boot + Thymeleaf a 4 servicios y un frontend Angular.

| Puerto | Módulo | Responsabilidad | BD |
|---|---|---|---|
| 4200 | [frontend/](frontend/) | Angular 21 (solo desarrollo) | — |
| 8080 | [web-gateway/](web-gateway/) | Enruta `/api/**` y sirve el bundle Angular | no |
| 8081 | [catalogo/](catalogo/) | Productos, categorías, marcas, chatbot | esquema `catalogo` |
| 8082 | [usuarios/](usuarios/) | Autenticación, emisión de JWT, usuarios | esquema `usuarios` |
| 8083 | [compras/](compras/) | Carrito, pedidos, pagos, envíos | esquema `compras` |

El monolito original queda en [Web-de-Ventas-de-Computadoras/](Web-de-Ventas-de-Computadoras/) como
referencia. No se arranca.

## Contenido

- [1. Configuración](#1-configuración) · [Google y Facebook](#1b-login-con-google-y-facebook-opcional)
- [2. Arranque](#2-arranque) · [3. Despliegue](#3-despliegue-como-una-sola-app)
- [Decisiones de arquitectura](#decisiones-de-arquitectura) · [Estructura de un servicio](#estructura-de-un-servicio)
- [La saga de compra](#la-saga-de-compra) · [Resiliencia entre servicios](#resiliencia-entre-servicios)
- [Frontend](#frontend)
- [OWASP Top 10](#owasp-top-10-2021) · [DDoS](#defensa-frente-a-abuso-ddos) · [Métricas](#métricas-de-seguridad)
- [Pruebas](#pruebas) · [Pendiente](#pendiente)

---

## 1. Configuración

Toda la configuración sensible vive en **un único `.env` en la raíz**, que está en
`.gitignore` y nunca se sube. La plantilla versionada es `.env.example`.

```bash
cp .env.example .env        # Git Bash
Copy-Item .env.example .env # PowerShell
```

Los cuatro servicios lo leen solos al arrancar:

```properties
spring.config.import=optional:file:../.env[.properties],optional:file:./.env[.properties]
```

**Las variables de entorno del sistema tienen prioridad sobre el archivo.** Eso es lo que
permite usar `.env` en local y secretos inyectados de verdad en producción, sin cambiar nada
del código.

### Qué hay que rellenar

| Variable | Obligatoria | Nota |
|---|---|---|
| `DB_URL` · `DB_USER` · `DB_PASSWORD` | **Sí** | Neon o Postgres local. Cada servicio crea su propio esquema |
| `JWT_SECRET` | **Sí** | Ya viene una clave de 384 bits generada. Idéntica en los cuatro |
| `ADMIN_PASSWORD` | No | Vacío = no se crea administrador inicial |
| `GOOGLE_CLIENT_ID` · `GOOGLE_CLIENT_SECRET` | No | Vacío = sin botón de Google |
| `FACEBOOK_CLIENT_ID` · `FACEBOOK_CLIENT_SECRET` | No | Vacío = sin botón de Facebook |
| `MP_ACCESS_TOKEN` | No | Vacío = el checkout responde 503 |
| `MP_WEBHOOK_SECRET` | No | Ya generado. Pégalo también en el panel de MercadoPago |
| `PAGOEFECTIVO_*` | No | Reservadas; la integración aún no está implementada |

### Comprobación previa

Compose lleva un servicio `verificador` que revisa el `.env` **antes de levantar nada**. El
resto depende de él con `service_completed_successfully`, así que si algo falta la pila no
arranca a medias:

```bash
docker compose run --rm verificador     # comprobar sin levantar nada
```

```
  Obligatorias
    [OK] DB_URL               configurada
    [X]  JWT_SECRET           solo 5 bytes, hacen falta 32

  Opcionales
    [ ]  ADMIN_PASSWORD       Administrador inicial (desactivado)
    [ ]  MP_ACCESS_TOKEN      Cobros con MercadoPago (desactivado)

  Faltan 1 valores obligatorios. Edita el .env de la raiz.
```

Existe porque Compose por sí solo únicamente falla con `JWT_SECRET`: las opcionales vacías
pasan en silencio y levantarías la pila sin administrador, sin login social y con el checkout
devolviendo 503, sin saber por qué.

Además, cada servicio valida su propia configuración al arrancar y se detiene con un mensaje
que dice qué variable falta, en vez de fallar cincuenta líneas más abajo con un error de
Hibernate.

### Generar secretos propios

```bash
node -e "console.log(require('crypto').randomBytes(48).toString('base64'))"   # JWT_SECRET
openssl rand -base64 48                                                        # alternativa
```

## 1b. Login con Google y Facebook (opcional)

Las credenciales van en el mismo `.env` (`GOOGLE_CLIENT_ID`, `FACEBOOK_CLIENT_ID`…).

No se declaran como propiedades `spring.security.oauth2.client.registration.*` porque Spring
valida esas propiedades al arrancar y **un `client-id` vacío tumba el servicio**. En su lugar,
`ProveedoresOAuthConfig` registra en tiempo de arranque solo los proveedores que tengan
credenciales; el resto simplemente no existe, y el frontend consulta
`GET /api/auth/proveedores` para saber qué botones pintar.

**URLs de callback a registrar** (apuntan al gateway, no al servicio):

| Proveedor | Dónde | URI exacta |
|---|---|---|
| Google | [console.cloud.google.com](https://console.cloud.google.com/apis/credentials) → Authorized redirect URIs | `http://localhost:8080/login/oauth2/code/google` |
| Facebook | [developers.facebook.com](https://developers.facebook.com/apps) → Valid OAuth Redirect URIs | `http://localhost:8080/login/oauth2/code/facebook` |

### Cómo funciona el flujo

```
Angular  ──"Continuar con Google"──►  :8080/oauth2/authorization/google
                                              │  (gateway → usuarios)
                                              ▼
                                        Google / Facebook
                                              │
                                              ▼
                                :8080/login/oauth2/code/google
                                              │  (gateway → usuarios)
                                              ▼
                              OAuth2SuccessHandler: crea o busca la cuenta,
                                    firma el JWT y redirige a
                          :4200/oauth/callback#token=…&refresh=…
```

El token viaja en el **fragmento** (`#`), no en la query, para que no acabe en los logs del
servidor ni en la cabecera `Referer`. Angular lo consume, lo guarda y limpia la URL.

**El servicio `usuarios` tiene dos cadenas de seguridad** por este motivo: el flujo de código
de autorización necesita sesión para conservar el `state` entre la ida y la vuelta, mientras
que el resto de la API es stateless. La sesión dura solo el handshake; a partir del JWT todo
vuelve a ser stateless, que es lo único que funciona entre los cuatro servicios.

## 2. Arranque

### Con Docker (recomendado)

```bash
docker compose up --build
```

Levanta Postgres, los cuatro servicios y el frontend en nginx, respetando el orden de arranque
mediante *healthchecks*. Con `--profile neon` omite el Postgres local y usa la base del `.env`.

### Sin Docker (para desarrollar)

Docker reconstruye la imagen en cada cambio, lo que se hace lento mientras programas. Para
iterar sobre un servicio conviene levantarlo a mano y dejar el resto en Compose. Una terminal
por servicio; no hace falta exportar nada, cada uno lee el `.env`:

```bash
cd usuarios     && ./mvnw spring-boot:run     # 8082 — primero: emite los tokens
cd catalogo     && ./mvnw spring-boot:run     # 8081
cd compras      && ./mvnw spring-boot:run     # 8083
cd web-gateway  && ./mvnw spring-boot:run     # 8080
cd frontend     && pnpm start                  # 4200
```

Abre `http://localhost:4200`. El proxy de Angular manda `/api/**` al gateway.

Flyway crea los tres esquemas en el primer arranque de cada servicio.

## 3. Despliegue como una sola app

```bash
cd frontend && pnpm run build
cp -r dist/frontend/browser/* ../web-gateway/src/main/resources/static/
cd ../web-gateway && ./mvnw package
java -jar target/web-gateway-0.0.1-SNAPSHOT.jar
```

El gateway sirve el bundle en `/` con *fallback* a `index.html` para las rutas del router.

---

## Decisiones de arquitectura

**Paquete por funcionalidad, no por capa.** El monolito agrupaba `Model/`, `Repository/`,
`Service/`, `Controller/` — justo lo que hizo invisible dónde estaban las fronteras del
dominio. Ahora cada servicio agrupa por concepto (`producto/`, `carrito/`, `pedido/`) con sus
capas dentro, y `shared/` para seguridad, errores y clientes HTTP.

**Validación de JWT en cada servicio, no solo en el gateway.** Los puertos 8081-8083 son
alcanzables directamente; si la autorización viviera solo en el gateway, bastaría llamar a
`:8081` para saltársela. Se usa `oauth2-resource-server` en lugar de un filtro propio.

**Un esquema por servicio en la misma instancia.** Da propiedad de datos sin el coste de
migrar a tres bases. `ddl-auto=validate` + Flyway: cuatro servicios con `ddl-auto=update`
sobre la misma base sería una carrera de migraciones.

**RestClient, no OpenFeign.** Solo existe una llamada entre servicios (compras → catálogo,
para precios y stock). Un único consumidor no justifica arrastrar Spring Cloud entero.

**Sin Eureka ni Config Server.** Tres destinos fijos y conocidos; el descubrimiento de
servicios resuelve un problema que este sistema no tiene.

**Comentarios cortos en el código, el porqué aquí.** En el código quedan una o dos líneas que
dicen *qué* hace algo; el razonamiento largo —por qué se eligió una opción, qué fallo del
monolito cierra, qué pasaría sin ello— vive en este documento. Así el código se lee rápido y
la explicación no queda desperdigada en cincuenta archivos.

### Estructura de un servicio

Los cuatro comparten el mismo esqueleto, para que moverse entre ellos no requiera aprender
nada nuevo:

```
com.backend.<servicio>/
├── <concepto>/              producto/, carrito/, pedido/, usuario/…
│   ├── XController.java     entrada HTTP, @PreAuthorize, validación
│   ├── XService.java        reglas de negocio y transacciones
│   ├── XRepository.java     acceso a datos (solo lo usa el Service)
│   ├── X.java               entidad JPA
│   └── dto/XDtos.java       records de entrada y salida
└── shared/
    ├── security/            SecurityConfig, RespuestasSeguridad, JWT
    ├── seguridad/           LimitadorPeticiones, RateLimitFilter, cabeceras
    ├── metricas/            Metricas (catálogo de nombres), MetricasSeguridad
    ├── validacion/          Limites (topes de entrada)
    ├── error/               GlobalExceptionHandler y excepciones propias
    └── config/              beans transversales
```

Tres reglas que el monolito rompía:

1. **La entidad nunca sale del `Service`.** Al controlador solo van DTOs (`record`).
2. **El `Repository` solo se inyecta en el `Service`.** El `CarritoController` del monolito
   inyectaba `MetodoPagoRepository` directo y creaba métodos de pago desde el controlador.
3. **Nada de `System.out.println`.** SLF4J y un `@RestControllerAdvice` por servicio.

### La saga de compra

El checkout toca dos servicios y una pasarela externa, repartido en **dos peticiones HTTP**
separadas por el tiempo que el usuario tarde en pagar. Ninguna transacción de base de datos
cubre eso, así que el estado se persiste paso a paso en `saga_checkout` y cada paso tiene su
compensación.

```
Fase 1 — POST /api/pagos/preferencia
  1. RESERVAR_STOCK      catálogo aparta el stock con caducidad  → compensa: liberar
  2. CREAR_PEDIDO        pedido en PENDIENTE (local)             → compensa: cancelar
  3. CREAR_PREFERENCIA   MercadoPago, importe calculado aquí     → sin efecto que deshacer

        ··· el usuario paga en MercadoPago ···

Fase 2 — POST /api/pagos/confirmar
  4. VERIFICAR_PAGO      estado + importe contra la pasarela
  5. CONFIRMAR_STOCK     la reserva pasa a definitiva
  6. PEDIDO → PAGADO
  7. CREAR_ENVIO
  8. VACIAR_CARRITO
```

**Por qué reserva en vez de descontar al final**: descontar al confirmar permite vender dos
veces la última unidad mientras el usuario paga; descontar al empezar la bloquea para siempre
si abandona. La reserva con caducidad resuelve ambos.

**Tres redes de seguridad, por orden:**

1. `SagaBarrendero` compensa las sagas que llevan >25 min esperando pago (cada 2 min).
2. Si la compensación falla, se reintenta (cada 5 min, hasta 5 veces).
3. Si aun así falla, catálogo libera la reserva por caducidad a los 20 min, y la saga queda
   en `FALLIDA` para revisión manual.

La compensación corre en `REQUIRES_NEW`: si la saga principal hace rollback, el registro de
lo compensado debe persistir igualmente.

**Estados de la saga** (`saga_checkout.estado`):

| Estado | Significado |
|---|---|
| `INICIADA` | Se creó el registro; aún no reservó stock |
| `ESPERANDO_PAGO` | Todo listo, el usuario está en MercadoPago |
| `COMPLETADA` | Pago verificado, pedido pagado, envío creado |
| `COMPENSANDO` | Algo falló; se están deshaciendo los pasos |
| `COMPENSADA` | Todo deshecho correctamente |
| `FALLIDA` | La compensación no pudo completarse → **revisión manual** |

**Idempotencia**: `confirmar` con un `payment_id` ya procesado devuelve el pedido existente en
vez de crear otro; las reservas son idempotentes por `referencia` (índice único sobre
`referencia + producto_id`). Recargar la página de retorno no duplica nada.

**Máquina de estados del pedido.** Las transiciones se validan en el backend y se replican en
`pedido.model.ts` del frontend, para no ofrecer botones que acabarían en un 409:

```
PENDIENTE ──► PAGADO ──► EN_TRANSITO ──► ENTREGADO
    │           │             │
    └───────────┴─────────────┴──────► CANCELADO
```

`ENTREGADO` y `CANCELADO` son finales. Una prueba contrasta `siguienteEstado()` con
`puedePasarA()` para que ambas definiciones no se desincronicen — así se detectó que el panel
de envíos ofrecía `PENDIENTE → EN_TRANSITO`, transición que el backend rechaza.

**Idempotencia**: `confirmar` con un `payment_id` ya procesado devuelve el pedido existente en
vez de crear otro; las reservas son idempotentes por `referencia`. Recargar la página de
retorno no duplica nada.

### Resiliencia entre servicios

`compras → catálogo` es la única llamada saliente, y va con:

- **Timeouts** explícitos (3 s conexión / 5 s lectura).
- **Reintentos** con espera exponencial, solo en operaciones idempotentes. Un 4xx (sin stock)
  no se reintenta: es una respuesta de negocio.
- **Cortacircuitos**: tras 5 fallos seguidos se rechaza sin llamar durante 30 s, luego tantea
  con una petición. Sin esto, catálogo caído agota los hilos de compras y lo tumba por arrastre.
- **Correlación**: `X-Correlation-Id` nace en el gateway y se propaga; una compra deja una
  traza seguible en los tres servicios.

Escrito a mano en lugar de Resilience4j porque su integración publicada apunta a Spring Boot 3
y aquí estamos en 4.1.

### Las 5 FK que cruzaban servicios

| Antes (`@ManyToOne`) | Ahora |
|---|---|
| `carrito.usuario_id` → Usuario | `Long usuarioId` |
| `carrito_item.producto_id` → Producto | `Long productoId` |
| `pedido.usuario_id` → Usuario | `Long usuarioId` |
| `detalle_pedido.producto_id` → Producto | `Long productoId` + copia de nombre/imagen/precio |
| `envios.pedido_id` → Pedido | **se mantiene** (misma BD) |

`detalle_pedido` guarda una copia de los datos del producto, así un pedido antiguo no cambia
si luego se edita o borra el producto en catálogo. Y como carrito, pedido y detalle viven en
el mismo esquema, **crear un pedido sigue siendo una transacción local ACID**.

La consulta de envíos por usuario, que en el monolito era un join de tres niveles
(`e.pedido.usuario.id`), ahora se resuelve dentro de `compras`.

---

## Frontend

Angular 21 standalone, sin zonas (`zoneless`), con signals. Reemplaza por completo las
plantillas Thymeleaf: los servicios Spring quedaron como APIs REST puras.

### `core/` organizado igual que el backend

```
src/app/core/
├── shared/       transversal: config, errores, paginación, guards, interceptores
├── usuarios/     :8082  rutas + modelos + services de identidad
├── catalogo/     :8081  productos, categorías, marcas, chatbot
└── compras/      :8083  carrito, pedidos, pagos, envíos
```

Cada carpeta de servicio contiene **sus rutas, sus modelos y sus services**. Las páginas
importan del barrel raíz y no conocen ninguna URL:

```typescript
import { ProductoService, Producto, ErrorApi } from '../../core';
```

Eso es lo que permite reorganizar dentro de `core` sin tocar una sola página.

### Interceptores, en orden

El orden importa: en la ida se ejecutan de arriba abajo, en la vuelta al revés.

| # | Interceptor | Qué hace |
|---|---|---|
| 1 | `correlacion` | Añade `X-Correlation-Id` a todo lo que sale |
| 2 | `error` | Convierte cualquier fallo en `ErrorApi` antes de que nadie lo vea |
| 3 | `reintento` | Repite los GET que fallaron por algo transitorio |
| 4 | `auth` | Adjunta el JWT; ante un 401 renueva la sesión y reintenta |

`error` va antes que `reintento` y `auth` para que ambos decidan por intención
(`transitorio`, `noAutenticado`) en vez de por código HTTP.

**Renovación de sesión sin condiciones de carrera.** Si cinco peticiones reciben 401 a la vez,
solo una canjea el refresh token: como el backend lo rota y revoca el anterior, varios canjes
en paralelo harían que todos menos uno fallaran por «reúso detectado» y cerrarían la sesión sin
motivo. Las demás esperan al token nuevo y se reintentan con él.

**Reintento seguro.** Solo métodos idempotentes (GET, HEAD) y solo fallos transitorios: repetir
un POST podría crear dos pedidos o cobrar dos veces, y un 409 daría exactamente el mismo
resultado. Con un 429 respeta el `Retry-After` del servidor, pero si pide más de 3 segundos se
rinde y deja que la interfaz lo explique.

### `ErrorApi`: un solo formato de error

El interceptor normaliza el `ProblemDetail` del backend y el corte de red en el mismo objeto,
así los componentes escriben por intención:

```typescript
error: (e: ErrorApi) => {
  if (e.conflicto) { /* "Solo quedan 2 unidades de X" */ }
  if (e.limitado)  { /* esperar e.reintentarEn segundos */ }
}
```

| Campo | Cuándo |
|---|---|
| `noAutenticado` · `sinPermiso` | 401 · 403 |
| `noEncontrado` · `conflicto` | 404 · 409 |
| `entradaInvalida` | 400 / 422, con `camposInvalidos` por campo |
| `limitado` · `reintentarEn` | 429, leyendo `Retry-After` |
| `servicioCaido` | 503 o red caída (estado 0) |
| `transitorio` | Lo que merece reintento |
| `correlacionId` | Referencia para cruzar con los registros del backend |

### `EstadoPeticion`

Doce componentes repetían los mismos `cargando`, `error` y `aviso` con el mismo `setTimeout`.
Ahora es un objeto con `iniciar()`, `exito()`, `fallo(e)` que además guarda el `ErrorApi`
completo, no solo texto:

```typescript
protected estado = new EstadoPeticion();

this.estado.iniciar();
servicio.listar().subscribe({
  next: (datos) => { this.datos.set(datos); this.estado.exito(); },
  error: (e: ErrorApi) => this.estado.fallo(e),
});
```

### Caché de catálogo

Productos, categorías y marcas se cachean con `shareReplay`, y toda escritura invalida. El
listado de categorías lo piden a la vez la cabecera, la portada, la página de categoría y tres
pantallas de admin: sin caché eran nueve peticiones idénticas por navegación.

### Límites replicados del backend

`limites.ts` es espejo de `Limites.java`. No sustituye a la validación del servidor —esa es la
que manda— sino que evita el viaje de ida y vuelta para un 400 que se conoce de antemano: una
búsqueda de 200 caracteres se recorta a 80, y `?size=999999` se acota a 100 sin salir del
navegador. Es duplicación consciente: si cambian en el backend, hay que cambiarlos aquí.

## OWASP Top 10 (2021)

| | Riesgo | Qué se hizo |
|---|---|---|
| **A01** | Control de acceso roto | `@PreAuthorize` por endpoint; el `usuarioId` sale del token, nunca de la URL; los ítems del carrito se buscan por `id` **y** `carrito_id`; un pedido ajeno responde 404 (no 403) para no confirmar que existe; validación en **cada** servicio, no solo en el gateway |
| **A02** | Fallos criptográficos | BCrypt con coste 12; el arranque falla si `JWT_SECRET` tiene <32 bytes o es un valor de ejemplo; HSTS; comparación de firmas en tiempo constante |
| **A03** | Inyección | JPA parametrizado en todas las consultas; validación con Bean Validation en cada DTO; el chatbot escapa los nombres de producto antes de meterlos en HTML; Angular sanea con `[innerHTML]` |
| **A04** | Diseño inseguro | Saga con compensaciones; reservas de stock con caducidad; máquina de estados del pedido; importe calculado siempre en el servidor; rate limiting |
| **A05** | Configuración defectuosa | CSP, X-Frame-Options, X-Content-Type-Options, Referrer-Policy, Permissions-Policy; actuator reducido a `/health` sin detalles; sin trazas de pila ni whitelabel en errores; límites de tamaño de cuerpo y cabeceras |
| **A06** | Componentes vulnerables | Spring Boot 4.1.0 y JJWT 0.12.6 (el monolito usaba JJWT 0.11.5, con API retirada) |
| **A07** | Fallos de autenticación | Rate limit en login (10/15 min) y registro (5/h); rotación de refresh token con detección de reúso; revocación por `jti`; contraseñas 8+ con mayúscula, minúscula, número y símbolo; mensajes genéricos que no revelan si el correo existe |
| **A08** | Fallos de integridad | Firma HMAC del webhook de MercadoPago verificada; JWT con emisor y audiencia validados; idempotencia por `payment_id` y por `referencia` |
| **A09** | Registro y monitorización | `AuditoriaService` con logger propio para login, fallos, cambios de rol y borrados; `X-Correlation-Id` propagado entre servicios; correos ofuscados en los logs; nunca se registran tokens ni contraseñas |
| **A10** | SSRF | Las únicas llamadas salientes van a destinos fijos de configuración; el `imageUrl` de producto no se descarga en el servidor |

### Defensa frente a abuso (DDoS)

Rate limiting **en los cuatro servicios**, no solo en el gateway: los puertos 8081-8083 son
alcanzables directamente, así que confiar solo en la puerta de entrada equivaldría a no tener
límite para quien llame de frente.

Los cupos se separan por ámbito según lo caro que sea abusar:

| Ámbito | Cupo | Dónde | Por qué |
|---|---|---|---|
| `autenticacion` | 20 / 15 min | gateway, usuarios | Fuerza bruta contra el login |
| `pagos` | 20 / 10 min | gateway, compras | Cada intento reserva stock real |
| `chatbot` | 30 / min | catalogo | Recorre el catálogo en memoria |
| `escritura` | 120 / min | todos | Uso normal |
| `lectura` | 600 / min | todos | Uso normal |
| `inventario` | 600 / min | catalogo | Contrato interno; lo consume compras, no un navegador |

Al rechazar devuelve **429 con `Retry-After`** en segundos, que el frontend lee y respeta.
El healthcheck y los assets no consumen cupo.

**Implementación**: ventana deslizante en memoria, escrita a mano (~60 líneas) en vez de
Bucket4j, para no añadir una dependencia sobre un Spring Boot recién salido. Tiene un tope de
50 000 claves: si se desborda deja pasar la petición en vez de quedarse sin memoria — el
limitador nunca debe ser lo que tumbe el servicio.

**Limitación conocida**: el cupo es **por instancia**. Con varias réplicas detrás de un
balanceador el límite efectivo se multiplica por el número de réplicas. Para un tope global
haría falta Redis; a esta escala no compensa.

### Topes de entrada

`Limites.java`, idéntico en los tres servicios de dominio. Cada uno cierra un vector concreto
de agotamiento de recursos:

| Tope | Valor | Qué evita |
|---|---|---|
| `MAX_PAGINA` | 100 | `?size=999999999` cargaba la tabla entera en memoria |
| `MAX_BUSQUEDA` | 80 | Un `LIKE '%…%'` con un término larguísimo |
| `MAX_LOTE` | 200 | Consultas de precios desmesuradas desde la saga |
| `TEXTO_BUSQUEDA` | regex | Términos con caracteres de control o payloads |
| `SLUG` | regex | Recorrido de rutas (`../`) en las URLs de categoría |

`@Validated` a nivel de clase es lo que hace que estas anotaciones se apliquen a
`@PathVariable` y `@RequestParam`: sin ella solo se valida el cuerpo.

### Errores: nunca crudos

Un único formato RFC 7807 en los cuatro servicios, cubriendo 12 tipos de error. **Regla sin
excepciones: el detalle técnico va al registro, nunca a la respuesta.** Un mensaje de Hibernate
o una traza revelan versiones, nombres de tabla y estructura interna.

```json
{"detail":"Revisa los parámetros de la petición","status":400,
 "title":"Parámetros inválidos","errores":{"size":"debe ser menor que o igual a 100"}}
```

`RespuestasSeguridad` cubre además los 401 y 403, que Spring Security devolvía con **cuerpo
vacío**. De paso suprime la cabecera `WWW-Authenticate`, que incluía
`resource_metadata="http://localhost:8081/…"` y filtraba la URL interna del servicio.

### Métricas de seguridad

Los cuatro servicios exponen `/actuator/prometheus`, **restringido a ADMINISTRADOR**: las
métricas revelan volumen de tráfico, errores y sesiones activas.

Catálogo común (`Metricas.java`, idéntico en los cuatro), con convención de Prometheus —
prefijo `smartzone_`, contadores en `_total`, medidores sin sufijo:

| Métrica | Tipo | Etiquetas | OWASP |
|---|---|---|---|
| `smartzone_seguridad_autenticacion_total` | contador | `resultado` `motivo` `rol` | A07 |
| `smartzone_seguridad_sesiones_activas` | medidor | — | A07 |
| `smartzone_seguridad_token_total` | contador | `evento` `motivo` | A07 |
| `smartzone_seguridad_autorizacion_denegada_total` | contador | `recurso` | A01 |
| `smartzone_seguridad_cambio_rol_total` | contador | `rol` | A01 |
| `smartzone_seguridad_rate_limit_total` | contador | `ambito` | A04 |
| `smartzone_seguridad_rate_limit_claves_activas` | medidor | — | A04 |
| `smartzone_seguridad_entrada_rechazada_total` | contador | `tipo` | A03 |
| `smartzone_seguridad_integridad_total` | contador | `evento` | A08 |
| `smartzone_compras_saga_total` | contador | `resultado` | — |

Todas llevan además `aplicacion` para distinguir el servicio de origen.

**Dos reglas que se cumplen sin excepción:**

1. **Cardinalidad baja.** Nunca la IP, el correo ni un id como etiqueta: cada valor distinto
   crea una serie temporal y acaba tumbando al recolector, además de filtrar datos personales
   a la monitorización. Hay una prueba que lo verifica.
2. **Sin métricas muertas.** Todo lo declarado se invoca de verdad. Un contador que siempre
   marca cero se lee como «no hay ataques» cuando en realidad significa «no está
   instrumentado». Por eso el gateway declara menos que los servicios de dominio: enruta, no
   valida cuerpos.

**Lo que merece alerta inmediata**: cualquier valor distinto de cero en
`smartzone_seguridad_integridad_total` (reúso de refresh token, webhook falsificado, importe
manipulado) indica manipulación deliberada, no un error de usuario.

### Convención de comentarios

El código lleva comentarios **cortos**: una o dos líneas que dicen qué hace algo cuando no es
evidente por el nombre. Todo el razonamiento —por qué se eligió una opción, qué fallo cierra,
qué pasaría sin ello— está en este documento.

Densidad actual: 6-10 % de líneas de comentario, frente al 11-21 % anterior.

### Pruebas

**68 unitarias de backend** + **50 de frontend** + **6 de integración**:

```bash
# Backend
cd compras  && ./mvnw test      # 36 — saga, cortacircuitos, webhook, estados
cd usuarios && ./mvnw test      # 23 — rate limit, métricas, OAuth, login social
cd catalogo && ./mvnw test      # 31 — límites de entrada, payloads de inyección
cd web-gateway && ./mvnw test   #  8 — limitador bajo concurrencia
cd compras  && ./mvnw verify    # añade las *IT con Testcontainers

# Frontend
cd frontend && pnpm test         # 50 — estado, interceptores, guards, services, modelos
```

`mvn test` corre solo las unitarias; `mvn verify` añade las de integración, que levantan un
PostgreSQL real con Testcontainers para verificar migraciones y consultas JPA. **Si no hay
Docker se saltan en vez de fallar**: un build roto por eso solo enseña al equipo a ignorarlo.

**Verificación de integración ejecutada** contra la base Neon real y la API real de
MercadoPago, con los cuatro servicios y el frontend levantados:

| Bloque | Resultado |
|---|---|
| Salud de los 4 servicios | 4/4 |
| Catálogo público sin token | 4/4 |
| Autenticación (login, registro, duplicados, contraseña débil) | 6/6 |
| Control de acceso por rol e IDOR | 13/13 |
| Seguridad de tokens (manipulado, refresh como acceso, rotación) | 5/5 |
| CRUD de catálogo como admin | 8/8 |
| Carrito cruzando compras → catálogo | 7/7 |
| Saga: reserva, compensación, webhook sin firma | 5/5 |
| Frontend :4200 → gateway :8080 | 4/4 |

Recorrido real de la saga verificado en los logs:

```
stock 5 → preferencia creada (init_point real de MercadoPago) → stock 2 (reservado 3)
       → pedido #2 PENDIENTE por S/ 16 499.70 (calculado en el servidor)
       → segundo checkout: compensa el anterior (libera 3) y reserva de nuevo → stock 2
```

## Fallos del monolito que se cerraron

| Problema original | Cómo queda |
|---|---|
| Cualquier cliente podía asignarse ADMINISTRADOR vía `POST /usuarios/editar/{id}` | `PerfilUpdate` no incluye `rol`; cambiarlo es `PATCH /{id}/rol` solo-ADMIN |
| `/productos/**` era `permitAll`, dejando abiertos editar y eliminar | GET público; POST/PUT/DELETE con `@PreAuthorize("hasRole('ADMINISTRADOR')")` |
| El precio del pago venía del navegador | `POST /api/pagos/preferencia` solo recibe `metodoPagoId`; el total se recalcula del carrito |
| `confirmarPago` creaba el pedido sin verificar nada | Se consulta el pago en MercadoPago, se exige `approved` y se contrasta el importe |
| `eliminarItem(itemId)` borraba ítems de cualquier carrito | Se busca por `id` **y** `carrito_id` a la vez |
| `/pedidos/usuario/{id}` exponía pedidos ajenos | `/api/pedidos/mios`; el id sale del token |
| `JwtAuthFilter` leía el subject antes de validar → 500 | Lo hace `oauth2-resource-server` |
| Login OAuth asignaba `"Aa@12345"` a **todas** las cuentas sociales | Las cuentas OAuth se guardan **sin contraseña** (`password_hash` nulo) y `DetallesUsuarioService` rechaza el login por formulario para ellas |
| OAuth vinculaba por correo sin comprobar que estuviera verificado | Se exige `email_verified`; sin eso no se vincula ni se crea la cuenta |
| El stock nunca se descontaba | Reserva con caducidad al iniciar la compra, confirmada al pagar, con bloqueo pesimista |
| Si el pago se cobraba y algo fallaba después, no había forma de deshacerlo | Saga con compensaciones persistidas y barrendero que cierra lo que quedó a medias |
| El webhook de MercadoPago se aceptaba sin verificar | Firma HMAC comprobada en tiempo constante |
| El refresh token no se podía invalidar | Rotación con revocación por `jti` y detección de reúso |
| Sin límite de intentos de login | 10 por IP cada 15 minutos |
| N+1 en la portada | `JOIN FETCH` en todas las consultas de listado |
| Secretos en `application.properties` | Variables de entorno, sin valores por defecto |

---

## Pendiente

- **La tabla `empleado`** existe en el esquema pero no tiene entidad ni endpoints. En el
  monolito tampoco se usaba (había modelo, pero ningún repositorio).
- **La dirección de envío** se guarda como "Por confirmar": falta pedirla en el checkout.
- **Las pruebas de integración fueron manuales**, no automatizadas. Falta llevarlas a
  Testcontainers para que corran en CI.
- **El pago no se completó de extremo a extremo**: se verificó hasta la creación de la
  preferencia y la compensación. Confirmar el cobro requiere pagar de verdad en el checkout de
  MercadoPago con credenciales de prueba.
- **`auto_return` se omite cuando la URL de retorno es localhost**, porque MercadoPago exige
  una URL que pueda alcanzar. En local el comprador vuelve pulsando el enlace de la pasarela;
  en producción, con `MP_RETORNO_BASE` público, el retorno es automático.
- **El rate limiting es por proceso.** Con varias instancias detrás de un balanceador el cupo
  efectivo se multiplica; para un límite global haría falta Redis.
- **Revocar un rol no invalida los tokens ya emitidos**: el rol viaja dentro del JWT y sigue
  valiendo hasta que caduca (30 min para admin). Queda registrado en auditoría.
- **El webhook solo registra**; la conciliación de pagos que el navegador nunca confirmó está
  por implementar.
