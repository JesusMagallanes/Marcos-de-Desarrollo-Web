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
    public CommandLineRunner initData(UsuarioRepository usuarioRepository,
                                      PasswordEncoder passwordEncoder) {
        return args -> {

            String email = "admin@smartzone.com";

            // Si no existe, lo creamos
            if (usuarioRepository.findByEmailAddress(email).isEmpty()) {

                UsuarioModel admin = UsuarioModel.builder()
                        .name("Admin")
                        .lastname("Principal")
                        .emailAddress(email)
                        .password(passwordEncoder.encode("holaMundo426"))
                        .phoneNumber("999999999")
                        .address("Oficina central")
                        .rol(Roles.ADMINISTRADOR) // 👈 muy importante
                        .build();

                usuarioRepository.save(admin);

                System.out.println("========================================");
                System.out.println("   ADMINISTRADOR CREADO CORRECTAMENTE   ");
                System.out.println("========================================");
                System.out.println(" Email: " + email);
                System.out.println(" Password: holaMundo426");
                System.out.println(" Rol: ADMINISTRADOR");
                System.out.println("========================================");

            } else {
                System.out.println("Ya existe un administrador con el correo: " + email);
            }
        };
    }
}
