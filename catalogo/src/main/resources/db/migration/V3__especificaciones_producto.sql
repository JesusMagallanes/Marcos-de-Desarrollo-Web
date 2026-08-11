-- Especificaciones del producto: lista formateada en Markdown, una línea por
-- especificación con el formato `· **Característica**: Valor`.
--
-- Se guarda en una sección independiente de la descripción (que ahora es solo
-- el párrafo de presentación). Nula cuando el producto aún no las tiene.

ALTER TABLE producto ADD COLUMN specifications TEXT;
