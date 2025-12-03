let map;
let marker;
let geocoder;

// Función que se ejecuta cuando la API de Google Maps se carga
function initMap() {
    // Verificar que el elemento del mapa existe
    const mapElement = document.getElementById('map');
    if (!mapElement) {
        console.warn('Elemento del mapa no encontrado');
        return;
    }

    // Verificar que Google Maps está disponible
    if (typeof google === 'undefined' || !google.maps) {
        console.warn('Google Maps API no está cargada');
        return;
    }

    // Obtenemos la dirección actual del usuario (si existe)
    const direccionInput = document.getElementById('direccionInput');
    const direccionInicial = direccionInput ? direccionInput.value : '';
    const defaultLocation = { lat: -9.189967, lng: -75.015152 }; // Centro de Perú (ejemplo)
    
    geocoder = new google.maps.Geocoder();
        
        if (direccionInicial) {
            // Intentar geocodificar la dirección actual del usuario para centrar el mapa
            geocoder.geocode({ 'address': direccionInicial }, (results, status) => {
                let initialLocation = defaultLocation;
                if (status === 'OK' && results[0]) {
                    initialLocation = results[0].geometry.location;
                }
                loadMapAndMarker(initialLocation);
            });
        } else {
            loadMapAndMarker(defaultLocation);
        }
    }

    function loadMapAndMarker(initialLocation) {
        const mapElement = document.getElementById("map");
        if (!mapElement) return;

        // Limpiar mapa anterior si existe
        if (map) {
            google.maps.event.clearInstanceListeners(map);
        }
        if (marker) {
            marker.setMap(null);
        }

        map = new google.maps.Map(mapElement, {
            zoom: 12,
            center: initialLocation,
        });
        
        marker = new google.maps.Marker({
            position: initialLocation,
            map: map,
            draggable: true // Permite arrastrar el marcador
        });
        
        // Evento al hacer clic en el mapa
        map.addListener("click", (e) => {
            marker.setPosition(e.latLng);
            geocodeLatLng(e.latLng);
        });
        
        // Evento al terminar de arrastrar el marcador
        marker.addListener('dragend', (e) => {
            geocodeLatLng(e.latLng);
        });
    }

    // Función para convertir coordenadas (Lat/Lng) a una dirección legible
    function geocodeLatLng(latlng) {
        geocoder.geocode({ 'location': latlng })
            .then((response) => {
                if (response.results[0]) {
                    const address = response.results[0].formatted_address;
                    // Actualiza el campo de input del formulario
                    document.getElementById('direccionInput').value = address;
                } else {
                    // Si no encuentra dirección, limpia el campo
                    document.getElementById('direccionInput').value = "Ubicación no reconocida, por favor reubique el marcador.";
                }
            })
            .catch((e) => {
                console.error("Fallo el Geocoder: ", e);
                alert("Error al obtener la dirección. Revisa la consola para más detalles.");
            });
    }

    // Asegurarse de que initMap se ejecute solo cuando el fragmento 'cuenta' esté visible
    // Esto es crucial si el contenido se carga dinámicamente con tu JS
    document.addEventListener('DOMContentLoaded', () => {
        // Tu script Loggin-user.js probablemente maneja la carga del fragmento.
        // Debes asegurarte de llamar a initMap() DESPUÉS de que el fragmento con el id="map" se haya cargado en el DOM.
        
        // Asumiendo que 'cuenta' es el fragmento inicial, llamamos a initMap al cargar la página
        // Si usas AJAX o Fetch para cargar el fragmento, debes llamar a initMap en el callback de éxito.
        if (document.getElementById('map')) {
            // initMap se llama automáticamente por el script de Google API (callback=initMap)
        }
    });