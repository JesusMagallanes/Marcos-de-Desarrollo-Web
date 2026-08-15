# SmartZone — Frontend (Angular 21)

Angular standalone, sin zonas (`zoneless`), con signals. Reemplaza por completo las plantillas
Thymeleaf del monolito; los servicios Spring quedaron como APIs REST puras.

> La documentación completa del sistema —arquitectura, saga, seguridad, métricas, despliegue—
> está en el [README de la raíz](../README.md). Aquí solo lo propio del frontend.

## Arrancar

```bash
pnpm install
pnpm start          # http://localhost:4200
pnpm run build      # genera dist/frontend/browser
pnpm test           # 65 pruebas
```

`proxy.conf.json` redirige `/api/**`, `/oauth2/**` y `/login/oauth2/**` al gateway (`:8080`),
así que en desarrollo no hay CORS ni puertos incrustados en el código.

## Gestor de paquetes: pnpm

Fijado en `packageManager` del `package.json`. Dos archivos acompañan esa decisión:

- **`pnpm-workspace.yaml`** — lista los paquetes autorizados a compilar binarios nativos
  (`esbuild`, `lmdb`, `@parcel/watcher`, `msgpackr-extract`). pnpm los bloquea por defecto, y
  sin esta lista `pnpm install` termina con código distinto de cero y el CLI de Angular da la
  instalación por fallida.
- **`.npmrc`** — `shamefully-hoist=true`: las herramientas de Angular esperan encontrar las
  dependencias transitivas en `node_modules`, y el aislamiento estricto de pnpm rompe varias
  resoluciones.

Si una dependencia nueva pide compilar, `pnpm approve-builds --all` la añade.

## Estructura

`core/` está organizado igual que el backend, una carpeta por microservicio:

```
src/app/
├── core/
│   ├── shared/       transversal: config, errores, paginación, guards, interceptores
│   ├── usuarios/     :8082  rutas + modelos + services de identidad
│   ├── catalogo/     :8081  productos, categorías, marcas, chatbot
│   └── compras/      :8083  carrito, pedidos, pagos, envíos
├── layout/           header, footer, chatbot
├── pages/            home, categoria, producto-detalle, buscar, carrito, login,
│                     perfil/{cuenta,mis-compras}, envios,
│                     admin/{productos,categorias,marcas,usuarios,metodos-pago},
│                     estaticas, no-encontrado
└── shared/           producto-card
```

Cada carpeta de servicio contiene **sus rutas, sus modelos y sus services**. Las páginas
importan del barrel raíz y no conocen ninguna URL:

```typescript
import { ProductoService, Producto, ErrorApi } from '../../core';
```

Todas las rutas usan `loadComponent`, así que un visitante anónimo no descarga el panel admin.

## Interceptores

| # | Interceptor | Qué hace |
|---|---|---|
| 1 | `correlacion` | Añade `X-Correlation-Id` a todo lo que sale |
| 2 | `error` | Convierte cualquier fallo en `ErrorApi` |
| 3 | `reintento` | Repite los GET que fallaron por algo transitorio |
| 4 | `auth` | Adjunta el JWT; ante un 401 renueva la sesión y reintenta |

El detalle de por qué ese orden, cómo se serializa la renovación del token y qué hace seguro
el reintento está en el [README de la raíz](../README.md#frontend).

## Contrato con el backend

Los 33 endpoints están cubiertos por 11 services, uno por controlador del backend. La tabla
completa de rutas, roles y cuerpos vive en el
[README de la raíz](../README.md#frontend), junto con la explicación de `ErrorApi`,
`EstadoPeticion`, la caché de catálogo y los límites replicados.

---

## Las pantallas de colaborador

Ya están. El contrato del backend, con la forma exacta de cada petición y respuesta, está en
[docs/contrato-colaboradores.md](../docs/contrato-colaboradores.md).

| Ruta | Para quién | Qué hace |
|---|---|---|
| `/vender` | cliente | solicitar: identidad, domicilio y **subida de documentos** |
| `/vender/mis-productos` | colaborador | publicar y editar lo suyo, ver por qué le rechazaron |
| `/admin/solicitudes` | admin | revisar los datos **junto a las fotos**, aprobar o rechazar |
| `/admin/moderacion-productos` | admin | aprobar o rechazar productos de colaborador |

### Cuatro cosas que tienen truco, y cómo están resueltas

**1 · `GET /api/auth/yo` miente sobre el rol.** Lee de la base de datos, no del token, así que
dice `COLABORADOR` en cuanto el admin aprueba — pero el token guardado sigue diciendo `CLIENTE` y
es el token quien decide los 403.

Por eso `/vender` no enseña «ya puedes vender» y ya está: cuando la solicitud está aprobada pero
el token todavía no lo refleja, ofrece un botón de **Activar mi cuenta** que llama a `refrescar()`.
Sin ese paso, el usuario ve que está aprobado y todo le da 403.

**2 · Los archivos se suben de uno en uno, antes de enviar el formulario.** Cada `<input file>`
dispara su `POST /adjuntos?tipo=…`, y al enviar la solicitud **no se mandan los ids**: el backend
coge los últimos de cada tipo. Subir dos veces el mismo tipo **sustituye**, no acumula.

**3 · Para ver un documento no vale un `<img src>`.** El endpoint exige el token, así que se pide
con `HttpClient` (`responseType: 'blob'`) y se monta un `URL.createObjectURL`. La bandeja lo
revoca al cambiar de documento y en `ngOnDestroy`: si no, cada foto de DNI abierta se queda en
memoria hasta recargar.

**4 · Editar un producto lo devuelve a PENDIENTE.** El formulario lo avisa **antes** de guardar,
no después: el colaborador espera que su cambio se vea al momento, y lo que pasa es que su
producto desaparece de la tienda hasta que lo revisen.

### Errores que conviene pintar bien

Las reglas que cruzan campos (tipo de documento contra tipo de persona, mayoría de edad, archivos
que faltan) llegan en `detail` y **ya vienen redactadas para enseñárselas al usuario**. Las de un
solo campo llegan en `errores`, con la clave del campo, para marcarlo en el formulario.

Códigos propios de esta parte: `413` si el archivo pasa de 5 MB y `429` si se pasan de 30 subidas
en 10 minutos.
