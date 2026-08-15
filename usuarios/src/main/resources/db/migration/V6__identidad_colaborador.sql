-- Verificación de identidad del colaborador.
--
-- Hasta ahora bastaba con escribir un número de documento: nadie comprobaba que
-- quien lo escribía fuera su titular. Aquí se añaden los datos que el
-- administrador necesita para decidir de verdad, y los adjuntos con los que se
-- comprueba.
--
-- Dos tipos de solicitante, porque el trámite no es el mismo:
--   NATURAL   una persona que quiere vender sus cosas  → DNI o carné de extranjería
--   JURIDICA  una empresa con RUC                      → RUC + representante legal
--
-- Ver docs/contrato-colaboradores.md.

-- ── 1 · Quién es el titular ────────────────────────────────────────────────

-- Las columnas entran como NULL, se rellenan y solo entonces pasan a NOT NULL.
-- Es el orden obligatorio cuando la tabla ya tiene filas: un ADD COLUMN NOT NULL
-- sin DEFAULT sobre una tabla con datos falla en seco.
ALTER TABLE solicitud_colaborador
    ADD COLUMN tipo_persona        VARCHAR(8),
    ADD COLUMN tipo_documento      VARCHAR(3),
    -- Nombre completo del titular, o razón social si es empresa. Se compara a
    -- ojo contra el documento adjunto: es lo que hace útil la revisión.
    ADD COLUMN nombre_titular      VARCHAR(160),
    -- Solo empresas. Quien firma por ella.
    ADD COLUMN representante_legal VARCHAR(160),
    -- Solo personas. Se pide para exigir mayoría de edad.
    ADD COLUMN fecha_nacimiento    DATE;

-- ── 2 · Dónde está ─────────────────────────────────────────────────────────

ALTER TABLE solicitud_colaborador
    ADD COLUMN referencia    VARCHAR(200),
    ADD COLUMN distrito      VARCHAR(80),
    ADD COLUMN provincia     VARCHAR(80),
    ADD COLUMN departamento  VARCHAR(80),
    ADD COLUMN codigo_postal VARCHAR(10),
    -- ISO 3166-1 alfa-2. Hoy siempre PE, pero dejarlo fijado a Perú obligaría a
    -- otra migración el día que se venda desde fuera.
    --
    -- VARCHAR y no CHAR(2) aunque midan siempre dos letras: la entidad mapea un
    -- String a varchar y con CHAR la validación de esquema de Hibernate impide
    -- arrancar. El CHECK de más abajo ya obliga al formato.
    ADD COLUMN pais          VARCHAR(2);

-- ── 3 · Qué aceptó y cuándo ────────────────────────────────────────────────

-- Sin la versión, "aceptó los términos" no dice nada: los términos cambian y
-- después nadie sabe cuáles firmó. Con versión y fecha, la traza se sostiene.
ALTER TABLE solicitud_colaborador
    ADD COLUMN terminos_version      VARCHAR(20),
    ADD COLUMN terminos_aceptados_en TIMESTAMPTZ;

-- ── 4 · Relleno de las filas que ya existían ───────────────────────────────

-- Son solicitudes anteriores a la verificación de identidad: se creyeron sin
-- comprobar nada. Se marcan con 'SIN VERIFICAR' en vez de inventarles datos,
-- para que quien las mire sepa que no pasaron por este control.
--
-- No se tocan sus estados. Una migración que rechaza solicitudes de gente real
-- decide por el administrador y encima sin motivo que darle al solicitante. El
-- agujero que dejan (una pendiente sin adjuntos que se podría aprobar) lo cierra
-- `aprobar()`, que exige los documentos antes de conceder el rol: así vale para
-- estas filas y para cualquier otra que llegue por donde no se espera.
UPDATE solicitud_colaborador SET
    tipo_persona          = CASE WHEN length(documento) = 11 THEN 'JURIDICA' ELSE 'NATURAL' END,
    tipo_documento        = CASE WHEN length(documento) = 11 THEN 'RUC'      ELSE 'DNI'     END,
    nombre_titular        = 'SIN VERIFICAR',
    representante_legal   = CASE WHEN length(documento) = 11 THEN 'SIN VERIFICAR' END,
    referencia            = NULL,
    distrito              = 'SIN VERIFICAR',
    provincia             = 'SIN VERIFICAR',
    departamento          = 'SIN VERIFICAR',
    codigo_postal         = '00000',
    pais                  = 'PE',
    terminos_version      = 'previa',
    terminos_aceptados_en = creada_en
WHERE tipo_persona IS NULL;

ALTER TABLE solicitud_colaborador
    ALTER COLUMN tipo_persona          SET NOT NULL,
    ALTER COLUMN tipo_documento        SET NOT NULL,
    ALTER COLUMN nombre_titular        SET NOT NULL,
    ALTER COLUMN distrito              SET NOT NULL,
    ALTER COLUMN provincia             SET NOT NULL,
    ALTER COLUMN departamento          SET NOT NULL,
    ALTER COLUMN codigo_postal         SET NOT NULL,
    ALTER COLUMN pais                  SET NOT NULL,
    ALTER COLUMN terminos_version      SET NOT NULL,
    ALTER COLUMN terminos_aceptados_en SET NOT NULL;

-- Un carné de extranjería puede llegar a 12 caracteres y la columna se quedó en
-- 11 cuando solo se contemplaban DNI y RUC.
ALTER TABLE solicitud_colaborador ALTER COLUMN documento TYPE VARCHAR(12);

-- ── 5 · Reglas que la base hace cumplir ────────────────────────────────────

ALTER TABLE solicitud_colaborador
    ADD CONSTRAINT ck_solicitud_tipo_persona
        CHECK (tipo_persona IN ('NATURAL', 'JURIDICA')),

    ADD CONSTRAINT ck_solicitud_tipo_documento
        CHECK (tipo_documento IN ('DNI', 'CE', 'RUC')),

    -- El tipo de documento no es libre: depende de quién solicita. Una persona
    -- no se identifica con un RUC ni una empresa con un DNI. Se comprueba aquí
    -- y no solo en Java porque es la regla de la que cuelgan las demás.
    ADD CONSTRAINT ck_solicitud_persona_documento CHECK (
        (tipo_persona = 'NATURAL'  AND tipo_documento IN ('DNI', 'CE'))
        OR (tipo_persona = 'JURIDICA' AND tipo_documento = 'RUC')
    ),

    -- Cada tipo trae su campo propio y no el del otro: el representante legal
    -- solo tiene sentido en una empresa, y la fecha de nacimiento en una
    -- persona. Exigir el NULL evita filas medio rellenas de las dos formas.
    ADD CONSTRAINT ck_solicitud_representante CHECK (
        (tipo_persona = 'JURIDICA' AND representante_legal IS NOT NULL)
        OR (tipo_persona = 'NATURAL'  AND representante_legal IS NULL)
    ),
    ADD CONSTRAINT ck_solicitud_nacimiento CHECK (
        (tipo_persona = 'NATURAL'  AND fecha_nacimiento IS NOT NULL)
        OR (tipo_persona = 'JURIDICA' AND fecha_nacimiento IS NULL)
    ),

    -- Menor de edad no puede obligarse por contrato. El margen se calcula desde
    -- la fecha de nacimiento, no desde una edad guardada, que envejecería mal.
    ADD CONSTRAINT ck_solicitud_mayoria_edad CHECK (
        fecha_nacimiento IS NULL
        OR fecha_nacimiento <= CURRENT_DATE - INTERVAL '18 years'
    ),

    ADD CONSTRAINT ck_solicitud_pais CHECK (pais ~ '^[A-Z]{2}$');

-- ── 6 · Los adjuntos ───────────────────────────────────────────────────────

-- El fichero NO se guarda aquí. En la base va solo la ficha; los bytes viven en
-- disco, en un volumen que no sirve la web. Meter fotos de DNI en columnas
-- bytea hincha la base, encarece cada copia de seguridad y las arrastra a
-- cualquier volcado que alguien haga para depurar.
CREATE TABLE documento_identidad (
    id              BIGSERIAL PRIMARY KEY,

    -- El dueño. Es quien puede descargarlo, junto al administrador.
    usuario_id      BIGINT       NOT NULL,

    -- Nulo mientras el fichero está subido pero aún no enviado con ninguna
    -- solicitud: se sube según se elige en el formulario, antes de darle a
    -- enviar. Los que se quedan sin solicitud los limpia la purga.
    solicitud_id    BIGINT,

    tipo            VARCHAR(20)  NOT NULL,

    -- Nombre con el que llegó. Se guarda solo para enseñárselo al usuario:
    -- NUNCA se usa para construir la ruta en disco.
    nombre_original VARCHAR(255) NOT NULL,
    -- El detectado leyendo los primeros bytes, no el que declaró el cliente.
    tipo_mime       VARCHAR(50)  NOT NULL,
    tamano_bytes    BIGINT       NOT NULL,
    -- Permite detectar que dos cuentas subieron exactamente el mismo fichero,
    -- que es señal de suplantación.
    -- VARCHAR y no CHAR(64) aunque el hash mida siempre lo mismo: la entidad
    -- mapea un String a varchar, y con CHAR la validación de esquema de
    -- Hibernate rechaza el arranque entero. No merece un tipo exótico en Java
    -- para ganar nada: el CHECK de abajo ya obliga a los 64 caracteres.
    sha256          VARCHAR(64)  NOT NULL,

    -- Ruta relativa dentro del volumen. Es un identificador generado, sin
    -- relación con el nombre que subió el usuario.
    ruta            VARCHAR(255) NOT NULL UNIQUE,

    subido_en       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Cuándo se borraron los bytes. La ficha se conserva para poder demostrar
    -- que la verificación se hizo, aunque la imagen ya no esté.
    purgado_en      TIMESTAMPTZ,

    CONSTRAINT fk_documento_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT fk_documento_solicitud FOREIGN KEY (solicitud_id)
        REFERENCES solicitud_colaborador (id) ON DELETE CASCADE,

    CONSTRAINT ck_documento_tipo CHECK (
        tipo IN ('DOCUMENTO_ANVERSO', 'DOCUMENTO_REVERSO', 'FICHA_RUC')
    ),

    -- Tope de 5 MB. También lo aplica la subida, pero un límite que solo vive en
    -- la aplicación se salta cualquier cosa que escriba en la base sin pasar
    -- por ella.
    CONSTRAINT ck_documento_tamano CHECK (tamano_bytes > 0 AND tamano_bytes <= 5242880),

    CONSTRAINT ck_documento_mime CHECK (
        tipo_mime IN ('image/jpeg', 'image/png', 'application/pdf')
    ),

    CONSTRAINT ck_documento_sha CHECK (sha256 ~ '^[a-f0-9]{64}$')
);

-- Un adjunto de cada tipo por solicitud.
CREATE UNIQUE INDEX uk_documento_tipo_por_solicitud
    ON documento_identidad (solicitud_id, tipo)
    WHERE solicitud_id IS NOT NULL;

-- Y uno de cada tipo por usuario mientras no esté enviado.
--
-- Esto es lo que acota el disco. Sin ello nada impedía que una cuenta dejase
-- archivos sueltos sin fin: con el cupo general de 300 peticiones por minuto y
-- 5 MB cada una son 1,5 GB por minuto, y los huérfanos no se purgan hasta pasada
-- una semana. El servicio ya sustituye el anterior al subir uno nuevo, pero eso
-- es una comprobación previa: dos peticiones simultáneas la pasarían las dos.
-- Quien de verdad lo impide es este índice.
CREATE UNIQUE INDEX uk_documento_suelto_por_usuario
    ON documento_identidad (usuario_id, tipo)
    WHERE solicitud_id IS NULL;

-- La bandeja abre una solicitud y pide sus adjuntos.
CREATE INDEX idx_documento_solicitud ON documento_identidad (solicitud_id);

-- La purga busca los huérfanos y los ya resueltos por fecha.
CREATE INDEX idx_documento_purga ON documento_identidad (subido_en)
    WHERE purgado_en IS NULL;
