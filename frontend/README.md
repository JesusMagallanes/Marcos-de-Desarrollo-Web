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
