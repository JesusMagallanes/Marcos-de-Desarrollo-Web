package Pry_01.Web.de.Ventas.de.Computadoras.Security;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class PromptSecurityFilter {

    // Patrones de inyección de prompts (ataques comunes)
    private static final Set<String> BLOCKED_PATTERNS = new HashSet<>(Arrays.asList(
            // Intento de cambiar el rol del sistema
            "ignore previous", "ignore all", "forget your", "forget all",
            "new instruction", "system prompt", "override", "overwrite",
            "you are now", "from now on", "act as", "pretend to be",
            "you will now", "you must now", "your new role",

            // Intento de extraer información sensible
            "tell me your", "reveal your", "show your", "output your",
            "what is your", "give me your", "expose your",

            // Intento de ejecutar código
            "```", "javascript:", "eval(", "exec(", "system(",
            "runtime.exec", "processbuilder", "cmd.exe", "powershell",

            // Intento de SQL injection (precaución extra)
            "drop table", "delete from", "insert into", "update set",
            "union select", "select * from", "or 1=1", "--", "/*",

            // Intento de escapar del contexto
            "echo", "print", "console.log", "system.out",

            // Ataques de jailbreak
            "jailbreak", "jail break", "break out", "escape",
            "developer mode", "developer mode", "red team",

            // Intento de leer archivos
            "cat ", "type ", "read file", "open file",
            "etc/passwd", "win.ini", "boot.ini"
    ));

    // Patrones regex para detectar inyecciones más complejas
    private static final Pattern DANGEROUS_PATTERN = Pattern.compile(
            "(?i)(ignore|forget|override|overwrite|new instruction|system prompt|" +
                    "you are now|act as|jailbreak|escape|developer mode|" +
                    "eval\\(|exec\\(|system\\(|runtime\\.exec|" +
                    "drop\\s+table|delete\\s+from|insert\\s+into|update\\s+set|" +
                    "union\\s+select|select\\s+\\*\\s+from|" +
                    "cat\\s+|type\\s+|etc/passwd|win\\.ini)"
    );

    // Límite de caracteres por mensaje
    private static final int MAX_MESSAGE_LENGTH = 500;

    // Caracteres permitidos (solo texto seguro)
    private static final Pattern ALLOWED_CHARS = Pattern.compile("^[a-zA-Z0-9áéíóúüñÑ\\s.,!?¿¡:;()\\-\\[\\]\"]+$");

    /**
     * Valida y sanitiza un mensaje antes de enviarlo a la IA
     * @param mensaje El mensaje del usuario
     * @return El mensaje sanitizado
     * @throws SecurityException Si se detecta un intento de inyección
     */
    public String sanitizarMensaje(String mensaje) throws SecurityException {
        if (mensaje == null || mensaje.trim().isEmpty()) {
            throw new SecurityException("El mensaje está vacío");
        }

        // 1. Limitar longitud
        String sanitized = mensaje.trim();
        if (sanitized.length() > MAX_MESSAGE_LENGTH) {
            sanitized = sanitized.substring(0, MAX_MESSAGE_LENGTH);
        }

        // 2. Eliminar caracteres de control
        sanitized = sanitized.replaceAll("[\\n\\r\\t]", " ");
        sanitized = sanitized.replaceAll("\\s+", " ");

        // 3. Verificar caracteres permitidos (opcional, descomentar si quieres restringir)
        // if (!ALLOWED_CHARS.matcher(sanitized).matches()) {
        //     throw new SecurityException("El mensaje contiene caracteres no permitidos");
        // }

        // 4. Verificar patrones bloqueados (CASO INSENSITIVE)
        String lowerMessage = sanitized.toLowerCase();
        for (String pattern : BLOCKED_PATTERNS) {
            if (lowerMessage.contains(pattern.toLowerCase())) {
                throw new SecurityException("Intento de inyección de prompt detectado: " + pattern);
            }
        }

        // 5. Verificar con regex (detección más avanzada)
        if (DANGEROUS_PATTERN.matcher(sanitized).find()) {
            throw new SecurityException("Patrón sospechoso detectado en el mensaje");
        }

        return sanitized;
    }

    /**
     * Verifica si un mensaje es seguro sin lanzar excepción
     * @param mensaje El mensaje a verificar
     * @return true si es seguro, false si es sospechoso
     */
    public boolean esSeguro(String mensaje) {
        try {
            sanitizarMensaje(mensaje);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    /**
     * Obtiene la lista de patrones bloqueados (para logs)
     */
    public Set<String> getPatronesBloqueados() {
        return new HashSet<>(BLOCKED_PATTERNS);
    }
}