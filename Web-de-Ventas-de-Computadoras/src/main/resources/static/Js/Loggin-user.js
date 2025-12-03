document.addEventListener("DOMContentLoaded", () => {
  const menuLinks = document.querySelectorAll(".menu-link");
  const contenedor = document.getElementById("contenido-dinamico");

  // Función para cargar un script dinámicamente
  function cargarScript(src) {
    return new Promise((resolve, reject) => {
      // Verificar si el script ya existe
      const scriptExistente = document.querySelector(`script[src="${src}"]`);
      if (scriptExistente) {
        scriptExistente.remove(); // Remover para recargar
      }

      const script = document.createElement('script');
      script.src = src;
      script.onload = () => resolve();
      script.onerror = () => reject(new Error(`Error al cargar ${src}`));
      document.body.appendChild(script);
    });
  }

  menuLinks.forEach(link => {
    link.addEventListener("click", async (e) => {
      e.preventDefault();

      menuLinks.forEach(l => l.classList.remove("active"));
      link.classList.add("active");

      const fragmentName = link.getAttribute("data-fragment");
      let url = `/fragment?name=${encodeURIComponent(fragmentName)}`;

      try {
        const response = await fetch(url);
        if (!response.ok) throw new Error("Error al cargar el fragmento");

        const html = await response.text();
        contenedor.innerHTML = html;

        // Cargar scripts específicos según el fragmento
        if (fragmentName === 'misCompras') {
          await cargarScript('/Js/misCompras.js');
        }
      } catch (err) {
        contenedor.innerHTML = `
          <div class="alert alert-danger mt-3">
            Error al cargar el contenido: ${err.message}
          </div>`;
      }
    });
  });
});
