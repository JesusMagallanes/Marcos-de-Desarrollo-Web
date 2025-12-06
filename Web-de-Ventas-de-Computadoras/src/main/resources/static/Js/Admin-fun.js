function mostrarSeccion(id, boton) {
    document.querySelectorAll('.seccion').forEach(sec => sec.classList.add('d-none'));
    const target = document.getElementById(id);
    if (target) {
        target.classList.remove('d-none');
    } else {
        console.warn("No se encontró la sección con id:", id);
    }
    document.querySelectorAll('#sidebarMenu button').forEach(btn => btn.classList.remove('active'));
    if (boton) boton.classList.add('active');
}

function toggleSidebar() {
    const sidebar = document.getElementById('sidebarMenu');
    if (!sidebar) return;
    sidebar.classList.toggle('collapsed');
    // También marcar en el body para estilos globales
    document.body.classList.toggle('sidebar-collapsed');
    // guardar estado en localStorage para persistir entre recargas
    const collapsed = sidebar.classList.contains('collapsed');
    try { localStorage.setItem('adminSidebarCollapsed', collapsed ? '1' : '0'); } catch (e) {}
}

// Restaurar estado al cargar
document.addEventListener('DOMContentLoaded', function() {
    try {
        const val = localStorage.getItem('adminSidebarCollapsed');
        if (val === '1') {
            const sidebar = document.getElementById('sidebarMenu');
            if (sidebar) sidebar.classList.add('collapsed');
            document.body.classList.add('sidebar-collapsed');
        }
    } catch (e) {}
    
    // Mostrar sección específica si viene en URL
    const urlParams = new URLSearchParams(window.location.search);
    const seccion = urlParams.get('seccion');
    if (seccion) {
        // Buscar el botón correspondiente y activarlo
        const botones = document.querySelectorAll('#sidebarMenu button, .offcanvas-body button');
        botones.forEach(btn => {
            const onclickAttr = btn.getAttribute('onclick');
            if (onclickAttr && onclickAttr.includes("'" + seccion + "'")) {
                mostrarSeccion(seccion, btn);
            }
        });
    }
});

// Actualizar icono del botón de colapso según el estado
function updateCollapseIcon() {
    const sidebar = document.getElementById('sidebarMenu');
    const icon = document.getElementById('collapseSidebarIcon');
    if (!icon) return;
    if (sidebar && sidebar.classList.contains('collapsed')) {
        icon.className = 'bi bi-chevron-right';
    } else {
        icon.className = 'bi bi-chevron-left';
    }
}

// Vincular actualización del icono al cargar y al toggle
document.addEventListener('DOMContentLoaded', function() {
    updateCollapseIcon();
    const btn = document.getElementById('collapseSidebarBtn');
    if (btn) btn.addEventListener('click', function() { setTimeout(updateCollapseIcon, 50); });
    // también inicializar tooltips de bootstrap
    try {
        var tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
        tooltipTriggerList.map(function (el) { return new bootstrap.Tooltip(el); });
    } catch (e) {}
    // si hay botón superior de toggle, actualizar icono al hacer click
    const topToggle = document.getElementById('toggleSidebarBtn');
    if (topToggle) topToggle.addEventListener('click', function() { setTimeout(updateCollapseIcon, 120); });
});

// Manejo responsivo: en pantallas pequeñas forzamos sidebar expandido (uso del offcanvas)
function handleResponsiveSidebar() {
    try {
        const sidebar = document.getElementById('sidebarMenu');
        if (!sidebar) return;
        if (window.innerWidth < 768) {
            sidebar.classList.remove('collapsed');
            document.body.classList.remove('sidebar-collapsed');
            // no sobreescribimos localStorage aquí; dejamos la preferencia del usuario para pantallas grandes
        } else {
            // restaurar según preferencia almacenada
            const val = localStorage.getItem('adminSidebarCollapsed');
            if (val === '1') {
                sidebar.classList.add('collapsed');
                document.body.classList.add('sidebar-collapsed');
            } else {
                sidebar.classList.remove('collapsed');
                document.body.classList.remove('sidebar-collapsed');
            }
        }
        // actualizar icono
        updateCollapseIcon();
    } catch (e) { console.warn(e); }
}

window.addEventListener('resize', function() {
    // pequeño debounce
    if (window._adminResizeTimeout) clearTimeout(window._adminResizeTimeout);
    window._adminResizeTimeout = setTimeout(handleResponsiveSidebar, 120);
});

// Inicializar el comportamiento responsivo inmediatamente
handleResponsiveSidebar();

/**
 * Carga las marcas disponibles para una categoría específica
 * @param {number} categoriaId - ID de la categoría seleccionada
 * @param {string} selectId - ID del select de marcas a actualizar
 */
function cargarMarcasPorCategoria(categoriaId, selectId) {
    const marcaSelect = document.getElementById(selectId);
    
    if (!categoriaId || categoriaId === '') {
        marcaSelect.innerHTML = '<option value="">Seleccione primero una categoría</option>';
        return;
    }
    
    // Mostrar loading
    marcaSelect.innerHTML = '<option value="">Cargando marcas...</option>';
    marcaSelect.disabled = true;
    
    // Hacer petición al backend para obtener las marcas de la categoría
    fetch(`/marcas/categoria/${categoriaId}`)
        .then(response => {
            if (!response.ok) {
                throw new Error('Error al cargar marcas');
            }
            return response.json();
        })
        .then(marcas => {
            marcaSelect.innerHTML = '<option value="">Seleccione una marca</option>';
            
            if (marcas && marcas.length > 0) {
                marcas.forEach(marca => {
                    const option = document.createElement('option');
                    option.value = marca.id;
                    option.textContent = marca.name;
                    marcaSelect.appendChild(option);
                });
            } else {
                marcaSelect.innerHTML = '<option value="">No hay marcas disponibles para esta categoría</option>';
            }
            
            marcaSelect.disabled = false;
        })
        .catch(error => {
            console.error('Error cargando marcas:', error);
            marcaSelect.innerHTML = '<option value="">Error al cargar marcas</option>';
            marcaSelect.disabled = false;
        });
}

/**
 * Carga las marcas para el formulario de edición
 * @param {number} categoriaId - ID de la categoría seleccionada
 * @param {string} categoriaSelectId - ID del select de categorías
 */
function cargarMarcasPorCategoriaEdit(categoriaId, categoriaSelectId) {
    // Extraer el ID del producto del select de categoría
    const productoId = categoriaSelectId.replace('categoriaSelectEdit-', '');
    const marcaSelectId = 'marcaSelectEdit-' + productoId;
    const marcaSelect = document.getElementById(marcaSelectId);
    
    if (!categoriaId || categoriaId === '') {
        marcaSelect.innerHTML = '<option value="">Seleccione primero una categoría</option>';
        return;
    }
    
    // Guardar la marca actualmente seleccionada
    const selectedMarcaId = marcaSelect.value;
    
    // Mostrar loading
    marcaSelect.innerHTML = '<option value="">Cargando marcas...</option>';
    marcaSelect.disabled = true;
    
    // Hacer petición al backend para obtener las marcas de la categoría
    fetch(`/marcas/categoria/${categoriaId}`)
        .then(response => {
            if (!response.ok) {
                throw new Error('Error al cargar marcas');
            }
            return response.json();
        })
        .then(marcas => {
            marcaSelect.innerHTML = '<option value="">Seleccione una marca</option>';
            
            if (marcas && marcas.length > 0) {
                marcas.forEach(marca => {
                    const option = document.createElement('option');
                    option.value = marca.id;
                    option.textContent = marca.name;
                    // Mantener la selección si la marca sigue disponible
                    if (marca.id == selectedMarcaId) {
                        option.selected = true;
                    }
                    marcaSelect.appendChild(option);
                });
            } else {
                marcaSelect.innerHTML = '<option value="">No hay marcas disponibles para esta categoría</option>';
            }
            
            marcaSelect.disabled = false;
        })
        .catch(error => {
            console.error('Error cargando marcas:', error);
            marcaSelect.innerHTML = '<option value="">Error al cargar marcas</option>';
            marcaSelect.disabled = false;
        });
}