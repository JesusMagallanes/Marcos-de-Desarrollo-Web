// MercadoPago.js - Integración con MercadoPago para el carrito

(function () {
    function getCsrf() {
        const tokenMeta = document.querySelector('meta[name="_csrf"]');
        const headerMeta = document.querySelector('meta[name="_csrf_header"]');
        return {
            token: tokenMeta ? tokenMeta.getAttribute('content') : null,
            header: headerMeta ? headerMeta.getAttribute('content') : 'X-CSRF-TOKEN'
        };
    }

    async function payWithMercadoPago() {
        try {
            // obtener public key desde meta
            const mpPublicMeta = document.querySelector('meta[name="mp-public-key"]');
            const publicKey = mpPublicMeta ? mpPublicMeta.getAttribute('content') : null;
            if (!publicKey || publicKey.startsWith('REEMPLAZA')) {
                alert('Public Key de MercadoPago no encontrada. Configure MP_PUBLIC_KEY en variables de entorno.');
                return;
            }

            // pedir carrito actual al servidor
            const cartRes = await fetch('/carrito/items', { credentials: 'same-origin' });
            if (!cartRes.ok) {
                alert('No se pudo leer el carrito. Inicia sesión y vuelve a intentarlo.');
                return;
            }
            const cartJson = await cartRes.json();

            // Construir una preferencia simple: un item único que representa el carrito (subtotal)
            const subtotal = cartJson.subtotal ? Number(cartJson.subtotal) : 0;
            if (!subtotal || subtotal <= 0) {
                alert('El carrito está vacío.');
                return;
            }

            const body = {
                title: 'Carrito SmartZone',
                id: 'carrito-' + Date.now(),
                quantity: 1,
                unitPrice: subtotal
            };

            const csrf = getCsrf();
            const headers = { 'Content-Type': 'application/json' };
            if (csrf.token) headers[csrf.header] = csrf.token;

            const res = await fetch('/api/mercadopago/create_preference', {
                method: 'POST',
                credentials: 'same-origin',
                headers,
                body: JSON.stringify(body)
            });

            const json = await res.json();
            if (!res.ok) {
                console.error('Error creando preferencia MP:', json);
                alert('Error creando preferencia de pago. Revisa la consola del servidor.');
                return;
            }

            // Si el backend devuelve init_point (URL de checkout), redirigimos directamente
            // Esto evita problemas con bloqueadores de popups porque usamos navegación en la misma pestaña.
            const url = json.init_point || json.sandbox_init_point;
            if (url) {
                // Marcar que hay un pago pendiente
                sessionStorage.setItem('pagoPendiente', 'true');
                window.location.href = url; // redirige al checkout de MercadoPago
                return;
            }

            // Fallback: intentar abrir lightbox vía SDK si se devolvió un id de preferencia
            if (json.id && typeof MercadoPago !== 'undefined' && publicKey) {
                const mp = new MercadoPago(publicKey, { locale: 'es-PE' });
                try {
                    mp.checkout({ preference: { id: json.id } });
                } catch (err) {
                    console.error('Error abriendo checkout via SDK:', err, json);
                    alert('No se pudo abrir el checkout embebido. Se redirigirá al init_point si está disponible en el servidor.');
                }
            } else {
                alert('Respuesta de MP inesperada. Revisa la consola.');
                console.log(json);
            }

        } catch (err) {
            console.error('Error payWithMercadoPago:', err);
            alert('Error iniciando pago. Revisa la consola.');
        }
    }

    document.addEventListener('DOMContentLoaded', function () {
        const btn = document.getElementById('mp-pay-btn');
        if (btn) btn.addEventListener('click', payWithMercadoPago);
    });
})();
