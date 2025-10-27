let carrito = JSON.parse(localStorage.getItem('carrito')) || [];

  const botonesAgregar = document.querySelectorAll('.btn-agregar');
  const carritoContenido = document.getElementById('carrito-contenido');
  const totalSpan = document.getElementById('total');

  botonesAgregar.forEach(btn => {
    btn.addEventListener('click', (e) => {
      const card = e.target.closest('.card');
      const nombre = card.querySelector('.nombre').textContent;
      const precio = parseFloat(card.querySelector('.precio').dataset.precio);

      const itemExistente = carrito.find(i => i.nombre === nombre);
      if (itemExistente) {
        itemExistente.cantidad++;
      } else {
        carrito.push({ nombre, precio, cantidad: 1 });
      }

      actualizarCarrito();
    });
  });

  function actualizarCarrito() {
    localStorage.setItem('carrito', JSON.stringify(carrito));

    if (carrito.length === 0) {
      carritoContenido.innerHTML = "<p>El carrito está vacío.</p>";
      totalSpan.textContent = "0.00";
      return;
    }

    carritoContenido.innerHTML = carrito.map(item => `
      <div class="d-flex justify-content-between align-items-center mb-2 border-bottom pb-2">
        <span>${item.nombre}</span>
        <span>x${item.cantidad}</span>
        <span>$${(item.precio * item.cantidad).toFixed(2)}</span>
      </div>
    `).join('');

    const total = carrito.reduce((sum, i) => sum + i.precio * i.cantidad, 0);
    totalSpan.textContent = total.toFixed(2);
  }

  actualizarCarrito();