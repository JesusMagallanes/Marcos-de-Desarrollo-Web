# Contrato · Solicitudes de colaborador

> **SZ-B01.** Lo que el equipo de frontend necesita para montar las pantallas.
> Todo lo de aquí está implementado y probado contra la pila: 45 comprobaciones
> de extremo a extremo en verde.
>
> Servicio: `usuarios` (:8082) · Se llega por el gateway (:8080) · Prefijo `/api`

## Qué resuelve

Un cliente que quiere vender rellena una solicitud **y demuestra quién es**. El
administrador revisa los datos junto a las fotos de su documento, y aprueba o
rechaza. Al aprobar, el usuario pasa a `COLABORADOR` y puede publicar productos.

## Dos tipos de solicitante

De esto cuelga casi todo lo demás, así que es lo primero que hay que preguntar
en el formulario:

| | **NATURAL** — una persona | **JURIDICA** — una empresa |
|---|---|---|
| Documento | `DNI` (8 dígitos) o `CE` (9–12) | `RUC` (11, empieza por 10 o 20) |
| `nombreTitular` | su nombre completo | la razón social |
| `representanteLegal` | **no se manda** | obligatorio |
| `fechaNacimiento` | obligatoria, 18+ | **no se manda** |
| Archivos | anverso + reverso | anverso + reverso + ficha RUC |

Mandar un campo que no corresponde es un `400`, no se ignora en silencio: si
alguien manda representante legal siendo persona natural, el formulario tiene un
error que conviene enseñar.

## Estados

```
                    ┌──────────────┐
                    │  PENDIENTE   │  ← se crea aquí
                    └──────┬───────┘
                           │
              ┌────────────┴────────────┐
              ▼                         ▼
       ┌─────────────┐          ┌──────────────┐
       │  APROBADA   │          │  RECHAZADA   │
       └─────────────┘          └──────┬───────┘
                                       │
                                       ▼
                            puede volver a solicitar
```

- **Una solicitud pendiente por persona.** Un segundo intento da `409`.
- **De `APROBADA` no se sale.** Quitar el rol es otra operación
  (`PATCH /api/usuarios/{id}/rol`), no una transición de la solicitud.
- **De `RECHAZADA` sí se vuelve a solicitar.** La rechazada se conserva.
- **El rechazo exige motivo**, y se le enseña al solicitante.

---

## El recorrido, en orden

Los archivos **se suben antes** de enviar el formulario, de uno en uno:

```
1. POST /adjuntos?tipo=DOCUMENTO_ANVERSO   (según lo elige)   → id
2. POST /adjuntos?tipo=DOCUMENTO_REVERSO                      → id
3. POST /adjuntos?tipo=FICHA_RUC           (solo empresas)     → id
4. POST /solicitudes  { …datos… }          ← sin ids: se asocian solos
```

**Por qué así y no todo junto:** si viajaran con el formulario, cualquier error
de validación en un campo de texto obligaría a volver a subir varios megas de
fotos. Además permite subir, ver que salió borrosa y volver a subir.

En el paso 4 **no se mandan los ids**. El backend coge los últimos que el
usuario haya subido de cada tipo. Si falta alguno responde `400` diciendo cuál.

---

## Endpoints

### 1 · Subir un archivo

```http
POST /api/colaboradores/solicitudes/adjuntos?tipo=DOCUMENTO_ANVERSO
Authorization: Bearer <token>
Content-Type: multipart/form-data

archivo=<binario>
```

`tipo`: `DOCUMENTO_ANVERSO` · `DOCUMENTO_REVERSO` · `FICHA_RUC`

**201 Created**

```json
{
  "id": 12,
  "tipo": "DOCUMENTO_ANVERSO",
  "etiqueta": "Anverso del documento",
  "nombreOriginal": "dni-frente.jpg",
  "tipoMime": "image/jpeg",
  "tamanoBytes": 184320
}
```

| Código | Cuándo |
|---|---|
| `400` | No es JPG, PNG ni PDF; está vacío; o el `tipo` no existe |
| `401` | Sin token |
| `409` | La cuenta ya es colaborador o más |
| `413` | Pasa de 5 MB |
| `429` | Más de 30 subidas en 10 minutos |

> **Subir otra vez el mismo `tipo` sustituye al anterior**, no lo añade. Es lo que
> espera quien ve la foto borrosa y la repite, y significa que el `id` cambia:
> quedaos siempre con el último que devuelve el backend.

> **Se acepta JPG, PNG y PDF, y nada más.** El tipo se decide **leyendo los
> primeros bytes del archivo**, no por la extensión ni por el `Content-Type`.
> Renombrar un `.exe` a `.jpg` da `400`. Un SVG también se rechaza aunque sea una
> imagen: puede llevar scripts dentro.
>
> Conviene avisarlo en la interfaz antes de que suba 4 MB para nada.

### 2 · Ver un archivo ya subido

```http
GET /api/colaboradores/solicitudes/adjuntos/{id}
Authorization: Bearer <token>
```

Devuelve el archivo. **Solo su dueño y el administrador.** Si pregunta otro, da
`404` y no `403`: un `403` confirmaría que ese id existe, y probando números se
sabría cuántos documentos hay en el sistema.

Llega con `Content-Disposition: attachment` y `Cache-Control: no-store`, así que
el navegador lo descarga en lugar de abrirlo y no queda en la caché. **Para
enseñarlo en la bandeja hay que pedirlo con el token y hacer un blob**; un `<img
src="...">` a pelo no lleva la cabecera de autorización.

`404` también si el archivo ya se borró por retención (ver más abajo).

### 3 · Enviar la solicitud

```http
POST /api/colaboradores/solicitudes
Authorization: Bearer <token>
Content-Type: application/json
```

El solicitante sale del token: **no se manda `usuarioId`**.

```json
{
  "tipoPersona": "JURIDICA",
  "tipoDocumento": "RUC",
  "documento": "20512345678",
  "nombreTitular": "Importaciones Vega SAC",
  "representanteLegal": "Ana Vega Ríos",

  "nombreComercial": "Importaciones Vega",
  "telefonoContacto": "987654321",
  "rubro": "Componentes y periféricos de PC",
  "descripcion": "Importamos teclados mecánicos y monitores desde 2019. Tenemos almacén propio en Lima y factura electrónica.",

  "domicilio": {
    "direccion": "Av. Los Próceres 1420",
    "referencia": "Frente al parque",
    "distrito": "Surco",
    "provincia": "Lima",
    "departamento": "Lima",
    "codigoPostal": "15039",
    "pais": "PE"
  },

  "aceptaTerminos": true,
  "terminosVersion": "2026-08"
}
```

| Campo | Obligatorio | Reglas |
|---|---|---|
| `tipoPersona` | sí | `NATURAL` o `JURIDICA` |
| `tipoDocumento` | sí | tiene que casar con `tipoPersona` |
| `documento` | sí | según el tipo (ver tabla de arriba) |
| `nombreTitular` | sí | 3–160 |
| `representanteLegal` | solo empresas | hasta 160 |
| `fechaNacimiento` | solo personas | pasada, y 18 años cumplidos |
| `nombreComercial` | sí | 3–120 |
| `telefonoContacto` | sí | 9 dígitos |
| `rubro` | sí | hasta 120 |
| `descripcion` | sí | 30–1000 |
| `domicilio.direccion` | sí | hasta 200 |
| `domicilio.referencia` | no | hasta 200 |
| `domicilio.distrito` / `provincia` / `departamento` | sí | hasta 80 |
| `domicilio.codigoPostal` | sí | 5 dígitos |
| `domicilio.pais` | no | dos letras; si falta se asume `PE` |
| `aceptaTerminos` | sí | tiene que ser `true` |
| `terminosVersion` | sí | la que enseñasteis; ver abajo |

**201 Created** devuelve la solicitud creada, con sus adjuntos.

| Código | Cuándo |
|---|---|
| `400` | Un campo no cumple, las reglas cruzadas fallan, o **faltan archivos** |
| `401` | Sin token o caducado |
| `409` | Ya tiene una pendiente, ya es colaborador, o ese documento es de otro |

#### La versión de los términos

Mandad la versión que le enseñasteis al usuario. Si mientras rellenaba el
formulario los términos cambiaron, el backend responde `400` pidiendo que los
lea otra vez. Así no queda gente que aceptó un texto que ya no existe.

La vigente hoy es **`2026-08`**. Si cambia, avisamos.

### 4 · Consultar la propia

```http
GET /api/colaboradores/solicitudes/mia
Authorization: Bearer <token>
```

La **última** del usuario, sea cual sea su estado. **204 No Content** si nunca
envió ninguna — no es un error, no lo pintéis como tal.

```json
{
  "id": 7,
  "estado": "APROBADA",
  "tipoPersona": "JURIDICA",
  "tipoDocumento": "RUC",
  "documento": "20512345678",
  "nombreTitular": "Importaciones Vega SAC",
  "representanteLegal": "Ana Vega Ríos",
  "fechaNacimiento": null,
  "nombreComercial": "Importaciones Vega",
  "telefonoContacto": "987654321",
  "rubro": "Componentes y periféricos de PC",
  "descripcion": "Importamos teclados mecánicos...",
  "domicilio": {
    "direccion": "Av. Los Próceres 1420",
    "referencia": "Frente al parque",
    "distrito": "Surco",
    "provincia": "Lima",
    "departamento": "Lima",
    "codigoPostal": "15039",
    "pais": "PE"
  },
  "adjuntos": [
    {
      "id": 12,
      "tipo": "DOCUMENTO_ANVERSO",
      "etiqueta": "Anverso del documento",
      "nombreOriginal": "dni-frente.jpg",
      "tipoMime": "image/jpeg",
      "tamanoBytes": 184320,
      "subidoEn": "2026-08-14T09:10:00Z",
      "disponible": true
    }
  ],
  "motivoRechazo": null,
  "creadaEn": "2026-08-14T09:12:44Z",
  "resueltaEn": "2026-08-14T15:40:02Z"
}
```

`disponible: false` significa que el archivo se borró por retención: la ficha
sigue, la imagen ya no. No ofrezcáis descargarlo en ese caso.

`adjuntos` trae **exactamente uno por tipo**: dos para una persona, tres para una
empresa. Si alguien subió el anverso cinco veces, solo sobrevive el último.

### 5 · Bandeja del administrador

```http
GET /api/colaboradores/solicitudes?estado=PENDIENTE
Authorization: Bearer <token de ADMINISTRADOR>
```

`estado` es opcional; sin él devuelve todas, las pendientes primero. Cada
elemento es como el anterior **más** el bloque `solicitante`:

```json
"solicitante": {
  "id": 42,
  "nombreCompleto": "Ana Vega Ríos",
  "email": "ana.vega@correo.com",
  "rol": "CLIENTE"
}
```

`403` si quien pregunta no es administrador.

> Para revisar de verdad hay que **comparar `nombreTitular` con lo que se ve en
> la foto**. Merece la pena poner los dos juntos en la pantalla.

### 6 · Aprobar

```http
POST /api/colaboradores/solicitudes/{id}/aprobar
Authorization: Bearer <token de ADMINISTRADOR>
```

Sin cuerpo. Marca la solicitud como `APROBADA` y cambia el rol a `COLABORADOR`,
las dos cosas en la misma transacción.

| Código | Cuándo |
|---|---|
| `403` | No es administrador |
| `404` | No existe |
| `409` | Ya estaba resuelta, **o le faltan archivos de identidad** |

Ese último `409` es la red de seguridad: nadie se hace colaborador sin
documentos que se hayan podido mirar.

### 7 · Rechazar

```http
POST /api/colaboradores/solicitudes/{id}/rechazar
Authorization: Bearer <token de ADMINISTRADOR>

{ "motivo": "La foto del reverso está movida y no se lee el código." }
```

`motivo` obligatorio, 10–500 caracteres. Se le enseña al solicitante. Mismos
códigos que aprobar.

---

## Lo que el frontend tiene que saber sí o sí

### El rol no cambia solo en el navegador

El rol viaja **dentro del token**. Cuando el administrador aprueba a alguien, ese
usuario sigue teniendo un token que dice `CLIENTE`, y lo seguirá teniendo
**hasta una hora**.

No hace falta cerrar sesión: **`POST /api/auth/refresh` ya emite un token con el
rol actualizado**, porque relee al usuario de la base de datos.

```
1. GET /api/colaboradores/solicitudes/mia  →  estado: "APROBADA"
2. POST /api/auth/refresh { refreshToken }  →  llega rol: "COLABORADOR"
3. Guardar los tokens nuevos y refrescar el menú
```

**Si se salta el paso 2**, el usuario ve que está aprobado pero recibe `403` al
publicar, y no entiende por qué. Es el fallo más probable de esta función.

### ⚠ `GET /api/auth/yo` os va a mentir

Salió probando y es contraintuitivo:

| Fuente | De dónde saca el rol | Qué dice justo después de aprobar |
|---|---|---|
| `GET /api/auth/yo` | de la **base de datos** | `COLABORADOR` ✅ |
| El token que tenéis guardado | del **JWT** | `CLIENTE` ❌ |
| Lo que decide si un endpoint responde 403 | del **JWT** | `CLIENTE` ❌ |

O sea: **`/auth/yo` dirá `COLABORADOR` antes de que el usuario pueda actuar como
tal.** Si la app pinta el menú con esa respuesta, enseñará las opciones de
vendedor y todas devolverán `403`.

**Regla práctica:** para decidir qué se le enseña, fiaos del rol que venga en la
respuesta del *login* o del *refresh*, no de `/auth/yo`.

### Los archivos no se borran para siempre… pero casi

- Subidos y **nunca enviados**: se borran a los **7 días**.
- De solicitudes **ya resueltas**: a los **90 días**.

Se borran los bytes, no la ficha: queda constancia de que la verificación se
hizo. Por eso existe `disponible` en la respuesta.

### Todo error tiene la misma forma

RFC 7807, igual que el resto de la API:

```json
{
  "type": "about:blank",
  "title": "Datos inválidos",
  "status": 400,
  "detail": "El DNI son 8 dígitos",
  "errores": { "documento": "..." }
}
```

`errores` solo aparece en los `400` de validación de campo, con el mensaje por
campo para poder marcarlo en el formulario. Las reglas cruzadas (tipo de
documento contra tipo de persona, mayoría de edad, archivos que faltan) llegan
en `detail`, que **ya viene redactado para enseñárselo al usuario**.

### El texto se guarda limpio

El backend recorta espacios, normaliza a NFC y quita caracteres invisibles antes
de guardar. No escapa HTML: el texto se conserva tal cual y se escapa **al
pintarlo**. Angular interpola escapando por defecto, así que basta con no usar
`innerHTML` con estos campos.

---

## Estado de implementación

| Endpoint | Estado |
|---|---|
| `POST /solicitudes/adjuntos` | ✅ |
| `GET /solicitudes/adjuntos/{id}` | ✅ |
| `POST /solicitudes` | ✅ |
| `GET /solicitudes/mia` | ✅ |
| `GET /solicitudes` | ✅ |
| `POST /solicitudes/{id}/aprobar` | ✅ |
| `POST /solicitudes/{id}/rechazar` | ✅ |

Comprobado con `docs/pruebas/colaboradores.sh` (45/45).

Si el frontend detecta que la realidad no coincide con lo escrito aquí, **es un
error del backend**: avisad y lo corregimos, no adaptéis el cliente a la
desviación.
