// Variables globales
let precioMaximo = 10000;
let precioMinimo = 0;
let busquedaTexto = '';

// Inicialización al cargar el DOM
document.addEventListener('DOMContentLoaded', function() {
    inicializarEventos();
    inicializarHoverCards();
});

// Inicializar todos los eventos
function inicializarEventos() {
    // Sincronizar inputs de precio
    const precioMinInput = document.getElementById('precioMin');
    const precioMaxInput = document.getElementById('precioMax');
    
    if (precioMinInput) {
        precioMinInput.addEventListener('input', function() {
            precioMinimo = parseInt(this.value) || 0;
            aplicarFiltros();
        });
    }
    
    if (precioMaxInput) {
        precioMaxInput.addEventListener('input', function() {
            precioMaximo = parseInt(this.value) || 10000;
            const rangoPrecio = document.getElementById('rangoPrecio');
            if (rangoPrecio) {
                rangoPrecio.value = precioMaximo;
            }
            aplicarFiltros();
        });
    }
}

// Hover effect en cards
function inicializarHoverCards() {
    const cards = document.querySelectorAll('.hover-card');
    cards.forEach(card => {
        card.addEventListener('mouseenter', function() {
            this.style.transform = 'translateY(-5px)';
        });
        card.addEventListener('mouseleave', function() {
            this.style.transform = 'translateY(0)';
        });
    });
}

// Actualizar precio desde el slider
function actualizarPrecio(valor) {
    precioMaximo = parseInt(valor);
    const precioMaxInput = document.getElementById('precioMax');
    if (precioMaxInput) {
        precioMaxInput.value = precioMaximo;
    }
    aplicarFiltros();
}

// Limpiar todos los filtros
function limpiarFiltros() {
    // Limpiar checkboxes
    document.querySelectorAll('.filter-checkbox').forEach(cb => cb.checked = false);
    
    // Resetear precio
    precioMinimo = 0;
    precioMaximo = 10000;
    
    const rangoPrecio = document.getElementById('rangoPrecio');
    const precioMinInput = document.getElementById('precioMin');
    const precioMaxInput = document.getElementById('precioMax');
    
    if (rangoPrecio) rangoPrecio.value = 10000;
    if (precioMinInput) precioMinInput.value = 0;
    if (precioMaxInput) precioMaxInput.value = 10000;
    
    // Limpiar búsqueda
    const searchInput = document.getElementById('searchInput');
    if (searchInput) {
        searchInput.value = '';
        busquedaTexto = '';
    }
    
    // Aplicar filtros (mostrar todo)
    aplicarFiltros();
    
    // Cerrar offcanvas en móvil
    const offcanvas = bootstrap.Offcanvas.getInstance(document.getElementById('offcanvasFiltros'));
    if (offcanvas) {
        offcanvas.hide();
    }
}

// Aplicar filtros a los productos
function aplicarFiltros() {
    const productos = document.querySelectorAll('.producto-item');
    const marcasSeleccionadas = Array.from(document.querySelectorAll('[data-filter="marca"]:checked'))
        .map(cb => cb.value.toLowerCase());
    const stockSeleccionado = Array.from(document.querySelectorAll('[data-filter="stock"]:checked'))
        .map(cb => cb.value);
    
    const searchInput = document.getElementById('searchInput');
    busquedaTexto = searchInput ? searchInput.value.toLowerCase() : '';
    
    let productosVisibles = 0;
    
    productos.forEach(producto => {
        const nombre = producto.dataset.nombre?.toLowerCase() || '';
        const descripcion = producto.dataset.descripcion?.toLowerCase() || '';
        const marca = producto.dataset.marca?.toLowerCase() || '';
        const precio = parseFloat(producto.dataset.precio) || 0;
        const stock = parseInt(producto.dataset.stock) || 0;
        
        let mostrar = true;
        
        // Filtro de búsqueda por texto
        if (busquedaTexto && mostrar) {
            mostrar = nombre.includes(busquedaTexto) || descripcion.includes(busquedaTexto) || marca.includes(busquedaTexto);
        }
        
        // Filtro de marca
        if (marcasSeleccionadas.length > 0 && mostrar) {
            mostrar = marcasSeleccionadas.some(marcaFiltro => marca.includes(marcaFiltro));
        }
        
        // Filtro de precio
        if (mostrar && (precio < precioMinimo || precio > precioMaximo)) {
            mostrar = false;
        }
        
        // Filtro de stock
        if (stockSeleccionado.length > 0 && mostrar) {
            const tieneStock = stock > 0;
            const mostrarDisponible = stockSeleccionado.includes('disponible');
            const mostrarAgotado = stockSeleccionado.includes('agotado');
            
            if (mostrarDisponible && !mostrarAgotado) {
                mostrar = tieneStock;
            } else if (mostrarAgotado && !mostrarDisponible) {
                mostrar = !tieneStock;
            }
        }
        
        producto.style.display = mostrar ? '' : 'none';
        if (mostrar) productosVisibles++;
    });
    
    // Actualizar contador
    actualizarContador(productosVisibles);
    
    // Mostrar mensaje si no hay resultados
    const noResults = document.getElementById('no-results');
    if (noResults) {
        noResults.style.display = productosVisibles === 0 ? 'block' : 'none';
    }
    
    // Actualizar badges de filtros activos
    actualizarBadgesFiltros(marcasSeleccionadas, stockSeleccionado);
}

// Actualizar contador de resultados
function actualizarContador(visibles) {
    const contador = document.getElementById('resultado-contador');
    const total = document.querySelectorAll('.producto-item').length;
    if (contador) {
        contador.innerHTML = `<i class="fas fa-box-open me-2"></i>${visibles} de ${total} resultados`;
    }
}

// Actualizar badges de filtros activos
function actualizarBadgesFiltros(marcas, stocks) {
    const container = document.getElementById('active-filters');
    if (!container) return;
    
    let html = '';
    const todosFiltros = [];
    
    if (marcas.length > 0) {
        marcas.forEach(m => todosFiltros.push({ tipo: 'marca', valor: m }));
    }
    if (stocks.length > 0) {
        stocks.forEach(s => todosFiltros.push({ tipo: 'stock', valor: s }));
    }
    if (busquedaTexto) {
        todosFiltros.push({ tipo: 'búsqueda', valor: busquedaTexto });
    }
    if (precioMaximo < 10000 || precioMinimo > 0) {
        todosFiltros.push({ tipo: 'precio', valor: `S/ ${precioMinimo} - S/ ${precioMaximo}` });
    }
    
    if (todosFiltros.length > 0) {
        html = '<small class="text-muted">Filtros activos:</small><div class="d-flex flex-wrap gap-1 mt-1">';
        todosFiltros.forEach(filtro => {
            html += `<span class="badge bg-success">${filtro.tipo}: ${filtro.valor}</span>`;
        });
        html += '</div>';
    }
    
    container.innerHTML = html;
}

// Ordenar productos
function ordenarProductos(tipo) {
    const container = document.getElementById('productos-container');
    const productos = Array.from(container.querySelectorAll('.producto-item'));
    
    productos.sort((a, b) => {
        switch(tipo) {
            case 'nombre-asc':
                return (a.dataset.nombre || '').localeCompare(b.dataset.nombre || '');
            case 'nombre-desc':
                return (b.dataset.nombre || '').localeCompare(a.dataset.nombre || '');
            case 'precio-asc':
                return (parseFloat(a.dataset.precio) || 0) - (parseFloat(b.dataset.precio) || 0);
            case 'precio-desc':
                return (parseFloat(b.dataset.precio) || 0) - (parseFloat(a.dataset.precio) || 0);
            default:
                return 0;
        }
    });
    
    // Reorganizar elementos
    productos.forEach(producto => container.appendChild(producto));
    
    // Actualizar texto del botón
    const botonOrden = document.querySelector('.dropdown-toggle');
    if (botonOrden) {
        const textos = {
            'relevancia': 'Relevancia',
            'nombre-asc': 'Nombre A-Z',
            'nombre-desc': 'Nombre Z-A',
            'precio-asc': 'Precio ↑',
            'precio-desc': 'Precio ↓'
        };
        botonOrden.innerHTML = `<i class="fas fa-sort me-2"></i>${textos[tipo] || 'Ordenar'}`;
    }
}
