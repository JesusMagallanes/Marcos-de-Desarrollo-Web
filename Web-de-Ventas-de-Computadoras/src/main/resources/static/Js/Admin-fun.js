function mostrarSeccion(id, boton) {
    document.querySelectorAll('.seccion').forEach(sec => sec.classList.add('d-none'));
    document.getElementById(id).classList.remove('d-none');
    document.querySelectorAll('#sidebarMenu button').forEach(btn => btn.classList.remove('active'));
    boton.classList.add('active');
}