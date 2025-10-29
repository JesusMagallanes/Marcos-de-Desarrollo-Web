package Pry_01.Web.de.Ventas.de.Computadoras.Controller.Token;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;

@Service
public class TokenBlacklistService {
    private final Set<String> blacklist = new HashSet<>();
    //agregar token a la blacklist
    public void addToBlacklist(String token) {
        blacklist.add(token);
    }
    //verificar si el token esta en la blacklist
    public boolean isBlacklisted(String token) {
        return blacklist.contains(token);
    }
}
