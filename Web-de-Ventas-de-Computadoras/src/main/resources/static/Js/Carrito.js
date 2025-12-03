// Carrito.js - Gestión del carrito de compras y pago con MercadoPago

(function () {
    const pageContainer = document.getElementById('carrito-page-contenido');
    const countEl = document.getElementById('carrito-page-count');
    const totalEl = document.getElementById('carrito-page-total');
    const totalStrong = document.getElementById('carrito-page-total-strong');

    function getCsrf() {
        const tokenMeta = document.querySelector('meta[name="_csrf"]');
        const headerMeta = document.querySelector('meta[name="_csrf_header"]');
        return {
            token: tokenMeta ? tokenMeta.getAttribute('content') : null,
            header: headerMeta ? headerMeta.getAttribute('content') : 'X-CSRF-TOKEN'
        };
    }

    function renderItems(json) {
        const items = (json && json.items) ? json.items : [];
        if (!items || items.length === 0) {
            pageContainer.innerHTML = '<p class="text-center">Tu carrito está vacío.</p>';
            countEl.textContent = 0;
            totalEl.textContent = 'S/ 0.00';
            totalStrong.textContent = 'S/ 0.00';
            return;
        }

        const html = items.map(item => {
            const price = Number(item.precio || 0).toFixed(2);
            return `
                <div class="row align-items-center mb-2">
                    <div class="col-2 text-center">
                        <img src="${item.image || '/Img/img.png'}" class="img-fluid" style="max-height:80px; object-fit:contain;" alt="Producto">
                    </div>
                    <div class="col-6">
                        <h6 class="mb-1 fw-bold">${item.nombre}</h6>
                        <p class="mb-1 text-muted">${item.nombre}</p>
                    </div>
                    <div class="col-4 text-end">
                        <h5 class="fw-bold text-danger mb-1">S/ ${price}</h5>
                        <div class="d-flex justify-content-end align-items-center mt-2">
                            <button data-product-id="${item.productId}" class="btn btn-sm btn-outline-secondary btn-decrease">-</button>
                            <input type="text" value="${item.cantidad}" class="form-control form-control-sm text-center mx-2" style="width:50px;" readonly>
                            <button data-product-id="${item.productId}" class="btn btn-sm btn-outline-secondary btn-add">+</button>
                            <button data-item-id="${item.itemId}" class="btn btn-sm btn-outline-danger ms-2 btn-delete" title="Eliminar item"><i class="fa fa-trash"></i></button>
                        </div>
                    </div>
                </div>
                <hr>
            `;
        }).join('\n');

        pageContainer.innerHTML = html;
        countEl.textContent = items.length;
        totalEl.textContent = 'S/ ' + (json.subtotal || 0).toFixed(2);
        totalStrong.textContent = 'S/ ' + (json.subtotal || 0).toFixed(2);

        // attach handlers
        document.querySelectorAll('.btn-add').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const productId = btn.dataset.productId;
                addAjax(productId, 1);
            });
        });

        // disminuir cantidad (usa addAjax con cantidad negativa)
        document.querySelectorAll('.btn-decrease').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const productId = btn.dataset.productId;
                addAjax(productId, -1);
            });
        });

        // eliminar item por completo
        document.querySelectorAll('.btn-delete').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const itemId = btn.dataset.itemId;
                if (!confirm('¿Eliminar este producto del carrito?')) return;
                removeAjax(itemId);
            });
        });
    }

    function loadPageCart() {
        return fetch('/carrito/items', { credentials: 'same-origin' })
            .then(r => {
                if (r.status === 401) {
                    pageContainer.innerHTML = '<p class="text-center">Debes iniciar sesión para ver el carrito.</p>';
                    return null;
                }
                return r.json();
            })
            .then(json => {
                if (json) renderItems(json);
            }).catch(err => {
                console.error('Error cargando carrito:', err);
                pageContainer.innerHTML = '<p class="text-center text-danger">Error cargando carrito.</p>';
            });
    }

    function addAjax(productId, cantidad) {
        const csrf = getCsrf();
        fetch('/carrito/addAjax/' + encodeURIComponent(productId), {
            method: 'POST',
            credentials: 'same-origin',
            headers: Object.assign({ 'Content-Type': 'application/json' }, csrf.token ? { [csrf.header]: csrf.token } : {}),
            body: JSON.stringify({ cantidad: cantidad })
        }).then(r => {
            if (!r.ok) throw new Error('No autorizado');
            return r.json().catch(() => ({ ok: true }));
        }).then(() => {
            loadPageCart();
        }).catch(err => {
            console.error('Error addAjax:', err);
            if (err.message && err.message.includes('No autorizado')) {
                window.location.href = '/usuarios/Loggin-User';
            }
        });
    }

    function removeAjax(itemId) {
        const csrf = getCsrf();
        fetch('/carrito/removeAjax/' + encodeURIComponent(itemId), {
            method: 'POST',
            credentials: 'same-origin',
            headers: csrf.token ? { [csrf.header]: csrf.token } : {}
        }).then(r => {
            if (!r.ok) throw new Error('No autorizado');
            return r.json().catch(() => ({ ok: true }));
        }).then(() => {
            loadPageCart();
        }).catch(err => {
            console.error('Error removeAjax:', err);
        });
    }

    // Mostrar modal de confirmación
    function mostrarModalExito() {
        // Verificar si Bootstrap está disponible
        if (typeof bootstrap === 'undefined') {
            console.warn('Bootstrap no está cargado, usando alert como fallback');
            alert('¡Pago exitoso! Tu pedido ha sido registrado.\n\nPuedes verlo en "Mis Compras".');
            window.location.reload();
            return;
        }

        // Crear modal dinámico
        const modalHtml = `
            <div class="modal fade" id="modalPagoExitoso" tabindex="-1" aria-labelledby="modalPagoExitosoLabel" aria-hidden="true">
                <div class="modal-dialog modal-dialog-centered">
                    <div class="modal-content">
                        <div class="modal-header bg-success text-white">
                            <h5 class="modal-title" id="modalPagoExitosoLabel">
                                <i class="fa fa-check-circle me-2"></i>¡Pago Exitoso!
                            </h5>
                            <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="Close"></button>
                        </div>
                        <div class="modal-body text-center">
                            <i class="fa fa-check-circle text-success" style="font-size: 4rem;"></i>
                            <h4 class="mt-3">¡Gracias por tu compra!</h4>
                            <p class="text-muted">Tu pago ha sido procesado exitosamente.</p>
                            <p class="text-muted">Tu pedido ha sido registrado y puedes verlo en "Mis Compras".</p>
                        </div>
                        <div class="modal-footer justify-content-center">
                            <button type="button" class="btn btn-primary" id="btnVerMisCompras">Ver Mis Compras</button>
                            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Continuar Comprando</button>
                        </div>
                    </div>
                </div>
            </div>
        `;

        // Insertar modal en el body si no existe
        if (!document.getElementById('modalPagoExitoso')) {
            document.body.insertAdjacentHTML('beforeend', modalHtml);
        }

        const modal = new bootstrap.Modal(document.getElementById('modalPagoExitoso'));
        modal.show();

        // Agregar evento para ir a mis compras
        document.getElementById('btnVerMisCompras').addEventListener('click', () => {
            window.location.href = '/usuarios/Loggin-User#misCompras';
        });

        // Al cerrar el modal, recargar la página
        document.getElementById('modalPagoExitoso').addEventListener('hidden.bs.modal', () => {
            window.location.reload();
        });
    }

    // Variable para evitar múltiples confirmaciones
    let confirmandoPago = false;

    // Función para confirmar el pago y crear el pedido
    async function confirmarPago() {
        if (confirmandoPago) {
            console.log('Ya se está procesando un pago, ignorando...');
            return;
        }

        try {
            confirmandoPago = true;
            console.log('=== Iniciando confirmación de pago ===');
            const csrf = getCsrf();
            const headers = { 'Content-Type': 'application/json' };
            if (csrf.token) headers[csrf.header] = csrf.token;

            console.log('Enviando petición a /carrito/confirmarPago');
            const res = await fetch('/carrito/confirmarPago', {
                method: 'POST',
                credentials: 'same-origin',
                headers
            });

            console.log('Respuesta recibida:', res.status);
            const data = await res.json();
            console.log('Datos:', data);

            if (res.ok && data.success) {
                console.log('Pago confirmado exitosamente. Pedido ID:', data.pedidoId);
                // Limpiar el flag de pago pendiente
                sessionStorage.removeItem('pagoPendiente');
                // Recargar el carrito para reflejar que está vacío
                await loadPageCart();
                // Mostrar modal de éxito
                mostrarModalExito();
            } else {
                console.error('Error en la respuesta:', data.message);
                // Si el error es que el carrito está vacío, es porque ya se procesó
                if (data.message && data.message.includes('vacío')) {
                    console.log('El pedido ya fue procesado, mostrando modal...');
                    sessionStorage.removeItem('pagoPendiente');
                    await loadPageCart();
                    mostrarModalExito();
                } else {
                    alert('Error al procesar el pago: ' + (data.message || 'Error desconocido'));
                    confirmandoPago = false;
                }
            }
        } catch (err) {
            console.error('Error confirmando pago:', err);
            console.error('Detalles del error:', {
                message: err.message,
                stack: err.stack,
                name: err.name
            });
            alert('Error al confirmar el pago: ' + err.message + '\nRevisa la consola para más detalles.');
            confirmandoPago = false;
        }
    }

    // Verificar si se regresó de MercadoPago con éxito
    function verificarPagoRetorno() {
        const urlParams = new URLSearchParams(window.location.search);
        const status = urlParams.get('status');
        const paymentId = urlParams.get('payment_id');
        const collectionStatus = urlParams.get('collection_status');

        console.log('=== Verificando parámetros de retorno ===');
        console.log('status:', status);
        console.log('payment_id:', paymentId);
        console.log('collection_status:', collectionStatus);
        console.log('collection_id:', urlParams.get('collection_id'));

        // MercadoPago puede devolver diferentes parámetros según la configuración
        // status=approved o collection_status=approved
        if ((status === 'approved' || collectionStatus === 'approved') && 
            (paymentId || urlParams.get('collection_id'))) {
            console.log('✓ Pago aprobado detectado, confirmando...');
            // El pago fue aprobado, confirmar en el servidor
            confirmarPago();
            // Limpiar los parámetros de la URL
            window.history.replaceState({}, document.title, window.location.pathname);
        } else {
            console.log('No se detectó pago aprobado o faltan parámetros');
        }
    }

    // Mostrar botón de confirmación manual si hay pago pendiente en sessionStorage
    function verificarPagoPendiente() {
        const pagoPendiente = sessionStorage.getItem('pagoPendiente');
        const btnConfirmar = document.getElementById('confirmar-pago-manual');
        
        if (pagoPendiente === 'true' && btnConfirmar) {
            console.log('Pago pendiente detectado, mostrando botón de confirmación');
            btnConfirmar.style.display = 'block';
            
            // Remover eventos previos para evitar duplicados
            const nuevoBtn = btnConfirmar.cloneNode(true);
            btnConfirmar.parentNode.replaceChild(nuevoBtn, btnConfirmar);
            
            nuevoBtn.addEventListener('click', () => {
                nuevoBtn.disabled = true;
                nuevoBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Procesando...';
                confirmarPago();
            });
        }
    }

    // inicializar
    document.addEventListener('DOMContentLoaded', () => {
        loadPageCart();
        verificarPagoRetorno();
        verificarPagoPendiente();
    });

    // Exponer función para uso externo si es necesario
    window.CarritoApp = {
        loadPageCart,
        confirmarPago
    };
})();
