// @ts-check
const eslint = require('@eslint/js');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');

/**
 * Reglas de lint del frontend.
 *
 * Lo que se busca aquí NO es imponer estilo —de eso ya se encarga Prettier— sino
 * cazar las cosas que compilan y fallan en tiempo de ejecución: una promesa sin
 * esperar, un `any` que se cuela y desactiva el tipado de ahí para abajo, un
 * `@Input` mal declarado. Por eso las reglas de formato quedan fuera y las de
 * corrección entran.
 *
 * Los prefijos `app-` y `app` son los que ya usa todo el proyecto; la regla está
 * para que un componente nuevo no llegue con otro sin que nadie lo note.
 */
module.exports = tseslint.config(
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      ...tseslint.configs.recommended,
      ...angular.configs.tsRecommended,
    ],
    processor: angular.processInlineTemplates,
    rules: {
      '@angular-eslint/directive-selector': [
        'error',
        { type: 'attribute', prefix: 'app', style: 'camelCase' },
      ],
      '@angular-eslint/component-selector': [
        'error',
        { type: 'element', prefix: 'app', style: 'kebab-case' },
      ],

      /*
       * `any` como aviso y no como error: hay sitios donde es legítimo —el
       * casting de un evento del DOM en una plantilla— y convertirlo en error
       * obligaría a silenciarlo con comentarios, que es peor que verlo.
       */
      '@typescript-eslint/no-explicit-any': 'warn',

      // Un argumento sin usar suele ser una firma que cambió y se quedó a
      // medias. Los que empiezan por `_` son deliberados.
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
    },
  },
  {
    files: ['**/*.html'],
    extends: [...angular.configs.templateRecommended, ...angular.configs.templateAccessibility],
    rules: {
      /*
       * Accesibilidad: las reglas de `templateAccessibility` cazan lo que un
       * lector de pantalla necesita y la vista no echa de menos —una imagen sin
       * `alt`, un `click` en un elemento que no se puede enfocar con el
       * teclado—. Entran como aviso para no bloquear el build de golpe con las
       * pantallas que ya existen.
       */
      '@angular-eslint/template/click-events-have-key-events': 'warn',
      '@angular-eslint/template/interactive-supports-focus': 'warn',

      /*
       * `x != null` es el idioma correcto para "ni null ni undefined", y las
       * plantillas lo usan justo donde hace falta: `latitud` es
       * `number | null | undefined`, así que un `!== null` daría por buena una
       * coordenada indefinida y pintaría un mapa vacío. La regla se queda para
       * cazar comparaciones flojas de verdad, con esta excepción.
       */
      '@angular-eslint/template/eqeqeq': ['error', { allowNullOrUndefined: true }],

      /*
       * Estas dos SÍ en error, porque ya están cerradas y lo que se quiere es
       * que no vuelvan.
       *
       * Eran 46 etiquetas de formulario sin asociar y 7 botones sin nombre
       * accesible. Las etiquetas se apuntaron a su control con `for`/`id`;
       * media docena que en realidad encabezaban un grupo —los pasos de una
       * guía, los permisos de un rol— dejaron de ser `<label>`, que es lo
       * correcto: una etiqueta apunta a UN control, y esas prometían un campo
       * que no existía. Los botones son el `btn-close` de Bootstrap, cuya X es
       * una imagen de fondo: llevan `aria-label`.
       */
      '@angular-eslint/template/label-has-associated-control': 'error',
      '@angular-eslint/template/elements-content': 'error',
    },
  },
);
