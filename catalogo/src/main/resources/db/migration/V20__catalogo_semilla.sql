-- Catalogo de arranque, ya sobre el modelo normalizado de V18 y V19.
--
-- Antes de esto la tienda arrancaba VACIA: cero categorias, cero marcas y cero
-- productos. Lo unico sembrado en todo el proyecto eran dos guias, dos metodos
-- de pago y el ubigeo. Con la vitrina vacia no se puede probar la paginacion,
-- ni los filtros, ni la portada, ni el buscador.
--
-- Los datos se insertan por CLAVE NATURAL (slug, nombre, codigo) y nunca por id
-- numerico: los BIGSERIAL los asigna Postgres y dependerian del orden.
--
-- TODO ES IDEMPOTENTE, y no es un adorno defensivo. Una tienda que ya lleva
-- tiempo funcionando tiene categorias y productos creados desde el panel; si
-- esta migracion chocara con UNO SOLO de esos nombres, Flyway abortaria y el
-- servicio no arrancaria. Una semilla que rompe una instalacion con datos es
-- peor que no tener semilla. Lo que ya exista se respeta y se omite.
--
-- IMAGENES: todas apuntan al marcador local /Img/img.png. Es deliberado. Sembrar
-- URLs de fotos ajenas ataria el catalogo a un dominio de terceros que puede
-- caerse o cambiar; las fotos reales se suben desde el panel. La directiva
-- ImagenCaida del frontend ya usa ese mismo marcador cuando una imagen falla.

-- ═══════════ Categorias (arbol de 16 nodos) ═══════════

INSERT INTO categoria (name, slug, description, icono)
    SELECT 'Tecnología', 'tecnologia', 'Todo lo que se enchufa, se programa o se juega.', 'microchip'
     WHERE NOT EXISTS (SELECT 1 FROM categoria WHERE name = 'Tecnología' OR slug = 'tecnologia');
INSERT INTO categoria (name, slug, description, icono)
    SELECT 'Computación', 'computacion', 'Equipos de trabajo y estudio: portátiles, pantallas y piezas.', 'desktop'
     WHERE NOT EXISTS (SELECT 1 FROM categoria WHERE name = 'Computación' OR slug = 'computacion');
INSERT INTO categoria (name, slug, description, icono)
    SELECT 'Laptops', 'laptops', 'Portátiles para estudiar, trabajar y jugar.', 'laptop'
     WHERE NOT EXISTS (SELECT 1 FROM categoria WHERE name = 'Laptops' OR slug = 'laptops');
INSERT INTO categoria (name, slug, description, icono)
    SELECT 'Monitores', 'monitores', 'Pantallas de escritorio, de la ofimática al competitivo.', 'display'
     WHERE NOT EXISTS (SELECT 1 FROM categoria WHERE name = 'Monitores' OR slug = 'monitores');
INSERT INTO categoria (name, slug, description, icono)
    SELECT 'Componentes', 'componentes', 'Procesadores, tarjetas gráficas y memoria para armar o mejorar.', 'memory'
     WHERE NOT EXISTS (SELECT 1 FROM categoria WHERE name = 'Componentes' OR slug = 'componentes');
INSERT INTO categoria (name, slug, description, icono)
    SELECT 'Almacenamiento', 'almacenamiento', 'Discos sólidos, mecánicos y unidades externas.', 'hard-drive'
     WHERE NOT EXISTS (SELECT 1 FROM categoria WHERE name = 'Almacenamiento' OR slug = 'almacenamiento');
INSERT INTO categoria (name, slug, description, icono)
    SELECT 'Periféricos', 'perifericos', 'Lo que se conecta al equipo y se toca todos los días.', 'keyboard'
     WHERE NOT EXISTS (SELECT 1 FROM categoria WHERE name = 'Periféricos' OR slug = 'perifericos');
INSERT INTO categoria (name, slug, description, icono)
    SELECT 'Teclados', 'teclados', 'Mecánicos, de membrana e inalámbricos.', 'keyboard'
     WHERE NOT EXISTS (SELECT 1 FROM categoria WHERE name = 'Teclados' OR slug = 'teclados');
INSERT INTO categoria (name, slug, description, icono)
    SELECT 'Mouse', 'mouse', 'Sensores de precisión para oficina y competitivo.', 'computer-mouse'
     WHERE NOT EXISTS (SELECT 1 FROM categoria WHERE name = 'Mouse' OR slug = 'mouse');
INSERT INTO categoria (name, slug, description, icono)
    SELECT 'Audio', 'audio', 'Audífonos, diademas y micrófonos.', 'headphones'
     WHERE NOT EXISTS (SELECT 1 FROM categoria WHERE name = 'Audio' OR slug = 'audio');
INSERT INTO categoria (name, slug, description, icono)
    SELECT 'Móviles', 'moviles', 'Teléfonos y tabletas.', 'mobile-screen'
     WHERE NOT EXISTS (SELECT 1 FROM categoria WHERE name = 'Móviles' OR slug = 'moviles');
INSERT INTO categoria (name, slug, description, icono)
    SELECT 'Smartphones', 'smartphones', 'Teléfonos de gama de entrada, media y alta.', 'mobile-screen-button'
     WHERE NOT EXISTS (SELECT 1 FROM categoria WHERE name = 'Smartphones' OR slug = 'smartphones');
INSERT INTO categoria (name, slug, description, icono)
    SELECT 'Tablets', 'tablets', 'Tabletas para leer, dibujar y trabajar fuera del escritorio.', 'tablet-screen-button'
     WHERE NOT EXISTS (SELECT 1 FROM categoria WHERE name = 'Tablets' OR slug = 'tablets');
INSERT INTO categoria (name, slug, description, icono)
    SELECT 'Gaming', 'gaming', 'Consolas, sillas y todo lo que rodea al juego.', 'gamepad'
     WHERE NOT EXISTS (SELECT 1 FROM categoria WHERE name = 'Gaming' OR slug = 'gaming');
INSERT INTO categoria (name, slug, description, icono)
    SELECT 'Consolas', 'consolas', 'Sobremesa y portátiles, con sus mandos.', 'gamepad'
     WHERE NOT EXISTS (SELECT 1 FROM categoria WHERE name = 'Consolas' OR slug = 'consolas');
INSERT INTO categoria (name, slug, description, icono)
    SELECT 'Sillas gamer', 'sillas-gamer', 'Asientos para jornadas largas.', 'chair'
     WHERE NOT EXISTS (SELECT 1 FROM categoria WHERE name = 'Sillas gamer' OR slug = 'sillas-gamer');

-- El padre se enlaza despues, cuando ya existen todos los nodos.
UPDATE categoria SET categoria_padre_id = (SELECT id FROM categoria WHERE slug = 'tecnologia')
    WHERE slug = 'computacion';
UPDATE categoria SET categoria_padre_id = (SELECT id FROM categoria WHERE slug = 'computacion')
    WHERE slug = 'laptops';
UPDATE categoria SET categoria_padre_id = (SELECT id FROM categoria WHERE slug = 'computacion')
    WHERE slug = 'monitores';
UPDATE categoria SET categoria_padre_id = (SELECT id FROM categoria WHERE slug = 'computacion')
    WHERE slug = 'componentes';
UPDATE categoria SET categoria_padre_id = (SELECT id FROM categoria WHERE slug = 'computacion')
    WHERE slug = 'almacenamiento';
UPDATE categoria SET categoria_padre_id = (SELECT id FROM categoria WHERE slug = 'tecnologia')
    WHERE slug = 'perifericos';
UPDATE categoria SET categoria_padre_id = (SELECT id FROM categoria WHERE slug = 'perifericos')
    WHERE slug = 'teclados';
UPDATE categoria SET categoria_padre_id = (SELECT id FROM categoria WHERE slug = 'perifericos')
    WHERE slug = 'mouse';
UPDATE categoria SET categoria_padre_id = (SELECT id FROM categoria WHERE slug = 'perifericos')
    WHERE slug = 'audio';
UPDATE categoria SET categoria_padre_id = (SELECT id FROM categoria WHERE slug = 'tecnologia')
    WHERE slug = 'moviles';
UPDATE categoria SET categoria_padre_id = (SELECT id FROM categoria WHERE slug = 'moviles')
    WHERE slug = 'smartphones';
UPDATE categoria SET categoria_padre_id = (SELECT id FROM categoria WHERE slug = 'moviles')
    WHERE slug = 'tablets';
UPDATE categoria SET categoria_padre_id = (SELECT id FROM categoria WHERE slug = 'tecnologia')
    WHERE slug = 'gaming';
UPDATE categoria SET categoria_padre_id = (SELECT id FROM categoria WHERE slug = 'gaming')
    WHERE slug = 'consolas';
UPDATE categoria SET categoria_padre_id = (SELECT id FROM categoria WHERE slug = 'gaming')
    WHERE slug = 'sillas-gamer';


-- ═══════════ Marcas (25) ═══════════

INSERT INTO marca (name, descripcion)
    SELECT 'LG', 'Fabricante surcoreano de electrónica de consumo y paneles.'
     WHERE NOT EXISTS (SELECT 1 FROM marca WHERE name = 'LG');
INSERT INTO marca (name, descripcion)
    SELECT 'Samsung', 'Electrónica de consumo, memoria y pantallas.'
     WHERE NOT EXISTS (SELECT 1 FROM marca WHERE name = 'Samsung');
INSERT INTO marca (name, descripcion)
    SELECT 'ASUS', 'Placas, portátiles y equipo para juego bajo la línea ROG.'
     WHERE NOT EXISTS (SELECT 1 FROM marca WHERE name = 'ASUS');
INSERT INTO marca (name, descripcion)
    SELECT 'Acer', 'Portátiles y monitores de gama amplia.'
     WHERE NOT EXISTS (SELECT 1 FROM marca WHERE name = 'Acer');
INSERT INTO marca (name, descripcion)
    SELECT 'HP', 'Equipos de oficina, portátiles e impresión.'
     WHERE NOT EXISTS (SELECT 1 FROM marca WHERE name = 'HP');
INSERT INTO marca (name, descripcion)
    SELECT 'Dell', 'Portátiles y monitores para empresa y hogar.'
     WHERE NOT EXISTS (SELECT 1 FROM marca WHERE name = 'Dell');
INSERT INTO marca (name, descripcion)
    SELECT 'Lenovo', 'Portátiles ThinkPad, IdeaPad y tabletas.'
     WHERE NOT EXISTS (SELECT 1 FROM marca WHERE name = 'Lenovo');
INSERT INTO marca (name, descripcion)
    SELECT 'Apple', 'Ordenadores, teléfonos y tabletas.'
     WHERE NOT EXISTS (SELECT 1 FROM marca WHERE name = 'Apple');
INSERT INTO marca (name, descripcion)
    SELECT 'Logitech', 'Periféricos de precisión para oficina y juego.'
     WHERE NOT EXISTS (SELECT 1 FROM marca WHERE name = 'Logitech');
INSERT INTO marca (name, descripcion)
    SELECT 'Razer', 'Periféricos y portátiles orientados al competitivo.'
     WHERE NOT EXISTS (SELECT 1 FROM marca WHERE name = 'Razer');
INSERT INTO marca (name, descripcion)
    SELECT 'Corsair', 'Memoria, refrigeración y periféricos mecánicos.'
     WHERE NOT EXISTS (SELECT 1 FROM marca WHERE name = 'Corsair');
INSERT INTO marca (name, descripcion)
    SELECT 'HyperX', 'Audio y teclados con foco en juego.'
     WHERE NOT EXISTS (SELECT 1 FROM marca WHERE name = 'HyperX');
INSERT INTO marca (name, descripcion)
    SELECT 'Xiaomi', 'Teléfonos y ecosistema conectado.'
     WHERE NOT EXISTS (SELECT 1 FROM marca WHERE name = 'Xiaomi');
INSERT INTO marca (name, descripcion)
    SELECT 'Sony', 'Audio, imagen y consolas PlayStation.'
     WHERE NOT EXISTS (SELECT 1 FROM marca WHERE name = 'Sony');
INSERT INTO marca (name, descripcion)
    SELECT 'Redragon', 'Periféricos de juego de precio ajustado.'
     WHERE NOT EXISTS (SELECT 1 FROM marca WHERE name = 'Redragon');
INSERT INTO marca (name, descripcion)
    SELECT 'Kingston', 'Memoria RAM y unidades de estado sólido.'
     WHERE NOT EXISTS (SELECT 1 FROM marca WHERE name = 'Kingston');
INSERT INTO marca (name, descripcion)
    SELECT 'Western Digital', 'Almacenamiento mecánico y sólido.'
     WHERE NOT EXISTS (SELECT 1 FROM marca WHERE name = 'Western Digital');
INSERT INTO marca (name, descripcion)
    SELECT 'Seagate', 'Discos duros de sobremesa y externos.'
     WHERE NOT EXISTS (SELECT 1 FROM marca WHERE name = 'Seagate');
INSERT INTO marca (name, descripcion)
    SELECT 'AMD', 'Procesadores Ryzen y gráficas Radeon.'
     WHERE NOT EXISTS (SELECT 1 FROM marca WHERE name = 'AMD');
INSERT INTO marca (name, descripcion)
    SELECT 'Intel', 'Procesadores Core y gráficas Arc.'
     WHERE NOT EXISTS (SELECT 1 FROM marca WHERE name = 'Intel');
INSERT INTO marca (name, descripcion)
    SELECT 'NVIDIA', 'Tarjetas gráficas GeForce.'
     WHERE NOT EXISTS (SELECT 1 FROM marca WHERE name = 'NVIDIA');
INSERT INTO marca (name, descripcion)
    SELECT 'Nintendo', 'Consolas y videojuegos.'
     WHERE NOT EXISTS (SELECT 1 FROM marca WHERE name = 'Nintendo');
INSERT INTO marca (name, descripcion)
    SELECT 'Microsoft', 'Consolas Xbox y accesorios.'
     WHERE NOT EXISTS (SELECT 1 FROM marca WHERE name = 'Microsoft');
INSERT INTO marca (name, descripcion)
    SELECT 'Cougar', 'Sillas y mobiliario para juego.'
     WHERE NOT EXISTS (SELECT 1 FROM marca WHERE name = 'Cougar');
INSERT INTO marca (name, descripcion)
    SELECT 'Secretlab', 'Sillas ergonómicas de gama alta.'
     WHERE NOT EXISTS (SELECT 1 FROM marca WHERE name = 'Secretlab');

-- Cada marca en TODAS sus categorias: es lo que la V18 vino a permitir.
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'LG' AND c.slug = 'monitores'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'LG' AND c.slug = 'smartphones'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Samsung' AND c.slug = 'monitores'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Samsung' AND c.slug = 'smartphones'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Samsung' AND c.slug = 'tablets'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Samsung' AND c.slug = 'almacenamiento'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'ASUS' AND c.slug = 'laptops'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'ASUS' AND c.slug = 'monitores'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'ASUS' AND c.slug = 'componentes'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Acer' AND c.slug = 'laptops'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Acer' AND c.slug = 'monitores'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'HP' AND c.slug = 'laptops'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'HP' AND c.slug = 'monitores'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Dell' AND c.slug = 'laptops'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Dell' AND c.slug = 'monitores'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Lenovo' AND c.slug = 'laptops'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Lenovo' AND c.slug = 'tablets'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Apple' AND c.slug = 'laptops'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Apple' AND c.slug = 'smartphones'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Apple' AND c.slug = 'tablets'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Logitech' AND c.slug = 'teclados'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Logitech' AND c.slug = 'mouse'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Logitech' AND c.slug = 'audio'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Razer' AND c.slug = 'teclados'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Razer' AND c.slug = 'mouse'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Razer' AND c.slug = 'audio'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Corsair' AND c.slug = 'componentes'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Corsair' AND c.slug = 'teclados'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Corsair' AND c.slug = 'mouse'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Corsair' AND c.slug = 'almacenamiento'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'HyperX' AND c.slug = 'teclados'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'HyperX' AND c.slug = 'audio'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'HyperX' AND c.slug = 'mouse'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Xiaomi' AND c.slug = 'smartphones'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Xiaomi' AND c.slug = 'tablets'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Sony' AND c.slug = 'audio'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Sony' AND c.slug = 'consolas'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Redragon' AND c.slug = 'teclados'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Redragon' AND c.slug = 'mouse'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Redragon' AND c.slug = 'audio'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Kingston' AND c.slug = 'componentes'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Kingston' AND c.slug = 'almacenamiento'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Western Digital' AND c.slug = 'almacenamiento'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Seagate' AND c.slug = 'almacenamiento'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'AMD' AND c.slug = 'componentes'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Intel' AND c.slug = 'componentes'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'NVIDIA' AND c.slug = 'componentes'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Nintendo' AND c.slug = 'consolas'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Microsoft' AND c.slug = 'consolas'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Cougar' AND c.slug = 'sillas-gamer'
    ON CONFLICT DO NOTHING;
INSERT INTO marca_categoria (marca_id, categoria_id) SELECT m.id, c.id
    FROM marca m, categoria c WHERE m.name = 'Secretlab' AND c.slug = 'sillas-gamer'
    ON CONFLICT DO NOTHING;


-- ═══════════ Vocabulario de atributos (22) ═══════════

INSERT INTO atributo (codigo, nombre, unidad, tipo, filtrable) VALUES
    ('pulgadas', 'Tamaño de pantalla', 'pulgadas', 'NUMERO', TRUE) ON CONFLICT (codigo) DO NOTHING;
INSERT INTO atributo (codigo, nombre, unidad, tipo, filtrable) VALUES
    ('resolucion', 'Resolución', NULL, 'TEXTO', TRUE) ON CONFLICT (codigo) DO NOTHING;
INSERT INTO atributo (codigo, nombre, unidad, tipo, filtrable) VALUES
    ('refresco_hz', 'Tasa de refresco', 'Hz', 'NUMERO', TRUE) ON CONFLICT (codigo) DO NOTHING;
INSERT INTO atributo (codigo, nombre, unidad, tipo, filtrable) VALUES
    ('panel', 'Tipo de panel', NULL, 'TEXTO', TRUE) ON CONFLICT (codigo) DO NOTHING;
INSERT INTO atributo (codigo, nombre, unidad, tipo, filtrable) VALUES
    ('procesador', 'Procesador', NULL, 'TEXTO', TRUE) ON CONFLICT (codigo) DO NOTHING;
INSERT INTO atributo (codigo, nombre, unidad, tipo, filtrable) VALUES
    ('ram_gb', 'Memoria RAM', 'GB', 'NUMERO', TRUE) ON CONFLICT (codigo) DO NOTHING;
INSERT INTO atributo (codigo, nombre, unidad, tipo, filtrable) VALUES
    ('almacenamiento_gb', 'Almacenamiento', 'GB', 'NUMERO', TRUE) ON CONFLICT (codigo) DO NOTHING;
INSERT INTO atributo (codigo, nombre, unidad, tipo, filtrable) VALUES
    ('tipo_almacenamiento', 'Tipo de unidad', NULL, 'TEXTO', TRUE) ON CONFLICT (codigo) DO NOTHING;
INSERT INTO atributo (codigo, nombre, unidad, tipo, filtrable) VALUES
    ('sistema_operativo', 'Sistema operativo', NULL, 'TEXTO', TRUE) ON CONFLICT (codigo) DO NOTHING;
INSERT INTO atributo (codigo, nombre, unidad, tipo, filtrable) VALUES
    ('grafica', 'Tarjeta gráfica', NULL, 'TEXTO', TRUE) ON CONFLICT (codigo) DO NOTHING;
INSERT INTO atributo (codigo, nombre, unidad, tipo, filtrable) VALUES
    ('color', 'Color', NULL, 'TEXTO', TRUE) ON CONFLICT (codigo) DO NOTHING;
INSERT INTO atributo (codigo, nombre, unidad, tipo, filtrable) VALUES
    ('conectividad', 'Conectividad', NULL, 'TEXTO', FALSE) ON CONFLICT (codigo) DO NOTHING;
INSERT INTO atributo (codigo, nombre, unidad, tipo, filtrable) VALUES
    ('tipo_switch', 'Tipo de switch', NULL, 'TEXTO', TRUE) ON CONFLICT (codigo) DO NOTHING;
INSERT INTO atributo (codigo, nombre, unidad, tipo, filtrable) VALUES
    ('sensor_dpi', 'Sensor', 'DPI', 'NUMERO', TRUE) ON CONFLICT (codigo) DO NOTHING;
INSERT INTO atributo (codigo, nombre, unidad, tipo, filtrable) VALUES
    ('inalambrico', 'Inalámbrico', NULL, 'BOOLEANO', TRUE) ON CONFLICT (codigo) DO NOTHING;
INSERT INTO atributo (codigo, nombre, unidad, tipo, filtrable) VALUES
    ('cancelacion_ruido', 'Cancelación de ruido', NULL, 'BOOLEANO', TRUE) ON CONFLICT (codigo) DO NOTHING;
INSERT INTO atributo (codigo, nombre, unidad, tipo, filtrable) VALUES
    ('bateria_mah', 'Batería', 'mAh', 'NUMERO', FALSE) ON CONFLICT (codigo) DO NOTHING;
INSERT INTO atributo (codigo, nombre, unidad, tipo, filtrable) VALUES
    ('camara_mp', 'Cámara principal', 'MP', 'NUMERO', TRUE) ON CONFLICT (codigo) DO NOTHING;
INSERT INTO atributo (codigo, nombre, unidad, tipo, filtrable) VALUES
    ('peso_kg', 'Peso', 'kg', 'NUMERO', FALSE) ON CONFLICT (codigo) DO NOTHING;
INSERT INTO atributo (codigo, nombre, unidad, tipo, filtrable) VALUES
    ('garantia_meses', 'Garantía', 'meses', 'NUMERO', FALSE) ON CONFLICT (codigo) DO NOTHING;
INSERT INTO atributo (codigo, nombre, unidad, tipo, filtrable) VALUES
    ('capacidad_kg', 'Capacidad de carga', 'kg', 'NUMERO', FALSE) ON CONFLICT (codigo) DO NOTHING;
INSERT INTO atributo (codigo, nombre, unidad, tipo, filtrable) VALUES
    ('material', 'Material', NULL, 'TEXTO', FALSE) ON CONFLICT (codigo) DO NOTHING;


-- ═══════════ Productos (68) ═══════════

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'ASUS TUF Gaming A15 FA507', 'Portátil de juego con chasis reforzado y refrigeración de doble ventilador.', 4299.00, '/Img/img.png', 14, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'laptops' AND m.name = 'ASUS'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'ASUS TUF Gaming A15 FA507');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'ASUS TUF Gaming A15 FA507'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'AMD Ryzen 7 7435HS', NULL, 0 FROM producto p, atributo a
    WHERE p.name = 'ASUS TUF Gaming A15 FA507' AND a.codigo = 'procesador'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '16', 16.000, 1 FROM producto p, atributo a
    WHERE p.name = 'ASUS TUF Gaming A15 FA507' AND a.codigo = 'ram_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '512', 512.000, 2 FROM producto p, atributo a
    WHERE p.name = 'ASUS TUF Gaming A15 FA507' AND a.codigo = 'almacenamiento_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'SSD NVMe', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'ASUS TUF Gaming A15 FA507' AND a.codigo = 'tipo_almacenamiento'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '15.6', 15.600, 4 FROM producto p, atributo a
    WHERE p.name = 'ASUS TUF Gaming A15 FA507' AND a.codigo = 'pulgadas'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'NVIDIA RTX 4060', NULL, 5 FROM producto p, atributo a
    WHERE p.name = 'ASUS TUF Gaming A15 FA507' AND a.codigo = 'grafica'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Windows 11', NULL, 6 FROM producto p, atributo a
    WHERE p.name = 'ASUS TUF Gaming A15 FA507' AND a.codigo = 'sistema_operativo'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '2.2', 2.200, 7 FROM producto p, atributo a
    WHERE p.name = 'ASUS TUF Gaming A15 FA507' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '24', 24.000, 8 FROM producto p, atributo a
    WHERE p.name = 'ASUS TUF Gaming A15 FA507' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Acer Aspire 5 A515-58', 'Equilibrio entre precio y rendimiento para estudio y teletrabajo.', 2399.00, '/Img/img.png', 22, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'laptops' AND m.name = 'Acer'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Acer Aspire 5 A515-58');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Acer Aspire 5 A515-58'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Intel Core i5-1335U', NULL, 0 FROM producto p, atributo a
    WHERE p.name = 'Acer Aspire 5 A515-58' AND a.codigo = 'procesador'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '16', 16.000, 1 FROM producto p, atributo a
    WHERE p.name = 'Acer Aspire 5 A515-58' AND a.codigo = 'ram_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '512', 512.000, 2 FROM producto p, atributo a
    WHERE p.name = 'Acer Aspire 5 A515-58' AND a.codigo = 'almacenamiento_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'SSD NVMe', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'Acer Aspire 5 A515-58' AND a.codigo = 'tipo_almacenamiento'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '15.6', 15.600, 4 FROM producto p, atributo a
    WHERE p.name = 'Acer Aspire 5 A515-58' AND a.codigo = 'pulgadas'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Windows 11', NULL, 5 FROM producto p, atributo a
    WHERE p.name = 'Acer Aspire 5 A515-58' AND a.codigo = 'sistema_operativo'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '1.8', 1.800, 6 FROM producto p, atributo a
    WHERE p.name = 'Acer Aspire 5 A515-58' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 7 FROM producto p, atributo a
    WHERE p.name = 'Acer Aspire 5 A515-58' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'HP Pavilion 14-dv2020', 'Portátil ligero de 14 pulgadas con pantalla IPS y buena autonomía.', 2899.00, '/Img/img.png', 11, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'laptops' AND m.name = 'HP'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'HP Pavilion 14-dv2020');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'HP Pavilion 14-dv2020'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Intel Core i7-1255U', NULL, 0 FROM producto p, atributo a
    WHERE p.name = 'HP Pavilion 14-dv2020' AND a.codigo = 'procesador'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '16', 16.000, 1 FROM producto p, atributo a
    WHERE p.name = 'HP Pavilion 14-dv2020' AND a.codigo = 'ram_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '1024', 1024.000, 2 FROM producto p, atributo a
    WHERE p.name = 'HP Pavilion 14-dv2020' AND a.codigo = 'almacenamiento_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'SSD NVMe', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'HP Pavilion 14-dv2020' AND a.codigo = 'tipo_almacenamiento'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '14', 14.000, 4 FROM producto p, atributo a
    WHERE p.name = 'HP Pavilion 14-dv2020' AND a.codigo = 'pulgadas'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Windows 11', NULL, 5 FROM producto p, atributo a
    WHERE p.name = 'HP Pavilion 14-dv2020' AND a.codigo = 'sistema_operativo'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '1.4', 1.400, 6 FROM producto p, atributo a
    WHERE p.name = 'HP Pavilion 14-dv2020' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 7 FROM producto p, atributo a
    WHERE p.name = 'HP Pavilion 14-dv2020' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Dell Inspiron 15 3520', 'Portátil de uso diario con teclado numérico y puertos de sobra.', 2149.00, '/Img/img.png', 18, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'laptops' AND m.name = 'Dell'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Dell Inspiron 15 3520');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Dell Inspiron 15 3520'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Intel Core i5-1235U', NULL, 0 FROM producto p, atributo a
    WHERE p.name = 'Dell Inspiron 15 3520' AND a.codigo = 'procesador'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '8', 8.000, 1 FROM producto p, atributo a
    WHERE p.name = 'Dell Inspiron 15 3520' AND a.codigo = 'ram_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '512', 512.000, 2 FROM producto p, atributo a
    WHERE p.name = 'Dell Inspiron 15 3520' AND a.codigo = 'almacenamiento_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'SSD NVMe', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'Dell Inspiron 15 3520' AND a.codigo = 'tipo_almacenamiento'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '15.6', 15.600, 4 FROM producto p, atributo a
    WHERE p.name = 'Dell Inspiron 15 3520' AND a.codigo = 'pulgadas'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Windows 11', NULL, 5 FROM producto p, atributo a
    WHERE p.name = 'Dell Inspiron 15 3520' AND a.codigo = 'sistema_operativo'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '1.9', 1.900, 6 FROM producto p, atributo a
    WHERE p.name = 'Dell Inspiron 15 3520' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 7 FROM producto p, atributo a
    WHERE p.name = 'Dell Inspiron 15 3520' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Lenovo IdeaPad Slim 3 15IAN8', 'Delgado y silencioso, pensado para clase y ofimática.', 1899.00, '/Img/img.png', 25, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'laptops' AND m.name = 'Lenovo'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Lenovo IdeaPad Slim 3 15IAN8');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Lenovo IdeaPad Slim 3 15IAN8'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Intel Core i3-N305', NULL, 0 FROM producto p, atributo a
    WHERE p.name = 'Lenovo IdeaPad Slim 3 15IAN8' AND a.codigo = 'procesador'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '8', 8.000, 1 FROM producto p, atributo a
    WHERE p.name = 'Lenovo IdeaPad Slim 3 15IAN8' AND a.codigo = 'ram_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '256', 256.000, 2 FROM producto p, atributo a
    WHERE p.name = 'Lenovo IdeaPad Slim 3 15IAN8' AND a.codigo = 'almacenamiento_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'SSD NVMe', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'Lenovo IdeaPad Slim 3 15IAN8' AND a.codigo = 'tipo_almacenamiento'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '15.6', 15.600, 4 FROM producto p, atributo a
    WHERE p.name = 'Lenovo IdeaPad Slim 3 15IAN8' AND a.codigo = 'pulgadas'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Windows 11', NULL, 5 FROM producto p, atributo a
    WHERE p.name = 'Lenovo IdeaPad Slim 3 15IAN8' AND a.codigo = 'sistema_operativo'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '1.6', 1.600, 6 FROM producto p, atributo a
    WHERE p.name = 'Lenovo IdeaPad Slim 3 15IAN8' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 7 FROM producto p, atributo a
    WHERE p.name = 'Lenovo IdeaPad Slim 3 15IAN8' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Lenovo ThinkPad E14 Gen 5', 'Portátil de empresa con teclado de referencia y chasis resistente.', 3999.00, '/Img/img.png', 8, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'laptops' AND m.name = 'Lenovo'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Lenovo ThinkPad E14 Gen 5');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Lenovo ThinkPad E14 Gen 5'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'AMD Ryzen 7 7730U', NULL, 0 FROM producto p, atributo a
    WHERE p.name = 'Lenovo ThinkPad E14 Gen 5' AND a.codigo = 'procesador'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '16', 16.000, 1 FROM producto p, atributo a
    WHERE p.name = 'Lenovo ThinkPad E14 Gen 5' AND a.codigo = 'ram_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '512', 512.000, 2 FROM producto p, atributo a
    WHERE p.name = 'Lenovo ThinkPad E14 Gen 5' AND a.codigo = 'almacenamiento_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'SSD NVMe', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'Lenovo ThinkPad E14 Gen 5' AND a.codigo = 'tipo_almacenamiento'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '14', 14.000, 4 FROM producto p, atributo a
    WHERE p.name = 'Lenovo ThinkPad E14 Gen 5' AND a.codigo = 'pulgadas'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Windows 11 Pro', NULL, 5 FROM producto p, atributo a
    WHERE p.name = 'Lenovo ThinkPad E14 Gen 5' AND a.codigo = 'sistema_operativo'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '1.4', 1.400, 6 FROM producto p, atributo a
    WHERE p.name = 'Lenovo ThinkPad E14 Gen 5' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '36', 36.000, 7 FROM producto p, atributo a
    WHERE p.name = 'Lenovo ThinkPad E14 Gen 5' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Apple MacBook Air 13 M2', 'Sin ventilador, silencioso y con autonomía de jornada completa.', 5499.00, '/Img/img.png', 9, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'laptops' AND m.name = 'Apple'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Apple MacBook Air 13 M2');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Apple MacBook Air 13 M2'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Apple M2', NULL, 0 FROM producto p, atributo a
    WHERE p.name = 'Apple MacBook Air 13 M2' AND a.codigo = 'procesador'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '8', 8.000, 1 FROM producto p, atributo a
    WHERE p.name = 'Apple MacBook Air 13 M2' AND a.codigo = 'ram_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '256', 256.000, 2 FROM producto p, atributo a
    WHERE p.name = 'Apple MacBook Air 13 M2' AND a.codigo = 'almacenamiento_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'SSD', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'Apple MacBook Air 13 M2' AND a.codigo = 'tipo_almacenamiento'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '13.6', 13.600, 4 FROM producto p, atributo a
    WHERE p.name = 'Apple MacBook Air 13 M2' AND a.codigo = 'pulgadas'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'macOS', NULL, 5 FROM producto p, atributo a
    WHERE p.name = 'Apple MacBook Air 13 M2' AND a.codigo = 'sistema_operativo'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '1.24', 1.240, 6 FROM producto p, atributo a
    WHERE p.name = 'Apple MacBook Air 13 M2' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 7 FROM producto p, atributo a
    WHERE p.name = 'Apple MacBook Air 13 M2' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Razer Blade 15 Advanced', 'Chasis de aluminio unibody y pantalla de alta frecuencia.', 9499.00, '/Img/img.png', 4, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'laptops' AND m.name = 'Razer'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Razer Blade 15 Advanced');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Razer Blade 15 Advanced'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Intel Core i7-13800H', NULL, 0 FROM producto p, atributo a
    WHERE p.name = 'Razer Blade 15 Advanced' AND a.codigo = 'procesador'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '32', 32.000, 1 FROM producto p, atributo a
    WHERE p.name = 'Razer Blade 15 Advanced' AND a.codigo = 'ram_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '1024', 1024.000, 2 FROM producto p, atributo a
    WHERE p.name = 'Razer Blade 15 Advanced' AND a.codigo = 'almacenamiento_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'SSD NVMe', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'Razer Blade 15 Advanced' AND a.codigo = 'tipo_almacenamiento'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '15.6', 15.600, 4 FROM producto p, atributo a
    WHERE p.name = 'Razer Blade 15 Advanced' AND a.codigo = 'pulgadas'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'NVIDIA RTX 4070', NULL, 5 FROM producto p, atributo a
    WHERE p.name = 'Razer Blade 15 Advanced' AND a.codigo = 'grafica'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '240', 240.000, 6 FROM producto p, atributo a
    WHERE p.name = 'Razer Blade 15 Advanced' AND a.codigo = 'refresco_hz'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Windows 11', NULL, 7 FROM producto p, atributo a
    WHERE p.name = 'Razer Blade 15 Advanced' AND a.codigo = 'sistema_operativo'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '2.0', 2.000, 8 FROM producto p, atributo a
    WHERE p.name = 'Razer Blade 15 Advanced' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '24', 24.000, 9 FROM producto p, atributo a
    WHERE p.name = 'Razer Blade 15 Advanced' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'LG UltraGear 27GP850-B', 'Panel Nano IPS de 165 Hz y 1 ms para juego competitivo.', 1899.00, '/Img/img.png', 16, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'monitores' AND m.name = 'LG'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'LG UltraGear 27GP850-B');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'LG UltraGear 27GP850-B'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '27', 27.000, 0 FROM producto p, atributo a
    WHERE p.name = 'LG UltraGear 27GP850-B' AND a.codigo = 'pulgadas'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '2560x1440', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'LG UltraGear 27GP850-B' AND a.codigo = 'resolucion'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '165', 165.000, 2 FROM producto p, atributo a
    WHERE p.name = 'LG UltraGear 27GP850-B' AND a.codigo = 'refresco_hz'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Nano IPS', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'LG UltraGear 27GP850-B' AND a.codigo = 'panel'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'HDMI 2.0, DisplayPort 1.4', NULL, 4 FROM producto p, atributo a
    WHERE p.name = 'LG UltraGear 27GP850-B' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '24', 24.000, 5 FROM producto p, atributo a
    WHERE p.name = 'LG UltraGear 27GP850-B' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'LG 24MP400-B', 'Monitor IPS de 24 pulgadas para oficina y estudio.', 549.00, '/Img/img.png', 30, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'monitores' AND m.name = 'LG'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'LG 24MP400-B');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'LG 24MP400-B'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '24', 24.000, 0 FROM producto p, atributo a
    WHERE p.name = 'LG 24MP400-B' AND a.codigo = 'pulgadas'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '1920x1080', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'LG 24MP400-B' AND a.codigo = 'resolucion'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '75', 75.000, 2 FROM producto p, atributo a
    WHERE p.name = 'LG 24MP400-B' AND a.codigo = 'refresco_hz'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'IPS', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'LG 24MP400-B' AND a.codigo = 'panel'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'HDMI, VGA', NULL, 4 FROM producto p, atributo a
    WHERE p.name = 'LG 24MP400-B' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '24', 24.000, 5 FROM producto p, atributo a
    WHERE p.name = 'LG 24MP400-B' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Samsung Odyssey G5 LC27G55T', 'Curvatura 1000R que envuelve el campo de visión.', 1249.00, '/Img/img.png', 12, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'monitores' AND m.name = 'Samsung'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Samsung Odyssey G5 LC27G55T');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Samsung Odyssey G5 LC27G55T'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '27', 27.000, 0 FROM producto p, atributo a
    WHERE p.name = 'Samsung Odyssey G5 LC27G55T' AND a.codigo = 'pulgadas'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '2560x1440', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Samsung Odyssey G5 LC27G55T' AND a.codigo = 'resolucion'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '144', 144.000, 2 FROM producto p, atributo a
    WHERE p.name = 'Samsung Odyssey G5 LC27G55T' AND a.codigo = 'refresco_hz'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'VA', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'Samsung Odyssey G5 LC27G55T' AND a.codigo = 'panel'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'HDMI, DisplayPort', NULL, 4 FROM producto p, atributo a
    WHERE p.name = 'Samsung Odyssey G5 LC27G55T' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '24', 24.000, 5 FROM producto p, atributo a
    WHERE p.name = 'Samsung Odyssey G5 LC27G55T' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Samsung ViewFinity S6 S34C650', 'Ultrapanorámico de 34 pulgadas para trabajar con varias ventanas.', 2299.00, '/Img/img.png', 7, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'monitores' AND m.name = 'Samsung'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Samsung ViewFinity S6 S34C650');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Samsung ViewFinity S6 S34C650'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '34', 34.000, 0 FROM producto p, atributo a
    WHERE p.name = 'Samsung ViewFinity S6 S34C650' AND a.codigo = 'pulgadas'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '3440x1440', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Samsung ViewFinity S6 S34C650' AND a.codigo = 'resolucion'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '100', 100.000, 2 FROM producto p, atributo a
    WHERE p.name = 'Samsung ViewFinity S6 S34C650' AND a.codigo = 'refresco_hz'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'VA', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'Samsung ViewFinity S6 S34C650' AND a.codigo = 'panel'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'USB-C, HDMI, DisplayPort', NULL, 4 FROM producto p, atributo a
    WHERE p.name = 'Samsung ViewFinity S6 S34C650' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '24', 24.000, 5 FROM producto p, atributo a
    WHERE p.name = 'Samsung ViewFinity S6 S34C650' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'ASUS ROG Swift PG279QM', 'IPS de 240 Hz con G-SYNC para competitivo exigente.', 3499.00, '/Img/img.png', 5, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'monitores' AND m.name = 'ASUS'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'ASUS ROG Swift PG279QM');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'ASUS ROG Swift PG279QM'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '27', 27.000, 0 FROM producto p, atributo a
    WHERE p.name = 'ASUS ROG Swift PG279QM' AND a.codigo = 'pulgadas'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '2560x1440', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'ASUS ROG Swift PG279QM' AND a.codigo = 'resolucion'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '240', 240.000, 2 FROM producto p, atributo a
    WHERE p.name = 'ASUS ROG Swift PG279QM' AND a.codigo = 'refresco_hz'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'IPS', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'ASUS ROG Swift PG279QM' AND a.codigo = 'panel'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'HDMI 2.0, DisplayPort 1.4', NULL, 4 FROM producto p, atributo a
    WHERE p.name = 'ASUS ROG Swift PG279QM' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '36', 36.000, 5 FROM producto p, atributo a
    WHERE p.name = 'ASUS ROG Swift PG279QM' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Acer Nitro VG240Y', 'IPS de 24 pulgadas y 165 Hz a precio contenido.', 749.00, '/Img/img.png', 26, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'monitores' AND m.name = 'Acer'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Acer Nitro VG240Y');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Acer Nitro VG240Y'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '23.8', 23.800, 0 FROM producto p, atributo a
    WHERE p.name = 'Acer Nitro VG240Y' AND a.codigo = 'pulgadas'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '1920x1080', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Acer Nitro VG240Y' AND a.codigo = 'resolucion'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '165', 165.000, 2 FROM producto p, atributo a
    WHERE p.name = 'Acer Nitro VG240Y' AND a.codigo = 'refresco_hz'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'IPS', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'Acer Nitro VG240Y' AND a.codigo = 'panel'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'HDMI, DisplayPort', NULL, 4 FROM producto p, atributo a
    WHERE p.name = 'Acer Nitro VG240Y' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '24', 24.000, 5 FROM producto p, atributo a
    WHERE p.name = 'Acer Nitro VG240Y' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Dell UltraSharp U2723QE', 'Panel IPS Black 4K con concentrador USB-C de 90 W.', 3199.00, '/Img/img.png', 6, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'monitores' AND m.name = 'Dell'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Dell UltraSharp U2723QE');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Dell UltraSharp U2723QE'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '27', 27.000, 0 FROM producto p, atributo a
    WHERE p.name = 'Dell UltraSharp U2723QE' AND a.codigo = 'pulgadas'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '3840x2160', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Dell UltraSharp U2723QE' AND a.codigo = 'resolucion'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '60', 60.000, 2 FROM producto p, atributo a
    WHERE p.name = 'Dell UltraSharp U2723QE' AND a.codigo = 'refresco_hz'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'IPS Black', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'Dell UltraSharp U2723QE' AND a.codigo = 'panel'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'USB-C 90W, HDMI, DisplayPort, RJ45', NULL, 4 FROM producto p, atributo a
    WHERE p.name = 'Dell UltraSharp U2723QE' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '36', 36.000, 5 FROM producto p, atributo a
    WHERE p.name = 'Dell UltraSharp U2723QE' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'HP M24f FHD', 'Marcos finos por tres lados y ajuste de inclinación.', 629.00, '/Img/img.png', 21, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'monitores' AND m.name = 'HP'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'HP M24f FHD');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'HP M24f FHD'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '23.8', 23.800, 0 FROM producto p, atributo a
    WHERE p.name = 'HP M24f FHD' AND a.codigo = 'pulgadas'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '1920x1080', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'HP M24f FHD' AND a.codigo = 'resolucion'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '75', 75.000, 2 FROM producto p, atributo a
    WHERE p.name = 'HP M24f FHD' AND a.codigo = 'refresco_hz'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'IPS', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'HP M24f FHD' AND a.codigo = 'panel'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'HDMI, VGA', NULL, 4 FROM producto p, atributo a
    WHERE p.name = 'HP M24f FHD' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 5 FROM producto p, atributo a
    WHERE p.name = 'HP M24f FHD' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'AMD Ryzen 5 7600X', 'Seis núcleos Zen 4 para juego y creación sin cuellos de botella.', 1099.00, '/Img/img.png', 19, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'componentes' AND m.name = 'AMD'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'AMD Ryzen 5 7600X');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'AMD Ryzen 5 7600X'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'AMD Ryzen 5 7600X', NULL, 0 FROM producto p, atributo a
    WHERE p.name = 'AMD Ryzen 5 7600X' AND a.codigo = 'procesador'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Socket AM5', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'AMD Ryzen 5 7600X' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '36', 36.000, 2 FROM producto p, atributo a
    WHERE p.name = 'AMD Ryzen 5 7600X' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'AMD Ryzen 7 7800X3D', 'Caché 3D apilada: la referencia para juego a alta tasa de cuadros.', 2299.00, '/Img/img.png', 9, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'componentes' AND m.name = 'AMD'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'AMD Ryzen 7 7800X3D');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'AMD Ryzen 7 7800X3D'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'AMD Ryzen 7 7800X3D', NULL, 0 FROM producto p, atributo a
    WHERE p.name = 'AMD Ryzen 7 7800X3D' AND a.codigo = 'procesador'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Socket AM5', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'AMD Ryzen 7 7800X3D' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '36', 36.000, 2 FROM producto p, atributo a
    WHERE p.name = 'AMD Ryzen 7 7800X3D' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Intel Core i5-13600K', 'Catorce núcleos híbridos con multiplicador desbloqueado.', 1449.00, '/Img/img.png', 13, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'componentes' AND m.name = 'Intel'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Intel Core i5-13600K');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Intel Core i5-13600K'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Intel Core i5-13600K', NULL, 0 FROM producto p, atributo a
    WHERE p.name = 'Intel Core i5-13600K' AND a.codigo = 'procesador'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Socket LGA1700', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Intel Core i5-13600K' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '36', 36.000, 2 FROM producto p, atributo a
    WHERE p.name = 'Intel Core i5-13600K' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Intel Core i7-14700K', 'Veinte núcleos para compilar, renderizar y jugar a la vez.', 2199.00, '/Img/img.png', 7, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'componentes' AND m.name = 'Intel'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Intel Core i7-14700K');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Intel Core i7-14700K'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Intel Core i7-14700K', NULL, 0 FROM producto p, atributo a
    WHERE p.name = 'Intel Core i7-14700K' AND a.codigo = 'procesador'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Socket LGA1700', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Intel Core i7-14700K' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '36', 36.000, 2 FROM producto p, atributo a
    WHERE p.name = 'Intel Core i7-14700K' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'NVIDIA GeForce RTX 4060 Ti 8GB', 'Trazado de rayos y DLSS 3 para 1440p.', 2149.00, '/Img/img.png', 11, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'componentes' AND m.name = 'NVIDIA'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'NVIDIA GeForce RTX 4060 Ti 8GB');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'NVIDIA GeForce RTX 4060 Ti 8GB'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'GeForce RTX 4060 Ti', NULL, 0 FROM producto p, atributo a
    WHERE p.name = 'NVIDIA GeForce RTX 4060 Ti 8GB' AND a.codigo = 'grafica'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '8', 8.000, 1 FROM producto p, atributo a
    WHERE p.name = 'NVIDIA GeForce RTX 4060 Ti 8GB' AND a.codigo = 'ram_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'PCIe 4.0, HDMI 2.1, DisplayPort 1.4', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'NVIDIA GeForce RTX 4060 Ti 8GB' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '36', 36.000, 3 FROM producto p, atributo a
    WHERE p.name = 'NVIDIA GeForce RTX 4060 Ti 8GB' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'NVIDIA GeForce RTX 4070 SUPER 12GB', 'Salto grande en 1440p y entrada honesta a 4K.', 3599.00, '/Img/img.png', 6, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'componentes' AND m.name = 'NVIDIA'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'NVIDIA GeForce RTX 4070 SUPER 12GB');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'NVIDIA GeForce RTX 4070 SUPER 12GB'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'GeForce RTX 4070 SUPER', NULL, 0 FROM producto p, atributo a
    WHERE p.name = 'NVIDIA GeForce RTX 4070 SUPER 12GB' AND a.codigo = 'grafica'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 1 FROM producto p, atributo a
    WHERE p.name = 'NVIDIA GeForce RTX 4070 SUPER 12GB' AND a.codigo = 'ram_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'PCIe 4.0, HDMI 2.1, DisplayPort 1.4', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'NVIDIA GeForce RTX 4070 SUPER 12GB' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '36', 36.000, 3 FROM producto p, atributo a
    WHERE p.name = 'NVIDIA GeForce RTX 4070 SUPER 12GB' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Corsair Vengeance DDR5 32GB 6000MHz', 'Kit de dos módulos con perfil EXPO y disipador bajo.', 749.00, '/Img/img.png', 24, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'componentes' AND m.name = 'Corsair'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Corsair Vengeance DDR5 32GB 6000MHz');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Corsair Vengeance DDR5 32GB 6000MHz'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '32', 32.000, 0 FROM producto p, atributo a
    WHERE p.name = 'Corsair Vengeance DDR5 32GB 6000MHz' AND a.codigo = 'ram_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'DDR5 DIMM', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Corsair Vengeance DDR5 32GB 6000MHz' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '60', 60.000, 2 FROM producto p, atributo a
    WHERE p.name = 'Corsair Vengeance DDR5 32GB 6000MHz' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Kingston FURY Beast DDR4 16GB 3200MHz', 'Kit de dos módulos con XMP y altura reducida.', 289.00, '/Img/img.png', 33, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'componentes' AND m.name = 'Kingston'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Kingston FURY Beast DDR4 16GB 3200MHz');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Kingston FURY Beast DDR4 16GB 3200MHz'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '16', 16.000, 0 FROM producto p, atributo a
    WHERE p.name = 'Kingston FURY Beast DDR4 16GB 3200MHz' AND a.codigo = 'ram_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'DDR4 DIMM', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Kingston FURY Beast DDR4 16GB 3200MHz' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '60', 60.000, 2 FROM producto p, atributo a
    WHERE p.name = 'Kingston FURY Beast DDR4 16GB 3200MHz' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Samsung 990 PRO 1TB NVMe', 'PCIe 4.0 con lecturas de hasta 7450 MB/s.', 649.00, '/Img/img.png', 20, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'almacenamiento' AND m.name = 'Samsung'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Samsung 990 PRO 1TB NVMe');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Samsung 990 PRO 1TB NVMe'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '1024', 1024.000, 0 FROM producto p, atributo a
    WHERE p.name = 'Samsung 990 PRO 1TB NVMe' AND a.codigo = 'almacenamiento_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'SSD NVMe', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Samsung 990 PRO 1TB NVMe' AND a.codigo = 'tipo_almacenamiento'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'M.2 PCIe 4.0', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'Samsung 990 PRO 1TB NVMe' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '60', 60.000, 3 FROM producto p, atributo a
    WHERE p.name = 'Samsung 990 PRO 1TB NVMe' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Samsung 870 EVO 500GB SATA', 'SSD de 2.5 pulgadas para revivir un equipo con disco mecánico.', 279.00, '/Img/img.png', 28, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'almacenamiento' AND m.name = 'Samsung'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Samsung 870 EVO 500GB SATA');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Samsung 870 EVO 500GB SATA'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '512', 512.000, 0 FROM producto p, atributo a
    WHERE p.name = 'Samsung 870 EVO 500GB SATA' AND a.codigo = 'almacenamiento_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'SSD SATA', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Samsung 870 EVO 500GB SATA' AND a.codigo = 'tipo_almacenamiento'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'SATA III', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'Samsung 870 EVO 500GB SATA' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '60', 60.000, 3 FROM producto p, atributo a
    WHERE p.name = 'Samsung 870 EVO 500GB SATA' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'WD Black SN770 1TB', 'NVMe sin DRAM pero con caché dinámica generosa.', 469.00, '/Img/img.png', 17, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'almacenamiento' AND m.name = 'Western Digital'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'WD Black SN770 1TB');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'WD Black SN770 1TB'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '1024', 1024.000, 0 FROM producto p, atributo a
    WHERE p.name = 'WD Black SN770 1TB' AND a.codigo = 'almacenamiento_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'SSD NVMe', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'WD Black SN770 1TB' AND a.codigo = 'tipo_almacenamiento'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'M.2 PCIe 4.0', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'WD Black SN770 1TB' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '60', 60.000, 3 FROM producto p, atributo a
    WHERE p.name = 'WD Black SN770 1TB' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'WD Blue 2TB 7200RPM', 'Disco mecánico para archivo y copias de seguridad.', 299.00, '/Img/img.png', 23, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'almacenamiento' AND m.name = 'Western Digital'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'WD Blue 2TB 7200RPM');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'WD Blue 2TB 7200RPM'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '2048', 2048.000, 0 FROM producto p, atributo a
    WHERE p.name = 'WD Blue 2TB 7200RPM' AND a.codigo = 'almacenamiento_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'HDD', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'WD Blue 2TB 7200RPM' AND a.codigo = 'tipo_almacenamiento'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'SATA III', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'WD Blue 2TB 7200RPM' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '24', 24.000, 3 FROM producto p, atributo a
    WHERE p.name = 'WD Blue 2TB 7200RPM' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Seagate Expansion Portable 2TB', 'Disco externo USB 3.0 que no necesita alimentación aparte.', 349.00, '/Img/img.png', 15, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'almacenamiento' AND m.name = 'Seagate'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Seagate Expansion Portable 2TB');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Seagate Expansion Portable 2TB'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '2048', 2048.000, 0 FROM producto p, atributo a
    WHERE p.name = 'Seagate Expansion Portable 2TB' AND a.codigo = 'almacenamiento_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'HDD externo', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Seagate Expansion Portable 2TB' AND a.codigo = 'tipo_almacenamiento'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'USB 3.0', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'Seagate Expansion Portable 2TB' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '0.17', 0.170, 3 FROM producto p, atributo a
    WHERE p.name = 'Seagate Expansion Portable 2TB' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 4 FROM producto p, atributo a
    WHERE p.name = 'Seagate Expansion Portable 2TB' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Kingston NV2 500GB NVMe', 'Entrada al NVMe con buena relación precio por gigabyte.', 199.00, '/Img/img.png', 31, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'almacenamiento' AND m.name = 'Kingston'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Kingston NV2 500GB NVMe');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Kingston NV2 500GB NVMe'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '512', 512.000, 0 FROM producto p, atributo a
    WHERE p.name = 'Kingston NV2 500GB NVMe' AND a.codigo = 'almacenamiento_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'SSD NVMe', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Kingston NV2 500GB NVMe' AND a.codigo = 'tipo_almacenamiento'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'M.2 PCIe 4.0', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'Kingston NV2 500GB NVMe' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '36', 36.000, 3 FROM producto p, atributo a
    WHERE p.name = 'Kingston NV2 500GB NVMe' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Logitech MX Keys S', 'Teclado bajo para escribir horas con retroiluminación adaptativa.', 549.00, '/Img/img.png', 18, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'teclados' AND m.name = 'Logitech'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Logitech MX Keys S');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Logitech MX Keys S'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Membrana de perfil bajo', NULL, 0 FROM producto p, atributo a
    WHERE p.name = 'Logitech MX Keys S' AND a.codigo = 'tipo_switch'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Sí', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Logitech MX Keys S' AND a.codigo = 'inalambrico'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Bluetooth, receptor Logi Bolt', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'Logitech MX Keys S' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Grafito', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'Logitech MX Keys S' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 4 FROM producto p, atributo a
    WHERE p.name = 'Logitech MX Keys S' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Logitech G Pro X TKL', 'Sin bloque numérico y con switches intercambiables.', 799.00, '/Img/img.png', 10, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'teclados' AND m.name = 'Logitech'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Logitech G Pro X TKL');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Logitech G Pro X TKL'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Mecánico GX Brown', NULL, 0 FROM producto p, atributo a
    WHERE p.name = 'Logitech G Pro X TKL' AND a.codigo = 'tipo_switch'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Sí', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Logitech G Pro X TKL' AND a.codigo = 'inalambrico'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'LIGHTSPEED, Bluetooth, USB-C', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'Logitech G Pro X TKL' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Negro', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'Logitech G Pro X TKL' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '24', 24.000, 4 FROM producto p, atributo a
    WHERE p.name = 'Logitech G Pro X TKL' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Razer Huntsman V2 TKL', 'Switches ópticos con reposamuñecas de espuma con memoria.', 869.00, '/Img/img.png', 8, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'teclados' AND m.name = 'Razer'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Razer Huntsman V2 TKL');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Razer Huntsman V2 TKL'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Óptico lineal', NULL, 0 FROM producto p, atributo a
    WHERE p.name = 'Razer Huntsman V2 TKL' AND a.codigo = 'tipo_switch'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'No', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Razer Huntsman V2 TKL' AND a.codigo = 'inalambrico'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'USB-C desmontable', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'Razer Huntsman V2 TKL' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Negro', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'Razer Huntsman V2 TKL' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '24', 24.000, 4 FROM producto p, atributo a
    WHERE p.name = 'Razer Huntsman V2 TKL' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Corsair K70 RGB PRO', 'Chasis de aluminio cepillado y reposamuñecas magnético.', 899.00, '/Img/img.png', 7, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'teclados' AND m.name = 'Corsair'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Corsair K70 RGB PRO');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Corsair K70 RGB PRO'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Cherry MX Red', NULL, 0 FROM producto p, atributo a
    WHERE p.name = 'Corsair K70 RGB PRO' AND a.codigo = 'tipo_switch'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'No', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Corsair K70 RGB PRO' AND a.codigo = 'inalambrico'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'USB-A', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'Corsair K70 RGB PRO' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Negro', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'Corsair K70 RGB PRO' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '24', 24.000, 4 FROM producto p, atributo a
    WHERE p.name = 'Corsair K70 RGB PRO' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'HyperX Alloy Origins Core', 'Compacto, de aluminio y con switches propios.', 449.00, '/Img/img.png', 20, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'teclados' AND m.name = 'HyperX'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'HyperX Alloy Origins Core');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'HyperX Alloy Origins Core'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'HyperX Red', NULL, 0 FROM producto p, atributo a
    WHERE p.name = 'HyperX Alloy Origins Core' AND a.codigo = 'tipo_switch'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'No', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'HyperX Alloy Origins Core' AND a.codigo = 'inalambrico'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'USB-C desmontable', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'HyperX Alloy Origins Core' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Negro', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'HyperX Alloy Origins Core' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '24', 24.000, 4 FROM producto p, atributo a
    WHERE p.name = 'HyperX Alloy Origins Core' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Redragon Kumara K552', 'Mecánico compacto de entrada, muy vendido por su precio.', 149.00, '/Img/img.png', 40, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'teclados' AND m.name = 'Redragon'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Redragon Kumara K552');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Redragon Kumara K552'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Outemu Blue', NULL, 0 FROM producto p, atributo a
    WHERE p.name = 'Redragon Kumara K552' AND a.codigo = 'tipo_switch'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'No', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Redragon Kumara K552' AND a.codigo = 'inalambrico'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'USB-A', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'Redragon Kumara K552' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Negro', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'Redragon Kumara K552' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 4 FROM producto p, atributo a
    WHERE p.name = 'Redragon Kumara K552' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Logitech MX Master 3S', 'Rueda MagSpeed y clics silenciosos para trabajo de escritorio.', 599.00, '/Img/img.png', 16, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'mouse' AND m.name = 'Logitech'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Logitech MX Master 3S');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Logitech MX Master 3S'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '8000', 8000.000, 0 FROM producto p, atributo a
    WHERE p.name = 'Logitech MX Master 3S' AND a.codigo = 'sensor_dpi'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Sí', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Logitech MX Master 3S' AND a.codigo = 'inalambrico'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Bluetooth, Logi Bolt', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'Logitech MX Master 3S' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Grafito', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'Logitech MX Master 3S' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '0.141', 0.141, 4 FROM producto p, atributo a
    WHERE p.name = 'Logitech MX Master 3S' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 5 FROM producto p, atributo a
    WHERE p.name = 'Logitech MX Master 3S' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Logitech G502 X', 'Switches híbridos y contrapesos ajustables.', 449.00, '/Img/img.png', 22, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'mouse' AND m.name = 'Logitech'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Logitech G502 X');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Logitech G502 X'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '25600', 25600.000, 0 FROM producto p, atributo a
    WHERE p.name = 'Logitech G502 X' AND a.codigo = 'sensor_dpi'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'No', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Logitech G502 X' AND a.codigo = 'inalambrico'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'USB-C', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'Logitech G502 X' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Negro', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'Logitech G502 X' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '0.089', 0.089, 4 FROM producto p, atributo a
    WHERE p.name = 'Logitech G502 X' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '24', 24.000, 5 FROM producto p, atributo a
    WHERE p.name = 'Logitech G502 X' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Razer DeathAdder V3 Pro', 'Ochenta y ocho gramos con forma ergonómica clásica.', 899.00, '/Img/img.png', 9, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'mouse' AND m.name = 'Razer'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Razer DeathAdder V3 Pro');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Razer DeathAdder V3 Pro'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '30000', 30000.000, 0 FROM producto p, atributo a
    WHERE p.name = 'Razer DeathAdder V3 Pro' AND a.codigo = 'sensor_dpi'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Sí', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Razer DeathAdder V3 Pro' AND a.codigo = 'inalambrico'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'HyperSpeed, USB-C', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'Razer DeathAdder V3 Pro' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Negro', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'Razer DeathAdder V3 Pro' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '0.063', 0.063, 4 FROM producto p, atributo a
    WHERE p.name = 'Razer DeathAdder V3 Pro' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '24', 24.000, 5 FROM producto p, atributo a
    WHERE p.name = 'Razer DeathAdder V3 Pro' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Razer Viper V3 HyperSpeed', 'Simétrico y ligero para agarre de garra.', 549.00, '/Img/img.png', 14, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'mouse' AND m.name = 'Razer'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Razer Viper V3 HyperSpeed');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Razer Viper V3 HyperSpeed'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '30000', 30000.000, 0 FROM producto p, atributo a
    WHERE p.name = 'Razer Viper V3 HyperSpeed' AND a.codigo = 'sensor_dpi'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Sí', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Razer Viper V3 HyperSpeed' AND a.codigo = 'inalambrico'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'HyperSpeed 2.4 GHz', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'Razer Viper V3 HyperSpeed' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Negro', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'Razer Viper V3 HyperSpeed' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '0.082', 0.082, 4 FROM producto p, atributo a
    WHERE p.name = 'Razer Viper V3 HyperSpeed' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '24', 24.000, 5 FROM producto p, atributo a
    WHERE p.name = 'Razer Viper V3 HyperSpeed' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Corsair M65 RGB Ultra', 'Chasis de aluminio con botón de francotirador.', 429.00, '/Img/img.png', 13, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'mouse' AND m.name = 'Corsair'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Corsair M65 RGB Ultra');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Corsair M65 RGB Ultra'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '26000', 26000.000, 0 FROM producto p, atributo a
    WHERE p.name = 'Corsair M65 RGB Ultra' AND a.codigo = 'sensor_dpi'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'No', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Corsair M65 RGB Ultra' AND a.codigo = 'inalambrico'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'USB-A', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'Corsair M65 RGB Ultra' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Negro', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'Corsair M65 RGB Ultra' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '0.097', 0.097, 4 FROM producto p, atributo a
    WHERE p.name = 'Corsair M65 RGB Ultra' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '24', 24.000, 5 FROM producto p, atributo a
    WHERE p.name = 'Corsair M65 RGB Ultra' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Redragon Cobra M711', 'Sensor decente y peso equilibrado a precio de entrada.', 119.00, '/Img/img.png', 38, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'mouse' AND m.name = 'Redragon'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Redragon Cobra M711');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Redragon Cobra M711'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '10000', 10000.000, 0 FROM producto p, atributo a
    WHERE p.name = 'Redragon Cobra M711' AND a.codigo = 'sensor_dpi'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'No', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Redragon Cobra M711' AND a.codigo = 'inalambrico'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'USB-A', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'Redragon Cobra M711' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Negro', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'Redragon Cobra M711' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '0.085', 0.085, 4 FROM producto p, atributo a
    WHERE p.name = 'Redragon Cobra M711' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 5 FROM producto p, atributo a
    WHERE p.name = 'Redragon Cobra M711' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Sony WH-1000XM5', 'Referencia en cancelación de ruido para viaje y oficina.', 1699.00, '/Img/img.png', 12, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'audio' AND m.name = 'Sony'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Sony WH-1000XM5');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Sony WH-1000XM5'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Sí', NULL, 0 FROM producto p, atributo a
    WHERE p.name = 'Sony WH-1000XM5' AND a.codigo = 'inalambrico'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Sí', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Sony WH-1000XM5' AND a.codigo = 'cancelacion_ruido'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Bluetooth 5.2, jack 3.5 mm', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'Sony WH-1000XM5' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Negro', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'Sony WH-1000XM5' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '0.25', 0.250, 4 FROM producto p, atributo a
    WHERE p.name = 'Sony WH-1000XM5' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 5 FROM producto p, atributo a
    WHERE p.name = 'Sony WH-1000XM5' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Sony WF-C700N', 'Intraurales compactos con cancelación activa.', 549.00, '/Img/img.png', 19, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'audio' AND m.name = 'Sony'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Sony WF-C700N');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Sony WF-C700N'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Sí', NULL, 0 FROM producto p, atributo a
    WHERE p.name = 'Sony WF-C700N' AND a.codigo = 'inalambrico'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Sí', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Sony WF-C700N' AND a.codigo = 'cancelacion_ruido'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Bluetooth 5.2', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'Sony WF-C700N' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Blanco', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'Sony WF-C700N' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 4 FROM producto p, atributo a
    WHERE p.name = 'Sony WF-C700N' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'HyperX Cloud II', 'Diadema de juego con sonido envolvente por USB.', 449.00, '/Img/img.png', 24, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'audio' AND m.name = 'HyperX'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'HyperX Cloud II');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'HyperX Cloud II'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'No', NULL, 0 FROM producto p, atributo a
    WHERE p.name = 'HyperX Cloud II' AND a.codigo = 'inalambrico'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'No', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'HyperX Cloud II' AND a.codigo = 'cancelacion_ruido'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'USB, jack 3.5 mm', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'HyperX Cloud II' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Rojo', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'HyperX Cloud II' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '0.32', 0.320, 4 FROM producto p, atributo a
    WHERE p.name = 'HyperX Cloud II' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '24', 24.000, 5 FROM producto p, atributo a
    WHERE p.name = 'HyperX Cloud II' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'HyperX Cloud Alpha Wireless', 'Autonomía declarada de 300 horas.', 899.00, '/Img/img.png', 8, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'audio' AND m.name = 'HyperX'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'HyperX Cloud Alpha Wireless');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'HyperX Cloud Alpha Wireless'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Sí', NULL, 0 FROM producto p, atributo a
    WHERE p.name = 'HyperX Cloud Alpha Wireless' AND a.codigo = 'inalambrico'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'No', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'HyperX Cloud Alpha Wireless' AND a.codigo = 'cancelacion_ruido'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '2.4 GHz', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'HyperX Cloud Alpha Wireless' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Negro', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'HyperX Cloud Alpha Wireless' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '0.335', 0.335, 4 FROM producto p, atributo a
    WHERE p.name = 'HyperX Cloud Alpha Wireless' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '24', 24.000, 5 FROM producto p, atributo a
    WHERE p.name = 'HyperX Cloud Alpha Wireless' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Logitech G435 Lightspeed', 'Ligeros, inalámbricos y pensados también para consola.', 329.00, '/Img/img.png', 21, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'audio' AND m.name = 'Logitech'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Logitech G435 Lightspeed');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Logitech G435 Lightspeed'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Sí', NULL, 0 FROM producto p, atributo a
    WHERE p.name = 'Logitech G435 Lightspeed' AND a.codigo = 'inalambrico'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'No', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Logitech G435 Lightspeed' AND a.codigo = 'cancelacion_ruido'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'LIGHTSPEED, Bluetooth', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'Logitech G435 Lightspeed' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Azul', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'Logitech G435 Lightspeed' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '0.165', 0.165, 4 FROM producto p, atributo a
    WHERE p.name = 'Logitech G435 Lightspeed' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '24', 24.000, 5 FROM producto p, atributo a
    WHERE p.name = 'Logitech G435 Lightspeed' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Redragon Zeus X H510', 'Diadema con drivers de 53 mm y micrófono desmontable.', 219.00, '/Img/img.png', 27, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'audio' AND m.name = 'Redragon'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Redragon Zeus X H510');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Redragon Zeus X H510'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'No', NULL, 0 FROM producto p, atributo a
    WHERE p.name = 'Redragon Zeus X H510' AND a.codigo = 'inalambrico'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'No', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Redragon Zeus X H510' AND a.codigo = 'cancelacion_ruido'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'USB, jack 3.5 mm', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'Redragon Zeus X H510' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Negro', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'Redragon Zeus X H510' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '0.38', 0.380, 4 FROM producto p, atributo a
    WHERE p.name = 'Redragon Zeus X H510' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 5 FROM producto p, atributo a
    WHERE p.name = 'Redragon Zeus X H510' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Samsung Galaxy S24', 'Pantalla de 6.2 pulgadas y actualizaciones durante siete años.', 3899.00, '/Img/img.png', 15, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'smartphones' AND m.name = 'Samsung'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Samsung Galaxy S24');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Samsung Galaxy S24'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '6.2', 6.200, 0 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy S24' AND a.codigo = 'pulgadas'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '2340x1080', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy S24' AND a.codigo = 'resolucion'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '120', 120.000, 2 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy S24' AND a.codigo = 'refresco_hz'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '8', 8.000, 3 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy S24' AND a.codigo = 'ram_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '256', 256.000, 4 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy S24' AND a.codigo = 'almacenamiento_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '50', 50.000, 5 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy S24' AND a.codigo = 'camara_mp'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '4000', 4000.000, 6 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy S24' AND a.codigo = 'bateria_mah'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Android 14', NULL, 7 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy S24' AND a.codigo = 'sistema_operativo'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Negro', NULL, 8 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy S24' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 9 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy S24' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Samsung Galaxy A55', 'Gama media con chasis de aluminio y pantalla Super AMOLED.', 1699.00, '/Img/img.png', 26, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'smartphones' AND m.name = 'Samsung'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Samsung Galaxy A55');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Samsung Galaxy A55'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '6.6', 6.600, 0 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy A55' AND a.codigo = 'pulgadas'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '2340x1080', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy A55' AND a.codigo = 'resolucion'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '120', 120.000, 2 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy A55' AND a.codigo = 'refresco_hz'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '8', 8.000, 3 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy A55' AND a.codigo = 'ram_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '256', 256.000, 4 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy A55' AND a.codigo = 'almacenamiento_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '50', 50.000, 5 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy A55' AND a.codigo = 'camara_mp'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '5000', 5000.000, 6 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy A55' AND a.codigo = 'bateria_mah'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Android 14', NULL, 7 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy A55' AND a.codigo = 'sistema_operativo'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Azul', NULL, 8 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy A55' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 9 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy A55' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Xiaomi Redmi Note 13 Pro', 'Cámara de 200 MP en un teléfono de gama media.', 1199.00, '/Img/img.png', 34, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'smartphones' AND m.name = 'Xiaomi'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Xiaomi Redmi Note 13 Pro');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Xiaomi Redmi Note 13 Pro'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '6.67', 6.670, 0 FROM producto p, atributo a
    WHERE p.name = 'Xiaomi Redmi Note 13 Pro' AND a.codigo = 'pulgadas'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '2712x1220', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Xiaomi Redmi Note 13 Pro' AND a.codigo = 'resolucion'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '120', 120.000, 2 FROM producto p, atributo a
    WHERE p.name = 'Xiaomi Redmi Note 13 Pro' AND a.codigo = 'refresco_hz'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '8', 8.000, 3 FROM producto p, atributo a
    WHERE p.name = 'Xiaomi Redmi Note 13 Pro' AND a.codigo = 'ram_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '256', 256.000, 4 FROM producto p, atributo a
    WHERE p.name = 'Xiaomi Redmi Note 13 Pro' AND a.codigo = 'almacenamiento_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '200', 200.000, 5 FROM producto p, atributo a
    WHERE p.name = 'Xiaomi Redmi Note 13 Pro' AND a.codigo = 'camara_mp'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '5100', 5100.000, 6 FROM producto p, atributo a
    WHERE p.name = 'Xiaomi Redmi Note 13 Pro' AND a.codigo = 'bateria_mah'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Android 13', NULL, 7 FROM producto p, atributo a
    WHERE p.name = 'Xiaomi Redmi Note 13 Pro' AND a.codigo = 'sistema_operativo'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Morado', NULL, 8 FROM producto p, atributo a
    WHERE p.name = 'Xiaomi Redmi Note 13 Pro' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 9 FROM producto p, atributo a
    WHERE p.name = 'Xiaomi Redmi Note 13 Pro' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Xiaomi 14', 'Óptica Leica y formato compacto.', 3499.00, '/Img/img.png', 10, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'smartphones' AND m.name = 'Xiaomi'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Xiaomi 14');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Xiaomi 14'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '6.36', 6.360, 0 FROM producto p, atributo a
    WHERE p.name = 'Xiaomi 14' AND a.codigo = 'pulgadas'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '2670x1200', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Xiaomi 14' AND a.codigo = 'resolucion'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '120', 120.000, 2 FROM producto p, atributo a
    WHERE p.name = 'Xiaomi 14' AND a.codigo = 'refresco_hz'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 3 FROM producto p, atributo a
    WHERE p.name = 'Xiaomi 14' AND a.codigo = 'ram_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '512', 512.000, 4 FROM producto p, atributo a
    WHERE p.name = 'Xiaomi 14' AND a.codigo = 'almacenamiento_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '50', 50.000, 5 FROM producto p, atributo a
    WHERE p.name = 'Xiaomi 14' AND a.codigo = 'camara_mp'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '4610', 4610.000, 6 FROM producto p, atributo a
    WHERE p.name = 'Xiaomi 14' AND a.codigo = 'bateria_mah'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Android 14', NULL, 7 FROM producto p, atributo a
    WHERE p.name = 'Xiaomi 14' AND a.codigo = 'sistema_operativo'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Verde', NULL, 8 FROM producto p, atributo a
    WHERE p.name = 'Xiaomi 14' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 9 FROM producto p, atributo a
    WHERE p.name = 'Xiaomi 14' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Apple iPhone 15', 'Puerto USB-C y la isla dinámica en el modelo base.', 4299.00, '/Img/img.png', 13, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'smartphones' AND m.name = 'Apple'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Apple iPhone 15');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Apple iPhone 15'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '6.1', 6.100, 0 FROM producto p, atributo a
    WHERE p.name = 'Apple iPhone 15' AND a.codigo = 'pulgadas'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '2556x1179', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Apple iPhone 15' AND a.codigo = 'resolucion'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '60', 60.000, 2 FROM producto p, atributo a
    WHERE p.name = 'Apple iPhone 15' AND a.codigo = 'refresco_hz'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '128', 128.000, 3 FROM producto p, atributo a
    WHERE p.name = 'Apple iPhone 15' AND a.codigo = 'almacenamiento_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '48', 48.000, 4 FROM producto p, atributo a
    WHERE p.name = 'Apple iPhone 15' AND a.codigo = 'camara_mp'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'iOS 17', NULL, 5 FROM producto p, atributo a
    WHERE p.name = 'Apple iPhone 15' AND a.codigo = 'sistema_operativo'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Negro', NULL, 6 FROM producto p, atributo a
    WHERE p.name = 'Apple iPhone 15' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 7 FROM producto p, atributo a
    WHERE p.name = 'Apple iPhone 15' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Apple iPhone 15 Pro', 'Chasis de titanio y botón de acción configurable.', 5799.00, '/Img/img.png', 6, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'smartphones' AND m.name = 'Apple'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Apple iPhone 15 Pro');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Apple iPhone 15 Pro'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '6.1', 6.100, 0 FROM producto p, atributo a
    WHERE p.name = 'Apple iPhone 15 Pro' AND a.codigo = 'pulgadas'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '2556x1179', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Apple iPhone 15 Pro' AND a.codigo = 'resolucion'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '120', 120.000, 2 FROM producto p, atributo a
    WHERE p.name = 'Apple iPhone 15 Pro' AND a.codigo = 'refresco_hz'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '256', 256.000, 3 FROM producto p, atributo a
    WHERE p.name = 'Apple iPhone 15 Pro' AND a.codigo = 'almacenamiento_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '48', 48.000, 4 FROM producto p, atributo a
    WHERE p.name = 'Apple iPhone 15 Pro' AND a.codigo = 'camara_mp'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'iOS 17', NULL, 5 FROM producto p, atributo a
    WHERE p.name = 'Apple iPhone 15 Pro' AND a.codigo = 'sistema_operativo'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Titanio natural', NULL, 6 FROM producto p, atributo a
    WHERE p.name = 'Apple iPhone 15 Pro' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 7 FROM producto p, atributo a
    WHERE p.name = 'Apple iPhone 15 Pro' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'LG K62 Plus', 'Batería grande y pantalla amplia a precio de entrada.', 549.00, '/Img/img.png', 29, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'smartphones' AND m.name = 'LG'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'LG K62 Plus');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'LG K62 Plus'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '6.6', 6.600, 0 FROM producto p, atributo a
    WHERE p.name = 'LG K62 Plus' AND a.codigo = 'pulgadas'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '1600x720', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'LG K62 Plus' AND a.codigo = 'resolucion'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '60', 60.000, 2 FROM producto p, atributo a
    WHERE p.name = 'LG K62 Plus' AND a.codigo = 'refresco_hz'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '4', 4.000, 3 FROM producto p, atributo a
    WHERE p.name = 'LG K62 Plus' AND a.codigo = 'ram_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '128', 128.000, 4 FROM producto p, atributo a
    WHERE p.name = 'LG K62 Plus' AND a.codigo = 'almacenamiento_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '48', 48.000, 5 FROM producto p, atributo a
    WHERE p.name = 'LG K62 Plus' AND a.codigo = 'camara_mp'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '4000', 4000.000, 6 FROM producto p, atributo a
    WHERE p.name = 'LG K62 Plus' AND a.codigo = 'bateria_mah'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Android 10', NULL, 7 FROM producto p, atributo a
    WHERE p.name = 'LG K62 Plus' AND a.codigo = 'sistema_operativo'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Azul', NULL, 8 FROM producto p, atributo a
    WHERE p.name = 'LG K62 Plus' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 9 FROM producto p, atributo a
    WHERE p.name = 'LG K62 Plus' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Samsung Galaxy A15', 'Autonomía holgada para quien no carga a diario.', 749.00, '/Img/img.png', 41, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'smartphones' AND m.name = 'Samsung'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Samsung Galaxy A15');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Samsung Galaxy A15'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '6.5', 6.500, 0 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy A15' AND a.codigo = 'pulgadas'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '2340x1080', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy A15' AND a.codigo = 'resolucion'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '90', 90.000, 2 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy A15' AND a.codigo = 'refresco_hz'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '4', 4.000, 3 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy A15' AND a.codigo = 'ram_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '128', 128.000, 4 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy A15' AND a.codigo = 'almacenamiento_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '50', 50.000, 5 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy A15' AND a.codigo = 'camara_mp'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '5000', 5000.000, 6 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy A15' AND a.codigo = 'bateria_mah'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Android 14', NULL, 7 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy A15' AND a.codigo = 'sistema_operativo'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Negro', NULL, 8 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy A15' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 9 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy A15' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Apple iPad 10.9 (10.ª gen)', 'Tableta polivalente con USB-C y pantalla Liquid Retina.', 2299.00, '/Img/img.png', 12, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'tablets' AND m.name = 'Apple'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Apple iPad 10.9 (10.ª gen)');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Apple iPad 10.9 (10.ª gen)'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '10.9', 10.900, 0 FROM producto p, atributo a
    WHERE p.name = 'Apple iPad 10.9 (10.ª gen)' AND a.codigo = 'pulgadas'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '2360x1640', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Apple iPad 10.9 (10.ª gen)' AND a.codigo = 'resolucion'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '64', 64.000, 2 FROM producto p, atributo a
    WHERE p.name = 'Apple iPad 10.9 (10.ª gen)' AND a.codigo = 'almacenamiento_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'iPadOS', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'Apple iPad 10.9 (10.ª gen)' AND a.codigo = 'sistema_operativo'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Plata', NULL, 4 FROM producto p, atributo a
    WHERE p.name = 'Apple iPad 10.9 (10.ª gen)' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '0.477', 0.477, 5 FROM producto p, atributo a
    WHERE p.name = 'Apple iPad 10.9 (10.ª gen)' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 6 FROM producto p, atributo a
    WHERE p.name = 'Apple iPad 10.9 (10.ª gen)' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Samsung Galaxy Tab S9 FE', 'Incluye S Pen y resistencia al agua IP68.', 1899.00, '/Img/img.png', 9, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'tablets' AND m.name = 'Samsung'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Samsung Galaxy Tab S9 FE');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Samsung Galaxy Tab S9 FE'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '10.9', 10.900, 0 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy Tab S9 FE' AND a.codigo = 'pulgadas'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '2304x1440', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy Tab S9 FE' AND a.codigo = 'resolucion'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '90', 90.000, 2 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy Tab S9 FE' AND a.codigo = 'refresco_hz'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '6', 6.000, 3 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy Tab S9 FE' AND a.codigo = 'ram_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '128', 128.000, 4 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy Tab S9 FE' AND a.codigo = 'almacenamiento_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Android 13', NULL, 5 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy Tab S9 FE' AND a.codigo = 'sistema_operativo'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Gris', NULL, 6 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy Tab S9 FE' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '0.523', 0.523, 7 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy Tab S9 FE' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 8 FROM producto p, atributo a
    WHERE p.name = 'Samsung Galaxy Tab S9 FE' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Lenovo Tab M10 Plus', 'Tableta familiar para vídeo y lectura.', 899.00, '/Img/img.png', 17, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'tablets' AND m.name = 'Lenovo'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Lenovo Tab M10 Plus');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Lenovo Tab M10 Plus'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '10.6', 10.600, 0 FROM producto p, atributo a
    WHERE p.name = 'Lenovo Tab M10 Plus' AND a.codigo = 'pulgadas'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '2000x1200', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Lenovo Tab M10 Plus' AND a.codigo = 'resolucion'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '4', 4.000, 2 FROM producto p, atributo a
    WHERE p.name = 'Lenovo Tab M10 Plus' AND a.codigo = 'ram_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '128', 128.000, 3 FROM producto p, atributo a
    WHERE p.name = 'Lenovo Tab M10 Plus' AND a.codigo = 'almacenamiento_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Android 13', NULL, 4 FROM producto p, atributo a
    WHERE p.name = 'Lenovo Tab M10 Plus' AND a.codigo = 'sistema_operativo'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Gris', NULL, 5 FROM producto p, atributo a
    WHERE p.name = 'Lenovo Tab M10 Plus' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '0.465', 0.465, 6 FROM producto p, atributo a
    WHERE p.name = 'Lenovo Tab M10 Plus' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 7 FROM producto p, atributo a
    WHERE p.name = 'Lenovo Tab M10 Plus' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Xiaomi Redmi Pad SE', 'Pantalla de 90 Hz y altavoces cuádruples.', 699.00, '/Img/img.png', 23, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'tablets' AND m.name = 'Xiaomi'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Xiaomi Redmi Pad SE');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Xiaomi Redmi Pad SE'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '11', 11.000, 0 FROM producto p, atributo a
    WHERE p.name = 'Xiaomi Redmi Pad SE' AND a.codigo = 'pulgadas'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '1920x1200', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Xiaomi Redmi Pad SE' AND a.codigo = 'resolucion'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '90', 90.000, 2 FROM producto p, atributo a
    WHERE p.name = 'Xiaomi Redmi Pad SE' AND a.codigo = 'refresco_hz'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '6', 6.000, 3 FROM producto p, atributo a
    WHERE p.name = 'Xiaomi Redmi Pad SE' AND a.codigo = 'ram_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '128', 128.000, 4 FROM producto p, atributo a
    WHERE p.name = 'Xiaomi Redmi Pad SE' AND a.codigo = 'almacenamiento_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Android 13', NULL, 5 FROM producto p, atributo a
    WHERE p.name = 'Xiaomi Redmi Pad SE' AND a.codigo = 'sistema_operativo'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Verde', NULL, 6 FROM producto p, atributo a
    WHERE p.name = 'Xiaomi Redmi Pad SE' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '0.478', 0.478, 7 FROM producto p, atributo a
    WHERE p.name = 'Xiaomi Redmi Pad SE' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 8 FROM producto p, atributo a
    WHERE p.name = 'Xiaomi Redmi Pad SE' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Sony PlayStation 5 Slim', 'Modelo revisado, más compacto y con lector de discos.', 2599.00, '/Img/img.png', 11, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'consolas' AND m.name = 'Sony'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Sony PlayStation 5 Slim');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Sony PlayStation 5 Slim'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '1024', 1024.000, 0 FROM producto p, atributo a
    WHERE p.name = 'Sony PlayStation 5 Slim' AND a.codigo = 'almacenamiento_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'SSD NVMe', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Sony PlayStation 5 Slim' AND a.codigo = 'tipo_almacenamiento'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'HDMI 2.1, Wi-Fi 6', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'Sony PlayStation 5 Slim' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Blanco', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'Sony PlayStation 5 Slim' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '3.2', 3.200, 4 FROM producto p, atributo a
    WHERE p.name = 'Sony PlayStation 5 Slim' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 5 FROM producto p, atributo a
    WHERE p.name = 'Sony PlayStation 5 Slim' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Sony DualSense Edge', 'Mando profesional con palancas y gatillos reemplazables.', 899.00, '/Img/img.png', 8, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'consolas' AND m.name = 'Sony'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Sony DualSense Edge');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Sony DualSense Edge'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Sí', NULL, 0 FROM producto p, atributo a
    WHERE p.name = 'Sony DualSense Edge' AND a.codigo = 'inalambrico'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Bluetooth, USB-C', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Sony DualSense Edge' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Blanco', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'Sony DualSense Edge' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '0.325', 0.325, 3 FROM producto p, atributo a
    WHERE p.name = 'Sony DualSense Edge' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 4 FROM producto p, atributo a
    WHERE p.name = 'Sony DualSense Edge' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Xbox Series S 1TB', 'Consola digital compacta orientada a 1440p.', 1699.00, '/Img/img.png', 14, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'consolas' AND m.name = 'Microsoft'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Xbox Series S 1TB');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Xbox Series S 1TB'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '1024', 1024.000, 0 FROM producto p, atributo a
    WHERE p.name = 'Xbox Series S 1TB' AND a.codigo = 'almacenamiento_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'SSD NVMe', NULL, 1 FROM producto p, atributo a
    WHERE p.name = 'Xbox Series S 1TB' AND a.codigo = 'tipo_almacenamiento'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'HDMI 2.1, Wi-Fi 5', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'Xbox Series S 1TB' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Negro', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'Xbox Series S 1TB' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '1.93', 1.930, 4 FROM producto p, atributo a
    WHERE p.name = 'Xbox Series S 1TB' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 5 FROM producto p, atributo a
    WHERE p.name = 'Xbox Series S 1TB' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Nintendo Switch OLED', 'Pantalla OLED de 7 pulgadas y soporte ajustable.', 1899.00, '/Img/img.png', 16, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'consolas' AND m.name = 'Nintendo'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Nintendo Switch OLED');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Nintendo Switch OLED'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '7', 7.000, 0 FROM producto p, atributo a
    WHERE p.name = 'Nintendo Switch OLED' AND a.codigo = 'pulgadas'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '64', 64.000, 1 FROM producto p, atributo a
    WHERE p.name = 'Nintendo Switch OLED' AND a.codigo = 'almacenamiento_gb'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Wi-Fi, Bluetooth, USB-C', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'Nintendo Switch OLED' AND a.codigo = 'conectividad'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Blanco', NULL, 3 FROM producto p, atributo a
    WHERE p.name = 'Nintendo Switch OLED' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '0.42', 0.420, 4 FROM producto p, atributo a
    WHERE p.name = 'Nintendo Switch OLED' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '12', 12.000, 5 FROM producto p, atributo a
    WHERE p.name = 'Nintendo Switch OLED' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Cougar Armor One', 'Respaldo reclinable hasta 180 grados y cojines incluidos.', 899.00, '/Img/img.png', 13, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'sillas-gamer' AND m.name = 'Cougar'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Cougar Armor One');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Cougar Armor One'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Cuero sintético', NULL, 0 FROM producto p, atributo a
    WHERE p.name = 'Cougar Armor One' AND a.codigo = 'material'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '120', 120.000, 1 FROM producto p, atributo a
    WHERE p.name = 'Cougar Armor One' AND a.codigo = 'capacidad_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Negro y rojo', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'Cougar Armor One' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '22', 22.000, 3 FROM producto p, atributo a
    WHERE p.name = 'Cougar Armor One' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '24', 24.000, 4 FROM producto p, atributo a
    WHERE p.name = 'Cougar Armor One' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Cougar Explore S', 'Malla transpirable para climas cálidos.', 1149.00, '/Img/img.png', 7, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'sillas-gamer' AND m.name = 'Cougar'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Cougar Explore S');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Cougar Explore S'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Malla', NULL, 0 FROM producto p, atributo a
    WHERE p.name = 'Cougar Explore S' AND a.codigo = 'material'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '120', 120.000, 1 FROM producto p, atributo a
    WHERE p.name = 'Cougar Explore S' AND a.codigo = 'capacidad_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Negro', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'Cougar Explore S' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '20', 20.000, 3 FROM producto p, atributo a
    WHERE p.name = 'Cougar Explore S' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '24', 24.000, 4 FROM producto p, atributo a
    WHERE p.name = 'Cougar Explore S' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Secretlab TITAN Evo 2022', 'Soporte lumbar integrado de cuatro direcciones.', 2799.00, '/Img/img.png', 5, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'sillas-gamer' AND m.name = 'Secretlab'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Secretlab TITAN Evo 2022');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Secretlab TITAN Evo 2022'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'NEO Hybrid Leatherette', NULL, 0 FROM producto p, atributo a
    WHERE p.name = 'Secretlab TITAN Evo 2022' AND a.codigo = 'material'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '130', 130.000, 1 FROM producto p, atributo a
    WHERE p.name = 'Secretlab TITAN Evo 2022' AND a.codigo = 'capacidad_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Negro', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'Secretlab TITAN Evo 2022' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '28', 28.000, 3 FROM producto p, atributo a
    WHERE p.name = 'Secretlab TITAN Evo 2022' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '60', 60.000, 4 FROM producto p, atributo a
    WHERE p.name = 'Secretlab TITAN Evo 2022' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;

INSERT INTO producto (name, description, precio, image_url, stock, categoria_id, marca_id)
SELECT 'Secretlab Classic', 'La base de la gama, con la misma estructura metálica.', 1999.00, '/Img/img.png', 6, c.id, m.id
    FROM categoria c, marca m WHERE c.slug = 'sillas-gamer' AND m.name = 'Secretlab'
      AND NOT EXISTS (SELECT 1 FROM producto WHERE name = 'Secretlab Classic');
INSERT INTO producto_imagen (producto_id, url, posicion)
    SELECT id, '/Img/img.png', 0 FROM producto WHERE name = 'Secretlab Classic'
    ON CONFLICT (producto_id, posicion) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Cuero PRIME 2.0', NULL, 0 FROM producto p, atributo a
    WHERE p.name = 'Secretlab Classic' AND a.codigo = 'material'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '110', 110.000, 1 FROM producto p, atributo a
    WHERE p.name = 'Secretlab Classic' AND a.codigo = 'capacidad_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, 'Negro', NULL, 2 FROM producto p, atributo a
    WHERE p.name = 'Secretlab Classic' AND a.codigo = 'color'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '26', 26.000, 3 FROM producto p, atributo a
    WHERE p.name = 'Secretlab Classic' AND a.codigo = 'peso_kg'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;
INSERT INTO producto_atributo (producto_id, atributo_id, valor, valor_numero, posicion)
    SELECT p.id, a.id, '36', 36.000, 4 FROM producto p, atributo a
    WHERE p.name = 'Secretlab Classic' AND a.codigo = 'garantia_meses'
    ON CONFLICT (producto_id, atributo_id) DO NOTHING;


-- ═══════════ `specifications` como valor DERIVADO ═══════════
--
-- La columna sigue existiendo por compatibilidad con el frontend y la app movil
-- (ver V19). Se compone aqui a partir de los atributos, que son el dato de
-- verdad, con el mismo formato Markdown que consumen esas pantallas.

UPDATE producto p SET specifications = s.texto
FROM (
    SELECT pa.producto_id,
           string_agg(
               '· **' || a.nombre || '**: ' || pa.valor
                       || coalesce(' ' || a.unidad, ''),
               chr(10) ORDER BY pa.posicion) AS texto
      FROM producto_atributo pa
      JOIN atributo a ON a.id = pa.atributo_id
     GROUP BY pa.producto_id
) s
WHERE s.producto_id = p.id;
