// -----------------------------
// Datos por defecto (tu producto Asus)
// -----------------------------
const productData = {
    id: 'vacio',
    titulo: 'Proximamente',
    precio: 'S/. 0,000',
    descripcion: 'Proximamente',
    imagenes: [],
    lista: ['Proximamente']
};

const productos = {
    "vacio": productData, // Producto vacio

    "asus-tuf": {
        id: 'asus-tuf',
        titulo: 'Laptop Asus Tuf F15 Gaming FX507VV-LP142',
        precio: 'S/. 5,499',
        descripcion: `La ASUS TUF F15 FX507VV-LP142 es una opción formidable para gamers o
    profesionales creativos que buscan alto rendimiento en un rango de precio competitivo. Su GPU
    RTX 4060 y el sólido procesador i7 garantizan desempeño realista en juegos y productividad.`,
        imagenes: [
            'https://media.falabella.com/falabellaPE/143189901_01/w=1500,h=1500,fit=pad',
            'https://media.falabella.com/falabellaPE/143189901_02/w=1500,h=1500,fit=pad',
            'https://media.falabella.com/falabellaPE/143189901_05/w=1500,h=1500,fit=pad'
        ],
        lista: [
            { label: 'Tamaño de la pantalla', value: '15.6' },
            { label: 'Memoria RAM', value: '16GB' },
            { label: 'Capacidad de almacenamiento', value: '1TB' },
            { label: 'Sistema operativo', value: 'Free DOS' },
            { label: 'Procesador', value: 'Intel Core i7' },
            { label: 'Núcleos del procesador', value: 'Deca core' },
            { label: 'Tipo', value: 'Notebooks' },
            { label: 'Resolución de pantalla', value: 'FHD' }
        ]
    },

    "acer-nitro-v15": {
        id: 'acer-nitro-v15',
        titulo: 'Laptop Acer Nitro V 15 ANV15-15-526M',
        precio: 'S/. 4,199',
        descripcion: `La Acer Nitro V15 ofrece potencia gamer con gráficos de alto nivel, buen
        balance entre rendimiento y precio para jugadores que buscan una experiencia fluida.`,
        imagenes: [
            'https://media.falabella.com/falabellaPE/137650197_01/w=1500,h=1500,fit=pad',
            'https://media.falabella.com/falabellaPE/137650197_02/w=1500,h=1500,fit=pad',
            'https://media.falabella.com/falabellaPE/137650197_03/w=1500,h=1500,fit=pad'
        ],
        lista: [
            { label: 'Tamaño de la pantalla', value: '15' },
            { label: 'Memoria RAM', value: '16GB' },
            { label: 'Sistema operativo', value: 'Windows 11' },
            { label: 'Procesador', value: 'Intel Core i5' },
            { label: 'Núcleos del procesador', value: 'Octa core' },
            { label: 'Tasa de refresco', value: '144 Hz' },
            { label: 'Tipo', value: 'Notebooks' },
            { label: 'Resolución de pantalla', value: 'FHD' }
        ]
    },

    "msi-katana-17": {
        id: 'msi-katana-17',
        titulo: 'Laptop MSI Katana 17 B13VFK',
        precio: 'S/. 6,299',
        descripcion: `La MSI Katana 17 ofrece un gran rendimiento con procesador Intel Core i7 de 13ª generación, 
    pantalla de 17.3” y gráficos potentes, ideal para gaming y productividad.`,
        imagenes: [
            'https://coolboxpe.vtexassets.com/arquivos/ids/386933-1200-1200?v=638839662511770000&width=1200&height=1200&aspect=true',
            'https://coolboxpe.vtexassets.com/arquivos/ids/386930-1200-1200?v=638839662511930000&width=1200&height=1200&aspect=true',
            'https://coolboxpe.vtexassets.com/arquivos/ids/386932-1200-1200?v=638839662512100000&width=1200&height=1200&aspect=true'
        ],
        lista: [
            { label: 'Tamaño de pantalla', value: '17.3"' },
            { label: 'Procesador', value: 'Intel Core i7' },
            { label: 'Detalle del procesador', value: 'Intel® Core™ i7-13620H Processor 2.4GHz (24MB Cache, up to 4.9GHz)' },
            { label: 'Memoria RAM', value: '16 GB' },
            { label: 'Detalle de memoria RAM', value: 'DDR5 5200 16GB (8GBx2)' },
            { label: 'Capacidad de disco sólido (SSD)', value: '512 GB' },
            { label: 'Resolución de pantalla', value: '1920 x 1080 (FHD)' },
            { label: 'Tipo de panel', value: 'IPS' }
        ]
    },

    "hp-pavilion-gaming-15": {
        id: 'hp-pavilion-gaming-15',
        titulo: 'HP Pavilion Gaming 15 Intel i5',
        precio: 'S/. 3,299',
        descripcion: `La HP Pavilion Gaming 15 es una laptop equilibrada para gaming y uso diario, 
    equipada con procesador Intel Core i5 de 10ma generación, tarjeta gráfica dedicada y pantalla Full HD.`,
        imagenes: [
            'https://laptronic.pe/catalogo/wp-content/uploads/HP-PAVILION-15-2-600x543.jpg',
            'https://laptronic.pe/catalogo/wp-content/uploads/HP-PAVILION-GAMING-15-3-600x600.png',
            'https://laptronic.pe/catalogo/wp-content/uploads/HP-PAVILION-GAMING-15-2-600x600.png'
        ],
        lista: [
            { label: 'Procesador', value: 'Intel Core i5 10ma Generación 10300H' },
            { label: 'Sistema operativo', value: 'Windows 10' },
            { label: 'Memoria RAM', value: '8GB DDR4' },
            { label: 'Almacenamiento', value: '1TB Disco Duro' },
            { label: 'Tarjeta gráfica', value: 'Nvidia GeForce GTX 1050 3GB' },
            { label: 'Pantalla', value: '15.6” FHD IPS' },
            { label: 'Conectividad', value: 'WiFi y Bluetooth' },
            { label: 'Puertos', value: '1 salida HDMI 1.4' }
        ]
    },

};

function createCarousel(id, imagenes) {
    const carousel = document.createElement('div');
    carousel.className = 'carousel slide';
    carousel.id = id;
    carousel.setAttribute('data-bs-ride', 'carousel');

    const inner = document.createElement('div');
    inner.className = 'carousel-inner';

    imagenes.forEach((src, idx) => {
        const item = document.createElement('div');
        item.className = 'carousel-item' + (idx === 0 ? ' active' : '');
        if (idx === 0) item.setAttribute('data-bs-interval', '10000');
        if (idx === 1) item.setAttribute('data-bs-interval', '2000');

        const img = document.createElement('img');
        img.className = 'd-block w-100';
        img.src = src;
        img.alt = (id || 'producto') + '-img-' + idx;
        img.style.padding = '30px';

        item.appendChild(img);
        inner.appendChild(item);
    });

    carousel.appendChild(inner);

    const prevBtn = document.createElement('button');
    prevBtn.className = 'carousel-control-prev';
    prevBtn.type = 'button';
    prevBtn.setAttribute('data-bs-target', '#' + id);
    prevBtn.setAttribute('data-bs-slide', 'prev');
    prevBtn.innerHTML = `<span class="carousel-control-prev-icon" aria-hidden="true"></span>
                       <span class="visually-hidden">Previous</span>`;
    carousel.appendChild(prevBtn);

    const nextBtn = document.createElement('button');
    nextBtn.className = 'carousel-control-next';
    nextBtn.type = 'button';
    nextBtn.setAttribute('data-bs-target', '#' + id);
    nextBtn.setAttribute('data-bs-slide', 'next');
    nextBtn.innerHTML = `<span class="carousel-control-next-icon" aria-hidden="true"></span>
                       <span class="visually-hidden">Next</span>`;
    carousel.appendChild(nextBtn);

    return carousel;
}

function renderProduct(rootId, data) {
    const root = document.getElementById(rootId);
    root.innerHTML = ''; // limpio

    const row = document.createElement('div');
    row.className = 'row row-cols-1 row-cols-lg-2 g-5';

    // columna izquierda (imagen / carrusel)
    const colLeft = document.createElement('div');
    colLeft.className = 'col';

    const imgWrap = document.createElement('div');
    imgWrap.className = 'imgprod position-relative';

    const titulo = document.createElement('h4');
    titulo.className = 'card-title mt-4';
    titulo.innerHTML = `<b>${data.titulo}</b>`;

    imgWrap.appendChild(titulo);

    // crear carousel con id único
    const carouselId = 'carousel-' + data.id + '-' + Date.now();
    const carouselEl = createCarousel(carouselId, data.imagenes);
    imgWrap.appendChild(carouselEl);

    colLeft.appendChild(imgWrap);
    row.appendChild(colLeft);

    // columna derecha (características, lista, precio, controls)
    const colRight = document.createElement('div');
    colRight.className = 'col text-start';

    // Encabezado características
    const ELista = document.createElement('h5');
    ELista.className = 'card-title text-start mt-4';
    ELista.style.borderBottom = '2px solid #BDC3C7';
    ELista.style.paddingBottom = '13px';
    ELista.textContent = 'Características específicas';
    colRight.appendChild(ELista);
    colRight.appendChild(document.createElement('br'));

    // descripcion
    const desc = document.createElement('p');
    desc.className = 'card-text';
    desc.style.marginRight = '20px';
    desc.style.textAlign = 'justify';
    desc.textContent = data.descripcion;
    colRight.appendChild(desc);

    // caja de especificaciones
    const specBox = document.createElement('div');
    specBox.className = 'spec-box';
    const specInner = document.createElement('div');
    specInner.className = 'text-start';
    specInner.style.margin = '15px';

    data.lista.forEach(s => {
        const p = document.createElement('p');
        p.className = 'card-text';
        p.style.marginTop = '-15px';
        p.innerHTML = `<span class="fw-semibold">${s.label}:</span> ${s.value}`;
        specInner.appendChild(p);
    });

    specBox.appendChild(specInner);
    colRight.appendChild(specBox);

    // Métodos de pago
    const hPayments = document.createElement('h5');
    hPayments.className = 'card-title text-start mt-4';
    hPayments.style.borderBottom = '2px solid #BDC3C7';
    hPayments.style.paddingBottom = '13px';
    hPayments.textContent = 'Métodos de pago';
    colRight.appendChild(hPayments);

    // Imagenes meotod pago
    const paymentsPlaceholder = document.getElementById('payments-placeholder');
    if (paymentsPlaceholder) {
        const clone = paymentsPlaceholder.firstElementChild.cloneNode(true);
        clone.style.display = 'block';
        colRight.appendChild(clone);
    }

    // Precio
    const price = document.createElement('h2');
    price.className = 'card-title text-start mt-4';
    price.style.color = '#06b56c';
    price.textContent = data.precio;
    colRight.appendChild(price);
    colRight.appendChild(document.createElement('br'));

    // Controles (cantidad + boton)
    const controls = document.createElement('div');
    controls.className = 'd-flex align-items-center justify-content-center';
    controls.style.gap = '40px';

    const qtyWrap = document.createElement('div');
    qtyWrap.className = 'd-flex align-items-center';
    const qtyLabel = document.createElement('span');
    qtyLabel.className = 'me-2';
    qtyLabel.textContent = 'Cantidad';
    const qtyInput = document.createElement('input');
    qtyInput.type = 'number';
    qtyInput.className = 'form-control';
    qtyInput.style.maxWidth = '100px';
    qtyInput.value = 1;
    qtyInput.min = 1;

    qtyWrap.appendChild(qtyLabel);
    qtyWrap.appendChild(qtyInput);

    const addBtn = document.createElement('button');
    addBtn.className = 'btn btn-outline-success';
    addBtn.style.fontSize = '15px';
    addBtn.type = 'button';
    addBtn.textContent = 'Agregar a Carrito';

    // Notificación carrito
    addBtn.addEventListener('click', () => {
        const cantidad = Number(qtyInput.value) || 1;

        // texto dinámico
        const msg = `Añadido al carrito: ${data.titulo}`;
        const msg2 = `Cantidad: ${cantidad}`;
        document.getElementById('toastMessage').innerHTML = `<span class="fw-bold">Añadido al carrito:</span> ${data.titulo}<br>
        <span class="fw-bold">Cantidad:</span> ${cantidad}`;

        // mostrar hora
        document.getElementById('toastTime').textContent = new Date().toLocaleTimeString();

        // mostrar el toast
        const toastEl = document.getElementById('liveToast');
        const toast = new bootstrap.Toast(toastEl);
        toast.show();
    });

    controls.appendChild(qtyWrap);
    const boldWrap = document.createElement('b');
    boldWrap.appendChild(addBtn);
    controls.appendChild(boldWrap);

    colRight.appendChild(controls);

    row.appendChild(colRight);
    root.appendChild(row);
}


(function elegirYRenderizar() {

    const parametros = new URLSearchParams(window.location.search);
    const idEnURL = parametros.get('id');

    const seleccionado = (idEnURL && productos[idEnURL]) ? productos[idEnURL] : productData;

    renderProduct('product-root', seleccionado);
})();
