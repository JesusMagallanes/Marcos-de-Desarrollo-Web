# Modelo de datos: análisis de formalización y plan a 4FN

> Análisis hecho el 2026-09-08, rama `modelo-datos-4fn`.
> Cubre las tablas de los tres servicios (`usuarios`, `catalogo`, `compras`).

Este es el paso que faltaba. El esquema creció por migraciones sueltas, cada una
razonable por su cuenta, sin que nadie escribiera las dependencias funcionales.
Sin eso no se puede afirmar en qué forma normal está una tabla, y varias no están
donde se cree.

Notación: `→` dependencia funcional, `↠` dependencia multivaluada.

---

## 1 · En qué forma normal está cada tabla


| Servicio | Tabla                     | FN real    | Problema                                                                  |
| -------- | ------------------------- | ---------- | ------------------------------------------------------------------------- |
| catalogo | `producto`                | **1FN ✗** | `specifications` es una lista en Markdown dentro de una columna           |
| catalogo | `producto`                | 3FN ✗     | `image_url` duplica `producto_imagen` pos. 0; `precio_oferta` es derivada |
| catalogo | `marca`                   | **4FN ✗** | `categoria_id` fuerza una categoría por marca; la relación es M:N       |
| catalogo | `categoria`               | 3FN ✓     | plana: sin jerarquía no modela un catálogo real                         |
| catalogo | `valoracion`              | 3FN ✗     | `nombre` depende de `usuario_id`, no de la clave                          |
| usuarios | `ubigeo`                  | **2FN ✗** | `provincia_id → provincia`, `departamento_id → departamento`            |
| usuarios | `usuario`                 | 3FN ✗     | `address` es derivada de las columnas `dir_*`                             |
| usuarios | `usuario`                 | **4FN ✗** | una sola dirección embebida; un cliente tiene varias                     |
| compras  | `pedido`                  | 3FN ✗     | `total = subtotal + costo_envio`, derivada                                |
| compras  | `detalle_pedido`          | 3FN ✗     | `total = cantidad × precio_unitario`, derivada                           |
| compras  | `envios`, `saga_checkout` | 3FN ~      | dirección repetida en ambas — ver 2.5, aquí es correcto                |

Las marcadas en negrita rompen de verdad: obligan a parsear texto, impiden
consultas que el negocio necesita, o admiten estados contradictorios.

---

## 2 · Las violaciones, una por una

### 2.1 `producto.specifications` — rompe 1FN

```sql
specifications TEXT
-- '· **Pantalla**: 27 pulgadas\n· **Panel**: IPS\n· **Refresco**: 144 Hz'
```

Una lista de pares característica/valor guardada como texto con formato. La
primera forma normal exige valores atómicos, y esto es una tabla entera metida
en una celda.

Lo que cuesta, en concreto:

- **No se puede filtrar.** «Monitores de 27 pulgadas» exige un `LIKE` sobre
  texto que también encontraría «2700 MHz». Un catálogo grande vive de esos
  filtros por facetas.
- **No se pueden comparar** dos productos característica a característica.
- **No hay vocabulario.** Un producto dice `Pantalla`, otro `Tamaño de pantalla`
  y un tercero `Display`. Son lo mismo y nada lo sabe.
- **No hay tipos ni unidades.** `144`, `144Hz` y `144 Hz` conviven.

**Corrección:** `atributo` (el vocabulario, con unidad y tipo) +
`producto_atributo` (el valor para ese producto), con clave
`(producto_id, atributo_id)`.

### 2.2 `marca.categoria_id` — rompe 4FN

```sql
CREATE TABLE marca (..., categoria_id BIGINT NOT NULL, ...)
```

Una marca pertenece a UNA categoría. En un catálogo real es falso: LG vende
monitores, televisores y refrigeradoras. Para representarlo hoy habría que crear
«LG», «LG (TV)» y «LG (Línea blanca)» como marcas distintas, y `uk_marca_name`
lo impide.

Formalmente: en `(marca, categoria, producto)` se cumple `marca ↠ categoria` con
independencia de los productos concretos. Esa dependencia multivaluada no está
proyectada en su propia tabla, que es justo lo que 4FN exige.

**Corrección:** `marca_categoria (marca_id, categoria_id)`.

### 2.3 `ubigeo` — rompe 2FN/3FN

```sql
codigo VARCHAR(6) PRIMARY KEY,
departamento_id, departamento, provincia_id, provincia, distrito
```

Con clave `codigo` hay dependencias entre atributos no primos:

```
provincia_id    → provincia
departamento_id → departamento
```

«Lima» está escrito una vez por cada distrito de la provincia. La migración V10
lo defiende diciendo que «cada distrito pertenece a UNA provincia, no hay nada
que se repita de verdad». Lo primero es cierto; lo segundo no: lo que se repite
es el **nombre**, no la pertenencia. Renombrar una provincia son hoy N UPDATE
que pueden quedarse a medias, y nada impide que dos filas con el mismo
`provincia_id` acaben con nombres distintos.

**Corrección:** `departamento` (25) → `provincia` (196) → `distrito` (1874).
Los desplegables en cascada siguen saliendo de consultas triviales y el nombre
vive en un único sitio.

### 2.4 `usuario` — dirección única y `address` derivada

Dos problemas distintos:

1. `address` está documentada como «se deriva de las columnas `dir_*`». Un
   atributo calculable a partir de otros de la misma fila no pertenece a la
   tabla: puede contradecirlos sin que nadie se entere.
2. Las diez columnas `dir_*` modelan **una** dirección. Un cliente tiene la de
   casa, la del trabajo y la de su madre. Es `usuario ↠ direccion`, una
   dependencia multivaluada que hoy no tiene tabla.

**Corrección:** tabla `direccion` con N filas por usuario, una marcada como
predeterminada, y FK al ubigeo en vez de texto libre.

### 2.5 La dirección repetida en `compras` — aquí NO hay violación

Las mismas ocho columnas están en `saga_checkout` y en `envios`, y se copian de
una a otra. Parece duplicación y no lo es: el envío guarda a dónde se mandó el
paquete **en ese momento**. Si el cliente edita su dirección después, un envío ya
hecho no debe cambiar. Es un dato histórico, igual que
`detalle_pedido.producto_nombre`.

**Corrección:** ninguna por forma normal. Conviene extraer un tipo embebido
compartido para no repetir el DDL, pero es limpieza, no normalización.

### 2.6 Valores derivados almacenados


| Columna                   | Se deriva de                                  | Decisión                    |
| ------------------------- | --------------------------------------------- | ---------------------------- |
| `producto.specifications` | —                                            | se sustituye por tabla (2.1) |
| `producto.image_url`      | `producto_imagen` con `posicion = 0`          | quitar                       |
| `producto.precio_oferta`  | `precio`, `descuento_tipo`, `descuento_valor` | tabla con vigencia           |
| `usuario.address`         | columnas`dir_*`                               | quitar                       |
| `pedido.total`            | `subtotal + costo_envio`                      | **se queda**                 |
| `detalle_pedido.total`    | `cantidad × precio_unitario`                 | **se queda**                 |

Los dos últimos son importes históricos con `CHECK` que los cuadra. Recalcularlos
al vuelo haría que un pedido de hace un año cambiara de importe si cambia la
regla de cálculo. Un almacenamiento derivado deliberado y congelado no es el
mismo defecto que uno que puede desincronizarse en silencio.

---

## 3 · Lo que falta para que sea un catálogo de verdad

Aparte de las formas normales, faltan piezas que cualquier tienda grande
necesita. Sin ellas los datos «son muy pocos» por construcción: no hay dónde
ponerlos.


| Falta                          | Por qué importa                                                                 |
| ------------------------------ | -------------------------------------------------------------------------------- |
| Jerarquía de categorías      | Hoy es un nivel. Un catálogo real es «Tecnología › Computación › Laptops» |
| Producto en varias categorías | Un teclado gamer va en «Periféricos» y en «Gaming»                          |
| Atributos tipados              | Filtros por facetas: RAM, pulgadas, color                                        |
| Variantes                      | El mismo modelo en tres colores y dos capacidades                                |
| Descuentos con vigencia        | Promociones programadas e historial                                              |

Los **atributos tipados ya están y alimentan filtros por facetas reales**: la búsqueda y la
navegación por categoría filtran, ordenan y paginan en servidor consultando `producto_atributo`
(AND entre códigos distintos, OR entre valores del mismo código), y las facetas cuentan sobre el
conjunto filtrado entero. Ver «Búsqueda y navegación por categoría, con filtros en el servidor»
en el [README de la raíz](../README.md#frontend). Lo que sigue pendiente de esta fila son las
**variantes**, no los filtros.

---

## 4 · Plan por etapas

Cada etapa deja el proyecto compilando, con pruebas en verde y desplegable.


| Etapa | Alcance                                                                      | Estado    |
| ----- | ---------------------------------------------------------------------------- | --------- |
| **1** | `catalogo`: taxonomía jerárquica, marca M:N, atributos, datos semilla      | hecha     |
| **2** | `usuarios`: ubigeo a tres tablas, direcciones N por usuario, `address` fuera | pendiente |
| **3** | `catalogo`: descuentos con vigencia, `image_url` derivada                    | pendiente |
| **4** | `catalogo`: variantes de producto                                            | pendiente |

El orden no es arbitrario. La etapa 1 **no cambia ningún contrato REST**, así que
frontend y móvil siguen funcionando sin tocarlos. Las que sí los cambian van
después y de una en una, porque cada una arrastra entidades, DTOs, pruebas, la
aplicación Angular y la de Flutter.
