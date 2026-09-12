-- Cuando se dio de alta un producto. Nada mas.
--
-- El catalogo nunca ha guardado esa fecha, y V15 lo dice a las claras: «la cola
-- se ordena por id y no por fecha porque `producto` no tiene columna de fecha».
-- Sin ella no se puede responder si un producto es nuevo, y sin eso no hay
-- forma de darle una oportunidad de aparecer: el que nadie ha visto no genera
-- eventos, sin eventos no entra en ninguna relacion, y sin relaciones no lo
-- recomienda nadie. Es un ciclo cerrado del que un producto no sale solo.

ALTER TABLE producto ADD COLUMN creado_en TIMESTAMPTZ;

/*
 * NULLABLE, Y LOS EXISTENTES SE QUEDAN EN NULL. A PROPOSITO.
 *
 * Lo comodo seria rellenar los sesenta y ocho productos que ya hay con algo
 * plausible derivado del `id` —es monotono, asi que daria un orden correcto— y
 * es justo lo que no se va a hacer.
 *
 * Un `id` alto solo significa que la fila se inserto despues, no que el producto
 * sea reciente: la semilla V20 metio sesenta y ocho de golpe en un segundo, y
 * una migracion futura podria reinsertar cualquier cosa. Usarlo como edad seria
 * fabricar un dato que nadie midio y del que despues se tomarian decisiones
 * comerciales — que producto se promociona, cual recibe exposicion. Un numero
 * inventado que parece un hecho es peor que un hueco declarado.
 *
 * Por eso NULL significa exactamente «no se sabe cuando se creo», y el sistema
 * lo trata como NO NUEVO. Es la lectura conservadora: un producto viejo que se
 * pierde una oportunidad de exposicion no le hace dano a nadie; uno que la
 * recibe por una fecha falsa desplaza a otro que si la merecia.
 *
 * A partir de aqui, cada alta escribe su fecha real. Ver el `@PrePersist` de
 * `Producto`, que es el idioma que ya usan otras seis entidades del proyecto.
 */
COMMENT ON COLUMN producto.creado_en IS
    'Alta real del producto. NULL = edad desconocida (anterior a V24), NO nuevo.';

/*
 * Indice parcial: solo las filas que tienen fecha.
 *
 * La consulta de catalogo nuevo pregunta siempre «creado_en >= X», y las filas
 * con NULL no pueden satisfacerla jamas. Excluirlas del indice lo deja
 * pequenisimo hoy —cero filas— y lo mantiene proporcional a los productos que
 * de verdad se den de alta, en vez de al tamano del catalogo.
 */
CREATE INDEX idx_producto_creado ON producto (creado_en DESC)
 WHERE creado_en IS NOT NULL;
