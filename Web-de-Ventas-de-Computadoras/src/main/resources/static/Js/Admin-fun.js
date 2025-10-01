function mostrarSeccion(id, boton) {
    // Ocultar secciones
    document.querySelectorAll('.seccion').forEach(sec => sec.classList.add('d-none'));

    // Mostrar seccion
    document.getElementById(id).classList.remove('d-none');

    // Desactivar boton
    document.querySelectorAll('#sidebarMenu button').forEach(btn => btn.classList.remove('active'));

    // Activar boton
    boton.classList.add('active');
}