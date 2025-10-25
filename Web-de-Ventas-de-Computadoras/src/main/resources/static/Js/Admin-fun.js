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