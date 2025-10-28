function abrirModalEditar(button) {
    const userId = button.getAttribute('data-id');
    
    fetch(`/usuarios/${userId}`)
        .then(res => res.json())
        .then(data => {
            document.querySelector('[name="id"]').value = data.id;
            document.getElementById('name-edit').value = data.name;
            document.getElementById('lastname-edit').value = data.lastname;
            document.getElementById('emailAddress-edit').value = data.emailAddress;
            document.getElementById('phoneNumber-edit').value = data.phoneNumber;
            document.getElementById('address-edit').value = data.address;
            document.getElementById('rol-edit').value = data.rol;
        });
}

