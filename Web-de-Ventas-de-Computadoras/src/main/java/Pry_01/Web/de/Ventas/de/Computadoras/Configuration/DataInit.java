package Pry_01.Web.de.Ventas.de.Computadoras.Configuration;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.Roles;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.UsuarioRepository;

@Configuration
public class DataInit {
     @Bean
    public CommandLineRunner initData(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            String email = "admin@smartzone.com";

            if (usuarioRepository.findByEmailAddress(email).isEmpty()) {
                UsuarioModel admin = UsuarioModel.builder()
                        .name("Admin")
                        .lastname("Principal")
                        .emailAddress(email)
                        .password(passwordEncoder.encode("holaMundo426")) 
                        .phoneNumber("999999999")
                        .address("Oficina central")
                        .rol(Roles.ADMINISTRADOR)
                        .build();

                usuarioRepository.save(admin);
                System.out.println(" Usuario admin creado con éxito:");
                System.out.println("   Email: " + email);
                System.out.println("   Password: holaMundo426");
            } else {
                System.out.println("Ya existe un admin con el correo: " + email);
            }
        };
    }
}
