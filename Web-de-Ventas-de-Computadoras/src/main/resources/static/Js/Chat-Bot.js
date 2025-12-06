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
    
    // Mostrar indicador de "escribiendo..."
    const typingIndicator = document.createElement('div');
    typingIndicator.className = 'message bot typing-indicator';
    typingIndicator.innerHTML = '<span></span><span></span><span></span>';
    chatBody.appendChild(typingIndicator);
    chatBody.scrollTop = chatBody.scrollHeight;
    
    // Enviar mensaje al backend
    fetch('/api/chatbot/mensaje', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({ mensaje: text })
    })
    .then(response => {
        console.log('Response status:', response.status);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        return response.json();
    })
    .then(data => {
        console.log('Data received:', data);
        // Remover indicador de escritura
        typingIndicator.remove();
        
        // Mostrar respuesta del backend
        appendMessage(data.respuesta, 'bot');
        
        // Si hay productos, mostrar botones de acción
        if (data.productos && data.productos.length > 0) {
            appendProductButtons(data.productos);
        }
    })
    .catch(error => {
        console.error('Error al comunicar con el chatbot:', error);
        typingIndicator.remove();
        appendMessage('😔 Lo siento, hubo un error al procesar tu mensaje. Intenta de nuevo.<br><small>Error: ' + error.message + '</small>', 'bot');
    });
}

// Función para mostrar botones de productos
function appendProductButtons(productos) {
    if (!productos || productos.length === 0) return;
    
    const productsDiv = document.createElement('div');
    productsDiv.className = 'chat-products';
    productsDiv.innerHTML = '<small><b>Productos encontrados:</b></small>';
    
    productos.forEach(producto => {
        const btn = document.createElement('button');
        btn.className = 'product-btn';
        btn.innerHTML = `
            <strong>${producto.name}</strong>
            <span>S/ ${producto.precio.toFixed(2)}</span>
        `;
        btn.onclick = () => {
            // Intentar navegar usando el nombre de categoría para construir el slug
            const categoriaSlug = producto.categoriaName ? producto.categoriaName.toLowerCase().replace(/\s+/g, '-') : 'all';
            const marca = producto.marcaName ? ` - ${producto.marcaName}` : '';
            window.location.href = `/productos/categoria/${categoriaSlug}`;
        };
        productsDiv.appendChild(btn);
    });
    
    chatBody.appendChild(productsDiv);
    chatBody.scrollTop = chatBody.scrollHeight;
}