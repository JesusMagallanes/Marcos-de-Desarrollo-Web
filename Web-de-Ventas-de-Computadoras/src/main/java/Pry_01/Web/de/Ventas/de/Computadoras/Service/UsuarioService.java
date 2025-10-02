package Pry_01.Web.de.Ventas.de.Computadoras.Service;


import java.util.List;

import org.springframework.stereotype.Service;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.UsuarioRepository;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public List<UsuarioModel> listarUsuario(){
        return usuarioRepository.findAll();
    }

    public UsuarioModel guardarUsuario(UsuarioModel usuario){
        return usuarioRepository.save(usuario);
    }

    public UsuarioModel obtenerPorId(Long id){
        return usuarioRepository.findById(id).orElse(null);
    }

    public void eliminarUsuario(Long id){
        usuarioRepository.deleteById(id);
    }
    
   
}
