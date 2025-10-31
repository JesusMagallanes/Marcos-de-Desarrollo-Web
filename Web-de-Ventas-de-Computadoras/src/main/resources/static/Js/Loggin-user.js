document.addEventListener("DOMContentLoaded", () => {
  const menuLinks = document.querySelectorAll(".menu-link");
  const contenedor = document.getElementById("contenido-dinamico");

  menuLinks.forEach(link => {
    link.addEventListener("click", async (e) => {
      e.preventDefault();

      menuLinks.forEach(l => l.classList.remove("active"));
      link.classList.add("active");

      const fragmentName = link.getAttribute("data-fragment");
      let url = `/fragment?name=${encodeURIComponent(fragmentName)}`;

      if (fragmentName === "cuenta") {
        url = "/fragment/cuenta";
      }

      try {
        const response = await fetch(url);
        if (!response.ok) throw new Error("Error al cargar el fragmento");

        const html = await response.text();
        contenedor.innerHTML = html;
      } catch (err) {
        contenedor.innerHTML = `
          <div class="alert alert-danger mt-3">
            Error al cargar el contenido: ${err.message}
          </div>`;
      }
    });
  });
  const defaultLink = document.querySelector(".menu-link[data-fragment='cuenta']");
  if (defaultLink) {
    setTimeout(() => {
      defaultLink.classList.add("active");
      defaultLink.click();
    }, 100);
  }
});
