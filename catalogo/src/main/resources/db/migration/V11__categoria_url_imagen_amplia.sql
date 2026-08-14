-- El ícono de categoría se elige en el panel admin desde una lista de
-- predeterminados o se sube como archivo y viaja incrustado como data:image
-- (base64). Un PNG de 128px ya no cabe en el VARCHAR(1000) original, así que la
-- columna crece hasta 200.000 caracteres.
ALTER TABLE catalogo.categoria ALTER COLUMN url_image TYPE VARCHAR(200000);
