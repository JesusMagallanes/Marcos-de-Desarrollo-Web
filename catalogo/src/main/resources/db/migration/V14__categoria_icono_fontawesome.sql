-- La categoría pasa de llevar un ícono como imagen (url_image) a llevar el
-- nombre de un ícono de FontAwesome (icono), igual que las guías (V10). El dato
-- viejo (URLs de imagen o data URLs) no sirve como nombre de ícono, así que la
-- columna se vacía antes de estrecharla; el panel vuelve a pedirlo con el
-- selector de FontAwesome.

ALTER TABLE catalogo.categoria RENAME COLUMN url_image TO icono;
UPDATE catalogo.categoria SET icono = NULL;
ALTER TABLE catalogo.categoria ALTER COLUMN icono TYPE VARCHAR(60);
