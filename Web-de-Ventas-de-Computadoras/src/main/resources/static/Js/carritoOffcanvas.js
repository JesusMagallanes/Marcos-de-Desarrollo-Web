// Carrito gestionado en backend vía CarritoController
const carritoContenido = document.getElementById('carrito-contenido');
const subtotalEl = document.getElementById('subtotal');
const totalEl = document.getElementById('total');

function isLogged() {
  // Si existe el enlace a /Carrito en el header significa que hay usuario en sesión
  return !!document.querySelector('a[href="/Carrito"]');
}

// Delegated listener: cualquier elemento con atributo data-add-to-cart="true"
document.addEventListener('click', (e) => {
  const btn = e.target.closest('[data-add-to-cart]');
  if (!btn) return;
  e.preventDefault();

  if (!isLogged()) {
    const toastMsg = document.getElementById('toastMessage');
    const toastTime = document.getElementById('toastTime');
    if (toastMsg) toastMsg.innerHTML = '<span class="fw-bold">Necesitas iniciar sesión</span> para agregar productos al carrito.';
    if (toastTime) toastTime.textContent = new Date().toLocaleTimeString();
    const toastEl = document.getElementById('liveToast');
    if (toastEl) new bootstrap.Toast(toastEl).show();
    setTimeout(() => { window.location.href = '/usuarios/Loggin-User'; }, 900);
    return;
  }

  const id = btn.dataset.id || null;
  const cantidadInput = btn.closest('.col') ? btn.closest('.col').querySelector('input[type="number"]') : null;
  const cantidad = cantidadInput ? Math.max(1, Number(cantidadInput.value) || 1) : 1;

  // Llamada al controlador (endpoint AJAX añadido) para persistir en servidor
  const csrfToken = document.querySelector('meta[name="_csrf"]') ? document.querySelector('meta[name="_csrf"]').getAttribute('content') : null;
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]') ? document.querySelector('meta[name="_csrf_header"]').getAttribute('content') : 'X-CSRF-TOKEN';

  fetch('/carrito/addAjax/' + encodeURIComponent(id), {
    method: 'POST',
    headers: Object.assign({ 'Content-Type': 'application/json' }, csrfToken ? { [csrfHeader]: csrfToken } : {}),
    body: JSON.stringify({ cantidad })
  }).then(res => {
    if (!res.ok) throw new Error('No autorizado');
    // recargar items del carrito
    loadCartItems();
  }).catch(err => {
    console.error('Error al agregar al carrito:', err);
  });
});

function loadCartItems() {
  if (!isLogged()) {
    // si no está logeado, mostrar vacío
    if (carritoContenido) carritoContenido.innerHTML = '<p class="text-center">Tu carrito está vacío.</p>';
    if (subtotalEl) subtotalEl.textContent = 'S/0.00';
    if (totalEl) totalEl.textContent = 'S/0.00';
    return;
  }

  fetch('/carrito/items').then(r => r.json()).then(json => {
    const items = (json && json.items) ? json.items : [];
    if (!carritoContenido) return;
    if (items.length === 0) {
      carritoContenido.innerHTML = '<p class="text-center">Tu carrito está vacío.</p>';
      if (subtotalEl) subtotalEl.textContent = 'S/0.00';
      if (totalEl) totalEl.textContent = 'S/0.00';
      return;
    }

    carritoContenido.innerHTML = items.map((item) => {
      return `
        <div class="d-flex align-items-center mb-3 border-bottom pb-2" style="gap:12px;">
          <img src="${item.image || '/Img/img.png'}" width="56" height="56" style="object-fit:contain;border-radius:6px;" />
          <div style="flex:1;">
            <div class="d-flex justify-content-between align-items-start">
              <strong style="font-size:0.95rem">${item.nombre}</strong>
              <button data-item-id="${item.itemId}" class="btn btn-sm btn-link remove-item" style="color:inherit;">Quitar</button>
            </div>
            <div class="d-flex align-items-center justify-content-between mt-2">
              <div class="d-flex align-items-center" style="gap:8px;">
                <input type="number" min="1" value="${item.cantidad}" data-item-id-qty="${item.itemId}" data-product-id="${item.productId}" class="form-control form-control-sm qty-input" style="width:72px;" />
                <small class="text-muted">S/ ${Number(item.precio).toFixed(2)}</small>
              </div>
              <div><strong>S/ ${(Number(item.precio) * Number(item.cantidad)).toFixed(2)}</strong></div>
            </div>
          </div>
        </div>
      `;
    }).join('');

    if (subtotalEl) subtotalEl.textContent = 'S/ ' + (json.subtotal || 0).toFixed(2);
    if (totalEl) totalEl.textContent = 'S/ ' + (json.subtotal || 0).toFixed(2);

    // attach remove listeners
    document.querySelectorAll('button.remove-item').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const itemId = btn.dataset.itemId;
        const csrfToken2 = document.querySelector('meta[name="_csrf"]') ? document.querySelector('meta[name="_csrf"]').getAttribute('content') : null;
        const csrfHeader2 = document.querySelector('meta[name="_csrf_header"]') ? document.querySelector('meta[name="_csrf_header"]').getAttribute('content') : 'X-CSRF-TOKEN';
        fetch('/carrito/removeAjax/' + encodeURIComponent(itemId), { method: 'POST', headers: csrfToken2 ? { [csrfHeader2]: csrfToken2 } : {} }).then(r => {
          if (!r.ok) throw new Error('No autorizado');
          loadCartItems();
        }).catch(err => console.error(err));
      });
    });

    // attach qty listeners
    document.querySelectorAll('input[data-item-id-qty]').forEach(input => {
      input.addEventListener('change', (e) => {
        const newQty = Math.max(1, Number(input.value) || 1);
        input.value = newQty;
        const itemId = input.dataset.itemIdQty;
        const productId = input.dataset.productId;
        // Simplest update: eliminar item y volver a agregar con la cantidad deseada
        const csrfToken3 = document.querySelector('meta[name="_csrf"]') ? document.querySelector('meta[name="_csrf"]').getAttribute('content') : null;
        const csrfHeader3 = document.querySelector('meta[name="_csrf_header"]') ? document.querySelector('meta[name="_csrf_header"]').getAttribute('content') : 'X-CSRF-TOKEN';
        fetch('/carrito/removeAjax/' + encodeURIComponent(itemId), { method: 'POST', headers: csrfToken3 ? { [csrfHeader3]: csrfToken3 } : {} }).then(r => {
          if (!r.ok) throw new Error('No autorizado');
          return fetch('/carrito/addAjax/' + encodeURIComponent(productId), {
            method: 'POST', headers: Object.assign({ 'Content-Type': 'application/json' }, csrfToken3 ? { [csrfHeader3]: csrfToken3 } : {}), body: JSON.stringify({ cantidad: newQty })
          });
        }).then(r2 => {
          if (!r2.ok) throw new Error('No autorizado');
          loadCartItems();
        }).catch(err => console.error(err));
      });
    });

  }).catch(err => {
    console.error('Error cargando carrito:', err);
  });
}

// Inicializar: cargar desde servidor si hay sesión, si no mostrar vacío
loadCartItems();