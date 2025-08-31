const chatContainer = document.getElementById('chatbot-container');
const chatToggle = document.getElementById('chat-toggle');
const chatBody = document.getElementById('chat-body');
const closeChat = document.getElementById('close-chat');
const sendBtn = document.getElementById('send-btn');
const userInput = document.getElementById('user-input');

chatToggle.addEventListener('click', () => {
    chatContainer.style.display = 'flex';
    chatToggle.style.display = 'none';
    if (!chatBody.querySelector('.message')) appendBotWelcome();
});
closeChat.addEventListener('click', () => { chatContainer.style.display = 'none'; chatToggle.style.display = 'block'; });
sendBtn.addEventListener('click', sendMessage);
userInput.addEventListener('keydown', e => { if (e.key === 'Enter') sendMessage(); });

function appendMessage(text, who = 'bot') {
    const div = document.createElement('div');
    div.classList.add('message', who);
    div.innerHTML = text;
    chatBody.appendChild(div);
    chatBody.scrollTop = chatBody.scrollHeight;
}

function appendBotWelcome() {
    appendMessage("👋 ¡Hola! Soy <b>SmartZoneBot</b>. Puedo ayudarte con <b>laptops</b>, <b>monitores</b>, <b>celulares</b>, <b>ofertas</b>, <b>envíos</b>, <b>pagos</b> o <b>contacto</b>.", 'bot');
    const actions = document.createElement('div');
    actions.className = "quick-actions";
    actions.innerHTML = `
        <button data-q="laptops">💻 Laptops</button>
        <button data-q="monitores">🖥️ Monitores</button>
        <button data-q="celulares">📱 Celulares</button>
        <button data-q="consolas">🎮 Consolas</button>
        <button data-q="ofertas">🔥 Ofertas</button>
        <button data-q="envíos">🚚 Envíos</button>
        <button data-q="pagos">💳 Pagos</button>
        <button data-q="contacto">📞 Contacto</button>
      `;
    chatBody.appendChild(actions);

    actions.querySelectorAll("button").forEach(btn => {
        btn.addEventListener("click", () => sendMessage(btn.dataset.q));
    });
}

function sendMessage(textOverride = null) {
    const text = textOverride || userInput.value.trim();
    if (!text) return;
    appendMessage(text, 'user');
    userInput.value = '';
    setTimeout(() => appendMessage(getBotResponse(text), 'bot'), 600);
}

function getBotResponse(input) {
    input = input.toLowerCase();

    if (input.includes('oferta')) return "🔥 Hoy: 20% OFF en accesorios y 15% en monitores seleccionados.";
    if (input.includes('envío')) return "🚚 Envíos gratis en compras mayores a S/200. Tiempo de entrega: 1-3 días hábiles.";
    if (input.includes('pago')) return "💳 Aceptamos tarjetas de crédito/débito, Yape, Plin y transferencias bancarias.";
    if (input.includes('contacto')) {
        return `
        📞 <b>Teléfono:</b> <a href="tel:+51987654321">+51 987 654 321</a><br>
        💬 <b>WhatsApp:</b> <a href="https://wa.me/51987654321" target="_blank">Chatear</a><br>
        📧 <b>Email:</b> <a href="mailto:soporte@smartzone.com">soporte@smartzone.com</a><br>
        🌐 <b>Web:</b> <a href="https://smartzone.com" target="_blank">smartzone.com</a>
        `;
    }

    // Consolas
    if (input.includes('consola') || input.includes('consolas')) {
        return `
        🎮 <b>Top Consolas:</b><br><br>
        🔹 <b>Nintendo Switch (OLED Model)</b><br>
        📺 Pantalla: OLED de 7"<br>
        💾 Almacenamiento: 64 GB<br>
        🎮 Modo: Portátil y Sobremesa<br>
        🕹️ Incluye: 2 Joy-Cons<br>
        💲 Precio: <b>S/ 1,699</b><br><br>

        🔹 <b>PlayStation 5 Slim Edition</b><br>
        💾 Almacenamiento: 1 TB SSD<br>
        📺 Resolución: 4K UHD<br>
        🎮 Modo: Sobremesa<br>
        🕹️ Incluye: 1 Control DualSense<br>
        💲 Precio: <b>S/ 3,299</b><br><br>

        🔹 <b>Xbox Series S</b><br>
        💾 Almacenamiento: 1 TB SSD<br>
        📺 Resolución: 4K UHD / 8K Ready<br>
        🎮 Modo: Sobremesa<br>
        🕹️ Incluye: 1 Control Xbox Wireless<br>
        💲 Precio: <b>S/ 2,999</b><br><br>

        🔹 <b>Steam Deck OLED</b><br>
        📺 Pantalla: OLED de 7.4"<br>
        💾 Almacenamiento: 512 GB NVMe SSD<br>
        🎮 Modo: Portátil<br>
        ⚙️ Sistema: SteamOS<br>
        💲 Precio: <b>S/ 2,499</b><br><br>
        👉 Elige una consola para más detalles.
        `;
    }
    // Laptops
    if (input.includes('laptop') || input.includes('laptops')) {
        return `
        💻 <b>Opciones de Laptops:</b><br><br>
        🔹 <b>ASUS TUF Gaming F15</b> – Intel i7 12ª Gen | RTX 3060 | 16GB RAM | 512GB SSD – <b>S/ 5,499</b><br><br>
        🔹 <b>HP Pavilion 15</b> – AMD Ryzen 5 | Radeon Graphics | 8GB RAM | 512GB SSD – <b>S/ 3,299</b><br><br>
        🔹 <b>Lenovo IdeaPad 3</b> – Intel i5 11ª Gen | Iris Xe | 8GB RAM | 256GB SSD – <b>S/ 2,899</b><br><br>
        🔹 <b>Acer Nitro 5</b> – Intel i5 12ª Gen | RTX 3050 | 16GB RAM | 512GB SSD – <b>S/ 4,199</b><br><br>
        🔹 <b>MSI Katana GF66</b> – Intel i7 11ª Gen | RTX 3060 | 16GB RAM | 1TB SSD – <b>S/ 6,299</b><br><br>
        👉 Elige una marca para más detalles, por ejemplo escribe: <b>ASUS Gaming</b>.
        `;
    }

    // ASUS Gaming especial
    if (input.includes('asus gaming')) {
        return `
        💻 <b>ASUS TUF Gaming F15</b><br>
        ⚡ Intel Core i7 12ª Gen | RTX 3060 6GB | 16GB RAM DDR4 | 512GB SSD NVMe<br>
        🎮 Pantalla 15.6" FHD 144Hz | Teclado RGB<br>
        💲 Precio: <b>S/ 5,499.00</b><br>
        🔥 Oferta especial: Incluye mouse gamer de regalo.<br><br>
        👉 <a href="https://smartzone.com/asus-gaming" target="_blank">Ver detalles</a>
        `;
    }

    // Monitores
    if (input.includes('monitor') || input.includes('monitores')) {
        return `
        🖥️ <b>Opciones de Monitores:</b><br><br>
        🔹 <b>Samsung Odyssey G5</b> – 27" QHD Curvo | 144Hz | 1ms – <b>S/ 1,399</b><br><br>
        🔹 <b>LG UltraGear</b> – 24" FHD | 165Hz | 1ms – <b>S/ 999</b><br><br>
        🔹 <b>ASUS TUF Gaming VG27AQ</b> – 27" QHD | 165Hz | Adaptive Sync – <b>S/ 1,599</b><br><br>
        🔹 <b>Acer Predator XB273</b> – 27" FHD | 240Hz | HDR – <b>S/ 1,899</b><br><br>
        🔹 <b>Dell UltraSharp</b> – 27" 4K UHD | IPS | HDR – <b>S/ 2,199</b><br><br>
        👉 Elige una marca para más detalles.
        `;
    }

    // Celulares
    if (input.includes('celular') || input.includes('celulares') || input.includes('smartphone')) {
        return `
        📱 <b>Opciones de Celulares:</b><br><br>
        🔹 <b>iPhone 14</b> – 6.1" OLED | 128GB | 12MP Dual – <b>S/ 4,299</b><br><br>
        🔹 <b>Samsung Galaxy S23</b> – 6.1" AMOLED 120Hz | 8GB RAM | 256GB – <b>S/ 3,899</b><br><br>
        🔹 <b>Xiaomi 12T Pro</b> – 6.67" AMOLED | Snapdragon 8+ Gen1 | 200MP – <b>S/ 2,599</b><br><br>
        🔹 <b>Motorola Edge 30</b> – 6.5" OLED | 144Hz | 50MP | 256GB – <b>S/ 1,899</b><br><br>
        🔹 <b>Huawei P50 Pro</b> – 6.6" OLED | 120Hz | 50MP Cuádruple – <b>S/ 3,499</b><br><br>
        👉 Elige un modelo para más detalles.
        `;
    }

    return "🤔 No entendí bien. Intenta con: <b>laptops</b>, <b>monitores</b>, <b>celulares</b>, <b>ofertas</b>, <b>envíos</b>, <b>pagos</b> o <b>contacto</b>.";
}