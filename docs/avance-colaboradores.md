# Avance · Rol colaborador (frente 1)

> Estado del trabajo del **líder de backend**. Sin commitear: todo está en el árbol para que
> lo revises y lo subas tú.
>
> Última actualización: 14 de agosto de 2026

## Resumen en una línea

Los **siete** endpoints funcionan contra la pila real: **47 de 47 comprobaciones en verde**, más
**71 pruebas unitarias**. El colaborador ya no se aprueba por confianza: manda fotos de su documento,
el sistema comprueba que son lo que dicen ser, y **Postgres impide que nadie vea las de otro**
aunque el código se equivoque.

---

## Qué está hecho

| Ticket | Qué | Estado |
|---|---|---|
| SZ-B01 | Contrato de la API | ✅ [`docs/contrato-colaboradores.md`](contrato-colaboradores.md) |
| SZ-B02 | Migración: rol y tabla | ✅ `V5__colaboradores.sql` |
| SZ-B03 | Entidad y transiciones | ✅ con 8 pruebas |
| SZ-B04 | Enviar y consultar la propia | ✅ |
| SZ-B05 | Bandeja del admin | ✅ |
| SZ-B06 | Que el rol cambie de verdad | ✅ resuelto de otra forma — ver abajo |
| SZ-B07 | Verificación de identidad y domicilio | ✅ V6 + adjuntos + purga |
| SZ-B08 | Row Level Security en `usuarios` | ✅ V7, 5 tablas |

**Verificado de extremo a extremo** con `docs/pruebas/colaboradores.sh`: 47/47.

## Ficheros tocados

**Nuevos**

```
docs/contrato-colaboradores.md                  ← el que desbloquea al frontend
docs/avance-colaboradores.md                    ← este documento
usuarios/…/db/migration/V5__colaboradores.sql
usuarios/…/colaborador/EstadoSolicitud.java
usuarios/…/colaborador/SolicitudColaborador.java
usuarios/…/colaborador/SolicitudRepository.java
usuarios/…/colaborador/SolicitudService.java
usuarios/…/colaborador/SolicitudController.java
usuarios/…/colaborador/dto/SolicitudDtos.java
usuarios/…/colaborador/TipoPersona.java          ← de aquí cuelgan las reglas
usuarios/…/colaborador/TipoDocumento.java        ← un patrón por documento
usuarios/…/colaborador/TipoAdjunto.java
usuarios/…/colaborador/AlmacenDocumentos.java    ← lo que toca los ficheros
usuarios/…/colaborador/DocumentoIdentidad.java
usuarios/…/colaborador/DocumentoRepository.java
usuarios/…/colaborador/PurgaDocumentos.java
usuarios/…/shared/error/DatosInvalidosException.java
usuarios/…/db/migration/V6__identidad_colaborador.sql
usuarios/…/db/migration/V7__row_level_security.sql
usuarios/…/shared/seguridad/ContextoRls.java
usuarios/…/shared/seguridad/DataSourceRls.java
usuarios/…/shared/seguridad/RlsConfig.java
usuarios/…/shared/seguridad/VerificacionRls.java
usuarios/src/test/…/colaborador/SolicitudColaboradorTest.java
usuarios/src/test/…/colaborador/AlmacenDocumentosTest.java
usuarios/src/test/…/colaborador/SolicitudRequestTest.java
docs/pruebas/colaboradores.sh
```

**Modificados**

```
usuarios/…/usuario/Rol.java                     + COLABORADOR
usuarios/…/shared/auditoria/AuditoriaService.java  + 3 eventos
usuarios/src/main/resources/application.properties + almacén y retención de documentos
web-gateway/…/config/RutasConfig.java           + ruta /api/colaboradores/**
usuarios/…/shared/error/GlobalExceptionHandler.java + 400 de negocio y 413
docker/Dockerfile.jvm                           + /datos con dueño correcto
docker-compose.yml                              + volumen documentos-identidad
usuarios/…/auth/AuthController.java             + marca de sistema (RLS)
usuarios/…/auth/oauth/OAuth2SuccessHandler.java + marca de sistema (RLS)
usuarios/…/auth/token/TokenService.java         + marca de sistema (RLS)
usuarios/…/auth/token/TokenRevocadoRepository.java + @Transactional propia
usuarios/…/shared/config/AdminSeeder.java       + marca de sistema (RLS)
usuarios/…/colaborador/PurgaDocumentos.java     + marca de sistema (RLS)
```

---

## Las tres decisiones que tomé

### 1 · SZ-B06 no necesitaba revocar tokens

Era el ticket que más miedo daba: el rol viaja dentro del JWT, así que aprobar a alguien no
surtía efecto hasta que su token caducara.

**Al mirar el código resultó que ya estaba medio resuelto.** `AuthService.refrescar()` recarga
al usuario de la base de datos y `construirRespuesta()` usa `usuario.getRol()`. Es decir:

> Un refresco ya emite un token con el rol actualizado.

Así que **no hace falta revocar nada**. Y no conviene: revocar sus tokens le obligaría a volver
a escribir la contraseña, que es peor experiencia por un problema que se arregla solo. Lo que
hace falta es que el cliente refresque cuando vea su solicitud aprobada, y eso está escrito en
el contrato como paso obligatorio para el frontend.

**Ventana real:** hasta 1 hora si el usuario no hace nada (lo que dura el token de un cliente).
Cero si el frontend hace el refresco al detectar la aprobación.

### 2 · Duración del token del colaborador: 1 hora, y ya no hace falta decirlo

Al principio esto fue una decisión: el `switch` sobre el enum `Rol` era exhaustivo, así que añadir
COLABORADOR **rompió la compilación** y obligó a elegir. Le puse lo mismo que a un cliente (1 h)
porque solo toca sus propios productos y todo lo que publica pasa por moderación: el alcance de un
token robado es pequeño.

**Tras integrar el trabajo de Josefernando, el enum desapareció** y con él el switch exhaustivo.
Ahora cualquier rol que no sea EMPLEADO ni ADMINISTRADOR cae en el `default`, que da exactamente
esa hora. El resultado es el mismo y sobra una propiedad de configuración, así que se quitó.

Lo que sí conviene saber: **un rol nuevo de tipo TRABAJADOR creado desde el panel heredaría la
duración de cliente** sin que nadie lo decida. Ya no hay compilador que avise.

### 3 · Un documento identifica a UN negocio

Faltaba: dos cuentas distintas podían reclamar el mismo RUC. O es un error de tecleo o alguien
intenta colgarse de un negocio ajeno, y en los dos casos lo tiene que mirar una persona.

Se rechaza con 409 **sin decir de quién es**: confirmar que ese documento ya está en el sistema,
y a nombre de quién, sería filtrar datos de otro cliente. Las rechazadas no cuentan, así que un
RUC denegado vuelve a quedar libre.

### 4 · Aprobar tenía que contar como cambio de rol

Se me había pasado. `PATCH /usuarios/{id}/rol` incrementa `smartzone_seguridad_cambio_rol_total`,
pero aprobar una solicitud cambia el rol por otro camino y no contaba nada.

El resultado habría sido silencioso y peor que no tener la métrica: el panel enseñaría una cifra
de "cambios de rol" que **excluye la vía por la que van a entrar casi todos los colaboradores**,
y nadie sospecharía que falta la mitad.

Se cuenta al final, después de que todo haya salido bien, igual que en el flujo gemelo: un intento
fallido no es un cambio de rol y no debe inflar el contador.

### 5 · La unicidad de "una pendiente por persona" va en la base

El servicio comprueba antes y da un 409 con mensaje claro, pero **esa no es la defensa real**:
dos peticiones a la vez pasarían las dos la comprobación. Quien de verdad lo impide es un índice
único parcial:

```sql
CREATE UNIQUE INDEX uk_solicitud_pendiente_por_usuario
    ON solicitud_colaborador (usuario_id)
    WHERE estado = 'PENDIENTE';
```

Parcial y no un `UNIQUE` normal porque **quien fue rechazado tiene que poder volver a
intentarlo**. El servicio captura la violación de integridad y la traduce al mismo 409, para que
el usuario vea el mismo mensaje llegue por donde llegue.

---

## Detalles que conviene no perder

- **El solicitante siempre sale del token.** No hay ningún endpoint que acepte `usuarioId` por
  URL ni por cuerpo. Si se aceptara, cualquiera podría solicitar a nombre de otro.
- **`GET /mia` devuelve 204, no 404**, cuando nunca se ha solicitado: no haber pedido nada es un
  estado normal, no un error.
- **Hay dos DTO de respuesta.** El del solicitante no incluye los datos del solicitante (ya sabe
  quién es); el del admin sí, porque los necesita para decidir. Son tipos separados y no un campo
  opcional, para que no se filtre por descuido.
- **La base comprueba la coherencia del estado**, no solo Java: una rechazada tiene motivo y una
  pendiente no puede estar resuelta. Un estado incoherente es de los que nadie sabe explicar
  después.
- **Aprobar y cambiar el rol ocurren en la misma transacción.** Por eso el servicio toca
  `UsuarioRepository` directamente en vez de llamar a `UsuarioService`, que abriría la suya.

---

## Qué deja para producción

| Qué | Estado |
|---|---|
| **Auditoría** | 3 eventos, con el correo enmascarado (`v***@t.com`) |
| **Métricas** | `cambio_rol_total{rol="COLABORADOR"}` sube al aprobar |
| **Limitador** | cubo general, 300 por ventana |
| **Logs de aplicación** | ninguno, **a propósito** |

Lo de los logs merece explicación, porque parece una falta: `UsuarioService`, que hace justo lo
mismo, tampoco tiene `log.info`. En este servicio la traza de negocio va por `AUDITORIA` y
`@Slf4j` está reservado a autenticación e infraestructura. Añadir logs sueltos aquí duplicaría lo
que ya está auditado y encima sin enmascarar el correo.

Sobre el limitador: `/api/colaboradores/**` cae en el cubo general y no necesita uno propio,
porque **la regla de negocio ya lo acota mejor que cualquier cupo** — con una solicitud pendiente
por persona, insistir no consigue nada.

Comprobado con el endpoint autenticado (pide rol `ADMINISTRADOR`, como debe ser):

```bash
curl -s localhost:8082/actuator/prometheus -H "Authorization: Bearer $ADMIN" | grep cambio_rol
#  smartzone_seguridad_cambio_rol_total{aplicacion="usuarios",rol="COLABORADOR"} 1.0
```

---

## Verificación de identidad · las decisiones

### 1 · El tipo de archivo se decide leyendo el archivo

Es lo que sostiene toda esta parte. La extensión y la cabecera `Content-Type` las
**elige quien sube**, así que creerles es dejar entrar cualquier cosa. Se leen los
primeros bytes y se comparan con la firma de JPEG, PNG y PDF.

Nada más. Un SVG se rechaza aunque sea una imagen de verdad: es un documento que puede llevar
scripts dentro, y no compensa por poder mandar un vector.

Está probado con el caso concreto: un ejecutable renombrado a `.jpg` da 400.

### 2 · El nombre que sube el usuario no toca el disco

La ruta se genera entera (`2026/08/<uuid>.jpg`). El nombre original se guarda aparte, solo para
enseñarlo en la bandeja. Así un archivo llamado `../../application.properties` deja de ser un
problema: no se usa para nada que decida dónde se escribe.

### 3 · Las fotos no se sirven como estáticas

Salen por un endpoint que comprueba quién pregunta: el dueño o el administrador. Una carpeta de
fotos de DNI accesible por URL es una fuga esperando a que alguien pruebe números.

Y cuando pregunta un tercero devuelve **404, no 403**. Un 403 confirmaría que ese id existe, y
probando se sabría cuántos documentos hay en el sistema.

### 4 · De qué tipo de persona cuelga todo

`TipoPersona` no es una etiqueta: lleva dentro qué documentos admite, qué campos exige y qué
archivos hay que mandar. Repartir eso en `if`s por el servicio es lo que hace que meses después
alguien añada un caso y se olvide de una de las cinco comprobaciones.

| | NATURAL | JURIDICA |
|---|---|---|
| Documento | DNI o carné de extranjería | RUC (empieza por 10 o 20) |
| Exige | fecha de nacimiento, 18+ | representante legal |
| Archivos | anverso + reverso | anverso + reverso + ficha RUC |

Mandar el campo del otro tipo es un 400. No se ignora en silencio, porque significa que el
formulario está mandando algo que no debería.

### 5 · Aprobar exige los documentos, no solo el alta

La comprobación está en `aprobar()` además de en el alta. Parece redundante y no lo es: vale para
las solicitudes anteriores a esta migración —que se crearon sin adjuntos— y para cualquiera que
llegue por una vía que no hayamos previsto. **Nadie se hace colaborador sin identidad revisable.**

Por eso la migración no toca los estados de las filas viejas: una migración que rechaza
solicitudes de gente real decide por el administrador y encima sin motivo que darle al
solicitante.

### 6 · Las fotos se borran solas

Guardar fotos de DNI para siempre es acumular el peor dato posible sin motivo.

- **Huérfanas** (subidas y nunca enviadas): 7 días.
- **De solicitudes resueltas**: 90 días.

Se borran los bytes, **no la ficha**: hay que poder demostrar que la verificación se hizo aunque
la imagen ya no esté. La respuesta trae `disponible: false` para que el frontend no ofrezca
descargar lo que ya no hay.

### 7 · La versión de los términos se compara

"Aceptó los términos" no dice nada si no se sabe cuáles. El cliente manda la versión que enseñó y
el backend comprueba que es la vigente; si cambiaron mientras rellenaba, se le pide que los lea
otra vez.

---

## Tres cosas que solo salieron al probar

**El límite de multipart era de 2 MB.** Estaba puesto de antes, cuando no había ninguna subida en
todo el proyecto. Mi código decía 5 MB, así que Spring habría cortado antes con un mensaje que
hablaba de un límite distinto del real. Ahora coinciden los dos.

**Una subida grande daba 500.** `MaxUploadSizeExceededException` caía en el manejador genérico, y
el usuario leía "error interno" cuando lo único que pasaba es que su foto pesaba demasiado. Ahora
es un 413 que lo explica.

**El volumen era de root y el proceso corre como `spring`.** La carpeta raíz existía —por eso el
arranque no falló— pero crear la subcarpeta del mes daba `AccessDenied`. Se arregla creando
`/datos` en el Dockerfile con el dueño correcto: Docker copia el propietario de esa carpeta al
volumen la primera vez que lo monta vacío.

---

## Row Level Security · la segunda capa

Era el hueco que quedaba, y ya está cerrado: **`usuarios` tiene RLS en sus 5 tablas** con datos
personales. Antes lo único que impedía ver los datos de otro era que el código se acordara de
filtrar; ahora el filtro lo pone Postgres en el plan de ejecución.

```
Row Level Security activo en las 5 tablas con datos personales
```

### Lo que hace distinto a este servicio

En `catalogo` y `compras` toda petición llega autenticada. **Aquí no.** Entrar y registrarse son,
por definición, operaciones sin identidad: el login busca por correo a alguien de quien todavía no
se sabe nada.

Por eso login, registro, refresco, logout y la entrada por Google/Facebook van marcados como
sistema. No es una excusa para saltarse RLS: es que autenticar es justamente la operación que no
puede exigir identidad previa.

Sin esa marca pasarían dos cosas, y la segunda es grave:

| | Qué pasaría |
|---|---|
| Login | Diría "credenciales incorrectas" a quien las escribió bien |
| Comprobar si un token está revocado | **No encontraría la fila, y lo daría por bueno** |

La segunda **falla abriendo**, que es la peor forma posible de fallar.

### Empleado ≠ administrador

Las políticas distinguen los dos, cosa que el resto del proyecto no hacía:

| Tabla | Quién la ve |
|---|---|
| `usuario` | uno mismo · el personal · el sistema |
| `solicitud_colaborador` | el solicitante · **solo administrador** |
| `documento_identidad` | el dueño · **solo administrador** |

Un empleado atiende pedidos: no tiene por qué ver la foto del DNI de nadie.

### Comprobado sin pasar por la aplicación

Las 45 pruebas de extremo a extremo no demuestran que RLS haga nada, porque el servicio ya
comprobaba permisos por su cuenta. Así que se probó hablando con Postgres directamente, con el rol
de la aplicación:

| Contexto | Documentos que ve | Usuarios que ve |
|---|---|---|
| Sin contexto (anónimo) | 0 | 0 |
| Cliente 40 (dueño de 2) | **2** de 12 | **1** de 31 |
| Otro cliente cualquiera | 0 | — |
| Empleado | **0** | 31 |
| Administrador | 12 | 31 |

Y la consulta hostil, pidiendo a propósito los documentos ajenos:

```sql
SELECT count(*) FROM documento_identidad WHERE usuario_id <> 40;  -- devuelve 0
```

### La trampa que casi me como tres veces

`@Transactional` hace que Spring pida la conexión **antes** de entrar en el cuerpo del método. Y el
contexto de RLS se fija al coger la conexión. Así que marcar como sistema *dentro* de un método
transaccional llega tarde y no sirve de nada.

Por eso la marca va siempre un escalón más afuera: en el controlador para la autenticación, y en
el método programado para las purgas. Aparece tres veces porque se repite en tres sitios y es
invisible: no falla al compilar, falla en ejecución y en silencio.

De paso salió otro: los borrados `@Modifying` del repositorio de tokens no llevaban
`@Transactional` y funcionaban solo porque todos los que llamaban eran transaccionales. Una
dependencia invisible que se rompía en cuanto la llamaba una tarea programada. Ahora la
transacción es de la propia operación.

---

## Integración con el trabajo de Josefernando

**Ya está fusionado.** Él subió `V4__roles_permisos.sql` y convirtió los roles en datos; esto se
adaptó a su diseño, no al revés. Compila, las 55 pruebas pasan y la cadena V1–V7 aplica limpia.

### Lo que hubo que retirar de urgencia

La migración de RLS se había aplicado sobre tablas compartidas, pero su rama no tenía el código
Java que fija el contexto. Comprobado conectando con el rol de la aplicación:

```
SELECT count(*) FROM usuarios.usuario;   →   0
```

Cero filas: quien levantara `usuarios` desde esa rama no podía ni hacer login. Se retiraron las
políticas de la base compartida en cuanto se detectó y quedó como la dejó su V4.

### Los seis ficheros que tocamos los dos

| Fichero | Cómo se resolvió |
|---|---|
| `Rol.java` | **suyo entero**: pasó de enum a entidad JPA |
| `JwtService` | suyo; el `switch` exhaustivo ya no es posible con roles dinámicos |
| `AdminSeeder` | mezclado: su `rol("ADMINISTRADOR")` de texto + la marca de sistema para RLS |
| `AuditoriaService` | mezclado: sus eventos `ROL_*` + los de solicitudes y documentos |
| `GlobalExceptionHandler` | mezclado automáticamente |
| `RutasConfig` (gateway) | las dos rutas, `/api/roles/**` y `/api/colaboradores/**` |

### Lo que cambió en mi código

- `Rol` era un enum y ahora es una entidad: `usuario.rol` es un `String`. Adaptados
  `SolicitudService` y los DTO.
- **COLABORADOR se siembra en `RolSeeder`**, junto a los otros tres, y no con un `INSERT` en la
  migración. Tenerlo en los dos sitios serían dos definiciones del mismo rol que se separarían en
  cuanto alguien cambiase una.
- Fuera `expiracion-colaborador`: el `default` de su switch ya da la hora que le corresponde.

### Dos cosas que decidí y conviene que confirmes

1. **`TipoRol.CLIENTE`, no TRABAJADOR.** Un colaborador vende, pero no es personal de la casa ni
   entra al panel. Es su clasificación, así que díselo.
2. **Sin permisos, y no es un olvido.** El único que le pegaría es `PRODUCTOS_GESTIONAR`, que da
   el catálogo **entero**, incluidos los productos de los demás. Falta un permiso más estrecho
   —del estilo `PRODUCTOS_PROPIOS`— que es justo lo que toca crear cuando se implemente la
   publicación con moderación. Dárselo ahora sería un agujero.

---

## El agujero que tenía y ya no

Salió al repasar qué faltaba, y era mío: **nada limitaba cuántos archivos podía dejar una
cuenta.** El javadoc de `subir()` decía «sustituye al anterior del mismo tipo» y el código no lo
hacía — el comentario mentía.

Las cuentas eran feas: con el cupo general de 300 peticiones por minuto y 5 MB cada una salen
**1,5 GB por minuto**, y los huérfanos no se purgan hasta pasada una semana. Con una cuenta
registrada se llenaba el disco del contenedor.

**Tres capas, de dentro afuera:**

1. **Un índice único parcial** `(usuario_id, tipo) WHERE solicitud_id IS NULL`. Es el que de
   verdad acota el disco: un archivo por tipo y usuario, se suba las veces que se suba.
2. **El servicio sustituye**: borra el anterior del disco y de la base antes de guardar el nuevo.
   Lo que el comentario ya prometía. Los ya enviados con una solicitud no se tocan nunca: son la
   prueba con la que se aprobó.
3. **Cupo propio del limitador**: 30 subidas cada 10 minutos, aparte del general. Contra el ancho
   de banda, no contra el disco, que ya está resuelto arriba.

Comprobado tras la prueba completa: **13 ficheros en disco y 13 filas en la base**, ni un
huérfano, y ningún usuario con dos sueltos del mismo tipo.

## El permiso que faltaba

`PRODUCTOS_PROPIOS`, nuevo, y asignado a COLABORADOR. Es distinto de `PRODUCTOS_GESTIONAR` y no
un caso suyo: aquel da el catálogo **entero, incluidos los productos ajenos**.

Ojo: **`catalogo` todavía no lo comprueba** ni sabe de quién es cada producto (SZ-B08). Hasta que
lo haga, tenerlo no concede nada. Está para que el frente 2 tenga contra qué programar y no se
invente otro nombre.

Un detalle de la siembra que conviene saber: `crearSiFalta` respeta los roles que ya existen, así
que **un permiso nuevo nunca llega a un rol ya creado**. En una base con datos hay que asignarlo
desde el panel.

---

## El hallazgo que hay que contarle al frontend

Probando salió algo contraintuitivo: **`GET /api/auth/yo` lee de la base de datos, no del
token.** Así que justo después de aprobar a alguien:

| Fuente | Dice |
|---|---|
| `GET /api/auth/yo` | `COLABORADOR` |
| El claim dentro del token | `CLIENTE` |
| Lo que decide si un endpoint da 403 | `CLIENTE` |

Si la app pinta el menú a partir de `/auth/yo`, enseñará las opciones de vendedor y todas
devolverán 403. Está avisado en el contrato con una tabla, porque es el error que iban a cometer.

## Lo que falta

### Del resto del equipo

- **Frente 2** ya puede arrancar: la migración del rol existe y el contrato está escrito.
- **Frente 3** tenía asignada la migración (SZ-B02) y la hice yo para no bloquearme. Conviene
  darle otra cosa a cambio — la de `catalogo` (SZ-B08, dueño y estado del producto) es la
  siguiente en la ruta crítica.
- **Frontend**: el contrato está completo y estable. Pueden empezar ya.

---

## Cómo volver a probarlo

```bash
docker compose --profile neon up -d
bash docs/pruebas/colaboradores.sh
```

Deja usuarios y solicitudes de prueba en la base: son inofensivos, pero conviene saberlo. Si la
preparación falla, el script se para y lo dice — casi siempre es el limitador de peticiones.

## El recorrido, paso a paso

```bash
docker compose --profile neon up -d

# 1 · entrar como un cliente cualquiera
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"cliente@…","password":"…"}' | jq -r .accessToken)

# 2 · enviar la solicitud
curl -X POST http://localhost:8080/api/colaboradores/solicitudes \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"nombreComercial":"Importaciones Vega","documento":"20512345678",
       "telefonoContacto":"987654321","direccion":"Av. Los Próceres 1420",
       "rubro":"Componentes de PC",
       "descripcion":"Importamos teclados mecánicos y monitores desde 2019."}'

# 3 · como admin, ver la bandeja y aprobar
curl "http://localhost:8080/api/colaboradores/solicitudes?estado=PENDIENTE" \
  -H "Authorization: Bearer $ADMIN"
curl -X POST http://localhost:8080/api/colaboradores/solicitudes/1/aprobar \
  -H "Authorization: Bearer $ADMIN"

# 4 · el cliente refresca y su token ya dice COLABORADOR
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" -d "{\"refreshToken\":\"$REFRESH\"}"
```

Lo que hay que ver en el paso 4 es `"rol": "COLABORADOR"`. Si sigue diciendo `CLIENTE`, el
problema está en `construirRespuesta` y hay que mirarlo antes de cerrar el ticket.
