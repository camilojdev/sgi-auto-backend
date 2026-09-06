package com.sgi.auto.autenticacion;

import com.sgi.auto.usuarios.UsuarioRepositorio;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class ConfiguracionSeguridad {

    private final FiltroJwt filtroJwt;
    private final UsuarioRepositorio usuarioRepositorio;

    @Bean
    public SecurityFilterChain cadenaFiltros(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(fuenteCors()))
                .sessionManagement(s ->
                        s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/autenticacion/**",
                                "/api/docs/**",
                                "/api/swagger-ui/**",
                                "/swagger-ui/**",
                                "/api/salud"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .authenticationProvider(proveedorAutenticacion())
                .addFilterBefore(filtroJwt, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * UserDetailsService que carga el Usuario directamente desde el repositorio.
     * Usuario ya implementa UserDetails, así que no se necesita adaptador.
     */
    @Bean
    public UserDetailsService usuarioDetailsService() {
        return nombreUsuario -> usuarioRepositorio
                .buscarPorNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuario no encontrado: " + nombreUsuario));
    }

    /**
     * Provider explícito: le dice a Spring exactamente cómo validar credenciales,
     * evitando que Spring intente auto-configurar uno y genere referencia circular.
     */
    @Bean
    public AuthenticationProvider proveedorAutenticacion() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(usuarioDetailsService());
        provider.setPasswordEncoder(codificadorContrasena());
        return provider;
    }

    @Bean
    public AuthenticationManager gestorAutenticacion() {
        return new ProviderManager(proveedorAutenticacion());
    }

    @Bean
    public PasswordEncoder codificadorContrasena() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource fuenteCors() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "https://*.vercel.app",
                "https://sgi-auto-frontend.vercel.app"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", config);
        return fuente;
    }
}