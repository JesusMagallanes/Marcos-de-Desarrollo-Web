
const productData = {
    id: 'vacio',
    titulo: 'Proximamente',
    precio: 'S/. 0,000',
    descripcion: 'Proximamente',
    imagenes: [],
    lista: ['Proximamente']
};

const productos = {
    "vacio": productData, 



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

    //CELULARES

    "iphone-14": {
        id: 'iphone-14',
        titulo: 'iPhone 14 – 128GB',
        precio: 'S/. 4,299',
        descripcion: `El iPhone 14 ofrece un equilibrio perfecto entre rendimiento, diseño y ecosistema Apple. Ideal para quienes buscan fluidez y calidad fotográfica confiable.`,
        imagenes: [
            'https://m.media-amazon.com/images/I/61WUSYIQdKL._AC_SY879_.jpg',
            'https://m.media-amazon.com/images/I/41lENDon3NL._AC_SX679_.jpg',
            'https://m.media-amazon.com/images/I/614ltfkJQwL._AC_SX679_.jpg'
        ],
        lista: [
            { label: 'Pantalla', value: '6.1" OLED' },
            { label: 'Resolución', value: 'Super Retina XDR' },
            { label: 'Almacenamiento', value: '128GB' },
            { label: 'Procesador', value: 'A15 Bionic' },
            { label: 'Cámara', value: 'Dual 12MP' },
            { label: 'Memoria RAM', value: '6GB' },
            { label: 'Sistema operativo', value: 'iOS 16' },
            { label: 'Batería', value: 'Hasta 20h de video' }
        ]
    },

    "samsung-galaxy-s23": {
        id: 'samsung-galaxy-s23',
        titulo: 'Samsung Galaxy S23 – 256GB',
        precio: 'S/. 3,899',
        descripcion: `El Galaxy S23 combina potencia con diseño compacto. Su pantalla AMOLED 120Hz y el Snapdragon 8 Gen2 garantizan fluidez en juegos y multitarea.`,
        imagenes: [
            'https://i5.walmartimages.com/seo/Samsung-Galaxy-S23-256GB-Unlocked-Phantom-Black_9f234d8a-15b9-4db1-9ace-f8d07d68a8c5.fd6e4f7e369de98956b78ea8560c9820.jpeg?odnHeight=573&odnWidth=573&odnBg=FFFFFF',
            'https://i5.walmartimages.com/asr/51f851d2-79fd-4547-9438-d8a90dfcccaa.2dbdd4e7dcaba981c29642e1206bc70d.jpeg?odnHeight=2000&odnWidth=2000&odnBg=FFFFFF',
            'https://i5.walmartimages.com/asr/7bd2a6f9-f02e-438c-8dc1-404b86bd60ad.45118719db03e26ee26ae3810d9bb28d.jpeg?odnHeight=2000&odnWidth=2000&odnBg=FFFFFF'
        ],
        lista: [
            { label: 'Pantalla', value: '6.1" AMOLED 120Hz' },
            { label: 'Procesador', value: 'Snapdragon 8 Gen2' },
            { label: 'Almacenamiento', value: '256GB' },
            { label: 'Memoria RAM', value: '8GB' },
            { label: 'Cámara', value: 'Triple 50MP' },
            { label: 'Batería', value: '3900 mAh' },
            { label: 'Sistema operativo', value: 'Android 13' },
            { label: 'Conectividad', value: '5G' }
        ]
    },

    "xiaomi-12t-pro": {
        id: 'xiaomi-12t-pro',
        titulo: 'Xiaomi 12T Pro – 256GB',
        precio: 'S/. 2,599',
        descripcion: `El Xiaomi 12T Pro destaca por su cámara de 200MP y gran desempeño gracias al Snapdragon 8+ Gen1, ideal para fotografía avanzada y potencia diaria.`,
        imagenes: [
            'https://chobs.mistoremx.com/images/202210/goods_img/0_P_1666325094009.jpg',
            'https://chobs.mistoremx.com/images/202212/goods_img/1003_P_1669935333398.jpg',
            'https://chobs.mistoremx.com/images/202210/goods_img/_P_1666325112170.jpg'
        ],
        lista: [
            { label: 'Pantalla', value: '6.67" AMOLED 120Hz' },
            { label: 'Procesador', value: 'Snapdragon 8+ Gen1' },
            { label: 'Almacenamiento', value: '256GB' },
            { label: 'Memoria RAM', value: '12GB' },
            { label: 'Cámara', value: '200MP' },
            { label: 'Batería', value: '5000 mAh' },
            { label: 'Carga rápida', value: '120W' },
            { label: 'Sistema operativo', value: 'Android 13' }
        ]
    },

    "motorola-edge-30": {
        id: 'motorola-edge-30',
        titulo: 'Motorola Edge 30 – 256GB',
        precio: 'S/. 1,899',
        descripcion: `El Motorola Edge 30 combina fluidez con su panel OLED de 144Hz y un diseño ultraligero, ideal para quienes buscan pantalla rápida y gran conectividad.`,
        imagenes: [
            'https://miportal.entel.pe/static/073120251709044/images/Motorola_Moto_Edge_30_Fusion_256GB_Negro_Posterior_276x549.jpg',
            'https://miportal.entel.pe/static/073120251709044/images/Motorola_Moto_Edge_30_Fusion_256GB_Negro_Frontal_276x549.jpg',
            'https://miportal.entel.pe/static/073120251709044/images/Motorola_Moto_Edge_30_Fusion_256GB_Negro_Lateral1_276x549.jpg'
        ],
        lista: [
            { label: 'Pantalla', value: '6.5" OLED 144Hz' },
            { label: 'Procesador', value: 'Snapdragon 778G+' },
            { label: 'Almacenamiento', value: '256GB' },
            { label: 'Memoria RAM', value: '8GB' },
            { label: 'Cámara', value: 'Dual 50MP' },
            { label: 'Batería', value: '4020 mAh' },
            { label: 'Sistema operativo', value: 'Android 12' },
            { label: 'Conectividad', value: '5G' }
        ]
    },

    //MONITORES

    "samsung-odyssey-g5": {
        id: 'samsung-odyssey-g5',
        titulo: 'Samsung Odyssey G5 – 27" Curvo',
        precio: 'S/. 1,399',
        descripcion: `El Samsung Odyssey G5 es un monitor curvo QHD con 144Hz, ideal para gamers que buscan inmersión, rapidez y gran calidad visual.`,
        imagenes: [
            'https://images.samsung.com/is/image/samsung/p6pim/pe/ls27cg552elxpe/gallery/pe-odyssey-g5-g55c-ls27cg552elxpe-539626583?$684_547_PNG$',
            'https://images.samsung.com/is/image/samsung/p6pim/pe/ls27cg552elxpe/gallery/pe-odyssey-g5-g55c-ls27cg552elxpe-539626565?$Q90_684_547_JPG$',
            'https://images.samsung.com/is/image/samsung/p6pim/pe/ls27cg552elxpe/gallery/pe-odyssey-g5-g55c-ls27cg552elxpe-539626551?$Q90_684_547_JPG$'
        ],
        lista: [
            { label: 'Tamaño', value: '27" Curvo' },
            { label: 'Resolución', value: 'QHD (2560 x 1440)' },
            { label: 'Frecuencia', value: '144Hz' },
            { label: 'Tiempo de respuesta', value: '1ms' },
            { label: 'Panel', value: 'VA' },
            { label: 'Conectividad', value: 'HDMI, DisplayPort' },
            { label: 'Sincronización', value: 'FreeSync Premium' },
            { label: 'Diseño', value: 'Curvatura 1000R' }
        ]
    },

    "lg-ultragear-24": {
        id: 'lg-ultragear-24',
        titulo: 'LG UltraGear – 24"',
        precio: 'S/. 999',
        descripcion: `El LG UltraGear de 24 pulgadas es un monitor FHD con 165Hz y 1ms, diseñado para jugadores competitivos que necesitan precisión y fluidez.`,
        imagenes: [
            'https://www.lg.com/content/dam/channel/wcms/pe/images/monitores/24gs65f-b_awf_espr_pe_c/gallery/ultragear-24gs65f-gallery-01-2010.jpg/jcr:content/renditions/thum-1600x1062.jpeg',
            'https://www.lg.com/content/dam/channel/wcms/pe/images/monitores/24gs65f-b_awf_espr_pe_c/gallery/ultragear-24gs65f-gallery-04-2010.jpg/jcr:content/renditions/thum-1600x1062.jpeg',
            'https://www.lg.com/content/dam/channel/wcms/pe/images/monitores/24gs65f-b_awf_espr_pe_c/gallery/ultragear-24gs65f-gallery-05-2010.jpg/jcr:content/renditions/thum-1600x1062.jpeg'
        ],
        lista: [
            { label: 'Tamaño', value: '24"' },
            { label: 'Resolución', value: 'FHD (1920x1080)' },
            { label: 'Frecuencia', value: '165Hz' },
            { label: 'Tiempo de respuesta', value: '1ms' },
            { label: 'Panel', value: 'IPS' },
            { label: 'Conectividad', value: 'HDMI, DisplayPort' },
            { label: 'Sincronización', value: 'AMD FreeSync' },
            { label: 'Diseño', value: 'Bordes ultrafinos' }
        ]
    },

    "asus-tuf-vg27aq": {
        id: 'asus-tuf-vg27aq',
        titulo: 'ASUS TUF Gaming VG27AQ – 27"',
        precio: 'S/. 1,599',
        descripcion: `El ASUS TUF VG27AQ combina resolución QHD con 165Hz y Adaptive Sync, pensado para gamers que buscan rendimiento y detalle visual superior.`,
        imagenes: [
            'https://dlcdnwebimgs.asus.com/gain/45d87ea6-4e9a-4510-8a7f-d9954b0c0e64/w692',
            'https://dlcdnimgs.asus.com/websites/global/products/x2ahzOXdioDZggLv/images/section4-img.png',
            'https://dlcdnimgs.asus.com/websites/global/products/x2ahzOXdioDZggLv/images/pic_Multi_HDR_Mode.jpg'
        ],
        lista: [
            { label: 'Tamaño', value: '27"' },
            { label: 'Resolución', value: 'QHD (2560x1440)' },
            { label: 'Frecuencia', value: '165Hz' },
            { label: 'Panel', value: 'IPS' },
            { label: 'Tecnología', value: 'G-Sync Compatible / Adaptive Sync' },
            { label: 'Tiempo de respuesta', value: '1ms MPRT' },
            { label: 'Conectividad', value: 'HDMI 2.0, DP 1.2' },
            { label: 'Audio', value: 'Altavoces integrados' }
        ]
    },

    "acer-predator-xb273": {
        id: 'acer-predator-xb273',
        titulo: 'Acer Predator XB273 – 27"',
        precio: 'S/. 1,899',
        descripcion: `El Acer Predator XB273 está orientado al gaming competitivo, con 240Hz y FHD, ideal para shooters rápidos y eSports.`,
        imagenes: [
            'https://www.magitech.pe/media/catalog/product/cache/1/image/600x/040ec09b1e35df139433887a97daa66f/p/r/predator-1_1.jpg',
            'https://www.magitech.pe/media/catalog/product/cache/1/image/600x/040ec09b1e35df139433887a97daa66f/p/r/predator-4_1.jpg',
            'https://www.magitech.pe/media/catalog/product/cache/1/image/600x/040ec09b1e35df139433887a97daa66f/p/r/predator-2_1.jpg'
        ],
        lista: [
            { label: 'Tamaño', value: '27"' },
            { label: 'Resolución', value: 'FHD (1920x1080)' },
            { label: 'Frecuencia', value: '240Hz' },
            { label: 'Tiempo de respuesta', value: '1ms' },
            { label: 'Panel', value: 'IPS' },
            { label: 'Conectividad', value: 'HDMI, DisplayPort' },
            { label: 'Sincronización', value: 'G-Sync Compatible' },
            { label: 'Ergonomía', value: 'Ajustable en altura' }
        ]
    },

    //CONSOLAS

    "nintendo-switch-oled": {
        id: 'nintendo-switch-oled',
        titulo: 'Nintendo Switch (OLED Model)',
        precio: 'S/. 1,699',
        descripcion: `La Nintendo Switch OLED ofrece una experiencia de juego versátil con pantalla OLED de 7", audio mejorado y almacenamiento interno ampliable. Ideal para jugar en casa o en modo portátil.`,
        imagenes: [
            'https://promart.vteximg.com.br/arquivos/ids/2229646-1000-1000/image-0685b3f9dc974de494a7c76483d72934.jpg?v=637687030626370000',
            'https://promart.vteximg.com.br/arquivos/ids/2229651-1000-1000/image-8b9d20b670a8454abed134276944b77f.jpg?v=637687030627170000',
            'https://promart.vteximg.com.br/arquivos/ids/2229649-1000-1000/image-06101c573e724b5dbaf862e33cd452dd.jpg?v=637687030626830000'
        ],
        lista: [
            { label: 'Pantalla', value: '7" OLED' },
            { label: 'Resolución', value: '1280x720 (modo portátil)' },
            { label: 'Almacenamiento interno', value: '64GB, expandible con microSD' },
            { label: 'Batería', value: '4.5 a 9 horas según uso' },
            { label: 'Conectividad', value: 'Wi-Fi, Bluetooth 5.0' },
            { label: 'Audio', value: 'Mejorado en modo portátil' },
            { label: 'Modos de juego', value: 'Docked, tabletop, handheld' },
            { label: 'Color', value: 'Blanco Joy-Con OLED' }
        ]
    },

    "ps5-slim-edition": {
        id: 'ps5-slim-edition',
        titulo: 'PS5 Slim Edition',
        precio: 'S/. 3,299',
        descripcion: `La PS5 Slim Edition ofrece la experiencia de juego de nueva generación en un diseño más compacto, con lector de discos Blu-ray 4K, audio 3D inmersivo y gran biblioteca de títulos exclusivos.`,
        imagenes: [
            'https://promart.vteximg.com.br/arquivos/ids/8731437-1000-1000/imageUrl_6.jpg?v=638786540522570000',
            'https://promart.vteximg.com.br/arquivos/ids/8731439-1000-1000/imageUrl_4.jpg?v=638786540523030000',
            'https://promart.vteximg.com.br/arquivos/ids/8731442-1000-1000/imageUrl_5.jpg?v=638786540523970000'
        ],
        lista: [
            { label: 'Procesador', value: 'CPU AMD Zen 2 8 núcleos' },
            { label: 'GPU', value: 'AMD RDNA 2 personalizada' },
            { label: 'Memoria RAM', value: '16GB GDDR6' },
            { label: 'Almacenamiento interno', value: '1TB SSD' },
            { label: 'Resolución', value: 'Hasta 4K' },
            { label: 'Audio', value: 'Tempest 3D AudioTech' },
            { label: 'Conectividad', value: 'Wi-Fi 6, Bluetooth 5.1' },
            { label: 'Compatibilidad', value: 'Juegos PS4 y PS5' }
        ]
    },

    "xbox-series-s": {
        id: 'xbox-series-s',
        titulo: 'Xbox Series S',
        precio: 'S/. 2,999',
        descripcion: `La Xbox Series S es una consola de nueva generación compacta y potente, ideal para juegos digitales en resolución hasta 1440p, con tiempos de carga ultrarrápidos gracias a su SSD y compatibilidad con Xbox Game Pass.`,
        imagenes: [
            'https://promart.vteximg.com.br/arquivos/ids/8824052-1000-1000/image-f6727484b2094f4e82a6a542e7e0c79c.jpg?v=638824079692870000',
            'https://promart.vteximg.com.br/arquivos/ids/8469966-1000-1000/image-0.jpg?v=638682562604470000',
            'https://promart.vteximg.com.br/arquivos/ids/8469967-1000-1000/image-1.jpg?v=638682562607600000'
        ],
        lista: [
            { label: 'Procesador', value: 'CPU AMD Zen 2 8 núcleos' },
            { label: 'GPU', value: 'AMD RDNA 2 personalizada' },
            { label: 'Memoria RAM', value: '10GB GDDR6' },
            { label: 'Almacenamiento interno', value: '1TB SSD' },
            { label: 'Resolución', value: 'Hasta 1440p' },
            { label: 'Tasa de refresco', value: 'Hasta 120Hz' },
            { label: 'Conectividad', value: 'Wi-Fi 5, Bluetooth 5.0' },
            { label: 'Compatibilidad', value: 'Juegos Xbox One, Xbox Series S/X' }
        ]
    },

    "steam-deck-oled": {
        id: 'steam-deck-oled',
        titulo: 'Steam Deck OLED – 512GB SSD',
        precio: 'S/. 2,499',
        descripcion: `La Steam Deck OLED de 512GB ofrece una experiencia de juego portátil con pantalla OLED de alta calidad, almacenamiento SSD rápido y control ergonómico, ideal para jugar títulos de PC en cualquier lugar.`,
        imagenes: [
            'https://ezpc.pe/wp-content/uploads/2023/03/V004284-30-2.jpg',
            'https://ezpc.pe/wp-content/uploads/2023/03/V004284-30.jpg',
            'https://ezpc.pe/wp-content/uploads/2023/03/V004284-30-3.jpg'
        ],
        lista: [
            { label: 'Pantalla', value: '7" OLED táctil' },
            { label: 'Resolución', value: '1280x800' },
            { label: 'Almacenamiento interno', value: '512GB SSD' },
            { label: 'Procesador', value: 'APU AMD personalizado' },
            { label: 'Memoria RAM', value: '16GB LPDDR5' },
            { label: 'Conectividad', value: 'Wi-Fi, Bluetooth 5.0' },
            { label: 'Audio', value: 'Estéreo con 3.5mm jack' },
            { label: 'Controles', value: 'Joystick, botones, trackpads' }
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

    // Si el id en la URL es numérico asumimos que viene desde la BD (p.id)
    const isNumericId = idEnURL && /^\d+$/.test(idEnURL);

    if (isNumericId) {
        // Llamar a la API REST para obtener el producto por id
        fetch('/productos/' + idEnURL)
            .then(res => {
                if (!res.ok) throw new Error('Producto no encontrado');
                return res.json();
            })
            .then(json => {
                // json corresponde a ProductosResponseDTO
                const data = {
                    id: json.id,
                    titulo: json.name || 'Producto',
                    precio: 'S/. ' + (json.precio != null ? Number(json.precio).toFixed(2) : '0.00'),
                    descripcion: json.description || '',
                    imagenes: json.imageUrl ? [json.imageUrl] : [],
                    lista: []
                };
                renderProduct('product-root', data);
            })
            .catch(err => {
                console.error('Error al cargar producto desde API:', err);
                renderProduct('product-root', productData);
            });
    } else {
        const seleccionado = (idEnURL && productos[idEnURL]) ? productos[idEnURL] : productData;
        renderProduct('product-root', seleccionado);
    }
})();

