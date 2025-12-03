// misCompras.js - Gestión de la vista de compras del usuario

(function () {
    const loadingEl = document.getElementById('pedidos-loading');
    const containerEl = document.getElementById('pedidos-container');
    const emptyEl = document.getElementById('pedidos-empty');
    const errorEl = document.getElementById('pedidos-error');

    // Función para formatear fecha
    function formatearFecha(fechaStr) {
        try {
            const fecha = new Date(fechaStr);
            return fecha.toLocaleDateString('es-ES', {
                year: 'numeric',
                month: 'long',
                day: 'numeric',
                hour: '2-digit',
                minute: '2-digit'
            });
        } catch (e) {
            return fechaStr;
        }
    }

    // Función para obtener el color y texto del estado
    function getEstadoInfo(estado) {
        const estados = {
            'PENDIENTE': { color: 'warning', texto: 'Pendiente', icon: 'clock' },
            'PROCESANDO': { color: 'info', texto: 'Procesando', icon: 'spinner' },
            'ENVIADO': { color: 'primary', texto: 'Enviado', icon: 'truck' },
            'ENTREGADO': { color: 'success', texto: 'Entregado', icon: 'check-circle' },
            'CANCELADO': { color: 'danger', texto: 'Cancelado', icon: 'times-circle' }
        };
        return estados[estado] || { color: 'secondary', texto: estado, icon: 'question-circle' };
    }

    // Función para renderizar un pedido
    function renderPedido(pedido) {
        const estadoInfo = getEstadoInfo(pedido.estado);
        const detallesHtml = pedido.detalles.map(detalle => `
            <div class="row align-items-center mb-2 pb-2 border-bottom">
                <div class="col-2 col-md-1">
                    <img src="${detalle.imagen || '/Img/img.png'}" 
                         class="img-fluid rounded" 
                         style="max-height: 60px; object-fit: contain;" 
                         alt="${detalle.productoNombre}">
                </div>
                <div class="col-6 col-md-7">
                    <h6 class="mb-1">${detalle.productoNombre}</h6>
                    <small class="text-muted">Cantidad: ${detalle.cantidad}</small>
                </div>
                <div class="col-4 col-md-4 text-end">
                    <p class="mb-0 fw-bold">S/ ${Number(detalle.total).toFixed(2)}</p>
                    <small class="text-muted">S/ ${Number(detalle.precioUnitario).toFixed(2)} c/u</small>
                </div>
            </div>
        `).join('');

        return `
            <div class="card mb-3 shadow-sm">
                <div class="card-header bg-light">
                    <div class="row align-items-center">
                        <div class="col-md-3">
                            <small class="text-muted">Pedido #${pedido.id}</small>
                        </div>
                        <div class="col-md-4">
                            <small class="text-muted">
                                <i class="fa fa-calendar me-1"></i>
                                ${formatearFecha(pedido.fecha)}
                            </small>
                        </div>
                        <div class="col-md-3">
                            <small class="text-muted">
                                <i class="fa fa-credit-card me-1"></i>
                                ${pedido.metodoPago}
                            </small>
                        </div>
                        <div class="col-md-2 text-end">
                            <span class="badge bg-${estadoInfo.color}">
                                <i class="fa fa-${estadoInfo.icon} me-1"></i>
                                ${estadoInfo.texto}
                            </span>
                        </div>
                    </div>
                </div>
                <div class="card-body">
                    ${detallesHtml}
                    <div class="row mt-3">
                        <div class="col-md-8">
                            <!-- Espacio para acciones futuras -->
                        </div>
                        <div class="col-md-4">
                            <div class="d-flex justify-content-between align-items-center">
                                <h5 class="mb-0">Total:</h5>
                                <h4 class="mb-0 text-primary fw-bold">S/ ${Number(pedido.total).toFixed(2)}</h4>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }

    // Función para cargar los pedidos
    async function cargarPedidos() {
        try {
            // Mostrar loading
            loadingEl.classList.remove('d-none');
            containerEl.classList.add('d-none');
            emptyEl.classList.add('d-none');
            errorEl.classList.add('d-none');

            const response = await fetch('/carrito/pedidos', {
                credentials: 'same-origin'
            });

            if (!response.ok) {
                throw new Error('Error al cargar pedidos');
            }

            const data = await response.json();
            const pedidos = data.pedidos || [];

            // Ocultar loading
            loadingEl.classList.add('d-none');

            if (pedidos.length === 0) {
                // Mostrar mensaje de vacío
                emptyEl.classList.remove('d-none');
            } else {
                // Renderizar pedidos
                const pedidosHtml = pedidos.map(pedido => renderPedido(pedido)).join('');
                containerEl.innerHTML = pedidosHtml;
                containerEl.classList.remove('d-none');
            }
        } catch (error) {
            console.error('Error cargando pedidos:', error);
            loadingEl.classList.add('d-none');
            errorEl.classList.remove('d-none');
        }
    }

    // Inicializar cuando el DOM esté listo
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', cargarPedidos);
    } else {
        cargarPedidos();
    }

    // Exponer función para recargar
    window.MisCompras = {
        recargar: cargarPedidos
    };
})();
